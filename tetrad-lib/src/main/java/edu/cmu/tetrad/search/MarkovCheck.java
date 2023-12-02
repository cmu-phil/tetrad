package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.test.RowsSettable;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.UniformityTest;
import org.apache.commons.math3.distribution.BinomialDistribution;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

import static org.apache.commons.math3.util.FastMath.min;

/**
 * <p>Checks whether a graph is locally Markov or locally Faithful given a data set. First a lists of m-separation
 * predictions are made for each pair of variables in the graph given the parents of one of the variables, one list (for
 * local Markov) where the m-separation holds and another list (for local Faithfulness) where the m-separation does not
 * hold. Then the predictions are tested against the data set using the independence test. For the Markov test, since an
 * independence test yielding p-values should be Uniform under the null hypothesis, these p-values are tested for
 * Uniformity using the Kolmogorov-Smirnov test. Also, a fraction of dependent judgments is returned, which should equal
 * the alpha level of the independence test if the test is Uniform under the null hypothesis. For the Faithfulness test,
 * the p-values are tested for Uniformity using the Kolmogorov-Smirnov test; these should be dependent. Also, a fraction
 * of dependent judgments is returned, which should be maximal./p>
 *
 * @author josephramsey
 */
public class MarkovCheck {

    private final Graph graph;
    private final IndependenceTest independenceTest;
    private final MsepTest msep;
    private final List<IndependenceResult> resultsIndep = new ArrayList<>();
    private final List<IndependenceResult> resultsDep = new ArrayList<>();
    private ConditioningSetType setType;
    private boolean parallelized = false;
    private double fractionDependentIndep = Double.NaN;
    private double fractionDependentDep = Double.NaN;
    private double ksPValueIndep = Double.NaN;
    private double ksPValueDep = Double.NaN;
    private double bernoulliPIndep = Double.NaN;
    private double bernoulliPDep = Double.NaN;
    private int numResamples = 1;
    private double percentResammple = 0.5;

    /**
     * Constructor. Takes a graph and an independence test over the variables of the graph.
     *
     * @param graph            The graph.
     * @param independenceTest The test over the variables of the graph.
     */
    public MarkovCheck(Graph graph, IndependenceTest independenceTest, ConditioningSetType setType) {
        this.graph = GraphUtils.replaceNodes(graph, independenceTest.getVariables());
        this.independenceTest = independenceTest;
        this.msep = new MsepTest(this.graph);
        this.setType = setType;
    }

    @NotNull
    public static AllSubsetsIndependenceFacts getAllSubsetsIndependenceFacts(Graph graph) {
        List<Node> variables = new ArrayList<>(graph.getNodes());
        MsepTest msepTest = new MsepTest(graph);

        List<Node> nodes = new ArrayList<>(variables);
        Collections.sort(nodes);

        Set<IndependenceFact> msep = new HashSet<>();
        Set<IndependenceFact> mconn = new HashSet<>();

        for (Node x : nodes) {
            List<Node> other = new ArrayList<>(variables);
            Collections.sort(other);
            other.remove(x);

            for (Node y : other) {
                List<Node> _other = new ArrayList<>(other);
                _other.remove(y);

                SublistGenerator generator = new SublistGenerator(_other.size(), _other.size());
                int[] list;

                while ((list = generator.next()) != null) {
                    Set<Node> z = GraphUtils.asSet(list, _other);

                    if (msepTest.isMSeparated(x, y, z)) {
                        msep.add(new IndependenceFact(x, y, z));
                    } else {
                        mconn.add(new IndependenceFact(x, y, z));
                    }
                }
            }
        }

        return new AllSubsetsIndependenceFacts(msep, mconn);
    }

    /**
     * Generates all results, for both the local Markov and local Faithfulness checks, for each node in the graph given
     * the parents of that node. These results are stored in the resultsIndep and resultsDep lists.
     *
     * @see #getResults(boolean)
     */
    public void generateResults() {
        resultsIndep.clear();
        resultsDep.clear();

        if (setType == ConditioningSetType.GLOBAL_MARKOV) {
            AllSubsetsIndependenceFacts result = getAllSubsetsIndependenceFacts(graph);
            generateResultsAllSubsets(true, result.msep, result.mconn);
            generateResultsAllSubsets(false, result.msep, result.mconn);
        } else {
            List<Node> variables = independenceTest.getVariables();
            List<Node> nodes = new ArrayList<>(variables);
            Collections.sort(nodes);

            List<Node> order = null;

            if (!graph.paths().existsDirectedCycle()) {
                order = graph.paths().getValidOrder(graph.getNodes(), true);
            }

            Set<IndependenceFact> msep = new HashSet<>();
            Set<IndependenceFact> mconn = new HashSet<>();

            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    Node x = nodes.get(i);
                    Node y = nodes.get(j);

                    if (graph.isAdjacentTo(x, y)) continue;

                    Set<Node> z;

                    switch (setType) {
                        case LOCAL_MARKOV:
                            z = new HashSet<>(graph.getParents(x));
                            break;
                        case ORDERED_LOCAL_MARKOV:
                            if (order == null) throw new IllegalArgumentException("No valid order found.");
                            z = new HashSet<>(graph.getParents(x));

                            // Keep only the parents in Prefix(x).
                            for (Node w : new ArrayList<>(z)) {
                                int i1 = order.indexOf(x);
                                int i2 = order.indexOf(w);

                                if (i2 >= i1) {
                                    z.remove(w);
                                }
                            }

                            break;
                        case MARKOV_BLANKET:
                            z = GraphUtils.markovBlanket(x, graph);
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown separation set type: " + setType);
                    }

                    List<Node> other = new ArrayList<>(graph.getNodes());
                    Collections.sort(other);
                    other.removeAll(z);

                    for (Node w : other) {
                        if (w == x || w == y) continue;
                        if (z.contains(x) || z.contains(y) || z.contains(w)) continue;
                        if (this.msep.isMSeparated(x, y, z)) {
                            if (!msep.contains(new IndependenceFact(y, x, z))) {
                                msep.add(new IndependenceFact(x, y, z));
                            }
                        } else {
                            if (!mconn.contains(new IndependenceFact(y, x, z))) {
                                mconn.add(new IndependenceFact(x, y, z));
                            }
                        }
                    }
                }
            }

            generateResults(msep, mconn);
        }

        calcStats(true);
        calcStats(false);
    }

    /**
     * Returns type of conditioning sets to use in the Markov check.
     *
     * @return The type of conditioning sets to use in the Markov check.
     * @see ConditioningSetType
     */
    public ConditioningSetType getSetType() {
        return this.setType;
    }

    /**
     * Sets the type of conditioning sets to use in the Markov check.
     *
     * @param setType The type of conditioning sets to use in the Markov check.
     * @see ConditioningSetType
     */
    public void setSetType(ConditioningSetType setType) {
        this.setType = setType;
    }

    /**
     * True if the checks should be parallelized. (Not always a good idea.)
     *
     * @param parallelized True if the checks should be parallelized.
     */
    public void setParallelized(boolean parallelized) {
        this.parallelized = parallelized;
    }

    /**
     * After the generateResults method has been called, this method returns the results for the local Markov or local
     * Faithfulness check, depending on the value of the indep parameter.
     *
     * @param indep True for the local Markov results, false for the local Faithfulness results.
     * @return The results for the local Markov or local Faithfulness check.
     */
    public List<IndependenceResult> getResults(boolean indep) {
        if (indep) {
            return new ArrayList<>(this.resultsIndep);
        } else {
            return new ArrayList<>(this.resultsDep);
        }
    }

    /**
     * Returns the list of p-values for the given list of results.
     *
     * @param results The results.
     * @return Their p-values.
     */
    public List<Double> getPValues(List<IndependenceResult> results) {
        List<Double> pValues = new ArrayList<>();

        for (IndependenceResult result : results) {
            pValues.add(result.getPValue());
        }

        return pValues;
    }

    /**
     * Returns the fraction of dependent judgments for the given list of results.
     *
     * @param indep True for the local Markov results, false for the local Faithfulness results.
     * @return The fraction of dependent judgments for this condition.
     */
    public double getFractionDependent(boolean indep) {
        if (indep) {
            return fractionDependentIndep;
        } else {
            return fractionDependentDep;
        }
    }

    /**
     * Returns the Kolmorogov-Smirnov p-value for the given list of results.
     *
     * @param indep True for the local Markov results, false for the local Faithfulness results.
     * @return The Kolmorogov-Smirnov p-value for this condition.
     */
    public double getKsPValue(boolean indep) {
        if (indep) {
            return ksPValueIndep;
        } else {
            return ksPValueDep;
        }
    }

    public double getBernoulliPValue(boolean indep) {
        if (indep) {
            return bernoulliPIndep;
        } else {
            return bernoulliPDep;
        }
    }

    /**
     * Returns the variables of the independence test.
     *
     * @return The variables of the independence test.
     */
    public List<Node> getVariables() {
        return new ArrayList<>(independenceTest.getVariables());
    }

//    /**
//     * Returns the Markov Adequacy Score for the graph. This is zero if the p-value of the KS test of Uniformity is less
//     * than alpha, and the fraction of dependent pairs otherwise. This is only for continuous Gaussian data, as it
//     * hard-codes the Fisher Z test for the local Markov and Faithfulness check.
//     *
//     * @param alpha The alpha level for the KS test of Uniformity. An alpha level greater than this will be considered
//     *              uniform.
//     * @return The Markov Adequacy Score for this graph given the data.
//     */
//    public double getMarkovAdequacyScore(double alpha) {
//        if (getKsPValue(true) > alpha) {
//            return getFractionDependent(false);
//        } else {
//            return 0.0;
//        }
//    }

    /**
     * Returns the variable with the given name.
     *
     * @param name The name of the variables.
     * @return The variable with the given name.
     */
    public Node getVariable(String name) {
        return independenceTest.getVariable(name);
    }

    /**
     * Returns the independence test being used.
     *
     * @return This test.
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    private void generateResults(Set<IndependenceFact> msep, Set<IndependenceFact> mconn) {
        class IndCheckTask implements Callable<Pair<Set<IndependenceResult>, Set<IndependenceResult>>> {
            private final int from;
            private final int to;
            private final List<IndependenceFact> facts;
            private final IndependenceTest independenceTest;

            IndCheckTask(int from, int to, List<IndependenceFact> facts, IndependenceTest test) {
                this.from = from;
                this.to = to;
                this.facts = facts;
                this.independenceTest = test;
            }

            @Override
            public Pair<Set<IndependenceResult>, Set<IndependenceResult>> call() {
                Set<IndependenceResult> resultsIndep = new HashSet<>();
                Set<IndependenceResult> resultsDep = new HashSet<>();

                for (int i = from; i < to; i++) {
                    if (Thread.interrupted()) break;
                    IndependenceFact fact = facts.get(i);

                    Node x = fact.getX();
                    Node y = fact.getY();
                    Set<Node> z = fact.getZ();

                    if (independenceTest instanceof RowsSettable) {
//                        for (int t = 0; t < getNumResamples(); t++) {
                        List<Integer> rows = getSubsampleRows(percentResammple);
                            ((RowsSettable) independenceTest).setRows(rows);

                            boolean verbose = independenceTest.isVerbose();
                            independenceTest.setVerbose(false);
                            IndependenceResult result;
                            try {
                                result = independenceTest.checkIndependence(x, y, z);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                            boolean indep = result.isIndependent();
                            double pValue = result.getPValue();
                            independenceTest.setVerbose(verbose);

                            if (!Double.isNaN(pValue)) {
                                if (msep.contains(new IndependenceFact(x, y, z))) {
                                    resultsIndep.add(new IndependenceResult(fact, indep, pValue, Double.NaN));
                                } else if (mconn.contains(new IndependenceFact(x, y, z))) {
                                    resultsDep.add(new IndependenceResult(fact, indep, pValue, Double.NaN));
                                } else {
                                    throw new IllegalArgumentException("Unknown separation set: " + z);
                                }
                            }
//                        }
                    } else {
                        boolean verbose = independenceTest.isVerbose();
                        independenceTest.setVerbose(false);
                        IndependenceResult result;
                        try {
                            result = independenceTest.checkIndependence(x, y, z);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        boolean indep = result.isIndependent();
                        double pValue = result.getPValue();
                        independenceTest.setVerbose(verbose);

                        if (!Double.isNaN(pValue)) {
                            if (msep.contains(new IndependenceFact(x, y, z))) {
                                resultsIndep.add(new IndependenceResult(fact, indep, pValue, Double.NaN));
                            } else if (mconn.contains(new IndependenceFact(x, y, z))) {
                                resultsDep.add(new IndependenceResult(fact, indep, pValue, Double.NaN));
                            } else {
                                throw new IllegalArgumentException("Unknown separation set: " + z);
                            }
                        }
                    }
                }

                return new Pair<>(resultsIndep, resultsDep);
            }
        }

        List<Callable<Pair<Set<IndependenceResult>, Set<IndependenceResult>>>> tasks = new ArrayList<>();

        Set<IndependenceFact> _facts = new HashSet<>();
        _facts.addAll(msep);
        _facts.addAll(mconn);

        List<IndependenceFact> facts = new ArrayList<>(_facts);

        int chunkSize = getChunkSize(facts.size());

        for (int i = 0; i < facts.size() && !Thread.currentThread().isInterrupted(); i += chunkSize) {
            IndCheckTask task = new IndCheckTask(i, min(facts.size(), i + chunkSize), facts, independenceTest);

            if (!parallelized) {
                Pair<Set<IndependenceResult>, Set<IndependenceResult>> _results = task.call();
                resultsIndep.addAll(_results.getFirst());
                resultsDep.addAll(_results.getSecond());
            } else {
                tasks.add(task);
            }
        }

        if (parallelized) {
            List<Future<Pair<Set<IndependenceResult>, Set<IndependenceResult>>>> theseResults = ForkJoinPool.commonPool().invokeAll(tasks);

            for (Future<Pair<Set<IndependenceResult>, Set<IndependenceResult>>> future : theseResults) {
                try {
                    resultsIndep.addAll(future.get().getFirst());
                    resultsDep.addAll(future.get().getSecond());
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private List<Integer> getSubsampleRows(double v) {
        int sampleSize = independenceTest.getSampleSize();
        int subsampleSize = (int) FastMath.ceil(sampleSize * v);
        List<Integer> rows = new ArrayList<>(sampleSize);
        for (int i = 0; i < sampleSize; i++) {
            rows.add(i);
        }
        Collections.shuffle(rows);
        return rows.subList(0, subsampleSize);
    }

    private List<Integer> getBoostrapSample(double v) {
        int sampleSize = independenceTest.getSampleSize();
        int subsampleSize = (int) FastMath.floor(sampleSize * v);
        List<Integer> rows = new ArrayList<>(sampleSize);
        for (int i = 0; i < subsampleSize; i++) {
            rows.add(RandomUtil.getInstance().nextInt(sampleSize));
        }
        return rows;
    }

    private void generateResultsAllSubsets(boolean indep, Set<IndependenceFact> msep, Set<IndependenceFact> mconn) {
        Set<IndependenceFact> facts = indep ? msep : mconn;

        class IndCheckTask implements Callable<List<IndependenceResult>> {
            private final int from;
            private final int to;
            private final List<IndependenceFact> facts;
            private final IndependenceTest independenceTest;

            IndCheckTask(int from, int to, List<IndependenceFact> facts, IndependenceTest test) {
                this.from = from;
                this.to = to;
                this.facts = facts;
                this.independenceTest = test;
            }

            @Override
            public List<IndependenceResult> call() {
                List<IndependenceResult> results = new ArrayList<>();

                for (int i = from; i < to; i++) {
                    if (Thread.interrupted()) break;
                    IndependenceFact fact = facts.get(i);

                    Node x = fact.getX();
                    Node y = fact.getY();
                    Set<Node> z = fact.getZ();
                    boolean verbose = independenceTest.isVerbose();
                    independenceTest.setVerbose(false);
                    IndependenceResult result;
                    try {
                        result = independenceTest.checkIndependence(x, y, z);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    boolean indep = result.isIndependent();
                    double pValue = result.getPValue();
                    independenceTest.setVerbose(verbose);

                    if (!Double.isNaN(pValue)) {
                        results.add(new IndependenceResult(fact, indep, pValue, Double.NaN));
                    }
                }

                return results;
            }
        }

        generateResults(msep, mconn);
    }

    private void calcStats(boolean indep) {
        List<IndependenceResult> results = new ArrayList<>(getResultsLocal(indep));

//        Collections.shuffle(_results);
//
//        List<IndependenceResult> results = new ArrayList<>();
//        for (int i = 0; i < _results.size() / getNumResamples(); i++) {
//            results.add(_results.get(i));
//        }

        int dependent = 0;

        for (IndependenceResult result : results) {
            if (result.isDependent() && !Double.isNaN(result.getPValue())) dependent++;
        }

        if (indep) {
            fractionDependentIndep = dependent / (double) results.size();
        } else {
            fractionDependentDep = dependent / (double) results.size();
        }

        List<Double> pValues = getPValues(results);

        if (indep) {
            if (pValues.size() < 2) {
                ksPValueIndep = Double.NaN;
                bernoulliPIndep = Double.NaN;
            } else {
                ksPValueIndep = UniformityTest.getPValue(pValues, 0.0, 1.0);
                bernoulliPIndep = getBernoulliP(pValues, independenceTest.getAlpha());
            }
        } else {
            if (pValues.size() < 2) {
                ksPValueDep = Double.NaN;
                bernoulliPDep = Double.NaN;
            } else {
                ksPValueDep = UniformityTest.getPValue(pValues, 0.0, 1.0);
                bernoulliPDep = getBernoulliP(pValues, independenceTest.getAlpha());
            }
        }
    }

    /**
     * Returns a Bernoulli p-value for the hypothesis that the distribution of p-values is not Uniform under the null
     * hypothesis. Values less than alpha imply non-uniform distributions.
     * @param pValues The p-values.
     * @param alpha The alpha level. Rejections with p-values less than this are considered dependent.
     * @return The Bernoulli p-value for non-uniformity.
     */
    private double getBernoulliP(List<Double> pValues, double alpha) {
        int dependentJudgments = 0;

        for (double pValue : pValues) {
            if (pValue < alpha) dependentJudgments++;
        }

        int n = pValues.size();

        // The left tail of this binomial distribution is a p-value for getting too few dependent judgments for
        // the distribution to count as uniform.
        BinomialDistribution bd = new BinomialDistribution(n, alpha);

        // We want the area to the right of this, so we subtract from 1.
        return (1.0 - bd.cumulativeProbability(dependentJudgments)) / 2.0 + (bd.probability(n - dependentJudgments) / 2.0);
    }

    private int getChunkSize(int n) {
        int chunk = (int) FastMath.ceil((n / ((double) (5 * Runtime.getRuntime().availableProcessors()))));
        if (chunk < 1) chunk = 1;
        return chunk;
    }

    private List<IndependenceResult> getResultsLocal(boolean indep) {
        if (indep) {
            return this.resultsIndep;
        } else {
            return this.resultsDep;
        }
    }

    public int getNumResamples() {
        return numResamples;
    }

    public void setNumResamples(int numResamples) {
        this.numResamples = numResamples;
    }

    public double getPercentResammple() {
        return percentResammple;
    }

    public void setPercentResammple(double percentResammple) {
        this.percentResammple = percentResammple;
    }

    public static class AllSubsetsIndependenceFacts {
        private final Set<IndependenceFact> msep;
        private final Set<IndependenceFact> mconn;

        public AllSubsetsIndependenceFacts(Set<IndependenceFact> msep, Set<IndependenceFact> mconn) {
            this.msep = msep;
            this.mconn = mconn;
        }

        public String toStringIndep() {
            StringBuilder builder = new StringBuilder("All subsets independence facts:\n");

            for (IndependenceFact fact : msep) {
                builder.append(fact).append("\n");
            }

            return builder.toString();
        }


        public String toStringDep() {
            StringBuilder builder = new StringBuilder("All subsets independence facts:\n");

            for (IndependenceFact fact : mconn) {
                builder.append(fact).append("\n");
            }

            return builder.toString();
        }

        public List<IndependenceFact> getMsep() {
            List<IndependenceFact> facts = new ArrayList<>(msep);
            Collections.sort(facts);
            return facts;
        }

        public List<IndependenceFact> getMconn() {
            List<IndependenceFact> facts = new ArrayList<>(mconn);
            Collections.sort(facts);
            return facts;
        }
    }
}

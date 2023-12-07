package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.GeneralAndersonDarlingTest;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.test.RowsSettable;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.UniformityTest;
import org.apache.commons.math3.distribution.BinomialDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;
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
    private final List<IndependenceResult> resultsIndep = new ArrayList<>();
    private final List<IndependenceResult> resultsDep = new ArrayList<>();
    private ConditioningSetType setType;
    private boolean parallelized = true;
    private double fractionDependentIndep = Double.NaN;
    private double fractionDependentDep = Double.NaN;
    private double ksPValueIndep = Double.NaN;
    private double ksPValueDep = Double.NaN;
    private double aSquaredStarIndep = Double.NaN;
    private double aSquaredStarDep = Double.NaN;
    private double andersonDarlingPIndep = Double.NaN;
    private double andersonDarlingPDep = Double.NaN;
    private double bernoulliPIndep = Double.NaN;
    private double bernoulliPDep = Double.NaN;
    private double percentResammple = 0.5;
    private int numTestsindep = 0;
    private int numTestsDep = 0;

    /**
     * Constructor. Takes a graph and an independence test over the variables of the graph.
     *
     * @param graph            The graph.
     * @param independenceTest The test over the variables of the graph.
     * @param setType          The type of conditioning sets to use in the Markov check.
     */
    public MarkovCheck(Graph graph, IndependenceTest independenceTest, ConditioningSetType setType) {
        this.graph = GraphUtils.replaceNodes(graph, independenceTest.getVariables());
        this.independenceTest = independenceTest;
        this.setType = setType;
    }

    /**
     * Returns the set of independence facts used in the Markov check, for dsepation and dconnection separately.
     *
     * @param graph The graph.
     * @return The set of independence facts used in the Markov check, for dsepation and dconnection separately.
     */
    @NotNull
    public static AllSubsetsIndependenceFacts getAllSubsetsIndependenceFacts(Graph graph) {
        List<Node> variables = new ArrayList<>(graph.getNodes());

        for (Node node : variables) {
            if (node == null) throw new NullPointerException("Null node in graph.");
        }


        MsepTest msepTest = new MsepTest(graph);

        List<Node> nodes = new ArrayList<>(variables);
        Set<IndependenceFact> msep = new HashSet<>();
        Set<IndependenceFact> mconn = new HashSet<>();

        for (Node x : nodes) {
            List<Node> other = new ArrayList<>(variables);
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
     * the parents of that node. These results are stored in the resultsIndep and resultsDep lists. This should be
     * called before any of the results methods.
     *
     * @see #getResults(boolean)
     */
    public void generateResults() {
        resultsIndep.clear();
        resultsDep.clear();

        if (setType == ConditioningSetType.GLOBAL_MARKOV) {
            AllSubsetsIndependenceFacts result = getAllSubsetsIndependenceFacts(graph);
            generateResultsAllSubsets(result.msep, result.mconn);
            generateResultsAllSubsets(result.msep, result.mconn);
        } else {
            List<Node> variables = independenceTest.getVariables();
            List<Node> nodes = new ArrayList<>(variables);

            List<Node> order = null;

            try {
                order = graph.paths().getValidOrder(graph.getNodes(), true);
            } catch (Exception e) {
                // Leave null. Not an error here. Just means we can't use the ordered local Markov check.
            }

            Set<IndependenceFact> allIndependenceFacts = new HashSet<>();
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
                        allIndependenceFacts.add(new IndependenceFact(x, y, z));
                    }
                }
            }

            generateMseps(new ArrayList<>(allIndependenceFacts), msep, mconn, new MsepTest(graph));
            generateResults(msep, true);
            generateResults(mconn, false);

            this.numTestsindep = msep.size();
            this.numTestsDep = mconn.size();
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

    /**
     * Returns the Anderson-Darling A^2* statistic for the given list of results.
     *
     * @param indep True if for implied independencies, false if for implied dependencies.
     * @return The Anderson-Darling A^2* statistic for the given list of results.
     */
    public double getAndersonDarlingA2Star(boolean indep) {
        if (indep) {
            return aSquaredStarIndep;
        } else {
            return aSquaredStarDep;
        }
    }

    /**
     * Returns the Anderson-Darling p-value for the given list of results.
     *
     * @param indep True if for implied independencies, false if for implied dependencies.
     * @return The Anderson-Darling p-value for the given list of results.
     */
    public double getAndersonDarlingP(boolean indep) {
        if (indep) {
            return andersonDarlingPIndep;
        } else {
            return andersonDarlingPDep;
        }
    }

    /**
     * Returns the Bernoulli p-value for the given list of results.
     *
     * @param indep True if for implied independencies, false if for implied dependencies.
     * @return The Bernoulli p-value for the given list of results.
     */
    public double getBernoulliPValue(boolean indep) {
        if (indep) {
            return bernoulliPIndep;
        } else {
            return bernoulliPDep;
        }
    }

    /**
     * Returns the number of tests for the given list of results.
     *
     * @param indep True if for implied independencies, false if for implied dependencies.
     * @return The number of tests for the given list of results.
     */
    public int getNumTests(boolean indep) {
        if (indep) {
            return numTestsindep;
        } else {
            return numTestsDep;
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

    /**
     * Sets the percentage of all samples to use when resampling for each conditional independence test.
     *
     * @param percentResammple The percentage of all samples to use when resampling for each conditional independence
     *                         test.
     */
    public void setPercentResammple(double percentResammple) {
        this.percentResammple = percentResammple;
    }

    /**
     * Generates the m-separation sets for the given list of independence facts. The m-separation sets are stored in the
     * msep and mconn sets.
     *
     * @param allIndependenceFacts The list of independence facts.
     * @param msep                 The set of m-separation facts.
     * @param mconn                The set of m-connection facts.
     * @param msepTest             The m-separation test.
     */
    private void generateMseps(List<IndependenceFact> allIndependenceFacts, Set<IndependenceFact> msep, Set<IndependenceFact> mconn,
                               MsepTest msepTest) {
        class IndCheckTask implements Callable<Pair<Set<IndependenceFact>, Set<IndependenceFact>>> {
            private final int index;
            private final List<IndependenceFact> facts;
            private final MsepTest msepTest;

            IndCheckTask(int index, List<IndependenceFact> facts, MsepTest test) {
                this.index = index;
                this.facts = facts;
                this.msepTest = test;
            }

            @Override
            public Pair<Set<IndependenceFact>, Set<IndependenceFact>> call() {
                Set<IndependenceFact> msep = new HashSet<>();
                Set<IndependenceFact> mconn = new HashSet<>();

                IndependenceFact fact = facts.get(index);

                Node x = fact.getX();
                Node y = fact.getY();
                Set<Node> z = fact.getZ();

                if (this.msepTest.isMSeparated(x, y, z)) {
                    msep.add(fact);
                } else {
                    mconn.add(fact);
                }

                return new Pair<>(msep, mconn);
            }
        }

        List<Callable<Pair<Set<IndependenceFact>, Set<IndependenceFact>>>> tasks = new ArrayList<>();

        for (int i = 0; i < allIndependenceFacts.size() && !Thread.currentThread().isInterrupted(); i++) {
            IndCheckTask task = new IndCheckTask(i, allIndependenceFacts, msepTest);

            if (!parallelized) {
                Pair<Set<IndependenceFact>, Set<IndependenceFact>> _results = task.call();
                msep.addAll(_results.getFirst());
                mconn.addAll(_results.getSecond());
            } else {
                tasks.add(task);
            }
        }

        if (parallelized) {
            List<Future<Pair<Set<IndependenceFact>, Set<IndependenceFact>>>> theseResults
                    = ForkJoinPool.commonPool().invokeAll(tasks);

            for (Future<Pair<Set<IndependenceFact>, Set<IndependenceFact>>> future : theseResults) {
                try {
                    msep.addAll(future.get().getFirst());
                    mconn.addAll(future.get().getSecond());
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Generates the results for the given set of independence facts.
     *
     * @param facts The set of independence facts.
     * @param msep  True if for implied independencies, false if for implied dependencies.
     */
    private void generateResults(Set<IndependenceFact> facts, boolean msep) {
        class IndCheckTask implements Callable<Pair<Set<IndependenceResult>, Set<IndependenceResult>>> {
            private final int index;
            private final List<IndependenceFact> facts;
            private final IndependenceTest independenceTest;

            IndCheckTask(int index, List<IndependenceFact> facts, IndependenceTest test) {
                this.index = index;
                this.facts = facts;
                this.independenceTest = test;
            }

            @Override
            public Pair<Set<IndependenceResult>, Set<IndependenceResult>> call() {
                Set<IndependenceResult> resultsIndep = new HashSet<>();
                Set<IndependenceResult> resultsDep = new HashSet<>();

                IndependenceFact fact = facts.get(index);

                Node x = fact.getX();
                Node y = fact.getY();
                Set<Node> z = fact.getZ();

                if (independenceTest instanceof RowsSettable) {
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
                        if (msep) {
                            resultsIndep.add(new IndependenceResult(fact, indep, pValue, Double.NaN));
                        } else {
                            resultsDep.add(new IndependenceResult(fact, indep, pValue, Double.NaN));
                        }
                    }
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
                        if (msep) {
                            resultsIndep.add(new IndependenceResult(fact, indep, pValue, Double.NaN));
                        } else {
                            resultsDep.add(new IndependenceResult(fact, indep, pValue, Double.NaN));
                        }
                    }
                }

                return new Pair<>(resultsIndep, resultsDep);
            }
        }

        List<Callable<Pair<Set<IndependenceResult>, Set<IndependenceResult>>>> tasks = new ArrayList<>();

        for (int i = 0; i < facts.size() && !Thread.currentThread().isInterrupted(); i++) {
            IndCheckTask task = new IndCheckTask(i, new ArrayList<>(facts), independenceTest);

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

    /**
     * Calculates the statistics for the given list of results.
     *
     * @param indep True if for implied independencies, false if for implied dependencies.
     */
    private void calcStats(boolean indep) {
        List<IndependenceResult> results = new ArrayList<>(getResultsLocal(indep));

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
        double aSquaredStar = new GeneralAndersonDarlingTest(pValues, new UniformRealDistribution(0, 1)).getASquaredStar();

        if (indep) {
            if (pValues.size() < 2) {
                ksPValueIndep = Double.NaN;
                bernoulliPIndep = Double.NaN;
                aSquaredStarIndep = Double.NaN;
                andersonDarlingPIndep = Double.NaN;
            } else {
                ksPValueIndep = UniformityTest.getPValue(pValues, 0.0, 1.0);
                bernoulliPIndep = getBernoulliP(pValues, independenceTest.getAlpha());
                aSquaredStarIndep = aSquaredStar;
                andersonDarlingPIndep = 1. - new GeneralAndersonDarlingTest(pValues, new UniformRealDistribution(0, 1)).getProbTail(pValues.size(), aSquaredStar);
            }
        } else {
            if (pValues.size() < 2) {
                ksPValueDep = Double.NaN;
                bernoulliPDep = Double.NaN;
                aSquaredStarDep = Double.NaN;
                andersonDarlingPDep = Double.NaN;

            } else {
                ksPValueDep = UniformityTest.getPValue(pValues, 0.0, 1.0);
                bernoulliPDep = getBernoulliP(pValues, independenceTest.getAlpha());
                aSquaredStarDep = aSquaredStar;
                andersonDarlingPDep = 1. - new GeneralAndersonDarlingTest(pValues, new UniformRealDistribution(0, 1)).getProbTail(pValues.size(), aSquaredStar);
            }
        }
    }

    /**
     * Returns a list of row indices for a subsample of the data set.
     *
     * @param v The fraction of the data set to use.
     * @return A list of row indices for a subsample of the data set.
     */
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

    /**
     * Generates the results for the given set of independence facts, for both the local Markov and local Faithfulness
     *
     * @param msep  The set of m-separation facts.
     * @param mconn The set of m-connection facts.
     */
    private void generateResultsAllSubsets(Set<IndependenceFact> msep, Set<IndependenceFact> mconn) {
        generateResults(msep, true);
        generateResults(mconn, false);
    }

    /**
     * Returns a Bernoulli p-value for the hypothesis that the distribution of p-values is not Uniform under the null
     * hypothesis. Values less than alpha imply non-uniform distributions.
     *
     * @param pValues The p-values.
     * @param alpha   The alpha level. Rejections with p-values less than this are considered dependent.
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

    /**
     * Returns the chunk size for parallelization.
     *
     * @param n The number of items to chunk.
     * @return The chunk size for parallelization.
     */
    private int getChunkSize(int n) {
        if (true) return 1;
        int numProcessors = Runtime.getRuntime().availableProcessors();
        int chunk = (int) FastMath.ceil((n / ((double) numProcessors))) / 10;
        if (chunk < 1) chunk = 1;
        return chunk;
    }

    /**
     * Returns the list of results for the given condition.
     *
     * @param indep True if for implied independencies, false if for implied dependencies.
     * @return The list of results for the given condition.
     */
    private List<IndependenceResult> getResultsLocal(boolean indep) {
        if (indep) {
            return this.resultsIndep;
        } else {
            return this.resultsDep;
        }
    }

    /**
     * Stores the set of m-separation facts and the set of m-connection facts for a graph, for the global check.
     */
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
            return new ArrayList<>(msep);
        }

        public List<IndependenceFact> getMconn() {
            return new ArrayList<>(mconn);
        }
    }
}

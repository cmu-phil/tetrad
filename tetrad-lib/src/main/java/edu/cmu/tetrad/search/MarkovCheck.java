package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.UniformityTest;
import org.apache.commons.math3.util.FastMath;
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
 * <p>A "Markov adequacy score" is also given, which simply returns zero if the Markov p-value Uniformity test
 * fails and the fraction of dependent judgments for the local Faithfulness check otherwise. Maximizing this score picks
 * out models for which Markov holds and faithfulness holds to the extend possible; these model should generally have
 * good accuracy scores.</p>
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

    /**
     * Generates all results, for both the local Markov and local Faithfulness checks, for each node in the graph given
     * the parents of that node. These results are stored in the resultsIndep and resultsDep lists.
     *
     * @see #getResults(boolean)
     */
    public void generateResults() {
        resultsIndep.clear();
        resultsDep.clear();

        if (setType == ConditioningSetType.ALL_SUBSETS) {
            AllSubsetsIndependenceFacts result = getAllSubsetsIndependenceFacts(graph);
            generateResultsAllSubsets(true, result.msep, result.mconn);
            generateResultsAllSubsets(false, result.msep, result.mconn);
        } else {
            List<Node> variables = independenceTest.getVariables();
            List<Node> nodes = new ArrayList<>(variables);
            Collections.sort(nodes);

            for (Node x : nodes) {
                Set<Node> z;

                switch (setType) {
                    case PARENTS:
                        z = new HashSet<>(graph.getParents(x));
                        break;
                    case MARKOV_BLANKET:
                        z = GraphUtils.markovBlanket(x, graph);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown separation set type: " + setType);
                }

                Set<Node> msep = new HashSet<>();
                Set<Node> mconn = new HashSet<>();

                List<Node> other = new ArrayList<>(graph.getNodes());
                Collections.sort(other);
                other.removeAll(z);

                for (Node y : other) {
                    if (y == x) continue;
                    if (z.contains(x) || z.contains(y)) continue;
                    if (this.msep.isMSeparated(x, y, z)) {
                        msep.add(y);
                    } else {
                        mconn.add(y);
                    }
                }

                generateResults(true, x, z, msep, mconn);
                generateResults(false, x, z, msep, mconn);
            }
        }

        calcStats(true);
        calcStats(false);
    }

    @NotNull
    public static AllSubsetsIndependenceFacts getAllSubsetsIndependenceFacts(Graph graph) {
        List<Node> variables = new ArrayList<>(graph.getNodes());
        MsepTest msepTest = new MsepTest(graph);

        List<Node> nodes = new ArrayList<>(variables);
        Collections.sort(nodes);

        List<IndependenceFact> msep = new ArrayList<>();
        List<IndependenceFact> mconn = new ArrayList<>();

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

    public static class AllSubsetsIndependenceFacts {
        public final List<IndependenceFact> msep;
        public final List<IndependenceFact> mconn;

        public AllSubsetsIndependenceFacts(List<IndependenceFact> msep, List<IndependenceFact> mconn) {
            this.msep = msep;
            this.mconn = mconn;
        }

        public String toString() {
            StringBuilder builder = new StringBuilder("All subsets independence facts:\n");

            builder.append("\n");

            builder.append("M-separated:");

            for (IndependenceFact fact : msep) {
                builder.append(fact).append("\n");
            }

            builder.append('\n');
            builder.append("M-connected:");

            for (IndependenceFact fact : mconn) {
                builder.append(fact).append("\n");
            }

            builder.append('\n');

            return builder.toString();
        }
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
     * Returns the Markov Adequacy Score for the graph. This is zero if the p-value of the KS test of Uniformity is less
     * than alpha, and the fraction of dependent pairs otherwise. This is only for continuous Gaussian data, as it
     * hard-codes the Fisher Z test for the local Markov and Faithfulness check.
     *
     * @param alpha The alpha level for the KS test of Uniformity. An alpha level greater than this will be considered
     *              uniform.
     * @return The Markov Adequacy Score for this graph given the data.
     */
    public double getMarkovAdequacyScore(double alpha) {
        if (getKsPValue(true) > alpha) {
            return getFractionDependent(false);
        } else {
            return 0.0;
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

    private void generateResults(boolean indep, Node x, Set<Node> z, Set<Node> msep, Set<Node> mconn) {
        List<IndependenceFact> facts = new ArrayList<>();

        // Listing all facts before checking any (in preparation for parallelization).
        if (indep) {
            for (Node y : msep) {
                if (z.contains(y)) continue;
                facts.add(new IndependenceFact(x, y, z));
            }
        } else {
            for (Node y : mconn) {
                if (z.contains(y)) continue;
                facts.add(new IndependenceFact(x, y, z));
            }
        }

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

        List<Callable<List<IndependenceResult>>> tasks = new ArrayList<>();

        int chunkSize = getChunkSize(facts.size());

        for (int i = 0; i < facts.size() && !Thread.currentThread().isInterrupted(); i += chunkSize) {
            IndCheckTask task = new IndCheckTask(i, min(facts.size(), i + chunkSize), facts, independenceTest);

            if (!parallelized) {
                List<IndependenceResult> _results = task.call();
                getResultsLocal(indep).addAll(_results);
            } else {
                tasks.add(task);
            }
        }

        if (parallelized) {
            List<Future<List<IndependenceResult>>> theseResults = ForkJoinPool.commonPool().invokeAll(tasks);

            for (Future<List<IndependenceResult>> future : theseResults) {
                try {
                    getResultsLocal(indep).addAll(future.get());
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void generateResultsAllSubsets(boolean indep, List<IndependenceFact> msep, List<IndependenceFact> mconn) {
        List<IndependenceFact> facts = indep ? msep : mconn;

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

        List<Callable<List<IndependenceResult>>> tasks = new ArrayList<>();

        int chunkSize = getChunkSize(facts.size());

        for (int i = 0; i < facts.size() && !Thread.currentThread().isInterrupted(); i += chunkSize) {
            IndCheckTask task = new IndCheckTask(i, min(facts.size(), i + chunkSize), facts, independenceTest);

            if (!parallelized) {
                List<IndependenceResult> _results = task.call();
                getResultsLocal(indep).addAll(_results);
            } else {
                tasks.add(task);
            }
        }

        if (parallelized) {
            List<Future<List<IndependenceResult>>> theseResults = ForkJoinPool.commonPool().invokeAll(tasks);

            for (Future<List<IndependenceResult>> future : theseResults) {
                try {
                    getResultsLocal(indep).addAll(future.get());
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void calcStats(boolean indep) {
        List<IndependenceResult> results = getResultsLocal(indep);

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
            } else {
                ksPValueIndep = UniformityTest.getPValue(pValues);
            }
        } else {
            if (pValues.size() < 2) {
                ksPValueDep = Double.NaN;
            } else {
                ksPValueDep = UniformityTest.getPValue(pValues);
            }
        }
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


    /**
     * The type of conditioning set to use for the Markov check. The default is PARENTS, which uses the parents of the
     * target variable to predict the separation set. DAG_MB uses the Markov blanket of the target variable in a DAG
     * setting, and PAG_MB uses a Markov blanket of the target variable in a PAG setting.
     */
    public enum ConditioningSetType {
        PARENTS, MARKOV_BLANKET, ALL_SUBSETS
    }
}

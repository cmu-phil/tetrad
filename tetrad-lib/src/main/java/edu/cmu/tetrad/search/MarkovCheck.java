package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.UniformityTest;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

import static org.apache.commons.math3.util.FastMath.min;

public class MarkovCheck {
    private final Graph graph;
    private final IndependenceTest independenceTest;
    private final MsepTest msep;
    private final List<IndependenceResult> resultsIndep = new ArrayList<>();
    private final List<IndependenceResult> resultsDep = new ArrayList<>();
    private boolean parallelized = false;
    private double fractionDependentIndep = Double.NaN;
    private double fractionDependentDep = Double.NaN;
    private double ksPValueIndep = Double.NaN;
    private double ksPValueDep = Double.NaN;

    public MarkovCheck(Graph graph, IndependenceTest independenceTest) {
        this.graph = GraphUtils.replaceNodes(graph, independenceTest.getVariables());
        this.independenceTest = independenceTest;
        this.msep = new MsepTest(this.graph);
    }

    public void generateResults() {
        resultsIndep.clear();
        resultsDep.clear();

        for (Node x : independenceTest.getVariables()) {
            Set<Node> z = new HashSet<>(graph.getParents(x));
            Set<Node> msep = new HashSet<>();
            Set<Node> mconn = new HashSet<>();

            List<Node> other = graph.getNodes();
            other.removeAll(z);

            for (Node y : other) {
                if (y == x) continue;
                if (this.msep.isMSeparated(x, y, z)) {
                    msep.add(y);
                } else {
                    mconn.add(y);
                }
            }

            generateResults(true, x, z, msep, mconn);
            generateResults(false, x, z, msep, mconn);
        }

        calcStats(true);
        calcStats(false);
    }

    public void setParallelized(boolean parallelized) {
        this.parallelized = parallelized;
    }

    public List<IndependenceResult> getResults(boolean indep) {
        if (indep) {
            return new ArrayList<>(this.resultsIndep);
        } else {
            return new ArrayList<>(this.resultsDep);
        }
    }

    public List<Double> getPValues(List<IndependenceResult> results) {
        List<Double> pValues = new ArrayList<>();

        for (IndependenceResult result : results) {
            pValues.add(result.getPValue());
        }

        return pValues;
    }

    public double getFractionDependent(boolean indep) {
        if (indep) {
            return fractionDependentIndep;
        } else {
            return fractionDependentDep;
        }
    }

    public double getKsPValue(boolean indep) {
        if (indep) {
            return ksPValueIndep;
        } else {
            return ksPValueDep;
        }
    }

    public List<Node> getVariables() {
        return independenceTest.getVariables();
    }

    public Node getVariable(String name) {
        return independenceTest.getVariable(name);
    }

    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    private void generateResults(boolean indep, Node x, Set<Node> z, Set<Node> msep, Set<Node> mconn) {
        List<IndependenceFact> facts = new ArrayList<>();

        // Listing all facts before checking any (in preparation for parallelization).
        if (indep) {
            for (Node y : msep) {
                facts.add(new IndependenceFact(x, y, z));
            }
        } else {
            for (Node y : mconn) {
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
            IndCheckTask task = new IndCheckTask(i, min(facts.size(), i + chunkSize),
                    facts, independenceTest);

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
}

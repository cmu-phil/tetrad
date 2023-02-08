package edu.pitt.dbmi.algo.bayesian.constraint.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.GraphSearch;
import edu.cmu.tetrad.search.IndTestProbabilistic;
import edu.cmu.tetrad.search.Rfci;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.GraphTools;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

/**
 *
 * Jan 29, 2023 4:10:52 PM
 *
 * @author Kevin V. Bui (kvb2univpitt@gmail.com)
 */
public class PagSamplingRfci implements GraphSearch {

    // Rfci parameters
    private int depth = -1;
    private int maxPathLength = -1;
    // IndTestProbabilistic parameters
    private boolean threshold;
    private double cutoff = 0.5;
    private double priorEquivalentSampleSize = 10;

    private int numRandomizedSearchModels = 10;

    /**
     * True if verbose output should be printed.
     */
    private boolean verbose;

    /**
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    private final DataSet dataSet;

    public PagSamplingRfci(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    @Override
    public Graph search() {
        return GraphTools.createHighEdgeProbabilityGraph(runSearches());
    }

    List<Callable<Graph>> createSearchTasks(int numOfTasks) {
        List<Callable<Graph>> callableTasks = new LinkedList<>();

        for (int i = 0; i < numOfTasks; i++) {
            callableTasks.add(new RfciSearchTask());
        }

        return callableTasks;
    }

    private List<Graph> runSearches() {
        List<Graph> graphs = new LinkedList<>();

        ExecutorService pool = Executors.newFixedThreadPool(numRandomizedSearchModels);
        try {
            while (graphs.size() < numRandomizedSearchModels) {
                List<Callable<Graph>> callableTasks = createSearchTasks(numRandomizedSearchModels - graphs.size());
                List<Future<Graph>> completedTasks = pool.invokeAll(callableTasks);
                for (Future<Graph> completedTask : completedTasks) {
                    try {
                        Graph graph = completedTask.get();
                        if (graph != null && SearchGraphUtils.isLegalPag(graph).isLegalPag()) {
                            graphs.add(graph);
                        }
                    } catch (ExecutionException | InterruptedException exception) {
                        exception.printStackTrace(System.err);
                    }
                }
            }
        } catch (InterruptedException exception) {
            exception.printStackTrace(System.err);
        } finally {
            shutdownAndAwaitTermination(pool);
        }

        return graphs;
    }

    /**
     * Call shutdown to reject incoming tasks, and then calling shutdownNow, if
     * necessary, to cancel any lingering tasks.
     */
    private void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("Pool did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getMaxPathLength() {
        return maxPathLength;
    }

    public void setMaxPathLength(int maxPathLength) {
        this.maxPathLength = maxPathLength;
    }

    public int getNumRandomizedSearchModels() {
        return numRandomizedSearchModels;
    }

    public void setNumRandomizedSearchModels(int numRandomizedSearchModels) {
        this.numRandomizedSearchModels = numRandomizedSearchModels;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public boolean isThreshold() {
        return threshold;
    }

    public void setThreshold(boolean threshold) {
        this.threshold = threshold;
    }

    public double getCutoff() {
        return cutoff;
    }

    public void setCutoff(double cutoff) {
        this.cutoff = cutoff;
    }

    public double getPriorEquivalentSampleSize() {
        return priorEquivalentSampleSize;
    }

    public void setPriorEquivalentSampleSize(double priorEquivalentSampleSize) {
        this.priorEquivalentSampleSize = priorEquivalentSampleSize;
    }

    private class RfciSearchTask implements Callable<Graph> {

        public RfciSearchTask() {
        }

        @Override
        public Graph call() throws Exception {
            try {
                IndTestProbabilistic independenceTest = new IndTestProbabilistic(dataSet);
                independenceTest.setThreshold(threshold);
                independenceTest.setCutoff(cutoff);
                independenceTest.setPriorEquivalentSampleSize(priorEquivalentSampleSize);

                Rfci rfci = new Rfci(independenceTest);
                rfci.setDepth(depth);
                rfci.setMaxPathLength(maxPathLength);
                rfci.setVerbose(verbose);

                return rfci.search();
            } catch (Exception exception) {
                exception.printStackTrace(System.err);

                return null;
            }
        }

    }

}

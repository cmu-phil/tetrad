package edu.pitt.dbmi.algo.bayesian.constraint.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.GraphSearch;
import edu.cmu.tetrad.search.IndTestProbabilistic;
import edu.cmu.tetrad.search.Rfci;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.FrequencyProbability;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    private boolean completeRuleSetUsed = true;

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
        return FrequencyProbability.createHighEdgeProbabilityGraph(runSearches());
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

        ExecutorService executorService = Executors.newFixedThreadPool(numRandomizedSearchModels);
        try {
            while (graphs.size() < numRandomizedSearchModels) {
                List<Callable<Graph>> callableTasks = createSearchTasks(numRandomizedSearchModels - graphs.size());
                List<Future<Graph>> futures = executorService.invokeAll(callableTasks);
                futures.forEach(e -> {
                    try {
                        Graph graph = e.get();
                        if (SearchGraphUtils.isLegalPag(graph).isLegalPag()) {
                            graphs.add(graph);
                        }
                    } catch (ExecutionException | InterruptedException exception) {
                        exception.printStackTrace(System.err);
                    }
                });
            }
        } catch (InterruptedException exception) {
            exception.printStackTrace(System.err);
        } finally {
            executorService.shutdown();
        }

        return graphs;
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

    public boolean isCompleteRuleSetUsed() {
        return completeRuleSetUsed;
    }

    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
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
            IndTestProbabilistic independenceTest = new IndTestProbabilistic(dataSet);
            independenceTest.setThreshold(threshold);
            independenceTest.setCutoff(cutoff);
            independenceTest.setPriorEquivalentSampleSize(priorEquivalentSampleSize);

            Rfci rfci = new Rfci(independenceTest);
            rfci.setKnowledge(knowledge);
            rfci.setDepth(depth);
            rfci.setMaxPathLength(maxPathLength);
            rfci.setCompleteRuleSetUsed(completeRuleSetUsed);
            rfci.setVerbose(verbose);

            return rfci.search();
        }

    }

}

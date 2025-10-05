///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.pitt.dbmi.algo.bayesian.constraint.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.Rfci;
import edu.cmu.tetrad.search.test.IndTestProbabilistic;
import edu.cmu.tetrad.search.utils.GraphLegalityCheck;
import edu.cmu.tetrad.util.GraphSampling;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Jan 29, 2023 4:10:52 PM
 *
 * @author Kevin V. Bui (kvb2univpitt@gmail.com)
 * @version $Id: $Id
 */
public class PagSamplingRfci implements IGraphSearch {

    private final int NUM_THREADS = 10;
    private final DataSet dataSet;
    // PagSamplingRfci
    private int numRandomizedSearchModels = 10;
    private boolean verbose = false;
    // Rfci parameters
    private int depth = -1;
    private int maxDiscriminatingPathLength = -1;
    // IndTestProbabilistic parameters
    private boolean threshold = true;
    private double cutoff = 0.5;
    private double priorEquivalentSampleSize = 10;
    /**
     * The background knowledge.
     */
    private Knowledge knowledge;

    /**
     * Constructor.
     *
     * @param dataSet the data set.
     */
    public PagSamplingRfci(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Search for a PAG.
     */
    @Override
    public Graph search() {
        List<Graph> graphs = runSearches();

        return GraphSampling.createGraphWithHighProbabilityEdges(graphs);
    }

    /**
     * Create tasks for parallel execution.
     *
     * @param numOfTasks the number of tasks.
     * @return a list of callable tasks.
     */
    List<Callable<Graph>> createTasks(int numOfTasks) {
        List<Callable<Graph>> callableTasks = new LinkedList<>();

        for (int i = 0; i < numOfTasks; i++) {
            callableTasks.add(new RfciSearchTask());
        }

        return callableTasks;
    }

    private List<Graph> runSearches() {
        List<Graph> graphs = new LinkedList<>();

        ForkJoinPool pool = new ForkJoinPool(NUM_THREADS);
        try {
            while (graphs.size() < numRandomizedSearchModels && !Thread.currentThread().isInterrupted()) {
                List<Callable<Graph>> callableTasks = createTasks(numRandomizedSearchModels - graphs.size());
                List<Future<Graph>> completedTasks = pool.invokeAll(callableTasks);
                for (Future<Graph> completedTask : completedTasks) {
                    try {
                        Graph graph = completedTask.get();
                        if (graph != null && GraphLegalityCheck.isLegalPag(graph, new HashSet<>()).isLegalPag()) {
                            graphs.add(graph);
                        }
                    } catch (ExecutionException exception) {
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
     * Call shutdown to reject incoming tasks, and then calling shutdownNow, if necessary, to cancel any lingering
     * tasks.
     */
    private void shutdownAndAwaitTermination(ForkJoinPool pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
//                    System.err.println("Pool did not terminate");
                    throw new RuntimeException("Pool did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted");
        }
    }

    /**
     * Set the number of randomized search models.
     *
     * @param numRandomizedSearchModels the number of randomized search models.
     */
    public void setNumRandomizedSearchModels(int numRandomizedSearchModels) {
        this.numRandomizedSearchModels = numRandomizedSearchModels;
    }

    /**
     * Set the verbose flag.
     *
     * @param verbose the verbose flag.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Set the depth.
     *
     * @param depth the depth.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets the maximum length of any discriminating path.
     *
     * @param maxDiscriminatingPathLength the maximum length of any discriminating path, or -1 if unlimited.
     */
    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        if (maxDiscriminatingPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxDiscriminatingPathLength);
        }

        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
    }

    /**
     * Set the threshold.
     *
     * @param threshold the threshold.
     */
    public void setThreshold(boolean threshold) {
        this.threshold = threshold;
    }

    /**
     * Set the cutoff.
     *
     * @param cutoff the cutoff.
     */
    public void setCutoff(double cutoff) {
        this.cutoff = cutoff;
    }

    /**
     * Set the prior equivalent sample size.
     *
     * @param priorEquivalentSampleSize the prior equivalent sample size.
     */
    public void setPriorEquivalentSampleSize(double priorEquivalentSampleSize) {
        this.priorEquivalentSampleSize = priorEquivalentSampleSize;
    }

    /**
     * Set the knowledge.
     *
     * @param knowledge the knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
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
                independenceTest.setVerbose(verbose);

                Rfci rfci = new Rfci(independenceTest);
                if (knowledge != null) {
                    rfci.setKnowledge(knowledge);
                }
                rfci.setDepth(depth);
                rfci.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
                rfci.setVerbose(verbose);

                return rfci.search();
            } catch (Exception exception) {
                exception.printStackTrace(System.err);

                return null;
            }
        }

    }

}


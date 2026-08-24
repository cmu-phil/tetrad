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

package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.annotation.Bootstrapping;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.util.*;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.SynchronizedRandomGenerator;
import org.apache.commons.math3.random.Well44497b;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import static edu.cmu.tetrad.data.DataSampling.createDataSample;

/**
 * This is a base class for bootstrap algorithms.
 * <p>
 * Mar 4, 2024 5:05:28 PM
 *
 * @author Kevin V. Bui (kvb2univpitt@gmail.com)
 */
@Bootstrapping
public abstract class AbstractBootstrapAlgorithm implements Algorithm, ReturnsBootstrapGraphs {

    // 2025-6-10 Refactored this code so that bootstrap datasets are not all calculated up front but are calculated
    // on the fly as they are needed for running bootstrap samples. This was needed for a use case where a client
    // wanted to run 50,000 bootstraps. Note that with this change we are allowing the system to decide when
    // to garbage collect rather than calling System.gc() every run, which speeds things up. jdramsey

    /**
     * The bootstrap graphs. Protected so that subclasses that override the resampling loop (e.g., the multi-dataset
     * bootstrap base class) can populate the same list that {@link #getBootstrapGraphs()} returns.
     */
    protected transient List<Graph> bootstrapGraphs = new LinkedList<>();
    /**
     * Bootstrap count, printed out to track bootstraps.
     */
    private int count = 0;

    /**
     * This is a base class for bootstrap algorithms.
     */
    protected AbstractBootstrapAlgorithm() {
    }

    private Graph runSingleBootstrapSearch(RandomGenerator randomGenerator, int[] selectedColumns, DataModel dataModel, Parameters parameters)
            throws InterruptedException {
        TetradLogger.getInstance().log("Bootstrap count = " + ++count);
        double r = parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE);
        return runSearch(createDataSample((DataSet) dataModel, randomGenerator, selectedColumns, parameters, r), parameters);
    }

    protected abstract Graph runSearch(DataModel dataSet, Parameters parameters) throws InterruptedException;

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (dataModel instanceof CovarianceMatrix) {
            if (this instanceof TakesCovarianceMatrix) {
                return runSearch(dataModel, parameters);
            } else {
                throw new IllegalArgumentException("This search cannot take a covariance matrix as input.");
            }
        } else if (parameters.getInt(Params.NUMBER_RESAMPLING) == 0) {
            Graph graph = runSearch(dataModel, parameters);

            if (parameters.getInt(Params.TIME_LAG) > 0) {
                LayoutUtil.layoutByKnowledgeIndices(graph);
            }

            return graph;
        }

        // Time-lag data sets must be lagged ONCE from the original row order, before any resampling; see
        // BootstrapTimeLag. The wrapper's core then runs with timeLag = 0 on lagged rows, and the wrapper's
        // knowledge is temporarily the lagged knowledge, restored in the finally block below.
        BootstrapTimeLag.Prepared prepared = BootstrapTimeLag.prepare(this, Collections.singletonList(dataModel), parameters);
        final boolean lagged = prepared.dataSets().get(0) != dataModel;
        final DataModel bootData = prepared.dataSets().get(0);
        final Parameters bootParams = prepared.parameters();

        try {
            return bootstrapSearch(bootData, bootParams, lagged);
        } finally {
            prepared.restore().run();
        }
    }

    private Graph bootstrapSearch(DataModel dataModel, Parameters parameters, boolean lagged) throws InterruptedException {
        // create a new random generator if a seed is given
        long seed = parameters.getLong(Params.SEED);
        RandomGenerator randomGenerator = (seed < 0) ? null : new SynchronizedRandomGenerator(new Well44497b(seed));

        Graph graph;
        Graph medianMemberGraph = null;
        if (Thread.currentThread().isInterrupted()) {
            graph = new EdgeListGraph();
        } else {
            List<Callable<Graph>> tasks = new LinkedList<>();

            // select all data columns
            int[] selectedColumns = IntStream.range(0, ((DataSet) dataModel).getNumColumns()).toArray();

            this.count = 0;

            for (int i = 0; i < parameters.getInt(Params.NUMBER_RESAMPLING) && !Thread.currentThread().isInterrupted(); i++) {
                tasks.add(() -> runSingleBootstrapSearch(randomGenerator, selectedColumns, dataModel, parameters));
            }

            if (parameters.getBoolean(Params.ADD_ORIGINAL_DATASET) || parameters.getInt(Params.NUMBER_RESAMPLING) == 0) {
                tasks.add(() -> runSearch(dataModel, parameters));
            }

            TaskRunner<Graph> taskRunner = new TaskRunner<>(parameters.getInt(Params.BOOTSTRAPPING_NUM_THREADS));
            List<Graph> graphs = taskRunner.run(tasks);

            System.gc();

            if (graphs.isEmpty()) {
                graph = new EdgeListGraph();
            } else {
                if (parameters.getInt(Params.NUMBER_RESAMPLING) > 0) {
                    if (parameters.getBoolean(Params.SAVE_BOOTSTRAP_GRAPHS)) {
                        this.bootstrapGraphs.clear();
                        this.bootstrapGraphs.addAll(graphs);
                    }
                    graph = GraphSampling.createGraphWithHighProbabilityEdges(graphs);

                    // Median member graph, added 2026-8-13: the single member graph closest to the ensemble
                    // edge frequencies. Unlike the composite - whose edges are drawn from different member
                    // graphs and taken together are generally NOT a legal member of the algorithm's output
                    // class - this graph is one the algorithm actually produced, so it inherits the algorithm's
                    // legality guarantee. It is computed unconditionally (it cannot be recomputed later from the
                    // composite alone) and stored as the ancillary "medianGraph" on the sampling graph, so the
                    // Ensemble Display menu can switch to it regardless of the ensemble used at search time.
                    medianMemberGraph = GraphSampling.selectMedianEnsembleGraph(graphs);
                } else {
                    graph = graphs.getFirst();
                }
            }
        }

        graph = GraphUtils.fixDirections(graph);

        // Fix the returned graph so that it is a proper display graph. Note that we need to set the "sample graph"
        // for this graph to the above graph so that ensemble choices can be done using the right click menu in the
        // interface. A "proper display graph" doesn't include the "..." edges that represent non-adjacency and
        // are only needed for the sampling graph because they contain bootstrapping information. We return a
        // graph without these edges so that, e.g., accuracies or other graph operations can be performed on the
        // graph that is displayed in the interface. jdramsey 2025-6-22
        ((EdgeListGraph) graph).setAncillaryGraph("samplingGraph", graph);

        if (medianMemberGraph != null) {
            ((EdgeListGraph) graph).setAncillaryGraph("medianGraph", medianMemberGraph);
            ((EdgeListGraph) medianMemberGraph).setAncillaryGraph("samplingGraph", graph);

            // As of 2026-8-13 the median member graph is the default initial display for every bootstrap
            // search: unlike the composite it is a graph the algorithm actually produced, hence a legal
            // member of its output class. The composite views (Preserved / Highest / Majority / Threshold)
            // remain available from the Ensemble Display menu via the ancillary sampling graph.
            Graph median = GraphUtils.fixDirections(medianMemberGraph);
            if (lagged) LayoutUtil.layoutByKnowledgeIndices(median);
            return median;
        }

        Graph displayGraph = GraphSampling.createDisplayGraph(graph, ResamplingEdgeEnsemble.Highest);
        ((EdgeListGraph) displayGraph).setAncillaryGraph("samplingGraph", graph);

        // Make double sure that all directable edges point to the right before returning this graph.
        // jdramsey 2025-6-21
        graph = GraphUtils.fixDirections(displayGraph);
        if (lagged) LayoutUtil.layoutByKnowledgeIndices(graph);

        return graph;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the bootstrap graphs.
     */
    @Override
    public List<Graph> getBootstrapGraphs() {
        return Collections.unmodifiableList(bootstrapGraphs);
    }

}


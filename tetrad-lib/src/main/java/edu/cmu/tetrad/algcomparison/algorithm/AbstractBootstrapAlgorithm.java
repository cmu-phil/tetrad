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
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.PooledIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.PooledScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.util.*;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.SynchronizedRandomGenerator;
import org.apache.commons.math3.random.Well44497b;

import java.util.ArrayList;
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
        // A DataModelList is the request to POOL its data sets (IMaGES-style) into one search; see searchPooled.
        if (dataModel instanceof DataModelList list) {
            if (list.size() == 1) return search(list.getFirst(), parameters);
            return searchPooled(new ArrayList<>(list), parameters);
        }

        if (dataModel instanceof CovarianceMatrix) {
            if (this instanceof TakesCovarianceMatrix) {
                return runSearch(dataModel, parameters);
            } else {
                throw new IllegalArgumentException("This search cannot take a covariance matrix as input.");
            }
        } else if (parameters.getInt(Params.NUMBER_RESAMPLING) == 0) {
            Graph graph = runSearch(dataModel, parameters);

            if (parameters.getInt(Params.TIME_LAG) > 0 || LayoutUtil.isLaggedGraph(graph)) {
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
        // select all data columns
        int[] selectedColumns = IntStream.range(0, ((DataSet) dataModel).getNumColumns()).toArray();
        return bootstrapSearch(rg -> runSingleBootstrapSearch(rg, selectedColumns, dataModel, parameters),
                () -> runSearch(dataModel, parameters), parameters, lagged);
    }

    /**
     * One bootstrap replicate, given the random generator shared by all replicates.
     */
    private interface Replicate {
        Graph run(RandomGenerator randomGenerator) throws InterruptedException;
    }

    /**
     * The bootstrap loop and aggregation, shared by the single-data-set and pooled paths.
     */
    private Graph bootstrapSearch(Replicate replicate, Callable<Graph> original, Parameters parameters, boolean lagged)
            throws InterruptedException {
        // create a new random generator if a seed is given
        long seed = parameters.getLong(Params.SEED);
        RandomGenerator randomGenerator = (seed < 0) ? null : new SynchronizedRandomGenerator(new Well44497b(seed));

        Graph graph;
        Graph medianMemberGraph = null;
        if (Thread.currentThread().isInterrupted()) {
            graph = new EdgeListGraph();
        } else {
            List<Callable<Graph>> tasks = new LinkedList<>();

            this.count = 0;

            for (int i = 0; i < parameters.getInt(Params.NUMBER_RESAMPLING) && !Thread.currentThread().isInterrupted(); i++) {
                tasks.add(() -> replicate.run(randomGenerator));
            }

            if (parameters.getBoolean(Params.ADD_ORIGINAL_DATASET) || parameters.getInt(Params.NUMBER_RESAMPLING) == 0) {
                tasks.add(original);
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
            if (lagged || LayoutUtil.isLaggedGraph(median)) LayoutUtil.layoutByKnowledgeIndices(median);
            return median;
        }

        Graph displayGraph = GraphSampling.createDisplayGraph(graph, ResamplingEdgeEnsemble.Highest);
        ((EdgeListGraph) displayGraph).setAncillaryGraph("samplingGraph", graph);

        // Make double sure that all directable edges point to the right before returning this graph.
        // jdramsey 2025-6-21
        graph = GraphUtils.fixDirections(displayGraph);
        if (lagged || LayoutUtil.isLaggedGraph(graph)) LayoutUtil.layoutByKnowledgeIndices(graph);

        return graph;
    }

    /**
     * Pools several data sets into one search, IMaGES-style. This is the general form of IMaGES: rather than a
     * separate algorithm, pooling is done by temporarily replacing the algorithm's score wrapper with a
     * {@link PooledScoreWrapper} (an IMaGES sum of the inner score over the data sets) and/or its independence
     * wrapper with a {@link PooledIndependenceWrapper} (Fisher-combined p-values), then running the algorithm's
     * ordinary core once. So BOSS + any score on a list of data sets IS IMaGES with that score, and PC + any test
     * is a pooled PC, with no per-combination wrapper classes.
     * <p>
     * Requirements: all data sets have the same variables (by name). Time lag, if requested, is applied to each data
     * set separately from its own row order before pooling (so region or subject seams never become fake
     * transitions). Bootstrapping resamples rows WITHIN each data set for every replicate, so each data set
     * contributes its own rows to every replicate and rows never cross data sets; the pooled wrappers pick up each
     * replicate's data sets through a thread-local registration.
     * <p>
     * Caveat: the algorithm's core still receives the first data set as its nominal data model. Algorithms whose core
     * consults the data beyond the score and test (e.g. for residuals or skewness) see only that first data set;
     * pooling is meaningful for score- and test-based algorithms.
     *
     * @param dataSets   the data sets to pool (two or more).
     * @param parameters the parameters.
     * @return the graph.
     * @throws InterruptedException if interrupted.
     */
    private Graph searchPooled(List<DataModel> dataSets, Parameters parameters) throws InterruptedException {
        if (!(this instanceof TakesScoreWrapper) && !(this instanceof TakesIndependenceWrapper)) {
            throw new IllegalArgumentException("Pooling data sets requires a score- or test-based algorithm.");
        }

        List<String> names = dataSets.getFirst().getVariableNames();
        for (DataModel dataModel : dataSets) {
            if (!dataModel.getVariableNames().equals(names)) {
                throw new IllegalArgumentException("All pooled data sets must have the same variables in the same "
                                                   + "order; " + dataModel.getName() + " differs from "
                                                   + dataSets.getFirst().getName() + ".");
            }
        }

        boolean bootstrap = parameters.getInt(Params.NUMBER_RESAMPLING) > 0;

        if (bootstrap) {
            for (DataModel dataModel : dataSets) {
                if (!(dataModel instanceof DataSet)) {
                    throw new IllegalArgumentException("Sorry, you need tabular datasets in order to do bootstrapping.");
                }
            }
        }

        // Lag each data set separately (no-op unless timeLag > 0); restores the wrapper's knowledge afterwards.
        BootstrapTimeLag.Prepared prepared = BootstrapTimeLag.prepare(this, dataSets, parameters);
        final List<DataModel> pooled = prepared.dataSets();
        final Parameters params = prepared.parameters();
        final boolean lagged = pooled.get(0) != dataSets.get(0);

        ScoreWrapper originalScore = null;
        PooledScoreWrapper pooledScore = null;
        IndependenceWrapper originalTest = null;
        PooledIndependenceWrapper pooledTest = null;

        try {
            if (this instanceof TakesScoreWrapper takesScore && takesScore.getScoreWrapper() != null
                && !(takesScore.getScoreWrapper() instanceof PooledScoreWrapper)) {
                originalScore = takesScore.getScoreWrapper();
                pooledScore = new PooledScoreWrapper(originalScore, pooled);
                takesScore.setScoreWrapper(pooledScore);
            }

            if (this instanceof TakesIndependenceWrapper takesTest && takesTest.getIndependenceWrapper() != null
                && !(takesTest.getIndependenceWrapper() instanceof PooledIndependenceWrapper)) {
                originalTest = takesTest.getIndependenceWrapper();
                pooledTest = new PooledIndependenceWrapper(originalTest, pooled);
                takesTest.setIndependenceWrapper(pooledTest);
            }

            final DataModel nominal = pooled.get(0);

            if (!bootstrap) {
                Graph graph = runSearch(nominal, params);
                if (lagged || LayoutUtil.isLaggedGraph(graph)) LayoutUtil.layoutByKnowledgeIndices(graph);
                return graph;
            }

            final PooledScoreWrapper fScore = pooledScore;
            final PooledIndependenceWrapper fTest = pooledTest;
            final double r = params.getDouble(Params.PERCENT_RESAMPLE_SIZE);

            Replicate replicate = rg -> {
                TetradLogger.getInstance().log("Bootstrap count = " + ++this.count);
                List<DataModel> sample = new ArrayList<>(pooled.size());
                for (DataModel dataModel : pooled) {
                    DataSet dataSet = (DataSet) dataModel;
                    int[] cols = IntStream.range(0, dataSet.getNumColumns()).toArray();
                    DataSet resampled = createDataSample(dataSet, rg, cols, params, r);
                    if (dataSet.getName() != null) resampled.setName(dataSet.getName());
                    sample.add(resampled);
                }
                try {
                    if (fScore != null) fScore.setThreadDataSets(sample);
                    if (fTest != null) fTest.setThreadDataSets(sample);
                    return runSearch(sample.get(0), params);
                } finally {
                    if (fScore != null) fScore.setThreadDataSets(null);
                    if (fTest != null) fTest.setThreadDataSets(null);
                }
            };

            return bootstrapSearch(replicate, () -> runSearch(nominal, params), params, lagged);
        } finally {
            if (originalScore != null) ((TakesScoreWrapper) this).setScoreWrapper(originalScore);
            if (originalTest != null) ((TakesIndependenceWrapper) this).setIndependenceWrapper(originalTest);
            prepared.restore().run();
        }
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


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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.GraphSampling;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TaskRunner;
import edu.cmu.tetrad.util.TetradLogger;
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
 * A base class for bootstrapping multi-dataset algorithms (such as IMaGES), parallel to
 * {@link AbstractBootstrapAlgorithm} for single-dataset algorithms. Each bootstrap iteration resamples rows WITHIN
 * each of the given data sets separately (each data set is resampled to {@code percentResampleSize} percent of its
 * own rows, with or without replacement per the usual bootstrapping parameters) and runs the subclass's search on the
 * resampled list; the resulting graphs are aggregated into an edge-frequency graph exactly as in the single-dataset
 * case. Resampling within data sets, rather than pooling them, preserves the multi-dataset structure the algorithm
 * exists for: each subject or session contributes the same number of rows to every bootstrap sample, and rows are
 * never exchanged across data sets.
 * <p>
 * Subclasses implement {@link #runSearch(List, Parameters)}, the non-resampling core; the inherited single-dataset
 * hook {@link #runSearch(DataModel, Parameters)} delegates to it with a singleton list, so single-dataset calls (via
 * {@link AbstractBootstrapAlgorithm#search(DataModel, Parameters)}) get bootstrapping as well.
 * <p>
 * Design decisions, deliberate and contestable: (1) Rows are resampled within data sets; an alternative for
 * multi-subject designs is to resample at the DATA SET level (subjects with replacement), which targets
 * between-subject variability instead of within-subject sampling error. That would be a different, additional
 * resampling mode, not a replacement for this one, and is not implemented here. (2) Row resampling treats rows as
 * exchangeable within each data set; for serially dependent rows (see the SERIAL_DEPENDENCE data audit finding) the
 * bootstrap distribution understates uncertainty, the same caveat that applies to row bootstrapping of any single
 * time-series data set.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public abstract class AbstractMultiBootstrapAlgorithm extends AbstractBootstrapAlgorithm {

    /**
     * Bootstrap count, printed out to track bootstraps.
     */
    private int count = 0;

    /**
     * This is a base class for bootstrapping multi-dataset algorithms.
     */
    protected AbstractMultiBootstrapAlgorithm() {
    }

    /**
     * The non-resampling core of the multi-dataset search. Implementations should not consult
     * {@code numberResampling}; resampling is handled here in {@link #search(List, Parameters)}.
     *
     * @param dataSets   the data sets to search over.
     * @param parameters the parameters.
     * @return the graph.
     * @throws InterruptedException if the search is interrupted.
     */
    protected abstract Graph runSearch(List<DataModel> dataSets, Parameters parameters) throws InterruptedException;

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to the multi-dataset core with a singleton list, so that single-dataset calls (including bootstrapped
     * ones, via the superclass) run the same code path.
     */
    @Override
    protected Graph runSearch(DataModel dataSet, Parameters parameters) throws InterruptedException {
        return runSearch(Collections.singletonList(dataSet), parameters);
    }

    private Graph runSingleBootstrapSearch(RandomGenerator randomGenerator, List<DataModel> dataSets,
                                           Parameters parameters) throws InterruptedException {
        TetradLogger.getInstance().log("Bootstrap count = " + ++this.count);
        double r = parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE);

        List<DataModel> sample = new ArrayList<>(dataSets.size());

        for (DataModel dataModel : dataSets) {
            DataSet dataSet = (DataSet) dataModel;
            int[] selectedColumns = IntStream.range(0, dataSet.getNumColumns()).toArray();
            DataSet resampled = createDataSample(dataSet, randomGenerator, selectedColumns, parameters, r);

            if (dataSet.getName() != null) {
                resampled.setName(dataSet.getName());
            }

            sample.add(resampled);
        }

        return runSearch(sample, parameters);
    }

    /**
     * Runs the multi-dataset search, bootstrapping if {@code numberResampling} is greater than zero. The aggregation
     * of the bootstrap graphs (edge-frequency graph, saved bootstrap graphs, sampling/display graph handling) is the
     * same as in {@link AbstractBootstrapAlgorithm#search(DataModel, Parameters)}.
     *
     * @param dataSets   the data sets to search over.
     * @param parameters the parameters.
     * @return the graph.
     * @throws InterruptedException if the search is interrupted.
     */
    public Graph search(List<DataModel> dataSets, Parameters parameters) throws InterruptedException {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) == 0) {
            return runSearch(dataSets, parameters);
        }

        for (DataModel dataModel : dataSets) {
            if (dataModel instanceof ICovarianceMatrix) {
                throw new IllegalArgumentException("Sorry, you need tabular datasets in order to do bootstrapping.");
            }
        }

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
                tasks.add(() -> runSingleBootstrapSearch(randomGenerator, dataSets, parameters));
            }

            if (parameters.getBoolean(Params.ADD_ORIGINAL_DATASET)) {
                tasks.add(() -> runSearch(dataSets, parameters));
            }

            TaskRunner<Graph> taskRunner = new TaskRunner<>(parameters.getInt(Params.BOOTSTRAPPING_NUM_THREADS));
            List<Graph> graphs = taskRunner.run(tasks);

            System.gc();

            if (graphs.isEmpty()) {
                graph = new EdgeListGraph();
            } else {
                if (parameters.getBoolean(Params.SAVE_BOOTSTRAP_GRAPHS)) {
                    this.bootstrapGraphs.clear();
                    this.bootstrapGraphs.addAll(graphs);
                }

                graph = GraphSampling.createGraphWithHighProbabilityEdges(graphs);

                // Median member graph; see the corresponding comment in AbstractBootstrapAlgorithm.search.
                if (parameters.getInt(Params.NUMBER_RESAMPLING) > 0) {
                    medianMemberGraph = GraphSampling.selectMedianEnsembleGraph(graphs);
                }
            }
        }

        graph = GraphUtils.fixDirections(graph);

        // See the corresponding comment in AbstractBootstrapAlgorithm.search: the sampling graph carries the
        // bootstrapping information; the display graph is the proper display version whose ancillary "samplingGraph"
        // allows ensemble choices from the right-click menu in the interface.
        ((EdgeListGraph) graph).setAncillaryGraph("samplingGraph", graph);

        if (medianMemberGraph != null) {
            ((EdgeListGraph) graph).setAncillaryGraph("medianGraph", medianMemberGraph);
            ((EdgeListGraph) medianMemberGraph).setAncillaryGraph("samplingGraph", graph);

            // As of 2026-8-13 the median member graph is the default initial display for every bootstrap
            // search; see the corresponding comment in AbstractBootstrapAlgorithm.search.
            return GraphUtils.fixDirections(medianMemberGraph);
        }

        Graph displayGraph = GraphSampling.createDisplayGraph(graph, ResamplingEdgeEnsemble.Highest);
        ((EdgeListGraph) displayGraph).setAncillaryGraph("samplingGraph", graph);

        return GraphUtils.fixDirections(displayGraph);
    }
}

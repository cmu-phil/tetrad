/*
 * Copyright (C) 2024 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.*;
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
public abstract class AbstractBootstrapAlgorithm implements Algorithm, ReturnsBootstrapGraphs {

    // 2025-6-10 Refactored this code so that bootstrap datasets are not all calculated up front but are calculated
    // on the fly as they are needed for running bootstrap samples. This was needed for a use case where a client
    // wanted to run 50,000 bootstraps. Note that with this change we are allowing the system to decide when
    // to garbage collect rather than calling System.gc() every run, which speeds things up. jdramsey

    /**
     * The bootstrap graphs.
     */
    private final List<Graph> bootstrapGraphs = new LinkedList<>();
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
        return runSearch(createDataSample((DataSet) dataModel, randomGenerator, selectedColumns, parameters), parameters);
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
            return runSearch(dataModel, parameters);
        }

        // create new random generator if a seed is given
        long seed = parameters.getLong(Params.SEED);
        RandomGenerator randomGenerator = (seed < 0) ? null : new SynchronizedRandomGenerator(new Well44497b(seed));

        Graph graph;
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

            if (graphs.isEmpty()) {
                graph = new EdgeListGraph();
            } else {
                if (parameters.getInt(Params.NUMBER_RESAMPLING) > 0) {
                    this.bootstrapGraphs.clear();
                    this.bootstrapGraphs.addAll(graphs);
                    graph = GraphSampling.createGraphWithHighProbabilityEdges(graphs);
                } else {
                    graph = graphs.getFirst();
                }
            }
        }

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

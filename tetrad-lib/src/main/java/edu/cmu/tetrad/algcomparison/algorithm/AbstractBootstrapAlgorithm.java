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
import edu.cmu.tetrad.data.DataSampling;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.GraphSampling;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TaskRunner;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * This is a base class for bootstrap algorithms.
 *
 * Mar 4, 2024 5:05:28 PM
 *
 * @author Kevin V. Bui (kvb2univpitt@gmail.com)
 */
public abstract class AbstractBootstrapAlgorithm implements Algorithm, ReturnsBootstrapGraphs {

    /**
     * The bootstrap graphs.
     */
    private final List<Graph> bootstrapGraphs = new LinkedList<>();

    protected abstract Graph runSearch(DataModel dataSet, Parameters parameters);

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (dataModel instanceof CovarianceMatrix) {
            if (this instanceof TakesCovarianceMatrix) {
                return runSearch(dataModel, parameters);
            } else {
                throw new IllegalArgumentException("This search cannot take a covariance matrix as input.");
            }
        }

        List<DataSet> dataSets = DataSampling.createDataSamples((DataSet) dataModel, parameters);

        Graph graph;
        if (Thread.currentThread().isInterrupted()) {
            // release resources
            dataSets.clear();
            System.gc();

            graph = new EdgeListGraph();
        } else {
            List<Callable<Graph>> tasks = new LinkedList<>();
            for (DataSet dataSet : dataSets) {
                tasks.add(() -> runSearch(dataSet, parameters));
            }

            TaskRunner<Graph> taskRunner = new TaskRunner<>(parameters.getInt(Params.BOOTSTRAPPING_NUM_THEADS));
            List<Graph> graphs = taskRunner.run(tasks);

            if (graphs.isEmpty()) {
                graph = new EdgeListGraph();
            } else {
                if (parameters.getInt(Params.NUMBER_RESAMPLING) > 0) {
                    this.bootstrapGraphs.clear();
                    this.bootstrapGraphs.addAll(graphs);
                    graph = GraphSampling.createGraphWithHighProbabilityEdges(graphs);
                } else {
                    graph = graphs.get(0);
                }
            }
        }

        // release resources
        dataSets.clear();
        System.gc();

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

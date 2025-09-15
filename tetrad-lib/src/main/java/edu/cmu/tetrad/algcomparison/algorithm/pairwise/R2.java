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

package edu.cmu.tetrad.algcomparison.algorithm.pairwise;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Lofs;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * R2.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@Experimental
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "R2",
//        command = "r2",
//        algoType = AlgType.orient_pairwise
//)
@Bootstrapping
public class R2 extends AbstractBootstrapAlgorithm implements Algorithm, TakesExternalGraph {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The algorithm to use for the initial graph.
     */
    private Algorithm algorithm;

    /**
     * The external graph.
     */
    private Graph externalGraph;

    /**
     * <p>Constructor for R2.</p>
     */
    public R2() {
    }

    /**
     * <p>Constructor for R2.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    public R2(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * Runs the search algorithm using the provided data model and parameters.
     *
     * @param dataModel  The data model containing the dataset to search.
     * @param parameters The parameters to be used for the search algorithm.
     * @return The graph result of the search algorithm.
     * @throws IllegalArgumentException if the data model is not a continuous dataset, or if the search algorithm does
     *                                  not find a graph result.
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (!(dataModel instanceof DataSet dataSet && dataModel.isContinuous())) {
            throw new IllegalArgumentException("Expecting a continuous dataset.");
        }

        Graph graph = this.algorithm.search(dataSet, parameters);

        if (graph != null) {
            this.externalGraph = graph;
        } else {
            throw new IllegalArgumentException("This R2 algorithm needs both data and a graph source as inputs; it \n"
                                               + "will orient the edges in the input graph using the data");
        }

        List<DataSet> dataSets = new ArrayList<>();
        dataSets.add(SimpleDataLoader.getContinuousDataSet(dataSet));

        Lofs lofs = new Lofs(this.externalGraph, dataSets);
        lofs.setRule(Lofs.Rule.R2);

        return lofs.orient();
    }

    /**
     * Returns a comparison graph for the given true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return A comparison graph representing the true directed graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * Returns the description of the algorithm.
     *
     * @return The description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "R2, entropy based pairwise orientation" + (this.algorithm != null ? " with initial graph from "
                                                                                    + this.algorithm.getDescription() : "");
    }

    /**
     * Returns the data type required for the search, whether continuous, discrete, or mixed.
     *
     * @return The data type required for the search.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the list of parameters for the current instance of the class.
     *
     * @return A list of parameter names used by the algorithm. If the algorithm does not have any parameters, an empty
     * list is returned.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new LinkedList<>();

        if (this.algorithm != null && !this.algorithm.getParameters().isEmpty()) {
            parameters.addAll(this.algorithm.getParameters());
        }

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    /**
     * Sets the external graph for the algorithm.
     *
     * @param algorithm a {@link Algorithm} object representing the external graph to be set
     * @throws IllegalArgumentException if the algorithm is null
     */
    @Override
    public void setExternalGraph(Algorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("This R2 algorithm needs both data and a graph source as inputs; it \n"
                                               + "will orient the edges in the input graph using the data.");
        }

        this.algorithm = algorithm;
    }

}


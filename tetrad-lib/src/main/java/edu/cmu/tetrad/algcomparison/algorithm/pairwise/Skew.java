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
import edu.cmu.tetrad.annotation.AlgType;
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
import java.util.List;

/**
 * Skew.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "Skew",
        command = "skew",
        algoType = AlgType.orient_pairwise,
        dataType = DataType.Continuous

)
@Bootstrapping
public class Skew extends AbstractBootstrapAlgorithm implements Algorithm, TakesExternalGraph {

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
     * <p>Constructor for Skew.</p>
     */
    public Skew() {
    }

    /**
     * <p>Constructor for Skew.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    public Skew(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * Runs the search algorithm to orient edges in the input graph using the given data model and parameters.
     *
     * @param dataModel  The data model representing the data set. It must be a continuous data set.
     * @param parameters The parameters for the search algorithm.
     * @return The oriented graph produced by the search algorithm.
     * @throws IllegalArgumentException If the data model is not a continuous data set or if the search algorithm fails
     *                                  to produce a graph.
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
            throw new IllegalArgumentException("This Skew algorithm needs both data and a graph source as inputs; it \n"
                                               + "will orient the edges in the input graph using the data");
        }

        List<DataSet> dataSets = new ArrayList<>();
        dataSets.add(SimpleDataLoader.getContinuousDataSet(dataSet));

        Lofs lofs = new Lofs(this.externalGraph, dataSets);
        lofs.setRule(Lofs.Rule.Skew);

        return lofs.orient();
    }

    /**
     * Returns a comparison graph based on the true directed graph, if there is one.
     *
     * @param graph The true directed graph, if there is one.
     * @return A comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * Returns a description of the method.
     *
     * @return A string representing the description of the method. If the algorithm is not null, it appends the initial
     * graph description from the algorithm.
     */
    @Override
    public String getDescription() {
        return "Skew" + (this.algorithm != null ? " with initial graph from "
                                                  + this.algorithm.getDescription() : "");
    }

    /**
     * Retrieves the data type of the current algorithm.
     *
     * @return The data type of the algorithm. It can be one of the following: - Continuous: if all variables in the
     * data set are continuous - Discrete: if all variables in the data set are discrete - Mixed: if the data set
     * contains a mix of continuous and discrete variables - Graph: if the algorithm produces a graph as the result -
     * Covariance: if the algorithm requires a covariance matrix as input - All: if the data type is not known or if the
     * algorithm can handle any data type
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the list of parameter names that are used by this method and its associated algorithm.
     *
     * @return A list of parameter names. If the associated algorithm has parameters, they are included in the list.
     * Additionally, the parameter name "VERBOSE" is always added to the list.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        if (this.algorithm != null && !this.algorithm.getParameters().isEmpty()) {
            parameters.addAll(this.algorithm.getParameters());
        }

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    /**
     * Sets the external graph to be used by the algorithm.
     *
     * @param algorithm The algorithm object representing the external graph. Must implement the {@link Algorithm}
     *                  interface.
     * @throws IllegalArgumentException If the algorithm is null.
     */
    @Override
    public void setExternalGraph(Algorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("This Skew algorithm needs both data and a graph source as inputs; it \n"
                                               + "will orient the edges in the input graph using the data.");
        }

        this.algorithm = algorithm;
    }

}


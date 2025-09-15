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
 * RSkewE.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@Experimental
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "RSkewE",
//        command = "r-skew-e",
//        algoType = AlgType.orient_pairwise
//)
@Bootstrapping
public class RskewE extends AbstractBootstrapAlgorithm implements Algorithm, TakesExternalGraph {

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
     * <p>Constructor for RskewE.</p>
     */
    public RskewE() {

    }

    /**
     * <p>Constructor for RskewE.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    public RskewE(Algorithm algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * Runs a search algorithm to find the orientation of edges in a graph using the given data model and parameters.
     *
     * @param dataModel  the data model containing the dataset
     * @param parameters the parameters to be used for the search algorithm
     * @return the graph with oriented edges
     * @throws IllegalArgumentException if the data model is not a continuous dataset or if the graph is null
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
            throw new IllegalArgumentException("This RSkewE algorithm needs both data and a graph source as inputs; it \n"
                                               + "will orient the edges in the input graph using the data");
        }

        List<DataSet> dataSets = new ArrayList<>();
        dataSets.add(SimpleDataLoader.getContinuousDataSet(dataSet));

        Lofs lofs = new Lofs(this.externalGraph, dataSets);
        lofs.setRule(Lofs.Rule.RSkewE);

        return lofs.orient();
    }

    /**
     * Returns a comparison graph based on the provided true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * Returns the description of the current algorithm. It returns the description of the algorithm if the external
     * graph is not null (using the getDescription method of the algorithm), otherwise it returns an empty string.
     *
     * @return The description of the current algorithm.
     */
    @Override
    public String getDescription() {
        return "RSkewE" + (this.externalGraph != null ? " with initial graph from "
                                                        + this.algorithm.getDescription() : "");
    }

    /**
     * Returns the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return The data type of the search.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the list of parameters required for the current instance of the class.
     *
     * @return A list of parameter names. If the algorithm associated with this instance has parameters defined, those
     * parameters will be included in the list. In addition, the parameter "VERBOSE" will always be included.
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
     * @param algorithm the algorithm object representing the external graph
     * @throws IllegalArgumentException if the algorithm is null
     */
    @Override
    public void setExternalGraph(Algorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("This RSkewE algorithm needs both data and a graph source as inputs; it \n"
                                               + "will orient the edges in the input graph using the data.");
        }

        this.algorithm = algorithm;
    }

}


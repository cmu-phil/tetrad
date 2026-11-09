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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks on a graph handed straight to an Estimator box, in place of a parametric model. Each estimator used to
 * be reachable only through its PM box (SEM PM, Bayes PM, Hybrid CG PM, Generalized SEM PM), so that from a Graph
 * box the only estimator on offer was the neural-net one, which takes a graph directly. The estimators now also
 * take (data, graph) and build the PM themselves, exactly as the PM box would from the same graph and data; these
 * methods say clearly, before that happens, why a graph cannot be used, since the exceptions from deeper down
 * ("A SEM graph may contain only directed and bidirected edges") do not tell the user what to do.
 * <p>
 * A graph from a search is usually a CPDAG or a PAG, with undirected, partially oriented or circle-ended edges;
 * none of the estimators can use those. The Graph box's "Choose Dag in CPDAG" picks one DAG in the class, and for
 * a measurement model any choice that adds no collider gives the same fit; the message names that step.
 *
 * @author josephramsey
 */
final class EstimatorGraphs {

    private EstimatorGraphs() {
    }

    /**
     * The graph of the source, copied, with every edge directed: the input a Bayes or Hybrid CG estimator needs.
     *
     * @param source    the Graph, Search, or other box the graph comes from.
     * @param estimator the estimator's name, for the message.
     * @return a copy of the graph.
     * @throws IllegalArgumentException if some edge is not directed, or the graph has a directed cycle.
     */
    static Graph dag(GraphSource source, String estimator) {
        Graph graph = copy(source, estimator);
        List<Edge> bad = new ArrayList<>();

        for (Edge edge : graph.getEdges()) {
            if (!Edges.isDirectedEdge(edge)) bad.add(edge);
        }

        if (!bad.isEmpty()) {
            throw new IllegalArgumentException(estimator + " needs a DAG, and " + bad.size() + " of the graph's "
                                               + graph.getNumEdges() + " edges " + (bad.size() == 1 ? "is" : "are")
                                               + " not directed (for example " + bad.get(0) + "). If the graph came "
                                               + "from a search, it is an equivalence class: add a Graph box, "
                                               + "choose \"Choose Dag in CPDAG\" on it, and estimate from that.");
        }

        if (graph.paths().existsDirectedCycle()) {
            throw new IllegalArgumentException(estimator + " needs a DAG, and the graph has a directed cycle.");
        }

        return graph;
    }

    /**
     * The graph of the source, copied, with every edge directed or bidirected: the input a SEM estimator needs
     * (a bidirected edge is a correlated error).
     *
     * @param source    the Graph, Search, or other box the graph comes from.
     * @param estimator the estimator's name, for the message.
     * @return a copy of the graph.
     * @throws IllegalArgumentException if some edge is neither directed nor bidirected.
     */
    static Graph semGraph(GraphSource source, String estimator) {
        Graph graph = copy(source, estimator);
        List<Edge> bad = new ArrayList<>();

        for (Edge edge : graph.getEdges()) {
            if (!Edges.isDirectedEdge(edge) && !Edges.isBidirectedEdge(edge)) bad.add(edge);
        }

        if (!bad.isEmpty()) {
            throw new IllegalArgumentException(estimator + " needs every edge directed (or bidirected, for a "
                                               + "correlated error), and " + bad.size() + " of the graph's "
                                               + graph.getNumEdges() + " edges " + (bad.size() == 1 ? "is" : "are")
                                               + " neither (for example " + bad.get(0) + "). If the graph came from "
                                               + "a search, it is an equivalence class: add a Graph box, choose "
                                               + "\"Choose Dag in CPDAG\" on it, and estimate from that. For a "
                                               + "measurement model, any orientation of the latent-to-latent "
                                               + "edges that adds no collider gives the same fit.");
        }

        return graph;
    }

    /**
     * The data box's selected model as a tabular data set whose variables are all discrete, for the Bayes
     * estimators.
     *
     * @param dataWrapper the Data box.
     * @param estimator   the estimator's name, for the message.
     * @return the data set.
     * @throws IllegalArgumentException if the model is not a data set or has a continuous variable.
     */
    static DataSet discreteDataSet(DataWrapper dataWrapper, String estimator) {
        DataModel model = dataWrapper.getSelectedDataModel();

        if (!(model instanceof DataSet dataSet)) {
            throw new IllegalArgumentException(estimator + " needs a tabular data set, not a covariance matrix.");
        }

        for (Node v : dataSet.getVariables()) {
            if (!(v instanceof DiscreteVariable)) {
                throw new IllegalArgumentException(estimator + " needs discrete data, and " + v.getName()
                                                   + " is continuous. Discretize first, or use the Hybrid CG "
                                                   + "estimator for mixed data.");
            }
        }

        return dataSet;
    }

    /**
     * The data box's selected model as a tabular data set, for the Hybrid CG estimator.
     */
    static DataSet dataSet(DataWrapper dataWrapper, String estimator) {
        DataModel model = dataWrapper.getSelectedDataModel();

        if (!(model instanceof DataSet dataSet)) {
            throw new IllegalArgumentException(estimator + " needs a tabular data set, not a covariance matrix.");
        }

        return dataSet;
    }

    private static Graph copy(GraphSource source, String estimator) {
        if (source == null) throw new NullPointerException("graph source");
        Graph graph = source.getGraph();

        if (graph == null) {
            throw new IllegalArgumentException(estimator + ": the graph box has no graph yet.");
        }

        return new EdgeListGraph(graph, true);
    }
}

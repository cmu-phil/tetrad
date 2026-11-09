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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.session.DoNotAddOldModel;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Compares a target workbench with a reference workbench by counting errors of omission and commission.  (for edge
 * presence only, not orientation).
 *
 * @author josephramsey
 * @author Erin Korber (added remove latents functionality July 2004)
 * @version $Id: $Id
 */
public final class EdgewiseComparisonModel implements SessionModel, DoNotAddOldModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The target graph.
     */
    private final Graph targetGraph;

    /**
     * The reference graph.
     */
    private final Graph referenceGraph;

    /**
     * The parameters.
     */
    private final Parameters params;

    /**
     * The name of the model.
     */
    private String name;

    /**
     * The target graphs, in display order, when more than one graph source is compared to the reference. Null for
     * instances deserialized from sessions saved before multi-target comparison was added; see getTargetGraphs().
     */
    private List<Graph> targetGraphs;

    /**
     * The names of the target graphs, in display order. Null for instances deserialized from older sessions; see
     * getTargetNames().
     */
    private List<String> targetNames;

    /**
     * Compares the results of a PC to a reference workbench by counting errors of omission and commission. The counts
     * can be retrieved using the methods
     * <code>countOmissionErrors</code> and <code>countCommissionErrors</code>.
     *
     * @param model1 a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param model2 a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public EdgewiseComparisonModel(GraphSource model1, GraphSource model2, Parameters params) {
        this(checkPair(model1, model2), params);
    }

    /**
     * Compares the graphs of any number of graph sources against a single reference (true) graph, which is one of
     * them. The reference is chosen by the "referenceGraphName" parameter, falling back to a Simulation box if one
     * is attached; the remaining sources are the targets, ordered by the "targetGraphNames" parameter where
     * available. The session machinery matches this constructor when the comparison box has two or more graph
     * source parents; see SessionNode's handling of (X[], Parameters) constructors.
     *
     * @param models the graph sources to compare; one is the reference, the rest are targets.
     * @param params the parameters for the comparison.
     */
    public EdgewiseComparisonModel(GraphSource[] models, Parameters params) {
        if (params == null) {
            throw new NullPointerException("Parameters must not be null");
        }

        if (models == null || models.length == 0) {
            throw new IllegalArgumentException("At least one graph source expected.");
        }

        for (GraphSource model : models) {
            if (model == null) {
                throw new NullPointerException("Null graph source>");
            }
        }

        this.params = params;

        List<GraphSource> sources = GraphComparisonUtils.sortByName(models);
        GraphSource reference = GraphComparisonUtils.resolveReference(sources, params);
        List<GraphSource> targets = GraphComparisonUtils.orderTargets(sources, reference, params);

        this.referenceGraph = reference.getGraph();

        this.targetGraphs = new ArrayList<>();
        this.targetNames = new ArrayList<>();

        for (GraphSource target : targets) {
            this.targetGraphs.add(target.getGraph());
            this.targetNames.add(target.getName());
        }

        this.targetGraph = targets.isEmpty() ? reference.getGraph() : this.targetGraphs.getFirst();

        TetradLogger.getInstance().log("Graph Comparison");

    }

    /**
     * Bundles the two graph sources of the legacy pair constructor into an array, null-checking first so the
     * legacy constructor keeps its error behavior.
     */
    private static GraphSource[] checkPair(GraphSource model1, GraphSource model2) {
        if (model1 == null || model2 == null) {
            throw new NullPointerException("Null graph source>");
        }

        return new GraphSource[]{model1, model2};
    }


    //=============================CONSTRUCTORS==========================//

    /**
     * <p>getComparisonGraph.</p>
     *
     * @param graph  a {@link edu.cmu.tetrad.graph.Graph} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph getComparisonGraph(Graph graph, Parameters params) {
        return Misclassifications.getComparisonGraph(graph, params);
    }

    //==============================PUBLIC METHODS========================//

    /**
     * <p>getDataSet.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet getDataSet() {
        return (DataSet) this.params.get("dataSet", null);
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>getComparisonString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getComparisonString() {
        String refName = getParams().getString("referenceGraphName", null);

        Graph comparisonGraph = getComparisonGraph(this.referenceGraph, this.params);

        List<Graph> graphs = getTargetGraphs();
        List<String> names = getTargetNames();

        if (graphs.isEmpty()) {
            return "True graph from " + refName + "\n\nNo target graphs to compare to it.";
        }

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < graphs.size(); i++) {
            if (i > 0) {
                builder.append("\n\n==============================================================\n\n");
            }

            builder.append(GraphSearchUtils.getEdgewiseComparisonString(refName, comparisonGraph,
                    names.get(i), graphs.get(i)));
        }

        return builder.toString();
    }

    /**
     * Returns the target graphs, in display order. For instances deserialized from sessions saved before
     * multi-target comparison was added, returns the single target graph in a list.
     *
     * @return the target graphs.
     */
    public List<Graph> getTargetGraphs() {
        if (this.targetGraphs == null) {
            return Collections.singletonList(this.targetGraph);
        }

        return this.targetGraphs;
    }

    /**
     * Returns the names of the target graphs, in display order. For instances deserialized from older sessions,
     * returns the single target name (from the "targetGraphName" parameter) in a list.
     *
     * @return the target names.
     */
    public List<String> getTargetNames() {
        if (this.targetNames == null) {
            return Collections.singletonList(this.params.getString("targetGraphName", null));
        }

        return this.targetNames;
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * <p>Getter for the field <code>params</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Parameters getParams() {
        return this.params;
    }
}




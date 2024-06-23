///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.algcomparison.CompareTwoGraphs;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.session.DoNotAddOldModel;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;


/**
 * Compares a target workbench with a reference workbench by counting errors of omission and commission.  (for edge
 * presence only, not orientation).
 *
 * @author josephramsey
 * @author Erin Korber (added remove latents functionality July 2004)
 * @version $Id: $Id
 */
public final class Misclassifications implements SessionModel, DoNotAddOldModel {
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
     * The parameters object, so the GUI can remember stuff for logging.
     */
    private final Parameters params;

    /**
     * The name of the model.
     */
    private String name;


    //=============================CONSTRUCTORS==========================//

    /**
     * Compares the results of a PC to a reference workbench by counting errors of omission and commission. The counts
     * can be retrieved using the methods
     * <code>countOmissionErrors</code> and <code>countCommissionErrors</code>.
     *
     * @param model1 a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param model2 a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Misclassifications(GraphSource model1, GraphSource model2, Parameters params) {
        if (params == null) {
            throw new NullPointerException("Parameters must not be null");
        }

        if (model1 == null || model2 == null) {
            throw new NullPointerException("Null graph source>");
        }

        this.params = params;

        String referenceName = params.getString("referenceGraphName", null);

        String model1Name = model1.getName();
        String model2Name = model2.getName();

        if (referenceName.equals(model1Name)) {
            this.referenceGraph = model1.getGraph();
            this.targetGraph = model2.getGraph();
        } else if (referenceName.equals(model2Name)) {
            this.referenceGraph = model2.getGraph();
            this.targetGraph = model1.getGraph();
        } else {
            this.referenceGraph = model1.getGraph();
            this.targetGraph = model2.getGraph();
        }

        TetradLogger.getInstance().log("Graph Comparison");

    }

    //==============================PUBLIC METHODS========================//

    /**
     * Returns a comparison graph based on the given input graph and parameters.
     *
     * @param graph  The input graph to compare.
     * @param params The parameters for the comparison.
     * @return The comparison graph based on the input graph and parameters.
     */
    public static Graph getComparisonGraph(Graph graph, Parameters params) {
        String type = params.getString("graphComparisonType");

        if ("DAG".equals(type)) {
            params.set("graphComparisonType", "DAG");
            return new EdgeListGraph(graph);
        } else if ("CPDAG".equals(type)) {
            params.set("graphComparisonType", "CPDAG");
            return GraphTransforms.dagToCpdag(graph);
        } else if ("PAG".equals(type)) {
            params.set("graphComparisonType", "PAG");
            return GraphTransforms.dagToPag(graph);
        } else {
            params.set("graphComparisonType", "DAG");
            return new EdgeListGraph(graph);
        }
    }

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
        String targetName = getParams().getString("targetGraphName", null);


        Graph comparisonGraph = getComparisonGraph(referenceGraph, params);

        String table = CompareTwoGraphs.getMisclassificationTable(comparisonGraph, targetGraph);
        return "True graph from " + refName + "\nTarget graph from " + targetName +
               "\n\n\n" + table;
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

    /**
     * <p>Getter for the field <code>referenceGraph</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getReferenceGraph() {
        return this.referenceGraph;
    }
}



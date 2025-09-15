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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.CheckKnowledge;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.List;


/**
 * Compares a target workbench with a reference workbench by counting errors of omission and commission.  (for edge
 * presence only, not orientation).
 *
 * @author josephramsey
 * @author Erin Korber (added remove latents functionality July 2004)
 * @version $Id: $Id
 */
public final class CheckKnowledgeModel implements SessionModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The graph to be checked.
     */
    private final Graph graph;

    /**
     * The knowledge to be checked against.
     */
    private final Knowledge knowledge;

    /**
     * The parameters for the check.
     */
    private final Parameters params;

    /**
     * The name of the model.
     */
    private final String modelName;

    /**
     * The name of the model.
     */
    private String name = "Check Knowledge";

    /**
     * Compares the results of a PC to a reference workbench by counting errors of omission and commission. The counts
     * can be retrieved using the methods
     * <code>countOmissionErrors</code> and <code>countCommissionErrors</code>.
     *
     * @param model             a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param params            a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public CheckKnowledgeModel(GraphSource model, KnowledgeBoxModel knowledgeBoxModel, Parameters params) {
        if (params == null) {
            throw new NullPointerException("Parameters must not be null");
        }

        if (model == null) {
            throw new NullPointerException("Null graph source>");
        }

        if (knowledgeBoxModel == null) {
            throw new NullPointerException("Null knowledge box model");
        }

        this.graph = model.getGraph();
        this.knowledge = knowledgeBoxModel.getKnowledge();
        this.params = params;
        this.modelName = model.getName();
    }


    /**
     * <p>getComparisonString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getComparisonString() {
        List<Edge> forbiddenViolations = CheckKnowledge.forbiddenViolations(graph, knowledge);
        List<Edge> requiredViolations = CheckKnowledge.requiredViolations(graph, knowledge);

        StringBuilder sb = new StringBuilder();
        sb.append("Violations of knowledge for ").append(modelName).append(": ");

        sb.append("\n\nForbidden Violations:\n");

        for (Edge edge : forbiddenViolations) {
            sb.append("\n");
            sb.append(edge.toString());
            sb.append(", ");
        }

        sb.append("\n\nRequired Violations:\n");

        for (Edge edge : requiredViolations) {
            sb.append("\n");
            sb.append(edge.toString());
            sb.append(", ");
        }

        sb.append("\n\nKnowledge:\n\n");
        sb.append(knowledge);

        sb.append("\n\nGraph:\n\n");
        sb.append(graph);

        return sb.toString();
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
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        this.name = name;
    }
}




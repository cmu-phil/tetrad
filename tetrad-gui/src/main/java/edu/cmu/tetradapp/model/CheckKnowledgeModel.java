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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.CheckKnowledge;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;

import java.io.IOException;
import java.io.ObjectInputStream;
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
    private static final long serialVersionUID = 23L;
    private final Graph graph;
    private final Knowledge knowledge;
    private final Parameters params;
    private final String modelName;
    private String name = "Check Knowledge";

    /**
     * Compares the results of a PC to a reference workbench by counting errors of omission and commission. The counts
     * can be retrieved using the methods
     * <code>countOmissionErrors</code> and <code>countCommissionErrors</code>.
     *
     * @param model a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
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
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.)
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    /**
     * <p>Getter for the field <code>params</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Parameters getParams() {
        return this.params;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

    /** {@inheritDoc} */
    public void setName(String name) {
        this.name = name;
    }
}



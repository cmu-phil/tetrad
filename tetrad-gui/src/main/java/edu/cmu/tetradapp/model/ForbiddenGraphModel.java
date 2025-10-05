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
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import static edu.cmu.tetrad.graph.GraphUtils.addForbiddenReverseEdgesForDirectedEdges;

/**
 * <p>ForbiddenGraphModel class.</p>
 *
 * @author kaalpurush
 * @version $Id: $Id
 */
public class ForbiddenGraphModel extends KnowledgeBoxModel {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The graph to which the forbidden edges are to be added.
     */
    private Graph resultGraph = new EdgeListGraph();

    /**
     * <p>Constructor for ForbiddenGraphModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesPmWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ForbiddenGraphModel(BayesPmWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for ForbiddenGraphModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ForbiddenGraphModel(GraphWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for ForbiddenGraphModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.StandardizedSemImWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ForbiddenGraphModel(StandardizedSemImWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for ForbiddenGraphModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemImWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ForbiddenGraphModel(SemImWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for ForbiddenGraphModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemPmWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ForbiddenGraphModel(SemPmWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for ForbiddenGraphModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ForbiddenGraphModel(DataWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for ForbiddenGraphModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.TimeLagGraphWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ForbiddenGraphModel(TimeLagGraphWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for ForbiddenGraphModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.GeneralizedSemImWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ForbiddenGraphModel(GeneralizedSemImWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for ForbiddenGraphModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesImWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ForbiddenGraphModel(BayesImWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for ForbiddenGraphModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemGraphWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ForbiddenGraphModel(SemGraphWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for ForbiddenGraphModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.GeneralizedSemPmWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ForbiddenGraphModel(GeneralizedSemPmWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for ForbiddenGraphModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DagWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ForbiddenGraphModel(DagWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for ForbiddenGraphModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DirichletBayesImWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ForbiddenGraphModel(DirichletBayesImWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for ForbiddenGraphModel.</p>
     *
     * @param input  a {@link KnowledgeBoxInput} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ForbiddenGraphModel(KnowledgeBoxInput input, Parameters params) {
        this(params, input);
    }

    /**
     * Constructor from dataWrapper edge
     *
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     * @param input  a {@link KnowledgeBoxInput} object
     */
    public ForbiddenGraphModel(Parameters params, KnowledgeBoxInput input) {
        super(new KnowledgeBoxInput[]{input}, params);

        if (input == null) {
            throw new NullPointerException();
        }

        SortedSet<Node> variableNodes = new TreeSet<>(input.getVariables());
        SortedSet<String> variableNames = new TreeSet<>(input.getVariableNames());

        /*
         * @serial @deprecated
         */
        List<Node> variables = new ArrayList<>(variableNodes);
        List<String> variableNames1 = new ArrayList<>(variableNames);

        this.resultGraph = input.getResultGraph();

        /*
         * @serial @deprecated
         */
        Knowledge knowledge = new Knowledge();

        for (Node v : input.getVariables()) {
            knowledge.addVariable(v.getName());
        }

        createKnowledge(params);

        TetradLogger.getInstance().log("Knowledge");

        // This is a conundrum. At this point I dont know whether I am in a
        // simulation or not. If in a simulation, I should print the knowledge.
        // If not, I should wait for resetParams to be called. For now I'm
        // printing the knowledge if it's not empty.
        if (!((Knowledge) params.get("knowledge", new Knowledge())).isEmpty()) {
            String message = params.get("knowledge", new Knowledge()).toString();
            TetradLogger.getInstance().log(message);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.ForbiddenGraphModel} object
     * @see TetradSerializableUtils
     */
    public static ForbiddenGraphModel serializableInstance() {
        return new ForbiddenGraphModel(new Parameters(), GraphWrapper.serializableInstance());
    }

    private void createKnowledge(Parameters params) {
        Knowledge knowledge = getKnowledge();
        if (knowledge == null) {
            return;
        }

        knowledge.clear();

        if (this.resultGraph == null) {
            throw new NullPointerException("I couldn't find a parent graph.");
        }

        addForbiddenReverseEdgesForDirectedEdges(resultGraph, knowledge);


//        List<Node> nodes = this.resultGraph.getNodes();
//
//        int numOfNodes = nodes.size();
//        for (int i = 0; i < numOfNodes; i++) {
//            for (int j = i + 1; j < numOfNodes; j++) {
//                Node n1 = nodes.get(i);
//                Node n2 = nodes.get(j);
//
//                if (n1.getName().startsWith("E_") || n2.getName().startsWith("E_")) {
//                    continue;
//                }
//
//                Edge edge = this.resultGraph.getEdge(n1, n2);
//                if (edge != null && edge.isDirected()) {
//                    knowledge.setForbidden(edge.getNode2().getName(), edge.getNode1().getName());
//                }
//            }
//        }
    }

    /**
     * <p>Getter for the field <code>resultGraph</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getResultGraph() {
        return this.resultGraph;
    }

}


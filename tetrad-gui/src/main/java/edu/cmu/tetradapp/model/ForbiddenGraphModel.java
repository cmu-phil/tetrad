///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author kaalpurush
 */
public class ForbiddenGraphModel extends KnowledgeBoxModel {
    static final long serialVersionUID = 23L;

//    private String name;
//    private KnowledgeParams params;
//    private KnowledgeBoxInput knowledgeBoxInput1;
//    private List<String> varNames = new ArrayList<String>();

    /**
     * @serial
     * @deprecated
     */
    private IKnowledge knowledge;

    /**
     * @serial
     * @deprecated
     */
//    private List<Knowledge> knowledgeList;
    private List<Node> variables = new ArrayList<Node>();
    private List<String> variableNames = new ArrayList<String>();
    private Graph resultGraph = new EdgeListGraph();

    public ForbiddenGraphModel(BayesPmWrapper wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(GraphWrapper wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(StandardizedSemImWrapper wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(SemImWrapper wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(SemPmWrapper wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(DataWrapper wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(TimeLagGraphWrapper wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(GeneralizedSemImWrapper wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(BayesImWrapper wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(SemGraphWrapper wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(GeneralizedSemPmWrapper wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(DagWrapper wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(DirichletBayesImWrapper wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(BuildPureClustersRunner wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(PurifyRunner wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(LofsRunner wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(MeasurementModelWrapper wrapper, KnowledgeParams params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public ForbiddenGraphModel(KnowledgeBoxInput input, KnowledgeParams params) {
        this(params, input);
    }


    /**
     * Constructor from dataWrapper edge
     */
    public ForbiddenGraphModel(KnowledgeParams params, KnowledgeBoxInput input) {
        super(params, input);

        if (params == null) {
            throw new NullPointerException();
        }

        if (input == null) {
            throw new NullPointerException();
        }

        SortedSet<Node> variableNodes = new TreeSet<Node>();
        SortedSet<String> variableNames = new TreeSet<String>();

        variableNodes.addAll(input.getVariables());
        variableNames.addAll(input.getVariableNames());

        this.variables = new ArrayList<Node>(variableNodes);
        this.variableNames = new ArrayList<String>(variableNames);

        setKnowledgeBoxInput(input);

        this.resultGraph = input.getResultGraph();;

        createKnowledge(params);

        TetradLogger.getInstance().log("info", "Knowledge");

        // This is a conundrum. At this point I dont know whether I am in a
        // simulation or not. If in a simulation, I should print the knowledge.
        // If not, I should wait for resetParams to be called. For now I'm
        // printing the knowledge if it's not empty.
        if (!params.getKnowledge().isEmpty()) {
            TetradLogger.getInstance().log("knowledge", params.getKnowledge().toString());
        }
    }

    private void createKnowledge(KnowledgeParams params) {
        IKnowledge knowledge = params.getKnowledge();
        knowledge.clear();

        for (String varName : getKnowledgeBoxInput().getVariableNames()) {
            if (!varName.startsWith("E_")) {
                getVarNames().add(varName);
                knowledge.addVariable(varName);
            }
        }

        List<Node> nodes = resultGraph.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
//                if (resultGraph.getEdges().size() >= 2) continue;
                if (nodes.get(i).getName().startsWith("E_")) continue;
                if (nodes.get(j).getName().startsWith("E_")) continue;

                Edge edge = resultGraph.getEdge(nodes.get(i), nodes.get(j));

                if (edge == null) {
                    Node node1 = nodes.get(i);
                    Node node2 = nodes.get(j);
                    knowledge.setForbidden(node1.getName(), node2.getName());
                    knowledge.setForbidden(node2.getName(), node1.getName());
                }
                else if (edge.isDirected()) {
                    Node node1 = edge.getNode1();
                    Node node2 = edge.getNode2();
                    knowledge.setForbidden(node2.getName(), node1.getName());
//                    knowledge.setEdgeRequired(node1.getName(), node2.getName(), true);
                }
            }
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static ForbiddenGraphModel serializableInstance() {
        return new ForbiddenGraphModel(new KnowledgeParams(), GraphWrapper.serializableInstance());
    }

    public Graph getResultGraph() {
        return resultGraph;
    }
}




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
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.data.KnowledgeTransferable;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.session.ParamsResettable;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author kaalpurush
 */
public class KnowledgeBoxModel implements SessionModel, ParamsResettable, KnowledgeEditable,
        KnowledgeBoxInput, KnowledgeBoxNotifiable {

    static final long serialVersionUID = 23L;

    private String name;
    private Parameters params;
    private KnowledgeBoxInput knowledgeBoxInput;
    private List<String> varNames = new ArrayList<>();
    private List<Node> variables = new ArrayList<>();
    private List<String> variableNames = new ArrayList<>();
    private final Graph sourceGraph = new EdgeListGraph();
    private IKnowledge knowledge = new Knowledge2();
    private int numTiers = 3;

    public KnowledgeBoxModel(GeneralAlgorithmRunner runner) {
        this.knowledge = runner.getKnowledge();
    }

    /**
     * Constructor from dataWrapper edge
     */
    public KnowledgeBoxModel(KnowledgeBoxInput[] inputs, Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        if (inputs.length == 1 && inputs[0] instanceof KnowledgeTransferable) {
            this.knowledge = ((KnowledgeTransferable) inputs[0]).getKnowledge();
            this.numTiers = knowledge.getNumTiers();
            return;
        }

        for (KnowledgeBoxInput input : inputs) {
            if (input == null) {
                throw new NullPointerException();
            }
        }

        SortedSet<Node> variableNodes = new TreeSet<>();
        SortedSet<String> variableNames = new TreeSet<>();

        for (KnowledgeBoxInput input : inputs) {
            for (Node node : input.getVariables()) {
                if (node.getNodeType() == NodeType.MEASURED) {
                    variableNodes.add(node);
                    variableNames.add(node.getName());
                }
            }

//            variableNodes.addAll(input.getVariables());
//            variableNames.addAll(input.getVariableNames());
        }

        this.variables = new ArrayList<>(variableNodes);
        this.variableNames = new ArrayList<>(variableNames);

        this.params = params;
        this.setKnowledgeBoxInput(this);

        if (knowledge.isEmpty()) {
            freshenKnowledgeIfEmpty();
        }

        TetradLogger.getInstance().log("info", "Knowledge");

        // This is a conundrum. At this point I dont know whether I am in a
        // simulation or not. If in a simulation, I should print the knowledge.
        // If not, I should wait for resetParams to be called. For now I'm
        // printing the knowledge if it's not empty.
        if (!knowledge.isEmpty()) {
            TetradLogger.getInstance().log("knowledge", params.get("knowledge", new Knowledge2()).toString());
        }
    }

    private void freshenKnowledgeIfEmpty() {
        if (knowledge.isEmpty()) {
            createKnowledge(knowledge);
            varNames = new ArrayList<>();

            for (String varName : knowledgeBoxInput.getVariableNames()) {
                if (!varName.startsWith("E_")) {
                    varNames.add(varName);
                }
            }
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static KnowledgeBoxModel serializableInstance() {
        return new KnowledgeBoxModel(new KnowledgeBoxInput[]{GraphWrapper.serializableInstance()}, new Parameters());
    }

    private IKnowledge createKnowledge(IKnowledge knowledge) {
        knowledge.clear();
        for (String varName : varNames) {
            knowledge.addVariable(varName);
        }
        return knowledge;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Graph getSourceGraph() {
        return sourceGraph;
    }

    @Override
    public Graph getResultGraph() {
        return sourceGraph;
    }

    @Override
    public List<String> getVarNames() {
        return varNames;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }
        this.knowledge = knowledge;
        this.numTiers = knowledge.getNumTiers();
        TetradLogger.getInstance().log("knowledge", knowledge.toString());
    }

    @Override
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void resetParams(Object params) {
        this.params = (Parameters) params;
        freshenKnowledgeIfEmpty();
        TetradLogger.getInstance().log("knowledge", knowledge.toString());
    }

    @Override
    public Object getResettableParams() {
        return this.params;
    }

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public List<String> getVariableNames() {
        return variableNames;
    }

    KnowledgeBoxInput getKnowledgeBoxInput() {
        return knowledgeBoxInput;
    }

    void setKnowledgeBoxInput(KnowledgeBoxInput knowledgeBoxInput) {
        this.knowledgeBoxInput = knowledgeBoxInput;
    }

    public int getNumTiers() {
        return numTiers;
    }

    public void setNumTiers(int numTiers) {
        this.numTiers = numTiers;
    }
}

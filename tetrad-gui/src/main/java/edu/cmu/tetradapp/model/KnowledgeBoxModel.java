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

import java.io.Serial;
import java.util.*;

/**
 * <p>KnowledgeBoxModel class.</p>
 *
 * @author kaalpurush
 * @version $Id: $Id
 */
public class KnowledgeBoxModel implements SessionModel, ParamsResettable, KnowledgeEditable {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The source graph.
     */
    private final Graph sourceGraph = new EdgeListGraph();

    /**
     * The name of the model.
     */
    private String name;

    /**
     * The parameters.
     */
    private Parameters params;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The variables.
     */
    private List<Node> variables = new ArrayList<>();

    /**
     * The variable names.
     */
    private List<String> variableNames = new ArrayList<>();

    /**
     * The number of tiers.
     */
    private int numTiers = 3;

    /**
     * <p>Constructor for KnowledgeBoxModel.</p>
     *
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public KnowledgeBoxModel(Parameters params) {
        this.knowledge = new Knowledge();
        this.numTiers = 3;
        this.variables = new ArrayList<>();
        this.params = params;
        this.params.set("__myKnowledge", this.knowledge);
    }

    /**
     * Constructor from dataWrapper edge
     *
     * @param inputs an array of {@link edu.cmu.tetrad.data.KnowledgeBoxInput} objects
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public KnowledgeBoxModel(KnowledgeBoxInput[] inputs, Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        if (inputs.length == 1 && inputs[0] instanceof TimeLagGraphWrapper) {
            this.knowledge = ((TimeLagGraphWrapper) inputs[0]).getKnowledge();
            return;
        }

        if (inputs.length == 1 && inputs[0] instanceof KnowledgeTransferable) {
            this.knowledge = ((KnowledgeTransferable) inputs[0]).getKnowledge();
            this.numTiers = this.knowledge.getNumTiers();
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
                    this.knowledge.addVariable(node.getName());
                }
            }
        }

        this.variables = new ArrayList<>(variableNodes);
        this.variableNames = new ArrayList<>(variableNames);

        this.params = params;

        Object myKnowledge = params.get("__myKnowledge");
        if (myKnowledge instanceof Knowledge
                && new HashSet<>(((Knowledge) myKnowledge).getVariables())
                .equals(new HashSet<>(variableNames))) {
            this.knowledge = (Knowledge) myKnowledge;
        } else {
            this.knowledge = new Knowledge();

            for (String var : variableNames) {
                this.knowledge.addVariable(var);
            }

            params.set("__myKnowledge", this.knowledge);
        }

        TetradLogger.getInstance().log("info", "Knowledge");
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     * @return a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public static KnowledgeBoxModel serializableInstance() {
        return new KnowledgeBoxModel(new KnowledgeBoxInput[]{GraphWrapper.serializableInstance()}, new Parameters());
    }

    private void freshenKnowledgeIfEmpty(List<String> varNames) {
        if (this.knowledge.isEmpty()) {
            createKnowledge(this.knowledge);

            for (String varName : varNames) {
                if (!varName.startsWith("E_")) {
                    varNames.add(varName);
                }
            }
        }
    }

    private void createKnowledge(Knowledge knowledge) {
        knowledge.clear();
        this.variableNames.clear();
        for (String varName : knowledge.getVariables()) {
            knowledge.addVariable(varName);
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return this.name;
    }

    /** {@inheritDoc} */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    public Graph getSourceGraph() {
        return this.sourceGraph;
    }

    //    @Override
    /**
     * <p>getResultGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getResultGraph() {
        return this.sourceGraph;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getVarNames() {
        return this.variableNames;
    }

    /** {@inheritDoc} */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /** {@inheritDoc} */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }
        this.knowledge = knowledge;
        this.numTiers = knowledge.getNumTiers();
        this.params.set("__myKnowledge", knowledge);

        // printing out is bad for large knowledge input
//        TetradLogger.getInstance().log("knowledge", knowledge.toString());
    }

    /** {@inheritDoc} */
    @Override
    public void resetParams(Object params) {
        this.params = (Parameters) params;
        freshenKnowledgeIfEmpty(this.variableNames);

        // printing out is bad for large knowledge input
//        TetradLogger.getInstance().log("knowledge", knowledge.toString());
    }

    /** {@inheritDoc} */
    @Override
    public Object getResettableParams() {
        return this.params;
    }

    //    @Override
    /**
     * <p>Getter for the field <code>variables</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    //    @Override
    /**
     * <p>Getter for the field <code>variableNames</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getVariableNames() {
        return this.variableNames;
    }

    /**
     * <p>Getter for the field <code>numTiers</code>.</p>
     *
     * @return a int
     */
    public int getNumTiers() {
        return this.numTiers;
    }

    /**
     * <p>Setter for the field <code>numTiers</code>.</p>
     *
     * @param numTiers a int
     */
    public void setNumTiers(int numTiers) {
        this.numTiers = numTiers;
    }
}

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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.SortedSet;
import java.util.TreeSet;

import static edu.cmu.tetrad.graph.GraphUtils.removeNonSkeletonEdges;

/**
 * @author kaalpurush
 */
public class RemoveNonSkeletonEdgesModel extends KnowledgeBoxModel {

    static final long serialVersionUID = 23L;

    private Graph resultGraph = new EdgeListGraph();

    public RemoveNonSkeletonEdgesModel(BayesPmWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public RemoveNonSkeletonEdgesModel(GraphWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public RemoveNonSkeletonEdgesModel(StandardizedSemImWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public RemoveNonSkeletonEdgesModel(SemImWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public RemoveNonSkeletonEdgesModel(SemPmWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public RemoveNonSkeletonEdgesModel(DataWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public RemoveNonSkeletonEdgesModel(TimeLagGraphWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public RemoveNonSkeletonEdgesModel(GeneralizedSemImWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public RemoveNonSkeletonEdgesModel(BayesImWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public RemoveNonSkeletonEdgesModel(SemGraphWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public RemoveNonSkeletonEdgesModel(GeneralizedSemPmWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public RemoveNonSkeletonEdgesModel(DagWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public RemoveNonSkeletonEdgesModel(DirichletBayesImWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public RemoveNonSkeletonEdgesModel(BuildPureClustersRunner wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public RemoveNonSkeletonEdgesModel(PurifyRunner wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public RemoveNonSkeletonEdgesModel(MeasurementModelWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    public RemoveNonSkeletonEdgesModel(KnowledgeBoxInput input, Parameters params) {
        this(params, input);
    }

    /**
     * Constructor from dataWrapper edge
     */
    public RemoveNonSkeletonEdgesModel(Parameters params, KnowledgeBoxInput input) {
        super(new KnowledgeBoxInput[]{input}, params);

        if (input == null) {
            throw new NullPointerException();
        }

        SortedSet<Node> variableNodes = new TreeSet<>(input.getVariables());
        SortedSet<String> variableNames = new TreeSet<>(input.getVariableNames());

        this.resultGraph = input.getResultGraph();

        /*
         * @serial @deprecated
         */
        IKnowledge knowledge = new Knowledge();

        for (Node v : input.getVariables()) {
            knowledge.addVariable(v.getName());
        }

        createKnowledge(params);

        TetradLogger.getInstance().log("info", "Knowledge");

        // This is a conundrum. At this point I dont know whether I am in a
        // simulation or not. If in a simulation, I should print the knowledge.
        // If not, I should wait for resetParams to be called. For now I'm
        // printing the knowledge if it's not empty.
        if (!((IKnowledge) params.get("knowledge", new Knowledge())).isEmpty()) {
            TetradLogger.getInstance().log("knowledge", params.get("knowledge", new Knowledge()).toString());
        }
    }

    private void createKnowledge(Parameters params) {
        IKnowledge knowledge = getKnowledge();
        if (knowledge == null) {
            return;
        }

        knowledge.clear();

        if (this.resultGraph == null) {
            throw new NullPointerException("I couldn't find a parent graph.");
        }

        removeNonSkeletonEdges(resultGraph, knowledge);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static RemoveNonSkeletonEdgesModel serializableInstance() {
        return new RemoveNonSkeletonEdgesModel(new Parameters(), GraphWrapper.serializableInstance());
    }

    public Graph getResultGraph() {
        return this.resultGraph;
    }

}

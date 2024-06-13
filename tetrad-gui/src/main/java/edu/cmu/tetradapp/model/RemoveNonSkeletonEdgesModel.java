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
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.Serial;
import java.util.SortedSet;
import java.util.TreeSet;

import static edu.cmu.tetrad.graph.GraphUtils.removeNonSkeletonEdges;

/**
 * <p>RemoveNonSkeletonEdgesModel class.</p>
 *
 * @author kaalpurush
 * @version $Id: $Id
 */
public class RemoveNonSkeletonEdgesModel extends KnowledgeBoxModel {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The graph with non-skeleton edges removed.
     */
    private Graph resultGraph = new EdgeListGraph();

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesPmWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(BayesPmWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(GraphWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.StandardizedSemImWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(StandardizedSemImWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemImWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(SemImWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemPmWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(SemPmWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(DataWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.TimeLagGraphWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(TimeLagGraphWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.GeneralizedSemImWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(GeneralizedSemImWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesImWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(BayesImWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemGraphWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(SemGraphWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.GeneralizedSemPmWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(GeneralizedSemPmWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DagWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(DagWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DirichletBayesImWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(DirichletBayesImWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BuildPureClustersRunner} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(BuildPureClustersRunner wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.PurifyRunner} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(PurifyRunner wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.MeasurementModelWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(MeasurementModelWrapper wrapper, Parameters params) {
        this((KnowledgeBoxInput) wrapper, params);
    }

    /**
     * <p>Constructor for RemoveNonSkeletonEdgesModel.</p>
     *
     * @param input  a {@link KnowledgeBoxInput} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNonSkeletonEdgesModel(KnowledgeBoxInput input, Parameters params) {
        this(params, input);
    }

    /**
     * Constructor from dataWrapper edge
     *
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     * @param input  a {@link KnowledgeBoxInput} object
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
     * @return a {@link edu.cmu.tetradapp.model.RemoveNonSkeletonEdgesModel} object
     * @see TetradSerializableUtils
     */
    public static RemoveNonSkeletonEdgesModel serializableInstance() {
        return new RemoveNonSkeletonEdgesModel(new Parameters(), GraphWrapper.serializableInstance());
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

        removeNonSkeletonEdges(resultGraph, knowledge);
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

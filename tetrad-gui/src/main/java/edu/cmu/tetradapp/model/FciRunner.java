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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fci;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Rfci;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.util.IndTestType;
import edu.cmu.tetradapp.util.IonInput;

import java.util.ArrayList;
import java.util.List;


/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the FCI algorithm.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class FciRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, IonInput {
    private static final long serialVersionUID = 23L;

    //=========================CONSTRUCTORS================================//

    /**
     * <p>Constructor for FciRunner.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params      a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public FciRunner(DataWrapper dataWrapper, Parameters params) {
        super(dataWrapper, params, null);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     *
     * @param graphWrapper      a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param params            a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public FciRunner(GraphSource graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }


    /**
     * <p>Constructor for FciRunner.</p>
     *
     * @param dataWrapper       a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params            a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public FciRunner(DataWrapper dataWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    /**
     * <p>Constructor for FciRunner.</p>
     *
     * @param graph  a {@link edu.cmu.tetrad.graph.Graph} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public FciRunner(Graph graph, Parameters params) {
        super(graph, params);
    }

    /**
     * <p>Constructor for FciRunner.</p>
     *
     * @param graph             a {@link edu.cmu.tetrad.graph.Graph} object
     * @param params            a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public FciRunner(Graph graph, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graph, params, knowledgeBoxModel);
    }

    /**
     * <p>Constructor for FciRunner.</p>
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     * @param params       a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public FciRunner(GraphWrapper graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
    }

    /**
     * <p>Constructor for FciRunner.</p>
     *
     * @param dagWrapper a {@link edu.cmu.tetradapp.model.DagWrapper} object
     * @param params     a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public FciRunner(DagWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getDag(), params);
    }

    /**
     * <p>Constructor for FciRunner.</p>
     *
     * @param dagWrapper a {@link edu.cmu.tetradapp.model.SemGraphWrapper} object
     * @param params     a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public FciRunner(SemGraphWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getGraph(), params);
    }

    /**
     * <p>Constructor for FciRunner.</p>
     *
     * @param model  a {@link edu.cmu.tetradapp.model.IndependenceFactsModel} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public FciRunner(IndependenceFactsModel model, Parameters params) {
        super(model, params, null);
    }

    /**
     * <p>Constructor for FciRunner.</p>
     *
     * @param model             a {@link edu.cmu.tetradapp.model.IndependenceFactsModel} object
     * @param params            a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public FciRunner(IndependenceFactsModel model, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.FciRunner} object
     * @see TetradSerializableUtils
     */
    public static FciRunner serializableInstance() {
        return new FciRunner(Dag.serializableInstance(), new Parameters());
    }

    //=================PUBLIC METHODS OVERRIDING ABSTRACT=================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be implemented in the extending class.
     */
    public void execute() {
        Knowledge knowledge = (Knowledge) getParams().get("knowledge", new Knowledge());

        Graph graph;

        if (getParams().getBoolean("rfciUsed", false)) {
            Rfci fci = new Rfci(getIndependenceTest());
            fci.setKnowledge(knowledge);
            fci.setMaxDiscriminatingPathLength(getParams().getInt("maxReachablePathLength", -1));
            fci.setDepth(getParams().getInt("depth", -1));
            try {
                graph = fci.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            Fci fci = new Fci(getIndependenceTest());
            fci.setKnowledge(knowledge);
            fci.setCompleteRuleSetUsed(getParams().getBoolean("completeRuleSetUsed", false));
            fci.setDoPossibleDsep(getParams().getBoolean("doPossibleDsep", true));
            fci.setMaxDiscriminatingPathLength(getParams().getInt("maxReachablePathLength", -1));
            fci.setDepth(getParams().getInt("depth", -1));
            try {
                graph = fci.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        if (getSourceGraph() != null) {
            LayoutUtil.arrangeBySourceGraph(graph, getSourceGraph());
        } else if (knowledge.isDefaultToKnowledgeLayout()) {
            GraphSearchUtils.arrangeByKnowledgeTiers(graph, knowledge);
        } else {
            LayoutUtil.defaultLayout(graph);
        }

        setResultGraph(graph);
    }

    /**
     * <p>getIndependenceTest.</p>
     *
     * @return a {@link edu.cmu.tetrad.search.IndependenceTest} object
     */
    public IndependenceTest getIndependenceTest() {
        Object dataModel = getDataModel();

        if (dataModel == null) {
            dataModel = getSourceGraph();
        }

        Parameters params = getParams();
        IndTestType testType;

        testType = (IndTestType) params.get("indTestType", IndTestType.FISHER_Z);

        return new IndTestChooser().getTest(dataModel, params, testType);
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return getResultGraph();
    }


    /**
     * <p>getTriplesClassificationTypes.</p>
     *
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        return new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<>();
        Graph graph = getGraph();
        return triplesList;
    }

    /**
     * <p>supportsKnowledge.</p>
     *
     * @return a boolean
     */
    public boolean supportsKnowledge() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAlgorithmName() {
        return "FCI";
    }
}



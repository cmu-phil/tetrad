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
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.util.IndTestType;

import java.io.Serial;
import java.util.*;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the PC algorithm.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class PcRunner extends AbstractAlgorithmRunner
        implements IndTestProducer {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The external graph, if any, to use as a starting point for the search.
     */
    private Graph externalGraph;

    /**
     * The set of edges that are adjacent in the PC graph.
     */
    private Set<Edge> pcAdjacent;

    /**
     * The set of edges that are non-adjacent in the PC graph.
     */
    private Set<Edge> pcNonadjacent;


    //============================CONSTRUCTORS============================//


    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must contain a DataSet that is either a DataSet
     * or a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params      a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public PcRunner(DataWrapper dataWrapper, Parameters params) {
        super(dataWrapper, params, null);
    }

    /**
     * <p>Constructor for PcRunner.</p>
     *
     * @param dataWrapper       a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params            a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public PcRunner(DataWrapper dataWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    // Starts PC from the given graph.

    /**
     * <p>Constructor for PcRunner.</p>
     *
     * @param dataWrapper  a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     * @param params       a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public PcRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper, Parameters params) {
        super(dataWrapper, params, null);
        this.externalGraph = graphWrapper.getGraph();
    }

    /**
     * <p>Constructor for PcRunner.</p>
     *
     * @param dataWrapper       a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param graphWrapper      a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     * @param params            a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public PcRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.externalGraph = graphWrapper.getGraph();
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     *
     * @param graph  a {@link edu.cmu.tetrad.graph.Graph} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public PcRunner(Graph graph, Parameters params) {
        super(graph, params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     * @param params       a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public PcRunner(GraphWrapper graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
    }

    /**
     * <p>Constructor for PcRunner.</p>
     *
     * @param graphWrapper      a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param params            a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public PcRunner(GraphWrapper graphWrapper, KnowledgeBoxModel knowledgeBoxModel, Parameters params) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    /**
     * <p>Constructor for PcRunner.</p>
     *
     * @param dagWrapper a {@link edu.cmu.tetradapp.model.DagWrapper} object
     * @param params     a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public PcRunner(DagWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getDag(), params);
    }

    /**
     * <p>Constructor for PcRunner.</p>
     *
     * @param dagWrapper a {@link edu.cmu.tetradapp.model.SemGraphWrapper} object
     * @param params     a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public PcRunner(SemGraphWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getGraph(), params);
    }

    /**
     * <p>Constructor for PcRunner.</p>
     *
     * @param graphModel a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     * @param facts      a {@link edu.cmu.tetradapp.model.IndependenceFactsModel} object
     * @param params     a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public PcRunner(GraphWrapper graphModel, IndependenceFactsModel facts, Parameters params) {
        super(graphModel.getGraph(), params, null, facts.getFacts());
    }

    /**
     * <p>Constructor for PcRunner.</p>
     *
     * @param model             a {@link edu.cmu.tetradapp.model.IndependenceFactsModel} object
     * @param params            a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public PcRunner(IndependenceFactsModel model, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return new PcRunner(Dag.serializableInstance(), new Parameters());
    }

    /**
     * <p>getMeekRules.</p>
     *
     * @return a {@link edu.cmu.tetrad.search.utils.MeekRules} object
     */
    public MeekRules getMeekRules() {
        MeekRules rules = new MeekRules();
        rules.setMeekPreventCycles(this.isGuaranteeCpdag());
        rules.setKnowledge((Knowledge) getParams().get("knowledge", new Knowledge()));
        rules.setVerbose(false);
        return rules;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAlgorithmName() {
        return "PC";
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    /**
     * <p>execute.</p>
     */
    public void execute() {
        Knowledge knowledge = (Knowledge) getParams().get("knowledge", new Knowledge());
        int depth = getParams().getInt("depth", -1);
        Graph graph;
        Pc pc = new Pc(getIndependenceTest());
        pc.setKnowledge(knowledge);
        pc.setGuaranteeCpdag(isGuaranteeCpdag());
        pc.setDepth(depth);
        try {
            graph = pc.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println(graph);

        if (getSourceGraph() != null) {
            LayoutUtil.arrangeBySourceGraph(graph, getSourceGraph());
        } else if (knowledge.isDefaultToKnowledgeLayout()) {
            GraphSearchUtils.arrangeByKnowledgeTiers(graph, knowledge);
        } else {
            LayoutUtil.defaultLayout(graph);
        }

        setResultGraph(graph);
        setPcFields(pc);
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

        IndTestType testType = (IndTestType) (getParams()).get("indTestType", IndTestType.FISHER_Z);
        return new IndTestChooser().getTest(dataModel, getParams(), testType);
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
     * @return the names of the triple classifications. Coordinates with getTriplesList.
     */
    public List<String> getTriplesClassificationTypes() {
        return new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        return new ArrayList<>();
    }

    /**
     * <p>getAdj.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Edge> getAdj() {
        return new HashSet<>(this.pcAdjacent);
    }

    /**
     * <p>getNonAdj.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Edge> getNonAdj() {
        return new HashSet<>(this.pcNonadjacent);
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
    public Map<String, String> getParamSettings() {
        super.getParamSettings();
        this.paramSettings.put("Test", getIndependenceTest().toString());
        return this.paramSettings;
    }

    //========================== Private Methods ===============================//

    private boolean isGuaranteeCpdag() {
        return getParams().getBoolean(Params.GUARANTEE_CPDAG, false);
    }

    private void setPcFields(Pc pc) {
        this.pcAdjacent = pc.getAdjacencies();
        this.pcNonadjacent = pc.getNonadjacencies();
        List<Node> pcNodes = getGraph().getNodes();
    }
}







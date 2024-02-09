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
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.work_in_progress.SampleVcpcFast;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.util.IndTestType;

import java.io.Serial;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the PC algorithm.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SampleVcpcFastRunner extends AbstractAlgorithmRunner
        implements IndTestProducer {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The SEM to be used in the search.
     */
    private SemIm semIm;

    /**
     * The set of adjacencies found by the search.
     */
    private Set<Edge> sfVcpcAdjacent;

    /**
     * The set of apparent non-adjacencies found by the search.
     */
    private Set<Edge> sfVcpcApparent;

    /**
     * The set of definite non-adjacencies found by the search.
     */
    private Set<Edge> sfVcpcDefinite;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must contain a DataSet that is either a DataSet
     * or a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SampleVcpcFastRunner(DataWrapper dataWrapper, Parameters params) {
        super(dataWrapper, params, null);
    }

    /**
     * <p>Constructor for SampleVcpcFastRunner.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public SampleVcpcFastRunner(DataWrapper dataWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    /**
     * <p>Constructor for SampleVcpcFastRunner.</p>
     *
     * @param semImWrapper a {@link edu.cmu.tetradapp.model.SemImWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     */
    public SampleVcpcFastRunner(SemImWrapper semImWrapper, Parameters params, DataWrapper dataWrapper) {
        super(dataWrapper, params, null);
        this.semIm = semImWrapper.getSemIm();
    }


    /**
     * Constucts a wrapper for the given EdgeListGraph.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SampleVcpcFastRunner(Graph graph, Parameters params) {
        super(graph, params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public SampleVcpcFastRunner(Graph graph, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graph, params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SampleVcpcFastRunner(GraphWrapper graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public SampleVcpcFastRunner(GraphWrapper graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public SampleVcpcFastRunner(GraphSource graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SampleVcpcFastRunner(GraphSource graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
    }

    /**
     * <p>Constructor for SampleVcpcFastRunner.</p>
     *
     * @param dagWrapper a {@link edu.cmu.tetradapp.model.DagWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SampleVcpcFastRunner(DagWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getDag(), params);
    }

    /**
     * <p>Constructor for SampleVcpcFastRunner.</p>
     *
     * @param dagWrapper a {@link edu.cmu.tetradapp.model.DagWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public SampleVcpcFastRunner(DagWrapper dagWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dagWrapper.getDag(), params, knowledgeBoxModel);
    }

    /**
     * <p>Constructor for SampleVcpcFastRunner.</p>
     *
     * @param dagWrapper a {@link edu.cmu.tetradapp.model.SemGraphWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SampleVcpcFastRunner(SemGraphWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getGraph(), params);
    }

    /**
     * <p>Constructor for SampleVcpcFastRunner.</p>
     *
     * @param dagWrapper a {@link edu.cmu.tetradapp.model.SemGraphWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public SampleVcpcFastRunner(SemGraphWrapper dagWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dagWrapper.getGraph(), params, knowledgeBoxModel);
    }

    /**
     * <p>Constructor for SampleVcpcFastRunner.</p>
     *
     * @param model a {@link edu.cmu.tetradapp.model.IndependenceFactsModel} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SampleVcpcFastRunner(IndependenceFactsModel model, Parameters params) {
        super(model, params, null);
    }

    /**
     * <p>Constructor for SampleVcpcFastRunner.</p>
     *
     * @param model a {@link edu.cmu.tetradapp.model.IndependenceFactsModel} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     */
    public SampleVcpcFastRunner(IndependenceFactsModel model, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.SampleVcpcFastRunner} object
     */
    public static SampleVcpcFastRunner serializableInstance() {
        return new SampleVcpcFastRunner(Dag.serializableInstance(), new Parameters());
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    /**
     * <p>execute.</p>
     */
    public void execute() {
        Knowledge knowledge = (Knowledge) getParams().get("knowledge", new Knowledge());
        Parameters params = getParams();
        SampleVcpcFast sfvcpc = new SampleVcpcFast(getIndependenceTest());

        sfvcpc.setKnowledge(knowledge);
        sfvcpc.setMeekPreventCycles(this.isMeekPreventCycles());
        sfvcpc.setDepth(params.getInt("depth", -1));

        sfvcpc.setSemIm(this.semIm);
        Graph graph = sfvcpc.search();

        if (getSourceGraph() != null) {
            LayoutUtil.arrangeBySourceGraph(graph, getSourceGraph());
        } else if (knowledge.isDefaultToKnowledgeLayout()) {
            GraphSearchUtils.arrangeByKnowledgeTiers(graph, knowledge);
        } else {
            LayoutUtil.defaultLayout(graph);
        }

        setResultGraph(graph);
        setSfvcpcFields(sfvcpc);
    }

    //
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
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<>();
        names.add("Ambiguous Triples");
        return names;
    }

    /** {@inheritDoc} */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<>();
        Graph graph = getGraph();
        triplesList.add(GraphUtils.getAmbiguousTriplesFromGraph(node, graph));
        return triplesList;
    }

    /**
     * <p>getAdj.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Edge> getAdj() {
        return new HashSet<>(this.sfVcpcAdjacent);
    }

    /**
     * <p>getAppNon.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Edge> getAppNon() {
        return new HashSet<>(this.sfVcpcApparent);
    }

    /**
     * <p>getDefNon.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Edge> getDefNon() {
        return new HashSet<>(this.sfVcpcDefinite);
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
     * <p>getMeekRules.</p>
     *
     * @return a {@link edu.cmu.tetrad.search.utils.MeekRules} object
     */
    public MeekRules getMeekRules() {
        MeekRules meekRules = new MeekRules();
        meekRules.setMeekPreventCycles(this.isMeekPreventCycles());
        meekRules.setKnowledge((Knowledge) getParams().get("knowledge", new Knowledge()));
        return meekRules;
    }

    /** {@inheritDoc} */
    @Override
    public String getAlgorithmName() {
        return "Sample-VCPC-Fast";
    }

    /**
     * <p>Getter for the field <code>semIm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    public SemIm getSemIm() {
        return this.semIm;
    }

    //========================== Private Methods ===============================//

    private boolean isMeekPreventCycles() {
        Parameters params = getParams();
        if (params != null) {
            return params.getBoolean("MeekPreventCycles", false);
        }
        return false;
    }

    private void setSfvcpcFields(SampleVcpcFast sfvcpc) {
        this.sfVcpcAdjacent = sfvcpc.getAdjacencies();
        this.sfVcpcApparent = sfvcpc.getApparentNonadjacencies();
        this.sfVcpcDefinite = sfvcpc.getDefiniteNonadjacencies();
    }

}



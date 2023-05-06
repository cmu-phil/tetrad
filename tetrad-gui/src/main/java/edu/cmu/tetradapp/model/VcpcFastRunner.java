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
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.WIP.VcPcFast;
import edu.cmu.tetrad.search.test.IndTestDSep;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.GraphUtilsSearch;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.util.IndTestType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the PC algorithm.
 *
 * @author Joseph Ramsey
 */
public class VcpcFastRunner extends AbstractAlgorithmRunner
        implements IndTestProducer {
    static final long serialVersionUID = 23L;
    private Graph dag;
    private IndependenceFactsModel independenceFactsModel;
    private Graph trueGraph;
//    private Vcpc vcpc = null;


    private Set<Edge> fvcpcAdjacent;
    private Set<Edge> fvcpcApparent;
    private Set<Edge> fvcpcDefinite;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public VcpcFastRunner(DataWrapper dataWrapper, Parameters params) {
        super(dataWrapper, params, null);
    }

    public VcpcFastRunner(DataWrapper dataWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    public VcpcFastRunner(IndependenceFactsModel indModel, GraphWrapper graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
        this.dag = graphWrapper.getGraph();
        this.independenceFactsModel = indModel;
    }


    /**
     * Constucts a wrapper for the given
     * /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public VcpcFastRunner(Graph graph, Parameters params) {
        super(graph, params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public VcpcFastRunner(Graph graph, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graph, params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public VcpcFastRunner(GraphWrapper graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public VcpcFastRunner(GraphWrapper graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public VcpcFastRunner(GraphSource graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    public VcpcFastRunner(GraphSource graphWrapper, Parameters params, IndependenceFactsModel model) {
        super(graphWrapper.getGraph(), params);
        this.independenceFactsModel = model;
    }


    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public VcpcFastRunner(GraphSource graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
    }

    public VcpcFastRunner(DagWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getDag(), params);
    }

    public VcpcFastRunner(DagWrapper dagWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dagWrapper.getDag(), params, knowledgeBoxModel);
    }

    public VcpcFastRunner(SemGraphWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getGraph(), params);
    }

    public VcpcFastRunner(SemGraphWrapper dagWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dagWrapper.getGraph(), params, knowledgeBoxModel);
    }

    public VcpcFastRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper, Parameters params) {
        super(dataWrapper, params, null);
        this.trueGraph = graphWrapper.getGraph();
    }

    public VcpcFastRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.trueGraph = graphWrapper.getGraph();
    }

    public VcpcFastRunner(IndependenceFactsModel model, Parameters params) {
        super(model, params, null);
    }

    public VcpcFastRunner(IndependenceFactsModel model, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static VcpcFastRunner serializableInstance() {
        return new VcpcFastRunner(Dag.serializableInstance(), new Parameters());
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    public void execute() {
        Knowledge knowledge = (Knowledge) getParams().get("knowledge", new Knowledge());


        VcPcFast fvcpc = new VcPcFast(getIndependenceTest());
        fvcpc.setKnowledge(knowledge);
        fvcpc.setAggressivelyPreventCycles(this.isAggressivelyPreventCycles());
        fvcpc.setDepth(getParams().getInt("depth", -1));
        if (this.independenceFactsModel != null) {
            fvcpc.setFacts(this.independenceFactsModel.getFacts());
        }
        Graph graph = fvcpc.search();

        if (getSourceGraph() != null) {
            LayoutUtil.arrangeBySourceGraph(graph, getSourceGraph());
        } else if (knowledge.isDefaultToKnowledgeLayout()) {
            GraphUtilsSearch.arrangeByKnowledgeTiers(graph, knowledge);
        } else {
            LayoutUtil.circleLayout(graph, 200, 200, 150);
        }

        setResultGraph(graph);
        setVcpcFastFields(fvcpc);
    }

    public IndependenceTest getIndependenceTest() {
        if (this.dag != null) {
            return new IndTestDSep(getGraph());
        }

        Object dataModel = getDataModel();

        if (dataModel == null) {
            dataModel = getSourceGraph();
        }

        IndTestType testType = (IndTestType) (getParams()).get("indTestType", IndTestType.FISHER_Z);
        return new IndTestChooser().getTest(dataModel, getParams(), testType);
    }

    public Graph getGraph() {
        return getResultGraph();
    }

    public IndependenceFactsModel getIndependenceFactsModel() {
        return this.independenceFactsModel;
    }

    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<>();
        names.add("Ambiguous Triples");
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<>();
        Graph graph = getGraph();
        triplesList.add(GraphUtils.getAmbiguousTriplesFromGraph(node, graph));
        return triplesList;
    }

    public Set<Edge> getAdj() {
        return new HashSet<>(this.fvcpcAdjacent);
    }

    public Set<Edge> getAppNon() {
        return new HashSet<>(this.fvcpcApparent);
    }

    public Set<Edge> getDefNon() {
        return new HashSet<>(this.fvcpcDefinite);
    }

    public boolean supportsKnowledge() {
        return true;
    }

    public ImpliedOrientation getMeekRules() {
        MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(this.isAggressivelyPreventCycles());
        meekRules.setKnowledge((Knowledge) getParams().get("knowledge", new Knowledge()));
        return meekRules;
    }

    @Override
    public String getAlgorithmName() {
        return "VCPC-Fast";
    }

    //========================== Private Methods ===============================//

    private boolean isAggressivelyPreventCycles() {
        Parameters params = getParams();
        return params instanceof Parameters && params.getBoolean("aggressivelyPreventCycles", false);
    }

    private void setVcpcFastFields(VcPcFast fvcpc) {
        this.fvcpcAdjacent = fvcpc.getAdjacencies();
        this.fvcpcApparent = fvcpc.getApparentNonadjacencies();
        this.fvcpcDefinite = fvcpc.getDefiniteNonadjacencies();
        List<Node> fvcpcNodes = getGraph().getNodes();
    }

}



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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the PC algorithm.
 *
 * @author Joseph Ramsey
 */
public class VcpcRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, GraphSource {
    static final long serialVersionUID = 23L;
    private Graph dag;
    private IndependenceFactsModel independenceFactsModel = null;
    private Graph trueGraph;
//    private Vcpc vcpc = null;


    private Set<Edge>vcpcAdjacent;
    private Set<Edge>vcpcApparent;
    private Set<Edge>vcpcDefinite;
    private List<Node>vcpcNodes;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public VcpcRunner(DataWrapper dataWrapper, Parameters params) {
        super(dataWrapper, params, null);
    }

    public VcpcRunner(DataWrapper dataWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    public VcpcRunner(IndependenceFactsModel indModel, GraphWrapper graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
        this.dag = graphWrapper.getGraph();
        this.independenceFactsModel = indModel;
    }



    /**
     * Constucts a wrapper for the given
    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public VcpcRunner(Graph graph, Parameters params) {
        super(graph, params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public VcpcRunner(Graph graph, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graph, params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public VcpcRunner(GraphWrapper graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public VcpcRunner(GraphWrapper graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public VcpcRunner(GraphSource graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    public VcpcRunner(GraphSource graphWrapper, Parameters params, IndependenceFactsModel model) {
        super(graphWrapper.getGraph(), params);
        this.independenceFactsModel = model;
    }


    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public VcpcRunner(GraphSource graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
    }

    public VcpcRunner(DagWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getDag(), params);
    }

    public VcpcRunner(DagWrapper dagWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dagWrapper.getDag(), params, knowledgeBoxModel);
    }

    public VcpcRunner(SemGraphWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getGraph(), params);
    }

    public VcpcRunner(SemGraphWrapper dagWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dagWrapper.getGraph(), params, knowledgeBoxModel);
    }

    public VcpcRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper, Parameters params) {
        super(dataWrapper, params, null);
        this.trueGraph = graphWrapper.getGraph();
    }

    public VcpcRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.trueGraph = graphWrapper.getGraph();
    }

    public VcpcRunner(IndependenceFactsModel model, Parameters params) {
        super(model, params, null);
    }

    public VcpcRunner(IndependenceFactsModel model, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static VcpcRunner serializableInstance() {
        return new VcpcRunner(Dag.serializableInstance(), new Parameters());
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    public void execute() {
        IKnowledge knowledge = (IKnowledge) getParams().get("knowledge", new Knowledge2());
        Parameters searchParams = getParams();

        Parameters params =
                searchParams;


        Vcpc vcpc = new Vcpc(getIndependenceTest());
        vcpc.setKnowledge(knowledge);
        vcpc.setAggressivelyPreventCycles(this.isAggressivelyPreventCycles());
        vcpc.setDepth(params.getInt("depth", -1));
        if (independenceFactsModel != null) {
            vcpc.setFacts(independenceFactsModel.getFacts());
        }
        Graph graph = vcpc.search();

        if (getSourceGraph() != null) {
            GraphUtils.arrangeBySourceGraph(graph, getSourceGraph());
        }
        else if (knowledge.isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(graph, knowledge);
        }
        else {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }

        setResultGraph(graph);
        setVcpcFields(vcpc);
    }

    public IndependenceTest getIndependenceTest() {
        if (dag != null) {
            return new IndTestDSep(dag);
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
        return independenceFactsModel;
    }

    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<>();
//        names.add("ColliderDiscovery");
//        names.add("Noncolliders");
        names.add("Ambiguous Triples");
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<>();
        Graph graph = getGraph();
//        triplesList.add(DataGraphUtils.getCollidersFromGraph(node, graph));
//        triplesList.add(DataGraphUtils.getNoncollidersFromGraph(node, graph));
        triplesList.add(GraphUtils.getAmbiguousTriplesFromGraph(node, graph));
        return triplesList;
    }

    public Set<Edge> getAdj() {
        return new HashSet<>(vcpcAdjacent);
    }

    public Set<Edge> getAppNon() {
        return new HashSet<>(vcpcApparent);
    }

    public Set<Edge> getDefNon() {
        return new HashSet<>(vcpcDefinite);
    }

    public boolean supportsKnowledge() {
        return true;
    }

    public ImpliedOrientation getMeekRules() {
        MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(this.isAggressivelyPreventCycles());
        meekRules.setKnowledge((IKnowledge) getParams().get("knowledge", new Knowledge2()));
        return meekRules;
    }

    @Override
    public String getAlgorithmName() {
        return "VCPC";
    }

    //========================== Private Methods ===============================//

    private boolean isAggressivelyPreventCycles() {
        Parameters params = getParams();
        if (params instanceof Parameters) {
            return params.getBoolean("aggressivelyPreventCycles", false);
        }
        return false;
    }

    private void setVcpcFields(Vcpc vcpc) {
        vcpcAdjacent = vcpc.getAdjacencies();
        vcpcApparent = vcpc.getApparentNonadjacencies();
        vcpcDefinite = vcpc.getDefiniteNonadjacencies();
        vcpcNodes = getGraph().getNodes();
    }

//    public Vcpc getVcpc() {
//        return vcpc;
//    }
}



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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the PC algorithm.
 *
 * @author Joseph Ramsey
 */
public class PcLocalRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, GraphSource {
    static final long serialVersionUID = 23L;
    private Graph trueGraph;
    private Graph sourceGraph;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public PcLocalRunner(DataWrapper dataWrapper, PcLocalSearchParams params) {
        super(dataWrapper, params, null);
        this.sourceGraph = dataWrapper.getSourceGraph();
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public PcLocalRunner(DataWrapper dataWrapper, PcLocalSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.sourceGraph = dataWrapper.getSourceGraph();
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public PcLocalRunner(Graph graph, PcLocalSearchParams params) {
        super(graph, params);
        this.sourceGraph = graph;
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public PcLocalRunner(GraphWrapper graphWrapper, PcLocalSearchParams params) {
        super(graphWrapper.getGraph(), params);
        this.sourceGraph = graphWrapper.getGraph();
    }

    public PcLocalRunner(DagWrapper graphWrapper, PcLocalSearchParams params) {
        super(graphWrapper.getDag(), params);
    }

    public PcLocalRunner(SemGraphWrapper graphWrapper, PcLocalSearchParams params) {
        super(graphWrapper.getGraph(), params);
    }

    public PcLocalRunner(IndependenceFactsModel model, PcLocalSearchParams params) {
        super(model, params, null);
    }

    public PcLocalRunner(IndependenceFactsModel model, PcLocalSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcLocalRunner serializableInstance() {
        return new PcLocalRunner(Dag.serializableInstance(),
                PcLocalSearchParams.serializableInstance());
    }

    public ImpliedOrientation getMeekRules() {
        MeekRules rules = new MeekRules();
        rules.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
        rules.setKnowledge(getParams().getKnowledge());
        return rules;
    }

    @Override
    public String getAlgorithmName() {
        return "PC-Local";
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    public void execute() {
        IKnowledge knowledge = getParams().getKnowledge();

        IndependenceTest independenceTest = getIndependenceTest();
        PcLocalIndTestParams testParams = (PcLocalIndTestParams) getParams().getIndTestParams();

        PcLocal search = new PcLocal(independenceTest);

        search.setAggressivelyPreventCycles(isAggressivelyPreventCycles());

        search.setKnowledge(knowledge);

        Graph graph = search.search();

//        Jcpc1 search = new Jcpc1(independenceTest);
//
//        search.setMaxAdjacencies(testParams.getMaxAdjacencies());
//        search.setMaxIterations(testParams.getMaxIterations());
//        search.setStartFromEmptyGraph(testParams.isStartFromEmptyGraph());
//        search.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
//
//        search.setKnowledge(knowledge);
//
//        Graph graph = search.search();

        if (getSourceGraph() != null) {
            GraphUtils.arrangeBySourceGraph(graph, getSourceGraph());
        } else if (knowledge.isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(graph, knowledge);
        } else {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }

        setResultGraph(graph);
    }

    public IndependenceTest getIndependenceTest() {
        Object dataModel = getDataModel();

        if (dataModel == null) {
            dataModel = getSourceGraph();
        }

        IndTestType testType = (getParams()).getIndTestType();
        return new IndTestChooser().getTest(dataModel, getParams(), testType);
    }

    public Graph getGraph() {
        return getResultGraph();
    }

//    /**
//     * @return the names of the triple classifications. Coordinates with
//     */
//    public List<String> getTriplesClassificationTypes() {
//        return new LinkedList<String>();
//    }

    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<String>();
//        names.add("Colliders");
//        names.add("Noncolliders");
        names.add("Ambiguous Triples");
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<List<Triple>>();
        Graph graph = getGraph();
//        triplesList.add(DataGraphUtils.getCollidersFromGraph(node, graph));
//        triplesList.add(DataGraphUtils.getNoncollidersFromGraph(node, graph));
        triplesList.add(GraphUtils.getAmbiguousTriplesFromGraph(node, graph));
        return triplesList;
    }

//    /**
//     * @param node
//     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>.
//     */
//    public List<List<Triple>> getTriplesLists(Node node) {
//        return new LinkedList<List<Triple>>();
//    }

    /**
     * @return the underline triples for the given node. Non-null.
     */
    public List<Triple> getUnderlineTriples(Node node) {
        return new ArrayList<Triple>();
    }

    /**
     * @return the dotted underline triples for the given node. Non-null.
     */
    public List<Triple> getDottedUnderlineTriples(Node node) {
        return new ArrayList<Triple>();
    }

    public boolean supportsKnowledge() {
        return true;
    }

    @Override
    public Map<String, String> getParamSettings() {
        super.getParamSettings();
        paramSettings.put("Test", getIndependenceTest().toString());
        return paramSettings;
    }

    //========================== Private Methods ===============================//

    private boolean isAggressivelyPreventCycles() {
        SearchParams params = getParams();
        if (params instanceof MeekSearchParams) {
            return ((MeekSearchParams) params).isAggressivelyPreventCycles();
        }
        return false;
    }

}



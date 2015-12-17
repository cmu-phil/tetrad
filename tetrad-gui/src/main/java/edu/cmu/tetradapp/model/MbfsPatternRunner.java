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

import java.util.LinkedList;
import java.util.List;


/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the PC algorithm.
 *
 * @author Joseph Ramsey
 */
public class MbfsPatternRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, GraphSource {
    static final long serialVersionUID = 23L;
    private Graph trueGraph;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public MbfsPatternRunner(DataWrapper dataWrapper, PcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    public MbfsPatternRunner(DataWrapper dataWrapper, PcSearchParams params) {
        super(dataWrapper, params, new KnowledgeBoxModel(dataWrapper, new KnowledgeParams()));
    }

//    public MbfsPatternRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper, PcSearchParams params) {
//        super(dataWrapper, params, null);
//        this.trueGraph = graphWrapper.getGraph();
//    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public MbfsPatternRunner(Graph graph, PcSearchParams params) {
        super(graph, params);
    }

//    /**
//     * Constucts a wrapper for the given EdgeListGraph.
//     */
//    public MbfsPatternRunner(GraphWrapper graphWrapper, PcSearchParams params) {
//        super(graphWrapper.getGraph(), params);
//    }

    public MbfsPatternRunner(DagWrapper dagWrapper, PcSearchParams params) {
        super(dagWrapper.getDag(), params);
    }

//    public MbfsPatternRunner(SemGraphWrapper dagWrapper, PcSearchParams params) {
//        super(dagWrapper.getGraph(), params);
//    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return new PcRunner(Dag.serializableInstance(),
                PcSearchParams.serializableInstance());
    }

    public ImpliedOrientation getMeekRules() {
        MeekRules rules = new MeekRules();
        rules.setAggressivelyPreventCycles(this.isAggressivelyPreventCycles());
        rules.setKnowledge(getParams().getKnowledge());
        return rules;
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    public void execute() {
        IKnowledge knowledge = getParams().getKnowledge();

        Mbfs search = new Mbfs(getIndependenceTest(), -1);
        search.setDepth(getParams().getIndTestParams().getDepth());
//        search.setTrueGraph(trueGraph);

        Graph graph = search.search();

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

    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        return new LinkedList<String>();
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>
     * for the given node.
     * @param node The node that the classifications are for. All triple from adjacencies to this
     * node to adjacencies to this node through the given node will be considered.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        return new LinkedList<List<Triple>>();
    }

    public boolean supportsKnowledge() {
        return true;
    }

    //========================== Private Methods ===============================//

    private boolean isAggressivelyPreventCycles(){
        SearchParams params = getParams();
        if(params instanceof MeekSearchParams){
           return ((MeekSearchParams)params).isAggressivelyPreventCycles();
        }
        return false;
    }

}



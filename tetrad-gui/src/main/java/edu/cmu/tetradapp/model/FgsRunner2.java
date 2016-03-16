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
import edu.cmu.tetrad.sem.StandardizedSemIm;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.*;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the PC algorithm.
 *
 * @author Joseph Ramsey
 */
public class FgsRunner2 extends AbstractAlgorithmRunner
        implements IndTestProducer, GraphSource {
    static final long serialVersionUID = 23L;
    private Graph initialGraph = null;


    //============================CONSTRUCTORS============================//


    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public FgsRunner2(DataWrapper dataWrapper, PcSearchParams params) {
        super(dataWrapper, params, null);
    }

    public FgsRunner2(DataWrapper dataWrapper, PcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    // Starts PC from the given graph.
    public FgsRunner2(DataWrapper dataWrapper, GraphWrapper graphWrapper, PcSearchParams params) {
        super(dataWrapper, params, null);
        this.initialGraph = graphWrapper.getGraph();
    }

    public FgsRunner2(DataWrapper dataWrapper, GraphWrapper graphWrapper, PcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.initialGraph = graphWrapper.getGraph();
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public FgsRunner2(Graph graph, PcSearchParams params) {
        super(graph, params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public FgsRunner2(GraphWrapper graphWrapper, PcSearchParams params) {
        super(graphWrapper.getGraph(), params);
    }

    public FgsRunner2(GraphWrapper graphWrapper, KnowledgeBoxModel knowledgeBoxModel, PcSearchParams params) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    public FgsRunner2(DagWrapper dagWrapper, PcSearchParams params) {
        super(dagWrapper.getDag(), params);
    }

    public FgsRunner2(SemGraphWrapper dagWrapper, PcSearchParams params) {
        super(dagWrapper.getGraph(), params);
    }

    public FgsRunner2(GraphWrapper graphModel, IndependenceFactsModel facts, PcSearchParams params) {
        super(graphModel.getGraph(), params, null, facts.getFacts());
    }

    public FgsRunner2(IndependenceFactsModel model, PcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static FgsRunner2 serializableInstance() {
        return new FgsRunner2(Dag.serializableInstance(),
                PcSearchParams.serializableInstance());
    }

    public ImpliedOrientation getMeekRules() {
        MeekRules rules = new MeekRules();
        rules.setAggressivelyPreventCycles(this.isAggressivelyPreventCycles());
        rules.setKnowledge(getParams().getKnowledge());
        return rules;
    }

    @Override
    public String getAlgorithmName() {
        return "FGS2";
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    public void execute() {
        IKnowledge knowledge = getParams().getKnowledge();
        int depth = getParams().getIndTestParams().getDepth();
        Fgs pc = new Fgs(new ScoredIndTest(getIndependenceTest()));
        pc.setKnowledge(knowledge);
        pc.setDepth(depth);
        pc.setInitialGraph(initialGraph);
        Graph graph = pc.search();

        System.out.println(graph);

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

    /**
     * @return the names of the triple classifications. Coordinates with getTriplesList.
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<String>();
//        names.add("Colliders");
//        names.add("Noncolliders");
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>
     * for the given node.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<List<Triple>>();
//        Graph graph = getGraph();
//        triplesList.add(DataGraphUtils.getCollidersFromGraph(node, graph));
//        triplesList.add(DataGraphUtils.getNoncollidersFromGraph(node, graph));
        return triplesList;
    }

    public boolean supportsKnowledge() {
        return true;
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






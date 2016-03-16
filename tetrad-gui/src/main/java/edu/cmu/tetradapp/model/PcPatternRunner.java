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

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the PC algorithm.
 *
 * @author Joseph Ramsey
 */
public class PcPatternRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, GraphSource {
    static final long serialVersionUID = 23L;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public PcPatternRunner(DataWrapper dataWrapper, PcSearchParams params) {
        super(dataWrapper, params, null);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public PcPatternRunner(DataWrapper dataWrapper, PcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public PcPatternRunner(Graph graph, PcSearchParams params) {
        super(graph, params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public PcPatternRunner(GraphWrapper graphWrapper, PcSearchParams params) {
        super(graphWrapper.getGraph(), params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public PcPatternRunner(GraphSource graphWrapper, PcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    public PcPatternRunner(DagWrapper dagWrapper, PcSearchParams params) {
        super(dagWrapper.getDag(), params);
    }

    public PcPatternRunner(SemGraphWrapper dagWrapper, PcSearchParams params) {
        super(dagWrapper.getGraph(), params);
    }

    public PcPatternRunner(IndependenceFactsModel model, PcSearchParams params) {
        super(model, params, null);
    }

    public PcPatternRunner(IndependenceFactsModel model, PcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcPatternRunner serializableInstance() {
        return new PcPatternRunner(Dag.serializableInstance(),
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
        return "PC-Pattern";
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    public void execute() {
        IKnowledge knowledge = getParams().getKnowledge();
        int depth = getParams().getIndTestParams().getDepth();

        PcPattern pcSearch = new PcPattern(getIndependenceTest());
        pcSearch.setKnowledge(knowledge);
        pcSearch.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
        pcSearch.setDepth(depth);
        Graph graph = pcSearch.search();

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
        Graph graph = getGraph();
//        triplesList.add(DataGraphUtils.getCollidersFromGraph(node, graph));
//        triplesList.add(DataGraphUtils.getNoncollidersFromGraph(node, graph));
        return triplesList;
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




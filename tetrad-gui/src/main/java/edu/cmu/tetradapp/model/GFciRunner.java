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
 * Extends AbstractAlgorithmRunner to produce a wrapper for the FCI algorithm.
 *
 * @author Joseph Ramsey
 */
public class GFciRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, GraphSource {
    static final long serialVersionUID = 23L;

    //=========================CONSTRUCTORS================================//

    public GFciRunner(DataWrapper dataWrapper, GFciSearchParams params) {
        super(dataWrapper, params, null);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public GFciRunner(GraphSource graphWrapper, PcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }
    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public GFciRunner(DataWrapper dataWrapper, GFciSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    public GFciRunner(Graph graph, FciSearchParams params) {
        super(graph, params);
    }


    public GFciRunner(GraphWrapper graphWrapper, GFciSearchParams params) {
        super(graphWrapper.getGraph(), params);
    }

    public GFciRunner(DagWrapper dagWrapper, GFciSearchParams params) {
        super(dagWrapper.getDag(), params);
    }

    public GFciRunner(SemGraphWrapper dagWrapper, GFciSearchParams params) {
        super(dagWrapper.getGraph(), params);
    }

    public GFciRunner(IndependenceFactsModel model, GFciSearchParams params) {
        super(model, params, null);
    }

    public GFciRunner(IndependenceFactsModel model, GFciSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static GFciRunner serializableInstance() {
        return new GFciRunner(Dag.serializableInstance(),
                FciSearchParams.serializableInstance());
    }

    //=================PUBLIC METHODS OVERRIDING ABSTRACT=================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public void execute() {
        IKnowledge knowledge = getParams().getKnowledge();
        SearchParams searchParams = getParams();

        GFciIndTestParams indTestParams = (GFciIndTestParams) searchParams.getIndTestParams();

        Graph graph;

        if (getIndependenceTest() instanceof  IndTestDSep) {
            final DagToPag dagToPag = new DagToPag(((IndTestDSep) getIndependenceTest()).getGraph());
            dagToPag.setCompleteRuleSetUsed(indTestParams.isCompleteRuleSetUsed());
            graph = dagToPag.convert();
        }
        else {
            GFci fci = new GFci(getIndependenceTest());
            fci.setKnowledge(knowledge);
            fci.setCompleteRuleSetUsed(indTestParams.isCompleteRuleSetUsed());
            fci.setPossibleDsepSearchDone(indTestParams.isPossibleDsepDone());
            fci.setMaxPathLength(indTestParams.getMaxReachablePathLength());
            fci.setDepth(indTestParams.getDepth());
            fci.setPenaltyDiscount(indTestParams.getPenaltyDiscount());
            fci.setSamplePrior(indTestParams.getSamplePrior());
            fci.setStructurePrior(indTestParams.getStructurePrior());
            fci.setCompleteRuleSetUsed(false);
            fci.setPenaltyDiscount(indTestParams.getPenaltyDiscount());
            fci.setFaithfulnessAssumed(indTestParams.isFaithfulnessAssumed());
            graph = fci.search();
        }

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

        SearchParams params = getParams();
        IndTestType testType;

        if (getParams() instanceof BasicSearchParams) {
            BasicSearchParams _params = (BasicSearchParams) params;
            testType = _params.getIndTestType();
        }
        else {
            GFciSearchParams _params = (GFciSearchParams) params;
            testType = _params.getIndTestType();
        }

        return new IndTestChooser().getTest(dataModel, params, testType);
    }

    public Graph getGraph() {
        return getResultGraph();
    }

    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<String>();
//        names.add("Definite Colliders");
//        names.add("Definite Noncolliders");
        names.add("Ambiguous Triples");
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<List<Triple>>();
        Graph graph = getGraph();
//        triplesList.add(DataGraphUtils.getDefiniteCollidersFromGraph(node, graph));
//        triplesList.add(DataGraphUtils.getDefiniteNoncollidersFromGraph(node, graph));
        triplesList.add(GraphUtils.getAmbiguousTriplesFromGraph(node, graph));
        return triplesList;
    }


    @Override
    public String getAlgorithmName() {
        return "GFCI";
    }
}




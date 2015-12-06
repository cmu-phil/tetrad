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
import java.util.LinkedList;
import java.util.List;


/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the PC algorithm.
 *
 * @author Joseph Ramsey
 */
public class JpcRunner extends AbstractAlgorithmRunner
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
    public JpcRunner(DataWrapper dataWrapper, JpcSearchParams params) {
        super(dataWrapper, params, null);
        this.sourceGraph = dataWrapper.getSourceGraph();
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public JpcRunner(DataWrapper dataWrapper, JpcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.sourceGraph = dataWrapper.getSourceGraph();
    }

    public JpcRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper, JpcSearchParams params) {
        super(dataWrapper, params, null);
        this.trueGraph = graphWrapper.getGraph();
        this.sourceGraph = dataWrapper.getSourceGraph();
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public JpcRunner(Graph graph, JpcSearchParams params) {
        super(graph, params);
        this.sourceGraph = graph;
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public JpcRunner(GraphWrapper graphWrapper, JpcSearchParams params) {
        super(graphWrapper.getGraph(), params);
        this.sourceGraph = graphWrapper.getGraph();
    }

    public JpcRunner(DagWrapper graphWrapper, JpcSearchParams params) {
        super(graphWrapper.getDag(), params);
    }

    public JpcRunner(SemGraphWrapper graphWrapper, JpcSearchParams params) {
        super(graphWrapper.getGraph(), params);
    }

    public JpcRunner(IndependenceFactsModel model, JpcSearchParams params) {
        super(model, params, null);
    }

    public JpcRunner(IndependenceFactsModel model, JpcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static JpcRunner serializableInstance() {
        return new JpcRunner(Dag.serializableInstance(),
                JpcSearchParams.serializableInstance());
    }

    public ImpliedOrientation getMeekRules() {
        MeekRules rules = new MeekRules();
        rules.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
        rules.setKnowledge(getParams().getKnowledge());
        return rules;
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    public void execute() {
        IKnowledge knowledge = getParams().getKnowledge();

        IndependenceTest independenceTest = getIndependenceTest();
        JpcIndTestParams testParams = (JpcIndTestParams) getParams().getIndTestParams();
        boolean useCpc = testParams.isUseCpc();
        Graph graph;

//        if (useCpc) {
//            Jcpc2 search = new Jcpc2(independenceTest);
//
//            search.setSoftmaxAdjacencies(testParams.getSoftmaxAdjacencies());
//            search.setMaxDescendantPath(testParams.getMaxDescendantPath());
//            search.setMaxIterations(testParams.getMaxIterations());
//            search.setStartFromEmptyGraph(testParams.isStartFromEmptyGraph());
//            search.setAggressivelyPreventCycles(isAggressivelyPreventCycles());
//
//
//            graph = search.search();
//        }
//
//        else {
            Jpc search = new Jpc(independenceTest);

            search.setSoftmaxAdjacencies(testParams.getMaxAdjacencies());
            search.setMaxDescendantPath(testParams.getMaxDescendantPath());
            search.setMaxIterations(testParams.getMaxIterations());
//            search.setStartFromEmptyGraph(testParams.isStartFromEmptyGraph());
            search.setKnowledge(knowledge);
            search.setAggressivelyPreventCycles(isAggressivelyPreventCycles());

//            if (useCpc) {
//                search.setAlgorithmType(Jpc.AlgorithmType.CPC);
//            } else {
//                search.setAlgorithmType(Jpc.AlgorithmType.PC);
//            }

            graph = search.search();
//        }



//        double maxP = Double.NEGATIVE_INFINITY;
//        Graph maxGraph = null;
//
//        for (int _p = 1; _p < 50; _p++) {
//            double alpha = _p / (double) 100;
//            System.out.println("_p = " + alpha);
//
//            independenceTest.setAlpha(alpha);
////            search.setAlgorithmType((Jpc.AlgorithmType.CPC));
//            Graph _graph = search.search();
//
//            Graph _graph2 = MbUtils.generatePatternDags(_graph, true).get(0);
//
//            SemPm pm = new SemPm(_graph2);
//
//            Object dataModel = getDataModel();
//
//            SemEstimator est = new SemEstimator((DataSet) dataModel, pm);
//            SemIm sem = est.estimate();
//
//            if (sem == null) continue;
//
//            double pValue = sem.getPValue();
//
//            System.out.println("Pvalue = " + pValue);
//
//            if (pValue > maxP) {
//                maxP = pValue;
//                maxGraph = _graph;
//                System.out.println("maxP = " + maxP);
//            }
//        }
//
//        Graph graph = maxGraph;

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
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        return new LinkedList<String>();
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        return new LinkedList<List<Triple>>();
    }

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

    //========================== Private Methods ===============================//

    private boolean isAggressivelyPreventCycles() {
        SearchParams params = getParams();
        if (params instanceof MeekSearchParams) {
            return ((MeekSearchParams) params).isAggressivelyPreventCycles();
        }
        return false;
    }

}



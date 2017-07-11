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
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the PC algorithm.
 *
 * @author Joseph Ramsey
 */
public class PcdRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, GraphSource {
    static final long serialVersionUID = 23L;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public PcdRunner(DataWrapper dataWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public PcdRunner(DataWrapper dataWrapper, Parameters params) {
        super(dataWrapper, params, null);
    }
    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public PcdRunner(Graph graph, Parameters params) {
        super(graph, params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public PcdRunner(Graph graph, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graph, params, knowledgeBoxModel);
    }
    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public PcdRunner(GraphWrapper graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public PcdRunner(GraphWrapper graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }
    
    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public PcdRunner(GraphSource graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public PcdRunner(GraphSource graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
    }
    
    public PcdRunner(DagWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getDag(), params);
    }

    public PcdRunner(DagWrapper dagWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dagWrapper.getDag(), params, knowledgeBoxModel);
    }

    public PcdRunner(SemGraphWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getGraph(), params);
    }

    public PcdRunner(SemGraphWrapper dagWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dagWrapper.getGraph(), params, knowledgeBoxModel);
    }

    
    public ImpliedOrientation getMeekRules() {
        MeekRules rules = new MeekRules();
        rules.setKnowledge((IKnowledge) getParams().get("knowledge", new Knowledge2()));
        return rules;
    }

    @Override
    public String getAlgorithmName() {
        return "PCD";
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcdRunner serializableInstance() {
        return new PcdRunner(Dag.serializableInstance(), new Parameters());
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    public void execute() {
        IKnowledge knowledge = (IKnowledge) getParams().get("knowledge", new Knowledge2());
        Parameters searchParams = getParams();

        Parameters params =
                searchParams;

        Pcd pc = new Pcd(getIndependenceTest());
        pc.setKnowledge(knowledge);
        pc.setDepth(params.getInt("depth", -1));

        Graph graph;

        try {
            graph = pc.search();
        } catch (UnsupportedOperationException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), "There was an error, probably because the " +
                    "independence test doesn't support determinististic checks.");
            return;
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

        IndTestType testType = (IndTestType) (getParams()).get("indTestType", IndTestType.FISHER_Z);
        IndependenceTest test = new IndTestChooser().getTest(dataModel, getParams(), testType);

        System.out.println(test);

        return test;
    }

    public Graph getGraph() {
        return getResultGraph();
    }

    /**
     * @return the names of the triple classifications. Coordinates with getTriplesList.
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<>();
//        names.add("ColliderDiscovery");
//        names.add("Noncolliders");
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>
     * for the given node.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<>();
        Graph graph = getGraph();
//        triplesList.add(DataGraphUtils.getCollidersFromGraph(node, graph));
//        triplesList.add(DataGraphUtils.getNoncollidersFromGraph(node, graph));
        return triplesList;
    }

}






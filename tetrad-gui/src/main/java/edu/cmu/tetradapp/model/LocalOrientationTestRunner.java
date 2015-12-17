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

import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.MeekRules;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the PC algorithm.
 *
 * @author Joseph Ramsey
 */
public class LocalOrientationTestRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, GraphSource {
    static final long serialVersionUID = 23L;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public LocalOrientationTestRunner(DataWrapper dataWrapper,
            PcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public LocalOrientationTestRunner(Graph graph, PcSearchParams params) {
        super(graph, params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public LocalOrientationTestRunner(GraphWrapper graphWrapper,
            PcSearchParams params) {
        super(graphWrapper.getGraph(), params);
    }

    public LocalOrientationTestRunner(DagWrapper dagWrapper,
            PcSearchParams params) {
        super(dagWrapper.getDag(), params);
    }

    public LocalOrientationTestRunner(SemGraphWrapper dagWrapper,
            PcSearchParams params) {
        super(dagWrapper.getGraph(), params);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static LocalOrientationTestRunner serializableInstance() {
        return new LocalOrientationTestRunner(Dag.serializableInstance(),
                PcSearchParams.serializableInstance());
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public void execute() {
        Graph graph1 = new EdgeListGraph(getSourceGraph());
        SearchGraphUtils.basicPattern(graph1, false);

        IndependenceTest test = getIndependenceTest();

        List<Node> variables = test.getVariables();
        List<Node> nodes = new LinkedList<Node>();

        for (Node variable : variables) {
            nodes.add(variable);
        }

        Graph graph2 = new EdgeListGraph(nodes);

        Set<Edge> edges = graph1.getEdges();

        for (Edge edge : edges) {
            Node var1 = test.getVariable(edge.getNode1().getName());
            Node var2 = test.getVariable(edge.getNode2().getName());

            if (var1 == null || var2 == null) {
                continue;
            }

            graph2.addUndirectedEdge(var1, var2);
        }

        SearchGraphUtils.orientCollidersLocally(new Knowledge2(), graph2,
                getIndependenceTest(), -1);
        new MeekRules().orientImplied(graph2);

        setResultGraph(graph2);
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

}






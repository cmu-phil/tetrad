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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.mb.HitonMb;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the MB Fan Search
 * algorithm.
 *
 * @author Frank Wimberly after Joe Ramsey's PcRunner
 */
public class HitonRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, GraphSource {
    static final long serialVersionUID = 23L;

    //=========================CONSTRUCTORS===============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public HitonRunner(DataWrapper dataWrapper, MbSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public HitonRunner(Graph graph, MbSearchParams params) {
        super(graph, params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public HitonRunner(GraphWrapper dagWrapper, MbSearchParams params) {
        super(dagWrapper.getGraph(), params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public HitonRunner(DagWrapper dagWrapper, MbSearchParams params) {
        super(dagWrapper.getDag(), params);
    }

    public HitonRunner(SemGraphWrapper dagWrapper,
                             BasicSearchParams params) {
        super(dagWrapper.getGraph(), params);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static HitonRunner serializableInstance() {
        return new HitonRunner(DataWrapper.serializableInstance(),
                MbSearchParams.serializableInstance(), KnowledgeBoxModel.serializableInstance());
    }

    //=================PUBLIC METHODS OVERRIDING ABSTRACT=================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public void execute() {
        int pcDepth = ((MbSearchParams) getParams()).getDepth();
        HitonMb search =
                new HitonMb(getIndependenceTest(), pcDepth, false);
//        SearchParams params = getParams();
//        if (params instanceof MeekSearchParams) {
//            search.setAggressivelyPreventCycles(((MeekSearchParams) params).isAggressivelyPreventCycles());
//        }
//        Knowledge knowledge = getParams().getKnowledge();
//        search.setKnowledge(knowledge);
        String targetName = ((MbSearchParams) getParams()).getTargetName();
        List<Node> nodes = search.findMb(targetName);

        Graph graph = new EdgeListGraph();

        for (Node node : nodes) {
            graph.addNode(node);
        }

        setResultGraph(graph);

        if (getSourceGraph() != null) {
            GraphUtils.arrangeBySourceGraph(graph, getSourceGraph());
        }
        else {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }
    }

    public IndependenceTest getIndependenceTest() {
        Object dataModel = getDataModel();

        if (dataModel == null) {
            dataModel = getSourceGraph();
        }

        MbSearchParams params = (MbSearchParams) getParams();
        IndTestType testType = params.getIndTestType();
        return new IndTestChooser().getTest(dataModel, params, testType);
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
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

    @Override
    public String getAlgorithmName() {
        return "HITON";
    }
}





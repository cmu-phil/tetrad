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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.LinkedList;
import java.util.List;


/**
 * Compares a target workbench with a reference workbench by counting errors of
 * omission and commission.  (for edge presence only, not orientation).
 *
 * @author Joseph Ramsey
 * @author Erin Korber (added remove latents functionality July 2004)
 */
public final class GraphComparison implements SessionModel {
    static final long serialVersionUID = 23L;
    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * @serial Cannot be null.
     */
    private GraphComparisonParams params;

    /**
     * The target workbench.
     *
     * @serial Cannot be null.
     */
    private final Graph targetGraph;

    /**
     * The workbench to which the target workbench is being compared.
     *
     * @serial Cannot be null.
     */
    private final Graph referenceGraph;

    /**
     * The true DAG, if available. (May be null.)
     */
    private Graph trueGraph;

    //=============================CONSTRUCTORS==========================//

    /**
     * Compares the results of a Pc to a reference workbench by counting errors
     * of omission and commission. The counts can be retrieved using the methods
     * <code>countOmissionErrors</code> and <code>countCommissionErrors</code>.
     */
    public GraphComparison(SessionModel model1, SessionModel model2,
                           GraphComparisonParams params) {
        if (params == null) {
            throw new NullPointerException("Params must not be null");
        }

        // Need to be able to construct this object even if the models are
        // null. Otherwise the interface is annoying.
        if (model2 == null) {
            model2 = new DagWrapper(new Dag());
        }

        if (model1 == null) {
            model1 = new DagWrapper(new Dag());
        }

        if (!(model1 instanceof GraphSource) ||
                !(model2 instanceof GraphSource)) {
            throw new IllegalArgumentException("Must be graph sources.");
        }

        this.params = params;

        String referenceName = this.params.getReferenceGraphName();

        if (referenceName == null) {
            throw new IllegalArgumentException("Must specify a reference graph.");
//            this.referenceGraph = ((GraphSource) model1).getGraph();
//            this.targetGraph = ((GraphSource) model2).getGraph();
//            this.params.setReferenceGraphName(model1.getName());
        } else if (referenceName.equals(model1.getName())) {
            this.referenceGraph = ((GraphSource) model1).getGraph();
            this.targetGraph = ((GraphSource) model2).getGraph();
        } else if (referenceName.equals(model2.getName())) {
            this.referenceGraph = ((GraphSource) model2).getGraph();
            this.targetGraph = ((GraphSource) model1).getGraph();
        } else {
            throw new IllegalArgumentException(
                    "Neither of the supplied session " + "models is named '" +
                            referenceName + "'.");
        }

        Graph alteredRefGraph;

        //Normally, one's target graph won't have latents, so we'll want to
        // remove them from the ref graph to compare, but algorithms like
        // MimBuild might not want to do this.
        if (this.params != null && this.params.isKeepLatents()) {
            alteredRefGraph = this.referenceGraph;
        } else {
            alteredRefGraph = removeLatent(this.referenceGraph);
        }

        GraphUtils.GraphComparison comparison = SearchGraphUtils.
                getGraphComparison(targetGraph, alteredRefGraph);

        if (this.params != null) {
            this.params.addRecord(comparison);
        }

        TetradLogger.getInstance().log("info", "Graph Comparison");
        TetradLogger.getInstance().log("comparison", getComparisonString());
    }

    public GraphComparison(GraphWrapper referenceGraph,
                           AbstractAlgorithmRunner algorithmRunner,
                           GraphComparisonParams params) {
        this(referenceGraph, (SessionModel) algorithmRunner,
                params);
    }

    public GraphComparison(GraphWrapper referenceWrapper,
                           GraphWrapper targetWrapper, GraphComparisonParams params) {
        this(referenceWrapper, (SessionModel) targetWrapper,
                params);
    }

    public GraphComparison(DagWrapper referenceGraph,
                           AbstractAlgorithmRunner algorithmRunner,
                           GraphComparisonParams params) {
        this(referenceGraph, (SessionModel) algorithmRunner,
                params);
    }

    public GraphComparison(DagWrapper referenceWrapper,
                           GraphWrapper targetWrapper, GraphComparisonParams params) {
        this(referenceWrapper, (SessionModel) targetWrapper,
                params);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static GraphComparison serializableInstance() {
        DagWrapper wrapper1 = DagWrapper.serializableInstance();
        wrapper1.setName("Ref");
        DagWrapper wrapper2 = DagWrapper.serializableInstance();
        GraphComparisonParams graphComparisonParams = GraphComparisonParams.serializableInstance();
        graphComparisonParams.setReferenceGraphName("Ref");
        return new GraphComparison(wrapper1, wrapper2, graphComparisonParams);
    }

    //==============================PUBLIC METHODS========================//

    public DataSet getDataSet() {
        return params.getDataSet();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComparisonString() {
        return SearchGraphUtils.graphComparisonString(getParams().getTargetGraphName(), targetGraph,
                getParams().getReferenceGraphName(), referenceGraph, false);
    }

    //adjacent to the latent node with an undirected edge (edge type doesnt matter).
    private static Graph removeLatent(Graph g) {
        Graph result = new EdgeListGraph(g);
        result.setGraphConstraintsChecked(false);

        List<Node> allNodes = g.getNodes();
        LinkedList<Node> toBeRemoved = new LinkedList<Node>();

        for (Node curr : allNodes) {
            if (curr.getNodeType() == NodeType.LATENT) {
                List<Node> adj = result.getAdjacentNodes(curr);

                for (int i = 0; i < adj.size(); i++) {
                    Node a = adj.get(i);
                    for (int j = i + 1; j < adj.size(); j++) {
                        Node b = adj.get(j);

                        if (!result.isAdjacentTo(a, b)) {
                            result.addEdge(Edges.undirectedEdge(a, b));
                        }
                    }
                }

                toBeRemoved.add(curr);
            }
        }

        result.removeNodes(toBeRemoved);
        return result;
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

        if (params == null) {
            throw new NullPointerException();
        }

        if (targetGraph == null) {
            throw new NullPointerException();
        }

        if (referenceGraph == null) {
            throw new NullPointerException();
        }
    }

    public Graph getTrueGraph() {
        return trueGraph;
    }

    public void setTrueGraph(Graph trueGraph) {
        this.trueGraph = trueGraph;
    }

    public GraphComparisonParams getParams() {
        return params;
    }
}



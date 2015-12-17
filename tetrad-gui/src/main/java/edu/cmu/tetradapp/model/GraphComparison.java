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

    /**
     * @serial
     * @deprecated
     */
    private int numMissingEdges;

    /**
     * @serial
     * @deprecated
     */
    private int numCorrectEdges;

    /**
     * @serial
     * @deprecated
     */
    private int commissionErrors;

    /**
     * The number of correct edges the last time they were counted.
     *
     * @serial Range greater than or equal to 0.
     */
    private int adjCorrect;

    /**
     * The number of errors of commission that last time they were counted.
     *
     * @serial Range greater than or equal to 0.
     */
    private int adjFp;

    /**
     * The number of errors of omission the last time they were counted.
     *
     * @serial Range greater than or equal to 0.                                   GraphParams param
     */
    private int adjFn;

    /**
     * The number of correct edges the last time they were counted.
     *
     * @serial Range greater than or equal to 0.
     */
    private int arrowptCorrect;

    /**
     * The number of errors of commission that last time they were counted.
     *
     * @serial Range greater than or equal to 0.
     */
    private int arrowptFp;

    /**
     * The number of errors of omission the last time they were counted.
     *
     * @serial Range greater than or equal to 0.
     */
    private int arrowptFn;

    private int twoCycleCorrect;
    private int twoCycleFp;
    private int twoCycleFn;

    /**
     * @serial
     * @deprecated
     */
    private int arrowptAfp;

    /**
     * @serial
     * @deprecated
     */
    private int arrowptAfn;

    /**
     * The list of edges that were added to the target graph. These are
     * new adjacencies.
     */
    private List<Edge> edgesAdded;

    /**
     * The list of edges that were removed from the reference graphs. These
     * are missing adjacencies.
     */
    private List<Edge> edgesRemoved;

    /**
     * The list of edges that were reoriented from the reference to the
     * target graph, as they were in the reference graph. This list
     * coordinates with <code>edgesReorientedTo</code>, in that
     * the i'th element of <code>edgesReorientedFrom</code> and the ith
     * element of <code>edgesReorientedTo</code> represent the same
     * adjacency.
     */
    private List<Edge> edgesReorientedFrom;

    /**
     * The list of edges that were reoriented from the reference to the
     * target graph, as they are in the target graph. This list
     * coordinates with <code>edgesReorientedFrom</code>, in that
     * the i'th element of <code>edgesReorientedFrom</code> and the ith
     * element of <code>edgesReorientedTo</code> represent the same
     * adjacency.
     */
    private List<Edge> edgesReorientedTo;
    private boolean printStars = true;

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

        this.adjFn = comparison.getAdjFn();
        this.adjFp = comparison.getAdjFp();
        this.adjCorrect = comparison.getAdjCorrect();
        this.arrowptFn = comparison.getArrowptFn();
        this.arrowptFp = comparison.getArrowptFp();
        this.arrowptCorrect = comparison.getArrowptCorrect();
        this.twoCycleFn = comparison.getTwoCycleFn();
        this.twoCycleFp = comparison.getTwoCycleFp();
        this.twoCycleCorrect = comparison.getTwoCycleCorrect();

        this.edgesAdded = comparison.getEdgesAdded();
        this.edgesRemoved = comparison.getEdgesRemoved();
        this.edgesReorientedFrom = comparison.getEdgesReorientedFrom();
        this.edgesReorientedTo = comparison.getEdgesReorientedTo();

        if (this.params != null) {
            this.params.addRecord(getAdjCorrect(), getAdjFn(), getAdjFp(),
                    getArrowptCorrect(), getArrowptFn(), getArrowptFp(),
                    getTwoCycleCorrect(), getTwoCycleFn(), getTwoCycleFp());
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

    public GraphComparison(Graph referenceGraph, Graph targetGraph) {
        this.referenceGraph = referenceGraph;
        this.targetGraph = targetGraph;
        Graph alteredRefGraph;

        //Normally, one's target graph won't have latents, so we'll want to
        // remove them from the ref graph to compare, but algorithms like
        // MimBuild might not want to do this.
        if (params != null && params.isKeepLatents()) {
            alteredRefGraph = this.referenceGraph;
        } else {
            alteredRefGraph = removeLatent(this.targetGraph);
        }

        GraphUtils.GraphComparison comparison = SearchGraphUtils.
                getGraphComparison(this.targetGraph, alteredRefGraph);

        this.adjFn = comparison.getAdjFn();
        this.adjFp = comparison.getAdjFp();
        this.adjCorrect = comparison.getAdjCorrect();
        this.arrowptFn = comparison.getArrowptFn();
        this.arrowptFp = comparison.getArrowptFp();
        this.arrowptCorrect = comparison.getArrowptCorrect();

        this.edgesAdded = comparison.getEdgesAdded();
        this.edgesRemoved = comparison.getEdgesRemoved();
        this.edgesReorientedFrom = comparison.getEdgesReorientedFrom();
        this.edgesReorientedTo = comparison.getEdgesReorientedTo();

        if (params != null) {
            params.addRecord(getAdjCorrect(), getAdjFn(), getAdjFp(),
                    getArrowptCorrect(), getArrowptFn(), getArrowptFp(),
                    getTwoCycleCorrect(), getTwoCycleFn(), getTwoCycleFp());
        }

        TetradLogger.getInstance().log("info", "Graph Comparison");
        TetradLogger.getInstance().log("comparison", getComparisonString());
    }

    public GraphComparison(Graph referenceGraph, Graph targetGraph,
                           Graph trueGraph) {
        this.referenceGraph = referenceGraph;
        this.targetGraph = targetGraph;
        this.trueGraph = trueGraph;
        Graph alteredRefGraph;

        //Normally, one's target graph won't have latents, so we'll want to
        // remove them from the ref graph to compare, but algorithms like
        // MimBuild might not want to do this.
        if (params != null && params.isKeepLatents()) {
            alteredRefGraph = this.referenceGraph;
        } else {
            alteredRefGraph = removeLatent(this.targetGraph);
        }

        GraphUtils.GraphComparison comparison = SearchGraphUtils.
                getGraphComparison(this.targetGraph, alteredRefGraph);

        this.adjFn = comparison.getAdjFn();
        this.adjFp = comparison.getAdjFp();
        this.adjCorrect = comparison.getAdjCorrect();
        this.arrowptFn = comparison.getArrowptFn();
        this.arrowptFp = comparison.getArrowptFp();
        this.arrowptCorrect = comparison.getArrowptCorrect();

        this.edgesAdded = comparison.getEdgesAdded();
        this.edgesRemoved = comparison.getEdgesRemoved();
        this.edgesReorientedFrom = comparison.getEdgesReorientedFrom();
        this.edgesReorientedTo = comparison.getEdgesReorientedTo();

        if (params != null) {
            params.addRecord(getAdjCorrect(), getAdjFn(), getAdjFp(),
                    getArrowptCorrect(), getArrowptFn(), getArrowptFp(),
                    getTwoCycleCorrect(), getTwoCycleFn(), getTwoCycleFp());
        }

        TetradLogger.getInstance().log("info", "Graph Comparison");
        TetradLogger.getInstance().log("comparison", getComparisonString());
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

    public List<Edge> getEdgesAdded() {
        return edgesAdded;
    }

    public List<Edge> getEdgesRemoved() {
        return edgesRemoved;
    }

    public List<Edge> getEdgesReorientedFrom() {
        return edgesReorientedFrom;
    }

    public List<Edge> getEdgesReorientedTo() {
        return edgesReorientedTo;
    }

    public String toString() {
        return "Errors of omission = " + getAdjFn() +
                ", Errors of commission = " + getAdjFp();
    }

    public String getComparisonString() {
        return SearchGraphUtils.graphComparisonString(getParams().getTargetGraphName(), targetGraph,
                getParams().getReferenceGraphName(), referenceGraph, false);
    }

//    public String graphComparisonString() {
//        StringBuilder builder = new StringBuilder();
//
//        if (params != null) {
//            String trueGraphAndTarget = "Comparing " +
//                    getParams().getTargetGraphName() + " to " + getParams().getReferenceGraphName();
//            builder.append(trueGraphAndTarget + "\n");
//        }
//        builder.append("\nEdges added:");
//
//        if (this.getEdgesAdded().isEmpty()) {
//            builder.append("\n  --NONE--");
//        } else {
//            List<Edge> edgesAdded = getEdgesAdded();
//
//            for (int i = 0; i < edgesAdded.size(); i++) {
//                Edge edge = edgesAdded.get(i);
//                boolean directedInReference = false;
//
//                Node node1 = referenceGraph.getNode(edge.getNode1().getName());
//                Node node2 = referenceGraph.getNode(edge.getNode2().getName());
//
//                if (Edges.isDirectedEdge(edge) && DataGraphUtils.existsSemidirectedPathFromTo(referenceGraph, node1, node2)) {
//                    directedInReference = true;
//                } else if ((Edges.isDirectedEdge(edge) || Edges.isBidirectedEdge(edge)) &&
//                        (DataGraphUtils.existsSemidirectedPathFromTo(referenceGraph, node1, node2) ||
//                        DataGraphUtils.existsSemidirectedPathFromTo(referenceGraph, node2, node1))) {
//                    directedInReference = true;
//                }
//
////                Graph graph = comparison.getTargetGraph();
////                builder.append("\n").append(i + 1).append(". <> ====> ").append(edge);
//                builder.append("\n").append(i + 1).append(". ").append(edge);
//
//                if (printStars && directedInReference) {
//                    builder.append(" *");
//                }
//
//            }
//        }
//
//        builder.append("\n\nEdge removed:");
//
//        if (this.getEdgesRemoved().isEmpty()) {
//            builder.append("\n  --NONE--");
//        } else {
//            List<Edge> edgesRemoved = getEdgesRemoved();
//
//            for (int i = 0; i < edgesRemoved.size(); i++) {
//                Edge edge = edgesRemoved.get(i);
//                boolean directedInTarget = false;
//
//                Node node1 = targetGraph.getNode(edge.getNode1().getName());
//                Node node2 = targetGraph.getNode(edge.getNode2().getName());
//
//                if (Edges.isDirectedEdge(edge) && DataGraphUtils.existsSemidirectedPathFromTo(targetGraph, node1, node2)) {
//                    directedInTarget = true;
//                } else if ((Edges.isDirectedEdge(edge) || Edges.isBidirectedEdge(edge)) &&
//                        (DataGraphUtils.existsSemidirectedPathFromTo(targetGraph, node1, node2) ||
//                        DataGraphUtils.existsSemidirectedPathFromTo(targetGraph, node2, node1))) {
//                    directedInTarget = true;
//                }
//
////                Graph graph = comparison.getTargetGraph();
////                builder.append("\n").append(i + 1).append(". <> ====> ").append(edge);
//                builder.append("\n").append(i + 1).append(". ").append(edge);
//
//                if (printStars && directedInTarget) {
//                    builder.append(" *");
//                }
//            }
//        }
//
//        builder.append("\n\nEdges reoriented:");
//
//        if (this.getEdgesReorientedFrom().isEmpty()) {
//            builder.append("\n  --NONE--");
//        } else {
//            List<Edge> edgesReorientedFrom = this.getEdgesReorientedFrom();
//            List<Edge> edgesReorientedTo = this.getEdgesReorientedTo();
//
//            for (int i = 0; i < this.getEdgesReorientedFrom().size(); i++) {
//                Edge from = edgesReorientedFrom.get(i);
//                Edge to = edgesReorientedTo.get(i);
////                Graph graph = comparison.getTargetGraph();
//                builder.append("\n").append(i + 1).append(". ").append(from)
//                        .append(" ====> ").append(to);
//            }
//        }
//
//        String compareString = builder.toString();
//        return compareString;
//    }


    //============================PRIVATE METHODS=========================//


    public Graph getTargetGraph() {
        return new EdgeListGraph(targetGraph);
    }


    public Graph getReferenceGraph() {
        return new EdgeListGraph(referenceGraph);
    }

    //This removes the latent nodes in G and connects nodes that were formerly
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
     * @return the number of correct edges last time they were counted.
     */
    private int getAdjCorrect() {
        return adjCorrect;
    }

    /**
     * @return the number of errors of omission (in the reference workbench but
     * not in the target workbench) the last time they were counted.
     */
    private int getAdjFn() {
        return adjFn;
    }

    private int getAdjFp() {
        return adjFp;
    }

    private int getArrowptCorrect() {
        return arrowptCorrect;
    }

    private int getArrowptFn() {
        return arrowptFn;
    }

    private int getArrowptFp() {
        return arrowptFp;
    }

    private int getTwoCycleCorrect() {
        return twoCycleCorrect;
    }

    private int getTwoCycleFn() {
        return twoCycleFn;
    }

    private int getTwoCycleFp() {
        return twoCycleFp;
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

        if (getAdjCorrect() < 0) {
            throw new IllegalArgumentException();
        }

        if (getAdjFn() < 0) {
            throw new IllegalArgumentException();
        }

        if (getAdjFp() < 0) {
            throw new IllegalArgumentException();
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



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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.GwpResult.AdjacencyEvaluationResult;
import edu.cmu.tetrad.search.GwpResult.CoefficientEvaluationResult;
import edu.cmu.tetrad.search.GwpResult.OrientationEvaluationResult;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;

import java.util.HashMap;
import java.util.List;
import java.util.Vector;


/**
 * Dag plus edge weights. Later include distributions for the error terms
 */
public class GraphWithParameters {
    //a Dag has a list of edges
    //therefore, Hashmap from edges to weights

    private Graph graph;

    public String generatingMethodName = null;

    public String getGeneratingMethodName() {
        return this.generatingMethodName;
    }

    private final HashMap<Edge, Double> weightHash;

//	public Dag patDag = null; //only non-null when graph is a CPDAG

    /*
     * estimate the weights for the nodes that have all parents determined.
     */
    //it would have been more efficient to only regression on the nodes that matter

    public GraphWithParameters(final SemIm semIm, final Graph trueCPDAG) {
//		Graph g = (trueCPDAG==null) ? semIm.getEstIm().getGraph() : trueCPDAG;
//		this.graph = g;
//		weightHash = new HashMap<Edge,Double>();
        this(trueCPDAG);

        //make the SemIm

        //estimate the weights for the nodes that have all parents determined.
        for (final Node node : this.getGraph().getNodes()) {
            if (GraphUtils.allAdjacenciesAreDirected(node, getGraph())) {    //if we know the set of parents of 'node'

                //steal the coefficients from the SemIm
                for (final Edge edge : this.getGraph().getEdges(node)) {
                    final double semImWeight = semIm.getEdgeCoef(edge);
                    this.getWeightHash().put(edge, semImWeight);
                }
            }
        }
        this.graph = getGraph();
    }

    public GraphWithParameters(final Graph graph) {
        this.graph = graph;
        this.weightHash = new HashMap<>();
    }

//	public CPDAGWithParameters(ColtDataSet B) {
//		Shimizu2006Search.makeDagWithParms(B);
//	}

    public void addEdge(final Node node1, final Node node2, final double weight) {
        final Edge edge = new Edge(node1, node2, Endpoint.TAIL, Endpoint.ARROW);
        getGraph().addEdge(edge);
        getWeightHash().put(edge, weight);
    }

    public void addEdge(final String nodeName1, final String nodeName2, final double weight) {
        final Node node1 = getGraph().getNode(nodeName1);
        final Node node2 = getGraph().getNode(nodeName2);
        addEdge(node1, node2, weight);
    }


    public GraphWithParameters(final DataSet dataSet) {

        final Matrix Bmatrix = dataSet.getDoubleData();

//    	List<Node> variables = Bmatrix.getVariable();

        this.graph = new EdgeListGraph();
        this.weightHash = new HashMap<>();

        final int n = Bmatrix.rows();
//		System.out.println("n = " + n);
//		n = Bmatrix.columns();
//		System.out.println("n = " + n);

        //add nodes
        for (int i = 0; i < n; i++) {
            this.getGraph().addNode(new GraphNode(dataSet.getVariable(i).getName()));
        }

        //add edges with weights
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                final double value = Bmatrix.get(i, j);
                if (value > 1E-15 || value < -1E-15) {
                    // Switched i and j in the below. --jdrmasey 10/23/08
                    final Node node1 = getGraph().getNode(dataSet.getVariableNames().get(i));//"X"+(j+1)); //read as the B matrix as: from column to row
                    final Node node2 = getGraph().getNode(dataSet.getVariableNames().get(j));
                    final Edge edge = new Edge(node1, node2, Endpoint.TAIL, Endpoint.ARROW);
                    getGraph().addEdge(edge);
                    getWeightHash().put(edge, value);
                }
            }
        }
    }

    public String toString() { //iterate through the edges and print their weight too
        String str = "";
        for (final Edge edge : getGraph().getEdges()) {
            str += edge.toString();
            str += "   " + getWeightHash().get(edge) + "\n";
        }
        return str;
    }


    public int errorsOfOmission = 0;
    public int errorsOfCommission = 0;

    public AdjacencyEvaluationResult evalAdjacency(final Graph standardDag) {
        //for each edge in this DAG, check whether it is in standardDag. If it isn't, that's an error of
        //commission.
        for (final Edge thisEdge : this.getGraph().getEdges()) {
            System.out.print("thisEdge = " + thisEdge);

            //is it in this DAG?
            final Edge standardEdge = GraphWithParameters.getCorrespondingEdge(standardDag, thisEdge);
            System.out.println(", standardEdge = " + standardEdge);

            final boolean adjCorrect = (standardEdge != null);
            if (!adjCorrect) {
                this.errorsOfCommission++;
            }
        }

        //for each edge in standardDag, check whether it is in this DAG. If it isn't, that's an error of
        //omission.
        for (final Edge standardEdge : standardDag.getEdges()) {
            System.out.print("standardEdge = " + standardEdge);

            //is it in this DAG?
            final Edge thisEdge = GraphWithParameters.getCorrespondingEdge(this.getGraph(), standardEdge);
            System.out.println(", thisEdge = " + thisEdge);

            final boolean adjCorrect = (thisEdge != null);
            if (!adjCorrect) {
                this.errorsOfOmission++;
            }
        }
        return new AdjacencyEvaluationResult(this.errorsOfOmission, this.errorsOfCommission);
    }


    public void printAdjacencyEvaluation() {
        System.out.println("== Results of evaluating adjacency ==");
        System.out.println("errorsOfOmission = " + this.errorsOfOmission);
        System.out.println("errorsOfCommission = " + this.errorsOfCommission);
    }


    public int oriEvaluated = 0;
    public int oriCorrect = 0;
    public int directedWrongWay = 0;
    public int undirectedWhenShouldBeDirected = 0;
    public int directedWhenShouldBeUndirected = 0;
    public List<Edge> correctDirectedOrientationEdges;


    //evaluating orientations
    //should only evaluate on the adjacencies that are correct

    public OrientationEvaluationResult evalOrientations(final Graph standardGraph) {
        this.correctDirectedOrientationEdges = new Vector();

        for (final Edge standardEdge : standardGraph.getEdges()) { //for each edge in the "correct" graph

            final Edge thisEdge = GraphWithParameters.getCorrespondingEdge(this.getGraph(), standardEdge);
            System.out.print("standardEdge = " + standardEdge +
                    (standardEdge == null ? "" : " (directed = " + standardEdge.isDirected()));
            System.out.println("), thisEdge = " + thisEdge +
                    (thisEdge == null ? "" : " (directed = " + thisEdge.isDirected()) + ")");

            if (thisEdge == null) //skip the ones that are not adjacent
                continue;

            this.oriEvaluated++;

            if (!standardEdge.isDirected()) {
                if (!thisEdge.isDirected()) { //both undirected
                    this.oriCorrect++;
                } else {
                    this.directedWhenShouldBeUndirected++;
                }
            } else { //standardEdge is directed
                if (thisEdge.isDirected()) { //estimate edge is directed: compare direction
                    if (GraphWithParameters.getCorrespondingDirectedEdge(this.getGraph(), standardEdge) != null) { //there is a corresponding edge pointing "forward"
                        this.oriCorrect++;
                        this.correctDirectedOrientationEdges.add(thisEdge);
                    } else { //standardEdge is undirected, is directed
                        this.directedWrongWay++;
                    }
                } else { //not directed when it should be
                    this.undirectedWhenShouldBeDirected++;
                }
            }
            System.out.print("\n");

        } //end for
        return new OrientationEvaluationResult(this.oriCorrect, this.directedWrongWay, this.undirectedWhenShouldBeDirected, this.directedWhenShouldBeUndirected);
    }


    public void printOrientationEvaluation() {
        System.out.println("== Results of evaluating orientation ==");
        System.out.println("oriCorrect = " + this.oriCorrect + "  directedWrongWay = " + this.directedWrongWay +
                "  undirectedWhenShouldBeDirected = " + this.undirectedWhenShouldBeDirected + "  directedWhenShouldBeUndirected = " + this.directedWhenShouldBeUndirected);
        System.out.println("oriEvaluated = " + this.oriEvaluated);
    }


    //evaluating coefficients
    double totalCoeffErrorSq;

    //evaluate every node-pair

    public CoefficientEvaluationResult evalCoeffs(final GraphWithParameters standardGraph) {
        this.totalCoeffErrorSq = 0;

        final List<Node> nodes = getGraph().getNodes();
        for (int i = 0; i < nodes.size(); i++) { //iterating through each node pair
            final Node node1 = nodes.get(i);
            final Node realNode1 = GraphWithParameters.getCorrespondingNode(standardGraph.getGraph(), node1);
            for (int j = 0; j < i; j++) {
                final Node node2 = nodes.get(j);
                final Node realNode2 = GraphWithParameters.getCorrespondingNode(standardGraph.getGraph(), node2);

                System.out.println("node1 = " + node1 + "  node2 = " + node2);
                final double coeff12 = getDirectedEdgeCoeff(node1, node2);
                final double realCoeff12 = standardGraph.getDirectedEdgeCoeff(realNode1, realNode2);
                final double err12 = java.lang.Math.pow(coeff12 - realCoeff12, 2);
                System.out.println("err12 = " + err12);

                final double coeff21 = getDirectedEdgeCoeff(node2, node1);
                final double realCoeff21 = standardGraph.getDirectedEdgeCoeff(realNode2, realNode1);
                final double err21 = java.lang.Math.pow(coeff21 - realCoeff21, 2);
                System.out.println("err21 = " + err21);

                final double error = err12 + err21;
                System.out.println("error = " + error);

                this.totalCoeffErrorSq += error;
            }
        }

        return new CoefficientEvaluationResult(this.totalCoeffErrorSq, null);
    }


    //we call this, passing the edges that PC evaluates

    /**
     * evalute coefficients for some node pairs
     *
     * @param edges edges from the CPDAG returned by PC-search
     */
    public CoefficientEvaluationResult evalCoeffsForNodePairs(final GraphWithParameters standardGraph, final List<Edge> edges) {

        this.totalCoeffErrorSq = 0;

        //turn them into 'graph' edges
        for (final Edge edge : edges) {
            final Node node1Edges = edge.getNode1();
            final Node node2Edges = edge.getNode2();

            System.out.println("node1Edges = " + node1Edges + "  node2Edges = " + node2Edges);
            final Node node1this = GraphWithParameters.getCorrespondingNode(this.getGraph(), node1Edges);
            final Node node2this = GraphWithParameters.getCorrespondingNode(this.getGraph(), node2Edges);
            final double coeff12 = getDirectedEdgeCoeff(node1this, node2this);
            final Node node1sta = GraphWithParameters.getCorrespondingNode(standardGraph.getGraph(), node1Edges);
            final Node node2sta = GraphWithParameters.getCorrespondingNode(standardGraph.getGraph(), node2Edges);
            final double realCoeff12 = standardGraph.getDirectedEdgeCoeff(node1sta, node2sta);
            final double err12 = java.lang.Math.pow(coeff12 - realCoeff12, 2);
            System.out.println("err12 = " + err12);

            final double coeff21 = getDirectedEdgeCoeff(node2this, node1this);
            final double realCoeff21 = standardGraph.getDirectedEdgeCoeff(node2sta, node1sta);
            final double err21 = java.lang.Math.pow(coeff21 - realCoeff21, 2);
            System.out.println("err21 = " + err21);

            final double error = err12 + err21;
            System.out.println("error = " + error);

            this.totalCoeffErrorSq += error;
        }
        return new CoefficientEvaluationResult(this.totalCoeffErrorSq, edges.size());
    }


    private double getDirectedEdgeCoeff(final Node node1, final Node node2) {
        final double result;
        final Edge edge = getGraph().getDirectedEdge(node1, node2);
        if (edge == null)
            result = 0;
        else
            result = getWeightHash().get(edge);  //weightHash is null!
        return result;
    }

    //should only evaluate those that are oriented correctly

    public void evalCoeffsCorrectOrientation(final GraphWithParameters standardGraph) {
        final List<Edge> edgesToEvaluate;
//		if (patDag!=null) //we use it
//		{
//		edgesToEvaluate = new Vector();
//		//add only the patDag edges whose orientation is correct
//		for (Edge patDagEdge : patDag.getEdges()){
//		Edge standardEdge = getCorrespondingEdge(standardDag.graph,patDagEdge);

//		if (standardEdge!=null && oriAgrees(patDagEdge,standardEdge))
//		edgesToEvaluate.add(getCorrespondingEdge(this.graph,patDagEdge));
//		}
//		}
//		else 
        edgesToEvaluate = this.correctDirectedOrientationEdges;

        System.out.println("correctOrientationEdges = " + this.correctDirectedOrientationEdges);
        for (final Edge edge : edgesToEvaluate) {
            final double thisCoeff = this.getWeightHash().get(edge);
            final Edge standardEdge = GraphWithParameters.getCorrespondingEdge(standardGraph.getGraph(), edge);
            final double standardCoeff = standardGraph.getWeightHash().get(standardEdge);
            final double diff = thisCoeff - standardCoeff;
            System.out.println("thisEdge " + edge + ": " + thisCoeff + "   err = " + diff);
            this.totalCoeffErrorSq += java.lang.Math.pow(diff, 2);
        }

    }

    //either both point to the left or both point to the right

    private boolean oriAgrees(final Edge edge1, final Edge edge2) {
        int count = 0;
        System.out.println();
        if (edge1.pointsTowards(edge1.getNode1()))
            count++;
        if (edge2.pointsTowards(edge2.getNode1()))
            count++;
        return (count % 2) == 0;
    }

    public void printCoefficientEvaluation() {
        System.out.println("== Results of evaluating coefficients ==");
        System.out.println("totalCoeffErrorSq = " + this.totalCoeffErrorSq);
    }

    public static Node getCorrespondingNode(final Graph graph, final Node node) {
        final String nodeName = node.getName();
        final Node node1 = graph.getNode(nodeName);
        return node1;
    }

    //returns the edge of graph corresponding to edge

    public static Edge getCorrespondingEdge(final Graph graph, final Edge edge) {
//		System.out.println("entered getCorrespondingEdge: edge = " + edge);
        final Node node1 = GraphWithParameters.getCorrespondingNode(graph, edge.getNode1());
        final Node node2 = GraphWithParameters.getCorrespondingNode(graph, edge.getNode2());
        final Edge result = graph.getEdge(node1, node2);
        return result;
    }

    //returns the directed edge of graph corresponding to edge

    public static Edge getCorrespondingDirectedEdge(final Graph graph, final Edge edge) {
        if (edge == null)
            return null;
        else {
            final String nodeName1 = edge.getNode1().getName();
            final String nodeName2 = edge.getNode2().getName();
            final Node node1 = graph.getNode(nodeName1);
            final Node node2 = graph.getNode(nodeName2);
            final Edge result = graph.getDirectedEdge(node1, node2);
            return result;
        }
    }


    //does the graph have an edge similar to 'edge'?

    private static boolean hasCorrespondingAdjacency(final Graph graph, final Edge edge) {
        final Edge corrEdge = GraphWithParameters.getCorrespondingEdge(graph, edge);
        return corrEdge != null;
    }

    private static boolean directionAgrees(final Graph graph, final Edge edge) {
        final String edgeDirection = (edge.toString().indexOf(">") == -1) ? "left" : "right";

        final String nodeName1 = edge.getNode1().getName();
        final String nodeName2 = edge.getNode2().getName();
        final Node node1 = graph.getNode(nodeName1);
        final Node node2 = graph.getNode(nodeName2);
        final Edge graphEdge = graph.getEdge(node1, node2);

        final String graphEdgeDirection = (graphEdge.toString().indexOf(">") == -1) ? "left" : "right";

        return edgeDirection.equals(graphEdgeDirection);
    }

    /**
     * creates a CPDAGWithParameters by running a regression, given a graph and data
     */
    public static GraphWithParameters regress(final DataSet dataSet, final Graph graph) {
        final SemPm semPmEstDag = new SemPm(graph);
        final SemEstimator estimatorEstDag = new SemEstimator(dataSet, semPmEstDag);
        estimatorEstDag.estimate();
        final SemIm semImEstDag = estimatorEstDag.getEstimatedSem();
        final GraphWithParameters estimatedGraph = new GraphWithParameters(semImEstDag, graph);
        return estimatedGraph;
    }


    /**
     * @return the B matrix corresponding to the graph we do the reverse of Shimizu2006Search.makeDagWithParms()
     */
    //possible difference: makeDagWithParms() uses
    //    	List<Node> variables = ltDataSet.getVariable();
    public DataSet getGraphMatrix() {
        final int n = this.getGraph().getNumNodes();
        final Matrix matrix = new Matrix(n, n);
        for (final Edge edge : this.getGraph().getEdges()) {
            final Node node1 = edge.getNode1();
            final Node node2 = edge.getNode2();
            final int node1Index = getGraph().getNodes().indexOf(node1);
            final int node2Index = getGraph().getNodes().indexOf(node2);
            final double value = getWeightHash().get(edge);
            matrix.set(node2Index, node1Index, value); //the B matrix is read: from column to row
        }
        return new BoxDataSet(new DoubleDataBox(matrix.toArray()), getGraph().getNodes());
    }

    List<List<Integer>> cycles = null;

    public List<List<Integer>> getCycles() {
        if (this.cycles == null) {
            //find cycles


        }
        return this.cycles;
    }


    public Graph getGraph() {
        return this.graph;
    }

    public HashMap<Edge, Double> getWeightHash() {
        return this.weightHash;
    }
}




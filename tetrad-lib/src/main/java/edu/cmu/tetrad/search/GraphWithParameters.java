///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;

import java.util.HashMap;
import java.util.List;


/**
 * Dag plus edge weights. Later include distributions for the error terms
 */
public class GraphWithParameters {
    //a Dag has a list of edges
    //therefore, Hashmap from edges to weights

    private Graph graph;

    private final HashMap<Edge, Double> weightHash;

//	public Dag patDag = null; //only non-null when graph is a CPDAG

    /*
     * estimate the weights for the nodes that have all parents determined.
     */
    //it would have been more efficient to only regression on the nodes that matter

    public GraphWithParameters(SemIm semIm, Graph trueCPDAG) {
        this(trueCPDAG);

        //make the SemIm

        //estimate the weights for the nodes that have all parents determined.
        for (Node node : this.getGraph().getNodes()) {
            if (GraphUtils.allAdjacenciesAreDirected(node, getGraph())) {    //if we know the set of parents of 'node'

                //steal the coefficients from the SemIm
                for (Edge edge : this.getGraph().getEdges(node)) {
                    double semImWeight = semIm.getEdgeCoef(edge);
                    this.getWeightHash().put(edge, semImWeight);
                }
            }
        }
        this.graph = getGraph();
    }

    public GraphWithParameters(Graph graph) {
        this.graph = graph;
        this.weightHash = new HashMap<>();
    }

    public void addEdge(Node node1, Node node2, double weight) {
        Edge edge = new Edge(node1, node2, Endpoint.TAIL, Endpoint.ARROW);
        getGraph().addEdge(edge);
        getWeightHash().put(edge, weight);
    }

    public void addEdge(String nodeName1, String nodeName2, double weight) {
        Node node1 = getGraph().getNode(nodeName1);
        Node node2 = getGraph().getNode(nodeName2);
        addEdge(node1, node2, weight);
    }


    public GraphWithParameters(DataSet dataSet) {

        Matrix Bmatrix = dataSet.getDoubleData();

        this.graph = new EdgeListGraph();
        this.weightHash = new HashMap<>();

        int n = Bmatrix.rows();

        //add nodes
        for (int i = 0; i < n; i++) {
            this.getGraph().addNode(new GraphNode(dataSet.getVariable(i).getName()));
        }

        //add edges with weights
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double value = Bmatrix.get(i, j);
                if (value > 1E-15 || value < -1E-15) {
                    // Switched i and j in the below. --jdrmasey 10/23/08
                    Node node1 = getGraph().getNode(dataSet.getVariableNames().get(i));//"X"+(j+1)); //read as the B matrix as: from column to row
                    Node node2 = getGraph().getNode(dataSet.getVariableNames().get(j));
                    Edge edge = new Edge(node1, node2, Endpoint.TAIL, Endpoint.ARROW);
                    getGraph().addEdge(edge);
                    getWeightHash().put(edge, value);
                }
            }
        }
    }

    public String toString() { //iterate through the edges and print their weight too
        StringBuilder str = new StringBuilder();
        for (Edge edge : getGraph().getEdges()) {
            str.append(edge.toString());
            str.append("   ").append(getWeightHash().get(edge)).append("\n");
        }
        return str.toString();
    }

    /**
     * creates a CPDAGWithParameters by running a regression, given a graph and data
     */
    public static GraphWithParameters regress(DataSet dataSet, Graph graph) {
        SemPm semPmEstDag = new SemPm(graph);
        SemEstimator estimatorEstDag = new SemEstimator(dataSet, semPmEstDag);
        estimatorEstDag.estimate();
        SemIm semImEstDag = estimatorEstDag.getEstimatedSem();
        return new GraphWithParameters(semImEstDag, graph);
    }


    /**
     * @return the B matrix corresponding to the graph we do the reverse of Shimizu2006Search.makeDagWithParms()
     */
    //possible difference: makeDagWithParms() uses
    //    	List<Node> variables = ltDataSet.getVariable();
    public DataSet getGraphMatrix() {
        int n = this.getGraph().getNumNodes();
        Matrix matrix = new Matrix(n, n);
        for (Edge edge : this.getGraph().getEdges()) {
            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();
            int node1Index = getGraph().getNodes().indexOf(node1);
            int node2Index = getGraph().getNodes().indexOf(node2);
            double value = getWeightHash().get(edge);
            matrix.set(node2Index, node1Index, value); //the B matrix is read: from column to row
        }
        return new BoxDataSet(new DoubleDataBox(matrix.toArray()), getGraph().getNodes());
    }

    List<List<Integer>> cycles;

    public List<List<Integer>> getCycles() {
        //find cycles
        return this.cycles;
    }


    public Graph getGraph() {
        return this.graph;
    }

    public HashMap<Edge, Double> getWeightHash() {
        return this.weightHash;
    }
}




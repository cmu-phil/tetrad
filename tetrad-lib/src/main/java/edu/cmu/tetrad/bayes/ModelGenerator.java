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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.graph.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Provides static methods for generating variants of an input graph. For
 * example, the first method generates all 1 step modifications of the graph.
 *
 * @author Frank Wimberly
 */
public final class ModelGenerator {

    /**
     * This method takes an acyclic graph as input and returns a list of graphs
     * each of which is a modification of the original graph with either an edge
     * deleted, added or reversed.  Edges are not added or reversed if a cycle
     * would result.
     */
    public static List<Graph> generate(Graph graph) {

        //Make sure the argument contains no cycles.
        if (graph.existsDirectedCycle()) {
            throw new IllegalArgumentException(
                    "Input must not contain cycles.");
        }

        List<Graph> graphs = new LinkedList<>();

        Set<Edge> allEdges = graph.getEdges();
        List<Node> allNodes = graph.getNodes();

        //Add those graphs in which each edge is removed in turn.
        for (Edge allEdge1 : allEdges) {
            Graph toAdd = new EdgeListGraph(graph);
            toAdd.removeEdge(allEdge1);
            graphs.add(toAdd);
        }

        //Add those graphs in which each edge is reversed
        for (Edge allEdge : allEdges) {
            Graph toAdd = new EdgeListGraph(graph);

            Endpoint e1 = allEdge.getEndpoint1();
            Endpoint e2 = allEdge.getEndpoint2();

            Node n1 = allEdge.getNode1();
            Node n2 = allEdge.getNode2();

            Edge newEdge = new Edge(n1, n2, e2, e1);

            toAdd.removeEdge(allEdge);
            if (!toAdd.existsDirectedPathFromTo(n1, n2)) {
                toAdd.addEdge(newEdge);
                graphs.add(toAdd);
            }

        }

        //For each pair of nodes which are not adjacent, add the two graphs produced by
        //adding a directed edge in each direction provided no cycles are introduced.
        //for(Iterator itn1 = allNodes.iterator(); itn1.hasNext(); ) {
        //    Node node1 = (Node) itn1.next();

        for (int i = 0; i < allNodes.size(); i++) {
            Node node1 = allNodes.get(i);

            //for(Iterator itn2 = allNodes.iterator(); itn2.hasNext(); ) {
            //    Node node2 = (Node) itn2.next();
            for (int j = i + 1; j < allNodes.size(); j++) {
                Node node2 = allNodes.get(j);

                //if(node1 == node2) continue;

                //If there is no edge between node1 and node2
                if (!graph.isParentOf(node1, node2) &&
                        !graph.isParentOf(node2, node1)) {

                    Graph toAdd1 = new EdgeListGraph(graph);
                    //Make sure adding this edge won't introduce a cycle.
                    if (!toAdd1.existsDirectedPathFromTo(node1, node2)) {  //
                        Edge newN2N1 = new Edge(node2, node1, Endpoint.TAIL,
                                Endpoint.ARROW);
                        toAdd1.addEdge(newN2N1);
                        graphs.add(toAdd1);
                    }

                    //Now create the graph with the edge added in the other direction
                    Graph toAdd2 = new EdgeListGraph(graph);
                    //Make sure adding this edge won't introduce a cycle.
                    if (!toAdd2.existsDirectedPathFromTo(node2, node1)) {
                        Edge newN1N2 = new Edge(node1, node2, Endpoint.TAIL,
                                Endpoint.ARROW);
                        toAdd2.addEdge(newN1N2);
                        graphs.add(toAdd2);
                    }

                }

            }
        }

        return graphs;
    }
}






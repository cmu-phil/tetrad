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

import edu.cmu.tetrad.graph.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Given a pattern this class implements two algortithms for finding an associated directed acyclic graph (DAG).
 * <p/>
 * The first algorithm (in patternToDagMeek) was described in Zhang and Spirtes (2005), "A Characterization of Markov
 * Equivalence Classes for Ancestral Graphical Models" on pp. 53-54.
 * <p/>
 * The second algorithm (in patternToDagDorTarsi) was described by Chickering (2002) in "Optimal Structure
 * Identification with Greedy Search" in the Journal of Machine Learning Research.  The algorithm was proposed by Dor
 * and Tarsi (1992).
 *
 * @author Frank Wimberly
 */
public class PatternToDag {

    /**
     * The input pattern
     */
    private Pattern pattern;

    //=============================CONSTRUCTORS==========================//

    public PatternToDag(Pattern pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException(
                    "Input pattern must not be null");
        }

        this.pattern = new Pattern(pattern);
    }

    /**
     * This algorithm is due to Meek (1995) and was described by Zhang and Spirtes (2005), "A Characterization of Markov
     * Equivalence Classes for Ancestral Graphical Models" on pp. 53-54.
     * <p/>
     * THIS IMPLEMENTATION IS NOT DEBUGGED.
     */
    public Dag patternToDagMeek() {
        Graph dag = new EdgeListGraph(pattern);

        while (nondirectedEdges(dag).size() > 0) {
            List edges = nondirectedEdges(dag);
            Edge edge = (Edge) edges.get(0);
            Node A = edge.getNode1();
            Node B = edge.getNode2();

            dag.setEndpoint(A, B, Endpoint.ARROW);

            //Try rule UR1
            for (int i = 1; i < edges.size(); i++) {
                Edge otherEdge = (Edge) edges.get(i);

                if (otherEdge.getNode1() == B &&
                        !dag.isAdjacentTo(A, otherEdge.getNode2())) {
                    dag.setEndpoint(B, otherEdge.getNode2(), Endpoint.ARROW);
                }

                if (otherEdge.getNode2() == B &&
                        !dag.isAdjacentTo(A, otherEdge.getNode1())) {
                    dag.setEndpoint(B, otherEdge.getNode1(), Endpoint.ARROW);
                }
            }

            //Try rule UR2
            Set<Edge> allEdges = dag.getEdges();

            for (Edge otherEdge : allEdges) {
                if (otherEdge.getNode1() == B && otherEdge.getNode2() != A &&
                        otherEdge.getEndpoint1() == Endpoint.TAIL &&
                        otherEdge.getEndpoint2() == Endpoint.ARROW &&
                        dag.isAdjacentTo(A, otherEdge.getNode2()) &&
                        dag.getEdge(A, otherEdge.getNode2()).getEndpoint1() ==
                                Endpoint.TAIL && dag.getEdge(A,
                        otherEdge.getNode2()).getEndpoint2() == Endpoint.TAIL) {
                    dag.setEndpoint(A, otherEdge.getNode2(), Endpoint.ARROW);
                }
            }

            //Try rule UR3
            List<Node> allNodes = dag.getNodes();

            for (Edge otherEdge : allEdges) {
                for (Node D : allNodes) {
                    Node C = otherEdge.getNode2();
                    if (otherEdge.getNode1() == B && C != A &&
                            otherEdge.getEndpoint1() == Endpoint.TAIL &&
                            otherEdge.getEndpoint2() == Endpoint.ARROW &&
                            (D != A) && (D != B) && (D != C) &&
                            dag.isAdjacentTo(A, D) && dag.isAdjacentTo(D, C) &&
                            dag.isAdjacentTo(B, D) &&
                            dag.getEdge(A, D).getEndpoint1() == Endpoint.TAIL &&
                            dag.getEdge(A, D).getEndpoint2() == Endpoint.TAIL &&
                            dag.getEdge(D, C).getEndpoint1() == Endpoint.TAIL &&
                            dag.getEdge(D, C).getEndpoint2() == Endpoint.TAIL &&
                            dag.getEdge(B, D).getEndpoint1() == Endpoint.TAIL &&
                            dag.getEdge(B, D).getEndpoint2() == Endpoint.TAIL &&
                            !dag.isAdjacentTo(A, C)) {
                        dag.setEndpoint(D, C, Endpoint.ARROW);
                    }
                }
            }
        }

        return new Dag(dag);
    }

    public Graph patternToDagMeekRules() {
        DagInPatternIterator iterator = new DagInPatternIterator(pattern);

        if (iterator.hasNext()) {
            return new EdgeListGraph(iterator.next());
        } else {
            return null;
        }

//        Graph graph = new EdgeListGraph(pattern);
//        Edge undirectedEdge;
//
//        while ((undirectedEdge = findUndirectedEdge(graph)) != null) {
//            graph.removeEdge(undirectedEdge);
//            graph.addDirectedEdge(undirectedEdge.getNode1(), undirectedEdge.getNode2());
//            new MeekRules().orientImplied(graph);
//        }
//
//        return new Dag(graph);
    }

    private Edge findUndirectedEdge(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            if (Edges.isUndirectedEdge(edge)) {
                return edge;
            }
        }

        return null;
    }

    private List nondirectedEdges(Graph graph) {
        Set<Edge> allEdges = graph.getEdges();
        List<Edge> nondirected = new ArrayList<Edge>();

        for (Edge edge : allEdges) {
            if (edge.getEndpoint1() == Endpoint.TAIL &&
                    edge.getEndpoint2() == Endpoint.TAIL) {
                nondirected.add(edge);
            }
        }

        return nondirected;
    }

//    private boolean allOriented() {
//        List allEdges = pattern.getEdges();
//
//        for (Iterator itedge = allEdges.iterator(); itedge.hasNext();) {
//            Edge edge = (Edge) itedge.next();
//            if (edge.getEndpoint1() == Endpoint.TAIL && edge.getEndpoint2() == Endpoint.TAIL) {
//                return false;
//            }
//        }
//        return true;
//    }

    public Dag patternToDagDorTarsi() {
        //Create the DAG and all the nodes of pattern to it.
        Dag dag = new Dag();

        List<Node> allNodes = pattern.getNodes();

        for (Node node : allNodes) {
            dag.addNode(node);
        }

        Set<Edge> allEdges = pattern.getEdges();

        for (Edge edge : allEdges) {

            //Ignore bidirected edges
            if (pattern.isDirectedFromTo(edge.getNode1(), edge.getNode2()) &&
                    pattern.isDirectedFromTo(edge.getNode2(), edge.getNode1())) {
                //deal with a bi-directed edge. That is, skip it.
                System.out.println(
                        "A bidirected edge was found in the input pattern");
                continue;  //next edge
            }

            //Add all directed edges in the pattern to the dag.
            if (pattern.isDirectedFromTo(edge.getNode1(), edge.getNode2())) {
                dag.addDirectedEdge(edge.getNode1(), edge.getNode2());
            } else if (pattern.isDirectedFromTo(edge.getNode2(), edge.getNode1())) {
                dag.addDirectedEdge(edge.getNode2(), edge.getNode1());
            }
        }

        //dag now has all the directed edges from pattern and no other edges.

        //If the pattern has no nondirected edges return dag as it is.
        if (nondirectedEdges(pattern).size() == 0) {
            return dag;
        }

        //There are no edges to orient in a graph with only one node:
        while (pattern.getNumNodes() > 1) {
            allNodes = pattern.getNodes();

            for (Node node : allNodes) {
                List<Node> adjacentNodes = pattern.getAdjacentNodes(node);
                List<Node> neighborNodes = new ArrayList<Node>();

                for (Node adjacent : adjacentNodes) {
                    Edge nodeToAdjacent = pattern.getEdge(node, adjacent);
                    if (nodeToAdjacent.getEndpoint1() == Endpoint.TAIL &&
                            nodeToAdjacent.getEndpoint2() == Endpoint.TAIL) {
                        neighborNodes.add(adjacent);
                    }
                }

                //System.out.println("neighbors = " + neighborNodes);

                List<Node> parentsUnionNeighbors =
                        new ArrayList<Node>(neighborNodes);
                List<Node> parentNodes = pattern.getParents(node);

                //System.out.println("parents = " + parentNodes);

                for (Node parent : parentNodes) {
                    if (!parentsUnionNeighbors.contains(parent)) {
                        parentsUnionNeighbors.add(parent);
                    }
                }

                //System.out.println("union = " + parentsUnionNeighbors);

                boolean isClique = false;
                if (neighborNodes.size() > 0) {
                    if (cliqueSubgraph(pattern, parentsUnionNeighbors)) {
                        isClique = true;
                    }
                }

                //System.out.println("outdegree = " + pattern.getOutdegree(node));
                //System.out.println("isclique = " + isClique);

                //If node has no outgoing edges and if the set of neighbors being non-empty
                //implies that the set parents union neighbors is a clique:
                if (pattern.getOutdegree(node) == 0 && isClique) {
                    //System.out.println("X exists = " + node);

                    List<Edge> undirectedEdges = pattern.getEdges(node);

                    //For each undirected edge Y -- X incident to node in pattern insert
                    //a directed edge Y --> X in dag
                    for (Edge edge : undirectedEdges) {
                        if (edge.getEndpoint1() == Endpoint.TAIL &&
                                edge.getEndpoint2() == Endpoint.TAIL) {
                            if (edge.getNode1() == node) {
                                dag.addDirectedEdge(edge.getNode2(), node);
                            } else {
                                dag.addDirectedEdge(edge.getNode1(), node);
                            }
                        }
                    }

                    //System.out.println("Removing " + node.getName());
                    pattern.removeNode(node);
                }

                //pattern.removeNode(node);
            }
        }   //while

        return dag;
    }

    /**
     * @return a boolean which indicates whether a list of nodes forms a clique. That is, is the subgraph determined by
     * those nodes complete.  If so, every pair of the nodes is adjacent.
     *
     * @param g a graph
     * @param s a list of nodes in the graph
     * @return true if the subgraph determined by the nodes in s is a clique.
     */
    private boolean cliqueSubgraph(Graph g, List<Node> s) {

        //Is every pair of nodes in s adjacent?
        int n = s.size();

        for (int i = 0; i < n; i++) {
            if (!g.getNodes().contains(s.get(i))) {
                throw new IllegalArgumentException(
                        "s must consist of nodes of g");
            }
        }

        if (n == 1) {
            return true;
        }

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (!g.isAdjacentTo(s.get(i), s.get(j))) {
                    return false;
                }
            }
        }

        return true;
    }

}





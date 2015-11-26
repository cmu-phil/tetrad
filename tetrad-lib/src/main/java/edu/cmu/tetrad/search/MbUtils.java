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

import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.*;

/**
 * Some useful utilities for dealing with Markov blankets and Markov blanket DAGs.
 *
 * @author Joseph Ramsey
 */
public class MbUtils {

    /**
     * Trims the graph to the target, the parents and children of the target, and the parents of the children of the
     * target. Bidirected edges are interpreted as if they could be oriented in either direction.
     *
     * @param includeBidirected true if bidirected edges should be included.
     */
    public static void trimToMbNodes(Graph graph, Node target,
                                     boolean includeBidirected) {
        if (includeBidirected) {
            List<Node> pc = graph.getAdjacentNodes(target);
            List<Node> children = graph.getNodesOutTo(target, Endpoint.ARROW);

            Set<Node> parentsOfChildren = new HashSet<Node>();

            for (Node v : children) {
                for (Node w : graph.getAdjacentNodes(v)) {
                    if (w == target) {
                        continue;
                    }
                    if (parentsOfChildren.contains(w)) {
                        continue;
                    }
                    if (pc.contains(w)) {
                        continue;
                    }

                    if (graph.isDefCollider(target, v, w)) {
                        parentsOfChildren.add(w);
                    } else if (graph.getNodesInTo(v, Endpoint.ARROW).contains(target)
                            && graph.isUndirectedFromTo(v, w)) {
                        parentsOfChildren.add(w);
                    }
                }
            }

            Set<Node> allRelevantNodes = new HashSet<Node>();
            allRelevantNodes.add(target);
            allRelevantNodes.addAll(pc);
            allRelevantNodes.addAll(parentsOfChildren);

            List<Node> irrelevantNodes = graph.getNodes();
            irrelevantNodes.removeAll(allRelevantNodes);

            graph.removeNodes(irrelevantNodes);
        } else {
            List<Node> pc = new LinkedList<Node>();

            for (Node node : graph.getAdjacentNodes(target)) {
                if (graph.isDirectedFromTo(target, node) ||
                        graph.isDirectedFromTo(node, target) ||
                        graph.isUndirectedFromTo(node, target)) {
                    pc.add(node);
                }
            }

            List<Node> children = new LinkedList<Node>();

            for (Node v : graph.getAdjacentNodes(target)) {
                if (children.contains(v)) {
                    continue;
                }

                if (graph.isDirectedFromTo(target, v)) {
                    children.add(v);
                }
            }

            Set<Node> parentsOfChildren = new HashSet<Node>();

            for (Node v : children) {
                for (Node w : graph.getAdjacentNodes(v)) {
                    if (w == target) {
                        continue;
                    }
                    if (parentsOfChildren.contains(w)) {
                        continue;
                    }
                    if (pc.contains(w)) {
                        continue;
                    }

                    if (graph.isDirectedFromTo(target, v) &&
                            graph.isDirectedFromTo(w, v)) {
                        parentsOfChildren.add(w);
                    }
                }
            }

            Set<Node> allRelevantNodes = new HashSet<Node>();
            allRelevantNodes.add(target);
            allRelevantNodes.addAll(pc);
            allRelevantNodes.addAll(parentsOfChildren);

            List<Node> irrelevantNodes = graph.getNodes();
            irrelevantNodes.removeAll(allRelevantNodes);

            graph.removeNodes(irrelevantNodes);
        }
    }

    /**
     * Removes edges among the parents of the target.
     */
    public static void trimEdgesAmongParents(Graph graph, Node target) {
        List parents = graph.getParents(target);

        if (parents.size() >= 2) {
            ChoiceGenerator cg = new ChoiceGenerator(parents.size(), 2);
            int[] choice;

            while ((choice = cg.next()) != null) {
                Node v = (Node) parents.get(choice[0]);
                Node w = (Node) parents.get(choice[1]);

                Edge edge = graph.getEdge(v, w);

                if (edge != null) {
//                    LogUtils.getInstance().finest("Removing edge among parents: " + edge);
                    graph.removeEdges(v, w);
                }
            }
        }
    }

    /**
     * Removes edges among the parents of children of the target.
     */
    public static void trimEdgesAmongParentsOfChildren(Graph graph,
                                                       Node target) {
        List<Node> children = graph.getNodesOutTo(target, Endpoint.ARROW);
        Set<Node> parents = new HashSet<Node>();

        for (Node aChildren : children) {
            parents.addAll(graph.getParents(aChildren));
        }

        parents.remove(target);
        parents.removeAll(graph.getAdjacentNodes(target));
        List<Node> parentsOfChildren = new ArrayList<Node>(parents);

        if (parentsOfChildren.size() >= 2) {
            ChoiceGenerator cg =
                    new ChoiceGenerator(parentsOfChildren.size(), 2);
            int[] choice;

            while ((choice = cg.next()) != null) {
                Node v = parentsOfChildren.get(choice[0]);
                Node w = parentsOfChildren.get(choice[1]);

                Edge edge = graph.getEdge(v, w);

                if (edge != null) {
//                    LogUtils.getInstance().finest("Removing edge among parents: " + edge);
                    graph.removeEdge(v, w);
                }
            }
        }
    }

    public static void trimToAdjacents(Graph graph, Node target) {
        for (Node node : graph.getNodes()) {
            if (node == target) {
                continue;
            }

            if (!graph.isAdjacentTo(node, target)) {
                graph.removeNode(node);
            }
        }
    }

    public static void trimToNeighborhood(Graph graph,
                                          List<Node> neighborhood) {
        List<Node> irrelevantNodes = graph.getNodes();
        irrelevantNodes.removeAll(neighborhood);

        graph.removeNodes(irrelevantNodes);
    }

    /**
     * Trims <code>graph</code> to variables whose least distance to the target is no more than <code>distance</code>
     */
    public static void trimToDistance(Graph graph, Node target, int distance) {
        Set<Node> nodes = getNeighborhood(graph, target, distance);

        List<Node> irrelevantNodes = graph.getNodes();
        irrelevantNodes.removeAll(nodes);

        graph.removeNodes(irrelevantNodes);
    }

    public static Set<Node> getNeighborhood(Graph graph, Node target,
                                            int distance) {
        if (distance < 1) {
            throw new IllegalArgumentException("Distance must be >= 1.");
        }

        Set<Node> nodes = new HashSet<Node>();
        nodes.add(target);
        Set<Node> tier = new HashSet<Node>(nodes);

        for (int i = 0; i < distance; i++) {
            Set<Node> adjacents = new HashSet<Node>();

            for (Node aTier : tier) {
                adjacents.addAll(graph.getAdjacentNodes(aTier));
            }

            nodes.addAll(adjacents);
            tier = new HashSet<Node>(adjacents);
        }
        return nodes;
    }

    /**
     * Generates the list of MB DAGs consistent with the MB Pattern returned by the previous search.
     *
     * @param orientBidirectedEdges True iff bidirected edges should be oriented as if they were undirected.
     * @param test
     * @param depth
     * @param target
     * @return a list of Dag's.
     */
    public static List<Graph> generateMbDags(Graph mbPattern,
                                             boolean orientBidirectedEdges,
                                             IndependenceTest test, int depth,
                                             Node target) {
        return new LinkedList<Graph>(listMbDags(new EdgeListGraph(mbPattern),
                orientBidirectedEdges, test, depth, target));
    }

    /**
     * The recursive method used to list the MB DAGS consistent with an MB Pattern (i.e. with the independence
     * information available to the search.
     */
    private static Set<Graph> listMbDags(Graph mbPattern,
                                         boolean orientBidirectedEdges,
                                         IndependenceTest test, int depth,
                                         Node target) {
        Set<Graph> dags = new HashSet<Graph>();
        Graph graph = new EdgeListGraph(mbPattern);
        doAbbreviatedMbOrientation(graph, test, depth, target);
        Set<Edge> edges = graph.getEdges();
        Edge edge = null;

        for (Edge _edge : edges) {
            if (orientBidirectedEdges && Edges.isBidirectedEdge(_edge)) {
                edge = _edge;
                break;
            }

            if (Edges.isUndirectedEdge(_edge)) {
                edge = _edge;
                break;
            }
        }

        if (edge == null) {
            dags.add(graph);
            return dags;
        }

        graph.setEndpoint(edge.getNode2(), edge.getNode1(), Endpoint.TAIL);
        graph.setEndpoint(edge.getNode1(), edge.getNode2(), Endpoint.ARROW);
        dags.addAll(
                listMbDags(graph, orientBidirectedEdges, test, depth, target));

        graph.setEndpoint(edge.getNode1(), edge.getNode2(), Endpoint.TAIL);
        graph.setEndpoint(edge.getNode2(), edge.getNode1(), Endpoint.ARROW);
        dags.addAll(
                listMbDags(graph, orientBidirectedEdges, test, depth, target));

        return dags;
    }

    public static Dag getOneMbDag(Graph mbPattern) {
        PatternToDag search = new PatternToDag(new Pattern(mbPattern));
        return search.patternToDagMeek();
    }

    /**
     * A reiteration of orientation steps 5-7 of the search for use in generating the list of MB DAGs.
     */
    private static void doAbbreviatedMbOrientation(Graph graph,
                                                   IndependenceTest test,
                                                   int depth, Node target) {
        SearchGraphUtils.orientUsingMeekRulesLocally(new Knowledge2(), graph,
                test, depth);
        trimToMbNodes(graph, target, false);
        trimEdgesAmongParents(graph, target);
        trimEdgesAmongParentsOfChildren(graph, target);
    }
}




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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.util.*;

/**
 * Provides some useful utilities for dealing with Markov blankets and Markov blanket DAGs.
 *
 * @author josephramsey
 */
public class MbUtils {

    /**
     * Trims the graph to the target, the parents and children of the target, and the parents of the children of the
     * target. Bidirected edges are interpreted as if they could be oriented in either direction.
     *
     * @param graph             The graph.
     * @param target            The target.
     * @param includeBidirected true if bidirected edges should be included.
     */
    public static void trimToMbNodes(Graph graph, Node target,
                                     boolean includeBidirected) {
        if (includeBidirected) {
            List<Node> pc = graph.getAdjacentNodes(target);
            List<Node> children = graph.getNodesOutTo(target, Endpoint.ARROW);

            Set<Node> parentsOfChildren = new HashSet<>();

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
                            && graph.paths().isUndirectedFromTo(v, w)) {
                        parentsOfChildren.add(w);
                    }
                }
            }

            Set<Node> allRelevantNodes = new HashSet<>();
            allRelevantNodes.add(target);
            allRelevantNodes.addAll(pc);
            allRelevantNodes.addAll(parentsOfChildren);

            List<Node> irrelevantNodes = graph.getNodes();
            irrelevantNodes.removeAll(allRelevantNodes);

            graph.removeNodes(irrelevantNodes);
        } else {
            List<Node> pc = new LinkedList<>();

            for (Node node : graph.getAdjacentNodes(target)) {
                if (graph.paths().isDirectedFromTo(target, node) ||
                        graph.paths().isDirectedFromTo(node, target) ||
                        graph.paths().isUndirectedFromTo(node, target)) {
                    pc.add(node);
                }
            }

            List<Node> children = new LinkedList<>();

            for (Node v : graph.getAdjacentNodes(target)) {
                if (children.contains(v)) {
                    continue;
                }

                if (graph.paths().isDirectedFromTo(target, v)) {
                    children.add(v);
                }
            }

            Set<Node> parentsOfChildren = new HashSet<>();

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

                    if (graph.paths().isDirectedFromTo(target, v) &&
                            graph.paths().isDirectedFromTo(w, v)) {
                        parentsOfChildren.add(w);
                    }
                }
            }

            Set<Node> allRelevantNodes = new HashSet<>();
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
     *
     * @param graph  The graph.
     * @param target The target.
     */
    public static void trimEdgesAmongParents(Graph graph, Node target) {
        List<Node> parents = new ArrayList<>(graph.getParents(target));

        if (parents.size() >= 2) {
            ChoiceGenerator cg = new ChoiceGenerator(parents.size(), 2);
            int[] choice;

            while ((choice = cg.next()) != null) {
                Node v = parents.get(choice[0]);
                Node w = parents.get(choice[1]);

                Edge edge = graph.getEdge(v, w);

                if (edge != null) {
                    graph.removeEdges(v, w);
                }
            }
        }
    }

    /**
     * Removes edges among the parents of children of the target.
     *
     * @param graph  The graph.
     * @param target The target.
     */
    public static void trimEdgesAmongParentsOfChildren(Graph graph,
                                                       Node target) {
        List<Node> children = graph.getNodesOutTo(target, Endpoint.ARROW);
        Set<Node> parents = new HashSet<>();

        for (Node aChildren : children) {
            parents.addAll(graph.getParents(aChildren));
        }

        parents.remove(target);
        graph.getAdjacentNodes(target).forEach(parents::remove);
        List<Node> parentsOfChildren = new ArrayList<>(parents);

        if (parentsOfChildren.size() >= 2) {
            ChoiceGenerator cg =
                    new ChoiceGenerator(parentsOfChildren.size(), 2);
            int[] choice;

            while ((choice = cg.next()) != null) {
                Node v = parentsOfChildren.get(choice[0]);
                Node w = parentsOfChildren.get(choice[1]);

                Edge edge = graph.getEdge(v, w);

                if (edge != null) {
                    graph.removeEdge(v, w);
                }
            }
        }
    }

    /**
     * Trims the graph to just the adjacents of the target.
     *
     * @param graph  The graph.
     * @param target The target.
     */
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

    /**
     * Generates the list of MB DAGs consistent with the MB CPDAG returned by the previous search.
     *
     * @param orientBidirectedEdges True iff bidirected edges should be oriented as if they were undirected.
     * @return a list of Dag's.
     */
    public static List<Graph> generateMbDags(Graph mbCPDAG,
                                             boolean orientBidirectedEdges,
                                             IndependenceTest test, int depth,
                                             Node target) {
        return new LinkedList<>(MbUtils.listMbDags(new EdgeListGraph(mbCPDAG),
                orientBidirectedEdges, test, depth, target));
    }

    /**
     * Returns an example DAG from the given MB CPDAG.
     *
     * @param mbCpdag The MB CPDAG.
     * @return An example DAG in this CPDAG.
     */
    public static Graph getOneMbDag(Graph mbCpdag) {
        return GraphTransforms.dagFromCpdag(mbCpdag, null);
    }

    /**
     * The recursive method used to list the MB DAGS consistent with an MB CPDAG (i.e. with the independence information
     * available to the search).
     */
    private static Set<Graph> listMbDags(Graph mbCPDAG,
                                         boolean orientBidirectedEdges,
                                         IndependenceTest test, int depth,
                                         Node target) {
        Set<Graph> dags = new HashSet<>();
        Graph graph = new EdgeListGraph(mbCPDAG);
        MbUtils.doAbbreviatedMbOrientation(graph, test, depth, target);
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
                MbUtils.listMbDags(graph, orientBidirectedEdges, test, depth, target));

        graph.setEndpoint(edge.getNode1(), edge.getNode2(), Endpoint.TAIL);
        graph.setEndpoint(edge.getNode2(), edge.getNode1(), Endpoint.ARROW);
        dags.addAll(
                MbUtils.listMbDags(graph, orientBidirectedEdges, test, depth, target));

        return dags;
    }

    /**
     * A reiteration of orientation steps 5-7 of the search for use in generating the list of MB DAGs.
     */
    private static void doAbbreviatedMbOrientation(Graph graph,
                                                   IndependenceTest test,
                                                   int depth, Node target) {
        MeekRules meekRules = new MeekRules();
        meekRules.setRevertToUnshieldedColliders(false);
        meekRules.orientImplied(graph);
        MbUtils.trimToMbNodes(graph, target, false);
        MbUtils.trimEdgesAmongParents(graph, target);
        MbUtils.trimEdgesAmongParentsOfChildren(graph, target);
    }
}




///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.test.MsepTest;

import java.util.*;

/**
 * The MsepVertexCutFinder class is responsible for finding "choke points" within a given directed acyclic graph (DAG)
 * using d-separation principles. Choke points represent sets of nodes that separate two given nodes in the graph.
 * <p>
 * The implementation leverages ancestor maps and considers causal relationships represented in the graph to find paths
 * and cut-points between two nodes.
 */
public class MsepVertexCutFinder {
    private final Graph graph;

    /**
     * Constructs an instance of the MsepVertexCutFinder class with the specified graph.
     *
     * @param graph the graph for which vertex cuts will be identified. This graph serves as the base structure on which
     *              the operations of the MsepVertexCutFinder are conducted.
     */
    public MsepVertexCutFinder(Graph graph) {
        this.graph = graph;
    }

    /**
     * The entry point for the MsepVertexCutFinder application. This method generates a random directed acyclic graph
     * (DAG), initializes necessary objects, and verifies m-separation conditions for pairs of non-adjacent nodes in the
     * graph. It determines choke points and checks their validity based on m-separation.
     *
     * @param args command-line arguments (not used in this implementation).
     */
    public static void main(String[] args) {
        Graph graph = RandomGraph.randomDag(20, 0, 40, 100, 100, 100, false);
        MsepVertexCutFinder finder = new MsepVertexCutFinder(graph);
        MsepTest msep = new MsepTest(graph);

        List<Node> nodes = graph.getNodes();
        Map<Node, Set<Node>> ancestorMap = computeAncestorMap(graph);

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (!x.equals(y) && !graph.isAdjacentTo(x, y)) {
                    Set<Node> chokePoint = finder.findChokePoint(x, y, ancestorMap);

                    if (chokePoint != null && !chokePoint.contains(x) && !chokePoint.contains(y)) {
                        if (msep.checkIndependence(x, y, chokePoint).isIndependent()) {
                            System.out.println("Verified choke point (m-separation) from " + x + " to " + y + ": " + chokePoint);
                        } else {
                            System.out.println("#### NOT VERIFIED! choke point (m-separation) from " + x + " to " + y + ": " + chokePoint);
                        }
                    }
                }
            }
        }
    }

    private static Map<Node, Set<Node>> computeAncestorMap(Graph graph) {
        Map<Node, Set<Node>> ancestorMap = new HashMap<>();
        for (Node node : graph.getNodes()) {
            ancestorMap.put(node, new HashSet<>(graph.paths().getAncestors(Collections.singletonList(node))));
        }
        return ancestorMap;
    }

    private static boolean reachable(Graph graph, Node a, Node b, Set<Node> z, Map<Node, Set<Node>> ancestors) {
        boolean collider = graph.isDefCollider(a, b, b);

        if (!collider && !z.contains(b)) {
            return true;
        }

        if (collider) {
            for (Node zNode : z) {
                if (ancestors.get(b).contains(zNode)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Finds the choke points between a source node and a sink node within a given graph, considering a specific
     * ancestor map and utilizing a d-separation-aware approach.
     *
     * @param source      the starting node in the graph.
     * @param sink        the destination node in the graph.
     * @param ancestorMap a map containing each node's set of ancestors. Used to determine valid paths based on
     *                    d-separation principles.
     * @return a set of nodes representing the choke points between the source and sink if a valid path exists, or null
     * if no such path exists.
     */
    public Set<Node> findChokePoint(Node source, Node sink, Map<Node, Set<Node>> ancestorMap) {
        Set<Node> chokePoint = new HashSet<>();
        Set<Node> visited = new HashSet<>();
        if (dSeparationAwareDfs(source, sink, new HashSet<>(), chokePoint, visited, ancestorMap)) {
            chokePoint.remove(source);
            chokePoint.remove(sink);
            return chokePoint;
        }
        return null;
    }

    private boolean dSeparationAwareDfs(Node current, Node target, Set<Node> conditioned,
                                        Set<Node> chokePoint, Set<Node> visited,
                                        Map<Node, Set<Node>> ancestorMap) {
        if (current.equals(target)) return true;
        visited.add(current);

        for (Node neighbor : graph.getAdjacentNodes(current)) {
            if (visited.contains(neighbor)) continue;
            if (!reachable(graph, current, neighbor, conditioned, ancestorMap)) continue;

            if (conditioned.contains(neighbor) || neighbor.getNodeType() == NodeType.LATENT) continue;

            chokePoint.add(neighbor);
            conditioned.add(neighbor);

            if (!dSeparationAwareDfs(neighbor, target, conditioned, chokePoint, visited, ancestorMap)) {
                chokePoint.remove(neighbor);
                conditioned.remove(neighbor);
            } else {
                return true;
            }
        }

        return false;
    }
}

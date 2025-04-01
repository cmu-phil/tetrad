package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.test.MsepTest;

import java.util.*;

public class MsepVertexCutFinder {
    private final Graph graph;

    public MsepVertexCutFinder(Graph graph) {
        this.graph = graph;
    }

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
}
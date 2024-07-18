package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.util.SublistGenerator;

import java.util.*;

public class SepsetFinder {


    /**
     * Retrieves the sepset (a set of nodes) between two given nodes. The sepset is the minimal set of nodes that need
     * to be conditioned on in order to render two nodes conditionally independent.
     *
     * @param x the first node
     * @param y the second node
     * @return the sepset between the two nodes as a Set<Node>
     */
    public static Set<Node> getSepsetContaining(Graph graph, Node x, Node y, Set<Node> containing) {
        if (graph.getNumEdges(x) < graph.getNumEdges(y)) {
            return getSepsetVisit(graph, x, y, containing, graph.paths().getAncestorMap());
        } else {
            return getSepsetVisit(graph, y, x, containing, graph.paths().getAncestorMap());
        }
    }

    private static Set<Node> getSepsetVisit(Graph graph, Node x, Node y, Set<Node> containing, Map<Node, Set<Node>> ancestorMap) {
        if (x == y) {
            return null;
        }

        Set<Node> z = new HashSet<>(containing);

        Set<Node> _z;

        do {
            _z = new HashSet<>(z);

            Set<Node> path = new HashSet<>();
            path.add(x);
            Set<Triple> colliders = new HashSet<>();

            for (Node b : graph.getAdjacentNodes(x)) {
                if (sepsetPathFound(graph, x, b, y, path, z, colliders, -1, ancestorMap)) {
                    return null;
                }
            }
        } while (!new HashSet<>(z).equals(new HashSet<>(_z)));

        return z;
    }

    private static boolean sepsetPathFound(Graph graph, Node a, Node b, Node y, Set<Node> path, Set<Node> z, Set<Triple> colliders, int bound, Map<Node,
            Set<Node>> ancestorMap) {
        if (b == y) {
            return true;
        }

        if (path.contains(b)) {
            return false;
        }

        if (path.size() > (bound == -1 ? 1000 : bound)) {
            return false;
        }

        path.add(b);

        if (b.getNodeType() == NodeType.LATENT || z.contains(b)) {
            List<Node> passNodes = getPassNodes(graph, a, b, z, ancestorMap);

            for (Node c : passNodes) {
                if (sepsetPathFound(graph, b, c, y, path, z, colliders, bound, ancestorMap)) {
//                    path.remove(b);
                    return true;
                }
            }

            path.remove(b);
            return false;
        } else {
            boolean found1 = false;
            Set<Triple> _colliders1 = new HashSet<>();

            for (Node c : getPassNodes(graph, a, b, z, ancestorMap)) {
                if (sepsetPathFound(graph, b, c, y, path, z, _colliders1, bound, ancestorMap)) {
                    found1 = true;
                    break;
                }
            }

            if (!found1) {
                path.remove(b);
                colliders.addAll(_colliders1);
                return false;
            }

            z.add(b);
            boolean found2 = false;
            Set<Triple> _colliders2 = new HashSet<>();

            for (Node c : getPassNodes(graph, a, b, z, ancestorMap)) {
                if (sepsetPathFound(graph, b, c, y, path, z, _colliders2, bound, ancestorMap)) {
                    found2 = true;
                    break;
                }
            }

            if (!found2) {
                path.remove(b);
                colliders.addAll(_colliders2);
                return false;
            }

//            z.remove(b);
//            path.remove(b);
            return true;
        }
    }

    private static List<Node> getPassNodes(Graph graph, Node a, Node b, Set<Node> z, Map<Node, Set<Node>> ancestorMap) {
        List<Node> passNodes = new ArrayList<>();

        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) {
                continue;
            }

            if (reachable(graph, a, b, c, z, ancestorMap)) {
                passNodes.add(c);
            }
        }

        return passNodes;
    }

    private static boolean reachable(Graph graph, Node a, Node b, Node c, Set<Node> z, Map<Node, Set<Node>> ancestors) {
        boolean collider = graph.isDefCollider(a, b, c);

        if ((!collider || graph.isUnderlineTriple(a, b, c)) && !z.contains(b)) {
            return true;
        }

        if (ancestors == null) {
            return collider && graph.paths().isAncestor(b, z);
        } else {
            boolean ancestor = false;

            for (Node _z : ancestors.get(b)) {
                if (z.contains(_z)) {
                    ancestor = true;
                    break;
                }
            }

            return collider && ancestor;
        }
    }

    public static Set<Node> getSepsetContaining2(Graph graph, Node x, Node y, Set<Node> containing, boolean allowSelectionBias) {
        List<Node> adjx = graph.getAdjacentNodes(x);
        List<Node> adjy = graph.getAdjacentNodes(y);
        adjx.removeAll(graph.getChildren(x));
        adjy.retainAll(graph.getChildren(y));
        adjx.remove(y);
        adjy.remove(x);

        adjx.removeAll(containing);
        adjy.removeAll(containing);

//        adjx.removeIf(z -> !graph.paths().existsTrek(z, y));
//        adjy.removeIf(z -> !graph.paths().existsTrek(z, x));

        List<int[]> choices = new ArrayList<>();

        // Looking at each size subset from 0 up to the number of variables in adjy, for all subsets of that size
        // of adjy, check if the subset is a separating set for x and y.
        for (int i = 0; i <= adjx.size(); i++) {
            SublistGenerator cg = new SublistGenerator(adjx.size(), i);
            int[] choice;

            while ((choice = cg.next()) != null) {
                choices.add(choice);
            }
        }

        int[] sepset = choices.parallelStream().filter(choice -> separates(graph, x, y, allowSelectionBias, combination(choice, adjx), containing)).findFirst().orElse(null);

        if (sepset != null) {
            return combination(sepset, adjx);
        }

        // Do the same for adjy.
        choices.clear();

        // Looking at each size subset from 0 up to the number of variables in adjy, for all subsets of that size
        // of adjy, check if the subset is a separating set for x and y.
        for (int i = 0; i <= adjy.size(); i++) {
            SublistGenerator cg = new SublistGenerator(adjy.size(), i);
            int[] choice;

            while ((choice = cg.next()) != null) {
                choices.add(choice);
            }
        }

        sepset = choices.parallelStream().filter(choice -> separates(graph, x, y, allowSelectionBias, combination(choice, adjy), containing)).findFirst().orElse(null);

        if (sepset != null) {
            return combination(sepset, adjy);
        }

        return null;
    }

    private static Set<Node> combination(int[] choice, List<Node> adj) {
        // Create a set of nodes from the subset of adjx represented by choice.
        Set<Node> combination = new HashSet<>();
        for (int i : choice) {
            combination.add(adj.get(i));
        }
        return combination;
    }

    private static boolean separates(Graph graph, Node x, Node y, boolean allowSelectionBias, Set<Node> combination, Set<Node> containing) {
        if (graph.getNumEdges(x) < graph.getNumEdges(y)) {
            return !graph.paths().isMConnectedTo(x, y, combination, allowSelectionBias);
        } else {
            return !graph.paths().isMConnectedTo(y, x, combination, allowSelectionBias);
        }
    }
}

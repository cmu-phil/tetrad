package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Utilities for computing the Henckel–Perković–Maathuis O-set (optimal adjustment set)
 * in DAGs and amenable CPDAGs.
 */
public final class OSet {

    private OSet() {}

    /**
     * O_G(X -> Y) for a DAG.
     * cn_G(X -> Y) = all vertices on a directed path from X to Y, excluding X but including Y.
     * O_G(X -> Y) = pa_G(cn_G(X -> Y)) \ (cn_G(X -> Y) ∪ {X}).
     *
     * @param dag The DAG
     * @param X The source
     * @param Y The target
     * @return The O-set of (X, Y))
     */
    public static Set<Node> oSetDag(Graph dag, Node X, Node Y) {
        if (!dag.paths().isLegalDag())
            throw new IllegalArgumentException("Graph must be a DAG.");
        if (X == null || Y == null || X.equals(Y))
            throw new IllegalArgumentException("X and Y must be distinct non-null nodes.");

        if (!dag.paths().isGraphAmenable(X, Y, "PDAG", -1, Set.of())) {
            return null; // O-set are only defined when a total effect can be calculated.
        }

        // 1) All potentially-directed paths X ~> Y
        Set<List<Node>> pdPaths =
                dag.paths().potentiallyDirectedPaths(X, Y, -1);

        if (pdPaths == null || pdPaths.isEmpty()) {
            return null; // O-Sets are only defined when the total effect is not zero.
        }

        // 1) causal nodes = nodes on some directed path X -> ... -> Y, excluding X
        Set<Node> causalNodes = new LinkedHashSet<>();
        for (Node v : dag.getNodes()) {
            if (v.equals(X)) continue;

            boolean xToV = dag.paths().existsDirectedPath(X, v);
            boolean vToY;

            if (v.equals(Y)) {
                // Treat Y as trivially on its own path
                vToY = true;
            } else {
                vToY = dag.paths().existsDirectedPath(v, Y);
            }

            if (xToV && vToY) {
                causalNodes.add(v);
            }
        }

        // 2) parents of all causal nodes
        Set<Node> parents = new LinkedHashSet<>();
        for (Node v : causalNodes) {
            parents.addAll(dag.getParents(v));
        }

        // 3) O-set = parents \ (causalNodes ∪ {X})
        parents.removeAll(causalNodes);
        parents.remove(X);

        return parents;
    }

    /**
     * O_G(X -> Y) for an amenable CPDAG or maxPDAG.
     * Uses potentially-directed paths from X to Y to find causal nodes.
     *
     * @param cpdag The CPDAG
     * @param X The source node X
     * @param Y The target node Y
     * @param maxPathLength The maximum path length to consider
     * @return The O-set of (X, Y), or null if O-set is undefined (non-amenable or total effect is zero).
     */
    public static Set<Node> oSetCpdag(Graph cpdag, Node X, Node Y, int maxPathLength) {
        if (X == null || Y == null || X.equals(Y))
            throw new IllegalArgumentException("X and Y must be distinct non-null nodes.");

        // If this CPDAG is actually a DAG, just reuse DAG formula
        if (cpdag.paths().isLegalDag()) {
            return oSetDag(cpdag, X, Y);
        }

        if (!cpdag.paths().isGraphAmenable(X, Y, "PDAG", -1, Set.of())) {
            return null; // O-set are only defined when a total effect can be calculated.
        }

        // 1) All potentially-directed paths X ~> Y
        Set<List<Node>> pdPaths =
                cpdag.paths().potentiallyDirectedPaths(X, Y, maxPathLength);

        if (pdPaths == null || pdPaths.isEmpty()) {
            return null; // O-Sets are only defined when the total effect is not zero.
        }

        // 2) causal nodes = all nodes on those paths, excluding X
        Set<Node> causalNodes = new LinkedHashSet<>();
        for (List<Node> path : pdPaths) {
            for (Node v : path) {
                if (!v.equals(X)) {
                    causalNodes.add(v);
                }
            }
        }

        // 3) parents of causal nodes (directed edges into v)
        Set<Node> parents = new LinkedHashSet<>();
        for (Node v : causalNodes) {
            parents.addAll(cpdag.getParents(v));
        }

        // 4) O-set = parents \ (causalNodes ∪ {X})
        parents.removeAll(causalNodes);
        parents.remove(X);

        return parents;
    }
}
package edu.cmu.tetrad.graph;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GraphUtils2 {
    /**
     * Reverts the provided graph to an unshielded colliders PAG (Partial Ancestral Graph). The operation ensures that
     * all unshielded colliders in the graph are detected and marked appropriately based on adjacency and collider
     * conditions.
     *
     * @param graph the input graph on which the operation is performed. It must represent a directed acyclic graph
     *              (DAG) for correct results.
     * @return a new graph with the specified updates applied, reverting to an unshielded colliders PAG structure.
     */
    public static @NotNull Graph revertUnshieldedPag(Graph graph) {
        Graph _graph = new EdgeListGraph(graph);

        _graph.reorientAllWith(Endpoint.CIRCLE);

        List<Node> nodes = _graph.getNodes();

        for (Node z : nodes) {
            List<Node> adjNodes = _graph.getAdjacentNodes(z);

            for (int i = 0; i < adjNodes.size(); i++) {
                for (int j = i + 1; j < adjNodes.size(); j++) {
                    Node x = adjNodes.get(i);
                    Node y = adjNodes.get(j);

                    if (!graph.isAdjacentTo(x, y) && graph.isDefCollider(x, z, y)) {
                        _graph.setEndpoint(x, z, Endpoint.ARROW);
                        _graph.setEndpoint(y, z, Endpoint.ARROW);
                    }
                }
            }
        }
        return _graph;
    }
}

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.util.*;

/**
 * A class for heuristically removing almost cycles from a PAG to avoid unfaithfulness in an estimated PAG. An almost
 * cycle is a path x ~~&gt; y where x &lt;-&gt; y. Bidirected edge semantics for PAGs require that there be no almost
 * directed cycles, though LV algorithms may produce them.
 * <p>
 * This class is meant to be incorporated into a latent variable algorithm and used to remove almost cycles from the
 * graph in the final step.
 * <p>
 * The method works by identifying almost cyclic paths for x &lt;-&gt; y where there is a semidirected path from x to y
 * in the estimated PAG and then removing all unshielded collider orientations into x for these. This removes the need
 * to orient a collider at x for these edges, and so removes the need to orient a path out of x to y. Almost directed
 * paths are symptomatic of unfaithfulness in the data (implying dependencies that should not exist if the output is a
 * faithful PAG), so this is a reasonable heuristic.
 *
 * @author jdramsey
 */
public class AlmostCycleRemover implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * A map of nodes to parents oriented by triples for them.
     */
    private final Map<Node, Set<Node>> M = new HashMap<>();
    /**
     * A map of nodes to triples for them.
     */
    private final Map<Node, Set<Triple>> T = new HashMap<>();
    /**
     * A map of nodes to bidirected edges for them.
     */
    private Map<Node, Set<Edge>> B;

    /**
     * Constructs a new instance of the AlmostCycleRemover class with the specified Graph.
     */
    public AlmostCycleRemover() {
    }

    /**
     * Returns a map of nodes to bidirected edges for them.
     *
     * @param pag The Graph to be reoriented.
     * @return a map of nodes to bidirected edges for them.
     */
    public static @NotNull Map<Node, Set<Edge>> getBMap(Graph pag) {
        Map<Node, Set<Edge>> B = new HashMap<>();

        for (Edge edge : pag.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                if (pag.paths().existsSemiDirectedPath(edge.getNode1(), edge.getNode2())) {
                    B.computeIfAbsent(edge.getNode1(), k -> new HashSet<>());
                    B.get(edge.getNode1()).add(edge);
                } else if (pag.paths().existsSemiDirectedPath(edge.getNode2(), edge.getNode1())) {
                    B.computeIfAbsent(edge.getNode2(), k -> new HashSet<>());
                    B.get(edge.getNode2()).add(edge);
                }
            }
        }

        return B;
    }

    /**
     * Adds a triple consisting of three given nodes to the data structure. This should be a triple x, b, y where x and
     * y are adjacent to b and oriented into y, and x and y are non-adjacent.
     *
     * @param x the first node
     * @param b the second node
     * @param y the third node
     * @throws IllegalArgumentException if the nodes are not distinct
     */
    public void addTriple(Node x, Node b, Node y) {
        if (!distinct(x, b, y)) {
            throw new IllegalArgumentException("Nodes must be distinct.");
        }

        M.computeIfAbsent(b, k -> new HashSet<>());
        M.get(b).add(x);
        M.get(b).add(y);
        T.computeIfAbsent(b, k -> new HashSet<>());
        T.get(b).add(new Triple(x, b, y));
    }

    /**
     * Removes almost cycles from the Graph. An almost cycle is a path x ~~&gt; y where x &lt;-&gt; y.
     */
    public void removeAlmostCycles(Graph pag) {
        TetradLogger.getInstance().log("Removing almost cycles.");

        Map<Node, Set<Edge>> B = getBMap(pag);
        List<Node> nodesInOrder = new ArrayList<>(B.keySet());
        nodesInOrder.sort(Comparator.comparingInt(x -> B.get(x).size()));

        for (Node x : nodesInOrder) {
            B.remove(x);
            M.remove(x);

            TetradLogger.getInstance().log("Removing almost cycles for node " + x);
        }

        TetradLogger.getInstance().log("Done removing almost cycles.");
    }

    /**
     * Determines whether a triple consisting of three given nodes is allowed. This should be a triple x, b, z where x
     * and z are adjacent to b and oriented into z, and x and z are non-adjacent.
     *
     * @param x the first node
     * @param b the second node
     * @param z the third node
     * @return true if the triple is allowed; false otherwise
     */
    public boolean tripleAllowed(Node x, Node b, Node z) {
        return M.containsKey(b) && M.get(b).contains(x) && M.get(b).contains(z);
    }

    /**
     * Returns the set of nodes that are keys in the map of triples.
     *
     * @return the set of nodes that are keys in the map of triples
     */
    public Set<Node> tKeys() {
        return T.keySet();
    }

    /**
     * Returns the set of triples for the given node.
     *
     * @param y the node
     * @return the set of triples for the given node
     */
    public Set<Triple> getTriple(Node y) {
        return T.get(y);
    }

    /**
     * Recalls unshielded triples in the given graph.
     *
     * @param pag The graph from which unshielded triples should be recalled.
     */
    public void recallUnshieldedTriples(Graph pag) {
        for (Node y : tKeys()) {
            Set<Triple> triples = getTriple(y);

            for (Triple triple : triples) {
                Node x = triple.getX();
                Node b = triple.getY();
                Node z = triple.getZ();

                if (tripleAllowed(x, b, z)) {
                    if (pag.isAdjacentTo(x, b) && pag.isAdjacentTo(z, b)) {
                        pag.setEndpoint(x, b, Endpoint.ARROW);
                        pag.setEndpoint(z, b, Endpoint.ARROW);
                        pag.removeEdge(x, z);
                    }
                }
            }
        }
    }

    /**
     * Determines whether three {@link Node} objects are distinct.
     *
     * @param x the first Node object
     * @param b the second Node object
     * @param y the third Node object
     * @return true if x, b, and y are distinct; false otherwise
     */
    private boolean distinct(Node x, Node b, Node y) {
        return x != b && y != b && x != y;
    }
}

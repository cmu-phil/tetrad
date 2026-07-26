package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static edu.cmu.tetrad.graph.GraphUtils.distinct;

/**
 * Represents a discriminating path in a graph.
 * <p>
 * The path has the form &lt;X, ..., W, V, Y&gt;, where:
 * - V is the discriminated vertex,
 * - V is adjacent to Y on the path,
 * - X is not adjacent to Y (in the strict/original setting),
 * - every vertex between X and V is a collider on the path and a parent of Y.
 * <p>
 * The colliderPath field stores the subpath from X to V excluding X and V but including W.
 * Its order is from W backward toward X, matching the historical Tetrad convention; thus,
 * if the full path is &lt;X, A, ..., W, V, Y&gt;f, colliderPath is [W, ..., A].
 */
public class DiscriminatingPath {
    private final Node x;
    private final Node w;
    private final Node v;
    private final Node y;

    /**
     * Stores the vertices between X and V, excluding X and V but including W,
     * in reverse order: [W, ..., first-after-X].
     */
    private final List<Node> colliderPath;

    /**
     * If true, require X not adjacent to Y and require each interior node to be a parent of Y.
     * If false, use the relaxed condition that each interior node is adjacent to Y and not Y -> interior.
     */
    private final boolean checkXYNonadjacency;

    /**
     * Constructs a DiscriminatingPath object with the specified parameters.
     *
     * @param x                   the starting node in the discriminating path.
     * @param w                   the node following x in the discriminating path.
     * @param v                   the middle node that forms a collider in the path.
     * @param y                   the ending node in the discriminating path.
     * @param colliderPath        the list of nodes that represents the collider path between nodes.
     * @param checkXyNonadjacency a flag indicating whether non-adjacency between certain nodes should be checked.
     */
    public DiscriminatingPath(Node x,
                              Node w,
                              Node v,
                              Node y,
                              LinkedList<Node> colliderPath,
                              boolean checkXyNonadjacency) {
        this.x = x;
        this.w = w;
        this.v = v;
        this.y = y;
        this.colliderPath = colliderPath;
        this.checkXYNonadjacency = checkXyNonadjacency;
    }

    /**
     * Determines whether a discriminating path exists in the given graph.
     * <p>
     * The method evaluates specific conditions such as adjacency, collider properties,
     * and path structure to verify the existence of the discriminating path that starts
     * with a sequence of nodes and satisfies the required constraints.
     *
     * @param graph the graph in which to verify the existence of the discriminating path.
     * @return true if the discriminating path exists in the graph; false otherwise.
     */
    public boolean existsIn(Graph graph) {
        // Distinct distinguished vertices.
        if (!distinct(x, w, v, y)) {
            return false;
        }

        // Strict/original FCI setting: X not adjacent to Y.
        if (checkXYNonadjacency && graph.isAdjacentTo(x, y)) {
            return false;
        }

        // V must be adjacent to Y on the path.
        if (!graph.isAdjacentTo(v, y)) {
            return false;
        }

        // W must be the vertex adjacent to V on the path. By the storage convention
        // [W, ..., first-after-X], W must be the first element of colliderPath.
        if (colliderPath.isEmpty() || colliderPath.get(0) != w) {
            return false;
        }

        // Reconstruct the X ... V part of the path in forward order.
        // colliderPath is stored as [W, ..., first-after-X], so reverse it.
        LinkedList<Node> p = new LinkedList<>();
        p.add(x);
        for (int i = colliderPath.size() - 1; i >= 0; i--) {
            p.add(colliderPath.get(i));
        }
        p.add(v);

        // R4 requires at least three edges in <X, ..., W, V, Y>.
        // Since p is <X, ..., V>, this means p must contain at least 3 vertices:
        // X, W, V at minimum.
        if (p.size() < 3) {
            return false;
        }

        // Enforce that this is a genuine path: all vertices distinct,
        // and Y does not occur earlier in the path.
        Set<Node> seen = new HashSet<>(p);
        if (seen.size() != p.size()) {
            return false;
        }
        if (p.contains(y)) {
            return false;
        }

        // Every vertex between X and V must be a collider on the path and a parent of Y
        // (or the relaxed analogue if checkXYNonadjacency is false).
        for (int i = 1; i < p.size() - 1; i++) {
            Node n1 = p.get(i - 1);
            Node n2 = p.get(i);
            Node n3 = p.get(i + 1);

            if (!graph.isDefCollider(n1, n2, n3)) {
                return false;
            }

            if (checkXYNonadjacency) {
                if (!graph.isParentOf(n2, y)) {
                    return false;
                }
            } else {
                if (!graph.isAdjacentTo(n2, y)) {
                    return false;
                }
                if (graph.getEndpoint(y, n2) == Endpoint.ARROW) {
                    return false;
                }
            }
        }

        // Note: the collider check at W (last loop iteration above) requires an
        // arrowhead at W from V, i.e., V *-> W. This is correct: W lies between
        // X and V and so must be a collider on the path per Zhang's definition.
        return true;
    }

    /**
     * Retrieves the starting node of the discriminating path.
     *
     * @return the starting node in the discriminating path.
     */
    public Node getX() {
        return x;
    }

    /**
     * Retrieves the node following the starting node in the discriminating path.
     *
     * @return the node immediately after the starting node in the discriminating path.
     */
    public Node getW() {
        return w;
    }

    /**
     * Retrieves the middle node that forms a collider in the discriminating path.
     *
     * @return the middle node in the discriminating path.
     */
    public Node getV() {
        return v;
    }

    /**
     * Retrieves the ending node of the discriminating path.
     *
     * @return the ending node in the discriminating path.
     */
    public Node getY() {
        return y;
    }

    /**
     * Retrieves the list of nodes that represents the collider path in the discriminating path.
     *
     * @return a list of nodes constituting the collider path.
     */
    public List<Node> getColliderPath() {
        return colliderPath;
    }

    /**
     * Returns a string representation of the DiscriminatingPath object.
     * The string provides a detailed view of the object's fields including the
     * starting node, intermediate nodes, ending node, and the collider path.
     *
     * @return a string describing the DiscriminatingPath object and its field values.
     */
    @Override
    public String toString() {
        return "DiscriminatingPath{" +
                "x=" + x +
                ", w=" + w +
                ", v=" + v +
                ", y=" + y +
                ", colliderPath=" + colliderPath +
                '}';
    }
}
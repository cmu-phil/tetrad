package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.LinkedList;
import java.util.List;

import static edu.cmu.tetrad.graph.GraphUtils.distinct;

/**
 * Represents a discriminating path in a graph. The triangles that must be oriented this way (won't be done by another
 * rule) all look like the ABC triangle below, where the dots are a collider path from X to V (excluding X and V but
 * including W) with each node on the collider path a parent of Y. The orientation of W *-* V *-* Y is not a feature of
 * the discriminating path. Note that if there is not a circle at V, the path no longer needs to be oriented by the
 * rule. Whether the path exists in a given graph and is as yet unoriented can be checked with the existsAndUnorientedIn
 * method.
 * <pre>
 *          V
 *         *o           * is either an arrowhead or a circle; note V *-> W is not a condition in Zhang's rule
 *        /  \
 *       v    v
 * X....W --> Y
 * </pre>
 * This is equivalent to Zhang's rule R4. (Zhang, J. (2008). On the completeness of orientation rules for causal
 * discovery in the presence of latent confounders and selection bias. Artificial Intelligence, 172(16-17), 1873-1896.)
 * A similar rule was originally given in Spirtes et al. (1993). Note that as in Zhang, the discriminating path itself
 * is X...W, V, Y. We refer to the part of this path between X to V as the 'collider path.' The collider path is
 * included in any sepset of X and Y. Note also that in Zhang's tail-complete version of the rule, the arrow endpoint V
 * *-> W is not a condition of the rule, as in previous code, so we do not check for it.
 * <p>
 * The idea is that if we know that X is independent of Y given all the nodes on the collider path plus perhaps some
 * other nodes in the graph, then there should be a collider at V; otherwise, there should be a noncollider at V. If
 * there should be a collider at V, we orient W *-&gt; V &lt;-&gt; Y; otherwise, we orient W *-* V -&gt; Y.
 *
 * @author josephramsey
 * @see #existsIn(Graph)
 */
public class DiscriminatingPath {
    /**
     * The X node.
     */
    private final Node x;
    /**
     * The W node.
     */
    private final Node w;
    /**
     * The V node.
     */
    private final Node v;
    /**
     * The Y node.
     */
    private final Node y;
    /**
     * Represents a list of nodes that make up a path in a graph, specifically referred to as "collider path". This list
     * includes all the nodes between X and V along the discriminating path, excluding X and V, but including W. The
     * collider path will be included in any sepset of X and Y if this is a discriminating path in the graph.
     *
     * @since 1.0
     */
    private final List<Node> colliderPath;
    private boolean checkXYNonadjacency = true;

    /**
     * Represents a discriminating path construct in a graph. A discriminating path is a path in a graph that meets
     * certain criteria, as explained in the class documentation. This class stores the nodes in the discriminating
     * path, as well as a reference to collider subpath of the discriminating path itself, which consists of all the
     * nodes between X and V along the discriminating path, excluding X and V but including W. These nodes need to be
     * included in any sepset of X and Y in the graph, which can be checked.
     *
     * @param x                   the node X in the discriminating path
     * @param w                   the node W in the discriminating path
     * @param v                   the node V in the discriminating path
     * @param y                   the node Y in the discriminating path
     * @param colliderPath        the collider subpath of the discriminating path
     * @param checkEcNonadjacency whether to check that X is not adjacent to Y
     */
    public DiscriminatingPath(Node x, Node w, Node v, Node y, LinkedList<Node> colliderPath, boolean checkEcNonadjacency) {
        this.x = x;
        this.w = w;
        this.v = v;
        this.y = y;
        this.colliderPath = colliderPath;
        this.checkXYNonadjacency = checkEcNonadjacency;
    }

    /**
     * Checks this discriminating path construct to make sure it is a discriminating path in the given graph. See the
     * class documentation, above, for a description of the requirements.
     *
     * @param graph the graph to check
     * @return true if the discriminating path construct is valid, false otherwise.
     */
    public boolean existsIn(Graph graph) {

        // Check that the nodes are distinct.
        if (!distinct(x, w, v, y)) {
            return false;
        }

        // Relabeling as in Zhang's article:
        //  *         B
        // *         **           * is either an arrowhead, a tail, or a circle.
        // *        /  \
        // *       v    *
        // * E....A --> C

        //  *         V
        // *         **            * is either an arrowhead, a tail, or a circle
        // *        /  \
        // *       v    *
        // * X....W --> Y

        // Make sure there should be a sepset of E and C in the path (Zhang's X and Y). This is the case
        // if E is not adjacent to C.
        if (checkXYNonadjacency && graph.isAdjacentTo(x, y)) {
            return false;
        }

        // C is adjacent to B on the path.
        if (!graph.isAdjacentTo(v, y)) {
            return false;
        }

        // Make sure the path is at least of length 3, which means that E, A, and B need to be on the path. First,
        // we need to make sure A is on the path:
        if (!colliderPath.contains(w)) {
            return false;
        }

        // Then we need to make sure E and B are on the path, E first, B last:
        LinkedList<Node> p = new LinkedList<>(colliderPath.reversed());
        p.addFirst(x);
        p.addLast(v);

        for (int i = 1; i < p.size() - 1; i++) {
            Node n1 = p.get(i - 1);
            Node n2 = p.get(i);
            Node n3 = p.get(i + 1);

            if (!graph.isDefCollider(n1, n2, n3)) {
                return false;
            }

            if (checkXYNonadjacency && !graph.isParentOf(n2, y)) {
                return false;
            }

            if (checkXYNonadjacency) {
                if (!graph.isParentOf(n2, y)) {
                    return false;
                }
            } else {
                if (!graph.isAdjacentTo(y, n2) || graph.getEndpoint(y, n2) == Endpoint.ARROW) {
                    return false;
                }
            }
        }

        if (graph.getEndpoint(v, w) != Endpoint.ARROW) {
            throw new IllegalArgumentException("The edge from v to w must be an arrow.");
        }

        return true;
    }

    /**
     * Returns the node X in the discriminating path.
     *
     * @return the node X in the discriminating path.
     */
    public Node getX() {
        return x;
    }

    /**
     * Retrieves the node W in the discriminating path.
     *
     * @return the node W in the discriminating path
     */
    public Node getW() {
        return w;
    }

    /**
     * Returns the node V in the discriminating path.
     *
     * @return the node V in the discriminating path.
     */
    public Node getV() {
        return v;
    }

    /**
     * Returns the node Y in the discriminating path.
     *
     * @return the node Y in the discriminating path.
     */
    public Node getY() {
        return y;
    }

    /**
     * Returns the collider subpath of the discriminating path.
     *
     * @return the collider subpath of the discriminating path.
     */
    public List<Node> getColliderPath() {
        return colliderPath;
    }

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

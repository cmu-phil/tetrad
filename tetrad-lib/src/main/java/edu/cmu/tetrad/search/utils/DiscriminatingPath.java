package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.LinkedList;
import java.util.List;

/**
 * Represents a discriminating path in a graph. The triangles that must be oriented this way (won't be done by another
 * rule) all look like the ABC triangle below, where the dots are a collider path from E to B (excluding E and B but
 * including A) with each node on the collider path a parent of C. The orientation of A *-* B *-* C is not a feature of
 * the discriminating path. Note that if there is not a circle at B, the path no longer needs to be oriented by the
 * rule. Whether the path exists in a given graph and is as yet unoriented can be checked with the existsAndUnorientedIn
 * method.
 * <pre>
 *          B
 *         *o           * is either an arrowhead or a circle
 *        /  \
 *       v    v
 * E....A --> C
 * </pre>
 * This is equivalent to Zhang's rule R4. (Zhang, J. (2008). On the completeness of orientation rules for causal
 * discovery in the presence of latent confounders and selection bias. Artificial Intelligence, 172(16-17), 1873-1896.)
 * The rule was originally given in Spirtes et al. (1993). Note that as in Zhang, the discriminating path itself is
 * E...A, B, C. We refer to the part of this path between E to B as the 'collider path.' The collider path is included
 * in any sepset of E and C.
 * <p>
 * The idea is that if we know that E is independent of C given all the nodes on the collider path plus perhaps some
 * other nodes in the graph, then there should be a collider at B; otherwise, there should be a noncollider at B. If
 * there should be a collider at B, we orient A *-&gt; B &lt;-&gt; C; otherwise, we orient A *-* B -&gt; C.
 *
 * @author josephramsey
 * @see #existsAndUnorientedIn(Graph)
 */
public class DiscriminatingPath {
    /**
     * The E node.
     */
    private final Node e;
    /**
     * The A node.
     */
    private final Node a;
    /**
     * The B node.
     */
    private final Node b;
    /**
     * The C node.
     */
    private final Node c;
    /**
     * Represents a list of nodes that make up a path in a graph, specifically referred to as "collider path". This list
     * includes all the nodes between E and B along the discriminating path, excluding E and B, but including A. The
     * collider path will be included in any sepset of E and C if this is a discriminating path in the graph.
     *
     * @since 1.0
     */
    private final List<Node> colliderPath;

    /**
     * Represents a discriminating path construct in a graph. A discriminating path is a path in a graph that meets
     * certain criteria, as explained in the class documentation. This class stores the nodes in the discriminating
     * path, as well as a reference to collider subpath of the discriminating path itself, which consists of all of the
     * nodes between E and B along the discriminating path, excluding E and B but including A. These nodes need to be
     * included in any sepset of E and C in the graph, which can be checked.
     *
     * @param e            the node E in the discriminating path
     * @param a            the node A in the discriminating path
     * @param b            the node B in the discriminating path
     * @param c            the node C in the discriminating path
     * @param colliderPath the collider subpath of the discriminating path
     */
    public DiscriminatingPath(Node e, Node a, Node b, Node c, LinkedList<Node> colliderPath) {
        this.e = e;
        this.a = a;
        this.b = b;
        this.c = c;
        this.colliderPath = colliderPath;
    }

    /**
     * Checks this discriminating path construct to make sure it is a discriminating path in the given graph. See the
     * class documentation, above, for a description of the requirements.
     *
     * @param graph the graph to check
     * @return true if the discriminating path construct is valid, false otherwise.
     * @throws IllegalArgumentException if 'e' is adjacent to 'c'
     */
    public boolean existsAndUnorientedIn(Graph graph) {

        // Check that the inducing path has not been oriented.
        if (graph.getEndpoint(c, b) != Endpoint.CIRCLE) {
            return false;
        }

        // Relabeling as in Zhang's article:
        //  *         B
        // *         *o           * is either an arrowhead or a circle
        // *        /  \
        // *       v    v
        // * E....A --> C

        //  *         V
        // *         *o           * is either an arrowhead or a circle
        // *        /  \
        // *       v    v
        // * X....W --> Y

        // Make sure there should be a sepset of E and C in the path (Zhang's X and Y). This is the case
        // if E is not adjacent to C.
        if (graph.isAdjacentTo(e, c)) {
            return false;
        }

        // We don't need this check by Definition 7, since c already needs to be collider on the path from b to a.
//        if (graph.getEndpoint(b, c) != Endpoint.ARROW) {
//            return false;
//        }

        // Also, this check is not required by Definition 7. (It was in the original version of the code.)
//        if (graph.getEndpoint(b, a) != Endpoint.ARROW) {
//            return false;
//        }

        if (!colliderPath.contains(a)) {
            return false;
        }

        LinkedList<Node> p = new LinkedList<>(colliderPath);
        p.addFirst(e);
        p.addLast(b);

        for (int i = 1; i < p.size() - 2; i++) {
            Node n1 = p.get(i - 1);
            Node n2 = p.get(i);
            Node n3 = p.get(i + 1);

            if (!graph.isDefCollider(n1, n2, n3)) {
                return false;
            }

            if (!graph.isParentOf(n2, c)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the node E in the discriminating path.
     *
     * @return the node E in the discriminating path.
     */
    public Node getE() {
        return e;
    }

    /**
     * Retrieves the node A in the discriminating path.
     *
     * @return the node A in the discriminating path
     */
    public Node getA() {
        return a;
    }

    /**
     * Returns the node B in the discriminating path.
     *
     * @return the node B in the discriminating path.
     */
    public Node getB() {
        return b;
    }

    /**
     * Returns the node C in the discriminating path.
     *
     * @return the node C in the discriminating path.
     */
    public Node getC() {
        return c;
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
               "e=" + e +
               ", a=" + a +
               ", b=" + b +
               ", c=" + c +
               ", colliderPath=" + colliderPath +
               '}';
    }

}

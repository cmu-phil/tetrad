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
 * the discriminating path, but the circle at B is insisted upon, since otherwise the path does not need to be oriented
 * by the rule.
 * <pre>
 *          B
 *         *o           * is either an arrowhead or a circle
 *        /  \
 *       v    v
 * E....A --> C
 * </pre>
 * This is equivalent to Zhang's rule R4. (Zhang, J. (2008). On the completeness of orientation rules for causal
 * discovery in the presence of latent confounders and selection bias. Artificial Intelligence, 172(16-17), 1873-1896.)
 * The rule was originally given in Spirtes et al. (1993).
 * <p>
 * The idea is that if we know that E is independent of C given all the nodes on the collider path plus perhaps some
 * other nodes in the graph, then there should be a collider at B; otherwise, there should be a noncollider at B. If
 * there should be a collider at B, we orient A *-&gt; B &lt;-&gt; C; otherwise, we orient A *-* B -&gt; C.
 *
 * @author josephramsey
 */
public class DiscriminatingPath {
    private final Node e;
    private final Node a;
    private final Node b;
    private final Node c;
    private final List<Node> colliderPath;

    public DiscriminatingPath(Node e, Node a, Node b, Node c, LinkedList<Node> colliderPath) {
        this.e = e;
        this.a = a;
        this.b = b;
        this.c = c;
        this.colliderPath = colliderPath;
    }

    /**
     * Checks a discriminating path construct to make sure it satisfies all the requirements. See the class
     * documentation, above.
     *
     * @param graph the graph to check
     * @return true if the discriminating path construct is valid, false otherwise.
     * @throws IllegalArgumentException if 'e' is adjacent to 'c'
     */
    public boolean isValidForGraph(Graph graph) {
        if (graph.getEndpoint(b, c) != Endpoint.ARROW) {
            return false;
        }

        if (graph.getEndpoint(c, b) != Endpoint.CIRCLE) {
            return false;
        }

        if (graph.getEndpoint(a, c) != Endpoint.ARROW) {
            return false;
        }

        if (graph.getEndpoint(b, a) != Endpoint.ARROW) {
            return false;
        }

        if (graph.getEndpoint(c, a) != Endpoint.TAIL) {
            return false;
        }

        if (!colliderPath.contains(a)) {
            return false;
        }

        if (graph.isAdjacentTo(e, c)) {
            return false;
        }

        for (Node n : colliderPath) {
            if (!graph.isParentOf(n, c)) {
                return false;
            }
        }

        return true;
    }

    public Node getE() {
        return e;
    }

    public Node getA() {
        return a;
    }

    public Node getB() {
        return b;
    }

    public Node getC() {
        return c;
    }

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

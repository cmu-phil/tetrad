package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * The FCI orientation rules are almost entirely taken up with an examination of the FCI graph, but there are two rules
 * that require looking at the data. The first is the R0 rule, which orients unshielded colliders in the graph. The
 * second is the R4 rule, which orients certain colliders or tails based on an examination of discriminating paths. For
 * the discriminating path rule, we need to know the sepset for two nodes, e and c, which can only be determined by
 * looking at the data.
 * <p>
 * Since this can be done in various ways, we separate out a Strategy here for this purpose.
 *
 * @author jdramsey
 */
public interface FciOrientDataExaminationStrategy {

    /**
     * Determines if a given triple is an unshielded collider based on an examination of the data.
     *
     * @param graph the graph representation
     * @param a     the first node of the collider path
     * @param b     the second node of the collider path
     * @param c     the third node of the collider path
     * @return true if the collider is unshielded, false otherwise
     */
    boolean isUnshieldedCollider(Graph graph, Node a, Node b, Node c);

    /**
     * Determines the orientation for the nodes in a Directed Acyclic Graph (DAG) based on the Discriminating Path Rule.
     * The discriminating paths are found by FciOrient, but the part of the algorithm that needs to examing the data is
     * separated out into this Strategy. This checks to see whether a sepset for two nodes, e and c, contains b. All of
     * the nodes along the collider path must be in the sepset; otherwise, the orientation is not determined. This may
     * be checked directly by checking to make sure the sepset for e and c contains the given path (which is passed in
     * from FciOrient). Or it may be assumed that this sepset will contain the path, sinc theoretically it must.
     * <p>
     * Here is the information about what is being done:
     * <p>
     * The triangles that must be oriented this way (won't be done by another rule) all look like the ones below, where
     * the dots are a collider path from E to A with each node on the path (except E) a parent of C.
     * <pre>
     *          B
     *         xo           x is either an arrowhead or a circle
     *        /  \
     *       v    v
     * E....A --> C
     * </pre>
     * <p>
     * The orientation that is being discriminated here is whether there is a collider at B or a noncollider at B. If a
     * collider, then A *-&gt; B &lt;-* C is oriented; if a tail, then B --&gt; C is oriented.
     * <p>
     * So don't screw this up! jdramsey 2024-7-25
     * <p>
     * This is Zhang's rule R4, discriminating paths.
     *
     * @param e     the 'e' node
     * @param a     the 'a' node
     * @param b     the 'b' node
     * @param c     the 'c' node
     * @param path  the collider path from 'e' to 'b', not including 'e' but including 'a'.
     * @param graph the graph to be oriented.
     * @return true if an orientation is done, false otherwise.
     */
    boolean doDiscriminatingPathOrientation(DiscriminatingPath discriminatingPath, Graph graph);

    /**
     * Triple-checks a discriminating path construct to make sure it satisfies all of the requirements.
     * <p>
     * Here, we insist that the sepset for D and B contain all the nodes along the collider path.
     * <p>
     * Reminder:
     * <pre>
     *      The triangles that must be oriented this way (won't be done by another rule) all look like the ones below, where
     *      the dots are a collider path from E to A with each node on the path (except E) a parent of C.
     *
     *               B
     *              xo           x is either an arrowhead or a circle
     *             /  \
     *            v    v
     *      E....A --> C
     *
     *      This is Zhang's rule R4, discriminating paths. The "collider path" here is all of the collider nodes
     *      along the E...A path (all parents of C), including A. The idea is that is we know that E is independent
     *      of C given all of nodes on the collider path plus perhaps some other nodes, then there should be a collider
     *      at B; otherwise, there should be a noncollider at B.
     * </pre>
     *
     * @param e     the 'e' node
     * @param a     the 'a' node
     * @param b     the 'b' node
     * @param c     the 'c' node
     * @param path  the collider path from 'e' to 'b', not including 'e' but including 'a'.
     * @param graph the graph representation
     * @throws IllegalArgumentException if 'e' is adjacent to 'c'
     * @return  true if the discriminating path construct is valid, false otherwise.
     */
    default boolean doubleCheckDiscriminatingPathConstruct(Node e, Node a, Node b, Node c, List<Node> path, Graph graph) {
        if (graph.getEndpoint(b, c) != Endpoint.ARROW) {
//            throw new IllegalArgumentException("This is not a discriminating path construct.");
            return false;
        }

        if (graph.getEndpoint(c, b) != Endpoint.CIRCLE) {
//            throw new IllegalArgumentException("This is not a discriminating path construct.");
            return false;
        }

        if (graph.getEndpoint(a, c) != Endpoint.ARROW) {
//            throw new IllegalArgumentException("This is not a dicriminatin path construct.");
            return false;
        }

        if (graph.getEndpoint(b, a) != Endpoint.ARROW) {
//            throw new IllegalArgumentException("This is not a discriminating path construct.");
            return false;
        }

        if (graph.getEndpoint(c, a) != Endpoint.TAIL) {
//            throw new IllegalArgumentException("This is not a discriminating path construct.");
            return false;
        }

        if (!path.contains(a)) {
//            throw new IllegalArgumentException("This is not a discriminating path construct.");
            return false;
        }

        if (graph.isAdjacentTo(e, c)) {
//            throw new IllegalArgumentException("This is not a discriminating path construct.");
            return false;
        }

        for (Node n : path) {
            if (!graph.isParentOf(n, c)) {
//                throw new IllegalArgumentException("Node " + n + " is not a parent of " + c);
                return false;
            }
        }

        return true;
    }

    /**
     * Sets the knowledge object to be used by the strategy.
     *
     * @param knowledge the knowledge object.
     */
    void setKnowledge(Knowledge knowledge);

    /**
     * Returns the knowledge object used by the strategy.
     *
     * @return the knowledge object.
     */
    Knowledge getknowledge();
}

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The FCI orientation rules are almost entirely taken up with an examination of the FCI graph, but there are two rules
 * that require looking at the distribution. The first is the R0 rule, which orients unshielded colliders in the graph.
 * The second is the R4 rule, which orients certain colliders or tails based on an examination of discriminating paths.
 * For the discriminating path rule, we need to know the sepset for two nodes, e and c, which can only be determined by
 * looking at the distribution.
 * <p>
 * Note that for searches from Oracle, the distribution is not available, but these rules can be applied using knowledge
 * of the true DAG (with latents).
 * <p>
 * Since this can be done in various ways, we separate out a Strategy here for this purpose.
 *
 * @author jdramsey
 */
public interface R0R4Strategy {

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
     * Performs the discriminating path orientation for the given discriminating path.
     *
     * @param discriminatingPath the discriminating path construct
     * @param graph              the graph to be oriented.
     * @return a pair of the discriminating path construct and a boolean indicating whether the orientation was
     * determined.
     * @see DiscriminatingPath
     */
    Pair<DiscriminatingPath, Boolean> doDiscriminatingPathOrientation(DiscriminatingPath discriminatingPath, Graph graph);

    /**
     * Checks a discriminating path construct to make sure it satisfies all the requirements.
     *
     * @param path  the discriminating path, x->p1<->....<->pn<->w-ovo->y, with p1,...,pn parents of y.
     * @param graph the graph representation
     * @return true if the discriminating path construct is valid, false otherwise.
     * @throws IllegalArgumentException if 'x' is adjacent to 'y'
     * @see DiscriminatingPath
     */
    default boolean discriminatingPathIllFormed(List<Node> path, Graph graph) {
        Node x = path.get(0);
        Node y = path.get(path.size() - 1);

        if (path.size() - 1 < 3) {
            return true;
        }

        if (graph.isAdjacentTo(x, y)) {
            return false;
        }

        for (int i = 1; i < path.size() - 3; i++) {
            Node p1 = path.get(i - 1);
            Node p2 = path.get(i);
            Node p3 = path.get(i + 1);

            if (!graph.isDefCollider(p1, p2, p3)) {
                return true;
            }

            if (!graph.isParentOf(p2, y)) {
                return true;
            }
        }

        return false;
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

    /**
     * Sets the allowed colliders for the current strategy.
     *
     * @param allowedColliders a Set of Triple objects representing the allowed colliders
     */
    void setAllowedColliders(Set<Triple> allowedColliders);

    default Set<Triple> getInitialAllowedColliders() {
        return null;
    }

    default void setInitialAllowedColliders(HashSet<Triple> initialAllowedColliders) {
        // no op.
    }
}

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
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
 * @author josephramsey
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
     * Does a discriminating path orientation based on an examination of the data.
     *
     * @param discriminatingPath the discriminating path construct
     * @param graph              the graph to be oriented.
     * @param vNodes             the set of nodes that are v-structures in the graph.
     * @return a pair of the discriminating path construct and a boolean indicating whether the orientation was
     * determined.
     * @see DiscriminatingPath
     */
    Pair<DiscriminatingPath, Boolean> doDiscriminatingPathOrientation(DiscriminatingPath discriminatingPath, Graph graph, Set<Node> vNodes);

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

    /**
     * Returns the allowed colliders for the current strategy.
     *
     * @return a Set of Triple objects representing the allowed colliders
     */
    default Set<Triple> getInitialAllowedColliders() {
        return null;
    }

    /**
     * Sets the initial allowed colliders for the current strategy.
     *
     * @param initialAllowedColliders a Set of Triple objects representing the allowed colliders
     */
    default void setInitialAllowedColliders(HashSet<Triple> initialAllowedColliders) {
        // no op.
    }
}

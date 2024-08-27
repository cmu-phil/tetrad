package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Set;

/**
 * The FciOrientDataExaminationStrategyTestBased class implements the FciOrientDataExaminationStrategy interface and
 * provides methods for checking unshielded colliders and determining orientations based on the Discriminating Path
 * Rule.
 * <p>
 * This classes uses a TeyssierScorer object to determine the sepset for two nodes, e and c, which can only be
 * determined by looking at the data.
 *
 * @author jdramsey
 * @see R0R4Strategy
 */
public class R0R4StrategyScoreBased implements R0R4Strategy {

    /**
     * The scorer used for scoring the nodes in a Directed Acyclic Graph (DAG). It is of type TeyssierScorer.
     */
    private final TeyssierScorer scorer;
    /**
     * The knowledge object used for storing the knowledge of the nodes in a Directed Acyclic Graph (DAG).
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * The depth of the Directed Acyclic Graph (DAG).
     */
    private int depth = -1;
    /**
     * A boolean value indicating whether the verbose mode is on or off.
     */
    private boolean verbose;

    /**
     * Constructs a new FciOrientDataExaminationStrategyScoreBased object with the given TeyssierScorer object.
     *
     * @param scorer the TeyssierScorer object
     */
    private R0R4StrategyScoreBased(TeyssierScorer scorer) {
        this.scorer = scorer;
    }

    /**
     * Returns a special configuration of FciOrientDataExaminationStrategy.
     *
     * @param scorer    the TeyssierScorer object
     * @param knowledge the Knowledge object
     * @param verbose   a boolean indicating if verbose mode is enabled
     * @param depth     the depth
     * @return an instance of FciOrientDataExaminationStrategy with the specified configuration
     */
    public static R0R4Strategy specialConfiguration(TeyssierScorer scorer, Knowledge knowledge, boolean verbose, int depth) {
        R0R4StrategyScoreBased strategy = new R0R4StrategyScoreBased(scorer);
        strategy.knowledge = knowledge;
        strategy.verbose = verbose;
        strategy.depth = depth;
        return strategy;
    }

    /**
     * Returns a default configuration of the FciOrientDataExaminationStrategy.
     *
     * @param scorer    the TeyssierScorer object
     * @param knowledge the Knowledge object
     * @param verbose   a boolean indicating if verbose mode is enabled
     * @return an instance of FciOrientDataExaminationStrategy with the default configuration
     */
    public static R0R4Strategy defaultConfiguration(TeyssierScorer scorer, Knowledge knowledge, boolean verbose) {
        return R0R4StrategyScoreBased.specialConfiguration(scorer, knowledge, true, 5);
    }

    /**
     * Does a discriminating path orientation based on the Discriminating Path Rule.
     *
     * @param discriminatingPath the discriminating path
     * @param graph              the graph representation
     * @return The discriminating path is returned as the first element of the pair, and a boolean indicating whether
     * the orientation was done is returned as the second element of the pair.
     * @throws IllegalArgumentException if 'e' is adjacent to 'c'
     * @see DiscriminatingPath
     */
    @Override
    public Pair<DiscriminatingPath, Boolean> doDiscriminatingPathOrientation(DiscriminatingPath discriminatingPath, Graph graph) {
        Node e = discriminatingPath.getE();
        Node a = discriminatingPath.getA();
        Node b = discriminatingPath.getB();
        Node c = discriminatingPath.getC();
        List<Node> path = discriminatingPath.getColliderPath();

        // Check that the discriminating path construct still exists in the graph.
        if (!discriminatingPath.existsIn(graph)) {
            return Pair.of(discriminatingPath, false);
        }

        // Check that the discriminating path has not yet been oriented; we don't need to list the ones that have
        // already been oriented.
        if (graph.getEndpoint(c, b) != Endpoint.CIRCLE) {
            return Pair.of(discriminatingPath, false);
        }

        System.out.println("For discriminating path rule, tucking");
        scorer.goToBookmark();
        scorer.tuck(c, b);
        scorer.tuck(e, b);
        scorer.tuck(a, c);
        boolean collider = !scorer.adjacent(e, c);
        System.out.println("For discriminating path rule, found collider = " + collider);

        if (collider) {
            graph.setEndpoint(a, b, Endpoint.ARROW);
            graph.setEndpoint(c, b, Endpoint.ARROW);

            if (verbose) {
                TetradLogger.getInstance().log(
                        "R4: Definite discriminating path collider rule e = " + e + " " + GraphUtils.pathString(graph, a, b, c));
            }

            return Pair.of(discriminatingPath, true);
        } else {
            graph.setEndpoint(c, b, Endpoint.TAIL);

            if (verbose) {
                TetradLogger.getInstance().log(
                        "R4: Definite discriminating path tail rule e = " + e + " " + GraphUtils.pathString(graph, a, b, c));
            }

            return Pair.of(discriminatingPath, true);
        }
    }

    @Override
    public Knowledge getknowledge() {
        return null;
    }

    @Override
    public void setAllowedColliders(Set<Triple> allowedColliders) {

    }

    /**
     * Checks if a collider is unshielded or not.
     *
     * @param graph the graph containing the nodes
     * @param i     the first node of the collider
     * @param j     the second node of the collider
     * @param k     the third node of the collider
     * @return true if the collider is unshielded, false otherwise
     */
    @Override
    public boolean isUnshieldedCollider(Graph graph, Node i, Node j, Node k) {
        return scorer.unshieldedCollider(i, j, k);
    }

    /**
     * Sets the verbose mode for this FciOrientDataExaminationStrategyScoreBased object.
     *
     * @param verbose a boolean indicating if verbose mode is enabled
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Retrieves the knowledge associated with this instance.
     *
     * @return the Knowledge object associated with this instance
     */
    public Knowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the Knowledge object for this instance.
     *
     * @param knowledge the Knowledge object to be set
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * Retrieves the depth value of the FciOrientDataExaminationStrategyScoreBased object.
     *
     * @return the depth value of the FciOrientDataExaminationStrategyScoreBased object
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Sets the depth value of the FciOrientDataExaminationStrategyScoreBased object.
     *
     * @param depth the depth value to be set
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }
}

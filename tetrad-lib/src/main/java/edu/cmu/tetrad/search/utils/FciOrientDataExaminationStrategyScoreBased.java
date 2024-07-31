package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.List;

/**
 * The FciOrientDataExaminationStrategyTestBased class implements the FciOrientDataExaminationStrategy interface and
 * provides methods for checking unshielded colliders and determining orientations based on the Discriminating Path
 * Rule.
 * <p>
 * This classes uses a TeyssierScorer object to determine the sepset for two nodes, e and c, which can only be
 * determined by looking at the data.
 *
 * @author jdramsey
 * @see FciOrientDataExaminationStrategy
 */
public class FciOrientDataExaminationStrategyScoreBased implements FciOrientDataExaminationStrategy {

    /**
     * The scorer used for scoring the nodes in a Directed Acyclic Graph (DAG).
     * It is of type TeyssierScorer.
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
     * A boolean value indicating whether the Discriminating Path Collider Rule is to be used or not.
     */
    private boolean doDiscriminatingPathColliderRule;
    /**
     * A boolean value indicating whether the Discriminating Path Tail Rule is to be used or not.
     */
    private boolean doDiscriminatingPathTailRule;

    /**
     * Constructs a new FciOrientDataExaminationStrategyScoreBased object with the given TeyssierScorer object.
     *
     * @param scorer the TeyssierScorer object
     */
    private FciOrientDataExaminationStrategyScoreBased(TeyssierScorer scorer) {
        this.scorer = scorer;
    }

    /**
     * Returns a special configuration of FciOrientDataExaminationStrategy.
     *
     * @param scorer                       the TeyssierScorer object
     * @param knowledge                    the Knowledge object
     * @param completeRuleSetUsed          a boolean indicating if the complete rule set is used
     * @param doDiscriminatingPathTailRule a boolean indicating if the discriminating path tail rule is applied
     * @param doDiscriminatingPathColliderRule a boolean indicating if the discriminating path collider rule is applied
     * @param maxPathLength                the maximum path length
     * @param verbose                      a boolean indicating if verbose mode is enabled
     * @param depth                        the depth
     * @return an instance of FciOrientDataExaminationStrategy with the specified configuration
     */
    public static FciOrientDataExaminationStrategy specialConfiguration(TeyssierScorer scorer, Knowledge knowledge, boolean completeRuleSetUsed,
                                                                        boolean doDiscriminatingPathTailRule, boolean doDiscriminatingPathColliderRule,
                                                                        int maxPathLength, boolean verbose, int depth) {
        FciOrientDataExaminationStrategyScoreBased strategy = new FciOrientDataExaminationStrategyScoreBased(scorer);
        strategy.knowledge = knowledge;
        strategy.doDiscriminatingPathTailRule = doDiscriminatingPathTailRule;
        strategy.doDiscriminatingPathColliderRule = doDiscriminatingPathColliderRule;
        strategy.verbose = verbose;
        strategy.depth = depth;
        return strategy;
    }

    /**
     * Returns a default configuration of the FciOrientDataExaminationStrategy.
     *
     * @param scorer  the TeyssierScorer object
     * @param knowledge  the Knowledge object
     * @param verbose  a boolean indicating if verbose mode is enabled
     * @return an instance of FciOrientDataExaminationStrategy with the default configuration
     */
    public static FciOrientDataExaminationStrategy defaultConfiguration(TeyssierScorer scorer, Knowledge knowledge, boolean verbose) {
        return FciOrientDataExaminationStrategyScoreBased.specialConfiguration(scorer, knowledge, true,
                true, true, -1, verbose, 5);
    }

    /**
     * Determines the orientation for the nodes in a Directed Acyclic Graph (DAG) based on the Discriminating Path Rule
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
     *      along the E...A path (all parents of C), including A. The idea is that if we know that E is independent
     *      of C given all of nodes on the collider path plus perhaps some other nodes, then there should be a collider
     *      at B; otherwise, there should be a noncollider at B.
     * </pre>
     *
     * @param e     the 'e' node
     * @param a     the 'a' node
     * @param b     the 'b' node
     * @param c     the 'c' node
     * @param graph the graph representation
     * @return true if the orientation is determined, false otherwise
     * @throws IllegalArgumentException if 'e' is adjacent to 'c'
     */
    @Override
    public boolean doDiscriminatingPathOrientation(DiscriminatingPath discriminatingPath, Graph graph) {
        Node e = discriminatingPath.getE();
        Node a = discriminatingPath.getA();
        Node b = discriminatingPath.getB();
        Node c = discriminatingPath.getC();
        List<Node> path = discriminatingPath.getColliderPath();

        System.out.println("For discriminating path rule, tucking");
        scorer.goToBookmark();
        scorer.tuck(c, b);
        scorer.tuck(e, b);
        scorer.tuck(a, c);
        boolean collider = !scorer.adjacent(e, c);
        System.out.println("For discriminating path rule, found collider = " + collider);

        if (collider) {
            if (doDiscriminatingPathColliderRule) {
                graph.setEndpoint(a, b, Endpoint.ARROW);
                graph.setEndpoint(c, b, Endpoint.ARROW);

                if (verbose) {
                    TetradLogger.getInstance().log(
                            "R4: Definite discriminating path collider rule e = " + e + " " + GraphUtils.pathString(graph, a, b, c));
                }

                return true;
            }
        } else {
            if (doDiscriminatingPathTailRule) {
                graph.setEndpoint(c, b, Endpoint.TAIL);

                if (verbose) {
                    TetradLogger.getInstance().log(
                            "R4: Definite discriminating path tail rule e = " + e + " " + GraphUtils.pathString(graph, a, b, c));
                }

                return true;
            }
        }

        return false;
    }

    @Override
    public Knowledge getknowledge() {
        return null;
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

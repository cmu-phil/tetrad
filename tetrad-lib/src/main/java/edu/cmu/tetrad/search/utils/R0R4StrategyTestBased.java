package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SepsetFinder;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The FciOrientDataExaminationStrategyTestBased class implements the FciOrientDataExaminationStrategy interface and
 * provides methods for checking unshielded colliders and determining orientations based on the Discriminating Path
 * Rule.
 * <p>
 * This classes uses a test to determine the sepset for two nodes, e and c, which can only be determined by looking at
 * the data.
 *
 * @author jdramsey
 * @see R0R4Strategy
 */
public class R0R4StrategyTestBased implements R0R4Strategy {

    /**
     * The test variable holds an instance of the IndependenceTest class. It is a final variable, meaning its value
     * cannot be changed once assigned. This variable is a private field and can only be accessed within the containing
     * class FciOrientDataExaminationStrategyTestBased.
     */
    private final IndependenceTest test;

    /**
     * Private variable representing the knowledge.
     * <p>
     * This variable holds the knowledge used by the FciOrientDataExaminationStrategyTestBased class. It is an instance
     * of the Knowledge class.
     *
     * @see R0R4StrategyTestBased
     * @see Knowledge
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Private variable representing the depth--that is, the maximum number of variables conditioned in in any test of
     * independence.
     */
    private int depth = -1;
    /**
     * Determines whether verbose mode is enabled or not.
     */
    private boolean verbose = false;
    /**
     * The Set of Triples representing the allowed colliders for the FciOrientDataExaminationStrategy. This variable is
     * initially set to null. Use the setAllowedColliders method to set the allowed colliders. Use the
     * getInitialAllowedColliders method to retrieve the initial set of allowed colliders.
     */
    private Set<Triple> allowedColliders = null;
    /**
     * This variable represents the initial set of allowed colliders for the FciOrientDataExaminationStrategy. It is a
     * HashSet containing Triples that represent the allowed colliders.
     * <p>
     * The value of this variable can be set using the setInitialAllowedColliders() method and retrieved using the
     * getInitialAllowedColliders() method.
     * <p>
     * Example usage:
     * <p>
     * FciOrientDataExaminationStrategyTestBased strategy = new FciOrientDataExaminationStrategyTestBased();
     * <p>
     * // Create a HashSet of Triples representing the allowed colliders HashSet<Triple> allowedColliders = new
     * HashSet<>(); Triple collider1 = new Triple(node1, node2, node3); Triple collider2 = new Triple(node4, node5,
     * node6); allowedColliders.add(collider1); allowedColliders.add(collider2);
     * <p>
     * // Set the initial allowed colliders for the strategy strategy.setInitialAllowedColliders(allowedColliders);
     * <p>
     * // Retrieve the initial allowed colliders HashSet<Triple> initialAllowedColliders =
     * strategy.getInitialAllowedColliders();
     * <p>
     * Note: This is an example and the actual values and implementation may vary depending on the context.
     */
    private HashSet<Triple> initialAllowedColliders = null;
    /**
     * The maximum length of the path, for relevant paths.
     */
    private int maxLength;

    /**
     * Creates a new instance of FciOrientDataExaminationStrategyTestBased.
     *
     * @param test the IndependenceTest object used by the strategy
     */
    public R0R4StrategyTestBased(IndependenceTest test) {
        this.test = test;
    }

    /**
     * Provides a special configuration for creating an instance of FciOrientDataExaminationStrategy.
     *
     * @param test      the IndependenceTest object used by the strategy
     * @param knowledge the Knowledge object used by the strategy
     * @param verbose   boolean indicating whether to provide verbose output
     * @return a configured FciOrientDataExaminationStrategy object
     * @throws IllegalArgumentException if test or knowledge is null
     */
    public static R0R4Strategy specialConfiguration(IndependenceTest test, Knowledge knowledge,
                                                    boolean verbose) {
        if (test == null) {
            throw new IllegalArgumentException("Test is null.");
        }

        if (knowledge == null) {
            throw new IllegalArgumentException("Knowledge is null.");
        }

        if (test instanceof MsepTest) {
            R0R4Strategy r0R4Strategy = R0R4StrategyTestBased.defaultConfiguration(((MsepTest) test).getGraph(), knowledge);
            R0R4StrategyTestBased _r0R4Strategy = (R0R4StrategyTestBased) r0R4Strategy;
            _r0R4Strategy.setVerbose(verbose);
            return _r0R4Strategy;
        } else {
            R0R4StrategyTestBased strategy = new R0R4StrategyTestBased(test);
            strategy.setKnowledge(knowledge);
            strategy.setVerbose(verbose);
            return strategy;
        }
    }

    /**
     * Returns a default configuration of the FciOrientDataExaminationStrategy object.
     *
     * @param dag       the graph representation
     * @param knowledge the Knowledge object used by the strategy
     * @return a default configured FciOrientDataExaminationStrategy object
     */
    public static R0R4Strategy defaultConfiguration(Graph dag, Knowledge knowledge) {
        return defaultConfiguration(new MsepTest(dag), knowledge);
    }

    /**
     * Returns a default configuration of the FciOrientDataExaminationStrategy object.
     *
     * @param test      the IndependenceTest object used by the strategy
     * @param knowledge the Knowledge object used by the strategy
     * @return a configured FciOrientDataExaminationStrategy object
     * @throws IllegalArgumentException if test or knowledge is null
     */
    public static R0R4Strategy defaultConfiguration(IndependenceTest test, Knowledge knowledge) {
        R0R4StrategyTestBased strategy = new R0R4StrategyTestBased(test);
        strategy.setKnowledge(knowledge);
        return strategy;
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
        Set<Node> sepset = SepsetFinder.getSepsetContainingGreedy(graph, i, k, new HashSet<>(), test, depth);
        return sepset != null && !sepset.contains(j);
    }

    /**
     * Does a discriminating path orientation.
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

        // Check that the discriminating path has not yet been oriented; we don't need to list the ones that have
        // already been oriented.
        if (graph.getEndpoint(c, b) != Endpoint.CIRCLE) {
            return Pair.of(discriminatingPath, false);
        }

        // Check that the discriminating path construct still exists in the graph.
        if (!discriminatingPath.existsIn(graph)) {
            return Pair.of(discriminatingPath, false);
        }

        for (Node n : path) {
            if (!graph.isParentOf(n, c)) {
                throw new IllegalArgumentException("Node " + n + " is not a parent of " + c);
            }
        }

        Set<Node> blocking = SepsetFinder.getPathBlockingSetRecursive(graph, e, c, new HashSet<>(), maxLength);

        blocking.add(b);

        if (!test.checkIndependence(e, c, blocking).isIndependent()) {
            blocking.remove(b);
        }

        boolean collider = !blocking.contains(b);

        if (collider) {
            if (graph.getEndpoint(c, b) != Endpoint.CIRCLE) {
                return Pair.of(discriminatingPath, false);
            }

            if (!FciOrient.isArrowheadAllowed(a, b, graph, knowledge)) {
                return Pair.of(discriminatingPath, false);
            }

            if (!FciOrient.isArrowheadAllowed(c, b, graph, knowledge)) {
                return Pair.of(discriminatingPath, false);
            }

            if (initialAllowedColliders != null) {
                initialAllowedColliders.add(new Triple(a, b, c));
            } else {
                if (allowedColliders != null && !allowedColliders.contains(new Triple(a, b, c))) {
                    return Pair.of(discriminatingPath, false);
                }
            }

            graph.setEndpoint(a, b, Endpoint.ARROW);
            graph.setEndpoint(c, b, Endpoint.ARROW);

            if (verbose) {
                TetradLogger.getInstance().log("R4: Discriminating path oriented: " + discriminatingPath);
                TetradLogger.getInstance().log("    Oriented as: " + GraphUtils.pathString(graph, a, b, c));
            }

            return Pair.of(discriminatingPath, true);
        } else {
            if (graph.getEndpoint(c, b) != Endpoint.CIRCLE) {
                return Pair.of(discriminatingPath, false);
            }

            graph.setEndpoint(c, b, Endpoint.TAIL);

            if (verbose) {
                TetradLogger.getInstance().log("R4: Discriminating path oriented: " + discriminatingPath);
                TetradLogger.getInstance().log("    Oriented as: " + GraphUtils.pathString(graph, a, b, c));
            }

            return Pair.of(discriminatingPath, true);
        }
    }

    /**
     * Sets the knowledge object used by the FciOrientDataExaminationStrategy.
     *
     * @param knowledge the knowledge object to be set
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * Retrieves the Knowledge object used by the FciOrientDataExaminationStrategy.
     *
     * @return the Knowledge object used by the strategy
     */
    @Override
    public Knowledge getknowledge() {
        return knowledge;
    }

    /**
     * Sets the allowed colliders for the FciOrientDataExaminationStrategy.
     *
     * @param allowedColliders the Set of Triples representing allowed colliders
     */
    @Override
    public void setAllowedColliders(Set<Triple> allowedColliders) {
        this.allowedColliders = allowedColliders;
    }

    /**
     * Sets the verbose mode for the FciOrientDataExaminationStrategy object.
     *
     * @param verbose true to enable verbose output, false otherwise
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the depth for the FciOrientDataExaminationStrategy object.
     *
     * @param depth the depth to be set for the strategy
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Retrieves the IndependenceTest object used by the strategy.
     *
     * @return the IndependenceTest object used by the strategy
     */
    public IndependenceTest getTest() {
        return test;
    }

    /**
     * Retrieves the initial set of allowed colliders.
     *
     * @return The initial set of allowed colliders.
     */
    public Set<Triple> getInitialAllowedColliders() {
        return initialAllowedColliders;
    }

    /**
     * Sets the initial set of allowed colliders for the FciOrientDataExaminationStrategy.
     *
     * @param initialAllowedColliders the HashSet containing the initial allowed colliders
     */
    public void setInitialAllowedColliders(HashSet<Triple> initialAllowedColliders) {
        this.initialAllowedColliders = initialAllowedColliders;
    }

    /**
     * Sets the maximum length for relevant paths.
     *
     * @param maxLength the maximum length to be set. Set to -1 for no maximum length.
     */
    public void setMaxLength(int maxLength) {
        if (maxLength < -1) {
            throw new IllegalArgumentException("Maximum length must be -1 or greater.");
        }

        this.maxLength = maxLength;
    }
}

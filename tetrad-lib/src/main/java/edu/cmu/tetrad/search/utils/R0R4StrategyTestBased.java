package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SepsetFinder;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
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
     * The type of blocking strategy used in the R0R4StrategyTestBased class.
     * This variable determines whether the strategy will be recursive or greedy.
     */
    private BlockingType blockingType = BlockingType.RECURSIVE;
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
    private int maxLength = -1;
    /**
     * The PAG (partial ancestral graph) for the strategy.
     */
    private Graph pag = null;
    /**
     * Helper variable of type EnsureMarkov used for ensuring Markov properties in the R0R4StrategyTestBased class.
     * Initialized to null by default.
     */
    private EnsureMarkov ensureMarkovHelper = null;

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
    public static R0R4Strategy specialConfiguration(IndependenceTest test, Knowledge knowledge, boolean verbose) {
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
     * @param vNodes             the set of v-nodes
     * @return The discriminating path is returned as the first element of the pair, and a boolean indicating whether
     * the orientation was done is returned as the second element of the pair.
     * @throws IllegalArgumentException if 'e' is adjacent to 'c'
     * @see DiscriminatingPath
     */
    @Override
    public Pair<DiscriminatingPath, Boolean> doDiscriminatingPathOrientation(DiscriminatingPath discriminatingPath, Graph graph, Set<Node> vNodes) throws InterruptedException {
        Node x = discriminatingPath.getX();
        Node w = discriminatingPath.getW();
        Node v = discriminatingPath.getV();
        Node y = discriminatingPath.getY();
        List<Node> path = discriminatingPath.getColliderPath();

        // Check that the discriminating path still exists in the graph. Note that at this point nothing is claimed
        // about the orientation of W<-*V*->Y.
        if (!discriminatingPath.existsIn(graph)) {
            return Pair.of(discriminatingPath, false);
        }

        // Check that the discriminating path has not yet been oriented; we don't need to orient those. This also
        // makes sure that W<-*V*->Y has not yet been oriented as a collider, which is necessary below.
        if (graph.getEndpoint(y, v) != Endpoint.CIRCLE) {
            return Pair.of(discriminatingPath, false);
        }

        Set<Node> blocking;

        if (blockingType == BlockingType.RECURSIVE) {
            blocking = SepsetFinder.getPathBlockingSetRecursive(graph, x, y, new HashSet<>(path), maxLength, Set.of());
        } else if (blockingType == BlockingType.GREEDY) {
            blocking = SepsetFinder.getSepsetContainingGreedy(graph, x, y, new HashSet<>(path), test, depth);
        } else {
            throw new IllegalArgumentException("Unknown blocking type.");
        }

        //  *         V
        // *         **            * is either an arrowhead, a tail, or a circle
        // *        /  \
        // *       v    *
        // * X....W --> Y


        // This is needed for greedy and anteriority methods, which return sepsets, not recursive, which always
        // returns a blocking set.
        if (blockingType == BlockingType.GREEDY && blocking == null) {
            throw new IllegalArgumentException("Sepset is null.");
        }

        if (blockingType == BlockingType.RECURSIVE && !(blocking.containsAll(path) && blocking.contains(w))) {
            throw new IllegalArgumentException("Blocking set is not correct; it should contain the path (including W) and V.");
        }

        if (blockingType == BlockingType.GREEDY && !blocking.containsAll(path)) {
            throw new IllegalArgumentException("Blocking set is not correct; it should contain the path.");
        }

        // Now at this point, for the recursive case, we simply need to know whether X _||_ Y | blocking. If so, we
        // can orient W<-*V*->Y as a non-collider, otherwise as a collider. For the greedy case, we need to know whether
        // blocking contains v. These are two ways to express the same idea, since for the recursive case blocking
        // must contain V by construction.
        if ((blockingType == BlockingType.RECURSIVE && checkIndependenceRecursive(x, y, blocking, vNodes, discriminatingPath, test)) || (blockingType == BlockingType.GREEDY && blocking.contains(v))) {
            if (graph.getEndpoint(y, v) != Endpoint.CIRCLE) {
                return Pair.of(discriminatingPath, false);
            }

            graph.setEndpoint(y, v, Endpoint.TAIL);

            if (verbose) {
                TetradLogger.getInstance().log("R4: Discriminating path ORIENTED: " + discriminatingPath);
                TetradLogger.getInstance().log("    Oriented as: " + GraphUtils.pathString(graph, w, v, y));
                TetradLogger.getInstance().log("    Collider path = " + path);
                TetradLogger.getInstance().log("    Blocking set for " + x + " and " + y + " is " + blocking);
            }

            return Pair.of(discriminatingPath, true);
        } else {
            if (graph.getEndpoint(y, v) != Endpoint.CIRCLE) {
                return Pair.of(discriminatingPath, false);
            }

            if (!FciOrient.isArrowheadAllowed(w, v, graph, knowledge)) {
                return Pair.of(discriminatingPath, false);
            }

            if (!FciOrient.isArrowheadAllowed(y, v, graph, knowledge)) {
                return Pair.of(discriminatingPath, false);
            }

            if (initialAllowedColliders != null) {
                initialAllowedColliders.add(new Triple(w, v, y));
            } else {
                if (allowedColliders != null && !allowedColliders.contains(new Triple(w, v, y))) {
                    return Pair.of(discriminatingPath, false);
                }
            }

            graph.setEndpoint(w, v, Endpoint.ARROW);
            graph.setEndpoint(y, v, Endpoint.ARROW);

            if (verbose) {
                TetradLogger.getInstance().log("R4: Discriminating path ORIENTED: " + discriminatingPath);
                TetradLogger.getInstance().log("    Oriented as: " + GraphUtils.pathString(graph, w, v, y));
                TetradLogger.getInstance().log("    Collider path = " + path);
                TetradLogger.getInstance().log("    Blocking set for " + x + " and " + y + " is " + blocking);
            }

            return Pair.of(discriminatingPath, true);
        }
    }

    private boolean checkIndependenceRecursive(Node x, Node y, Set<Node> blocking, Set<Node> vNodes, DiscriminatingPath discriminatingPath, IndependenceTest test) throws InterruptedException {

        List<Node> vs = new ArrayList<>();
        List<Node> nonVs = new ArrayList<>();

        for (Node v : blocking) {
            if (vNodes.contains(v)) {
                vs.add(v);
            } else {
                nonVs.add(v);
            }
        }

        Node v = discriminatingPath.getV();
        vs.remove(v);

        SublistGenerator generator = new SublistGenerator(vs.size(), vs.size());
        int[] choice;

        while ((choice = generator.next()) != null) {
            Set<Node> newBlocking = GraphUtils.asSet(choice, vs);
            newBlocking.add(v);
            newBlocking.addAll(nonVs);

            // You didn't condition on any colliders. V is in the set. So V is a noncollider.
            boolean independent = ensureMarkovHelper != null ? ensureMarkovHelper.markovIndependence(x, y, newBlocking) : test.checkIndependence(x, y, newBlocking).isIndependent();

            if (independent) {
                return true;
            }
        }

        return false;
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

    /**
     * The Set of Triples representing the allowed colliders for the FciOrientDataExaminationStrategy. This variable is
     * initially set to null. Use the setAllowedColliders method to set the allowed colliders. Use the
     * getInitialAllowedColliders method to retrieve the initial set of allowed colliders.
     *
     * @return The Set of Triples representing the allowed colliders for the FciOrientDataExaminationStrategy.
     */
    public Set<Triple> getAllowedColliders() {
        return allowedColliders;
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
     * Sets the PAG (partial ancestral graph) for the strategy.
     *
     * @param pag the PAG to be set
     */
    public void setPag(Graph pag) {
        this.pag = pag;
    }

    /**
     * Sets the EnsureMarkov object used by the R0R4StrategyTestBased.
     *
     * @param ensureMarkovHelper the EnsureMarkov object to be set
     */
    public void setEnsureMarkovHelper(EnsureMarkov ensureMarkovHelper) {
        this.ensureMarkovHelper = ensureMarkovHelper;
    }

    /**
     * Sets the blocking type for the strategy.
     *
     * @param blockingType the blocking type to be set, which can be either RECURSIVE or GREEDY.
     */
    public void setBlockingType(BlockingType blockingType) {
        this.blockingType = blockingType;
    }

    /**
     * Enum representing the different types of blocking strategies.
     * <p>
     * The available blocking strategies are:
     * <p>
     * RECURSIVE - This strategy involves a recursive approach to blocking. GREEDY - This strategy involves a greedy
     * approach to blocking.
     */
    public enum BlockingType {
        /**
         * Recursive blocking. This calculates the blocking set B recursively that must include V and then checks the
         * independence of X and Y given B.
         */
        RECURSIVE,
        /**
         * Greedy blocking. This searches greedily, in the distribution, for a sepset B of X and Y and then looks to see
         * if V is in B.
         */
        GREEDY,
    }
}

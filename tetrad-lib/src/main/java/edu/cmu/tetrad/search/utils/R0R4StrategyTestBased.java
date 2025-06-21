package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.RecursiveDiscriminatingPathRule;
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
     * The type of blocking strategy used in the R0R4StrategyTestBased class. This variable determines whether the
     * strategy will be recursive or greedy.
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
    /**
     * The maximum length of the path, for relevant paths.
     */
    private int maxLength = -1;
    /**
     * Helper variable of type PreserveMarkov used for preserving Markov properties in the R0R4StrategyTestBased class.
     * Initialized to null by default.
     */
    private PreserveMarkov preserveMarkovHelper = null;
    /**
     * A private instance of the SepsetMap used to manage and store separating sets within the
     * FciOrientDataExaminationStrategy. The separating sets are used to capture conditional independencies in a graph.
     * This map preserves that proper independence relationships are maintained during the execution of the strategy.
     */
    private SepsetMap sepsetMap = new SepsetMap();

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
            R0R4Strategy r0R4Strategy = defaultConfiguration(((MsepTest) test).getGraph(), knowledge);
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
        Set<Node> sepset = SepsetFinder.findSepsetSubsetOfAdjxOrAdjy(graph, i, k, new HashSet<>(), test, depth);
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

        Set<Node> blocking = null;

        // If you already have a sepset, use it.
        if (sepsetMap.get(x, y) != null) {
            blocking = sepsetMap.get(x, y);
        }

        if (blocking == null && blockingType == BlockingType.RECURSIVE) {
            blocking = RecursiveDiscriminatingPathRule.findDdpSepsetRecursive(test, graph, x, y, new FciOrient(new R0R4StrategyTestBased(test)),
                    maxLength, maxLength, preserveMarkovHelper, depth);
        }

        if (blocking == null) {
            blocking = SepsetFinder.findSepsetSubsetOfAdjxOrAdjy(graph, x, y, new HashSet<>(path), test, depth);
        }

        if (blocking != null) {
            sepsetMap.set(x, y, blocking);
        } else {
            blocking = Set.of();
//            TetradLogger.getInstance().log("Blocking set is null in R4.");
//            throw new IllegalArgumentException("Blocking set is null in R4.");
        }

        if (!(blocking.containsAll(path) && blocking.contains(w))) {
            throw new IllegalArgumentException("Blocking set is not correct; it should contain the path (including W) and V.");
        }

        // Now at this point, for the recursive case, we simply need to know whether X _||_ Y | blocking. If so, we
        // can orient W<-*V*->Y as a non-collider, otherwise as a collider. For the greedy case, we need to know whether
        // blocking contains v. These are two ways to express the same idea, since for the recursive case blocking
        // must contain V by construction.
        boolean noncollider = blocking.contains(v);

        if (noncollider) {
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
     * Sets the PreserveMarkov object used by the R0R4StrategyTestBased.
     *
     * @param preserveMarkovHelper the PreserveMarkov object to be set
     */
    public void setPreserveMarkovHelper(PreserveMarkov preserveMarkovHelper) {
        this.preserveMarkovHelper = preserveMarkovHelper;
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
     * Sets the SepsetMap used by the R0R4StrategyTestBased.
     *
     * @param sepsetMap the SepsetMap object to be set
     */
    public void setSepsetMap(SepsetMap sepsetMap) {
        this.sepsetMap = sepsetMap;
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

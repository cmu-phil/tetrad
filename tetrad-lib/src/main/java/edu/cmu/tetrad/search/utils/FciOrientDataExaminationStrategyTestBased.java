package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SepsetFinder;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.TetradLogger;

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
 * @see FciOrientDataExaminationStrategy
 */
public class FciOrientDataExaminationStrategyTestBased implements FciOrientDataExaminationStrategy {

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
     * @see FciOrientDataExaminationStrategyTestBased
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
     * Determines whether the Discriminating Path Collider Rule should be applied or not.
     */
    private boolean doDiscriminatingPathColliderRule = true;
    /**
     * Determines whether the Discriminating Path Tail Rule is enabled or not.
     */
    private boolean doDiscriminatingPathTailRule = true;

    /**
     * Creates a new instance of FciOrientDataExaminationStrategyTestBased.
     *
     * @param test the IndependenceTest object used by the strategy
     */
    public FciOrientDataExaminationStrategyTestBased(IndependenceTest test) {
        this.test = test;
    }

    /**
     * Provides a special configuration for creating an instance of FciOrientDataExaminationStrategy.
     *
     * @param test                             the IndependenceTest object used by the strategy
     * @param knowledge                        the Knowledge object used by the strategy
     * @param doDiscriminatingPathTailRule     boolean indicating whether to use the Discriminating Path Tail Rule
     * @param doDiscriminatingPathColliderRule boolean indicating whether to use the Discriminating Path Collider Rule
     * @param verbose                          boolean indicating whether to provide verbose output
     * @return a configured FciOrientDataExaminationStrategy object
     * @throws IllegalArgumentException if test or knowledge is null
     */
    public static FciOrientDataExaminationStrategy specialConfiguration(IndependenceTest test, Knowledge knowledge,
                                                                        boolean doDiscriminatingPathTailRule,
                                                                        boolean doDiscriminatingPathColliderRule,
                                                                        boolean verbose) {
        if (test == null) {
            throw new IllegalArgumentException("Test is null.");
        }

        if (knowledge == null) {
            throw new IllegalArgumentException("Knowledge is null.");
        }

        if (test instanceof MsepTest) {
            return FciOrientDataExaminationStrategyTestBased.defaultConfiguration(((MsepTest) test).getGraph(), knowledge, verbose);
        } else {
            FciOrientDataExaminationStrategyTestBased strategy = new FciOrientDataExaminationStrategyTestBased(test);
            strategy.setKnowledge(knowledge);
            strategy.setDoDiscriminatingPathTailRule(doDiscriminatingPathTailRule);
            strategy.setDoDiscriminatingPathColliderRule(doDiscriminatingPathColliderRule);
            strategy.verbose = verbose;
            return strategy;
        }
    }

    /**
     * Returns a default configuration of the FciOrientDataExaminationStrategy object.
     *
     * @param dag       the graph representation
     * @param knowledge the Knowledge object used by the strategy
     * @param verbose   boolean indicating whether to provide verbose output
     * @return a default configured FciOrientDataExaminationStrategy object
     */
    public static FciOrientDataExaminationStrategy defaultConfiguration(Graph dag, Knowledge knowledge, boolean verbose) {
        return defaultConfiguration(new MsepTest(dag), knowledge, verbose);
    }

    /**
     * Returns a default configuration of the FciOrientDataExaminationStrategy object.
     *
     * @param test      the IndependenceTest object used by the strategy
     * @param knowledge the Knowledge object used by the strategy
     * @param verbose   boolean indicating whether to provide verbose output
     * @return a configured FciOrientDataExaminationStrategy object
     * @throws IllegalArgumentException if test or knowledge is null
     */
    public static FciOrientDataExaminationStrategy defaultConfiguration(IndependenceTest test, Knowledge knowledge, boolean verbose) {
        FciOrientDataExaminationStrategyTestBased strategy = new FciOrientDataExaminationStrategyTestBased(test);
        strategy.setDoDiscriminatingPathTailRule(true);
        strategy.setDoDiscriminatingPathColliderRule(true);
        strategy.setVerbose(verbose);
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
     * Determines the orientation for the nodes in a Directed Acyclic Graph (DAG) based on the Discriminating Path Rule
     * Here, we insist that the sepset for D and B contain all the nodes along the collider path.
     * <p>
     * Reminder:
     * <pre>
     *      The triangles that must be oriented this way (won't be done by another rule) all look like the ones below, where
     *      the dots are a collider path from E to A with each node on the path (except E) a parent of C.

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

        doubleCheckDiscriminatingPathConstruct(e, a, b, c, path, graph);

        for (Node n : path) {
            if (!graph.isParentOf(n, c)) {
                throw new IllegalArgumentException("Node " + n + " is not a parent of " + c);
            }
        }

//        System.out.println("Looking for sepset for " + e + " and " + c + " with path " + path);

        Set<Node> blacklist = new HashSet<>();
        Set<Node> sepset = SepsetFinder.getSepsetPathBlockingOutOfX(graph, e, c, test, -1, -1, true, blacklist, -1);

//        System.out.println("...sepset for " + e + " *-* " + c + " = " + sepset);

        if (sepset == null) {
            return false;
        }

        if (this.verbose) {
            TetradLogger.getInstance().log("Sepset for e = " + e + " and c = " + c + " = " + sepset);
        }

        boolean collider = !sepset.contains(b);

        if (collider) {
            if (doDiscriminatingPathColliderRule) {
                graph.setEndpoint(a, b, Endpoint.ARROW);
                graph.setEndpoint(c, b, Endpoint.ARROW);

                if (this.verbose) {
                    TetradLogger.getInstance().log(
                            "R4: Definite discriminating path collider rule e = " + e + " " + GraphUtils.pathString(graph, a, b, c));
                }

                return true;
            }
        } else {
            if (doDiscriminatingPathTailRule) {
                graph.setEndpoint(c, b, Endpoint.TAIL);

                if (this.verbose) {
                    TetradLogger.getInstance().log(
                            "R4: Definite discriminating path tail rule e = " + e + " " + GraphUtils.pathString(graph, a, b, c));
                }

                return true;
            }
        }

        if (graph.isAdjacentTo(e, c)) {
            throw new IllegalArgumentException("e is adjacent to c");
        }

        if (!sepset.contains(b) && doDiscriminatingPathColliderRule) {
            if (!FciOrient.isArrowheadAllowed(a, b, graph, knowledge)) {
                return false;
            }

            if (!FciOrient.isArrowheadAllowed(c, b, graph, knowledge)) {
                return false;
            }

            graph.setEndpoint(a, b, Endpoint.ARROW);
            graph.setEndpoint(c, b, Endpoint.ARROW);

            if (this.verbose) {
                TetradLogger.getInstance().log(
                        "R4: Definite discriminating path collider rule d = " + e + " " + GraphUtils.pathString(graph, a, b, c));
            }

        } else if (doDiscriminatingPathTailRule) {
            graph.setEndpoint(c, b, Endpoint.TAIL);

            if (this.verbose) {
                TetradLogger.getInstance().log(LogUtilsSearch.edgeOrientedMsg(
                        "R4: Definite discriminating path tail rule d = " + e, graph.getEdge(b, c)));
            }

            return true;
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
     * Determines whether the Discriminating Path Collider Rule is enabled or not.
     *
     * @return true if the Discriminating Path Collider Rule is enabled, false otherwise
     */
    public boolean isDoDiscriminatingPathColliderRule() {
        return doDiscriminatingPathColliderRule;
    }

    /**
     * Sets the value indicating whether to use the Discriminating Path Collider Rule.
     *
     * @param doDiscriminatingPathColliderRule boolean value indicating whether to use the Discriminating Path Collider
     *                                         Rule
     */
    public void setDoDiscriminatingPathColliderRule(boolean doDiscriminatingPathColliderRule) {
        this.doDiscriminatingPathColliderRule = doDiscriminatingPathColliderRule;
    }

    /**
     * Returns the value indicating whether the Discriminating Path Tail Rule is enabled or not.
     *
     * @return true if the Discriminating Path Tail Rule is enabled, false otherwise
     */
    public boolean isDoDiscriminatingPathTailRule() {
        return doDiscriminatingPathTailRule;
    }

    /**
     * Sets the value indicating whether to use the Discriminating Path Tail Rule.
     *
     * @param doDiscriminatingPathTailRule boolean value indicating whether to use the Discriminating Path Tail Rule
     */
    public void setDoDiscriminatingPathTailRule(boolean doDiscriminatingPathTailRule) {
        this.doDiscriminatingPathTailRule = doDiscriminatingPathTailRule;
    }
}

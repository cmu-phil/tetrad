/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.RecursiveDiscriminatingPathRule;
import edu.cmu.tetrad.search.SepsetFinder;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
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
    private long timeout = -1;
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
     * Whether R4's recursive discriminating-path search returns the candidate separating set with the strongest
     * evidence of independence rather than the first one it finds. Off by default, which preserves the historical
     * (order-dependent) behavior exactly. See
     * {@link edu.cmu.tetrad.search.RecursiveDiscriminatingPathRule#findDdpSepsetRecursive(IndependenceTest, Graph,
     * Node, Node, int, int, int, PreserveMarkov, IndependenceCheckCounter, long, boolean)}.
     */
    private boolean ddpMaxP = false;
    /**
     * A private instance of the SepsetMap used to manage and store separating sets within the
     * FciOrientDataExaminationStrategy. The separating sets are used to capture conditional independencies in a graph.
     * This map preserves that proper independence relationships are maintained during the execution of the strategy.
     */
    private SepsetMap sepsets = new SepsetMap();

    /**
     * Frozen reference graph (the initial Markov PAG, G_0) against which an
     * unrecorded discriminating-path separator is computed, enforcing P1's
     * "recorded, not live" discipline. When null, R4 falls back to computing on
     * the live graph (the prior behavior).
     */
    private Graph sepsetGraph = null;

    /**
     * Creates a new instance of FciOrientDataExaminationStrategyTestBased.
     *
     * @param test the IndependenceTest object used by the strategy
     */
    public R0R4StrategyTestBased(IndependenceTest test) {
        this.test = test;
    }

    /**
     * Constructs an instance of R0R4StrategyTestBased with the specified
     * IndependenceTest and timeout.
     *
     * @param test   the IndependenceTest object used by the strategy
     * @param timeout the timeout value in milliseconds for the strategy
     */
    public R0R4StrategyTestBased(IndependenceTest test, long timeout) {
        this.test = test;
        this.timeout = timeout;
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
     * @throws IllegalStateException    if a blocking set cannot be found.
     * @see DiscriminatingPath
     */
    @Override
    public Pair<DiscriminatingPath, Boolean> doDiscriminatingPathOrientation(DiscriminatingPath discriminatingPath,
                                                                             int recursiveDepth, int maxDiscriminatingPathLength,
                                                                             Graph graph, Set<Node> vNodes) throws InterruptedException {
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

        Set<Node> blocking = sepsets.get(x, y);

        // GREEDY fast path: look for a separator of X and Y among subsets of adj(X) or adj(Y)
        // that contain the collider path. This is sound but incomplete -- in the presence of
        // latent confounding a separator of a non-adjacent pair need not be a subset of the
        // adjacents of either endpoint -- so when it fails we fall through to the (complete but
        // more expensive) recursive search below.
        if (blocking == null && blockingType == BlockingType.GREEDY) {
            blocking = findAdjSetSepset(graph, x, y, path, v);

            if (blocking != null) {
                sepsets.set(x, y, blocking);
            }
        }

        // BlockingType.RECURSIVE, and the GREEDY fallback.
        if (blocking == null) {
            // P1 ("recorded, not live"): compute an unrecorded endpoint separator
            // against the frozen initial Markov PAG (G_0) rather than the live,
            // mid-reorientation graph. This branch is reached only for pairs
            // non-adjacent in G_0 -- deleted pairs always carry a recorded sepset --
            // so sepsetGraph has x,y non-adjacent and the call is well-posed. A null
            // sepsetGraph falls back to the live graph (prior behavior).
            Graph refGraph = (sepsetGraph != null && !sepsetGraph.isAdjacentTo(x, y))
                    ? sepsetGraph : graph;

            blocking = RecursiveDiscriminatingPathRule.findDdpSepsetRecursive(test, refGraph, x, y,
                    recursiveDepth, maxDiscriminatingPathLength, depth, preserveMarkovHelper, null, timeout,
                    ddpMaxP);

            if (blocking != null) {
                sepsets.set(x, y, blocking);
            } else {
                // No separator could be found for X and Y. R4 has no basis on which to decide
                // whether V is a collider, so decline to orient rather than aborting the whole
                // orientation pass. (Under an oracle this cannot happen for a non-adjacent pair;
                // with a fallible test it can, and it is not an error.)
                if (verbose) {
                    TetradLogger.getInstance().log("R4: No separator found for " + x + " and " + y
                                                   + "; declining to orient " + discriminatingPath);
                }

                return Pair.of(discriminatingPath, false);
            }
        }

        // Every vertex strictly between X and V on a discriminating path is a parent of Y, so
        // X *-* A -> Y is an open path unless A is conditioned on. Any separator of X and Y must
        // therefore contain the whole collider path (which includes W, by DiscriminatingPath's
        // storage convention). With a fallible test this can fail; when it does, the separator is
        // not a valid basis for R4, so decline rather than orienting from it.
        if (!blocking.containsAll(path)) {
            if (verbose) {
                TetradLogger.getInstance().log("R4: Separator " + blocking + " for " + x + " and " + y
                                               + " does not contain the collider path " + path
                                               + "; declining to orient " + discriminatingPath);
            }

            return Pair.of(discriminatingPath, false);
        }

        // R4 proper: V is a non-collider on the discriminating path exactly when V belongs to a
        // separator of X and Y, and a collider otherwise. This is the criterion for both blocking
        // types -- the types differ only in how the separator is found, not in how it is read.
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
     * Greedily searches for a separator of x and y among subsets of adj(x) or adj(y) that contain the given collider
     * path, and then normalizes it so that its membership of v is decisive for R4. Returns null if no such separator
     * exists, in which case the caller falls back to the recursive search.
     *
     * @param graph the graph to search in
     * @param x     the first endpoint of the discriminating path
     * @param y     the second endpoint of the discriminating path
     * @param path  the collider path, which the separator must contain
     * @param v     the node whose collider status is at issue
     * @return a separator of x and y, or null if none was found
     * @throws InterruptedException if the operation is interrupted
     */
    private @Nullable Set<Node> findAdjSetSepset(Graph graph, Node x, Node y, List<Node> path, Node v) throws InterruptedException {
        Set<Node> sepset = SepsetFinder.findSepsetSubsetOfAdjxOrAdjy(graph, x, y, new HashSet<>(path), test, depth);

        // No adjacency-restricted separator exists; let the caller fall back.
        if (sepset == null) {
            return null;
        }

        // Probe without v first, then with v. Exactly one of these separates under an oracle, and
        // testing in this order makes v's membership of the returned set decisive.
        Set<Node> b1 = new HashSet<>(sepset);
        b1.remove(v);

        if (test.checkIndependence(x, y, b1).isIndependent()) {
            return b1;
        }

        Set<Node> b2 = new HashSet<>(b1);
        b2.add(v);

        if (test.checkIndependence(x, y, b2).isIndependent()) {
            return b2;
        }

        return null;
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
     * Sets whether R4's recursive discriminating-path search picks the strongest candidate separating set
     * (max-p) rather than the first one found. Off by default.
     *
     * @param ddpMaxP whether to use the max-p rule
     */
    public void setDdpMaxP(boolean ddpMaxP) {
        this.ddpMaxP = ddpMaxP;
    }

    /**
     * Sets the PreserveMarkov helper.
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
     * @param sepsets the SepsetMap object to be set
     */
    public void setSepsetMap(SepsetMap sepsets) {
        this.sepsets = sepsets;
    }

    /**
     * Sets the frozen reference graph (G_0) used to compute separators for
     * endpoint pairs that have no recorded separator. Pass the initial PAG; pass
     * null to recompute on the live graph (prior behavior).
     *
     * @param sepsetGraph the frozen initial PAG, or null
     */
    public void setSepsetGraph(Graph sepsetGraph) {
        this.sepsetGraph = sepsetGraph;
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


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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static edu.cmu.tetrad.graph.GraphUtils.colliderAllowed;
import static edu.cmu.tetrad.graph.GraphUtils.fciOrientbk;
import static java.util.Collections.shuffle;

/**
 * *-FCI implements a template modification of GFCI that starts with a given Markov CPDAG and then fixes that result to
 * be correct for latent variables models. First, colliders from the Markov DAG are copied into the final circle-circle
 * graph, and some independence reasoning is used to remove edges from this and add the remaining colliders into the
 * graph. Then, the FCI final orientation rules are applied.
 * <p>
 * The Markov CPDAG needs to be supplied by classes inheriting from this abstract class using the getMarkovCpdag()
 * methods.
 * <p>
 * The reference for the GFCI algorithm this is being modeled from is here:
 * <p>
 * Ogarrio, J. M., Spirtes, P., &amp; Ramsey, J. (2016, August). A hybrid causal search algorithm for latent variable
 * models. In Conference on probabilistic graphical models (pp. 368-379). PMLR.
 * <p>
 * We modify this by insistent that getMarkovCpdag() is overridden by a method that will return a CPDAG Markov to the
 * data or underlying generative model and removing the possible d-sep step of the original algorithm.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author josephramsey
 * @author bryanandrews
 * @see #getMarkovCpdag()
 * @see Knowledge
 */
public abstract class StarFci implements IGraphSearch {
    /**
     * The independence test used in search.
     */
    private final IndependenceTest independenceTest;
    /**
     * The knowledge used in search.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * Whether Zhang's complete rules are used.
     */
    private boolean completeRuleSetUsed = true;
    /**
     * The maximum path length for the discriminating path rule.
     */
    private int maxDiscriminatingPathLength = -1;
    /**
     * The depth for independence testing.
     */
    private int depth = -1;
    /**
     * Whether verbose output should be printed.
     */
    private boolean verbose = false;
    /**
     * Whether to guarantee the output is a PAG by repairing a faulty PAG.
     */
    private boolean guaranteePag = false;
    /**
     * A boolean flag indicating whether to use the maximum p-value heuristic during certain operations in the Star-FCI
     * algorithm. The default value is {@code true}, enabling the heuristic by default.
     */
    private boolean useMaxP = false;
    private boolean replicatingGraph = false;

    /**
     * Constructs a new GFci algorithm with the given independence test and score.
     *
     * @param test The independence test to use.
     */
    public StarFci(IndependenceTest test) {
        this.independenceTest = test;
    }

    /**
     * Finds a separating set that is a subset of the adjacency of nodes x or y in the input graph.
     *
     * @param graph      The graph being analyzed.
     * @param x          The first node between which independence is checked.
     * @param y          The second node between which independence is checked.
     * @param containing A set of nodes that must be included in the separating set.
     * @param test       The independence test used to evaluate separation.
     * @param depth      The maximum size of subsets to be tested for independence.
     * @param order      An optional list specifying the order of nodes for additional constraints.
     * @param useMaxP    True if the maxP method should be used.
     * @return A separating set of nodes (if found) that is a subset of the adjacency of x or y, or {@code null} if no
     * such set is found.
     */
    public static Set<Node> sepsetSubsetOfAdjxOrAdjy(Graph graph, Node x, Node y, Set<Node> containing,
                                                     IndependenceTest test, int depth, List<Node> order, boolean useMaxP) {

        // We need to look at the original adjx and adjy, not some modified version.
        List<Node> adjx = graph.getAdjacentNodes(x);
        List<Node> adjy = graph.getAdjacentNodes(y);
        adjx.remove(y);
        adjy.remove(x);

        adjx.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        adjy.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        Set<Node> sepset1 = getSepset(x, y, containing, test, depth, order, adjx, useMaxP);
        Set<Node> sepset2 = getSepset(y, x, containing, test, depth, order, adjy, useMaxP);

        if (sepset1 == null && sepset2 == null) {
            return null;
        }

        if (sepset1 != null && sepset2 == null) {
            return sepset1;
        }

        if (sepset1 == null) {
            return sepset2;
        }

        try {
            double p1 = test.checkIndependence(x, y, sepset1).getPValue();
            double p2 = test.checkIndependence(x, y, sepset2).getPValue();

            return p1 > p2 ? sepset1 : sepset2;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Finds a separating set between nodes x and y that satisfies certain conditions, including containing a specified
     * set of nodes, maintaining optional ordering constraints, and ensuring the independence between x and y with
     * respect to the separating set.
     * <p>
     * The separating set is constructed from the adjacency list of node x.
     *
     * @param x          The first node between which independence is being checked.
     * @param y          The second node between which independence is being checked.
     * @param containing A set of nodes that must be included in the separating set.
     * @param test       The independence test used to evaluate separation between x and y.
     * @param depth      The maximum size of subsets to be tested for independence.
     * @param order      An optional list specifying the processing order of nodes, used to enforce additional
     *                   constraints during the search.
     * @param adjx       The adjacency list of node x, from which subsets are generated to test for separation.
     * @return A separating set of nodes that fulfills all constraints and is a subset of adjx, or {@code null} if no
     * such set is found.
     */
    private static @Nullable Set<Node> getSepset(Node x, Node y, Set<Node> containing, IndependenceTest test, int depth,
                                                 List<Node> order, List<Node> adjx, boolean useMaxP) {
        List<Set<Node>> choices = getChoices(adjx, depth);

        if (useMaxP) {
            // Max p for stability...
            return choices.parallelStream()
                    .max(Comparator.comparingDouble(set -> computeScore(x, y, set, test))) // Find max
                    .filter(set -> computeScore(x, y, set, test) > test.getAlpha()) // Filter by threshold
                    .orElse(null); // Return best set or null if none pass the threshold
        } else { // Greedy

            // Parallelize processing for adjx
            // Generate combinations in parallel
            // Filter combinations that don't contain 'containing'
            return choices.parallelStream() // Generate combinations in parallel
                    .filter(subset -> subset.containsAll(containing)) // Filter combinations that don't contain 'containing'
                    .filter(subset -> {
                        try {
                            return test.checkIndependence(x, y, subset).isIndependent();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }).findFirst().orElse(null);
        }
    }

    private static double computeScore(Node x, Node y, Set<Node> set, IndependenceTest test) {
        try {
            return test.checkIndependence(x, y, set).getPValue();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Checks if three nodes in a graph form an unshielded triple. An unshielded triple is a configuration where node a
     * is adjacent to node b, node b is adjacent to node c, but node a is not adjacent to node c.
     *
     * @param graph The graph in which the nodes reside.
     * @param a     The first node in the triple.
     * @param b     The second node in the triple.
     * @param c     The third node in the triple.
     * @return {@code true} if the nodes form an unshielded triple, {@code false} otherwise.
     */
    private static boolean unshieldedTriple(Graph graph, Node a, Node b, Node c) {
        return graph.isAdjacentTo(a, b) && graph.isAdjacentTo(b, c) && !graph.isAdjacentTo(a, c);
    }

    /**
     * Checks if the given nodes are unshielded colliders when considering the given graph.
     *
     * @param graph the graph to consider
     * @param a     the first node
     * @param b     the second node
     * @param c     the third node
     * @return true if the nodes are unshielded colliders, false otherwise
     */
    private static boolean unshieldedCollider(Graph graph, Node a, Node b, Node c) {
        return a != c && unshieldedTriple(graph, a, b, c) && graph.isDefCollider(a, b, c);
    }

    /**
     * Generates a list of all possible choices for sublists from the adjacency list with sizes up to the given depth
     * using combinations.
     *
     * @param adjx  The adjacency list of nodes to generate combinations from.
     * @param depth The maximum size of the sublists to be generated. If the depth is negative or exceeds the size of
     *              the adjacency list, it will be adjusted to the size of the adjacency list.
     * @return A list of all possible lists of integers representing combinations of indices from the adjacency list up
     * to the given depth.
     */
    private static @NotNull List<Set<Node>> getChoices(List<Node> adjx, int depth) {
        List<Set<Node>> choices = new ArrayList<>();

        if (depth < 0 || depth > adjx.size()) depth = adjx.size();

        SublistGenerator cg = new SublistGenerator(adjx.size(), depth);
        int[] choice;

        while ((choice = cg.next()) != null) {
            choices.add(GraphUtils.asSet(choice, adjx));
        }

        return choices;
    }

    /**
     * Sets whether to use the maxP criterion during the search process.
     *
     * @param useMaxP A boolean indicating whether the maxP criterion should be applied (true) or not (false).
     */
    public void setUseMaxP(boolean useMaxP) {
        this.useMaxP = useMaxP;
    }

    /**
     * Runs the graph and returns the search PAG.
     *
     * @return This PAG.
     * @throws InterruptedException if any
     */
    public Graph search() throws InterruptedException {
        this.independenceTest.setVerbose(verbose);
        List<Node> nodes = new ArrayList<>(getIndependenceTest().getVariables());

        Graph cpdag = getMarkovCpdag();
//        Graph pag = new EdgeListGraph(cpdag);
        Graph pag = wrapWorkingGraph(cpdag);
        Set<Triple> unshieldedColliders = new HashSet<>();
        SepsetMap sepsetMap = new SepsetMap();

        if (verbose) {
            TetradLogger.getInstance().log("Starting *-FCI extra edge removal step.");
        }

        List<Edge> edges = new ArrayList<>(pag.getEdges());
        shuffle(edges);

        for (Edge edge : edges) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node a = edge.getNode1();
            Node c = edge.getNode2();

            Set<Node> sepset = sepsetSubsetOfAdjxOrAdjy(pag, a, c, new HashSet<>(), independenceTest, depth, null, useMaxP);

            if (sepset != null) {
                pag.removeEdge(a, c);
                sepsetMap.set(a, c, sepset);

                List<Node> adj = pag.getAdjacentNodes(a);
                adj.retainAll(pag.getAdjacentNodes(c));

                if (verbose) {
                    IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
                    double pValue = result.getPValue();
                    TetradLogger.getInstance().log("Removed edge " + a + " -- " + c + " in extra-edge removal step; sepset = "
                                                   + sepset + ", p-value = " + pValue + ".");
                }
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("Starting *-FCI-R0.");
        }

        pag.reorientAllWith(Endpoint.CIRCLE);
        fciOrientbk(knowledge, pag, pag.getNodes());

        for (Node y : nodes) {
            List<Node> adjacentNodes = new ArrayList<>(pag.getAdjacentNodes(y));

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (cpdag.isDefCollider(x, y, z)) {
                    if (colliderAllowed(pag, x, y, z, knowledge)) {
                        pag.setEndpoint(x, y, Endpoint.ARROW);
                        pag.setEndpoint(z, y, Endpoint.ARROW);
                        unshieldedColliders.add(new Triple(x, y, z));

                        if (verbose) {
                            TetradLogger.getInstance().log("Copied collider " + x + " â " + y + " â " + z + " from CPDAG.");
                        }
                    }
                } else if (cpdag.isAdjacentTo(x, z)) {
                    Set<Node> sepset = sepsetMap.get(x, z);

                    if (sepset != null && !sepset.contains(y)) {
                        if (colliderAllowed(pag, x, y, z, knowledge)) {
                            pag.setEndpoint(x, y, Endpoint.ARROW);
                            pag.setEndpoint(z, y, Endpoint.ARROW);

                            if (!pag.isAdjacentTo(x, z)) {
                                unshieldedColliders.add(new Triple(x, y, z));
                            }

                            if (verbose) {
                                TetradLogger.getInstance().log("Oriented collider by separating set: " + x + " â " + y + " â " + z);
                            }
                        }
                    }
                }
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("Starting final FCI orientation.");
        }

        R0R4StrategyTestBased strategy = (R0R4StrategyTestBased) R0R4StrategyTestBased.specialConfiguration(independenceTest, knowledge, verbose);
        strategy.setDepth(-1);
        strategy.setMaxLength(-1);
        FciOrient fciOrient = new FciOrient(strategy);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxDiscriminatingPathLength(maxDiscriminatingPathLength);
        fciOrient.setVerbose(verbose);

        fciOrient.finalOrientation(pag);

        if (verbose) {
            TetradLogger.getInstance().log("Finished implied orientation.");
        }

        if (guaranteePag) {
            pag = GraphUtils.guaranteePag(pag, fciOrient, knowledge, unshieldedColliders, verbose, new HashSet<>());

//            pag = new DagToPag(pag).convert();
        }

        if (verbose) {
            TetradLogger.getInstance().log("*-FCI finished.");
        }

        return pag;
    }

    private Graph wrapWorkingGraph(Graph cpdag) {
        if (replicatingGraph) {
            return new ReplicatingGraph(cpdag, new LagReplicationPolicy());
        } else {
            return new EdgeListGraph(cpdag);
        }
    }

    /**
     * Returns the knowledge used in search.
     *
     * @return This knowledge
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge to use in search.
     *
     * @param knowledge This knowledge.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * Sets whether Zhang's complete rules are used.
     *
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. True by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Sets the maximum length of any discriminating path.
     *
     * @param maxDiscriminatingPathLength the maximum length of any discriminating path, or -1 if unlimited.
     */
    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        if (maxDiscriminatingPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxDiscriminatingPathLength);
        }

        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
    }

    /**
     * Indicates whether verbose output is enabled.
     *
     * @return true if verbose output is enabled, false otherwise.
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the independence test used in search.
     *
     * @return This test.
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * Sets the depth of the search for the possible m-sep search.
     *
     * @param depth This depth.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets the flag indicating whether to guarantee the output is a legal PAG.
     *
     * @param guaranteePag A boolean value indicating whether to guarantee the output is a legal PAG.
     */
    public void setGuaranteePag(boolean guaranteePag) {
        this.guaranteePag = guaranteePag;
    }

    /**
     * Returns a Markov CPDAG to use as the initial graph in the Star-FCI search.
     *
     * @return This CPDAG.
     * @throws InterruptedException if interrupted.
     */
    public abstract Graph getMarkovCpdag() throws InterruptedException;

    /**
     * Sets the flag indicating whether the graph is being replicated.
     *
     * @param replicatingGraph A boolean value where {@code true} indicates that
     *                         the graph is being replicated, and {@code false}
     *                         otherwise.
     */
    public void setReplicatingGraph(boolean replicatingGraph) {
        this.replicatingGraph = replicatingGraph;
    }
}


/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
/// ////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.Score;
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

import java.io.PrintStream;
import java.util.*;

import static edu.cmu.tetrad.graph.GraphUtils.colliderAllowed;
import static edu.cmu.tetrad.graph.GraphUtils.fciOrientbk;

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
public class Gfci implements IGraphSearch {
    /**
     * The independence test used in search.
     */
    private final IndependenceTest independenceTest;
    /**
     * The score used for FGES.
     */
    private final Score score;
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
     * The maximum degree of the output graph.
     */
    private int maxDegree = -1;
    /**
     * The print stream used for output.
     */
    private transient PrintStream out = System.out;
    /**
     * Whether one-edge faithfulness is assumed.
     */
    private boolean faithfulnessAssumed = true;
    /**
     * The number of threads to use in the search. Must be at least 1.
     */
    private int numThreads = 1;

    /**
     * A flag indicating whether the algorithm should start its search from a complete undirected graph.
     * <p>
     * If set to true, the Star-FCI algorithm initializes the search with a complete graph where every node is connected
     * with an undirected edge. If set to false, the algorithm starts the search with an alternative initial graph, such
     * as a learned or predefined CPDAG.
     * <p>
     * This option impacts the structure of the initial graph and may influence the overall search process and results.
     */
    private boolean startFromCompleteGraph;
    /**
     * A flag indicating whether the maximum p-value should be used for selecting separating sets or directing edges in
     * the algorithm.
     * <p>
     * When set to {@code true}, the algorithm prioritizes separating sets or edges based on the highest p-value
     * encountered. This may impact the behavior of independence tests or edge direction decisions during the search
     * process.
     */
    private boolean useMaxP;

    /**
     * Constructs a new GFci algorithm with the given independence test and score.
     *
     * @param test  The independence test to use.
     * @param score The score to use.
     */
    public Gfci(IndependenceTest test, Score score) {
        this.independenceTest = test;
        this.score = score;
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
     * Creates a set of nodes by selecting elements from the adjacency list based on the given indices.
     *
     * @param choice A list of integers representing the indices of nodes to be included in the combination.
     * @param adj    A list of nodes representing the adjacency list from which the nodes are selected.
     * @return A set of nodes selected from the adjacency list based on the indices in the choice list.
     */
    private static Set<Node> combination(List<Integer> choice, List<Node> adj) {

        // Create a set of nodes from the subset of adjx represented by choice.
        Set<Node> combination = new HashSet<>();

        for (int i : choice) {
            combination.add(adj.get(i));
        }

        return combination;
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

        Graph cpdag;

        if (startFromCompleteGraph) {
            TetradLogger.getInstance().log("===Starting with complete graph=== ");
            cpdag = new EdgeListGraph(independenceTest.getVariables());
            cpdag = GraphUtils.completeGraph(cpdag);
        } else {
            cpdag = getMarkovCpdag();
        }

        Graph pag = new EdgeListGraph(cpdag);

        Set<Triple> unshieldedColliders = new HashSet<>();

        SepsetMap sepsetMap = new SepsetMap();

        if (verbose) {
            TetradLogger.getInstance().log("Starting *-FCI extra edge removal step.");
        }

        for (Edge edge : pag.getEdges()) {
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
                            TetradLogger.getInstance().log("Copied collider " + x + " → " + y + " ← " + z + " from CPDAG.");
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
                                TetradLogger.getInstance().log("Oriented collider by separating set: " + x + " → " + y + " ← " + z);
                            }
                        }
                    }
                }
            }
        }

        // Possible d-sep removal
        for (Edge edge : pag.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node a = edge.getNode1();
            Node c = edge.getNode2();

            List<Node> possibleDsep = pag.paths().possibleDsep(a, -1);
            possibleDsep.remove(a);
            possibleDsep.remove(c);
            Set<Node> sepset = getSepset(a, c, new HashSet<>(), independenceTest, depth, null, possibleDsep, useMaxP);

            if (sepset != null) {
                pag.removeEdge(a, c);
                sepsetMap.set(a, c, sepset);

                if (verbose) {
                    IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
                    double pValue = result.getPValue();
                    TetradLogger.getInstance().log("Removed edge " + a + " -- " + c + " in extra-edge removal step; sepset = "
                                                   + sepset + ", p-value = " + pValue + ".");
                }
            }

            if (pag.isAdjacentTo(a, c)) {
                possibleDsep = pag.paths().possibleDsep(c, -1);
                possibleDsep.remove(a);
                possibleDsep.remove(c);
                sepset = getSepset(a, c, new HashSet<>(), independenceTest, depth, null, possibleDsep, useMaxP);

                if (sepset != null) {
                    pag.removeEdge(a, c);
                    sepsetMap.set(a, c, sepset);

                    if (verbose) {
                        IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
                        double pValue = result.getPValue();
                        TetradLogger.getInstance().log("Removed edge " + a + " -- " + c + " in extra-edge removal step; sepset = "
                                                       + sepset + ", p-value = " + pValue + ".");
                    }
                }
            }
        }

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
                            TetradLogger.getInstance().log("Copied collider " + x + " → " + y + " ← " + z + " from CPDAG.");
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
                                TetradLogger.getInstance().log("Oriented collider by separating set: " + x + " → " + y + " ← " + z);
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
        }

        if (verbose) {
            TetradLogger.getInstance().log("GFCI finished.");
        }

        return pag;
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
     * Sets the maximum indegree of the output graph.
     *
     * @param maxDegree This maximum.
     */
    public void setMaxDegree(int maxDegree) {
        if (maxDegree < -1) {
            throw new IllegalArgumentException("Depth must be -1 (unlimited) or >= 0: " + maxDegree);
        }

        this.maxDegree = maxDegree;
    }

    /**
     * Sets the print stream used for output, default System.out.
     *
     * @param out This print stream.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * Sets whether one-edge faithfulness is assumed. For FGES
     *
     * @param faithfulnessAssumed True, if so.
     * @see Fges#setFaithfulnessAssumed(boolean)
     */
    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = faithfulnessAssumed;
    }

    /**
     * Sets the number of threads to use in the search.
     *
     * @param numThreads The number of threads to use. Must be at least 1.
     */
    public void setNumThreads(int numThreads) {
        if (numThreads < 1) {
            throw new IllegalArgumentException("Number of threads must be at least 1: " + numThreads);
        }
        this.numThreads = numThreads;
    }

    /**
     * Sets whether the search should start from a complete graph.
     *
     * @param startFromCompleteGraph A boolean value indicating if the search should start from a complete graph.
     */
    public void setStartFromCompleteGraph(boolean startFromCompleteGraph) {
        this.startFromCompleteGraph = startFromCompleteGraph;
    }

    /**
     * Executes the FGES algorithm to compute the Markov equivalence class in the form of a completed partially directed
     * acyclic graph (CPDAG) based on the provided score and algorithm configuration.
     *
     * @return The resulting CPDAG representing the Markov equivalence class.
     * @throws InterruptedException if the operation is interrupted.
     */
    public Graph getMarkovCpdag() throws InterruptedException {
        if (isVerbose()) {
            TetradLogger.getInstance().log("Starting FGES.");
        }

        Fges fges = new Fges(this.score);
        fges.setKnowledge(getKnowledge());
        fges.setVerbose(isVerbose());
        fges.setFaithfulnessAssumed(this.faithfulnessAssumed);
        fges.setMaxDegree(this.maxDegree);
        fges.setOut(this.out);
        fges.setNumThreads(numThreads);
        Graph cpdag = fges.search();

        if (isVerbose()) {
            TetradLogger.getInstance().log("Finished FGES.");
        }

        return cpdag;
    }

    /**
     * Sets whether the "Use Max-P" option is enabled or not.
     *
     * @param useMaxP A boolean flag indicating whether the "Use Max-P" option is enabled (true) or disabled (false).
     */
    public void setUseMaxP(boolean useMaxP) {
        this.useMaxP = useMaxP;
    }
}

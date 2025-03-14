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
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.search.utils.SepsetMap;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static edu.cmu.tetrad.graph.GraphUtils.colliderAllowed;
import static edu.cmu.tetrad.graph.GraphUtils.fciOrientbk;

/**
 * Implements a template modification of GFCI that starts with a given Markov CPDAG and then fixes that result to be
 * correct for latent variables models. First, colliders from the Markov DAG are copied into the final circle-circle
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
     * Constructs a new GFci algorithm with the given independence test and score.
     *
     * @param test The independence test to use.
     */
    public StarFci(IndependenceTest test) {
        this.independenceTest = test;
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

    private static @NotNull List<List<Integer>> getChoices(List<Node> adjx, int depth) {
        List<List<Integer>> choices = new ArrayList<>();

        if (depth < 0 || depth > adjx.size()) depth = adjx.size();

        SublistGenerator cg = new SublistGenerator(adjx.size(), depth);
        int[] choice;

        while ((choice = cg.next()) != null) {
            choices.add(GraphUtils.asList(choice));
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
     * Finds a separating set that is a subset of the adjacency of nodes x or y in the input graph.
     *
     * @param graph      The graph being analyzed.
     * @param x          The first node between which independence is checked.
     * @param y          The second node between which independence is checked.
     * @param containing A set of nodes that must be included in the separating set.
     * @param test       The independence test used to evaluate separation.
     * @param depth      The maximum size of subsets to be tested for independence.
     * @param order      An optional list specifying the order of nodes for additional constraints.
     * @return A separating set of nodes (if found) that is a subset of the adjacency of x or y, or {@code null} if no
     * such set is found.
     */
    public static Set<Node> sepsetSubsetOfAdjxOrAdjy(Graph graph, Node x, Node y, Set<Node> containing, IndependenceTest test, int depth, List<Node> order) {
        List<Node> adjx = graph.getAdjacentNodes(x);
        List<Node> adjy = graph.getAdjacentNodes(y);
        adjx.remove(y);
        adjy.remove(x);

        adjx.removeIf(node -> node.getNodeType() == NodeType.LATENT);
        adjy.removeIf(node -> node.getNodeType() == NodeType.LATENT);

        List<List<Integer>> choices = getChoices(adjx, depth);

        // Parallelize processing for adjx
        Set<Node> sepset = choices.parallelStream()
                .map(choice -> combination(choice, adjx)) // Generate combinations in parallel
                .filter(subset -> subset.containsAll(containing)) // Filter combinations that don't contain 'containing'
                .filter(subset -> {
                    try {
                        if (order != null) {
                            Node _y = order.indexOf(x) < order.indexOf(y) ? y : x;

                            for (Node node : subset) {
                                if (order.indexOf(node) > order.indexOf(_y)) {
                                    return false;
                                }
                            }
                        }

                        return test.checkIndependence(x, y, subset).isIndependent();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).findFirst().orElse(null);

        if (sepset != null) {
            return sepset;
        }

        // Parallelize processing for adjy
        choices = getChoices(adjy, depth);

        sepset = choices.parallelStream()
                .map(choice -> combination(choice, adjy)) // Generate combinations in parallel
                .filter(subset -> subset.containsAll(containing)) // Filter combinations that don't contain 'containing'
                .filter(subset -> {
                    try {
                        if (order != null) {
                            Node _y = order.indexOf(x) < order.indexOf(y) ? y : x;

                            for (Node node : subset) {
                                if (order.indexOf(node) > order.indexOf(_y)) {
                                    return false;
                                }
                            }
                        }

                        return test.checkIndependence(x, y, subset).isIndependent();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).findFirst().orElse(null);

        return sepset;
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
        pag.reorientAllWith(Endpoint.CIRCLE);

        Set<Triple> unshieldedColliders = new HashSet<>();

        SepsetMap sepsetMap = new SepsetMap();

        for (Edge edge : pag.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            Node a = edge.getNode1();
            Node c = edge.getNode2();

            Set<Node> sepset = sepsetSubsetOfAdjxOrAdjy(pag, a, c, new HashSet<>(), independenceTest, depth, null);

            if (sepset != null) {
                pag.removeEdge(a, c);
                sepsetMap.set(a, c, sepset);

                List<Node> adj = pag.getAdjacentNodes(a);
                adj.retainAll(pag.getAdjacentNodes(c));

                if (verbose) {
                    IndependenceResult result = independenceTest.checkIndependence(a, c, sepset);
                    double pValue = result.getPValue();
                    TetradLogger.getInstance().log("Removed edge " + a + " -- " + c
                                                   + " in extra-edge removal step; sepset = " + sepset + ", p-value = " + pValue + ".");
                }
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("Starting GFCI-R0.");
        }

        pag.reorientAllWith(Endpoint.CIRCLE);

        fciOrientbk(knowledge, pag, pag.getNodes());

        for (Node y : nodes) {
            List<Node> adjacentNodes = new ArrayList<>(pag.getAdjacentNodes(y));

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (unshieldedTriple(pag, x, y, z) && unshieldedCollider(cpdag, x, y, z)) {
                    if (colliderAllowed(pag, x, y, z, knowledge) && cpdag.isDefCollider(x, y, z)) {
                        pag.setEndpoint(x, y, Endpoint.ARROW);
                        pag.setEndpoint(z, y, Endpoint.ARROW);

                        if (verbose) {
                            TetradLogger.getInstance().log("Copied " + x + " *-> " + y + " <-* " + z + " from CPDAG.");

                            if (Edges.isBidirectedEdge(pag.getEdge(x, y))) {
                                TetradLogger.getInstance().log("Created bidirected edge: " + pag.getEdge(x, y));
                            }

                            if (Edges.isBidirectedEdge(pag.getEdge(y, z))) {
                                TetradLogger.getInstance().log("Created bidirected edge: " + pag.getEdge(y, z));
                            }
                        }
                    }
                } else if (cpdag.isAdjacentTo(x, z)) {
                    if (colliderAllowed(pag, x, y, z, knowledge)) {
                        Set<Node> sepset = sepsetMap.get(x, z);

                        if (sepset != null) {
//                            pag.removeEdge(x, z);

                            if (!sepset.contains(y)) {
                                pag.setEndpoint(x, y, Endpoint.ARROW);
                                pag.setEndpoint(z, y, Endpoint.ARROW);

                                if (verbose) {
                                    TetradLogger.getInstance().log("Oriented collider by test " + x + " *-> " + y + " <-* " + z + ".");

                                    if (Edges.isBidirectedEdge(pag.getEdge(x, y))) {
                                        TetradLogger.getInstance().log("Created bidirected edge: " + pag.getEdge(x, y));
                                    }

                                    if (Edges.isBidirectedEdge(pag.getEdge(y, z))) {
                                        TetradLogger.getInstance().log("Created bidirected edge: " + pag.getEdge(y, z));
                                    }
                                }
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
            pag = GraphUtils.guaranteePag(pag, fciOrient, knowledge, unshieldedColliders, unshieldedColliders, verbose, new HashSet<>());
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
     * Sets whether the search should start from a complete graph.
     *
     * @param startFromCompleteGraph A boolean value indicating if the search should start from a complete graph.
     */
    public void setStartFromCompleteGraph(boolean startFromCompleteGraph) {
        this.startFromCompleteGraph = startFromCompleteGraph;
    }

    /**
     * Returns a Markov CPDAG to use as the initial graph in the Star-FCI search.
     *
     * @return This CPDAG.
     * @throws InterruptedException if interrupted.
     */
    public abstract Graph getMarkovCpdag() throws InterruptedException;
}

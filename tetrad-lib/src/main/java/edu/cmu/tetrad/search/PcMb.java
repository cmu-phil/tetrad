///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.util.FastMath;

import java.text.NumberFormat;
import java.util.*;

/**
 * Searches for a CPDAG representing all the Markov blankets for a given target T consistent with the given independence
 * information. This CPDAG may be used to generate the actual list of DAG's that might be Markov blankets. Note that
 * this code has been converted to be consistent with the CPC algorithm. The reference is here:
 * <p>
 * Bai, X., Padman, R., Ramsey, J., &amp; Spirtes, P. (2008). Tabu search-enhanced graphical models for classification
 * in high dimensions. INFORMS Journal on Computing, 20(3), 423-437.
 * <p>
 * This class is configured to respect knowledge of forbidden and required edges, including knowledge of temporal
 * tiers.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see FgesMb
 * @see Knowledge
 */
public final class PcMb implements IMbSearch, IGraphSearch {

    /**
     * The independence test used to perform the search.
     */
    private final IndependenceTest test;
    /**
     * The list of variables being searched over. Must contain the target.
     */
    private List<Node> variables;
    /**
     * The target variable.
     */
    private List<Node> targets;
    /**
     * The depth to which independence tests should be performed--i.e., the maximum number of conditioning variables for
     * any independence test.
     */
    private int depth;
    /**
     * The CPDAG output by the most recent search. This is saved in case the user wants to generate the list of MB
     * DAGs.
     */
    private Graph resultGraph;
    /**
     * A count of the number of independence tests performed in the course of the most recent search.
     */
    private int numIndependenceTests;
    /**
     * Information to help understand what part of the search is taking the most time.
     */
    private int[] maxRemainingAtDepth;
    /**
     * The set of nodes that edges should not be drawn to in the addDepthZeroAssociates method.
     */
    private Set<Node> a;
    /**
     * Elapsed time for the last run of the algorithm.
     */
    private long elapsedTime;
    /**
     * Knowledge.
     */
    private Knowledge knowledge;
    /**
     * Set of ambiguous unshielded triples.
     */
    private Set<Triple> ambiguousTriples;
    /**
     * True if cycles are to be prevented. Maybe expensive for large graphs (but also useful for large graphs).
     */
    private boolean meekPreventCycles;
    /**
     * True if the search should return the MB, not the MB CPDAG.
     */
    private boolean findMb = false;
    /**
     * Flag indicating whether verbose output should be enabled.
     */
    private boolean verbose = false;

    /**
     * Constructs a new search.
     *
     * @param test  The source of conditional independence information for the search.
     * @param depth The maximum number of variables conditioned on for any
     */
    public PcMb(IndependenceTest test, int depth) {
        if (test == null) {
            throw new NullPointerException();
        }

        if (depth == -1) {
            depth = Integer.MAX_VALUE;
        }

        if (depth < 0) {
            throw new IllegalArgumentException("Depth must be >= -1: " + depth);
        }

        this.test = test;
        this.depth = depth;
        this.variables = test.getVariables();
        knowledge = new Knowledge();
    }

    /**
     * Determines whether an arrowhead is allowed from one node to another based on knowledge.
     *
     * @param from      the starting node
     * @param to        the target node
     * @param knowledge the knowledge information
     * @return true if the arrowhead is allowed, false otherwise
     */
    private static boolean isArrowheadAllowed1(Node from, Node to,
                                               Knowledge knowledge) {
        if (knowledge == null) {
            return true;
        }

        return !knowledge.isRequired(to.toString(), from.toString()) &&
               !knowledge.isForbidden(from.toString(), to.toString());
    }

    /**
     * Sets whether cycles should be prevented, using a cycle checker.
     *
     * @param meekPreventCycles True, if so.
     */
    public void setMeekPreventCycles(boolean meekPreventCycles) {
        this.meekPreventCycles = meekPreventCycles;
    }

    /**
     * Searches for the MB CPDAG for the given targets.
     *
     * @param targets The targets variable.
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph search(List<Node> targets) {
        long start = MillisecondTimes.timeMillis();
        this.numIndependenceTests = 0;
        this.ambiguousTriples = new HashSet<>();

        if (targets == null) {
            throw new IllegalArgumentException(
                    "Null targets name not permitted");
        }

        this.targets = targets;

        TetradLogger.getInstance().forceLogMessage("Target = " + targets);

        // Some statistics.
        this.maxRemainingAtDepth = new int[20];
        Arrays.fill(this.maxRemainingAtDepth, -1);

        TetradLogger.getInstance().forceLogMessage("targets = " + getTargets());

        Graph graph = new EdgeListGraph();

        // Each time the addDepthZeroAssociates method is called for a node
        // v, v is added to this set, and edges to elements in this set are
        // not added to the graph subsequently. This is to handle the situation
        // of adding v1---v2 to the graph, removing it by conditioning on
        // nodes adjacent to v1, then re-adding it and not being able to
        // remove it by conditioning on nodes adjacent to v2. Once an edge
        // is removed, it should not be re-added to the graph.
        // jdramsey 8/6/04
        this.a = new HashSet<>();

        // Step 1. Get associates for the targets.
        TetradLogger.getInstance().forceLogMessage("BEGINNING step 1 (prune targets).");

        for (Node target : getTargets()) {
            if (target == null) throw new NullPointerException("Target not specified");

            graph.addNode(target);
            constructFan(target, graph);

            TetradLogger.getInstance().forceLogMessage("After step 1 (prune targets)" + graph);
            TetradLogger.getInstance().forceLogMessage("After step 1 (prune targets)" + graph);
        }

        // Step 2. Get associates for each variable adjacent to the targets,
        // removing edges based on those associates where possible. After this
        // step, adjacencies to the targets are parents or children of the targets.
        // Call this set PC.
        TetradLogger.getInstance().forceLogMessage("BEGINNING step 2 (prune PC).");

        if (findMb) {
            for (Node target : getTargets()) {
                for (Node v : graph.getAdjacentNodes(target)) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    constructFan(v, graph);

                    // Optimization: For t---v---w, toss out w if <t, v, w> can't
                    // be an unambiguous collider, judging from the side of t alone.
                    // Look at adjacencies w of v. If w is not in A, and there is no
                    // S in adj(t) containing v s.g. t _||_ v | S, then remove v.

                    W:
                    for (Node w : graph.getAdjacentNodes(v)) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        if (this.a.contains(w)) {
                            continue;
                        }

                        List<Node> _a = new LinkedList<>(this.a);
                        _a.retainAll(graph.getAdjacentNodes(w));
                        if (_a.size() > 1) continue;

                        List<Node> adjT = new ArrayList<>(graph.getAdjacentNodes(target));
                        SublistGenerator cg = new SublistGenerator(
                                adjT.size(), this.depth);
                        int[] choice;

                        while ((choice = cg.next()) != null) {
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }

                            Set<Node> s = GraphUtils.asSet(choice, adjT);
                            if (!s.contains(v)) continue;

                            if (independent(target, w, s)) {
                                graph.removeEdge(v, w);
                                continue W;
                            }
                        }
                    }
                }

                TetradLogger.getInstance().forceLogMessage("After step 2 (prune PC)" + graph);

                // Step 3. Get associates for each node now two links away from the
                // targets, removing edges based on those associates where possible.
                // After this step, adjacencies to adjacencies of the targets are parents
                // or children of adjacencies to the targets. Call this set PCPC.
                TetradLogger.getInstance().forceLogMessage("BEGINNING step 3 (prune PCPC).");

                for (Node v : graph.getAdjacentNodes(target)) {
                    for (Node w : graph.getAdjacentNodes(v)) {
                        if (getA().contains(w)) {
                            continue;
                        }

                        constructFan(w, graph);
                    }
                }
            }
        }

        TetradLogger.getInstance().forceLogMessage("After step 3 (prune PCPC)" + graph);

        TetradLogger.getInstance().forceLogMessage("BEGINNING step 4 (PC Orient).");

        GraphSearchUtils.pcOrientbk(this.knowledge, graph, graph.getNodes(), verbose);

        List<Node> _visited = new LinkedList<>(getA());
        orientUnshieldedTriples(this.knowledge, graph, getDepth(), _visited);

        MeekRules meekRules = new MeekRules();
        meekRules.setMeekPreventCycles(this.meekPreventCycles);
        meekRules.setKnowledge(this.knowledge);
        meekRules.orientImplied(graph);

        TetradLogger.getInstance().forceLogMessage("After step 4 (PC Orient)" + graph);

        TetradLogger.getInstance().forceLogMessage("BEGINNING step 5 (Trim graph to {T} U PC U " +
                                                   "{Parents(Children(T))}).");

        if (findMb) {
            Set<Node> mb = new HashSet<>();

            for (Node n : graph.getNodes()) {
                for (Node t : targets) {
                    if (graph.isAdjacentTo(t, n)) {
                        mb.add(n);
                    } else {
                        for (Node m : graph.getChildren(t)) {
                            if (graph.isParentOf(n, m)) {
                                mb.add(n);
                            }
                        }
                    }
                }
            }

            N:
            for (Node n : graph.getNodes()) {
                for (Node t : targets) {
                    if (t == n) continue N;
                }

                if (!mb.contains(n)) graph.removeNode(n);
            }
        } else {
            for (Edge e : graph.getEdges()) {
                if (!(targets.contains(e.getNode1()) || targets.contains(e.getNode2()))) {
                    graph.removeEdge(e);
                }
            }
        }

        TetradLogger.getInstance().forceLogMessage("After step 6 (Remove edges among P and P of C)" + graph);

        finishUp(start, graph);

        return graph;
    }

    /**
     * Searches for the Markov blanket CPDAG for the given targets.
     *
     * @return The Markov blanket CPDAG as a Graph object.
     */
    public Graph search() {
        this.numIndependenceTests = 0;
        this.ambiguousTriples = new HashSet<>();

        // Some statistics.
        this.maxRemainingAtDepth = new int[20];
        Arrays.fill(this.maxRemainingAtDepth, -1);

        Graph graph = new EdgeListGraph();

        // Each time the addDepthZeroAssociates method is called for a node
        // v, v is added to this set, and edges to elements in this set are
        // not added to the graph subsequently. This is to handle the situation
        // of adding v1---v2 to the graph, removing it by conditioning on
        // nodes adjacent to v1, then re-adding it and not being able to
        // remove it by conditioning on nodes adjacent to v2. Once an edge
        // is removed, it should not be re-added to the graph.
        // jdramsey 8/6/04
        this.a = new HashSet<>();
        this.variables = this.test.getVariables();

        Node target = this.variables.get(0);
        graph.addNode(target);

        for (Node node : this.variables) {
            if (!graph.containsNode(node)) {
                graph.addNode(node);
            }

            constructFan(node, graph);
        }

        for (Node node : this.variables) {
            if (!graph.containsNode(node)) {
                graph.addNode(node);
            }
        }

        orientUnshieldedTriples(this.knowledge, graph, getDepth(), graph.getNodes());

        MeekRules meekRules = new MeekRules();
        meekRules.setMeekPreventCycles(this.meekPreventCycles);
        meekRules.setKnowledge(this.knowledge);
        meekRules.orientImplied(graph);

        return graph;
    }

    /**
     * Returns the set of triples identified as ambiguous by the CPC algorithm during the most recent search.
     *
     * @return This set.
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    /**
     * Returns the number of independence tests performed during the most recent search.
     *
     * @return This number.
     */
    public int getNumIndependenceTests() {
        return this.numIndependenceTests;
    }

    /**
     * Return the targets of the most recent search.
     *
     * @return This list.
     */
    public List<Node> getTargets() {
        return this.targets;
    }

    /**
     * Returns the elapsed time of the most recent search.
     *
     * @return This time.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * Returns "PC-MB."
     *
     * @return This string.
     */
    public String getAlgorithmName() {
        return "PC-MB";
    }

    /**
     * Returns the depth of the search--that is, the maximum number of variables conditioned on in any conditional
     * independence test.
     *
     * @return This depth.
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * Sets the maximum number of conditioning variables for any conditional independence test.
     *
     * @param depth This depth.
     */
    public void setDepth(int depth) {
        //  If it's -1 to set it to some unreasonably high number like 1000
        if (depth < 0) {
            depth = 1000;
        }

        this.depth = depth;
    }

    /**
     * Returns the result graph.
     *
     * @return This graph.
     */
    public Graph resultGraph() {
        return this.resultGraph;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the Markov blanket variables (not the Markov blanket DAG).
     */
    public Set<Node> findMb(Node target) {
        Graph graph = search(Collections.singletonList(target));
        Set<Node> nodes = new HashSet<>(graph.getNodes());
        nodes.remove(target);
        return nodes;
    }

    /**
     * Returns the test used in search.
     *
     * @return This test.
     */
    public IndependenceTest getTest() {
        return this.test;
    }

    /**
     * Returns The knowledge used in search.
     *
     * @return This knowledge.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets knowledge, to which the algorithm is in fact sensitive.
     *
     * @param knowledge This knowledge.
     * @see Knowledge
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Returns the set of nodes in A. A is a set of nodes.
     *
     * @return The set of nodes in A.
     */
    private Set<Node> getA() {
        return this.a;
    }

    /**
     * Adds associates of the target and prunes edges using subsets of adjacencies to the target.
     *
     * @param target The variable whose Markov blanket is sought.
     * @param graph  The getModel search graph.
     */
    private void constructFan(Node target, Graph graph) {
        addAllowableAssociates(target, graph);
        prune(target, graph);
    }

    /**
     * Adds allowable associates to a node in the graph.
     *
     * @param v     The node to add associates to.
     * @param graph The graph object to which the associates are added.
     */
    private void addAllowableAssociates(Node v, Graph graph) {
        getA().add(v);
        int numAssociated = 0;

        for (Node w : this.variables) {
            if (getA().contains(w)) {
                continue;
            }

            if (!independent(v, w, new HashSet<>()) && !edgeForbidden(v, w)) {
                addEdge(graph, w, v);
                numAssociated++;
            }
        }

        noteMaxAtDepth(0, numAssociated);
    }

    /**
     * Prunes edges in the graph based on the given node and graph.
     *
     * @param node  The node about which pruning will take place.
     * @param graph The graph to be modified by pruning.
     */
    private void prune(Node node, Graph graph) {
        for (int depth = 1; depth <= getDepth(); depth++) {
            if (graph.getAdjacentNodes(node).size() < depth) {
                return;
            }

            prune(node, graph, depth);
        }
    }

    /**
     * Tries to remove the edge node---from using adjacent nodes of node 'from.' then tries to remove each other edge
     * adjacent node 'from' using remaining edges adjacent node 'from.' If the edge 'node' is removed, the method
     * immediately returns.
     *
     * @param node  The node about which pruning it to take place.
     * @param graph The getModel search graph, to be modified by pruning.
     * @param depth The maximum number of conditioning variables.
     */
    private void prune(Node node, Graph graph, int depth) {
        TetradLogger.getInstance().forceLogMessage("Trying to remove edges adjacent to node " + node +
                                                   ", depth = " + depth + ".");

        // Otherwise, try removing all other edges adjacent node. Return
        // true if more edges could be removed at the next depth.
        List<Node> a = new LinkedList<>(graph.getAdjacentNodes(node));

        NEXT_EDGE:
        for (Node y : a) {
            List<Node> adjNode =
                    new LinkedList<>(graph.getAdjacentNodes(node));
            adjNode.remove(y);
            adjNode = possibleParents(node, adjNode);

            if (adjNode.size() < depth) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjNode.size(), depth);
            int[] choice;

            while ((choice = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Set<Node> condSet = GraphUtils.asSet(choice, adjNode);

                if (independent(node, y, condSet) && !edgeRequired(node, y)) {
                    graph.removeEdge(node, y);

                    // The target itself must not be removed.
                    if (graph.getEdges(y).isEmpty() && !getTargets().contains(y)) {
                        graph.removeNode(y);
                    }

                    continue NEXT_EDGE;
                }
            }
        }

        int numAdjacents = graph.getAdjacentNodes(node).size();
        noteMaxAtDepth(depth, numAdjacents);
    }

    /**
     * Finish up the search process by calculating elapsed time, logging messages, and assigning the result graph.
     *
     * @param start The start time of the search process.
     * @param graph The result graph of the search process.
     */
    private void finishUp(long start, Graph graph) {
        long stop = MillisecondTimes.timeMillis();
        this.elapsedTime = stop - start;
        double seconds = this.elapsedTime / 1000d;

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        String message = "PC-MB took " + nf.format(seconds) + " seconds.";
        TetradLogger.getInstance().forceLogMessage(message);
        TetradLogger.getInstance().forceLogMessage("Number of independence tests performed = " +
                                                   getNumIndependenceTests());

        this.resultGraph = graph;
    }

    /**
     * Determines whether the given nodes are independent.
     *
     * @param v The first node.
     * @param w The second node.
     * @param z The set of nodes to condition on.
     * @return True if the nodes are independent, false otherwise.
     */
    private boolean independent(Node v, Node w, Set<Node> z) {
        boolean independent = getTest().checkIndependence(v, w, z).isIndependent();

        this.numIndependenceTests++;
        return independent;
    }

    /**
     * Adds an undirected edge between two nodes in a graph if the source node does not already exist in the graph.
     *
     * @param graph The graph to which the edge will be added.
     * @param w     The source node.
     * @param v     The target node.
     */
    private void addEdge(Graph graph, Node w, Node v) {
        if (!graph.containsNode(w)) {
            graph.addNode(w);
        }

        graph.addUndirectedEdge(v, w);
    }

    /**
     * Notes the maximum number of adjacents at a given depth.
     *
     * @param depth        the depth at which to note the maximum number of adjacents
     * @param numAdjacents the number of adjacents
     */
    private void noteMaxAtDepth(int depth, int numAdjacents) {
        if (depth < this.maxRemainingAtDepth.length &&
            numAdjacents > this.maxRemainingAtDepth[depth]) {
            this.maxRemainingAtDepth[depth] = numAdjacents;
        }
    }

    /**
     * Orients unshielded triples in a given graph based on the provided knowledge.
     *
     * @param knowledge the knowledge used for orientation
     * @param graph     the graph containing the triples
     * @param depth     the depth of the orientation process
     * @param nodes     the specific nodes to orient triples for (if null, all nodes in the graph will be considered)
     */
    private void orientUnshieldedTriples(Knowledge knowledge, Graph graph, int depth, List<Node> nodes) {
        TetradLogger.getInstance().forceLogMessage("Starting Collider Orientation:");

        this.ambiguousTriples = new HashSet<>();

        if (nodes == null) {
            nodes = graph.getNodes();
        }

        for (Node y : nodes) {
            List<Node> adjacentNodes = new ArrayList<>(graph.getAdjacentNodes(y));

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (graph.isAdjacentTo(x, z)) {
                    continue;
                }

                TripleType type = getTripleType(graph, x, y, z, depth);

                if (type == TripleType.COLLIDER) {
                    if (colliderAllowed(x, y, z, knowledge)) {
                        graph.setEndpoint(x, y, Endpoint.ARROW);
                        graph.setEndpoint(z, y, Endpoint.ARROW);
                        String message = "Collider oriented: " + Triple.pathString(graph, x, y, z);
                        TetradLogger.getInstance().forceLogMessage(message);
                    }
                } else if (type == TripleType.AMBIGUOUS) {
                    Triple triple = new Triple(x, y, z);
                    this.ambiguousTriples.add(triple);
                    graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                    String message = "tripleClassifications: " + Triple.pathString(graph, x, y, z);
                    TetradLogger.getInstance().forceLogMessage(message);
                } else {
                    String message = "tripleClassifications: " + Triple.pathString(graph, x, y, z);
                    TetradLogger.getInstance().forceLogMessage(message);
                }
            }
        }

        TetradLogger.getInstance().forceLogMessage("Finishing Collider Orientation.");
    }

    /**
     * Determines the type of a triple based on the given graph, nodes, and depth.
     *
     * @param graph the graph representing the causal relationships between nodes
     * @param x     the first node of the triple
     * @param y     the second node of the triple
     * @param z     the third node of the triple
     * @param depth the depth of the search for separating sets (-1 for unlimited depth)
     * @return the type of the triple (AMBIGUOUS, NONCOLLIDER, COLLIDER)
     */
    private TripleType getTripleType(Graph graph, Node x, Node y, Node z, int depth) {
        boolean existsSepsetContainingY = false;
        boolean existsSepsetNotContainingY = false;

        Set<Node> __nodes = new HashSet<>(graph.getAdjacentNodes(x));
        __nodes.remove(z);

        List<Node> _nodes = new LinkedList<>(__nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = Integer.MAX_VALUE;
        }
        _depth = FastMath.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Set<Node> condSet = GraphUtils.asSet(choice, _nodes);

                if (independent(x, z, condSet)) {
                    if (condSet.contains(y)) {
                        existsSepsetContainingY = true;
                    } else {
                        existsSepsetNotContainingY = true;
                    }
                }
            }
        }

        __nodes = new HashSet<>(graph.getAdjacentNodes(z));
        __nodes.remove(x);

        _nodes = new LinkedList<>(__nodes);

        _depth = depth;
        if (_depth == -1) {
            _depth = Integer.MAX_VALUE;
        }
        _depth = FastMath.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Set<Node> condSet = GraphUtils.asSet(choice, _nodes);

                if (independent(x, z, condSet)) {
                    if (condSet.contains(y)) {
                        existsSepsetContainingY = true;
                    } else {
                        existsSepsetNotContainingY = true;
                    }
                }
            }
        }

        if (existsSepsetContainingY == existsSepsetNotContainingY) {
            return TripleType.AMBIGUOUS;
        } else if (!existsSepsetNotContainingY) {
            return TripleType.NONCOLLIDER;
        } else {
            return TripleType.COLLIDER;
        }
    }

    /**
     * Checks if there is a forbidden edge between two nodes.
     *
     * @param x1 the first node
     * @param x2 the second node
     * @return true if there is a forbidden edge between the two nodes, false otherwise.
     */
    private boolean edgeForbidden(Node x1, Node x2) {
        return getKnowledge().isForbidden(x1.toString(), x2.toString()) &&
               getKnowledge().isForbidden(x2.toString(), x1.toString());
    }

    /**
     * Checks if an edge is required between two nodes.
     *
     * @param x1 the first node
     * @param x2 the second node
     * @return true if an edge is required between the two nodes, false otherwise
     */
    private boolean edgeRequired(Node x1, Node x2) {
        return getKnowledge().isRequired(x1.toString(), x2.toString()) ||
               getKnowledge().isRequired(x2.toString(), x1.toString());
    }

    /**
     * Removes from adjx any adjacencies that cannot be parents of x given the background knowledge.
     *
     * @param node    The node being discussed.
     * @param adjNode The list of adjacencies to <code>node</code>.
     * @return The revised list of nodes--i.e., the possible parents among adjx, according to knowledge.
     */
    private List<Node> possibleParents(Node node, List<Node> adjNode) {
        List<Node> possibleParents = new LinkedList<>();
        String _x = node.getName();

        for (Node z : adjNode) {
            String _z = z.getName();

            if (possibleParentOf(_z, _x, this.knowledge)) {
                possibleParents.add(z);
            }
        }

        return possibleParents;
    }

    /**
     * @param z         The name of a node.
     * @param x         The name of another node.
     * @param knowledge The knowledge set--see the Knowledge class.
     * @return True, just in case z is a possible parent of x.
     */
    private boolean possibleParentOf(String z, String x, Knowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    /**
     * Checks if colliders are allowed based on the given parameters.
     *
     * @param x         the first node
     * @param y         the middle node
     * @param z         the last node
     * @param knowledge the knowledge object
     * @return true if colliders are allowed, false otherwise
     */
    private boolean colliderAllowed(Node x, Node y, Node z, Knowledge knowledge) {
        return PcMb.isArrowheadAllowed1(x, y, knowledge) &&
               PcMb.isArrowheadAllowed1(z, y, knowledge);
    }

    /**
     * <p>Setter for the field <code>variables</code>.</p>
     *
     * @param variables a {@link java.util.List} object
     */
    public void setVariables(List<Node> variables) {
        this.variables = variables;
    }

    /**
     * <p>Setter for the field <code>findMb</code>.</p>
     *
     * @param findMb a boolean
     */
    public void setFindMb(boolean findMb) {
        this.findMb = findMb;
    }

    /**
     * Sets the verbosity level of the search.
     *
     * @param verbose true if verbose output should be enabled, false otherwise
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * The TripleType class represents the possible types of triples.
     */
    private enum TripleType {
        /**
         * Could be either a collider or not; indeterminate.
         */
        AMBIGUOUS,
        /**
         * The COLLIDER variable represents X*-&gt;Y&lt;*Z.
         */
        COLLIDER,
        /**
         * Not a collider.
         */
        NONCOLLIDER
    }
}







///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.NumberFormat;
import java.util.*;

/**
 * Searches for a CPDAG representing all of the Markov blankets for a given target T consistent with the given
 * independence information. This CPDAG may be used to generate the actual list of DAG's that might be Markov
 * blankets. Note that this code has been converted to be consistent with the CPC algorithm.
 *
 * @author Joseph Ramsey
 */
public final class Mbfs implements MbSearch, GraphSearch {

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
    private Node target;

    /**
     * The depth to which independence tests should be performed--i.e. the maximum number of conditioning variables for
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
     * Information to help understand what part of the search is taking the most time.
     */
    private Node[] maxVariableAtDepth;

    /**
     * The set of nodes that edges should not be drawn to in the addDepthZeroAssociates method.
     */
    private Set<Node> a;

    /**
     * Elapsed time for the last run of the algorithm.
     */
    private long elapsedTime;

    /**
     * The true graph, if known. If this is provided, notes will be printed out for edges removed that are in the true
     * Markov blanket.
     */
    private Dag trueMb;

    /**
     * Knowledge.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * The list of all unshielded triples.
     */
    private Set<Triple> allTriples;

    /**
     * Set of unshielded colliders from the triple orientation step.
     */
    private Set<Triple> colliderTriples;

    /**
     * Set of unshielded noncolliders from the triple orientation step.
     */
    private Set<Triple> noncolliderTriples;

    /**
     * Set of ambiguous unshielded triples.
     */
    private Set<Triple> ambiguousTriples;

    /**
     * The most recently returned graph.
     */
    private Graph graph;

    /**
     * True if cycles are to be aggressively prevented. May be expensive for large graphs (but also useful for large
     * graphs).
     */
    private boolean aggressivelyPreventCycles;

    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    //==============================CONSTRUCTORS==========================//

    /**
     * Constructs a new search.
     *
     * @param test  The source of conditional independence information for the search.
     * @param depth The maximum number of variables conditioned on for any
     */
    public Mbfs(final IndependenceTest test, int depth) {
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
    }

    //===============================PUBLIC METHODS=======================//


    public boolean isAggressivelyPreventCycles() {
        return this.aggressivelyPreventCycles;
    }

    public void setAggressivelyPreventCycles(final boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    /**
     * Searches for the MB CPDAG for the given target.
     *
     * @param targetName The name of the target variable.
     */
    public Graph search(final String targetName) {
        if (targetName == null) {
            throw new IllegalArgumentException("Target variable name needs to be provided.");
        }

        this.target = getVariableForName(targetName);
        return search(this.target);
    }

    /**
     * Searches for the MB CPDAG for the given target.
     *
     * @param target The target variable.
     */
    public Graph search(final Node target) {
        final long start = System.currentTimeMillis();
        this.numIndependenceTests = 0;
        this.allTriples = new HashSet<>();
        this.ambiguousTriples = new HashSet<>();
        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();

        if (target == null) {
            throw new IllegalArgumentException(
                    "Null target name not permitted");
        }

        this.target = target;

        this.logger.log("info", "Target = " + target);

        // Some statistics.
        this.maxRemainingAtDepth = new int[20];
        this.maxVariableAtDepth = new Node[20];
        Arrays.fill(this.maxRemainingAtDepth, -1);
        Arrays.fill(this.maxVariableAtDepth, null);

        this.logger.log("info", "target = " + getTarget());

        final Graph graph = new EdgeListGraph();

        // Each time the addDepthZeroAssociates method is called for a node
        // v, v is added to this set, and edges to elements in this set are
        // not added to the graph subsequently. This is to handle the situation
        // of adding v1---v2 to the graph, removing it by conditioning on
        // nodes adjacent to v1, then re-adding it and not being able to
        // remove it by conditioning on nodes adjacent to v2. Once an edge
        // is removed, it should not be re-added to the graph.
        // jdramsey 8/6/04
        this.a = new HashSet<>();

        // Step 1. Get associates for the target.
        this.logger.log("info", "BEGINNING step 1 (prune target).");

        graph.addNode(getTarget());
        constructFan(getTarget(), graph);

        this.logger.log("graph", "After step 1 (prune target)" + graph);
        this.logger.log("graph", "After step 1 (prune target)" + graph);

        // Step 2. Get associates for each variable adjacent to the target,
        // removing edges based on those associates where possible. After this
        // step, adjacencies to the target are parents or children of the target.
        // Call this set PC.
        this.logger.log("info", "BEGINNING step 2 (prune PC).");

//        variables = graph.getNodes();

        for (final Node v : graph.getAdjacentNodes(getTarget())) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            constructFan(v, graph);

            // Optimization: For t---v---w, toss out w if <t, v, w> can't
            // be an unambiguous collider, judging from the side of t alone.
            // Look at adjacencies w of v. If w is not in A, and there is no
            // S in adj(t) containing v s.g. t _||_ v | S, then remove v.

            W:
            for (final Node w : graph.getAdjacentNodes(v)) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (this.a.contains(w)) {
                    continue;
                }

                final List _a = new LinkedList<>(this.a);
                _a.retainAll(graph.getAdjacentNodes(w));
                if (_a.size() > 1) continue;

                final List<Node> adjT = graph.getAdjacentNodes(getTarget());
                final DepthChoiceGenerator cg = new DepthChoiceGenerator(
                        adjT.size(), this.depth);
                int[] choice;

                while ((choice = cg.next()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    final List<Node> s = GraphUtils.asList(choice, adjT);
                    if (!s.contains(v)) continue;

                    if (independent(getTarget(), w, s)) {
                        graph.removeEdge(v, w);
                        continue W;
                    }
                }
            }
        }

        this.logger.log("graph", "After step 2 (prune PC)" + graph);

        // Step 3. Get associates for each node now two links away from the
        // target, removing edges based on those associates where possible.
        // After this step, adjacencies to adjacencies of the target are parents
        // or children of adjacencies to the target. Call this set PCPC.
        this.logger.log("info", "BEGINNING step 3 (prune PCPC).");

        for (final Node v : graph.getAdjacentNodes(getTarget())) {
            for (final Node w : graph.getAdjacentNodes(v)) {
                if (getA().contains(w)) {
                    continue;
                }

                constructFan(w, graph);
            }
        }

        this.logger.log("graph", "After step 3 (prune PCPC)" + graph);

        this.logger.log("info", "BEGINNING step 4 (PC Orient).");

        SearchGraphUtils.pcOrientbk(this.knowledge, graph, graph.getNodes());

        final List<Node> _visited = new LinkedList<>(getA());
        orientUnshieldedTriples(this.knowledge, graph, getTest(), getDepth(), _visited);

        final MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
        meekRules.setKnowledge(this.knowledge);
        meekRules.orientImplied(graph);

        this.logger.log("graph", "After step 4 (PC Orient)" + graph);

        this.logger.log("info", "BEGINNING step 5 (Trim graph to {T} U PC U " +
                "{Parents(Children(T))}).");

        MbUtils.trimToMbNodes(graph, getTarget(), false);

        this.logger.log("graph",
                "After step 5 (Trim graph to {T} U PC U {Parents(Children(T))})" +
                        graph);

        this.logger.log("info", "BEGINNING step 6 (Remove edges among P and P of C).");

        MbUtils.trimEdgesAmongParents(graph, getTarget());
        MbUtils.trimEdgesAmongParentsOfChildren(graph, getTarget());

        this.logger.log("graph", "After step 6 (Remove edges among P and P of C)" + graph);
//        logger.log("details", "Bounds: ");
//
//        for (int i = 0; i < maxRemainingAtDepth.length; i++) {
//            if (maxRemainingAtDepth[i] != -1) {
//                logger.log("details", "\ta" + i + " = " + maxRemainingAtDepth[i] +
//                                " (" + maxVariableAtDepth[i] + ")");
//            }
//        }

//        System.out.println("Number of fan constructions = " + visited.size());

        finishUp(start, graph);

        this.logger.log("graph", "\nReturning this graph: " + graph);

        this.graph = graph;
        return graph;
    }

    /**
     * Does a CPDAG search.
     */
    public Graph search() {
        final long start = System.currentTimeMillis();
        this.numIndependenceTests = 0;
        this.allTriples = new HashSet<>();
        this.ambiguousTriples = new HashSet<>();
        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();

        // Some statistics.
        this.maxRemainingAtDepth = new int[20];
        this.maxVariableAtDepth = new Node[20];
        Arrays.fill(this.maxRemainingAtDepth, -1);
        Arrays.fill(this.maxVariableAtDepth, null);

//        logger.info("target = " + getTarget());

        final Graph graph = new EdgeListGraph();

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

        final Node target = this.variables.get(0);
        graph.addNode(target);

        for (final Node node : this.variables) {
            if (!graph.containsNode(node)) {
                graph.addNode(node);
            }

            constructFan(node, graph);
        }

        for (final Node node : this.variables) {
            if (!graph.containsNode(node)) {
                graph.addNode(node);
            }
        }

        orientUnshieldedTriples(this.knowledge, graph, getTest(), getDepth(), graph.getNodes());

        final MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
        meekRules.setKnowledge(this.knowledge);
        meekRules.orientImplied(graph);

        this.logger.log("graph", "\nReturning this graph: " + graph);

        return graph;
    }

    /**
     * @return the set of triples identified as ambiguous by the CPC algorithm during the most recent search.
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    /**
     * @return the set of triples identified as colliders by the CPC algorithm during the most recent search.
     */
    public Set<Triple> getColliderTriples() {
        return this.colliderTriples;
    }

    /**
     * @return the set of triples identified as noncolliders by the CPC algorithm during the most recent search.
     */
    public Set<Triple> getNoncolliderTriples() {
        return this.noncolliderTriples;
    }

    /**
     * @return the number of independence tests performed during the most recent search.
     */
    public int getNumIndependenceTests() {
        return this.numIndependenceTests;
    }

    /**
     * @return the target of the most recent search.
     */
    public Node getTarget() {
        return this.target;
    }

    /**
     * @return the elapsed time of the most recent search.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * @return "MBFS."
     */
    public String getAlgorithmName() {
        return "MBFS";
    }

    /**
     * If a true MB was set before running the search, this returns it.
     */
    public Dag getTrueMb() {
        return this.trueMb;
    }

    /**
     * Sets the true MB; should be done before running the search to get on-the-fly comparisons.
     */
    public void setTrueMb(final Dag trueMb) {
        this.trueMb = trueMb;
    }

    /**
     * @return Ibid.
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * Sets the maximum number of conditioning variables for any conditional independence test.
     *
     * @param depth Ibid.
     */
    public void setDepth(int depth) {
        //  If it's -1 to set it to some unreasonably high number like 1000
        if (depth < 0) {
            depth = 1000;
        }

        this.depth = depth;
    }

    /**
     * @return Ibid.
     */
    public Graph resultGraph() {
        return this.resultGraph;
    }

    /**
     * @return just the Markov blanket (not the Markov blanket DAG).
     */
    public List<Node> findMb(final String targetName) {
        final Graph graph = search(targetName);
        final List<Node> nodes = graph.getNodes();
        nodes.remove(this.target);
        return nodes;
    }

    /**
     * @return Ibid.
     */
    public IndependenceTest getTest() {
        return this.test;
    }

    /**
     * @return Ibid.
     */
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets knowledge, to which the algorithm is in fact sensitive.
     *
     * @param knowledge See the Knowledge class.
     */
    public void setKnowledge(final IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public Graph getGraph() {
        return this.graph;
    }

    //================================PRIVATE METHODS====================//

    private Set<Node> getA() {
        return this.a;
    }

    /**
     * Adds associates of the target and prunes edges using subsets of adjacencies to the target.
     *
     * @param target The variable whose Markov blanket is sought.
     * @param graph  The getModel search graph.
     */
    private void constructFan(final Node target, final Graph graph) {
        addAllowableAssociates(target, graph);
        prune(target, graph);
    }

    private void addAllowableAssociates(final Node v, final Graph graph) {
        getA().add(v);
        int numAssociated = 0;

        for (final Node w : this.variables) {
            if (getA().contains(w)) {
                continue;
            }

            if (!independent(v, w, new LinkedList<Node>()) && !edgeForbidden(v, w)) {
                addEdge(graph, w, v);
                numAssociated++;
            }
        }

//        System.out.println("***************NUMASSOC = " + numAssociated);

        noteMaxAtDepth(0, numAssociated, v);
    }

    private void prune(final Node node, final Graph graph) {
        for (int depth = 1; depth <= getDepth(); depth++) {
            if (graph.getAdjacentNodes(node).size() < depth) {
                return;
            }

            prune(node, graph, depth);
        }
    }

    /**
     * Tries node remove the edge node---from using adjacent nodes of node 'from', then tries node remove each other
     * edge adjacent node 'from' using remaining edges adjacent node 'from.' If the edge 'node' is removed, the method
     * immediately returns.
     *
     * @param node  The node about which pruning it to take place.
     * @param graph The getModel search graph, to be modified by pruning.
     * @param depth The maximum number of conditioning variables.
     */
    private void prune(final Node node, final Graph graph, final int depth) {
        this.logger.log("pruning", "Trying to remove edges adjacent to node " + node +
                ", depth = " + depth + ".");

        // Otherwise, try removing all other edges adjacent node node. Return
        // true if more edges could be removed at the next depth.
        final List<Node> a = new LinkedList<>(graph.getAdjacentNodes(node));

        NEXT_EDGE:
        for (final Node y : a) {
            List<Node> adjNode =
                    new LinkedList<>(graph.getAdjacentNodes(node));
            adjNode.remove(y);
            adjNode = possibleParents(node, adjNode);

            if (adjNode.size() < depth) {
                continue;
            }

            final ChoiceGenerator cg = new ChoiceGenerator(adjNode.size(), depth);
            int[] choice;

            while ((choice = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final List<Node> condSet = GraphUtils.asList(choice, adjNode);

                if (independent(node, y, condSet) && !edgeRequired(node, y)) {
                    graph.removeEdge(node, y);

                    // The target itself must not be removed.
                    if (graph.getEdges(y).isEmpty() && y != getTarget()) {
                        graph.removeNode(y);
                    }

                    continue NEXT_EDGE;
                }
            }
        }

        final int numAdjacents = graph.getAdjacentNodes(node).size();
        noteMaxAtDepth(depth, numAdjacents, node);
    }

    private void finishUp(final long start, final Graph graph) {
        final long stop = System.currentTimeMillis();
        this.elapsedTime = stop - start;
        final double seconds = this.elapsedTime / 1000d;

        final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        this.logger.log("info", "MB fan search took " + nf.format(seconds) + " seconds.");
        this.logger.log("info", "Number of independence tests performed = " +
                getNumIndependenceTests());

        this.resultGraph = graph;
    }

    private boolean independent(final Node v, final Node w, final List<Node> z) {
//        if (test.splitDetermines(z, v, w)) {
//            return false;
//        }


        final boolean independent = getTest().isIndependent(v, w, z);

        if (independent) {
            if (getTrueMb() != null) {
                final Node node1 = getTrueMb().getNode(v.getName());
                final Node node2 = getTrueMb().getNode(w.getName());

                if (node1 != null && node2 != null) {
                    final Edge edge = getTrueMb().getEdge(node1, node2);

                    if (edge != null) {
                        final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
                        System.out.println(
                                "Edge removed that was in the true MB:");
                        System.out.println("\tTrue edge = " + edge);
                        System.out.println("\t" +
                                SearchLogUtils.independenceFact(v, w, z) +
                                "\tp = " +
                                nf.format(getTest().getPValue()));
                    }
                }
            }
        }

        this.numIndependenceTests++;
        return independent;
    }

    private void addEdge(final Graph graph, final Node w, final Node v) {
        if (!graph.containsNode(w)) {
            graph.addNode(w);
        }

        graph.addUndirectedEdge(v, w);
    }

    private Node getVariableForName(final String targetVariableName) {
        Node target = null;

        for (final Node V : this.variables) {
            if (V.getName().equals(targetVariableName)) {
                target = V;
                break;
            }
        }

        if (target == null) {
            throw new IllegalArgumentException(
                    "Target variable not in dataset: " + targetVariableName);
        }

        return target;
    }

    private void noteMaxAtDepth(final int depth, final int numAdjacents, final Node to) {
        if (depth < this.maxRemainingAtDepth.length &&
                numAdjacents > this.maxRemainingAtDepth[depth]) {
            this.maxRemainingAtDepth[depth] = numAdjacents;
            this.maxVariableAtDepth[depth] = to;
        }
    }

    private void orientUnshieldedTriples(final IKnowledge knowledge, final Graph graph,
                                         final IndependenceTest test, final int depth, List<Node> nodes) {
        this.logger.log("info", "Starting Collider Orientation:");

        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();
        this.ambiguousTriples = new HashSet<>();

        if (nodes == null) {
            nodes = graph.getNodes();
        }

        for (final Node y : nodes) {
            final List<Node> adjacentNodes = graph.getAdjacentNodes(y);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            final ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final Node x = adjacentNodes.get(combination[0]);
                final Node z = adjacentNodes.get(combination[1]);

                if (graph.isAdjacentTo(x, z)) {
                    continue;
                }

                this.allTriples.add(new Triple(x, y, z));

                final TripleType type = getTripleType(graph, x, y, z, test, depth);

                if (type == TripleType.COLLIDER) {
                    if (colliderAllowed(x, y, z, knowledge)) {
                        graph.setEndpoint(x, y, Endpoint.ARROW);
                        graph.setEndpoint(z, y, Endpoint.ARROW);
                        this.logger.log("tripleClassifications", "Collider oriented: " + Triple.pathString(graph, x, y, z));
                    }

                    this.colliderTriples.add(new Triple(x, y, z));
                } else if (type == TripleType.AMBIGUOUS) {
                    final Triple triple = new Triple(x, y, z);
                    this.ambiguousTriples.add(triple);
                    graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                    this.logger.log("tripleClassifications", "tripleClassifications: " + Triple.pathString(graph, x, y, z));
                } else {
                    this.noncolliderTriples.add(new Triple(x, y, z));
                    this.logger.log("tripleClassifications", "tripleClassifications: " + Triple.pathString(graph, x, y, z));
                }
            }
        }

        this.logger.log("info", "Finishing Collider Orientation.");
    }

    private TripleType getTripleType(final Graph graph, final Node x, final Node y, final Node z,
                                     final IndependenceTest test, final int depth) {
        boolean existsSepsetContainingY = false;
        boolean existsSepsetNotContainingY = false;

        Set<Node> __nodes = new HashSet<>(graph.getAdjacentNodes(x));
        __nodes.remove(z);

        List<Node> _nodes = new LinkedList<>(__nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = Integer.MAX_VALUE;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final List<Node> condSet = Mbfs.asList(choice, _nodes);

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
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final List<Node> condSet = Mbfs.asList(choice, _nodes);

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

    private boolean edgeForbidden(final Node x1, final Node x2) {
        return getKnowledge().isForbidden(x1.toString(), x2.toString()) &&
                getKnowledge().isForbidden(x2.toString(), x1.toString());
    }

    private boolean edgeRequired(final Node x1, final Node x2) {
        return getKnowledge().isRequired(x1.toString(), x2.toString()) ||
                getKnowledge().isRequired(x2.toString(), x1.toString());
    }

    /**
     * Removes from adjx any adjacencies that cannot be parents of x given the background knowledge.
     *
     * @param node    The node being discussed.
     * @param adjNode The list of adjacencies to <code>node</code>.
     * @return The revised list of nodes--i.e. the possible parents among adjx, according to knowledge.
     */
    private List<Node> possibleParents(final Node node, final List<Node> adjNode) {
        final List<Node> possibleParents = new LinkedList<>();
        final String _x = node.getName();

        for (final Node z : adjNode) {
            final String _z = z.getName();

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
     * @return True just in case z is a possible parent of x.
     */
    private boolean possibleParentOf(final String z, final String x, final IKnowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    private static List<Node> asList(final int[] indices, final List<Node> nodes) {
        final List<Node> list = new LinkedList<>();

        for (final int i : indices) {
            list.add(nodes.get(i));
        }

        return list;
    }

    private boolean colliderAllowed(final Node x, final Node y, final Node z, final IKnowledge knowledge) {
        return Mbfs.isArrowpointAllowed1(x, y, knowledge) &&
                Mbfs.isArrowpointAllowed1(z, y, knowledge);
    }

    private static boolean isArrowpointAllowed1(final Node from, final Node to,
                                                final IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }

        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    public void setVariables(final List<Node> variables) {
        this.variables = variables;
    }


    //==============================CLASSES==============================//

    private enum TripleType {
        COLLIDER, NONCOLLIDER, AMBIGUOUS
    }
}







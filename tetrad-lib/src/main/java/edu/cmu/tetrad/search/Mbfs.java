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
 * Searches for a pattern representing all of the Markov blankets for a given target T consistent with the given
 * independence information. This pattern may be used to generate the actual list of DAG's that might be Markov
 * blankets. Note that this code has been converted to be consistent with the CPC algorithm.
 *
 * @author Joseph Ramsey
 */
public final class Mbfs implements MbSearch, GraphSearch {

    /**
     * The independence test used to perform the search.
     */
    private IndependenceTest test;

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
     * The pattern output by the most recent search. This is saved in case the user wants to generate the list of MB
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
    private boolean aggressivelyPreventCycles = false;

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    //==============================CONSTRUCTORS==========================//

    /**
     * Constructs a new search.
     *
     * @param test  The source of conditional independence information for the search.
     * @param depth The maximum number of variables conditioned on for any
     */
    public Mbfs(IndependenceTest test, int depth) {
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
        return aggressivelyPreventCycles;
    }

    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    /**
     * Searches for the MB Pattern for the given target.
     *
     * @param targetName The name of the target variable.
     */
    public Graph search(String targetName) {
        if (targetName == null) {
            throw new IllegalArgumentException(
                    "Null target name not permitted");
        }

        this.target = getVariableForName(targetName);
        return search(target);
    }

    /**
     * Searches for the MB Pattern for the given target.
     *
     * @param target The target variable.
     */
    public Graph search(Node target) {
        long start = System.currentTimeMillis();
        this.numIndependenceTests = 0;
        this.allTriples = new HashSet<Triple>();
        this.ambiguousTriples = new HashSet<Triple>();
        this.colliderTriples = new HashSet<Triple>();
        this.noncolliderTriples = new HashSet<Triple>();

        if (target == null) {
            throw new IllegalArgumentException(
                    "Null target name not permitted");
        }

        this.target = target;

        logger.log("info", "Target = " + target);

        // Some statistics.
        this.maxRemainingAtDepth = new int[20];
        this.maxVariableAtDepth = new Node[20];
        Arrays.fill(maxRemainingAtDepth, -1);
        Arrays.fill(maxVariableAtDepth, null);

        logger.log("info", "target = " + getTarget());

        Graph graph = new EdgeListGraph();

        // Each time the addDepthZeroAssociates method is called for a node
        // v, v is added to this set, and edges to elements in this set are
        // not added to the graph subsequently. This is to handle the situation
        // of adding v1---v2 to the graph, removing it by conditioning on
        // nodes adjacent to v1, then re-adding it and not being able to
        // remove it by conditioning on nodes adjacent to v2. Once an edge
        // is removed, it should not be re-added to the graph.
        // jdramsey 8/6/04
        this.a = new HashSet<Node>();

        // Step 1. Get associates for the target.
        logger.log("info", "BEGINNING step 1 (prune target).");

        graph.addNode(getTarget());
        constructFan(getTarget(), graph);

        logger.log("graph", "After step 1 (prune target)" + graph);
        logger.log("graph", "After step 1 (prune target)" + graph);

        // Step 2. Get associates for each variable adjacent to the target,
        // removing edges based on those associates where possible. After this
        // step, adjacencies to the target are parents or children of the target.
        // Call this set PC.
        logger.log("info", "BEGINNING step 2 (prune PC).");

//        variables = graph.getNodes();

        for (Node v : graph.getAdjacentNodes(getTarget())) {
            constructFan(v, graph);

            // Optimization: For t---v---w, toss out w if <t, v, w> can't
            // be an unambiguous collider, judging from the side of t alone.
            // Look at adjacencies w of v. If w is not in A, and there is no
            // S in adj(t) containing v s.g. t _||_ v | S, then remove v.

            W:
            for (Node w : graph.getAdjacentNodes(v)) {
                if (a.contains(w)) {
                    continue;
                }

                List _a = new LinkedList<Node>(a);
                _a.retainAll(graph.getAdjacentNodes(w));
                if (_a.size() > 1) continue;

                List<Node> adjT = graph.getAdjacentNodes(getTarget());
                DepthChoiceGenerator cg = new DepthChoiceGenerator(
                        adjT.size(), depth);
                int[] choice;

                while ((choice = cg.next()) != null) {
                    List<Node> s = GraphUtils.asList(choice, adjT);
                    if (!s.contains(v)) continue;

                    if (independent(getTarget(), w, s)) {
                        graph.removeEdge(v, w);
                        continue W;
                    }
                }
            }
        }

        logger.log("graph", "After step 2 (prune PC)" + graph);

        // Step 3. Get associates for each node now two links away from the
        // target, removing edges based on those associates where possible.
        // After this step, adjacencies to adjacencies of the target are parents
        // or children of adjacencies to the target. Call this set PCPC.
        logger.log("info", "BEGINNING step 3 (prune PCPC).");

        for (Node v : graph.getAdjacentNodes(getTarget())) {
            for (Node w : graph.getAdjacentNodes(v)) {
                if (getA().contains(w)) {
                    continue;
                }

                constructFan(w, graph);
            }
        }

        logger.log("graph", "After step 3 (prune PCPC)" + graph);

        logger.log("info", "BEGINNING step 4 (PC Orient).");

        SearchGraphUtils.pcOrientbk(knowledge, graph, graph.getNodes());

        List<Node> _visited = new LinkedList<Node>(getA());
        orientUnshieldedTriples(knowledge, graph, getTest(), getDepth(), _visited);

        MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
        meekRules.setKnowledge(knowledge);
        meekRules.orientImplied(graph);

        logger.log("graph", "After step 4 (PC Orient)" + graph);

        logger.log("info", "BEGINNING step 5 (Trim graph to {T} U PC U " +
                "{Parents(Children(T))}).");

        MbUtils.trimToMbNodes(graph, getTarget(), false);

        logger.log("graph",
                "After step 5 (Trim graph to {T} U PC U {Parents(Children(T))})" +
                        graph);

        logger.log("info", "BEGINNING step 6 (Remove edges among P and P of C).");

        MbUtils.trimEdgesAmongParents(graph, getTarget());
        MbUtils.trimEdgesAmongParentsOfChildren(graph, getTarget());

        logger.log("graph", "After step 6 (Remove edges among P and P of C)" + graph);
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
     * Does a pattern search.
     */
    public Graph search() {
        long start = System.currentTimeMillis();
        this.numIndependenceTests = 0;
        this.allTriples = new HashSet<Triple>();
        this.ambiguousTriples = new HashSet<Triple>();
        this.colliderTriples = new HashSet<Triple>();
        this.noncolliderTriples = new HashSet<Triple>();

        // Some statistics.
        this.maxRemainingAtDepth = new int[20];
        this.maxVariableAtDepth = new Node[20];
        Arrays.fill(maxRemainingAtDepth, -1);
        Arrays.fill(maxVariableAtDepth, null);

//        logger.info("target = " + getTarget());

        Graph graph = new EdgeListGraph();

        // Each time the addDepthZeroAssociates method is called for a node
        // v, v is added to this set, and edges to elements in this set are
        // not added to the graph subsequently. This is to handle the situation
        // of adding v1---v2 to the graph, removing it by conditioning on
        // nodes adjacent to v1, then re-adding it and not being able to
        // remove it by conditioning on nodes adjacent to v2. Once an edge
        // is removed, it should not be re-added to the graph.
        // jdramsey 8/6/04
        this.a = new HashSet<Node>();
        this.variables = test.getVariables();

        Node target = variables.get(0);
        graph.addNode(target);

        for (Node node : variables) {
            if (!graph.containsNode(node)) {
                graph.addNode(node);
            }

            constructFan(node, graph);
        }

        for (Node node : variables) {
            if (!graph.containsNode(node)) {
                graph.addNode(node);
            }
        }

        orientUnshieldedTriples(knowledge, graph, getTest(), getDepth(), graph.getNodes());

        MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
        meekRules.setKnowledge(knowledge);
        meekRules.orientImplied(graph);

        this.logger.log("graph", "\nReturning this graph: " + graph);

        return graph;
    }

    /**
     * @return the set of triples identified as ambiguous by the CPC algorithm during the most recent search.
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<Triple>(ambiguousTriples);
    }

    /**
     * @return the set of triples identified as colliders by the CPC algorithm during the most recent search.
     */
    public Set<Triple> getColliderTriples() {
        return colliderTriples;
    }

    /**
     * @return the set of triples identified as noncolliders by the CPC algorithm during the most recent search.
     */
    public Set<Triple> getNoncolliderTriples() {
        return noncolliderTriples;
    }

    /**
     * @return the number of independence tests performed during the most recent search.
     */
    public int getNumIndependenceTests() {
        return numIndependenceTests;
    }

    /**
     * @return the target of the most recent search.
     */
    public Node getTarget() {
        return target;
    }

    /**
     * @return the elapsed time of the most recent search.
     */
    public long getElapsedTime() {
        return elapsedTime;
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
        return trueMb;
    }

    /**
     * Sets the true MB; should be done before running the search to get on-the-fly comparisons.
     */
    public void setTrueMb(Dag trueMb) {
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
        if (depth < 0) throw new IllegalArgumentException("Depth must be >= 0: " + depth);
        this.depth = depth;
    }

    /**
     * @return Ibid.
     */
    public Graph resultGraph() {
        return resultGraph;
    }

    /**
     * @return just the Markov blanket (not the Markov blanket DAG).
     */
    public List<Node> findMb(String targetName) {
        Graph graph = search(targetName);
        List<Node> nodes = graph.getNodes();
        nodes.remove(target);
        return nodes;
    }

    /**
     * @return Ibid.
     */
    public IndependenceTest getTest() {
        return test;
    }

    /**
     * @return Ibid.
     */
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets knowledge, to which the algorithm is in fact sensitive.
     *
     * @param knowledge See the Knowledge class.
     */
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public Graph getGraph() {
        return graph;
    }

    //================================PRIVATE METHODS====================//

    private Set<Node> getA() {
        return a;
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

    private void addAllowableAssociates(Node v, Graph graph) {
        getA().add(v);
        int numAssociated = 0;

        for (Node w : variables) {
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

    private void prune(Node node, Graph graph) {
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
    private void prune(Node node, Graph graph, int depth) {
        logger.log("pruning", "Trying to remove edges adjacent to node " + node +
                ", depth = " + depth + ".");

        // Otherwise, try removing all other edges adjacent node node. Return
        // true if more edges could be removed at the next depth.
        List<Node> a = new LinkedList<Node>(graph.getAdjacentNodes(node));

        NEXT_EDGE:
        for (Node y : a) {
            List<Node> adjNode =
                    new LinkedList<Node>(graph.getAdjacentNodes(node));
            adjNode.remove(y);
            adjNode = possibleParents(node, adjNode);

            if (adjNode.size() < depth) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjNode.size(), depth);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> condSet = GraphUtils.asList(choice, adjNode);

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

        int numAdjacents = graph.getAdjacentNodes(node).size();
        noteMaxAtDepth(depth, numAdjacents, node);
    }

    private void finishUp(long start, Graph graph) {
        long stop = System.currentTimeMillis();
        this.elapsedTime = stop - start;
        double seconds = this.elapsedTime / 1000d;

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        logger.log("info", "MB fan search took " + nf.format(seconds) + " seconds.");
        logger.log("info", "Number of independence tests performed = " +
                getNumIndependenceTests());

        this.resultGraph = graph;
    }

    private boolean independent(Node v, Node w, List<Node> z) {
//        if (test.splitDetermines(z, v, w)) {
//            return false;
//        }


        boolean independent = getTest().isIndependent(v, w, z);

        if (independent) {
            if (getTrueMb() != null) {
                Node node1 = getTrueMb().getNode(v.getName());
                Node node2 = getTrueMb().getNode(w.getName());

                if (node1 != null && node2 != null) {
                    Edge edge = getTrueMb().getEdge(node1, node2);

                    if (edge != null) {
                        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
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

    private void addEdge(Graph graph, Node w, Node v) {
        if (!graph.containsNode(w)) {
            graph.addNode(w);
        }

        graph.addUndirectedEdge(v, w);
    }

    private Node getVariableForName(String targetVariableName) {
        Node target = null;

        for (Node V : variables) {
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

    private void noteMaxAtDepth(int depth, int numAdjacents, Node to) {
        if (depth < maxRemainingAtDepth.length &&
                numAdjacents > maxRemainingAtDepth[depth]) {
            maxRemainingAtDepth[depth] = numAdjacents;
            maxVariableAtDepth[depth] = to;
        }
    }

    private void orientUnshieldedTriples(IKnowledge knowledge, Graph graph,
                                         IndependenceTest test, int depth, List<Node> nodes) {
        logger.log("info", "Starting Collider Orientation:");

        colliderTriples = new HashSet<Triple>();
        noncolliderTriples = new HashSet<Triple>();
        ambiguousTriples = new HashSet<Triple>();

        if (nodes == null) {
            nodes = graph.getNodes();
        }

        for (Node y : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(y);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (graph.isAdjacentTo(x, z)) {
                    continue;
                }

                allTriples.add(new Triple(x, y, z));

                TripleType type = getTripleType(graph, x, y, z, test, depth);

                if (type == TripleType.COLLIDER) {
                    if (colliderAllowed(x, y, z, knowledge)) {
                        graph.setEndpoint(x, y, Endpoint.ARROW);
                        graph.setEndpoint(z, y, Endpoint.ARROW);
                        logger.log("tripleClassifications", "Collider oriented: " + Triple.pathString(graph, x, y, z));
                    }

                    colliderTriples.add(new Triple(x, y, z));
                } else if (type == TripleType.AMBIGUOUS) {
                    Triple triple = new Triple(x, y, z);
                    ambiguousTriples.add(triple);
                    graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                    logger.log("tripleClassifications", "tripleClassifications: " + Triple.pathString(graph, x, y, z));
                } else {
                    noncolliderTriples.add(new Triple(x, y, z));
                    logger.log("tripleClassifications", "tripleClassifications: " + Triple.pathString(graph, x, y, z));
                }
            }
        }

        logger.log("info", "Finishing Collider Orientation.");
    }

    private TripleType getTripleType(Graph graph, Node x, Node y, Node z,
                                     IndependenceTest test, int depth) {
        boolean existsSepsetContainingY = false;
        boolean existsSepsetNotContainingY = false;

        Set<Node> __nodes = new HashSet<Node>(graph.getAdjacentNodes(x));
        __nodes.remove(z);

        List<Node> _nodes = new LinkedList<Node>(__nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = Integer.MAX_VALUE;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> condSet = asList(choice, _nodes);

                if (independent(x, z, condSet)) {
                    if (condSet.contains(y)) {
                        existsSepsetContainingY = true;
                    } else {
                        existsSepsetNotContainingY = true;
                    }
                }
            }
        }

        __nodes = new HashSet<Node>(graph.getAdjacentNodes(z));
        __nodes.remove(x);

        _nodes = new LinkedList<Node>(__nodes);

        _depth = depth;
        if (_depth == -1) {
            _depth = Integer.MAX_VALUE;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> condSet = asList(choice, _nodes);

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

    private boolean edgeForbidden(Node x1, Node x2) {
        return getKnowledge().isForbidden(x1.toString(), x2.toString()) &&
                getKnowledge().isForbidden(x2.toString(), x1.toString());
    }

    private boolean edgeRequired(Node x1, Node x2) {
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
    private List<Node> possibleParents(Node node, List<Node> adjNode) {
        List<Node> possibleParents = new LinkedList<Node>();
        String _x = node.getName();

        for (Node z : adjNode) {
            String _z = z.getName();

            if (possibleParentOf(_z, _x, knowledge)) {
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
    private boolean possibleParentOf(String z, String x, IKnowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    private static List<Node> asList(int[] indices, List<Node> nodes) {
        List<Node> list = new LinkedList<Node>();

        for (int i : indices) {
            list.add(nodes.get(i));
        }

        return list;
    }

    private boolean colliderAllowed(Node x, Node y, Node z, IKnowledge knowledge) {
        return isArrowpointAllowed1(x, y, knowledge) &&
                isArrowpointAllowed1(z, y, knowledge);
    }

    private static boolean isArrowpointAllowed1(Node from, Node to,
                                                IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }

        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    public void setVariables(List<Node> variables) {
        this.variables = variables;
    }


    //==============================CLASSES==============================//

    private enum TripleType {
        COLLIDER, NONCOLLIDER, AMBIGUOUS
    }
}







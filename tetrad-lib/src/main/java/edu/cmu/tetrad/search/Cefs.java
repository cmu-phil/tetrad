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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
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
public final class Cefs {

    /**
     * The independence test used to perform the search.
     */
    private final IndependenceTest test;

    /**
     * The list of variables being searched over. Must contain the target.
     */
    private final List<Node> variables;

    /**
     * The target variable.
     */
    private Node target;

    /**
     * The depth to which independence tests should be performed--i.e. the maximum number of conditioning variables for
     * any independence test.
     */
    private final int depth;

    /**
     * The CPDAG output by the most recent search. This is saved in case the user wants to generate the list of MB
     * DAGs.
     */
    private Graph resultGraph;

    /**
     * A count of the number of independence tests performed in the course of the most recent search.
     */
    private long numIndependenceTests;

    /**
     * Information to help understand what part of the search is taking the most time.
     */
    private int[] maxRemainingAtDepth;

    /**
     * The set of nodes that edges should not be drawn to in the addDepthZeroAssociates method.
     */
    private Set<Node> visited;

    /**
     * Elapsed time for the last run of the algorithm.
     */
    private long elapsedTime;

    /**
     * The true graph, if known. If this is provided, notes will be printed out for edges removed that are in the true
     * Markov blanket.
     */
    private final Dag trueMb = null;

    /**
     * Knowledge.
     */
    private IKnowledge knowledge = new Knowledge2();

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
     * @param depth The maximum number of variables conditioned on for any independence test in the search.
     */
    public Cefs(IndependenceTest test, int depth) {
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

    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }


    /**
     * Searches for the MB CPDAG for the given target.
     *
     * @param targetName The name of the target variable.
     */
    public Graph search(String targetName) {
        long start = System.currentTimeMillis();
        this.numIndependenceTests = 0;
        this.ambiguousTriples = new HashSet<>();
        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();

        if (targetName == null) {
            throw new IllegalArgumentException(
                    "Null target name not permitted");
        }

        this.target = getVariableForName(targetName);

        // Some statistics.
        this.maxRemainingAtDepth = new int[20];
        Arrays.fill(this.maxRemainingAtDepth, -1);

        TetradLogger.getInstance().log("info", "target = " + getTarget());

        Graph graph = new EdgeListGraph();

        // Each time the addDepthZeroAssociates method is called for a node
        // v, v is added to this set, and edges to elements in this set are
        // not added to the graph subsequently. This is to handle the situation
        // of adding v1---v2 to the graph, removing it by conditioning on
        // nodes adjacent to v1, then re-adding it and not being able to
        // remove it by conditioning on nodes adjacent to v2. Once an edge
        // is removed, it should not be re-added to the graph.
        // jdramsey 8/6/04
        this.visited = new HashSet<>();

        // Step 1. Get associates for the target.
        TetradLogger.getInstance().log("info", "BEGINNING step 1 (prune target).");

        graph.addNode(getTarget());
        constructFan(getTarget(), graph);

        TetradLogger.getInstance().log("graph", "After step 1 (prune target)" + graph);

        // Step 2. Get associates for each variable adjacent to the target,
        // removing edges based on those associates where possible. After this
        // step, adjacencies to the target are parents or children of the target.
        // Call this set PC.
        TetradLogger.getInstance().log("info", "BEGINNING step 2 (prune PC).");

        for (Node v : graph.getAdjacentNodes(getTarget())) {
            constructFan(v, graph);
        }

        TetradLogger.getInstance().log("graph", "After step 2 (prune PC)" + graph);

        TetradLogger.getInstance().log("info", "BEGINNING step 4 (PC Orient).");

        SearchGraphUtils.pcOrientbk(this.knowledge, graph, graph.getNodes());

        List<Node> _visited = new LinkedList<>(getVisited());
        orientUnshieldedTriples(this.knowledge, graph, getTest(), getDepth(), _visited);

        MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
        meekRules.setKnowledge(this.knowledge);
        meekRules.orientImplied(graph);

        TetradLogger.getInstance().log("graph", "After step 4 (PC Orient)" + graph);

        MbUtils.trimToAdjacents(graph, this.target);

        TetradLogger.getInstance().log("graph", "After step 6 (Remove edges among P and P of C)" + graph);

        finishUp(start, graph);

        this.logger.log("graph", "\nReturning this graph: " + graph);

        this.graph = graph;
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
    public long getNumIndependenceTests() {
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
    public double getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * If a true MB was set before running the search, this returns it.
     */
    public Dag getTrueMb() {
        return this.trueMb;
    }

    /**
     * @return the depth of the most recent search.
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * @return the result graph of the most recent search.
     */
    public Graph resultGraph() {
        return this.resultGraph;
    }

    /**
     * @return just the Markov blanket (not the Markov blanket DAG).
     */
    public List<Node> findMb(String targetName) {
        Graph graph = search(targetName);
        List<Node> nodes = graph.getNodes();
        nodes.remove(this.target);
        return nodes;
    }

    /**
     * @return the independence test set in the constructor.
     */
    public IndependenceTest getTest() {
        return this.test;
    }

    /**
     * @return the most recently set Knowledge object.
     */
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets knowledge, to which the algorithm is in fact sensitive.
     */
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public Graph getGraph() {
        return this.graph;
    }

    //================================PRIVATE METHODS====================//

    private Set<Node> getVisited() {
        return this.visited;
    }

    /**
     * Adds associates of the target and prunes edges using subsets of adjacencies to the target.
     */
    private void constructFan(Node target, Graph graph) {
        addAllowableAssociates(target, graph);
        prune(target, graph);
    }

    private void addAllowableAssociates(Node v, Graph graph) {
        this.getVisited().add(v);
        int numAssociated = 0;

        for (Node w : this.variables) {
            if (getVisited().contains(w)) {
                continue;
            }

            if (graph.containsNode(w) && graph.isAdjacentTo(v, w)) {
                continue;
            }

            if (!independent(v, w, new LinkedList<>()) && !edgeForbidden(v, w)) {
                addEdge(graph, w, v);
                numAssociated++;
            }
        }

        noteMaxAtDepth(0, numAssociated);
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
     */
    private void prune(Node node, Graph graph, int depth) {
        TetradLogger.getInstance().log("pruning", "Trying to remove edges adjacent to node " + node +
                ", depth = " + depth + ".");

        // Otherwise, try removing all other edges adjacent node node. Return
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
        noteMaxAtDepth(depth, numAdjacents);
    }

    private void finishUp(long start, Graph graph) {
        long stop = System.currentTimeMillis();
        this.elapsedTime = stop - start;
        double seconds = this.elapsedTime / 1000d;

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        TetradLogger.getInstance().log("info", "MB fan search took " + nf.format(seconds) + " seconds.");
        TetradLogger.getInstance().log("info", "Number of independence tests performed = " +
                getNumIndependenceTests());

        this.resultGraph = graph;
    }

    private boolean independent(Node v, Node w, List<Node> z) {
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

        for (Node V : this.variables) {
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

    private void noteMaxAtDepth(int depth, int numAdjacents) {
        if (depth < this.maxRemainingAtDepth.length &&
                numAdjacents > this.maxRemainingAtDepth[depth]) {
            this.maxRemainingAtDepth[depth] = numAdjacents;
        }
    }

    private void orientUnshieldedTriples(IKnowledge knowledge, Graph graph,
                                         IndependenceTest test, int depth, List<Node> nodes) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();
        this.ambiguousTriples = new HashSet<>();

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

                TripleType type = getTripleType(graph, x, y, z, test, depth);

                if (type == TripleType.COLLIDER) {
                    if (colliderAllowed(x, y, z, knowledge)) {
                        graph.setEndpoint(x, y, Endpoint.ARROW);
                        graph.setEndpoint(z, y, Endpoint.ARROW);
                        this.logger.log("tripleClassifications", "Collider oriented: " + Triple.pathString(graph, x, y, z));
                    }

                    this.colliderTriples.add(new Triple(x, y, z));
                } else if (type == TripleType.AMBIGUOUS) {
                    Triple triple = new Triple(x, y, z);
                    this.ambiguousTriples.add(triple);
                    graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                    this.logger.log("tripleClassifications", "Ambiguous triple oriented: " + Triple.pathString(graph, x, y, z));
                } else {
                    this.noncolliderTriples.add(new Triple(x, y, z));
                    this.logger.log("tripleClassifications", "Noncollider oriented: " + Triple.pathString(graph, x, y, z));
                }
            }
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    private TripleType getTripleType(Graph graph, Node x, Node y, Node z,
                                     IndependenceTest test, int depth) {
        boolean existsSepsetContainingY = false;
        boolean existsSepsetNotContainingY = false;

        Set<Node> __nodes = new HashSet<>(graph.getAdjacentNodes(x));
        __nodes.remove(z);

        List<Node> _nodes = new LinkedList<>(__nodes);
//        TetradLogger.getInstance().log("details", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = Integer.MAX_VALUE;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> condSet = Cefs.asList(choice, _nodes);

                if (test.isIndependent(x, z, condSet)) {
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
//        TetradLogger.getInstance().log("details", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        _depth = depth;
        if (_depth == -1) {
            _depth = Integer.MAX_VALUE;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> condSet = Cefs.asList(choice, _nodes);

                if (test.isIndependent(x, z, condSet)) {
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
     * Removes from adjx any that cannot be parents of x given the background knowledge.
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
     * @return true just in case z is a possible parent of x, in the sense that edges are not forbidden from z to x, and
     * edges are not required from either x to z, according to background knowledge.
     */
    private boolean possibleParentOf(String z, String x, IKnowledge knowledge) {
        return !knowledge.isForbidden(z, x) && !knowledge.isRequired(x, z);
    }

    private static List<Node> asList(int[] indices, List<Node> nodes) {
        List<Node> list = new LinkedList<>();

        for (int i : indices) {
            list.add(nodes.get(i));
        }

        return list;
    }

    private boolean colliderAllowed(Node x, Node y, Node z, IKnowledge knowledge) {
        return Cefs.isArrowpointAllowed1(x, y, knowledge) &&
                Cefs.isArrowpointAllowed1(z, y, knowledge);
    }

    private static boolean isArrowpointAllowed1(Node from, Node to,
                                                IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }

        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    //==============================CLASSES==============================//

    private enum TripleType {
        COLLIDER, NONCOLLIDER, AMBIGUOUS
    }
}







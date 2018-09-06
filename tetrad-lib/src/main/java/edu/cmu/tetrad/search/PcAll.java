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
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Implements a convervative version of PC, in which the Markov condition is assumed but faithfulness is tested
 * locally.
 *
 * @author Joseph Ramsey (this version).
 */
public final class PcAll implements GraphSearch {

    public void setUseHeuristic(boolean useHeuristic) {
        this.useHeuristic = useHeuristic;
    }

    public int getMaxPathLength() {
        return maxPathLength;
    }

    public void setMaxPathLength(int maxPathLength) {
        this.maxPathLength = maxPathLength;
    }

    public void setFasType(FasType fasType) {
        this.fasType = fasType;
    }

    public void setConcurrent(Concurrent concurrent) {
        this.concurrent = concurrent;
    }

    public enum FasType {REGULAR, STABLE}

    public enum Concurrent {YES, NO};

    public enum ColliderDiscovery {FAS_SEPSETS, CONSERVATIVE, MAX_P}

    public enum ConflictRule {PRIORITY, BIDIRECTED, OVERWRITE}

    /**
     * The independence test used for the PC search.
     */
    private IndependenceTest independenceTest;

    /**
     * Forbidden and required edges for the search.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * The maximum number of nodes conditioned on in the search.
     */
    private int depth = 1000;

    private Graph graph;

    /**
     * Elapsed time of last search.
     */
    private long elapsedTime;

    /**
     * Set of unshielded colliderDiscovery from the triple orientation step.
     */
    private Set<Triple> colliderTriples;

    /**
     * Set of unshielded noncolliders from the triple orientation step.
     */
    private Set<Triple> noncolliderTriples;

    private Graph initialGraph;

    /**
     * Set of ambiguous unshielded triples.
     */
    private Set<Triple> ambiguousTriples;

    /**
     * True if cycles are to be aggressively prevented. May be expensive for large graphs (but also useful for large
     * graphs).
     */
    private boolean aggressivelyPreventCycles = false;

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * The sepsets.
     */
    private SepsetMap sepsets;

    /**
     * Whether verbose output about independencies is output.
     */

    private boolean verbose = false;

    private boolean useHeuristic = false;
    private int maxPathLength;


    private FasType fasType = FasType.REGULAR;
    private Concurrent concurrent = Concurrent.YES;
    private ColliderDiscovery colliderDiscovery = ColliderDiscovery.FAS_SEPSETS;
    private ConflictRule conflictRule = ConflictRule.OVERWRITE;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a CPC algorithm that uses the given independence test as oracle. This does not make a copy of the
     * independence test, for fear of duplicating the data set!
     */
    public PcAll(IndependenceTest independenceTest, Graph initialGraph) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.initialGraph = initialGraph;
    }

    //==============================PUBLIC METHODS========================//

    /**
     * @return true just in case edges will not be added if they would create cycles.
     */
    public boolean isAggressivelyPreventCycles() {
        return this.aggressivelyPreventCycles;
    }

    /**
     * Sets to true just in case edges will not be added if they would create cycles.
     */
    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    public void setColliderDiscovery(ColliderDiscovery colliderDiscovery) {
        this.colliderDiscovery = colliderDiscovery;
    }

    public void setConflictRule(ConflictRule conflictRule) {
        this.conflictRule = conflictRule;
    }

    /**
     * Sets the maximum number of variables conditioned on in any conditional independence test. If set to -1, the value
     * of 1000 will be used. May not be set to Integer.MAX_VALUE, due to a Java bug on multi-core systems.
     */
    public final void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException("Depth must be -1 or >= 0: " + depth);
        }

        if (depth == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Depth must not be Integer.MAX_VALUE, " +
                    "due to a known bug.");
        }

        this.depth = depth;
    }

    /**
     * @return the elapsed time of search in milliseconds, after <code>search()</code> has been run.
     */
    public final long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * @return the knowledge specification used in the search. Non-null.
     */
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the knowledge specification used in the search. Non-null.
     */
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * @return the independence test used in the search, set in the constructor. This is not returning a copy, for fear
     * of duplicating the data set!
     */
    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    /**
     * @return the depth of the search--that is, the maximum number of variables conditioned on in any conditional
     * independence test.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * @return the set of ambiguous triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(ambiguousTriples);
    }

    /**
     * @return the set of collider triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getColliderTriples() {
        return new HashSet<>(colliderTriples);
    }

    /**
     * @return the set of noncollider triples found during the most recent run of the algorithm. Non-null after a call
     * to <code>search()</code>.
     */
    public Set<Triple> getNoncolliderTriples() {
        return new HashSet<>(noncolliderTriples);
    }

    public Set<Edge> getAdjacencies() {
        return new HashSet<>(graph.getEdges());
    }

    public Set<Edge> getNonadjacencies() {
        Graph complete = GraphUtils.completeGraph(graph);
        Set<Edge> nonAdjacencies = complete.getEdges();
        Graph undirected = GraphUtils.undirectedGraph(graph);
        nonAdjacencies.removeAll(undirected.getEdges());
        return new HashSet<>(nonAdjacencies);
    }

    public Graph search() {
        return search(getIndependenceTest().getVariables());
    }

    public Graph search(List<Node> nodes) {
        this.logger.log("info", "Starting CPC algorithm");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");
        this.ambiguousTriples = new HashSet<>();
        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();

        independenceTest.setVerbose(verbose);

        long startTime = System.currentTimeMillis();

        if (getIndependenceTest() == null) {
            throw new NullPointerException();
        }

        List<Node> allNodes = getIndependenceTest().getVariables();
        if (!allNodes.containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                    "be in the domain of the independence test provided.");
        }

        IFas fas;

        if (fasType == FasType.REGULAR) {
            if (concurrent == Concurrent.NO) {
                fas = new Fas(initialGraph, getIndependenceTest());
            } else {
                fas = new FasConcurrent(initialGraph, getIndependenceTest());
                ((FasConcurrent) fas).setStable(false);
            }
        } else {
            if (concurrent == Concurrent.NO) {
                fas = new FasStable(initialGraph, getIndependenceTest());
            } else {
                fas = new FasConcurrent(initialGraph, getIndependenceTest());
                ((FasConcurrent) fas).setStable(true);
            }
        }

        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(verbose);

        // Note that we are ignoring the sepset map returned by this method
        // on purpose; it is not used in this search.
        graph = fas.search();
        sepsets = fas.getSepsets();

        SearchGraphUtils.pcOrientbk(knowledge, graph, nodes);

        if (colliderDiscovery == ColliderDiscovery.FAS_SEPSETS) {
            orientCollidersUsingSepsets(this.sepsets, knowledge, graph, verbose, conflictRule);
        } else if (colliderDiscovery == ColliderDiscovery.MAX_P) {
            if (verbose) {
                System.out.println("MaxP orientation...");
            }

            final OrientCollidersMaxP orientCollidersMaxP = new OrientCollidersMaxP(independenceTest);
            orientCollidersMaxP.setConflictRule(conflictRule);
            orientCollidersMaxP.setUseHeuristic(useHeuristic);
            orientCollidersMaxP.setMaxPathLength(maxPathLength);
            orientCollidersMaxP.setDepth(depth);
            orientCollidersMaxP.orient(graph);
        } else if (colliderDiscovery == ColliderDiscovery.CONSERVATIVE) {
            if (verbose) {
                System.out.println("CPC orientation...");
            }

            orientUnshieldedTriplesConservative(knowledge);

            //            orientUnshieldedTriplesConcurrent(knowledge, getIndependenceTest(), getMaxIndegree());
        }

        graph = GraphUtils.replaceNodes(graph, nodes);

        MeekRules meekRules = new MeekRules();
//        meekRules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
        meekRules.setKnowledge(knowledge);
        meekRules.orientImplied(graph);

        // Remove ambiguities whose status have been determined.
        Set<Triple> ambiguities = graph.getAmbiguousTriples();

        for (Triple triple : new HashSet<>(ambiguities)) {
            final Node x = triple.getX();
            final Node y = triple.getY();
            final Node z = triple.getZ();

            if (!graph.isAdjacentTo(x, y) || !graph.isAdjacentTo(y, x)) {
                graph.removeAmbiguousTriple(x, y, z);
            }

            if (graph.isDefCollider(x, y, z)) {
                graph.removeAmbiguousTriple(x, y, z);
            }

            if (graph.getEdge(x, y).pointsTowards(x) || graph.getEdge(y, z).pointsTowards(z)) {
                graph.removeAmbiguousTriple(x, y, z);
            }
        }

        TetradLogger.getInstance().log("graph", "\nReturning this graph: " + graph);

        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - startTime;

        TetradLogger.getInstance().log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        TetradLogger.getInstance().log("info", "Finishing CPC algorithm.");

        logTriples();

        TetradLogger.getInstance().flush();
        return graph;
    }

    //==========================PRIVATE METHODS===========================//

    private void logTriples() {
        TetradLogger.getInstance().log("info", "\nCollider triples:");

        for (Triple triple : colliderTriples) {
            TetradLogger.getInstance().log("info", "Collider: " + triple);
        }

        TetradLogger.getInstance().log("info", "\nNoncollider triples:");

        for (Triple triple : noncolliderTriples) {
            TetradLogger.getInstance().log("info", "Noncollider: " + triple);
        }

        TetradLogger.getInstance().log("info", "\nAmbiguous triples (i.e. list of triples for which " +
                "\nthere is ambiguous data about whether they are colliderDiscovery or not):");

        for (Triple triple : getAmbiguousTriples()) {
            TetradLogger.getInstance().log("info", "Ambiguous: " + triple);
        }
    }

    private void orientUnshieldedTriplesConservative(IKnowledge knowledge) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        colliderTriples = new HashSet<>();
        noncolliderTriples = new HashSet<>();
        ambiguousTriples = new HashSet<>();
        List<Node> nodes = graph.getNodes();

        for (Node y : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(y);

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

                if (this.graph.isAdjacentTo(x, z)) {
                    continue;
                }

                List<List<Node>> sepsetsxz = getSepsets(x, z, graph);

                if (isColliderSepset(y, sepsetsxz)) {
                    if (colliderAllowed(x, y, z, knowledge)) {
                        orientCollider(x, y, z, conflictRule, graph);
                    }

                    colliderTriples.add(new Triple(x, y, z));
                } else if (isNoncolliderSepset(y, sepsetsxz)) {
                    noncolliderTriples.add(new Triple(x, y, z));
                } else {
                    Triple triple = new Triple(x, y, z);
                    ambiguousTriples.add(triple);
                    graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                }
            }
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    private static void orientCollider(Node x, Node y, Node z, ConflictRule conflictRule, Graph graph) {
        if (conflictRule == ConflictRule.PRIORITY) {
            if (!(graph.getEndpoint(y, x) == Endpoint.ARROW || graph.getEndpoint(y, z) == Endpoint.ARROW)) {
                graph.removeEdge(x, y);
                graph.removeEdge(z, y);
                graph.addDirectedEdge(x, y);
                graph.addDirectedEdge(z, y);
            }
        } else if (conflictRule == ConflictRule.BIDIRECTED) {
            graph.setEndpoint(x, y, Endpoint.ARROW);
            graph.setEndpoint(z, y, Endpoint.ARROW);
        } else if (conflictRule == ConflictRule.OVERWRITE) {
            graph.removeEdge(x, y);
            graph.removeEdge(z, y);
            graph.addDirectedEdge(x, y);
            graph.addDirectedEdge(z, y);
        }

        TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
    }

    private List<List<Node>> getSepsets(Node i, Node k, Graph g) {
        List<Node> adji = g.getAdjacentNodes(i);
        List<Node> adjk = g.getAdjacentNodes(k);
        List<List<Node>> sepsets = new ArrayList<>();

        for (int d = 0; d <= Math.max(adji.size(), adjk.size()); d++) {
            if (adji.size() >= 2 && d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    List<Node> v = GraphUtils.asList(choice, adji);
                    if (getIndependenceTest().isIndependent(i, k, v)) sepsets.add(v);
                }
            }

            if (adjk.size() >= 2 && d <= adjk.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    List<Node> v = GraphUtils.asList(choice, adjk);
                    if (getIndependenceTest().isIndependent(i, k, v)) sepsets.add(v);
                }
            }
        }

        return sepsets;
    }

    private boolean isColliderSepset(Node j, List<List<Node>> sepsets) {
        if (sepsets.isEmpty()) return false;

        for (List<Node> sepset : sepsets) {
            if (sepset.contains(j)) return false;
        }

        return true;
    }

    private boolean isNoncolliderSepset(Node j, List<List<Node>> sepsets) {
        if (sepsets.isEmpty()) return false;

        for (List<Node> sepset : sepsets) {
            if (!sepset.contains(j)) return false;
        }

        return true;
    }

    private boolean colliderAllowed(Node x, Node y, Node z, IKnowledge knowledge) {
        return PcAll.isArrowpointAllowed1(x, y, knowledge) &&
                PcAll.isArrowpointAllowed1(z, y, knowledge);
    }

    public static boolean isArrowpointAllowed1(Node from, Node to,
                                               IKnowledge knowledge) {
        return knowledge == null || !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    public SepsetMap getSepsets() {
        return sepsets;
    }

    /**
     * The graph that's constructed during the search.
     */
    public Graph getGraph() {
        return graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public Graph getInitialGraph() {
        return initialGraph;
    }

    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    /**
     * Step C of PC; orients colliders using specified sepset. That is, orients x *-* y *-* z as x *-> y <-* z just in
     * case y is in Sepset({x, z}).
     */
    private void orientCollidersUsingSepsets(SepsetMap set, IKnowledge knowledge, Graph graph, boolean verbose,
                                             ConflictRule conflictRule) {
        if (verbose) {
            System.out.println("FAS Sepset orientation...");
        }

        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");

        List<Node> nodes = graph.getNodes();
        Map<Triple, Double> scoredTriples = new HashMap<>();

        for (Node b : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                List<Node> sepset = set.get(a, c);
//
                List<Node> s2 = new ArrayList<>(sepset);
                if (!s2.contains(b)) s2.add(b);
//
                if (!sepset.contains(b) && isArrowpointAllowed(a, b, knowledge) && isArrowpointAllowed(c, b, knowledge)) {
//                    independenceTest.isIndependent(a, c, sepset);
//                    double p = independenceTest.getPValue();
//
//                    independenceTest.isIndependent(a, c, s2);
//                    double p2 = independenceTest.getPValue();
//                    if (!(p > independenceTest.getAlpha() && p2 < .15)) continue;

//                    scoredTriples.put(new Triple(a, b, c), p);

                    orientCollider(a, b, c, conflictRule, graph);

                    if (verbose) {
                        System.out.println("Collider orientation <" + a + ", " + b + ", " + c + "> sepset = " + sepset);
                    }

                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(a, b, c, sepset));
                }
            }
        }

//        List<Triple> tripleList = new ArrayList<>(scoredTriples.keySet());
//        tripleList.sort((o1, o2) -> Double.compare(scoredTriples.get(o2), scoredTriples.get(o1)));
//
//        for (Triple triple : tripleList) {
//            final Node x = triple.getX();
//            final Node y = triple.getY();
//            final Node z = triple.getZ();
//            if (!(graph.getEndpoint(y, x) == Endpoint.ARROW || graph.getEndpoint(y, z) == Endpoint.ARROW)) {
//                graph.removeEdge(x, y);
//                graph.removeEdge(z, y);
//                graph.addDirectedEdge(x, y);
//                graph.addDirectedEdge(z, y);
//            }
//        }
    }

    /**
     * Checks if an arrowpoint is allowed by background knowledge.
     */
    public static boolean isArrowpointAllowed(Object from, Object to,
                                              IKnowledge knowledge) {
        if (knowledge == null) {
            return true;

        }
        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }
}


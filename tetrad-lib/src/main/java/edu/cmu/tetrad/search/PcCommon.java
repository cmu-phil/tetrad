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
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Common implementation of variaous PC-like algorithms, with options for
 * collider discovery type, FAS type, and conflict rule.
 *
 * @author josephramsey
 */
public final class PcCommon implements GraphSearch {
    public enum FasType {REGULAR, STABLE}

    public enum ColliderDiscovery {FAS_SEPSETS, CONSERVATIVE, MAX_P}

    public enum ConflictRule {PRIORITY, BIDIRECTED, OVERWRITE}

    /**
     * The independence test used for the PC search.
     */
    private final IndependenceTest independenceTest;
    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
    private int heuristic;
    /**
     * Forbidden and required edges for the search.
     */
    private Knowledge knowledge = new Knowledge();
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
    /**
     * Set of ambiguous unshielded triples.
     */
    private Set<Triple> ambiguousTriples;
    /**
     * True if cycles are to be aggressively prevented. May be expensive for large graphs (but also useful for large
     * graphs).
     */
    private boolean aggressivelyPreventCycles;
    /**
     * The sepsets.
     */
    private SepsetMap sepsets;

    /**
     * Whether verbose output about independencies is output.
     */
    private boolean verbose;
    private boolean useHeuristic;
    private int maxPathLength;
    private FasType fasType = FasType.REGULAR;
    private ColliderDiscovery colliderDiscovery = ColliderDiscovery.FAS_SEPSETS;
    private ConflictRule conflictRule = ConflictRule.OVERWRITE;

    /**
     * Constructs a CPC algorithm that uses the given independence test as oracle. This does not make a copy of the
     * independence test, for fear of duplicating the data set!
     */
    public PcCommon(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }


    /**
     * For the edge from*-*to, return true iff an arrowpoint is allowed as 'to'.
     *
     * @param from      the from edge.
     * @param to        the to edge.
     * @param knowledge the knowledge.
     * @return true iff an arrow point is allowed at 'to'.
     */
    public static boolean isArrowpointAllowed1(Node from, Node to,
                                               Knowledge knowledge) {
        return knowledge == null || !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    /**
     * Checks if an arrowpoint is allowed by background knowledge.
     */
    public static boolean isArrowpointAllowed(Object from, Object to,
                                              Knowledge knowledge) {
        if (knowledge == null) {
            return true;

        }
        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    /**
     * @param useHeuristic Whether the heuristic should be used for max P collider
     *                     orientation.
     */
    public void setUseHeuristic(boolean useHeuristic) {
        this.useHeuristic = useHeuristic;
    }

    /**
     * @param maxPathLength The max path length for the max p collider orientation heuristic.
     */
    public void setMaxPathLength(int maxPathLength) {
        this.maxPathLength = maxPathLength;
    }

    /**
     * @param fasType The type of FAS to be used.
     */
    public void setFasType(FasType fasType) {
        this.fasType = fasType;
    }

    /**
     * @param heuristic Whether to use the max p orientation heuristic.
     */
    public void setHeuristic(int heuristic) {
        this.heuristic = heuristic;
    }

    //=============================CONSTRUCTORS==========================//

    /**
     * @return true just in case edges will not be added if they would create cycles.
     */
    public boolean isAggressivelyPreventCycles() {
        return this.aggressivelyPreventCycles;
    }

    //==============================PUBLIC METHODS========================//

    /**
     * Sets to true just in case edges will not be added if they would create cycles.
     */
    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    /**
     * Sets the type of collider discovery to do.
     *
     * @param colliderDiscovery This type.
     */
    public void setColliderDiscovery(ColliderDiscovery colliderDiscovery) {
        this.colliderDiscovery = colliderDiscovery;
    }

    /**
     * Sets the conflict rule to use.
     *
     * @param conflictRule This rule.
     */
    public void setConflictRule(ConflictRule conflictRule) {
        this.conflictRule = conflictRule;
    }

    /**
     * @return the elapsed time of search in milliseconds, after <code>search()</code> has been run.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * @return the knowledge specification used in the search. Non-null.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge specification used in the search. Non-null.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * @return the independence test used in the search, set in the constructor. This is not returning a copy, for fear
     * of duplicating the data set!
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * @return the depth of the search--that is, the maximum number of variables conditioned on in any conditional
     * independence test.
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * Sets the maximum number of variables conditioned on in any conditional independence test. If set to -1, the value
     * of 1000 will be used. May not be set to Integer.MAX_VALUE, due to a Java bug on multi-core systems.
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException("Depth must be -1 or >= 0: " + depth);
        }

        if (depth == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Depth must not be Integer.MAX_VALUE, " +
                    "due to a known bug.");
        }

        this.depth = depth;
    }

    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True iff the case.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * @return the set of ambiguous triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    /**
     * @return the set of collider triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getColliderTriples() {
        return new HashSet<>(this.colliderTriples);
    }

    /**
     * @return the set of noncollider triples found during the most recent run of the algorithm. Non-null after a call
     * to <code>search()</code>.
     */
    public Set<Triple> getNoncolliderTriples() {
        return new HashSet<>(this.noncolliderTriples);
    }

    /**
     * Returns the edges in the search graph.
     *
     * @return These edges.
     */
    public Set<Edge> getAdjacencies() {
        return new HashSet<>(this.graph.getEdges());
    }

    /**
     * Returns the non-adjacenices in the search graph.
     *
     * @return These non-adjacencies.
     */
    public Set<Edge> getNonadjacencies() {
        Graph complete = GraphUtils.completeGraph(this.graph);
        Set<Edge> nonAdjacencies = complete.getEdges();
        Graph undirected = GraphUtils.undirectedGraph(this.graph);
        nonAdjacencies.removeAll(undirected.getEdges());
        return new HashSet<>(nonAdjacencies);
    }

    /**
     * Runs the search and returns the search graph.
     *
     * @return This result graph.
     */
    public Graph search() {
        return search(getIndependenceTest().getVariables());
    }

    /**
     * Runs the search over the given list of nodes only, returning the serach graph.
     *
     * @param nodes The nodes to search over.
     * @return The result graph.
     */
    public Graph search(List<Node> nodes) {
        nodes = new ArrayList<>(nodes);

        this.logger.log("info", "Starting algorithm");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");
        this.ambiguousTriples = new HashSet<>();
        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();

        this.independenceTest.setVerbose(this.verbose);

        long startTime = MillisecondTimes.timeMillis();

        List<Node> allNodes = getIndependenceTest().getVariables();

        if (!new HashSet<>(allNodes).containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                    "be in the domain of the independence test provided.");
        }

        Fas fas;

        if (this.fasType == FasType.REGULAR) {
            fas = new Fas(getIndependenceTest());
            fas.setHeuristic(this.heuristic);
        } else {
            fas = new Fas(getIndependenceTest());
            fas.setStable(true);
        }

        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(this.verbose);

        // Note that we are ignoring the sepset map returned by this method
        // on purpose; it is not used in this search.
        this.graph = fas.search();
        this.sepsets = fas.getSepsets();

        SearchGraphUtils.pcOrientbk(this.knowledge, this.graph, nodes);

        if (this.colliderDiscovery == ColliderDiscovery.FAS_SEPSETS) {
            orientCollidersUsingSepsets(this.sepsets, this.knowledge, this.graph, this.verbose, this.conflictRule);
        } else if (this.colliderDiscovery == ColliderDiscovery.MAX_P) {
            if (this.verbose) {
                System.out.println("MaxP orientation...");
            }

            MaxP orientCollidersMaxP = new MaxP(this.independenceTest);
            orientCollidersMaxP.setConflictRule(this.conflictRule);
            orientCollidersMaxP.setUseHeuristic(this.useHeuristic);
            orientCollidersMaxP.setMaxPathLength(this.maxPathLength);
            orientCollidersMaxP.setDepth(this.depth);
            orientCollidersMaxP.setKnowledge(this.knowledge);
            orientCollidersMaxP.orient(this.graph);
        } else if (this.colliderDiscovery == ColliderDiscovery.CONSERVATIVE) {
            if (this.verbose) {
                System.out.println("CPC orientation...");
            }

            orientUnshieldedTriplesConservative(this.knowledge);
        }

        this.graph = GraphUtils.replaceNodes(this.graph, nodes);

        MeekRules meekRules = new MeekRules();
        meekRules.setKnowledge(this.knowledge);
        meekRules.setVerbose(verbose);
        meekRules.orientImplied(this.graph);

        long endTime = MillisecondTimes.timeMillis();
        this.elapsedTime = endTime - startTime;

        TetradLogger.getInstance().log("info", "Elapsed time = " + (this.elapsedTime) / 1000. + " s");

        logTriples();

        TetradLogger.getInstance().flush();

        return this.graph;
    }


    //==========================PRIVATE METHODS===========================//

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

            System.out.println("Orienting " + graph.getEdge(x, y) + " " + graph.getEdge(z, y));

            System.out.println("graph = " + graph);
        } else if (conflictRule == ConflictRule.OVERWRITE) {
            graph.removeEdge(x, y);
            graph.removeEdge(z, y);
            graph.addDirectedEdge(x, y);
            graph.addDirectedEdge(z, y);
        }

        TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
    }

    private void logTriples() {
        TetradLogger.getInstance().log("info", "\nCollider triples:");

        for (Triple triple : this.colliderTriples) {
            TetradLogger.getInstance().log("info", "Collider: " + triple);
        }

        TetradLogger.getInstance().log("info", "\nNoncollider triples:");

        for (Triple triple : this.noncolliderTriples) {
            TetradLogger.getInstance().log("info", "Noncollider: " + triple);
        }

        TetradLogger.getInstance().log("info", "\nAmbiguous triples (i.e. list of triples for which " +
                "\nthere is ambiguous data about whether they are colliderDiscovery or not):");

        for (Triple triple : getAmbiguousTriples()) {
            TetradLogger.getInstance().log("info", "Ambiguous: " + triple);
        }
    }

    private void orientUnshieldedTriplesConservative(Knowledge knowledge) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();
        this.ambiguousTriples = new HashSet<>();
        List<Node> nodes = this.graph.getNodes();

        for (Node y : nodes) {
            List<Node> adjacentNodes = this.graph.getAdjacentNodes(y);

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

                List<List<Node>> sepsetsxz = getSepsets(x, z, this.graph);

                if (isColliderSepset(y, sepsetsxz)) {
                    if (colliderAllowed(x, y, z, knowledge)) {
                        PcCommon.orientCollider(x, y, z, this.conflictRule, this.graph);
                    }

                    this.colliderTriples.add(new Triple(x, y, z));
                } else if (isNoncolliderSepset(y, sepsetsxz)) {
                    this.noncolliderTriples.add(new Triple(x, y, z));
                } else {
                    Triple triple = new Triple(x, y, z);
                    this.ambiguousTriples.add(triple);
                    this.graph.underlines().addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                }
            }
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    private List<List<Node>> getSepsets(Node i, Node k, Graph g) {
        List<Node> adji = g.getAdjacentNodes(i);
        List<Node> adjk = g.getAdjacentNodes(k);
        List<List<Node>> sepsets = new ArrayList<>();

        for (int d = 0; d <= FastMath.max(adji.size(), adjk.size()); d++) {
            if (adji.size() >= 2 && d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    List<Node> v = GraphUtils.asList(choice, adji);
                    if (getIndependenceTest().checkIndependence(i, k, v).isIndependent()) sepsets.add(v);
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
                    if (getIndependenceTest().checkIndependence(i, k, v).isIndependent()) sepsets.add(v);
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

    private boolean colliderAllowed(Node x, Node y, Node z, Knowledge knowledge) {
        return PcCommon.isArrowpointAllowed1(x, y, knowledge) &&
                PcCommon.isArrowpointAllowed1(z, y, knowledge);
    }

    /**
     * Step C of PC; orients colliders using specified sepset. That is, orients x *-* y *-* z as x *-&gt; y &lt;-* z just in
     * case y is in Sepset({x, z}).
     */
    private void orientCollidersUsingSepsets(SepsetMap set, Knowledge knowledge, Graph graph, boolean verbose,
                                             ConflictRule conflictRule) {
        if (verbose) {
            System.out.println("FAS Sepset orientation...");
        }

        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");

        List<Node> nodes = graph.getNodes();

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

                List<Node> s2 = new ArrayList<>(sepset);
                if (!s2.contains(b)) s2.add(b);

                if (!sepset.contains(b) && PcCommon.isArrowpointAllowed(a, b, knowledge) && PcCommon.isArrowpointAllowed(c, b, knowledge)) {
                    PcCommon.orientCollider(a, b, c, conflictRule, graph);

                    if (verbose) {
                        System.out.println("Collider orientation <" + a + ", " + b + ", " + c + "> sepset = " + sepset);
                    }

                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(a, b, c, sepset));
                }
            }
        }
    }
}

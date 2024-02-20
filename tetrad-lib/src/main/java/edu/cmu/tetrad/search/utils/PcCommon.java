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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides some common implementation pieces of various PC-like algorithms, with options for collider discovery type,
 * FAS type, and conflict rule.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class PcCommon implements IGraphSearch {

    /**
     * The independence test to use.
     */
    private final IndependenceTest independenceTest;

    /**
     * The logger.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    /**
     * The knowledge specification to use.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The depth of the search.
     */
    private int depth = 1000;

    /**
     * The graph.
     */
    private Graph graph;

    /**
     * The elapsed time of the search.
     */
    private long elapsedTime;

    /**
     * The set of collider triples found during the most recent run of the algorithm.
     */
    private Set<Triple> colliderTriples;

    /**
     * The set of noncollider triples found during the most recent run of the algorithm.
     */
    private Set<Triple> noncolliderTriples;

    /**
     * The set of ambiguous triples found during the most recent run of the algorithm.
     */
    private Set<Triple> ambiguousTriples;

    /**
     * Whether to prevent cycles using Meek's rules.
     */
    private boolean meekPreventCycles;

    /**
     * Whether to print verbose output.
     */
    private boolean verbose = false;

    /**
     * The max path length for the max p collider orientation heuristic.
     */
    private int maxPathLength = 3;

    /**
     * The type of FAS to be used.
     */
    private FasType fasType = FasType.REGULAR;

    /**
     * The type of collider discovery to do.
     */
    private ColliderDiscovery colliderDiscovery = ColliderDiscovery.FAS_SEPSETS;

    /**
     * The conflict rule to use.
     */
    private ConflictRule conflictRule = ConflictRule.PRIORITIZE_EXISTING;

    /**
     * Which PC heuristic to use (see Causation, Prediction and Search). Default is PcHeuristicType.NONE.
     */
    private PcHeuristicType pcHeuristicType = PcHeuristicType.NONE;

    /**
     * Constructs a CPC algorithm that uses the given independence test as oracle. This does not make a copy of the
     * independence test, for fear of duplicating the data set!
     *
     * @param independenceTest The independence test to use.
     */
    public PcCommon(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }

    /**
     * Orient a single unshielded triple, x*-*y*-*z, in a graph.
     *
     * @param x            a {@link Node} object
     * @param y            a {@link Node} object
     * @param z            a {@link Node} object
     * @param conflictRule The conflict rule to use.
     * @param graph        The graph to orient.
     * @param verbose      If verbose output should be printed.
     * @see PcCommon.ConflictRule
     */
    public static void orientCollider(Node x, Node y, Node z, ConflictRule conflictRule, Graph graph, boolean verbose) {
        if (conflictRule == ConflictRule.PRIORITIZE_EXISTING) {
            if (!(graph.getEndpoint(x, y) == Endpoint.ARROW && graph.getEndpoint(z, y) == Endpoint.ARROW)) {
                graph.removeEdge(x, y);
                graph.removeEdge(z, y);
                graph.addDirectedEdge(x, y);
                graph.addDirectedEdge(z, y);
                forceLogMessage(LogUtilsSearch.colliderOrientedMsg(x, y, z), verbose);
            }
        } else if (conflictRule == ConflictRule.ORIENT_BIDIRECTED) {
            graph.setEndpoint(x, y, Endpoint.ARROW);
            graph.setEndpoint(z, y, Endpoint.ARROW);

            forceLogMessage(LogUtilsSearch.colliderOrientedMsg(x, y, z), verbose);
        } else if (conflictRule == ConflictRule.OVERWRITE_EXISTING) {
            graph.removeEdge(x, y);
            graph.removeEdge(z, y);
            graph.addDirectedEdge(x, y);
            graph.addDirectedEdge(z, y);
            forceLogMessage(LogUtilsSearch.colliderOrientedMsg(x, y, z), verbose);
        }

    }

    private static void forceLogMessage(String s, boolean verbose) {
        if (verbose) {
            TetradLogger.getInstance().forceLogMessage(s);
        }
    }

    /**
     * <p>Setter for the field <code>maxPathLength</code>.</p>
     *
     * @param maxPathLength The max path length for the max p collider orientation heuristic.
     */
    public void setMaxPathLength(int maxPathLength) {
        this.maxPathLength = maxPathLength;
    }

    /**
     * <p>Setter for the field <code>fasType</code>.</p>
     *
     * @param fasType The type of FAS to be used.
     */
    public void setFasType(FasType fasType) {
        this.fasType = fasType;
    }

    /**
     * <p>Setter for the field <code>pcHeuristicType</code>.</p>
     *
     * @param pcHeuristic Which PC heuristic to use (see Causation, Prediction and Search). Default is
     *                    PcHeuristicType.NONE.
     * @see PcHeuristicType
     */
    public void setPcHeuristicType(PcHeuristicType pcHeuristic) {
        this.pcHeuristicType = pcHeuristic;
    }

    /**
     * <p>isMeekPreventCycles.</p>
     *
     * @return true, just in case edges will not be added if they create cycles.
     */
    public boolean isMeekPreventCycles() {
        return this.meekPreventCycles;
    }

    /**
     * Sets to true just in case edges will not be added if they create cycles.
     *
     * @param meekPreventCycles True, just in case edges will not be added if they create cycles.
     */
    public void setMeekPreventCycles(boolean meekPreventCycles) {
        this.meekPreventCycles = meekPreventCycles;
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
     * Runs the search over the given list of nodes only, returning the search graph.
     *
     * @param nodes The nodes to search over.
     * @return The result graph.
     */
    public Graph search(List<Node> nodes) {
        nodes = new ArrayList<>(nodes);

        if (verbose) {
            this.logger.forceLogMessage("Starting algorithm");
            this.logger.forceLogMessage("Independence test = " + getIndependenceTest() + ".");
        }

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
            fas.setPcHeuristicType(this.pcHeuristicType);
        } else {
            fas = new Fas(getIndependenceTest());
            fas.setPcHeuristicType(this.pcHeuristicType);
            fas.setStable(true);
        }

        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(this.verbose);

        // Note that we are ignoring the sepset map returned by this method
        // on purpose; it is not used in this search.
        this.graph = fas.search();
        SepsetMap sepsets = fas.getSepsets();

        if (this.graph.paths().existsDirectedCycle())
            throw new IllegalArgumentException("Graph is cyclic after sepsets!");

        GraphSearchUtils.pcOrientbk(this.knowledge, this.graph, nodes);

        if (this.colliderDiscovery == ColliderDiscovery.FAS_SEPSETS) {
            orientCollidersUsingSepsets(sepsets, this.knowledge, this.graph, this.verbose, this.conflictRule);
        } else if (this.colliderDiscovery == ColliderDiscovery.MAX_P) {
            if (this.verbose) {
                System.out.println("MaxP orientation...");
            }

            if (this.graph.paths().existsDirectedCycle())
                throw new IllegalArgumentException("Graph is cyclic before maxp!");

            MaxP orientCollidersMaxP = new MaxP(this.independenceTest);
            orientCollidersMaxP.setConflictRule(this.conflictRule);
            orientCollidersMaxP.setMaxPathLength(this.maxPathLength);
            orientCollidersMaxP.setDepth(this.depth);
            orientCollidersMaxP.setKnowledge(this.knowledge);
            orientCollidersMaxP.orient(this.graph);
            orientCollidersMaxP.setVerbose(verbose);
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
        meekRules.setMeekPreventCycles(this.meekPreventCycles);
        meekRules.orientImplied(this.graph);

        long endTime = MillisecondTimes.timeMillis();
        this.elapsedTime = endTime - startTime;

        forceLogMessage((this.elapsedTime) / 1000. + " s", verbose);

        logTriples();

        return this.graph;
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
     * @see ConflictRule
     */
    public void setConflictRule(ConflictRule conflictRule) {
        this.conflictRule = conflictRule;
    }

    /**
     * <p>Getter for the field <code>elapsedTime</code>.</p>
     *
     * @return The elapsed time of search in milliseconds, after <code>search()</code> has been run.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return The knowledge specification used in the search. Non-null.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge specification used in the search. Non-null.
     *
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * <p>Getter for the field <code>independenceTest</code>.</p>
     *
     * @return the independence test used in the search, set in the constructor. This is not returning a copy, for fear
     * of duplicating the data set!
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * <p>Getter for the field <code>depth</code>.</p>
     *
     * @return The depth of the search--that is, the maximum number of variables conditioned on in any conditional
     * independence test.
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * Sets the maximum number of variables conditioned on in any conditional independence test. If set to -1, the value
     * of 1000 will be used. May not be set to Integer.MAX_VALUE due to a Java bug on multicore systems.
     *
     * @param depth The depth.
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

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True iff the case.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * <p>Getter for the field <code>ambiguousTriples</code>.</p>
     *
     * @return The set of ambiguous triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    /**
     * <p>Getter for the field <code>colliderTriples</code>.</p>
     *
     * @return The set of collider triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getColliderTriples() {
        return new HashSet<>(this.colliderTriples);
    }

    /**
     * <p>Getter for the field <code>noncolliderTriples</code>.</p>
     *
     * @return The set of noncollider triples found during the most recent run of the algorithm. Non-null after a call
     * to <code>search()</code>.
     */
    public Set<Triple> getNoncolliderTriples() {
        return new HashSet<>(this.noncolliderTriples);
    }

    /**
     * Returns The edges in the search graph.
     *
     * @return These edges.
     */
    public Set<Edge> getAdjacencies() {
        return new HashSet<>(this.graph.getEdges());
    }

    private void logTriples() {
        forceLogMessage("\nCollider triples:", verbose);

        for (Triple triple : this.colliderTriples) {
            forceLogMessage("Collider: " + triple, verbose);
        }

        forceLogMessage("\nNoncollider triples:", verbose);

        for (Triple triple : this.noncolliderTriples) {
            forceLogMessage("Noncollider: " + triple, verbose);
        }

        forceLogMessage("""

                Ambiguous triples (i.e. list of triples for which\s
                there is ambiguous data about whether they are colliderDiscovery or not):""", verbose);

        for (Triple triple : getAmbiguousTriples()) {
            forceLogMessage("Ambiguous: " + triple, verbose);
        }
    }

    private void orientUnshieldedTriplesConservative(Knowledge knowledge) {
        forceLogMessage("Starting Collider Orientation:", verbose);

        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();
        this.ambiguousTriples = new HashSet<>();
        List<Node> nodes = this.graph.getNodes();

        for (Node y : nodes) {
            List<Node> adjacentNodes = new ArrayList<>(this.graph.getAdjacentNodes(y));

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

                Set<Set<Node>> sepsetsxz = getSepsets(x, z, this.graph);

                if (isColliderSepset(y, sepsetsxz)) {
                    if (colliderAllowed(x, y, z, knowledge)) {
                        PcCommon.orientCollider(x, y, z, this.conflictRule, this.graph, verbose);
                        this.colliderTriples.add(new Triple(x, y, z));
                    }
                } else if (isNoncolliderSepset(y, sepsetsxz)) {
                    this.noncolliderTriples.add(new Triple(x, y, z));
                } else {
                    Triple triple = new Triple(x, y, z);
                    this.ambiguousTriples.add(triple);
                    this.graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                }
            }
        }

        forceLogMessage("Finishing Collider Orientation.", verbose);
    }

    private Set<Set<Node>> getSepsets(Node i, Node k, Graph g) {
        List<Node> adji = new ArrayList<>(g.getAdjacentNodes(i));
        List<Node> adjk = new ArrayList<>(g.getAdjacentNodes(k));
        Set<Set<Node>> sepsets = new HashSet<>();

        for (int d = 0; d <= FastMath.max(adji.size(), adjk.size()); d++) {
            if (adji.size() >= 2 && d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    Set<Node> v = GraphUtils.asSet(choice, adji);
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

                    Set<Node> v = GraphUtils.asSet(choice, adjk);
                    if (getIndependenceTest().checkIndependence(i, k, v).isIndependent()) sepsets.add(v);
                }
            }
        }

        return sepsets;
    }

    private boolean isColliderSepset(Node j, Set<Set<Node>> sepsets) {
        if (sepsets.isEmpty()) return false;

        for (Set<Node> sepset : sepsets) {
            if (sepset.contains(j)) return false;
        }

        return true;
    }

    private boolean isNoncolliderSepset(Node j, Set<Set<Node>> sepsets) {
        if (sepsets.isEmpty()) return false;

        for (Set<Node> sepset : sepsets) {
            if (!sepset.contains(j)) return false;
        }

        return true;
    }

    private boolean colliderAllowed(Node x, Node y, Node z, Knowledge knowledge) {
        boolean result = true;
        if (knowledge != null) {
            result = !knowledge.isRequired(((Object) y).toString(), ((Object) x).toString())
                    && !knowledge.isForbidden(((Object) x).toString(), ((Object) y).toString());
        }
        if (!result) return false;
        if (knowledge == null) {
            return true;
        }
        return !knowledge.isRequired(((Object) y).toString(), ((Object) z).toString())
                && !knowledge.isForbidden(((Object) z).toString(), ((Object) y).toString());
    }

    /**
     * Step C of PC; orients colliders using specified sepset. That is, orients x *-* y *-* z as x *-&gt; y &lt;-* z
     * just in case y is in Sepset({x, z}).
     */
    private void orientCollidersUsingSepsets(SepsetMap set, Knowledge knowledge, Graph graph, boolean verbose,
                                             ConflictRule conflictRule) {
        if (verbose) {
            System.out.println("FAS Sepset orientation...");
        }

        forceLogMessage("Starting Collider Orientation:", verbose);

        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = new ArrayList<>(graph.getAdjacentNodes(b));

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

                Set<Node> sepset = set.get(a, c);

                if (sepset == null) continue;

                if (!sepset.contains(b)) {
                    boolean result1 = true;
                    if (knowledge != null) {
                        result1 = !knowledge.isRequired(((Object) b).toString(), ((Object) a).toString())
                                && !knowledge.isForbidden(((Object) a).toString(), ((Object) b).toString());
                    }
                    if (result1) {
                        boolean result = true;
                        if (knowledge != null) {
                            result = !knowledge.isRequired(((Object) b).toString(), ((Object) c).toString())
                                    && !knowledge.isForbidden(((Object) c).toString(), ((Object) b).toString());
                        }
                        if (result) {
                            PcCommon.orientCollider(a, b, c, conflictRule, graph, verbose);

                            if (verbose) {
                                System.out.println("Collider orientation <" + a + ", " + b + ", " + c + "> sepset = " + sepset);
                            }

                            colliderTriples.add(new Triple(a, b, c));
                            forceLogMessage(LogUtilsSearch.colliderOrientedMsg(a, b, c, sepset), verbose);
                        }
                    }
                }
            }
        }
    }

    /**
     * <p>NONE = no heuristic, PC-1 = sort nodes alphabetically; PC-1 = sort edges by p-value; PC-3 = additionally sort
     * edges in reverse order using p-values of associated independence facts. See this reference:</p>
     *
     * <p>Spirtes, P., Glymour, C. N., &amp; Scheines, R. (2000). Causation, prediction, and search. MIT press.</p>
     */
    public enum PcHeuristicType {

        /**
         * No heuristic.
         */
        NONE,

        /**
         * Sort nodes alphabetically.
         */
        HEURISTIC_1,

        /**
         * Sort edges by p-value.
         */
        HEURISTIC_2,

        /**
         * Sort edges in reverse order using p-values of associated independence facts.
         */
        HEURISTIC_3
    }

    /**
     * Gives the type of FAS used, regular or stable.
     *
     * @see Pc
     * @see Cpc
     * @see FasType
     */
    public enum FasType {

        /**
         * Regular FAS.
         */
        REGULAR,

        /**
         * Stable FAS.
         */
        STABLE
    }

    /**
     * <p>Give the options for the collider discovery algorithm to use--FAS with sepsets reasoning, FAS with
     * conservative reasoning, or FAS with Max P reasoning. See these respective references:</p>
     *
     * <p>Spirtes, P., Glymour, C. N., &amp; Scheines, R. (2000). Causation, prediction, and search. MIT press.</p>
     *
     * <p>Ramsey, J., Zhang, J., &amp; Spirtes, P. L. (2012). Adjacency-faithfulness and conservative causal inference.
     * arXiv preprint arXiv:1206.6843.</p>
     *
     * <p>Ramsey, J. (2016). Improving accuracy and scalability of the pc algorithm by maximizing p-value. arXiv
     * preprint arXiv:1610.00378.</p>
     *
     * @see Fas
     * @see Cpc
     * @see ColliderDiscovery
     */
    public enum ColliderDiscovery {

        /**
         * FAS with sepsets reasoning.
         */
        FAS_SEPSETS,

        /**
         * FAS with conservative reasoning.
         */
        CONSERVATIVE,

        /**
         * FAS with Max P reasoning.
         */
        MAX_P
    }

    /**
     * Gives the type of conflict to be used, priority (when there is a conflict, keep the orientation that has already
     * been made), bidirected (when there is a conflict, orient a bidirected edge), or overwrite (when there is a
     * conflict, use the new orientation).
     *
     * @see Pc
     * @see Cpc
     * @see ConflictRule
     */
    public enum ConflictRule {

        /**
         * When there is a conflict, keep the orientation that has already been made.
         */
        PRIORITIZE_EXISTING,

        /**
         * When there is a conflict, orient a bidirected edge.
         */
        ORIENT_BIDIRECTED,

        /**
         * When there is a conflict, use the new orientation.
         */
        OVERWRITE_EXISTING
    }
}


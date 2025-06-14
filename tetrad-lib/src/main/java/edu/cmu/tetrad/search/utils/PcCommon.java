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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

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
    private boolean guaranteeCpdag;

    /**
     * Whether to print verbose output.
     */
    private boolean verbose = false;

    /**
     * The maximum length of a discriminating path.
     * <p>
     * This variable represents the maximum length of a discriminating path. It is used in the context of some algorithm
     * or data structure where discriminating paths are relevant. The value of this variable is -1 by default and can be
     * updated as necessary.
     * </p>
     */
    private int maxDiscriminatingPathLength = -1;

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
     * Represents the start time in milliseconds since the Unix epoch (January 1, 1970, 00:00:00 GMT). This variable is
     * typically used to store the timestamp marking the initiation of a specific event or process.
     */
    private long startTime = -1;
    /**
     * Specifies the maximum duration, in milliseconds, to wait for an operation to complete before timing out. This
     * variable can be used to define a limit on how long a process or operation should take. New independence checks
     * will not be run if the elapsed time exceeds this in millisecond, and an exception will be thrown.
     */
    private long timeout;

    /**
     * Constructs a CPC algorithm that uses the given independence test as oracle. This does not make a copy of the
     * independence test, for fear of duplicating the data set!
     *
     * @param independenceTest The independence test to use.
     */
    public PcCommon(IndependenceTest independenceTest) {
        this.independenceTest = Objects.requireNonNull(independenceTest, "Independence test cannot be null.");
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
     * @param acyclic      True if the output is supposed to be acyclic.
     * @see PcCommon.ConflictRule
     */
    public static void orientCollider(Node x, Node y, Node z, ConflictRule conflictRule, Graph graph, boolean verbose, boolean acyclic) {
        if (graph.isAncestorOf(y, x) || graph.isAncestorOf(y, z)) {
            return;
        }

        if (conflictRule == ConflictRule.PRIORITIZE_EXISTING) {
            if (!(graph.getEndpoint(x, y) == Endpoint.ARROW && graph.getEndpoint(z, y) == Endpoint.ARROW)) {
                graph.removeEdge(x, y);
                graph.removeEdge(z, y);
                graph.addDirectedEdge(x, y);
                graph.addDirectedEdge(z, y);

//                TetradLogger.getInstance().log("Collider oriented: " + GraphUtils.pathString(graph, x, y, z));
            }
        } else if (conflictRule == ConflictRule.ORIENT_BIDIRECTED) {
            graph.setEndpoint(x, y, Endpoint.ARROW);
            graph.setEndpoint(z, y, Endpoint.ARROW);
//            TetradLogger.getInstance().log("Collider oriented: " + GraphUtils.pathString(graph, x, y, z));
        } else if (conflictRule == ConflictRule.OVERWRITE_EXISTING) {
            graph.removeEdge(x, y);
            graph.removeEdge(z, y);
            graph.addDirectedEdge(x, y);
            graph.addDirectedEdge(z, y);
//            TetradLogger.getInstance().log("Collider oriented: " + GraphUtils.pathString(graph, x, y, z));
        }

    }

    /**
     * Logs the given string based on the value of the verbose flag.
     *
     * @param s       the string to be logged
     * @param verbose a boolean flag indicating whether the log message should be printed or not
     */
    private static void log(String s, boolean verbose) {
        if (verbose) {
            TetradLogger.getInstance().log(s);
        }
    }

    /**
     * Checks if colliders are allowed based on the given knowledge.
     *
     * @param x         The first node.
     * @param y         The second node.
     * @param z         The third node.
     * @param knowledge The knowledge object containing the required and forbidden relationships.
     * @return True if colliders are allowed based on the given knowledge, false otherwise.
     */
    public static boolean colliderAllowed(Node x, Node y, Node z, Knowledge knowledge) {
        if (knowledge == null) return true;

        if (knowledge.isRequired(y.toString(), x.toString()) || knowledge.isForbidden(x.toString(), y.toString())) {
            return false;
        }

        return !(knowledge.isRequired(y.toString(), z.toString()) || knowledge.isForbidden(z.toString(), y.toString()));
    }

    /**
     * <p>Setter for the field <code>maxPathLength</code>.</p>
     *
     * @param maxDiscriminatingPathLength The max path length for the max p collider orientation heuristic.
     */
    public void setMaxDiscriminatingPathLength(int maxDiscriminatingPathLength) {
        this.maxDiscriminatingPathLength = maxDiscriminatingPathLength;
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
     * Checks if the current object guarantees a complete directed acyclic graph (CPDAG).
     *
     * @return {@code true} if the current object guarantees a CPDAG, {@code false} otherwise.
     */
    public boolean isGuaranteeCpdag() {
        return this.guaranteeCpdag;
    }

    /**
     * Sets to true just in case edges will not be added if they create cycles.
     *
     * @param guaranteeCpdag True, just in the output will guarantee a CPDAG.
     */
    public void setGuaranteeCpdag(boolean guaranteeCpdag) {
        this.guaranteeCpdag = guaranteeCpdag;
    }

    /**
     * Runs the search and returns the search graph.
     *
     * @return This result graph.
     * @throws InterruptedException if any
     */
    public Graph search() throws InterruptedException {
        return search(getIndependenceTest().getVariables());
    }

    /**
     * Runs the search over the given list of nodes only, returning the search graph.
     *
     * @param nodes The nodes to search over.
     * @return The result graph.
     * @throws InterruptedException if any
     */
    public Graph search(List<Node> nodes) throws InterruptedException {
        nodes = new ArrayList<>(nodes);

        if (verbose) {
            this.logger.log("Starting algorithm");
            this.logger.log("Independence test = " + getIndependenceTest() + ".");
        }

        this.ambiguousTriples = new HashSet<>();
        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();

        this.independenceTest.setVerbose(this.verbose);

        if (startTime <= 0) {
            startTime = System.currentTimeMillis();
        }

        List<Node> allNodes = getIndependenceTest().getVariables();

        if (!new HashSet<>(allNodes).containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " + "be in the domain of the independence test provided.");
        }

        Fas fas = new Fas(getIndependenceTest());

        fas.setStable(this.fasType == FasType.STABLE);

        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(this.verbose);

        // Note that we are ignoring the sepset map returned by this method
        // on purpose; it is not used in this search.
        try {
            this.graph = fas.search();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        SepsetMap sepsets = fas.getSepsets();

        if (this.graph.paths().existsDirectedCycle())
            throw new IllegalArgumentException("Graph is cyclic after sepsets!");

        GraphSearchUtils.pcOrientbk(this.knowledge, this.graph, nodes, verbose);

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
            orientCollidersMaxP.setMaxPMaxHeuristicPathLength(this.maxDiscriminatingPathLength);
            orientCollidersMaxP.setDepth(this.depth);
            orientCollidersMaxP.setKnowledge(this.knowledge);
            orientCollidersMaxP.setVerbose(verbose);
            orientCollidersMaxP.setAcyclic(guaranteeCpdag);
            orientCollidersMaxP.orient(this.graph);
        } else if (this.colliderDiscovery == ColliderDiscovery.CONSERVATIVE) {
            if (this.verbose) {
                System.out.println("CPC orientation...");
            }

            orientUnshieldedTriplesConservative(this.knowledge);
        }

        this.graph = GraphUtils.replaceNodes(this.graph, nodes);

        if (guaranteeCpdag) {
            MeekRules meekRules = new MeekRules();
            meekRules.setKnowledge(this.knowledge);
            meekRules.setVerbose(verbose);
            meekRules.setMeekPreventCycles(true);
            meekRules.orientImplied(this.graph);
        } else {
            MeekRules meekRules = new MeekRules();
            meekRules.setKnowledge(this.knowledge);
            meekRules.setVerbose(verbose);
            meekRules.setMeekPreventCycles(false);
            meekRules.orientImplied(this.graph);
        }

        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - startTime;

        log((this.elapsedTime) / 1000. + " s", verbose);

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
            throw new IllegalArgumentException("Depth must not be Integer.MAX_VALUE, " + "due to a known bug.");
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

    /**
     * Logs the collider triples, noncollider triples, and ambiguous triples (i.e. those triples for which there is
     * ambiguous data about whether they are colliderDiscovery or not). Only logs the information if verbose is set to
     * true.
     */
    private void logTriples() {
        if (verbose) {
            log("\nCollider triples:", verbose);

            for (Triple triple : this.colliderTriples) {
                log("Collider: " + triple, verbose);
            }

            log("\nNoncollider triples:", verbose);

            for (Triple triple : this.noncolliderTriples) {
                log("Noncollider: " + triple, verbose);
            }

            log("""
                    
                    Ambiguous triples (i.e. list of triples for which\s
                    there is ambiguous data about whether they are colliderDiscovery or not):""", verbose);

            for (Triple triple : getAmbiguousTriples()) {
                log("Ambiguous: " + triple, verbose);
            }

        }
    }

    /**
     * Orients unshielded triples conservatively based on the given knowledge.
     *
     * @param knowledge the knowledge used for orientation
     * @throws InterruptedException if any
     */
    private void orientUnshieldedTriplesConservative(Knowledge knowledge) throws InterruptedException {
        log("Starting Collider Orientation:", verbose);

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
                        PcCommon.orientCollider(x, y, z, this.conflictRule, this.graph, verbose, guaranteeCpdag);
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

        log("Finishing Collider Orientation.", verbose);
    }

    /**
     * Retrieves the set of separation sets between two nodes in a graph.
     *
     * @param i The first node
     * @param k The second node
     * @param g The graph
     * @return The set of separation sets between node i and node k
     * @throws InterruptedException if any
     */
    private Set<Set<Node>> getSepsets(Node i, Node k, Graph g) throws InterruptedException {
        List<Node> adji = new ArrayList<>(g.getAdjacentNodes(i));
        List<Node> adjk = new ArrayList<>(g.getAdjacentNodes(k));
        Set<Set<Node>> sepsets = new HashSet<>();

        for (int d = 0; d <= Math.max(adji.size(), adjk.size()); d++) {
            List<List<Node>> adjLists = List.of(adji, adjk);

            for (List<Node> adj : adjLists) {
                if (adj.size() < 2 || d > adj.size()) continue;

                ChoiceGenerator gen = new ChoiceGenerator(adj.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    if (Thread.currentThread().isInterrupted()) return sepsets;

                    Set<Node> v = GraphUtils.asSet(choice, adj);
                    if (getIndependenceTest().checkIndependence(i, k, v).isIndependent()) {
                        sepsets.add(v);
                    }
                }
            }
        }

        return sepsets;
    }

    /**
     * Checks if a given node is a collider, according to any of the separation sets.
     *
     * @param j       The node to check if it is a collider.
     * @param sepsets The set of separation sets to check for colliders.
     * @return True if the node is a collider in any of the separation sets, false otherwise.
     */
    private boolean isColliderSepset(Node j, Set<Set<Node>> sepsets) {
        if (sepsets.isEmpty()) return false;

        for (Set<Node> sepset : sepsets) {
            if (sepset.contains(j)) return false;
        }

        return true;
    }

    /**
     * Checks if a given node is a noncollider, according to any of the separation sets.
     *
     * @param j       The node to check if it is a noncollider.
     * @param sepsets The set of separation sets to check for noncolliders.
     * @return True if the node is a noncollider in all of the separation sets, false otherwise.
     */
    private boolean isNoncolliderSepset(Node j, Set<Set<Node>> sepsets) {
        if (sepsets.isEmpty()) return false;

        for (Set<Node> sepset : sepsets) {
            if (!sepset.contains(j)) return false;
        }

        return true;
    }

    /**
     * Step C of PC; orients colliders using specified sepset. That is, orients x *-* y *-* z as x *-&gt; y &lt;-* z
     * just in case y is in Sepset({x, z}).
     *
     * @param set          the sepset map containing the sepsets between nodes
     * @param knowledge    the knowledge object used for required and forbidden edges
     * @param graph        the graph containing all the nodes
     * @param verbose      a flag indicating whether to display verbose output
     * @param conflictRule the conflict resolution rule to use when orienting colliders
     */
    private void orientCollidersUsingSepsets(SepsetMap set, Knowledge knowledge, Graph graph, boolean verbose,
                                             ConflictRule conflictRule) {
        if (verbose) {
            System.out.println("FAS Sepset orientation...");
        }

        log("Starting Collider Orientation:", verbose);

        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = new ArrayList<>(graph.getAdjacentNodes(b));

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
                        result1 = !knowledge.isRequired(b.toString(), a.toString())
                                  && !knowledge.isForbidden(a.toString(), b.toString());
                    }
                    if (result1) {
                        boolean result = true;
                        if (knowledge != null) {
                            result = !knowledge.isRequired(b.toString(), c.toString())
                                     && !knowledge.isForbidden(c.toString(), b.toString());
                        }
                        if (result) {
                            if (colliderAllowed(a, b, c, knowledge)) {
                                PcCommon.orientCollider(a, b, c, conflictRule, graph, verbose, guaranteeCpdag);

//                                if (verbose) {
//                                    System.out.println("Collider orientation <" + a + ", " + b + ", " + c + "> sepset = " + sepset);
//                                }

                                colliderTriples.add(new Triple(a, b, c));
                                log(LogUtilsSearch.colliderOrientedMsg(a, b, c, sepset), verbose);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Sets the start time for an operation or event.
     *
     * @param startTime the start time in milliseconds since epoch
     */
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    /**
     * Sets the timeout value for the operation.
     *
     * @param timeout the timeout duration in milliseconds
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
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
     * Gives the options for the collider discovery algorithm to use--FAS with sepsets reasoning, FAS with conservative
     * reasoning, or FAS with Max P reasoning. See these respective references:
     * <p>
     * Spirtes, P., Glymour, C. N., &amp; Scheines, R. (2000). Causation, prediction, and search. MIT press.
     * <p>
     * Ramsey, J., Zhang, J., &amp; Spirtes, P. L. (2012). Adjacency-faithfulness and conservative causal inference.
     * arXiv preprint arXiv:1206.6843.
     * <p>
     * Ramsey, J. (2016). Improving accuracy and scalability of the pc algorithm by maximizing p-value. arXiv preprint
     * arXiv:1610.00378.
     *
     * @see Fas
     * @see Cpc
     * @see ColliderDiscovery
     */
    public enum ColliderDiscovery {

        /**
         * FAS with conservative reasoning.
         */
        CONSERVATIVE,

        /**
         * FAS with sepsets reasoning.
         */
        FAS_SEPSETS,

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
         * When there is a conflict, orient a bidirected edge.
         */
        ORIENT_BIDIRECTED,

        /**
         * When there is a conflict, use the new orientation.
         */
        OVERWRITE_EXISTING,

        /**
         * When there is a conflict, keep the orientation that has already been made.
         */
        PRIORITIZE_EXISTING

    }
}


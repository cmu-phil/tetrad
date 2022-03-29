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
import edu.cmu.tetrad.util.CombinationGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Implements a convervative version of PC, in which the Markov condition is assumed but faithfulness is tested
 * locally.
 *
 * @author Joseph Ramsey (this version).
 */
public final class VcpcAlt implements GraphSearch {

    private final int NTHREDS = Runtime.getRuntime().availableProcessors() * 5;

    /**
     * The independence test used for the PC search.
     */
    private final IndependenceTest independenceTest;

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

    private Set<Edge> definitelyNonadjacencies;

    private Set<Node> markovInAllCPDAGs;

    private Set<Graph> markovCPDAG;

    private Set<Node> markovNodes;

    private Set<Node> all;

    private boolean aggressivelyPreventCycles = false;

    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    /**
     * The sepsets.
     */
    private Map<Edge, List<Node>> apparentlyNonadjacencies;

    /**
     * True iff orientation should be done.
     */
    private boolean doOrientation = true;

    /**
     * Whether verbose output about independencies is output.
     */
    private boolean verbose = false;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a CPC algorithm that uses the given independence test as oracle. This does not make a copy of the
     * independence test, for fear of duplicating the data set!
     */
    public VcpcAlt(final IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
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
    public void setAggressivelyPreventCycles(final boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    /**
     * Sets the maximum number of variables conditioned on in any conditional independence test. If set to -1, the value
     * of 1000 will be used. May not be set to Integer.MAX_VALUE, due to a Java bug on multi-core systems.
     */
    public final void setDepth(final int depth) {
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
        return this.knowledge;
    }

    /**
     * Sets the knowledge specification used in the search. Non-null.
     */
    public void setKnowledge(final IKnowledge knowledge) {
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
     * @return the set of all triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getAllTriples() {
        return new HashSet<>(this.allTriples);
    }

    /**
     * Runs PC starting with a fully connected graph over all of the variables in the domain of the independence test.
     * See PC for caveats. The number of possible cycles and bidirected edges is far less with CPC than with PC.
     */
//    public final Graph search() {
//        return search(independenceTest.getVariable());
//    }

////    public Graph search(List<Node> nodes) {
////
//////        return search(new FasICov2(getIndependenceTest()), nodes);
//////        return search(new Fas(getIndependenceTest()), nodes);
////        return search(new Fas(getIndependenceTest()), nodes);
//    }


//  modified FAS into VCFAS; added in definitelyNonadjacencies set of edges.
    public Graph search() {
        this.logger.log("info", "Starting VCCPC algorithm");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");
        this.allTriples = new HashSet<>();
        this.ambiguousTriples = new HashSet<>();
        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();
        final Vcfas fas = new Vcfas(getIndependenceTest());
        this.definitelyNonadjacencies = new HashSet<>();
        this.markovInAllCPDAGs = new HashSet<>();

//        this.logger.log("info", "Variables " + independenceTest.getVariable());

        final long startTime = System.currentTimeMillis();

        if (getIndependenceTest() == null) {
            throw new NullPointerException();
        }

        final List<Node> allNodes = getIndependenceTest().getVariables();

//        if (!allNodes.containsAll(nodes)) {
//            throw new IllegalArgumentException("All of the given nodes must " +
//                    "be in the domain of the independence test provided.");
//        }

//        Fas fas = new Fas(graph, getIndependenceTest());
//        FasStableConcurrent fas = new FasStableConcurrent(graph, getIndependenceTest());
//        Fas6 fas = new Fas6(graph, getIndependenceTest());
//        fas = new FasICov(graph, (IndTestFisherZ) getIndependenceTest());

        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(this.verbose);

        // Note that we are ignoring the sepset map returned by this method
        // on purpose; it is not used in this search.
        this.graph = fas.search();

        this.apparentlyNonadjacencies = fas.getApparentlyNonadjacencies();

        if (isDoOrientation()) {
            if (this.verbose) {
                System.out.println("CPC orientation...");
            }
            SearchGraphUtils.pcOrientbk(this.knowledge, this.graph, allNodes);
            orientUnshieldedTriples(this.knowledge, getIndependenceTest(), getDepth());
//            orientUnshieldedTriplesConcurrent(knowledge, getIndependenceTest(), getMaxIndegree());
            final MeekRules meekRules = new MeekRules();

            meekRules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
            meekRules.setKnowledge(this.knowledge);

            meekRules.orientImplied(this.graph);
        }


        final List<Triple> ambiguousTriples = new ArrayList(this.graph.getAmbiguousTriples());

        final int[] dims = new int[ambiguousTriples.size()];

        for (int i = 0; i < ambiguousTriples.size(); i++) {
            dims[i] = 2;
        }

        final List<Graph> patterns = new ArrayList<>();
        final Map<Graph, List<Triple>> newColliders = new IdentityHashMap<>();
        final Map<Graph, List<Triple>> newNonColliders = new IdentityHashMap<>();

//      Using combination generator to generate a list of combinations of ambiguous triples dismabiguated into colliders
//      and non-colliders. The combinations are added as graphs to the list patterns. The graphs are then subject to
//      basic rules to ensure consistent patterns.


        final CombinationGenerator generator = new CombinationGenerator(dims);
        int[] combination;

        while ((combination = generator.next()) != null) {
            final Graph _graph = new EdgeListGraph(this.graph);
            newColliders.put(_graph, new ArrayList<Triple>());
            newNonColliders.put(_graph, new ArrayList<Triple>());
            for (final Graph graph : newColliders.keySet()) {
//                System.out.println("$$$ " + newColliders.get(graph));
            }
            for (int k = 0; k < combination.length; k++) {
//                System.out.println("k = " + combination[k]);
                final Triple triple = ambiguousTriples.get(k);
                _graph.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());


                if (combination[k] == 0) {
                    newColliders.get(_graph).add(triple);
//                    System.out.println(newColliders.get(_graph));
                    final Node x = triple.getX();
                    final Node y = triple.getY();
                    final Node z = triple.getZ();

                    _graph.setEndpoint(x, y, Endpoint.ARROW);
                    _graph.setEndpoint(z, y, Endpoint.ARROW);

                }
                if (combination[k] == 1) {
                    newNonColliders.get(_graph).add(triple);
                }
            }
            patterns.add(_graph);
        }

        final List<Graph> _patterns = new ArrayList<>(patterns);


        ///    Takes patterns and runs them through basic constraints to ensure consistent patterns (e.g. no cycles, no bidirected edges).

        GRAPH:

        for (final Graph graph : new ArrayList<>(patterns)) {
//            _graph = new EdgeListGraph(graph);

//            System.out.println("graph = " + graph + " in keyset? " + newColliders.containsKey(graph));
//
            final List<Triple> colliders = newColliders.get(graph);
            final List<Triple> nonColliders = newNonColliders.get(graph);


            for (final Triple triple : colliders) {
                final Node x = triple.getX();
                final Node y = triple.getY();
                final Node z = triple.getZ();

                if (graph.getEdge(x, y).pointsTowards(x) || (graph.getEdge(y, z).pointsTowards(z))) {
                    patterns.remove(graph);
                    continue GRAPH;
                }
            }

            for (final Triple triple : colliders) {
                final Node x = triple.getX();
                final Node y = triple.getY();
                final Node z = triple.getZ();

                graph.setEndpoint(x, y, Endpoint.ARROW);
                graph.setEndpoint(z, y, Endpoint.ARROW);
            }

            for (final Triple triple : nonColliders) {
                final Node x = triple.getX();
                final Node y = triple.getY();
                final Node z = triple.getZ();

                if (graph.getEdge(x, y).pointsTowards(y)) {
                    graph.removeEdge(y, z);
                    graph.addDirectedEdge(y, z);
                }
                if (graph.getEdge(y, z).pointsTowards(y)) {
                    graph.removeEdge(x, y);
                    graph.addDirectedEdge(y, x);
                }
            }

            for (final Edge edge : graph.getEdges()) {
                if (Edges.isBidirectedEdge(edge)) {
                    patterns.remove(graph);
                    continue GRAPH;
                }
            }

            final MeekRules rules = new MeekRules();
            rules.orientImplied(graph);
            if (graph.existsDirectedCycle()) {
                patterns.remove(graph);
                continue GRAPH;
            }

        }


//        Step V5* Instead of checking if Markov in every pattern, just find some pattern that is Markov.

//        PATTERNS:
//
//        for (Graph _graph : new ArrayList<Graph>(patterns)) {
//            for (Node node : graph.getNodes()) {
//                if (!isMarkov(node, _graph)) {
//                    continue PATTERNS;
//                }
//                markovInAllPatterns.add(node);
//            }
//            break;
//        }
//
//        Graph h = new EdgeListGraph(graph.getNodes());
//        for (Edge edge : apparentlyNonadjacencies.keySet()) {
//            h.addEdge(edge);
//        }
//
//        List<Edge> edges = h.getEdges();
//
//        for (Edge edge : edges) {
//            Node x = edge.getNode1();
//            Node y = edge.getNode2();
//
//            if (markovInAllPatterns.contains(x) &&
//                    markovInAllPatterns.contains(y)) {
//                definitelyNonadjacencies.add(edge);
//                apparentlyNonadjacencies.remove(edge);
//            }
//        }

//        "some" version: For each apparently non-adjacent pair X and Y, if X and Y are independent given *some* subset of
//         X's possible parents or *some* subset of Y's possible parents, then X and Y are definitely non-adjacent.
        // 4/8/15 Local Relative Markov (M2)

        MARKOV:

        for (final Edge edge : this.apparentlyNonadjacencies.keySet()) {
            final Node x = edge.getNode1();
            final Node y = edge.getNode2();

            for (final Graph _graph : new ArrayList<>(patterns)) {

                final List<Node> boundaryX = new ArrayList<>(boundary(x, _graph));
                final List<Node> boundaryY = new ArrayList<>(boundary(y, _graph));
                final List<Node> futureX = new ArrayList<>(future(x, _graph));
                final List<Node> futureY = new ArrayList<>(future(y, _graph));

                if (y == x) {
                    continue;
                }
                if (boundaryX.contains(y) || boundaryY.contains(x)) {
                    continue;
                }
                final IndependenceTest test = this.independenceTest;

                if (!futureX.contains(y)) {
                    if (test.isIndependent(x, y, boundaryX)) {
                        if (!futureY.contains(x)) {
                            if (test.isIndependent(y, x, boundaryY)) {
                                this.definitelyNonadjacencies.add(edge);
                                continue MARKOV;
                            }
                        }
                    }
                }
            }
        }

        for (final Edge edge : this.definitelyNonadjacencies) {
            if (this.apparentlyNonadjacencies.keySet().contains(edge)) {
                this.apparentlyNonadjacencies.keySet().remove(edge);
            }
        }

//        MARKOV:
//
//        for (Edge edge : apparentlyNonadjacencies.keySet()) {
//            Node x = edge.getNode1();
//            Node y = edge.getNode2();
//
//            for (Graph _graph : new ArrayList<Graph>(patterns)) {
//
//                List<Node> boundaryX = new ArrayList<Node>(boundary(x, _graph));
//                List<Node> boundaryY = new ArrayList<Node>(boundary(y, _graph));
//                List<Node> futureX = new ArrayList<Node>(future(x, _graph));
//                List<Node> futureY = new ArrayList<Node>(future(y, _graph));
//                if (y == x) {
//                    continue;
//                }
//                if (futureX.contains(y) || futureY.contains(x)) {
//                    continue;
//                }
//                if (boundaryX.contains(y) || boundaryY.contains(x)) {
//                    continue;
//                }
//
//                System.out.println(_graph);
//                IndependenceTest test = new IndTestDSep(_graph);
//                if (!test.isIndependent(x, y, boundaryX)) {
//                    continue;
//                }
//                if (!test.isIndependent(y, x, boundaryY)) {
//                    continue;
//                }
//
//                definitelyNonadjacencies.add(edge);
//                continue MARKOV;
//            }
//
////            apparentlyNonadjacencies.remove(edge);
//
//        }
//
//        for (Edge edge : definitelyNonadjacencies) {
//            if (apparentlyNonadjacencies.keySet().contains(edge)) {
//                apparentlyNonadjacencies.keySet().remove(edge);
//            }
//        }


//        Step V5. For each consistent disambiguation of the ambiguous triples
//                we test whether the resulting pattern satisfies Markov. If
//                every pattern does, then mark all the apparently non-adjacent
//                pairs as definitely non-adjacent.


//        NODES:
//
//        for (Node node : graph.getNodes()) {
//            for (Graph _graph : new ArrayList<Graph>(patterns)) {
//                System.out.println("boundary of" + node + boundary(node, _graph));
//                System.out.println("future of" + node + future(node, _graph));
//                if (!isMarkov(node, _graph)) {
//                    continue NODES;
//                }
//            }
//            markovInAllPatterns.add(node);
//            continue NODES;
//        }
//
//        Graph g = new EdgeListGraph(graph.getNodes());
//        for (Edge edge : apparentlyNonadjacencies.keySet()) {
//            g.addEdge(edge);
//        }
//
//        List<Edge> _edges = g.getEdges();
//
//        for (Edge edge : _edges) {
//            Node x = edge.getNode1();
//            Node y = edge.getNode2();
//
//            if (markovInAllPatterns.contains(x) &&
//                    markovInAllPatterns.contains(y)) {
//                definitelyNonadjacencies.add(edge);
//            }
//        }


        System.out.println("Definitely Nonadjacencies:");

        for (final Edge edge : this.definitelyNonadjacencies) {
            System.out.println(edge);
        }

        System.out.println("markov in all CPDAGs:" + this.markovInAllCPDAGs);
        System.out.println("patterns:" + patterns);
        System.out.println("Apparently Nonadjacencies:");


        for (final Edge edge : this.apparentlyNonadjacencies.keySet()) {
            System.out.println(edge);
        }
        System.out.println("Definitely Nonadjacencies:");


        for (final Edge edge : this.definitelyNonadjacencies) {
            System.out.println(edge);
        }

        TetradLogger.getInstance().log("apparentlyNonadjacencies", "\n Apparent Non-adjacencies" + this.apparentlyNonadjacencies);

        TetradLogger.getInstance().log("definitelyNonadjacencies", "\n Definite Non-adjacencies" + this.definitelyNonadjacencies);

        TetradLogger.getInstance().log("patterns", "Disambiguated Patterns: " + patterns);


        TetradLogger.getInstance().log("graph", "\nReturning this graph: " + this.graph);

        final long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - startTime;

        TetradLogger.getInstance().log("info", "Elapsed time = " + (this.elapsedTime) / 1000. + " s");
        TetradLogger.getInstance().log("info", "Finishing CPC algorithm.");

        logTriples();

        TetradLogger.getInstance().flush();
//        SearchGraphUtils.verifySepsetIntegrity(Map<Edge, List<Node>>, graph);
        return this.graph;
    }

    /**
     * Orients the given graph using CPC orientation with the conditional independence test provided in the
     * constructor.
     */
    public final Graph orientationForGraph(final Dag trueGraph) {
        final Graph graph = new EdgeListGraph(this.independenceTest.getVariables());

        for (final Edge edge : trueGraph.getEdges()) {
            final Node nodeA = edge.getNode1();
            final Node nodeB = edge.getNode2();

            final Node _nodeA = this.independenceTest.getVariable(nodeA.getName());
            final Node _nodeB = this.independenceTest.getVariable(nodeB.getName());

            graph.addUndirectedEdge(_nodeA, _nodeB);
        }

        SearchGraphUtils.pcOrientbk(this.knowledge, graph, graph.getNodes());
        orientUnshieldedTriples(this.knowledge, getIndependenceTest(), this.depth);
        final MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
        meekRules.setKnowledge(this.knowledge);
        meekRules.orientImplied(graph);

        return graph;
    }

    //==========================PRIVATE METHODS===========================//

// find cyclic loops to test chordal condition.


//    Tests if a node x is markov by using an independence test to test if x is independent of variables
//    not in its boundary conditional on its boundary and if x is independent of variables not in its future
//    conditional on its boundary.

    private boolean isMarkov(final Node node, final Graph graph) {
//        Graph dag = SearchGraphUtils.dagFromPattern(graph);
        System.out.println(graph);
        final IndependenceTest test = new IndTestDSep(graph);

        final Node x = node;

//        for (Node x : graph.getNodes()) {
        final List<Node> future = new ArrayList<>(future(x, graph));
        final List<Node> boundary = new ArrayList<>(boundary(x, graph));

        for (final Node y : graph.getNodes()) {
            if (y == x) {
                continue;
            }
            if (future.contains(y)) {
                continue;
            }
            if (boundary.contains(y)) {
                continue;
            }
            System.out.println(SearchLogUtils.independenceFact(x, y, boundary) + " " + test.isIndependent(x, y, boundary));
            if (!test.isIndependent(x, y, boundary)) {
                return false;
            }
        }
//        }

        return true;
    }

    //    For a node x, adds nodes y such that either y-x or y->x to the boundary of x
    private Set<Node> boundary(final Node x, final Graph graph) {
        final Set<Node> boundary = new HashSet<>();
        final List<Node> adj = graph.getAdjacentNodes(x);
        for (final Node y : adj) {
            if (graph.isParentOf(y, x) || Edges.isUndirectedEdge(graph.getEdge(x, y))) {
                boundary.add(y);
            }
        }
        return boundary;
    }

    //      For a node x, adds nodes y such that either x->..->y or x-..-..->..->y to the future of x
    private Set<Node> future(final Node x, final Graph graph) {
        final Set<Node> futureNodes = new HashSet<>();
        final LinkedList path = new LinkedList<>();
        VcpcAlt.futureNodeVisit(graph, x, path, futureNodes);
        if (futureNodes.contains(x)) {
            futureNodes.remove(x);
        }
        final List<Node> adj = graph.getAdjacentNodes(x);
        for (final Node y : adj) {
            if (graph.isParentOf(y, x) || Edges.isUndirectedEdge(graph.getEdge(x, y))) {
                futureNodes.remove(y);
            }
        }
        return futureNodes;
    }

    //    Constraints to guarantee future path conditions met. After traversing the entire path,
//    returns last node on path when satisfied, stops otherwise.
    private static Node traverseFuturePath(final Node node, final Edge edge1, final Edge edge2) {
        final Endpoint E1 = edge1.getProximalEndpoint(node);
        final Endpoint E2 = edge2.getProximalEndpoint(node);
        final Endpoint E3 = edge2.getDistalEndpoint(node);
        final Endpoint E4 = edge1.getDistalEndpoint(node);
//        if (E1 == Endpoint.ARROW && E2 == Endpoint.TAIL && E3 == Endpoint.TAIL) {
//            return null;
//        }
        if (E1 == Endpoint.ARROW && E2 == Endpoint.ARROW && E3 == Endpoint.TAIL) {
            return null;
        }
        if (E4 == Endpoint.ARROW) {
            return null;
        }
        if (E4 == Endpoint.TAIL && E1 == Endpoint.TAIL && E2 == Endpoint.TAIL && E3 == Endpoint.TAIL) {
            return null;
        }
        return edge2.getDistalNode(node);
    }

    //    Takes a triple n1-n2-child and adds child to futureNodes set if satisfies constraints for future.
//    Uses traverseFuturePath to add nodes to set.
    public static void futureNodeVisit(final Graph graph, final Node b, final LinkedList<Node> path, final Set<Node> futureNodes) {
        path.addLast(b);
        futureNodes.add(b);
        for (final Edge edge2 : graph.getEdges(b)) {
            final Node c;

            final int size = path.size();
            if (path.size() < 2) {
                c = edge2.getDistalNode(b);
                if (c == null) {
                    continue;
                }
                if (path.contains(c)) {
                    continue;
                }
            } else {
                final Node a = path.get(size - 2);
                final Edge edge1 = graph.getEdge(a, b);
                c = VcpcAlt.traverseFuturePath(b, edge1, edge2);
                if (c == null) {
                    continue;
                }
                if (path.contains(c)) {
                    continue;
                }
            }
            VcpcAlt.futureNodeVisit(graph, c, path, futureNodes);
        }
        path.removeLast();
    }


    private void logTriples() {
        TetradLogger.getInstance().log("info", "\nCollider triples:");

        for (final Triple triple : this.colliderTriples) {
            TetradLogger.getInstance().log("info", "Collider: " + triple);
        }

        TetradLogger.getInstance().log("info", "\nNoncollider triples:");

        for (final Triple triple : this.noncolliderTriples) {
            TetradLogger.getInstance().log("info", "Noncollider: " + triple);
        }

        TetradLogger.getInstance().log("info", "\nAmbiguous triples (i.e. list of triples for which " +
                "\nthere is ambiguous data about whether they are colliders or not):");

        for (final Triple triple : getAmbiguousTriples()) {
            TetradLogger.getInstance().log("info", "Ambiguous: " + triple);
        }
    }

    private void orientUnshieldedTriples(final IKnowledge knowledge,
                                         final IndependenceTest test, final int depth) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

//        System.out.println("orientUnshieldedTriples 1");

        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();
        this.ambiguousTriples = new HashSet<>();
        final List<Node> nodes = this.graph.getNodes();

        for (final Node y : nodes) {
            final List<Node> adjacentNodes = this.graph.getAdjacentNodes(y);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            final ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                final Node x = adjacentNodes.get(combination[0]);
                final Node z = adjacentNodes.get(combination[1]);

                if (this.graph.isAdjacentTo(x, z)) {
                    continue;
                }

                getAllTriples().add(new Triple(x, y, z));
                final SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType(x, y, z, test, depth, this.graph, this.verbose);
//                SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType2(x, y, z, test, depth, graph);

                if (type == SearchGraphUtils.CpcTripleType.COLLIDER) {
                    if (colliderAllowed(x, y, z, knowledge)) {
                        this.graph.setEndpoint(x, y, Endpoint.ARROW);
                        this.graph.setEndpoint(z, y, Endpoint.ARROW);

                        TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
                    }

                    this.colliderTriples.add(new Triple(x, y, z));
                } else if (type == SearchGraphUtils.CpcTripleType.AMBIGUOUS) {
                    final Triple triple = new Triple(x, y, z);
                    this.ambiguousTriples.add(triple);
                    this.graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                    final Edge edge = Edges.undirectedEdge(x, z);
                    this.definitelyNonadjacencies.add(edge);
                } else {
                    this.noncolliderTriples.add(new Triple(x, y, z));
                }
            }
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    private void orientUnshieldedTriplesConcurrent(final IKnowledge knowledge,
                                                   final IndependenceTest test, final int depth) {
        final ExecutorService executor = Executors.newFixedThreadPool(this.NTHREDS);

        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        final Graph graph = new EdgeListGraph(getGraph());

//        System.out.println("orientUnshieldedTriples 1");

        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();
        this.ambiguousTriples = new HashSet<>();
        final List<Node> nodes = graph.getNodes();

        for (final Node _y : nodes) {
            final Node y = _y;

            final List<Node> adjacentNodes = graph.getAdjacentNodes(y);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            final ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                final Node x = adjacentNodes.get(combination[0]);
                final Node z = adjacentNodes.get(combination[1]);

                if (graph.isAdjacentTo(x, z)) {
                    continue;
                }

                final Runnable worker = new Runnable() {
                    @Override
                    public void run() {

                        getAllTriples().add(new Triple(x, y, z));
                        final SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType(x, y, z, test, depth, getGraph(), VcpcAlt.this.verbose);
//                        SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType2(x, y, z, test, depth, getGraph());
//                        SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType4(x, y, z, test, depth, getGraph());
//
                        if (type == SearchGraphUtils.CpcTripleType.COLLIDER) {
                            if (colliderAllowed(x, y, z, knowledge)) {
                                getGraph().setEndpoint(x, y, Endpoint.ARROW);
                                getGraph().setEndpoint(z, y, Endpoint.ARROW);

                                TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
                            }

                            VcpcAlt.this.colliderTriples.add(new Triple(x, y, z));
                        } else if (type == SearchGraphUtils.CpcTripleType.AMBIGUOUS) {
                            final Triple triple = new Triple(x, y, z);
                            VcpcAlt.this.ambiguousTriples.add(triple);
                            getGraph().addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                        } else {
                            VcpcAlt.this.noncolliderTriples.add(new Triple(x, y, z));
                        }
                    }
                };

                executor.execute(worker);
            }
        }

        // This will make the executor accept no new threads
        // and finish all existing threads in the queue
        executor.shutdown();
        try {
            // Wait until all threads are finish
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            System.out.println("Finished all threads");
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    private boolean colliderAllowed(final Node x, final Node y, final Node z, final IKnowledge knowledge) {
        return VcpcAlt.isArrowpointAllowed1(x, y, knowledge) &&
                VcpcAlt.isArrowpointAllowed1(z, y, knowledge);
    }

    public static boolean isArrowpointAllowed1(final Node from, final Node to,
                                               final IKnowledge knowledge) {
        return knowledge == null || !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    public Map<Edge, List<Node>> getApparentlyNonadjacencies() {
        return this.apparentlyNonadjacencies;
    }

    public boolean isDoOrientation() {
        return this.doOrientation;
    }

    public void setDoOrientation(final boolean doOrientation) {
        this.doOrientation = doOrientation;
    }

    /**
     * The graph that's constructed during the search.
     */
    public Graph getGraph() {
        return this.graph;
    }

    public void setGraph(final Graph graph) {
        this.graph = graph;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }


}


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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.IndependenceFacts;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.CombinationGenerator;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.util.*;

/**
 * Implements a convervative version of PC, in which the Markov condition is assumed but faithfulness is tested
 * locally.
 *
 * @author josephramsey (this version).
 * @version $Id: $Id
 */
public final class VcPc implements IGraphSearch {

    /**
     * The independence test used for the PC search.
     */
    private final IndependenceTest independenceTest;
    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();
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
    private boolean meekPreventCycles;
    /**
     * The sepsets.
     */
    private Map<Edge, Set<Node>> apparentlyNonadjacencies;

    /**
     * Whether verbose output about independencies is output.
     */
    private boolean verbose;

    /**
     * Document me.
     */
    private IndependenceFacts facts;


    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a CPC algorithm that uses the given independence test as oracle. This does not make a copy of the
     * independence test, for fear of duplicating the data set!
     *
     * @param independenceTest a {@link edu.cmu.tetrad.search.IndependenceTest} object
     */
    public VcPc(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }

    //==============================PUBLIC METHODS========================//

    //    Constraints to guarantee future path conditions met. After traversing the entire path,
//    returns last node on path when satisfied, stops otherwise.
    private static Node traverseFuturePath(Node node, Edge edge1, Edge edge2) {
        Endpoint E1 = edge1.getProximalEndpoint(node);
        Endpoint E2 = edge2.getProximalEndpoint(node);
        Endpoint E3 = edge2.getDistalEndpoint(node);
        Endpoint E4 = edge1.getDistalEndpoint(node);
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

    /**
     * <p>futureNodeVisit.</p>
     *
     * @param graph       a {@link edu.cmu.tetrad.graph.Graph} object
     * @param b           a {@link edu.cmu.tetrad.graph.Node} object
     * @param path        a {@link java.util.LinkedList} object
     * @param futureNodes a {@link java.util.Set} object
     */
    public static void futureNodeVisit(Graph graph, Node b, LinkedList<Node> path, Set<Node> futureNodes) {
        path.addLast(b);
        futureNodes.add(b);
        for (Edge edge2 : graph.getEdges(b)) {
            Node c;

            int size = path.size();
            if (path.size() < 2) {
                c = edge2.getDistalNode(b);
            } else {
                Node a = path.get(size - 2);
                Edge edge1 = graph.getEdge(a, b);
                c = VcPc.traverseFuturePath(b, edge1, edge2);
            }
            if (c == null) {
                continue;
            }
            if (path.contains(c)) {
                continue;
            }
            VcPc.futureNodeVisit(graph, c, path, futureNodes);
        }
        path.removeLast();
    }

    /**
     * <p>isArrowheadAllowed1.</p>
     *
     * @param from      a {@link edu.cmu.tetrad.graph.Node} object
     * @param to        a {@link edu.cmu.tetrad.graph.Node} object
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
     * @return a boolean
     */
    public static boolean isArrowheadAllowed1(Node from, Node to,
                                              Knowledge knowledge) {
        return knowledge == null || !knowledge.isRequired(to.toString(), from.toString()) &&
                                    !knowledge.isForbidden(from.toString(), to.toString());
    }

    /**
     * <p>isMeekPreventCycles.</p>
     *
     * @return true just in case edges will not be added if they would create cycles.
     */
    public boolean isMeekPreventCycles() {
        return this.meekPreventCycles;
    }

    /**
     * Sets to true just in case edges will not be added if they would create cycles.
     *
     * @param meekPreventCycles a boolean
     */
    public void setMeekPreventCycles(boolean meekPreventCycles) {
        this.meekPreventCycles = meekPreventCycles;
    }

    /**
     * <p>Getter for the field <code>elapsedTime</code>.</p>
     *
     * @return the elapsed time of search in milliseconds, after <code>search()</code> has been run.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return the knowledge specification used in the search. Non-null.
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
        this.knowledge = new Knowledge(knowledge);
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
     * @return the depth of the search--that is, the maximum number of variables conditioned on in any conditional
     * independence test.
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * Sets the maximum number of variables conditioned on in any conditional independence test. If set to -1, the value
     * of 1000 will be used. May not be set to Integer.MAX_VALUE, due to a Java bug on multi-core systems.
     *
     * @param depth a int
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
     * <p>Getter for the field <code>ambiguousTriples</code>.</p>
     *
     * @return the set of ambiguous triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    /**
     * <p>Getter for the field <code>colliderTriples</code>.</p>
     *
     * @return the set of collider triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getColliderTriples() {
        return new HashSet<>(this.colliderTriples);
    }

    /**
     * <p>Getter for the field <code>noncolliderTriples</code>.</p>
     *
     * @return the set of noncollider triples found during the most recent run of the algorithm. Non-null after a call
     * to <code>search()</code>.
     */
    public Set<Triple> getNoncolliderTriples() {
        return new HashSet<>(this.noncolliderTriples);
    }

    /**
     * <p>getAdjacencies.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Edge> getAdjacencies() {
        return new HashSet<>(this.graph.getEdges());
    }

    //==========================PRIVATE METHODS===========================//

    /**
     * <p>getApparentNonadjacencies.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Edge> getApparentNonadjacencies() {
        return new HashSet<>(this.apparentlyNonadjacencies.keySet());
    }

    /**
     * <p>getDefiniteNonadjacencies.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Edge> getDefiniteNonadjacencies() {
        return new HashSet<>(this.definitelyNonadjacencies);
    }

    //  modified FAS into VCFAS; added in definitelyNonadjacencies set of edges.

    /**
     * <p>search.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph search() throws InterruptedException {
        IndependenceTest independenceTest = getIndependenceTest();

        if (verbose) {
            TetradLogger.getInstance().log("Starting VCCPC algorithm");
            TetradLogger.getInstance().log("Independence test = " + independenceTest + ".");
        }

        this.ambiguousTriples = new HashSet<>();
        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();
        VcFas fas = new VcFas(independenceTest);
        this.definitelyNonadjacencies = new HashSet<>();

        long startTime = MillisecondTimes.timeMillis();

        List<Node> allNodes = independenceTest.getVariables();

        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(this.verbose);

        // Note that we are ignoring the sepset map returned by this method
        // on purpose; it is not used in this search.
        this.graph = fas.search();

        this.apparentlyNonadjacencies = fas.getApparentlyNonadjacencies();

        if (this.verbose) {
            System.out.println("CPC orientation...");
        }
        GraphSearchUtils.pcOrientbk(this.knowledge, this.graph, allNodes, verbose);
        orientUnshieldedTriples(this.knowledge, independenceTest, getDepth());
        MeekRules meekRules = new MeekRules();

        meekRules.setMeekPreventCycles(this.meekPreventCycles);
        meekRules.setKnowledge(this.knowledge);
        meekRules.setVerbose(verbose);

        meekRules.orientImplied(this.graph);


        List<Triple> ambiguousTriples = new ArrayList<>(this.graph.getAmbiguousTriples());

        int[] dims = new int[ambiguousTriples.size()];

        for (int i = 0; i < ambiguousTriples.size(); i++) {
            dims[i] = 2;
        }

//        CPDAG Search:

        List<Graph> CPDAG = new ArrayList<>();
        Map<Graph, List<Triple>> newColliders = new IdentityHashMap<>();
        Map<Graph, List<Triple>> newNonColliders = new IdentityHashMap<>();

//      Using combination generator to generate a list of combinations of ambiguous triples dismabiguated into colliders
//      and non-colliders. The combinations are added as graphs to the list CPDAG. The graphs are then subject to
//      basic rules to ensure consistent CPDAG.

        CombinationGenerator generator = new CombinationGenerator(dims);
        int[] combination;

        while ((combination = generator.next()) != null) {
            Graph _graph = new EdgeListGraph(this.graph);
            newColliders.put(_graph, new ArrayList<>());
            newNonColliders.put(_graph, new ArrayList<>());

            for (int k = 0; k < combination.length; k++) {
                Triple triple = ambiguousTriples.get(k);
                _graph.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());

                if (combination[k] == 0) {
                    newColliders.get(_graph).add(triple);
                    Node x = triple.getX();
                    Node y = triple.getY();
                    Node z = triple.getZ();

                    _graph.setEndpoint(x, y, Endpoint.ARROW);
                    _graph.setEndpoint(z, y, Endpoint.ARROW);

                }
                if (combination[k] == 1) {
                    newNonColliders.get(_graph).add(triple);
                }
            }
            CPDAG.add(_graph);
        }

        ///    Takes CPDAG and runs them through basic constraints to ensure consistent CPDAG (e.g. no cycles, no bidirected edges).
        GRAPH:
        for (Graph graph : new ArrayList<>(CPDAG)) {
            List<Triple> colliders = newColliders.get(graph);
            List<Triple> nonColliders = newNonColliders.get(graph);


            for (Triple triple : colliders) {
                Node x = triple.getX();
                Node y = triple.getY();
                Node z = triple.getZ();

                if (graph.getEdge(x, y).pointsTowards(x) || (graph.getEdge(y, z).pointsTowards(z))) {
                    CPDAG.remove(graph);
                    continue GRAPH;
                }
            }

            for (Triple triple : colliders) {
                Node x = triple.getX();
                Node y = triple.getY();
                Node z = triple.getZ();

                graph.setEndpoint(x, y, Endpoint.ARROW);
                graph.setEndpoint(z, y, Endpoint.ARROW);
            }

            for (Triple triple : nonColliders) {
                Node x = triple.getX();
                Node y = triple.getY();
                Node z = triple.getZ();

                if (graph.getEdge(x, y).pointsTowards(y)) {
                    graph.removeEdge(y, z);
                    graph.addDirectedEdge(y, z);
                }
                if (graph.getEdge(y, z).pointsTowards(y)) {
                    graph.removeEdge(x, y);
                    graph.addDirectedEdge(y, x);
                }
            }

            for (Edge edge : graph.getEdges()) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();
                if (Edges.isBidirectedEdge(edge)) {
                    graph.removeEdge(x, y);
                    graph.addUndirectedEdge(x, y);
                }
            }

            MeekRules rules = new MeekRules();
            rules.setVerbose(verbose);
            rules.orientImplied(graph);
            if (graph.paths().existsDirectedCycle()) {
                CPDAG.remove(graph);
            }

        }


////        4/8/15 Local Relative Markov (M2)

        MARKOV:

        for (Edge edge : this.apparentlyNonadjacencies.keySet()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            for (Graph _graph : new ArrayList<>(CPDAG)) {

                Set<Node> boundaryX = new HashSet<>(boundary(x, _graph));
                Set<Node> boundaryY = new HashSet<>(boundary(y, _graph));
                Set<Node> futureX = new HashSet<>(future(x, _graph));
                Set<Node> futureY = new HashSet<>(future(y, _graph));

                if (y == x) {
                    continue;
                }
                if (boundaryX.contains(y) || boundaryY.contains(x)) {
                    continue;
                }
                IndependenceTest test = this.independenceTest;

                if (!futureX.contains(y)) {
                    if (!test.checkIndependence(x, y, boundaryX).isIndependent()) {
                        continue MARKOV;
                    }

                }

                if (!futureY.contains(x)) {
                    if (!test.checkIndependence(y, x, boundaryY).isIndependent()) {
                        continue MARKOV;
                    }
                }

            }
            this.definitelyNonadjacencies.add(edge);
//            apparentlyNonadjacencies.remove(edge);

        }

        for (Edge edge : this.definitelyNonadjacencies) {
            if (this.apparentlyNonadjacencies.containsKey(edge)) {
                this.apparentlyNonadjacencies.keySet().remove(edge);
            }
        }


        //Modified VCPC to be faster but less correct 4/14/15

        System.out.println("VCPC:");

        long endTime = MillisecondTimes.timeMillis();
        this.elapsedTime = endTime - startTime;

        System.out.println("Search Time (seconds):" + (this.elapsedTime) / 1000 + " s");
        System.out.println("Search Time (milli):" + this.elapsedTime + " ms");

        System.out.println("# of Apparent Nonadj: " + this.apparentlyNonadjacencies.size());
        System.out.println("# of Definite Nonadj: " + this.definitelyNonadjacencies.size());

        if (verbose) {
            TetradLogger.getInstance().log("\n Apparent Non-adjacencies" + this.apparentlyNonadjacencies);
            TetradLogger.getInstance().log("\n Definite Non-adjacencies" + this.definitelyNonadjacencies);
            TetradLogger.getInstance().log("Elapsed time = " + (this.elapsedTime) / 1000. + " s");
            TetradLogger.getInstance().log("Finishing CPC algorithm.");
        }

        return this.graph;
    }

    //    For a node x, adds nodes y such that either y-x or y->x to the boundary of x
    private Set<Node> boundary(Node x, Graph graph) {
        Set<Node> boundary = new HashSet<>();
        List<Node> adj = graph.getAdjacentNodes(x);
        for (Node y : adj) {
            if (graph.isParentOf(y, x) || Edges.isUndirectedEdge(graph.getEdge(x, y))) {
                boundary.add(y);
            }
        }
        return boundary;
    }

    //      For a node x, adds nodes y such that either x->..->y or x-..-..->..->y to the future of x
    private Set<Node> future(Node x, Graph graph) {
        Set<Node> futureNodes = new HashSet<>();
        LinkedList<Node> path = new LinkedList<>();
        VcPc.futureNodeVisit(graph, x, path, futureNodes);
        futureNodes.remove(x);
        List<Node> adj = graph.getAdjacentNodes(x);
        for (Node y : adj) {
            if (graph.isParentOf(y, x) || Edges.isUndirectedEdge(graph.getEdge(x, y))) {
                futureNodes.remove(y);
            }
        }
        return futureNodes;
    }

    private void orientUnshieldedTriples(Knowledge knowledge,
                                         IndependenceTest test, int depth) throws InterruptedException {
        TetradLogger.getInstance().log("Starting Collider Orientation:");

//        System.out.println("orientUnshieldedTriples 1");

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
                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (this.graph.isAdjacentTo(x, z)) {
                    continue;
                }

                CpcTripleType type = getPopulationTripleType(x, y, z, test, depth, this.graph, this.verbose);
//                SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType2(x, y, z, test, depth, graph);

                if (type == CpcTripleType.COLLIDER) {
                    if (colliderAllowed(x, y, z, knowledge)) {
                        this.graph.setEndpoint(x, y, Endpoint.ARROW);
                        this.graph.setEndpoint(z, y, Endpoint.ARROW);

                        String message = LogUtilsSearch.colliderOrientedMsg(x, y, z);
                        TetradLogger.getInstance().log(message);
                    }

                    this.colliderTriples.add(new Triple(x, y, z));
                } else if (type == CpcTripleType.AMBIGUOUS) {
                    Triple triple = new Triple(x, y, z);
                    this.ambiguousTriples.add(triple);
                    this.graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                    Edge edge = Edges.undirectedEdge(x, z);
                    this.definitelyNonadjacencies.add(edge);
                } else {
                    this.noncolliderTriples.add(new Triple(x, y, z));
                }
            }
        }

        TetradLogger.getInstance().log("Finishing Collider Orientation.");
    }

    /**
     * <p>getPopulationTripleType.</p>
     *
     * @param x       a {@link edu.cmu.tetrad.graph.Node} object
     * @param y       a {@link edu.cmu.tetrad.graph.Node} object
     * @param z       a {@link edu.cmu.tetrad.graph.Node} object
     * @param test    a {@link edu.cmu.tetrad.search.IndependenceTest} object
     * @param depth   a int
     * @param graph   a {@link edu.cmu.tetrad.graph.Graph} object
     * @param verbose a boolean
     * @return a {@link edu.cmu.tetrad.search.work_in_progress.VcPc.CpcTripleType} object
     * @throws InterruptedException if any
     */
    public CpcTripleType getPopulationTripleType(Node x, Node y, Node z,
                                                 IndependenceTest test, int depth,
                                                 Graph graph, boolean verbose) throws InterruptedException {

        if (this.facts == null) throw new NullPointerException("Need independence facts as a parent");

        // JOE HERE ARE THE INDEPENDENCE FACTS
        System.out.println("NameS" + this.facts.getVariableNames());

        int numSepsetsContainingY = 0;
        int numSepsetsNotContainingY = 0;

        List<Node> _nodes = new ArrayList<>(graph.getAdjacentNodes(x));
        _nodes.remove(z);
        TetradLogger.getInstance().log("Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = FastMath.min(_depth, _nodes.size());

        while (true) {
            for (int d = 0; d <= _depth; d++) {
                ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg.next()) != null) {
                    Set<Node> cond = GraphUtils.asSet(choice, _nodes);

                    // JOE HERE IS WHERE I ASK THE FACTS INDEPENDENCE QUESTIONS. I'M NEVER ABLE TO GET WITHIN THE IF STATEMENT TO "SYSTEM.OUT.."

                    if (this.facts.isIndependent(x, z, cond)) {
//                        if (verbose) {
                        System.out.println("Indep Fact said: " + x + " _||_ " + z + " | " + cond);
//                        }

                        if (cond.contains(y)) {
                            numSepsetsContainingY++;
                        } else {
                            numSepsetsNotContainingY++;
                        }
                    } else {
                        System.out.println("This is not Indep by facts: " + x + " _||_ " + z + " | " + cond);
                    }

                    if (numSepsetsContainingY > 0 && numSepsetsNotContainingY > 0) {
                        return CpcTripleType.AMBIGUOUS;
                    }
                }
            }

            _nodes = new ArrayList<>(graph.getAdjacentNodes(z));
            _nodes.remove(x);
            TetradLogger.getInstance().log("Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

            _depth = depth;
            if (_depth == -1) {
                _depth = 1000;
            }
            _depth = FastMath.min(_depth, _nodes.size());

            for (int d = 0; d <= _depth; d++) {
                ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg.next()) != null) {
                    Set<Node> cond = GraphUtils.asSet(choice, _nodes);

                    if (test.checkIndependence(x, z, cond).isIndependent()) {
//                        System.out.println("Indep: " + x + " _||_ " + z + " | " + cond);

                        if (cond.contains(y)) {
                            numSepsetsContainingY++;
                        } else {
                            numSepsetsNotContainingY++;
                        }
                    }

                    if (numSepsetsContainingY > 0 && numSepsetsNotContainingY > 0) {
                        return CpcTripleType.AMBIGUOUS;
                    }
                }
            }

            break;
        }

        if (numSepsetsContainingY > 0) {
            return CpcTripleType.NONCOLLIDER;
        } else {
            if (verbose) {
                System.out.println("Orienting " + x + "-->" + y + "&lt;-" + z);
            }
            return CpcTripleType.COLLIDER;
        }
    }

    private boolean colliderAllowed(Node x, Node y, Node z, Knowledge knowledge) {
        return VcPc.isArrowheadAllowed1(x, y, knowledge) &&
               VcPc.isArrowheadAllowed1(z, y, knowledge);
    }

    /**
     * The graph that's constructed during the search.
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return this.graph;
    }

    /**
     * <p>Setter for the field <code>graph</code>.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    /**
     * <p>Setter for the field <code>verbose</code>.</p>
     *
     * @param verbose a boolean
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * <p>Setter for the field <code>facts</code>.</p>
     *
     * @param facts a {@link edu.cmu.tetrad.data.IndependenceFacts} object
     */
    public void setFacts(IndependenceFacts facts) {
        this.facts = facts;
    }

    /**
     * An enum of triple types.
     */
    public enum CpcTripleType {

        /**
         * The triple is a collider.
         */
        COLLIDER,

        /**
         * The triple is a noncollider.
         */
        NONCOLLIDER,

        /**
         * The triple is ambiguous.
         */
        AMBIGUOUS
    }
}


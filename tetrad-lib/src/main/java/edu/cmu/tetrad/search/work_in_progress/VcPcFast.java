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
public final class VcPcFast implements IGraphSearch {

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
    public VcPcFast(IndependenceTest independenceTest) {
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
                c = VcPcFast.traverseFuturePath(b, edge1, edge2);
            }
            if (c == null) {
                continue;
            }
            if (path.contains(c)) {
                continue;
            }
            VcPcFast.futureNodeVisit(graph, c, path, futureNodes);
        }
        path.removeLast();
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

    /**
     * <p>getApparentNonadjacencies.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Edge> getApparentNonadjacencies() {
        return new HashSet<>(this.apparentlyNonadjacencies.keySet());
    }

    //==========================PRIVATE METHODS===========================//

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
    public Graph search() {
        TetradLogger.getInstance().forceLogMessage("Starting VCCPC algorithm");
        TetradLogger.getInstance().forceLogMessage("Independence test = " + getIndependenceTest() + ".");
        this.ambiguousTriples = new HashSet<>();
        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();
        VcFas fas = new VcFas(getIndependenceTest());
        this.definitelyNonadjacencies = new HashSet<>();

//        this.logger.log("info", "Variables " + independenceTest.getVariable());

        long startTime = MillisecondTimes.timeMillis();

        List<Node> allNodes = getIndependenceTest().getVariables();


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
        GraphSearchUtils.pcOrientbk(this.knowledge, this.graph, allNodes);
        orientUnshieldedTriples(this.knowledge, getIndependenceTest(), getDepth());
//            orientUnshieldedTriplesConcurrent(knowledge, getIndependenceTest(), getMaxIndegree());
        MeekRules meekRules = new MeekRules();

        meekRules.setMeekPreventCycles(this.meekPreventCycles);
        meekRules.setKnowledge(this.knowledge);

        meekRules.orientImplied(this.graph);


        List<Triple> ambiguousTriples = new ArrayList<>(this.graph.getAmbiguousTriples());

        int[] dims = new int[ambiguousTriples.size()];

        for (int i = 0; i < ambiguousTriples.size(); i++) {
            dims[i] = 2;
        }

//        Pattern Search:

        List<Graph> patterns = new ArrayList<>();
        Map<Graph, List<Triple>> newColliders = new IdentityHashMap<>();
        Map<Graph, List<Triple>> newNonColliders = new IdentityHashMap<>();

//      Using combination generator to generate a list of combinations of ambiguous triples dismabiguated into colliders
//      and non-colliders. The combinations are added as graphs to the list patterns. The graphs are then subject to
//      basic rules to ensure consistent patterns.


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
            patterns.add(_graph);
        }

        ///    Takes patterns and runs them through basic constraints to ensure consistent patterns (e.g. no cycles, no bidirected edges).

        GRAPH:
        for (Graph graph : new ArrayList<>(patterns)) {


            List<Triple> colliders = newColliders.get(graph);
            List<Triple> nonColliders = newNonColliders.get(graph);


            for (Triple triple : colliders) {
                Node x = triple.getX();
                Node y = triple.getY();
                Node z = triple.getZ();

                if (graph.getEdge(x, y).pointsTowards(x) || (graph.getEdge(y, z).pointsTowards(z))) {
                    patterns.remove(graph);
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
            rules.orientImplied(graph);
            if (graph.paths().existsDirectedCycle()) {
                patterns.remove(graph);
            }

        }


////        4/8/15 Local Relative Markov (M2)

        MARKOV:

        for (Edge edge : this.apparentlyNonadjacencies.keySet()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            for (Graph _graph : new ArrayList<>(patterns)) {

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

//        System.out.println("# of patterns: " + patterns.size());
        long endTime = MillisecondTimes.timeMillis();
        this.elapsedTime = endTime - startTime;

        System.out.println("Search Time (seconds):" + (this.elapsedTime) / 1000 + " s");
        System.out.println("Search Time (milli):" + this.elapsedTime + " ms");

        System.out.println("# of Apparent Nonadj: " + this.apparentlyNonadjacencies.size());
        System.out.println("# of Definite Nonadj: " + this.definitelyNonadjacencies.size());


        TetradLogger.getInstance().forceLogMessage("\n Apparent Non-adjacencies" + this.apparentlyNonadjacencies);
        TetradLogger.getInstance().forceLogMessage("\n Definite Non-adjacencies" + this.definitelyNonadjacencies);
        TetradLogger.getInstance().forceLogMessage("Elapsed time = " + (this.elapsedTime) / 1000. + " s");
        TetradLogger.getInstance().forceLogMessage("Finishing CPC algorithm.");
        TetradLogger.getInstance().flush();
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
        VcPcFast.futureNodeVisit(graph, x, path, futureNodes);
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
                                         IndependenceTest test, int depth) {
        TetradLogger.getInstance().forceLogMessage("Starting Collider Orientation:");

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
                        TetradLogger.getInstance().forceLogMessage(message);
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

        TetradLogger.getInstance().forceLogMessage("Finishing Collider Orientation.");
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
     * @return a {@link edu.cmu.tetrad.search.work_in_progress.VcPcFast.CpcTripleType} object
     */
    public CpcTripleType getPopulationTripleType(Node x, Node y, Node z,
                                                 IndependenceTest test, int depth,
                                                 Graph graph, boolean verbose) {
//        if ((x.getNode().equals("X5") && z.getNode().equals("X7"))
//            || (x.getNode().equals("X7") && z.getNode().equals("X5"))) {
//            System.out.println();
//        }

        // JOE HERE ARE THE FACTS.

        setFacts(this.facts);
        System.out.println("NameS" + this.facts.getVariableNames());


        int numSepsetsContainingY = 0;
        int numSepsetsNotContainingY = 0;

        List<Node> _nodes = new ArrayList<>(graph.getAdjacentNodes(x));


        _nodes.remove(z);
        TetradLogger.getInstance().forceLogMessage("Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

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

                    // JOE THIS IS WHERE I ASK THE FACTS INDEPENDENCE QUESTIONS.

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
            TetradLogger.getInstance().forceLogMessage("Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

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
        if (!GraphSearchUtils.isArrowheadAllowed(x, y, knowledge)) return false;
        return GraphSearchUtils.isArrowheadAllowed(z, y, knowledge);
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
     * An enum of the types of triples that can be found in a graph.
     */
    public enum CpcTripleType {

        /**
         * Constant <code>COLLIDER</code>
         */
        COLLIDER,

        /**
         * Constant <code>NONCOLLIDER</code>
         */
        NONCOLLIDER,

        /**
         * Constant <code>AMBIGUOUS</code>
         */
        AMBIGUOUS
    }


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


//        //  Local Relative Markox condition. Tests if X is markov with respect to Y in all patterns.
//
//        MARKOV:
//
//        for (Edge edge : apparentlyNonadjacencies.keySet()) {
//            Node x = edge.getNode1();
//
//            Node y = edge.getNode2();
//
//            for (Graph _graph : new ArrayList<Graph>(patterns)) {
//
//                List<Node> boundaryX = new ArrayList<Node>(boundary(x, _graph));
//                List<Node> boundaryY = new ArrayList<Node>(boundary(y, _graph));
//                List<Node> futureX = new ArrayList<Node>(future(x, _graph));
//                List<Node> futureY = new ArrayList<Node>(future(y, _graph));
//
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
//
//                IndependenceTest test = new IndTestMSep(_graph);
//                if (!test.isIndependent(x, y, boundaryX)) {
//                    continue MARKOV;
//                }
//                if (!test.isIndependent(y, x, boundaryY)) {
//                    continue MARKOV;
//                }
//            }
//            definitelyNonadjacencies.add(edge);
////            apparentlyNonadjacencies.remove(edge);
//
//        }
//
//        for (Edge edge : definitelyNonadjacencies) {
//            if (apparentlyNonadjacencies.keySet().contains(edge)) {
//                apparentlyNonadjacencies.keySet().remove(edge);
//            }
//        }


////        Build Power sets from boundary.
//
//        powerSet = new HashSet<List<Node>>();
//        Set<Node> ssX = new HashSet<Node>(boundary(x, _graph));
//        List<Node> listX = new ArrayList<Node>(ssX);
//        buildPowerSet(listX, listX.size());
//        Set<List<Node>> bdryX = powerSet;
//
//        powerSet = new HashSet<List<Node>>();
//        Set<Node> ssY = new HashSet<Node>(boundary(y, _graph));
//        List<Node> listY = new ArrayList<Node>(ssY);
//        buildPowerSet(listY, listY.size());
//        Set<List<Node>> bdryY = powerSet;
//
//


//
////        11/4/14 - Local "relative" Markov test: For each apparent non-adjacency X-Y, and
////        smallest subset of boundaries for X and Y, Sx and Sy such that for SOME pattern:
////                X _||_ Y | Sx and X_||_Y | Sy.
////                If such smallest subsets of the boundaries for X and Y are found for SOME pattern,
////                then mark the edge as definitely non-adjacent.
//
//        MARKOV:
//
//        for (Edge edge : apparentlyNonadjacencies.keySet()) {
//            Node x = edge.getNode1();
//            Node y = edge.getNode2();
//
//            PATT:
//
//            for (Graph _graph : new ArrayList<Graph>(patterns)) {
//                Set<Node> ssX = new HashSet<Node>(boundary(x, _graph));
//                List<Node> listX = new ArrayList<Node>(ssX);
//                Set<Node> ssY = new HashSet<Node>(boundary(y, _graph));
//                List<Node> listY = new ArrayList<Node>(ssY);
//                List<Node> boundaryX = new ArrayList<Node>(boundary(x, _graph));
//                List<Node> boundaryY = new ArrayList<Node>(boundary(y, _graph));
//                List<Node> futureX = new ArrayList<Node>(future(x, _graph));
//                List<Node> futureY = new ArrayList<Node>(future(y, _graph));
//
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
//
//                IndependenceTest test = independenceTest;
//
//                DepthChoiceGenerator genX = new DepthChoiceGenerator(listX.size(), listX.size());
//                int[] choiceX;
//
//                while ((choiceX = genX.next()) !=null) {
//                    List<Node> z1 = DataGraphUtils.asList(choiceX, listX);
//
//                    if (!test.isIndependent(x, y, z1)) {
//                        continue;
//                    }
//
//                    DepthChoiceGenerator genY = new DepthChoiceGenerator(listY.size(), listY.size());
//                    int[] choiceY;
//
//                    while ((choiceY = genY.next()) !=null) {
//                        List<Node> z2 = DataGraphUtils.asList(choiceY, listY);
//
//                        if (!test.isIndependent(y, x, z2)) {
//                            continue;
//                        }
//                        continue PATT;
//                    }
//                    continue MARKOV;
//                }
//                continue MARKOV;
//            }
//            definitelyNonadjacencies.add(edge);
//        }


////        11/10/14 Find possible orientations of boundary of Y such that no unshielded colliders
////        result. E.g., for x-y-z, the possible orientations are x->y->z, x&lt;-y&lt;-z, and x&lt;-y->z.
////        For each orientation, calculate bdry(y) and ftre(y). Perform Markov tests for each possible
////        orientation - e.g. X_||_Y | bdry(Y). If the answer is yes for each orientation then X and Y
////        are definitely non-adjacent for that pattern. If they pass such a test for every pattern, then
////        they are definitely non-adjacent.
//
//        MARKOV:
//
//        for (Edge edge : apparentlyNonadjacencies.keySet()) {
//            Node x = edge.getNode1();
//            Node y = edge.getNode2();
//            IndependenceTest test = independenceTest;
//
//            for (Graph _graph : new ArrayList<Graph>(patterns)) {
//
//                List<Graph> dagPatternsX = dagPatterns(x, _graph);
//
//                for (Graph pattX : new ArrayList<Graph>(dagPatternsX)) {
//                    List<Node> boundaryX = new ArrayList<Node>(boundary(x, pattX));
//
//                    List<Node> futureX = new ArrayList<Node>(future(x, pattX));
//
//
//                    if (y == x) {
//                        continue;
//                    }
//                    if (futureX.contains(y)) {
//                        continue;
//                    }
//                    if (boundaryX.contains(y)) {
//                        continue;
//                    }
//
//                    if (!test.isIndependent(x, y, pattX.getParents(x))) {
//                        continue MARKOV;
//                    }
//                }
//
//                List<Graph> dagPatternsY = dagPatterns(y, _graph);
//
//                for (Graph pattY : new ArrayList<Graph>(dagPatternsY)) {
//
//                    List<Node> boundaryY = new ArrayList<Node>(boundary(y, pattY));
//
//                    List<Node> futureY = new ArrayList<Node>(future(y, pattY));
//
//                    if (y == x) {
//                        continue;
//                    }
//                    if (futureY.contains(x)) {
//                        continue;
//                    }
//                    if (boundaryY.contains(x)) {
//                        continue;
//                    }
//
//                    if (!test.isIndependent(x, y, pattY.getParents(y))) {
//                        continue MARKOV;
//                    }
//                }
//            }
//            definitelyNonadjacencies.add(edge);
//        }
//
//
//        for (Edge edge : definitelyNonadjacencies) {
//            if (apparentlyNonadjacencies.keySet().contains(edge)) {
//                apparentlyNonadjacencies.keySet().remove(edge);
//            }
//        }

//        List<Graph> patternss = new ArrayList<Graph>();


//        MARKOV:
//
//        for (Edge edge : apparentlyNonadjacencies.keySet()) {
//            Node x = edge.getNode1();
//            Node y = edge.getNode2();
//            IndependenceTest test = independenceTest;
//            List<Graph> ePatternsX = ePatterns(x, graph);
//
//            for (Graph pattX : new ArrayList<Graph>(ePatternsX)) {
//                List<Node> boundaryX = new ArrayList<Node>(boundary(x, pattX));
//                List<Node> futureX = new ArrayList<Node>(future(x, pattX));
//
//                if (y == x) { continue;}
//                if (boundaryX.contains(y)) { continue;}
//
//                if (futureX.contains(y)) {
//                    continue;
//                }
//
//                if (!test.isIndependent(x, y, pattX.getParents(x))) {
//                    continue MARKOV;
//                }
//            }
//
//            List<Graph> dagPatternsY = ePatterns(y, graph);
//
//            for (Graph pattY : new ArrayList<Graph>(dagPatternsY)) {
//
//                List<Node> boundaryY = new ArrayList<Node>(boundary(y, pattY));
//                List<Node> futureY = new ArrayList<Node>(future(y, pattY));
//
//                if (y == x) {continue;}
//                if (boundaryY.contains(x)) {continue;}
//
//                if (futureY.contains(x)) { continue;}
//
//
//                if (!test.isIndependent(x, y, pattY.getParents(y))) {
//                        continue MARKOV;
//                }
//            }
//
//            definitelyNonadjacencies.add(edge);
//        }
//
//        for (Edge edge : definitelyNonadjacencies) {
//            if (apparentlyNonadjacencies.keySet().contains(edge)) {
//                apparentlyNonadjacencies.keySet().remove(edge);
//            }
//        }
}


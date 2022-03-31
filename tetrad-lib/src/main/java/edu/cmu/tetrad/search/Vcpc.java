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
import edu.cmu.tetrad.data.IndependenceFacts;
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
public final class Vcpc implements GraphSearch {

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

    /**
     *
     */
    // the set of definitely non-adjacencies

    private Set<Edge> definitelyNonadjacencies;

    private Set<Node> markovInAllCPDAGs;

    private static Set<List<Node>> powerSet;


    private boolean aggressivelyPreventCycles;

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
    private boolean verbose;

    /**
     * Document me.
     */
    private IndependenceFacts facts;


    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a CPC algorithm that uses the given independence test as oracle. This does not make a copy of the
     * independence test, for fear of duplicating the data set!
     */
    public Vcpc(IndependenceTest independenceTest) {
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
    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
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
        return this.knowledge;
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

    public Set<Edge> getAdjacencies() {
        Set<Edge> adjacencies = new HashSet<>();
        for (Edge edge : this.graph.getEdges()) {
            adjacencies.add(edge);
        }
        return adjacencies;
    }

    public Set<Edge> getApparentNonadjacencies() {
        return new HashSet<>(this.apparentlyNonadjacencies.keySet());
    }

    public Set<Edge> getDefiniteNonadjacencies() {
        return new HashSet<>(this.definitelyNonadjacencies);
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
        IndependenceTest independenceTest = getIndependenceTest();
        this.logger.log("info", "Independence test = " + independenceTest + ".");
        this.allTriples = new HashSet<>();
        this.ambiguousTriples = new HashSet<>();
        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();
        Vcfas fas = new Vcfas(independenceTest);
        this.definitelyNonadjacencies = new HashSet<>();
        this.markovInAllCPDAGs = new HashSet<>();

//        this.logger.log("info", "Variables " + independenceTest.getVariable());

        long startTime = System.currentTimeMillis();

        if (independenceTest == null) {
            throw new NullPointerException();
        }

        List<Node> allNodes = independenceTest.getVariables();

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
            orientUnshieldedTriples(this.knowledge, independenceTest, getDepth());
//            orientUnshieldedTriplesConcurrent(knowledge, getIndependenceTest(), getMaxIndegree());
            MeekRules meekRules = new MeekRules();

            meekRules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
            meekRules.setKnowledge(this.knowledge);

            meekRules.orientImplied(this.graph);
        }


        List<Triple> ambiguousTriples = new ArrayList(this.graph.getAmbiguousTriples());

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
            newColliders.put(_graph, new ArrayList<Triple>());
            newNonColliders.put(_graph, new ArrayList<Triple>());
            for (Graph graph : newColliders.keySet()) {
//                System.out.println("$$$ " + newColliders.get(graph));
            }
            for (int k = 0; k < combination.length; k++) {
//                System.out.println("k = " + combination[k]);
                Triple triple = ambiguousTriples.get(k);
                _graph.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());


                if (combination[k] == 0) {
                    newColliders.get(_graph).add(triple);
//                    System.out.println(newColliders.get(_graph));
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

        List<Graph> _CPDAGs = new ArrayList<>(CPDAG);

        ///    Takes CPDAG and runs them through basic constraints to ensure consistent CPDAG (e.g. no cycles, no bidirected edges).

        GRAPH:

        for (Graph graph : new ArrayList<>(CPDAG)) {
//            _graph = new EdgeListGraph(graph);

//            System.out.println("graph = " + graph + " in keyset? " + newColliders.containsKey(graph));
//
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

//            for (Edge edge : graph.getEdges()) {
//                if (Edges.isBidirectedEdge(edge)) {
//                    CPDAG.remove(graph);
//                    continue Graph;
//                }
//            }

            MeekRules rules = new MeekRules();
            rules.orientImplied(graph);
            if (graph.existsDirectedCycle()) {
                CPDAG.remove(graph);
                continue GRAPH;
            }

        }


////        4/8/15 Local Relative Markov (M2)

        MARKOV:

        for (Edge edge : this.apparentlyNonadjacencies.keySet()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            for (Graph _graph : new ArrayList<>(CPDAG)) {

                List<Node> boundaryX = new ArrayList<>(boundary(x, _graph));
                List<Node> boundaryY = new ArrayList<>(boundary(y, _graph));
                List<Node> futureX = new ArrayList<>(future(x, _graph));
                List<Node> futureY = new ArrayList<>(future(y, _graph));

                if (y == x) {
                    continue;
                }
                if (boundaryX.contains(y) || boundaryY.contains(x)) {
                    continue;
                }
                IndependenceTest test = this.independenceTest;

                if (!futureX.contains(y)) {
                    if (!test.isIndependent(x, y, boundaryX)) {
                        continue MARKOV;
                    }

                }

                if (!futureY.contains(x)) {
                    if (!test.isIndependent(y, x, boundaryY)) {
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

//        IndependenceTest test = independenceTest;
////
//        MARKOV:
//        for (Edge edge : apparentlyNonadjacencies.keySet()) {
//            Node x = edge.getNode1();
//            Node y = edge.getNode2();
//            //        Build Power sets from boundary.
//            powerSet = new HashSet<List<Node>>();
//            Set<Node> ssX = new HashSet<Node>(boundary(x, graph));
//            List<Node> listX = new ArrayList<Node>(ssX);
//            buildPowerSet(listX, listX.size());
//            Set<List<Node>> bdryX = powerSet;
//            powerSet = new HashSet<List<Node>>();
//            Set<Node> ssY = new HashSet<Node>(boundary(y, graph));
//            List<Node> listY = new ArrayList<Node>(ssY);
//            buildPowerSet(listY, listY.size());
//            Set<List<Node>> bdryY = powerSet;
//            for (List<Node> boundaryX : bdryX) {
//                List<Node> futureX = new ArrayList<Node>(future(x, graph));
//                if (y == x) {
//                    continue;
//                }
//                if (boundaryX.contains(y)) {
//                    continue;
//                }
//                if (!futureX.contains(y)) {
//                    if (!test.isIndependent(x, y, boundaryX)) {
//                        continue MARKOV;
//                    }
//                }
//            }
//            for (List<Node> boundaryY : bdryY) {
//                List<Node> futureX = new ArrayList<Node>(future(x, graph));
//                List<Node> futureY = new ArrayList<Node>(future(y, graph));
//                if (y == x) {
//                    continue;
//                }
//                if (boundaryY.contains(x)) {
//                    continue;
//                }
//                if (!futureY.contains(x)) {
//                    if (!test.isIndependent(y, x, boundaryY)) {
//                        continue MARKOV;
//                    }
//                }
//            }
//            definitelyNonadjacencies.add(edge);
////            apparentlyNonadjacencies.remove(edge);
//        }
//        for (Edge edge : definitelyNonadjacencies) {
//            if (apparentlyNonadjacencies.keySet().contains(edge)) {
//                apparentlyNonadjacencies.keySet().remove(edge);
//            }
//        }

        System.out.println("VCPC:");

//        System.out.println("# of CPDAG: " + CPDAG.size());
        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - startTime;

        System.out.println("Search Time (seconds):" + (this.elapsedTime) / 1000 + " s");
        System.out.println("Search Time (milli):" + this.elapsedTime + " ms");

        System.out.println("# of Apparent Nonadj: " + this.apparentlyNonadjacencies.size());
        System.out.println("# of Definite Nonadj: " + this.definitelyNonadjacencies.size());

//        System.out.println("aMIGUOUS tRIPLES: " + ambiguousTriples);
//        System.out.println("Definitely Nonadjacencies:");
//        for (Edge edge : definitelyNonadjacencies) {
//            System.out.println(edge);
//        }
//        System.out.println("markov in all CPDAG:" + markovInAllCPDAGs);
////        System.out.println("CPDAG:" + CPDAG);
//        System.out.println("Apparently Nonadjacencies:");
//        for (Edge edge : apparentlyNonadjacencies.keySet()) {
//            System.out.println(edge);
//        }
//        System.out.println("Definitely Nonadjacencies:");
//        for (Edge edge : definitelyNonadjacencies) {
//            System.out.println(edge);
//        }

        TetradLogger.getInstance().log("apparentlyNonadjacencies", "\n Apparent Non-adjacencies" + this.apparentlyNonadjacencies);
        TetradLogger.getInstance().log("definitelyNonadjacencies", "\n Definite Non-adjacencies" + this.definitelyNonadjacencies);
//        TetradLogger.getInstance().log("CPDAG", "Disambiguated CPDAGs: " + CPDAG);
        TetradLogger.getInstance().log("graph", "\nReturning this graph: " + this.graph);
        TetradLogger.getInstance().log("info", "Elapsed time = " + (this.elapsedTime) / 1000. + " s");
        TetradLogger.getInstance().log("info", "Finishing CPC algorithm.");
//        logTriples();
        TetradLogger.getInstance().flush();
//        SearchGraphUtils.verifySepsetIntegrity(Map<Edge, List<Node>>, graph);
        return this.graph;
    }

    /**
     * Orients the given graph using CPC orientation with the conditional independence test provided in the
     * constructor.
     */
    public final Graph orientationForGraph(Dag trueGraph) {
        Graph graph = new EdgeListGraph(this.independenceTest.getVariables());
        for (Edge edge : trueGraph.getEdges()) {
            Node nodeA = edge.getNode1();
            Node nodeB = edge.getNode2();

            Node _nodeA = this.independenceTest.getVariable(nodeA.getName());
            Node _nodeB = this.independenceTest.getVariable(nodeB.getName());
            graph.addUndirectedEdge(_nodeA, _nodeB);
        }
        SearchGraphUtils.pcOrientbk(this.knowledge, graph, graph.getNodes());
        orientUnshieldedTriples(this.knowledge, getIndependenceTest(), this.depth);
        MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
        meekRules.setKnowledge(this.knowledge);
        meekRules.orientImplied(graph);
        return graph;
    }

    //==========================PRIVATE METHODS===========================//

//    Takes CPDAGs and, with respect to a node and its boundary, finds all possible combinations of orientations
//    of its boundary such that no new colliders are created. For each combination, a new CPDAG is added to the
//    list dagCPDAGs.

    private List<Graph> dagCPDAGs(Node x, Graph graph) {
        List<Graph> dagCPDAGs = new ArrayList<>();
        List<Node> boundaryX = new ArrayList<>(boundary(x, graph));

        BOUNDARY1:

        for (Node a : boundaryX) {
            Graph dag = new EdgeListGraph(graph);

            if (dag.getEdge(x, a).pointsTowards(a)) {
                continue;
            }

            if (Edges.isUndirectedEdge(dag.getEdge(x, a))) {
                dag.setEndpoint(a, x, Endpoint.ARROW);
            }


            List<Node> otherNodesX = new ArrayList<>(boundaryX);
            otherNodesX.remove(a);
            for (Node b : otherNodesX) {
                if (dag.getEdge(x, b).pointsTowards(x)) {
                    continue BOUNDARY1;
                }
                if (Edges.isUndirectedEdge(dag.getEdge(x, b))) {
                    List<Node> boundaryB = new ArrayList<>(boundary(b, dag));
                    boundaryB.remove(x);
                    for (Node c : boundaryB) {
                        if (dag.isParentOf(c, b)) {
                            continue BOUNDARY1;
                        }
                    }
                    dag.setEndpoint(x, b, Endpoint.ARROW);
                }
            }
            dagCPDAGs.add(dag);
        }

        Graph _dag = new EdgeListGraph(graph);
        List<Node> newCollider = new ArrayList<>();

        BOUNDARY2:

        for (Node v : boundaryX) {

            if (_dag.getEdge(x, v).pointsTowards(v)) {
                continue;
            }

            if (Edges.isUndirectedEdge(_dag.getEdge(x, v))) {

                _dag.setEndpoint(x, v, Endpoint.ARROW);

                List<Node> boundaryV = new ArrayList<>(boundary(v, _dag));
                boundaryV.remove(x);

                for (Node d : boundaryV) {
                    if (_dag.isParentOf(d, v)) {
                        newCollider.add(v);
                    }
                }

            }
        }
        if (newCollider.size() == 0) {
            dagCPDAGs.add(_dag);
        }
        return dagCPDAGs;
    }


    private List<Graph> eCPDAGs(Node x, Graph graph) {
        List<Graph> eCPDAGs = new ArrayList<>();
        List<Node> boundaryX = new ArrayList<>(boundary(x, graph));

        BOUNDARY1:

        for (Node a : boundaryX) {
            Graph CPDAG = new EdgeListGraph(graph);

            if (CPDAG.getEdge(x, a).pointsTowards(a)) {
                continue;
            }

            if (Edges.isUndirectedEdge(CPDAG.getEdge(x, a))) {
                CPDAG.setEndpoint(a, x, Endpoint.ARROW);
            }

            List<Node> otherNodesX = new ArrayList<>(boundaryX);
            otherNodesX.remove(a);
            for (Node b : otherNodesX) {
                if (CPDAG.getEdge(x, b).pointsTowards(x)) {
                    continue BOUNDARY1;
                }
                if (Edges.isUndirectedEdge(CPDAG.getEdge(x, b))) {
                    List<Node> boundaryB = new ArrayList<>(boundary(b, CPDAG));
                    boundaryB.remove(x);
                    for (Node c : boundaryB) {
                        if (CPDAG.isParentOf(c, b)) {
                            continue BOUNDARY1;
                        }
                    }
                    CPDAG.setEndpoint(x, b, Endpoint.ARROW);
                }
            }
            eCPDAGs.add(CPDAG);
        }

        Graph _dag = new EdgeListGraph(graph);
        List<Node> newCollider = new ArrayList<>();

        BOUNDARY2:

        for (Node v : boundaryX) {

            if (_dag.getEdge(x, v).pointsTowards(v)) {
                continue;
            }

            if (Edges.isUndirectedEdge(_dag.getEdge(x, v))) {

                _dag.setEndpoint(x, v, Endpoint.ARROW);

                List<Node> boundaryV = new ArrayList<>(boundary(v, _dag));
                boundaryV.remove(x);

                for (Node d : boundaryV) {
                    if (_dag.isParentOf(d, v)) {
                        newCollider.add(v);
                    }
                }

            }
        }
        if (newCollider.size() == 0) {
            eCPDAGs.add(_dag);
        }
        return eCPDAGs;
    }


    private static void buildPowerSet(List<Node> boundary, int count) {
        Vcpc.powerSet.add(boundary);

        for (int i = 0; i < boundary.size(); i++) {
            List<Node> temp = new ArrayList<>(boundary);
            temp.remove(i);
            Vcpc.buildPowerSet(temp, temp.size());
        }
    }


//    Tests if a node x is markov by using an independence test to test if x is independent of variables
//    not in its boundary conditional on its boundary and if x is independent of variables not in its future
//    conditional on its boundary.

    private boolean isMarkov(Node node, Graph graph) {
//        Graph dag = SearchGraphUtils.dagFromCPDAG(graph);
        System.out.println(graph);
        IndependenceTest test = this.independenceTest;

        Node x = node;

//        for (Node x : graph.getNodes()) {
        List<Node> future = new ArrayList<>(future(x, graph));
        List<Node> boundary = new ArrayList<>(boundary(x, graph));

        for (Node y : graph.getNodes()) {
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
        LinkedList path = new LinkedList<>();
        Vcpc.futureNodeVisit(graph, x, path, futureNodes);
        futureNodes.remove(x);
        List<Node> adj = graph.getAdjacentNodes(x);
        for (Node y : adj) {
            if (graph.isParentOf(y, x) || Edges.isUndirectedEdge(graph.getEdge(x, y))) {
                futureNodes.remove(y);
            }
        }
        return futureNodes;
    }

    //    Constraints to guarantee future path conditions met. After traversing the entire path,
//    returns last node on path when satisfied, stops otherwise.
    private static Node traverseFuturePath(Node node, Edge edge1, Edge edge2) {
        Endpoint E1 = edge1.getProximalEndpoint(node);
        Endpoint E2 = edge2.getProximalEndpoint(node);
        Endpoint E3 = edge2.getDistalEndpoint(node);
        Endpoint E4 = edge1.getDistalEndpoint(node);
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
    public static void futureNodeVisit(Graph graph, Node b, LinkedList<Node> path, Set<Node> futureNodes) {
        path.addLast(b);
        futureNodes.add(b);
        for (Edge edge2 : graph.getEdges(b)) {
            Node c;

            int size = path.size();
            if (path.size() < 2) {
                c = edge2.getDistalNode(b);
                if (c == null) {
                    continue;
                }
                if (path.contains(c)) {
                    continue;
                }
            } else {
                Node a = path.get(size - 2);
                Edge edge1 = graph.getEdge(a, b);
                c = Vcpc.traverseFuturePath(b, edge1, edge2);
                if (c == null) {
                    continue;
                }
                if (path.contains(c)) {
                    continue;
                }
            }
            Vcpc.futureNodeVisit(graph, c, path, futureNodes);
        }
        path.removeLast();
    }


//    private void logTriples() {
//        TetradLogger.getInstance().log("info", "\nCollider triples:");
//
//        for (Triple triple : colliderTriples) {
//            TetradLogger.getInstance().log("info", "Collider: " + triple);
//        }
//
//        TetradLogger.getInstance().log("info", "\nNoncollider triples:");
//
//        for (Triple triple : noncolliderTriples) {
//            TetradLogger.getInstance().log("info", "Noncollider: " + triple);
//        }
//
//        TetradLogger.getInstance().log("info", "\nAmbiguous triples (i.e. list of triples for which " +
//                "\nthere is ambiguous data about whether they are colliders or not):");
//
//        for (Triple triple : getAmbiguousTriples()) {
//            TetradLogger.getInstance().log("info", "Ambiguous: " + triple);
//        }
//    }
//
////        Original triple orientation procedure.
//    private void orientUnshieldedTriples(IKnowledge knowledge,
//                                         IndependenceTest test, int depth) {
//        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");
//        colliderTriples = new HashSet<Triple>();
//        noncolliderTriples = new HashSet<Triple>();
//        ambiguousTriples = new HashSet<Triple>();
//        List<Node> nodes = graph.getNodes();
//        for (Node y : nodes) {
//            List<Node> adjacentNodes = graph.getAdjacentNodes(y);
//            if (adjacentNodes.size() < 2) {
//                continue;
//            }
//            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
//            int[] combination;
//
//            while ((combination = cg.next()) != null) {
//                Node x = adjacentNodes.get(combination[0]);
//                Node z = adjacentNodes.get(combination[1]);
//
//                if (this.graph.isAdjacentTo(x, z)) {
//                    continue;
//                }
//                getAllTriples().add(new Triple(x, y, z));
//                SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType(x, y, z, test, depth, graph, verbose);
////                SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType2(x, y, z, test, depth, graph);
//
//                if (type == SearchGraphUtils.CpcTripleType.COLLIDER) {
//                    if (colliderAllowed(x, y, z, knowledge)) {
//                        graph.setEndpoint(x, y, Endpoint.ARROW);
//                        graph.setEndpoint(z, y, Endpoint.ARROW);
//
//                        TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
//                    }
//
//                    colliderTriples.add(new Triple(x, y, z));
//                } else if (type == SearchGraphUtils.CpcTripleType.AMBIGUOUS) {
//                    Triple triple = new Triple(x, y, z);
//                    ambiguousTriples.add(triple);
//                    graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
//                    Edge edge = Edges.undirectedEdge(x, z);
//                    definitelyNonadjacencies.add(edge);
//                } else {
//                    noncolliderTriples.add(new Triple(x, y, z));
//                }
//            }
//        }
//        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
//    }

//// Population version.

    private void orientUnshieldedTriples(IKnowledge knowledge,
                                         IndependenceTest test, int depth) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

//        System.out.println("orientUnshieldedTriples 1");

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
                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (this.graph.isAdjacentTo(x, z)) {
                    continue;
                }

                getAllTriples().add(new Triple(x, y, z));
                CpcTripleType type = getPopulationTripleType(x, y, z, test, depth, this.graph, this.verbose);
//                SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType2(x, y, z, test, depth, graph);

                if (type == CpcTripleType.COLLIDER) {
                    if (colliderAllowed(x, y, z, knowledge)) {
                        this.graph.setEndpoint(x, y, Endpoint.ARROW);
                        this.graph.setEndpoint(z, y, Endpoint.ARROW);

                        TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
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

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    private void orientUnshieldedTriplesConcurrent(IKnowledge knowledge,
                                                   IndependenceTest test, int depth) {
        ExecutorService executor = Executors.newFixedThreadPool(this.NTHREDS);

        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        Graph graph = new EdgeListGraph(getGraph());

//        System.out.println("orientUnshieldedTriples 1");

        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();
        this.ambiguousTriples = new HashSet<>();
        List<Node> nodes = graph.getNodes();

        for (Node _y : nodes) {
            Node y = _y;

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

                Runnable worker = new Runnable() {
                    @Override
                    public void run() {

                        getAllTriples().add(new Triple(x, y, z));
                        SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType(x, y, z, test, depth, getGraph());
//                        SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType2(x, y, z, test, depth, getGraph());
//                        SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType4(x, y, z, test, depth, getGraph());
//
                        if (type == SearchGraphUtils.CpcTripleType.COLLIDER) {
                            if (colliderAllowed(x, y, z, knowledge)) {
                                getGraph().setEndpoint(x, y, Endpoint.ARROW);
                                getGraph().setEndpoint(z, y, Endpoint.ARROW);

                                TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
                            }

                            Vcpc.this.colliderTriples.add(new Triple(x, y, z));
                        } else if (type == SearchGraphUtils.CpcTripleType.AMBIGUOUS) {
                            Triple triple = new Triple(x, y, z);
                            Vcpc.this.ambiguousTriples.add(triple);
                            getGraph().addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                        } else {
                            Vcpc.this.noncolliderTriples.add(new Triple(x, y, z));
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
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }


    public CpcTripleType getPopulationTripleType(Node x, Node y, Node z,
                                                 IndependenceTest test, int depth,
                                                 Graph graph, boolean verbose) {
//        if ((x.getNode().equals("X5") && z.getNode().equals("X7"))
//            || (x.getNode().equals("X7") && z.getNode().equals("X5"))) {
//            System.out.println();
//        }

        if (this.facts == null) throw new NullPointerException("Need independence facts as a parent");

        // JOE HERE ARE THE INDEPENDENCE FACTS
        System.out.println("NameS" + this.facts.getVariableNames());

        int numSepsetsContainingY = 0;
        int numSepsetsNotContainingY = 0;

        List<Node> _nodes = graph.getAdjacentNodes(x);
        _nodes.remove(z);
        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        while (true) {
            for (int d = 0; d <= _depth; d++) {
                ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg.next()) != null) {
                    List<Node> cond = GraphUtils.asList(choice, _nodes);


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

//                    if (!test1.isIndependent(x, z, cond)) {
//                        if (facts.isIndependent(x, z, cond)) {
//                            if (cond.contains(y)) {
//                                numSepsetsContainingY++;
//                            } else {
//                                numSepsetsNotContainingY++;
//                            }
//                        }
//                        if (facts.isIndependent(z, x, cond)) {
//                            if (cond.contains(y)) {
//                                numSepsetsContainingY++;
//                            } else {
//                                numSepsetsNotContainingY++;
//                            }
//                        }
//                    }
//
                    if (numSepsetsContainingY > 0 && numSepsetsNotContainingY > 0) {
                        return CpcTripleType.AMBIGUOUS;
                    }
                }
            }

            _nodes = graph.getAdjacentNodes(z);
            _nodes.remove(x);
            TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

            _depth = depth;
            if (_depth == -1) {
                _depth = 1000;
            }
            _depth = Math.min(_depth, _nodes.size());

            for (int d = 0; d <= _depth; d++) {
                ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg.next()) != null) {
                    List<Node> cond = GraphUtils.asList(choice, _nodes);

                    if (test.isIndependent(x, z, cond)) {
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
                System.out.println("Orienting " + x + "-->" + y + "<-" + z);
            }
            return CpcTripleType.COLLIDER;
        }
    }

    public enum CpcTripleType {
        COLLIDER, NONCOLLIDER, AMBIGUOUS
    }

    private boolean colliderAllowed(Node x, Node y, Node z, IKnowledge knowledge) {
        return Vcpc.isArrowpointAllowed1(x, y, knowledge) &&
                Vcpc.isArrowpointAllowed1(z, y, knowledge);
    }

    public static boolean isArrowpointAllowed1(Node from, Node to,
                                               IKnowledge knowledge) {
        return knowledge == null || !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    public Map<Edge, List<Node>> getApparentlyNonadjacencies() {
        return this.apparentlyNonadjacencies;
    }

    public boolean isDoOrientation() {
        return this.doOrientation;
    }

    public void setDoOrientation(boolean doOrientation) {
        this.doOrientation = doOrientation;
    }

    /**
     * The graph that's constructed during the search.
     */
    public Graph getGraph() {
        return this.graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setFacts(IndependenceFacts facts) {
        this.facts = facts;
    }

//        Step V5. For each consistent disambiguation of the ambiguous triples
//                we test whether the resulting CPDAG satisfies Markov. If
//                every CPDAG does, then mark all the apparently non-adjacent
//                pairs as definitely non-adjacent.


//        NODES:
//
//        for (Node node : graph.getNodes()) {
//            for (Graph _graph : new ArrayList<Graph>(CPDAGs)) {
//                System.out.println("boundary of" + node + boundary(node, _graph));
//                System.out.println("future of" + node + future(node, _graph));
//                if (!isMarkov(node, _graph)) {
//                    continue NODES;
//                }
//            }
//            markovInAllCPDAGs.add(node);
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
//            if (markovInAllCPDAGs.contains(x) &&
//                    markovInAllCPDAGs.contains(y)) {
//                definitelyNonadjacencies.add(edge);
//            }
//        }


//        Step V5* Instead of checking if Markov in every CPDAG, just find some CPDAG that is Markov.

//        CPDAGS:
//
//        for (Graph _graph : new ArrayList<Graph>(CPDAGs)) {
//            for (Node node : graph.getNodes()) {
//                if (!isMarkov(node, _graph)) {
//                    continue CPDAGS;
//                }
//                markovInAllCPDAGs.add(node);
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
//            if (markovInAllCPDAGs.contains(x) &&
//                    markovInAllCPDAGs.contains(y)) {
//                definitelyNonadjacencies.add(edge);
//                apparentlyNonadjacencies.remove(edge);
//            }
//        }


//        //  Local Relative Markox condition. Tests if X is markov with respect to Y in all CPDAGs.
//
//        MARKOV:
//
//        for (Edge edge : apparentlyNonadjacencies.keySet()) {
//            Node x = edge.getNode1();
//
//            Node y = edge.getNode2();
//
//            for (Graph _graph : new ArrayList<Graph>(CPDAGs)) {
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
//                IndependenceTest test = new IndTestDSep(_graph);
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
////        smallest subset of boundaries for X and Y, Sx and Sy such that for SOME CPDAG:
////                X _||_ Y | Sx and X_||_Y | Sy.
////                If such smallest subsets of the boundaries for X and Y are found for SOME CPDAG,
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
//            for (Graph _graph : new ArrayList<Graph>(CPDAGs)) {
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
////        result. E.g., for x-y-z, the possible orientations are x->y->z, x<-y<-z, and x<-y->z.
////        For each orientation, calculate bdry(y) and ftre(y). Perform Markov tests for each possible
////        orientation - e.g. X_||_Y | bdry(Y). If the answer is yes for each orientation then X and Y
////        are definitely non-adjacent for that CPDAG. If they pass such a test for every CPDAG, then
////        they are definitely non-adjacent.
//
//        MARKOV:
//
//        for (Edge edge : apparentlyNonadjacencies.keySet()) {
//            Node x = edge.getNode1();
//            Node y = edge.getNode2();
//            IndependenceTest test = independenceTest;
//
//            for (Graph _graph : new ArrayList<Graph>(CPDAGs)) {
//
//                List<Graph> dagCPDAGsX = dagCPDAGs(x, _graph);
//
//                for (Graph pattX : new ArrayList<Graph>(dagCPDAGsX)) {
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
//                List<Graph> dagCPDAGsY = dagCPDAGs(y, _graph);
//
//                for (Graph pattY : new ArrayList<Graph>(dagCPDAGsY)) {
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

//        List<Graph> CPDAGss = new ArrayList<Graph>();


//        MARKOV:
//
//        for (Edge edge : apparentlyNonadjacencies.keySet()) {
//            Node x = edge.getNode1();
//            Node y = edge.getNode2();
//            IndependenceTest test = independenceTest;
//            List<Graph> eCPDAGsX = eCPDAGs(x, graph);
//
//            for (Graph pattX : new ArrayList<Graph>(eCPDAGsX)) {
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
//            List<Graph> dagCPDAGsY = eCPDAGs(y, graph);
//
//            for (Graph pattY : new ArrayList<Graph>(dagCPDAGsY)) {
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


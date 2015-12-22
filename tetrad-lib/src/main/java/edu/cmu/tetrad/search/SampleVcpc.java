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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.CombinationGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Implements a convervative version of PC, in which the Markov condition is assumed but faithfulness is tested
 * locally.
 *
 * @author Joseph Ramsey (this version).
 */
public final class SampleVcpc implements GraphSearch {

    private int NTHREDS = Runtime.getRuntime().availableProcessors() * 5;

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

    private Set<Node> markovInAllPatterns;

    private static Set<List<Node>> powerSet;


    private boolean aggressivelyPreventCycles = false;

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * The sepsets.
     */
    private Map<Edge, List<Node>> apparentlyNonadjacencies;

    private Map<List<Node>, Double> partialCorrs;

    /**
     * True iff orientation should be done.
     */
    private boolean doOrientation = true;

    /**
     * Whether verbose output about independencies is output.
     */
    private boolean verbose = false;

    /**
     * Document me.
     */
    private IndependenceFacts facts = null;

    private List<Node> variables;

    private Map<Node, Integer> indexMap;
    private Map<String, Node> nameMap;
    private DataSet dataSet;
    private ICovarianceMatrix covMatrix;

    private SemPm semPm = null;
    private SemIm semIm = null;

    private Map<Node, Node> nodesToVariables;

    /**
     * The map from variables to nodes.
     */
    private Map<Node, Node> variablesToNodes;


    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a CPC algorithm that uses the given independence test as oracle. This does not make a copy of the
     * independence test, for fear of duplicating the data set!
     */
    public SampleVcpc(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }
        if (!(independenceTest instanceof IndTestFisherZ)) {
            throw new IllegalArgumentException("Need Fisher Z test to proceed with algorithm");
        }


        this.independenceTest = independenceTest;

        this.dataSet = (DataSet) independenceTest.getData();
        this.variables = dataSet.getVariables();

        this.covMatrix = new CovarianceMatrix(dataSet);
        List<Node> nodes = covMatrix.getVariables();
        this.indexMap = indexMap(variables);
        this.nameMap = mapNames(variables);

        this.nodesToVariables = new HashMap<Node, Node>();
        this.variablesToNodes = new HashMap<Node, Node>();


    }

    //==============================PUBLIC METHODS========================//


    public SemIm getSemIm() {
        return semIm;
    }

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
        return new HashSet<Triple>(ambiguousTriples);
    }


    /**
     * @return the set of collider triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getColliderTriples() {
        return new HashSet<Triple>(colliderTriples);
    }

    /**
     * @return the set of noncollider triples found during the most recent run of the algorithm. Non-null after a call
     * to <code>search()</code>.
     */
    public Set<Triple> getNoncolliderTriples() {
        return new HashSet<Triple>(noncolliderTriples);
    }

    /**
     * @return the set of all triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getAllTriples() {
        return new HashSet<Triple>(allTriples);
    }

    public Set<Edge> getAdjacencies() {
        Set<Edge> adjacencies = new HashSet<Edge>();
        for (Edge edge : graph.getEdges()) {
            adjacencies.add(edge);
        }
        return adjacencies;
    }

    public Set<Edge> getApparentNonadjacencies() {
        return new HashSet<Edge>(apparentlyNonadjacencies.keySet());
    }

    public Set<Edge> getDefiniteNonadjacencies() {
        return new HashSet<Edge>(definitelyNonadjacencies);
    }

    /**
     * Runs PC starting with a fully connected graph over all of the variables in the domain of the independence test.
     * See PC for caveats. The number of possible cycles and bidirected edges is far less with CPC than with PC.
     */
//    public final Graph search() {
//        return search(independenceTest.getVariables());
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
        this.allTriples = new HashSet<Triple>();
        this.ambiguousTriples = new HashSet<Triple>();
        this.colliderTriples = new HashSet<Triple>();
        this.noncolliderTriples = new HashSet<Triple>();
        Vcfas fas = new Vcfas(getIndependenceTest());
        definitelyNonadjacencies = new HashSet<Edge>();
        markovInAllPatterns = new HashSet<Node>();

//        this.logger.log("info", "Variables " + independenceTest.getVariables());

        long startTime = System.currentTimeMillis();

        if (getIndependenceTest() == null) {
            throw new NullPointerException();
        }

        List<Node> allNodes = getIndependenceTest().getVariables();

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
        fas.setVerbose(verbose);

        // Note that we are ignoring the sepset map returned by this method
        // on purpose; it is not used in this search.
        graph = fas.search();

        apparentlyNonadjacencies = fas.getApparentlyNonadjacencies();

        if (isDoOrientation()) {
            if (verbose) {
                System.out.println("CPC orientation...");
            }
            SearchGraphUtils.pcOrientbk(knowledge, graph, allNodes);
            orientUnshieldedTriples(knowledge, getIndependenceTest(), getDepth());
//            orientUnshieldedTriplesConcurrent(knowledge, getIndependenceTest(), getDepth());
            MeekRules meekRules = new MeekRules();

            meekRules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
            meekRules.setKnowledge(knowledge);

            meekRules.orientImplied(graph);
        }


        List<Triple> ambiguousTriples = new ArrayList(graph.getAmbiguousTriples());

        int[] dims = new int[ambiguousTriples.size()];

        for (int i = 0; i < ambiguousTriples.size(); i++) {
            dims[i] = 2;
        }

        List<Graph> patterns = new ArrayList<Graph>();
        Map<Graph, List<Triple>> newColliders = new IdentityHashMap<Graph, List<Triple>>();
        Map<Graph, List<Triple>> newNonColliders = new IdentityHashMap<Graph, List<Triple>>();

//      Using combination generator to generate a list of combinations of ambiguous triples dismabiguated into colliders
//      and non-colliders. The combinations are added as graphs to the list patterns. The graphs are then subject to
//      basic rules to ensure consistent patterns.


        CombinationGenerator generator = new CombinationGenerator(dims);
        int[] combination;

        while ((combination = generator.next()) != null) {
            Graph _graph = new EdgeListGraph(graph);
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
            patterns.add(_graph);
        }

        List<Graph> _patterns = new ArrayList<Graph>(patterns);


        ///    Takes patterns and runs them through basic constraints to ensure consistent patterns (e.g. no cycles, no bidirected edges).

        GRAPH:

        for (Graph graph : new ArrayList<Graph>(patterns)) {
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

//            for (Edge edge : graph.getEdges()) {
//                if (Edges.isBidirectedEdge(edge)) {
//                    patterns.remove(graph);
//                    continue GRAPH;
//                }
//            }

            MeekRules rules = new MeekRules();
            rules.orientImplied(graph);
            if (graph.existsDirectedCycle()) {
                patterns.remove(graph);
                continue GRAPH;
            }

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


//        11/10/14 Find possible orientations of boundary of Y such that no unshielded colliders
//        result. E.g., for x-y-z, the possible orientations are x->y->z, x<-y<-z, and x<-y->z.
//        For each orientation, calculate bdry(y) and ftre(y). Perform Markov tests for each possible
//        orientation - e.g. X_||_Y | bdry(Y). If the answer is yes for each orientation then X and Y
//        are definitely non-adjacent for that pattern. If they pass such a test for every pattern, then
//        they are definitely non-adjacent.

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

        // 4/8/15 Local Relative Markov (M2)

        MARKOV:

        for (Edge edge : apparentlyNonadjacencies.keySet()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            for (Graph _graph : new ArrayList<Graph>(patterns)) {

                List<Node> boundaryX = new ArrayList<Node>(boundary(x, _graph));
                List<Node> boundaryY = new ArrayList<Node>(boundary(y, _graph));
                List<Node> futureX = new ArrayList<Node>(future(x, _graph));
                List<Node> futureY = new ArrayList<Node>(future(y, _graph));

                if (y == x) {
                    continue;
                }
                if (boundaryX.contains(y) || boundaryY.contains(x)) {
                    continue;
                }
                IndependenceTest test = independenceTest;

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
            definitelyNonadjacencies.add(edge);
//            apparentlyNonadjacencies.remove(edge);

        }

        for (Edge edge : definitelyNonadjacencies) {
            if (apparentlyNonadjacencies.keySet().contains(edge)) {
                apparentlyNonadjacencies.keySet().remove(edge);
            }
        }

        setSemIm(semIm);
//        semIm.getSemPm().getGraph();
        System.out.println(semIm.getEdgeCoef());
//        graph = DataGraphUtils.replaceNodes(graph, semIm.getVariableNodes());


//        System.out.println(semIm.getEdgeCoef());
//        System.out.println(sampleRegress.entrySet());

        List<Double> squaredDifference = new ArrayList<Double>();
        int numNullEdges = 0;


//        //Edge Estimation Alg I

        Regression sampleRegression = new RegressionDataset(dataSet);
        System.out.println(sampleRegression.getGraph());

        graph = GraphUtils.replaceNodes(graph, dataSet.getVariables());
        Map<Edge, double[]> sampleRegress = new HashMap<Edge, double[]>();
        Map<Edge, Double> edgeCoefs = new HashMap<Edge, Double>();

        ESTIMATION:

        for (Node z : graph.getNodes()) {

            Set<Edge> adj = getAdj(z, graph);
            for (Edge edge : apparentlyNonadjacencies.keySet()) {
                if (z == edge.getNode1() || z == edge.getNode2()) {
                    for (Edge adjacency : adj) {
//                        return Unknown and go to next Z
                        sampleRegress.put(adjacency, null);
                        Node a = adjacency.getNode1();
                        Node b = adjacency.getNode2();
                        if (semIm.existsEdgeCoef(a, b)) {
                            Double c = semIm.getEdgeCoef(a, b);
                            edgeCoefs.put(adjacency, c);
                        } else {
                            edgeCoefs.put(adjacency, 0.0);
                        }
                    }
                    continue ESTIMATION;
                }
            }

            for (Edge nonadj : definitelyNonadjacencies) {
                if (nonadj.getNode1() == z || nonadj.getNode2() == z) {
                    // return 0 for e
                    double[] d = {0, 0};
                    sampleRegress.put(nonadj, d);
                    Node a = nonadj.getNode1();
                    Node b = nonadj.getNode2();
                    if (semIm.existsEdgeCoef(a, b)) {
                        Double c = semIm.getEdgeCoef(a, b);
                        edgeCoefs.put(nonadj, c);
                    } else {
                        edgeCoefs.put(nonadj, 0.0);
                    }
                }
            }

            Set<Edge> parentsOfZ = new HashSet<Edge>();
            Set<Edge> _adj = getAdj(z, graph);

            for (Edge _adjacency : _adj) {
                if (!_adjacency.isDirected()) {
                    for (Edge adjacency : adj) {
                        sampleRegress.put(adjacency, null);
                        Node a = adjacency.getNode1();
                        Node b = adjacency.getNode2();
                        if (semIm.existsEdgeCoef(a, b)) {
                            Double c = semIm.getEdgeCoef(a, b);
                            edgeCoefs.put(adjacency, c);
                        } else {
                            edgeCoefs.put(adjacency, 0.0);
                        }
                    }
                }
                if (_adjacency.pointsTowards(z)) {
                    parentsOfZ.add(_adjacency);
                }
            }

            for (Edge edge : parentsOfZ) {
                if (edge.pointsTowards(edge.getNode2())) {
                    RegressionResult result = sampleRegression.regress(edge.getNode2(), edge.getNode1());
                    System.out.println(result);
                    double[] d = result.getCoef();
                    sampleRegress.put(edge, d);

                    Node a = edge.getNode1();
                    Node b = edge.getNode2();
                    if (semIm.existsEdgeCoef(a, b)) {
                        Double c = semIm.getEdgeCoef(a, b);
                        edgeCoefs.put(edge, c);
                    } else {
                        edgeCoefs.put(edge, 0.0);
                    }
                }
//                if (edge.pointsTowards(edge.getNode2())) {
//                    RegressionResult result = sampleRegression.regress(edge.getNode2(), edge.getNode1());
//                    double[] d = result.getCoef();
//                    sampleRegress.put(edge, d);
//
//                    Node a = edge.getNode1();
//                    Node b = edge.getNode2();
//                    if (semIm.existsEdgeCoef(a, b)) {
//                        Double c = semIm.getEdgeCoef(a, b);
//                        edgeCoefs.put(edge, c);
//                    } else { edgeCoefs.put(edge, 0.0); }
//                }
            }
        }

        System.out.println("All IM: " + semIm + "Finish");
        System.out.println("Just IM coefs: " + semIm.getEdgeCoef());


        System.out.println("IM Coef Map: " + edgeCoefs);
        System.out.println("Regress Coef Map: " + sampleRegress);


//
//
//
//        // Squared difference between true edge coefficients and results from edge estimation algroithm.


//        SQUARE:
//
//        for (Edge edge : sampleRegress.keySet()) {
//
//            double e;
//            Node x = edge.getNode1();
//            Node y = edge.getNode2();
//
//            if (semIm.existsEdgeCoef(x, y)) {
//                e = semIm.getEdgeCoef(x, y);
//                System.out.println("IM Coef for " + x + y + " is " + e);
//
//                if (sampleRegress.get(edge) == null) {
//                    numNullEdges++;
//                    continue SQUARE;
//                }
//
//                double[] a = sampleRegress.get(edge);
//                double c = a[0];
//                System.out.println("SR Coef for " + x + y + edge + " is " + c);
//                double r = c;
//                double d = r - e;
//                double sd = d*d;
//                squaredDifference.add(sd);
//                System.out.println("Squared Difference for: " + edge + " = " + sd);
//            }
//            else {
//                e = 0;
//
//                System.out.println("IM Coef for " + x + y + edge + " is " + e);
//
//                if (sampleRegress.get(edge) == null) {
//                    numNullEdges++;
//                    continue SQUARE;
//                }
//
//                System.out.println("IM Coef for " + x + y + edge + " is " + e);
//                double[] a = sampleRegress.get(edge);
//                double c = a[0];
//                System.out.println("SR Coef for " + x + y + " is " + c);
//                double r = c;
//                double d = r - e;
//                double sd = d*d;
//                squaredDifference.add(sd);
//                System.out.println("Squared Difference for: " + x + y + " = " + sd);
//
//            }
//        }
//
//        Double sum = new Double(0);
//        for (Double i : squaredDifference) {
//            sum = sum + i;
//        }
//
//        System.out.println("Squared Difference sum: " + sum);
//        System.out.println("Number of null edges: " + numNullEdges);
//
//        double numEdges = graph.getNumEdges();
//
//        double nullRatio = numNullEdges / numEdges;
//
//        System.out.println("Null ratio: " + nullRatio);
//
//
//
//
//
        for (Edge edge : sampleRegress.keySet()) {
            System.out.println(" Sample Regression: " + edge + java.util.Arrays.toString(sampleRegress.get(edge)));
        }

        for (Edge edge : graph.getEdges()) {
//            if (edge.isDirected()) {
//                System.out.println("IM edge: " + semIm.getEdgeCoef(edge));
//            }
            System.out.println("Sample edge: " + java.util.Arrays.toString(sampleRegress.get(edge)));
        }
//
//


        System.out.println("Sample VCPC:");
        System.out.println("# of patterns: " + patterns.size());
        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - startTime;

        System.out.println("Search Time (seconds):" + (elapsedTime) / 1000 + " s");
        System.out.println("Search Time (milli):" + elapsedTime + " ms");
        System.out.println("# of Apparent Nonadj: " + apparentlyNonadjacencies.size());
        System.out.println("# of Definite Nonadj: " + definitelyNonadjacencies.size());

//        System.out.println("Definitely Nonadjacencies:");
//
//        for (Edge edge : definitelyNonadjacencies) {
//            System.out.println(edge);
//        }
//
//        System.out.println("markov in all patterns:" + markovInAllPatterns);
//        System.out.println("patterns:" + patterns);
//        System.out.println("Apparently Nonadjacencies:");

//
//        for (Edge edge : apparentlyNonadjacencies.keySet()) {
//            System.out.println(edge);
//        }
//        System.out.println("Definitely Nonadjacencies:");
//
//
//        for (Edge edge : definitelyNonadjacencies) {
//            System.out.println(edge);
//        }

        TetradLogger.getInstance().log("apparentlyNonadjacencies", "\n Apparent Non-adjacencies" + apparentlyNonadjacencies);

        TetradLogger.getInstance().log("definitelyNonadjacencies", "\n Definite Non-adjacencies" + definitelyNonadjacencies);

        TetradLogger.getInstance().log("patterns", "Disambiguated Patterns: " + patterns);

        TetradLogger.getInstance().log("graph", "\nReturning this graph: " + graph);


        TetradLogger.getInstance().log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        TetradLogger.getInstance().log("info", "Finishing CPC algorithm.");

        logTriples();

        TetradLogger.getInstance().flush();
//        SearchGraphUtils.verifySepsetIntegrity(Map<Edge, List<Node>>, graph);
        return graph;
    }


    /**
     * Orients the given graph using CPC orientation with the conditional independence test provided in the
     * constructor.
     */
    public final Graph orientationForGraph(Dag trueGraph) {
        Graph graph = new EdgeListGraph(independenceTest.getVariables());

        for (Edge edge : trueGraph.getEdges()) {
            Node nodeA = edge.getNode1();
            Node nodeB = edge.getNode2();

            Node _nodeA = independenceTest.getVariable(nodeA.getName());
            Node _nodeB = independenceTest.getVariable(nodeB.getName());

            graph.addUndirectedEdge(_nodeA, _nodeB);
        }

        SearchGraphUtils.pcOrientbk(knowledge, graph, graph.getNodes());
        orientUnshieldedTriples(knowledge, getIndependenceTest(), depth);
        MeekRules meekRules = new MeekRules();
        meekRules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
        meekRules.setKnowledge(knowledge);
        meekRules.orientImplied(graph);

        return graph;
    }

    //==========================PRIVATE METHODS===========================//


    private Map<String, Node> mapNames(List<Node> variables) {
        Map<String, Node> nameMap = new ConcurrentHashMap<String, Node>();

        for (Node node : variables) {
            nameMap.put(node.getName(), node);
        }

        return nameMap;
    }

    private Map<Node, Integer> indexMap(List<Node> variables) {
        Map<Node, Integer> indexMap = new ConcurrentHashMap<Node, Integer>();

        for (int i = 0; i < variables.size(); i++) {
            indexMap.put(variables.get(i), i);
        }

        return indexMap;
    }

    private ICovarianceMatrix covMatrix() {
        return covMatrix;
    }

    public ICovarianceMatrix getCov() {
        return covMatrix;
    }

//    Takes patterns and, with respect to a node and its boundary, finds all possible combinations of orientations
//    of its boundary such that no new colliders are created. For each combination, a new pattern is added to the
//    list dagPatterns.


    private Set<Edge> getAdj(Node node, Graph graph) {
        Node z = node;
        Set<Edge> adj = new HashSet<Edge>();

        for (Edge edge : graph.getEdges()) {
            if (z == edge.getNode1()) {
                adj.add(edge);
            }
            if (z == edge.getNode2()) {
                adj.add(edge);
            }
        }
        return adj;
    }

    private List<Graph> dagPatterns(Node x, Graph graph) {
        List<Graph> dagPatterns = new ArrayList<Graph>();
        List<Node> boundaryX = new ArrayList<Node>(boundary(x, graph));

        BOUNDARY1:

        for (Node a : boundaryX) {
            Graph dag = new EdgeListGraph(graph);

            if (dag.getEdge(x, a).pointsTowards(a)) {
                continue;
            }

            if (Edges.isUndirectedEdge(dag.getEdge(x, a))) {
                dag.setEndpoint(a, x, Endpoint.ARROW);
            }


            List<Node> otherNodesX = new ArrayList<Node>(boundaryX);
            otherNodesX.remove(a);
            for (Node b : otherNodesX) {
                if (dag.getEdge(x, b).pointsTowards(x)) {
                    continue BOUNDARY1;
                }
                if (Edges.isUndirectedEdge(dag.getEdge(x, b))) {
                    List<Node> boundaryB = new ArrayList<Node>(boundary(b, dag));
                    boundaryB.remove(x);
                    for (Node c : boundaryB) {
                        if (dag.isParentOf(c, b)) {
                            continue BOUNDARY1;
                        }
                    }
                    dag.setEndpoint(x, b, Endpoint.ARROW);
                }
            }
            dagPatterns.add(dag);
        }

        Graph _dag = new EdgeListGraph(graph);
        List<Node> newCollider = new ArrayList<Node>();

        BOUNDARY2:

        for (Node v : boundaryX) {

            if (_dag.getEdge(x, v).pointsTowards(v)) {
                continue;
            }

            if (Edges.isUndirectedEdge(_dag.getEdge(x, v))) {

                _dag.setEndpoint(x, v, Endpoint.ARROW);

                List<Node> boundaryV = new ArrayList<Node>(boundary(v, _dag));
                boundaryV.remove(x);

                for (Node d : boundaryV) {
                    if (_dag.isParentOf(d, v)) {
                        newCollider.add(v);
                    }
                }

            }
        }
        if (newCollider.size() == 0) {
            dagPatterns.add(_dag);
        }
        return dagPatterns;
    }


    private static void buildPowerSet(List<Node> boundary, int count) {
        powerSet.add(boundary);

        for (int i = 0; i < boundary.size(); i++) {
            List<Node> temp = new ArrayList<Node>(boundary);
            temp.remove(i);
            buildPowerSet(temp, temp.size());
        }
    }


//    Tests if a node x is markov by using an independence test to test if x is independent of variables
//    not in its boundary conditional on its boundary and if x is independent of variables not in its future
//    conditional on its boundary.

    private boolean isMarkov(Node node, Graph graph) {
//        Graph dag = SearchGraphUtils.dagFromPattern(graph);
        System.out.println(graph);
        IndependenceTest test = new IndTestDSep(graph);

        Node x = node;

//        for (Node x : graph.getNodes()) {
        List<Node> future = new ArrayList<Node>(future(x, graph));
        List<Node> boundary = new ArrayList<Node>(boundary(x, graph));

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
        Set<Node> boundary = new HashSet<Node>();
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
        Set<Node> futureNodes = new HashSet<Node>();
        LinkedList path = new LinkedList<Node>();
        futureNodeVisit(graph, x, path, futureNodes);
        if (futureNodes.contains(x)) {
            futureNodes.remove(x);
        }
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
                c = traverseFuturePath(b, edge1, edge2);
                if (c == null) {
                    continue;
                }
                if (path.contains(c)) {
                    continue;
                }
            }
            futureNodeVisit(graph, c, path, futureNodes);
        }
        path.removeLast();
    }


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
                "\nthere is ambiguous data about whether they are colliders or not):");

        for (Triple triple : getAmbiguousTriples()) {
            TetradLogger.getInstance().log("info", "Ambiguous: " + triple);
        }
    }


    private void orientUnshieldedTriples(IKnowledge knowledge,
                                         IndependenceTest test, int depth) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

//        System.out.println("orientUnshieldedTriples 1");

        colliderTriples = new HashSet<Triple>();
        noncolliderTriples = new HashSet<Triple>();
        ambiguousTriples = new HashSet<Triple>();
        List<Node> nodes = graph.getNodes();

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

                if (this.graph.isAdjacentTo(x, z)) {
                    continue;
                }
                getAllTriples().add(new Triple(x, y, z));
                SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType(x, y, z, test, depth, graph, verbose);


//                CpcTripleType type = getSampleTripleType(x, y, z, test, depth, graph, verbose);
////                SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType2(x, y, z, test, depth, graph);


                if (type == SearchGraphUtils.CpcTripleType.COLLIDER) {
                    if (colliderAllowed(x, y, z, knowledge)) {
                        graph.setEndpoint(x, y, Endpoint.ARROW);
                        graph.setEndpoint(z, y, Endpoint.ARROW);

                        TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
                    }

                    colliderTriples.add(new Triple(x, y, z));
                } else if (type == SearchGraphUtils.CpcTripleType.AMBIGUOUS) {
                    Triple triple = new Triple(x, y, z);
                    ambiguousTriples.add(triple);
                    graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                    Edge edge = Edges.undirectedEdge(x, z);
                    definitelyNonadjacencies.add(edge);
                } else {
                    noncolliderTriples.add(new Triple(x, y, z));
                }
            }
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    public Map<List<Node>, Double> getPartialCorrs() {
        return new HashMap<List<Node>, Double>();
    }

//    Sample Version of Step 3 of VCPC.

//    private CpcTripleType getSampleTripleType(Node x, Node y, Node z, IndependenceTest test,
//                                                     int depth, Graph graph, boolean verbose) {
//
//        if (verbose) {
//            System.out.println("Checking " + x + " --- " + y + " --- " + z);
//        }
//
//        int numSepsetsContainingY = 0;
//        int numSepsetsNotContainingY = 0;
//
//        this.partialCorrs = getPartialCorrs();
//
//
//        List<Node> _nodes = graph.getAdjacentNodes(x);
//        _nodes.remove(z);
//        int _depth = depth;
//        if (_depth == -1) {
//            _depth = 1000;
//        }
//        _depth = Math.min(_depth, _nodes.size());
//
//
//        while (true) {
//            for (int d = 0; d <= _depth; d++) {
//                ChoiceGenerator cg1 = new ChoiceGenerator(_nodes.size(), d);
//                int[] choice;
//                while ((choice = cg1.next()) != null) {
//                    List<Node> cond = DataGraphUtils.asList(choice, _nodes);
//                    TetradMatrix submatrix = DataUtils.subMatrix(covMatrix, indexMap, x, z, cond);
//                    double r = StatUtils.partialCorrelation(submatrix);
//                    partialCorrs.put(cond, r);
//
//                    if (test.isIndependent(x, z, cond)) {
//                        if (verbose) {
//                            System.out.println("Indep: " + x + " _||_ " + z + " | " + cond);
//                        }
//                        if (cond.contains(y)) {
//                            numSepsetsContainingY++;
//                        } else {
//                            numSepsetsNotContainingY++;
//                        }
//                    }
//
//                    if (numSepsetsContainingY > 0 && numSepsetsNotContainingY > 0) {
//                        return CpcTripleType.AMBIGUOUS;
//                    }
//                }
//            }
//
//            _nodes = graph.getAdjacentNodes(z);
//            _nodes.remove(x);
//            TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);
//
//            _depth = depth;
//            if (_depth == -1) {
//                _depth = 1000;
//            }
//            _depth = Math.min(_depth, _nodes.size());
//
//            for (int d = 0; d <= _depth; d++) {
//                ChoiceGenerator cg1 = new ChoiceGenerator(_nodes.size(), d);
//                int[] choice;
//                while ((choice = cg1.next()) != null) {
//                    List<Node> cond = DataGraphUtils.asList(choice, _nodes);
//                    TetradMatrix submatrix = DataUtils.subMatrix(covMatrix, indexMap, x, z, cond);
//                    double r = StatUtils.partialCorrelation(submatrix);
//                    partialCorrs.put(cond, r);
//
//                    if (test.isIndependent(x, z, cond)) {
//
//                        if (verbose) {
//                            System.out.println("Indep: " + x + " _||_ " + z + " | " + cond);
//                        }
//
//                        if (cond.contains(y)) {
//                            numSepsetsContainingY++;
//                        } else {
//                            numSepsetsNotContainingY++;
//                        }
//                    }
//                    if (numSepsetsContainingY > 0 && numSepsetsNotContainingY > 0) {
//                        return CpcTripleType.AMBIGUOUS;
//                    }
//                }
//            }
//            break;
//        }
//
//        double L = 0.01;
////        System.out.println("L = " + L);
//
//        if (numSepsetsContainingY > 0 && numSepsetsNotContainingY == 0) {
//            for (List<Node> sepset1 : partialCorrs.keySet()) {
//                if (sepset1.contains(y)) {
//                    double r1 = partialCorrs.get(sepset1);
//                    for (List<Node> sepset2 : partialCorrs.keySet()) {
//                        if (!sepset2.contains(y)) {
//                            double r2 = partialCorrs.get(sepset2);
//                            double M = Math.abs(r1 - r2);
//
//                            if (!(M >= L)) {
//                                return CpcTripleType.AMBIGUOUS;
//                            }
//                        }
//                    }
//                    return CpcTripleType.NONCOLLIDER;
//                }
//            }
//        }
//
//        if (numSepsetsNotContainingY > 0 && numSepsetsContainingY == 0) {
//            for (List<Node> sepset1 : partialCorrs.keySet()) {
//                if (!sepset1.contains(y)) {
//                    double r1 = partialCorrs.get(sepset1);
//                    for (List<Node> sepset2 : partialCorrs.keySet()) {
//                        if (sepset2.contains(y)) {
//                            double r2 = partialCorrs.get(sepset2);
//                            double M = Math.abs(r1 - r2);
//                            if (!(M >= L)) {
//                                return CpcTripleType.AMBIGUOUS;
//                            }
//                        }
//                    }
//                    return CpcTripleType.COLLIDER;
//                }
//            }
//        }
//        return null;
//    }


//    public enum CpcTripleType {
//        COLLIDER, NONCOLLIDER, AMBIGUOUS
//    }


    private void orientUnshieldedTriplesConcurrent(final IKnowledge knowledge,
                                                   final IndependenceTest test, final int depth) {
        ExecutorService executor = Executors.newFixedThreadPool(NTHREDS);

        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        Graph graph = new EdgeListGraph(getGraph());

//        System.out.println("orientUnshieldedTriples 1");

        colliderTriples = new HashSet<Triple>();
        noncolliderTriples = new HashSet<Triple>();
        ambiguousTriples = new HashSet<Triple>();
        List<Node> nodes = graph.getNodes();

        for (Node _y : nodes) {
            final Node y = _y;

            List<Node> adjacentNodes = graph.getAdjacentNodes(y);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                final Node x = adjacentNodes.get(combination[0]);
                final Node z = adjacentNodes.get(combination[1]);

                if (graph.isAdjacentTo(x, z)) {
                    continue;
                }

                Runnable worker = new Runnable() {
                    @Override
                    public void run() {

                        getAllTriples().add(new Triple(x, y, z));
                        SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType(x, y, z, test, depth, getGraph(), verbose);
//                        SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType2(x, y, z, test, depth, getGraph());
//                        SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType4(x, y, z, test, depth, getGraph());
//
                        if (type == SearchGraphUtils.CpcTripleType.COLLIDER) {
                            if (colliderAllowed(x, y, z, knowledge)) {
                                getGraph().setEndpoint(x, y, Endpoint.ARROW);
                                getGraph().setEndpoint(z, y, Endpoint.ARROW);

                                TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
                            }

                            colliderTriples.add(new Triple(x, y, z));
                        } else if (type == SearchGraphUtils.CpcTripleType.AMBIGUOUS) {
                            Triple triple = new Triple(x, y, z);
                            ambiguousTriples.add(triple);
                            getGraph().addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                        } else {
                            noncolliderTriples.add(new Triple(x, y, z));
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

    private boolean colliderAllowed(Node x, Node y, Node z, IKnowledge knowledge) {
        return isArrowpointAllowed1(x, y, knowledge) &&
                isArrowpointAllowed1(z, y, knowledge);
    }

    public static boolean isArrowpointAllowed1(Node from, Node to,
                                               IKnowledge knowledge) {
        return knowledge == null || !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    public Map<Edge, List<Node>> getApparentlyNonadjacencies() {
        return apparentlyNonadjacencies;
    }

    public boolean isDoOrientation() {
        return doOrientation;
    }

    public void setDoOrientation(boolean doOrientation) {
        this.doOrientation = doOrientation;
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

    public void setFacts(IndependenceFacts facts) {
        this.facts = facts;
    }

    public void setSemPm(SemPm semPm) {
        this.semPm = semPm;
    }

    public void setSemIm(SemIm semIm) {
        this.semIm = semIm;
    }


    public SemPm getSemPm() {
        return semPm;
    }

}


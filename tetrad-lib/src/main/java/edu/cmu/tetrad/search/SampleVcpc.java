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

/**
 * Implements a conservative version of PC, in which the Markov condition is assumed but faithfulness is tested
 * locally.
 *
 * @author Joseph Ramsey (this version).
 */
public final class SampleVcpc implements GraphSearch {

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
     * Whether verbose output about independencies is output.
     */
    private boolean verbose;

    private final DataSet dataSet;
    private final ICovarianceMatrix covMatrix;

    private SemPm semPm;
    private SemIm semIm;


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
        this.covMatrix = new CovarianceMatrix(this.dataSet);
    }

    //==============================PUBLIC METHODS========================//


    public SemIm getSemIm() {
        return this.semIm;
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
     * @return the elapsed time of search in milliseconds, after <code>search()</code> has been run.
     */
    public long getElapsedTime() {
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

    public Set<Edge> getAdjacencies() {
        return new HashSet<>(this.graph.getEdges());
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
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");
        this.ambiguousTriples = new HashSet<>();
        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();
        Vcfas fas = new Vcfas(getIndependenceTest());
        this.definitelyNonadjacencies = new HashSet<>();
        long startTime = System.currentTimeMillis();
        List<Node> allNodes = getIndependenceTest().getVariables();

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
            MeekRules meekRules = new MeekRules();

            meekRules.setAggressivelyPreventCycles(this.aggressivelyPreventCycles);
            meekRules.setKnowledge(this.knowledge);

            meekRules.orientImplied(this.graph);
        }


        List<Triple> ambiguousTriples = new ArrayList<>(this.graph.getAmbiguousTriples());

        int[] dims = new int[ambiguousTriples.size()];

        for (int i = 0; i < ambiguousTriples.size(); i++) {
            dims[i] = 2;
        }

        List<Graph> CPDAGs = new ArrayList<>();
        Map<Graph, List<Triple>> newColliders = new IdentityHashMap<>();
        Map<Graph, List<Triple>> newNonColliders = new IdentityHashMap<>();

//      Using combination generator to generate a list of combinations of ambiguous triples dismabiguated into colliders
//      and non-colliders. The combinations are added as graphs to the list CPDAGs. The graphs are then subject to
//      basic rules to ensure consistent CPDAGs.


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
            CPDAGs.add(_graph);
        }

        ///    Takes CPDAGs and runs them through basic constraints to ensure consistent CPDAGs (e.g. no cycles, no bidirected edges).
        GRAPH:

        for (Graph graph : new ArrayList<>(CPDAGs)) {

            List<Triple> colliders = newColliders.get(graph);
            List<Triple> nonColliders = newNonColliders.get(graph);


            for (Triple triple : colliders) {
                Node x = triple.getX();
                Node y = triple.getY();
                Node z = triple.getZ();

                if (graph.getEdge(x, y).pointsTowards(x) || (graph.getEdge(y, z).pointsTowards(z))) {
                    CPDAGs.remove(graph);
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
            if (graph.existsDirectedCycle()) {
                CPDAGs.remove(graph);
            }
        }

//        11/10/14 Find possible orientations of boundary of Y such that no unshielded colliders
//        result. E.g., for x-y-z, the possible orientations are x->y->z, x&lt;-y&lt;-z, and x&lt;-y->z.
//        For each orientation, calculate bdry(y) and ftre(y). Perform Markov tests for each possible
//        orientation - e.g. X_||_Y | bdry(Y). If the answer is yes for each orientation then X and Y
//        are definitely non-adjacent for that CPDAG. If they pass such a test for every CPDAG, then
//        they are definitely non-adjacent.

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

        // 4/8/15 Local Relative Markov (M2)

        MARKOV:

        for (Edge edge : this.apparentlyNonadjacencies.keySet()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            for (Graph _graph : new ArrayList<>(CPDAGs)) {

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
                    if (!test.isIndependent(x, y, boundaryX).independent()) {
                        continue MARKOV;
                    }

                }

                if (!futureY.contains(x)) {
                    if (!test.isIndependent(y, x, boundaryY).independent()) {
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

        setSemIm(this.semIm);
        System.out.println(this.semIm.getEdgeCoef());

        Regression sampleRegression = new RegressionDataset(this.dataSet);
        System.out.println(sampleRegression.getGraph());

        this.graph = GraphUtils.replaceNodes(this.graph, this.dataSet.getVariables());
        Map<Edge, double[]> sampleRegress = new HashMap<>();
        Map<Edge, Double> edgeCoefs = new HashMap<>();

        ESTIMATION:

        for (Node z : this.graph.getNodes()) {

            Set<Edge> adj = getAdj(z, this.graph);
            for (Edge edge : this.apparentlyNonadjacencies.keySet()) {
                if (z == edge.getNode1() || z == edge.getNode2()) {
                    for (Edge adjacency : adj) {
//                        return Unknown and go to next Z
                        sampleRegress.put(adjacency, null);
                        Node a = adjacency.getNode1();
                        Node b = adjacency.getNode2();
                        if (this.semIm.existsEdgeCoef(a, b)) {
                            Double c = this.semIm.getEdgeCoef(a, b);
                            edgeCoefs.put(adjacency, c);
                        } else {
                            edgeCoefs.put(adjacency, 0.0);
                        }
                    }
                    continue ESTIMATION;
                }
            }

            for (Edge nonadj : this.definitelyNonadjacencies) {
                if (nonadj.getNode1() == z || nonadj.getNode2() == z) {
                    // return 0 for e
                    double[] d = {0, 0};
                    sampleRegress.put(nonadj, d);
                    Node a = nonadj.getNode1();
                    Node b = nonadj.getNode2();
                    if (this.semIm.existsEdgeCoef(a, b)) {
                        Double c = this.semIm.getEdgeCoef(a, b);
                        edgeCoefs.put(nonadj, c);
                    } else {
                        edgeCoefs.put(nonadj, 0.0);
                    }
                }
            }

            Set<Edge> parentsOfZ = new HashSet<>();
            Set<Edge> _adj = getAdj(z, this.graph);

            for (Edge _adjacency : _adj) {
                if (!_adjacency.isDirected()) {
                    for (Edge adjacency : adj) {
                        sampleRegress.put(adjacency, null);
                        Node a = adjacency.getNode1();
                        Node b = adjacency.getNode2();
                        if (this.semIm.existsEdgeCoef(a, b)) {
                            Double c = this.semIm.getEdgeCoef(a, b);
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
                    if (this.semIm.existsEdgeCoef(a, b)) {
                        Double c = this.semIm.getEdgeCoef(a, b);
                        edgeCoefs.put(edge, c);
                    } else {
                        edgeCoefs.put(edge, 0.0);
                    }
                }
            }
        }

        System.out.println("All IM: " + this.semIm + "Finish");
        System.out.println("Just IM coefs: " + this.semIm.getEdgeCoef());


        System.out.println("IM Coef Map: " + edgeCoefs);
        System.out.println("Regress Coef Map: " + sampleRegress);


        for (Edge edge : sampleRegress.keySet()) {
            System.out.println(" Sample Regression: " + edge + java.util.Arrays.toString(sampleRegress.get(edge)));
        }

        for (Edge edge : this.graph.getEdges()) {
            System.out.println("Sample edge: " + java.util.Arrays.toString(sampleRegress.get(edge)));
        }
//
//


        System.out.println("Sample VCPC:");
        System.out.println("# of CPDAGs: " + CPDAGs.size());
        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - startTime;

        System.out.println("Search Time (seconds):" + (this.elapsedTime) / 1000 + " s");
        System.out.println("Search Time (milli):" + this.elapsedTime + " ms");
        System.out.println("# of Apparent Nonadj: " + this.apparentlyNonadjacencies.size());
        System.out.println("# of Definite Nonadj: " + this.definitelyNonadjacencies.size());

        TetradLogger.getInstance().log("apparentlyNonadjacencies", "\n Apparent Non-adjacencies" + this.apparentlyNonadjacencies);

        TetradLogger.getInstance().log("definitelyNonadjacencies", "\n Definite Non-adjacencies" + this.definitelyNonadjacencies);

        TetradLogger.getInstance().log("CPDAGs", "Disambiguated CPDAGs: " + CPDAGs);

        TetradLogger.getInstance().log("graph", "\nReturning this graph: " + this.graph);


        TetradLogger.getInstance().log("info", "Elapsed time = " + (this.elapsedTime) / 1000. + " s");
        TetradLogger.getInstance().log("info", "Finishing CPC algorithm.");

        logTriples();

        TetradLogger.getInstance().flush();
//        SearchGraphUtils.verifySepsetIntegrity(Map<Edge, List<Node>>, graph);
        return this.graph;
    }


    //==========================PRIVATE METHODS===========================//


    public ICovarianceMatrix getCov() {
        return this.covMatrix;
    }

//    Takes CPDAGs and, with respect to a node and its boundary, finds all possible combinations of orientations
//    of its boundary such that no new colliders are created. For each combination, a new CPDAG is added to the
//    list dagCPDAGs.


    private Set<Edge> getAdj(Node node, Graph graph) {
        Set<Edge> adj = new HashSet<>();

        for (Edge edge : graph.getEdges()) {
            if (node == edge.getNode1()) {
                adj.add(edge);
            }
            if (node == edge.getNode2()) {
                adj.add(edge);
            }
        }
        return adj;
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
        SampleVcpc.futureNodeVisit(graph, x, path, futureNodes);
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
            } else {
                Node a = path.get(size - 2);
                Edge edge1 = graph.getEdge(a, b);
                c = SampleVcpc.traverseFuturePath(b, edge1, edge2);
            }
            if (c == null) {
                continue;
            }
            if (path.contains(c)) {
                continue;
            }
            SampleVcpc.futureNodeVisit(graph, c, path, futureNodes);
        }
        path.removeLast();
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
                "\nthere is ambiguous data about whether they are colliders or not):");

        for (Triple triple : getAmbiguousTriples()) {
            TetradLogger.getInstance().log("info", "Ambiguous: " + triple);
        }
    }


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
                SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType(x, y, z, test, depth, graph);


                if (type == SearchGraphUtils.CpcTripleType.COLLIDER) {
                    if (this.colliderAllowed(x, y, z, knowledge)) {
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

    private boolean colliderAllowed(Node x, Node y, Node z, IKnowledge knowledge) {
        return SampleVcpc.isArrowpointAllowed1(x, y, knowledge) &&
                SampleVcpc.isArrowpointAllowed1(z, y, knowledge);
    }

    public static boolean isArrowpointAllowed1(Node from, Node to,
                                               IKnowledge knowledge) {
        return knowledge == null || !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    public boolean isDoOrientation() {
        return true;
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

    public void setFacts() {
    }

    public void setSemPm(SemPm semPm) {
        this.semPm = semPm;
    }

    public void setSemIm(SemIm semIm) {
        this.semIm = semIm;
    }


    public SemPm getSemPm() {
        return this.semPm;
    }

}


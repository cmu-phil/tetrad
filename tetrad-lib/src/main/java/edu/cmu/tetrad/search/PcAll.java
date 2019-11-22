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
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.*;

import static edu.cmu.tetrad.search.SearchGraphUtils.basicPattern;

/**
 * Implements a convervative version of PC, in which the Markov condition is assumed but faithfulness is tested
 * locally.
 *
 * @author Joseph Ramsey (this version).
 */
public final class PcAll implements GraphSearch {
    public enum FasType {REGULAR, STABLE}

    public enum Concurrent {YES, NO}

    private FasType fasType = FasType.REGULAR;
    private Concurrent concurrent = Concurrent.NO;
    private IndependenceTest test;
    private OrientColliders.ColliderMethod colliderDiscovery = OrientColliders.ColliderMethod.SEPSETS;
    private OrientColliders.ConflictRule conflictRule = OrientColliders.ConflictRule.OVERWRITE;
    private OrientColliders.IndependenceDetectionMethod independenceMethod = OrientColliders.IndependenceDetectionMethod.ALPHA;
    private boolean doMarkovLoop = false;
    private IKnowledge knowledge = new Knowledge2();
    private int depth = 1000;
    private double orientationAlpha = 0.;
    private Graph initialGraph;
    private boolean aggressivelyPreventCycles = false;
    private TetradLogger logger = TetradLogger.getInstance();
    private SepsetMap sepsets;
    private long elapsedTime;
    private boolean verbose = false;
    private PrintStream out = System.out;

    private Graph G;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a CPC algorithm that uses the given independence test as oracle. This does not make a copy of the
     * independence test, for fear of duplicating the data set!
     */
    public PcAll(IndependenceTest independenceTest, Graph initialGraph) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.test = independenceTest;
        this.initialGraph = initialGraph;
    }

    //==============================PUBLIC METHODS========================//

    public void setFasType(FasType fasType) {
        this.fasType = fasType;
    }

    public void setConcurrent(Concurrent concurrent) {
        this.concurrent = concurrent;
    }

//    public void setOrientationAlpha(double orientationAlpha) {
//        this.orientationAlpha = orientationAlpha;
//    }

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

    public void setColliderDiscovery(OrientColliders.ColliderMethod colliderMethod) {
        this.colliderDiscovery = colliderMethod;
    }

    public void setConflictRule(OrientColliders.ConflictRule conflictRule) {
        this.conflictRule = conflictRule;
    }

//    public void setIndependenceMethod(OrientColliders.IndependenceDetectionMethod independenceMethod) {
//        this.independenceMethod = independenceMethod;
//    }

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
    public IndependenceTest getTest() {
        return test;
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
        throw new UnsupportedOperationException();
    }

    /**
     * @return the set of collider triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getColliderTriples() {
        throw new UnsupportedOperationException();
    }

    public void setDoMarkovLoop(boolean doMarkovLoop) {
        this.doMarkovLoop = doMarkovLoop;
    }

    /**
     * @return the set of noncollider triples found during the most recent run of the algorithm. Non-null after a call
     * to <code>search()</code>.
     */
    public Set<Triple> getNoncolliderTriples() {
        throw new UnsupportedOperationException();
    }

    public Set<Edge> getAdjacencies() {
        return new HashSet<>(G.getEdges());
    }

    public Set<Edge> getNonadjacencies() {
        Graph complete = GraphUtils.completeGraph(G);
        Set<Edge> nonAdjacencies = complete.getEdges();
        Graph undirected = GraphUtils.undirectedGraph(G);
        nonAdjacencies.removeAll(undirected.getEdges());
        return new HashSet<>(nonAdjacencies);
    }

    public Graph search() {
        return search(getTest().getVariables());
    }

    public Graph search(List<Node> nodes) {
        this.logger.log("info", "Starting CPC algorithm");
        this.logger.log("info", "Independence test = " + getTest() + ".");

        test.setVerbose(verbose);

        if (getTest() == null) {
            throw new NullPointerException();
        }

        List<Node> allNodes = getTest().getVariables();
        if (!allNodes.containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                    "be in the domain of the independence test provided.");
        }

        long start = System.currentTimeMillis();

        findAdjacencies();
        G = reorient(G);

        System.out.println("doMarkovLoop = " + doMarkovLoop);

        if (doMarkovLoop) {
            if (colliderDiscovery == OrientColliders.ColliderMethod.SEPSETS) {
                throw new IllegalArgumentException("Cannot do the Markov loop with the Sepset method of collider discovery.");
            }

            doMarkovLoop(nodes);
//
//            PcAll pcAll = new PcAll(test, G);
//            pcAll.setColliderDiscovery(OrientColliders.ColliderMethod.SEPSETS);
//            pcAll.setFasType(FasType.REGULAR);
//            G = pcAll.search();
////
//            doMarkovLoop(nodes);

//            pcAll = new PcAll(test, G);
//            pcAll.setColliderDiscovery(OrientColliders.ColliderMethod.SEPSETS);
//            pcAll.setFasType(FasType.STABLE);
//            G = pcAll.search();
//
//            doMarkovLoop(nodes);

//            Fas fas = new Fas(G, test);
//            G = E(G);
//            return fas.search();
        }

//        GraphUtils.printNonMarkovCounts(G, test);

        TetradLogger.getInstance().log("graph", "\nReturning this graph: " + G);

        long end = System.currentTimeMillis();

        this.elapsedTime = end - start;

        TetradLogger.getInstance().

                log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        TetradLogger.getInstance().

                log("info", "Finishing CPC algorithm.");

        TetradLogger.getInstance().flush();
        return G;
    }

    private void doMarkovLoop(List<Node> nodes) {

        forward(nodes);
        backward(nodes);

//        G = reorient(G);

        System.out.println("\nBefore edge removal");
        GraphUtils.printNonMarkovCounts(G, test);

        System.out.println("\nAfter the backward search, Markov = " + GraphUtils.markov(G, test));

        System.out.println("\nAfter edge removal");
        GraphUtils.printNonMarkovCounts(G, test);
        System.out.println();
    }

    private void forward(List<Node> nodes) {
        int round = 0;
        boolean changed = true;

        do {
            System.out.println("Forward = " + ++round);
            changed = false;

            Map<Pair, Double> scores = new HashMap<>();


            for (Node y : nodes) {
//                double maxp = Double.NEGATIVE_INFINITY;
//                Node maxy = null;
//                Node maxx = null;

                for (Node x : nodes) {
                    if (x == y) continue;
                    if (G.isAdjacentTo(x, y)) continue;
                    double p = nonMarkovContainsP(y, G, x);

                    if (!Double.isNaN(p)) {
                        scores.put(new Pair(x, y), p);

//                        if (!Double.isNaN(p) && p > maxp) {
//                            maxp = p;
//                            maxx = x;
//                            maxy = y;
//
//
//                            changed = true;
//                        }
                    }
                }

//                if (maxx != null) {
//                    G.addUndirectedEdge(maxx, maxy);
//                    updateOrientation(G, maxx, maxy);
//                    changed = true;
//                }
            }

            List<Pair> pairs = new ArrayList<>(scores.keySet());
            pairs.sort((o1, o2) -> Double.compare(scores.get(o2), scores.get(o1)));

            for (Pair pair : pairs) {
                if (!G.isAdjacentTo(pair.getX(), pair.getY())) {
                    G.addUndirectedEdge(pair.getX(), pair.getY());
                    updateOrientation(G, pair.getX(), pair.getY());
                    changed = true;
                }
            }
        } while (changed && round < 25);
    }

    private static class Pair {
        private Node x;
        private Node y;

        public Pair(Node x, Node y) {
            this.setX(x);
            this.setY(y);
        }

        public Node getX() {
            return x;
        }

        public void setX(Node x) {
            this.x = x;
        }

        public Node getY() {
            return y;
        }

        public void setY(Node y) {
            this.y = y;
        }
    }

    private void backward(List<Node> nodes) {
        boolean changed = true;
        int round = 0;

        System.out.println("\nAfter the forward search, Markov = " + GraphUtils.markov(G, test));

//        do {
//            System.out.println("Backward = " + ++round);
//
//            changed = false;
//
//            for (Edge edge : G.getEdges()) {
//                Node x = edge.getNode1();
//                Node y = edge.getNode2();
//                if (nodes.indexOf(x) < nodes.indexOf(y)) continue;
//
//                Graph H = new EdgeListGraph(G);
//                H.removeEdge(x, y);
//
//                if (GraphUtils.markov(x, H, test) && GraphUtils.markov(y, H, test)) {
//                    updateOrientation(H, x, y);
//                    G = H;
//                    changed = true;
//                    System.out.println("Removed " + Edges.undirectedEdge(x, y));
//                }
//            }
//        } while (changed && round < 10);

        do {
            System.out.println("Forward = " + ++round);
            changed = false;

            for (Node y : nodes) {
//                double maxp = Double.NEGATIVE_INFINITY;
//                Node maxx = null;

                for (Node x : nodes) {
                    if (x == y) continue;
                    if (!G.isAdjacentTo(x, y)) continue;

                    Graph H = new EdgeListGraph(G);
                    H.removeEdge(x, y);

                    if (GraphUtils.markov(x, H, test) && GraphUtils.markov(y, H, test)) {
                        updateOrientation(H, x, y);
                        G = H;
                        changed = true;
                        System.out.println("Removed " + Edges.undirectedEdge(x, y));
                    }
//
//
//                    if (!Double.isNaN(maxp)) {
//                        double p = nonMarkovContainsP(y, G, x);
//
//                        if (!Double.isNaN(p) && p > maxp) {
//                            maxp = p;
//                            maxx = x;
//                        }
//                    }
                }


//                if (maxx != null) {
//                    G.addUndirectedEdge(maxx, y);
//                    updateOrientation(G, maxx, y);
//                    changed = true;
//                }

            }
        } while (changed && round < 10);

    }

    public static double nonMarkovMaxP(Node y, Graph G, IndependenceTest test) {
        List<Node> boundary = GraphUtils.boundary(y, G);

        double maxp = Double.NEGATIVE_INFINITY;

        for (Node x : G.getNodes()) {
            if (y == x) continue;
            if (G.isDescendentOf(x, y)) continue;
            if (boundary.contains(x)) continue;


            boolean independent = test.isIndependent(y, x, boundary);
            double p = test.getPValue();

            if (p > maxp) {
                maxp = p;
            }
        }

        return maxp;
    }

//    private boolean validDelete(Node x, Node y, Graph G) {
//        Set<Node> n = new HashSet<>();
//
//        for (Node w : G.getAdjacentNodes(y)) {
//            if (G.isUndirectedFromTo(w, y) && G.isAdjacentTo(w, x)) {
//                n.add(w);
//            }
//        }
//
//        return isClique(n, G);
//    }

//    private boolean validInsert(Node x, Node y, Graph G) {
//        Set<Node> n = new HashSet<>();
//
//        for (Node w : G.getAdjacentNodes(y)) {
//            if (G.isUndirectedFromTo(y, w)) n.add(w);
//        }
//
//        boolean clique = isClique(n, G);
//        boolean noCycle = !existsUnblockedSemiDirectedPath(y, x, n, -1, G);
//
//        return clique && noCycle;
//    }

//    private boolean isClique(Set<Node> nodes, Graph graph) {
//        List<Node> _nodes = new ArrayList<>(nodes);
//        for (int i = 0; i < _nodes.size(); i++) {
//            for (int j = i + 1; j < _nodes.size(); j++) {
//                if (!graph.isAdjacentTo(_nodes.get(i), _nodes.get(j))) {
//                    return false;
//                }
//            }
//        }
//
//        return true;
//    }

//    private boolean existsUnblockedSemiDirectedPath(Node from, Node to, Set<Node> cond, int bound, Graph graph) {
//        Queue<Node> Q = new LinkedList<>();
//        Set<Node> V = new HashSet<>();
//        Q.offer(from);
//        V.add(from);
//        Node e = null;
//        int distance = 0;
//
//        while (!Q.isEmpty()) {
//            Node t = Q.remove();
//            if (t == to) {
//                return true;
//            }
//
//            if (e == t) {
//                e = null;
//                distance++;
//                if (distance > (bound == -1 ? 1000 : bound)) {
//                    return false;
//                }
//            }
//
//            for (Node u : graph.getAdjacentNodes(t)) {
//                Edge edge = graph.getEdge(t, u);
//                Node c = traverseSemiDirected(t, edge);
//                if (c == null) {
//                    continue;
//                }
//                if (cond.contains(c)) {
//                    continue;
//                }
//
//                if (c == to) {
//                    return true;
//                }
//
//                if (!V.contains(c)) {
//                    V.add(c);
//                    Q.offer(c);
//
//                    if (e == null) {
//                        e = u;
//                    }
//                }
//            }
//        }
//
//        return false;
//    }

//    private static Node traverseSemiDirected(Node node, Edge edge) {
//        if (node == edge.getNode1()) {
//            if (edge.getEndpoint1() == Endpoint.TAIL) {
//                return edge.getNode2();
//            }
//        } else if (node == edge.getNode2()) {
//            if (edge.getEndpoint2() == Endpoint.TAIL) {
//                return edge.getNode1();
//            }
//        }
//        return null;
//    }


//    private boolean markov(List<Node> nodes, Graph G, IndependenceTest test) {
//        boolean markov = true;
//
//        for (Node y : nodes) {
//            if (!GraphUtils.markov(y, G, test)) {
//                markov = false;
//                break;
//            }
//        }
//        return markov;
//    }

//    private List<Node> nonMarkov(Node y, Graph G) {
//        return GraphUtils.nonMarkov(y, G, test);
//    }

//    private boolean isAdjacentToCollider(Node x, Node z, Node y, Graph h) {
//        OrientColliders orientColliders;
//
//        if (colliderDiscovery == OrientColliders.ColliderMethod.SEPSETS) {
//            orientColliders = new OrientColliders(test, sepsets);
//        } else {
//            orientColliders = new OrientColliders(test, colliderDiscovery);
//        }
//
//        orientColliders.setConflictRule(conflictRule);
//        orientColliders.setIndependenceDetectionMethod(independenceMethod);
//        orientColliders.setDepth(depth);
//        orientColliders.setOrientationQ(orientationAlpha);
//        orientColliders.setVerbose(verbose);
//        orientColliders.setOut(out);
//        return orientColliders.orientTriple(h, x, z, y) == SearchGraphUtils.CpcTripleType.COLLIDER;
//    }

    private Graph reorient(Graph H) {
        H = removeOrientations(H);
        applyBackgroundKnowledge(H);
        orientTriples(H);
        applyMeekRules(H);
        removeUnnecessaryMarks(H);
        return H;
    }

    private void updateOrientation(Graph H, Node x, Node y) {
        Set<Triple> ambiguous = H.getAmbiguousTriples();
        basicPattern(H, false);

        OrientColliders orientColliders = new OrientColliders(test, OrientColliders.ColliderMethod.CPC);
        orientColliders.setConflictRule(OrientColliders.ConflictRule.PRIORITY);
        orientColliders.setIndependenceDetectionMethod(OrientColliders.IndependenceDetectionMethod.ALPHA);
        orientColliders.setDepth(-1);
        orientColliders.setOrientationQ(test.getAlpha());
        orientColliders.setVerbose(verbose);
        orientColliders.setOut(out);

        for (Node b : H.getAdjacentNodes(x)) {
            for (Node c : H.getAdjacentNodes(b)) {
                if (x == c) continue;

                if (H.isAdjacentTo(x, c)) {
                    H.removeAmbiguousTriple(x, b, c);
                    continue;
                }

                SearchGraphUtils.CpcTripleType type = orientColliders.orientTriple(H, x, b, c);

                if (type == SearchGraphUtils.CpcTripleType.AMBIGUOUS) {
                    H.addAmbiguousTriple(x, b, c);
                } else if (type == SearchGraphUtils.CpcTripleType.COLLIDER) {
                    H.removeAmbiguousTriple(x, b, c);

                    if (!H.getEdge(x, b).pointsTowards(b)) {
                        H.removeEdge(x, b);
                        H.addDirectedEdge(x, b);
                    }

                    if (!H.getEdge(c, b).pointsTowards(b)) {
                        H.removeEdge(c, b);
                        H.addDirectedEdge(c, b);
                    }
                } else if (type == SearchGraphUtils.CpcTripleType.NONCOLLIDER) {
                    H.addAmbiguousTriple(x, b, c);
                }
            }
        }

        for (Node b : H.getAdjacentNodes(y)) {
            for (Node c : H.getAdjacentNodes(b)) {
                if (y == c) continue;

                if (H.isAdjacentTo(y, c)) {
                    H.removeAmbiguousTriple(y, b, c);
                    continue;
                }

                SearchGraphUtils.CpcTripleType type = orientColliders.orientTriple(H, y, b, c);

                if (type == SearchGraphUtils.CpcTripleType.AMBIGUOUS) {
                    H.addAmbiguousTriple(y, b, c);
                } else if (type == SearchGraphUtils.CpcTripleType.COLLIDER) {
                    H.removeAmbiguousTriple(y, b, c);

                    if (!H.getEdge(y, b).pointsTowards(b)) {
                        H.removeEdge(y, b);
                        H.addDirectedEdge(y, b);
                    }

                    if (!H.getEdge(c, b).pointsTowards(b)) {
                        H.removeEdge(c, b);
                        H.addDirectedEdge(c, b);
                    }
                } else if (type == SearchGraphUtils.CpcTripleType.NONCOLLIDER) {
                    H.addAmbiguousTriple(y, b, c);
                }
            }
        }

        for (Triple triple : ambiguous) {
            if (H.isAdjacentTo(triple.getX(), triple.getZ())) continue;
            if (H.isAdjacentTo(triple.getX(), triple.getY()) && H.isAdjacentTo(triple.getY(), triple.getZ())) {
                H.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }
        }

        applyMeekRules(H);
        removeUnnecessaryMarks(H);
    }

    private void applyBackgroundKnowledge(Graph G) {
        SearchGraphUtils.pcOrientbk(knowledge, G, G.getNodes());
    }

    private Graph removeOrientations(Graph G) {
        return GraphUtils.undirectedGraph(G);
    }

    public static List<Edge> asList(int[] indices, List<Edge> nodes) {
        List<Edge> list = new LinkedList<>();

        for (int i : indices) {
            list.add(nodes.get(i));
        }

        return list;
    }


    // Returns true if the set of non-Markov variables to y contains x.
    private boolean nonMarkovContains(Node y, Graph G, Node x) {
        List<Node> boundary = GraphUtils.boundary(y, G);
        if (y == x) return false;
        if (G.isDescendentOf(x, y)) return false;
        if (boundary.contains(x)) return false;
        return !test.isIndependent(y, x, boundary);
    }

    private double nonMarkovContainsP(Node y, Graph G, Node x) {
        List<Node> boundary = GraphUtils.boundary(y, G);
        if (y == x) return Double.NaN;
        if (G.isDescendentOf(x, y)) return Double.NaN;
        if (boundary.contains(x)) return Double.NaN;
        if (!test.isIndependent(y, x, boundary)) {
            return test.getPValue();
        }

        return Double.NaN;
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
    public Graph getG() {
        return G;
    }

    public void setG(Graph g) {
        this.G = g;
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

    //==========================PRIVATE METHODS===========================//

    private void findAdjacencies() {
        IFas fas;

        if (G != null) {
            initialGraph = G;
        }

        if (fasType == FasType.REGULAR) {
            if (concurrent == Concurrent.NO) {
                fas = new Fas(initialGraph, getTest());
            } else {
                fas = new FasConcurrent(initialGraph, getTest());
                ((FasConcurrent) fas).setStable(false);
            }
        } else {
            if (concurrent == Concurrent.NO) {
                fas = new FasStable(initialGraph, getTest());
            } else {
                fas = new FasConcurrent(initialGraph, getTest());
                ((FasConcurrent) fas).setStable(true);
            }
        }

        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(verbose);

        // Note that we are ignoring the sepset map returned by this method
        // on purpose; it is not used in this search.
        this.G = fas.search();
        sepsets = fas.getSepsets();
    }

    private void orientTriples(Graph G) {
        OrientColliders orientColliders;

        if (colliderDiscovery == OrientColliders.ColliderMethod.SEPSETS) {
            orientColliders = new OrientColliders(test, sepsets);
        } else {
            orientColliders = new OrientColliders(test, colliderDiscovery);
        }

        orientColliders.setConflictRule(conflictRule);
        orientColliders.setIndependenceDetectionMethod(independenceMethod);
        orientColliders.setDepth(depth);
        orientColliders.setOrientationQ(orientationAlpha);
        orientColliders.setVerbose(verbose);
        orientColliders.setOut(out);
        orientColliders.orientTriples(G);
    }

    private void applyMeekRules(Graph G) {
        MeekRules meekRules = new MeekRules();
        meekRules.setKnowledge(getKnowledge());
        meekRules.setAggressivelyPreventCycles(true);
        meekRules.setUndirectUnforcedEdges(true);
        meekRules.orientImplied(G);
    }

    private void removeUnnecessaryMarks(Graph G) {

        // Remove unnecessary marks.
        for (Triple triple : G.getUnderLines()) {
            G.removeUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }

        for (Triple triple : G.getAmbiguousTriples()) {
            Edge edge1 = G.getEdge(triple.getZ(), triple.getY());
            Edge edge2 = G.getEdge(triple.getX(), triple.getY());

            if (edge1 == null) {
                G.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }

            if (edge2 == null) {
                G.removeAmbiguousTriple(triple.getZ(), triple.getY(), triple.getZ());
            }

            if (edge1 == null || edge2 == null) return;

            if (edge2.pointsTowards(triple.getX())) {
                G.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }

            if (edge1.pointsTowards(triple.getZ())) {
                G.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }

            if (edge2.pointsTowards(triple.getY()) && edge1.pointsTowards(triple.getY())) {
                G.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }
        }
    }

//    private Graph kpartial(IndependenceTest test) {
//        colliderDiscovery = OrientColliders.ColliderMethod.CPC;
//
//        int k = depth;
//
//        findAdjacencies();
//
//        G = E(G);
//
//        List<Node> nodes = test.getVariables();
//
//        for (int i = 0; i < nodes.size(); i++) {
//            for (int j = i + 1; j < nodes.size(); j++) {
//                Node a = nodes.get(i);
//                Node b = nodes.get(j);
//                List<Node> _nodes = new ArrayList<>(nodes);
//                _nodes.remove(a);
//                _nodes.remove(b);
//
//                DepthChoiceGenerator gen = new DepthChoiceGenerator(_nodes.size(), k);
//                int[] choice;
//
//                while ((choice = gen.next()) != null) {
//                    List<Node> Z = GraphUtils.asList(choice, _nodes);
//
//                    if (test.isIndependent(a, b, Z)) {
//                        List<Node> C = G.getAdjacentNodes(a);
//                        C.retainAll(G.getAdjacentNodes(b));
//
//                        for (Node c : C) {
//                            if (Z.contains(c)) continue;
//                            if (!G.getEdges(a, c).contains(Edges.directedEdge(c, a))
//                                    || !G.getEdges(b, c).contains(Edges.directedEdge(c, b))) continue;
//
//                            if (test.isDependent(a, c, Z) && test.isDependent(c, b, Z)) {
//                                System.out.println("Removing " + a + "<-" + c + "->" + b);
//
//                                kpartialRemoveEdge(G, c, a);
//                                kpartialRemoveEdge(G, c, b);
//
//                                System.out.println("a--c edges = " + G.getEdges(a, c));
//                                System.out.println("b--c edges = " + G.getEdges(b, c));
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        applyMeekRules(G);
//
//        return G;
//    }

//    private void kpartialRemoveEdge(Graph g, Node c, Node a) {
//        if (g.getEdge(a, c) == null) throw new IllegalArgumentException("No edge to remove");
//        if (g.getEdge(c, a).pointsTowards(a)) g.removeEdge(c, a);
//        else if (g.isUndirectedFromTo(c, a)) {
//            g.removeEdge(c, a);
//            g.addDirectedEdge(a, c);
//        }
//    }


    public boolean existsSemiDirectedPathFromTo(Node node1, Set<Node> nodes, Graph G) {
        return existsSemiDirectedPathVisit(null, node1, nodes,
                new LinkedList<>(), G);
    }

    /**
     * @return true iff there is a semi-directed path from node1 to node2
     */
    private boolean existsSemiDirectedPathVisit(Node node0, Node node1, Set<Node> nodes2,
                                                LinkedList<Node> path, Graph G) {
        path.addLast(node1);

        for (Edge edge : G.getEdges(node1)) {
            Node child = Edges.traverseSemiDirected(node1, edge);

            if (child == null) {
                continue;
            }

            if (nodes2.contains(child)) {
                return true;
            }

            if (path.contains(child)) {
                continue;
            }

            if (node0 != null) {
                if (G.isAmbiguousTriple(node0, node1, child)) continue;
            }

            if (existsSemiDirectedPathVisit(node1, child, nodes2, path, G)) {
                return true;
            }
        }

        path.removeLast();
        return false;
    }

}


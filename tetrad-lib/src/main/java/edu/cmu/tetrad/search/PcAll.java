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
    private Concurrent concurrent = Concurrent.YES;

    private IndependenceTest test;
    private OrientColliders.ColliderMethod colliderDiscovery = OrientColliders.ColliderMethod.SEPSETS;
    private OrientColliders.ConflictRule conflictRule = OrientColliders.ConflictRule.OVERWRITE;
    private OrientColliders.IndependenceDetectionMethod independenceMethod = OrientColliders.IndependenceDetectionMethod.ALPHA;
    private IKnowledge knowledge = new Knowledge2();
    private int depth = 1000;
    private double fdrQ = 0.2;
    private Set<Triple> colliderTriples;
    private Set<Triple> noncolliderTriples;
    private Set<Triple> ambiguousTriples;
    private Graph initialGraph;
    private boolean aggressivelyPreventCycles = false;
    private TetradLogger logger = TetradLogger.getInstance();
    private SepsetMap sepsets;
    private long elapsedTime;
    private boolean verbose = false;
    private boolean useHeuristic = false;
    private int maxPathLength;
    private PrintStream out = System.out;

    private Graph graph;

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

    public void setIndependenceMethod(OrientColliders.IndependenceDetectionMethod independenceMethod) {
        this.independenceMethod = independenceMethod;
    }

    public void setUseHeuristic(boolean useHeuristic) {
        this.useHeuristic = useHeuristic;
    }

    public int getMaxPathLength() {
        return maxPathLength;
    }

    public void setMaxPathLength(int maxPathLength) {
        this.maxPathLength = maxPathLength;
    }

    public void setFasType(FasType fasType) {
        this.fasType = fasType;
    }

    public void setConcurrent(Concurrent concurrent) {
        this.concurrent = concurrent;
    }

    public void setFdrQ(double fdrQ) {
        this.fdrQ = fdrQ;
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

    public void setColliderDiscovery(OrientColliders.ColliderMethod colliderMethod) {
        this.colliderDiscovery = colliderMethod;
    }

    public void setConflictRule(OrientColliders.ConflictRule conflictRule) {
        this.conflictRule = conflictRule;
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
        return new HashSet<>(ambiguousTriples);
    }

    /**
     * @return the set of collider triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getColliderTriples() {
        return new HashSet<>(colliderTriples);
    }

    /**
     * @return the set of noncollider triples found during the most recent run of the algorithm. Non-null after a call
     * to <code>search()</code>.
     */
    public Set<Triple> getNoncolliderTriples() {
        return new HashSet<>(noncolliderTriples);
    }

    public Set<Edge> getAdjacencies() {
        return new HashSet<>(graph.getEdges());
    }

    public Set<Edge> getNonadjacencies() {
        Graph complete = GraphUtils.completeGraph(graph);
        Set<Edge> nonAdjacencies = complete.getEdges();
        Graph undirected = GraphUtils.undirectedGraph(graph);
        nonAdjacencies.removeAll(undirected.getEdges());
        return new HashSet<>(nonAdjacencies);
    }

    public Graph search() {
        return search(getTest().getVariables());
    }

    public Graph search(List<Node> nodes) {
        this.logger.log("info", "Starting CPC algorithm");
        this.logger.log("info", "Independence test = " + getTest() + ".");
        this.ambiguousTriples = new HashSet<>();
        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();

        test.setVerbose(verbose);

        if (getTest() == null) {
            throw new NullPointerException();
        }

        List<Node> allNodes = getTest().getVariables();
        if (!allNodes.containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                    "be in the domain of the independence test provided.");
        }


        SearchGraphUtils.pcOrientbk(knowledge, graph, nodes);

        long start = System.currentTimeMillis();

        findAdjacencies();
        orientTriples();
        applyMeekRules();
        removeUnnecessaryMarks();

        for (int i = 0; i < 1; i++) {
            addErrantEdges(nodes);
//            findAdjacencies();


            graph = GraphUtils.undirectedGraph(graph);

            orientTriples();
            applyMeekRules();
            removeUnnecessaryMarks();
        }

        TetradLogger.getInstance().log("graph", "\nReturning this graph: " + graph);

        long end = System.currentTimeMillis();
        this.elapsedTime = end - start;

        TetradLogger.getInstance().log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        TetradLogger.getInstance().log("info", "Finishing CPC algorithm.");

        logTriples();

        TetradLogger.getInstance().flush();
        return graph;
    }

    private void addErrantEdges(List<Node> nodes) {
        graph = SearchGraphUtils.patternFromEPattern(graph);
//        graph = SearchGraphUtils.patternForDag(graph);

        List<Edge> definitelyNotAdjacent = new ArrayList<>();
        List<Edge> apparentlyNotAdjacent = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (!graph.isAdjacentTo(x, y) && satisfiesMarkov(graph, x) && satisfiesMarkov(graph, y)) {
                    definitelyNotAdjacent.add(Edges.undirectedEdge(x, y));
                } else if (!graph.isAdjacentTo(x, y)) {
                    apparentlyNotAdjacent.add(Edges.undirectedEdge(x, y));
                }
            }
        }

        System.out.println("Definitely not adjacent: " + definitelyNotAdjacent);
        System.out.println("Apparently not adjacent: " + apparentlyNotAdjacent);
    }

    private List<Node> descendants(Graph g, Node n) {
        return g.getDescendants(Collections.singletonList(n));
    }

    private List<Node> boundary(Graph g, Node x) {
        Set<Node> b = new HashSet<>();

        for (Node y : g.getAdjacentNodes(x)) {
            if (Edges.isUndirectedEdge(graph.getEdge(x, y))) {
                b.add(y);
            }

            if (g.isParentOf(y, x)) {
                b.add(y);
            }
        }

        return new ArrayList<>(b);
    }

    private boolean satisfiesMarkov(Graph g, Node x) {

        for (Node y : g.getNodes()) {
            if (y == x) continue;
            if (descendants(g, x).contains(y)) continue;
            if (boundary(g, x).contains(y)) continue;
            if (!test.isIndependent(x, y, boundary(g, x))) return false;
        }

        return true;
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
    public Graph getGraph() {
        return graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
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
                "\nthere is ambiguous data about whether they are colliderDiscovery or not):");

        for (Triple triple : getAmbiguousTriples()) {
            TetradLogger.getInstance().log("info", "Ambiguous: " + triple);
        }
    }

    private void findAdjacencies() {
//        FasStable fas = new FasStable(test);
//        fas.setKnowledge(knowledge);
//        fas.setVerbose(verbose);
//        this.graph = fas.search();

        IFas fas;

        if (graph != null) {
            initialGraph = graph;
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
        graph = fas.search();
        sepsets = fas.getSepsets();
    }

    private void orientTriples() {
        OrientColliders orientColliders;

        if (colliderDiscovery == OrientColliders.ColliderMethod.SEPSETS) {
            orientColliders = new OrientColliders(test, sepsets);
        } else {
            orientColliders = new OrientColliders(test, colliderDiscovery);
        }

        orientColliders.setConflictRule(conflictRule);
        orientColliders.setIndependenceDetectionMethod(independenceMethod);
        orientColliders.setDepth(depth);
        orientColliders.setOrientationQ(fdrQ);
        orientColliders.setVerbose(verbose);
        orientColliders.setOut(out);
        orientColliders.orientTriples(graph);
    }

    private void applyMeekRules() {
        MeekRules meekRules = new MeekRules();
        meekRules.setKnowledge(getKnowledge());
        meekRules.setAggressivelyPreventCycles(true);
        meekRules.orientImplied(graph);
    }

    private void removeUnnecessaryMarks() {

        // Remove unnecessary marks.
        for (Triple triple : graph.getUnderLines()) {
            graph.removeUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }

        for (Triple triple : graph.getAmbiguousTriples()) {
            if (graph.getEdge(triple.getX(), triple.getY()).pointsTowards(triple.getX())) {
                graph.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }

            if (graph.getEdge(triple.getZ(), triple.getY()).pointsTowards(triple.getZ())) {
                graph.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }

            if (graph.getEdge(triple.getX(), triple.getY()).pointsTowards(triple.getY())
                    && graph.getEdge(triple.getZ(), triple.getY()).pointsTowards(triple.getY())) {
                graph.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
            }
        }
    }
}


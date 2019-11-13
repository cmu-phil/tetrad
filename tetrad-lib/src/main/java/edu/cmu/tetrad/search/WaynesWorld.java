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
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.*;

import static java.util.Comparator.comparingDouble;

/**
 * Implements a convervative version of PC, in which the Markov condition is assumed but faithfulness is tested
 * locally.
 *
 * @author Joseph Ramsey (this version).
 */
public final class WaynesWorld implements GraphSearch {
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
    public WaynesWorld(IndependenceTest independenceTest, Graph initialGraph) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.test = independenceTest;
        this.initialGraph = null;
        this.knowledge = new Knowledge2();
    }

    //==============================PUBLIC METHODS========================//

    public void setFasType(FasType fasType) {
        this.fasType = fasType;
    }

    public void setConcurrent(Concurrent concurrent) {
        this.concurrent = concurrent;
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
//        this.knowledge = knowledge;
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
//        G = reorient(G);
        colliders = orientTriples(G);
        applyMeekRules(G);

        addCucViolationEdges();
//        addCucViolationEdges();
//        addCucViolationEdges();

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

    private void addCucViolationEdges() {
        List<Triple> cucViolations = new ArrayList<>();

        System.out.println("g1 = " + G);

        for (Node y : G.getNodes()) {
            System.out.println("Examining node " + y + " parents = " + G.getNodesInTo(y, Endpoint.ARROW));

            List<Node> parents = G.getNodesInTo(y, Endpoint.ARROW);

            if (parents.size() < 2) continue;

            // Print CUC violations.

            ChoiceGenerator gen = new ChoiceGenerator(parents.size(), 2);
            int[] choice;

            int c = 0;
            int d = 0;

            while ((choice = gen.next()) != null) {
                List<Node> adj = GraphUtils.asList(choice, parents);

                Node x = adj.get(0);
                Node z = adj.get(1);

                Triple triple = new Triple(x, y, z);
                System.out.println("Checking : " + triple);

                c++;

                if (!colliders.contains(triple)) {
                    System.out.println("CUC violation: " + triple);
                    cucViolations.add(triple);

                    d++;



//                    knowledge.setRequired(triple.getX().getName(), triple.getZ().getName());
//                    knowledge.setRequired(triple.getZ().getName(), triple.getX().getName());
                }
            }

            if (d > 1) {
                for (Node a : parents) {
                    G.setEndpoint(a, y, Endpoint.TAIL);
                }
            }
        }

//        findAdjacencies();
//        colliders = orientTriples(G);
//        applyMeekRules(G);
////
//        G = reorient(G);
    }

    List<Triple> colliders = new ArrayList<>();

    private Graph reorient(Graph H) {
        H = removeOrientations(H);
        applyBackgroundKnowledge(H);
        colliders = orientTriples(H);
//        applyMeekRules(H);
//        removeUnnecessaryMarks(H);
        return H;
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

    public List<Triple> orientTriples(Graph graph) {
        List<Triple> colliders = new ArrayList<>();
        List<Triple> ambiguous = new ArrayList<>();
        List<Triple> noncolliders = new ArrayList<>();

//        OrientColliders orient = new OrientColliders(test, OrientColliders.ColliderMethod.SEPSETS);
        OrientColliders orient = new OrientColliders(test, sepsets);
        orient.orientTriples(graph);
        colliders = orient.getColliders();
        return colliders;
    }

    private void applyMeekRules(Graph G) {
        MeekRules meekRules = new MeekRules();
        meekRules.setKnowledge(getKnowledge());
        meekRules.setAggressivelyPreventCycles(true);
        meekRules.setUndirectUnforcedEdges(true);
        meekRules.setNoNewUnshieldedColliders(true);
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


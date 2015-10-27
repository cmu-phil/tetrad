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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Reorients edges in the getModel graph as CPC would orient them. Basically, does a pattern search using CPC over the
 * given (undirected) edges in the given graph.
 *
 * @author Joseph Ramsey (this version).
 */
public final class CpcOrienter implements Reorienter {

    /**
     * The independence test used for the PC search.
     */
    private IndependenceTest independenceTest;

    /**
     * Forbidden and required edges for the search.
     */
    private IKnowledge knowledge;

    /**
     * The maximum number of nodes conditioned on in the search.
     */
    private int depth = Integer.MAX_VALUE;

    /**
     * The graph that's constructed during the search.
     */
    private Graph graph;

    /**
     * Elapsed time of last search.
     */
    private long elapsedTime;

//    /**
//     * Sepset map from the adjacency search.
//     */
//    private SepsetMap sepsetMap;

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

    //=============================CONSTRUCTORS==========================//

    public CpcOrienter(IndependenceTest independenceTest, IKnowledge knowledge) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.knowledge = knowledge;
    }

    //==============================PUBLIC METHODS========================//

    private IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    private IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    private int getDepth() {
        return depth;
    }

    public final void setDepth(int depth) {
        this.depth = depth;
    }

    public final Graph getPartialGraph() {
        return new EdgeListGraph(graph);
    }

    public final long getElapsedTime() {
        return this.elapsedTime;
    }

    public final int getNumAmbiguousPairs() {
        return ambiguousTriples.size();
    }

    public final int getNumPairs() {
        return allTriples.size();
    }

    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<Triple>(ambiguousTriples);
    }

    public Set<Triple> getColliderTriples() {
        return colliderTriples;
    }

    public Set<Triple> getNoncolliderTriples() {
        return noncolliderTriples;
    }

    /**
     * Runs PC on just the given variable, all of which must be in the domain of the independence test.
     */
    public void orient(Graph graph) {
        TetradLogger.getInstance().log("info", "Starting CPC algorithm.");
        TetradLogger.getInstance().log("info", "Independence test = " + independenceTest + ".");
        long startTime = System.currentTimeMillis();
        this.allTriples = new HashSet<Triple>();
        this.ambiguousTriples = new HashSet<Triple>();
        this.colliderTriples = new HashSet<Triple>();
        this.noncolliderTriples = new HashSet<Triple>();

        if (getIndependenceTest() == null) {
            throw new NullPointerException();
        }

//        List allNodes = getIndependenceTest().getVariables();
//        if (!allNodes.containsAll(nodes)) {
//            throw new IllegalArgumentException("All of the given nodes must " +
//                    "be in the domain of the independence test provided.");
//        }
//
//        graph = new EdgeListGraph(nodes);
//        graph.fullyConnect(Endpoint.TAIL);

        this.graph = graph;
        Set<Edge> edges = graph.getEdges();

        for (Edge edge : edges) {
            graph.removeEdge(edge);
            graph.addEdge(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
        }

//        Fas fas =
//                new Fas(graph, getIndependenceTest());
//        fas.setKnowledge(getKnowledge());
//        fas.setDepth(getDepth());
//        graph = fas.search();
//        this.sepsetMap = fas.getSepsets();

//        FastAdjacencySearchLo fas =
//                new FastAdjacencySearchLo(graph, getIndependenceTest());
//        fas.setKnowledge(getKnowledge());
//        fas.setDepth(depth());
//        fas.search();

//        if (!sepsetMap.equals(sepsetPc)) {
//            System.out.println("Not equal.");
//        }

//        verifySepsetIntegrity(sepsetMap, graph);

        SearchGraphUtils.pcOrientbk(knowledge, graph, graph.getNodes());
        orientUnshieldedTriples(knowledge, getIndependenceTest(), depth);
        MeekRules meekRules = new MeekRules();
        meekRules.setKnowledge(knowledge);
        meekRules.orientImplied(graph);

        TetradLogger.getInstance().log("graph", "\nReturning this graph: " + graph);
        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - startTime;
        TetradLogger.getInstance().log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
        TetradLogger.getInstance().log("info", "Finishing CPC algorithm.");
        logTriples();
        TetradLogger.getInstance().flush();

//        SearchGraphUtils.verifySepsetIntegrity(sepsetMap, graph);
//        return graph;
    }

    private void logTriples() {
        TetradLogger.getInstance().log("info", "\nCollider triples judged from sepsets:");

        for (Triple triple : getColliderTriples()) {
            TetradLogger.getInstance().log("collider", "Collider: " + triple);
        }

        TetradLogger.getInstance().log("info", "\nNoncollider triples judged from sepsets:");

        for (Triple triple : getNoncolliderTriples()) {
            TetradLogger.getInstance().log("noncollider", "Noncollider: " + triple);
        }

        TetradLogger.getInstance().log("info", "\nAmbiguous triples judged from sepsets (i.e. list of triples for which " +
                "\nthere is ambiguous data about whether they are colliders or not):");

        for (Triple triple : getAmbiguousTriples()) {
            TetradLogger.getInstance().log("ambiguous", "Ambiguous: " + triple);
        }
    }


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
        meekRules.setKnowledge(knowledge);
        meekRules.orientImplied(graph);

        return graph;
    }

    //==========================PRIVATE METHODS===========================//

    @SuppressWarnings({"SameParameterValue"})
    private void orientUnshieldedTriples(IKnowledge knowledge,
                                         IndependenceTest test, int depth) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        colliderTriples = new HashSet<Triple>();
        noncolliderTriples = new HashSet<Triple>();
        ambiguousTriples = new HashSet<Triple>();

        for (Node y : graph.getNodes()) {
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

                allTriples.add(new Triple(x, y, z));

                CpcOrienter.TripleType type = getTripleType(x, y, z, test, depth);

                System.out.println(new Triple(x, y, z) + " " + type);

                if (type == CpcOrienter.TripleType.COLLIDER) {
                    if (colliderAllowed(x, y, z, knowledge)) {
                        graph.setEndpoint(x, y, Endpoint.ARROW);
                        graph.setEndpoint(z, y, Endpoint.ARROW);
                        TetradLogger.getInstance().log("colliderOrientations",
                                SearchLogUtils.colliderOrientedMsg(x, y, z));
                    }

                    colliderTriples.add(new Triple(x, y, z));
                } else if (type == CpcOrienter.TripleType.AMBIGUOUS) {
                    Triple triple = new Triple(x, y, z);
                    ambiguousTriples.add(triple);
                    graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                } else {
                    noncolliderTriples.add(new Triple(x, y, z));
                }
            }
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    private boolean colliderAllowed(Node x, Node y, Node z, IKnowledge knowledge) {
        return CpcOrienter.isArrowpointAllowed1(x, y, knowledge) &&
                CpcOrienter.isArrowpointAllowed1(z, y, knowledge);
    }

    private CpcOrienter.TripleType getTripleType(Node x, Node y, Node z,
                                                 IndependenceTest test, int depth) {
        boolean existsSepsetContainingY = false;
        boolean existsSepsetNotContainingY = false;

        Set<Node> __nodes = new HashSet<Node>(this.graph.getAdjacentNodes(x));
        __nodes.remove(z);

        List<Node> _nodes = new LinkedList<Node>(__nodes);
        TetradLogger.getInstance().log("adjacencies",
                "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = Integer.MAX_VALUE;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> condSet = CpcOrienter.asList(choice, _nodes);

                if (test.isIndependent(x, z, condSet)) {
                    if (condSet.contains(y)) {
                        existsSepsetContainingY = true;
                    } else {
                        existsSepsetNotContainingY = true;
                    }
                }
            }
        }

        __nodes = new HashSet<Node>(this.graph.getAdjacentNodes(z));
        __nodes.remove(x);

        _nodes = new LinkedList<Node>(__nodes);
        TetradLogger.getInstance().log("adjacencies",
                "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        _depth = depth;
        if (_depth == -1) {
            _depth = Integer.MAX_VALUE;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> condSet = CpcOrienter.asList(choice, _nodes);

                if (test.isIndependent(x, z, condSet)) {
                    if (condSet.contains(y)) {
                        existsSepsetContainingY = true;
                    } else {
                        existsSepsetNotContainingY = true;
                    }
                }
            }
        }

        // These lines assume that the sepset information from the FAS was
        // stored. This check ensures that false positive arrow orientations
        // by CPC are strictly fewer than for PC, but from a practical standpoint
        // is not all that helpful.
//        List<Node> condSet = sepsetMap.get(x, z);
//
//        if (condSet == null) {
//            System.out.println("Wierd, no sepset!");
//        }
//
//        if (condSet != null) {
//            if (condSet.contains(y)) {
//                existsSepsetContainingY = true;
//            } else {
//                existsSepsetNotContainingY = true;
//            }
//        }

        if (existsSepsetContainingY == existsSepsetNotContainingY) {
            return CpcOrienter.TripleType.AMBIGUOUS;
        } else if (!existsSepsetNotContainingY) {
            return CpcOrienter.TripleType.NONCOLLIDER;
        } else {
            return CpcOrienter.TripleType.COLLIDER;
        }
    }

    private static List<Node> asList(int[] indices, List<Node> nodes) {
        List<Node> list = new LinkedList<Node>();

        for (int i : indices) {
            list.add(nodes.get(i));
        }

        return list;
    }

    private static boolean isArrowpointAllowed1(Node from, Node to,
                                                IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }

        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    //==============================CLASSES==============================//

    private enum TripleType {
        COLLIDER, NONCOLLIDER, AMBIGUOUS
    }
}




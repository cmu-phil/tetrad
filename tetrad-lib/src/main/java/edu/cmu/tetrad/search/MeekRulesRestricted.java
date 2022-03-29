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

import java.util.*;

/**
 * Implements Meek's complete orientation rule set for PC (Chris Meek (1995), "Causal inference and causal explanation
 * with background knowledge"), modified for Conservative PC to check noncolliders against recorded noncolliders before
 * orienting.
 * <p>
 * For now, the fourth rule is always performed.
 *
 * @author Joseph Ramsey
 */
public class MeekRulesRestricted implements ImpliedOrientation {

    private IKnowledge knowledge;

    /**
     * True if cycles are to be aggressively prevented. May be expensive for large graphs (but also useful for large
     * graphs).
     */
    private boolean aggressivelyPreventCycles;

    /**
     * If knowledge is available.
     */
    boolean useRule4;


    /**
     * The logger to use.
     */
    private ArrayList<OrderedPair<Edge>> changedEdges = new ArrayList<>();
    private final Set<Node> visitedNodes = new HashSet<>();

    private final Queue<Node> rule1Queue = new LinkedList<>();
    private final Queue<Node> rule2Queue = new LinkedList<>();
    private final Queue<Node> rule3Queue = new LinkedList<>();
    private final Queue<Node> rule4Queue = new LinkedList<>();
    private boolean orientInPlace;

    // Restricted to these nodes.
    private Set<Node> nodes;

//    private Set<Node> colliderNodes = null;

    /**
     * Constructs the <code>MeekRules</code> with no logging.
     */
    public MeekRulesRestricted() {
        this.useRule4 = this.knowledge != null && !this.knowledge.isEmpty();
    }

    //======================== Public Methods ========================//

    public Set<Node> orientImplied(final Graph graph) {
        this.nodes = new HashSet<>(graph.getNodes());
        this.visitedNodes.addAll(this.nodes);

        TetradLogger.getInstance().log("impliedOrientations", "Starting Orientation Step D.");
        this.changedEdges = new ArrayList<>();
        orientUsingMeekRulesLocally(this.knowledge, graph);
        TetradLogger.getInstance().log("impliedOrientations", "Finishing Orientation Step D.");

        graph.removeTriplesNotInGraph();

        return this.visitedNodes;
    }

    public void orientImplied(final Graph graph, final Set<Node> nodes) {
        this.nodes = nodes;
        this.visitedNodes.addAll(nodes);

        TetradLogger.getInstance().log("impliedOrientations", "Starting Orientation Step D.");
        this.changedEdges = new ArrayList<>();
        orientUsingMeekRulesLocally(this.knowledge, graph);
        TetradLogger.getInstance().log("impliedOrientations", "Finishing Orientation Step D.");

        graph.removeTriplesNotInGraph();
    }

    public void setKnowledge(final IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    //============================== Private Methods ===================================//

    private void orientUsingMeekRulesLocally(final IKnowledge knowledge, final Graph graph) {
//        List<Node> colliderNodes = getColliderNodes(graph);

        // Previously oriented, probably by knowledge.
        for (final Node node : getNodes()) {
            if (!graph.getParents(node).isEmpty()) {
                meekR1Locally(node, graph, knowledge);
                meekR2(node, graph, knowledge);
                meekR3(node, graph, knowledge);

                if (this.useRule4) {
                    meekR4(node, graph, knowledge);
                }
            }
        }

//        for (Node node : colliderNodes) {
//            meekR1Locally(node, graph, knowledge);
//            meekR2(node, graph, knowledge);
//            meekR3(node, graph, knowledge);
//
//            if (useRule4) {
//                meekR4(node, graph, knowledge);
//            }
//        }

        while (!this.rule1Queue.isEmpty() || !this.rule2Queue.isEmpty() || !this.rule3Queue.isEmpty() || !this.rule4Queue.isEmpty()) {
            while (!this.rule1Queue.isEmpty()) {
                final Node node = this.rule1Queue.remove();
                meekR1Locally(node, graph, knowledge);
            }

            while (!this.rule2Queue.isEmpty()) {
                final Node node = this.rule2Queue.remove();
                meekR2(node, graph, knowledge);
            }

            while (!this.rule3Queue.isEmpty()) {
                final Node node = this.rule3Queue.remove();
                meekR3(node, graph, knowledge);
            }

            while (!this.rule4Queue.isEmpty()) {
                final Node node = this.rule4Queue.remove();
                meekR4(node, graph, knowledge);
            }
        }
    }

//    private List<Node> getColliderNodes(Graph graph) {
//        if (colliderNodes != null) {
//            List<Node> nodes = new ArrayList<Node>();
//
//            for (Node node : colliderNodes) {
//                nodes.add(node);
//            }
//
//            return nodes;
//        }
//
//        List<Node> colliderNodes = new ArrayList<Node>();
//
//        NODES:
//        for (Node y : graph.getNodes()) {
//            List<Node> adj = graph.getAdjacentNodes(y);
//
//            int numInto = 0;
//
//            for (Node x : adj) {
//                if (graph.isDirectedFromTo(x, y)) numInto++;
//                if (numInto == 2) {
//                    colliderNodes.add(y);
//                    continue NODES;
//                }
//            }
//        }
//
//        return colliderNodes;
//    }

    /**
     * Meek's rule R1: if b-->a, a---c, and a not adj to c, then a-->c
     */
    private void meekR1Locally(final Node a, final Graph graph, final IKnowledge knowledge) {
        final List<Node> adjacentNodes = graph.getAdjacentNodes(a);
        this.visitedNodes.add(a);

        if (adjacentNodes.size() < 2) {
            return;
        }

        final ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
        int[] combination;

        while ((combination = cg.next()) != null) {
            final Node b = adjacentNodes.get(combination[0]);
            final Node c = adjacentNodes.get(combination[1]);

            // Skip triples that are shielded.
            if (graph.isAdjacentTo(b, c)) {
                continue;
            }

            if (graph.isDirectedFromTo(b, a) && graph.isUndirectedFromTo(a, c)) {
                if (!MeekRulesRestricted.isUnshieldedNoncollider(b, a, c, graph)) {
                    continue;
                }

                if (MeekRulesRestricted.isArrowpointAllowed(a, c, knowledge, graph) && !createsCycle(a, c, graph)) {
                    final Edge after = direct(a, c, graph);
                    final Node x = after.getNode1();
                    final Node y = after.getNode2();

//                    rule2Queue.add(x);
//                    rule3Queue.add(x);

                    this.rule1Queue.add(y);
                    this.rule2Queue.add(y);
                    this.rule3Queue.add(x);

                    if (this.useRule4) {
                        this.rule4Queue.add(x);
                    }

                    TetradLogger.getInstance().log("impliedOrientations", SearchLogUtils.edgeOrientedMsg(
                            "Meek R1 triangle (" + b + "-->" + a + "---" + c + ")", graph.getEdge(a, c)));
                }
            } else if (graph.isDirectedFromTo(c, a) && graph.isUndirectedFromTo(a, b)) {
                if (!MeekRulesRestricted.isUnshieldedNoncollider(b, a, c, graph)) {
                    continue;
                }

                if (MeekRulesRestricted.isArrowpointAllowed(a, b, knowledge, graph) && !createsCycle(a, b, graph)) {
                    final Edge after = direct(a, b, graph);
                    final Node x = after.getNode1();
                    final Node y = after.getNode2();

                    this.rule1Queue.add(y);
                    this.rule2Queue.add(y);
                    this.rule3Queue.add(x);

                    if (this.useRule4) {
                        this.rule4Queue.add(x);
                    }

                    TetradLogger.getInstance().log("impliedOrientations", SearchLogUtils.edgeOrientedMsg(
                            "Meek R1 (" + c + "-->" + a + "---" + b + ")", graph.getEdge(a, b)));
                }
            }
        }
    }

    /**
     * If b-->a-->c, b--c, then b-->c.
     */
    private void meekR2(final Node a, final Graph graph, final IKnowledge knowledge) {
        final List<Node> adjacentNodes = graph.getAdjacentNodes(a);
        this.visitedNodes.add(a);

        if (adjacentNodes.size() < 2) {
            return;
        }

        final ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
        int[] combination;

        while ((combination = cg.next()) != null) {
            final Node b = adjacentNodes.get(combination[0]);
            final Node c = adjacentNodes.get(combination[1]);

            if (graph.isDirectedFromTo(b, a) &&
                    graph.isDirectedFromTo(a, c) &&
                    graph.isUndirectedFromTo(b, c)) {
                if (MeekRulesRestricted.isArrowpointAllowed(b, c, knowledge, graph) && !createsCycle(b, c, graph)) {
                    final Edge after = direct(b, c, graph);
                    final Node x = after.getNode1();
                    final Node y = after.getNode2();

                    this.rule1Queue.add(y);
                    this.rule2Queue.add(y);
                    this.rule3Queue.add(x);

                    if (this.useRule4) {
                        this.rule4Queue.add(x);
                    }

                    TetradLogger.getInstance().log("impliedOrientations", SearchLogUtils.edgeOrientedMsg("Meek R2", graph.getEdge(b, c)));
                }
            } else if (graph.isDirectedFromTo(c, a) &&
                    graph.isDirectedFromTo(a, b) &&
                    graph.isUndirectedFromTo(c, b)) {
                if (MeekRulesRestricted.isArrowpointAllowed(c, b, knowledge, graph) && !createsCycle(c, b, graph)) {
                    final Edge after = direct(c, b, graph);
                    final Node x = after.getNode1();
                    final Node y = after.getNode2();

                    this.rule1Queue.add(y);
                    this.rule2Queue.add(y);
                    this.rule3Queue.add(x);

                    if (this.useRule4) {
                        this.rule4Queue.add(x);
                    }

                    TetradLogger.getInstance().log("impliedOrientations", SearchLogUtils.edgeOrientedMsg("Meek R2", graph.getEdge(c, b)));
                }
            }
        }
    }

    /**
     * Meek's rule R3. If a--b, a--c, a--d, c-->b, d-->b, then orient a-->b.
     */
    private void meekR3(final Node a, final Graph graph, final IKnowledge knowledge) {
        final List<Node> adjacentNodes = graph.getAdjacentNodes(a);
        this.visitedNodes.add(a);

        if (adjacentNodes.size() < 3) {
            return;
        }

        for (final Node b : adjacentNodes) {
            final List<Node> otherAdjacents = new LinkedList<>(adjacentNodes);
            otherAdjacents.remove(b);

            if (!graph.isUndirectedFromTo(a, b)) {
                continue;
            }

            final ChoiceGenerator cg = new ChoiceGenerator(otherAdjacents.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                final Node c = otherAdjacents.get(combination[0]);
                final Node d = otherAdjacents.get(combination[1]);

                if (graph.isAdjacentTo(c, d)) {
                    continue;
                }

                if (!graph.isDirectedFromTo(c, a)) {
                    continue;
                }

                if (!graph.isDirectedFromTo(d, a)) {
                    continue;
                }

                if (graph.isUndirectedFromTo(b, c) &&
                        graph.isUndirectedFromTo(b, d)) {
                    if (MeekRulesRestricted.isArrowpointAllowed(b, a, knowledge, graph) && !createsCycle(b, a, graph)) {
                        if (!MeekRulesRestricted.isUnshieldedNoncollider(c, b, d, graph)) {
                            continue;
                        }

                        final Edge after = direct(b, a, graph);

                        final Node x = after.getNode1();
                        final Node y = after.getNode2();

                        this.rule1Queue.add(y);
                        this.rule2Queue.add(y);
                        this.rule3Queue.add(x);

                        if (this.useRule4) {
                            this.rule4Queue.add(x);
                        }

                        TetradLogger.getInstance().log("impliedOrientations", SearchLogUtils.edgeOrientedMsg("Meek R3", graph.getEdge(a, b)));
//                        continue;
                    }
                }
            }
        }
    }

    private void meekR4(final Node a, final Graph graph, final IKnowledge knowledge) {
        if (!this.useRule4) {
            return;
        }

        final List<Node> adjacentNodes = graph.getAdjacentNodes(a);
        this.visitedNodes.add(a);

        if (adjacentNodes.size() < 3) {
            return;
        }

        for (final Node d : adjacentNodes) {
            if (!graph.isAdjacentTo(d, a)) {
                continue;
            }

            final List<Node> otherAdjacents = new LinkedList<>(adjacentNodes);
            otherAdjacents.remove(d);

            final ChoiceGenerator cg = new ChoiceGenerator(otherAdjacents.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                final Node b = otherAdjacents.get(combination[0]);
                final Node c = otherAdjacents.get(combination[1]);

                if (graph.isDirectedFromTo(b, a) && graph.isDirectedFromTo(a, c)) {
                    if (graph.isUndirectedFromTo(d, b) &&
                            graph.isUndirectedFromTo(d, c)) {
                        if (!MeekRulesRestricted.isUnshieldedNoncollider(c, d, b, graph)) {
                            continue;
                        }

                        if (MeekRulesRestricted.isArrowpointAllowed(d, c, knowledge, graph) && !createsCycle(d, c, graph)) {
                            final Edge after = direct(d, c, graph);
                            final Node x = after.getNode1();
                            final Node y = after.getNode2();

                            this.rule1Queue.add(y);
                            this.rule2Queue.add(y);
                            this.rule3Queue.add(x);

                            if (this.useRule4) {
                                this.rule4Queue.add(x);
                            }

                            TetradLogger.getInstance().log("impliedOientations", SearchLogUtils.edgeOrientedMsg("Meek T1", graph.getEdge(a, c)));
//                            continue;
                        }
                    }
                } else if (graph.isDirectedFromTo(c, a) && graph.isDirectedFromTo(a, b)) {
                    if (graph.isUndirectedFromTo(d, b) && graph.isUndirectedFromTo(d, c)) {
                        if (!MeekRulesRestricted.isUnshieldedNoncollider(c, d, b, graph)) {
                            continue;
                        }

                        if (MeekRulesRestricted.isArrowpointAllowed(d, c, knowledge, graph) && !createsCycle(d, c, graph)) {
                            final Edge after = direct(d, c, graph);
                            final Node x = after.getNode1();
                            final Node y = after.getNode2();

                            this.rule1Queue.add(y);
                            this.rule2Queue.add(y);
                            this.rule3Queue.add(x);

                            if (this.useRule4) {
                                this.rule4Queue.add(x);
                            }

                            TetradLogger.getInstance().log("impliedOientations", SearchLogUtils.edgeOrientedMsg("Meek T1", graph.getEdge(a, c)));
//                            continue;
                        }
                    }
                }
            }
        }
    }

    private Edge direct(final Node a, final Node c, final Graph graph) {
        final Edge before = graph.getEdge(a, c);
        final Edge after = Edges.directedEdge(a, c);

        graph.removeEdge(before);
        graph.addEdge(after);

        this.changedEdges.add(new OrderedPair<>(before, after));

        return after;
    }

    private static boolean isUnshieldedNoncollider(final Node a, final Node b, final Node c,
                                                   final Graph graph) {
        if (!graph.isAdjacentTo(a, b)) {
            return false;
        }

        if (!graph.isAdjacentTo(c, b)) {
            return false;
        }

        if (graph.isAdjacentTo(a, c)) {
            return false;
        }

        if (graph.isAmbiguousTriple(a, b, c)) {
            return false;
        }

        return !(graph.getEndpoint(a, b) == Endpoint.ARROW &&
                graph.getEndpoint(c, b) == Endpoint.ARROW);

    }


    private static boolean isArrowpointAllowed(final Node from, final Node to,
                                               final IKnowledge knowledge, final Graph graph) {
//        // Dont create a new unshielded collider.
//        List<Node> parents = graph.getParents(to);
//
//        for (int i = 0; i < parents.size(); i++) {
//            Node node2 = parents.get(i);
//
//            if (!graph.isAdjacentTo(from, node2)) {
//                return false;
//            }
//        }

        // Dont create a bidirected edge
//        Edge e = graph.getEdge(from, to);
//
//        if (e != null && e.pointsTowards(from)) {
//            return false;
//        }

        if (knowledge == null) return true;
        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    /**
     * @return true if orienting x-->y would create a cycle.
     */
    private boolean createsCycle(final Node x, final Node y, final Graph graph) {
        if (this.aggressivelyPreventCycles) {
            return graph.isAncestorOf(y, x);
        } else {
            return false;
        }
    }

    public boolean isAggressivelyPreventCycles() {
        return this.aggressivelyPreventCycles;
    }

    public void setAggressivelyPreventCycles(final boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    public List<OrderedPair<Edge>> getChangedEdges() {
        return this.changedEdges;
    }

    public Set<Node> getVisitedNodes() {
        return this.visitedNodes;
    }

    public boolean isOrientInPlace() {
        return this.orientInPlace;
    }

    public void setOrientInPlace(final boolean orientInPlace) {
        this.orientInPlace = orientInPlace;
    }

    public Set<Node> getNodes() {
        return this.nodes;
    }

//    public Set<Node> getCollidersNodes() {
//        return colliderNodes;
//    }
//
//    public void setColliderNodes(Set<Node> colliders) {
//        this.colliderNodes = colliders;
//    }
}




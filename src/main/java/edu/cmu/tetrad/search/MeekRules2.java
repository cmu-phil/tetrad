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
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Implements Meek's complete orientation rule set for PC (Chris Meek (1995), "Causal inference and causal explanation
 * with background knowledge"), modified for Conservative PC to check noncolliders against recorded noncolliders before
 * orienting.
 * <p/>
 * For now, the fourth rule is always performed.
 *
 * @author Joseph Ramsey
 */
public class MeekRules2 implements ImpliedOrientation {

    private IKnowledge knowledge;

    /**
     * True if cycles are to be aggressively prevented. May be expensive for large graphs (but also useful for large
     * graphs).
     */
    private boolean aggressivelyPreventCycles = false;

    /**
     * If knowledge is available.
     */
    boolean useRule4;


    /**
     * The logger to use.
     */
    private Map<Edge, Edge> changedEdges = new HashMap<Edge, Edge>();

    private Queue<Node> rule1Queue = new LinkedList<Node>();
    private Queue<Node> rule2Queue = new LinkedList<Node>();
    private Queue<Node> rule3Queue = new LinkedList<Node>();
    private Queue<Node> rule4Queue = new LinkedList<Node>();

//    private Set<Node> colliderNodes = null;

    /**
     * Constructs the <code>MeekRules</code> with no logging.
     */
    public MeekRules2() {
        useRule4 = knowledge != null && !knowledge.isEmpty();
    }

    //======================== Public Methods ========================//


    public void orientImplied(Graph graph) {
        TetradLogger.getInstance().log("impliedOrientations", "Starting Orientation Step D.");
        orientUsingMeekRulesLocally(knowledge, graph);
        TetradLogger.getInstance().log("impliedOrientations", "Finishing Orientation Step D.");
    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    //============================== Private Methods ===================================//

    private void orientUsingMeekRulesLocally(IKnowledge knowledge, Graph graph) {
//        List<Node> colliderNodes = getColliderNodes(graph);

        // Previously oriented, probably by knowledge.
        for (Node node : graph.getNodes()) {
            if (!graph.getParents(node).isEmpty()) {
                meekR1Locally(node, graph, knowledge);
                meekR2(node, graph, knowledge);
                meekR3(node, graph, knowledge);

                if (useRule4) {
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

        while (!rule1Queue.isEmpty() || !rule2Queue.isEmpty() || !rule3Queue.isEmpty()) {
            while (!rule1Queue.isEmpty()) {
                Node node = rule1Queue.remove();
                meekR1Locally(node, graph, knowledge);
            }

            while (!rule2Queue.isEmpty()) {
                Node node = rule2Queue.remove();
                meekR2(node, graph, knowledge);
            }
//            while (!rule1Queue.isEmpty() || !rule2Queue.isEmpty()) {
//           }

            while (!rule3Queue.isEmpty()) {
                Node node = rule3Queue.remove();
                meekR3(node, graph, knowledge);
            }

            if (useRule4) {
                while (!rule4Queue.isEmpty()) {
                    Node node = rule4Queue.remove();
                    meekR4(node, graph, knowledge);
                }
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
    private void meekR1Locally(Node a, Graph graph, IKnowledge knowledge) {
        List<Node> adjacentNodes = graph.getAdjacentNodes(a);

        if (adjacentNodes.size() < 2) {
            return;
        }

        ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
        int[] combination;

        while ((combination = cg.next()) != null) {
            Node b = adjacentNodes.get(combination[0]);
            Node c = adjacentNodes.get(combination[1]);


            if (graph.isDirectedFromTo(b, a) && graph.isUndirectedFromTo(a, c)) {
                // Skip triples that are shielded.
                if (graph.isAdjacentTo(b, c)) {
                    continue;
                }

                if (!isUnshieldedNoncollider(b, a, c, graph)) {
                    continue;
                }

                if (isArrowpointAllowed(a, c, knowledge, graph) && !createsCycle(a, c, graph)) {
                    Edge before = graph.getEdge(a, c);
                    graph.setEndpoint(a, c, Endpoint.ARROW);
                    Edge after = graph.getEdge(a, c);
                    queueVisits(after);
                    changedEdges.put(after, before);

                    TetradLogger.getInstance().log("impliedOrientations", SearchLogUtils.edgeOrientedMsg(
                            "Meek R1 triangle (" + b + "-->" + a + "---" + c + ")", graph.getEdge(a, c)));
                }
            } else if (graph.isDirectedFromTo(c, a) && graph.isUndirectedFromTo(a, b)) {

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(c, b)) {
                    continue;
                }

                if (!isUnshieldedNoncollider(b, a, c, graph)) {
                    continue;
                }

                if (isArrowpointAllowed(a, b, knowledge, graph) && !createsCycle(a, b, graph)) {
                    Edge before = graph.getEdge(a, b);
                    graph.setEndpoint(a, b, Endpoint.ARROW);
                    Edge after = graph.getEdge(a, b);
                    queueVisits(after);
                    changedEdges.put(after, before);

                    TetradLogger.getInstance().log("impliedOrientations", SearchLogUtils.edgeOrientedMsg(
                            "Meek R1 (" + c + "-->" + a + "---" + b + ")", graph.getEdge(a, b)));
                }
            }
        }
    }

    private void queueVisits(Edge edge) {
        Node x = edge.getNode1();
        Node y = edge.getNode2();

        rule2Queue.add(x);
        rule4Queue.add(x);

        rule1Queue.add(y);
        rule2Queue.add(y);
        rule3Queue.add(y);

        if (useRule4) {
            rule4Queue.add(y);
        }
    }

    /**
     * If b-->a-->c, b--c, then b-->c.
     */
    private void meekR2(Node a, Graph graph, IKnowledge knowledge) {
        List<Node> adjacentNodes = graph.getAdjacentNodes(a);

        if (adjacentNodes.size() < 2) {
            return;
        }

        ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
        int[] combination;

        while ((combination = cg.next()) != null) {
            Node b = adjacentNodes.get(combination[0]);
            Node c = adjacentNodes.get(combination[1]);

            if (graph.isDirectedFromTo(b, a) &&
                    graph.isDirectedFromTo(a, c) &&
                    graph.isUndirectedFromTo(b, c)) {
                if (isArrowpointAllowed(b, c, knowledge, graph) && !createsCycle(b, c, graph)) {
                    Edge before = graph.getEdge(b, c);
                    graph.setEndpoint(b, c, Endpoint.ARROW);
                    Edge after = graph.getEdge(b, c);
                    queueVisits(after);
                    changedEdges.put(after, before);
                    TetradLogger.getInstance().log("impliedOrientations", SearchLogUtils.edgeOrientedMsg("Meek R2", graph.getEdge(b, c)));
                }
            } else if (graph.isDirectedFromTo(c, a) &&
                    graph.isDirectedFromTo(a, b) &&
                    graph.isUndirectedFromTo(c, b)) {
                if (isArrowpointAllowed(c, b, knowledge, graph) && !createsCycle(c, b, graph)) {
                    Edge before = graph.getEdge(c, b);
                    graph.setEndpoint(c, b, Endpoint.ARROW);
                    Edge after = graph.getEdge(c, b);
                    queueVisits(after);
                    changedEdges.put(after, before);
                    TetradLogger.getInstance().log("impliedOrientations", SearchLogUtils.edgeOrientedMsg("Meek R2", graph.getEdge(c, b)));
                }
            }
        }
    }

    /**
     * Meek's rule R3. If a--b, a--c, a--d, c-->b, d-->b, then orient a-->b.
     */
    private void meekR3(Node a, Graph graph, IKnowledge knowledge) {
        List<Node> adjacentNodes = graph.getAdjacentNodes(a);

        if (adjacentNodes.size() < 3) {
            return;
        }

        for (Node b : adjacentNodes) {
            List<Node> otherAdjacents = new LinkedList<Node>(adjacentNodes);
            otherAdjacents.remove(b);

            if (!graph.isUndirectedFromTo(a, b)) {
                return;
            }

            ChoiceGenerator cg =
                    new ChoiceGenerator(otherAdjacents.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node c = otherAdjacents.get(combination[0]);
                Node d = otherAdjacents.get(combination[1]);

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
                    if (isArrowpointAllowed(b, a, knowledge, graph) && !createsCycle(b, a, graph)) {
                        if (!isUnshieldedNoncollider(c, b, d, graph)) {
                            continue;
                        }

                        Edge before = graph.getEdge(b, a);
                        graph.setEndpoint(b, a, Endpoint.ARROW);
                        Edge after = graph.getEdge(b, a);
                        queueVisits(after);
                        changedEdges.put(after, before);

                        TetradLogger.getInstance().log("impliedOrientations", SearchLogUtils.edgeOrientedMsg("Meek R3", graph.getEdge(a, b)));
                        break;
                    }
                }
            }
        }
    }

    private void meekR4(Node a, Graph graph, IKnowledge knowledge) {
        if (!useRule4) {
            return;
        }

        List<Node> adjacentNodes = graph.getAdjacentNodes(a);

        if (adjacentNodes.size() < 3) {
            return;
        }

        for (Node d : adjacentNodes) {
            if (!graph.isAdjacentTo(d, a)) {
                continue;
            }

            List<Node> otherAdjacents = new LinkedList<Node>(adjacentNodes);
            otherAdjacents.remove(d);

            ChoiceGenerator cg =
                    new ChoiceGenerator(otherAdjacents.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node b = otherAdjacents.get(combination[0]);
                Node c = otherAdjacents.get(combination[1]);

                if (graph.isDirectedFromTo(b, a) && graph.isDirectedFromTo(a, c)) {
                    if (graph.isUndirectedFromTo(d, b) &&
                            graph.isUndirectedFromTo(d, c)) {
                        if (!isUnshieldedNoncollider(c, d, b, graph)) {
                            continue;
                        }

                        if (isArrowpointAllowed(d, c, knowledge, graph) && !createsCycle(d, c, graph)) {
                            Edge before = graph.getEdge(d, c);
                            graph.setEndpoint(d, c, Endpoint.ARROW);
                            Edge after = graph.getEdge(d, c);
                            queueVisits(after);
                            changedEdges.put(after, before);

                            TetradLogger.getInstance().log("impliedOientations", SearchLogUtils.edgeOrientedMsg("Meek T1", graph.getEdge(a, c)));
                            break;
                        }
                    }
                } else if (graph.isDirectedFromTo(c, a) && graph.isDirectedFromTo(a, b)) {
                    if (graph.isUndirectedFromTo(d, b) && graph.isUndirectedFromTo(d, c)) {
                        if (!isUnshieldedNoncollider(c, d, b, graph)) {
                            continue;
                        }

                        if (isArrowpointAllowed(d, c, knowledge, graph) && !createsCycle(d, c, graph)) {
                            Edge before = graph.getEdge(d, c);
                            graph.setEndpoint(d, c, Endpoint.ARROW);
                            Edge after = graph.getEdge(d, c);
                            queueVisits(after);
                            changedEdges.put(after, before);

                            TetradLogger.getInstance().log("impliedOientations", SearchLogUtils.edgeOrientedMsg("Meek T1", graph.getEdge(a, c)));
                            break;
                        }
                    }
                }
            }
        }
    }

    private static boolean isUnshieldedNoncollider(Node a, Node b, Node c,
                                                   Graph graph) {
        if (graph.isAmbiguousTriple(a, b, c)) {
            return false;
        }

        if (!graph.isAdjacentTo(a, b)) {
            return false;
        }

        if (!graph.isAdjacentTo(c, b)) {
            return false;
        }

        if (graph.isAdjacentTo(a, c)) {
            return false;
        }

        return !(graph.getEndpoint(a, b) == Endpoint.ARROW &&
                graph.getEndpoint(c, b) == Endpoint.ARROW);

    }


    private static boolean isArrowpointAllowed(Node from, Node to,
                                               IKnowledge knowledge, Graph graph) {

//        // Dont create a new unshielded collider.
//        List<Node> parents = graph.getParents(to);
//
//        for (int i = 0; i < parents.size(); i++) {
//            Node node2 = parents.get(i);
////            if (node2 == from) continue;
//
//            if (!graph.isAdjacentTo(from, node2)) {
//                return false;
//            }
//        }

        if (knowledge == null) return true;
        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    /**
     * Returns true if orienting x-->y would create a cycle.
     */
    private boolean createsCycle(Node x, Node y, Graph graph) {
        if (aggressivelyPreventCycles) {
            return graph.isAncestorOf(y, x);
        } else {
            return false;
        }
    }

    public boolean isAggressivelyPreventCycles() {
        return aggressivelyPreventCycles;
    }

    public void setAggressivelyPreventCycles(boolean aggressivelyPreventCycles) {
        this.aggressivelyPreventCycles = aggressivelyPreventCycles;
    }

    public Map<Edge, Edge> getChangedEdges() {
        return changedEdges;
    }

//    public Set<Node> getCollidersNodes() {
//        return colliderNodes;
//    }
//
//    public void setColliderNodes(Set<Node> colliders) {
//        this.colliderNodes = colliders;
//    }
}

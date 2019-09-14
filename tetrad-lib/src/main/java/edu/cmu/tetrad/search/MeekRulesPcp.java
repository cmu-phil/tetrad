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

import java.io.PrintStream;
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
public class MeekRulesPcp implements ImpliedOrientation {

    private final Map<NodePair, List<Double>> P1;
    private final Map<NodePair, Double> P2;
    private final Map<NodePair, Double> Pp = new HashMap<>();
    private Set<NodePair> A;
    private IKnowledge knowledge;

    //True if cycles are to be aggressively prevented. May be expensive for large graphs (but also useful for large
    //graphs).
    private boolean aggressivelyPreventCycles = false;

    // If knowledge is available.
    boolean useRule4;

    //The logger to use.
    private Map<Edge, Edge> changedEdges = new HashMap<>();

    // The stack of nodes to be visited.
    private LinkedList<Node> directStack = new LinkedList<>();

    // Whether verbose output should be generated.

    private boolean verbose = false;

    // Where verbose output should be sent.
    private PrintStream out;

    // The initial list of nodes to visit.

    private List<Node> nodes = new ArrayList<>();

    // The lsit of nodes actually visited.
    private Set<Node> visited = new HashSet<>();

    // Edges already oriented by the algorithm to avoid repeats and prevent cycles.
    private HashSet<Edge> oriented;

    // True if unforced parents should be undirected before orienting.
    private boolean undirectUnforcedEdges = false;

    /**
     * Constructs the <code>MeekRules</code> with no logging.
     *
     * @param P1
     * @param P2
     */
    public MeekRulesPcp(Map<NodePair, List<Double>> P1, Map<NodePair, Double> P2, Set<NodePair> A) {
        useRule4 = knowledge != null && !knowledge.isEmpty();
        this.P1 = P1;
        this.P2 = P2;
        this.A = A;
    }

    //======================== Public Methods ========================//

    public void orientImplied(Graph graph) {
        orientImplied(graph, graph.getNodes());
    }

    public void orientImplied(Graph graph, List<Node> nodes) {
        this.nodes = nodes;
        this.visited.addAll(nodes);

        TetradLogger.getInstance().log("impliedOrientations", "Starting Orientation Step D.");

        orientUsingMeekRulesLocally(knowledge, graph);

        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();
            P2.put(new NodePair(x, y), Math.max(max(P1.get(new NodePair(x, y))), Pp.get(new NodePair(x, y))));
        }

        for (NodePair key : P1.keySet()) {
            if (P2.get(key) != null) {
                P2.put(key, max(P1.get(key)));
            }
        }

        for (Edge edge : graph.getEdges()) {
            final Node x = edge.getNode1();
            final Node y = edge.getNode2();

            if (Edges.isBidirectedEdge(edge)) {
                graph.removeEdge(edge);
                graph.addUndirectedEdge(x, y);
                A.add(new NodePair(x, y));
            }

            for (Node parent : graph.getParents(x)) {
                A.add(new NodePair(parent, x));
            }

            for (Node parent : graph.getParents(y)) {
                A.add(new NodePair(parent, y));
            }
        }

        TetradLogger.getInstance().log("impliedOrientations", "Finishing Orientation Step D.");

    }

    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
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

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public PrintStream getOut() {
        return out;
    }

    public Set<Node> getVisited() {
        return visited;
    }

    public boolean isUndirectUnforcedEdges() {
        return undirectUnforcedEdges;
    }

    public void setUndirectUnforcedEdges(boolean undirectUnforcedEdges) {
        this.undirectUnforcedEdges = undirectUnforcedEdges;
    }

    //============================== Private Methods ===================================//

    private void orientUsingMeekRulesLocally(IKnowledge knowledge, Graph graph) {

        for (Edge edge : graph.getEdges()) {
            Pp.put(new NodePair(edge.getNode1(), edge.getNode2()), 0.0);
        }

        oriented = new HashSet<>();

        if (undirectUnforcedEdges) {
            for (Node node : nodes) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                undirectUnforcedEdges(node, graph);
                directStack.addAll(graph.getAdjacentNodes(node));
            }
        }

        for (Node node : this.nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            runMeekRules(node, graph, knowledge);
        }

        while (!directStack.isEmpty()) {
            Node node = directStack.removeLast();

            if (undirectUnforcedEdges) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                undirectUnforcedEdges(node, graph);
            }

            runMeekRules(node, graph, knowledge);
        }

    }

    private void runMeekRules(Node node, Graph graph, IKnowledge knowledge) {
        meekR1(node, graph, knowledge);
        meekR2(node, graph, knowledge);
        meekR3(node, graph, knowledge);
        meekR4(node, graph, knowledge);
    }

    /**
     * Meek's rule R1: if a-->b, b---c, and a not adj to c, then a-->c
     */
    private void meekR1(Node b, Graph graph, IKnowledge knowledge) {
        List<Node> adjacentNodes = graph.getAdjacentNodes(b);

        if (adjacentNodes.size() < 2) {
            return;
        }

        ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
        int[] choice;

        while ((choice = cg.next()) != null) {
            List<Node> nodes = GraphUtils.asList(choice, adjacentNodes);
            Node a = nodes.get(0);
            Node c = nodes.get(1);

            r1Helper(a, b, c, graph, knowledge);
            r1Helper(c, b, a, graph, knowledge);
        }
    }

    private void r1Helper(Node a, Node b, Node c, Graph graph, IKnowledge knowledge) {
        if (!graph.isAdjacentTo(a, c) && graph.isDirectedFromTo(a, b)
                && graph.isAdjacentTo(b, c) && !pointsToward(b, c, graph)) {
            // && isUndirectedFromTo(b, c, graph)) {
            if (!isUnshieldedNoncollider(a, b, c, graph)) {
                return;
            }

            if (isArrowpointAllowed(b, c, knowledge)) {
                for (Node d : graph.getParents(b)) {
                    increment(b, c, P2.get(new NodePair(d, b)));
                }

                direct(b, c, graph);
                String message = SearchLogUtils.edgeOrientedMsg(
                        "Meek R1 triangle (" + b + "-->" + a + "---" + c + ")", graph.getEdge(a, c));
                log(message);
            }
        }
    }

    private void increment(Node b, Node c, double pp) {
        Pp.putIfAbsent(new NodePair(b, c), 0.0);
        Pp.put(new NodePair(b, c), pp);
    }

    /**
     * If a-->b-->c, a--c, then b-->c.
     */
    private void meekR2(Node c, Graph graph, IKnowledge knowledge) {
        List<Node> adjacentNodes = graph.getAdjacentNodes(c);

        if (adjacentNodes.size() < 2) {
            return;
        }

        ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
        int[] choice;

        while ((choice = cg.next()) != null) {
            List<Node> nodes = GraphUtils.asList(choice, adjacentNodes);
            Node a = nodes.get(0);
            Node b = nodes.get(1);

            r2Helper(a, b, c, graph, knowledge);
            r2Helper(b, a, c, graph, knowledge);
            r2Helper(a, c, b, graph, knowledge);
            r2Helper(c, a, b, graph, knowledge);
        }
    }

    private void r2Helper(Node a, Node b, Node c, Graph graph, IKnowledge knowledge) {
        if (graph.isDirectedFromTo(a, b) &&
                graph.isDirectedFromTo(b, c) &&
                graph.isAdjacentTo(a, c) && !pointsToward(a, c, graph)) {
            //&&
//                /*isUndirectedFromTo(a, c, graph)*/) {
            if (isArrowpointAllowed(a, c, knowledge)) {

                for (Node d : graph.getParents(c)) {
                    if (graph.isDirectedFromTo(b, d)) {
                        double p1 = P2.get(new NodePair(a, d));
                        double p2 = P2.get(new NodePair(d, c));

                        increment(a, c, Math.max(p1, p2));
                    }
                }

                direct(a, c, graph);
                log(SearchLogUtils.edgeOrientedMsg("Meek R2", graph.getEdge(b, c)));
            }
        }
    }

    private boolean isUndirectedFromTo(Node a, Node c, Graph graph) {
        Edge edge = graph.getEdge(a, c);
        return edge != null && Edges.isNondirectedEdge(edge);
    }

    /**
     * Meek's rule R3. If a--b, a--c, a--d, c-->b, d-->b, then orient a-->b.
     */
    private void meekR3(Node a, Graph graph, IKnowledge knowledge) {
        List<Node> adjacentNodes = graph.getAdjacentNodes(a);

        if (adjacentNodes.size() < 3) {
            return;
        }

        for (Node d : adjacentNodes) {
            if (Edges.isUndirectedEdge(graph.getEdge(a, d))) {
                List<Node> otherAdjacents = new ArrayList<>(adjacentNodes);
                otherAdjacents.remove(d);

                ChoiceGenerator cg = new ChoiceGenerator(otherAdjacents.size(), 2);
                int[] choice;

                while ((choice = cg.next()) != null) {
                    List<Node> nodes = GraphUtils.asList(choice, otherAdjacents);
                    Node b = nodes.get(0);
                    Node c = nodes.get(1);

                    boolean isKite = isKite(a, d, b, c, graph);

                    if (isKite) {
                        if (isArrowpointAllowed(d, a, knowledge)) {
                            if (!isUnshieldedNoncollider(c, d, b, graph)) {
                                continue;
                            }

                            for (Node e : graph.getParents(a)) {
                                if (graph.isAdjacentTo(b, e)) {
                                    double p1 = max(P1.get(new NodePair(d, e)));
                                    double p2 = P2.get(new NodePair(e, a));

                                    increment(a, c, Math.max(p1, p2));
                                }
                            }

                            direct(d, a, graph);
                            log(SearchLogUtils.edgeOrientedMsg("Meek R3", graph.getEdge(d, a)));
                        }
                    }
                }
            }
        }
    }

    private boolean isKite(Node a, Node d, Node b, Node c, Graph graph) {
        boolean b4 = isUndirectedFromTo(d, c, graph);
        boolean b5 = isUndirectedFromTo(d, b, graph);
        boolean b6 = graph.isDirectedFromTo(b, a);
        boolean b7 = graph.isDirectedFromTo(c, a);
//        boolean b8 = isUndirectedFromTo(d, a, graph);
        boolean b8 = graph.isAdjacentTo(d, a) && !pointsToward(d, a, graph);// isUndirectedFromTo(d, a, graph);

        return b4 && b5 && b6 && b7 && b8;
    }

    private boolean pointsToward(Node a, Node d, Graph graph) {
        final Edge edge = graph.getEdge(a, d);
        return edge.getProximalEndpoint(d) == Endpoint.ARROW;
    }

    private void meekR4(Node a, Graph graph, IKnowledge knowledge) {
        if (!useRule4) {
            return;
        }

        List<Node> adjacentNodes = graph.getAdjacentNodes(a);

        if (adjacentNodes.size() < 3) {
            return;
        }

        for (Node c : adjacentNodes) {
            List<Node> otherAdjacents = new LinkedList<>(adjacentNodes);
            otherAdjacents.remove(c);

            ChoiceGenerator cg = new ChoiceGenerator(otherAdjacents.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node b = otherAdjacents.get(combination[0]);
                Node d = otherAdjacents.get(combination[1]);

                if (!(graph.isAdjacentTo(a, b) && graph.isAdjacentTo(a, d) && graph.isAdjacentTo(b, c) && graph.isAdjacentTo(d, c) && graph.isAdjacentTo(a, c))) {
                    if (graph.isDirectedFromTo(b, c) && graph.isDirectedFromTo(c, d) && isUndirectedFromTo(a, d, graph)) {
                        if (isArrowpointAllowed(a, c, knowledge)) {
                            if (!isUnshieldedNoncollider(b, a, d, graph)) {
                                continue;
                            }
//
                            if (isArrowpointAllowed(c, d, knowledge)) {
                                direct(c, d, graph);
                                log(SearchLogUtils.edgeOrientedMsg("Meek R4", graph.getEdge(c, d)));
                                continue;
                            }
                        }
                    }

                    Node e = d;
                    d = b;
                    b = e;

                    if (graph.isDirectedFromTo(b, c) && graph.isDirectedFromTo(c, d) && !graph.isDirectedFromTo(a, d)) {
                        if (isArrowpointAllowed(a, c, knowledge)) {
                            if (!isUnshieldedNoncollider(b, a, d, graph)) {
                                continue;
                            }

                            if (isArrowpointAllowed(c, d, knowledge)) {
                                direct(c, d, graph);
                                log(SearchLogUtils.edgeOrientedMsg("Meek R4", graph.getEdge(c, d)));
                                continue;
                            }
                        }
                    }
                }
            }
        }
    }

    private void direct(Node a, Node b, Graph graph) {
        if (A.contains(new NodePair(a, b))) {
            return;
        }

        Edge before = graph.getEdge(a, b);

        if (aggressivelyPreventCycles && graph.isAncestorOf(b, a)) {
            return;
        }

        if (knowledge != null && knowledge.isForbidden(a.getName(), b.getName())) {
            return;
        }

        Edge after = new Edge(before);

//        Edge after = new Edge(before);

        after = new Edge(a, b, after.getProximalEndpoint(a), Endpoint.ARROW);
//
//        after.setProximalEndpoint(b, Endpoint.ARROW);

        visited.add(a);
        visited.add(b);

        graph.removeEdge(before);
        graph.addEdge(after);

        oriented.add(after);

        // Adding last works, checking for b or not. Adding first works, but when it is
        // checked whether directStack already contains it it seems to produce one in
        // 3000 trial error for FGES. Do not understand this yet.
        directStack.addLast(b);
    }

    private static boolean isUnshieldedNoncollider(Node a, Node b, Node c,
                                                   Graph graph) {
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


    private static boolean isArrowpointAllowed(Node from, Node to, IKnowledge knowledge) {
        if (knowledge == null) return true;
        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    private void undirectUnforcedEdges(Node y, Graph graph) {
        Set<Node> parentsToUndirect = new HashSet<>();
        List<Node> parents = graph.getParents(y);

        NEXT_EDGE:
        for (Node x : parents) {
            for (Node parent : parents) {
                if (parent != x) {
                    if (!graph.isAdjacentTo(parent, x)) {
                        oriented.add(graph.getEdge(x, y));
                        continue NEXT_EDGE;
                    }
                }
            }

            parentsToUndirect.add(x);
        }

        boolean didit = false;

        for (Node x : parentsToUndirect) {
            boolean mustOrient = knowledge.isRequired(x.getName(), y.getName()) ||
                    knowledge.isForbidden(y.getName(), x.getName());
            if (!oriented.contains(graph.getEdge(x, y)) && !mustOrient) {
                graph.removeEdge(x, y);
                graph.addNondirectedEdge(x, y);
                visited.add(x);
                visited.add(y);
                didit = true;
            }
        }

        if (didit) {
            for (Node z : graph.getAdjacentNodes(y)) {
                directStack.addLast(z);
            }

            directStack.addLast(y);
        }
    }

    private void log(String message) {
        if (verbose) {
            System.out.println(message);
            TetradLogger.getInstance().log("impliedOrientations", message);
        }
    }

    private double max(List<Double> p) {
        double max = Double.NEGATIVE_INFINITY;

        for (double d : p) {
            if (d > max) max = d;
        }

        return max;
    }
}




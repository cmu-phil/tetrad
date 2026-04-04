///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * Implements Meek's complete orientation rule set for PC (Chris Meek (1995), "Causal inference and causal explanation
 * orienting.
 * <p>
 * Rule R4 is only performed if knowledge is nonempty.
 * <p>
 * Note that the meekPreventCycles flag is set to true by default. This means that the algorithm will prevent cycles
 * from being created in the graph by adding arbitrary unshielded colliders to the graph. The user can turn this off if
 * they want to by setting the Meek prevent cycles flag to false, in which case the algorithm will not prevent cycles
 * from being created, e.g., by repeated applications of R1. This behavior was adjusted 2024-6-24, as a way to allow the
 * PC algorithm to always output a CPDAG.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class MeekRules {

    /**
     * The logger to use.
     */
    private final Map<Pair<Node, Node>, Pair<Edge, Edge>> changedEdges = new HashMap<>();
    /**
     * If knowledge is available.
     */
    boolean useRule4;
    /**
     * Represents the variable `knowledge` of type `Knowledge`.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * True if cycles are to be prevented. Default is true. If true, cycles are prevented adding arbitrary new
     * unshielded colliders to the graph.
     */
    private boolean meekPreventCycles = true;
    /**
     * Whether verbose output should be generated. True if verbose output should be printed.
     */
    private boolean verbose;
    /**
     * True (default) iff the graph should be reverted to its unshielded colliders before orienting.
     */
    private boolean revertToUnshieldedColliders = true;

    /**
     * Constructs the <code>MeekRules</code> with no logging.
     */
    public MeekRules() {
        this.useRule4 = !this.knowledge.isEmpty();
    }

    private static boolean isArrowheadAllowed(Node from, Node to, Knowledge knowledge) {
        if (knowledge.isEmpty()) return true;
        String f = from.getName();
        String t = to.getName();
        return !knowledge.isRequired(t, f) && !knowledge.isForbidden(f, t);
    }

    /**
     * Uses the Meek rules to do as many orientations in the given graph as possible.
     *
     * @param graph The graph.
     * @return The set of nodes that were visited in this orientation.
     */
    public Set<Node> orientImplied(Graph graph) {

        if (meekPreventCycles) {
            for (Edge edge : graph.getEdges()) {
                if (!(Edges.isDirectedEdge(edge) || Edges.isUndirectedEdge(edge))) {
                    throw new IllegalArgumentException("In order to guarantee the graph is a CPDAG, the graph must " +
                                                       "contain only directed or undirected edges.");
                }
            }
        }

        // The initial list of nodes to visit.
        Set<Node> visited = new HashSet<>();

        if (this.revertToUnshieldedColliders) {
            revertToUnshieldedColliders(graph.getNodes(), graph, visited);
        }

        boolean oriented = true;

        while (oriented) {
            oriented = false;

            if (orientByKnowledge(graph, visited)) oriented = true;

            List<Edge> undirected = new ArrayList<>();
            for (Edge e : graph.getEdges()) if (Edges.isUndirectedEdge(e)) undirected.add(e);

            for (Edge edge : undirected) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                Edge cur = graph.getEdge(x, y);
                if (cur == null || !Edges.isUndirectedEdge(cur)) continue;

                if (meekR1(x, y, graph, visited)) oriented = true;
                else if (meekR1(y, x, graph, visited)) oriented = true;
                else if (meekR2(x, y, graph, visited)) oriented = true;
                else if (meekR2(y, x, graph, visited)) oriented = true;
                else if (meekR3(x, y, graph, visited)) oriented = true;
                else if (meekR3(y, x, graph, visited)) oriented = true;
                else if (meekR4(x, y, graph, visited)) oriented = true;
                else if (meekR4(y, x, graph, visited)) oriented = true;
            }
        }

        return visited;
    }

    /**
     * Sets the knowledge to be used in the orientation.
     *
     * @param knowledge The knowledge.
     * @see Knowledge
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
        this.useRule4 = !this.knowledge.isEmpty();
    }

    /**
     * Sets whether cycles should be prevented by cycle checking. Default is true. If true, cycles are prevented by
     * refusing orientations that create cycles. This behavior was adjusted 2026-2-4, as a way to allow
     * the PC algorithm to more reliably output a CPDAG.
     *
     * @param meekPreventCycles True, if so.
     */
    public void setMeekPreventCycles(boolean meekPreventCycles) {
        this.meekPreventCycles = meekPreventCycles;
    }

    /**
     * Returns a complete set of all the edges that were changed in the course of orientation, as a map from the
     * previous edges in the graph to the new, changed edges for the same node pair. For example, if X-&gt;Y was changed
     * to X&lt;-Y, thie map will send X-&gt;Y to X&lt;-Y.
     *
     * @return This map.
     */
//    public Map<Edge, Edge> getChangedEdges() {
//        return this.changedEdges;
//    }

    public Map<Edge, Edge> getChangedEdges() {
        Map<Edge, Edge> out = new HashMap<>();
        for (Pair<Edge, Edge> p : changedEdges.values()) {
            out.put(p.getLeft(), p.getRight());
        }
        return out;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets whether orientations in the graph should be reverted to its unshielded colliders before performing any Meek
     * rule orientations.
     *
     * @param revertToUnshieldedColliders True, if so.
     */
    public void setRevertToUnshieldedColliders(boolean revertToUnshieldedColliders) {
        this.revertToUnshieldedColliders = revertToUnshieldedColliders;
    }

    /**
     * Reverts the subgraph of the given graph over the given nodes to just its unshielded colliders.
     *
     * @param nodes   The nodes of the subgraph.
     * @param graph   The graph.
     * @param visited The set of nodes visited.
     */
    private void revertToUnshieldedColliders(List<Node> nodes, Graph graph, Set<Node> visited) {
        for (Node node : nodes) {
            revertToUnshieldedColliders(node, graph, visited);
        }
    }

    /**
     * Meek's rule R1: if a-->b, b---c, and a not adj to c, then b-->c
     */
    private boolean meekR1(Node b, Node c, Graph graph, Set<Node> visited) {
        for (Node a : graph.getParents(b)) {
            if (graph.isAdjacentTo(c, a)) continue;
            if (direct(b, c, graph, visited)) {
                log(LogUtilsSearch.edgeOrientedMsg("Meek R1 triangle (" + a + "-->" + b + "---" + c + ")", graph.getEdge(b, c)));
                return true;
            }
        }

        return false;
    }

    /**
     * Meek's rule R2: if a-->b-->c and a--c, then a-->c.
     */
    private boolean meekR2(Node a, Node c, Graph graph, Set<Node> visited) {
        Set<Node> common = getCommonAdjacents(a, c, graph);
        boolean oriented = false;

        for (Node b : common) {
            if (graph.isParentOf(a, b) && graph.isParentOf(b, c)) {
                if (direct(a, c, graph, visited)) {
                    log(LogUtilsSearch.edgeOrientedMsg("Meek R2 triangle (" + a + "-->" + b + "-->" + c + ", " + a + "--" + c + ")", graph.getEdge(a, c)));
                    oriented = true;
                }
            }

            if (graph.isParentOf(c, b) && graph.isParentOf(b, a)) {
                if (direct(c, a, graph, visited)) {
                    log(LogUtilsSearch.edgeOrientedMsg("Meek R2 triangle (" + c + "-->" + b + "-->" + a + ", " + c + "--" + a + ")", graph.getEdge(c, a)));
                    oriented = true;
                }
            }
        }

        return oriented;
    }

    /**
     * Meek's rule R3. If d--a, d--b, d--c, b-->a, c-->a, then orient d-->a.
     */
    private boolean meekR3(Node d, Node a, Graph graph, Set<Node> visited) {
        List<Node> adjacentNodes = new ArrayList<>(getCommonAdjacents(a, d, graph));

        if (adjacentNodes.size() < 2) {
            return false;
        }

        boolean oriented = false;

        for (int i = 0; i < adjacentNodes.size(); i++) {
            for (int j = i + 1; j < adjacentNodes.size(); j++) {
                Node b = adjacentNodes.get(i);
                Node c = adjacentNodes.get(j);

                if (!graph.isAdjacentTo(b, c)) {
                    if (r3Helper(a, d, b, c, graph, visited)) {
                        oriented = true;
                    }
                }
            }
        }

        return oriented;
    }

    private boolean r3Helper(Node a, Node d, Node b, Node c, Graph graph, Set<Node> visited) {
        if (graph.isParentOf(b, a) && graph.isParentOf(c, a)
            && Edges.isUndirectedEdge(graph.getEdge(d, a))
            && Edges.isUndirectedEdge(graph.getEdge(d, b))
            && Edges.isUndirectedEdge(graph.getEdge(d, c))) {
            if (direct(d, a, graph, visited)) {
                log(LogUtilsSearch.edgeOrientedMsg("Meek R3 " + d + "--" + a + ", " + b + ", " + c, graph.getEdge(d, a)));
                return true;
            }
        }

        return false;
    }

    /**
     * Meek's rule R4. If a--b, a--c, a--d, c->b, d->b, c not adj to d, then a-->b.
     */
    private boolean meekR4(Node a, Node b, Graph graph, Set<Node> visited) {
        if (!this.useRule4) return false;

        Edge ab = graph.getEdge(a, b);
        if (ab == null || !Edges.isUndirectedEdge(ab)) return false;   // require a--b

        // candidates c: a--c and c->b
        List<Node> cand = new ArrayList<>();
        for (Node c : graph.getAdjacentNodes(a)) {
            if (c == b) continue;

            Edge ac = graph.getEdge(a, c);
            if (ac == null || !Edges.isUndirectedEdge(ac)) continue;   // require a--c
            if (!graph.isParentOf(c, b)) continue;                     // require c->b

            cand.add(c);
        }

        if (cand.size() < 2) return false;

        // need two nonadjacent candidates c,d (c not adj d)
        for (int i = 0; i < cand.size(); i++) {
            Node c = cand.get(i);
            for (int j = i + 1; j < cand.size(); j++) {
                Node d = cand.get(j);

                if (graph.isAdjacentTo(c, d)) continue; // require c not adj d

                // Pattern satisfied: try to orient a->b.
                // If direct() refuses (knowledge/cycle), keep searching other pairs.
                if (direct(a, b, graph, visited)) {
                    log(LogUtilsSearch.edgeOrientedMsg(
                            "Meek R4 (" + c + "->" + b + ", " + d + "->" + b
                                    + ", " + a + "---" + c + ", " + a + "---" + d + ")",
                            graph.getEdge(a, b)));
                    return true;
                }
            }
        }

        // If the pattern never occurred, R4 doesn't apply; if it occurred but direct() refused for all
        // witnessing pairs, also return false.
        return false;
    }
    /**
     * Directs an edge from a to c in the graph, if the edge is allowed by the knowledge and the edge is undirected.
     *
     * @param a       The node from which the edge is directed.
     * @param c       The node to which the edge is directed.
     * @param graph   The graph.
     * @param visited The set of visited nodes.
     * @return True if the edge was directed.
     */
    private boolean direct(Node a, Node c, Graph graph, Set<Node> visited) {
        if (!MeekRules.isArrowheadAllowed(a, c, this.knowledge)) return false;

        Edge e = graph.getEdge(a, c);
        if (e == null) return false;
        if (!Edges.isUndirectedEdge(e)) return false;

        Edge before = e;

        if (meekPreventCycles && graph.paths().existsDirectedPath(c, a)) {
            return false;
        }

        graph.removeEdge(before);
        Edge after = Edges.directedEdge(a, c);

        visited.add(a);
        visited.add(c);

        graph.addEdge(after);

        // NEW: record the change
        recordChange(before, after);

        return true;
    }


    /**
     * Reverts edges not in unshielded colliders to undirected edges.
     *
     * @param y       The node to revert.
     * @param graph   The graph.
     * @param visited The set of visited nodes.
     */
    private void revertToUnshieldedColliders(Node y, Graph graph, Set<Node> visited) {
        Set<Pair<Node, Node>> keep = new HashSet<>();

        List<Node> parents = graph.getNodesInTo(y, Endpoint.ARROW);
        ChoiceGenerator gen = new ChoiceGenerator(parents.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            Node x = parents.get(choice[0]);
            Node z = parents.get(choice[1]);

            if (!graph.isAdjacentTo(x, z)) {
                keep.add(Pair.of(x, y));
                keep.add(Pair.of(z, y));
            }
        }

        for (Node z : parents) {
            if (!keep.contains(Pair.of(z, y))) {
                if (this.knowledge.isForbidden(y.getName(), z.getName()) || this.knowledge.isRequired(z.getName(), y.getName()))
                    continue;

                Edge before = graph.getEdge(z, y);

                graph.removeEdge(z, y);
                graph.addUndirectedEdge(z, y);

                Edge after = graph.getEdge(z, y);
                recordChange(before, after);

                visited.add(z);
                visited.add(y);
            }
        }
    }

    /**
     * Logs a message if the verbose flag is set.
     *
     * @param message The message to be logged.
     */
    private void log(String message) {
        if (this.verbose) {
            TetradLogger.getInstance().log(message);
        }
    }

    /**
     * Returns the set of common adjacent nodes between two given nodes in a given graph.
     *
     * @param x     The first node.
     * @param y     The second node.
     * @param graph The graph.
     * @return The set of common adjacent nodes between the two given nodes.
     */
    private Set<Node> getCommonAdjacents(Node x, Node y, Graph graph) {
        Set<Node> adj = new HashSet<>(graph.getAdjacentNodes(x));
        adj.retainAll(graph.getAdjacentNodes(y));
        return adj;
    }

    // In MeekRules
    private boolean orientByKnowledge(Graph graph, Set<Node> visited) {
        boolean changed = false;

        for (Edge e : new ArrayList<>(graph.getEdges())) {
            Node a = e.getNode1();
            Node b = e.getNode2();

            String an = a.getName();
            String bn = b.getName();

            boolean reqAtoB = knowledge.isRequired(an, bn);
            boolean reqBtoA = knowledge.isRequired(bn, an);

            boolean forbAtoB = knowledge.isForbidden(an, bn);
            boolean forbBtoA = knowledge.isForbidden(bn, an);

            // ------------------------------------------------------------------
            // 1) If knowledge REQUIRES a direction, enforce it if possible.
            // ------------------------------------------------------------------
            if (reqAtoB && !reqBtoA) {
                // Enforce a -> b if possible
                if (Edges.isUndirectedEdge(e)) {
                    if (direct(a, b, graph, visited)) {
                        changed = true;
                    }
                } else if (Edges.isDirectedEdge(e) && !e.pointsTowards(b)) {
                    // Already directed the wrong way: don't "flip" it here (that can break CPDAG legality).
                    // Leave it; Meek+knowledge can’t both be satisfied without violating current structure.
                    // (Alternatively: you can throw if you consider this an inconsistent-knowledge situation.)
                }
                continue;
            }

            if (reqBtoA && !reqAtoB) {
                if (Edges.isUndirectedEdge(e)) {
                    if (direct(b, a, graph, visited)) {
                        changed = true;
                    }
                } else if (Edges.isDirectedEdge(e) && !e.pointsTowards(a)) {
                    // same comment as above
                }
                continue;
            }

            // If both directions are "required" (shouldn't happen, but defensively), do nothing.
            if (reqAtoB && reqBtoA) continue;

            // ------------------------------------------------------------------
            // 2) Otherwise, for UNDIRECTED edges only: if exactly one direction is
            //    permitted by knowledge, orient it.
            // ------------------------------------------------------------------
            if (!Edges.isUndirectedEdge(e)) continue;

            boolean a_to_b_ok = isArrowheadAllowed(a, b, knowledge);
            boolean b_to_a_ok = isArrowheadAllowed(b, a, knowledge);

            if (a_to_b_ok && !b_to_a_ok) {
                if (direct(a, b, graph, visited)) changed = true;
            } else if (b_to_a_ok && !a_to_b_ok) {
                if (direct(b, a, graph, visited)) changed = true;
            }
        }

        return changed;
    }

    private static Pair<Node, Node> pairKey(Node u, Node v) {
        // canonicalize order so (u,v) and (v,u) are the same key
        return (u.getName().compareTo(v.getName()) <= 0) ? Pair.of(u, v) : Pair.of(v, u);
    }

    private void recordChange(Edge before, Edge after) {
        if (before == null || after == null) return;

        Pair<Node, Node> key = pairKey(before.getNode1(), before.getNode2());
        Pair<Edge, Edge> cur = changedEdges.get(key);

        Edge firstBefore = (cur == null) ? before : cur.getLeft();
        changedEdges.put(key, Pair.of(firstBefore, after));
    }
}






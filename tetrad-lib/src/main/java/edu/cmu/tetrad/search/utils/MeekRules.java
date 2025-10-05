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
    private final Map<Edge, Edge> changedEdges = new HashMap<>();
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
    private boolean meekPreventCycles = false;
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
        return !knowledge.isRequired(to.toString(), from.toString()) && !knowledge.isForbidden(from.toString(), to.toString());
    }

    /**
     * Uses the Meek rules to do as many orientations in the given graph as possible.
     *
     * @param graph The graph.
     * @return The set of nodes that were visited in this orientation.
     */
    public Set<Node> orientImplied(Graph graph) {

        // If the meekPreventCycles flag is set to tru, eheck that the graph contains only directed or undirected
        // edges (i.e., is a mixed graph). For instance, if the graph contains bidirected edges, which
        // PC can possibly orient with one choice of collider conflict policy, then the graph is not a mixed
        // graph and the meekPreventCycles flag should be set to false. Also, if the graph contains a cycle, then
        // the meekPreventCycles flag should be set to false; otherwise, a model will be output that contains
        // a cycle. Also, this method cannot be applied to, say, PAGs, that contain edges other than directed
        // or undirected edges.
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

        if (verbose) {
            TetradLogger.getInstance().log("Starting Orientation Step D.");
        }

        if (this.revertToUnshieldedColliders) {
            revertToUnshieldedColliders(graph.getNodes(), graph, visited);
        }

        boolean oriented = true;

        while (oriented) {
            oriented = false;

            if (orientByKnowledge(graph, visited)) oriented = true;

            for (Edge edge : graph.getEdges()) {
                if (!Edges.isUndirectedEdge(edge)) continue;

                Node x = edge.getNode1();
                Node y = edge.getNode2();

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

        if (verbose) {
            TetradLogger.getInstance().log("Finishing Orientation Step D.");
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
    }

    /**
     * Sets whether cycles should be prevented by cycle checking. Default is true. If true, cycles are prevented by
     * adding arbitrary new unshielded colliders to the graph. This behavior was adjusted 2024-6-24, as a way to allow
     * the PC algorithm to always output a CPDAG.
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
    public Map<Edge, Edge> getChangedEdges() {
        return this.changedEdges;
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
     * If a-->b-->c, a--c, then a-->c.
     */
    private boolean meekR2(Node a, Node c, Graph graph, Set<Node> visited) {
        List<Node> adjacentNodes = graph.getAdjacentNodes(c);
        adjacentNodes.remove(a);

        Set<Node> common = getCommonAdjacents(a, c, graph);
        boolean oriented = false;

        for (Node b : common) {
            if (graph.paths().isDirected(a, b) && graph.paths().isDirected(b, c)) {
                if (r2Helper(a, b, c, graph, visited)) {
                    oriented = true;
                }
            }

            if (graph.paths().isDirected(c, b) && graph.paths().isDirected(b, a)) {
                if (r2Helper(c, b, a, graph, visited)) {
                    oriented = true;
                }
            }
        }

        return oriented;
    }

    private boolean r2Helper(Node a, Node b, Node c, Graph graph, Set<Node> visited) {
        if (direct(a, c, graph, visited)) {
            log(LogUtilsSearch.edgeOrientedMsg("Meek R2 triangle (" + a + "-->" + b + "-->" + c + ", " + a + "--" + c + ")", graph.getEdge(a, c)));
            return true;
        }
        return false;
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
        boolean b4 = graph.paths().isUndirected(d, a);
        boolean b5 = graph.paths().isUndirected(d, b);
        boolean b6 = graph.paths().isUndirected(d, c);
        boolean b7 = graph.paths().isDirected(b, a);
        boolean b8 = graph.paths().isDirected(c, a);

        if (b4 && b5 && b6 && b7 && b8) {
            if (direct(d, a, graph, visited)) {
                log(LogUtilsSearch.edgeOrientedMsg("Meek R3 " + d + "--" + a + ", " + b + ", " + c, graph.getEdge(d, a)));
                return true;
            }
        }

        return false;
    }

    /**
     * Meek's rule R4. If a--b, b--c, a--d, c not adj to d, then a-->c.
     */
    private boolean meekR4(Node a, Node b, Graph graph, Set<Node> visited) {
        if (!this.useRule4) {
            return false;
        }

        boolean oriented = false;

        for (Node c : graph.getParents(b)) {
            Set<Node> adj = getCommonAdjacents(a, c, graph);
            adj.remove(b);

            for (Node d : adj) {
                if (graph.isAdjacentTo(b, d)) continue;
                Edge dc = graph.getEdge(d, c);
                if (!dc.pointsTowards(c)) continue;
                if (graph.getEdge(a, d).isDirected()) continue;
                if (direct(a, b, graph, visited)) {
                    log(LogUtilsSearch.edgeOrientedMsg("Meek R4 using " + c + ", " + d, graph.getEdge(a, b)));
                    oriented = true;
                }
            }
        }

        return oriented;
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
        if (!Edges.isUndirectedEdge(graph.getEdge(a, c))) return false;

        Edge before = graph.getEdge(a, c);
        graph.removeEdge(before);

        // We prevent new cycles in the graph by adding arbitrary unshielded colliders to prevent cycles.
        // The user can turn this off if they want to by setting the Meek prevent cycles flag to false.
        if (meekPreventCycles && graph.paths().existsDirectedPath(c, a)) {

            // Log this before adding a <-- c back so that we don't accidentally say we added c --> a <--c
            // as an unshielded collider.
            if (verbose) {
                graph.getNodesInTo(a, Endpoint.ARROW).forEach(node -> {
                    if (!graph.isAdjacentTo(node, c)) {
                        TetradLogger.getInstance().log("Meek: Prevented cycle by orienting " + a + "---" + c + " as " + a + "<--" + c + " creating new unshielded collider " + node + " --> " + a + " <-- " + c);
                    }
                });
            }

//            graph.addEdge(before);
//            return false;

            graph.addEdge(Edges.directedEdge(c, a));

            visited.add(a);
            visited.add(c);

            return true;
        }

        Edge after = Edges.directedEdge(a, c);

        visited.add(a);
        visited.add(c);

        graph.addEdge(after);

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

                graph.removeEdge(z, y);
                graph.addUndirectedEdge(z, y);

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
            if (!Edges.isUndirectedEdge(e)) continue;

            Node a = e.getNode1();
            Node b = e.getNode2();

            boolean a_to_b_ok = isArrowheadAllowed(a, b, this.knowledge);
            boolean b_to_a_ok = isArrowheadAllowed(b, a, this.knowledge);

            // Exactly one direction permitted by knowledge ⇒ orient that way
            if (a_to_b_ok && !b_to_a_ok) {
                if (direct(a, b, graph, visited)) changed = true;
            } else if (b_to_a_ok && !a_to_b_ok) {
                if (direct(b, a, graph, visited)) changed = true;
            }
        }
        return changed;
    }
}






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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Implements Meek's complete orientation rule set for PC (Chris Meek (1995), "Causal inference and causal explanation
 * with background knowledge"), modified for Conservative PC to check noncolliders against recorded noncolliders before
 * orienting.
 * <p>
 * Rule R4 is only performed if knowledge is nonempty.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class MeekRules {

    //The logger to use.
    private final Map<Edge, Edge> changedEdges = new HashMap<>();
    // If knowledge is available.
    boolean useRule4;
    private Knowledge knowledge = new Knowledge();
    //True if cycles are to be prevented. May be expensive for large graphs (but also useful for large
    //graphs).
    private boolean meekPreventCycles;

    // Whether verbose output should be generated.
    // True if verbose output should be printed.
    private boolean verbose;

    // True (default) iff the graph should be reverted to its unshielded colliders before orienting.
    private boolean revertToUnshieldedColliders = true;

    /**
     * Constructs the <code>MeekRules</code> with no logging.
     */
    public MeekRules() {
        this.useRule4 = !this.knowledge.isEmpty();
    }


    private static boolean isArrowheadAllowed(Node from, Node to, Knowledge knowledge) {
        if (knowledge.isEmpty()) return true;
        return !knowledge.isRequired(to.toString(), from.toString()) &&
               !knowledge.isForbidden(from.toString(), to.toString());
    }

    /**
     * Uses the Meek rules to do as many orientations in the given graph as possible.
     *
     * @param graph The graph.
     * @return The set of nodes that were visited in this orientation.
     */
    public Set<Node> orientImplied(Graph graph) {
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
     * Sets whether cycles should be prevented by cycle checking.
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
                log(LogUtilsSearch.edgeOrientedMsg(
                        "Meek R1 triangle (" + a + "-->" + b + "---" + c + ")", graph.getEdge(b, c)));
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
            log(LogUtilsSearch.edgeOrientedMsg(
                    "Meek R2 triangle (" + a + "-->" + b + "-->" + c + ", " + a + "--" + c + ")", graph.getEdge(a, c)));
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
                log(LogUtilsSearch.edgeOrientedMsg("Meek R3 " + d + "--" + a + ", " + b + ", "
                                                   + c, graph.getEdge(d, a)));
                return true;
            }
        }

        return false;
    }

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

    private boolean direct(Node a, Node c, Graph graph, Set<Node> visited) {
        if (!MeekRules.isArrowheadAllowed(a, c, this.knowledge)) return false;
        if (!Edges.isUndirectedEdge(graph.getEdge(a, c))) return false;

        Edge before = graph.getEdge(a, c);
        graph.removeEdge(before);

        // We prevent new cycles in the graph by adding arbitrary unshielded colliders to prevent cycles.
        // The user can turn this off if they want to by setting the Meek prevent cycles flag to false.
        if (meekPreventCycles && graph.paths().existsDirectedPath(c, a)) {
            graph.addEdge(Edges.directedEdge(c, a));
            visited.add(a);
            visited.add(c);
            return false;
        }

        Edge after = Edges.directedEdge(a, c);

        visited.add(a);
        visited.add(c);

        graph.addEdge(after);

        return true;
    }

    private void revertToUnshieldedColliders(Node y, Graph graph, Set<Node> visited) {
        List<Node> parents = graph.getParents(y);

        P:
        for (Node p : parents) {
            for (Node q : parents) {
                if (p != q && !graph.isAdjacentTo(p, q)) {
                    continue P;
                }
            }

            if (this.knowledge.isForbidden(y.getName(), p.getName()) || this.knowledge.isRequired(p.getName(), y.getName()))
                continue;

            graph.removeEdge(p, y);
            graph.addUndirectedEdge(p, y);

            visited.add(p);
            visited.add(y);
        }
    }

    private void log(String message) {
        if (this.verbose) {
            TetradLogger.getInstance().log(message);
        }
    }

    private Set<Node> getCommonAdjacents(Node x, Node y, Graph graph) {
        Set<Node> adj = new HashSet<>(graph.getAdjacentNodes(x));
        adj.retainAll(graph.getAdjacentNodes(y));
        return adj;
    }
}





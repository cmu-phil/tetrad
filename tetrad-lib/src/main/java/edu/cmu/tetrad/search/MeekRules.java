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
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.*;

/**
 * Implements Meek's complete orientation rule set for PC (Chris Meek (1995), "Causal inference and causal explanation
 * with background knowledge"), modified for Conservative PC to check noncolliders against recorded noncolliders before
 * orienting.
 * <p>
 * Rule R4 is only performed if knowledge is nonempty.
 *
 * @author Joseph Ramsey
 */
public class MeekRules implements ImpliedOrientation {

    private IKnowledge knowledge = new Knowledge2();

    //True if cycles are to be aggressively prevented. May be expensive for large graphs (but also useful for large
    //graphs).
    private boolean aggressivelyPreventCycles = false;

    // If knowledge is available.
    boolean useRule4;

    //The logger to use.
    private final Map<Edge, Edge> changedEdges = new HashMap<>();

    // Whether verbose output should be generated.

    // Where verbose output should be sent.
    private PrintStream out;

    // True if verbose output should be printed.
    private boolean verbose = false;

    // True (default) iff the graph should be reverted to its unshielded colliders before orienting.
    private boolean revertToUnshieldedColliders = true;

    /**
     * Constructs the <code>MeekRules</code> with no logging.
     */
    public MeekRules() {
        useRule4 = !knowledge.isEmpty();
    }

    //======================== Public Methods ========================//

    public Set<Node> orientImplied(Graph graph) {
        // The initial list of nodes to visit.
        Set<Node> visited = new HashSet<>();

        TetradLogger.getInstance().log("impliedOrientations", "Starting Orientation Step D.");

        if (revertToUnshieldedColliders) {
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

        TetradLogger.getInstance().log("impliedOrientations", "Finishing Orientation Step D.");

        return visited;
    }

    public void revertToUnshieldedColliders(List<Node> nodes, Graph graph, Set<Node> visited) {
        boolean reverted = true;

        while (reverted) {
            reverted = false;

            for (Node node : nodes) {
                if (revertToUnshieldedColliders(node, graph, visited)) {
                    reverted = true;
                }
            }
        }
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) throw new IllegalArgumentException();
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

    //============================== Private Methods ===================================//

    /**
     * Meek's rule R1: if a-->b, b---c, and a not adj to c, then b-->c
     */
    private boolean meekR1(Node b, Node c, Graph graph, Set<Node> visited) {
        for (Node a : graph.getParents(b)) {
            if (graph.isAdjacentTo(c, a)) continue;
            if (direct(b, c, graph, visited)) {
                log(SearchLogUtils.edgeOrientedMsg(
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

        for (Node b : common) {
            if (graph.isDirectedFromTo(a, b) && graph.isDirectedFromTo(b, c)) {
                if (r2Helper(a, b, c, graph, visited)) {
                    return true;
                }
            }

            if (graph.isDirectedFromTo(c, b) && graph.isDirectedFromTo(b, a)) {
                if (r2Helper(c, b, a, graph, visited)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean r2Helper(Node a, Node b, Node c, Graph graph, Set<Node> visited) {
        boolean directed = direct(a, c, graph, visited);
        log(SearchLogUtils.edgeOrientedMsg(
                "Meek R2 triangle (" + a + "-->" + b + "-->" + c + ", " + a + "---" + c + ")", graph.getEdge(a, c)));
        return directed;
    }

    /**
     * Meek's rule R3. If d--a, d--b, d--c, b-->a, c-->a, then orient d-->a.
     */
    private boolean meekR3(Node d, Node a, Graph graph, Set<Node> visited) {
        List<Node> adjacentNodes = new ArrayList<>(getCommonAdjacents(a, d, graph));

        if (adjacentNodes.size() < 2) {
            return false;
        }

        for (int i = 0; i < adjacentNodes.size(); i++) {
            for (int j = i + 1; j < adjacentNodes.size(); j++) {
                Node b = adjacentNodes.get(i);
                Node c = adjacentNodes.get(j);

                if (!graph.isAdjacentTo(b, c)) {
                    if (r3Helper(a, d, b, c, graph, visited)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean r3Helper(Node a, Node d, Node b, Node c, Graph graph, Set<Node> visited) {
        boolean oriented = false;

        boolean b4 = graph.isUndirectedFromTo(d, a);
        boolean b5 = graph.isUndirectedFromTo(d, b);
        boolean b6 = graph.isUndirectedFromTo(d, c);
        boolean b7 = graph.isDirectedFromTo(b, a);
        boolean b8 = graph.isDirectedFromTo(c, a);

        if (b4 && b5 && b6 && b7 && b8) {
            oriented = direct(d, a, graph, visited);
            log(SearchLogUtils.edgeOrientedMsg("Meek R3 " + d + "--" + a + ", " + b + ", "
                    + c, graph.getEdge(d, a)));
        }

        return oriented;
    }

    private boolean meekR4(Node a, Node b, Graph graph, Set<Node> visited) {
        if (!useRule4) {
            return false;
        }

        for (Node c : graph.getParents(b)) {
            Set<Node> adj = getCommonAdjacents(a, c, graph);
            adj.remove(b);

            for (Node d : adj) {
                if (graph.isAdjacentTo(b, d)) continue;
                Edge dc = graph.getEdge(d, c);
                if (!dc.pointsTowards(c)) continue;
                if (graph.getEdge(a, d).isDirected()) continue;
                if (direct(a, b, graph, visited)) {
                    log(SearchLogUtils.edgeOrientedMsg("Meek R4 using " + c + ", " + d, graph.getEdge(a, b)));
                    return true;
                }
            }
        }

        return false;
    }

    private boolean direct(Node a, Node c, Graph graph, Set<Node> visited) {
        if (!isArrowpointAllowed(a, c, knowledge)) return false;
        if (!Edges.isUndirectedEdge(graph.getEdge(a, c))) return false;

        // True if new unshielded colliders should not be oriented by the procedure. That is, if
        // P->A--C, ~adj(A, C), where A--C is to be oriented by any rule, R1 usurps to yield P->A->C.
//        for (Node p : graph.getParents(c)) {
//            if (p != a && !graph.isAdjacentTo(a, p)) {
//                graph.removeEdge(a, c);
//                graph.addUndirectedEdge(a, c);
//                return true;
//            }
//        }

        Edge before = graph.getEdge(a, c);
        Edge after = Edges.directedEdge(a, c);

        visited.add(a);
        visited.add(c);

        graph.removeEdge(before);
        graph.addEdge(after);

        return true;
    }

    private static boolean isArrowpointAllowed(Node from, Node to, IKnowledge knowledge) {
        if (knowledge.isEmpty()) return true;
        return !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    private boolean revertToUnshieldedColliders(Node y, Graph graph, Set<Node> visited) {
        boolean did = false;

        List<Node> parents = graph.getParents(y);

        P:
        for (Node p : parents) {
            for (Node q : parents) {
                if (p != q && !graph.isAdjacentTo(p, q)) {
                    continue P;
                }
            }

            if (knowledge.isForbidden(y.getName(), p.getName()) || knowledge.isRequired(p.getName(), y.getName())) continue;

            graph.removeEdge(p, y);
            graph.addUndirectedEdge(p, y);

            visited.add(p);
            visited.add(y);

            did = true;
        }

        return did;
    }

    private void log(String message) {
        if (verbose) {
            TetradLogger.getInstance().forceLogMessage(message);
        }
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private Set<Node> getCommonAdjacents(Node x, Node y, Graph graph) {
        Set<Node> adj = new HashSet<>(graph.getAdjacentNodes(x));
        adj.retainAll(graph.getAdjacentNodes(y));
        return adj;
    }

    public void setRevertToUnshieldedColliders(boolean revertToUnshieldedColliders) {
        this.revertToUnshieldedColliders = revertToUnshieldedColliders;
    }
}





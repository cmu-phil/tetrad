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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * Option B, but â completely ignores the direct edge x *-* y while searching, and â returns {@code null} if it proves
 * impossible to block the remaining xây paths with eligible non-colliders.
 */
public final class RecursiveBlockingChokePointB {

    private RecursiveBlockingChokePointB() {
    }

    /**
     * Identifies and blocks paths in the given graph by iteratively finding and addressing chokepoints and eligible
     * non-collider nodes. This method recursively handles graph traversal and blocking based on specific conditions
     * until all paths between the given nodes are blocked or no further potential adjustments can be made.
     *
     * @param G             the graph in which paths are to be blocked
     * @param x             the starting node for path exploration
     * @param y             the target node for path blocking
     * @param forbidden     a set of nodes that are forbidden from being part of any path
     * @param maxPathLength the maximum permissible length of paths to explore
     * @return a set of nodes that block paths between x and y, or null if no valid blocking set can be determined
     * @throws InterruptedException if the operation is interrupted during execution
     */
    public static Set<Node> blockPathsRecursively(Graph G,
                                                  Node x,
                                                  Node y,
                                                  Set<Node> forbidden,
                                                  int maxPathLength)
            throws InterruptedException {

        if (x == y) return Set.of();

        Set<Node> B = new LinkedHashSet<>();
        Map<Node, Set<Node>> desc = G.paths().getDescendantsMap();

        /* -------------------  outer loop  --------------------------- */
        while (true) {
            Path witness = firstOpenPath(G, x, y, B,
                    maxPathLength, desc, forbidden);
            if (witness == null) break;                 // all other paths blocked

            Path prefix = Path.single(x, forbidden);    // always seed DFS at x
            Set<Node> I = intersectionSkipFirst(G, prefix, y, B,
                    maxPathLength, desc, forbidden);

            if (!I.isEmpty()) {          // ordinary âchoke-pointâ step
                B.addAll(I);
                continue;
            }

            /* ---------- fallback: first eligible non-collider ---------- */
            boolean added = false;
            for (Node v : witness.interiorEligibleNonColliders(G, B)) {
                B.add(v);
                added = true;
                break;
            }
            if (!added) {                       // nothing left to try â fail
                return null;
            }
        }
        return B;
    }

    /**
     * Finds the first open path between two specified nodes in a graph, adhering to certain constraints and conditions.
     * This method uses a depth-first traversal approach, keeping track of paths explored and respecting the constraints
     * such as forbidden nodes, maximum path length, and conditions for path feasibility based on given blocking sets
     * and node descriptors.
     *
     * @param G         the graph in which to search for paths
     * @param x         the starting node for the path search
     * @param y         the target node for the path search
     * @param B         a set of blocking nodes that constrain path exploration
     * @param maxLen    the maximum permissible path length (-1 for no limit)
     * @param desc      a mapping of nodes to sets of descendant nodes, used for additional constraints in defining
     *                  valid paths
     * @param forbidden a set of nodes that cannot be part of any path
     * @return the first valid path from x to y that meets the constraints, or null if no such path exists
     * @throws InterruptedException if the operation is interrupted during execution
     */
    private static Path firstOpenPath(Graph G, Node x, Node y, Set<Node> B,
                                      int maxLen, Map<Node, Set<Node>> desc,
                                      Set<Node> forbidden)
            throws InterruptedException {

        record Frame(Node node, Iterator<Node> it, Path soFar) {
        }
        Deque<Frame> S = new ArrayDeque<>();
        S.push(new Frame(x, G.getAdjacentNodes(x).iterator(),
                Path.single(x, forbidden)));

        while (!S.isEmpty()) {
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException();

            Frame f = S.peek();
            if (!f.it.hasNext()) {
                S.pop();
                continue;
            }

            Node nbr = f.it.next();

            /* ---------  NEW: ignore the direct edge x *-* y  --------- */
            if (isXYEdge(f.node, nbr, x, y)) continue;

            if (f.soFar.contains(nbr)) continue;
            if (!segmentPasses(G, f.node, nbr, B, desc)) continue;
            Path next = f.soFar.extend(nbr);

            if (maxLen != -1 && next.length() > maxLen) continue;
            if (nbr.equals(y)) return next;

            S.push(new Frame(nbr, G.getAdjacentNodes(nbr).iterator(), next));
        }
        return null;
    }

    /**
     * Identifies the intersection of eligible non-collider nodes for paths in a graph, while skipping the first
     * occurrence of the target node. This method traverses the graph starting from a given path prefix, adhering to
     * constraints such as forbidden nodes, maximum path length, and segment validity.
     *
     * @param G         the graph to explore for paths
     * @param prefix    the initial path prefix used for traversal
     * @param y         the target node for the path intersection
     * @param B         a set of blocking nodes that constrain path exploration
     * @param maxLen    the maximum permissible path length (-1 for no limit)
     * @param desc      a mapping of nodes to sets of descendant nodes, used for additional constraints in determining
     *                  valid paths
     * @param forbidden a set of nodes that cannot be part of any path
     * @return the set of intersecting eligible non-collider nodes for valid paths, or an empty set if no intersection
     * is found
     * @throws InterruptedException if the execution is interrupted during traversal
     */
    private static Set<Node> intersectionSkipFirst(Graph G, Path prefix, Node y,
                                                   Set<Node> B, int maxLen,
                                                   Map<Node, Set<Node>> desc,
                                                   Set<Node> forbidden)
            throws InterruptedException {

        Deque<Path> todo = new ArrayDeque<>();
        todo.push(prefix);

        boolean skippedFirst = false;
        Set<Node> inter = null;

        while (!todo.isEmpty()) {
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException();

            Path p = todo.pop();
            Node tail = p.tail();

            if (tail.equals(y)) {
                if (!skippedFirst) {
                    skippedFirst = true;
                    continue;
                }
                List<Node> interior = p.interiorEligibleNonColliders(G, B);
                if (inter == null) inter = new HashSet<>(interior);
                else inter.retainAll(interior);
                continue;
            }
            if (maxLen != -1 && p.length() >= maxLen) continue;

            for (Node w : G.getAdjacentNodes(tail)) {
                if (isXYEdge(tail, w, prefix.nodes.get(0), y)) continue;
                if (forbidden.contains(w)) continue;
                if (p.contains(w)) continue;
                if (!segmentPasses(G, tail, w, B, desc)) continue;
                todo.push(p.extend(w));
            }
        }
        return inter == null ? Set.of() : inter;
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Determines if the edge formed by the nodes a and b is equivalent to the edge formed by the nodes x and y. This
     * check considers the undirected nature of the edge, meaning the order of nodes does not matter.
     *
     * @param a the first node of the first edge
     * @param b the second node of the first edge
     * @param x the first node of the second edge
     * @param y the second node of the second edge
     * @return true if the edge formed by a and b is equivalent to the edge formed by x and y, false otherwise
     */
    private static boolean isXYEdge(Node a, Node b, Node x, Node y) {
        return (a.equals(x) && b.equals(y)) ||
               (a.equals(y) && b.equals(x));
    }

    /**
     * Determines whether the segment between nodes `a` and `b` in the graph satisfies specific conditions based on
     * colliders, blocking sets, and descendant nodes. The method evaluates the validity of the segment based on whether
     * `b` is a collider and whether any node in the blocking set `B` exists in the descendants of `b`.
     *
     * @param G    the graph being analyzed
     * @param a    the starting node of the segment
     * @param b    the ending node of the segment
     * @param B    the set of blocking nodes used to constrain path exploration
     * @param desc a mapping of nodes to their respective sets of descendant nodes
     * @return true if the segment passes the validation conditions, false otherwise
     */
    private static boolean segmentPasses(Graph G, Node a, Node b,
                                         Set<Node> B, Map<Node, Set<Node>> desc) {
        boolean collider = G.isDefCollider(a, b, null);
        if (!collider && !B.contains(b)) return true;
        if (collider) {
            for (Node d : desc.get(b)) if (B.contains(d)) return true;
        }
        return false;
    }

    /**
     * Determines whether a given node is an eligible non-collider in a graph based on specific conditions involving the
     * graph structure, path prefix, blocking set, and forbidden set.
     *
     * @param G         the graph to analyze
     * @param prefix    the path prefix leading to the current node
     * @param v         the node to check for eligibility as a non-collider
     * @param B         a set of blocking nodes that restrict valid paths
     * @param forbidden a set of nodes that are not allowed to participate in the path
     * @return true if the node is eligible to be a non-collider, false otherwise
     */
    private static boolean isEligibleNonCollider(Graph G, List<Node> prefix,
                                                 Node v, Set<Node> B,
                                                 Set<Node> forbidden) {
        if (forbidden.contains(v)) return false;
        if (v.getNodeType() == NodeType.LATENT) return false;
        if (B.contains(v)) return false;
        int i = prefix.size() - 1;
        if (i < 1) return false;
        Node a = prefix.get(i - 1), b = prefix.get(i);
        return !G.isDefCollider(a, b, v);
    }

    /**
     * Represents a path in a graph consisting of a sequence of nodes, with the ability to enforce restrictions based on
     * forbidden nodes and perform various path-related operations.
     */
    private static final class Path {
        private final List<Node> nodes;
        private final Set<Node> forbidden;

        private Path(List<Node> nodes, Set<Node> forbidden) {
            this.nodes = nodes;
            this.forbidden = forbidden;
        }

        static Path single(Node v, Set<Node> forbidden) {
            return new Path(new ArrayList<>(List.of(v)), forbidden);
        }

        int length() {
            return nodes.size() - 1;
        }

        Node tail() {
            return nodes.get(nodes.size() - 1);
        }

        boolean contains(Node v) {
            return nodes.contains(v);
        }

        Path extend(Node w) {
            List<Node> n = new ArrayList<>(nodes);
            n.add(w);
            return new Path(n, forbidden);
        }

        List<Node> interiorEligibleNonColliders(Graph G, Set<Node> B) {
            List<Node> rs = new ArrayList<>();
            for (int i = 1; i < nodes.size() - 1; i++) {
                Node v = nodes.get(i);
                if (isEligibleNonCollider(G, nodes.subList(0, i + 1),
                        v, B, forbidden)) rs.add(v);
            }
            return rs;
        }
    }
}


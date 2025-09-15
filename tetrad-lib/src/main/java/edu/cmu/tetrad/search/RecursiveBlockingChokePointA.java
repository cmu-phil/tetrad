package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * This class provides methods for identifying a minimal set of blocking nodes (a "choke point") that prevent all paths
 * between two specified nodes in a graph using recursive and iterative mechanisms. The process allows for certain nodes
 * and edges to be ignored during the computation, and uses depth-first search and path intersection logic to determine
 * the blocking set.
 * <p>
 * This class is designed to be utility-based and is not meant to be instantiated.
 */
public final class RecursiveBlockingChokePointA {

    /**
     * A private constructor for the RecursiveBlockingChokePointA class.
     * <p>
     * This constructor prevents instantiation of the RecursiveBlockingChokePointA class, as its primary function is to
     * serve as a container for static methods related to finding separator nodes while exploring paths in a graph
     * recursively.
     * <p>
     * The class is designed for applications requiring path blocking and separator identification in graph structures.
     * All functionality is exposed via static methods, ensuring that it cannot be instantiated or extended.
     */
    private RecursiveBlockingChokePointA() {
    }

    /* ------------------------------------------------------------------ */
    /*  Public entry point                                                */
    /* ------------------------------------------------------------------ */

    /**
     * Identifies and blocks paths between two nodes in a graph recursively, ensuring that all possible open paths
     * between the nodes are restricted by adding blocking nodes to a set.
     * <p>
     * The method is designed to explore paths in a graph iteratively, checking for intersections, non-colliders, and
     * other criteria to determine the appropriate blocking nodes. If all paths are successfully blocked, a set
     * containing the blocking nodes is returned. If blocking is not feasible, the method returns null.
     *
     * @param G             the graph in which paths are analyzed and blocked
     * @param x             the source node from which the path originates
     * @param y             the destination node to which the path leads
     * @param forbidden     a set of nodes that cannot be part of the paths or blocking set
     * @param maxPathLength the maximum length of allowable paths to be considered
     * @return a set of nodes that block all paths between x and y, or null if blocking all paths is not feasible
     * @throws InterruptedException if the process is interrupted
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
            if (witness == null) break;              // all other paths blocked

            /* restart DFS from x only */
            Path prefix = Path.single(x, forbidden);

            Set<Node> I = intersectionOfInteriorNonColliders(
                    G, prefix, y, B, maxPathLength, desc, forbidden);

            if (!I.isEmpty()) {              // choke-point step
                B.addAll(I);
                continue;
            }

            /* ---------- fallback: add first eligible non-collider ---------- */
            boolean added = false;
            for (Node v : witness.interiorEligibleNonColliders(G, B)) {
                B.add(v);
                added = true;
                break;
            }
            if (!added) {                    // no progress possible → give up
                return null;
            }
        }
        return B;
    }

    /**
     * Finds the first open path between two nodes in a graph within given constraints.
     * <p>
     * This method explores all possible paths from the source node to the destination node while adhering to various
     * constraints, such as avoiding forbidden nodes, limiting path length, and adhering to blocking and segment
     * constraints. If an open path satisfying the conditions is found, it is returned; otherwise, the method returns
     * null. The method operates iteratively using an explicit stack to avoid recursion.
     *
     * @param G         the graph in which paths are to be found
     * @param x         the source node from which the path originates
     * @param y         the destination node to which the path leads
     * @param B         a set of nodes used as blocking constraints
     * @param maxLen    the maximum allowable length for paths (-1 for no limit)
     * @param desc      a map providing precomputed descendant sets for nodes
     * @param forbidden a set of nodes that cannot be part of any valid path
     * @return the first open path found between nodes x and y, or null if no such path exists
     * @throws InterruptedException if the process is interrupted
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

            /* -------- NEW: ignore the edge x *-* y -------- */
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
     * Computes the intersection of the interior non-colliders identified across all valid paths from the given prefix
     * to a target node y within specified constraints.
     *
     * @param G         the graph in which paths are explored
     * @param prefix    the initial path prefix to begin exploration
     * @param y         the target node for path exploration
     * @param B         a set of nodes used as blocking constraints
     * @param maxLen    the maximum allowable length for paths (-1 for no limit)
     * @param desc      a map providing precomputed descendant sets for nodes
     * @param forbidden a set of nodes that cannot be part of any valid path
     * @return a set representing the intersection of interior non-colliders for all valid paths adhering to the
     * constraints, or an empty set if no valid path exists
     * @throws InterruptedException if the process is interrupted while executing
     */
    private static Set<Node> intersectionOfInteriorNonColliders(
            Graph G, Path prefix, Node y, Set<Node> B,
            int maxLen, Map<Node, Set<Node>> desc,
            Set<Node> forbidden) throws InterruptedException {

        Deque<Path> todo = new ArrayDeque<>();
        todo.push(prefix);

        Set<Node> intersection = null;   // null → first path not processed

        while (!todo.isEmpty()) {
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException();

            Path p = todo.pop();
            Node tail = p.tail();

            if (tail.equals(y)) {
                List<Node> interior = p.interiorEligibleNonColliders(G, B);
                if (intersection == null) intersection = new HashSet<>(interior);
                else intersection.retainAll(interior);
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
        return intersection == null ? Set.of() : intersection;
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Determines whether the edge formed by nodes a and b matches the edge formed by nodes x and y, considering
     * undirected relationships. Specifically, it checks if (a, b) is equivalent to (x, y) or (y, x).
     *
     * @param a the first node of the edge being checked
     * @param b the second node of the edge being checked
     * @param x the first node of the target edge
     * @param y the second node of the target edge
     * @return true if the edge (a, b) matches the edge (x, y), considering both orientations. Returns false otherwise.
     */
    private static boolean isXYEdge(Node a, Node b, Node x, Node y) {
        return (a.equals(x) && b.equals(y)) ||
               (a.equals(y) && b.equals(x));
    }

    /**
     * Determines whether a segment between two nodes in a graph passes the specified blocking constraints and
     * descendant sets.
     * <p>
     * The method evaluates the relationship between the nodes in the graph, considering whether the intermediate node
     * is a collider and whether it adheres to the given blocking conditions. For colliders, it checks if any descendant
     * of the node belongs to the blocking set.
     *
     * @param G    the graph in which the segment exists
     * @param a    the starting node of the segment
     * @param b    the intermediary node of the segment
     * @param B    a set of nodes used as blocking constraints
     * @param desc a map providing precomputed sets of descendants for each node
     * @return true if the segment passes the blocking constraints, false otherwise
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
     * Determines if a given node is an eligible non-collider within the context of a graph traversal or analysis, based
     * on specific constraints and conditions.
     * <p>
     * The method checks various criteria including whether the node is in a forbidden set, whether it is latent, or if
     * it already belongs to the blocking set. Additionally, it evaluates whether the node forms a collider along the
     * path defined by the provided prefix in the graph.
     *
     * @param G         the graph within which the eligibility is being determined
     * @param prefix    the current path prefix being considered during the traversal
     * @param v         the specific node being evaluated for eligibility
     * @param B         a set of nodes that are used as blocking constraints
     * @param forbidden a set of nodes that cannot be part of any eligible path
     * @return true if the node is an eligible non-collider based on the given conditions, false otherwise
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
     * The Path class represents a sequence of nodes within a graph, along with an optional set of forbidden nodes. It
     * provides utility methods to manage and evaluate paths, including operations for extension, containment checks,
     * and eligibility checks for internal nodes based on graph constraints.
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

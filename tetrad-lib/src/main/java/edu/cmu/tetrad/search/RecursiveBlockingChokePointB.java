package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * Option B, but
 *   – completely ignores the direct edge x *-* y while searching, and
 *   – returns {@code null} if it proves impossible to block the remaining
 *     x–y paths with eligible non-colliders.
 */
public final class RecursiveBlockingChokePointB {

    private RecursiveBlockingChokePointB() {}

    /* ------------------------------------------------------------------ */
    /*  Public entry point                                                 */
    /* ------------------------------------------------------------------ */

    /** @return { B } if a separator is found, ∅ if none is needed,
     *           or {@code null} if no separator exists (w.r.t. the
     *           ignored edge). */
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

            if (!I.isEmpty()) {          // ordinary ‘choke-point’ step
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
            if (!added) {                       // nothing left to try – fail
                return null;
            }
        }
        return B;
    }

    /* ------------------------------------------------------------------ */
    /*  firstOpenPath – DFS until y is reached on an open path            */
    /* ------------------------------------------------------------------ */

    private static Path firstOpenPath(Graph G, Node x, Node y, Set<Node> B,
                                      int maxLen, Map<Node, Set<Node>> desc,
                                      Set<Node> forbidden)
            throws InterruptedException {

        record Frame(Node node, Iterator<Node> it, Path soFar) {}
        Deque<Frame> S = new ArrayDeque<>();
        S.push(new Frame(x, G.getAdjacentNodes(x).iterator(),
                Path.single(x, forbidden)));

        while (!S.isEmpty()) {
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException();

            Frame f = S.peek();
            if (!f.it.hasNext()) { S.pop(); continue; }

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

    /* ------------------------------------------------------------------ */
    /*  intersectionSkipFirst – takes same ‘ignore-edge’ shortcut         */
    /* ------------------------------------------------------------------ */

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
                if (!skippedFirst) { skippedFirst = true; continue; }
                List<Node> interior = p.interiorEligibleNonColliders(G, B);
                if (inter == null)      inter = new HashSet<>(interior);
                else                    inter.retainAll(interior);
                continue;
            }
            if (maxLen != -1 && p.length() >= maxLen) continue;

            for (Node w : G.getAdjacentNodes(tail)) {
                if (isXYEdge(tail, w, prefix.nodes.get(0), y)) continue;
                if (forbidden.contains(w))                continue;
                if (p.contains(w))                        continue;
                if (!segmentPasses(G, tail, w, B, desc))  continue;
                todo.push(p.extend(w));
            }
        }
        return inter == null ? Set.of() : inter;
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                           */
    /* ------------------------------------------------------------------ */

    /** Skip the one undirected edge we want to pretend does not exist. */
    private static boolean isXYEdge(Node a, Node b, Node x, Node y) {
        return (a.equals(x) && b.equals(y)) ||
               (a.equals(y) && b.equals(x));
    }

    private static boolean segmentPasses(Graph G, Node a, Node b,
                                         Set<Node> B, Map<Node, Set<Node>> desc) {
        boolean collider = G.isDefCollider(a, b, null);
        if (!collider && !B.contains(b)) return true;
        if (collider) {
            for (Node d : desc.get(b)) if (B.contains(d)) return true;
        }
        return false;
    }

    private static boolean isEligibleNonCollider(Graph G, List<Node> prefix,
                                                 Node v, Set<Node> B,
                                                 Set<Node> forbidden) {
        if (forbidden.contains(v))      return false;
        if (v.getNodeType() == NodeType.LATENT) return false;
        if (B.contains(v))              return false;
        int i = prefix.size() - 1;
        if (i < 1) return false;
        Node a = prefix.get(i - 1), b = prefix.get(i);
        return !G.isDefCollider(a, b, v);
    }

    /* ---------------  immutable Path helper  ------------------------- */

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
        int length()     { return nodes.size() - 1; }
        Node tail()      { return nodes.get(nodes.size() - 1); }
        boolean contains(Node v) { return nodes.contains(v); }
        Path extend(Node w) {
            List<Node> n = new ArrayList<>(nodes); n.add(w);
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

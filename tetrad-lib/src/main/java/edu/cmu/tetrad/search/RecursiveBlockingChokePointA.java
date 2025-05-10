package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * A “choke‑point first” variant of the Recursive‑Blocking routine.
 * <p>
 * On each outer iteration we: 1. find <em>some</em> X–Y path that is still open given the current B; 2. restart a DFS
 * from <code>x</code> (only) and enumerate <em>all</em> still‑open X–Y paths; 3. take the intersection <i>I</i> of
 * interior, eligible non‑colliders that appear on <em>every</em> such path; 4. add the entire <i>I</i> to
 * <code>B</code> at once.
 * <p>
 * Every global choke point therefore enters B the first time it is met. Termination (≤ |V| outer loops) and soundness
 * are preserved; the weak‑ minimality property of the original algorithm no longer holds.
 */
public final class RecursiveBlockingChokePointA {

    private RecursiveBlockingChokePointA() {
    }

    /* ------------------------------------------------------------------ */
    /*  Public entry point                                                */
    /* ------------------------------------------------------------------ */

    public static Set<Node> blockPathsRecursively(Graph G,
                                                  Node x,
                                                  Node y,
                                                  Set<Node> forbidden, int maxPathLength)
            throws InterruptedException {

        if (x == y) return Set.of();

        // Blocking set we are building.
        Set<Node> B = new LinkedHashSet<>();

        // Pre‑compute descendants once (used for collider tests).
        Map<Node, Set<Node>> desc = G.paths().getDescendantsMap();

        /* -------------------  outer loop  --------------------------- */
        while (true) {
            Path witness = firstOpenPath(G, x, y, B, maxPathLength, desc, forbidden);
            if (witness == null) break;               // no open path → done

            /*  Restart exploration with <x> only so that the DFS must
             *  branch immediately and cover <all> still‑open X–Y paths.   */
            Path prefix = Path.single(x, forbidden);

            Set<Node> I = intersectionOfInteriorNonColliders(
                    G, prefix, y, B, maxPathLength, desc, forbidden);

            if (!I.isEmpty()) {
                B.addAll(I);          // add all choke points at once
                continue;             // restart outer loop
            }

            /* Fallback – should be rare: add the first eligible non‑
             * collider from the witness path (guarantees progress).      */
            for (Node v : witness.interiorEligibleNonColliders(G, B)) {
                B.add(v);
                break;
            }
        }
        return B;
    }

    /* ------------------------------------------------------------------ */
    /*  firstOpenPath – DFS until we hit y on a path that is open w.r.t B */
    /* ------------------------------------------------------------------ */

    private static Path firstOpenPath(Graph G, Node x, Node y, Set<Node> B,
                                      int maxLen, Map<Node, Set<Node>> desc, Set<Node> forbidden)
            throws InterruptedException {

        record Frame(Node node, Iterator<Node> it, Path soFar) {
        }

        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(x, G.getAdjacentNodes(x).iterator(), Path.single(x, forbidden)));

        while (!stack.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            Frame f = stack.peek();

            if (!f.it.hasNext()) {
                stack.pop();
                continue;
            }
            Node nbr = f.it.next();
            if (f.soFar.contains(nbr)) continue;   // simple paths only
            if (!segmentPasses(G, f.node, nbr, B, desc)) continue;
            Path next = f.soFar.extend(nbr);

            if (maxLen != -1 && next.length() > maxLen) continue;
            if (nbr.equals(y)) return next;

            stack.push(new Frame(nbr, G.getAdjacentNodes(nbr).iterator(), next));
        }
        return null; // none found
    }

    /* ------------------------------------------------------------------ */
    /*  Compute intersection of interior eligible non‑colliders           */
    /* ------------------------------------------------------------------ */

    private static Set<Node> intersectionOfInteriorNonColliders(
            Graph G, Path prefix, Node y, Set<Node> B,
            int maxLen, Map<Node, Set<Node>> desc, Set<Node> forbidden) throws InterruptedException {

        Deque<Path> todo = new ArrayDeque<>();
        todo.push(prefix);

        Set<Node> intersection = null;   // null → first path not processed yet

        while (!todo.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
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
                if (forbidden.contains(w)) continue;
                if (p.contains(w)) continue;              // simple path check
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
     * True iff the triple a–b passes through given current B.
     */
    private static boolean segmentPasses(Graph G, Node a, Node b,
                                         Set<Node> B, Map<Node, Set<Node>> desc) {
        // We do not yet know the next node after <b>, so test collider status
        // using the definition that ignores the third node.
        boolean collider = G.isDefCollider(a, b, null);
        if (!collider && !B.contains(b)) return true;
        if (collider) {
            for (Node d : desc.get(b)) if (B.contains(d)) return true;
        }
        return false;
    }

    /**
     * Eligible = interior non‑collider, observed, and not already in B.
     */
    private static boolean isEligibleNonCollider(Graph G, List<Node> prefix,
                                                 Node v, Set<Node> B, Set<Node> forbidden) {
        if (forbidden.contains(v)) return false;
        if (v.getNodeType() == NodeType.LATENT) return false;
        if (B.contains(v)) return false;
        int i = prefix.size() - 1;
        if (i < 1) return false; // need a triple to test collider
        Node a = prefix.get(i - 1), b = prefix.get(i);
        return !G.isDefCollider(a, b, v);
    }

    /* ------------------------------------------------------------------ */
    /*  Immutable Path helper class                                       */
    /* ------------------------------------------------------------------ */

    private static final class Path {
        private final List<Node> nodes;
        private final Set<Node> forbidden;

        private Path(List<Node> nodes, Set<Node> forbidden) {
            this.nodes = nodes;
            this.forbidden = forbidden;
        }

        /* factory helpers */
        static Path single(Node v, Set<Node> forbidden) {
            return new Path(new ArrayList<>(List.of(v)), forbidden);
        }

        /* basic accessors */
        int length() {
            return nodes.size() - 1;
        }

        Node tail() {
            return nodes.get(nodes.size() - 1);
        }

        boolean contains(Node v) {
            return nodes.contains(v);
        }

        List<Node> interior() {
            return nodes.subList(1, Math.max(1, nodes.size() - 1));
        }

        /* path operations */
        Path extend(Node w) {
            List<Node> n = new ArrayList<>(nodes);
            n.add(w);
            return new Path(n, forbidden);
        }

        /* gather all interior eligible non‑colliders for this path */
        List<Node> interiorEligibleNonColliders(Graph G, Set<Node> B) {
            List<Node> result = new ArrayList<>();
            for (int i = 1; i < nodes.size() - 1; i++) {
                Node v = nodes.get(i);
                if (isEligibleNonCollider(G, nodes.subList(0, i + 1), v, B, forbidden)) {
                    result.add(v);
                }
            }
            return result;
        }
    }
}

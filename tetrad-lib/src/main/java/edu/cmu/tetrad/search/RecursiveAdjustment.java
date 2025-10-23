package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * The {@code RecursiveAdjustment} algorithm searches recursively for a
 * (not necessarily minimal) adjustment set between variables X and Y
 * in a causal graph under PAG semantics.
 *
 * <p>This algorithm extends recursive blocking to the adjustment-set problem:
 * it forbids nodes on amenable (causal) paths and recursively adds nodes
 * that block all remaining backdoor paths, returning a graphical candidate
 * adjustment set suitable for causal effect estimation.</p>
 */
public final class RecursiveAdjustment {

    private static final boolean DEBUG = false; // set false to silence logging

    public enum GraphType{DAG, MPDAG, MAG, PAG}

    private RecursiveAdjustment() {}

    // ===== Public enumerator =====
    public static List<Set<Node>> findAdjustmentSets(
            Graph graph, String graphType, Node x, Node y,
            Set<Node> seedZ, Set<Node> notFollowed,
            int maxPathLength, Set<Node> latentMask,
            int maxSets, boolean minimizeEach) throws InterruptedException {

        if (x == y) {
            return List.of(Collections.emptySet());
        }

        GraphType _graphType = GraphType.valueOf(graphType);

        int cap = (maxSets <= 0 ? Integer.MAX_VALUE : maxSets);

        // Start from a single candidate seed
        Set<Node> baseZ = seedZ == null ? Collections.emptySet() : seedZ;
        Set<Node> nf    = notFollowed == null ? Collections.emptySet() : notFollowed;
        Set<Node> mask  = latentMask == null ? Collections.emptySet() : latentMask;

        // Build branch-wise, combining constraints across all backdoor-starting neighbors
        List<Set<Node>> frontier = new ArrayList<>();
        frontier.add(new HashSet<>(baseZ));

        Map<Node, Set<Node>> desc = graph.paths().getDescendantsMap();
        Set<String> globalSeen = new HashSet<>(); // optional global dedupe

        for (Node b : graph.getAdjacentNodes(x)) {
            if (Thread.currentThread().isInterrupted()) return List.of();
            if (b == y) continue;

            Edge e = graph.getEdge(x, b);
            if (e == null) continue;
            if (!startsBackdoorFromX(graph, _graphType, e, x, b, y)) continue;

            List<Set<Node>> next = new ArrayList<>();

            // For each partial Z that blocks all *previous* backdoor starts,
            // expand to the Z's that also block the branch via (x,b, ... y).
            for (Set<Node> Z : frontier) {
                if (Thread.currentThread().isInterrupted()) return List.of();
                List<Set<Node>> blockedVariants = blockBranchEnum(
                        graph, x, b, y, Z, nf, maxPathLength, desc, mask, cap - next.size());
                for (Set<Node> zv : blockedVariants) {
                    String key = canon(zv);
                    if (globalSeen.add(key)) {
                        next.add(zv);
                        if (next.size() >= cap) break;
                    }
                }
                if (cap != Integer.MAX_VALUE && next.size() >= cap) break;
//                if (next.size() >= cap) break;
            }

            logBranch(x, b, next.size());

            // If no variants can block this branch, enumeration fails
            if (next.isEmpty()) return List.of(); // no valid adjustment sets

            frontier = next;
            if (frontier.size() >= cap) break;
        }

        // Optionally minimize each found set using your checker
        if (minimizeEach) {
            List<Set<Node>> minimized = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Set<Node> Z : frontier) {
                Set<Node> m = minimizeZ(graph, _graphType, x, y, Z, nf, maxPathLength);
                String key = canon(m);
                if (seen.add(key)) minimized.add(m);
            }
            frontier = minimized;
        }

        log("Total adjustment sets found: " + frontier.size());

        return frontier;
    }

    // ===== Branch-level enumeration (conjoins into overall sets) =====
// Replace your blockBranchEnum with this version
    private static List<Set<Node>> blockBranchEnum(
            Graph graph, Node a, Node b, Node y,
            Set<Node> Z0, Set<Node> notFollowed,
            int maxPathLength, Map<Node, Set<Node>> descendantsMap, Set<Node> latentMask,
            int maxForThisBranch) throws InterruptedException {

        // Cap / guard
        if (Thread.currentThread().isInterrupted() || maxForThisBranch <= 0) return List.of();

        // Base failures
        if (b == y) return List.of();                 // cannot block this branch with current Z
        if (notFollowed.contains(b)) return List.of();

        return enumDescend(
                graph, a, b, y,
                new HashSet<>(Z0), notFollowed,
                maxPathLength, new ArrayDeque<>(List.of(a)),
                descendantsMap, latentMask,
                maxForThisBranch, new HashSet<>());
    }

    // Enumerate all Z ⊇ Zcur that block every continuation from (a,b) to y.
    private static List<Set<Node>> enumDescend(
            Graph graph, Node a, Node b, Node y,
            Set<Node> Zcur, Set<Node> notFollowed,
            int maxPathLength, Deque<Node> path,          // carries the path
            Map<Node, Set<Node>> descendantsMap, Set<Node> latentMask,
            int budget, Set<String> seen) throws InterruptedException {

        if (Thread.currentThread().isInterrupted()) return List.of();

        // Open branch → no solution from this branch
        if (b == y) return List.of();
        if (notFollowed.contains(b)) return List.of();

        // Treat "don't follow into y" as already blocked for this branch
        if (notFollowed.contains(y)) return List.of(new HashSet<>(Zcur));

        // Cycle guard (same as single-set version)
        if (path.contains(b)) return List.of();

        // Depth bound via path length
        if (maxPathLength != -1 && path.size() > maxPathLength) return List.of();

        // Dedup: include predecessor context so we don't over-prune
        String state = b.getName() + "||" + canon(Zcur) + "||" + path.peekLast().getName();
        if (!seen.add(state)) return List.of();

        // Push b
        path.addLast(b);
        try {
            boolean maskedLatent = latentMask.contains(b);
            boolean cannotAddB = (b.getNodeType() == NodeType.LATENT) || maskedLatent || Zcur.contains(b);

            // Children under current Z
            List<Node> kidsNoZ = children(graph, a, b, Zcur, descendantsMap, notFollowed);

            // No continuations → this sub-branch is already BLOCKED by current Z
            if (kidsNoZ.isEmpty()) {
                return List.of(new HashSet<>(Zcur));
            }

            List<Set<Node>> results = new ArrayList<>();

            // ---- Option 1: do NOT add b to Z ----
            {
                List<List<Set<Node>>> perChildSolutions = new ArrayList<>();
                boolean anyChildImpossible = false;

                for (Node c : kidsNoZ) {
                    if (budget > 0 && results.size() >= budget) break;

                    // IMPORTANT: pass a COPY of the current path (which already has b)
                    Deque<Node> nextPath = new ArrayDeque<>(path);
                    List<Set<Node>> solsC = enumDescend(
                            graph, b, c, y,
                            Zcur, notFollowed,
                            maxPathLength, nextPath,
                            descendantsMap, latentMask,
                            budget, seen);

                    if (solsC.isEmpty()) {
                        anyChildImpossible = true;  // some child can't be blocked without adding b
                        break;
                    }
                    perChildSolutions.add(solsC);
                }

                if (!anyChildImpossible) {
                    combineAcrossChildren(perChildSolutions,
                            (budget <= 0 ? Integer.MAX_VALUE : (budget - results.size())),
                            results);
                }
            }

            if (budget > 0 && results.size() >= budget) {
                return dedupeCap(results, budget);
            }

            // ---- Option 2: add b to Z (if allowed) ----
            if (!cannotAddB) {
                Set<Node> Zwith = new HashSet<>(Zcur);
                Zwith.add(b);

                List<Node> kidsWith = children(graph, a, b, Zwith, descendantsMap, notFollowed);
                if (kidsWith.isEmpty()) {
                    results.add(Zwith);
                } else {
                    List<List<Set<Node>>> perChildSolutions = new ArrayList<>();
                    boolean anyChildImpossible = false;

                    for (Node c : kidsWith) {
                        if (budget > 0 && results.size() >= budget) break;

                        Deque<Node> nextPath = new ArrayDeque<>(path);
                        List<Set<Node>> solsC = enumDescend(
                                graph, b, c, y,
                                Zwith, notFollowed,
                                maxPathLength, nextPath,
                                descendantsMap, latentMask,
                                budget, seen);

                        if (solsC.isEmpty()) {
                            anyChildImpossible = true;
                            break;
                        }
                        perChildSolutions.add(solsC);
                    }

                    if (!anyChildImpossible) {
                        combineAcrossChildren(perChildSolutions,
                                (budget <= 0 ? Integer.MAX_VALUE : (budget - results.size())),
                                results);
                    }
                }
            }

            return (budget > 0) ? dedupeCap(results, budget) : results;
        } finally {
            path.removeLast();
        }
    }

    // Take the cartesian product across children-lists and union the Z’s along each tuple.
    private static void combineAcrossChildren(
            List<List<Set<Node>>> perChildSolutions, int cap, List<Set<Node>> out) {

        if (perChildSolutions.isEmpty()) return;
        // iterative product
        List<Set<Node>> acc = new ArrayList<>();
        acc.add(Collections.emptySet());

        for (List<Set<Node>> childList : perChildSolutions) {
            List<Set<Node>> next = new ArrayList<>();
            for (Set<Node> prefix : acc) {
                for (Set<Node> add : childList) {
                    Set<Node> u = new HashSet<>(prefix);
                    u.addAll(add);
                    next.add(u);
                    if (cap > 0 && (out.size() + next.size()) >= cap) break;
                }
                if (cap > 0 && (out.size() + next.size()) >= cap) break;
            }
            acc = next;
            if (cap > 0 && (out.size() + acc.size()) >= cap) break;
        }
        // append to out with simple dedupe
        Set<String> seen = new HashSet<>();
        for (Set<Node> z : acc) {
            String key = canon(z);
            if (seen.add(key)) {
                out.add(z);
                if (cap > 0 && out.size() >= cap) break;
            }
        }
    }

    // simple dedupe + cap
    private static List<Set<Node>> dedupeCap(List<Set<Node>> in, int cap) {
        Map<String, Set<Node>> m = new LinkedHashMap<>();
        for (Set<Node> z : in) {
            String k = canon(z);
            m.putIfAbsent(k, z);
            if (cap > 0 && m.size() >= cap) break;
        }
        return new ArrayList<>(m.values());
    }

    // ===== Minimal greedy pass using the checker you already have =====
    private static Set<Node> minimizeZ(
            Graph graph, GraphType graphType, Node x, Node y, Set<Node> Z,
            Set<Node> notFollowed, int maxPathLength) throws InterruptedException {

        List<Node> order = new ArrayList<>(Z);
        Set<Node> best = new HashSet<>(Z);
        for (Node n : order) {
            if (Thread.currentThread().isInterrupted()) return best;
            Set<Node> trial = new HashSet<>(best);
            trial.remove(n);
            if (isAdjustmentSet(graph, graphType, x, y, trial, notFollowed, maxPathLength)) {
                best = trial;
            }
        }
        return best;
    }

    // ===== Pure checker (no additions to Z) =====
    public static boolean isAdjustmentSet(
            Graph graph, GraphType graphType, Node x, Node y, Set<Node> Z,
            Set<Node> notFollowed, int maxPathLength) throws InterruptedException {

        Set<Node> path = new HashSet<>();
        path.add(x);

        for (Node b : graph.getAdjacentNodes(x)) {
            if (Thread.currentThread().isInterrupted()) return false;
            if (b == y) continue;

            Edge e = graph.getEdge(x, b);
            if (e == null) continue;
            if (!startsBackdoorFromX(graph, graphType,  e, x, b, y)) continue;

            Blockable r = descendCheck(graph, x, b, y, path, Z, maxPathLength, notFollowed, graph.paths().getDescendantsMap());
            if (r == Blockable.UNBLOCKABLE || r == Blockable.INDETERMINATE) return false;
        }
        return true;
    }

    private static Blockable descendCheck(
            Graph graph, Node a, Node b, Node y,
            Set<Node> path, Set<Node> Z, int maxPathLength,
            Set<Node> notFollowed, Map<Node, Set<Node>> descendantsMap) throws InterruptedException {

        if (b == y) return Blockable.UNBLOCKABLE;
        if (path.contains(b)) return Blockable.UNBLOCKABLE;
        if (notFollowed.contains(b)) return Blockable.INDETERMINATE;
        if (maxPathLength != -1 && path.size() > maxPathLength) return Blockable.INDETERMINATE;

        path.add(b);
        try {
            List<Node> kids = children(graph, a, b, Z, descendantsMap, notFollowed);
            if (kids.isEmpty()) return Blockable.BLOCKED;
            for (Node c : kids) {
                Blockable r = descendCheck(graph, b, c, y, path, Z, maxPathLength, notFollowed, descendantsMap);
                if (r == Blockable.UNBLOCKABLE || r == Blockable.INDETERMINATE) return Blockable.UNBLOCKABLE;
            }
            return Blockable.BLOCKED;
        } finally {
            path.remove(b);
        }
    }

    // ===== Tiny helpers =====
    private static String canon(Set<Node> Z) {
        List<String> names = new ArrayList<>();
        for (Node n : Z) names.add(n.getName());
        Collections.sort(names);
        return String.join("|", names);
    }

    private static String canonState(Node cur, Deque<Node> path, Set<Node> Z) {
        StringBuilder sb = new StringBuilder();
        sb.append(cur.getName()).append("||");
        List<String> p = new ArrayList<>();
        for (Node n : path) p.add(n.getName());
        sb.append(String.join("-", p)).append("||");
        sb.append(canon(Z));
        return sb.toString();
    }

    private static void log(String msg) {
        if (DEBUG) System.out.println("[RecursiveAdjustment] " + msg);
    }

    private static void logBranch(Node x, Node b, int count) {
        if (DEBUG) {
            System.out.printf("[RecursiveAdjustment] Branch %s → %s produced %d candidate sets%n",
                    x.getName(), b.getName(), count);
        }
    }

    public static Set<Node> findAdjustmentSet(
            Graph graph, GraphType graphType, Node x, Node y,
            Set<Node> seedZ, Set<Node> notFollowed,
            int maxPathLength, Set<Node> latentMask) throws InterruptedException {

        if (x == y) {
            return Collections.emptySet();
        }

        Set<Node> z0 = visit(
                graph, graphType, x, y,
                seedZ == null ? Collections.emptySet() : seedZ,
                notFollowed == null ? Collections.emptySet() : notFollowed,
                graph.paths().getDescendantsMap(),
                maxPathLength, null,
                latentMask == null ? Collections.emptySet() : latentMask
        );

        if (z0 == null) return null;                // no valid adjustment set
        return z0;
//        return minimizeZ(graph, x, y, z0, notFollowed, maxPathLength);  // checker-based
    }

    private static Set<Node> visit(
            Graph graph, GraphType graphType, Node x, Node y,
            Set<Node> containing, Set<Node> notFollowed,
            Map<Node, Set<Node>> descendantsMap,
            int maxPathLength, Knowledge knowledge, Set<Node> latentMask) throws InterruptedException {

        if (x == null || y == null)
            throw new NullPointerException("x or y is null");
        if (x == y)
            throw new IllegalArgumentException("x and y must differ");

        Set<Node> Z = new HashSet<>(containing);
        Set<Node> path = new HashSet<>();
        path.add(x);

        // Explore only backdoor-starting edges out of X
        for (Node b : graph.getAdjacentNodes(x)) {
            log("Exploring backdoor branch " + x.getName() + " → " + b.getName());

            if (Thread.currentThread().isInterrupted()) return null;
            if (b == y) continue;

            Edge e = graph.getEdge(x, b);
            if (e == null) continue;
            if (!startsBackdoorFromX(graph, graphType, e, x, b, y)) continue;

            Blockable r = descend(graph, x, b, y, path, Z, maxPathLength, notFollowed, descendantsMap, latentMask);
            if (r == Blockable.UNBLOCKABLE || r == Blockable.INDETERMINATE) return null;
        }
        return Z;
    }

    private static boolean startsBackdoorFromX(Graph graph, GraphType graphType, Edge e, Node x, Node b, Node y) {
//        boolean mpdag = graph.paths().isLegalMpdag();
//        boolean mag   = graph.paths().isLegalMag();
//        boolean pag   = graph.paths().isLegalPag();

        if (graphType == GraphType.MPDAG) {
            return e.pointsTowards(x) || Edges.isUndirectedEdge(e);
        } else if (graphType == GraphType.MAG) {
            return e.pointsTowards(x) || Edges.isUndirectedEdge(e) || Edges.isBidirectedEdge(e);
        } else if (graphType == GraphType.PAG) {
            if (e.pointsTowards(x) || Edges.isUndirectedEdge(e)) return true;
            if (Edges.isBidirectedEdge(e)) {
                return graph.paths().existsDirectedPath(b, x) || graph.paths().existsDirectedPath(b, y);
            }
            return false;
        } else { // DAG default
            return e.pointsTowards(x);
        }
    }

    private static Blockable descend(
            Graph graph, Node a, Node b, Node y,
            Set<Node> path, Set<Node> Z, int maxPathLength,
            Set<Node> notFollowed, Map<Node, Set<Node>> descendantsMap, Set<Node> latentMask) throws InterruptedException {

        if (Thread.currentThread().isInterrupted()) return Blockable.INDETERMINATE;

        if (b == y) return Blockable.UNBLOCKABLE;
        if (path.contains(b)) return Blockable.UNBLOCKABLE;
        if (notFollowed.contains(b)) return Blockable.INDETERMINATE;
        if (notFollowed.contains(y)) return Blockable.BLOCKED;

        path.add(b);
        try {
            if (maxPathLength != -1 && path.size() > maxPathLength)
                return Blockable.INDETERMINATE;

            boolean maskedLatent = latentMask.contains(b);
            if (b == null || b == y) return Blockable.UNBLOCKABLE;

            // Case 1: cannot/shouldn’t condition on b
            if (b.getNodeType() == NodeType.LATENT || maskedLatent || Z.contains(b)) {
                for (Node c : children(graph, a, b, Z, descendantsMap, notFollowed)) {
                    Blockable r = descend(graph, b, c, y, path, Z, maxPathLength, notFollowed, descendantsMap, latentMask);
                    if (r == Blockable.UNBLOCKABLE || r == Blockable.INDETERMINATE)
                        return Blockable.UNBLOCKABLE;
                }
                return Blockable.BLOCKED;
            }

            // Case 2: try without b in Z
            boolean allBlocked = true;
            for (Node c : children(graph, a, b, Z, descendantsMap, notFollowed)) {
                Blockable r = descend(graph, b, c, y, path, Z, maxPathLength, notFollowed, descendantsMap, latentMask);
                if (r == Blockable.UNBLOCKABLE || r == Blockable.INDETERMINATE) {
                    allBlocked = false;
                    break;
                }
            }
            if (allBlocked) return Blockable.BLOCKED;

            // Case 3: try with b in Z (persist if works)
            Z.add(b);
            boolean allBlockedWithB = true;
            for (Node c : children(graph, a, b, Z, descendantsMap, notFollowed)) {
                Blockable r = descend(graph, b, c, y, path, Z, maxPathLength, notFollowed, descendantsMap, latentMask);
                if (r == Blockable.UNBLOCKABLE || r == Blockable.INDETERMINATE) {
                    allBlockedWithB = false;
                    break;
                }
            }
            if (allBlockedWithB) {
                return Blockable.BLOCKED; // keep b in Z
            } else {
                Z.remove(b);
                return Blockable.UNBLOCKABLE;
            }

        } finally {
            path.remove(b);
        }
    }

    private static List<Node> children(Graph graph, Node a, Node b, Set<Node> Z,
                                       Map<Node, Set<Node>> descendantsMap, Set<Node> notFollowed) {
        List<Node> pass = new ArrayList<>();
        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;
            if (notFollowed.contains(c)) continue;
            if (reachable(graph, a, b, c, Z, descendantsMap)) pass.add(c);
        }
        return pass;
    }

    private static boolean reachable(Graph graph, Node a, Node b, Node c,
                                     Set<Node> Z, Map<Node, Set<Node>> descendantsMap) {
        boolean collider = graph.isDefCollider(a, b, c);

        if ((!collider || graph.isUnderlineTriple(a, b, c)) && !Z.contains(b)) {
            return true;
        }

        if (!collider) return false;

        if (descendantsMap == null) {
            return graph.paths().isAncestorOfAnyZ(b, Z);
        } else {
            for (Node d : descendantsMap.getOrDefault(b, Collections.emptySet())) {
                if (Z.contains(d)) return true;
            }
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // Utility: Build latent mask from amenable paths
    // ---------------------------------------------------------------------

    public static Set<Node> buildAmenableNoncolliderMask(
            Graph graph, Node X, Node Y, Collection<List<Node>> amenablePaths, Set<Node> seedZ) {
        Set<Node> mask = new HashSet<>();
        for (List<Node> p : amenablePaths) {
            for (int i = 1; i + 1 < p.size(); i++) {
                Node a = p.get(i - 1), b = p.get(i), c = p.get(i + 1);
                if (!graph.isDefCollider(a, b, c) && b.getNodeType() == NodeType.MEASURED) {
                    mask.add(b);
                }
            }
        }
        mask.remove(X);
        mask.remove(Y);
        if (seedZ != null) mask.removeAll(seedZ);
        mask.removeIf(n -> n == null || n.getNodeType() != NodeType.MEASURED);
        return mask;
    }

    static Set<Node> minimize(Graph g, GraphType graphType, Node x, Node y, Set<Node> Z,
                              Set<Node> notFollowed, int maxLen, Set<Node> mask) throws InterruptedException {
        List<Node> order = new ArrayList<>(Z);
        // Try the easy wins first: remove descendants of X or Y last
        order.sort(Comparator.comparing((Node n) -> g.isDescendentOf(n, x) || g.isDescendentOf(n, y)));
        Set<Node> best = new HashSet<>(Z);
        for (Node n : order) {
            Set<Node> trial = new HashSet<>(best);
            trial.remove(n);
            Set<Node> check = RecursiveAdjustment.findAdjustmentSet(g, graphType, x, y, trial, notFollowed, maxLen, mask);
            if (check != null) best = check; // still blocks all backdoors; keep it smaller
        }
        return best;
    }

    // ---------------------------------------------------------------------
    // Enum
    // ---------------------------------------------------------------------

    public enum Blockable { BLOCKED, UNBLOCKABLE, INDETERMINATE }
}
package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;

import java.util.*;
import java.util.function.Function;

/**
 * CD-NOD-PAG orienter (arrowheads-only) with strong legality (fixed-point) gating.
 *
 * - Multiple context variables supported via ChangeOracle.contexts().
 * - Optional tier map: only orient X o-> Y if tier(X) < tier(Y).
 * - Never add arrowheads into protected nodes (contexts + any user-provided).
 * - Exclude contexts from S when testing stabilization (configurable).
 * - After tentatively adding each arrowhead, run propagation and the *strong* legality check
 *   (PAG(MAG(G)) == G). If it fails, rollback just that arrowhead and continue.
 */
public final class CdnodPagOrienter {

    // Core state
    private Graph pag;
    private final ChangeOracle oracle;
    private final Function<Graph, Boolean> strongPagLegality; // fixed-point: PAG(MAG(G)) == G
    private final Function<Graph, Graph> propagator;

    // Config
    private int maxSubsetSize = 1;
    private boolean useProxyGuard = true;
    private boolean excludeContextsFromS = true;

    // Protection & tiers
    private final Set<Node> protectedNodes = new LinkedHashSet<>();
    private final Map<Node, Integer> tier = new HashMap<>(); // smaller = earlier

    // Undo stack (kept for final safety net; per-edge rollback uses last push)
    private final Deque<Runnable> undoStack = new ArrayDeque<>();

    public CdnodPagOrienter(Graph pag,
                            ChangeOracle oracle,
                            Function<Graph, Boolean> strongPagLegality,
                            Function<Graph, Graph> propagator) {
        this.pag = Objects.requireNonNull(pag, "pag");
        this.oracle = Objects.requireNonNull(oracle, "oracle");
        this.strongPagLegality = Objects.requireNonNull(strongPagLegality, "legality");
        this.propagator = Objects.requireNonNull(propagator, "propagator");

        // Always protect contexts from receiving arrowheads
        this.protectedNodes.addAll(oracle.contexts());
    }

    // ---------------- Fluent setters ----------------

    public CdnodPagOrienter withMaxSubsetSize(int k) { this.maxSubsetSize = Math.max(0, k); return this; }
    public CdnodPagOrienter withProxyGuard(boolean on) { this.useProxyGuard = on; return this; }
    public CdnodPagOrienter withExcludeContextsFromS(boolean on) { this.excludeContextsFromS = on; return this; }

    /** Add nodes that must not receive arrowheads (contexts are already included). */
    public CdnodPagOrienter forbidArrowheadsInto(Collection<Node> nodes) { this.protectedNodes.addAll(nodes); return this; }
    public CdnodPagOrienter forbidArrowheadsInto(Node node) { this.protectedNodes.add(node); return this; }

    /** Provide a tier map; orientations allowed only when tier(X) < tier(Y) if both known. */
    public CdnodPagOrienter withTiers(Map<Node, Integer> tiers) {
        if (tiers != null) this.tier.putAll(tiers);
        return this;
    }

    // ---------------- Main entry ----------------

    public void run() {
        final List<Node> ctx = oracle.contexts();

        // Iterate over adjacencies (undirected/partially oriented)
        for (Node y : pag.getNodes()) {
            if (protectedNodes.contains(y)) continue; // never add arrowheads into protected

            final List<Node> adjs = pag.getAdjacentNodes(y);
            for (Node x : adjs) {
                if (protectedNodes.contains(x)) continue;
                if (pag.isDirectedFromTo(x, y) || pag.isDirectedFromTo(y, x)) continue;

                // Tier guard: allow X->Y only if tier(X) < tier(Y) when both are known
                Integer tx = tier.get(x), ty = tier.get(y);
                if (tx != null && ty != null && tx >= ty) continue;

                // Neighborhood for S = Adj(Y)\{X}
                List<Node> neigh = new ArrayList<>(adjs);
                neigh.remove(x);
                if (excludeContextsFromS) neigh.removeAll(ctx);

                boolean oriented = tryOrientC1PerEdgeStrong(x, y, neigh, ctx);
                // If oriented, keep going; we already propagated & legalized within the attempt.
                // If not, try the next neighbor.
            }
        }

        // Final safety net: propagate & ensure strong legality (should already hold)
        pag = propagator.apply(pag);
        if (!strongPagLegality.apply(pag)) {
            // Progressive rollback until strong legality holds (unlikely if per-edge gating worked)
            while (!undoStack.isEmpty() && !strongPagLegality.apply(pag)) {
                undoStack.pop().run();
                pag = propagator.apply(pag);
            }
        }
    }

    // Attempt CD-NOD C1-like orientation for (x,y) using per-edge strong legality gating.
    private boolean tryOrientC1PerEdgeStrong(Node x, Node y, List<Node> neigh, List<Node> contexts) {
        // Enumerate S ⊆ neigh with |S| ≤ maxSubsetSize
        for (Set<Node> S0 : SmallSubsetIter.subsets(neigh, maxSubsetSize)) {
            // Work on a copy; never mutate iterator's set
            Set<Node> S = new LinkedHashSet<>(S0);

            // Require: Y shows change under S
            if (!oracle.changes(y, S)) continue;

            // If adding X stabilizes across all contexts, propose X o-> Y
            Set<Node> SplusX = plus(S, x);
            if (!oracle.stable(y, SplusX)) continue;

            // Optional proxy guard: at least one context alone stabilizes Y
            if (useProxyGuard && !contexts.isEmpty()) {
                boolean someContextStabilizes = false;
                for (Node c : contexts) {
                    if (oracle.stable(y, plus(S, c))) { someContextStabilizes = true; break; }
                }
                if (!someContextStabilizes) continue;
            }

            // Tentatively orient arrowhead at child Y (X o-> Y)
            addArrowheadAt(pag, x, y);

            // Propagate and enforce strong fixed-point legality immediately
            pag = propagator.apply(pag);
            if (!strongPagLegality.apply(pag)) {
                // Roll back just this arrowhead and continue trying other S
                undoLast();
                pag = propagator.apply(pag);
                continue;
            }

            // Keep this orientation; move on to next (x,y)
            return true;
        }
        return false;
    }

    // ---------------- Internals ----------------

    private static <T> Set<T> plus(Set<T> s, T x) {
        Set<T> u = new LinkedHashSet<>(s);
        u.add(x);
        return u;
    }

    /** Add an arrowhead at y on edge (x,y); record undo. (Arrowheads-only; preserves the other endpoint.) */
    private void addArrowheadAt(Graph g, Node x, Node y) {
        Endpoint oldXY = g.getEndpoint(x, y);
        Endpoint oldYX = g.getEndpoint(y, x);
        if (oldXY == Endpoint.ARROW) return; // already has head at y

        g.setEndpoint(x, y, Endpoint.ARROW); // X *-> Y
        undoStack.push(() -> {
            g.setEndpoint(x, y, oldXY);
            g.setEndpoint(y, x, oldYX);
        });
    }

    private void undoLast() {
        if (!undoStack.isEmpty()) undoStack.pop().run();
    }
}
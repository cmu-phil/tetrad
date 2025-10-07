package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.graph.*;

import java.util.*;
import java.util.function.Function;

/**
 * CD-NOD-PAG orienter (arrowheads-only) with per-edge strong legality (fixed-point) gating.
 * Propagator is modeled as Function<Graph, Graph> to match runner convention.
 */
public final class CdnodPagOrienter {

    // Core state
    private Graph pag;
    private final ChangeOracle oracle;
    private final Function<Graph, Boolean> strongPagLegality; // fixed-point: PAG(MAG(G)) == G
    private final Function<Graph, Graph> propagator;          // in-place; returns same instance

    // Config
    private int maxSubsetSize = 1;
    private boolean useProxyGuard = true;
    private boolean excludeContextsFromS = true;

    // Protection & tiers
    private final Set<Node> protectedNodes = new LinkedHashSet<>();
    private final Map<Node, Integer> tier = new HashMap<>(); // smaller = earlier

    // Undo stack
    private final Deque<Runnable> undoStack = new ArrayDeque<>();

    public CdnodPagOrienter(Graph pag,
                            ChangeOracle oracle,
                            Function<Graph, Boolean> strongPagLegality,
                            Function<Graph, Graph> propagator) {
        this.pag = Objects.requireNonNull(pag, "pag");
        this.oracle = Objects.requireNonNull(oracle, "oracle");
        this.strongPagLegality = Objects.requireNonNull(strongPagLegality, "legality");
        this.propagator = Objects.requireNonNull(propagator, "propagator");
        // contexts never receive arrowheads
        this.protectedNodes.addAll(oracle.contexts());
    }

    // ---- Fluent setters ----
    public CdnodPagOrienter withMaxSubsetSize(int k) { this.maxSubsetSize = Math.max(0, k); return this; }
    public CdnodPagOrienter withProxyGuard(boolean on) { this.useProxyGuard = on; return this; }
    public CdnodPagOrienter withExcludeContextsFromS(boolean on) { this.excludeContextsFromS = on; return this; }
    public CdnodPagOrienter forbidArrowheadsInto(Collection<Node> nodes) { this.protectedNodes.addAll(nodes); return this; }
    public CdnodPagOrienter forbidArrowheadsInto(Node node) { this.protectedNodes.add(node); return this; }
    public CdnodPagOrienter withTiers(Map<Node, Integer> tiers) { if (tiers != null) this.tier.putAll(tiers); return this; }

    // ---- Main ----
    public void run() {
        final List<Node> ctx = oracle.contexts();

        for (Node y : pag.getNodes()) {
            if (protectedNodes.contains(y)) continue; // never add heads into protected

            final List<Node> adjs = pag.getAdjacentNodes(y);
            for (Node x : adjs) {
                if (protectedNodes.contains(x)) continue;
                if (pag.isDirectedFromTo(x, y) || pag.isDirectedFromTo(y, x)) continue;

                // Tier guard
                Integer tx = tier.get(x), ty = tier.get(y);
                if (tx != null && ty != null && tx >= ty) continue;

                // Neighborhood for S = Adj(Y)\{X}, optionally minus contexts
                List<Node> neigh = new ArrayList<>(adjs);
                neigh.remove(x);
                if (excludeContextsFromS) neigh.removeAll(ctx);

                tryOrientC1PerEdgeStrong(x, y, neigh, ctx);
            }
        }

        // Final safety net: propagate & ensure strong legality
        pag = propagator.apply(pag);
        if (!strongPagLegality.apply(pag)) {
            while (!undoStack.isEmpty() && !strongPagLegality.apply(pag)) {
                undoStack.pop().run();
                pag = propagator.apply(pag);
            }
        }
    }

    // Attempt C1-like orientation for (x,y) using per-edge strong legality gating.
    private void tryOrientC1PerEdgeStrong(Node x, Node y, List<Node> neigh, List<Node> contexts) {
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
            return;
        }
    }

    // ---- Internals ----

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
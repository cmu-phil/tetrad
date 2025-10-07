package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.graph.*;

import java.util.*;
import java.util.function.Function;

/**
 * CD-NOD-PAG orienter (arrowheads-only) with:
 *  - Multiple context variables (Tier-0) supported via ChangeOracle.contexts().
 *  - Optional tier map: only orient X o-> Y if tier(X) < tier(Y).
 *  - Never add arrowheads into protected nodes (contexts + any user-provided).
 *  - Exclude contexts from S when testing stabilization (except optional proxy guard).
 *  - Progressive rollback if the PAG becomes illegal after propagation.
 */
public final class CdnodPagOrienter {

    /** Functional interface for running propagation (R0–R10 + discriminating paths, etc.). */
    @FunctionalInterface
    public interface Propagator { void propagate(Graph graph); }

    // Core state
    private final Graph pag;
    private final ChangeOracle oracle;
    private final Function<Graph, Boolean> legalityCheck;
    private final Propagator propagator;

    // Config
    private int maxSubsetSize = 1;
    private boolean useProxyGuard = true;
    private boolean excludeContextsFromS = true;

    // Protection & tiers
    private final Set<Node> protectedNodes = new LinkedHashSet<>();
    private final Map<Node, Integer> tier = new HashMap<>(); // smaller = earlier

    // Undo stack for rollback
    private final Deque<Runnable> undoStack = new ArrayDeque<>();

    public CdnodPagOrienter(Graph pag,
                            ChangeOracle oracle,
                            Function<Graph, Boolean> legalityCheck,
                            Propagator propagator) {
        this.pag = Objects.requireNonNull(pag);
        this.oracle = Objects.requireNonNull(oracle);
        this.legalityCheck = Objects.requireNonNull(legalityCheck);
        this.propagator = Objects.requireNonNull(propagator);

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

    /** Provide a tier map; orientations are only allowed when tier(X) < tier(Y). */
    public CdnodPagOrienter withTiers(Map<Node, Integer> tiers) {
        if (tiers != null) this.tier.putAll(tiers);
        return this;
    }

    // ---------------- Main entry ----------------

    public void run() {
        final List<Node> ctx = oracle.contexts();

        // Iterate over undirected/partially oriented adjacencies
        for (Node y : pag.getNodes()) {
            if (protectedNodes.contains(y)) continue; // never add arrowheads into protected

            final List<Node> adjs = pag.getAdjacentNodes(y);
            for (Node x : adjs) {
                if (protectedNodes.contains(x)) continue;
                if (pag.isDirectedFromTo(x, y) || pag.isDirectedFromTo(y, x)) continue;

                // Tier guard: allow X->Y only if tier(X) < tier(Y) when both are known
                Integer tx = tier.get(x), ty = tier.get(y);
                if (tx != null && ty != null && tx >= ty) continue;

                // Build candidate neighborhood for S = Adj(Y)\{X}
                List<Node> neigh = new ArrayList<>(adjs);
                neigh.remove(x);
                if (excludeContextsFromS) neigh.removeAll(ctx);

                // Require that Y exhibits change (under some S; we try all up to maxSubsetSize)
                boolean oriented = false;

                for (Set<Node> S0 : SmallSubsetIter.subsets(neigh, maxSubsetSize)) {
                    // Work on a copy; don't mutate the iterator's set
                    Set<Node> S = new LinkedHashSet<>(S0);
                    if (excludeContextsFromS) S.removeAll(oracle.contexts());

                    if (!oracle.changes(y, S)) continue;

                    Set<Node> SplusX = plus(S, x);
                    if (!oracle.stable(y, SplusX)) continue;

                    boolean proxyOk = true;
                    if (useProxyGuard && !oracle.contexts().isEmpty()) {
                        proxyOk = false;
                        for (Node c : oracle.contexts()) {
                            if (oracle.stable(y, plus(S, c))) { proxyOk = true; break; }
                        }
                    }
                    if (!proxyOk) continue;

                    addArrowheadAt(pag, x, y);
                    break; // done with this (x,y)
                }

                // (Optional) could early-propagate per pair; we batch-propagate below for efficiency
                if (oriented) { /* keep going; we’ll propagate once at the end */ }
            }


        }

        // Propagate and ensure legality (with progressive rollback)
        propagateAndLegalize();
    }

    // ---------------- Internals ----------------

    private static <T> Set<T> plus(Set<T> s, T x) {
        Set<T> u = new LinkedHashSet<>(s);
        u.add(x);
        return u;
    }

    /** Add an arrowhead at y on edge (x,y); record undo. */
    private void addArrowheadAt(Graph g, Node x, Node y) {
        Endpoint oldXY = g.getEndpoint(x, y);
        Endpoint oldYX = g.getEndpoint(y, x);

        // If already has an arrowhead at y, nothing to do
        if (oldXY == Endpoint.ARROW) return;

        g.setEndpoint(x, y, Endpoint.ARROW);      // X *-> Y
        // (keep the other endpoint as-is; this is arrowheads-only)
        undoStack.push(() -> {
            g.setEndpoint(x, y, oldXY);
            g.setEndpoint(y, x, oldYX);
        });
    }

    private void propagateAndLegalize() {
        propagator.propagate(pag);

        if (!legalityCheck.apply(pag)) {
            // Try rolling back orientations until legal
            while (!undoStack.isEmpty() && !legalityCheck.apply(pag)) {
                undoStack.pop().run();
                propagator.propagate(pag);
            }
            // If still illegal, revert all CD-NOD orientations
            if (!legalityCheck.apply(pag)) {
                while (!undoStack.isEmpty()) undoStack.pop().run();
                propagator.propagate(pag);
            }
        }
    }
}
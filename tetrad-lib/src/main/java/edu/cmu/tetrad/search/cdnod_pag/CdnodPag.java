package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;

import java.util.*;
import java.util.function.Function;

/**
 * CD-NOD-PAG runner where ALL Tier-0 variables are treated as contexts.
 * No requirement that a context be the last column.
 */
public final class CdnodPag {

    @FunctionalInterface
    public interface PagBuilder { Graph search(DataSet fullData); }

    @FunctionalInterface
    public interface PropagatorFactory { CdnodPagOrienter.Propagator make(); }

    private final DataSet dataAll;
    private final double alpha;
    private final ChangeTest changeTest;
    private final PagBuilder pagBuilder;
    private final Function<Graph, Boolean> legalityCheck;
    private final PropagatorFactory propFactory;

    // Config
    private int maxSubsetSize = 1;
    private boolean useProxyGuard = true;
    private boolean stripArrowheadsIntoContexts = true;

    // Names for Tier-0 contexts (set these from your Knowledge / UI)
    private final List<String> contextNames = new ArrayList<>();

    // Optional: tiers for cross-tier guard (smaller index = earlier tier)
    private final Map<String, Integer> tierByName = new HashMap<>();

    // Optional: extra protected nodes (no arrowheads into these)
    private final Set<String> forbidHeadsIntoByName = new LinkedHashSet<>();

    public CdnodPag(DataSet dataAll,
                    double alpha,
                    ChangeTest changeTest,
                    PagBuilder pagBuilder,
                    Function<Graph, Boolean> legalityCheck,
                    PropagatorFactory propFactory) {
        this.dataAll = Objects.requireNonNull(dataAll);
        this.alpha = alpha;
        this.changeTest = Objects.requireNonNull(changeTest);
        this.pagBuilder = Objects.requireNonNull(pagBuilder);
        this.legalityCheck = Objects.requireNonNull(legalityCheck);
        this.propFactory = Objects.requireNonNull(propFactory);
    }

    // ---- Configuration API ----

    /** Add one or more Tier-0 (context) variables by name. */
    public CdnodPag addContexts(String... names) {
        this.contextNames.addAll(Arrays.asList(names));
        return this;
    }

    /** Optional: provide a tier index for a variable (0 for contexts; >=1 for system tiers). */
    public CdnodPag putTier(String varName, int tierIndex) {
        this.tierByName.put(varName, tierIndex);
        return this;
    }

    /** Optional: protect additional variables from receiving arrowheads. */
    public CdnodPag forbidArrowheadsInto(String... names) {
        this.forbidHeadsIntoByName.addAll(Arrays.asList(names));
        return this;
    }

    public CdnodPag withMaxSubsetSize(int k) { this.maxSubsetSize = Math.max(0, k); return this; }
    public CdnodPag withProxyGuard(boolean on) { this.useProxyGuard = on; return this; }
    public CdnodPag withStripArrowheadsIntoContexts(boolean on) { this.stripArrowheadsIntoContexts = on; return this; }

    // ---- Run ----

    public Graph run() {
        // 1) Build baseline PAG on ALL variables (contexts included)
        Graph pag = pagBuilder.search(dataAll);

        // Resolve Node handles
        List<Node> contexts = resolveNodes(pag, contextNames);
        if (contexts.isEmpty()) {
            System.out.println("[CD-NOD-PAG] No context variables provided; skipping change-based orientation.");
            return pag;
        }

        // 1a) Post-hoc safeguard: remove arrowheads INTO any context
        if (stripArrowheadsIntoContexts) {
            for (Node c : contexts) stripHeadsInto(pag, c);
        }

        // 2) Make the change oracle over ALL contexts
        ChangeOracle oracle = new ChangeOracle(dataAll, contexts, alpha, changeTest);

        // 3) Protected nodes: contexts + extras
        Set<Node> protectedNodes = new LinkedHashSet<>(contexts);
        protectedNodes.addAll(resolveNodes(pag, forbidHeadsIntoByName));

        // 4) Optional: build a Node->tier map
        Map<Node,Integer> tiers = new HashMap<>();
        for (Map.Entry<String,Integer> e : tierByName.entrySet()) {
            Node n = pag.getNode(e.getKey());
            if (n != null) tiers.put(n, e.getValue());
        }

        // 5) Orient + propagate + legalize
        var propagator = Objects.requireNonNull(propFactory.make(), "PropagatorFactory.make() returned null");

        CdnodPagOrienter orienter = new CdnodPagOrienter(pag, oracle, legalityCheck, propagator)
                .withMaxSubsetSize(maxSubsetSize)
                .withProxyGuard(useProxyGuard)
                .withExcludeContextsFromS(true)      // exclude all contexts from S
                .forbidArrowheadsInto(protectedNodes)
                .withTiers(tiers);                   // only orient X->Y if tier(X) < tier(Y) when both known

        orienter.run();
        return pag;
    }

    // ---- Helpers ----

    private static List<Node> resolveNodes(Graph g, Collection<String> names) {
        List<Node> out = new ArrayList<>();
        for (String name : names) {
            Node n = g.getNode(name);
            if (n != null) out.add(n);
        }
        return out;
    }

    private static Set<Node> resolveNodes(Graph g, Set<String> names) {
        return new LinkedHashSet<>(resolveNodes(g, (Collection<String>) names));
    }

    /** Remove any arrowheads INTO target: for any U *-> target, set endpoint at target to CIRCLE. */
    private static void stripHeadsInto(Graph pag, Node target) {
        for (Node u : new ArrayList<>(pag.getAdjacentNodes(target))) {
            if (pag.getEndpoint(u, target) == Endpoint.ARROW) pag.setEndpoint(u, target, Endpoint.CIRCLE);
            if (pag.getEndpoint(target, u) == Endpoint.ARROW) pag.setEndpoint(target, u, Endpoint.CIRCLE);
        }
    }
}
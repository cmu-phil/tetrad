package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
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

    private final DataSet dataAll;
    private final double alpha;
    private final ChangeTest changeTest;
    private final PagBuilder pagBuilder;
    private final Function<Graph, Boolean> legalityCheck;

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

    private final Knowledge knowledge;
    private final Function<Graph, Graph> propagator;

    public CdnodPag(DataSet dataAll,
                    double alpha,
                    ChangeTest changeTest,
                    PagBuilder pagBuilder,
                    Function<Graph, Boolean> legalityCheck,
                    Function<Graph, Graph> propagator,
                    Knowledge knowledge
    ) {
        this.dataAll = Objects.requireNonNull(dataAll);
        this.alpha = alpha;
        this.changeTest = Objects.requireNonNull(changeTest);
        this.pagBuilder = Objects.requireNonNull(pagBuilder);
        this.legalityCheck = Objects.requireNonNull(legalityCheck);
        this.propagator = Objects.requireNonNull(propagator);
        this.knowledge = Objects.requireNonNull(knowledge);
    }

    // ---- Configuration API ----

    /** Add one or more Tier-0 (context) variables by name. */
    public CdnodPag addContexts(String... names) {
        this.contextNames.addAll(Arrays.asList(names));
        return this;
    }

    public CdnodPag withMaxSubsetSize(int k) { this.maxSubsetSize = Math.max(0, k); return this; }
    public CdnodPag withProxyGuard(boolean on) { this.useProxyGuard = on; return this; }

    // ---- Run ----

    public Graph run() {

        contextNames.clear();

        for (String name : knowledge.getTier(0)) {
            contextNames.add(name);
            forbidHeadsIntoByName.add(name);
        }

        // 1) Build baseline PAG on ALL variables (contexts included)
        Graph pag = pagBuilder.search(dataAll);
        pag = propagator.apply(pag);

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
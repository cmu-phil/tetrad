package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.function.Function;

/**
 * CD-NOD-PAG runner where ALL Tier-0 variables are treated as contexts. No requirement that a
 * context be the last column.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Build a baseline PAG over all variables (contexts included) with the supplied
 *       {@link PagBuilder}.</li>
 *   <li>Orient every context edge C *-* V (V not a context) as C → V. Under the exogeneity
 *       assumption (contexts have no causes), these marks are fully identified: C is an ancestor
 *       of any variable it is adjacent to in the true MAG (tail at C), and V cannot be an
 *       ancestor of C (arrowhead at V). Context–context adjacencies are left untouched and
 *       logged, since under mutually independent exogenous contexts they should not occur.</li>
 *   <li>Propagate and run the {@link CdnodPagOrienter}, which adds evidence-backed arrowheads
 *       X *-&gt; Y where conditioning on X screens Y from the contexts, committing each candidate
 *       on a copy and adopting it only if the propagated result satisfies the strong legality
 *       predicate.</li>
 * </ol>
 *
 * <p><b>PagBuilder and propagator contract.</b> For best results the supplied PagBuilder should
 * already embed the exogeneity constraint (e.g., run BOSS-FCI/FCIT with knowledge forbidding
 * edges into the Tier-0 variables), and the propagator should be knowledge-aware. This runner
 * defensively re-asserts the C → V marks after every propagation (via a wrapped propagator), so a
 * knowledge-blind propagator cannot silently reintroduce heads into contexts; but if the baseline
 * PAG itself is built without the constraint, the forced marks may leave it outside strong
 * legality, which is logged.</p>
 */
public final class CdnodPag {

    /**
     * The input dataset over all variables, contexts included.
     */
    private final DataSet dataAll;

    /**
     * Significance level passed to the change tests.
     */
    private final double alpha;

    /**
     * The change/instability test used by the ChangeOracle.
     */
    private final ChangeTest changeTest;

    /**
     * Builds the baseline PAG from the dataset.
     */
    private final PagBuilder pagBuilder;

    /**
     * Strong legality predicate: the fixed-point condition PAG(MAG(G)) == G.
     */
    private final Function<Graph, Boolean> legalityCheck;

    /**
     * Orientation-rule propagator supplied by the caller. This runner wraps it so that the sound
     * context-edge marks are re-asserted after every propagation.
     */
    private final Function<Graph, Graph> propagator;

    /**
     * Names of Tier-0 contexts, resolved per run.
     */
    private final List<String> contextNames = new ArrayList<>();

    /**
     * Optional extra protected nodes (no arrowheads into these), by name.
     */
    private final Set<String> forbidHeadsIntoByName = new LinkedHashSet<>();

    /**
     * Prior knowledge; Tier-0 variables are treated as contexts, and the full tier structure is
     * passed to the orienter as a tier guard.
     */
    private final Knowledge knowledge;

    private int maxSubsetSize = 1;
    private boolean excludeContextsFromS = true;
    private boolean verbose = false;

    /**
     * Constructs an instance of the CdnodPag class with the specified parameters.
     *
     * @param dataAll       The dataset to be analyzed.
     * @param alpha         The significance level used in the change tests.
     * @param changeTest    The statistical change test to assess context dependence.
     * @param pagBuilder    The PAG builder used to construct the baseline PAG.
     * @param legalityCheck The strong legality predicate PAG(MAG(G)) == G.
     * @param propagator    The orientation-rule propagator (will be wrapped; see class javadoc).
     * @param knowledge     Prior background knowledge; Tier-0 variables are the contexts.
     */
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

    private static List<Node> resolveNodes(Graph g, Collection<String> names) {
        List<Node> out = new ArrayList<>();
        for (String name : names) {
            Node n = g.getNode(name);
            if (n != null) out.add(n);
        }
        return out;
    }

    /**
     * Orients each context edge C *-* V (V not a context) as C → V: tail at C, arrowhead at V.
     * Both marks are identified under exogeneity: C ∈ An(V) for any V adjacent to C in the true
     * MAG, and V ∉ An(C) since contexts have no causes. Context–context adjacencies are left
     * untouched (and logged), since they should not occur under mutually independent exogenous
     * contexts and no orientation of them is licensed by the model.
     */
    private static void orientContextEdges(Graph g, Collection<Node> contexts) {
        Set<Node> ctx = new LinkedHashSet<>(contexts);
        for (Node c : ctx) {
            for (Node v : new ArrayList<>(g.getAdjacentNodes(c))) {
                if (ctx.contains(v)) {
                    TetradLogger.getInstance().log("[CD-NOD-PAG] Warning: context-context adjacency "
                            + c.getName() + " *-* " + v.getName()
                            + "; leaving unoriented. Contexts are assumed exogenous and mutually independent.");
                    continue;
                }
                g.setEndpoint(v, c, Endpoint.TAIL);   // mark at c: tail (c is an ancestor of v)
                g.setEndpoint(c, v, Endpoint.ARROW);  // mark at v: head (v is not an ancestor of c)
            }
        }
    }

    // ---- Configuration API ----

    /**
     * Sets the maximum size of conditioning sets S considered per edge by the orienter; negative
     * values are clamped to 0.
     *
     * @param k the desired maximum subset size.
     * @return this, for chaining.
     */
    public CdnodPag withMaxSubsetSize(int k) {
        this.maxSubsetSize = TMath.max(0, k);
        return this;
    }

    /**
     * Configures whether contexts are excluded from the conditioning sets S offered to the change
     * tests.
     *
     * @param on true to exclude contexts from S.
     * @return this, for chaining.
     */
    public CdnodPag withExcludeContextsFromS(boolean on) {
        this.excludeContextsFromS = on;
        return this;
    }

    /**
     * Enables or disables logging of individual orientation decisions and summary telemetry.
     *
     * @param on true for verbose logging.
     * @return this, for chaining.
     */
    public CdnodPag withVerbose(boolean on) {
        this.verbose = on;
        return this;
    }

    /**
     * Executes the pipeline and returns the resulting PAG.
     *
     * @return the resulting PAG after context-edge orientation and change-based orientation, with
     * strong legality enforced per commit.
     */
    public Graph run() {

        contextNames.clear();
        forbidHeadsIntoByName.clear();

        for (String name : knowledge.getTier(0)) {
            contextNames.add(name);
            forbidHeadsIntoByName.add(name);
        }

        // 1) Build baseline PAG on ALL variables (contexts included).
        Graph pag = pagBuilder.search(dataAll);

        // Resolve Node handles. (Copies made downstream share node objects, so these handles
        // remain valid throughout.)
        List<Node> contexts = resolveNodes(pag, contextNames);
        if (contexts.isEmpty()) {
            TetradLogger.getInstance().log("[CD-NOD-PAG] No context variables provided; skipping change-based orientation.");
            return propagator.apply(pag);
        }

        // Guarded propagator: after each user propagation, re-assert the identified context-edge
        // marks, so a knowledge-blind propagator cannot reintroduce heads into contexts. The
        // strong legality check downstream then adjudicates the combination.
        final List<Node> ctxFinal = contexts;
        Function<Graph, Graph> guardedPropagator = g -> {
            Graph h = propagator.apply(g);
            orientContextEdges(h, ctxFinal);
            return h;
        };

        // 2) Orient context edges and propagate the baseline.
        orientContextEdges(pag, contexts);
        pag = guardedPropagator.apply(pag);

        if (!legalityCheck.apply(pag)) {
            TetradLogger.getInstance().log("[CD-NOD-PAG] Warning: baseline PAG fails strong legality after context-edge "
                    + "orientation. The PagBuilder was likely run without knowledge forbidding edges into "
                    + "Tier-0 contexts; results may be unreliable.");
        }

        // 3) Protected nodes: contexts + extras.
        Set<Node> protectedNodes = new LinkedHashSet<>(contexts);
        protectedNodes.addAll(resolveNodes(pag, forbidHeadsIntoByName));

        // 4) Tier map for the orienter's tier guard.
        Map<Node, Integer> tiers = new HashMap<>();
        for (int i = 0; i < knowledge.getNumTiers(); i++) {
            for (String nodeName : knowledge.getTier(i)) {
                Node n = pag.getNode(nodeName);
                if (n != null) tiers.put(n, i);
            }
        }

        // 5) Change oracle over ALL contexts.
        ChangeOracle oracle = new ChangeOracle(dataAll, contexts, alpha, changeTest);

        CdnodPagOrienter orienter = new CdnodPagOrienter(pag, oracle, legalityCheck, guardedPropagator)
                .withMaxSubsetSize(maxSubsetSize)
                .withExcludeContextsFromS(excludeContextsFromS)
                .withVerbose(verbose)
                .forbidArrowheadsInto(protectedNodes)
                .withTiers(tiers);

        Graph out = orienter.run();

        if (verbose) {
            TetradLogger.getInstance().log("[CD-NOD-PAG] Telemetry: attempted=" + orienter.getAttemptedCommits()
                    + " accepted=" + orienter.getAcceptedCommits()
                    + " legality-rejected=" + orienter.getLegalityRejections());
        }

        // The orienter adopts copies; the local 'pag' reference above is stale. Return the
        // orienter's final graph.
        return out;
    }

    /**
     * Functional interface representing a builder for constructing the baseline PAG.
     * <p>
     * Implementations should, where possible, embed the exogeneity constraint for Tier-0 contexts
     * (e.g., by supplying knowledge that forbids edges into contexts to the underlying search);
     * see the class javadoc.
     */
    @FunctionalInterface
    public interface PagBuilder {

        /**
         * Builds the baseline PAG from the given dataset.
         *
         * @param fullData the dataset over all variables, contexts included
         * @return the resulting PAG
         */
        Graph search(DataSet fullData);
    }
}

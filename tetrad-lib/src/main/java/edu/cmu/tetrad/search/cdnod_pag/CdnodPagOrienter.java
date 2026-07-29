package edu.cmu.tetrad.search.cdnod_pag;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.function.Function;

/**
 * Orients arrowheads in a PAG using context-based change/stability evidence, maintaining strong
 * PAG legality throughout.
 *
 * <p><b>The C1 rule.</b> For an edge X *-* Y not yet directed either way, and a small conditioning
 * set S ⊆ Adj(Y) \ {X}, if
 * <ol>
 *   <li>Y's conditional distribution given S changes with some context (changes(Y, S)), and</li>
 *   <li>Y is stable across all contexts given S ∪ {X} (stable(Y, S ∪ {X})),</li>
 * </ol>
 * then, under exogenous mutually independent contexts, no selection, and faithfulness of the
 * pooled distribution to the augmented graph, Y is not an ancestor of X, so an arrowhead at Y
 * (X *-&gt; Y) is sound. (Sketch: (1)+(2) force X onto every S-open context-to-Y path as a
 * non-collider; if Y were an ancestor of X, the adjacency edge Y → X together with either the
 * head-at-X or fork-at-X configuration of such a path yields an m-connecting path given
 * S ∪ {X} — via a direct collider splice in the head case, or via the first S-descendant of the
 * forced C-side collider in the fork case — contradicting (2).)
 *
 * <p><b>Commit discipline.</b> Each candidate arrowhead is applied to a <i>copy</i> of the current
 * PAG; the copy is propagated by the supplied propagator and checked against the strong legality
 * predicate (the fixed point PAG(MAG(G)) == G). Only if the check passes is the copy adopted as
 * the current PAG. Failed attempts are discarded whole, so no propagated marks from rejected
 * commits can leak into subsequent state — there is no undo stack to keep consistent. The
 * propagator may mutate its argument or return a new graph; only the returned instance is used.</p>
 *
 * <p><b>Caveat (interim classes).</b> Even when every individual commit is sound, the set of MAGs
 * consistent with the CI facts plus the change constraints need not form a full PAG equivalence
 * class, so the strong-legality fixed point may be unattainable for some sound commits. Such
 * commits are rejected (conservatively, and order-dependently). The telemetry counters record how
 * often this occurs; a nonzero {@link #getLegalityRejections()} count flags that information was
 * dropped to preserve legality.</p>
 */
public final class CdnodPagOrienter {

    /**
     * Provides multi-context change/stability judgments.
     */
    private final ChangeOracle oracle;

    /**
     * Strong legality predicate: the fixed-point condition PAG(MAG(G)) == G.
     */
    private final Function<Graph, Boolean> strongPagLegality;

    /**
     * Orientation-rule propagator. Applied to trial copies after each candidate commit; the
     * returned instance is used (the argument may or may not be mutated).
     */
    private final Function<Graph, Graph> propagator;

    /**
     * Nodes that must not receive arrowheads (contexts, plus any extras). A node in this set is
     * never used in the Y role. Nodes in this set MAY appear in the X role: orienting X *-&gt; Y
     * places a head at Y only, so protection of X is not at issue.
     */
    private final Set<Node> protectedNodes = new LinkedHashSet<>();

    /**
     * Optional tier map (smaller = earlier). If both endpoints are tiered and tier(X) >= tier(Y),
     * the pair (X, Y) is skipped: X cannot be oriented into Y across or within tiers.
     */
    private final Map<Node, Integer> tier = new HashMap<>();

    /**
     * The current PAG. Replaced wholesale by adopted trial copies; node objects are shared across
     * copies, so Node handles remain valid.
     */
    private Graph pag;

    private int maxSubsetSize = 1;
    private boolean excludeContextsFromS = true;
    private boolean verbose = false;

    // --- telemetry ---
    private int attemptedCommits = 0;
    private int acceptedCommits = 0;
    private int legalityRejections = 0;

    /**
     * Constructs a CdnodPagOrienter.
     *
     * @param pag               the PAG to orient (node objects are shared with adopted copies)
     * @param oracle            the ChangeOracle providing contexts and change/stability judgments
     * @param strongPagLegality the strong legality predicate PAG(MAG(G)) == G
     * @param propagator        the orientation-rule propagator; the returned instance is used
     */
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

    private static <T> Set<T> plus(Set<T> s, T x) {
        Set<T> u = new LinkedHashSet<>(s);
        u.add(x);
        return u;
    }

    // ---- Fluent setters ----

    /**
     * Sets the maximum size of conditioning sets S considered per edge; negative values are
     * clamped to 0.
     *
     * @param k the desired maximum subset size.
     * @return this, for chaining.
     */
    public CdnodPagOrienter withMaxSubsetSize(int k) {
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
    public CdnodPagOrienter withExcludeContextsFromS(boolean on) {
        this.excludeContextsFromS = on;
        return this;
    }

    /**
     * Enables or disables logging of individual commit decisions.
     *
     * @param on true for verbose logging.
     * @return this, for chaining.
     */
    public CdnodPagOrienter withVerbose(boolean on) {
        this.verbose = on;
        return this;
    }

    /**
     * Prohibits arrowheads into the given nodes (they are never used in the Y role).
     *
     * @param nodes the nodes to protect.
     * @return this, for chaining.
     */
    public CdnodPagOrienter forbidArrowheadsInto(Collection<Node> nodes) {
        this.protectedNodes.addAll(nodes);
        return this;
    }

    /**
     * Prohibits arrowheads into the given node (it is never used in the Y role).
     *
     * @param node the node to protect.
     * @return this, for chaining.
     */
    public CdnodPagOrienter forbidArrowheadsInto(Node node) {
        this.protectedNodes.add(node);
        return this;
    }

    /**
     * Merges the given tier assignments (smaller = earlier) into the tier map.
     *
     * @param tiers the tier assignments; may be null.
     * @return this, for chaining.
     */
    public CdnodPagOrienter withTiers(Map<Node, Integer> tiers) {
        if (tiers != null) this.tier.putAll(tiers);
        return this;
    }

    // ---- Execution ----

    /**
     * Runs the orientation pass and returns the resulting PAG. Every adopted state has passed the
     * strong legality check after propagation, so no final rollback pass is needed; callers should
     * use the returned graph (the graph passed to the constructor may be a stale, superseded copy).
     *
     * @return the final PAG.
     */
    public Graph run() {
        final List<Node> ctx = oracle.contexts();

        for (Node y : new ArrayList<>(pag.getNodes())) {
            if (protectedNodes.contains(y)) continue; // never add heads into protected

            // Adjacency (the skeleton) never changes during this pass, so this snapshot of
            // neighbors of y remains valid across adopted copies (which share node objects).
            for (Node x : new ArrayList<>(pag.getAdjacentNodes(y))) {
                if (pag.isDirectedFromTo(x, y) || pag.isDirectedFromTo(y, x)) continue;
                if (pag.getEndpoint(x, y) == Endpoint.ARROW) continue; // head at y already present

                // Tier guard: never orient into an earlier or equal tier.
                Integer tx = tier.get(x), ty = tier.get(y);
                if (tx != null && ty != null && tx >= ty) continue;

                // S candidates: Adj(Y) \ {X}, optionally minus contexts.
                List<Node> neigh = new ArrayList<>(pag.getAdjacentNodes(y));
                neigh.remove(x);
                if (excludeContextsFromS) neigh.removeAll(ctx);

                tryOrientC1PerEdgeStrong(x, y, neigh);
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("[CD-NOD-PAG] Orienter done: attempted=" + attemptedCommits
                    + " accepted=" + acceptedCommits
                    + " legality-rejected=" + legalityRejections);
        }

        return pag;
    }

    /**
     * Returns the current PAG (the final one, after {@link #run()} has completed).
     *
     * @return the current PAG.
     */
    public Graph getPag() {
        return pag;
    }

    /**
     * Number of candidate commits whose change/stability gates passed (each was tried on a copy).
     *
     * @return the attempted-commit count.
     */
    public int getAttemptedCommits() {
        return attemptedCommits;
    }

    /**
     * Number of commits adopted (trial copy passed strong legality after propagation).
     *
     * @return the accepted-commit count.
     */
    public int getAcceptedCommits() {
        return acceptedCommits;
    }

    /**
     * Number of commits rejected because the propagated trial failed strong legality. A nonzero
     * count indicates evidence-backed orientations were dropped to preserve legality (the interim
     * equivalence class was not representable as a legal PAG along this commit order).
     *
     * @return the legality-rejection count.
     */
    public int getLegalityRejections() {
        return legalityRejections;
    }

    // ---- Internals ----

    private void tryOrientC1PerEdgeStrong(Node x, Node y, List<Node> neigh) {
        for (Set<Node> S0 : SmallSubsetIter.subsets(neigh, maxSubsetSize)) {
            // Work on a copy; never mutate the iterator's set.
            Set<Node> S = new LinkedHashSet<>(S0);

            // Gate 1: Y shows change under S (w.r.t. some context).
            if (!oracle.changes(y, S)) continue;

            // Gate 2: adding X stabilizes Y across all contexts.
            if (!oracle.stable(y, plus(S, x))) continue;

            attemptedCommits++;

            // Try the commit on a copy; adopt only if strong legality holds after propagation.
            Graph trial = new EdgeListGraph(pag);
            trial.setEndpoint(x, y, Endpoint.ARROW); // X *-> Y; the mark at X is preserved
            trial = propagator.apply(trial);

            if (strongPagLegality.apply(trial)) {
                acceptedCommits++;
                pag = trial;
                if (verbose) {
                    TetradLogger.getInstance().log("[CD-NOD-PAG] Oriented " + x.getName() + " *-> " + y.getName()
                            + " (S=" + names(S) + ")");
                }
                return; // this pair is settled; move on
            } else {
                legalityRejections++;
                if (verbose) {
                    TetradLogger.getInstance().log("[CD-NOD-PAG] Rejected " + x.getName() + " *-> " + y.getName()
                            + " (S=" + names(S) + "): strong legality failed after propagation");
                }
                // Discard the trial entirely; try other S.
            }
        }
    }

    private static String names(Set<Node> S) {
        List<String> out = new ArrayList<>();
        for (Node n : S) out.add(n.getName());
        Collections.sort(out);
        return "{" + String.join(",", out) + "}";
    }
}

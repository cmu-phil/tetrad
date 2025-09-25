package edu.cmu.tetrad.search.mag.gps;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.DiscriminatingPath;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.R0R4Strategy;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Set;

/**
 * R0/R4 strategy for GPS: decide collider/noncollider by RICF score comparison,
 * subject to legal-MAG constraints. No sepsets required.
 *
 * Intended for use with FciOrient(..., R0R4Strategy) so the rest of Zhang’s
 * rules run in the usual order, but R0 and R4 decisions are made via likelihood.
 */
public final class R0R4StrategyGpsRicf implements R0R4Strategy {

    private final ScoreRicfMag scorer;
    private final ICovarianceMatrix cov;
    private Knowledge knowledge = new Knowledge();

    // Small tie-break to avoid flapping on numerically equal fits
    private double eps = 1e-8;
    private boolean verbose = false;

    public R0R4StrategyGpsRicf(ICovarianceMatrix cov, double tolerance) {
        this.cov = cov;
        this.scorer = new ScoreRicfMag(cov, tolerance);
    }

    /** Optional: bias toward simpler (noncollider) if |Δ| < eps. */
    public R0R4StrategyGpsRicf setEps(double eps) {
        this.eps = Math.max(0.0, eps);
        return this;
    }

    public R0R4StrategyGpsRicf setVerbose(boolean v) {
        this.verbose = v;
        return this;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    @Override
    public Knowledge getknowledge() {
        return knowledge;
    }

    // -------- R0: Unshielded triple X *-o Y o-* Z  (X not adjacent Z) --------
    //
    // Decide between collider (X -> Y <- Z) and noncollider (pick the legal MAG option
    // with no arrowheads into Y, i.e., UG on both sides if possible, else tails at Y),
    // by comparing RICF-BIC scores. Only consider legal-MAG realizations.
    //
    @Override
    public boolean isUnshieldedCollider(Graph g, Node x, Node y, Node z) {
        if (g.isAdjacentTo(x, z)) return false; // Not unshielded; leave to other rules.

        double sCollider = scoreIfCollider(g, x, y, z);
        double sNoncoll  = scoreIfNoncollider(g, x, y, z);

        if (verbose) {
            TetradLogger.getInstance().log(String.format(
                    "R0 (GPS): [%s - %s - %s] sCol=%.6f sNon=%.6f Δ=%.6f",
                    x, y, z, sCollider, sNoncoll, (sCollider - sNoncoll)));
        }

        // collider if strictly better by more than eps
        return sCollider > sNoncoll + eps;
    }

    // -------- R4: Discriminating path orientation --------
    //
    // Given discriminating path X →* ... →* W *-o V o-* Y, decide whether V is
    // a collider (W -> V <- Y) or noncollider (tails at V) by RICF score,
    // respecting legal-MAG constraints.
    //
    @Override
    public Pair<DiscriminatingPath, Boolean> doDiscriminatingPathOrientation(
            DiscriminatingPath dp, Graph g, Set<Node> vNodes
    ) {
        if (!dp.existsIn(g)) return Pair.of(dp, false);

        Node w = dp.getW();
        Node v = dp.getV();
        Node y = dp.getY();

        // Two candidates: collider at V vs noncollider at V.
        double sCollider = scoreR4Collider(g, w, v, y);
        double sNoncoll  = scoreR4Noncollider(g, w, v, y);

        if (verbose) {
            TetradLogger.getInstance().log(String.format(
                    "R4 (GPS): [%s - %s - %s] sCol=%.6f sNon=%.6f Δ=%.6f",
                    w, v, y, sCollider, sNoncoll, (sCollider - sNoncoll)));
        }

        if (sCollider > sNoncoll + eps) {
            if (!FciOrient.isArrowheadAllowed(w, v, g, knowledge)) return Pair.of(dp, false);
            if (!FciOrient.isArrowheadAllowed(y, v, g, knowledge)) return Pair.of(dp, false);
            // Orient W -> V <- Y
            Graph h = g; // in-place; FciOrient expects side-effects
            h.removeEdges(w, v);
            h.removeEdges(y, v);
            h.addDirectedEdge(w, v);
            h.addDirectedEdge(y, v);
            if (!h.paths().isLegalMag()) {
                // revert if violated (shouldn’t normally happen since we scored legal only)
                h.removeEdge(w, v); h.addUndirectedEdge(w, v);
                h.removeEdge(y, v); h.addUndirectedEdge(y, v);
                return Pair.of(dp, false);
            }
            return Pair.of(dp, true);
        } else if (sNoncoll > sCollider + eps) {
            // Noncollider: place tails at V sides if allowed (prefer UG if possible)
            Graph h = g;
            if (!orientNoncolliderAt(h, w, v)) return Pair.of(dp, false);
            if (!orientNoncolliderAt(h, y, v)) return Pair.of(dp, false);
            if (!h.paths().isLegalMag()) return Pair.of(dp, false);
            return Pair.of(dp, true);
        } else {
            // Within tie → no orientation (stay conservative)
            return Pair.of(dp, false);
        }
    }

    // ======== Helpers: local scoring under candidate orientations ========

    private double scoreIfCollider(Graph g, Node x, Node y, Node z) {
        Graph h = new EdgeListGraph(g);
        if (!orientColliderAt(h, x, y, z)) return Double.NEGATIVE_INFINITY;
        if (!h.paths().isLegalMag()) return Double.NEGATIVE_INFINITY;
        return safeScore(h);
    }

    private double scoreIfNoncollider(Graph g, Node x, Node y, Node z) {
        Graph h = new EdgeListGraph(g);
        // Try to realize both sides without arrowheads into y.
        if (!orientNoncolliderAt(h, x, y)) return Double.NEGATIVE_INFINITY;
        if (!orientNoncolliderAt(h, z, y)) return Double.NEGATIVE_INFINITY;
        if (!h.paths().isLegalMag()) return Double.NEGATIVE_INFINITY;
        return safeScore(h);
    }

    private double scoreR4Collider(Graph g, Node w, Node v, Node y) {
        Graph h = new EdgeListGraph(g);
        // W -> V <- Y
        h.removeEdges(w, v);
        h.removeEdges(y, v);
        h.addDirectedEdge(w, v);
        h.addDirectedEdge(y, v);
        if (!h.paths().isLegalMag()) return Double.NEGATIVE_INFINITY;
        return safeScore(h);
    }

    private double scoreR4Noncollider(Graph g, Node w, Node v, Node y) {
        Graph h = new EdgeListGraph(g);
        if (!orientNoncolliderAt(h, w, v)) return Double.NEGATIVE_INFINITY;
        if (!orientNoncolliderAt(h, y, v)) return Double.NEGATIVE_INFINITY;
        if (!h.paths().isLegalMag()) return Double.NEGATIVE_INFINITY;
        return safeScore(h);
    }

    private double safeScore(Graph h) {
        try {
            return scorer.score(h);
        } catch (Exception ex) {
            // Any numerical hiccup → treat as invalid candidate
            return Double.NEGATIVE_INFINITY;
        }
    }

    // ======== Orientation primitives (legal-MAG aware, conservative) ========

    /**
     * Force collider X -> Y <- Z on the current endpoints of (X,Y) and (Z,Y).
     * Returns false if that orientation conflicts with fixed knowledge or creates
     * an immediate structural impossibility.
     */
    private boolean orientColliderAt(Graph h, Node x, Node y, Node z) {
        // Clear edges then add directed
        h.removeEdges(x, y);
        h.removeEdges(z, y);

        // Knowledge checks
        if (!FciOrient.isArrowheadAllowed(x, y, h, knowledge)) return false;
        if (!FciOrient.isArrowheadAllowed(z, y, h, knowledge)) return false;

        h.addDirectedEdge(x, y);
        h.addDirectedEdge(z, y);
        return true;
    }

    /**
     * Orient “noncollider at (a,b)”: place a tail at b (i.e., avoid arrowhead into b).
     * Prefer an undirected edge a—b if possible; otherwise use a -> b or b -> a
     * consistent with knowledge and acyclicity. This keeps it MAG-legal.
     */
    private boolean orientNoncolliderAt(Graph h, Node a, Node b) {
        // If there is no edge yet, add the most neutral non-collider option first.
        if (!h.isAdjacentTo(a, b)) {
            // UG allowed only if neither endpoint has arrow into the other’s UG component
            if (GpsUndirectedAllowed(h, a, b)) {
                h.addUndirectedEdge(a, b);
                if (h.paths().isLegalMag()) return true;
                h.removeEdge(a, b);
            }
            // Try tails at b: a -> b
            if (FciOrient.isArrowheadAllowed(a, b, h, knowledge)) {
                h.addDirectedEdge(a, b);
                if (h.paths().isLegalMag()) return true;
                h.removeEdge(a, b);
            }
            // Try tails at a: b -> a
            if (FciOrient.isArrowheadAllowed(b, a, h, knowledge)) {
                h.addDirectedEdge(b, a);
                if (h.paths().isLegalMag()) return true;
                h.removeEdge(a, b);
            }
            // As a last resort, a ↔ b if legal MAG permits (rare here; avoid for noncollider)
            return false;
        }

        // If already adjacent, try to push it away from having an arrowhead pointing to b.
        Edge e = h.getEdge(a, b);
        if (e == null) return false;

        // If it already has no arrowhead at b, we're fine.
        if (h.getEndpoint(a, b) != Endpoint.ARROW) return true;

        // Otherwise, re-shape the edge to remove the arrowhead at b.
        h.removeEdge(a, b);

        // Try UG
        if (GpsUndirectedAllowed(h, a, b)) {
            h.addUndirectedEdge(a, b);
            if (h.paths().isLegalMag()) return true;
            h.removeEdge(a, b);
        }
        // Try a -> b (arrow at b) is *not* allowed for noncollider; try b -> a (tail at b)
        if (FciOrient.isArrowheadAllowed(b, a, h, knowledge)) {
            h.addDirectedEdge(b, a);
            if (h.paths().isLegalMag()) return true;
            h.removeEdge(a, b);
        }

        // Could not realize a clean noncollider without violating legality.
        return false;
    }

    // Conservative UG allowance: both endpoints must have no incoming/outgoing ARROWs that
    // would break the ancestral constraints of the UG component (i.e., UG nodes have degree
    // only in UG, no arrowheads touching them).
    private static boolean GpsUndirectedAllowed(Graph h, Node u, Node v) {
        // Neither endpoint may have incident arrowheads if we place an undirected edge.
        boolean uOk = h.getNodesInTo(u, Endpoint.ARROW).isEmpty() && h.getNodesOutTo(u, Endpoint.ARROW).isEmpty();
        boolean vOk = h.getNodesInTo(v, Endpoint.ARROW).isEmpty() && h.getNodesOutTo(v, Endpoint.ARROW).isEmpty();
        return uOk && vOk;
    }
}
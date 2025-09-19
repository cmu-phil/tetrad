package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * CyclicBoss: BOSS-first cyclic wrapper.
 * 1) Run base permutation algorithm (e.g., BOSS via PermutationSearch) to get a DAG backbone.
 * 2) Propose back-edges (reverse wrt DAG's topo order), ranked by bidirectional residual predictability.
 * 3) Greedy add + prune under a cyclic SEM BIC (CyclicSemScorer), with stability/SCC/knowledge guards.
 */
public final class CyclicBoss {

    private final PermutationSearch base;     // e.g., new PermutationSearch(new Boss(score))
    private Knowledge knowledge = new Knowledge();
    private final CyclicSemScorer scorer;

    // ---- Tunables (sane defaults) ----
    private int candidatePoolSize      = 5_000;          // cap candidate list
    private int maxBackEdges           = Integer.MAX_VALUE;
    private int maxSccSize             = 4;              // keep loops tiny by default
    private double stabilityTol        = 0.999;          // spectral radius guard
    private boolean higherIsBetterBic  = false;          // set true if your BIC is 2L - k ln n

    // Sparsity dials
    private double minImprove          = 3.0;            // require this ΔBIC margin to add edge
    private double minCorr             = 0.10;           // bidirectional residual corr cutoff

    public CyclicBoss(PermutationSearch base) {
        this.base = Objects.requireNonNull(base, "base algorithm required");
        this.scorer = new CyclicSemScorer()
                .withStabilityTol(stabilityTol)
                .withHigherIsBetterBic(higherIsBetterBic);
    }

    // ---- Fluent setters ----
    public CyclicBoss withCandidatePoolSize(int k)     { this.candidatePoolSize = Math.max(0, k); return this; }
    public CyclicBoss withMaxBackEdges(int k)          { this.maxBackEdges = Math.max(0, k); return this; }
    public CyclicBoss withMaxSccSize(int k)            { this.maxSccSize = Math.max(1, k); return this; }
    public CyclicBoss withStabilityTol(double tol)     { this.stabilityTol = tol; this.scorer.withStabilityTol(tol); return this; }
    public CyclicBoss withHigherIsBetterBic(boolean v) { this.higherIsBetterBic = v; this.scorer.withHigherIsBetterBic(v); return this; }
    public CyclicBoss withMinImprove(double v)         { this.minImprove = Math.max(0.0, v); return this; }
    public CyclicBoss withMinCorr(double v)            { this.minCorr = Math.max(0.0, v); return this; }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = (knowledge == null) ? new Knowledge() : knowledge;
        // also pass through to base if it accepts knowledge
        try {
            base.setKnowledge(this.knowledge);
        } catch (Throwable ignored) { /* PermutationSearch supports setKnowledge */ }
    }

    public Graph search(DataSet data) throws InterruptedException {
        // 1) Backbone DAG
        Graph G = base.search(false);
        if (!(G instanceof EdgeListGraph)) G = new EdgeListGraph(G);
        final List<Node> nodes = G.getNodes();
        final int p = nodes.size();

        // Topological order from DAG
        final List<Node> topo = G.paths().getValidOrder(nodes, true);
        final Map<Node, Integer> topoIndex = new HashMap<>();
        for (int i = 0; i < topo.size(); i++) topoIndex.put(topo.get(i), i);

        // 2) Candidate back-edges ranked by bidirectional residual signal
        List<Edge> C = rankBackEdgeCandidates(data, G, nodes, topoIndex, minCorr);
        if (C.size() > candidatePoolSize) C = C.subList(0, candidatePoolSize);

        // 3) Score starting DAG under cyclic scorer (valid for DAG too)
        CyclicSemScorer.ScoreResult best = scorer.score(data, G);
        if (!best.stable) return G; // should not happen for a DAG

        int added = 0;
        boolean changed = true;
        final LinkedHashSet<Edge> addedBackEdges = new LinkedHashSet<>();

        while (changed) {
            changed = false;

            // ---- ADD: pick the single best back-edge that clears the ΔBIC margin ----
            Edge bestEdge = null;
            CyclicSemScorer.ScoreResult bestEdgeScore = null;

            for (Edge e : C) {
                if (G.containsEdge(e)) continue;
                if (!respectsKnowledge(e, knowledge)) continue;
                if (added >= maxBackEdges) break;
                if (wouldExceedMaxScc(G, e, maxSccSize)) continue;

                Graph G2 = new EdgeListGraph(G);
                G2.addEdge(e);

                CyclicSemScorer.ScoreResult s2 = scorer.score(data, G2);
                if (!s2.stable) continue;

                double delta = s2.bic - best.bic;
                boolean improves = higherIsBetterBic ? (delta >  minImprove)
                        : (delta < -minImprove);
                if (!improves) continue;

                if (bestEdge == null) {
                    bestEdge = e;
                    bestEdgeScore = s2;
                } else {
                    double currentDelta = bestEdgeScore.bic - best.bic;
                    boolean isBetter = higherIsBetterBic ? (delta > currentDelta)
                            : (delta < currentDelta);
                    if (isBetter) {
                        bestEdge = e;
                        bestEdgeScore = s2;
                    }
                }
            }

            if (bestEdge != null) {
                G.addEdge(bestEdge);
                addedBackEdges.add(bestEdge);
                best = bestEdgeScore;
                added++;
                changed = true;
            }

            // ---- PRUNE: drop any previously added back-edge that no longer clears the margin ----
            for (Edge e : new ArrayList<>(addedBackEdges)) {
                Graph G2 = new EdgeListGraph(G);
                G2.removeEdge(e);
                CyclicSemScorer.ScoreResult s2 = scorer.score(data, G2);
                if (!s2.stable) continue;
                double delta = s2.bic - best.bic;
                boolean betterIfRemoved = higherIsBetterBic ? (delta >  minImprove)
                        : (delta < -minImprove);
                if (betterIfRemoved) {
                    G = G2;
                    best = s2;
                    addedBackEdges.remove(e);
                    changed = true;
                }
            }
        }

        return G;
    }

    // ---------- internals ----------

    private boolean respectsKnowledge(Edge e, Knowledge K) {
        if (K == null) return true;
        final Node from = e.getNode1();
        final Node to   = e.getNode2();
        if (K.isForbidden(from.getName(), to.getName())) return false;

        // tier semantics: conservatively disallow "upward" edges across tiers
        int tFrom = K.isInWhichTier(from);
        int tTo   = K.isInWhichTier(to);
        if (tFrom < tTo) return false;

        return true;
    }

    private boolean wouldExceedMaxScc(Graph g, Edge candidate, int maxScc) {
        Graph h = new EdgeListGraph(g);
        h.addEdge(candidate);
        List<Set<Node>> sccs = GraphUtils.stronglyConnectedComponents(h);
        for (Set<Node> s : sccs) if (s.size() > maxScc) return true;
        return false;
    }

    /**
     * Ranks back-edges j->i (reverse of topo order) by conservative bidirectional residual support:
     * score = min( |corr(x_j, r_i)|, |corr(x_i, r_j)| ), filtered by minCorr.
     */
    private List<Edge> rankBackEdgeCandidates(DataSet data, Graph g, List<Node> nodes,
                                              Map<Node,Integer> topoIndex, double minCorr) {
        // Build data matrix X (n x p), aligned with 'nodes' order
        SimpleMatrix X = toMatrix(data, nodes);
        SimpleMatrix Xc = centerColumns(X);

        // Precompute residuals r_i = x_i - X_{Pa(i)} beta (OLS)
        List<SimpleMatrix> resid = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            Node iNode = nodes.get(i);
            Set<Node> pa = new HashSet<>(g.getParents(iNode));
            SimpleMatrix xi = Xc.extractVector(false, i);
            if (pa.isEmpty()) {
                resid.add(xi.copy());
                continue;
            }
            int[] paIdx = pa.stream().mapToInt(nodes::indexOf).toArray();
            SimpleMatrix Xpa = extractColumns(Xc, paIdx);
            SimpleMatrix beta = lsSolve(Xpa, xi);
            SimpleMatrix fitted = Xpa.mult(beta);
            resid.add(xi.minus(fitted));
        }

        record Cand(Edge e, double score) {}
        List<Cand> cands = new ArrayList<>();

        for (int i = 0; i < nodes.size(); i++) {
            Node iNode = nodes.get(i);
            SimpleMatrix ri = resid.get(i);
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;
                Node jNode = nodes.get(j);

                // Only consider back-edges wrt topo order, skip existing adjacency
                if (topoIndex.get(jNode) <= topoIndex.get(iNode)) continue;
                if (g.isAdjacentTo(iNode, jNode)) continue;

                // quick forbid-tier filter
                if (knowledge != null && knowledge.isForbidden(jNode.getName(), iNode.getName())) continue;

                // Bidirectional residual signal
                SimpleMatrix xj = Xc.extractVector(false, j);
                SimpleMatrix xi = Xc.extractVector(false, i);
                double r_ji = Math.abs(pearson(xj, ri));            // x_j predicts r_i
                double r_ij = Math.abs(pearson(xi, resid.get(j)));  // x_i predicts r_j
                double score = Math.min(r_ji, r_ij);
                if (!Double.isFinite(score) || score < minCorr) continue;

                cands.add(new Cand(Edges.directedEdge(jNode, iNode), score));
            }
        }

        cands.sort((a, b) -> Double.compare(b.score, a.score));
        List<Edge> out = new ArrayList<>(Math.min(cands.size(), candidatePoolSize));
        for (Cand c : cands) out.add(c.e);
        return out;
    }

    // ======= small EJML helpers =======

    /** Extracts all rows and the given subset of columns (arbitrary indices). */
    private static SimpleMatrix extractColumns(SimpleMatrix X, int[] cols) {
        int n = X.numRows();
        int k = cols.length;
        SimpleMatrix out = new SimpleMatrix(n, k);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < k; j++) {
                out.set(i, j, X.get(i, cols[j]));
            }
        }
        return out;
    }

    /** Node-safe matrix fill (preserves Graph node ↔ DataSet column alignment). */
    private static SimpleMatrix toMatrix(DataSet data, List<Node> nodes) {
        int n = data.getNumRows();
        int p = nodes.size();
        SimpleMatrix X = new SimpleMatrix(n, p);
        for (int j = 0; j < p; j++) {
            Node v = nodes.get(j);
            for (int i = 0; i < n; i++) {
                X.set(i, j, data.getDouble(i, j));
            }
        }
        return X;
    }

    private static SimpleMatrix centerColumns(SimpleMatrix X) {
        int n = X.numRows(), p = X.numCols();
        SimpleMatrix Y = X.copy();
        for (int j = 0; j < p; j++) {
            double m = 0.0;
            for (int i = 0; i < n; i++) m += Y.get(i, j);
            m /= n;
            for (int i = 0; i < n; i++) Y.set(i, j, Y.get(i, j) - m);
        }
        return Y;
    }

    private static SimpleMatrix lsSolve(SimpleMatrix A, SimpleMatrix y) {
        // beta = (A^T A + λI)^(-1) A^T y   (small ridge for safety)
        double lambda = 1e-8;
        SimpleMatrix At = A.transpose();
        SimpleMatrix AtA = At.mult(A);
        for (int d = 0; d < AtA.numRows(); d++) {
            AtA.set(d, d, AtA.get(d, d) + lambda);
        }
        return AtA.solve(At.mult(y));
    }

    private static double pearson(SimpleMatrix x, SimpleMatrix y) {
        int n = x.getNumElements();
        double num = 0, sx2 = 0, sy2 = 0;
        for (int i = 0; i < n; i++) {
            double xi = x.get(i);
            double yi = y.get(i);
            num += xi * yi;
            sx2 += xi * xi;
            sy2 += yi * yi;
        }
        double den = Math.sqrt(sx2) * Math.sqrt(sy2) + 1e-12;
        return num / den;
    }
}
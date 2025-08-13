package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * BasisFunctionRankBic: BIC-style local score after Legendre embedding, using
 * cached RCCA singular values from RankTests. We scan ranks r = 0..m and pick
 * the best fit − penalty, where:
 *
 *   fit(r) = -n * sum_{i=0}^{r-1} log(1 - rho_i^2)
 *   pen(r) = c * [ r * (p + q - r) ] * log(n)
 *
 * with p = |X_block|, q = |Y_block|, m = min(p, q).
 *
 * For scalar Y (q=1) and r in {0,1}, this collapses to SEM-BIC.
 */
public class BasisFunctionRankBicScore implements Score {
    // --- Data / bookkeeping ---
    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<Node, Integer> nodeIndex;
    private final SimpleMatrix Sphi;                      // covariance of embedded data
    private final int n;                                  // sample size

    // Precomputed embedded column arrays (avoid per-call boxing/copies)
    private final int[][] blockAllCols;   // per original var -> all embedded cols
    private final int[][] blockFirstOnly; // per original var -> first embedded col (len 1) or empty

    // --- Knobs ---
    private double penaltyDiscount = 1.0;   // c
    private double ridge = 1e-8;            // regLambda passed to RankTests
    private boolean doOneEquationOnly = false; // use only first basis column of Y
    private double condThreshold = 1e10;    // conditioning guard inside RankTests hybrid path

    // --- Caches ---
    private static final int SCORE_CACHE_MAX = 100_000;
    private static final int XBLOCK_CACHE_MAX = 50_000;

    /** LRU cache for full local scores per (y, parents, knobs). */
    private final Map<FamilyKey, Double> scoreCache =
            new LinkedHashMap<>(2048, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<FamilyKey, Double> e) {
                    return size() > SCORE_CACHE_MAX;
                }
            };

    /** LRU cache for concatenated X-block embedded columns per parent set. */
    private final Map<ParentsKey, int[]> xblockCache =
            new LinkedHashMap<>(2048, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<ParentsKey, int[]> e) {
                    return size() > XBLOCK_CACHE_MAX;
                }
            };

    public BasisFunctionRankBicScore(DataSet dataSet, int truncationLimit) {
        this.dataSet = Objects.requireNonNull(dataSet);

        // index original variables
        this.variables = new ArrayList<>(dataSet.getVariables());
        this.nodeIndex = new HashMap<>();
        for (int j = 0; j < variables.size(); j++) nodeIndex.put(variables.get(j), j);

        this.n = dataSet.getNumRows();

        // Legendre embedding (basisType=1, basisScale=1 as before)
        Embedding.EmbeddedData ed = Embedding.getEmbeddedData(dataSet, truncationLimit, /*basisType*/1, /*basisScale*/1);

        // covariance in embedded space
        this.Sphi = DataUtils.cov(ed.embeddedData().getDoubleData().getSimpleMatrix());

        // Precompute embedded column arrays for each original variable
        int V = variables.size();
        this.blockAllCols   = new int[V][];
        this.blockFirstOnly = new int[V][];
        for (int v = 0; v < V; v++) {
            List<Integer> cols = ed.embedding().get(v);
            if (cols == null || cols.isEmpty()) {
                this.blockAllCols[v]   = new int[0];
                this.blockFirstOnly[v] = new int[0];
            } else {
                int[] all = new int[cols.size()];
                for (int k = 0; k < cols.size(); k++) all[k] = cols.get(k);
                this.blockAllCols[v] = all;
                this.blockFirstOnly[v] = new int[] { all[0] };
            }
        }
    }

    // --- Public API (mirrors SemBIC-style usage) ---

    public double localScore(int i, int... parents) {
        Node y = variables.get(i);
        List<Node> _parents = new ArrayList<>();
        for (int parent : parents) _parents.add(variables.get(parent));
        return localScore(y, _parents);
    }

    /**
     * Local BF-RankBIC score for Y given parents, reusing RCCA results from RankTests.
     * Adds caching at the family level and for the concatenated parent X-block.
     */
    public double localScore(Node y, List<Node> parents) {
        int yi = idx(y);
        if (parents.isEmpty()) return 0.0; // null baseline

        // Build sorted parent original indices for stable keys
        int[] parentIdx = new int[parents.size()];
        for (int t = 0; t < parents.size(); t++) parentIdx[t] = idx(parents.get(t));
        Arrays.sort(parentIdx);

        FamilyKey fkey = new FamilyKey(yi, parentIdx, doOneEquationOnly, ridge, condThreshold, penaltyDiscount);
        Double cached = scoreCache.get(fkey);
        if (cached != null) return cached;

        int[] Yblock = blockFor(yi, /*firstOnly*/ doOneEquationOnly);

        // Xblock: try cache first
        ParentsKey pkey = new ParentsKey(parentIdx);
        int[] Xblock = xblockCache.get(pkey);
        if (Xblock == null) {
            Xblock = concatBlocksFromSortedParentIdx(parentIdx);
            xblockCache.put(pkey, Xblock);
        }

        // Fetch RCCA results (cached inside RankTests) for (X, Y) in embedded space.
        RankTests.RccaEntry ent = RankTests.getRccaEntry(Sphi, Xblock, Yblock, /*regLambda*/ ridge, condThreshold);
        if (ent == null) {
            // Whitening failed or numerics too nasty; return aggressive penalty to avoid picking this family.
            scoreCache.put(fkey, Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }

        int p = Xblock.length, q = Yblock.length, m = Math.min(p, q);

        // Fit(r) = -n * sum_{i=0}^{r-1} log(1 - s_i^2)
        // Using suffix logs stored as sum_{i=r}^{m-1} log(1 - s_i^2):
        // sum_{i=0}^{r-1} = suffix[0] - suffix[r]
        double best = Double.NEGATIVE_INFINITY;
        double suffix0 = ent.suffixLogs[0];

        // Slight heuristic: check r=m first; if it's competitive, early exit can help in practice.
        int rBest = 0;
        {
            double sumLogsTopR = suffix0 - ent.suffixLogs[m];
            double fit = -n * sumLogsTopR;
            int k = m * (p + q - m);
            double pen = penaltyDiscount * k * Math.log(n);
            best = fit - pen;
            rBest = m;
        }

        for (int r = 0; r < m; r++) {
            double sumLogsTopR = suffix0 - ent.suffixLogs[r];
            double fit = -n * sumLogsTopR;
            int k = r * (p + q - r);
            double pen = penaltyDiscount * k * Math.log(n);
            double sc = fit - pen;
            if (sc > best) { best = sc; rBest = r; }
        }

        scoreCache.put(fkey, best);
        return best;
    }

    /**
     * Convenience delta score helper (add/remove a single parent).
     */
    public double localScoreDelta(Node y, List<Node> oldParents, Node changedParent, boolean adding) {
        List<Node> newParents = new ArrayList<>(oldParents);
        if (adding) newParents.add(changedParent);
        else newParents.remove(changedParent);
        return localScore(y, newParents) - localScore(y, oldParents);
    }

    // --- Settings ---
    public void setPenaltyDiscount(double c) {
        this.penaltyDiscount = c;
        scoreCache.clear(); // penalty affects scores
    }
    public void setRidge(double ridge) {
        this.ridge = ridge;
        scoreCache.clear(); // ridge affects rhos
        // RankTests has its own LRU keyed by regLambda; no need to clear it here.
    }
    public void setDoOneEquationOnly(boolean v) {
        this.doOneEquationOnly = v;
        scoreCache.clear(); // Y-block changes
    }
    /** Condition-number guard used by RankTests’ path; raise to be more permissive. */
    public void setCondThreshold(double v) {
        this.condThreshold = v;
        scoreCache.clear(); // may change acceptance/fallback
    }

    // --- Internals ---
    private int idx(Node v) {
        Integer i = nodeIndex.get(v);
        if (i == null) throw new IllegalArgumentException("Unknown node " + v);
        return i;
    }

    /** Embedded columns for an original variable (precomputed). */
    private int[] blockFor(int originalCol, boolean firstOnly) {
        return firstOnly ? blockFirstOnly[originalCol] : blockAllCols[originalCol];
    }

    /** Concatenate embedded columns for all parents (parents given as sorted original indices). */
    private int[] concatBlocksFromSortedParentIdx(int[] sortedParents) {
        int total = 0;
        for (int p : sortedParents) total += blockAllCols[p].length;
        int[] out = new int[total];
        int k = 0;
        for (int p : sortedParents) {
            int[] cols = blockAllCols[p];
            System.arraycopy(cols, 0, out, k, cols.length);
            k += cols.length;
        }
        return out;
    }

    /** Concatenate embedded columns for all parents (original Node list) — kept for completeness, unused now. */
    @SuppressWarnings("unused")
    private int[] concatBlocks(List<Node> parents) {
        int[] parentIdx = new int[parents.size()];
        for (int t = 0; t < parents.size(); t++) parentIdx[t] = idx(parents.get(t));
        Arrays.sort(parentIdx);
        return concatBlocksFromSortedParentIdx(parentIdx);
    }

    // --- Score interface ---
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(variables.get(y), appendNodes(z, x)) - localScore(variables.get(y), z);
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    // --- Helpers ---
    private List<Node> appendNodes(int[] parents, int x) {
        List<Node> list = new ArrayList<>(parents.length + 1);
        for (int p : parents) list.add(variables.get(p));
        list.add(variables.get(x));
        return list;
    }

    private double localScore(Node y, int[] parents) {
        List<Node> ps = new ArrayList<>(parents.length);
        for (int p : parents) ps.add(variables.get(p));
        return localScore(y, ps);
    }

    private double localScore(Node y, int[] zParents, int x) {
        List<Node> ps = new ArrayList<>(zParents.length + 1);
        for (int p : zParents) ps.add(variables.get(p));
        ps.add(variables.get(x));
        return localScore(y, ps);
    }

    // ----- Key types for caches -----

    /** Key for the per-family score cache. Includes knobs that affect the score. */
    private static final class FamilyKey {
        final int y;
        final int[] parents; // sorted original indices
        final boolean oneEq;
        final long ridgeBits;
        final long condBits;
        final long penBits;
        private final int hash;

        FamilyKey(int y, int[] parents, boolean oneEq, double ridge, double cond, double pen) {
            this.y = y;
            this.parents = parents.clone();
            this.oneEq = oneEq;
            this.ridgeBits = quantize(ridge);
            this.condBits  = quantize(cond);
            this.penBits   = quantize(pen);
            this.hash = computeHash();
        }

        private static long quantize(double x) {
            // quantize to ~1e-12 relative resolution to keep keys stable
            return Double.doubleToLongBits(Math.rint(x * 1e12) / 1e12);
        }

        private int computeHash() {
            int h = 1;
            h = 31 * h + y;
            h = 31 * h + Arrays.hashCode(parents);
            h = 31 * h + (oneEq ? 1 : 0);
            h = 31 * h + Long.hashCode(ridgeBits);
            h = 31 * h + Long.hashCode(condBits);
            h = 31 * h + Long.hashCode(penBits);
            return h;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof FamilyKey fk)) return false;
            return y == fk.y
                   && oneEq == fk.oneEq
                   && ridgeBits == fk.ridgeBits
                   && condBits == fk.condBits
                   && penBits == fk.penBits
                   && Arrays.equals(parents, fk.parents);
        }

        @Override public int hashCode() { return hash; }
    }

    /** Key for the per-parent-set X-block cache (depends only on parent original indices). */
    private static final class ParentsKey {
        final int[] parents; // sorted original indices
        private final int hash;

        ParentsKey(int[] parents) {
            this.parents = parents.clone();
            this.hash = Arrays.hashCode(this.parents);
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof ParentsKey pk)) return false;
            return Arrays.equals(parents, pk.parents);
        }

        @Override public int hashCode() { return hash; }
    }
}
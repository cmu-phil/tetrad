package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * BlocksBicScore: BIC-style local score over block representations using cached RCCA singular values from RankTests.
 *
 * fit(r) = -n * sum_{i=0}^{r-1} log(1 - rho_i^2)
 * pen(r) = c * [ r * (p + q - r) ] * log(n)
 *
 * where p = |X_block|, q = |Y_block|, and r in {0..min(p,q)}.
 *
 * CONTRACT:
 * - 'blocks' maps each BLOCK index b (0..B-1) to the list of embedded column indices in THIS dataset for that block.
 * - 'blockVariables' is exactly the list of Nodes to score over, one per block, same order (size B).
 * - getVariables() returns exactly 'blockVariables'.
 */
public class BlocksBicScore implements Score {
    // --- Caches ---
    private static final int SCORE_CACHE_MAX = 100_000;
    private static final int XBLOCK_CACHE_MAX = 50_000;

    // --- Data / bookkeeping ---
    private final DataSet dataSet;
    private final List<Node> variables;          // block-level variables, size == blocks.size()
    private final Map<Node, Integer> nodeIndex;  // block node -> block index
    private final SimpleMatrix Sphi;             // covariance of embedded data
    private final int n;                         // sample size

    // Precomputed embedded column arrays (avoid per-call boxing/copies)
    private final int[][] blockAllCols;          // per block -> all embedded cols

    /**
     * LRU cache for full local scores per (y, parents, knobs).
     * Not thread-safe; wrap externally if running multi-threaded.
     */
    private final Map<FamilyKey, Double> scoreCache =
            new LinkedHashMap<>(2048, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<FamilyKey, Double> e) {
                    return size() > SCORE_CACHE_MAX;
                }
            };

    /**
     * LRU cache for concatenated X-block embedded columns per parent set.
     */
    private final Map<ParentsKey, int[]> xblockCache =
            new LinkedHashMap<>(2048, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ParentsKey, int[]> e) {
                    return size() > XBLOCK_CACHE_MAX;
                }
            };

    // --- Knobs ---
    private double penaltyDiscount = 1.0;   // c
    private double ridge = 1e-8;            // regLambda passed to RankTests
    private double condThreshold = 1e10;    // conditioning guard inside RankTests hybrid path

    /**
     * @param dataSet        dataset whose columns the 'blocks' indices refer to
     * @param blocks         for each block b (0..B-1), the list of embedded column indices composing that block
     * @param blockVariables the exact variables to expose to the scorer; size must equal blocks.size()
     */
    public BlocksBicScore(DataSet dataSet, List<List<Integer>> blocks, List<Node> blockVariables) {
        this.dataSet = Objects.requireNonNull(dataSet, "dataSet == null");
        Objects.requireNonNull(blocks, "blocks == null");
        Objects.requireNonNull(blockVariables, "blockVariables == null");
        int B = blocks.size();
        if (blockVariables.size() != B) {
            throw new IllegalArgumentException("blockVariables.size() (" + blockVariables.size() +
                                               ") != blocks.size() (" + B + ")");
        }

        // block-level variables & index
        this.variables = new ArrayList<>(blockVariables);
        this.nodeIndex = new HashMap<>();
        for (int j = 0; j < variables.size(); j++) {
            Node v = variables.get(j);
            if (v == null) throw new IllegalArgumentException("blockVariables[" + j + "] is null");
            if (nodeIndex.put(v, j) != null) {
                throw new IllegalArgumentException("Duplicate Node in blockVariables: " + v.getName());
            }
        }

        this.n = dataSet.getNumRows();
        this.Sphi = DataUtils.cov(dataSet.getDoubleData().getSimpleMatrix());

        // Precompute embedded column arrays for each block
        int D = dataSet.getNumColumns();
        this.blockAllCols = new int[B][];
        for (int b = 0; b < B; b++) {
            List<Integer> cols = blocks.get(b);
            if (cols == null || cols.isEmpty()) {
                this.blockAllCols[b] = new int[0];
            } else {
                int[] all = new int[cols.size()];
                for (int k = 0; k < cols.size(); k++) {
                    int col = cols.get(k);
                    if (col < 0 || col >= D) {
                        throw new IllegalArgumentException(
                                "Block " + b + " references column " + col + " outside dataset width " + D);
                    }
                    all[k] = col;
                }
                this.blockAllCols[b] = all;
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
     * Local block Rank-BIC score for Y given parents, reusing RCCA results from RankTests.
     * Adds caching at the family level and for the concatenated parent X-block.
     */
    public double localScore(Node y, List<Node> parents) {
        int yi = idx(y);

        // Null baseline (no parents)
        if (parents == null || parents.isEmpty()) return 0.0;

        // Build sorted parent indices for stable keys
        int[] parentIdx = new int[parents.size()];
        for (int t = 0; t < parents.size(); t++) parentIdx[t] = idx(parents.get(t));
        Arrays.sort(parentIdx);

        FamilyKey fkey = new FamilyKey(yi, parentIdx, ridge, condThreshold, penaltyDiscount);
        Double cached = scoreCache.get(fkey);
        if (cached != null) return cached;

        // Y block
        int[] Yblock = blockFor(yi);
        if (Yblock.length == 0) {
            scoreCache.put(fkey, Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }

        // X block (concat of parents)
        ParentsKey pkey = new ParentsKey(parentIdx);
        int[] Xblock = xblockCache.get(pkey);
        if (Xblock == null) {
            Xblock = concatBlocksFromSortedParentIdx(parentIdx);
            xblockCache.put(pkey, Xblock);
        }
        if (Xblock.length == 0) {
            scoreCache.put(fkey, Double.NEGATIVE_INFINITY);  // FIX: was +INF
            return Double.NEGATIVE_INFINITY;
        }

        // RCCA entry (cached inside RankTests)
        RankTests.RccaEntry ent = RankTests.getRccaEntry(Sphi, Xblock, Yblock, /*regLambda*/ ridge);
        if (ent == null) {
            scoreCache.put(fkey, Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }

        int p = Xblock.length, q = Yblock.length, m = Math.min(p, q);
        if (m <= 0) {
            scoreCache.put(fkey, Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }

        double[] suffix = ent.suffixLogs;
        if (suffix == null || suffix.length < m + 1) {
            scoreCache.put(fkey, Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }

        // Fit/penalty sweep over r
        double best = Double.NEGATIVE_INFINITY;
        double suffix0 = suffix[0];

        // Heuristic: evaluate r = m once
        {
            double sumLogsTopR = suffix0 - suffix[m];
            double fit = -n * sumLogsTopR;
            int k = m * (p + q - m);
            double pen = penaltyDiscount * k * Math.log(n);
            double sc = fit - pen;
            if (!Double.isNaN(sc) && !Double.isInfinite(sc)) {
                best = sc;
            }
        }

        // Scan r = 1..m-1  (FIX: skip r=0 to avoid tie-with-null adding edges)
        for (int r = 1; r < m; r++) {
            double sumLogsTopR = suffix0 - suffix[r];
            double fit = -n * sumLogsTopR;
            int k = r * (p + q - r);
            double pen = penaltyDiscount * k * Math.log(n);
            double sc = fit - pen;
            if (Double.isNaN(sc) || Double.isInfinite(sc)) continue;
            if (sc > best) best = sc;
        }

        // Optional nudge to avoid null-equivalent ties being selected upstream
        if (best <= 0.0) best = -1e-12;

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
        // RankTests has its own LRU keyed by regLambda.
    }

    /**
     * Condition-number guard used by RankTests’ path; raise to be more permissive.
     */
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

    /** Embedded columns for a block (precomputed). */
    private int[] blockFor(int blockIndex) {
        return blockAllCols[blockIndex];
    }

    /** Concatenate embedded columns for all parents (parents given as sorted block indices). */
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

    /** Concatenate embedded columns for all parents (block Node list) — unused helper. */
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
        final int[] parents; // sorted block indices
        final long ridgeBits;
        final long condBits;
        final long penBits;
        private final int hash;

        FamilyKey(int y, int[] parents, double ridge, double cond, double pen) {
            this.y = y;
            this.parents = parents.clone();
            this.ridgeBits = quantize(ridge);
            this.condBits = quantize(cond);
            this.penBits = quantize(pen);
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
            h = 31 * h + Long.hashCode(ridgeBits);
            h = 31 * h + Long.hashCode(condBits);
            h = 31 * h + Long.hashCode(penBits);
            return h;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FamilyKey fk)) return false;
            return y == fk.y
                   && ridgeBits == fk.ridgeBits
                   && condBits == fk.condBits
                   && penBits == fk.penBits
                   && Arrays.equals(parents, fk.parents);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    /** Key for the per-parent-set X-block cache (depends only on parent block indices). */
    private static final class ParentsKey {
        final int[] parents; // sorted block indices
        private final int hash;

        ParentsKey(int[] parents) {
            this.parents = parents.clone();
            this.hash = Arrays.hashCode(this.parents);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ParentsKey pk)) return false;
            return Arrays.equals(parents, pk.parents);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
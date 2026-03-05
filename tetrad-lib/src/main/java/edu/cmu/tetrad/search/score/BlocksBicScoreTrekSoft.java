package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * BlocksBicScoreTrekSoft:
 * BlocksBicScore with an additional soft regularizer that prefers the trek-implied rank
 * r* = sum_{Z in Pa(Y)} rank(Z) (from BlockSpec.ranks()).
 *
 * score(r) = [fit(r) - c*k(r)*log(n)] - trekRankPenalty * (r - r*)^2  (+ optional EBIC).
 *
 * This keeps the empirical strength of BlocksBicScore (rank sweep on Xblock vs Yblock),
 * while injecting trek-structure information as a soft preference rather than a hard constraint.
 */
public class BlocksBicScoreTrekSoft implements Score, BlockScore, EffectiveSampleSizeSettable {

    private static final int SCORE_CACHE_MAX = 100_000;
    private static final int XBLOCK_CACHE_MAX = 50_000;

    private final List<Node> variables;
    private final Map<Node, Integer> nodeIndex;
    private final SimpleMatrix Sphi;
    private final int n;

    private final int[][] blockAllCols;
    private final int totalEmbeddedCols;

    private final Map<FamilyKey, Double> scoreCache =
            new LinkedHashMap<>(2048, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<FamilyKey, Double> e) {
                    return size() > SCORE_CACHE_MAX;
                }
            };

    private final Map<ParentsKey, int[]> xblockCache =
            new LinkedHashMap<>(2048, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ParentsKey, int[]> e) {
                    return size() > XBLOCK_CACHE_MAX;
                }
            };

    private final BlockSpec blockSpec;

    // --- Knobs (same as BlocksBicScore) ---
    private double penaltyDiscount = 1.0;
    private double ridge = 1e-8;
    private double ebicGamma = 0.0;
    private int nEff;

    // --- New knob ---
    private double trekRankPenalty = 3.0; // lambda; 0 disables

    public BlocksBicScoreTrekSoft(BlockSpec blockSpec) {
        this.blockSpec = Objects.requireNonNull(blockSpec, "blockspec == null");

        int B = blockSpec.blocks().size();

        this.variables = new ArrayList<>(blockSpec.blockVariables());
        this.nodeIndex = new HashMap<>();
        for (int j = 0; j < variables.size(); j++) {
            Node v = variables.get(j);

            if (v == null) throw new IllegalArgumentException("blockVariables[" + j + "] is null");
            if (nodeIndex.put(v, j) != null) {
                throw new IllegalArgumentException("Duplicate Node in blockVariables: " + v.getName());
            }
        }

        this.n = blockSpec.dataSet().getNumRows();
        setEffectiveSampleSize(-1);

        this.Sphi = new CorrelationMatrix(blockSpec.dataSet()).getMatrix().getSimpleMatrix();

        int D = blockSpec.dataSet().getNumColumns();
        this.blockAllCols = new int[B][];
        int totalCols = 0;
        for (int b = 0; b < B; b++) {
            List<Integer> cols = blockSpec.blocks().get(b);
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
            totalCols += this.blockAllCols[b].length;
        }
        this.totalEmbeddedCols = totalCols;
    }

    @Override
    public double localScore(int i, int... parents) {
        Node y = variables.get(i);
        List<Node> _parents = new ArrayList<>();
        for (int parent : parents) _parents.add(variables.get(parent));
        return localScore(y, _parents);
    }

    public double localScore(Node y, List<Node> parents) {
        final int yi = idx(y);

        // --- Enforce "rank-0 blocks are isolated" semantics ---
        Integer yRkObj = blockSpec.ranks().get(yi);
        if (yRkObj == null) {
            throw new IllegalStateException("Missing rank for block index " + yi + " (node=" + y + ")");
        }
        final int yRank = yRkObj;
        if (yRank == 0) {
            // Rank-0 blocks: no parents allowed (and they shouldn't help others either; handled below for parents).
            return (parents == null || parents.isEmpty()) ? 0.0 : Double.NEGATIVE_INFINITY;
        }

        // Null baseline (no parents)
        if (parents == null || parents.isEmpty()) return 0.0;

        // --- Filter out rank-0 parents (they must not create edges) ---
        int[] parentIdxTmp = new int[parents.size()];
        int mParents = 0;
        for (Node pNode : parents) {
            int pi = idx(pNode);
            Integer prk = blockSpec.ranks().get(pi);
            if (prk == null) {
                throw new IllegalStateException("Missing rank for block index " + pi + " (parent node=" + pNode + ")");
            }
            if (prk == 0) continue; // ignore rank-0 parent blocks entirely
            parentIdxTmp[mParents++] = pi;
        }

        // If all proposed parents were rank-0, treat as empty parent set.
        if (mParents == 0) return 0.0;

        int[] parentIdx = Arrays.copyOf(parentIdxTmp, mParents);
        Arrays.sort(parentIdx);

        // Cache key uses filtered parent set (critical!)
        FamilyKey fkey = new FamilyKey(yi, parentIdx, ridge, penaltyDiscount, ebicGamma, trekRankPenalty);
        Double cached = scoreCache.get(fkey);
        if (cached != null) return cached;

        // Y block
        int[] Yblock = blockFor(yi);
        if (Yblock.length == 0) {
            scoreCache.put(fkey, Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }

        // X block (concat of filtered parents)
        ParentsKey pkey = new ParentsKey(parentIdx);
        int[] Xblock = xblockCache.get(pkey);
        if (Xblock == null) {
            Xblock = concatBlocksFromSortedParentIdx(parentIdx);
            xblockCache.put(pkey, Xblock);
        }
        if (Xblock.length == 0) {
            scoreCache.put(fkey, Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }

        // RCCA entry (cached inside RankTests)
        RankTests.RccaEntry ent = RankTests.getRccaEntry(Sphi, Xblock, Yblock, ridge);
        if (ent == null || ent.suffixLogs == null) {
            scoreCache.put(fkey, Double.NEGATIVE_INFINITY);
            return Double.NEGATIVE_INFINITY;
        }

        int p = Xblock.length, q = Yblock.length;

        // Bartlett-style effective n for CCA LLR
        double nAdj = this.nEff - 1.0 - 0.5 * (p + q + 1.0);
        if (nAdj < 1.0) nAdj = 1.0;

        double[] suffix = ent.suffixLogs;
        int m = Math.min(Math.min(p, q), (int) nAdj - 1);
        m = Math.min(m, suffix.length - 1);
        if (m <= 0) {
            scoreCache.put(fkey, -1e-12);
            return -1e-12;
        }

        // Trek-implied rank target r*
        int rStar = 0;
        for (int pi : parentIdx) {
            Integer rk = blockSpec.ranks().get(pi);
            if (rk == null) throw new IllegalStateException("Missing rank for block index " + pi);
            rStar += rk;
        }
        if (rStar < 0) rStar = 0;

        double best = Double.NEGATIVE_INFINITY;
        double suffix0 = suffix[0];

        int Ppool = Math.max(totalEmbeddedCols - Yblock.length, 2);
        double logN = Math.log(Math.max(nAdj, 2.0));

        // Sweep r = 1..m
        for (int r = 1; r <= m; r++) {
            double sumLogsTopR = suffix0 - suffix[r];
            double fit = -nAdj * sumLogsTopR;

            int k = r * (p + q - r);

            double pen = penaltyDiscount * k * logN;
            if (ebicGamma > 0.0) {
                pen += 2.0 * ebicGamma * k * Math.log(Ppool);
            }

//            // Soft trek preference (FIXED: no stray "trekPen = 4")
//            double trekPen = 0.0;
//            if (trekRankPenalty > 0.0) {
//                double d = (double) r - (double) rStar;
//                trekPen = trekRankPenalty * d * d;
//            }

            double trekPen = 0.0;
            if (trekRankPenalty > 0.0) {
                double d = (double) r - (double) rStar;
                trekPen = trekRankPenalty * logN * d * d;  // <-- multiply by logN
            }

            double sc = fit - pen - trekPen;
            if (Double.isNaN(sc) || Double.isInfinite(sc)) continue;
            if (sc > best) best = sc;
        }

        if (best <= 0.0) best = -1e-12;

        scoreCache.put(fkey, best);
        return best;
    }

    @Override
    public double localScoreDiff(int x, int y, int[] z) {

        return localScore(variables.get(y), appendNodes(z, x)) - localScore(variables.get(y), z);
    }

    private double localScore(Node y, int[] parents) {
        List<Node> ps = new ArrayList<>(parents.length);
        for (int p : parents) ps.add(variables.get(p));
        return localScore(y, ps);
    }

    // --- knobs ---
    public void setPenaltyDiscount(double c) {
        this.penaltyDiscount = c;
        this.trekRankPenalty = c;
        scoreCache.clear();
    }
    public void setRidge(double ridge) { this.ridge = ridge; scoreCache.clear(); }
    public void setEbicGamma(double gamma) { this.ebicGamma = gamma; scoreCache.clear(); }

//    /** New: strength of the trek rank preference. 0 disables. */
//    public void setTrekRankPenalty(double lambda) { this.trekRankPenalty = Math.max(0.0, lambda); scoreCache.clear(); }

    // --- Score / BlockScore / EffectiveSampleSizeSettable ---
    @Override public List<Node> getVariables() { return new ArrayList<>(variables); }
    @Override public int getSampleSize() { return blockSpec.dataSet().getNumRows(); }
    @Override public BlockSpec getBlockSpec() { return blockSpec; }

    @Override public int getEffectiveSampleSize() { return nEff; }
    @Override public void setEffectiveSampleSize(int nEff) {
        this.nEff = nEff < 0 ? this.n : nEff;
        scoreCache.clear();
        xblockCache.clear();
    }

    // --- internals ---
    private int idx(Node v) {
        Integer i = nodeIndex.get(v);
        if (i == null) throw new IllegalArgumentException("Unknown node " + v);
        return i;
    }

    private int[] blockFor(int blockIndex) { return blockAllCols[blockIndex]; }

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

    private List<Node> appendNodes(int[] parents, int x) {
        List<Node> list = new ArrayList<>(parents.length + 1);
        for (int p : parents) list.add(variables.get(p));
        list.add(variables.get(x));
        return list;
    }

    // --- cache keys ---
    private static final class FamilyKey {
        final int y;
        final int[] parents;
        final long ridgeBits, penBits, ebicBits, trekBits;
        private final int hash;

        FamilyKey(int y, int[] parents, double ridge, double pen, double ebic, double trek) {
            this.y = y;
            this.parents = parents.clone();
            this.ridgeBits = quantize(ridge);
            this.penBits = quantize(pen);
            this.ebicBits = quantize(ebic);
            this.trekBits = quantize(trek);
            int h = 1;
            h = 31 * h + y;
            h = 31 * h + Arrays.hashCode(this.parents);
            h = 31 * h + Long.hashCode(ridgeBits);
            h = 31 * h + Long.hashCode(penBits);
            h = 31 * h + Long.hashCode(ebicBits);
            h = 31 * h + Long.hashCode(trekBits);
            this.hash = h;
        }

        private static long quantize(double x) {
            return Double.doubleToLongBits(Math.rint(x * 1e12) / 1e12);
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof FamilyKey fk)) return false;
            return y == fk.y
                    && ridgeBits == fk.ridgeBits
                    && penBits == fk.penBits
                    && ebicBits == fk.ebicBits
                    && trekBits == fk.trekBits
                    && Arrays.equals(parents, fk.parents);
        }

        @Override public int hashCode() { return hash; }
    }

    private static final class ParentsKey {
        final int[] parents;
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
///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.data.missing.MissingValueSupport;
import edu.cmu.tetrad.data.missing.TestwiseCovariance;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.TMath;
import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Block-level CI test using Wilks-rank. Robust to |Y| &lt; |X| by padding Y from the leftover observed pool and, if
 * needed, by subsetting X to |Y|. Thread-safe LRU caches preserved.
 */
public class IndTestBlocksWilkes implements IndependenceTest, BlockTest, EffectiveSampleSizeSettable {

    // ---- Cache sizes (tune) ----
    private static final int PV_CACHE_MAX = 400_000;   // (x,y,Z,n,alpha) -> p
    private static final int RANK_CACHE_MAX = 400_000; // (x,y,Z,n,alpha) -> rank
    private static final int ZBLOCK_CACHE_MAX = 150_000; // Z -> concatenated embedded cols

    private final List<Node> variables;
    private final Map<Node, Integer> nodeHash;
    private final SimpleMatrix S; // correlation (or covariance); null under test-wise deletion
    private final TestwiseCovariance testwise; // per-test covariance over complete rows; null otherwise
    private final int n;

    // Block -> embedded/expanded column indices
    private final int[][] allCols;

    // Universe of observed column indices (0..D-1) for padding
    private final BitSet universeBits;

    // Thread-safe LRUs
    private final LruMap<PKey, Integer> rankCache = new LruMap<>(RANK_CACHE_MAX);
    private final LruMap<PKey, Double> pvalCache = new LruMap<>(PV_CACHE_MAX);
    private final LruMap<ZKey, int[]> zblockCache = new LruMap<>(ZBLOCK_CACHE_MAX);
    private final BlockSpec blockSpec;

    // knobs
    private double alpha = 0.01;
    private boolean verbose = false;
    private int effN;

    /**
     * Constructs an instance of IndTestBlocks using the provided block specification. This class is used for conducting
     * independence tests based on a dataset's block structure.
     *
     * @param blockSpec the block specification containing metadata about blocks, variables, and the associated dataset.
     *                  Must not be null. Throws IllegalArgumentException if blockSpec is null or invalid.
     */
    public IndTestBlocksWilkes(BlockSpec blockSpec) {
        this(blockSpec, null);
    }

    /**
     * Constructs the test with an explicit missing-data specification for the block data set. On data with missing
     * values the supported policies are LISTWISE and TESTWISE; under TESTWISE each test's correlation matrix is
     * computed over the rows complete on every embedded column the test uses (x, y, and z blocks), with the count
     * of those rows as its sample size (an explicit effective sample size acts as a deflation factor). A null spec
     * on missing data throws.
     *
     * @param blockSpec The block specification.
     * @param spec      The missing-data specification, or null.
     */
    public IndTestBlocksWilkes(BlockSpec blockSpec, MissingDataSpec spec) {
        if (blockSpec == null) throw new IllegalArgumentException("blockSpec == null");

        DataSet resolved = MissingDataUtils.resolveDeletionPolicy(blockSpec.dataSet(), spec, "IndTestBlocksWilkes");
        if (resolved != blockSpec.dataSet()) {
            blockSpec = new BlockSpec(resolved, blockSpec.blocks(), blockSpec.blockVariables(), blockSpec.ranks());
        }

        this.blockSpec = blockSpec;

        final int B = blockSpec.blocks().size();

        this.variables = new ArrayList<>(blockSpec.blockVariables());
        Map<Node, Integer> nodesHash = new HashMap<>();
        for (int j = 0; j < this.variables.size(); j++) {
            Node v = this.variables.get(j);
            if (v == null) throw new IllegalArgumentException("blockVariables[" + j + "] is null");
            if (nodesHash.put(v, j) != null) {
                throw new IllegalArgumentException("Duplicate Node in blockVariables: " + v.getName());
            }
        }
        this.nodeHash = nodesHash;

        this.n = blockSpec.dataSet().getNumRows();
        setEffectiveSampleSize(-1);

        if (blockSpec.dataSet().existsMissingValue()) {
            this.testwise = new TestwiseCovariance(blockSpec.dataSet().getDoubleData());
            this.S = null;
        } else {
            this.testwise = null;
            this.S = new CorrelationMatrix(blockSpec.dataSet()).getMatrix().getSimpleMatrix();
        }
        // If you prefer covariance:
        // this.S = DataUtils.cov(dataSet.getDoubleData().getSimpleMatrix());

        final int D = blockSpec.dataSet().getNumColumns();
        this.allCols = new int[B][];
        for (int b = 0; b < B; b++) {
            List<Integer> cols = blockSpec.blocks().get(b);
            if (cols == null || cols.isEmpty()) {
                allCols[b] = new int[0];
            } else {
                int[] a = new int[cols.size()];
                for (int k = 0; k < cols.size(); k++) {
                    int col = cols.get(k);
                    if (col < 0 || col >= D) {
                        throw new IllegalArgumentException("Block " + b + " references column " + col
                                                           + " outside dataset width " + D);
                    }
                    a[k] = col;
                }
                allCols[b] = a;
            }
        }
        // Universe for padding
        this.universeBits = new BitSet(D);
        this.universeBits.set(0, D);
    }

    // === Public API ===

    private static int[] uniqSorted(int[] a) {
        if (a == null || a.length == 0) return new int[0];
        int[] b = Arrays.copyOf(a, a.length);
        Arrays.sort(b);
        int m = 1;
        for (int i = 1; i < b.length; i++) if (b[i] != b[m - 1]) b[m++] = b[i];
        return Arrays.copyOf(b, m);
    }

    /**
     * A \ B for sorted int arrays.
     */
    private static int[] minus(int[] A, int[] B) {
        if (A.length == 0) return A;
        if (B.length == 0) return Arrays.copyOf(A, A.length);
        int i = 0, j = 0, k = 0;
        int[] tmp = new int[A.length];
        while (i < A.length && j < B.length) {
            if (A[i] < B[j]) tmp[k++] = A[i++];
            else if (A[i] > B[j]) j++;
            else {
                i++;
                j++;
            }
        }
        while (i < A.length) tmp[k++] = A[i++];
        return Arrays.copyOf(tmp, k);
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        double pValue = getPValue(x, y, z);
        boolean independent = getEstimatedRank(x, y, z) == 0; // decision from Wilks-rank
        return new IndependenceResult(new IndependenceFact(x, y, z), independent, pValue, alpha - pValue);
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    @Override
    public DataModel getData() {
        return blockSpec.dataSet();
    }

    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    // === Core ===

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("Alpha must be in (0,1).");
        this.alpha = alpha;  // alpha participates in cache key
    }

    private int getEstimatedRank(Node x, Node y, Set<Node> z) {
        KeyParts kp = buildKeyParts(x, y, z);
        // Robustify (X,Y,Z) before using as cache key inputs
        XY pads = robustifyXY(kp.xCols, kp.yCols, kp.zCols);
        PKey key = new PKey(kp.a, kp.b, kp.zVars, effN, alpha);

        Integer cached = rankCache.get(key);
        if (cached != null) return cached;

        int rank;
        if (this.testwise == null) {
            rank = RankTests.estimateWilksRankConditioned(S, pads.xAdj, pads.yAdj, kp.zCols, effN, alpha);
        } else {
            Local loc = local(pads.xAdj, pads.yAdj, kp.zCols);
            rank = loc == null ? 0
                    : RankTests.estimateWilksRankConditioned(loc.S, loc.x, loc.y, loc.z, loc.effN, alpha);
        }
        if (rank < 0) rank = 0; // defensive
        rankCache.put(key, rank);
        return rank;
    }

    private double getPValue(Node x, Node y, Set<Node> z) {
        KeyParts kp = buildKeyParts(x, y, z);
        XY pads = robustifyXY(kp.xCols, kp.yCols, kp.zCols);
        PKey key = new PKey(kp.a, kp.b, kp.zVars, effN, alpha);

        Double cached = pvalCache.get(key);
        if (cached != null) return cached;

        if (pads.xAdj.length == 0 || pads.yAdj.length == 0) {
            pvalCache.put(key, 1.0);
            return 1.0;
        }

        // Use the effective sample size here, consistent with getEstimatedRank; the cache key
        // already includes effN. Prior to 2026-8 this used the raw row count n, so
        // setEffectiveSampleSize had no effect on reported p-values.
        double p;
        if (this.testwise == null) {
            p = RankTests.pValueIndepConditioned(S, pads.xAdj, pads.yAdj, kp.zCols, effN);
        } else {
            Local loc = local(pads.xAdj, pads.yAdj, kp.zCols);
            p = loc == null ? 1.0 : RankTests.pValueIndepConditioned(loc.S, loc.x, loc.y, loc.z, loc.effN);
        }
        if (Double.isNaN(p) || Double.isInfinite(p)) p = 1.0;
        p = TMath.max(0.0, TMath.min(1.0, p));

        pvalCache.put(key, p);
        return p;
    }

    /**
     * Ensure Y â Z (by construction), pad Y if needed, subset X if still |Y|<|X|.
     */
//    private XY robustifyXY(int[] xCols0, int[] yCols0, int[] zCols) {
//        // Deduplicate and sort for determinism
//        int[] X = uniqSorted(xCols0);
//        int[] Y = uniqSorted(yCols0);
//        int[] Z = uniqSorted(zCols);
//
//        // Remove any accidental overlaps with Z (paranoia; blocks are disjoint, but be safe).
//        if (Z.length > 0) {
//            Y = minus(Y, Z); // Y := Y \ Z
//            X = minus(X, Z); // X := X \ Z
//        }
//
//        // If |Y| < |X|, try to pad Y from the complement R = V \ (X âª Y âª Z)
//        // NEW knobs: robustness for |Y| < |X|
//        // try to grow Y from the complement pool
//        boolean padY = true;
//        if (padY && Y.length < X.length) {
//            BitSet pool = (BitSet) universeBits.clone();
//            for (int v : X) pool.clear(v);
//            for (int v : Y) pool.clear(v);
//            for (int v : Z) pool.clear(v);
//
//            int need = X.length - Y.length;
//            if (need > 0 && pool.cardinality() > 0) {
//                int take = TMath.min(need, pool.cardinality());
//                int[] Ypad = new int[Y.length + take];
//                System.arraycopy(Y, 0, Ypad, 0, Y.length);
//                int k = Y.length;
//                for (int i = pool.nextSetBit(0); i >= 0 && take > 0; i = pool.nextSetBit(i + 1)) {
//                    Ypad[k++] = i;
//                    take--;
//                }
//                Arrays.sort(Ypad);
//                Y = Ypad;
//                if (verbose && Y.length >= X.length) {
//                    System.out.printf("IndTestBlocks: padded Y to %d to meet X=%d%n", Y.length, X.length);
//                }
//            }
//        }
//
//        // If still |Y| < |X|, optionally subset X down to |Y|
//        // if still |Y| < |X|, shrink X to |Y|
//        boolean subsetXIfNeeded = true;
//        if (subsetXIfNeeded && Y.length > 0 && Y.length < X.length) {
//            // deterministic prefix (could also choose by leverage, but keep simple/fast)
//            X = Arrays.copyOf(X, Y.length);
//            if (verbose) {
//                System.out.printf("IndTestBlocks: subset X from %d to %d to match Y%n", X.length + (Y.length - X.length), Y.length);
//            }
//        }
//
//        return new XY(X, Y);
//    }

    /**
     * Clean up the X and Y blocks before CCA:
     *  1) Deduplicate and sort for determinism.
     *  2) Remove overlaps with Z.
     *  3) If either side becomes empty, return empty.
     * <p>
     * Changes from the pre-2026-8 implementation: the blocks are no longer equalized in size.
     * Previously, the larger of the two blocks was truncated to a deterministic prefix of the
     * smaller block's length. That step is unnecessary — both the Wilks rank estimator and the
     * Bartlett p-value handle rectangular blocks (min(p, q) canonical correlations, df = p * q
     * rank-aware) — and it silently discarded detection power. In particular, testing a
     * continuous variable (truncation-limit basis columns) against a binary variable (one
     * indicator column) reduced the continuous side to its first basis column, making the test
     * effectively linear on mixed data.
     */
    private XY robustifyXY(int[] xCols0, int[] yCols0, int[] zCols) {
        int[] X = uniqSorted(xCols0);
        int[] Y = uniqSorted(yCols0);
        int[] Z = uniqSorted(zCols);

        if (Z.length > 0) {
            X = minus(X, Z);
            Y = minus(Y, Z);
        }
        if (X.length == 0 || Y.length == 0) return new XY(new int[0], new int[0]);

        return new XY(X, Y);
    }

    /**
     * Retrieves the block specification associated with this instance of IndTestBlocks.
     *
     * @return the block specification containing metadata about blocks, variables, and the associated dataset.
     */
    @Override
    public BlockSpec getBlockSpec() {
        return blockSpec;
    }

    // Gather indices + build stable key parts
    private KeyParts buildKeyParts(Node x, Node y, Set<Node> z) {
        Integer xiVar = nodeHash.get(x);
        Integer yiVar = nodeHash.get(y);
        if (xiVar == null || yiVar == null) {
            throw new IllegalArgumentException("Unknown node(s): " + x + ", " + y);
        }

        int[] zVars = new int[z.size()];
        int t = 0;
        for (Node zn : z) {
            Integer idx = nodeHash.get(zn);
            if (idx == null) throw new IllegalArgumentException("Unknown conditioning node: " + zn);
            zVars[t++] = idx;
        }
        Arrays.sort(zVars);

        int a = TMath.min(xiVar, yiVar);
        int b = TMath.max(xiVar, yiVar);

        // Embedded columns
        int[] xCols = allCols[xiVar];
        int[] yCols = allCols[yiVar];

        // Concatenate Z embedded columns (cached)
        ZKey zkey = new ZKey(zVars);
        int[] zCols = zblockCache.get(zkey);
        if (zCols == null) {
            int total = 0;
            for (int zv : zVars) total += allCols[zv].length;
            zCols = new int[total];
            int k = 0;
            for (int zv : zVars) {
                int[] cols = allCols[zv];
                System.arraycopy(cols, 0, zCols, k, cols.length);
                k += cols.length;
            }
            zblockCache.put(zkey, zCols);
        }
        return new KeyParts(a, b, zVars, xCols, yCols, zCols);
    }

    @Override
    public int getEffectiveSampleSize() {
        return effN;
    }

    public void setEffectiveSampleSize(int effectiveSampleSize) {
        this.effN = effectiveSampleSize < 0 ? this.n : effectiveSampleSize;
    }

    private record XY(int[] xAdj, int[] yAdj) {
    }

    /**
     * Under test-wise deletion: the correlation matrix of the columns x, y, z over the rows complete on all of
     * them, with x, y, z remapped to positions in it, and the effective sample size scaled to those rows.
     */
    private record Local(SimpleMatrix S, int[] x, int[] y, int[] z, int effN) {
    }

    private Local local(int[] xCols, int[] yCols, int[] zCols) {
        int[] cols = new int[xCols.length + yCols.length + zCols.length];
        System.arraycopy(xCols, 0, cols, 0, xCols.length);
        System.arraycopy(yCols, 0, cols, xCols.length, yCols.length);
        System.arraycopy(zCols, 0, cols, xCols.length + yCols.length, zCols.length);

        TestwiseCovariance.Family fam = this.testwise.family(cols);
        int k = cols.length;
        if (fam.n() < k + 2) return null;

        SimpleMatrix corr = new SimpleMatrix(k, k);
        for (int i = 0; i < k; i++) {
            double vi = fam.cov().get(i, i);
            for (int j = 0; j < k; j++) {
                double vj = fam.cov().get(j, j);
                corr.set(i, j, fam.cov().get(i, j) / Math.sqrt(vi * vj));
            }
        }

        int[] x = new int[xCols.length];
        for (int i = 0; i < x.length; i++) x[i] = i;
        int[] y = new int[yCols.length];
        for (int i = 0; i < y.length; i++) y[i] = xCols.length + i;
        int[] z = new int[zCols.length];
        for (int i = 0; i < z.length; i++) z[i] = xCols.length + yCols.length + i;

        int localEffN = (int) Math.max(1, Math.round(fam.n() * (this.effN / (double) this.n)));
        return new Local(corr, x, y, z, localEffN);
    }

    // === Small, thread-safe LRU (access-order) ===
    private static final class LruMap<K, V> {
        private final ReentrantLock lock = new ReentrantLock();
        private final int maxSize;
        private final LinkedHashMap<K, V> map;

        LruMap(int maxSize) {
            this.maxSize = TMath.max(16, maxSize);
            this.map = new LinkedHashMap<>(1024, 0.75f, true);
        }

        V get(K k) {
            lock.lock();
            try {
                return map.get(k);
            } finally {
                lock.unlock();
            }
        }

        void put(K k, V v) {
            lock.lock();
            try {
                map.put(k, v);
                while (map.size() > maxSize) {
                    Iterator<Map.Entry<K, V>> it = map.entrySet().iterator();
                    if (it.hasNext()) {
                        it.next();
                        it.remove();
                    } else break;
                }
            } finally {
                lock.unlock();
            }
        }

        void clear() {
            lock.lock();
            try {
                map.clear();
            } finally {
                lock.unlock();
            }
        }
    }

    // === Key bits ===

    private record KeyParts(int a, int b, int[] zVars, int[] xCols, int[] yCols, int[] zCols) {
    }

    private static final class PKey {
        final int xVarMin, yVarMax;  // normalized so xVarMin <= yVarMax
        final int[] zVars;           // sorted
        final int n;                 // sample size
        final long alphaBits;        // quantized alpha
        private final int hash;

        PKey(int xVarMin, int yVarMax, int[] zVars, int n, double alpha) {
            this.xVarMin = xVarMin;
            this.yVarMax = yVarMax;
            this.zVars = zVars.clone();
            this.n = n;
            this.alphaBits = Double.doubleToLongBits(TMath.rint(alpha * 1e12) / 1e12);
            int h = 1;
            h = 31 * h + xVarMin;
            h = 31 * h + yVarMax;
            h = 31 * h + Arrays.hashCode(this.zVars);
            h = 31 * h + n;
            h = 31 * h + Long.hashCode(alphaBits);
            this.hash = h;
        }

        /**
         * Compares this object with the specified object for equality. Returns true if the specified object is also an
         * instance of PKey and if all defined fields in both objects are equal.
         *
         * @param o the object to compare this PKey instance against
         * @return true if the specified object is equal to this object; false otherwise
         */
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PKey k)) return false;
            return xVarMin == k.xVarMin && yVarMax == k.yVarMax && n == k.n
                   && alphaBits == k.alphaBits && Arrays.equals(zVars, k.zVars);
        }

        /**
         * Returns the precomputed hash code for this object. The hash code is calculated during object construction
         * based on the values of the object's fields and remains constant for the lifetime of the object.
         *
         * @return the hash code value of this object
         */
        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class ZKey {
        final int[] zVars; // sorted
        private final int hash;

        ZKey(int[] zVars) {
            this.zVars = zVars.clone();
            this.hash = Arrays.hashCode(this.zVars);
        }

        /**
         * Compares this object with the specified object for equality. Two ZKey objects are considered equal if they
         * have the same array of sorted integers in their zVars fields.
         *
         * @param o the object to be compared for equality with this object
         * @return true if the specified object is equal to this object; false otherwise
         */
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ZKey k)) return false;
            return Arrays.equals(zVars, k.zVars);
        }

        /**
         * Returns the hash code value for this object. The hash code is precomputed during the construction of the
         * object based on the contents of the sorted array in the zVars field.
         *
         * @return the hash code value of this object
         */
        @Override
        public int hashCode() {
            return hash;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * TESTWISE: constructed with {@code MissingDataSpec.testwise()}, each test uses the rows complete on the
     * embedded columns of x, y, and z.
     */
    @Override
    public MissingValueSupport getMissingValueSupport() {
        return MissingValueSupport.TESTWISE;
    }
}

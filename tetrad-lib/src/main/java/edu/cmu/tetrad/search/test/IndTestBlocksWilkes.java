package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Block-level CI test using Wilks-rank. Robust to |Y| &lt; |X| by padding Y from the leftover observed pool and, if
 * needed, by subsetting X to |Y|. Thread-safe LRU caches preserved.
 */
public class IndTestBlocksWilkes implements IndependenceTest, BlockTest {

    // ---- Cache sizes (tune) ----
    private static final int PV_CACHE_MAX = 400_000;   // (x,y,Z,n,alpha) -> p
    private static final int RANK_CACHE_MAX = 400_000; // (x,y,Z,n,alpha) -> rank
    private static final int ZBLOCK_CACHE_MAX = 150_000; // Z -> concatenated embedded cols

    private final List<Node> variables;
    private final Map<Node, Integer> nodeHash;
    private final SimpleMatrix S; // correlation (or covariance)
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

    /**
     * Constructs an instance of IndTestBlocks using the provided block specification. This class is used for conducting
     * independence tests based on a dataset's block structure.
     *
     * @param blockSpec the block specification containing metadata about blocks, variables, and the associated dataset.
     *                  Must not be null. Throws IllegalArgumentException if blockSpec is null or invalid.
     */
    public IndTestBlocksWilkes(BlockSpec blockSpec) {
        if (blockSpec == null) throw new IllegalArgumentException("blockSpec == null");
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
        this.S = new CorrelationMatrix(blockSpec.dataSet()).getMatrix().getSimpleMatrix();
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
        PKey key = new PKey(kp.a, kp.b, kp.zVars, n, alpha);

        Integer cached = rankCache.get(key);
        if (cached != null) return cached;

        int rank = RankTests.estimateWilksRankConditioned(S, pads.xAdj, pads.yAdj, kp.zCols, n, alpha);
        if (rank < 0) rank = 0; // defensive
        rankCache.put(key, rank);
        return rank;
    }

    private double getPValue(Node x, Node y, Set<Node> z) {
        KeyParts kp = buildKeyParts(x, y, z);
        XY pads = robustifyXY(kp.xCols, kp.yCols, kp.zCols);
        PKey key = new PKey(kp.a, kp.b, kp.zVars, n, alpha);

        Double cached = pvalCache.get(key);
        if (cached != null) return cached;

        if (pads.xAdj.length == 0 || pads.yAdj.length == 0) {
            pvalCache.put(key, 1.0);
            return 1.0;
        }

        double p = RankTests.pValueIndepConditioned(S, pads.xAdj, pads.yAdj, kp.zCols, n);
        if (Double.isNaN(p) || Double.isInfinite(p)) p = 1.0;
        p = Math.max(0.0, Math.min(1.0, p));

        pvalCache.put(key, p);
        return p;
    }

    /**
     * Ensure Y ⟂ Z (by construction), pad Y if needed, subset X if still |Y|<|X|.
     */
    private XY robustifyXY(int[] xCols0, int[] yCols0, int[] zCols) {
        // Deduplicate and sort for determinism
        int[] X = uniqSorted(xCols0);
        int[] Y = uniqSorted(yCols0);
        int[] Z = uniqSorted(zCols);

        // Remove any accidental overlaps with Z (paranoia; blocks are disjoint, but be safe).
        if (Z.length > 0) {
            Y = minus(Y, Z); // Y := Y \ Z
            X = minus(X, Z); // X := X \ Z
        }

        // If |Y| < |X|, try to pad Y from the complement R = V \ (X ∪ Y ∪ Z)
        // NEW knobs: robustness for |Y| < |X|
        // try to grow Y from the complement pool
        boolean padY = true;
        if (padY && Y.length < X.length) {
            BitSet pool = (BitSet) universeBits.clone();
            for (int v : X) pool.clear(v);
            for (int v : Y) pool.clear(v);
            for (int v : Z) pool.clear(v);

            int need = X.length - Y.length;
            if (need > 0 && pool.cardinality() > 0) {
                int take = Math.min(need, pool.cardinality());
                int[] Ypad = new int[Y.length + take];
                System.arraycopy(Y, 0, Ypad, 0, Y.length);
                int k = Y.length;
                for (int i = pool.nextSetBit(0); i >= 0 && take > 0; i = pool.nextSetBit(i + 1)) {
                    Ypad[k++] = i;
                    take--;
                }
                Arrays.sort(Ypad);
                Y = Ypad;
                if (verbose && Y.length >= X.length) {
                    System.out.printf("IndTestBlocks: padded Y to %d to meet X=%d%n", Y.length, X.length);
                }
            }
        }

        // If still |Y| < |X|, optionally subset X down to |Y|
        // if still |Y| < |X|, shrink X to |Y|
        boolean subsetXIfNeeded = true;
        if (subsetXIfNeeded && Y.length > 0 && Y.length < X.length) {
            // deterministic prefix (could also choose by leverage, but keep simple/fast)
            X = Arrays.copyOf(X, Y.length);
            if (verbose) {
                System.out.printf("IndTestBlocks: subset X from %d to %d to match Y%n", X.length + (Y.length - X.length), Y.length);
            }
        }

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

        int a = Math.min(xiVar, yiVar);
        int b = Math.max(xiVar, yiVar);

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

    private record XY(int[] xAdj, int[] yAdj) {
    }

    // === Small, thread-safe LRU (access-order) ===
    private static final class LruMap<K, V> {
        private final ReentrantLock lock = new ReentrantLock();
        private final int maxSize;
        private final LinkedHashMap<K, V> map;

        LruMap(int maxSize) {
            this.maxSize = Math.max(16, maxSize);
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
            this.alphaBits = Double.doubleToLongBits(Math.rint(alpha * 1e12) / 1e12);
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
}
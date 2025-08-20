package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Block-level CI test using Dong et al. (2024) Lemma 10:
 * <p>
 * C d-separates A and B  iff  rank( Σ_{A∪C, B∪C} ) = |C|
 * <p>
 * Finite-sample surrogate: use RankTests.estimateWilksRank(...) on (A∪C, B∪C). Decision can be EQ/LE/GE with integer
 * tolerance 'tol'.
 * <p>
 * p-value is reported via RankTests.pValueIndepConditioned(S, A∪C, B∪C, ∅, n), so it is at least monotone with the same
 * Wilks machinery you already use.
 * <p>
 * Thread-safe LRU caches (like IndTestBlocks).
 */
public class IndTestBlocksLemma10 implements IndependenceTest, BlockTest {

    // ---- Cache sizes (tune) ----
    private static final int PV_CACHE_MAX = 400_000;  // (AC,BC,n,alpha,mode,tol) -> p
    private static final int RANK_CACHE_MAX = 400_000;  // (AC,BC,n,alpha) -> rank
    private static final int ZBLOCK_CACHE_MAX = 150_000;  // Z (block vars) -> concatenated embedded cols

    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<Node, Integer> nodeHash;
    private final SimpleMatrix S; // correlation (or covariance)
    private final int n;

    // Block -> embedded/expanded column indices
    private final int[][] allCols;

    // LRUs
    private final LruMap<RKey, Integer> rankCache = new LruMap<>(RANK_CACHE_MAX);
    private final LruMap<PKey, Double> pvalCache = new LruMap<>(PV_CACHE_MAX);
    private final LruMap<ZKey, int[]> zblockCache = new LruMap<>(ZBLOCK_CACHE_MAX);
    private final BlockSpec blockSpec;

    // knobs
    private double alpha = 0.01;
    private boolean verbose = false;
    private EqualityMode mode = EqualityMode.LE; // robust default: accept rank <= |C|
    private int tol = 0;                         // integer tolerance on the equality (0 = strict)
    public IndTestBlocksLemma10(DataSet dataSet, BlockSpec blockSpec) {
        if (dataSet == null) throw new IllegalArgumentException("dataSet == null");
        if (blockSpec == null) throw new IllegalArgumentException("blockspec == null");
        this.blockSpec = blockSpec;

        this.dataSet = dataSet;
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

        this.n = dataSet.getNumRows();
        this.S = new CorrelationMatrix(dataSet).getMatrix().getSimpleMatrix();

        final int B = blockSpec.blocks().size();
        final int D = dataSet.getNumColumns();
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
                        throw new IllegalArgumentException("Block " + b + " references column " + col + " outside dataset width " + D);
                    }
                    a[k] = col;
                }
                allCols[b] = a;
            }
        }
    }

    private static String zNames(Set<Node> z) {
        if (z == null || z.isEmpty()) return "{}";
        List<String> names = new ArrayList<>(z.size());
        for (Node n : z) names.add(n.getName());
        Collections.sort(names);
        return "{" + String.join(",", names) + "}";
    }

    // === Public API ===

    /**
     * Sorted union (dedup) of two int[] sets of observed column indices.
     */
    private static int[] unionSorted(int[] a, int[] b) {
        if (a == null || a.length == 0) return distinctSorted(b);
        if (b == null || b.length == 0) return distinctSorted(a);
        int max = 0;
        for (int v : a) max = Math.max(max, v);
        for (int v : b) max = Math.max(max, v);
        BitSet bs = new BitSet(max + 1);
        for (int v : a) bs.set(v);
        for (int v : b) bs.set(v);
        int[] out = new int[bs.cardinality()];
        for (int i = bs.nextSetBit(0), k = 0; i >= 0; i = bs.nextSetBit(i + 1)) out[k++] = i;
        return out;
    }

    /**
     * Sort + dedup.
     */
    private static int[] distinctSorted(int[] x) {
        if (x == null || x.length == 0) return new int[0];
        int[] y = Arrays.copyOf(x, x.length);
        Arrays.sort(y);
        int w = 1;
        for (int i = 1; i < y.length; i++) if (y[i] != y[w - 1]) y[w++] = y[i];
        return Arrays.copyOf(y, w);
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        // Build A,B,C (block indices), concatenate to AC, BC
        Parts parts = buildParts(x, y, z);

        // Estimated rank for Σ_{A∪C, B∪C}
        int estRank = getRank(parts.ACcols, parts.BCcols);

        // Decision per Lemma 10 with chosen mode/tol
        boolean indep = switch (mode) {
            case EQ -> Math.abs(estRank - parts.Csize) <= tol;
            case LE -> estRank <= parts.Csize + tol;
            case GE -> estRank >= parts.Csize - tol;
        };

        // p-value: same Wilks machinery, treat as unconditioned on empty Z
        double pValue = getPValue(parts.ACcols, parts.BCcols);

//        if (verbose) {
//            System.out.printf("Lemma10: %s _||_ %s | %s ? estRank=%d, |C|=%d, mode=%s, tol=%d, p=%.4g -> %s%n",
//                    parts.xName, parts.yName, parts.zNames, estRank, parts.Csize, mode, tol, pValue,
//                    indep ? "INDEP" : "DEP");
//        }

        return new IndependenceResult(
                new IndependenceFact(parts.xNode, parts.yNode, z),
                indep,
                pValue,
                alpha - pValue
        );
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    @Override
    public DataModel getData() {
        return dataSet;
    }

    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("Alpha must be in (0,1).");
        this.alpha = alpha; // alpha participates in cache key
    }

    // === Core ===

    /**
     * Choose EQ/LE/GE equality mode for rank vs |C|. Default LE (robust).
     */
    public void setEqualityMode(EqualityMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    /**
     * Integer tolerance on equality (e.g., 1 to allow off-by-one). Default 0.
     */
    public void setTolerance(int tol) {
        if (tol < 0) throw new IllegalArgumentException("tol must be >= 0");
        this.tol = tol;
    }

    private int getRank(int[] AC, int[] BC) {
        RKey key = new RKey(AC, BC, n, alpha);
        Integer cached = rankCache.get(key);
        if (cached != null) return cached;

        int rank = RankTests.estimateWilksRank(S, AC, BC, n, alpha);
        if (rank < 0) rank = 0;
        rankCache.put(key, rank);
        return rank;
    }

    private double getPValue(int[] AC, int[] BC) {
        PKey key = new PKey(AC, BC, n, alpha, mode, tol);
        Double cached = pvalCache.get(key);
        if (cached != null) return cached;

        // Use the same p-value path; treat as "unconditioned" by passing empty Z.
        double p = RankTests.pValueIndepConditioned(S, AC, BC, new int[0], n);
        if (Double.isNaN(p) || Double.isInfinite(p)) p = 1.0;
        p = Math.max(0.0, Math.min(1.0, p));

        pvalCache.put(key, p);
        return p;
    }

    // Build AC, BC, and |C| from block-level x,y,z
    private Parts buildParts(Node x, Node y, Set<Node> z) {
        Integer xiVar = nodeHash.get(x);
        Integer yiVar = nodeHash.get(y);
        if (xiVar == null || yiVar == null) {
            throw new IllegalArgumentException("Unknown node(s): " + x + ", " + y);
        }

        // C blocks as block indices (sorted)
        int[] zVars = new int[z.size()];
        int t = 0;
        for (Node zn : z) {
            Integer idx = nodeHash.get(zn);
            if (idx == null) throw new IllegalArgumentException("Unknown conditioning node: " + zn);
            zVars[t++] = idx;
        }
        Arrays.sort(zVars);

        // Column arrays
        int[] Acols = allCols[xiVar];
        int[] Bcols = allCols[yiVar];

        // Concatenate C columns (cached)
        ZKey zkey = new ZKey(zVars);
        int[] Ccols = zblockCache.get(zkey);
        if (Ccols == null) {
            int total = 0;
            for (int zv : zVars) total += allCols[zv].length;
            Ccols = new int[total];
            int k = 0;
            for (int zv : zVars) {
                int[] cols = allCols[zv];
                System.arraycopy(cols, 0, Ccols, k, cols.length);
                k += cols.length;
            }
            zblockCache.put(zkey, Ccols);
        }

        // AC = A ∪ C, BC = B ∪ C
        int[] AC = unionSorted(Acols, Ccols);
        int[] BC = unionSorted(Bcols, Ccols);

        return new Parts(x, y, x.getName(), y.getName(), zNames(z), z, AC, BC, Ccols.length);
    }

    @Override
    public BlockSpec getBlockSpec() {
        return blockSpec;
    }

    // === Key bits ===

    /**
     * Equality mode for the Lemma-10 criterion.
     */
    public enum EqualityMode {EQ, LE, GE}

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

    private record Parts(Node xNode, Node yNode,
                         String xName, String yName, String zNames,
                         Set<Node> zSet, int[] ACcols, int[] BCcols, int Csize) {
    }

    private static final class RKey {
        final int[] AC; // sorted
        final int[] BC; // sorted
        final int n;
        final long alphaBits;
        private final int hash;

        RKey(int[] AC, int[] BC, int n, double alpha) {
            this.AC = AC.clone();
            this.BC = BC.clone();
            this.n = n;
            this.alphaBits = Double.doubleToLongBits(Math.rint(alpha * 1e12) / 1e12);
            int h = 1;
            h = 31 * h + Arrays.hashCode(this.AC);
            h = 31 * h + Arrays.hashCode(this.BC);
            h = 31 * h + n;
            h = 31 * h + Long.hashCode(alphaBits);
            this.hash = h;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof RKey k)) return false;
            return n == k.n && alphaBits == k.alphaBits
                   && Arrays.equals(AC, k.AC) && Arrays.equals(BC, k.BC);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    // === array helpers ===

    private static final class PKey {
        final int[] AC; // sorted
        final int[] BC; // sorted
        final int n;
        final long alphaBits;
        final int modeOrdinal;
        final int tol;
        private final int hash;

        PKey(int[] AC, int[] BC, int n, double alpha, EqualityMode mode, int tol) {
            this.AC = AC.clone();
            this.BC = BC.clone();
            this.n = n;
            this.alphaBits = Double.doubleToLongBits(Math.rint(alpha * 1e12) / 1e12);
            this.modeOrdinal = mode.ordinal();
            this.tol = tol;
            int h = 1;
            h = 31 * h + Arrays.hashCode(this.AC);
            h = 31 * h + Arrays.hashCode(this.BC);
            h = 31 * h + n;
            h = 31 * h + Long.hashCode(alphaBits);
            h = 31 * h + modeOrdinal;
            h = 31 * h + tol;
            this.hash = h;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PKey k)) return false;
            return n == k.n && alphaBits == k.alphaBits
                   && modeOrdinal == k.modeOrdinal && tol == k.tol
                   && Arrays.equals(AC, k.AC) && Arrays.equals(BC, k.BC);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class ZKey {
        final int[] zVars; // sorted block indices
        private final int hash;

        ZKey(int[] zVars) {
            this.zVars = zVars.clone();
            this.hash = Arrays.hashCode(this.zVars);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ZKey k)) return false;
            return Arrays.equals(zVars, k.zVars);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
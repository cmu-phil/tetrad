package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Block-level CI test using RankTests.estimateWilksRankConditioned for the decision and
 * RankTests.pValueIndepConditioned for a reported p-value. Thread-safe LRU caches.
 */
public class IndTestBlocks implements IndependenceTest {

    // ---- Cache sizes (tune) ----
    private static final int PV_CACHE_MAX = 400_000;  // (x,y,Z,n,alpha) -> p
    private static final int RANK_CACHE_MAX = 400_000;  // (x,y,Z,n,alpha) -> rank
    private static final int ZBLOCK_CACHE_MAX = 150_000;  // Z -> concatenated embedded cols

    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<Node, Integer> nodeHash;
    private final SimpleMatrix S; // correlation (or covariance)
    private final int n;

    // Block -> embedded/expanded column indices
    private final int[][] allCols;

    // Thread-safe LRUs
    private final LruMap<PKey, Integer> rankCache = new LruMap<>(RANK_CACHE_MAX);
    private final LruMap<PKey, Double> pvalCache = new LruMap<>(PV_CACHE_MAX);
    private final LruMap<ZKey, int[]> zblockCache = new LruMap<>(ZBLOCK_CACHE_MAX);

    // knobs
    private double alpha = 0.01;
    private boolean verbose = false;

    public IndTestBlocks(DataSet dataSet, List<List<Integer>> blocks, List<Node> blockVariables) {
        if (dataSet == null) throw new IllegalArgumentException("dataSet == null");
        if (blocks == null) throw new IllegalArgumentException("blocks == null");
        if (blockVariables == null) throw new IllegalArgumentException("blockVariables == null");

        final int B = blocks.size();
        if (blockVariables.size() != B) {
            throw new IllegalArgumentException("blockVariables.size() (" + blockVariables.size() + ") != blocks.size() (" + B + ")");
        }

        this.dataSet = dataSet;
        this.variables = new ArrayList<>(blockVariables);
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
        // If you prefer covariance:
        // this.S = DataUtils.cov(dataSet.getDoubleData().getSimpleMatrix());

        final int D = dataSet.getNumColumns();
        this.allCols = new int[B][];
        for (int b = 0; b < B; b++) {
            List<Integer> cols = blocks.get(b);
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

    // === Public API ===

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
        this.alpha = alpha;  // alpha participates in cache key
        // (No need to clear caches eagerly; keys include alpha.)
    }

    // === Core ===

    private int getEstimatedRank(Node x, Node y, Set<Node> z) {
        KeyParts kp = buildKeyParts(x, y, z);
        PKey key = new PKey(kp.a, kp.b, kp.zVars, n, alpha);

        Integer cached = rankCache.get(key);
        if (cached != null) return cached;

        // Map to RankTests API: C = X, VminusC = Y (Z handled internally)
        int rank = RankTests.estimateWilksRankConditioned(S, kp.xCols, kp.yCols, kp.zCols, n, alpha);
        if (rank < 0) rank = 0; // defensive
        rankCache.put(key, rank);
        return rank;
    }

    private double getPValue(Node x, Node y, Set<Node> z) {
        KeyParts kp = buildKeyParts(x, y, z);
        PKey key = new PKey(kp.a, kp.b, kp.zVars, n, alpha);

        Double cached = pvalCache.get(key);
        if (cached != null) return cached;

        if (kp.xCols.length == 0 || kp.yCols.length == 0) {
            pvalCache.put(key, 1.0);
            return 1.0;
        }

        // Report p using the robust RankTests path (consistent with Wilks rank)
        double p = RankTests.pValueIndepConditioned(S, kp.xCols, kp.yCols, kp.zCols, n);
        if (Double.isNaN(p) || Double.isInfinite(p)) p = 1.0;
        p = Math.max(0.0, Math.min(1.0, p));

        pvalCache.put(key, p);
        return p;
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

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PKey k)) return false;
            return xVarMin == k.xVarMin && yVarMax == k.yVarMax && n == k.n
                   && alphaBits == k.alphaBits && Arrays.equals(zVars, k.zVars);
        }

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
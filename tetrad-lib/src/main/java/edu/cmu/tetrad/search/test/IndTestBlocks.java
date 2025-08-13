package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Block-level Conditional Independence test using a Rank/CCA statistic (Bartlett–Wilks).
 * Delegates the statistic to RankTests and adds lightweight LRU caching.
 *
 * CONTRACT: 'blocks' maps each original variable index v (0..V-1) to the list of column indices
 * in the CURRENT dataSet that belong to v's block. Empty or null lists are allowed.
 */
public class IndTestBlocks implements IndependenceTest, RawMarginalIndependenceTest {

    // ---- Caches ----
    private static final int PV_CACHE_MAX = 200_000;
    private static final int ZBLOCK_CACHE_MAX = 100_000;

    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<Node, Integer> nodeHash;
    private final SimpleMatrix covarianceMatrix; // covariance over dataset columns
    private final int sampleSize;

    /**
     * For each variable v, the embedded/expanded column indices that compose its block.
     * allCols[v] may be empty (blockless).
     */
    private final int[][] allCols;

    /**
     * P-value cache keyed by (min(x,y), max(x,y), sorted Z vars).
     * Note: not thread-safe; wrap externally if you parallelize CI calls.
     */
    private final Map<PKey, Double> pvalCache =
            new LinkedHashMap<>(4096, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<PKey, Double> e) {
                    return size() > PV_CACHE_MAX;
                }
            };

    /**
     * Cache for concatenated embedded columns of Z.
     */
    private final Map<ZKey, int[]> zblockCache =
            new LinkedHashMap<>(2048, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ZKey, int[]> e) {
                    return size() > ZBLOCK_CACHE_MAX;
                }
            };

    private double alpha = 0.01;
    private boolean verbose = false;

    /**
     * @param dataSet dataset whose columns the 'blocks' indices refer to
     * @param blocks  for each variable v (by index), the list of column indices composing v's block
     */
    public IndTestBlocks(DataSet dataSet, List<List<Integer>> blocks) {
        if (dataSet == null) throw new IllegalArgumentException("dataSet == null");
        if (blocks == null) throw new IllegalArgumentException("blocks == null");

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        if (blocks.size() != variables.size()) {
            throw new IllegalArgumentException("blocks.size() must equal number of variables");
        }

        Map<Node, Integer> nodesHash = new HashMap<>();
        for (int j = 0; j < this.variables.size(); j++) nodesHash.put(this.variables.get(j), j);
        this.nodeHash = nodesHash;

        this.sampleSize = dataSet.getNumRows();
        this.covarianceMatrix = DataUtils.cov(dataSet.getDoubleData().getSimpleMatrix());

        // Precompute block columns
        int V = variables.size();
        this.allCols = new int[V][];
        for (int v = 0; v < V; v++) {
            List<Integer> cols = blocks.get(v);
            if (cols == null || cols.isEmpty()) {
                allCols[v] = new int[0];
            } else {
                int[] a = new int[cols.size()];
                for (int k = 0; k < cols.size(); k++) a[k] = cols.get(k);
                allCols[v] = a;
            }
        }
    }

    // === Public API ===

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        double pValue = getPValue(x, y, z);
        boolean independent = pValue > alpha;
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
        this.alpha = alpha; // alpha does not affect cached p-values
    }

    /**
     * Not supported for block-based tests (arrays are not tied to 'blocks').
     * Use checkIndependence(Node, Node, Set) on the dataset with configured blocks.
     */
    @Override
    public double computePValue(double[] x, double[] y) {
        throw new UnsupportedOperationException(
                "IndTestBlocks does not support computePValue(double[],double[]). " +
                "Use checkIndependence(Node,Node,Set) with the dataset and configured blocks.");
    }

    // === Core RCCA/Bartlett test ===

    private double getPValue(Node x, Node y, Set<Node> z) {
        // Map nodes to indices
        Integer xiVar = nodeHash.get(x);
        Integer yiVar = nodeHash.get(y);
        if (xiVar == null || yiVar == null) {
            throw new IllegalArgumentException("Unknown node(s): " + x + ", " + y);
        }

        // Build sorted Z var indices
        int[] zVars = new int[z.size()];
        int t = 0;
        for (Node zn : z) {
            Integer idx = nodeHash.get(zn);
            if (idx == null) throw new IllegalArgumentException("Unknown conditioning node: " + zn);
            zVars[t++] = idx;
        }
        Arrays.sort(zVars);

        // Normalize (x,y) in key for symmetry
        int a = Math.min(xiVar, yiVar);
        int b = Math.max(xiVar, yiVar);
        PKey key = new PKey(a, b, zVars);

        Double cached = pvalCache.get(key);
        if (cached != null) return cached;

        // Embedded indices (use original order for the actual test—stat is symmetric)
        int[] xCols = allCols[xiVar];
        int[] yCols = allCols[yiVar];

        if (xCols.length == 0 || yCols.length == 0) {
            if (verbose) {
                System.out.printf("IndTestBlocks: empty block for %s or %s%n", x.getName(), y.getName());
            }
            pvalCache.put(key, 1.0);
            return 1.0;
        }

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

        // Delegate statistic to RankTests (partial CCA with Wilks/Bartlett, testing r=0)
        double pValue = RankTests.pValueIndepConditioned(covarianceMatrix, xCols, yCols, zCols, sampleSize);
        if (Double.isNaN(pValue) || Double.isInfinite(pValue)) pValue = 1.0;
        pValue = Math.max(0.0, Math.min(1.0, pValue));

        pvalCache.put(key, pValue);
        return pValue;
    }

    // === Cache keys ===

    private static final class PKey {
        final int xVarMin, yVarMax;  // normalized so xVarMin <= yVarMax
        final int[] zVars; // sorted
        private final int hash;

        PKey(int xVarMin, int yVarMax, int[] zVars) {
            this.xVarMin = xVarMin;
            this.yVarMax = yVarMax;
            this.zVars = zVars.clone();
            int h = 1;
            h = 31 * h + xVarMin;
            h = 31 * h + yVarMax;
            h = 31 * h + Arrays.hashCode(this.zVars);
            this.hash = h;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PKey k)) return false;
            return xVarMin == k.xVarMin && yVarMax == k.yVarMax
                   && Arrays.equals(zVars, k.zVars);
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
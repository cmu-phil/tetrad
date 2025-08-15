package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
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
 * CONTRACT:
 * - 'blocks' maps each BLOCK index b (0..B-1) to the list of column indices in the CURRENT dataSet
 *   that belong to that block. Empty or null lists are allowed.
 * - 'blockVariables' is the list of Nodes you want to SEARCH OVER (size B). The k-th node corresponds to block k.
 * - getVariables() returns exactly 'blockVariables'.
 */
public class IndTestBlocksOld implements IndependenceTest, RawMarginalIndependenceTest {

    // ---- Caches ----
    private static final int PV_CACHE_MAX = 200_000;
    private static final int ZBLOCK_CACHE_MAX = 100_000;

    private final DataSet dataSet;
    private final List<Node> variables;            // block-level variables, size == blocks.size()
    private final Map<Node, Integer> nodeHash;     // block node -> block index
    private final SimpleMatrix covarianceMatrix;   // covariance over dataset columns
    private final int sampleSize;

    /** For each block b, the embedded/expanded column indices that compose it. */
    private final int[][] allCols;                 // size = blocks.size()

    /** P-value cache keyed by (min(x,y), max(x,y), sorted Z vars). */
    private final Map<PKey, Double> pvalCache =
            new LinkedHashMap<>(4096, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<PKey, Double> e) {
                    return size() > PV_CACHE_MAX;
                }
            };

    /** Cache for concatenated embedded columns of Z. */
    private final Map<ZKey, int[]> zblockCache =
            new LinkedHashMap<>(2048, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<ZKey, int[]> e) {
                    return size() > ZBLOCK_CACHE_MAX;
                }
            };

    private double alpha = 0.01;
    private boolean verbose = false;

    /**
     * @param dataSet         dataset whose columns the 'blocks' indices refer to
     * @param blocks          for each block b (by index 0..B-1), the list of column indices composing that block
     * @param blockVariables  the exact variables to expose to the search; blockVariables.size() must equal blocks.size()
     */
    public IndTestBlocksOld(DataSet dataSet, List<List<Integer>> blocks, List<Node> blockVariables) {
        if (dataSet == null) throw new IllegalArgumentException("dataSet == null");
        if (blocks == null) throw new IllegalArgumentException("blocks == null");
        if (blockVariables == null) throw new IllegalArgumentException("blockVariables == null");

        final int B = blocks.size();
        if (blockVariables.size() != B) {
            throw new IllegalArgumentException(
                    "blockVariables.size() (" + blockVariables.size() + ") != blocks.size() (" + B + ")");
        }

        this.dataSet = dataSet;
        this.variables = new ArrayList<>(blockVariables); // preserve caller order

        // Map block-node -> block index
        Map<Node, Integer> nodesHash = new HashMap<>();
        for (int j = 0; j < this.variables.size(); j++) {
            Node v = this.variables.get(j);
            if (v == null) throw new IllegalArgumentException("blockVariables[" + j + "] is null");
            if (nodesHash.put(v, j) != null) {
                throw new IllegalArgumentException("Duplicate Node in blockVariables: " + v.getName());
            }
        }
        this.nodeHash = nodesHash;

        this.sampleSize = dataSet.getNumRows();
        this.covarianceMatrix = new CorrelationMatrix(dataSet).getMatrix().getSimpleMatrix();

        // Precompute block columns; size to number of blocks (B)
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
                        throw new IllegalArgumentException(
                                "Block " + b + " references column " + col + " outside dataset width " + D);
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
        boolean independent = pValue > alpha;
        return new IndependenceResult(new IndependenceFact(x, y, z), independent, pValue, alpha - pValue);
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables); // block-nodes as provided by caller
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
     * Not supported for block-based tests. Use checkIndependence(Node, Node, Set) on the dataset with configured blocks.
     */
    @Override
    public double computePValue(double[] x, double[] y) {
        throw new UnsupportedOperationException(
                "IndTestBlocks does not support computePValue(double[],double[]). " +
                "Use checkIndependence(Node,Node,Set) with the dataset and configured blocks.");
    }

    // === Core RCCA/Bartlett test ===

    private double getPValue(Node x, Node y, Set<Node> z) {
        // Map block-nodes to block indices
        Integer xiVar = nodeHash.get(x);
        Integer yiVar = nodeHash.get(y);
        if (xiVar == null || yiVar == null) {
            throw new IllegalArgumentException("Unknown node(s): " + x + ", " + y);
        }

        // Build sorted Z block indices
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

        // Embedded indices for these blocks
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
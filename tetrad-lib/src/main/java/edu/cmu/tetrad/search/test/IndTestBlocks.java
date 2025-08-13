package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Basis-function Conditional Independence test using a Rank/CCA statistic (Bartlett–Wilks).
 * Delegates the statistic to RankTests and adds lightweight caching.
 */
public class IndTestBlocks implements IndependenceTest, RawMarginalIndependenceTest {

    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<Node, Integer> nodeHash;

    private final SimpleMatrix covarianceMatrix; // embedded covariance
    private final int sampleSize;

    private final Map<Integer, List<Integer>> embedding;
    private final double lambda;            // kept for API compatibility
    private final int truncationLimit;

    private double alpha = 0.01;
    private boolean verbose = false;
    private boolean doOneEquationOnly = false;

    // ---- Precomputed embedded column arrays per original variable ----
    private final int[][] allCols;      // all embedded cols for var j
    private final int[][] firstOnly;    // first embedded col (len 1) or empty

    // ---- Caches ----
    private static final int PV_CACHE_MAX = 200_000;
    private static final int ZBLOCK_CACHE_MAX = 100_000;

    /** P-value cache keyed by (xVar, yVar, sorted Z vars, doOneEquationOnly). */
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

    public IndTestBlocks(DataSet dataSet, int truncationLimit, double lambda) {
        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        Map<Node, Integer> nodesHash = new HashMap<>();
        for (int j = 0; j < this.variables.size(); j++) nodesHash.put(this.variables.get(j), j);
        this.nodeHash = nodesHash;
        this.truncationLimit = truncationLimit;
        this.lambda = lambda;

        Embedding.EmbeddedData embeddedData = Embedding.getEmbeddedData(
                dataSet, truncationLimit, /*basisType*/ 1, /*basisScale*/ 1
        );
        this.embedding = embeddedData.embedding();
        this.sampleSize = dataSet.getNumRows();

        // Covariance of the (centered) embedded columns
        this.covarianceMatrix = DataUtils.cov(
                embeddedData.embeddedData().getDoubleData().getSimpleMatrix()
        );

        // Precompute per-variable embedded columns
        int V = variables.size();
        this.allCols   = new int[V][];
        this.firstOnly = new int[V][];
        for (int v = 0; v < V; v++) {
            List<Integer> cols = embedding.get(v);
            if (cols == null || cols.isEmpty()) {
                allCols[v]   = new int[0];
                firstOnly[v] = new int[0];
            } else {
                int[] a = new int[cols.size()];
                for (int k = 0; k < cols.size(); k++) a[k] = cols.get(k);
                allCols[v] = a;
                firstOnly[v] = new int[]{ a[0] };
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
    public List<Node> getVariables() { return new ArrayList<>(variables); }

    @Override
    public DataModel getData() { return dataSet; }

    @Override
    public boolean isVerbose() { return this.verbose; }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    public double getAlpha() { return alpha; }
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("Alpha must be in (0,1).");
        this.alpha = alpha; // alpha does not affect cached p-values, so no invalidation needed
    }

    /** If true, only use the first embedded column for X (mirrors LRT class option). */
    public void setDoOneEquationOnly(boolean doOneEquationOnly) {
        if (this.doOneEquationOnly != doOneEquationOnly) {
            this.doOneEquationOnly = doOneEquationOnly;
            pvalCache.clear(); // key depends on this
        }
    }

    @Override
    public double computePValue(double[] x, double[] y) {
        double[][] combined = new double[x.length][2];
        for (int i = 0; i < x.length; i++) {
            combined[i][0] = x[i];
            combined[i][1] = y[i];
        }
        Node _x = new ContinuousVariable("X_computePValue");
        Node _y = new ContinuousVariable("Y_computePValue");
        List<Node> nodes = Arrays.asList(_x, _y);
        DataSet ds = new BoxDataSet(new DoubleDataBox(combined), nodes);

        IndTestBlocks test = new IndTestBlocks(ds, truncationLimit, lambda);
        test.setAlpha(alpha);
        test.setDoOneEquationOnly(doOneEquationOnly);
        return test.getPValue(_x, _y, Collections.emptySet());
    }

    // === Core RCCA/Bartlett test ===

    private double getPValue(Node x, Node y, Set<Node> z) {
        // Build a stable cache key
        int xVar = nodeHash.get(x);
        int yVar = nodeHash.get(y);

        int[] zVars = new int[z.size()];
        int t = 0;
        for (Node zn : z) zVars[t++] = nodeHash.get(zn);
        Arrays.sort(zVars);

        PKey key = new PKey(xVar, yVar, zVars, doOneEquationOnly);
        Double cached = pvalCache.get(key);
        if (cached != null) return cached;

        // Embedded indices
        int[] xi = doOneEquationOnly ? firstOnly[xVar] : allCols[xVar];
        int[] yi = allCols[yVar];
        if (xi.length == 0 || yi.length == 0) {
            pvalCache.put(key, 1.0);
            return 1.0;
        }

        // Concatenate Z embedded columns (cached)
        ZKey zkey = new ZKey(zVars);
        int[] zi = zblockCache.get(zkey);
        if (zi == null) {
            int total = 0;
            for (int zv : zVars) total += allCols[zv].length;
            zi = new int[total];
            int k = 0;
            for (int zv : zVars) {
                int[] cols = allCols[zv];
                System.arraycopy(cols, 0, zi, k, cols.length);
                k += cols.length;
            }
            zblockCache.put(zkey, zi);
        }

        // Delegate statistic to RankTests (partial CCA with Wilks/Bartlett, r=0)
        double pValue = RankTests.pValueIndepConditioned(covarianceMatrix, xi, yi, zi, sampleSize);
        if (Double.isNaN(pValue)) pValue = 1.0;
        pValue = Math.max(0.0, Math.min(1.0, pValue));

        pvalCache.put(key, pValue);
        return pValue;
    }

    // === Cache keys ===

    private static final class PKey {
        final int xVar, yVar;
        final int[] zVars; // sorted
        final boolean oneEq;
        private final int hash;
        PKey(int xVar, int yVar, int[] zVars, boolean oneEq) {
            this.xVar = xVar; this.yVar = yVar;
            this.zVars = zVars.clone();
            this.oneEq = oneEq;
            int h = 1;
            h = 31*h + xVar;
            h = 31*h + yVar;
            h = 31*h + Arrays.hashCode(this.zVars);
            h = 31*h + (oneEq ? 1 : 0);
            this.hash = h;
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof PKey k)) return false;
            return xVar == k.xVar && yVar == k.yVar && oneEq == k.oneEq
                   && Arrays.equals(zVars, k.zVars);
        }
        @Override public int hashCode() { return hash; }
    }

    private static final class ZKey {
        final int[] zVars; // sorted
        private final int hash;
        ZKey(int[] zVars) {
            this.zVars = zVars.clone();
            this.hash = Arrays.hashCode(this.zVars);
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof ZKey k)) return false;
            return Arrays.equals(zVars, k.zVars);
        }
        @Override public int hashCode() { return hash; }
    }
}
package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * GP / kernel ridge marginal likelihood score for continuous variables:
 *
 *   y = f(Z) + e,    e ~ N(0, sigma^2 I)
 *   f ~ GP(0, k(.,.))
 *
 * Score (up to constants):
 *   S = -0.5 * y^T C^{-1} y - 0.5 * log|C|,  C = Kz + sigma^2 I
 *
 * Higher is better (Tetrad convention).
 *
 * This is a "kernel marginal score" that is stable in greedy search
 * (BOSS/FGES-style) compared to operator/Kx-based surrogates.
 */
public final class KernelMarginalLikelihoodScore implements Score, EffectiveSampleSizeSettable {

    // -------------------- configuration knobs --------------------

    /** Base ridge/noise knob. Used to form sigma^2. Must be > 0. */
    private double lambda = 1e-3;

    /**
     * If true, sigma^2 = n * lambda. If false, sigma^2 = lambda.
     * Many kernel ridge / GP scoring derivations use an n-scaling.
     */
    private boolean useNScaledSigma2 = true;

    /** Jitter escalation base for Cholesky stabilization. Must be > 0. */
    private double jitter = 1e-10;

    /** If true, use valid row subsets when missing exists. */
    private final boolean calculateRowSubsets;

    /**
     * Bandwidth multiplier on the median heuristic.
     * 1.0 is default; try 0.5 or 2.0 if things feel too smooth/too spiky.
     */
    private double bandwidthMultiplier = 1.0;

    /**
     * Max rows used to estimate median bandwidth (subsample for speed).
     * Kernel itself still uses all rows.
     */
    private int bwMaxRows = 400;

    // -------------------- data --------------------

    private final DataSet dataSet;
    private final List<Node> variables;
    private final int sampleSize;
    private int nEff;

    /** Standardized columns (z-scored globally, NaNs preserved). zCols[p][n]. */
    private final double[][] zCols;

    /** Cache: (target i, sorted parents) -> score. */
    private final ConcurrentHashMap<Long, Double> localScoreCache = new ConcurrentHashMap<>();

    public KernelMarginalLikelihoodScore(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("dataSet");
        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        setEffectiveSampleSize(-1);

        this.calculateRowSubsets = dataSet.existsMissingValue();

        // Extract + z-score each column once (globally). Keeps kernels sane.
        int p = variables.size();
        double[][] cols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            for (int r = 0; r < sampleSize; r++) cols[j][r] = dataSet.getDouble(r, j);
        }

        this.zCols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            zscoreColumnPreserveNaN(cols[j], zCols[j]);
        }
    }

    // -------------------- Score interface --------------------

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScore(int i, int... parents) {
        Arrays.sort(parents);

        long key = cacheKey(i, parents);
        Double cached = localScoreCache.get(key);
        if (cached != null) return cached;

        try {
            int[] all = concat(i, parents);
            int[] rows = calculateRowSubsets ? validRows(all) : null;

            int n = (rows == null) ? nEff : rows.length;
            if (n < 5) return Double.NaN;

            // Response y = standardized Xi (subset rows if needed)
            double[] y = extract1D(i, rows, n);

            // Center y (mean function = constant)
            centerInPlace(y);

            // Build C = K(Z,Z) + sigma^2 I
            double sigma2 = useNScaledSigma2 ? (n * lambda) : lambda;
            if (!(sigma2 > 0) || !Double.isFinite(sigma2)) return Double.NaN;

            DMatrixRMaj C = (parents.length == 0)
                    ? new DMatrixRMaj(n, n)  // K = 0
                    : rbfGramND(parents, rows, n);

            addDiagonalInPlace(C, sigma2);

            double score = gpLogMarginalLikelihood(y, C, jitter);
            localScoreCache.put(key, score);
            return score;
        } catch (RuntimeException e) {
            TetradLogger.getInstance().log(e.getMessage());
            return Double.NaN;
        }
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(Math.log(Math.max(5, nEff)));
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        int i = variables.indexOf(y);
        int[] parents = new int[z.size()];
        for (int t = 0; t < z.size(); t++) parents[t] = variables.indexOf(z.get(t));

        double s = localScore(i, parents);
        return Double.isNaN(s) || Double.isInfinite(s);
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    public DataModel getDataModel() {
        return dataSet;
    }

    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        localScoreCache.clear();
    }

    @Override
    public String toString() {
        return "Huang Kernel Marginal Score (GP form, continuous)";
    }

    // -------------------- public tuning knobs --------------------

    public double getLambda() { return lambda; }

    public void setLambda(double lambda) {
        if (lambda <= 0) throw new IllegalArgumentException("lambda must be > 0");
        this.lambda = lambda;
        localScoreCache.clear();
    }

    public boolean isUseNScaledSigma2() { return useNScaledSigma2; }

    public void setUseNScaledSigma2(boolean useNScaledSigma2) {
        this.useNScaledSigma2 = useNScaledSigma2;
        localScoreCache.clear();
    }

    public double getJitter() { return jitter; }

    public void setJitter(double jitter) {
        if (jitter <= 0) throw new IllegalArgumentException("jitter must be > 0");
        this.jitter = jitter;
        localScoreCache.clear();
    }

    public double getBandwidthMultiplier() { return bandwidthMultiplier; }

    public void setBandwidthMultiplier(double bandwidthMultiplier) {
        if (!(bandwidthMultiplier > 0) || !Double.isFinite(bandwidthMultiplier)) {
            throw new IllegalArgumentException("bandwidthMultiplier must be > 0");
        }
        this.bandwidthMultiplier = bandwidthMultiplier;
        localScoreCache.clear();
    }

    public int getBwMaxRows() { return bwMaxRows; }

    public void setBwMaxRows(int bwMaxRows) {
        this.bwMaxRows = Math.max(50, bwMaxRows);
        localScoreCache.clear();
    }

    // -------------------- GP marginal likelihood core --------------------

    /**
     * Computes: -0.5*y^T C^{-1} y - 0.5*log|C|
     * using Cholesky with jitter escalation.
     */
    private static double gpLogMarginalLikelihood(double[] y, DMatrixRMaj C, double jitter) {
        final int n = y.length;
        if (C.numRows != n || C.numCols != n) throw new IllegalArgumentException("C dimension mismatch");

        // Try Cholesky with escalating jitter.
        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);

        double eps = 0.0;
        DMatrixRMaj Cf = null;

        boolean ok = false;
        for (int k = 0; k < 8; k++) {
            Cf = (k == 0) ? C : C.copy();
            if (k > 0) addDiagonalInPlace(Cf, eps);

            if (chol.decompose(Cf)) {
                ok = true;
                break;
            }
            eps = (eps == 0.0) ? jitter : eps * 10.0;
        }
        if (!ok) return Double.NaN;

        DMatrixRMaj L = chol.getT(null); // lower triangular
        double logDet = 0.0;
        for (int i = 0; i < n; i++) {
            double di = L.get(i, i);
            if (!(di > 0) || !Double.isFinite(di)) return Double.NaN;
            logDet += Math.log(di);
        }
        logDet *= 2.0;

        // Solve C^{-1} y via two triangular solves: L u = y, L^T x = u.
        double[] u = Arrays.copyOf(y, n);

        // Forward solve (L u = y)
        for (int i = 0; i < n; i++) {
            double sum = u[i];
            for (int j = 0; j < i; j++) sum -= L.get(i, j) * u[j];
            double di = L.get(i, i);
            u[i] = sum / di;
        }

        // Back solve (L^T x = u) reusing u as x
        for (int i = n - 1; i >= 0; i--) {
            double sum = u[i];
            for (int j = i + 1; j < n; j++) sum -= L.get(j, i) * u[j];
            double di = L.get(i, i);
            u[i] = sum / di;
        }

        // quad = y^T x
        double quad = 0.0;
        for (int i = 0; i < n; i++) quad += y[i] * u[i];

        if (!Double.isFinite(quad) || !Double.isFinite(logDet)) return Double.NaN;

        return -0.5 * quad - 0.5 * logDet;
    }

    // -------------------- kernels --------------------

    /**
     * RBF kernel Gram matrix on standardized parents:
     *   K_ij = exp(-||z_i - z_j||^2 / bw2)
     * where bw2 is median ||zi-zj||^2 times multiplier.
     */
    private DMatrixRMaj rbfGramND(int[] parentIdx, int[] rows, int n) {
        int d = parentIdx.length;

        // Extract Z (n x d)
        double[][] Z = new double[n][d];
        for (int r = 0; r < n; r++) {
            int row = (rows == null) ? r : rows[r];
            for (int j = 0; j < d; j++) Z[r][j] = zCols[parentIdx[j]][row];
        }

        double bw2 = medianDistanceSquaredND(Z, Math.min(n, bwMaxRows));
        if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;
        bw2 *= (bandwidthMultiplier * bandwidthMultiplier);

        double invBw = 1.0 / bw2;

//        DMatrixRMaj K = new DMatrixRMaj(n, n);
//        for (int i = 0; i < n; i++) {
//            K.set(i, i, 1.0);
//            for (int j = 0; j < i; j++) {
//                double dist2 = 0.0;
//                for (int k = 0; k < d; k++) {
//                    double diff = Z[i][k] - Z[j][k];
//                    dist2 += diff * diff;
//                }
//                double v = Math.exp(-dist2 * invBw);
//                K.set(i, j, v);
//                K.set(j, i, v);
//            }
//        }

        DMatrixRMaj K = new DMatrixRMaj(n, n);

        // fill diagonal
        for (int i = 0; i < n; i++) K.set(i, i, 1.0);

        IntStream.range(0, n).parallel().forEach(i -> {
            for (int j = 0; j < i; j++) {
                double dist2 = 0.0;
                for (int k = 0; k < d; k++) {
                    double diff = Z[i][k] - Z[j][k];
                    dist2 += diff * diff;
                }
                double v = Math.exp(-dist2 * invBw);
                K.set(i, j, v);  // write only lower triangle in parallel
            }
        });

        // mirror single-thread
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                K.set(j, i, K.get(i, j));
            }
        }

        return K;
    }

    // -------------------- missingness row selection --------------------

    private int[] validRows(int[] vars) {
        int n = sampleSize;
        int[] tmp = new int[n];
        int m = 0;

        outer:
        for (int r = 0; r < n; r++) {
            for (int v : vars) {
                double val = zCols[v][r]; // zCols preserves NaN
                if (Double.isNaN(val)) continue outer;
            }
            tmp[m++] = r;
        }
        return Arrays.copyOf(tmp, m);
    }

    // -------------------- extraction + preprocessing --------------------

    private double[] extract1D(int varIndex, int[] rows, int n) {
        double[] x = new double[n];
        if (rows == null) {
            for (int r = 0; r < n; r++) x[r] = zCols[varIndex][r];
        } else {
            for (int r = 0; r < n; r++) x[r] = zCols[varIndex][rows[r]];
        }
        return x;
    }

    private static void centerInPlace(double[] y) {
        double m = 0.0;
        for (double v : y) m += v;
        m /= y.length;
        for (int i = 0; i < y.length; i++) y[i] -= m;
    }

    private static void zscoreColumnPreserveNaN(double[] in, double[] out) {
        double sum = 0.0, sum2 = 0.0;
        int n = 0;
        for (double v : in) {
            if (Double.isNaN(v)) continue;
            sum += v;
            sum2 += v * v;
            n++;
        }
        if (n < 2) {
            System.arraycopy(in, 0, out, 0, in.length);
            return;
        }
        double mean = sum / n;
        double var = (sum2 - n * mean * mean) / (n - 1.0);
        double sd = Math.sqrt(Math.max(1e-12, var));

        for (int i = 0; i < in.length; i++) {
            double v = in[i];
            out[i] = Double.isNaN(v) ? Double.NaN : (v - mean) / sd;
        }
    }

    private static void addDiagonalInPlace(DMatrixRMaj M, double v) {
        int n = Math.min(M.numRows, M.numCols);
        for (int i = 0; i < n; i++) M.add(i, i, v);
    }

    // Median of ||zi-zj||^2 using a subsample of rows for speed.
    private static double medianDistanceSquaredND(double[][] Z, int maxRows) {
        int n = Z.length;
        int d = Z[0].length;
        if (n < 3) return 1.0;

        int m = Math.min(n, maxRows);

        // Take evenly spaced rows (deterministic, no RNG).
        int[] idx = new int[m];
        if (m == n) {
            for (int i = 0; i < m; i++) idx[i] = i;
        } else {
            for (int i = 0; i < m; i++) idx[i] = (int) Math.floor((i * (long) (n - 1)) / (double) (m - 1));
        }

        int cnt = m * (m - 1) / 2;
        double[] d2 = new double[cnt];
        int t = 0;

        for (int a = 1; a < m; a++) {
            int i = idx[a];
            for (int b = 0; b < a; b++) {
                int j = idx[b];
                double dist2 = 0.0;
                for (int k = 0; k < d; k++) {
                    double diff = Z[i][k] - Z[j][k];
                    dist2 += diff * diff;
                }
                d2[t++] = dist2;
            }
        }

        Arrays.sort(d2, 0, t);

        int firstPos = 0;
        while (firstPos < t && d2[firstPos] <= 0) firstPos++;
        if (firstPos >= t) return 1.0;

        int mid = firstPos + (t - firstPos) / 2;
        return d2[mid];
    }

    // -------------------- small utilities --------------------

    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }

    private static int[] concat(int i, int[] parents) {
        int[] all = new int[parents.length + 1];
        all[0] = i;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    private static long cacheKey(int i, int[] parents) {
        long h = 1469598103934665603L;
        h = (h ^ i) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        return h;
    }
}
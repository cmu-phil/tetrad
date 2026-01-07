package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;
import org.ejml.interfaces.linsol.LinearSolverDense;

import java.util.*;

/**
 * RCIT-inspired local score for continuous variables.
 *
 * Idea:
 * - Map parent set Z to random Fourier features Phi(Z) for an RBF kernel (like RCIT/RCoT).
 * - Fit ridge regression of target X on Phi(Z).
 * - Use Gaussian log-likelihood with sigma^2 = RSS/n.
 * - Penalize with BIC-like term using effective degrees of freedom under ridge:
 *     df_eff = tr( A (A + lambda I)^(-1) ), where A = Phi^T Phi.
 *
 * Score convention matches Tetrad: higher is better.
 *
 * Notes:
 * - Missingness handled by filtering rows where all variables in {target} U parents are observed.
 * - Bandwidth sigma chosen via:
 *     PER_VARIABLE_MEDIAN: precompute sigma_j per variable, parent-set sigma = median(sigma_j over parents).
 *     PARENT_SET_MEDIAN: compute median pairwise distance in the parent space (like RCIT).
 * - Uses EJML, Cholesky/solves, avoids explicit inverses.
 */
public final class RffBicScore implements Score, EffectiveSampleSizeSettable {

    // -------------------- tuning knobs --------------------

    /** #RFF features for parent set Z (dimension of Phi(Z)). */
    private int numFeatZ = 200;

    /** Ridge parameter added to Phi^T Phi (must be > 0). */
    private double lambda = 1e-4;

    /** Penalty discount multiplier (like SemBicScore). */
    private double penaltyDiscount = 1.0;

    /** Whether to z-score raw variables and also z-score RFF columns. */
    private boolean centerFeatures = true;

    /** RNG seed (score is deterministic per (target, parents) given seed). */
    private long seed = 1729L;

    /** Max rows used for bandwidth computation (RCIT uses 500). */
    private int maxBandwidthRows = 500;

    /** Bandwidth selection mode. */
    private BandwidthMode bandwidthMode = BandwidthMode.PER_VARIABLE_MEDIAN;

    /** Small jitter added to A+lambdaI if Cholesky fails. */
    private double jitter = 1e-10;

    // -------------------- data --------------------

    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<Node, Integer> indexMap;
    private final int sampleSize;
    private final boolean calculateRowSubsets;

    /** Cached columns cols[var][row] (may contain NaNs). */
    private final double[][] cols;

    /** Effective sample size. */
    private int nEff;

    /** Precomputed per-variable bandwidths (median pairwise distance), used in PER_VARIABLE_MEDIAN mode. */
    private final double[] sigmaPerVar;

    /** Optional score cache. */
    private final Map<Long, Double> localScoreCache = new HashMap<>();

    // -------------------- construction --------------------

    public RffBicScore(DataSet dataSet) {
        this.dataSet = Objects.requireNonNull(dataSet, "dataSet");
        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        setEffectiveSampleSize(-1);

        this.indexMap = new HashMap<>();
        for (int i = 0; i < variables.size(); i++) indexMap.put(variables.get(i), i);

        this.calculateRowSubsets = dataSet.existsMissingValue();

        int p = variables.size();
        this.cols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            for (int r = 0; r < sampleSize; r++) {
                cols[j][r] = dataSet.getDouble(r, j);
            }
        }

        // Precompute sigma per variable (median pairwise distance on up to maxBandwidthRows rows),
        // ignoring NaNs.
        this.sigmaPerVar = new double[p];
        for (int j = 0; j < p; j++) {
            this.sigmaPerVar[j] = medianPairwiseDistance1D(j, null, Math.min(sampleSize, maxBandwidthRows));
            if (!(sigmaPerVar[j] > 0) || !Double.isFinite(sigmaPerVar[j])) sigmaPerVar[j] = 1.0;
        }
    }

    // -------------------- Score interface --------------------

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScore(int target, int... parents) {
        Arrays.sort(parents);

        long key = cacheKey(target, parents);
        Double cached = localScoreCache.get(key);
        if (cached != null) return cached;

        int[] all = concat(target, parents);
        int[] rows = calculateRowSubsets ? validRows(all) : null;

        int n = (rows == null) ? nEff : rows.length;
        if (n < 10) return Double.NaN;

        // Extract y (target column)
        double[] y = extract1D(target, rows, n);

        // Extract Z matrix (n x d) if parents nonempty
        int d = parents.length;
        double[][] Z = null;
        if (d > 0) {
            Z = extractND(parents, rows, n, d);
        }

        // Standardize y and Z (RCIT style), for stability.
        if (centerFeatures) {
            zscoreInPlace(y);
            if (Z != null) zscoreInPlace(Z);
        }

        // If no parents, score is just marginal Gaussian likelihood with 0 regressors
        if (d == 0) {
            double ll = gaussianLogLikFromRss(y, n, rssZeroModel(y));
            double score = 2.0 * ll; // no penalty
            localScoreCache.put(key, score);
            return score;
        }

        // Choose sigma for RFF
        double sigma = chooseSigma(parents, Z, n);
        if (!(sigma > 0) || !Double.isFinite(sigma)) sigma = 1.0;

        // Build Phi(Z): n x m
        int m = numFeatZ;
        DMatrixRMaj Phi = rffFeatures(Z, n, d, m, sigma, localRng(target, parents));

        if (centerFeatures) {
            zscoreColumnsInPlace(Phi);
        }

        // Ridge regression: beta = (Phi^T Phi + lambda I)^(-1) Phi^T y
        // Compute A = Phi^T Phi, b = Phi^T y
        DMatrixRMaj A = new DMatrixRMaj(m, m);
        CommonOps_DDRM.multTransA(Phi, Phi, A);

        // b is m x 1
        DMatrixRMaj b = new DMatrixRMaj(m, 1);
        multTransA_vec(Phi, y, b);

        // Solve (A + lambda I) beta = b
        DMatrixRMaj Ab = A.copy();
        addDiagonalInPlace(Ab, lambda);

        DMatrixRMaj beta = new DMatrixRMaj(m, 1);

        if (!solveSymPosDefWithJitter(Ab, b, beta)) {
            localScoreCache.put(key, Double.NaN);
            return Double.NaN;
        }

        // Compute residuals and RSS
        double rss = rssFromPhiBeta(Phi, beta, y);
        if (!(rss > 0) || !Double.isFinite(rss)) {
            localScoreCache.put(key, Double.NaN);
            return Double.NaN;
        }

        // Log-likelihood under Gaussian noise with sigma^2 = RSS/n
        double ll = gaussianLogLikFromRss(y, n, rss);

        // Effective degrees of freedom under ridge:
        // df = tr( A (A + lambda I)^(-1) ) where A = Phi^T Phi
        double df = effectiveDf(A, lambda);
        if (!(df >= 0) || !Double.isFinite(df)) df = m;

        // BIC-like score (Tetrad convention: higher is better)
        double score = 2.0 * ll - penaltyDiscount * df * Math.log(n);

        if (Double.isNaN(score) || Double.isInfinite(score)) score = Double.NaN;

        localScoreCache.put(key, score);
        return score;
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
        return (int) Math.ceil(Math.log(Math.max(3, nEff)));
    }

    @Override
    public boolean determines(List<Node> z, Node yNode) {
        int i = variables.indexOf(yNode);
        int[] parents = new int[z.size()];
        for (int t = 0; t < z.size(); t++) parents[t] = variables.indexOf(z.get(t));

        try {
            double s = localScore(i, parents);
            return Double.isNaN(s) || Double.isInfinite(s);
        } catch (RuntimeException e) {
            TetradLogger.getInstance().log(e.getMessage());
            return true;
        }
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
        clearCache();
    }

    @Override
    public String toString() {
        return "RCIT-RFF Ridge BIC Score (continuous)";
    }

    // -------------------- parameters (getters/setters) --------------------

    public int getNumFeatZ() { return numFeatZ; }
    public void setNumFeatZ(int numFeatZ) {
        this.numFeatZ = Math.max(1, numFeatZ);
        clearCache();
    }

    public double getLambda() { return lambda; }
    public void setLambda(double lambda) {
        if (lambda <= 0) throw new IllegalArgumentException("lambda must be > 0");
        this.lambda = lambda;
        clearCache();
    }

    public double getPenaltyDiscount() { return penaltyDiscount; }
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (penaltyDiscount <= 0) throw new IllegalArgumentException("penaltyDiscount must be > 0");
        this.penaltyDiscount = penaltyDiscount;
        clearCache();
    }

    public boolean isCenterFeatures() { return centerFeatures; }
    public void setCenterFeatures(boolean centerFeatures) {
        this.centerFeatures = centerFeatures;
        clearCache();
    }

    public long getSeed() { return seed; }
    public void setSeed(long seed) {
        this.seed = seed;
        clearCache();
    }

    public int getMaxBandwidthRows() { return maxBandwidthRows; }
    public void setMaxBandwidthRows(int maxBandwidthRows) {
        this.maxBandwidthRows = Math.max(10, maxBandwidthRows);
        clearCache();
    }

    public BandwidthMode getBandwidthMode() { return bandwidthMode; }
    public void setBandwidthMode(BandwidthMode bandwidthMode) {
        this.bandwidthMode = Objects.requireNonNull(bandwidthMode, "bandwidthMode");
        clearCache();
    }

    public double getJitter() { return jitter; }
    public void setJitter(double jitter) {
        if (jitter <= 0) throw new IllegalArgumentException("jitter must be > 0");
        this.jitter = jitter;
        clearCache();
    }

    // -------------------- enum --------------------

    public enum BandwidthMode {
        /** Precompute per-variable medians; parent-set sigma = median of parent sigmas. */
        PER_VARIABLE_MEDIAN,
        /** Compute median pairwise distance in the parent space (more RCIT-like, more expensive). */
        PARENT_SET_MEDIAN
    }

    // -------------------- internals --------------------

    private void clearCache() {
        localScoreCache.clear();
    }

    private Random localRng(int target, int[] parents) {
        long h = 1469598103934665603L;
        h = (h ^ seed) * 1099511628211L;
        h = (h ^ target) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        return new Random(h);
    }

    private double chooseSigma(int[] parents, double[][] Z, int n) {
        if (bandwidthMode == BandwidthMode.PER_VARIABLE_MEDIAN) {
            double[] s = new double[parents.length];
            for (int i = 0; i < parents.length; i++) s[i] = sigmaPerVar[parents[i]];
            Arrays.sort(s);
            return s[s.length / 2];
        } else {
            // Parent-set median in d-dim on up to maxBandwidthRows rows
            int r = Math.min(n, maxBandwidthRows);
            return medianPairwiseDistanceND(Z, r);
        }
    }

    private static double gaussianLogLikFromRss(double[] y, int n, double rss) {
        double sigma2 = rss / n;
        return -0.5 * n * (Math.log(2.0 * Math.PI * sigma2) + 1.0);
    }

    private static double rssZeroModel(double[] y) {
        // if centered, mean ~ 0; otherwise compute mean
        double mean = 0.0;
        for (double v : y) mean += v;
        mean /= y.length;
        double rss = 0.0;
        for (double v : y) {
            double e = v - mean;
            rss += e * e;
        }
        return rss;
    }

    private static double rssFromPhiBeta(DMatrixRMaj Phi, DMatrixRMaj beta, double[] y) {
        int n = Phi.numRows;
        int m = Phi.numCols;
        double rss = 0.0;

        for (int i = 0; i < n; i++) {
            double fit = 0.0;
            int idx = i * m;
            for (int j = 0; j < m; j++) {
                fit += Phi.data[idx + j] * beta.get(j, 0);
            }
            double e = y[i] - fit;
            rss += e * e;
        }
        return rss;
    }

    /**
     * df = tr( A (A + lambda I)^(-1) ) = tr( X ), where (A + lambda I) X = A.
     */
    private double effectiveDf(DMatrixRMaj A, double lambda) {
        int m = A.numRows;
        DMatrixRMaj M = A.copy();
        addDiagonalInPlace(M, lambda);

        // Solve M X = A
        DMatrixRMaj X = new DMatrixRMaj(m, m);

        LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.symmPosDef(m);
        if (!solver.setA(M)) {
            // try jitter
            DMatrixRMaj M2 = M.copy();
            addDiagonalInPlace(M2, jitter);
            if (!solver.setA(M2)) return Double.NaN;
        }

        solver.solve(A, X);

        double tr = 0.0;
        for (int i = 0; i < m; i++) tr += X.get(i, i);
        return tr;
    }

    private boolean solveSymPosDefWithJitter(DMatrixRMaj A, DMatrixRMaj B, DMatrixRMaj X) {
        int n = A.numRows;

        // Prefer a solver
        LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.symmPosDef(n);
        if (solver.setA(A)) {
            solver.solve(B, X);
            return true;
        }

        // Fall back: try Cholesky with increasing jitter
        double eps = jitter;
        for (int k = 0; k < 6; k++) {
            DMatrixRMaj Aj = A.copy();
            addDiagonalInPlace(Aj, eps);
            CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
            if (chol.decompose(Aj)) {
                if (solver.setA(Aj)) {
                    solver.solve(B, X);
                    return true;
                }
            }
            eps *= 10.0;
        }
        return false;
    }

    private static void addDiagonalInPlace(DMatrixRMaj M, double v) {
        int n = Math.min(M.numRows, M.numCols);
        for (int i = 0; i < n; i++) {
            M.add(i, i, v);
        }
    }

    /**
     * RFF features for RBF kernel:
     *   Phi = sqrt(2/m) * cos( W z + b )
     * with W ~ N(0, 1/sigma), b ~ Uniform(0, 2π).
     */
    private static DMatrixRMaj rffFeatures(double[][] Z, int n, int d, int m, double sigma, Random rng) {
        double sd = 1.0 / sigma;

        // W is m x d, b is m
        double[][] W = new double[m][d];
        double[] b = new double[m];

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < d; j++) W[i][j] = rng.nextGaussian() * sd;
            b[i] = rng.nextDouble() * 2.0 * Math.PI;
        }

        DMatrixRMaj Phi = new DMatrixRMaj(n, m);
        double scale = Math.sqrt(2.0 / m);

        for (int i = 0; i < n; i++) {
            double[] zi = Z[i];
            for (int f = 0; f < m; f++) {
                double dot = 0.0;
                double[] wf = W[f];
                for (int j = 0; j < d; j++) dot += wf[j] * zi[j];
                Phi.set(i, f, scale * Math.cos(dot + b[f]));
            }
        }

        return Phi;
    }

    private static void zscoreInPlace(double[] x) {
        int n = x.length;
        if (n < 2) return;
        double sum = 0.0, sumsq = 0.0;
        for (double v : x) { sum += v; sumsq += v * v; }
        double mean = sum / n;
        double var = (sumsq - n * mean * mean) / (n - 1);
        double sd = (var > 0) ? Math.sqrt(var) : 1.0;
        for (int i = 0; i < n; i++) x[i] = (x[i] - mean) / sd;
    }

    private static void zscoreInPlace(double[][] X) {
        int n = X.length;
        if (n == 0) return;
        int d = X[0].length;
        if (n < 2 || d == 0) return;

        for (int j = 0; j < d; j++) {
            double sum = 0.0, sumsq = 0.0;
            for (int i = 0; i < n; i++) {
                double v = X[i][j];
                sum += v;
                sumsq += v * v;
            }
            double mean = sum / n;
            double var = (sumsq - n * mean * mean) / (n - 1);
            double sd = (var > 0) ? Math.sqrt(var) : 1.0;

            for (int i = 0; i < n; i++) {
                X[i][j] = (X[i][j] - mean) / sd;
            }
        }
    }

    private static void zscoreColumnsInPlace(DMatrixRMaj M) {
        int n = M.numRows, d = M.numCols;
        if (n < 2 || d == 0) return;

        for (int j = 0; j < d; j++) {
            double sum = 0.0, sumsq = 0.0;
            for (int i = 0; i < n; i++) {
                double v = M.get(i, j);
                sum += v;
                sumsq += v * v;
            }
            double mean = sum / n;
            double var = (sumsq - n * mean * mean) / (n - 1);
            double sd = (var > 0) ? Math.sqrt(var) : 1.0;

            for (int i = 0; i < n; i++) {
                M.set(i, j, (M.get(i, j) - mean) / sd);
            }
        }
    }

    // -------------------- bandwidth helpers --------------------

    private double medianPairwiseDistance1D(int varIndex, int[] rows, int limit) {
        // collect up to "limit" non-NaN values
        double[] tmp = new double[limit];
        int m = 0;

        if (rows == null) {
            for (int r = 0; r < sampleSize && m < limit; r++) {
                double v = cols[varIndex][r];
                if (Double.isNaN(v)) continue;
                tmp[m++] = v;
            }
        } else {
            for (int k = 0; k < rows.length && m < limit; k++) {
                double v = cols[varIndex][rows[k]];
                if (Double.isNaN(v)) continue;
                tmp[m++] = v;
            }
        }

        if (m < 3) return 1.0;
        tmp = Arrays.copyOf(tmp, m);

        double[] dists = new double[m * (m - 1) / 2];
        int idx = 0;
        for (int i = 1; i < m; i++) {
            double xi = tmp[i];
            for (int j = 0; j < i; j++) {
                double dist = Math.abs(xi - tmp[j]);
                if (dist > 0 && Double.isFinite(dist)) dists[idx++] = dist;
            }
        }
        if (idx == 0) return 1.0;

        Arrays.sort(dists, 0, idx);
        return dists[idx / 2];
    }

    private static double medianPairwiseDistanceND(double[][] Z, int limit) {
        int n = Math.min(Z.length, limit);
        if (n < 3) return 1.0;
        int d = Z[0].length;
        if (d == 0) return 1.0;

        double[] dists = new double[n * (n - 1) / 2];
        int idx = 0;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double ss = 0.0;
                for (int k = 0; k < d; k++) {
                    double diff = Z[i][k] - Z[j][k];
                    ss += diff * diff;
                }
                double dist = Math.sqrt(ss);
                if (dist > 0 && Double.isFinite(dist)) dists[idx++] = dist;
            }
        }
        if (idx == 0) return 1.0;

        Arrays.sort(dists, 0, idx);
        return dists[idx / 2];
    }

    // -------------------- missingness row filtering --------------------

    private int[] validRows(int[] vars) {
        int[] tmp = new int[sampleSize];
        int m = 0;

        outer:
        for (int r = 0; r < sampleSize; r++) {
            for (int v : vars) {
                double val = cols[v][r];
                if (Double.isNaN(val)) continue outer;
            }
            tmp[m++] = r;
        }

        return Arrays.copyOf(tmp, m);
    }

    // -------------------- extraction helpers --------------------

    private double[] extract1D(int varIndex, int[] rows, int n) {
        double[] x = new double[n];
        if (rows == null) {
            // use first nEff rows (assumed complete)
            for (int i = 0; i < n; i++) x[i] = cols[varIndex][i];
        } else {
            for (int i = 0; i < n; i++) x[i] = cols[varIndex][rows[i]];
        }
        return x;
    }

    private double[][] extractND(int[] vars, int[] rows, int n, int d) {
        double[][] Z = new double[n][d];
        if (rows == null) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < d; j++) Z[i][j] = cols[vars[j]][i];
            }
        } else {
            for (int i = 0; i < n; i++) {
                int r = rows[i];
                for (int j = 0; j < d; j++) Z[i][j] = cols[vars[j]][r];
            }
        }
        return Z;
    }

    // -------------------- small utilities --------------------

    private static void multTransA_vec(DMatrixRMaj A, double[] x, DMatrixRMaj out) {
        // out = A^T x, where A is n x m, x is n
        int n = A.numRows;
        int m = A.numCols;
        if (out.numRows != m || out.numCols != 1) throw new IllegalArgumentException("out dim mismatch");

        Arrays.fill(out.data, 0.0);
        for (int i = 0; i < n; i++) {
            double xi = x[i];
            int idx = i * m;
            for (int j = 0; j < m; j++) {
                out.data[j] += A.data[idx + j] * xi;
            }
        }
    }

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

    private static long cacheKey(int target, int[] parents) {
        long h = 1469598103934665603L;
        h = (h ^ target) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        return h;
    }
}
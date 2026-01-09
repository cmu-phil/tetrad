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

import static edu.cmu.tetrad.search.score.KcvBicScore.symmetrizeInPlace;

/**
 * Huang et al. (kernel-based) marginal score for continuous variables.
 *
 * Implements the marginal log-likelihood score (Huang et al., Eq. 6) for
 * a variable X regressed on its parent set Z using kernel ridge regression /
 * Gaussian process style marginal likelihood.
 *
 * Continuous-only version.
 *
 * Notes:
 * - Uses RBF kernels with median heuristic bandwidth.
 * - Uses EJML; avoids explicit inverses (Cholesky/solves).
 * - Handles missingness via row filtering (testwise deletion on {i} ∪ parents).
 *
 * Higher is better, consistent with Tetrad Score conventions.
 */
public final class HuangMarginalScore implements Score, EffectiveSampleSizeSettable {

    // -------------------- configuration --------------------

    /** Ridge parameter (lambda). Huang uses n*lambda in the stabilization term. Must be > 0. */
    private double lambda = 1e-3;

    /** Small jitter added to G for logdet stability; will be adaptively increased if needed. */
    private double jitter = 1e-10;

    /** If true, compute valid row subsets when missing values exist. */
    private final boolean calculateRowSubsets;

    // -------------------- data --------------------

    private final DataSet dataSet;
    private final List<Node> variables;
    private final Map<Node, Integer> indexMap;
    private final int sampleSize;

    /** Cached columns (full length, may include NaNs). cols[varIndex][row]. */
    private final double[][] cols;

    /** Effective sample size (defaults to sampleSize). */
    private int nEff;

    // Optional: lightweight cache to avoid recomputing kernels for repeated parent sets.
    // Key is (target i, parents array) -> score.
    // You can delete this if you’d rather not cache at first.
    private final Map<Long, Double> localScoreCache = new HashMap<>();

    // -------------------- construction --------------------

    public HuangMarginalScore(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("dataSet");
        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        setEffectiveSampleSize(-1);

        this.indexMap = new HashMap<>();
        for (int i = 0; i < variables.size(); i++) indexMap.put(variables.get(i), i);

        this.calculateRowSubsets = dataSet.existsMissingValue();

        // Extract columns once for speed.
        int p = variables.size();
        this.cols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            for (int r = 0; r < sampleSize; r++) {
                cols[j][r] = dataSet.getDouble(r, j);
            }
        }
    }

    // -------------------- Score interface --------------------

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        // score(y | z ∪ {x}) - score(y | z)
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScore(int i, int... parents) {
        Arrays.sort(parents);

        long key = cacheKey(i, parents);
        Double cached = localScoreCache.get(key);
        if (cached != null) return cached;

        // Valid rows for {i} ∪ parents, only if missing values exist.
        int[] all = concat(i, parents);
        int[] rows = calculateRowSubsets ? validRows(all) : null;

        // If too few rows, return NaN (FGES will typically ignore / treat as determinism).
        int n = (rows == null) ? nEff : rows.length;
        if (n < 3) return Double.NaN;

        // Build Kx (n x n) from target column, and Kz from parent matrix (n x n).
        DMatrixRMaj Kx = rbfGram1D(i, rows);
        DMatrixRMaj Kz = (parents.length == 0) ? zeroGram(n) : rbfGramND(parents, rows);

        // Center both.
        centerInPlace(Kx);
        centerInPlace(Kz);

        // Score via Huang Eq. (6), using Appendix A1 formula:
        // SigmaHat = (n*lambda/2) * Kx * (Kz + n*lambda I)^(-2) * Kx
        // We compute D = (Kz + n*lambda I)^(-1) * Kx, then G = D^T D = Kx * A^(-2) * Kx.
        double ll = huangLogLikelihoodFromCenteredKernels(Kx, Kz, n, lambda, jitter);

        // Cache & return.
        localScoreCache.put(key, ll);
        return ll;
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
        // Similar heuristic to SEM BIC: ceil(log(n)).
        // Adjust if you want something else.
        return (int) Math.ceil(Math.log(Math.max(3, nEff)));
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        int i = variables.indexOf(y);
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
    }

    @Override
    public String toString() {
        return "Huang Kernel Marginal Score (continuous)";
    }

    // -------------------- public tuning knobs --------------------

    public double getLambda() {
        return lambda;
    }

    public void setLambda(double lambda) {
        if (lambda <= 0) throw new IllegalArgumentException("lambda must be > 0");
        this.lambda = lambda;
        localScoreCache.clear();
    }

    public double getJitter() {
        return jitter;
    }

    public void setJitter(double jitter) {
        if (jitter <= 0) throw new IllegalArgumentException("jitter must be > 0");
        this.jitter = jitter;
        localScoreCache.clear();
    }

    // -------------------- core Huang computation --------------------

    private static double logDetSymPosDef(DMatrixRMaj A, double jitter) {
        int n = A.numRows;

        LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.symmPosDef(n);

        // Try with escalating jitter to ensure SPD numerically
        double eps = 0.0;
        for (int k = 0; k < 6; k++) {
            DMatrixRMaj Aj = A.copy();
            if (eps > 0) {
                for (int i = 0; i < n; i++) Aj.add(i, i, eps);
            }
            if (solver.setA(Aj)) {
                // We need the Cholesky factor diagonal; EJML solvers hide it,
                // so use a decomposition directly:
                CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
                if (!chol.decompose(Aj)) {
                    eps = (eps == 0.0) ? jitter : eps * 10.0;
                    continue;
                }
                DMatrixRMaj L = chol.getT(null); // lower-triangular
                double sumLogDiag = 0.0;
                for (int i = 0; i < n; i++) {
                    double di = L.get(i, i);
                    if (!(di > 0) || !Double.isFinite(di)) return Double.NaN;
                    sumLogDiag += Math.log(di);
                }
                return 2.0 * sumLogDiag;
            }
            eps = (eps == 0.0) ? jitter : eps * 10.0;
        }

        return Double.NaN;
    }

    private double huangLogLikelihoodFromCenteredKernels(DMatrixRMaj KxCentered,
                                                         DMatrixRMaj KzCentered,
                                                         int n,
                                                         double sigma2,   // <-- interpret this as \hat\sigma_i^2 (noise variance), NOT "ridge lambda"
                                                         double jitter) {
        if (n <= 0) return Double.NaN;
        if (!(sigma2 > 0) || !Double.isFinite(sigma2)) return Double.NaN;

        // A = Kz + sigma^2 I
        DMatrixRMaj A = KzCentered.copy();
        addDiagonalInPlace(A, sigma2);

        // We need:
        //  (1) traceTerm = tr( Kx * A^{-1} * Kx )
        //  (2) logDetA  = log |A|
        //
        // Do both stably using Cholesky on A (with escalating jitter if needed).

        // Factorize A (SPD) with jitter escalation if needed
        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);

        double eps = 0.0;
        DMatrixRMaj Af = A;

        boolean ok = false;
        for (int k = 0; k < 7; k++) {
            Af = (k == 0) ? A : A.copy();
            if (k > 0) addDiagonalInPlace(Af, eps);

            if (chol.decompose(Af)) {
                ok = true;
                break;
            }
            eps = (eps == 0.0) ? jitter : eps * 10.0;
        }
        if (!ok) return Double.NaN;

        // logdet(A) from Cholesky: log|A| = 2 * sum log diag(L)
        double logDetA = logDetFromCholesky(chol, n);
        if (!Double.isFinite(logDetA)) return Double.NaN;

        // Solve A X = Kx  => X = A^{-1} Kx, using the same factorization via a solver
        LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.symmPosDef(n);
        if (!solver.setA(Af)) return Double.NaN;

        DMatrixRMaj X = new DMatrixRMaj(n, n);
        solver.solve(KxCentered, X);

        // traceTerm = tr( Kx * X ) = sum_ij Kx_ij * X_ij  (Frobenius inner product)
        double traceTerm = frobeniusDot(KxCentered, X);
        if (!Double.isFinite(traceTerm)) return Double.NaN;

        // Huang Eq.(9), up to an additive constant:
        // S = -1/2 * traceTerm - (n/2) * logDetA + const
        //
        // You may omit constants entirely. If you want them:
        // const = - (n*n/2)*log(2*pi)
        double score = -0.5 * traceTerm - 0.5 * n * logDetA;

        return score;
    }

    private static void addDiagonalInPlace(DMatrixRMaj M, double v) {
        int n = Math.min(M.numRows, M.numCols);
        for (int i = 0; i < n; i++) {
            M.add(i, i, v);
        }
    }

    private static double logDetFromCholesky(CholeskyDecomposition_F64<DMatrixRMaj> chol, int n) {
        // chol.getT(null) returns the triangular factor (lower if chol(true))
        DMatrixRMaj L = chol.getT(null);
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            double di = L.get(i, i);
            if (!(di > 0) || !Double.isFinite(di)) return Double.NaN;
            sum += Math.log(di);
        }
        return 2.0 * sum;
    }

    private static double frobeniusDot(DMatrixRMaj A, DMatrixRMaj B) {
        if (A.numRows != B.numRows || A.numCols != B.numCols) {
            throw new IllegalArgumentException("Dimension mismatch in frobeniusDot");
        }
        double s = 0.0;
        int len = A.getNumElements();
        for (int i = 0; i < len; i++) {
            s += A.data[i] * B.data[i];
        }
        return s;
    }

    // -------------------- kernels --------------------

    private DMatrixRMaj rbfGram1D(int varIndex, int[] rows) {
        int n = (rows == null) ? nEff : rows.length;
        double[] x = extract1D(varIndex, rows);

        double bw2 = medianDistanceSquared1D(x);
        // Fallback if degenerate.
        if (!(bw2 > 0)) bw2 = 1.0;

        // RBF: exp( -||xi-xj||^2 / (2*sigma^2) ), with sigma^2 = bw2/2  => exp( -||..||^2 / bw2 )
        double invBw = 1.0 / bw2;

        DMatrixRMaj K = new DMatrixRMaj(n, n);
        for (int i = 0; i < n; i++) {
            K.set(i, i, 1.0);
            double xi = x[i];
            for (int j = 0; j < i; j++) {
                double d = xi - x[j];
                double v = Math.exp(-(d * d) * invBw);
                K.set(i, j, v);
                K.set(j, i, v);
            }
        }
        return K;
    }

    private DMatrixRMaj rbfGramND(int[] parentIdx, int[] rows) {
        int n = (rows == null) ? nEff : rows.length;
        int d = parentIdx.length;

        // Extract Z matrix: n x d
        double[][] Z = new double[n][d];
        for (int r = 0; r < n; r++) {
            int row = (rows == null) ? r : rows[r];
            for (int j = 0; j < d; j++) {
                Z[r][j] = cols[parentIdx[j]][row];
            }
        }

        double bw2 = medianDistanceSquaredND(Z);
        if (!(bw2 > 0)) bw2 = 1.0;
        double invBw = 1.0 / bw2;

        DMatrixRMaj K = new DMatrixRMaj(n, n);
        for (int i = 0; i < n; i++) {
            K.set(i, i, 1.0);
            for (int j = 0; j < i; j++) {
                double dist2 = 0.0;
                for (int k = 0; k < d; k++) {
                    double diff = Z[i][k] - Z[j][k];
                    dist2 += diff * diff;
                }
                double v = Math.exp(-dist2 * invBw);
                K.set(i, j, v);
                K.set(j, i, v);
            }
        }

        return K;
    }

    private static DMatrixRMaj zeroGram(int n) {
        return new DMatrixRMaj(n, n);
    }

    // -------------------- centering --------------------

    /** Centers a symmetric Gram matrix in-place: K <- H K H, where H = I - (1/n)11^T. */
    private static void centerInPlace(DMatrixRMaj K) {
        int n = K.numRows;
        if (n == 0) return;

        double[] rowMean = new double[n];
        double grand = 0.0;

        // Row means and grand mean.
        for (int i = 0; i < n; i++) {
            double s = 0.0;
            for (int j = 0; j < n; j++) s += K.get(i, j);
            rowMean[i] = s / n;
            grand += s;
        }
        grand /= (double) (n * n);

        // Since K is symmetric, colMean == rowMean, but we’ll keep it explicit.
        double[] colMean = rowMean;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double v = K.get(i, j) - rowMean[i] - colMean[j] + grand;
                K.set(i, j, v);
            }
        }
    }

    // -------------------- row selection for missingness --------------------

    private int[] validRows(int[] vars) {
        // vars are column indices; keep rows where all vars are non-NaN
        int n = sampleSize;
        int[] tmp = new int[n];
        int m = 0;

        outer:
        for (int r = 0; r < n; r++) {
            for (int v : vars) {
                double val = cols[v][r];
                if (Double.isNaN(val)) continue outer;
            }
            tmp[m++] = r;
        }

        return Arrays.copyOf(tmp, m);
    }

    // -------------------- bandwidth heuristics --------------------

    private static double medianDistanceSquared1D(double[] x) {
        int n = x.length;
        if (n < 3) return 1.0;

        // Collect upper-triangular distances^2 (excluding zeros if possible).
        double[] d2 = new double[n * (n - 1) / 2];
        int idx = 0;
        for (int i = 1; i < n; i++) {
            double xi = x[i];
            for (int j = 0; j < i; j++) {
                double d = xi - x[j];
                double v = d * d;
                d2[idx++] = v;
            }
        }

        Arrays.sort(d2, 0, idx);

        // Median of nonzero if possible.
        int firstPos = 0;
        while (firstPos < idx && d2[firstPos] <= 0) firstPos++;
        if (firstPos >= idx) return 1.0;

        int mid = firstPos + (idx - firstPos) / 2;
        return d2[mid];
    }

    private static double medianDistanceSquaredND(double[][] Z) {
        int n = Z.length;
        int d = Z[0].length;
        if (n < 3) return 1.0;

        double[] d2 = new double[n * (n - 1) / 2];
        int idx = 0;

        for (int i = 1; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double dist2 = 0.0;
                for (int k = 0; k < d; k++) {
                    double diff = Z[i][k] - Z[j][k];
                    dist2 += diff * diff;
                }
                d2[idx++] = dist2;
            }
        }

        Arrays.sort(d2, 0, idx);

        int firstPos = 0;
        while (firstPos < idx && d2[firstPos] <= 0) firstPos++;
        if (firstPos >= idx) return 1.0;

        int mid = firstPos + (idx - firstPos) / 2;
        return d2[mid];
    }

    // -------------------- column extraction --------------------

    private double[] extract1D(int varIndex, int[] rows) {
        int n = (rows == null) ? nEff : rows.length;
        double[] x = new double[n];

        if (rows == null) {
            // Use first nEff rows (standard Tetrad assumes full rows; adjust if you prefer).
            for (int r = 0; r < n; r++) x[r] = cols[varIndex][r];
        } else {
            for (int r = 0; r < n; r++) x[r] = cols[varIndex][rows[r]];
        }

        return x;
    }

    // -------------------- small utilities --------------------

    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }

    @Override
    public double localScoreDiff(int x, int y) {
        return Score.super.localScoreDiff(x, y);
    }

    @Override
    public double localScore(int node, int parent) {
        return Score.super.localScore(node, parent);
    }

    @Override
    public double localScore(int node) {
        return Score.super.localScore(node);
    }

    @Override
    public Node getVariable(String targetName) {
        return Score.super.getVariable(targetName);
    }

    private static int[] concat(int i, int[] parents) {
        int[] all = new int[parents.length + 1];
        all[0] = i;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    private static long cacheKey(int i, int[] parents) {
        // Simple stable hash -> 64-bit key. Good enough for an in-memory cache.
        long h = 1469598103934665603L;
        h = (h ^ i) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        return h;
    }
}
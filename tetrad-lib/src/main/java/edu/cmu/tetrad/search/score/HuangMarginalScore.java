package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.SpecializedOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.EigenDecomposition_F64;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;

import java.util.*;

/**
 * Huang et al. (kernel-based) marginal score for continuous variables.
 *
 * Drop-in replacement with stability improvements:
 *  - Standardizes inputs before distance computations (bandwidth robustness).
 *  - Symmetrizes kernels after centering.
 *  - Uses a small bandwidth grid (around median) and selects the most stable candidate
 *    (maximizes logdet(Kz + tau I); skips ill-conditioned).
 *  - Uses tau as the primary ridge knob: A = Kz + tau I (tau = n * lambda by default).
 *  - Never forms explicit inverses (solve via Cholesky / SPD solver).
 *  - Robust logdet(PSD) with: (i) adaptive diagonal jitter; (ii) PSD-eigen clamp fallback.
 *
 * Continuous-only version.
 *
 * Higher is better, consistent with Tetrad Score conventions.
 */
public final class HuangMarginalScore implements Score, EffectiveSampleSizeSettable {

    // -------------------- configuration --------------------

    /**
     * User-facing ridge parameter. Internally we use tau = n * lambda as default,
     * but you can also switch to fixed-tau by setting useNScaledTau=false.
     */
    private double lambda = 1e-3;

    /** Base jitter scale (relative to trace/n). */
    private double jitterRel = 1e-12;

    /** If true, uses tau = n*lambda. If false, uses tau = lambda (treat lambda as tau). */
    private boolean useNScaledTau = true;

    /** Bandwidth multipliers around median for a cheap robust grid search. */
    private double[] bwMultipliers = new double[]{0.25, 0.5, 1.0, 2.0, 4.0};

    /** Condition-number cap (approx) for rejecting near-singular A = Kz + tau I. */
    private double condCap = 1e12;

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

    /** Cache: (target i, parents array) -> score. */
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
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScore(int i, int... parents) {
        Arrays.sort(parents);

        long key = cacheKey(i, parents);
        Double cached = localScoreCache.get(key);
        if (cached != null) return cached;

        int[] all = concat(i, parents);
        int[] rows = calculateRowSubsets ? validRows(all) : null;

        int n = (rows == null) ? nEff : rows.length;
        if (n < 3) return Double.NaN;

        // Build centered Kx once (1D) with robust bandwidth selection (median grid).
        // Note: the Huang formulation you coded uses Kx in both places; we keep that.
        DMatrixRMaj Kx = buildCenteredRbfGram1D_Standardized(i, rows, /*withBandwidthGrid*/ true);
        if (Kx == null) return Double.NaN;

        // Build centered Kz (parents) with bandwidth grid + stability selection.
        DMatrixRMaj Kz;
        if (parents.length == 0) {
            Kz = new DMatrixRMaj(n, n); // zeros
        } else {
            Kz = buildCenteredRbfGramND_Standardized(parents, rows, /*withBandwidthGrid*/ true);
            if (Kz == null) return Double.NaN;
        }

        double ll = huangLogLikelihoodFromCenteredKernels(Kx, Kz, n);

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
        return "Huang Kernel Marginal Score (continuous, stabilized)";
    }

    // -------------------- public tuning knobs --------------------

    public double getLambda() { return lambda; }

    public void setLambda(double lambda) {
        if (lambda <= 0) throw new IllegalArgumentException("lambda must be > 0");
        this.lambda = lambda;
        localScoreCache.clear();
    }

    public double getJitterRel() { return jitterRel; }

    public void setJitterRel(double jitterRel) {
        if (jitterRel <= 0) throw new IllegalArgumentException("jitterRel must be > 0");
        this.jitterRel = jitterRel;
        localScoreCache.clear();
    }

    public boolean isUseNScaledTau() { return useNScaledTau; }

    public void setUseNScaledTau(boolean useNScaledTau) {
        this.useNScaledTau = useNScaledTau;
        localScoreCache.clear();
    }

    public double[] getBwMultipliers() { return bwMultipliers.clone(); }

    public void setBwMultipliers(double[] bwMultipliers) {
        if (bwMultipliers == null || bwMultipliers.length == 0) {
            throw new IllegalArgumentException("bwMultipliers must be non-empty");
        }
        this.bwMultipliers = bwMultipliers.clone();
        localScoreCache.clear();
    }

    public double getCondCap() { return condCap; }

    public void setCondCap(double condCap) {
        if (condCap <= 1) throw new IllegalArgumentException("condCap must be > 1");
        this.condCap = condCap;
        localScoreCache.clear();
    }

    // -------------------- core Huang computation --------------------

    /**
     * Your original computation, but stabilized:
     *  - A = Kz + tau I (tau = n*lambda or lambda)
     *  - D = solve(A, Kx)
     *  - G = D^T D
     *  - logdet(G) computed robustly (jitter + PSD clamp fallback)
     */
    private double huangLogLikelihoodFromCenteredKernels(DMatrixRMaj Kx, DMatrixRMaj Kz, int n) {
        // Ensure symmetry (centering can introduce slight asymmetry)
        symmetrizeInPlace(Kx);
        symmetrizeInPlace(Kz);

        double tau = useNScaledTau ? (n * lambda) : lambda;
        if (!(tau > 0)) return Double.NaN;

        // A = Kz + tau I
        DMatrixRMaj A = Kz.copy();
        addToDiagonalInPlace(A, tau);

        // Solve D = A^{-1} Kx
        DMatrixRMaj D = new DMatrixRMaj(n, n);

        // Use SPD solver; if it fails, PSD-clamp A and retry with eigen-based solve.
        if (!solveSPD(A, Kx, D)) {
            // fallback: PSD clamp A then try again
            DMatrixRMaj Aclamp = psdClamp(A, /*floorRel*/ 1e-14);
            if (Aclamp == null) return Double.NaN;
            if (!solveSPD(Aclamp, Kx, D)) {
                // final fallback: eigen-solve (stable, slower)
                if (!solveViaEigendecomp(Aclamp, Kx, D, /*floorRel*/ 1e-14)) return Double.NaN;
            }
        }

        // G = D^T D (PSD)
        DMatrixRMaj G = new DMatrixRMaj(n, n);
        CommonOps_DDRM.multTransA(D, D, G);
        symmetrizeInPlace(G);

        // Scale-aware jitter: eps = jitterRel * trace(G)/n (and increase if needed)
        double traceG = Math.max(0.0, CommonOps_DDRM.trace(G));
        double baseEps = Math.max(1e-300, jitterRel * (traceG / Math.max(1, n)));

        // Reject obviously degenerate case early
        if (!(traceG > 0)) return Double.NaN;

        // Compute logdet(G) robustly; also get an approximate condition number of A
        double logDetG = logDetPSD_Robust(G, baseEps);
        if (!Double.isFinite(logDetG)) return Double.NaN;

        // Optional: reject if A is extremely ill-conditioned (approx via eig clamp)
        // (Cheap-ish and helps avoid weird score spikes.)
        double condA = approximateCondSPD(A, /*floorRel*/ 1e-14);
        if (Double.isFinite(condA) && condA > condCap) {
            return Double.NaN;
        }

        // log |SigmaHat| = n*log(tau/2) + log|G|
        double logDetSigma = n * Math.log(tau / 2.0) + logDetG;

        // ll = -(n/2)*log(2π) -(n/2)*log|SigmaHat| -(n/2)
        return -0.5 * n * Math.log(2.0 * Math.PI) - 0.5 * n * logDetSigma - 0.5 * n;
    }

    // -------------------- robust linear algebra helpers --------------------

    private static boolean solveSPD(DMatrixRMaj A, DMatrixRMaj B, DMatrixRMaj X) {
        LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.symmPosDef(A.numRows);
        if (!solver.setA(A)) {
            // try adding a tiny diagonal loading and retry
            DMatrixRMaj Aj = A.copy();
            double tr = Math.max(0.0, CommonOps_DDRM.trace(Aj));
            double eps = Math.max(1e-12, 1e-12 * (tr / Math.max(1, Aj.numRows)));
            addToDiagonalInPlace(Aj, eps);
            if (!solver.setA(Aj)) return false;
        }
        solver.solve(B, X);
        return true;
    }

    private static boolean solveViaEigendecomp(DMatrixRMaj A, DMatrixRMaj B, DMatrixRMaj X, double floorRel) {
        int n = A.numRows;
        EigenDecomposition_F64<DMatrixRMaj> eig = DecompositionFactory_DDRM.eig(n, true);
        if (!eig.decompose(A)) return false;

        // Build Q and invLambda
        DMatrixRMaj Q = new DMatrixRMaj(n, n);
        double[] lam = new double[n];

        for (int i = 0; i < n; i++) {
            var ev = eig.getEigenVector(i);
            if (ev == null) return false; // complex eigenpair
            for (int r = 0; r < n; r++) Q.set(r, i, ev.get(r, 0));
            lam[i] = eig.getEigenvalue(i).getReal();
        }

        // Scale-aware floor: eps = floorRel * trace(A)/n
        double tr = Math.max(0.0, CommonOps_DDRM.trace(A));
        double floor = Math.max(1e-300, floorRel * (tr / Math.max(1, n)));

        // Y = Q^T B
        DMatrixRMaj Y = new DMatrixRMaj(n, B.numCols);
        CommonOps_DDRM.multTransA(Q, B, Y);

        // Y <- invLambda * Y
        for (int i = 0; i < n; i++) {
            double li = lam[i];
            if (!(li > 0)) li = floor;
            if (li < floor) li = floor;
            double inv = 1.0 / li;
            for (int c = 0; c < Y.numCols; c++) {
                Y.set(i, c, inv * Y.get(i, c));
            }
        }

        // X = Q Y
        CommonOps_DDRM.mult(Q, Y, X);
        return true;
    }

    private static double logDetPSD_Robust(DMatrixRMaj M, double baseEps) {
        int n = M.numRows;

        // Try Cholesky with increasing jitter
        double eps = baseEps;
        for (int attempt = 0; attempt < 6; attempt++) {
            DMatrixRMaj A = M.copy();
            addToDiagonalInPlace(A, eps);
            symmetrizeInPlace(A);

            CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);
            if (chol.decompose(A)) {
                DMatrixRMaj L = chol.getT(null);
                double sumLogDiag = 0.0;
                for (int i = 0; i < n; i++) {
                    double di = L.get(i, i);
                    if (!(di > 0) || !Double.isFinite(di)) return Double.NaN;
                    sumLogDiag += Math.log(di);
                }
                return 2.0 * sumLogDiag;
            }
            eps *= 10.0;
        }

        // Fallback: eigen clamp
        DMatrixRMaj A = M.copy();
        symmetrizeInPlace(A);
        EigenDecomposition_F64<DMatrixRMaj> eig = DecompositionFactory_DDRM.eig(n, false);
        if (!eig.decompose(A)) return Double.NaN;

        double tr = Math.max(0.0, CommonOps_DDRM.trace(A));
        double floor = Math.max(baseEps, 1e-14 * (tr / Math.max(1, n)));

        double logdet = 0.0;
        for (int i = 0; i < n; i++) {
            double li = eig.getEigenvalue(i).getReal();
            if (!Double.isFinite(li)) return Double.NaN;
            if (li < floor) li = floor;
            logdet += Math.log(li);
        }
        return logdet;
    }

    private static DMatrixRMaj psdClamp(DMatrixRMaj A, double floorRel) {
        int n = A.numRows;
        DMatrixRMaj S = A.copy();
        symmetrizeInPlace(S);

        EigenDecomposition_F64<DMatrixRMaj> eig = DecompositionFactory_DDRM.eig(n, true);
        if (!eig.decompose(S)) return null;

        DMatrixRMaj Q = new DMatrixRMaj(n, n);
        double[] lam = new double[n];

        for (int i = 0; i < n; i++) {
            var ev = eig.getEigenVector(i);
            if (ev == null) return null;
            for (int r = 0; r < n; r++) Q.set(r, i, ev.get(r, 0));
            lam[i] = eig.getEigenvalue(i).getReal();
        }

        double tr = Math.max(0.0, CommonOps_DDRM.trace(S));
        double floor = Math.max(1e-300, floorRel * (tr / Math.max(1, n)));

        // Reconstruct: Q diag(max(lam,floor)) Q^T
        DMatrixRMaj D = new DMatrixRMaj(n, n);
        for (int i = 0; i < n; i++) {
            double v = lam[i];
            if (!Double.isFinite(v)) return null;
            if (v < floor) v = floor;
            D.set(i, i, v);
        }

        DMatrixRMaj T = new DMatrixRMaj(n, n);
        CommonOps_DDRM.mult(Q, D, T);
        DMatrixRMaj out = new DMatrixRMaj(n, n);
        CommonOps_DDRM.multTransB(T, Q, out);
        symmetrizeInPlace(out);
        return out;
    }

    private static double approximateCondSPD(DMatrixRMaj A, double floorRel) {
        int n = A.numRows;
        DMatrixRMaj S = A.copy();
        symmetrizeInPlace(S);

        EigenDecomposition_F64<DMatrixRMaj> eig = DecompositionFactory_DDRM.eig(n, false);
        if (!eig.decompose(S)) return Double.NaN;

        double tr = Math.max(0.0, CommonOps_DDRM.trace(S));
        double floor = Math.max(1e-300, floorRel * (tr / Math.max(1, n)));

        double min = Double.POSITIVE_INFINITY;
        double max = 0.0;
        for (int i = 0; i < n; i++) {
            double li = eig.getEigenvalue(i).getReal();
            if (!Double.isFinite(li)) return Double.NaN;
            if (li < floor) li = floor;
            if (li < min) min = li;
            if (li > max) max = li;
        }
        if (!(min > 0) || !(max > 0)) return Double.NaN;
        return max / min;
    }

    private static void addToDiagonalInPlace(DMatrixRMaj A, double v) {
        int n = A.numRows;
        for (int i = 0; i < n; i++) A.add(i, i, v);
    }

    private static void symmetrizeInPlace(DMatrixRMaj A) {
        int n = A.numRows;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double v = 0.5 * (A.get(i, j) + A.get(j, i));
                A.set(i, j, v);
                A.set(j, i, v);
            }
        }
    }

    // -------------------- kernels (standardized distances + bandwidth grid) --------------------

    private DMatrixRMaj buildCenteredRbfGram1D_Standardized(int varIndex, int[] rows, boolean withBandwidthGrid) {
        int n = (rows == null) ? nEff : rows.length;

        double[] x = extract1D(varIndex, rows);

        // Standardize for distance computations (mean 0, sd 1)
        standardizeInPlace(x);

        double medD2 = medianPairwiseDistanceSquared1D(x);
        if (!(medD2 > 0)) medD2 = 1.0;

        double bestScore = Double.NEGATIVE_INFINITY;
        DMatrixRMaj best = null;

        double[] mults = withBandwidthGrid ? bwMultipliers : new double[]{1.0};
        for (double m : mults) {
            double bw2 = medD2 * m;
            if (!(bw2 > 0)) continue;

            DMatrixRMaj K = rbfGramFromStandardized1D(x, bw2);
            centerInPlace(K);
            symmetrizeInPlace(K);

            // For Kx we don’t need to maximize logdet, but we do want to avoid near-rank-0 after centering.
            // Heuristic: prefer higher trace after centering (still cheap).
            double tr = CommonOps_DDRM.trace(K);
            if (!Double.isFinite(tr)) continue;

            if (tr > bestScore) {
                bestScore = tr;
                best = K;
            }
        }

        return best;
    }

    private DMatrixRMaj buildCenteredRbfGramND_Standardized(int[] parentIdx, int[] rows, boolean withBandwidthGrid) {
        int n = (rows == null) ? nEff : rows.length;
        int d = parentIdx.length;

        // Extract Z (n x d)
        double[][] Z = new double[n][d];
        for (int r = 0; r < n; r++) {
            int row = (rows == null) ? r : rows[r];
            for (int j = 0; j < d; j++) {
                Z[r][j] = cols[parentIdx[j]][row];
            }
        }

        // Standardize each column of Z for distance computations
        standardizeColumnsInPlace(Z);

        double medD2 = medianPairwiseDistanceSquaredND(Z);
        if (!(medD2 > 0)) medD2 = 1.0;

        double tau = useNScaledTau ? (n * lambda) : lambda;

        double best = Double.NEGATIVE_INFINITY;
        DMatrixRMaj bestK = null;

        double[] mults = withBandwidthGrid ? bwMultipliers : new double[]{1.0};
        for (double m : mults) {
            double bw2 = medD2 * m;
            if (!(bw2 > 0)) continue;

            DMatrixRMaj K = rbfGramFromStandardizedND(Z, bw2);
            centerInPlace(K);
            symmetrizeInPlace(K);

            // Choose bandwidth by stability: maximize logdet(K + tau I), rejecting absurd condition numbers.
            DMatrixRMaj A = K.copy();
            addToDiagonalInPlace(A, tau);

            double cond = approximateCondSPD(A, 1e-14);
            if (Double.isFinite(cond) && cond > condCap) continue;

            double tr = Math.max(0.0, CommonOps_DDRM.trace(A));
            double eps = Math.max(1e-300, jitterRel * (tr / Math.max(1, n)));
            double ld = logDetPSD_Robust(A, eps);
            if (!Double.isFinite(ld)) continue;

            if (ld > best) {
                best = ld;
                bestK = K;
            }
        }

        return bestK;
    }

    private static DMatrixRMaj rbfGramFromStandardized1D(double[] x, double bw2) {
        int n = x.length;
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

    private static DMatrixRMaj rbfGramFromStandardizedND(double[][] Z, double bw2) {
        int n = Z.length;
        int d = Z[0].length;
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

    // -------------------- centering --------------------

    /** Centers a Gram matrix in-place: K <- H K H, where H = I - (1/n)11^T. */
    private static void centerInPlace(DMatrixRMaj K) {
        int n = K.numRows;
        if (n == 0) return;

        double[] rowMean = new double[n];
        double grand = 0.0;

        for (int i = 0; i < n; i++) {
            double s = 0.0;
            for (int j = 0; j < n; j++) s += K.get(i, j);
            rowMean[i] = s / n;
            grand += s;
        }
        grand /= (double) (n * n);

        // For symmetric K, colMean == rowMean
        for (int i = 0; i < n; i++) {
            double rmi = rowMean[i];
            for (int j = 0; j < n; j++) {
                double v = K.get(i, j) - rmi - rowMean[j] + grand;
                K.set(i, j, v);
            }
        }
    }

    // -------------------- missingness row selection --------------------

    private int[] validRows(int[] vars) {
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

    // -------------------- standardization + medians --------------------

    private static void standardizeInPlace(double[] x) {
        int n = x.length;
        double mean = 0.0;
        for (double v : x) mean += v;
        mean /= n;

        double var = 0.0;
        for (double v : x) {
            double d = v - mean;
            var += d * d;
        }
        var /= Math.max(1, n - 1);
        double sd = Math.sqrt(var);
        if (!(sd > 0) || !Double.isFinite(sd)) {
            // all equal / nearly equal -> leave as-is (but distances will be ~0)
            return;
        }

        for (int i = 0; i < n; i++) x[i] = (x[i] - mean) / sd;
    }

    private static void standardizeColumnsInPlace(double[][] Z) {
        int n = Z.length;
        int d = Z[0].length;
        for (int j = 0; j < d; j++) {
            double mean = 0.0;
            for (int i = 0; i < n; i++) mean += Z[i][j];
            mean /= n;

            double var = 0.0;
            for (int i = 0; i < n; i++) {
                double dv = Z[i][j] - mean;
                var += dv * dv;
            }
            var /= Math.max(1, n - 1);
            double sd = Math.sqrt(var);
            if (!(sd > 0) || !Double.isFinite(sd)) {
                // constant column; just mean-center
                for (int i = 0; i < n; i++) Z[i][j] = Z[i][j] - mean;
            } else {
                for (int i = 0; i < n; i++) Z[i][j] = (Z[i][j] - mean) / sd;
            }
        }
    }

    private static double medianPairwiseDistanceSquared1D(double[] x) {
        int n = x.length;
        if (n < 3) return 1.0;

        double[] d2 = new double[n * (n - 1) / 2];
        int idx = 0;
        for (int i = 1; i < n; i++) {
            double xi = x[i];
            for (int j = 0; j < i; j++) {
                double d = xi - x[j];
                d2[idx++] = d * d;
            }
        }
        Arrays.sort(d2, 0, idx);

        int firstPos = 0;
        while (firstPos < idx && d2[firstPos] <= 0) firstPos++;
        if (firstPos >= idx) return 1.0;

        int mid = firstPos + (idx - firstPos) / 2;
        return d2[mid];
    }

    private static double medianPairwiseDistanceSquaredND(double[][] Z) {
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
    public double localScoreDiff(int x, int y) { return Score.super.localScoreDiff(x, y); }

    @Override
    public double localScore(int node, int parent) { return Score.super.localScore(node, parent); }

    @Override
    public double localScore(int node) { return Score.super.localScore(node); }

    @Override
    public Node getVariable(String targetName) { return Score.super.getVariable(targetName); }

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
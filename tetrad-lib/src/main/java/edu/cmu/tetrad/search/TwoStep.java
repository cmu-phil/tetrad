package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Two-Step algorithm (linear, non-Gaussian disturbances; cycles allowed).
 * Orientation: B[i,j] != 0 means edge j -> i.
 *
 * Step 1: Sparse mask via (Adaptive) Lasso on Xi ~ X\i (Gram form over covariance).
 *         Penalty uses paper-style scaling when normalizeLossByN=true: lambda * (ln N)/2.
 * Step 2: ICA (FastICA) to get A, align (Hungarian) so diag(A)=1, B0 = I - A^{-1}.
 *         Project to mask, refine with constrained OLS (covariance), pairwise BIC cleanup,
 *         then strict two-cycle breaker (optional).
 */
@Deprecated
public final class TwoStep {

    // ---------- Result ----------
    public static final class Result {
        public final SimpleMatrix B;   // p x p
        public final Graph graph;      // j->i iff B[i,j] != 0 after pruning
        public Result(SimpleMatrix B, Graph g) { this.B = B; this.graph = g; }
    }

    // ---------- Parameters (mask / lasso) ----------
    private double lambda = 0.05;
    /** Paper-style scaling: if true, effective lambda = lambda * (ln N)/2 for Step-1 lasso. */
    private boolean normalizeLossByN = true;
    private boolean useAdaptiveLasso = true;
    private double adaptiveGamma = 1.0;
    private double alassoEps = 1e-6;
    private int alassoMaxIter = 500;
    /** Threshold for Step-1 mask support (paper suggests ~0.10). */
    private double maskThreshold = 0.10;
    /** Final pruning threshold for |B| -> 0 (paper suggests ~0.15). */
    private double coefThreshold = 0.15;

    // ---------- ICA (FastIca) ----------
    private int icaMaxIter = 1000;
    private double icaTol = 1e-5;
    private long randomSeed = 123L;   // FastIca uses RandomUtil internally
    private boolean verbose = true;
    private double condWarnThreshold = 1e8;

    // FastIca knobs
    private int fastIcaAlgorithm = FastIca.PARALLEL; // or FastIca.DEFLATION
    private int fastIcaFunction = FastIca.LOGCOSH;   // or FastIca.EXP
    private double fastIcaAlpha = 1.1;               // [1,2] for LOGCOSH
    private boolean fastIcaRowNorm = false;

    // ---------- Two-cycle breaker ----------
    private boolean breakTwoCyclesEnabled = true;
    private double twoCycleMinAbs = coefThreshold; // by default match coefThreshold

    // ---------- External mask ----------
    private boolean useExternalMask = false;
    private SimpleMatrix externalMask; // p x p, 0/1, zero diag expected

    // ---------- Diagnostics ----------
    private SimpleMatrix lastA = null; // aligned mixing

    // ---------- Setters ----------
    public void setLambda(double lambda) { this.lambda = lambda; }
    /** If true, Step-1 uses lambdaEff = lambda * (ln N)/2 (paper-style). */
    public void setNormalizeLossByN(boolean flag) { this.normalizeLossByN = flag; }
    public void setUseAdaptiveLasso(boolean flag) { this.useAdaptiveLasso = flag; }
    public void setAdaptiveGamma(double gamma) { this.adaptiveGamma = gamma; }
    public void setMaskThreshold(double t) { this.maskThreshold = t; }
    public void setCoefThreshold(double t) { this.coefThreshold = t; this.twoCycleMinAbs = Math.max(this.twoCycleMinAbs, t); }
    public void setIcaMaxIter(int n) { this.icaMaxIter = n; }
    public void setIcaTol(double t) { this.icaTol = t; }
    public void setRandomSeed(long seed) { this.randomSeed = seed; }
    public void setVerbose(boolean v) { this.verbose = v; }
    public void setCondWarnThreshold(double thr) { this.condWarnThreshold = thr; }
    public void setFastIcaAlgorithm(int alg) { this.fastIcaAlgorithm = alg; }
    public void setFastIcaFunction(int f) { this.fastIcaFunction = f; }
    public void setFastIcaAlpha(double a) { this.fastIcaAlpha = a; }
    public void setFastIcaRowNorm(boolean flag) { this.fastIcaRowNorm = flag; }
    public void setBreakTwoCyclesEnabled(boolean enabled) { this.breakTwoCyclesEnabled = enabled; }
    public void setTwoCycleMinAbs(double minAbs) { this.twoCycleMinAbs = minAbs; }
    /** Provide an external mask (e.g., from FAS-stable skeleton). 1=allowed, 0=forced zero. */
    public void setExternalMask(SimpleMatrix mask) { this.externalMask = sanitizeMask(mask); this.useExternalMask = true; }
    /** Optional diagnostics: aligned mixing A (~ (I - B)^{-1}). */
    public SimpleMatrix getLastA() { return lastA; }

    // ---------- Main ----------
    public Result search(DataSet data) {
        // Data
        SimpleMatrix X = standardizeCols(toMatrix(data)); // n x p
        final int n = X.numRows();
        final int p = X.numCols();

        // Covariance (p x p), used everywhere from here on
        final SimpleMatrix S = covarianceS(X);

        // Step 1: support mask M
        SimpleMatrix M = useExternalMask ? externalMask : buildMaskWithAdaptiveLassoGram(S, n);

        // Step 2a: ICA mixing with Hungarian alignment (diag=1)
        SimpleMatrix A = fastIcaMixingUsingFastIca(X, p);
        this.lastA = A;

        // Conditioning warning
        double condA = cond2(A);
        if (condA > condWarnThreshold && verbose) {
            System.err.printf(Locale.ROOT,
                    "[TwoStep] Warning: A ill-conditioned (cond2 ~ %.3e > %.3e). Inversion may amplify noise.%n",
                    condA, condWarnThreshold);
        }

        // Step 2b: B0 = I - A^{-1}
        SimpleMatrix Ainv;
        try { Ainv = A.solve(identity(p)); } // explicit solve
        catch (Exception ex) { throw new IllegalStateException("Failed to invert/solve for A^{-1}.", ex); }
        SimpleMatrix B = identity(p).minus(Ainv);

        // Step 2c: project to mask and zero diag
        forceMaskAndDiag(B, M);

        // Step 2d: refine coefficients via constrained OLS using covariance blocks
        B = refineByConstrainedLSCov(S, n, B, M);

        // Step 2e: pairwise BIC cleanup over {none, j->i, i->j, both}
//        bicPairwiseCleanupCov(S, n, M, B);
        // After refineByConstrainedLSCov(...) and BEFORE final graph build:
        B = residualIndependenceCleanup(X, M, B);

        // Final: strict two-cycle breaker (deterministic)
        if (breakTwoCyclesEnabled) {
            double minAbs = Math.max(twoCycleMinAbs, coefThreshold);
            B = breakTwoCyclesStrict(B, minAbs);
        }

        Graph g = toGraph(B, data.getVariables(), coefThreshold);
        return new Result(B, g);
    }

    // ======================================================================
    // Step 1: Adaptive Lasso mask using Gram (covariance) lasso per response
    // ======================================================================

    private SimpleMatrix buildMaskWithAdaptiveLassoGram(SimpleMatrix S, int n) {
        final int p = S.numRows();
        SimpleMatrix M = new SimpleMatrix(p, p);

        // Precompute index lists {0..p-1}\{i}
        int[][] predIdxCache = new int[p][];
        for (int i = 0; i < p; i++) {
            predIdxCache[i] = IntStream.concat(IntStream.range(0, i), IntStream.range(i + 1, p)).toArray();
        }

        // Paper-style penalty scaling: lambdaEff = lambda * (ln N)/2 when normalizeLossByN
        final double lambdaEffRow = normalizeLossByN ? (lambda * Math.log(Math.max(8, n)) / 2.0) : lambda;

        for (int i = 0; i < p; i++) {
            int[] P = predIdxCache[i];

            // OLS weights for adaptive lasso: w_j = 1/(|beta_ols| + eps)^gamma
            OlsCov ols = olsFromCov(S, i, P, n);
            double eps = 1e-6;
            double[] w = new double[P.length];
            for (int k = 0; k < P.length; k++) {
                double b = Math.abs(ols.beta.get(k, 0));
                w[k] = useAdaptiveLasso ? 1.0 / Math.pow(b + eps, adaptiveGamma) : 1.0;
            }

            SimpleMatrix beta = lassoGram(S, i, P, w, lambdaEffRow, alassoEps, alassoMaxIter, null);

            for (int k = 0; k < P.length; k++) {
                if (Math.abs(beta.get(k, 0)) > maskThreshold) {
                    M.set(i, P[k], 1.0);
                }
            }
            M.set(i, i, 0.0);
        }
        return M;
    }

    /** Gram-form lasso via coordinate descent on G=S_PP, c=S_Pi. */
    private SimpleMatrix lassoGram(SimpleMatrix S, int i, int[] P,
                                   double[] w, double lambdaEff, double tol, int maxIter,
                                   double[] betaWarm /*nullable*/) {
        int m = P.length;
        if (m == 0) return new SimpleMatrix(0, 1);
        SimpleMatrix G = take(S, P, P);              // m x m
        SimpleMatrix c = take(S, P, new int[]{i});   // m x 1

        double[] beta = new double[m];
        if (betaWarm != null && betaWarm.length == m) System.arraycopy(betaWarm, 0, beta, 0, m);

        double[] Gdiag = new double[m];
        for (int j = 0; j < m; j++) Gdiag[j] = Math.max(G.get(j, j), 1e-12);

        for (int it = 0; it < maxIter; it++) {
            double maxDelta = 0.0;
            for (int j = 0; j < m; j++) {
                double r = c.get(j, 0);
                for (int k = 0; k < m; k++) if (k != j) r -= G.get(j, k) * beta[k];
                double bjOld = beta[j];
                double bjNew = softThreshold(r, lambdaEff * w[j]) / Gdiag[j];
                beta[j] = bjNew;
                maxDelta = Math.max(maxDelta, Math.abs(bjNew - bjOld));
            }
            if (maxDelta < tol) break;
        }

        SimpleMatrix out = new SimpleMatrix(m, 1);
        for (int j = 0; j < m; j++) out.set(j, 0, beta[j]);
        return out;
    }

    private static double softThreshold(double z, double t) {
        if (z > t) return z - t;
        if (z < -t) return z + t;
        return 0.0;
    }

    // ============================================
    // Step 2: ICA with Hungarian alignment (diag=1)
    // ============================================

    /** Use FastIca; then permute/sign/scale columns of A so diag(A)=1 (Hungarian). */
    private SimpleMatrix fastIcaMixingUsingFastIca(SimpleMatrix X_nxp, int p) {
        // Convert (n x p) -> (p x n) for FastIca (rows=vars, cols=cases)
        Matrix X_pxn = new Matrix(p, X_nxp.numRows());
        for (int j = 0; j < p; j++) for (int i = 0; i < X_nxp.numRows(); i++) X_pxn.set(j, i, X_nxp.get(i, j));

        FastIca ica = new FastIca(X_pxn, p);
        ica.setAlgorithmType(fastIcaAlgorithm);
        ica.setFunction(fastIcaFunction);
        ica.setAlpha(fastIcaAlpha);
        ica.setRowNorm(fastIcaRowNorm);
        ica.setMaxIterations(icaMaxIter);
        ica.setTolerance(icaTol);
        ica.setVerbose(verbose);

        FastIca.IcaResult res = ica.findComponents();
        SimpleMatrix W = res.getW().getSimpleMatrix(); // unmixing
        SimpleMatrix A = W.invert();                   // mixing

        // Hungarian maximize |diag(A)| via column permutation
        int[] colOfRow = Hungarian.maximizeAbsDiagonal(A);
        SimpleMatrix Pm = new SimpleMatrix(p, p);
        for (int i = 0; i < p; i++) Pm.set(i, colOfRow[i], 1.0);
        SimpleMatrix Aperm = A.mult(Pm);

        // Flip signs so diag>0, then scale columns so diag=1
        for (int i = 0; i < p; i++) {
            double d = Aperm.get(i, i);
            if (d < 0.0) for (int r = 0; r < p; r++) Aperm.set(r, i, -Aperm.get(r, i));
            double s = 1.0 / Math.max(Math.abs(Aperm.get(i, i)), 1e-12);
            for (int r = 0; r < p; r++) Aperm.set(r, i, Aperm.get(r, i) * s);
        }
        return Aperm;
    }

    private static final class Hungarian {
        // argmax permutation of sum_i |C[i, pi(i)]|
        static int[] maximizeAbsDiagonal(SimpleMatrix C) {
            int n = C.numRows();
            double[][] cost = new double[n][n];
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    cost[i][j] = -Math.abs(C.get(i, j)); // maximize abs -> minimize negative abs
            return hungarian(cost);
        }
        private static int[] hungarian(double[][] a) {
            int n = a.length;
            double[] u = new double[n + 1], v = new double[n + 1];
            int[] p = new int[n + 1], way = new int[n + 1];
            for (int i = 1; i <= n; i++) {
                p[0] = i;
                int j0 = 0;
                double[] minv = new double[n + 1];
                boolean[] used = new boolean[n + 1];
                Arrays.fill(minv, Double.POSITIVE_INFINITY);
                Arrays.fill(used, false);
                do {
                    used[j0] = true;
                    int i0 = p[j0], j1 = 0;
                    double delta = Double.POSITIVE_INFINITY;
                    for (int j = 1; j <= n; j++) if (!used[j]) {
                        double cur = a[i0 - 1][j - 1] - u[i0] - v[j];
                        if (cur < minv[j]) { minv[j] = cur; way[j] = j0; }
                        if (minv[j] < delta) { delta = minv[j]; j1 = j; }
                    }
                    for (int j = 0; j <= n; j++) {
                        if (used[j]) { u[p[j]] += delta; v[j] -= delta; }
                        else minv[j] -= delta;
                    }
                    j0 = j1;
                } while (p[j0] != 0);
                do {
                    int j1 = way[j0];
                    p[j0] = p[j1];
                    j0 = j1;
                } while (j0 != 0);
            }
            int[] colOfRow = new int[n];
            for (int j = 1; j <= n; j++) if (p[j] != 0) colOfRow[p[j] - 1] = j - 1;
            return colOfRow;
        }
    }

    // =====================================================
    // Refinement & BIC cleanup using covariance S (p x p)
    // =====================================================

    /** Constrained OLS per row using covariance blocks. */
    private SimpleMatrix refineByConstrainedLSCov(SimpleMatrix S, int n, SimpleMatrix B0, SimpleMatrix M) {
        int p = S.numRows();
        SimpleMatrix B = B0.copy();
        for (int i = 0; i < p; i++) {
            List<Integer> parents = new ArrayList<>();
            for (int j = 0; j < p; j++) if (j != i && M.get(i, j) != 0.0) parents.add(j);
            if (parents.isEmpty()) {
                for (int j = 0; j < p; j++) B.set(i, j, 0.0);
                continue;
            }
            int[] P = parents.stream().mapToInt(Integer::intValue).toArray();
            OlsCov fit = olsFromCov(S, i, P, n);
            for (int k = 0; k < P.length; k++) {
                double v = fit.beta.get(k, 0);
                B.set(i, P[k], Math.abs(v) < coefThreshold ? 0.0 : v);
            }
        }
        for (int d = 0; d < p; d++) B.set(d, d, 0.0);
        return B;
    }

    /** BIC cleanup over {none, j->i, i->j, both} for each unordered pair. */
    private void bicPairwiseCleanupCov(SimpleMatrix S, int n, SimpleMatrix M, SimpleMatrix B) {
        int p = S.numRows();

        // Parent lists from mask
        List<Integer>[] parents = new List[p];
        for (int i = 0; i < p; i++) {
            parents[i] = new ArrayList<>();
            for (int j = 0; j < p; j++) if (j != i && M.get(i, j) != 0.0) parents[i].add(j);
        }

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                boolean allow_ij = M.get(i, j) != 0.0;
                boolean allow_ji = M.get(j, i) != 0.0;
                if (!allow_ij && !allow_ji) continue;

                int _i = i, _j = j;

                int[] Pi0 = parents[i].stream().filter(c -> c != _j).mapToInt(Integer::intValue).toArray();
                int[] Pj0 = parents[j].stream().filter(c -> c != _i).mapToInt(Integer::intValue).toArray();

                // none
                double rss_i_none = rssFromCov(S, i, Pi0, n);
                double rss_j_none = rssFromCov(S, j, Pj0, n);
                double bic_none = bicForRow(rss_i_none, n, Pi0.length) + bicForRow(rss_j_none, n, Pj0.length);

                // j->i
                double bic_j2i = Double.POSITIVE_INFINITY;
                if (allow_ij) {
                    int[] Pi = Arrays.copyOf(Pi0, Pi0.length + 1); Pi[Pi0.length] = j;
                    double rss_i = rssFromCov(S, i, Pi, n);
                    bic_j2i = bicForRow(rss_i, n, Pi.length) + bicForRow(rss_j_none, n, Pj0.length);
                }

                // i->j
                double bic_i2j = Double.POSITIVE_INFINITY;
                if (allow_ji) {
                    int[] Pj = Arrays.copyOf(Pj0, Pj0.length + 1); Pj[Pj0.length] = i;
                    double rss_j = rssFromCov(S, j, Pj, n);
                    bic_i2j = bicForRow(rss_i_none, n, Pi0.length) + bicForRow(rss_j, n, Pj.length);
                }

                // both
                double bic_both = Double.POSITIVE_INFINITY;
                if (allow_ij && allow_ji) {
                    int[] Pi = Arrays.copyOf(Pi0, Pi0.length + 1); Pi[Pi0.length] = j;
                    int[] Pj = Arrays.copyOf(Pj0, Pj0.length + 1); Pj[Pj0.length] = i;
                    double rss_i = rssFromCov(S, i, Pi, n);
                    double rss_j = rssFromCov(S, j, Pj, n);
                    bic_both = bicForRow(rss_i, n, Pi.length) + bicForRow(rss_j, n, Pj.length);
                }

                // pick best
                double best = bic_none; int choice = 0;
                if (bic_j2i < best) { best = bic_j2i; choice = 1; }
                if (bic_i2j < best) { best = bic_i2j; choice = 2; }
                if (bic_both < best) { best = bic_both; choice = 3; }

                // apply by refitting the affected rows
                if (choice == 0) {
                    B.set(i, j, 0.0); B.set(j, i, 0.0);
                    OlsCov bi = olsFromCov(S, i, Pi0, n);
                    for (int k = 0; k < Pi0.length; k++) B.set(i, Pi0[k], prune(bi.beta.get(k, 0)));
                    OlsCov bj = olsFromCov(S, j, Pj0, n);
                    for (int k = 0; k < Pj0.length; k++) B.set(j, Pj0[k], prune(bj.beta.get(k, 0)));
                } else if (choice == 1) {
                    B.set(j, i, 0.0);
                    int[] Pi = Arrays.copyOf(Pi0, Pi0.length + 1); Pi[Pi0.length] = j;
                    OlsCov bi = olsFromCov(S, i, Pi, n);
                    for (int k = 0; k < Pi.length; k++) B.set(i, Pi[k], prune(bi.beta.get(k, 0)));
                    OlsCov bj = olsFromCov(S, j, Pj0, n);
                    for (int k = 0; k < Pj0.length; k++) B.set(j, Pj0[k], prune(bj.beta.get(k, 0)));
                    B.set(j, i, 0.0);
                } else if (choice == 2) {
                    B.set(i, j, 0.0);
                    int[] Pj = Arrays.copyOf(Pj0, Pj0.length + 1); Pj[Pj0.length] = i;
                    OlsCov bj = olsFromCov(S, j, Pj, n);
                    for (int k = 0; k < Pj.length; k++) B.set(j, Pj[k], prune(bj.beta.get(k, 0)));
                    OlsCov bi = olsFromCov(S, i, Pi0, n);
                    for (int k = 0; k < Pi0.length; k++) B.set(i, Pi0[k], prune(bi.beta.get(k, 0)));
                    B.set(i, j, 0.0);
                } else { // both
                    int[] Pi = Arrays.copyOf(Pi0, Pi0.length + 1); Pi[Pi0.length] = j;
                    int[] Pj = Arrays.copyOf(Pj0, Pj0.length + 1); Pj[Pj0.length] = i;
                    OlsCov bi = olsFromCov(S, i, Pi, n);
                    for (int k = 0; k < Pi.length; k++) B.set(i, Pi[k], prune(bi.beta.get(k, 0)));
                    OlsCov bj = olsFromCov(S, j, Pj, n);
                    for (int k = 0; k < Pj.length; k++) B.set(j, Pj[k], prune(bj.beta.get(k, 0)));
                }
            }
        }
        // zero diagonal for safety
        int _p = S.numRows();
        for (int d = 0; d < _p; d++) B.set(d, d, 0.0);
    }

    /**
     * Residual-independence orientation pass (LiNGAM-style).
     * For each unordered pair {i,j} allowed by the mask, choose among {none, j->i, i->j, both}
     * by minimizing a dependence score between residuals and the candidate parent.
     * Score = |corr( tanh(residual), tanh(parent) )| (robust, cheap proxy for NG independence).
     *
     * Requires the standardized data matrix X (n x p).
     */
    private SimpleMatrix residualIndependenceCleanup(SimpleMatrix X, SimpleMatrix M, SimpleMatrix B) {
        final int n = X.numRows();
        final int p = X.numCols();

        // Build parent lists from mask (excluding the paired node)
        List<Integer>[] parents = new List[p];
        for (int i = 0; i < p; i++) {
            parents[i] = new ArrayList<>();
            for (int j = 0; j < p; j++) if (i != j && M.get(i, j) != 0.0) parents[i].add(j);
        }

        // Utility: fit residuals for y = X[:, target] on X[:, P] (OLS; returns length-n residual vector)
        java.util.function.BiFunction<Integer,int[],double[]> fitResiduals = (target, P) -> {
            if (P.length == 0) {
                double[] r = new double[n];
                for (int t = 0; t < n; t++) r[t] = X.get(t, target); // standardized -> mean 0
                return r;
            }
            // Build Z (n x |P|)
            SimpleMatrix Z = new SimpleMatrix(n, P.length);
            for (int c = 0; c < P.length; c++) for (int t = 0; t < n; t++) Z.set(t, c, X.get(t, P[c]));
            SimpleMatrix y = X.extractVector(false, target);

            // beta = (Z'Z + eps I)^{-1} Z' y
            double eps = 1e-8;
            SimpleMatrix ZtZ = Z.transpose().mult(Z);
            for (int d = 0; d < P.length; d++) ZtZ.set(d, d, ZtZ.get(d, d) + eps);
            SimpleMatrix beta = ZtZ.solve(Z.transpose().mult(y));

            // residual r = y - Z beta
            SimpleMatrix r = y.minus(Z.mult(beta));
            double[] out = new double[n];
            for (int t = 0; t < n; t++) out[t] = r.get(t, 0);
            return out;
        };

        // Nonlinearized correlation proxy for independence: |corr(tanh(a), tanh(b))|
        java.util.function.BiFunction<double[], double[], Double> depScore = (a, b) -> {
            double meanA = 0, meanB = 0;
            for (int t = 0; t < n; t++) { meanA += Math.tanh(a[t]); meanB += Math.tanh(b[t]); }
            meanA /= n; meanB /= n;
            double num = 0, va = 0, vb = 0;
            for (int t = 0; t < n; t++) {
                double ta = Math.tanh(a[t]) - meanA;
                double tb = Math.tanh(b[t]) - meanB;
                num += ta * tb; va += ta * ta; vb += tb * tb;
            }
            double den = Math.sqrt(Math.max(va,1e-12) * Math.max(vb,1e-12));
            return Math.abs(num / Math.max(den, 1e-12));
        };

        // Convenience: extract a single column as array
        java.util.function.Function<Integer,double[]> colAsArray = (col) -> {
            double[] v = new double[n];
            for (int t = 0; t < n; t++) v[t] = X.get(t, col);
            return v;
        };

        SimpleMatrix Bout = B.copy();

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                boolean allow_ij = M.get(i, j) != 0.0;
                boolean allow_ji = M.get(j, i) != 0.0;
                if (!allow_ij && !allow_ji) continue;

                // Parent sets excluding the paired node
                int _i = i, _j = j;

                int[] Pi0 = parents[i].stream().filter(c -> c != _j).mapToInt(Integer::intValue).toArray();
                int[] Pj0 = parents[j].stream().filter(c -> c != _i).mapToInt(Integer::intValue).toArray();

                // Scores for four options
                double bestScore = Double.POSITIVE_INFINITY;
                int choice = 0; // 0=none, 1=j->i, 2=i->j, 3=both

                // none: both residuals with Pi0/Pj0; score = max(dep(ri, Xj), dep(rj, Xi))
                double[] ri_none = fitResiduals.apply(i, Pi0);
                double[] rj_none = fitResiduals.apply(j, Pj0);
                double s_none = Math.max(depScore.apply(ri_none, colAsArray.apply(j)),
                        depScore.apply(rj_none, colAsArray.apply(i)));
                bestScore = s_none; choice = 0;

                // j->i
                if (allow_ij) {
                    int[] Pi = Arrays.copyOf(Pi0, Pi0.length + 1); Pi[Pi0.length] = j;
                    double[] ri = fitResiduals.apply(i, Pi);
                    double s = depScore.apply(ri, colAsArray.apply(j)); // should be small if j truly causes i
                    if (s < bestScore) { bestScore = s; choice = 1; }
                }

                // i->j
                if (allow_ji) {
                    int[] Pj = Arrays.copyOf(Pj0, Pj0.length + 1); Pj[Pj0.length] = i;
                    double[] rj = fitResiduals.apply(j, Pj);
                    double s = depScore.apply(rj, colAsArray.apply(i));
                    if (s < bestScore) { bestScore = s; choice = 2; }
                }

                // both (rare; keep only if both residuals look independent)
                if (allow_ij && allow_ji) {
                    int[] Pi = Arrays.copyOf(Pi0, Pi0.length + 1); Pi[Pi0.length] = j;
                    int[] Pj = Arrays.copyOf(Pj0, Pj0.length + 1); Pj[Pj0.length] = i;
                    double[] ri = fitResiduals.apply(i, Pi);
                    double[] rj = fitResiduals.apply(j, Pj);
                    double s = Math.max(depScore.apply(ri, colAsArray.apply(j)),
                            depScore.apply(rj, colAsArray.apply(i)));
                    if (s < bestScore) { bestScore = s; choice = 3; }
                }

                // Apply choice: refit affected rows and prune tiny coefs
                if (choice == 0) {
                    Bout.set(i, j, 0.0); Bout.set(j, i, 0.0);
                } else if (choice == 1) {
                    Bout.set(j, i, 0.0);
                    // refit i with j in parent set
                    int[] Pi = Arrays.copyOf(Pi0, Pi0.length + 1); Pi[Pi0.length] = j;
                    writeOlsRowCoeffs(X, i, Pi, Bout);
                } else if (choice == 2) {
                    Bout.set(i, j, 0.0);
                    int[] Pj = Arrays.copyOf(Pj0, Pj0.length + 1); Pj[Pj0.length] = i;
                    writeOlsRowCoeffs(X, j, Pj, Bout);
                } else {
                    int[] Pi = Arrays.copyOf(Pi0, Pi0.length + 1); Pi[Pi0.length] = j;
                    int[] Pj = Arrays.copyOf(Pj0, Pj0.length + 1); Pj[Pj0.length] = i;
                    writeOlsRowCoeffs(X, i, Pi, Bout);
                    writeOlsRowCoeffs(X, j, Pj, Bout);
                }
            }
        }

        // zero diagonal and final prune/sym break
        for (int d = 0; d < p; d++) Bout.set(d, d, 0.0);
        if (breakTwoCyclesEnabled) Bout = breakTwoCyclesStrict(Bout, Math.max(twoCycleMinAbs, coefThreshold));
        return Bout;
    }

    /** Fit OLS for row target on columns P using raw X, then write/prune into B. */
    private void writeOlsRowCoeffs(SimpleMatrix X, int target, int[] P, SimpleMatrix B) {
        int n = X.numRows();
        if (P.length == 0) { for (int j = 0; j < B.numCols(); j++) B.set(target, j, 0.0); return; }
        SimpleMatrix Z = new SimpleMatrix(n, P.length);
        for (int c = 0; c < P.length; c++) for (int t = 0; t < n; t++) Z.set(t, c, X.get(t, P[c]));
        SimpleMatrix y = X.extractVector(false, target);

        double eps = 1e-8;
        SimpleMatrix ZtZ = Z.transpose().mult(Z);
        for (int d = 0; d < P.length; d++) ZtZ.set(d, d, ZtZ.get(d, d) + eps);
        SimpleMatrix beta = ZtZ.solve(Z.transpose().mult(y));

        for (int k = 0; k < P.length; k++) {
            double v = beta.get(k, 0);
            B.set(target, P[k], Math.abs(v) < coefThreshold ? 0.0 : v);
        }
    }

    private double prune(double v) { return Math.abs(v) < coefThreshold ? 0.0 : v; }

    // ---------- Covariance-based OLS / RSS / BIC ----------

    private static class OlsCov {
        SimpleMatrix beta; // |P| x 1
        double rss;        // n * (1 - R^2), assuming Var(Xi)=1 after standardize
    }

    /** OLS: beta = S_PP^{-1} S_Pi ; rss = n * (1 - S_iP beta). */
    private OlsCov olsFromCov(SimpleMatrix S, int i, int[] P, int n) {
        OlsCov out = new OlsCov();
        if (P.length == 0) {
            out.beta = new SimpleMatrix(0, 1);
            out.rss = n; // Var(Xi)=1 (standardized), SSE = n*Var since intercept=0
            return out;
        }
        SimpleMatrix Spp = take(S, P, P);
        SimpleMatrix Spi = take(S, P, new int[]{i});
        SimpleMatrix beta = Spp.solve(Spi);
        double explained = Spi.transpose().mult(beta).get(0, 0); // S_iP * beta
        out.beta = beta;
        out.rss = n * Math.max(1.0 - explained, 0.0);
        return out;
    }

    /** RSS using covariance only. */
    private double rssFromCov(SimpleMatrix S, int i, int[] P, int n) {
        if (P.length == 0) return n; // standardized: Var(Xi)=1
        SimpleMatrix Spp = take(S, P, P);
        SimpleMatrix Spi = take(S, P, new int[]{i});
        SimpleMatrix beta = Spp.solve(Spi);
        double explained = Spi.transpose().mult(beta).get(0, 0);
        return n * Math.max(1.0 - explained, 0.0);
    }

    /** BIC for a row (no intercept): n*log(RSS/n) + k*log(n). */
    private static double bicForRow(double rss, int n, int k) {
        double nSafe = Math.max(1, n);
        double rssSafe = Math.max(rss, 1e-12);
        return nSafe * Math.log(rssSafe / nSafe) + k * Math.log(nSafe);
    }

    // ---------- Strict 2-cycle breaker ----------

    /** Keep at most one direction per unordered pair; use minAbs to ignore tiny noise. */
    private SimpleMatrix breakTwoCyclesStrict(SimpleMatrix B, double minAbs) {
        int p = B.numRows();
        SimpleMatrix out = B.copy();
        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                double a = Math.abs(out.get(i, j));
                double b = Math.abs(out.get(j, i));
                if (a < minAbs) a = 0.0;
                if (b < minAbs) b = 0.0;
                if (a == 0.0 && b == 0.0) {
                    out.set(i, j, 0.0); out.set(j, i, 0.0);
                } else if (a > b) {
                    out.set(j, i, 0.0);
                } else if (b > a) {
                    out.set(i, j, 0.0);
                } else { // tie and nonzero: deterministic (keep j->i for i<j)
                    out.set(i, j, 0.0);
                }
            }
        }
        for (int d = 0; d < p; d++) out.set(d, d, 0.0);
        return out;
    }

    // ---------- Small utilities ----------

    /** Build a 0/1 mask from an (undirected) adjacency Graph; diagonal is zero. */
    public static SimpleMatrix maskFromUndirected(Graph skeleton, List<Node> vars) {
        int p = vars.size();
        SimpleMatrix M = new SimpleMatrix(p, p);
        // any adjacency between vars[j] and vars[i] allows both j->i and i->j
        for (int i = 0; i < p; i++) for (int j = 0; j < p; j++) if (i != j) {
            Edge e = skeleton.getEdge(vars.get(i), vars.get(j));
            if (e != null) { M.set(i, j, 1.0); }
        }
        // zero diagonal
        for (int d = 0; d < p; d++) M.set(d, d, 0.0);
        return M;
    }

    /** Ensure a square 0/1 mask with a zero diagonal. */
    private static SimpleMatrix sanitizeMask(SimpleMatrix M) {
        int p = M.numRows();
        if (M.numCols() != p) {
            throw new IllegalArgumentException("External mask must be square (p x p).");
        }
        SimpleMatrix out = new SimpleMatrix(p, p);
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                double v = M.get(i, j);
                double bin = (i == j) ? 0.0 : ((Double.isNaN(v) ? 0.0 : v) != 0.0 ? 1.0 : 0.0);
                out.set(i, j, bin);
            }
        }
        return out;
    }

    /** S = X'X / n */
    private static SimpleMatrix covarianceS(SimpleMatrix X) {
        int n = X.numRows();
        return X.transpose().mult(X).divide(Math.max(1, n));
    }

    /** Submatrix A[rows, cols] */
    private static SimpleMatrix take(SimpleMatrix A, int[] rows, int[] cols) {
        SimpleMatrix out = new SimpleMatrix(rows.length, cols.length);
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < cols.length; c++)
                out.set(r, c, A.get(rows[r], cols[c]));
        return out;
    }

    private static SimpleMatrix standardizeCols(SimpleMatrix X) {
        int n = X.numRows(), p = X.numCols();
        SimpleMatrix out = X.copy();
        for (int j = 0; j < p; j++) {
            double mu = 0.0, s2 = 0.0;
            for (int i = 0; i < n; i++) mu += X.get(i, j);
            mu /= Math.max(1, n);
            for (int i = 0; i < n; i++) { double v = X.get(i, j) - mu; s2 += v * v; }
            double sd = Math.sqrt(Math.max(s2 / Math.max(n - 1, 1), 1e-12));
            for (int i = 0; i < n; i++) out.set(i, j, (X.get(i, j) - mu) / sd);
        }
        return out;
    }

    private static SimpleMatrix toMatrix(DataSet data) {
        int n = data.getNumRows(), p = data.getNumColumns();
        SimpleMatrix X = new SimpleMatrix(n, p);
        for (int i = 0; i < n; i++) for (int j = 0; j < p; j++) X.set(i, j, data.getDouble(i, j));
        return X;
    }

    private static SimpleMatrix identity(int p) {
        SimpleMatrix I = new SimpleMatrix(p, p);
        for (int i = 0; i < p; i++) I.set(i, i, 1.0);
        return I;
    }

    private static void forceMaskAndDiag(SimpleMatrix B, SimpleMatrix M) {
        int p = B.numRows();
        for (int i = 0; i < p; i++) {
            B.set(i, i, 0.0);
            for (int j = 0; j < p; j++) if (i == j || M.get(i, j) == 0.0) B.set(i, j, 0.0);
        }
    }

    private static Graph toGraph(SimpleMatrix B, List<Node> vars, double thr) {
        Graph g = new EdgeListGraph(vars);
        int p = B.numRows();
        for (int i = 0; i < p; i++) for (int j = 0; j < p; j++) {
            if (i == j) continue;
            if (Math.abs(B.get(i, j)) > thr) g.addDirectedEdge(vars.get(j), vars.get(i));
        }
        return g;
    }

    /** 2-norm condition number via eigs of A^T A. */
    private static double cond2(SimpleMatrix A) {
        SimpleMatrix AtA = A.transpose().mult(A);
        SimpleEVD<SimpleMatrix> evd = AtA.eig();
        double maxEv = 0.0, minEv = Double.POSITIVE_INFINITY;
        for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
            double ev = evd.getEigenvalue(i).getReal();
            if (Double.isNaN(ev) || ev <= 0.0) continue;
            maxEv = Math.max(maxEv, ev);
            minEv = Math.min(minEv, ev);
        }
        if (!(maxEv > 0.0) || !(minEv > 0.0)) return Double.POSITIVE_INFINITY;
        return Math.sqrt(maxEv / minEv);
    }
}
package edu.cmu.tetrad.search.utils;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.FDistribution;
import org.apache.commons.math3.stat.inference.TTest;

import java.util.*;

/**
 * Practical nonlinearity tests for conditional mean E(Y|X).
 * All methods assume continuous variables and use testwise deletion via clean(...).
 */
public final class NonlinearityTests {

    private NonlinearityTests() {}

    public static final class TestResult {
        public final double statistic;
        public final double pValue;   // NaN if not applicable
        public final boolean reject;

        public TestResult(double statistic, double pValue, boolean reject) {
            this.statistic = statistic;
            this.pValue = pValue;
            this.reject = reject;
        }

        @Override
        public String toString() {
            return "stat=" + statistic + ", p=" + pValue + ", reject=" + reject;
        }
    }

    public static final class CleanData {
        public final double[] y;
        public final double[][] X;

        public CleanData(double[] y, double[][] X) {
            this.y = y;
            this.X = X;
        }
    }

    /** Remove rows with NaNs in y or any X column. */
    public static CleanData clean(double[] y, double[][] X) {
        int n = y.length;
        int d = (X.length == 0) ? 0 : X[0].length;

        int[] keep = new int[n];
        int m = 0;

        outer:
        for (int i = 0; i < n; i++) {
            double yi = y[i];
            if (Double.isNaN(yi) || !Double.isFinite(yi)) continue;
            for (int j = 0; j < d; j++) {
                double v = X[i][j];
                if (Double.isNaN(v) || !Double.isFinite(v)) continue outer;
            }
            keep[m++] = i;
        }

        double[] yy = new double[m];
        double[][] XX = new double[m][d];
        for (int r = 0; r < m; r++) {
            int i = keep[r];
            yy[r] = y[i];
            for (int j = 0; j < d; j++) XX[r][j] = X[i][j];
        }
        return new CleanData(yy, XX);
    }

    // ============================================================
    // 1) RESET (Ramsey): augment with powers of fitted values
    // ============================================================

    public static TestResult resetTest(double[] y, double[][] X, double alpha) {
        int n = y.length;
        int d = (X.length == 0) ? 0 : X[0].length;
        if (n < 10 || d == 0) return new TestResult(Double.NaN, Double.NaN, false);

        // Base linear fit
        Fit base = ridgeFit(y, X, /*lambda*/1e-8);
        double rss0 = base.rss;

        // yhat, add yhat^2 and yhat^3 as extra regressors
        double[] yhat = base.yhat;
        double[][] Xaug = new double[n][d + 2];
        for (int i = 0; i < n; i++) {
            System.arraycopy(X[i], 0, Xaug[i], 0, d);
            Xaug[i][d] = yhat[i] * yhat[i];
            Xaug[i][d + 1] = yhat[i] * yhat[i] * yhat[i];
        }

        Fit aug = ridgeFit(y, Xaug, 1e-8);
        double rss1 = aug.rss;

        int q = 2;                // added terms
        int p1 = d + q;            // regressors count (no intercept in this utility)
        int df2 = n - p1;

        if (df2 <= 2) return new TestResult(Double.NaN, Double.NaN, false);
        double num = (rss0 - rss1) / q;
        double den = rss1 / df2;
        if (!(den > 0)) return new TestResult(Double.NaN, Double.NaN, false);

        double F = num / den;
        if (!Double.isFinite(F) || F < 0) F = 0;

        double p = 1.0 - new FDistribution(q, df2).cumulativeProbability(F);
        boolean rej = Double.isFinite(p) && p < alpha;
        return new TestResult(F, p, rej);
    }

    // ============================================================
    // 2) CV: linear ridge vs nonlinear kernel-ridge via RFF
    // ============================================================

    public static TestResult cvLinearVsNonlinear(double[] y, double[][] X, int kfold, double alpha) {
        int n = y.length;
        int d = (X.length == 0) ? 0 : X[0].length;
        if (n < 20 || d == 0) return new TestResult(Double.NaN, Double.NaN, false);

        kfold = Math.max(2, Math.min(kfold, Math.max(2, n / 5)));

        int[] idx = shuffledIndices(n, 1729L);
        int[][] folds = makeFolds(idx, kfold);

        double[] diff = new double[kfold]; // mse_linear - mse_nonlinear (positive => nonlinear better)
        for (int f = 0; f < kfold; f++) {
            Split sp = splitFold(y, X, folds, f);

            // Linear ridge
            Fit lin = ridgeFit(sp.yTr, sp.XTr, 1e-4);
            double mseLin = mse(sp.yTe, predict(sp.XTe, lin.beta));

            // Nonlinear: kernel ridge via RFF
            // (simple, deterministic; enough for “is nonlinear predictor better?”)
            double sigma = medianPairwiseDistanceND(sp.XTr, Math.min(sp.XTr.length, 300));
            double mseNl = mseKernelRidgeRff(sp.yTr, sp.XTr, sp.yTe, sp.XTe,
                    /*numFeat*/200, /*lambda*/1e-3, sigma, 2468L + f);

            diff[f] = mseLin - mseNl;
        }

        // Paired t-test: H0 mean(diff)=0 vs H1 mean(diff)>0
        // commons-math TTest gives two-sided; use one-sided conversion.
        TTest tt = new TTest();
        double t = tt.t(0.0, diff);
        double p2 = tt.tTest(0.0, diff); // two-sided
        double p1 = (t > 0) ? (p2 / 2.0) : (1.0 - p2 / 2.0);

        boolean rej = Double.isFinite(p1) && p1 < alpha;
        return new TestResult(t, p1, rej);
    }

    // ============================================================
    // 3) Conditional-moment / nonlinear-features LM test
    // ============================================================

    public static TestResult conditionalMomentTest(double[] y, double[][] X, double alpha) {
        int n = y.length;
        int d = (X.length == 0) ? 0 : X[0].length;
        if (n < 20 || d == 0) return new TestResult(Double.NaN, Double.NaN, false);

        // Fit linear model -> residuals
        Fit lin = ridgeFit(y, X, 1e-8);
        double[] e = lin.resid;

        // Build nonlinear feature matrix G(X): squares, cubes, and pairwise products (lightweight)
        List<double[]> feats = new ArrayList<>();
        for (int j = 0; j < d; j++) {
            double[] x = col(X, j);
            feats.add(pow(x, 2));
            feats.add(pow(x, 3));
        }
        for (int a = 0; a < d; a++) {
            for (int b = a + 1; b < d; b++) {
                double[] xa = col(X, a);
                double[] xb = col(X, b);
                feats.add(prod(xa, xb));
            }
        }

        double[][] G = stack(feats, n);
        if (G[0].length == 0) return new TestResult(Double.NaN, Double.NaN, false);

        // Regress residual on G; LM = n * R^2 ~ ChiSq(df = #features) under H0
        Fit gfit = ridgeFit(e, G, 1e-8);
        double r2 = 1.0 - (gfit.rss / sstZeroMean(e));
        if (!Double.isFinite(r2) || r2 < 0) r2 = 0;
        if (r2 > 1) r2 = 1;

        int df = G[0].length;
        double lm = n * r2;

        double p = 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(lm);
        boolean rej = Double.isFinite(p) && p < alpha;
        return new TestResult(lm, p, rej);
    }

    // ============================================================
    // 4) Additive-component test (GAM-ish hinge basis)
    // ============================================================

    public static TestResult additiveHingeTest(double[] y, double[][] X, double alpha) {
        int n = y.length;
        int d = (X.length == 0) ? 0 : X[0].length;
        if (n < 20 || d == 0) return new TestResult(Double.NaN, Double.NaN, false);

        // Base linear
        Fit base = ridgeFit(y, X, 1e-8);
        double rss0 = base.rss;

        // Add per-variable hinge basis at a few quantiles (additive, not interactions)
        int knotsPerVar = 3;
        double[][] H = hingeBasis(X, knotsPerVar);
        double[][] Xaug = hcat(X, H);

        Fit aug = ridgeFit(y, Xaug, 1e-8);
        double rss1 = aug.rss;

        int q = H[0].length;            // added terms
        int p1 = d + q;
        int df2 = n - p1;
        if (q <= 0 || df2 <= 2) return new TestResult(Double.NaN, Double.NaN, false);

        double num = (rss0 - rss1) / q;
        double den = rss1 / df2;
        if (!(den > 0)) return new TestResult(Double.NaN, Double.NaN, false);

        double F = num / den;
        if (!Double.isFinite(F) || F < 0) F = 0;

        double p = 1.0 - new FDistribution(q, df2).cumulativeProbability(F);
        boolean rej = Double.isFinite(p) && p < alpha;
        return new TestResult(F, p, rej);
    }

    // ============================================================
    // ---------- small linear algebra utilities (ridge OLS) ----------
    // ============================================================

    private static final class Fit {
        final double[] beta;
        final double[] yhat;
        final double[] resid;
        final double rss;

        Fit(double[] beta, double[] yhat, double[] resid, double rss) {
            this.beta = beta;
            this.yhat = yhat;
            this.resid = resid;
            this.rss = rss;
        }
    }

    /** Ridge fit using normal equations with small gaussian elimination (OK for small d). */
    private static Fit ridgeFit(double[] y, double[][] X, double lambda) {
        int n = y.length;
        int d = (X.length == 0) ? 0 : X[0].length;

        // Compute XtX + lambda I and Xty
        double[][] A = new double[d][d];
        double[] b = new double[d];

        for (int i = 0; i < n; i++) {
            double yi = y[i];
            for (int j = 0; j < d; j++) {
                double xij = X[i][j];
                b[j] += xij * yi;
                for (int k = 0; k < d; k++) A[j][k] += xij * X[i][k];
            }
        }
        for (int j = 0; j < d; j++) A[j][j] += lambda;

        double[] beta = solveSymmetric(A, b);

        double[] yhat = new double[n];
        double[] resid = new double[n];
        double rss = 0.0;
        for (int i = 0; i < n; i++) {
            double fit = 0.0;
            for (int j = 0; j < d; j++) fit += X[i][j] * beta[j];
            yhat[i] = fit;
            double e = y[i] - fit;
            resid[i] = e;
            rss += e * e;
        }
        return new Fit(beta, yhat, resid, rss);
    }

    /** Very small SPD-ish solver via Gaussian elimination with partial pivoting (fine for modest d). */
    private static double[] solveSymmetric(double[][] A, double[] b) {
        int n = b.length;
        double[][] M = new double[n][n];
        double[] x = Arrays.copyOf(b, n);
        for (int i = 0; i < n; i++) M[i] = Arrays.copyOf(A[i], n);

        for (int p = 0; p < n; p++) {
            int max = p;
            double best = Math.abs(M[p][p]);
            for (int i = p + 1; i < n; i++) {
                double v = Math.abs(M[i][p]);
                if (v > best) { best = v; max = i; }
            }
            if (max != p) {
                double[] tmp = M[p]; M[p] = M[max]; M[max] = tmp;
                double t = x[p]; x[p] = x[max]; x[max] = t;
            }
            double piv = M[p][p];
            if (Math.abs(piv) < 1e-12) piv = (piv >= 0 ? 1e-12 : -1e-12);

            for (int i = p + 1; i < n; i++) {
                double a = M[i][p] / piv;
                x[i] -= a * x[p];
                for (int j = p; j < n; j++) M[i][j] -= a * M[p][j];
            }
        }

        double[] sol = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = x[i];
            for (int j = i + 1; j < n; j++) sum -= M[i][j] * sol[j];
            double piv = M[i][i];
            if (Math.abs(piv) < 1e-12) piv = (piv >= 0 ? 1e-12 : -1e-12);
            sol[i] = sum / piv;
        }
        return sol;
    }

    private static double[] predict(double[][] X, double[] beta) {
        int n = X.length;
        int d = beta.length;
        double[] yhat = new double[n];
        for (int i = 0; i < n; i++) {
            double s = 0.0;
            for (int j = 0; j < d; j++) s += X[i][j] * beta[j];
            yhat[i] = s;
        }
        return yhat;
    }

    private static double mse(double[] y, double[] yhat) {
        double s = 0.0;
        for (int i = 0; i < y.length; i++) {
            double e = y[i] - yhat[i];
            s += e * e;
        }
        return s / y.length;
    }

    private static int[] shuffledIndices(int n, long seed) {
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Random r = new Random(seed);
        for (int i = n - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = idx[i]; idx[i] = idx[j]; idx[j] = t;
        }
        return idx;
    }

    private static int[][] makeFolds(int[] idx, int k) {
        int n = idx.length;
        int[][] folds = new int[k][];
        int base = n / k;
        int rem = n % k;
        int pos = 0;
        for (int f = 0; f < k; f++) {
            int sz = base + (f < rem ? 1 : 0);
            folds[f] = new int[sz];
            System.arraycopy(idx, pos, folds[f], 0, sz);
            pos += sz;
        }
        return folds;
    }

    private static final class Split {
        final double[] yTr, yTe;
        final double[][] XTr, XTe;
        Split(double[] yTr, double[][] XTr, double[] yTe, double[][] XTe) {
            this.yTr = yTr; this.XTr = XTr; this.yTe = yTe; this.XTe = XTe;
        }
    }

    private static Split splitFold(double[] y, double[][] X, int[][] folds, int f) {
        int n = y.length;
        boolean[] isTest = new boolean[n];
        for (int id : folds[f]) isTest[id] = true;

        int d = X[0].length;

        int nTe = folds[f].length;
        int nTr = n - nTe;

        double[] yTr = new double[nTr];
        double[][] XTr = new double[nTr][d];
        double[] yTe = new double[nTe];
        double[][] XTe = new double[nTe][d];

        int it = 0, ie = 0;
        for (int i = 0; i < n; i++) {
            if (isTest[i]) {
                yTe[ie] = y[i];
                System.arraycopy(X[i], 0, XTe[ie], 0, d);
                ie++;
            } else {
                yTr[it] = y[i];
                System.arraycopy(X[i], 0, XTr[it], 0, d);
                it++;
            }
        }
        return new Split(yTr, XTr, yTe, XTe);
    }

    // --------- CV nonlinear model: kernel ridge via RFF ---------

    private static double mseKernelRidgeRff(double[] yTr, double[][] XTr,
                                            double[] yTe, double[][] XTe,
                                            int m, double lambda, double sigma, long seed) {
        if (!(sigma > 0) || !Double.isFinite(sigma)) sigma = 1.0;

        // Build RFF features Phi(X): cos+sin pairs (lower variance)
        double[][] PhiTr = rffCosSin(XTr, m, sigma, seed);
        double[][] PhiTe = rffCosSin(XTe, m, sigma, seed);

        Fit fit = ridgeFit(yTr, PhiTr, lambda);
        double[] yhat = predict(PhiTe, fit.beta);
        return mse(yTe, yhat);
    }

    private static double[][] rffCosSin(double[][] X, int m, double sigma, long seed) {
        int n = X.length;
        int d = X[0].length;

        int mf = Math.max(1, m / 2);
        int outM = 2 * mf;

        Random rng = new Random(seed);
        double[][] W = new double[mf][d];
        double[] b = new double[mf];

        double wStd = 1.0 / sigma;
        for (int i = 0; i < mf; i++) {
            for (int j = 0; j < d; j++) W[i][j] = rng.nextGaussian() * wStd;
            b[i] = rng.nextDouble() * 2.0 * Math.PI;
        }

        double scale = Math.sqrt(2.0 / outM);
        double[][] Phi = new double[n][outM];

        for (int i = 0; i < n; i++) {
            int col = 0;
            for (int f = 0; f < mf; f++) {
                double dot = 0.0;
                for (int j = 0; j < d; j++) dot += W[f][j] * X[i][j];
                double t = dot + b[f];
                Phi[i][col++] = scale * Math.cos(t);
                Phi[i][col++] = scale * Math.sin(t);
            }
        }
        return Phi;
    }

    // --------- moment/additive helper features ---------

    private static double[] col(double[][] X, int j) {
        double[] out = new double[X.length];
        for (int i = 0; i < X.length; i++) out[i] = X[i][j];
        return out;
    }

    private static double[] pow(double[] x, int p) {
        double[] out = new double[x.length];
        for (int i = 0; i < x.length; i++) out[i] = Math.pow(x[i], p);
        return out;
    }

    private static double[] prod(double[] a, double[] b) {
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] * b[i];
        return out;
    }

    private static double[][] stack(List<double[]> cols, int n) {
        int d = cols.size();
        if (d == 0) return new double[n][0];
        double[][] M = new double[n][d];
        for (int j = 0; j < d; j++) {
            double[] c = cols.get(j);
            for (int i = 0; i < n; i++) M[i][j] = c[i];
        }
        return M;
    }

    private static double sstZeroMean(double[] y) {
        double s = 0.0;
        for (double v : y) s += v * v;
        return s;
    }

    private static double[][] hcat(double[][] A, double[][] B) {
        int n = A.length;
        int da = A[0].length;
        int db = B[0].length;
        double[][] out = new double[n][da + db];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, out[i], 0, da);
            System.arraycopy(B[i], 0, out[i], da, db);
        }
        return out;
    }

    private static double[][] hingeBasis(double[][] X, int knotsPerVar) {
        int n = X.length;
        int d = X[0].length;

        List<double[]> feats = new ArrayList<>();
        for (int j = 0; j < d; j++) {
            double[] x = col(X, j);
            double[] xs = Arrays.copyOf(x, x.length);
            Arrays.sort(xs);

            for (int k = 1; k <= knotsPerVar; k++) {
                double q = k / (double) (knotsPerVar + 1);
                int idx = Math.min(xs.length - 1, Math.max(0, (int) Math.floor(q * (xs.length - 1))));
                double knot = xs[idx];

                double[] h = new double[n];
                for (int i = 0; i < n; i++) h[i] = Math.max(0.0, x[i] - knot);
                feats.add(h);
            }
        }
        return stack(feats, n);
    }

    private static double medianPairwiseDistanceND(double[][] X, int limit) {
        int n = Math.min(X.length, limit);
        if (n < 3) return 1.0;
        int d = X[0].length;
        if (d == 0) return 1.0;

        double[] dists = new double[n * (n - 1) / 2];
        int idx = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double ss = 0.0;
                for (int k = 0; k < d; k++) {
                    double diff = X[i][k] - X[j][k];
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
}
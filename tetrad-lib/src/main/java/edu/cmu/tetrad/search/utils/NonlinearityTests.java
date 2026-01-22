package edu.cmu.tetrad.search.utils;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.FDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.inference.TTest;

import java.util.*;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;

import java.util.Arrays;
import java.util.Random;

/**
 * Practical nonlinearity tests for conditional mean E(Y|X).
 * All methods assume continuous variables and use testwise deletion via clean(...).
 */
public final class NonlinearityTests {

    private NonlinearityTests() {
    }

    /**
     * Encapsulates the result of a statistical test, including the test statistic, p-value,
     * and the decision to reject the null hypothesis.
     *
     * This class is immutable and provides information about the outcome of a hypothesis test.
     */
    public static final class TestResult {

        /**
         * Represents the value of the test statistic computed during a statistical hypothesis test.
         *
         * This value is used to determine the evidence against the null hypothesis based on the
         * context of the test being performed. The interpretation of this value depends on the specific
         * test type and its corresponding distribution.
         */
        public final double statistic;

        /**
         * Represents the p-value of a statistical hypothesis test.
         *
         * The p-value is the probability of observing a test statistic as extreme as,
         * or more extreme than, the one observed, under the assumption that the null
         * hypothesis is true. It quantifies the strength of evidence against the null
         * hypothesis. A smaller p-value indicates stronger evidence against the null
         * hypothesis.
         *
         * If the p-value is not applicable or cannot be computed, this value is set to NaN.
         */
        public final double pValue;   // NaN if not applicable

        /**
         * Indicates whether the null hypothesis of the statistical hypothesis test is rejected.
         */
        public final boolean reject;

        /**
         * Constructs a TestResult instance with the specified test statistic, p-value,
         * and decision on whether to reject the null hypothesis.
         *
         * @param statistic the value of the test statistic computed in the statistical hypothesis test
         * @param pValue the p-value of the test, representing the probability of observing a test statistic
         *               as extreme as, or more extreme than, the one observed, under the null hypothesis
         * @param reject a boolean value indicating whether the null hypothesis is rejected based on the test result
         */
        public TestResult(double statistic, double pValue, boolean reject) {
            this.statistic = statistic;
            this.pValue = pValue;
            this.reject = reject;
        }

        /**
         * Returns a string representation of this TestResult instance.
         *
         * The returned string includes the test statistic, p-value, and the rejection decision
         * of the null hypothesis, formatted as key-value pairs.
         *
         * @return a string representation of the TestResult in the format:
         *         "stat={statistic}, p={pValue}, reject={reject}"
         */
        @Override
        public String toString() {
            return "stat=" + statistic + ", p=" + pValue + ", reject=" + reject;
        }
    }

    /**
     * Represents cleaned data for nonlinearity tests, with NaNs removed from y and X.
     */
    public static final class CleanData {

        /**
         * The array of dependent variable values, cleaned of NaNs.
         */
        public final double[] y;

        /**
         * The 2D array of independent variable values, cleaned of NaNs.
         */
        public final double[][] X;

        /**
         * Constructs a CleanData object with the provided data arrays.
         *
         * @param y the array of dependent variable values, cleaned of NaNs
         * @param X the 2D array of independent variable values, cleaned of NaNs
         */
        public CleanData(double[] y, double[][] X) {
            this.y = y;
            this.X = X;
        }
    }

    /**
     * Remove rows with NaNs in y or any X column.
     *
     * @param y the array of dependent variable values
     * @param X the 2D array of independent variable values
     * @return a CleanData object containing the cleaned data arrays
     */
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

    /**
     * Performs the RESET (Regression Equation Specification Error Test) to assess model specification.
     * The test evaluates whether nonlinear transformations of the predicted values improve the model's fit.
     *
     * @param y the response variable array, of length n
     * @param X the predictor variable matrix, of size n-by-d
     * @param alpha the significance level for the hypothesis test
     * @return a TestResult object containing the test statistic, p-value, and a boolean indicating whether
     *         the null hypothesis is rejected
     */
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

    /**
     * Performs cross-validation to compare linear ridge regression with nonlinear kernel ridge regression using Random Fourier Features (RFF).
     * @param y The target variable values.
     * @param X The feature matrix.
     * @param kfold The number of folds for cross-validation.
     * @param alpha The significance level for hypothesis testing.
     * @return TestResult containing F-statistic, p-value, and rejection status.
     */
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

    /**
     * Performs a conditional moment test to assess the specification of a linear model.
     * This test evaluates whether non-linear transformations of the predictor variables
     * contain additional information not captured by the linear model.
     *
     * @param y the response variable array, of length n
     * @param X the predictor variable matrix, of size n-by-d
     * @param alpha the significance level for hypothesis testing
     * @return a TestResult object containing the test statistic, p-value, and a boolean indicating
     *         whether the null hypothesis of linearity is rejected
     */
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

    /**
     * Performs an additive-component test using hinge basis functions to assess nonlinearity in the data.
     *
     * @param y The response variable.
     * @param X The predictor variables.
     * @param alpha The significance level for hypothesis testing.
     * @return A TestResult object containing the test statistic, p-value, and rejection decision.
     */
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

    /**
     * Ridge fit using normal equations with small gaussian elimination (OK for small d).
     */
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

    /**
     * Very small SPD-ish solver via Gaussian elimination with partial pivoting (fine for modest d).
     */
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
                if (v > best) {
                    best = v;
                    max = i;
                }
            }
            if (max != p) {
                double[] tmp = M[p];
                M[p] = M[max];
                M[max] = tmp;
                double t = x[p];
                x[p] = x[max];
                x[max] = t;
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

    private static int[] shuffledIndices(int n, long seed) {
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Random r = new Random(seed);
        for (int i = n - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = idx[i];
            idx[i] = idx[j];
            idx[j] = t;
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
            this.yTr = yTr;
            this.XTr = XTr;
            this.yTe = yTe;
            this.XTe = XTe;
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

    // In edu.cmu.tetrad.search.utils.NonlinearityTests


    /**
     * Compares additive hinge-basis ridge regression with full RFF-ridge (RBF kernel) using k-fold cross-validation.
     * Returns a TestResult object containing the test statistic, p-value, and decision to reject the null hypothesis.
     *
     * @param y       the array of dependent variable values
     * @param X       the 2D array of independent variable values
     * @param kfold   the number of folds for cross-validation
     * @param alpha   the regularization parameter for ridge regression
     * @return        a TestResult object with the test statistic, p-value, and decision to reject the null hypothesis
     */
    public static TestResult cvAdditiveVsRff(double[] y, double[][] X, int kfold, double alpha) {
        // Compares:
        //   Additive hinge-basis ridge  vs  Full RFF-ridge (RBF kernel) using k-fold CV.
        // reject=true => "Non-additive" (general model significantly better).

        if (y == null || X == null || y.length == 0 || X.length != y.length) {
            return new TestResult(Double.NaN, Double.NaN, false);
        }
        int n = y.length;
        int d = X[0].length;

        if (d < 2 || n < 30) {
            // With <2 regressors additivity isn't meaningful; with too small n CV is noisy.
            return new TestResult(Double.NaN, Double.NaN, false);
        }

        // --- sane fold count (like your other CV method) ---
        int folds = Math.max(2, Math.min(kfold, Math.max(2, n / 5)));

        final long seed = 1729L;

        // Hyperparams (UI tool): keep modest but not tiny.
        final double ridge = 1e-3;

        final int knotsPerVar = 6;

        // RFF feature count: you can bump this (e.g., 600 or 1000) when you want more power.
        final int rffFeatures = 600;
        final boolean useCosSinPairs = true;

        // ---- make folds ----
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i;
        shuffleInPlace(perm, new Random(seed));

        int[] foldId = new int[n];
        for (int i = 0; i < n; i++) foldId[perm[i]] = i % folds;

        double[] diffs = new double[folds];  // diff = mse_additive - mse_rff ; positive => RFF better => non-additive
        Arrays.fill(diffs, Double.NaN);

        for (int f = 0; f < folds; f++) {
            // Split indices
            int nTrain = 0, nTest = 0;
            for (int i = 0; i < n; i++) {
                if (foldId[i] == f) nTest++;
                else nTrain++;
            }
            if (nTrain < 15 || nTest < 8) continue;

            int[] tr = new int[nTrain];
            int[] te = new int[nTest];
            int it = 0, ie = 0;
            for (int i = 0; i < n; i++) {
                if (foldId[i] == f) te[ie++] = i;
                else tr[it++] = i;
            }

            double[] yTr = take(y, tr);
            double[] yTe = take(y, te);

            // Center y using TRAIN mean
            double yMean = mean(yTr);
            for (int i = 0; i < yTr.length; i++) yTr[i] -= yMean;
            for (int i = 0; i < yTe.length; i++) yTe[i] -= yMean;

            double[][] XTr = takeRows(X, tr);
            double[][] XTe = takeRows(X, te);

            // Z-score X using TRAIN stats
            Standardizer st = new Standardizer(XTr);
            st.applyInPlace(XTr);
            st.applyInPlace(XTe);

            // ---- additive hinge design ----
            AdditiveHingeBasis basis = new AdditiveHingeBasis(XTr, knotsPerVar);
            DMatrixRMaj PhiTrAdd = basis.designMatrix(XTr);
            DMatrixRMaj PhiTeAdd = basis.designMatrix(XTe);

            double[] yHatAdd = ridgePredict(PhiTrAdd, yTr, PhiTeAdd, ridge);
            double mseAdd = mse(yTe, yHatAdd);

            // ---- RFF design (RBF) ----
            double sigma = medianPairwiseDistanceND(XTr, Math.min(400, XTr.length));
            if (!(sigma > 0) || !Double.isFinite(sigma)) sigma = 1.0;

            Random rng = new Random(mix64(seed ^ (long) f));
            RffMap rff = new RffMap(d, rffFeatures, sigma, rng, useCosSinPairs);

            DMatrixRMaj PhiTr = rff.transform(XTr);
            DMatrixRMaj PhiTe = rff.transform(XTe);

            // Add an intercept column to RFF features (important in practice)
            PhiTr = addInterceptColumn(PhiTr);
            PhiTe = addInterceptColumn(PhiTe);

            // Standardize RFF columns using TRAIN stats (skip intercept col=0)
            zscoreWithTrainStatsSkipFirst(PhiTr, PhiTe);

            double[] yHatRff = ridgePredict(PhiTr, yTr, PhiTe, ridge);
            double mseRff = mse(yTe, yHatRff);

            diffs[f] = mseAdd - mseRff; // positive => RFF better (non-additive evidence)
        }

        // Keep only valid folds
        double[] good = Arrays.stream(diffs).filter(Double::isFinite).toArray();
        if (good.length < 3) return new TestResult(Double.NaN, Double.NaN, false);

        double meanDiff = mean(good);
        double p = pairedTTestGreaterThanZeroPValue(good);

        boolean reject = Double.isFinite(p) ? (p < alpha) : (meanDiff > 0);

        // statistic = mean CV improvement (additive - rff) [positive => RFF better]
        return new TestResult(meanDiff, p, reject);
    }

    // ----- helpers for intercept + skipping intercept in z-score -----

    private static DMatrixRMaj addInterceptColumn(DMatrixRMaj M) {
        DMatrixRMaj out = new DMatrixRMaj(M.numRows, M.numCols + 1);
        for (int i = 0; i < M.numRows; i++) {
            out.set(i, 0, 1.0);
            for (int j = 0; j < M.numCols; j++) out.set(i, j + 1, M.get(i, j));
        }
        return out;
    }

    private static void zscoreWithTrainStatsSkipFirst(DMatrixRMaj train, DMatrixRMaj test) {
        int nTr = train.numRows;
        int nTe = test.numRows;
        int d = train.numCols;
        if (nTr < 2 || d <= 1) return;

        for (int j = 1; j < d; j++) {
            double sum = 0.0, sumsq = 0.0;
            for (int i = 0; i < nTr; i++) {
                double v = train.get(i, j);
                sum += v;
                sumsq += v * v;
            }
            double mean = sum / nTr;
            double var = (sumsq - nTr * mean * mean) / (nTr - 1);
            double sd = (var > 0) ? Math.sqrt(var) : 1.0;

            for (int i = 0; i < nTr; i++) train.set(i, j, (train.get(i, j) - mean) / sd);
            for (int i = 0; i < nTe; i++) test.set(i, j, (test.get(i, j) - mean) / sd);
        }
    }

    private static void zscoreColumnsInPlaceSkipFirst(DMatrixRMaj M) {
        int n = M.numRows, d = M.numCols;
        if (n < 2 || d <= 1) return;

        for (int j = 1; j < d; j++) {
            double sum = 0.0, sumsq = 0.0;
            for (int i = 0; i < n; i++) {
                double v = M.get(i, j);
                sum += v;
                sumsq += v * v;
            }
            double mean = sum / n;
            double var = (sumsq - n * mean * mean) / (n - 1);
            double sd = (var > 0) ? Math.sqrt(var) : 1.0;

            for (int i = 0; i < n; i++) M.set(i, j, (M.get(i, j) - mean) / sd);
        }
    }

    private static void zscoreColumnsInPlaceWithTrainStatsSkipFirst(DMatrixRMaj M, DMatrixRMaj train) {
        int n = M.numRows, d = M.numCols;
        int nTr = train.numRows;
        if (nTr < 2 || d <= 1) return;

        for (int j = 1; j < d; j++) {
            double sum = 0.0, sumsq = 0.0;
            for (int i = 0; i < nTr; i++) {
                double v = train.get(i, j);
                sum += v;
                sumsq += v * v;
            }
            double mean = sum / nTr;
            double var = (sumsq - nTr * mean * mean) / (nTr - 1);
            double sd = (var > 0) ? Math.sqrt(var) : 1.0;

            for (int i = 0; i < n; i++) M.set(i, j, (M.get(i, j) - mean) / sd);
        }
    }

    /* ---------------- helpers used above ---------------- */

    private static double[] ridgePredict(DMatrixRMaj PhiTr, double[] yTr, DMatrixRMaj PhiTe, double ridge) {
        int n = PhiTr.numRows;
        int m = PhiTr.numCols;

        // A = Phi^T Phi + ridge I
        DMatrixRMaj A = new DMatrixRMaj(m, m);
        CommonOps_DDRM.multTransA(PhiTr, PhiTr, A);
        addDiagonalInPlace(A, ridge);

        // b = Phi^T y
        DMatrixRMaj b = new DMatrixRMaj(m, 1);
        multTransA_vec(PhiTr, yTr, b);

        LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.symmPosDef(m);
        if (!solver.setA(A)) {
            // mild fallback jitter
            DMatrixRMaj Aj = A.copy();
            addDiagonalInPlace(Aj, Math.max(1e-10, ridge));
            if (!solver.setA(Aj)) return fillNaN(PhiTe.numRows);
        }

        DMatrixRMaj beta = new DMatrixRMaj(m, 1);
        solver.solve(b, beta);

        // yhat = PhiTe * beta
        double[] yHat = new double[PhiTe.numRows];
        for (int i = 0; i < PhiTe.numRows; i++) {
            double s = 0.0;
            int base = i * m;
            for (int j = 0; j < m; j++) s += PhiTe.data[base + j] * beta.data[j];
            yHat[i] = s;
        }
        return yHat;
    }

    private static double[] fillNaN(int n) {
        double[] x = new double[n];
        Arrays.fill(x, Double.NaN);
        return x;
    }

    private static double mse(double[] y, double[] yhat) {
        double s = 0.0;
        int n = y.length;
        for (int i = 0; i < n; i++) {
            double e = y[i] - yhat[i];
            s += e * e;
        }
        return s / n;
    }

    private static double mean(double[] x) {
        double s = 0.0;
        for (double v : x) s += v;
        return s / x.length;
    }

    private static double varSample(double[] x) {
        if (x.length < 2) return Double.NaN;
        double mu = mean(x);
        double s = 0.0;
        for (double v : x) {
            double d = v - mu;
            s += d * d;
        }
        return s / (x.length - 1);
    }

    /** One-sided paired t-test p-value for H1: mean(diffs) > 0. */
    private static double pairedTTestGreaterThanZeroPValue(double[] diffs) {
        int n = diffs.length;
        if (n < 3) return Double.NaN;

        double mu = mean(diffs);
        double s2 = varSample(diffs);
        if (!(s2 > 0) || !Double.isFinite(s2)) return Double.NaN;

        double t = mu / Math.sqrt(s2 / n);

        // Use normal approx if you don't want Apache commons:
        // p = 1 - Phi(t)
        // (This is fine for UI diagnostics; folds usually ~10.)
        TDistribution dist = new TDistribution(n - 1);
        return 1.0 - dist.cumulativeProbability(t);
    }

    private static double normalCdf(double z) {
        // Abramowitz-Stegun erf approximation
        // Phi(z) = 0.5 * (1 + erf(z/sqrt(2)))
        double x = z / Math.sqrt(2.0);
        return 0.5 * (1.0 + erfApprox(x));
    }

    private static double erfApprox(double x) {
        // numerical approximation to erf
        double sign = (x < 0) ? -1.0 : 1.0;
        x = Math.abs(x);

        double a1 = 0.254829592;
        double a2 = -0.284496736;
        double a3 = 1.421413741;
        double a4 = -1.453152027;
        double a5 = 1.061405429;
        double p = 0.3275911;

        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
        return sign * y;
    }

    private static void shuffleInPlace(int[] a, Random rng) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = a[i]; a[i] = a[j]; a[j] = tmp;
        }
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private static double[] take(double[] y, int[] idx) {
        double[] out = new double[idx.length];
        for (int i = 0; i < idx.length; i++) out[i] = y[idx[i]];
        return out;
    }

    private static double[][] takeRows(double[][] X, int[] idx) {
        int n = idx.length;
        int d = X[0].length;
        double[][] out = new double[n][d];
        for (int i = 0; i < n; i++) {
            int r = idx[i];
            System.arraycopy(X[r], 0, out[i], 0, d);
        }
        return out;
    }

    private static final class Standardizer {
        final int d;
        final double[] mean;
        final double[] sd;

        Standardizer(double[][] X) {
            int n = X.length;
            this.d = X[0].length;
            this.mean = new double[d];
            this.sd = new double[d];

            for (int j = 0; j < d; j++) {
                double s = 0.0;
                for (int i = 0; i < n; i++) s += X[i][j];
                mean[j] = s / n;

                double ss = 0.0;
                for (int i = 0; i < n; i++) {
                    double v = X[i][j] - mean[j];
                    ss += v * v;
                }
                double var = (n > 1) ? ss / (n - 1) : 0.0;
                sd[j] = (var > 0) ? Math.sqrt(var) : 1.0;
            }
        }

        void applyInPlace(double[][] X) {
            for (int i = 0; i < X.length; i++) {
                for (int j = 0; j < d; j++) {
                    X[i][j] = (X[i][j] - mean[j]) / sd[j];
                }
            }
        }
    }

    private static final class AdditiveHingeBasis {
        final int d;
        final int k;
        final double[][] knots; // [d][k]
        final boolean includeLinear = true;
        final boolean includeIntercept = true;

        AdditiveHingeBasis(double[][] Xtr, int knotsPerVar) {
            this.d = Xtr[0].length;
            this.k = Math.max(2, knotsPerVar);
            this.knots = new double[d][k];

            for (int j = 0; j < d; j++) {
                double[] col = new double[Xtr.length];
                for (int i = 0; i < Xtr.length; i++) col[i] = Xtr[i][j];
                Arrays.sort(col);

                // choose interior quantile knots
                for (int t = 0; t < k; t++) {
                    double q = (t + 1.0) / (k + 1.0); // (0,1)
                    int idx = (int) Math.round(q * (col.length - 1));
                    idx = Math.max(0, Math.min(col.length - 1, idx));
                    knots[j][t] = col[idx];
                }
            }
        }

        DMatrixRMaj designMatrix(double[][] X) {
            int n = X.length;
            int perVar = (includeLinear ? 1 : 0) + k; // x plus k hinges
            int m = (includeIntercept ? 1 : 0) + d * perVar;

            DMatrixRMaj Phi = new DMatrixRMaj(n, m);

            for (int i = 0; i < n; i++) {
                int col = 0;
                if (includeIntercept) {
                    Phi.set(i, col++, 1.0);
                }
                for (int j = 0; j < d; j++) {
                    double x = X[i][j];
                    if (includeLinear) Phi.set(i, col++, x);
                    for (int t = 0; t < k; t++) {
                        double h = x - knots[j][t];
                        Phi.set(i, col++, (h > 0) ? h : 0.0);
                    }
                }
            }

            return Phi;
        }
    }

    private static final class RffMap {
        final int d;
        final int outM;
        final double[][] W;
        final double[] b;
        final boolean useCosSinPairs;
        final double scale;

        RffMap(int d, int m, double sigma, Random rng, boolean useCosSinPairs) {
            this.d = d;
            this.useCosSinPairs = useCosSinPairs;

            if (!useCosSinPairs) {
                this.outM = Math.max(1, m);
                this.W = new double[outM][d];
                this.b = new double[outM];
                double wStd = 1.0 / sigma;
                for (int i = 0; i < outM; i++) {
                    for (int j = 0; j < d; j++) W[i][j] = rng.nextGaussian() * wStd;
                    b[i] = rng.nextDouble() * 2.0 * Math.PI;
                }
                this.scale = Math.sqrt(2.0 / outM);
            } else {
                int mf = Math.max(1, m / 2);
                this.outM = 2 * mf;
                this.W = new double[mf][d];
                this.b = new double[mf];
                double wStd = 1.0 / sigma;
                for (int i = 0; i < mf; i++) {
                    for (int j = 0; j < d; j++) W[i][j] = rng.nextGaussian() * wStd;
                    b[i] = rng.nextDouble() * 2.0 * Math.PI;
                }
                this.scale = Math.sqrt(2.0 / outM);
            }
        }

        DMatrixRMaj transform(double[][] X) {
            int n = X.length;
            DMatrixRMaj Phi = new DMatrixRMaj(n, outM);

            if (!useCosSinPairs) {
                for (int i = 0; i < n; i++) {
                    double[] xi = X[i];
                    for (int f = 0; f < outM; f++) {
                        double dot = 0.0;
                        double[] wf = W[f];
                        for (int j = 0; j < d; j++) dot += wf[j] * xi[j];
                        Phi.set(i, f, scale * Math.cos(dot + b[f]));
                    }
                }
                return Phi;
            }

            int mf = outM / 2;
            for (int i = 0; i < n; i++) {
                double[] xi = X[i];
                int col = 0;
                for (int f = 0; f < mf; f++) {
                    double dot = 0.0;
                    double[] wf = W[f];
                    for (int j = 0; j < d; j++) dot += wf[j] * xi[j];
                    double t = dot + b[f];
                    Phi.set(i, col++, scale * Math.cos(t));
                    Phi.set(i, col++, scale * Math.sin(t));
                }
            }
            return Phi;
        }
    }

    private static double medianPairwiseDistanceND(double[][] X, int limit) {
        int n = Math.min(X.length, limit);
        if (n < 3) return 1.0;
        int d = X[0].length;

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

    private static void addDiagonalInPlace(DMatrixRMaj M, double v) {
        int n = Math.min(M.numRows, M.numCols);
        for (int i = 0; i < n; i++) M.add(i, i, v);
    }

    private static void multTransA_vec(DMatrixRMaj A, double[] x, DMatrixRMaj out) {
        int n = A.numRows;
        int m = A.numCols;
        if (out.numRows != m || out.numCols != 1) throw new IllegalArgumentException("out dim mismatch");
        Arrays.fill(out.data, 0.0);
        for (int i = 0; i < n; i++) {
            double xi = x[i];
            int idx = i * m;
            for (int j = 0; j < m; j++) out.data[j] += A.data[idx + j] * xi;
        }
    }
}
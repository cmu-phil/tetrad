// v1: New regression helper for the adjustment effect estimator.
// v1: Purpose: (1) OLS least squares solve + predict, (2) logistic regression via IRLS.
// v1: Uses EJML 0.44.0 SimpleMatrix.
// v1: No p-values, no significance graph; just stable numeric fits.

package edu.cmu.tetrad.regression.v1;

import org.ejml.simple.SimpleMatrix;

public final class RegressionUtilV1 {

    private RegressionUtilV1() { }

    // =========================
    // v1: OLS
    // =========================

    /** v1: OLS fit via QR-based least squares: beta = argmin ||X*beta - y||_2. */
    public static FitOlsV1 olsFitV1(SimpleMatrix X, SimpleMatrix y) {
        if (X.numRows() != y.numRows() || y.numCols() != 1) {
            throw new IllegalArgumentException("v1: OLS requires X(nxp), y(nx1).");
        }
        // v1: EJML SimpleMatrix.solve uses a least-squares solver when X is not square.
        SimpleMatrix beta = X.solve(y);
        return new FitOlsV1(beta);
    }

    /** v1: OLS fit with ridge (Tikhonov) via normal equations: (X'X + λI)β = X'y. */
    public static FitOlsV1 olsFitRidgeV1(SimpleMatrix X, SimpleMatrix y, double ridgeLambda) {
        if (ridgeLambda <= 0) return olsFitV1(X, y);
        if (X.numRows() != y.numRows() || y.numCols() != 1) {
            throw new IllegalArgumentException("v1: OLS requires X(nxp), y(nx1).");
        }
        int p = X.numCols();
        SimpleMatrix XtX = X.transpose().mult(X);
        for (int j = 0; j < p; j++) XtX.set(j, j, XtX.get(j, j) + ridgeLambda);
        SimpleMatrix Xty = X.transpose().mult(y);
        SimpleMatrix beta = XtX.solve(Xty);
        return new FitOlsV1(beta);
    }

    public static final class FitOlsV1 {
        public final SimpleMatrix beta; // v1: (p x 1)

        FitOlsV1(SimpleMatrix beta) {
            this.beta = beta;
        }

        /** v1: Predict yhat = X*beta. Returns double[n]. */
        public double[] predictV1(SimpleMatrix X) {
            SimpleMatrix yhat = X.mult(beta);
            double[] out = new double[yhat.numRows()];
            for (int i = 0; i < out.length; i++) out[i] = yhat.get(i, 0);
            return out;
        }
    }

    // =========================
    // v1: Logistic regression via IRLS
    // =========================

    /**
     * v1: Fit logistic regression using IRLS on design matrix X (n x p) with binary y01 (length n).
     * v1: Returns coefficients w (p x 1) for logit(P(y=1|x)) = X*w.
     */
    public static FitLogitV1 logitFitIrlsV1(
            SimpleMatrix X,
            int[] y01,
            int maxIter,
            double tol,
            double ridgeLambda
    ) {
        int n = X.numRows();
        int p = X.numCols();
        if (y01.length != n) throw new IllegalArgumentException("v1: y length mismatch.");
        if (maxIter <= 0) maxIter = 50;

        // v1: coefficients
        SimpleMatrix w = new SimpleMatrix(p, 1);

        double prevLl = Double.NEGATIVE_INFINITY;

        // v1: working arrays
        double[] pHat = new double[n];
        double[] wgt = new double[n];
        double[] z = new double[n];

        for (int iter = 0; iter < maxIter; iter++) {
            double ll = 0.0;

            // v1: compute probabilities, weights, working response
            for (int i = 0; i < n; i++) {
                double eta = 0.0;
                for (int j = 0; j < p; j++) eta += X.get(i, j) * w.get(j, 0);

                double pi = sigmoidV1(eta);
                pi = clipV1(pi, 1e-6, 1.0 - 1e-6); // v1: numerical safety

                pHat[i] = pi;

                double vi = pi * (1.0 - pi);
                wgt[i] = Math.max(vi, 1e-9);       // v1: avoid divide-by-zero

                z[i] = eta + (y01[i] - pi) / wgt[i];

                ll += y01[i] * Math.log(pi) + (1 - y01[i]) * Math.log(1 - pi);
            }

            if (Math.abs(ll - prevLl) < tol) break;
            prevLl = ll;

            // v1: weighted least squares solve:
            // minimize Σ wgt_i (z_i - X_i w)^2  => (X' W X + λI) w = X' W z
            SimpleMatrix XtWX = new SimpleMatrix(p, p);
            SimpleMatrix XtWz = new SimpleMatrix(p, 1);

            for (int i = 0; i < n; i++) {
                double wi = wgt[i];
                for (int a = 0; a < p; a++) {
                    double xia = X.get(i, a);
                    XtWz.set(a, 0, XtWz.get(a, 0) + wi * xia * z[i]);
                    for (int b = 0; b < p; b++) {
                        XtWX.set(a, b, XtWX.get(a, b) + wi * xia * X.get(i, b));
                    }
                }
            }

            if (ridgeLambda > 0) {
                for (int j = 0; j < p; j++) XtWX.set(j, j, XtWX.get(j, j) + ridgeLambda);
            }

            w = XtWX.solve(XtWz);
        }

        return new FitLogitV1(w);
    }

    public static final class FitLogitV1 {
        public final SimpleMatrix w; // v1: (p x 1)

        FitLogitV1(SimpleMatrix w) {
            this.w = w;
        }

        /** v1: Predict probabilities p = sigmoid(X*w). */
        public double[] predictProbV1(SimpleMatrix X) {
            int n = X.numRows();
            int p = X.numCols();
            if (w.numRows() != p) throw new IllegalArgumentException("v1: X columns must match w rows.");
            double[] out = new double[n];
            for (int i = 0; i < n; i++) {
                double eta = 0.0;
                for (int j = 0; j < p; j++) eta += X.get(i, j) * w.get(j, 0);
                out[i] = sigmoidV1(eta);
            }
            return out;
        }
    }

    // =========================
    // v1: Helpers
    // =========================

    private static double sigmoidV1(double x) {
        if (x >= 0) {
            double z = Math.exp(-x);
            return 1.0 / (1.0 + z);
        } else {
            double z = Math.exp(x);
            return z / (1.0 + z);
        }
    }

    private static double clipV1(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
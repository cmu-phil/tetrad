///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.util.FastMath;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import static org.apache.commons.math3.util.FastMath.*;

/**
 * FastICA (real-valued) translated for Tetrad.
 *
 * Key stability fixes:
 *  - Correct derivative in symmetric (parallel) update: g'(u) = α(1 - g(u)^2) for logcosh.
 *  - Whitening ridge (eps) to avoid exploding 1/sqrt(λ) on tiny eigenvalues.
 *  - Orthonormalize random wInit via SVD (helps convergence).
 *  - Small deflation loop fix (row assignment index).
 *
 * Reference:
 *   Hyvarinen & Oja (2000) Independent Component Analysis: Algorithms and Applications. Neural Networks 13(4–5):411–430.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class FastIca {

    /** Extract components simultaneously (symmetric decorrelation). */
    public static int PARALLEL;

    /** Extract components one-at-a-time. */
    public static int DEFLATION = 1;

    /** Neg-entropy nonlinearity: logcosh. */
    public static int LOGCOSH = 2;

    /** Neg-entropy nonlinearity: exp. */
    public static int EXP = 3;

    /** Data matrix (rows = cases, cols = variables) after preselect; no missing values. */
    private final Matrix X;

    /** Number of components. */
    private int numComponents;

    /** Algorithm type (PARALLEL or DEFLATION). */
    private int algorithmType;

    /** Nonlinearity function (LOGCOSH or EXP). */
    private int function = FastIca.LOGCOSH;

    /** Alpha in [1,2] for logcosh. */
    private double alpha = 1.1;

    /** Whether to row-normalize X prior to whitening. */
    private boolean rowNorm;

    /** Max iterations. */
    private int maxIterations = 200;

    /** Convergence tolerance. */
    private double tolerance = 1e-04;

    /** Verbose logging. */
    private boolean verbose;

    /** Initial unmixing (n.comp x n.comp). If null, random and orthonormalized. */
    private Matrix wInit;

    /**
     * Construct with data and number of components.
     */
    public FastIca(Matrix X, int numComponents) {
        this.X = X;
        this.numComponents = numComponents;
        this.algorithmType = FastIca.PARALLEL;
    }

    // ---------- small helpers ----------

    private static double mean(Vector v) {
        double sum = 0.0;
        for (int i = 0; i < v.size(); i++) sum += v.get(i);
        return sum / v.size();
    }

    /** Center each row (component-wise). */
    public static void center(Matrix x) {
        for (int i = 0; i < x.getNumRows(); i++) {
            Vector u = x.row(i);
            double m = mean(u);
            for (int j = 0; j < x.getNumColumns(); j++) {
                x.set(i, j, x.get(i, j) - m);
            }
        }
    }

    public void setAlgorithmType(int algorithmType) {
        if (!(algorithmType == FastIca.DEFLATION || algorithmType == FastIca.PARALLEL)) {
            throw new IllegalArgumentException("Value should be DEFLATION or PARALLEL.");
        }
        this.algorithmType = algorithmType;
    }

    public void setFunction(int function) {
        if (!(function == FastIca.LOGCOSH || function == FastIca.EXP)) {
            throw new IllegalArgumentException("Value should be LOGCOSH or EXP.");
        }
        this.function = function;
    }

    public void setAlpha(double alpha) {
        if (!(alpha >= 1 && alpha <= 2)) {
            throw new IllegalArgumentException("Alpha should be in range [1, 2].");
        }
        this.alpha = alpha;
    }

    public void setRowNorm(boolean rowNorm) {
        this.rowNorm = rowNorm;
    }

    public void setMaxIterations(int maxIterations) {
        if (maxIterations < 1) {
            TetradLogger.getInstance().log("maxIterations should be positive.");
        }
        this.maxIterations = maxIterations;
    }

    public void setTolerance(double tolerance) {
        if (!(tolerance > 0)) {
            TetradLogger.getInstance().log("Tolerance should be positive.");
        }
        this.tolerance = tolerance;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setWInit(Matrix wInit) {
        this.wInit = wInit;
    }

    // ---------- main entry ----------

    /**
     * Run FastICA and return preprocessed X, whitening K, unmixing W, and sources S.
     */
    public IcaResult findComponents() {
        int n = this.X.getNumColumns(); // cases
        int p = this.X.getNumRows();    // variables/components pre-whiten

        if (this.numComponents > min(n, p)) {
            TetradLogger.getInstance().log("Requested number of components is too large.");
            TetradLogger.getInstance().log("Reset to " + min(n, p));
            this.numComponents = min(n, p);
        }

        // Initialize W; if random, orthonormalize (SVD U).
        if (this.wInit == null) {
            this.wInit = new Matrix(this.numComponents, this.numComponents);
            for (int i = 0; i < this.wInit.getNumRows(); i++) {
                for (int j = 0; j < this.wInit.getNumColumns(); j++) {
                    this.wInit.set(i, j, RandomUtil.getInstance().nextGaussian(0, 1));
                }
            }
            SimpleSVD<SimpleMatrix> sWi = this.wInit.getSimpleMatrix().svd();
            this.wInit = new Matrix(sWi.getU()); // orthonormal
        } else if (this.wInit.getNumRows() != this.wInit.getNumColumns()) {
            throw new IllegalArgumentException("wInit is the wrong size.");
        }

        if (this.verbose) TetradLogger.getInstance().log("Centering");
        center(this.X);
        if (this.rowNorm) scale(this.X);

        if (this.verbose) TetradLogger.getInstance().log("Whitening");
        // Whitening with a small ridge to stabilize near-singular covariance.
        double eps = 1e-6;
        Matrix cov = this.X.times(this.X.transpose()).scalarMult(1.0 / n);
        for (int i = 0; i < cov.getNumRows(); i++) {
            cov.set(i, i, cov.get(i, i) + eps);
        }
        SimpleSVD<SimpleMatrix> s = cov.getSimpleMatrix().svd();
        Matrix D = new Matrix(s.getW());
        Matrix U = new Matrix(s.getU());
        for (int i = 0; i < D.getNumRows(); i++) {
            double lambda = D.get(i, i);
            D.set(i, i, 1.0 / FastMath.sqrt(Math.max(lambda, eps)));
        }
        Matrix K = D.times(U.transpose());
        K = K.getPart(0, this.numComponents, 0, p); // keep first components
        Matrix X1 = K.times(this.X);

        Matrix b;
        if (this.algorithmType == FastIca.DEFLATION) {
            b = icaDeflation(X1, this.tolerance, this.function, this.alpha,
                    this.maxIterations, this.verbose, this.wInit);
        } else if (this.algorithmType == FastIca.PARALLEL) {
            b = icaParallel(X1, this.numComponents, this.tolerance, this.alpha,
                    this.maxIterations, this.verbose, this.wInit);
        } else {
            throw new IllegalStateException();
        }

        Matrix w = b.times(K);
        Matrix S = w.times(this.X);
        return new IcaResult(this.X, K, w, S);
    }

    // ---------- deflationary FastICA ----------

    private Matrix icaDeflation(Matrix X,
                                double tolerance, int function, double alpha,
                                int maxIterations, boolean verbose, Matrix wInit) {
        if (verbose && function == FastIca.LOGCOSH) {
            TetradLogger.getInstance().log("Deflation FastICA using logcosh");
        }
        if (verbose && function == FastIca.EXP) {
            TetradLogger.getInstance().log("Deflation FastICA using exp");
        }

        Matrix W = new Matrix(X.getNumRows(), X.getNumRows());

        for (int i = 0; i < X.getNumRows(); i++) {
            if (verbose) TetradLogger.getInstance().log("Component " + (i + 1));

            Vector w = wInit.row(i);

            if (i > 0) {
                for (int u = 0; u < i; u++) {
                    double k = w.dotProduct(W.row(u));
                    w = w.minus(W.row(u).scalarMult(k));
                }
            }

            w = w.scalarMult(1.0 / rms(w));

            int it = 0;
            double _tolerance = Double.POSITIVE_INFINITY;

            while (_tolerance > tolerance && ++it <= maxIterations) {
                Vector wx = X.transpose().times(w);

                Vector gwx0 = new Vector(X.getNumColumns());
                for (int j = 0; j < X.getNumColumns(); j++) gwx0.set(j, g(alpha, wx.get(j)));

                Matrix gwx = new Matrix(X.getNumRows(), X.getNumColumns());
                for (int r = 0; r < X.getNumRows(); r++) {
                    gwx.assignRow(r, gwx0); // FIX: row index should be r, not i
                }

                // X weighted by gwx0
                Matrix xgwx = new Matrix(X.getNumRows(), X.getNumColumns());
                for (int r = 0; r < X.getNumRows(); r++) {
                    for (int j = 0; j < X.getNumColumns(); j++) {
                        xgwx.set(r, j, X.get(r, j) * gwx0.get(j));
                    }
                }

                Vector v1 = new Vector(X.getNumRows());
                for (int k = 0; k < X.getNumRows(); k++) v1.set(k, mean(xgwx.row(k)));

                Vector g_wx = new Vector(X.getNumColumns());
                for (int k = 0; k < X.getNumColumns(); k++) {
                    double t = g(alpha, wx.get(k));
                    g_wx.set(k, (1.0 - t * t));
                }

                Vector v2 = w.copy().scalarMult(mean(g_wx));

                Vector w1 = v1.minus(v2);

                if (i > 0) {
                    Vector t = w1.like();
                    for (int u = 0; u < i; u++) {
                        double k = 0.0;
                        for (int j = 0; j < X.getNumRows(); j++) k += w1.get(j) * W.get(u, j);
                        for (int j = 0; j < X.getNumRows(); j++) t.set(j, t.get(j) + k * W.get(u, j));
                    }
                    for (int j = 0; j < X.getNumRows(); j++) w1.set(j, w1.get(j) - t.get(j));
                }

                w1 = w1.scalarMult(1.0 / rms(w1));
                _tolerance = 0.0;
                for (int k = 0; k < X.getNumRows(); k++) _tolerance += w1.get(k) * w.get(k);
                _tolerance = abs(abs(_tolerance) - 1.0);

                if (verbose) TetradLogger.getInstance().log("Iteration " + it + " tol = " + _tolerance);
                w = w1;
            }

            W.assignRow(i, w);
        }

        return W;
    }

    // ---------- symmetric (parallel) FastICA ----------

    private Matrix icaParallel(Matrix X, int numComponents,
                               double tolerance, double alpha,
                               int maxIterations, boolean verbose, Matrix wInit) {
        int p = X.getNumColumns();
        Matrix W = wInit;

        // Symmetric decorrelation of initial W via SVD
        SimpleSVD<SimpleMatrix> sW = W.getSimpleMatrix().svd();
        Matrix D = new Matrix(sW.getW());
        for (int i = 0; i < D.getNumRows(); i++) D.set(i, i, 1.0 / D.get(i, i));
        Matrix WTemp = new Matrix(sW.getU()).times(D);
        WTemp = WTemp.times(new Matrix(sW.getU()).transpose());
        WTemp = WTemp.times(W);
        W = WTemp;

        Matrix W1;
        double _tolerance = Double.POSITIVE_INFINITY;
        int it = 0;

        if (verbose) TetradLogger.getInstance().log("Symmetric FastICA (logcosh)");

        while (_tolerance > tolerance && it < maxIterations) {
            Matrix wx = W.times(X); // (nComp x p)
            Matrix gwx = new Matrix(numComponents, p);
            for (int i = 0; i < numComponents; i++) {
                for (int j = 0; j < p; j++) {
                    gwx.set(i, j, g(alpha, wx.get(i, j)));
                }
            }

            // E[ g(wx) x^T ] = gwx * X^T / p
            Matrix v1 = gwx.times(X.transpose().scalarMult(1.0 / p));

            // g'(u) = alpha * (1 - g(u)^2) for logcosh with tanh(alpha u)
            Matrix g_wx = gwx.like();
            for (int i = 0; i < g_wx.getNumRows(); i++) {
                for (int j = 0; j < g_wx.getNumColumns(); j++) {
                    double v = gwx.get(i, j);                // FIX: read from gwx, not g_wx
                    g_wx.set(i, j, alpha * (1.0 - v * v));   // derivative
                }
            }

            Vector V20 = new Vector(numComponents);
            for (int k = 0; k < numComponents; k++) V20.set(k, mean(g_wx.row(k)));
            Matrix v2 = V20.diag().times(W);

            W1 = v1.minus(v2);

            // Symmetric decorrelation of W1
            SimpleSVD<SimpleMatrix> sW1 = (W1.getSimpleMatrix()).svd();
            Matrix U = new Matrix(sW1.getU());
            Matrix sD = new Matrix(sW1.getW());
            for (int i = 0; i < sD.getNumRows(); i++) sD.set(i, i, 1.0 / sD.get(i, i));
            Matrix W1Temp = U.times(sD).times(U.transpose()).times(W1);
            W1 = W1Temp;

            // Convergence via absolute cosines of principal angles between rows of W and W1
            Matrix d1 = W1.times(W.transpose());
            Vector d = d1.diag();
            _tolerance = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < d.size(); i++) {
                double m = abs(abs(d.get(i)) - 1);
                if (m > _tolerance) _tolerance = m;
            }

            W = W1;
            if (verbose) TetradLogger.getInstance().log("Iteration " + (it + 1) + " tol = " + _tolerance);
            it++;
        }

        return W;
    }

    // ---------- nonlinearity ----------

    private double g(double alpha, double y) {
        if (this.function == FastIca.LOGCOSH) {
            return tanh(alpha * y);
        } else if (this.function == FastIca.EXP) {
            return y * exp(-(y * y) / 2.);
        } else {
            throw new IllegalArgumentException("That function is not configured.");
        }
    }

    private double sumOfSquares(Vector v) {
        double sum = 0.0;
        for (int i = 0; i < v.size(); i++) sum += v.get(i) * v.get(i);
        return sum;
    }

    private double rms(Vector w) {
        double ssq = sumOfSquares(w);
        return FastMath.sqrt(ssq);
    }

    private void scale(Matrix x) {
        for (int i = 0; i < x.getNumRows(); i++) {
            Vector u = x.row(i).scalarMult(1.0 / rms(x.row(i)));
            x.assignRow(i, u);
        }
    }

    // ---------- result container ----------

    public static class IcaResult {
        private final Matrix X;
        private final Matrix K;
        private final Matrix W;
        private final Matrix S;

        public IcaResult(Matrix X, Matrix K, Matrix W, Matrix S) {
            this.X = X; this.K = K; this.W = W; this.S = S;
        }

        public Matrix getX() { return this.X; }
        public Matrix getK() { return this.K; }
        public Matrix getW() { return this.W; }
        public Matrix getS() { return this.S; }

        public String toString() {
            return "\n\nX:\n" + this.X + "\n\nK:\n" + this.K + "\n\nW:\n" + this.W + "\n\nS:\n" + this.S;
        }
    }
}
///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.apache.commons.math3.util.FastMath;

import static org.apache.commons.math3.util.FastMath.*;

/**
 * Translates a version of the FastICA algorithm used in R from Fortran into Java for use in Tetrad. This can be used in
 * various algorithms that assume linearity and non-gaussianity, as for example LiNGAM and LiNG-D. There is one
 * difference from the R, in that in R FastICA can operate over complex numbers, whereas here it is restricted to real
 * numbers. A useful reference is this:
 * <p>
 * Oja, E., &amp; Hyvarinen, A. (2000). Independent component analysis: algorithms and applications. Neural networks,
 * 13(4-5), 411-430.
 * <p>
 * The documentation of the R version is as follows, all of which is true of this translation (so far as I know) except
 * for its being in R and its allowing complex values.
 * <p>
 * Description:
 * <p>
 * This is an R and C code implementation of the FastICA algorithm of Aapo Hyvarinen et al. (URL:
 * <a href="http://www.cis.hut.fi/aapo/">http://www.cis.hut.fi/aapo/</a>) to perform Independent Component Analysis
 * (ICA) and Projection Pursuit.
 * <p>
 * Usage:
 * <p>
 * fastICA(X, n.comp, alg.typ = c("parallel","deflation"), fun = c("logcosh","exp"), alpha = 1.0, method = c("R","C"),
 * row.norm = FALSE, maxit = 200, tol = 1e-04, verbose = FALSE, w.init = NULL)
 * <p>
 * Arguments:
 * <p>
 * X: a data matrix with n rows representing observations and p columns representing variables.
 * <p>
 * n.comp: number of components to be extracted
 * <p>
 * alg.typ: if 'alg.typ == "parallel"' the components are extracted simultaneously (the default). if 'alg.typ ==
 * "deflation"' the components are extracted one at a time.
 * <p>
 * fun: the functional form of the G function used in the approximation to neg-entropy (see details)
 * <p>
 * alpha: constant in range [1, 2] used in approximation to neg-entropy when 'fun == "logcosh"'
 * <p>
 * method: if 'method == "R"' then computations are done exclusively in R (default). The code allows the interested R
 * user to see exactly what the algorithm does. if 'method == "C"' then C code is used to perform most of the
 * computations, which makes the algorithm run faster. During compilation the C code is linked to an optimized BLAS
 * library if present, otherwise stand-alone BLAS routines are compiled.
 * <p>
 * row.norm: a logical value indicating whether rows of the data matrix 'X' should be standardized beforehand.
 * <p>
 * maxit: maximum number of iterations to perform
 * <p>
 * tol: a positive scalar giving the tolerance at which the un-mixing The data matrix X is considered to be a linear
 * combination of non-Gaussian (independent) components i.e. X = SA where columns of S contain the independent
 * components and A is a linear mixing matrix. In short ICA attempts to `un-mix' the data by estimating an un-mixing
 * matrix W where XW = S.
 * <p>
 * Under this generative model the measured `signals' in X will tend to be `more Gaussian' than the source components
 * (in S) due to the Central Limit Theorem. Thus, in order to extract the independent components/sources we search for
 * an un-mixing matrix W that maximizes the non-gaussianity of the sources.
 * <p>
 * In FastICA, non-gaussianity is measured using approximations to neg-entropy (J) which are more robust than kurtosis
 * based measures and fast to compute.
 * <p>
 * The approximation takes the form
 * <p>
 * J(y)=[E{G(y)}-E{G(v)}]^2 where v is a N(0,1) r.v.
 * <p>
 * The following choices of G are included as options G(u)=frac{1}{alpha} log cosh (alpha u) and
 * G(u)=-exp(frac{-u^2}{2})
 * <p>
 * Algorithm*
 * <p>
 * First, the data is centered by subtracting the mean of each column of the data matrix X.
 * <p>
 * The data matrix is then `whitened' by projecting the data onto it's principle component directions i.e. X -&gt; XK
 * where K is a pre-whitening matrix. The user can specify the number of components.
 * <p>
 * The ICA algorithm then estimates a matrix W s.t XKW = S . W is chosen to maximize the neg-entropy approximation under
 * the constraints that W is an orthonormal matrix. This constraint ensures that the estimated components are
 * uncorrelated. The algorithm is based on a fixed-point iteration scheme for maximizing the neg-entropy.
 * <p>
 * Projection Pursuit*
 * <p>
 * In the absence of a generative model for the data the algorithm can be used to find the projection pursuit
 * directions. Projection pursuit is a technique for finding `interesting' directions in multi-dimensional datasets.
 * These projections and are useful for visualizing the dataset and in density estimation and regression. Interesting
 * directions are those which show the least Gaussian distribution, which is what the FastICA algorithm does.
 * <p>
 * Author(s):
 * <p>
 * J L Marchini and C Heaton
 * <p>
 * References:
 * <p>
 * A. Hyvarinen and E. Oja (2000) Independent Component Analysis: Algorithms and Applications, _Neural Networks_,
 * *13(4-5)*:411-430
 *
 * @author josephramsey
 */
public class FastIca {

    // The algorithm type where all components are extracted simultaneously.
    public static int PARALLEL;

    // The algorithm type where the components are extracted one at a time.
    public static int DEFLATION = 1;

    // One of the function types that can be used to approximate negative entropy.
    public static int LOGCOSH = 2;

    // The other function type that can be used to approximate negative entropy.
    public static int EXP = 3;

    // A data matrix with n rows representing observations and p columns representing variables.
    private final Matrix X;

    // The number of independent components to be extracted.
    private int numComponents;

    // If algorithmType == PARALLEL, the components are extracted simultaneously (the default). if algorithmType ==
    // DEFLATION, the components are extracted one at a time.
    private int algorithmType = FastIca.PARALLEL;

    // The function type to be used, either LOGCOSH or EXP.
    private int function = FastIca.LOGCOSH;

    // Constant in range [1, 2] used in approximation to neg-entropy when 'fun == "logcosh". Default = 1.0.
    private double alpha = 1.1;

    // A logical value indicating whether rows of the data matrix 'X' should be standardized beforehand. Default =
    // false.
    private boolean rowNorm;

    // Maximum number of iterations to perform. Default = 200.
    private int maxIterations = 200;

    // A positive scalar giving the tolerance at which the un-mixing matrix is considered to have converged. Default =
    // 1e-04.
    private double tolerance = 1e-04;

    // A logical value indicating the level of output as the algorithm runs. Default = false.
    private boolean verbose;

    // Initial un-mixing matrix of dimension (n.comp,n.comp). If null (default), then a matrix of normal r.v.'s is
    // used.
    private Matrix wInit;

    /**
     * Constructs an instance of the Fast ICA algorithm, taking as arguments the two arguments that cannot be defaulted:
     * the data matrix itself and the number of components to be extracted.
     *
     * @param X A 2D matrix, rows being cases, columns being variables. It is assumed that there are no missing values.
     */
    public FastIca(Matrix X, int numComponents) {
        this.X = X;
        this.numComponents = numComponents;
    }

    /**
     * If algorithmType == PARALLEL, the components are extracted simultaneously (the default). if algorithmType ==
     * DEFLATION, the components are extracted one at a time.
     *
     * @param algorithmType This type.
     */
    public void setAlgorithmType(int algorithmType) {
        if (!(algorithmType == FastIca.DEFLATION || algorithmType == FastIca.PARALLEL)) {
            throw new IllegalArgumentException("Value should be DEFLATION or PARALLEL.");
        }

        this.algorithmType = algorithmType;
    }

    /**
     * Sets the function type to be used, either LOGCOSH or EXP.
     *
     * @param function This function, LOGCOSH or EXP.
     */
    public void setFunction(int function) {
        if (!(function == FastIca.LOGCOSH || function == FastIca.EXP)) {
            throw new IllegalArgumentException("Value should be LOGCOSH or EXP.");
        }

        this.function = function;
    }

    /**
     * Sets the FastICA alpha constant in range [1, 2] used in approximation to neg-entropy when 'fun == "logcosh"'
     *
     * @param alpha this constant.
     */
    public void setAlpha(double alpha) {
        if (!(alpha >= 1 && alpha <= 2)) {
            throw new IllegalArgumentException("Alpha should be in range [1, 2].");
        }

        this.alpha = alpha;
    }

    /**
     * A logical value indicating whether rows of the data matrix 'X' should be standardized beforehand.
     *
     * @param rowNorm True, if so.
     */
    public void setRowNorm(boolean rowNorm) {
        this.rowNorm = rowNorm;
    }

    /**
     * Sets the maximum number of iterations to allow.
     *
     * @param maxIterations This maximum.
     */
    public void setMaxIterations(int maxIterations) {
        if (maxIterations < 1) {
            TetradLogger.getInstance().log("info", "maxIterations should be positive.");
        }

        this.maxIterations = maxIterations;
    }

    /**
     * Sets a positive scalar giving the tolerance at which the un-mixing matrix is considered to have converged.
     *
     * @param tolerance This value.
     */
    public void setTolerance(double tolerance) {
        if (!(tolerance > 0)) {
            TetradLogger.getInstance().log("info", "Tolerance should be positive.");
        }

        this.tolerance = tolerance;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the initial un-mixing matrix of dimension (n.comp,n.comp). If NULL (default), then a random matrix of normal
     * r.v.'s is used.
     *
     * @param wInit This matrix.
     */
    public void setWInit(Matrix wInit) {
        this.wInit = wInit;
    }

    /**
     * Runs the Fast ICA algorithm (following the R version) and returns the list of result items that the R version
     * returns.
     *
     * @return this list, as a FastIca.IcaResult object.
     */
    public IcaResult findComponents() {
        int n = this.X.getNumColumns();
        int p = this.X.getNumRows();

        if (this.numComponents > min(n, p)) {
            TetradLogger.getInstance().log("info", "Requested number of components is too large.");
            TetradLogger.getInstance().log("info", "Reset to " + min(n, p));
            this.numComponents = min(n, p);
        }

        if (this.wInit == null) {
            this.wInit = new Matrix(this.numComponents, this.numComponents);
            for (int i = 0; i < this.wInit.getNumRows(); i++) {
                for (int j = 0; j < this.wInit.getNumColumns(); j++) {
                    this.wInit.set(i, j, RandomUtil.getInstance().nextNormal(0, 1));
                }
            }
        } else if (this.wInit.getNumRows() != this.wInit.getNumColumns()) {
            throw new IllegalArgumentException("wInit is the wrong size.");
        }

        if (this.verbose) {
            TetradLogger.getInstance().log("info", "Centering");
        }

        center(this.X);

        if (this.rowNorm) {
            scale(this.X);
        }

        if (this.verbose) {
            TetradLogger.getInstance().log("info", "Whitening");
        }

        // Whiten.
        Matrix cov = this.X.times(this.X.transpose()).scalarMult(1.0 / n);

        SingularValueDecomposition s = new SingularValueDecomposition(cov.getApacheData());
        Matrix D = new Matrix(s.getS().getData());
        Matrix U = new Matrix(s.getU().getData());

        for (int i = 0; i < D.getNumRows(); i++) {
            D.set(i, i, 1.0 / FastMath.sqrt(D.get(i, i)));
        }

        Matrix K = D.times(U.transpose());
//        K = K.scalarMult(-1); // This SVD gives -U from R's SVD.
        K = K.getPart(0, this.numComponents - 1, 0, p - 1);

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


    private Matrix icaDeflation(Matrix X,
                                double tolerance, int function, double alpha,
                                int maxIterations, boolean verbose, Matrix wInit) {
        if (verbose && function == FastIca.LOGCOSH) {
            TetradLogger.getInstance().log("info", "Deflation FastIca using lgcosh approx. to neg-entropy function");
        }

        if (verbose && function == FastIca.EXP) {
            TetradLogger.getInstance().log("info", "Deflation FastIca using exponential approx. to neg-entropy function");
        }

        Matrix W = new Matrix(X.getNumRows(), X.getNumRows());

        for (int i = 0; i < X.getNumRows(); i++) {
            if (verbose) {
                TetradLogger.getInstance().log("fastIcaDetails", "Component " + (i + 1));
            }

            Vector w = wInit.getRow(i);

            if (i > 0) {
                for (int u = 0; u < i; u++) {
                    double k = w.dotProduct(W.getRow(u));
                    w = w.minus(W.getRow(u).scalarMult(k));
                }
            }

            w = w.scalarMult(1.0 / rms(w));

            int it = 0;
            double _tolerance = Double.POSITIVE_INFINITY;

            while (_tolerance > tolerance && ++it <= maxIterations) {
                Vector wx = X.transpose().times(w);

                Vector gwx0 = new Vector(X.getNumColumns());

                for (int j = 0; j < X.getNumColumns(); j++) {
                    gwx0.set(j, g(alpha, wx.get(j)));
                }

                Matrix gwx = new Matrix(X.getNumRows(), X.getNumColumns());

                for (int _i = 0; _i < X.getNumRows(); _i++) {
                    gwx.assignRow(i, gwx0);
                }

                // A weighting of X by gwx0.
                Matrix xgwx = new Matrix(X.getNumRows(), X.getNumColumns());

                for (int _i = 0; _i < X.getNumRows(); _i++) {
                    for (int j = 0; j < X.getNumColumns(); j++) {
                        xgwx.set(_i, j, X.get(_i, j) * gwx0.get(j));
                    }
                }

                Vector v1 = new Vector(X.getNumRows());

                for (int k = 0; k < X.getNumRows(); k++) {
                    v1.set(k, mean(xgwx.getRow(k)));
                }

                Vector g_wx = new Vector(X.getNumColumns());

                for (int k = 0; k < X.getNumColumns(); k++) {
                    double t = g(alpha, wx.get(k));
                    g_wx.set(k, (1.0 - t * t));
                }

                Vector v2 = w.copy();
                double meanGwx = mean(g_wx);
                v2 = v2.scalarMult(meanGwx);

                Vector w1 = v1.minus(v2);

                if (i > 0) {
                    Vector t = w1.like();

                    for (int u = 0; u < i; u++) {
                        double k = 0.0;

                        for (int j = 0; j < X.getNumRows(); j++) {
                            k += w1.get(j) * W.get(u, j);
                        }

                        for (int j = 0; j < X.getNumRows(); j++) {
                            t.set(j, t.get(j) + k * W.get(u, j));
                        }
                    }

                    for (int j = 0; j < X.getNumRows(); j++) {
                        w1.set(j, w1.get(j) - t.get(j));
                    }
                }

                w1 = w1.scalarMult(1.0 / rms(w1));

                _tolerance = 0.0;

                for (int k = 0; k < X.getNumRows(); k++) {
                    _tolerance += w1.get(k) * w.get(k);
                }

                _tolerance = abs(abs(_tolerance) - 1.0);

                if (verbose) {
                    TetradLogger.getInstance().log("fastIcaDetails", "Iteration " + it + " tol = " + _tolerance);
                }

                w = w1;
            }

            W.assignRow(i, w);
        }

        return W;
    }

    private double g(double alpha, double y) {
        if (this.function == FastIca.LOGCOSH) {
            return tanh(alpha * y);
        } else if (this.function == FastIca.EXP) {
            return y * exp(-(y * y) / 2.);
        } else {
            throw new IllegalArgumentException("That function is not configured.");
        }
    }

    private double mean(Vector v) {
        double sum = 0.0;

        for (int i = 0; i < v.size(); i++) {
            sum += v.get(i);
        }

        return sum / v.size();
    }

    private double sumOfSquares(Vector v) {
        double sum = 0.0;

        for (int i = 0; i < v.size(); i++) {
            sum += v.get(i) * v.get(i);
        }

        return sum;
    }

    private double rms(Vector w) {
        double ssq = sumOfSquares(w);
        return FastMath.sqrt(ssq);
    }

    private Matrix icaParallel(Matrix X, int numComponents,
                               double tolerance, double alpha,
                               int maxIterations, boolean verbose, Matrix wInit) {
        int p = X.getNumColumns();
        Matrix W = wInit;

        SingularValueDecomposition sW = new SingularValueDecomposition(W.getApacheData());
        Matrix D = new Matrix(sW.getS().getData());
        for (int i = 0; i < D.getNumRows(); i++) D.set(i, i, 1.0 / D.get(i, i));

        Matrix WTemp = new Matrix(sW.getU()).times(D);
        WTemp = WTemp.times(new Matrix(sW.getU()).transpose());
        WTemp = WTemp.times(W);
        W = WTemp;

        Matrix W1;
        double _tolerance = Double.POSITIVE_INFINITY;
        int it = 0;

        if (verbose) {
            TetradLogger.getInstance().log("info", "Symmetric FastICA using logcosh approx. to neg-entropy function");
        }

        while (_tolerance > tolerance && it < maxIterations) {
            Matrix wx = W.times(X);
            Matrix gwx = new Matrix(numComponents, p);

            for (int i = 0; i < numComponents; i++) {
                for (int j = 0; j < p; j++) {
                    gwx.set(i, j, g(alpha, wx.get(i, j)));
                }
            }

            Matrix v1 = gwx.times(X.transpose().scalarMult(1.0 / p));
            Matrix g_wx = gwx.like();

            for (int i = 0; i < g_wx.getNumRows(); i++) {
                for (int j = 0; j < g_wx.getNumColumns(); j++) {
                    double v = g_wx.get(i, j);
                    double w = alpha * (1.0 - v * v);
                    g_wx.set(i, j, w);
                }
            }

            Vector V20 = new Vector(numComponents);

            for (int k = 0; k < numComponents; k++) {
                V20.set(k, mean(g_wx.getRow(k)));
            }

            Matrix v2 = V20.diag();
            v2 = v2.times(W);
            W1 = v1.minus(v2);

            SingularValueDecomposition sW1 = new SingularValueDecomposition(W1.getApacheData());
            Matrix U = new Matrix(sW1.getU());
            Matrix sD = new Matrix(sW1.getS());
            for (int i = 0; i < sD.getNumRows(); i++)
                sD.set(i, i, 1.0 / sD.get(i, i));

            Matrix W1Temp = U.times(sD);
            W1Temp = W1Temp.times(U.transpose());
            W1Temp = W1Temp.times(W1);
            W1 = W1Temp;

            Matrix d1 = W1.times(W.transpose());
            Vector d = d1.diag();
            _tolerance = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < d.size(); i++) {
                double m = abs(abs(d.get(i)) - 1);
                if (m > _tolerance) _tolerance = m;
            }

            W = W1;

            if (verbose) {
                TetradLogger.getInstance().log("fastIcaDetails", "Iteration " + (it + 1) + " tol = " + _tolerance);
            }

            it++;
        }

        return W;
    }

    private void scale(Matrix x) {
        for (int i = 0; i < x.getNumRows(); i++) {
            Vector u = x.getRow(i).scalarMult(1.0 / rms(x.getRow(i)));
            x.assignRow(i, u);
        }
    }

    private void center(Matrix x) {
        for (int i = 0; i < x.getNumRows(); i++) {
            Vector u = x.getRow(i);
            double mean = mean(u);

            for (int j = 0; j < x.getNumColumns(); j++) {
                x.set(i, j, x.get(i, j) - mean);
            }
        }

    }


    /**
     * A list containing the following components
     * <p>
     * X: pre-processed data matrix
     * <p>
     * K: pre-whitening matrix that projects data onto the first n.comp principal components.
     * <p>
     * W: estimated un-mixing matrix (see definition in details)
     * <p>
     * A: estimated mixing matrix
     * <p>
     * S: estimated source matrix
     */
    public static class IcaResult {
        private final Matrix X;
        private final Matrix K;
        private final Matrix W;
        private final Matrix S;

        public IcaResult(Matrix X, Matrix K, Matrix W,
                         Matrix S) {
            this.X = X;
            this.K = K;
            this.W = W;
            this.S = S;
        }

        public Matrix getX() {
            return this.X;
        }

        public Matrix getK() {
            return this.K;
        }

        public Matrix getW() {
            return this.W;
        }

        public Matrix getS() {
            return this.S;
        }

        public String toString() {
            return "\n\nX:\n" +
                    this.X +
                    "\n\nK:\n" +
                    this.K +
                    "\n\nW:\n" +
                    this.W +
                    "\n\nS:\n" +
                    this.S;
        }
    }
}




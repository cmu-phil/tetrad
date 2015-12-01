///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import org.apache.commons.math3.linear.SingularValueDecomposition;

/**
 * A Java implementation of FastIca following the R package fastICA. The only
 * difference (I believe) is that the R package can handle complex numbers,
 * whereas this implementation cannot.
 * <p>
 * Performance. The R version scales up much better than this one does, the main
 * reason for which is that the calculation of the initial covariance matrix
 * (1/n) X'X is so much faster.
 * <p>
 * The documention of the R version is as follows, all of which is true of this
 * translation (so far as I know) except for its being in R and its allowing
 * complex values:
 * <p>
 * Description:
 * <p>
 * This is an R and C code implementation of the FastICA algorithm of Aapo
 * Hyvarinen et al. (<URL: http://www.cis.hut.fi/aapo/>) to perform Independent
 * Component Analysis (ICA) and Projection Pursuit.
 * <p>
 * Usage:
 * <p>
 * fastICA(X, n.comp, alg.typ = c("parallel","deflation"), fun =
 * c("logcosh","exp"), alpha = 1.0, method = c("R","C"), row.norm = FALSE, maxit
 * = 200, tol = 1e-04, verbose = FALSE, w.init = NULL)
 * <p>
 * Arguments:
 * <p>
 * X: a data matrix with n rows representing observations and p columns
 * representing variables.
 * <p>
 * n.comp: number of components to be extracted
 * <p>
 * alg.typ: if 'alg.typ == "parallel"' the components are extracted
 * simultaneously (the default). if 'alg.typ == "deflation"' the components are
 * extracted one at a time.
 * <p>
 * fun: the functional form of the G function used in the approximation to
 * neg-entropy (see details)
 * <p>
 * alpha: constant in range [1, 2] used in approximation to neg-entropy when
 * 'fun == "logcosh"'
 * <p>
 * method: if 'method == "R"' then computations are done exclusively in R
 * (default). The code allows the interested R user to see exactly what the
 * algorithm does. if 'method == "C"' then C code is used to perform most of the
 * computations, which makes the algorithm run faster. During compilation the C
 * code is linked to an optimized BLAS library if present, otherwise stand-alone
 * BLAS routines are compiled.
 * <p>
 * row.norm: a logical value indicating whether rows of the data matrix 'X'
 * should be standardized beforehand.
 * <p>
 * maxit: maximum number of iterations to perform
 * <p>
 * tol: a positive scalar giving the tolerance at which the un-mixing The data
 * matrix X is considered to be a linear combination of non-Gaussian
 * (independent) components i.e. X = SA where columns of S contain the
 * independent components and A is a linear mixing matrix. In short ICA attempts
 * to `un-mix' the data by estimating an un-mixing matrix W where XW = S.
 * <p>
 * Under this generative model the measured `signals' in X will tend to be `more
 * Gaussian' than the source components (in S) due to the Central Limit Theorem.
 * Thus, in order to extract the independent components/sources we search for an
 * un-mixing matrix W that maximizes the non-gaussianity of the sources.
 * <p>
 * In FastICA, non-gaussianity is measured using approximations to neg-entropy
 * (J) which are more robust than kurtosis based measures and fast to compute.
 * <p>
 * The approximation takes the form
 * <p>
 * J(y)=[E{G(y)}-E{G(v)}]^2 where v is a N(0,1) r.v.
 * <p>
 * The following choices of G are included as options G(u)=frac{1}{alpha} log
 * cosh (alpha u) and G(u)=-exp(frac{-u^2}{2})
 * <p>
 * Algorithm*
 * <p>
 * First, the data is centered by subtracting the mean of each column of the
 * data matrix X.
 * <p>
 * The data matrix is then `whitened' by projecting the data onto it's principle
 * component directions i.e. X -> XK where K is a pre-whitening matrix. The
 * number of components can be specified by the user.
 * <p>
 * The ICA algorithm then estimates a matrix W s.t XKW = S . W is chosen to
 * maximize the neg-entropy approximation under the constraints that W is an
 * orthonormal matrix. This constraint ensures that the estimated components are
 * uncorrelated. The algorithm is based on a fixed-point iteration scheme for
 * maximizing the neg-entropy.
 * <p>
 * Projection Pursuit*
 * <p>
 * In the absence of a generative model for the data the algorithm can be used
 * to find the projection pursuit directions. Projection pursuit is a technique
 * for finding `interesting' directions in multi-dimensional datasets. These
 * projections and are useful for visualizing the dataset and in density
 * estimation and regression. Interesting directions are those which show the
 * least Gaussian distribution, which is what the FastICA algorithm does.
 * <p>
 * Author(s):
 * <p>
 * J L Marchini and C Heaton
 * <p>
 * References:
 * <p>
 * A. Hyvarinen and E. Oja (2000) Independent Component Analysis: Algorithms and
 * Applications, _Neural Networks_, *13(4-5)*:411-430
 * <p>
 * <p>Note: This code is currently broken; please do not use it until it's fixed. 11/24/2015</p>
 *
 * @author Joseph Ramsey (of the translation, that is)
 */
public class FastIca {

    /**
     * The algorithm type where all components are extracted simultaneously.
     */
    public static int PARALLEL = 0;

    /**
     * The algorithm type where the components are extracted one at a time.
     */
    public static int DEFLATION = 1;

    /**
     * One of the function types that can be used to approximate negative
     * entropy.
     */
    public static int LOGCOSH = 2;

    /**
     * The other function type that can be used to approximate negative
     * entropy.
     */
    public static int EXP = 3;

    /**
     * A data matrix with n rows representing observations and p columns
     * representing variables.
     */
    private TetradMatrix X;

    /**
     * The number of independent components to be extracted.
     */
    private int numComponents;

    /**
     * If algorithmType == PARALLEL the components are extracted simultaneously
     * (the default). if algorithmType == DEFLATION the components are extracted
     * one at a time.
     */
    private int algorithmType = PARALLEL;

    /**
     * The function type to be used, either LOGCOSH or EXP.
     */
    private int function = LOGCOSH;

    /**
     * Constant in range [1, 2] used in approximation to neg-entropy when 'fun
     * == "logcosh". Default = 1.0.
     */
    private double alpha = 1.0;

    /**
     * A logical value indicating whether rows of the data matrix 'X' should be
     * standardized beforehand. Default = false.
     */
    private boolean colNorm = false;

    /**
     * Maximum number of iterations to perform. Default = 200.
     */
    private int maxIterations = 200;

    /**
     * A positive scalar giving the tolerance at which the un-mixing matrix is
     * considered to have converged. Default = 1e-04.
     */
    private double tolerance = 1e-04;

    /**
     * A logical value indicating the level of output as the algorithm runs.
     * Default = false.
     */
    private boolean verbose = false;

    /**
     * Initial un-mixing matrix of dimension (n.comp,n.comp). If null (default)
     * then a matrix of normal r.v.'s is used.
     */
    private TetradMatrix wInit = null;

    //============================CONSTRUCTOR===========================//

    /**
     * Constructs an instance of the Fast ICA algorithm, taking as arguments the
     * two arguments that cannot be defaulted: the data matrix itself and the
     * number of components to be extracted.
     *
     * @param X             A 2D matrix, rows being cases, columns being
     *                      variables. It is assumed that there are no missing
     *                      values.
     * @param numComponents
     */
    public FastIca(TetradMatrix X, int numComponents) {
        this.X = X;
        this.numComponents = numComponents;
    }

    //=============================PUBLIC METHODS=======================//

    /**
     * If algorithmType == PARALLEL the components are extracted simultaneously
     * (the default). if algorithmType == DEFLATION the components are extracted
     * one at a time.
     */
    public int getAlgorithmType() {
        return algorithmType;
    }

    /**
     * If algorithmType == PARALLEL the components are extracted simultaneously
     * (the default). if algorithmType == DEFLATION the components are extracted
     * one at a time.
     */
    public void setAlgorithmType(int algorithmType) {
        if (!(algorithmType == DEFLATION || algorithmType == PARALLEL)) {
            throw new IllegalArgumentException("Value should be DEFLATION or PARALLEL.");
        }

        this.algorithmType = algorithmType;
    }

    /**
     * The function type to be used, either LOGCOSH or EXP.
     */
    public int getFunction() {
        return function;
    }

    /**
     * The function type to be used, either LOGCOSH or EXP.
     */
    public void setFunction(int function) {
        if (!(function == LOGCOSH || function == EXP)) {
            throw new IllegalArgumentException("Value should be LOGCOSH or EXP.");
        }

        this.function = function;
    }

    /**
     * Constant in range [1, 2] used in approximation to neg-entropy when 'fun
     * == "logcosh"'
     */
    public double getAlpha() {
        return alpha;
    }

    /**
     * Constant in range [1, 2] used in approximation to neg-entropy when 'fun
     * == "logcosh"'
     */
    public void setAlpha(double alpha) {
        if (!(alpha >= 1 && alpha <= 2)) {
            throw new IllegalArgumentException("Alpha should be in range [1, 2].");
        }

        this.alpha = alpha;
    }

    /**
     * A logical value indicating whether rows of the data matrix 'X' should be
     * standardized beforehand.
     */
    public boolean isColNorm() {
        return colNorm;
    }

    /**
     * A logical value indicating whether rows of the data matrix 'X' should be
     * standardized beforehand.
     */
    public void setColNorm(boolean colNorm) {
        this.colNorm = colNorm;
    }

    /**
     * Maximum number of iterations to perform.
     */
    public int getMaxIterations() {
        return maxIterations;
    }

    /**
     * Maximum number of iterations to perform.
     */
    public void setMaxIterations(int maxIterations) {
        if (maxIterations < 1) {
            TetradLogger.getInstance().log("info", "maxIterations should be positive.");
        }

        this.maxIterations = maxIterations;
    }

    /**
     * A positive scalar giving the tolerance at which the un-mixing matrix is
     * considered to have converged.
     */
    public double getTolerance() {
        return tolerance;
    }

    /**
     * A positive scalar giving the tolerance at which the un-mixing matrix is
     * considered to have converged.
     */
    public void setTolerance(double tolerance) {
        if (!(tolerance > 0)) {
            TetradLogger.getInstance().log("info", "Tolerance should be positive.");
        }

        this.tolerance = tolerance;
    }

    /**
     * A logical value indicating the level of output as the algorithm runs.
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * A logical value indicating the level of output as the algorithm runs.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Initial un-mixing matrix of dimension (n.comp,n.comp). If NULL (default)
     * then a matrix of normal r.v.'s is used.
     */
    public TetradMatrix getWInit() {
        return wInit;
    }

    /**
     * Initial un-mixing matrix of dimension (n.comp,n.comp). If NULL (default)
     * then a matrix of normal r.v.'s is used.
     */
    public void setWInit(TetradMatrix wInit) {
        this.wInit = wInit;
    }

    /**
     * Runs the Fast ICA algorithm (following the R version) and returns the
     * list of result items that the R version returns.
     *
     * @return this list, as an FastIca.IcaResult object.
     */
    public IcaResult findComponents() {
        int n = X.rows();
        int p = X.columns();

        if (numComponents > Math.min(n, p)) {
            TetradLogger.getInstance().log("info", "Requested number of components is too large.");
            TetradLogger.getInstance().log("info", "Reset to " + Math.min(n, p));
            numComponents = Math.min(n, p);
        }

        if (wInit == null) {
            wInit = new TetradMatrix(numComponents, numComponents);
            for (int i = 0; i < wInit.rows(); i++) {
                for (int j = 0; j < wInit.columns(); j++) {
                    wInit.set(i, j, RandomUtil.getInstance().nextNormal(0, 1));
                }
            }
        } else if (wInit.rows() != wInit.columns()) {
            throw new IllegalArgumentException("wInit is the wrong size.");
        }

        if (verbose) {
            TetradLogger.getInstance().log("info", "Centering");
        }

        X = center(X);

        if (colNorm) {
            X = scale(X);
        }

        X = X.transpose();

        if (verbose) {
            TetradLogger.getInstance().log("info", "Whitening");
        }

        TetradMatrix V = X.times(X.transpose()).scalarMult(1.0 / n);
//        V.scalarMult(1.0 / n);

        SingularValueDecomposition s = new SingularValueDecomposition(V.getRealMatrix());
        TetradMatrix D = new TetradMatrix(s.getS());
        TetradMatrix U = new TetradMatrix(s.getU());

        for (int i = 0; i < D.rows(); i++) {
            D.set(i, i, 1.0 / Math.sqrt(D.get(i, i)));
        }

        TetradMatrix K = D.times(U.transpose());
        K = K.scalarMult(-1); // This SVD gives -U from R's SVD.
        K = K.getPart(0, numComponents - 1, 0, p - 1);

        TetradMatrix X1 = K.times(X);
        TetradMatrix b;

        if (algorithmType == DEFLATION) {
            b = icaDeflation(X1, numComponents, tolerance, function, alpha,
                    maxIterations, verbose, wInit);
        } else if (algorithmType == PARALLEL) {
            b = icaParallel(X1, numComponents, tolerance, function, alpha,
                    maxIterations, verbose, wInit);
        } else {
            throw new IllegalStateException();
        }

        TetradMatrix w = b.times(K);
        TetradMatrix S = w.times(X);
        TetradMatrix A = w.transpose().times(w.times(w.transpose()).inverse());
        return new IcaResult(X.transpose(), K.transpose(), b.transpose(), A.transpose(), S.transpose());

    }

    //==============================PRIVATE METHODS==========================//

    private TetradMatrix icaDeflation(TetradMatrix X, int numComponents,
                                      double tolerance, int function, double alpha,
                                      int maxIterations, boolean verbose, TetradMatrix wInit) {
        if (verbose && function == LOGCOSH) {
            TetradLogger.getInstance().log("info", "Deflation FastIca using lgcosh approx. to neg-entropy function");
        }

        if (verbose && function == EXP) {
            TetradLogger.getInstance().log("info", "Deflation FastIca using exponential approx. to neg-entropy function");
        }

        int p = X.columns();
        TetradMatrix W = new TetradMatrix(numComponents, numComponents);

        for (int i = 0; i < numComponents; i++) {
            if (verbose) {
                TetradLogger.getInstance().log("fastIcaDetails", "Component " + (i + 1));
            }

            TetradVector w = wInit.getRow(i);

            if (i > 0) {
                TetradVector t = w.like();

                for (int u = 0; u < i; u++) {
                    double k = 0.0;

                    for (int j = 0; j < numComponents; j++) {
                        k += w.get(j) * W.get(u, j);
                    }

                    for (int j = 0; j < numComponents; j++) {
                        t.set(j, t.get(j) + k * W.get(u, j));
                    }
                }

                for (int j = 0; j < numComponents; j++) {
                    w.set(j, w.get(j) - t.get(j));
                }
            }

            double rms = rms(w);

            for (int j = 0; j < numComponents; j++) {
                w.set(j, w.get(j) / rms);
            }

            int it = 0;
            double _tolerance = Double.POSITIVE_INFINITY;

            if (function == LOGCOSH) {
                while (_tolerance > tolerance && ++it <= maxIterations) {
                    TetradVector wx = X.transpose().times(w);

                    TetradVector gwx0 = new TetradVector(p);

                    for (int j = 0; j < p; j++) {
                        gwx0.set(j, Math.tanh(alpha * wx.get(j)));
                    }

                    TetradMatrix gwx = new TetradMatrix(numComponents, p);

                    for (int _i = 0; _i < numComponents; _i++) {
                        for (int j = 0; j < p; j++) {
                            gwx.set(_i, j, gwx0.get(j));
                        }
                    }

                    TetradMatrix xgwx = new TetradMatrix(numComponents, p);

                    for (int _i = 0; _i < numComponents; _i++) {
                        for (int j = 0; j < p; j++) {
                            xgwx.set(_i, j, X.get(_i, j) * gwx0.get(j));
                        }
                    }

                    TetradVector v1 = new TetradVector(numComponents);

                    for (int k = 0; k < numComponents; k++) {
                        v1.set(k, mean(xgwx.getRow(k)));
                    }

                    TetradVector g_wx = new TetradVector(p);

                    for (int k = 0; k < p; k++) {
                        double tmp1 = Math.tanh(alpha * wx.get(k));
                        g_wx.set(k, alpha * (1.0 - tmp1 * tmp1));
                    }

                    TetradVector v2 = w.copy();
                    double meanGwx = mean(g_wx);
                    v2 = v2.scalarMult(meanGwx);

                    TetradVector w1 = v1.copy();
//                    w1.assign(v2, PlusMult.plusMult(-1));
                    w1 = w1.minus(v2);

                    if (i > 0) {
                        TetradVector t = w1.like();

                        for (int u = 0; u < i; u++) {
                            double k = 0.0;

                            for (int j = 0; j < numComponents; j++) {
                                k += w1.get(j) * W.get(u, j);
                            }

                            for (int j = 0; j < numComponents; j++) {
                                t.set(j, t.get(j) + k * W.get(u, j));
                            }
                        }

                        for (int j = 0; j < numComponents; j++) {
                            w1.set(j, w1.get(j) - t.get(j));
                        }
                    }

                    double _rms = rms(w1);

                    for (int k = 0; k < numComponents; k++) {
                        w1.set(k, w1.get(k) / _rms);
                    }

                    _tolerance = 0.0;

                    for (int k = 0; k < numComponents; k++) {
                        _tolerance += w1.get(k) * w.get(k);
                    }

                    _tolerance = Math.abs(Math.abs(_tolerance) - 1.0);

                    if (verbose) {
                        TetradLogger.getInstance().log("fastIcaDetails", "Iteration " + it + " tol = " + _tolerance);
                    }

                    w = w1;
                }
            } else if (function == EXP) {
                while (_tolerance > tolerance && ++it <= maxIterations) {
                    TetradVector wx = X.transpose().times(w);

                    TetradVector gwx0 = new TetradVector(p);

                    for (int j = 0; j < p; j++) {
                        gwx0.set(j, wx.get(j) * Math.exp(-(wx.get(j) * wx.get(j)) / 2));
                    }

                    TetradMatrix gwx = new TetradMatrix(numComponents, p);

                    for (int _i = 0; _i < numComponents; _i++) {
                        for (int j = 0; j < p; j++) {
                            gwx.set(_i, j, gwx0.get(j));
                        }
                    }

                    TetradMatrix xgwx = new TetradMatrix(numComponents, p);

                    for (int _i = 0; _i < numComponents; _i++) {
                        for (int j = 0; j < p; j++) {
                            xgwx.set(_i, j, X.get(_i, j) * gwx0.get(j));
                        }
                    }

                    TetradVector v1 = new TetradVector(numComponents);

                    for (int k = 0; k < numComponents; k++) {
                        v1.set(k, mean(xgwx.getRow(k)));
                    }

                    TetradVector g_wx = new TetradVector(p);

                    for (int j = 0; j < p; j++) {
                        g_wx.set(j, (1.0 - wx.get(j) * wx.get(j)) * Math.exp(-(wx.get(j) * wx.get(j)) / 2));
                    }

                    TetradVector v2 = w.copy();
                    double meanGwx = mean(g_wx);
                    TetradVector w1 = v2.scalarMult(meanGwx).minus(v2);

//                    TetradVector w1 = v1.copy();
//                    w1.assign(v2, PlusMult.plusMult(-1));

                    if (i > 0) {
                        TetradVector t = w1.like();

                        for (int u = 0; u < i; u++) {
                            double k = 0.0;

                            for (int j = 0; j < numComponents; j++) {
                                k += w1.get(j) * W.get(u, j);
                            }

                            for (int j = 0; j < numComponents; j++) {
                                t.set(j, t.get(j) + k * W.get(u, j));
                            }
                        }

                        for (int j = 0; j < numComponents; j++) {
                            w1.set(j, w1.get(j) - t.get(j));
                        }
                    }

                    double _rms = rms(w1);

                    for (int k = 0; k < numComponents; k++) {
                        w1.set(k, w1.get(k) / _rms);
                    }

                    _tolerance = 0.0;

                    for (int k = 0; k < numComponents; k++) {
                        _tolerance += w1.get(k) * w.get(k);
                    }

                    _tolerance = Math.abs(Math.abs(_tolerance) - 1.0);

                    if (verbose) {
                        TetradLogger.getInstance().log("fastIcaDetails", "Iteration " + it + " tol = " + _tolerance);
                    }

                    w = w1;
                }
            }

            W.assignRow(i, w);
        }

        return W;
    }

    private double mean(TetradVector v) {
        double sum = 0.0;

        for (int i = 0; i < v.size(); i++) {
            sum += v.get(i);
        }

        return sum / v.size();
    }

    private double sumOfSquares(TetradVector v) {
        double sum = 0.0;

        for (int i = 0; i < v.size(); i++) {
            sum += v.get(i) * v.get(i);
        }

        return sum;
    }

    private double rms(TetradVector w) {
        double ssq = sumOfSquares(w);
        return Math.sqrt(ssq);
    }

    private TetradMatrix icaParallel(TetradMatrix X, int numComponents,
                                     double tolerance, int function, final double alpha,
                                     int maxIterations, boolean verbose, TetradMatrix wInit) {
        int p = X.columns();
        TetradMatrix W = wInit;

        SingularValueDecomposition sW = new SingularValueDecomposition(W.getRealMatrix());
        TetradMatrix D = new TetradMatrix(sW.getS());
        for (int i = 0; i < D.rows(); i++) D.set(i, i, 1.0 / D.get(i, i));

        TetradMatrix WTemp = new TetradMatrix(sW.getU()).times(D);
        WTemp = WTemp.times(new TetradMatrix(sW.getU()).transpose());
        WTemp = WTemp.times(W);
        W = WTemp;

        TetradMatrix W1;
        double _tolerance = Double.POSITIVE_INFINITY;
        int it = 0;

        if (function == LOGCOSH) {
            if (verbose) {
                TetradLogger.getInstance().log("info", "Symmetric FastICA using logcosh approx. to neg-entropy function");
            }

            while (_tolerance > tolerance && it < maxIterations) {
                TetradMatrix wx = W.times(X);
                TetradMatrix gwx = new TetradMatrix(numComponents, p);

                for (int i = 0; i < numComponents; i++) {
                    for (int j = 0; j < p; j++) {
                        gwx.set(i, j, Math.tanh(alpha * wx.get(i, j)));
                    }
                }

                TetradMatrix v1 = gwx.times(X.transpose().copy().scalarMult(1.0 / p));
                TetradMatrix g_wx = gwx.copy();

                for (int i = 0; i < g_wx.rows(); i++) {
                    for (int j = 0; j < g_wx.columns(); j++) {
                        double v = g_wx.get(i, j);
                        double w = alpha * (1.0 - v * v);
                        g_wx.set(i, j, w);
                    }
                }

                TetradVector V20 = new TetradVector(numComponents);

                for (int k = 0; k < numComponents; k++) {
                    V20.set(k, mean(g_wx.getRow(k)));
                }

                TetradMatrix v2 = V20.diag();
                v2 = v2.times(W);
                W1 = v1.minus(v2);

                SingularValueDecomposition sW1 = new SingularValueDecomposition(W1.getRealMatrix());
                TetradMatrix U = new TetradMatrix(sW1.getU());
                TetradMatrix sD = new TetradMatrix(sW1.getS());
                for (int i = 0; i < sD.rows(); i++)
                    sD.set(i, i, 1.0 / sD.get(i, i));

                TetradMatrix W1Temp = U.times(sD);
                W1Temp = W1Temp.times(U.transpose());
                W1Temp = W1Temp.times(W1);
                W1 = W1Temp;

                TetradMatrix d1 = W1.times(W.transpose());
                TetradVector d = d1.diag();
                _tolerance = Double.NEGATIVE_INFINITY;

                for (int i = 0; i < d.size(); i++) {
                    double m = Math.abs(Math.abs(d.get(i)) - 1);
                    if (m > _tolerance) _tolerance = m;
                }

                W = W1;

                if (verbose) {
                    TetradLogger.getInstance().log("fastIcaDetails", "Iteration " + (it + 1) + " tol = " + _tolerance);
                }

                it++;
            }
        } else if (function == EXP) {
            if (verbose) {
                TetradLogger.getInstance().log("info", "Symmetric FastICA using exponential approx. to neg-entropy function");
            }

            while (_tolerance > tolerance && it < maxIterations) {
                TetradMatrix wx = W.times(X);
                TetradMatrix gwx = new TetradMatrix(numComponents, p);


                for (int i = 0; i < numComponents; i++) {
                    for (int j = 0; j < p; j++) {
                        double v = wx.get(i, j);
                        gwx.set(i, j, v * Math.exp(-(v * v) / 2.0));
                    }
                }

                TetradMatrix v1 = gwx.times(X.transpose().scalarMult(p));
                TetradMatrix g_wx = wx.copy();

                for (int i = 0; i < g_wx.rows(); i++) {
                    for (int j = 0; j < g_wx.columns(); j++) {
                        double v = g_wx.get(i, j);
                        double w = (1.0 - v * v) * Math.exp(-(v * v) / 2.0);
                        g_wx.set(i, j, w);
                    }
                }

                TetradVector V20 = new TetradVector(numComponents);

                for (int k = 0; k < numComponents; k++) {
                    V20.set(k, mean(g_wx.getRow(k)));
                }

                TetradMatrix v2 = V20.diag();
                v2 = v2.times(W);
                W1 = v1.minus(v2);

                SingularValueDecomposition sW1 = new SingularValueDecomposition(W1.getRealMatrix());
                TetradMatrix U = new TetradMatrix(sW1.getU());
                TetradMatrix sD = new TetradMatrix(sW1.getS());
                for (int i = 0; i < sD.rows(); i++)
                    sD.set(i, i, 1.0 / sD.get(i, i));

                TetradMatrix W1Temp = U.times(sD);
                W1Temp = W1Temp.times(U.transpose());
                W1Temp = W1Temp.times(W1);
                W1 = W1Temp;

                TetradMatrix d1 = W1.times(W.transpose());
                TetradVector d = d1.diag();
                _tolerance = Double.NEGATIVE_INFINITY;

                for (int i = 0; i < d.size(); i++) {
                    double m = Math.abs(Math.abs(d.get(i)) - 1);
                    if (m > _tolerance) _tolerance = m;
                }

                W.assign(W1);

                if (verbose) {
                    TetradLogger.getInstance().log("fastIcaDetails", "Iteration " + (it + 1) + " tol = " + _tolerance);
                }

                it++;
            }
        }

        return W;
    }

    private TetradMatrix scale(TetradMatrix x) {
        for (int j = 0; j < x.columns(); j++) {
            TetradVector u = x.getColumn(j);
            double rms = rms(u);

            for (int i = 0; i < x.rows(); i++) {
                x.set(i, j, x.get(i, j) / rms);
            }
        }

        return x;
    }

    private TetradMatrix center(TetradMatrix x) {
        for (int j = 0; j < x.columns(); j++) {
            TetradVector u = x.getColumn(j);
            double mean = mean(u);

            for (int i = 0; i < x.rows(); i++) {
                x.set(i, j, x.get(i, j) - mean);
            }
        }

        return x;
    }


    //===============================CLASSES============================//

    /**
     * A list containing the following components
     * <p>
     * X: pre-processed data matrix
     * <p>
     * K: pre-whitening matrix that projects data onto th first n.comp principal
     * components.
     * <p>
     * W: estimated un-mixing matrix (see definition in details)
     * <p>
     * A: estimated mixing matrix
     * <p>
     * S: estimated source matrix
     */
    public static class IcaResult {
        private final TetradMatrix X;
        private final TetradMatrix K;
        private final TetradMatrix W;
        private final TetradMatrix S;
        private final TetradMatrix A;

        public IcaResult(TetradMatrix X, TetradMatrix K, TetradMatrix W,
                         TetradMatrix A, TetradMatrix S) {
            this.X = X;
            this.K = K;
            this.W = W;
            this.A = A;
            this.S = S;
        }

        public TetradMatrix getX() {
            return X;
        }

        public TetradMatrix getK() {
            return K;
        }

        public TetradMatrix getW() {
            return W;
        }

        public TetradMatrix getS() {
            return S;
        }

        public TetradMatrix getA() {
            return A;
        }

        public String toString() {
            StringBuilder buf = new StringBuilder();

            buf.append("\n\nX:\n");
            buf.append(X);

            buf.append("\n\nK:\n");
            buf.append(K);

            buf.append("\n\nW:\n");
            buf.append(W);

            buf.append("\n\nA:\n");
            buf.append(A);

            buf.append("\n\nS:\n");
            buf.append(S);

            return buf.toString();
        }
    }
}




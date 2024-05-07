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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.util.FastMath;

/**
 * A translation from Tibshirani's 2008 Fortran implementation of glasso.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Glasso {

    /**
     * Data covariance matrix.
     */
    private final Matrix ss;
    /**
     * Dimension of matrix.
     */
    private int n;
    /**
     * Regularization strength parameters for each element (must be symmetric, rho(i, j) = rho(j, i). False by default.
     */
    private Rho rho = (i, j) -> 0;


    /**
     * Maximum number of iterations (no effect if ia = true).
     */
    private int maxit = 10000;


    /**
     * Approximation flag. False if exact solution, true if Meinhausen-Buhlman approximation. False by default.
     */
    private boolean ia;

    /**
     * Initialization flag. false if cold start, initialize using ss. True if warm start, initialize with previous
     * solution stored in ww and wwi. False by default.
     */
    private boolean is;

    /**
     * Trace flag. True if trace information printed. False if trace information not printed. False by default.
     */
    private boolean itr;

    /**
     * Diagonal penalty flag. True if diagonal is penalized. False if diagonal is not penalized. False by default.
     */
    private boolean ipen;

    /**
     * Convergence threshold: Iterations stop when absolute average parameter change is less than thr *
     * avg(abs(offdiagonal(ss)). (Suggested default 1.0e-4.)
     */
    private double thr = 1.0e-4;

    /**
     * <p>Constructor for Glasso.</p>
     *
     * @param cov a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Glasso(Matrix cov) {
        this.n = cov.getNumRows();
        this.ss = cov;
    }

    /**
     * <p>search.</p>
     *
     * @return a {@link edu.cmu.tetrad.search.work_in_progress.Glasso.Result} object
     */
    public Result search() {
        int niter = 0;
        final double eps = 1.0e-7;
        int n = getN();
        Matrix ss = getSs();

        boolean approximateAlgorithm = isIa();
        boolean warmStart = isIs();
        boolean itr = isItr();
        boolean pen = isIpen();

        Rho rho = getRho();
        Matrix ww = new Matrix(n, n);
        Matrix wwi = new Matrix(n, n);

        double dlx;

        int nm1 = n - 1;

        Matrix vv = new Matrix(nm1, nm1);
        Matrix xs = null;
        if (!approximateAlgorithm) {
            xs = new Matrix(nm1, n);
        }
        Vector s = new Vector(nm1);
        Vector x = new Vector(n - 1);
        Vector ws;
        int[] mm = new int[nm1];
        Vector ro = new Vector(nm1);

        // shr warmStart sum(abs(offdiagonal(ss))).
        double shr = 0.0;

        for (int j = 0; j < n; j++) {
            for (int k = 0; k < n; k++) {
                if (j == k) continue;
                shr += FastMath.abs(ss.get(j, k));
            }
        }

        // TO TEST THE FOLLOWING CODE I NEED A DIAGONAL COVARIANCE MATRIX.

        // If ss is diagonal, just return the inverse of the covariance matrix diagonal
        // (penalized if necessary).
        if (shr == 0.0) {
            for (int j = 0; j < n; j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (!pen) {
                    ww.set(j, j, ss.get(j, j));
                } else {
                    ww.set(j, j, ss.get(j, j) + rho.get(j, j));
                }
                wwi.set(j, j, 1.0 / FastMath.max(ww.get(j, j), eps));
            }
            return new Result(wwi);
        }


        shr = getThr() * shr / nm1;

        if (approximateAlgorithm) {
            if (!warmStart) {
                zero(wwi);
            }

            for (int m = 0; m < n; m++) {
                System.out.println("m = " + m);

                // This sets up vv, s, and r--i.e., W.11, s.12, and r.12.
                setup(m, n, ss, rho, ss, vv, s, ro);

                // This sets up x.12--i.e. theta.12.
                int l = -1;

                for (int j = 0; j < n; j++) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    if (j == m) continue;
                    l = l + 1;
                    x.set(l, wwi.get(j, m));
                }

                lasso(ro, nm1, vv, s, shr / n, x, mm);

                l = -1;
                for (int j = 0; j < n; j++) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    if (j == m) continue;
                    l = l + 1;
                    wwi.set(j, m, x.get(l));
                }
            }

            return new Result(wwi);
        }

        if (!warmStart) {
            ww.assign(ss);

            zero(xs);
        } else {
            for (int j = 0; j < n; j++) {
                double xjj = -wwi.get(j, j);
                int l = -1;

                for (int k = 0; k < n; k++) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    if (k == j) continue;
                    l = l + 1;
                    xs.set(l, j, wwi.get(k, j) / xjj);
                }
            }
        }

        for (int j = 0; j < n; j++) {
            if (pen) {
                ww.set(j, j, ss.get(j, j) + rho.get(j, j));
            } else {
                ww.set(j, j, ss.get(j, j));
            }
        }


        while (true) {
            dlx = 0.0;

            for (int m = 0; m < n; m++) {
                if (itr) {
                    System.out.println("Outer loop = " + m);
                }

                x = xs.getColumn(m);

                ws = ww.getColumn(m);

                // This sets up vv, s, and ro--i.e., W.11, s.12, and r.12.
                setup(m, n, ss, rho, ww, vv, s, ro);

                Vector so = s.copy();

                // This updates s and x--the estimated correlation matrix and the reduced form of the
                // estimated inverse covariance.
                lasso(ro, nm1, vv, s, shr / sum_abs(vv), x, mm);
                int l = -1;

                for (int j = 0; j < n; j++) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    if (j == m) continue;
                    l = l + 1;
                    ww.set(j, m, so.get(l) - s.get(l));
                    ww.set(m, j, ww.get(j, m));
                }

                dlx = FastMath.max(dlx, sum_abs_diff(ww.getColumn(m), ws));
                xs.assignColumn(m, x);
            }

            niter = niter + 1;
            if (niter < getMaxit()) break;
            if (dlx < shr) break;
        }

        inv(n, ww, xs, wwi);

        return new Result(wwi);
    }

    private double sum_abs(Matrix m) {
        double sum = 0.0;

        for (int i = 0; i < m.getNumRows(); i++) {
            for (int j = 0; j < m.getNumColumns(); j++) {
                sum += FastMath.abs(m.get(i, j));
            }
        }

        return sum;
    }

    private double sum_abs_diff(Vector x, Vector y) {
        double sum = 0.0;

        for (int i = 0; i < x.size(); i++) {
            sum += FastMath.abs(x.get(i) - y.get(i));
        }

        return sum;
    }

    private void setup(int m, int n, Matrix ss, Rho rho, Matrix ww, Matrix vv,
                       Vector s, Vector r) {
        int l = -1;

        for (int j = 0; j < n; j++) {
            if (j == m) continue;
            l = l + 1;
            r.set(l, rho.get(j, m));                // r is r12
            s.set(l, ss.get(j, m));                 // s is s12
            int i = -1;
            for (int k = 0; k < n; k++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                if (k == m) continue;
                i++;
                vv.set(i, l, ww.get(k, j));         // vv is ww.11
            }
        }
    }

    private void lasso(Vector ro, int n, Matrix vv, Vector s, double thr,
                       Vector x, int[] mm) {
        // vv = W.11
        // s = s.12
        // ro = r.12
        // x = x.12 = theta.12

        // z is just passed in to avoid reallocating an array. It's only used in fatmul. Could be global.

        // s = vv * x, or s12 = Theta.1 * Theta.12
        fatmul(n, vv, x, s, mm);

        while (true) {

            // The maximal difference of the beta update of v(j) and v(j).
            double dlx = 0.0;

            for (int j = 0; j < n; j++) {
                double xj = x.get(j);
                x.set(j, 0.0);

                // There is no sum. In the paper there is a sum. Also, there is a minus instead of
                // a plus in the paper.
                double t = s.get(j) + vv.get(j, j) * xj;

                if (FastMath.abs(t) - ro.get(j) > 0.0) {
                    x.set(j, FastMath.signum(t) * (FastMath.abs(t) - ro.get(j)) / vv.get(j, j));
                }

                if (x.get(j) == xj) continue;
                double del = x.get(j) - xj;
                dlx = FastMath.max(dlx, FastMath.abs(del));

                for (int i = 0; i < s.size(); i++) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    s.set(i, s.get(i) - del * vv.get(i, j));
                }
            }

//            System.out.println("dlx = " + dlx + " thr = " + thr);

            if (dlx < thr) break;
        }
    }

    // s = vv * x, or s12 = Theta.1 * Theta.12
    private void fatmul(int n, Matrix vv, Vector x, Vector s, int[] m) {
        final double fac = 0.2;

        // z consists of the nonzero entries of x. m indexes these. If there are enough zeroes in x,
        // use the simpler multiplication method.
        int l = 0;

        for (int j = 0; j < n; j++) {
            if (x.get(j) == 0.0) continue;
            l = l + 1;
            m[l] = j;
        }

        if (l < (int) (fac * n)) {
            for (int j = 0; j < n; j++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                double dotProduct = 0.0;

                for (int i = 0; i < l; i++) {
                    dotProduct += vv.get(m[i], j) * x.get(m[j]);
                }

                s.set(j, s.get(j) - dotProduct);
            }
        } else {
            s.assign(vv.times(x).minus(x));
        }
    }

    private void inv(int n, Matrix ww, Matrix xs, Matrix wwi) {
        xs = xs.scalarMult(-1);
        int nm1 = n - 1;

        double dp3 = 0.0;

        for (int k = 0; k < n - 1; k++) {
            dp3 += xs.get(k, 0) * ww.get(k + 1, 0);
        }

        wwi.set(0, 0, 1.0 / (ww.get(0, 0) + dp3));

        for (int i = 1; i < n; i++) wwi.set(i, 0, wwi.get(0, 0) * xs.get(i - 1, 0));

        double dp4 = 0.0;

        for (int k = 0; k < n - 1; k++) {
            dp4 += xs.get(k, n - 1) * ww.get(k, n - 1);
        }

        wwi.set(n - 1, n - 1, 1.0 / (ww.get(n - 1, n - 1) + dp4));

        for (int i = 0; i < nm1; i++) {
            wwi.set(i, n - 1, wwi.get(n - 1, n - 1) * xs.get(i, n - 1));
        }

        for (int j = 1; j < n - 1; j++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            int jm1 = j - 1;
            int jp1 = j + 1;

            double dp1 = 0.0;

            for (int k = 0; k <= jm1; k++) {
                dp1 += xs.get(k, j) * ww.get(k, j);
            }

            double dp2 = 0.0;

            for (int k = j; k <= n - 2; k++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                dp2 += xs.get(k, j) * ww.get(k + 1, j);
            }

            wwi.set(j, j, 1.0 / (ww.get(j, j) + dp1 + dp2));
            for (int p = 0; p <= jm1; p++) wwi.set(p, j, wwi.get(j, j) * xs.get(p, j));
            for (int p = jp1; p < n; p++) wwi.set(p, j, wwi.get(j, j) * xs.get(p - 1, j));
        }
    }

    private void zero(Matrix wwi) {
        for (int i = 0; i < wwi.getNumRows(); i++) {
            for (int j = 0; j < wwi.getNumColumns(); j++) {
                wwi.set(i, j, 0.0);
            }
        }
    }

    /**
     * <p>isIa.</p>
     *
     * @return a boolean
     */
    public boolean isIa() {
        return this.ia;
    }

    /**
     * <p>Setter for the field <code>ia</code>.</p>
     *
     * @param ia a boolean
     */
    public void setIa(boolean ia) {
        this.ia = ia;
    }

    /**
     * <p>isIs.</p>
     *
     * @return a boolean
     */
    public boolean isIs() {
        return this.is;
    }

    /**
     * <p>Setter for the field <code>is</code>.</p>
     *
     * @param is a boolean
     */
    public void setIs(boolean is) {
        this.is = is;
    }

    /**
     * <p>isItr.</p>
     *
     * @return a boolean
     */
    public boolean isItr() {
        return this.itr;
    }

    /**
     * <p>Setter for the field <code>itr</code>.</p>
     *
     * @param itr a boolean
     */
    public void setItr(boolean itr) {
        this.itr = itr;
    }

    /**
     * <p>isIpen.</p>
     *
     * @return a boolean
     */
    public boolean isIpen() {
        return this.ipen;
    }

    /**
     * <p>Setter for the field <code>ipen</code>.</p>
     *
     * @param ipen a boolean
     */
    public void setIpen(boolean ipen) {
        this.ipen = ipen;
    }

    /**
     * <p>Getter for the field <code>thr</code>.</p>
     *
     * @return a double
     */
    public double getThr() {
        return this.thr;
    }

    /**
     * <p>Setter for the field <code>thr</code>.</p>
     *
     * @param thr a double
     */
    public void setThr(double thr) {
        if (thr < 0) throw new IllegalArgumentException("Threshold must be >= 0: " + thr);

        this.thr = thr;
    }

    /**
     * <p>Getter for the field <code>n</code>.</p>
     *
     * @return a int
     */
    public int getN() {
        return this.n;
    }

    /**
     * <p>Setter for the field <code>n</code>.</p>
     *
     * @param n a int
     */
    public void setN(int n) {
        if (n < 0) throw new IllegalArgumentException("Dimension >= 0: " + n);

        this.n = n;
    }

    /**
     * <p>Getter for the field <code>ss</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    public Matrix getSs() {
        return this.ss;
    }

    /**
     * <p>Getter for the field <code>rho</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.search.work_in_progress.Glasso.Rho} object
     */
    public Rho getRho() {
        return this.rho;
    }

    /**
     * <p>setRhoAllEqual.</p>
     *
     * @param rho a double
     */
    public void setRhoAllEqual(double rho) {
        this.rho = (i, j) -> rho;
    }

    /**
     * <p>Getter for the field <code>maxit</code>.</p>
     *
     * @return a int
     */
    public int getMaxit() {
        return this.maxit;
    }

    /**
     * <p>Setter for the field <code>maxit</code>.</p>
     *
     * @param maxit a int
     */
    public void setMaxit(int maxit) {
        if (maxit <= 0) throw new IllegalArgumentException("Max iterations must be > 0: " + maxit);

        this.maxit = maxit;
    }

    private interface Rho {
        double get(int i, int j);
    }

    /**
     * Return value of the algorithm.
     */
    public static class Result {

        /**
         * solution inverse covariance matrix estimate (ia = 0) = off-diagonal lasso coefficients (ia != 0)
         */
        private final Matrix wwi;

        /**
         * <p>Constructor for Result.</p>
         *
         * @param wwi a {@link edu.cmu.tetrad.util.Matrix} object
         */
        public Result(Matrix wwi) {
            this.wwi = wwi;
        }

        /**
         * <p>Getter for the field <code>wwi</code>.</p>
         *
         * @return a {@link edu.cmu.tetrad.util.Matrix} object
         */
        public Matrix getWwi() {
            return this.wwi;
        }
    }

}




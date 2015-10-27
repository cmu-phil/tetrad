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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.TetradLogger;

/**
 * <p>The Sem class mplements the methods needed to compute the maximum
 * likelihood estimates of the freeParameters of a Sem for a given sample; it
 * contains member variables for storing the results of such a computation. The
 * values of the freeParameters are a search procedure based on Powell's method for
 * multidimensional minimization.  In fact the Java code represents a conversion
 * of the C program for Powell's method which is found in <i>Numerical Recipes
 * in C</i> (Section 10.5).  This class uses the DoubleMatrix class of the <a
 * href="http://www.vni.com/products/wpd/jnl/"> Java Numerical Library</a>
 * Library, which is imported.</p> </p> <p>Copyright 1998 by Frank Wimberly. All
 * rights reserved.</p>
 *
 * @author Frank Wimberly
 * @author Joseph Ramsey
 */
public class SemOptimizerNrPowell implements SemOptimizer {
    static final long serialVersionUID = 23L;
    private int numRestarts;

    //=============================CONSTRUCTORS=========================//

    /**
     * Blank constructor.
     */
    public SemOptimizerNrPowell() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see edu.cmu.tetradapp.util.TetradSerializableUtils
     */
    public static SemOptimizerNrPowell serializableInstance() {
        return new SemOptimizerNrPowell();
    }

    //==============================PUBLIC METHODS========================//

    /**
     * Optimizes the fitting function of the given Sem using the Powell method
     * from Numerical Recipes by adjusting the freeParameters of the Sem.
     */
    public void optimize(SemIm semIm) {
        if (numRestarts != 1) throw new IllegalArgumentException("Number of restarts must be 1 for this method.");

        new SemOptimizerEm().optimize(semIm);
        SemOptimizerNrPowell.FittingFunction fcn = new SemOptimizerNrPowell.SemFittingFunction(semIm);
        int n = fcn.getNumParameters();
        double[][] xi =
                MatrixUtils.identity(n); // Columns are initial directions.
        double[] p = semIm.getFreeParamValues();
        double[] fret = {0.0};
        int[] iter = {0};
        double ftol = 0.001;
        powell(p, xi, n, ftol, iter, fret, fcn);
    }

    @Override
    public void setNumRestarts(int numRestarts) {
        this.numRestarts = numRestarts;
    }

    @Override
    public int getNumRestarts() {
        return numRestarts;
    }

    public String toString() {
        return "Sem Optimizer NR Powell";
    }

    /**
     * See <i>Numerical Recipes in C</i> for an explanation of this method.
     *
     * @param p    Initial values for each parameter. Double array length n.
     * @param xi   Initial set of directions, column-wise. Double array n x n.
     * @param n    Number of freeParameters.
     * @param ftol Fractional tolerance in the function value such that the
     *             failure to decrease by more than this amount on one iteration
     *             signals doneness.
     * @param iter Number of iterations taken. Array of length 1.
     * @param fret Returned function value at p. Array of length 1.
     * @param fcn  The fitting function to be minimized.
     */
    private void powell(double[] p, double[][] xi, int n, double ftol,
            int[] iter, double[] fret, SemOptimizerNrPowell.FittingFunction fcn) {

        double TINY = 1.0E-25;
        int ITMAX = 200;
        int i, ibig, j;
        double del, fp, fptt, t;

        double[] pt = new double[n];
        double[] ptt = new double[n];
        double[] xit = new double[n];

        fret[0] = fcn.evaluate(p);

        for (j = 0; j < n; j++) {
            pt[j] = p[j];
        }

        for (iter[0] = 0; ; iter[0]++) {
            fp = fret[0];
            ibig = 0;
            del = 0.0;

            for (i = 0; i < n; i++) {
                for (j = 0; j < n; j++) {
                    xit[j] = xi[j][i];
                }

                fptt = fret[0];

                linmin(p, xit, n, fret, fcn);

                if (fptt - fret[0] > del) {
                    del = fptt - fret[0];
                    ibig = i;
                }
            }

            if (2.0 * (fp - fret[0]) <=
                    ftol * (Math.abs(fp) + Math.abs(fret[0])) + TINY) {
                return;    // Terminate
            }

            if (iter[0] == ITMAX) {
                throw new IllegalStateException("Powell exceeding maximum " +
                        "number of iterations (" + ITMAX +
                        "); unable to find extremum.");
            }

            for (j = 0; j < n; j++) {
                ptt[j] = 2.0 * p[j] - pt[j];
                xit[j] = p[j] - pt[j];
                pt[j] = p[j];
            }

            fptt = fcn.evaluate(ptt);

            if (fptt < fp) {
                t = 2.0 * (fp - 2.0 * fret[0] + fptt) * (fp - fret[0] - del) *
                        (fp - fret[0] - del) - del * (fp - fptt) * (fp - fptt);

                if (t < 0.0) {
                    linmin(p, xit, n, fret, fcn);

                    // if(fret.getLabel( ) < -900.0) return;
                    for (j = 0; j < n; j++) {
                        xi[j][ibig] = xi[j][n - 1];
                        xi[j][n - 1] = xit[j];
                    }
                }
            }
        }
    }

    /**
     * See <i>Numerical Recipes in C</i> for an explanation of this method.
     */
    private void linmin(double[] p, double xi[], int n, double[] fret,
            SemOptimizerNrPowell.FittingFunction fcn) {

        int j;
        double xx, xmin, bx, ax;
        double TOL = 0.0000002;
        int ncom = n;
        double[] pcom = new double[n];
        double[] xicom = new double[n];

        for (j = 0; j < n; j++) {
            pcom[j] = p[j];
            xicom[j] = xi[j];
        }

        ax = 0.0;
        xx = 1.0;

        double[] brakarg = new double[6];

        brakarg[0] = ax;
        brakarg[1] = xx;

        mnbrak(brakarg, ncom, pcom, xicom, fcn);

        ax = brakarg[0];
        xx = brakarg[1];
        bx = brakarg[2];

        // xMin xmin = new xMin(0.0);
        double[] brentarg = new double[5];

        brentarg[0] = ax;
        brentarg[1] = xx;
        brentarg[2] = bx;
        brentarg[3] = TOL;

        fret[0] = brent(brentarg, ncom, pcom, xicom, fcn);

        if (fret[0] < -900.0) {
            return;
        }

        xmin = brentarg[4];

        for (j = 0; j < n; j++) {
            xi[j] *= xmin;
            p[j] = p[j] + xi[j];
        }
    }

    /**
     * See <i>Numerical Recipes in C</i> for an explanation of this method.
     */
    private double f1dim(double x, int ncom, double pcom[], double xicom[],
            SemOptimizerNrPowell.FittingFunction fcn) {
        double[] xt = new double[ncom];
        for (int j = 0; j < ncom; j++) {
            xt[j] = pcom[j] + x * xicom[j];
        }
        return fcn.evaluate(xt);
    }

    /**
     * See <i>Numerical Recipes in C</i> for an explanation of this method.
     */
    private void mnbrak(double args[], int ncom, double pcom[], double xicom[],
            SemOptimizerNrPowell.FittingFunction fcn) {

        double ulim, u, r, q, fu, dum;
        double GOLD = 1.618034;
        double GLIMIT = 100.0;
        double TINY = 1.0E-20;
        double ax = args[0];
        double bx = args[1];
        double cx = args[2];
        double fa = args[3];
        double fb = args[4];
        double fc = args[5];

        fa = f1dim(ax, ncom, pcom, xicom, fcn);
        fb = f1dim(bx, ncom, pcom, xicom, fcn);

        if (fb > fa) {
            dum = ax;
            ax = bx;
            bx = dum;
            dum = fb;
            fb = fa;
            fa = dum;
        }

        cx = bx + GOLD * (bx - ax);
        fc = f1dim(cx, ncom, pcom, xicom, fcn);

        while (fb > fc) {
            r = (bx - ax) * (fb - fc);
            q = (bx - cx) * (fb - fa);

            double fm = ((Math.abs(q - r) > TINY) ? Math.abs(q - r) : TINY);
            double s = ((q - r) >= 0.0 ? Math.abs(fm) : -Math.abs(fm));

            u = bx - ((bx - cx) * q - (bx - ax) * r) / (2.0 * s);
            ulim = bx + GLIMIT * (cx - bx);

            if ((bx - u) * (u - cx) > 0.0) {
                fu = f1dim(u, ncom, pcom, xicom, fcn);

                if (fu < fc) {
                    ax = bx;
                    bx = u;
                    fa = fb;
                    fb = fu;
                    args[0] = ax;
                    args[1] = bx;
                    args[2] = cx;
                    args[3] = fa;
                    args[4] = fb;
                    args[5] = fc;

                    return;
                }
                else if (fu > fb) {
                    cx = u;
                    fc = fu;
                    args[0] = ax;
                    args[1] = bx;
                    args[2] = cx;
                    args[3] = fa;
                    args[4] = fb;
                    args[5] = fc;

                    return;
                }

                u = cx + GOLD * (cx - bx);
                fu = f1dim(u, ncom, pcom, xicom, fcn);
            }
            else if ((cx - u) * (u - ulim) > 0.0) {
                fu = f1dim(u, ncom, pcom, xicom, fcn);

                if (fu < fc) {
                    bx = cx;
                    cx = u;
                    u = cx + GOLD * (cx - bx);
                    fb = fc;
                    fc = fu;
                    fu = f1dim(u, ncom, pcom, xicom, fcn);
                }
            }
            else if ((u - ulim) * (ulim - cx) >= 0.0) {
                u = ulim;
                fu = f1dim(u, ncom, pcom, xicom, fcn);
            }
            else {
                u = cx + GOLD * (cx - bx);
                fu = f1dim(u, ncom, pcom, xicom, fcn);
            }

            ax = bx;
            bx = cx;
            cx = u;
            fa = fb;
            fb = fc;
            fc = fu;
        }

        args[0] = ax;
        args[1] = bx;
        args[2] = cx;
        args[3] = fa;
        args[4] = fb;
        args[5] = fc;
    }

    /**
     * See <i>Numerical Recipes in C</i> for an explanation of this method.
     */
    private double brent(double args[], int ncom, double pcom[], double xicom[],
            SemOptimizerNrPowell. FittingFunction fcn) {

        int iter;
        double a, b, d, etemp, fu, fv, fw, fx, p, q, r, tol1, tol2, u, v, w, x, xm;
        double e = 0.0;

        // Not in Numerical Recipes but lack of initialization of d causes error.
        d = 0.0;

        int ITMAX = 100;
        double CGOLD = 0.3819660;
        double ZEPS = 1.0E-10;
        double ax = args[0];
        double bx = args[1];
        double cx = args[2];
        double tol = args[3];
        double xmin = args[4];

        a = ((ax < cx) ? ax : cx);
        b = ((ax > cx) ? ax : cx);
        x = w = v = bx;
        fw = fv = fx = f1dim(x, ncom, pcom, xicom, fcn);

        for (iter = 1; iter <= ITMAX; iter++) {
            xm = 0.5 * (a + b);
            tol1 = tol * Math.abs(x) + ZEPS;
            tol2 = 2.0 * tol1;

            if (Math.abs(x - xm) <= (tol2 - 0.5 * (b - a))) {
                xmin = x;
                args[4] = xmin;

                return fx;
            }

            if (Math.abs(e) > tol1) {
                r = (x - w) * (fx - fv);
                q = (x - v) * (fx - fw);
                p = (x - v) * q - (x - w) * r;
                q = 2.0 * (q - r);

                if (q > 0.0) {
                    p = -p;
                }

                q = Math.abs(q);
                etemp = e;
                e = d;

                if ((Math.abs(p) >= Math.abs(0.5 * q * etemp)) ||
                        (p <= q * (x - a)) || (p >= q * (b - x))) {
                    e = ((x >= xm) ? a - x : b - x);
                    d = CGOLD * e;
                }
                else {
                    d = p / q;
                    u = x + d;

                    if ((u - a) < tol2 || (b - u) < tol2) {
                        d = ((xm - x) >= 0.0 ? Math.abs(tol1) : -Math.abs(
                                tol1));
                    }
                }
            }
            else {
                e = ((x >= xm) ? a - x : b - x);
                d = CGOLD * e;
            }

            double s = ((tol1 >= 0.0) ? Math.abs(d) : -Math.abs(d));

            u = ((Math.abs(d) >= tol1) ? x + d : x + s);
            fu = f1dim(u, ncom, pcom, xicom, fcn);

            if (fu <= fx) {
                if (u >= x) {
                    a = x;
                }
                else {
                    b = x;
                }

                v = w;
                w = x;
                x = u;
                fv = fw;
                fw = fx;
                fx = fu;
            }
            else {
                if (u < x) {
                    a = u;
                }
                else {
                    b = u;
                }

                if ((fu <= fw) || (w == x)) {
                    v = w;
                    w = u;
                    fv = fw;
                    fw = fu;
                }
                else if ((fu <= fv) || (v == x) || (v == w)) {
                    v = u;
                    fv = fu;
                }
            }
        }

        xmin = x;
        args[4] = xmin;

        return fx;
    }

    /**
     * Evaluates a fitting function for an array of freeParameters.
     *
     * @author Joseph Ramsey
     */
    static interface FittingFunction {

        /**
         * Returns the value of the function for the given array of parameter
         * values.
         */
        double evaluate(double[] argument);

        /**
         * Returns the number of freeParameters.
         */
        int getNumParameters();
    }

    /**
     * Wraps a Sem for purposes of calculating its fitting function for given
     * parameter values.
     *
     * @author Joseph Ramsey
     */
    static class SemFittingFunction implements SemOptimizerNrPowell.FittingFunction {

        /**
         * The wrapped Sem.
         */
        private final SemIm sem;

        /**
         * Constructs a new CoefFittingFunction for the given Sem.
         */
        public SemFittingFunction(SemIm sem) {
            this.sem = sem;
        }

        /**
         * Computes the maximum likelihood function value for the given
         * freeParameters values as given by the optimizer. These values are mapped
         * to parameter values.
         */
        public double evaluate(double[] parameters) {
            sem.setFreeParamValues(parameters);

            double fml = sem.getScore();

            TetradLogger.getInstance().log("optimization", "FML = " + fml);
            System.out.println("FML = " + fml);

            if (Double.isNaN(fml)) {
                return 10000.0;
            }

            return fml;
        }

        /**
         * Returns the number of arguments. Required by the MultivariateFunction
         * interface.
         */
        public int getNumParameters() {
            return this.sem.getNumFreeParams();
        }
    }
}





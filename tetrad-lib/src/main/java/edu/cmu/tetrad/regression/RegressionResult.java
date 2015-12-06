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

package edu.cmu.tetrad.regression;

import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.TetradVector;
import edu.cmu.tetrad.util.TextTable;

import java.text.NumberFormat;


/**
 * Stores the various components of a regression result so they can be passed
 * around together more easily.
 *
 * @author Joseph Ramsey
 */
public class RegressionResult implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * True iff this model assumes a zero intercept.
     *
     * @see #RegressionResult
     */
    private boolean zeroInterceptAssumed;

    /**
     * Regressor names.
     *
     * @see #RegressionResult
     */
    private String[] regressorNames;

    /**
     * The number of data points.
     *
     * @see #RegressionResult
     */
    private int n;

    /**
     * The array of regression coefficients.
     *
     * @see #RegressionResult
     */
    private double[] b;

    /**
     * The array of t-statistics for the regression coefficients.
     *
     * @see #RegressionResult
     */
    private double[] t;

    /**
     * The array of p-values for the regression coefficients.
     *
     * @see #RegressionResult
     */
    private double[] p;

    /**
     * Standard errors of the coefficients
     *
     * @see #RegressionResult
     */
    private double[] se;

    /**
     * R square value.
     *
     * @see #RegressionResult
     */
    private double r2;

    /**
     * Residual sums of squares.
     *
     * @see #RegressionResult
     */
    private double rss;

    /**
     * Alpha value to determine significance.
     *
     * @see #RegressionResult
     */
    private double alpha;

    /**
     * Teh predicted values.
     */
    private TetradVector yHat;

    /**
     * The residuals.
     */
    private TetradVector res;

    /**
     * A result for a variety of regression algorithms.
     *
     * @param zeroInterceptAssumed True iff a zero intercept was assumed in
     *                             doing the regression, in which case this
     *                             coefficient is provided; otherwise, not.
     * @param regressorNames       The list of regressor variable names, in
     *                             order.
     * @param n                    The sample size.
     * @param b                    The list of coefficients, in order. If a zero
     *                             intercept was not assumed, this list begins
     *                             with the intercept.
     * @param t                    The list of t-statistics for the
     *                             coefficients, in order. If a zero intercept
     *                             was not assumed, this list begins with the t
     *                             statistic for the intercept.
     * @param p                    The p-values for the coefficients, in order.
     *                             If a zero intercept was not assumed, this
     *                             list begins with the p value for the
     *                             intercept.
     * @param se                   The standard errors for the coefficients, in
     *                             order. If a zero intercept was not assumed,
     *                             this list begins with the standard error of
     *                             the intercept.
     * @param r2                   The R squared statistic for the regression.
     * @param rss                  The residual sum of squares of the
     *                             regression.
     * @param alpha                The alpha value for the regression,
     *                             determining which regressors are taken to be
     */
    public RegressionResult(boolean zeroInterceptAssumed, String[] regressorNames,
                            int n, double[] b, double[] t,
                            double[] p, double[] se, double r2,
                            double rss, double alpha, TetradVector yHat, TetradVector res) {
        if (regressorNames == null) {
            throw new NullPointerException();
        }

        if (b == null) {
            throw new NullPointerException();
        }

        if (t == null) {
            throw new NullPointerException();
        }

        if (p == null) {
            throw new NullPointerException();
        }

        if (se == null) {
            throw new NullPointerException();
        }

        this.zeroInterceptAssumed = zeroInterceptAssumed;

        // Need to set this one before calling getNumRegressors.
        this.regressorNames = regressorNames;

        this.n = n;
        this.b = b;
        this.t = t;
        this.p = p;
        this.se = se;
        this.r2 = r2;
        this.alpha = alpha;
        this.rss = rss;

        this.yHat = yHat;
        this.res = res;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static RegressionResult serializableInstance() {
        return new RegressionResult(true, new String[0],
                10, new double[0], new double[0], new double[0],
                new double[0], 0, 0, 0, null, null);
    }

    public boolean isZeroInterceptAssumed() {
        return zeroInterceptAssumed;
    }

    public double getRSquared() {
        return r2;
    }

    /**
     * @return the number of data points.
     */
    public int getN() {
        return n;
    }

    /**
     * @return the number of regressors.
     */
    public int getNumRegressors() {
        return regressorNames.length;
    }

    /**
     * @return the array of regression coeffients.
     */
    public double[] getCoef() {
        return b;
    }

    /**
     * @return the array of t-statistics for the regression coefficients.
     */
    public double[] getT() {
        return t;
    }

    /**
     * @return the array of p-values for the regression coefficients.
     */
    public double[] getP() {
        return p;
    }

    public double[] getSe() {
        return se;
    }


    public String[] getRegressorNames() {
        return regressorNames;
    }

    public double getPredictedValue(double[] x) {
        double yHat = 0.0;

        int offset = zeroInterceptAssumed ? 0 : 1;

        for (int i = 0; i < getNumRegressors(); i++) {
            yHat += b[i + offset] * x[i];
        }

        if (!zeroInterceptAssumed) {
            yHat += b[0]; // error term.
        }

        return yHat;
    }

    public String toString() {
        StringBuilder summary = new StringBuilder(getPreamble());
        TextTable table = getResultsTable();

        summary.append("\n").append(table.toString());
        return summary.toString();
    }

    public TextTable getResultsTable() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        TextTable table = new TextTable(getNumRegressors() + 3, 6);

        table.setToken(0, 0, "VAR");
        table.setToken(0, 1, "COEF");
        table.setToken(0, 2, "SE");
        table.setToken(0, 3, "T");
        table.setToken(0, 4, "P");
        table.setToken(0, 5, "");

        for (int i = 0; i < getNumRegressors() + 1; i++) {

            // Note: the first column contains the regression constants.
            String variableName = (i > 0) ? regressorNames[i - 1] : "const";

            table.setToken(i + 2, 0, variableName);
            table.setToken(i + 2, 1, nf.format(b[i]));

            if (se.length != 0) {
                table.setToken(i + 2, 2, nf.format(se[i]));
            } else {
                table.setToken(i + 2, 2, "-1");
            }

            if (t.length != 0) {
                table.setToken(i + 2, 3, nf.format(t[i]));
            } else {
                table.setToken(i + 2, 3, "-1");
            }

            if (p.length != 0) {
                table.setToken(i + 2, 4, nf.format(p[i]));
            } else {
                table.setToken(i + 2, 4, "-1");
            }

            if (p.length != 0) {
                table.setToken(i + 2, 5, (p[i] < alpha) ? "significant " : "");
            } else {
                table.setToken(i + 2, 5, "(p not defined)");
            }
        }

        return table;
    }

    public String getPreamble() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        String rssString = nf.format(rss);
        String r2String = nf.format(r2);
        String preamble = "\n REGRESSION RESULT" +
                "\n n = " + n + ", k = " +
                (getNumRegressors() + 1) + ", alpha = " + alpha +
                "\n" + " SSE = " + rssString +
                "\n" + " R^2 = " + r2String +
                "\n";
        return preamble;
    }

    public TetradVector getYHat() {
        return yHat;
    }

    public TetradVector getResiduals() {
        return res;
    }
}







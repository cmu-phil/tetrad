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

package edu.cmu.tetrad.regression;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.util.FastMath;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements a logistic regression algorithm based on a Javascript implementation by John Pezzullo.  That
 * implementation together with a description of logistic regression and some examples appear on his web page
 * <a href="http://members.aol.com/johnp71/logistic.html">...</a>
 * <p>
 * See also  Applied Logistic Regression, by D.W. Hosmer and S. Lemeshow. 1989, John Wiley and Sons, New York which
 * Pezzullo references.  In particular see pages 27-29.
 *
 * @author Frank Wimberly
 * @version $Id: $Id
 */
public class LogisticRegression implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data set that was supplied.
     */
    private final DataSet dataSet;
    /**
     * The data converted into column major, to avoid unnecessary copying.
     */
    private final double[][] dataCols;
    /**
     * The default alpha level which may be specified otherwise in the GUI
     */
    private double alpha = 0.05;

    /**
     * The rows in the data used for regression.
     */
    private int[] rows;

    /**
     * A mixed data set. The targets of regresson must be binary. Regressors must be continuous or binary. Other
     * variables don't matter.
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public LogisticRegression(DataSet dataSet) {
        this.dataSet = dataSet;
        this.dataCols = dataSet.getDoubleData().transpose().toArray();
        setRows(new int[dataSet.getNumRows()]);
        for (int i = 0; i < getRows().length; i++) getRows()[i] = i;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.regression.LogisticRegression} object
     */
    public static LogisticRegression serializableInstance() {
        return new LogisticRegression(BoxDataSet.serializableInstance());
    }

    /**
     * x must be binary; regressors must be continuous or binary.
     *
     * @param x          a {@link edu.cmu.tetrad.data.DiscreteVariable} object
     * @param regressors a {@link java.util.List} object
     * @return a {@link edu.cmu.tetrad.regression.LogisticRegression.Result} object
     */
    public Result regress(DiscreteVariable x, List<Node> regressors) {
        if (!binary(x)) {
            throw new IllegalArgumentException("Target must be binary.");
        }

        for (Node node : regressors) {
            if (!(node instanceof ContinuousVariable || binary(node))) {
                throw new IllegalArgumentException("Regressors must be continuous or binary.");
            }
        }

        double[][] _regressors = new double[regressors.size()][getRows().length];

        for (int j = 0; j < regressors.size(); j++) {
            int col = this.dataSet.getColumn(regressors.get(j));
            double[] dataCol = this.dataCols[col];

            for (int i = 0; i < getRows().length; i++) {
                _regressors[j][i] = dataCol[getRows()[i]];
            }
        }

        int[] target = new int[getRows().length];
        int col = this.dataSet.getColumn(this.dataSet.getVariable(x.getName()));

        for (int i = 0; i < getRows().length; i++) {
            target[i] = this.dataSet.getInt(getRows()[i], col);
        }

        List<String> regressorNames = new ArrayList<>();

        for (Node node : regressors) {
            regressorNames.add(node.getName());
        }

        return regress(target, x.getName(), _regressors, regressorNames);
    }

    private boolean binary(Node x) {
        return x instanceof DiscreteVariable && ((DiscreteVariable) x).getNumCategories() == 2;
    }

    /**
     * Regresses the single-column target onto the regressors which have been previously set, generating a regression
     * result.
     * <p>
     * The target must be a two-valued variable with values 0 and 1.
     * <p>
     * This implements an iterative search.
     */
    private Result regress(int[] target, String targetName, double[][] regressors, List<String> regressorNames) {

        double[][] x;
        double[] c1;

        int numRegressors = regressors.length;
        int numCases = target.length;

        // make a new matrix x with all the columns of regressors
        // but with first column all 1.0's.
        x = new double[numRegressors + 1][];
        c1 = new double[numCases];

        x[0] = c1;

        System.arraycopy(regressors, 0, x, 1, numRegressors);

        for (int i = 0; i < numCases; i++) {
            x[0][i] = 1.0;
            c1[i] = 1.0;
        }

        double[] xMeans = new double[numRegressors + 1];
        double[] xStdDevs = new double[numRegressors + 1];

        double[] y0 = new double[numCases];
        double[] y1 = new double[numCases];
        for (int i = 0; i < numCases; i++) {
            y0[i] = 0;
            y1[i] = 0;
        }

        int ny0 = 0;
        int ny1 = 0;
        int nc = 0;

        for (int i = 0; i < numCases; i++) {
            if (target[i] == 0.0) {
                y0[i] = 1;
                ny0++;
            } else {
                y1[i] = 1;
                ny1++;
            }
            nc += y0[i] + y1[i];
            for (int j = 1; j <= numRegressors; j++) {
                xMeans[j] += (y0[i] + y1[i]) * x[j][i];
                xStdDevs[j] += (y0[i] + y1[i]) * x[j][i] * x[j][i];
            }
        }

        for (int j = 1; j <= numRegressors; j++) {
            xMeans[j] /= nc;
            xStdDevs[j] /= nc;
            xStdDevs[j] = FastMath.sqrt(FastMath.abs(xStdDevs[j] - xMeans[j] * xMeans[j]));
        }
        xMeans[0] = 0.0;
        xStdDevs[0] = 1.0;

        for (int i = 0; i < nc; i++) {
            for (int j = 1; j <= numRegressors; j++) {
                x[j][i] = (x[j][i] - xMeans[j]) / xStdDevs[j];
            }
        }

        //report = report + ("Iteration history...\n");

        double[] par = new double[numRegressors + 1];
        double[] parStdErr = new double[numRegressors + 1];
        double[] coefficients;

        par[0] = FastMath.log((double) ny1 / (double) ny0);
        for (int j = 1; j <= numRegressors; j++) {
            par[j] = 0.0;
        }

        double[][] arr = new double[numRegressors + 1][numRegressors + 2];

        double lnV;
        double ln1mV;

        double llP = 2e+10;
        double ll = 1e+10;
        double llN = 0.0;

        while (FastMath.abs(llP - ll) > 1e-7) {   /// 1e-7

            llP = ll;
            ll = 0.0;

            for (int j = 0; j <= numRegressors; j++) {
                for (int k = j; k <= numRegressors + 1; k++) {
                    arr[j][k] = 0.0;
                }
            }

            for (int i = 0; i < nc; i++) {
                double q;
                double v = par[0];

                for (int j = 1; j <= numRegressors; j++) {
                    v += par[j] * x[j][i];
                }

                if (v > 15.0) {
                    lnV = -FastMath.exp(-v);
                    ln1mV = -v;
                    q = FastMath.exp(-v);
                    v = FastMath.exp(lnV);
                } else {
                    if (v < -15.0) {
                        lnV = v;
                        ln1mV = -FastMath.exp(v);
                        q = FastMath.exp(v);
                        v = FastMath.exp(lnV);
                    } else {
                        v = 1.0 / (1 + FastMath.exp(-v));
                        lnV = FastMath.log(v);
                        ln1mV = FastMath.log(1.0 - v);
                        q = v * (1.0 - v);
                    }
                }

                ll = ll - 2.0 * y1[i] * lnV - 2.0 * y0[i] * ln1mV;

                for (int j = 0; j <= numRegressors; j++) {
                    double xij = x[j][i];
                    arr[j][numRegressors + 1] +=
                            xij * (y1[i] * (1.0 - v) + y0[i] * (-v));

                    for (int k = j; k <= numRegressors; k++) {
                        arr[j][k] += xij * x[k][i] * q * (y0[i] + y1[i]);
                    }
                }
            }

            if (llP == 1e+10) {
                llN = ll;
            }

            for (int j = 1; j <= numRegressors; j++) {
                for (int k = 0; k < j; k++) {
                    arr[j][k] = arr[k][j];
                }
            }

            for (int i = 0; i <= numRegressors; i++) {
                double s = arr[i][i];
                arr[i][i] = 1.0;
                for (int k = 0; k <= numRegressors + 1; k++) {
                    arr[i][k] = arr[i][k] / s;
                }

                for (int j = 0; j <= numRegressors; j++) {
                    if (i != j) {
                        s = arr[j][i];
                        arr[j][i] = 0.0;
                        for (int k = 0; k <= numRegressors + 1; k++) {
                            arr[j][k] = arr[j][k] - s * arr[i][k];
                        }
                    }
                }
            }

            for (int j = 0; j <= numRegressors; j++) {
                par[j] += arr[j][numRegressors + 1];
            }
        }

        double chiSq = llN - ll;

        //Indicates whether each coefficient is significant at the alpha level.
        double[] pValues = new double[numRegressors + 1];

        for (int j = 1; j <= numRegressors; j++) {
            par[j] = par[j] / xStdDevs[j];
            parStdErr[j] = FastMath.sqrt(arr[j][j]) / xStdDevs[j];
            par[0] = par[0] - par[j] * xMeans[j];
            double zScore = par[j] / parStdErr[j];
            double prob = norm(FastMath.abs(zScore));

            pValues[j] = prob;
        }

        parStdErr[0] = FastMath.sqrt(arr[0][0]);
        double zScore = par[0] / parStdErr[0];
        pValues[0] = norm(zScore);

        double intercept = par[0];

        coefficients = par;

        return new Result(targetName,
                regressorNames, xMeans, xStdDevs, numRegressors, ny0, ny1, coefficients,
                parStdErr, pValues, intercept, ll, chiSq, this.alpha
        );
    }

    private double norm(double z) {
        double q = z * z;
        final double piOver2 = FastMath.PI / 2.0;

        if (FastMath.abs(q) > 7.0) {
            return (1.0 - 1.0 / q + 3.0 / (q * q)) * FastMath.exp(-q / 2.0) /
                   (FastMath.abs(z) * FastMath.sqrt(piOver2));
        } else {
            return new ChiSquaredDistribution(1).cumulativeProbability(q);
        }

    }

    /**
     * <p>Getter for the field <code>alpha</code>.</p>
     *
     * @return the alpha level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the alpha level.
     *
     * @param alpha a double
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * The rows in the data used for regression.
     */
    private int[] getRows() {
        return this.rows;
    }

    /**
     * <p>Setter for the field <code>rows</code>.</p>
     *
     * @param rows an array of {@link int} objects
     */
    public void setRows(int[] rows) {
        this.rows = rows;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s a {@link java.io.ObjectInputStream} object
     * @throws IOException            if an error occurs
     * @throws ClassNotFoundException if an error occurs
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    //================================== Public Methods =======================================//

    /**
     * The result of a logistic regression.
     */
    public static class Result implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;

        /**
         * The chi square statistic.
         */
        private final double chiSq;

        /**
         * The alpha level.
         */
        private final double alpha;

        /**
         * The names of the regressors.
         */
        private final List<String> regressorNames;

        /**
         * The target.
         */
        private final String target;

        /**
         * The number of cases with target = 0.
         */
        private final int ny0;

        /**
         * The number of cases with target = 1.
         */
        private final int ny1;

        /**
         * The number of regressors.
         */
        private final int numRegressors;

        /**
         * The array of regression coefficients.
         */
        private final double[] coefs;

        /**
         * The array of standard errors for the regression coefficients.
         */
        private final double[] stdErrs;

        /**
         * The array of coefP-values for the regression coefficients.
         */
        private final double[] probs;

        /**
         * The array of means.
         */
        private final double[] xMeans;

        /**
         * The array of standard devs.
         */
        private final double[] xStdDevs;

        /**
         * The intercept.
         */
        private final double intercept;

        /**
         * The log likelihood of the regression.
         */
        private final double logLikelihood;


        /**
         * Constructs a new LinRegrResult.
         *
         * @param target         the target variable
         * @param regressorNames the names of the regressors
         * @param xMeans         the array of means
         * @param xStdDevs       the array of standard devs
         * @param numRegressors  the number of regressors
         * @param ny0            the number of cases with target = 0.
         * @param ny1            the number of cases with target = 1.
         * @param coefs          the array of regression coefficients.
         * @param stdErrs        the array of std errors of the coefficients.
         * @param probs          the array of P-values for the regression
         * @param intercept      the intercept
         * @param logLikelihood  the log likelihood of the regression
         * @param chiSq          the chi square statistic
         * @param alpha          the alpha level
         */
        public Result(String target, List<String> regressorNames, double[] xMeans, double[] xStdDevs,
                      int numRegressors, int ny0, int ny1, double[] coefs,
                      double[] stdErrs, double[] probs, double intercept, double logLikelihood,
                      double chiSq, double alpha) {


            if (regressorNames.size() != numRegressors) {
                throw new IllegalArgumentException();
            }

            if (coefs.length != numRegressors + 1) {
                throw new IllegalArgumentException();
            }

            if (stdErrs.length != numRegressors + 1) {
                throw new IllegalArgumentException();
            }

            if (probs.length != numRegressors + 1) {
                throw new IllegalArgumentException();
            }

            if (xMeans.length != numRegressors + 1) {
                throw new IllegalArgumentException();
            }

            if (xStdDevs.length != numRegressors + 1) {
                throw new IllegalArgumentException();
            }
            if (target == null) {
                throw new NullPointerException();
            }

            this.intercept = intercept;
            this.target = target;
            this.xMeans = xMeans;
            this.xStdDevs = xStdDevs;
            this.regressorNames = regressorNames;
            this.numRegressors = numRegressors;
            this.ny0 = ny0;
            this.ny1 = ny1;
            this.coefs = coefs;
            this.stdErrs = stdErrs;
            this.probs = probs;
            this.logLikelihood = logLikelihood;
            this.chiSq = chiSq;
            this.alpha = alpha;
        }

        /**
         * Generates a simple exemplar of this class to test serialization.
         *
         * @return a {@link edu.cmu.tetrad.regression.LogisticRegression.Result} object
         */
        public static Result serializableInstance() {
            return new Result("X1", new ArrayList<>(), new double[1], new double[1], 0, 0, 0,
                    new double[1], new double[1], new double[1], 1.5, 0.0, 0.0, 0.05);
        }

        /**
         * The variables.
         *
         * @return a {@link java.util.List} object
         */
        public List<String> getRegressorNames() {
            return this.regressorNames;
        }

        /**
         * The target.
         *
         * @return a {@link java.lang.String} object
         */
        public String getTarget() {
            return this.target;
        }

        /**
         * The number of data points with target = 0.
         *
         * @return a int
         */
        public int getNy0() {
            return this.ny0;
        }

        /**
         * The number of data points with target = 1.
         *
         * @return a int
         */
        public int getNy1() {
            return this.ny1;
        }

        /**
         * The number of regressors.
         *
         * @return a int
         */
        public int getNumRegressors() {
            return this.numRegressors;
        }

        /**
         * The array of regression coefficients.
         *
         * @return an array of double
         */
        public double[] getCoefs() {
            return this.coefs;
        }

        /**
         * The array of standard errors for the regression coefficients.
         *
         * @return an array of double
         */
        public double[] getStdErrs() {
            return this.stdErrs;
        }

        /**
         * The array of coefP-values for the regression coefficients.
         *
         * @return an array of double
         */
        public double[] getProbs() {
            return this.probs;
        }

        /**
         * THe array of means.
         *
         * @return an array of double
         */
        public double[] getxMeans() {
            return this.xMeans;
        }

        /**
         * The array of standard devs.
         *
         * @return an array of double
         */
        public double[] getxStdDevs() {
            return this.xStdDevs;
        }

        /**
         * The intercept.
         *
         * @return a double
         */
        public double getIntercept() {
            return this.intercept;
        }

        /**
         * The log likelihood of the regression
         *
         * @return a double
         */
        public double getLogLikelihood() {
            return this.logLikelihood;
        }

        /**
         * Returns a string representation of the regression results.
         */
        public String toString() {
            NumberFormat nf = new DecimalFormat("0.0000");
            StringBuilder report = new StringBuilder();

            report.append(this.ny0).append(" cases have ").append(this.target).append(" = 0; ").append(this.ny1).append(" cases have ").append(this.target).append(" = 1.\n");

            report.append("Overall Model Fit...\n");
            report.append("  Chi Square = ").append(nf.format(this.chiSq)).append("; df = ").append(this.numRegressors).append("; ").append("p = ").append(nf.format(new ChiSquaredDistribution(this.numRegressors).cumulativeProbability(this.chiSq))).append("\n");
            report.append("\nCoefficients and Standard Errors...\n");
            report.append("\tCoeff.\tStdErr\tprob.\tsig.");

            report.append("\n");

            for (int i = 0; i < this.regressorNames.size(); i++) {
                report.append("\n").append(this.regressorNames.get(i)).append("\t")
                        .append(nf.format(this.coefs[i + 1])).append("\t").append(nf.format(this.stdErrs[i + 1]))
                        .append("\t").append(nf.format(this.probs[i + 1])).append("\t")
                        .append(this.probs[i + 1] < this.alpha ? "*" : "");
            }

            report.append("\n\nIntercept = ").append(nf.format(this.intercept)).append("\n");

            return report.toString();
        }
    }
}








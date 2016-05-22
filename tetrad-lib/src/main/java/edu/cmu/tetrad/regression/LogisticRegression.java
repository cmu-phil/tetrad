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

import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradSerializable;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.sqrt;

/**
 * Implements a logistic regression algorithm based on a Javascript
 * implementation by John Pezzullo.  That implementation together with a
 * description of logistic regression and some examples appear on his web page
 * http://members.aol.com/johnp71/logistic.html
 * <p>
 * See also  Applied Logistic Regression, by D.W. Hosmer and S. Lemeshow. 1989,
 * John Wiley & Sons, New York which Pezzullo references.  In particular see
 * pages 27-29.
 *
 * @author Frank Wimberly
 */
public class LogisticRegression implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The data set that was supplied.
     */
    private DataSet dataSet;

    private double alpha = 0.05;

    /**
     * The data converted into column major, to avoid unnecessary copying.
     */
    private double[][] dataCols;

    private int[] rows;

    private boolean calculateAll = true;

    private String targetName;

    private List<String> regressorNames;
    private int ny0;
    private int ny1;
    private double chiSq;
    private int numRegressors;
    private int[] target;
    private double[] coefs;
    private double[] stdErrs;
    private double[] probs;
    private double intercept;
    private double likelihood;
    private double[] xMeans;

    /**
     * A mixed data set. The targets of regresson must be binary. Regressors must be continuous or binary.
     * Other variables don't matter.
     */
    public LogisticRegression(DataSet dataSet) {
        this.dataSet = dataSet;
        dataCols = dataSet.getDoubleData().transpose().toArray();
        setRows(new int[dataSet.getNumRows()]);
        for (int i = 0; i < getRows().length; i++) getRows()[i] = i;
    }

    public LogisticRegression() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static LogisticRegression serializableInstance() {
        return new LogisticRegression(ColtDataSet.serializableInstance());
    }

    /**
     * x must be binary; regressors must be continuous or binary.
     */
    public void regress(DiscreteVariable x, List<Node> regressors) {
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
            int col = dataSet.getColumn(regressors.get(j));
            double[] dataCol = dataCols[col];

            for (int i = 0; i < getRows().length; i++) {
                _regressors[j][i] = dataCol[getRows()[i]];
            }
        }

        for (int j = 0; j < regressors.size(); j++) {
            standardize(_regressors[j]);
        }

        int[] target = new int[getRows().length];
        int col = dataSet.getColumn(dataSet.getVariable(x.getName()));

        for (int i = 0; i < getRows().length; i++) {
            target[i] = dataSet.getInt(getRows()[i], col);
        }

        this.targetName = x.getName();
        this.regressorNames = new ArrayList<>();

        for (Node node : regressors) {
            regressorNames.add(node.getName());
        }
    }

    private boolean binary(Node x) {
        return x instanceof DiscreteVariable && ((DiscreteVariable) x).getNumCategories() == 2;
    }

    /**
     * Regresses the single-column target onto the regressors which have been
     * previously set, generating a regression result.
     * <p>
     * The target must be a two-valued variable with values 0 and 1.
     * <p>
     * This implements an iterative search.
     */
    public void regress(int[] target, double[][] regressors) {
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
            xStdDevs[j] = Math.sqrt(Math.abs(xStdDevs[j] - xMeans[j] * xMeans[j]));
        }
        xMeans[0] = 0.0;
        xStdDevs[0] = 1.0;

        for (int i = 0; i < nc; i++) {
            for (int j = 1; j <= numRegressors; j++) {
                x[j][i] = (x[j][i] - xMeans[j]) / xStdDevs[j];
            }
        }

        //report = report + ("Iteration history...\n");

        double[] coefficients = new double[numRegressors + 1];
        double[] coefStdErr = new double[numRegressors + 1];

        coefficients[0] = Math.log((double) ny1 / (double) ny0);
        for (int j = 1; j <= numRegressors; j++) {
            coefficients[j] = 0.0;
        }

        double[][] arr = new double[numRegressors + 1][numRegressors + 2];

        double lnV;
        double ln1mV;

        double llP = 2e+10;
        double ll = 1e+10;
        double llN = 0.0;

        while (Math.abs(llP - ll) > 1e-7) {   /// 1e-7

            llP = ll;
            ll = 0.0;

            for (int j = 0; j <= numRegressors; j++) {
                for (int k = j; k <= numRegressors + 1; k++) {
                    arr[j][k] = 0.0;
                }
            }

            for (int i = 0; i < nc; i++) {
                double q;
                double v = coefficients[0];

                for (int j = 1; j <= numRegressors; j++) {
                    v += coefficients[j] * x[j][i];
                }

                if (v > 15.0) {
                    lnV = -Math.exp(-v);
                    ln1mV = -v;
                    q = Math.exp(-v);
                    v = Math.exp(lnV);
                } else {
                    if (v < -15.0) {
                        lnV = v;
                        ln1mV = -Math.exp(v);
                        q = Math.exp(v);
                        v = Math.exp(lnV);
                    } else {
                        v = 1.0 / (1 + Math.exp(-v));
                        lnV = Math.log(v);
                        ln1mV = Math.log(1.0 - v);
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
                coefficients[j] += arr[j][numRegressors + 1];
            }
        }

        double chiSq = llN - ll;

        //Indicates whether each coefficient is significant at the alpha level.
        double[] pValues = null;

        if (calculateAll) {
            pValues = new double[numRegressors + 1];

            for (int j = 1; j <= numRegressors; j++) {
                coefficients[j] = coefficients[j] / xStdDevs[j];
                coefStdErr[j] = Math.sqrt(Math.abs(arr[j][j])) / xStdDevs[j];
                coefficients[0] = coefficients[0] - coefficients[j] * xMeans[j];
                double zScore = coefficients[j] / coefStdErr[j];
                double prob = norm(Math.abs(zScore));

                pValues[j] = prob;
            }

            coefStdErr[0] = Math.sqrt(arr[0][0]);
            double zScore = coefficients[0] / coefStdErr[0];
            pValues[0] = norm(zScore);
//            zScores[0] = zScore;
        }

        double intercept = coefficients[0];

        // Calculating this explicitly. Should check if it's equal to par[0].
        double likelihood = getLikelihood(numCases, regressors, coefficients);

        this.ny0 = ny0;
        this.ny1 = ny1;
        this.chiSq = chiSq;
        this.numRegressors = numRegressors;
        this.target = target;
        this.chiSq = chiSq;
        this.coefs = coefficients;
        this.stdErrs = coefStdErr;
        this.probs = pValues;
        this.intercept = intercept;
        this.likelihood = likelihood;
        this.xMeans = xMeans;
    }

    private double getLikelihood(int numCases, double[][] regressors, double[] coefficients) {
        double e = coefficients[0];

        for (int i = 0; i < numCases; i++) {
            for (int j = 0; j < regressors.length; j++) {
                e += coefficients[j + 1] * regressors[j][i];
            }

            likelihood += i * e - Math.log(1.0 + Math.exp(e));
        }

        return likelihood;
    }

    private static double norm(double z) {
        double q = z * z;
        double piOver2 = Math.PI / 2.0;

        if (Math.abs(q) > 7.0) {
            return (1.0 - 1.0 / q + 3.0 / (q * q)) * Math.exp(-q / 2.0) /
                    (Math.abs(z) * Math.sqrt(piOver2));
        } else {
            return new ChiSquaredDistribution(1).cumulativeProbability(q);
        }

    }

    /**
     * The default alpha level which may be specified otherwise in the GUI
     */
    /**
     * @return the alpha level.
     */
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the alpha level.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * The rows in the data used for regression.
     */
    private int[] getRows() {
        return rows;
    }

    public void setRows(int[] rows) {
        this.rows = rows;
    }

    public String toString() {
        NumberFormat nf = new DecimalFormat("0.0000");
        String report = "";

        report = report + (ny0 + " cases have " + target + " = 0; " + ny1 +
                " cases have " + target + " = 1.\n");

        report = report + ("Overall Model Fit...\n");
        report = report + ("  Chi Square = " + nf.format(chiSq) + "; df = " +
                numRegressors + "; " + "p = " +
                nf.format(new ChiSquaredDistribution(numRegressors).cumulativeProbability(chiSq)) + "\n");
        report = report + ("\nCoefficients and Standard Errors...\n");
        report = report + ("\tCoeff.\tStdErr\tprob.\tsig.");

        report += "\n";

        for (int i = 0; i < regressorNames.size(); i++) {
            report += "\n" + regressorNames.get(i) +
                    "\t" + nf.format(coefs[i + 1]) +
                    "\t" + nf.format(stdErrs[i + 1]) +
                    "\t" + nf.format(probs[i + 1]) +
                    "\t" + (probs[i + 1] < alpha ? "*" : "");
        }

        report = report + ("\n\nIntercept = " + nf.format(intercept) + "\n");

        return report;
    }

    //================================== Public Methods =======================================//

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    private void standardize(double[] data) {
        double sum = 0.0;

        for (double d : data) {
            sum += d;
        }

        double mean = sum / data.length;

        for (int i = 0; i < data.length; i++) {
            data[i] = data[i] - mean;
        }

        double var = 0.0;

        for (double d : data) {
            var += d * d;
        }

        var /= (data.length);
        double sd = sqrt(var);

        for (int i = 0; i < data.length; i++) {
            data[i] /= sd;
        }
    }

    public boolean isCalculateAll() {
        return calculateAll;
    }

    public void setCalculateAll(boolean calculateAll) {
        this.calculateAll = calculateAll;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public List<String> getRegressorNames() {
        return regressorNames;
    }

    public void setRegressorNames(List<String> regressorNames) {
        this.regressorNames = regressorNames;
    }

    public int getNy0() {
        return ny0;
    }

    public void setNy0(int ny0) {
        this.ny0 = ny0;
    }

    public int getNy1() {
        return ny1;
    }

    public void setNy1(int ny1) {
        this.ny1 = ny1;
    }

    public double getChiSq() {
        return chiSq;
    }

    public void setChiSq(double chiSq) {
        this.chiSq = chiSq;
    }

    public int getNumRegressors() {
        return numRegressors;
    }

    public void setNumRegressors(int numRegressors) {
        this.numRegressors = numRegressors;
    }

    public double[] getCoefs() {
        return coefs;
    }

    public void setCoefs(double[] coefs) {
        this.coefs = coefs;
    }

    public double[] getStdErrs() {
        return stdErrs;
    }

    public void setStdErrs(double[] stdErrs) {
        this.stdErrs = stdErrs;
    }

    public double[] getProbs() {
        return probs;
    }

    public void setProbs(double[] probs) {
        this.probs = probs;
    }

    public double getIntercept() {
        return intercept;
    }

    public void setIntercept(double intercept) {
        this.intercept = intercept;
    }

    public double getLikelihood() {
        return likelihood;
    }

    public void setLikelihood(double likelihood) {
        this.likelihood = likelihood;
    }

    public double[] getxMeans() {
        return xMeans;
    }

    public void setxMeans(double[] xMeans) {
        this.xMeans = xMeans;
    }
}








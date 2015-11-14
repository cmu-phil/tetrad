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
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements a logistic regression algorithm based on a Javascript
 * implementation by John Pezzullo.  That implementation together with a
 * description of logistic regression and some examples appear on his web page
 * http://members.aol.com/johnp71/logistic.html
 * <p/>
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

    /**
     * The default alpha level which may be specified otherwise in the GUI
     */
    private double alpha = 0.05;

    /**
     * The data converted into column major, to avoid unnecessary copying.
     */
    private double[][] dataCols;

    private int[] rows;

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

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static LogisticRegression serializableInstance() {
        return new LogisticRegression(ColtDataSet.serializableInstance());
    }

    /**
     * x must be binary; regressors must be continuous or binary.
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
            int col = dataSet.getColumn(regressors.get(j));
            double[] dataCol = dataCols[col];

            for (int i = 0; i < getRows().length; i++) {
                _regressors[j][i] = dataCol[getRows()[i]];
            }
        }

        int[] target = new int[getRows().length];
        int col = dataSet.getColumn(dataSet.getVariable(x.getName()));

        for (int i = 0; i < getRows().length; i++) {
            target[i] = dataSet.getInt(getRows()[i], col);
        }

        List<String> regressorNames = new ArrayList<String>();

        for (Node node : regressors) {
            regressorNames.add(node.getName());
        }

        return regress(target, x.getName(), _regressors, regressorNames);
    }

    private boolean binary(Node x) {
        return x instanceof DiscreteVariable && ((DiscreteVariable) x).getNumCategories() == 2;
    }

    /**
     * Regresses the single-column target onto the regressors which have been
     * previously set, generating a regression result.
     * <p/>
     * The target must be a two-valued variable with values 0 and 1.
     * <p/>
     * This implements an iterative search.
     */
    public Result regress(int[] target, String targetName, double[][] regressors, List<String> regressorNames) {

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

        double[] par = new double[numRegressors + 1];
        double[] parStdErr = new double[numRegressors + 1];
        double[] coefficients = new double[numRegressors + 1];

        par[0] = Math.log((double) ny1 / (double) ny0);
        for (int j = 1; j <= numRegressors; j++) {
            par[j] = 0.0;
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
                double v = par[0];

                for (int j = 1; j <= numRegressors; j++) {
                    v += par[j] * x[j][i];
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
                par[j] += arr[j][numRegressors + 1];
            }
        }

        //report = report + (" (Converged) \n");

        EdgeListGraph outgraph = new EdgeListGraph();
        Node targNode = new GraphNode(targetName);
        outgraph.addNode(targNode);

        double chiSq = llN - ll;

        //Indicates whether each coefficient is significant at the alpha level.
        String[] sigMarker = new String[numRegressors];
        double[] pValues = new double[numRegressors + 1];
        double[] zScores = new double[numRegressors + 1];

        for (int j = 1; j <= numRegressors; j++) {
            par[j] = par[j] / xStdDevs[j];
            parStdErr[j] = Math.sqrt(arr[j][j]) / xStdDevs[j];
            par[0] = par[0] - par[j] * xMeans[j];
            double zScore = par[j] / parStdErr[j];
            double prob = norm(Math.abs(zScore));


            pValues[j] = prob;
            zScores[j] = zScore;

            if (prob < alpha) {
                sigMarker[j - 1] = "*";
                Node predNode = new GraphNode(regressorNames.get(j - 1));
                outgraph.addNode(predNode);
                Edge newEdge = new Edge(predNode, targNode, Endpoint.TAIL,
                        Endpoint.ARROW);
                outgraph.addEdge(newEdge);
            } else {
                sigMarker[j - 1] = "";
            }
        }

        parStdErr[0] = Math.sqrt(arr[0][0]);
        double zScore = par[0] / parStdErr[0];
        pValues[0] = norm(Math.abs(zScore));
        zScores[0] = zScore;

        double intercept = par[0];

        coefficients = par;

        return new Result(targetName,
                regressorNames, xMeans, xStdDevs, numRegressors, ny0, ny1, coefficients,
                parStdErr, pValues, intercept, ll, sigMarker, chiSq
        );
    }

    private double norm(double z) {
        double q = z * z;
        double piOver2 = Math.PI / 2.0;

        if (Math.abs(z) > 7.0) {
            return (1.0 - 1.0 / q + 3.0 / (q * q)) * Math.exp(-q / 2.0) /
                    (Math.abs(z) * Math.sqrt(piOver2));
        } else {
            return ProbUtils.chisqCdf(q, 1);
        }

    }

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
    public int[] getRows() {
        return rows;
    }

    public void setRows(int[] rows) {
        this.rows = rows;
    }

    public static class Result implements TetradSerializable {
        static final long serialVersionUID = 23L;
        private final String[] sigMarker;
        private final double chiSq;
        private String result;
        private List<String> regressorNames;
        private String target;
        private int ny0;
        private int ny1;
        private int numRegressors;
        private double[] coefs;
        private double[] stdErrs;
        private double[] probs;
        private double[] xMeans;
        private double[] xStdDevs;
        private double intercept;
        private double logLikelihood;


        /**
         * Constructs a new LinRegrResult.
         *
         * @param ny0           the number of cases with target = 0.
         * @param ny1           the number of cases with target = 1.
         * @param numRegressors the number of regressors
         * @param coefs         the array of regression coefficients.
         * @param stdErrs       the array of std errors of the coefficients.
         * @param probs         the array of P-values for the regression
         *                      coefficients.
         */
        public Result(String target, List<String> regressorNames, double[] xMeans, double[] xStdDevs,
                      int numRegressors, int ny0, int ny1, double[] coefs,
                      double[] stdErrs, double[] probs, double intercept, double logLikelihood,
                      String[] sigmMarker, double chiSq) {


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
            this.result = result;
            this.logLikelihood = logLikelihood;
            this.sigMarker = sigmMarker;
            this.chiSq = chiSq;
        }

        /**
         * Generates a simple exemplar of this class to test serialization.
         */
        public static Result serializableInstance() {
            return new Result("X1", new ArrayList<String>(), new double[1], new double[1], 0, 0, 0,
                    new double[1], new double[1], new double[1], 1.5, 0.0, new String[0], 0.0);
        }

        /**
         * String representation of the result
         */
        public String getResult() {
            return result;
        }

        /**
         * The variables.
         */
        public List<String> getRegressorNames() {
            return regressorNames;
        }

        /**
         * The target.
         */
        public String getTarget() {
            return target;
        }

        /**
         * The number of data points with target = 0.
         */
        public int getNy0() {
            return ny0;
        }

        /**
         * The number of data points with target = 1.
         */
        public int getNy1() {
            return ny1;
        }

        /**
         * The number of regressors.
         */
        public int getNumRegressors() {
            return numRegressors;
        }

        /**
         * The array of regression coefficients.
         */
        public double[] getCoefs() {
            return coefs;
        }

        /**
         * The array of standard errors for the regression coefficients.
         */
        public double[] getStdErrs() {
            return stdErrs;
        }

        /**
         * The array of coefP-values for the regression coefficients.
         */
        public double[] getProbs() {
            return probs;
        }

        /**
         * THe array of means.
         */
        public double[] getxMeans() {
            return xMeans;
        }

        /**
         * The array of standard devs.
         */
        public double[] getxStdDevs() {
            return xStdDevs;
        }

        public double getIntercept() {
            return intercept;
        }

        /**
         * The log likelihood of the regression
         */
        public double getLogLikelihood() {
            return logLikelihood;
        }

        public String getReport() {
            NumberFormat nf = new DecimalFormat("0.0000");
            String report = "";

            report = report + (ny0 + " cases have " + target + " = 0; " + ny1 +
                    " cases have " + target + " = 1.\n");
            //if(nc != numCases) System.out.println("nc NOT numCases");

            for (int j = 0; j < regressorNames.size(); j++) {
                report = report + ("\tVariable\tAvg\tSD\n");
                report = report + ("\t" + regressorNames.get(j - 1) + "\t" +
                        nf.format(xMeans[j]) + "\t" + nf.format(xStdDevs[j]) +
                        "\n");
            }

            double[] par = new double[numRegressors + 1];
            double[] parStdErr = new double[numRegressors + 1];

            par[0] = Math.log((double) ny1 / (double) ny0);
            for (int j = 1; j <= numRegressors; j++) {
                par[j] = 0.0;
            }

            for (int j = 0; j < regressorNames.size(); j++) {
                report = report + (regressorNames.get(j - 1) + "\t" + nf.format(par[j]) +
                        "\t" + nf.format(parStdErr[j]) + "\t" + nf.format(probs[j]) +
                        "\t" + sigMarker[j - 1] + "\n");
            }

            report = report + ("Overall Model Fit...\n");
            report = report + ("  Chi Square = " + nf.format(chiSq) + "; df = " +
                    numRegressors + "; " + "p = " +
                    nf.format(ProbUtils.chisqCdf(chiSq, numRegressors)) + "\n");
            report = report + ("\nCoefficients and Standard Errors...\n");
            report = report + (" Variable\tCoeff.\tStdErr\tprob.\tsig.\n");

            report = report + ("\nIntercept = " + nf.format(intercept) + "\n");

            return report;
        }
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
}








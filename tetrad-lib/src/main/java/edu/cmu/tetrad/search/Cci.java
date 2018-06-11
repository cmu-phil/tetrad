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

import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.cmu.tetrad.util.StatUtils.cov;
import static edu.cmu.tetrad.util.StatUtils.median;
import static java.lang.Math.*;

/**
 * Checks conditional independence of variable in a continuous data set using a
 * conditional correlation test for the nonlinear nonGaussian case.
 *
 * @author Joseph Ramsey
 */
public final class Cci {

    /**
     * The matrix of data, N x M, where N is the number of samples, M the number
     * of variables, gotten from dataSet.
     */
    private double[][] data;

    /**
     * The significance level of the independence tests.
     */
    private double alpha;

    /**
     * Kernel widths of each variable.
     */
    private double[] h;

    /**
     * The q value of the most recent test.
     */
    private double score;

    /**
     * Map from nodes to the indices.
     */
    private Map<String, Integer> indices;

    /**
     * Number of functions to use in the (truncated) basis.
     */
    private int numFunctions = 10;

    /**
     * Z cutoff for testing; depends on alpha.
     */
    private double cutoff;

    //==================CONSTRUCTORS====================//

    /**
     * Constructs a new Independence test which checks independence facts based on the
     * correlation data implied by the given data set (must be continuous). The given
     * significance level is used.
     *
     * @param data  A data set containing only continuous columns.
     * @param alpha The alpha level of the test.
     */
    public Cci(RealMatrix data, List<String> variables, double alpha) {
        if (data == null) throw new NullPointerException();
        if (variables == null) throw new NullPointerException();
        if (data.getColumnDimension() != variables.size()) {
            throw new IllegalArgumentException("Columns in data do not match # variables.");
        }

        this.alpha = alpha;
        this.data = data.transpose().getData();

        indices = new HashMap<>();

        for (int i = 0; i < variables.size(); i++) {
            indices.put(variables.get(i), i);
        }

        h = new double[data.getColumnDimension()];

        for (int i = 0; i < data.getColumnDimension(); i++) {
            h[i] = h(variables.get(i));
        }

        this.cutoff = StatUtils.getZForAlpha(alpha);
    }

    //=================PUBLIC METHODS====================//

    /**
     * @return true iff x is independent of y conditional on z.
     */
    public boolean isIndependent(String x, String y, List<String> z) {
        double[][] ret = residuals(x, y, z);
        double[] rXZ = ret[0];
        double[] rYZ = ret[1];
        return independent(rXZ, rYZ);
    }

    /**
     * @return the minimal scores value calculated by the method for the most
     * recent independence check.
     */
    public double getScore() {
        return score - cutoff;
    }

    public double getAlpha() {
        return alpha;
    }

    /**
     * @return true just in the case the x and y vectors are independent,
     * once undefined values have been removed. Left public so it can be
     * accessed separately.
     */
    public boolean independent(double[] x, double[] y) {

        if (false) {
            int both = 0;

            for (int i = 0; i < x.length; i++) {
                if (!Double.isNaN(x[i]) && !Double.isNaN(y[i])) {
                    both++;
                }
            }

            // Get rid of NaN's.
            double[] _rXZ = new double[both];
            double[] _rYZ = new double[both];

            if (both != x.length) {
                int index = -1;

                for (int i = 0; i < x.length; i++) {
                    if (!Double.isNaN(x[i]) && !Double.isNaN(y[i])) {
                        ++index;
                        _rXZ[index] = x[i];
                        _rYZ[index] = y[i];
                    }
                }

                x = _rXZ;
                y = _rYZ;
            }
        }

        if (x.length < 10) {
            score = Double.NaN;
            return false; // For PC, should not remove the edge for this reason.
        }

        double[] _x = new double[x.length];
        double[] _y = new double[x.length];

        double maxScore = Double.NEGATIVE_INFINITY;

        for (int m = 1; m <= getNumFunctions(); m++) {
            for (int n = 1; n <= getNumFunctions(); n++) {
                for (int i = 0; i < x.length; i++) {
                    _x[i] = function(m, x[i]);
                    _y[i] = function(n, y[i]);
                }

                _x = scale(_x);
                _y = scale(_y);

                final double score = calcScore(_x, _y);
                if (Double.isInfinite(score) || Double.isNaN(score)) continue;
                if (score > maxScore) maxScore = score;
//                if (maxScore > cutoff) {
//                    this.score = maxScore;
//                    return false;
//                }
            }
        }

        this.score = maxScore;
        return maxScore < cutoff;
    }

    private double[] scale(double[] x) {
        double max = StatUtils.max(x);
        double min = StatUtils.min(x);

        double factor = Math.max(abs(max), abs(min));

//        double[] _x = new double[x.length];

        for (int i = 0; i < x.length; i++) {
            x[i] = x[i] / factor;
        }

        return x;
    }

    private double calcScore(double[] _x, double[] _y) {
        double[] covs = covariance(_x, _y);

        double sigmaXY = covs[0];
        double sigmaXX = covs[1];
        double sigmaYY = covs[2];
        double N = covs[3];


//        double sigmaXY = covariance(_x, _y);
//        double sigmaXX = covariance(_x, _x);
//        double sigmaYY = covariance(_y, _y);

        double r = sigmaXY / sqrt(sigmaXX * sigmaYY);

        // Non-parametric Fisher Z test.
        double _z = 0.5 * (log(1.0 + r) - log(1.0 - r));
//        final double N = _x.length;
        double w = sqrt(N) * _z;

        // Testing the hypothesis that _x and _y are uncorrelated and assuming that 4th moments of _x and _y
        // are finite and that the sample is large.
        _x = standardize(_x);
        _y = standardize(_y);

        return abs(w) / sqrt(moment22(_x, _y));
    }

    /**
     * Calculates the residuals of x regressed nonparametrically onto z. Left public
     * so it can be accessed separately.
     */
    public double[] residuals(String x, List<String> z) {
        int N = data[0].length;

        int _x = indices.get(x);

        double[] residuals = new double[N];

        final double[] xdata = data[_x];

        if (z.size() == 0) {

            // No need to center; the covariance calculation does that.
            for (int i = 0; i < N; i++) {
                residuals[i] = xdata[i];

                if (Double.isNaN(residuals[i])) {
                    residuals[i] = 0;
                }
            }

            return residuals;
        }

        int[] _z = new int[z.size()];

        for (int m = 0; m < z.size(); m++) {
            _z[m] = indices.get(z.get(m));
        }

        double h = 0.0;

        for (int c : _z) {
            if (this.h[c] > h) {
                h = this.h[c];
            }
        }

        h *= sqrt(_z.length);

//        double[] sums = new double[N];
//        double[] weights = new double[N];

        for (int i = 0; i < N; i++) {

            double sumsi = 0.0;
            double weightsi = 0.0;

            double xi = xdata[i];

            for (int j = 0; j < N; j++) {

                // Skips NaN values.
                double d = distance(data, _z, i, j);
                double k = kernel(d / h);

                double xj = xdata[j];

                if (Double.isNaN(xj)) xj = 0.0;

                sumsi += k * xj;
                weightsi += k;
            }

            residuals[i] = xi - sumsi / weightsi;

            if (Double.isNaN(residuals[i])) {
                residuals[i] = 0;
            }
        }

//        for (int i = 0; i < N; i++) {
//            double xi = xdata[i];
//
//            if (Double.isNaN(xi)) xi = 0.0;
//
//            // Skips NaN values.
////            double d = 0;//distance(data, _z, i, i);
//            double k = 0.5;//kernel(d / h);
//
//            sums[i] += k * xi;
//            weights[i] += k;
//        }

//        for (int i = 0; i < residuals.length; i++) {
//            residuals[i] = xdata[i] - sums[i] / weights[i];
//
//            if (Double.isNaN(residuals[i])) {
//                residuals[i] = 0;
//            }
//        }

        return residuals;
    }


    /**
     * Calculates the residuals of x regressed nonparametrically onto z. Left public
     * so it can be accessed separately.
     */
    public double[][] residuals(String x, String y, List<String> z) {
        int N = data[0].length;

        int _x = indices.get(x);
        int _y = indices.get(y);

        double[] residualsx = new double[N];
        double[] residualsy = new double[N];

        final double[] xdata = data[_x];
        final double[] ydata = data[_y];

        if (z.size() == 0) {

            // No need to center; the covariance calculation does that.
            for (int i = 0; i < N; i++) {
                residualsx[i] = xdata[i];

                if (Double.isNaN(residualsx[i])) {
                    residualsx[i] = 0;
                }
            }

            for (int i = 0; i < N; i++) {
                residualsy[i] = ydata[i];

                if (Double.isNaN(residualsy[i])) {
                    residualsy[i] = 0;
                }
            }
        } else {

            double[] sumsx = new double[N];
            double[] sumsy = new double[N];

            double[] weights = new double[N];

            int[] _z = new int[z.size()];

            for (int m = 0; m < z.size(); m++) {
                _z[m] = indices.get(z.get(m));
            }

            double h = 0.0;

            for (int c : _z) {
                if (this.h[c] > h) {
                    h = this.h[c];
                }
            }

            h *= sqrt(_z.length);

            for (int i = 0; i < N; i++) {
                double xi = xdata[i];
                double yi = ydata[i];

                for (int j = i + 1; j < N; j++) {

                    double xj = xdata[j];
                    double yj = ydata[j];

                    // Skips NaN values.
                    double d = distance(data, _z, i, j);
                    double k = kernel(d / h);

                    if (Double.isNaN(xi)) xi = 0.0;
                    if (Double.isNaN(yi)) yi = 0.0;

                    sumsx[i] += k * xj;
                    sumsy[i] += k * yj;

                    sumsx[j] += k * xi;
                    sumsy[j] += k * yi;

                    weights[i] += k;
                    weights[j] += k;
                }

                if (Double.isNaN(residualsx[i])) {
                    residualsx[i] = 0;
                }

                if (Double.isNaN(residualsy[i])) {
                    residualsy[i] = 0;
                }
            }

            for (int i = 0; i < N; i++) {
                double xi = xdata[i];
                double yi = ydata[i];

                if (Double.isNaN(xi)) xi = 0.0;
                if (Double.isNaN(yi)) yi = 0.0;

                double k = 0.5;

                sumsx[i] += k * xi;
                sumsy[i] += k * yi;
                weights[i] += k;
            }

            for (int i = 0; i < N; i++) {
                residualsx[i] = xdata[i] - sumsx[i] / weights[i];
                residualsy[i] = ydata[i] - sumsy[i] / weights[i];
            }
        }

        double[][] ret = new double[2][];
        ret[0] = residualsx;
        ret[1] = residualsy;

        return ret;
    }

    public void setNumFunctions(int numFunctions) {
        this.numFunctions = numFunctions;
    }

    //=====================PRIVATE METHODS====================//

    private double moment22(double[] x, double[] y) {
        int N = x.length;
        double sum = 0.0;

        for (int j = 0; j < x.length; j++) {
            sum += x[j] * x[j] * y[j] * y[j];
        }

        return sum / N;
    }

    // Polynomial basis. The 1 is left out according to Daudin.
    private double function(int index, double x) {
//        double g = sin((index) * x) + cos((index) * x); // This would be a sin cosine basis.
//
//        double g = Math.pow(x, index);
        double g = 1.0;

        for (int i = 1; i <= index; i++) {
            g *= x;
        }

        if (abs(g) == Double.POSITIVE_INFINITY) g = Double.NaN;

        return g;
    }

    /**
     * Number of functions to use in (truncated) basis
     */
    private int getNumFunctions() {
        return numFunctions;
    }

    private double[] covariance(double[] x, double[] y) {
        double sumXY = 0.0;
        double sumXX = 0.0;
        double sumYY = 0.0;
        double sumX = 0.0;
        double sumY = 0.0;
        int N = 0;

        for (int i = 0; i < x.length; i++) {
            if (Double.isNaN(x[i]) || Double.isNaN(y[i])) continue;

            sumXY += x[i] * y[i];
            sumXX += x[i] * x[i];
            sumYY += y[i] * y[i];
            sumX += x[i];
            sumY += y[i];

            N++;
        }

        double covxy = sumXY / N - (sumX / N) * (sumY / N);
        double covxx = sumXX / N - (sumX / N) * (sumX / N);
        double covyy = sumYY / N - (sumY / N) * (sumY / N);

        return new double[]{covxy, covxx, covyy, N};

//        return sumXY / N - (sumX / N) * (sumY / N);
    }

    // Optimal bandwidth qsuggested by Bowman and Azzalini (1997) q.31,
    // using MAD.
    private double h(String x) {

        double[] xCol = data[indices.get(x)];
        double[] g = new double[xCol.length];
        double median = median(xCol);
        for (int j = 0; j < xCol.length; j++) g[j] = abs(xCol[j] - median);
        double mad = median(g);
        return (1.4826 * mad) * pow((4.0 / 3.0) / xCol.length, 0.2);
    }

    // Uniform kernel.
    private double kernel(double z) {
        if (abs(z) > 1.) return 0.;
        else return .5;
    }

    // Euclidean distance.
    private double distance(double[][] data, int[] yCols, int i, int j) {
        double sum = 0.0;

        for (int yCol : yCols) {
            double d = data[yCol][i] - data[yCol][j];

            if (!Double.isNaN(d)) {
                sum += d * d;
            }
        }

        return sqrt(sum);
    }

    // Standardizes the given data array.
    private double[] standardize(double[] data1) {
        double[] data = Arrays.copyOf(data1, data1.length);

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

        return data;
    }
}





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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.*;

import static edu.cmu.tetrad.util.StatUtils.*;
import static org.apache.commons.math3.util.FastMath.pow;
import static org.apache.commons.math3.util.FastMath.*;

/**
 * Checks conditional independence of variable in a continuous data set using Daudin's method. See
 * <p>
 * Ramsey, J. D. (2014). A scalable conditional independence test for nonlinear, non-Gaussian data. arXiv preprint
 * arXiv:1401.5031.
 * <p>
 * This is corrected using Lemma 2, condition 4 of
 * <p>
 * Zhang, K., Peters, J., Janzing, D., and Schölkopf, B. (2012). Kernel-based conditional independence test and
 * application in causal discovery. arXiv preprint arXiv:1202.3775.
 * <p>
 * This all follows the original Daudin paper, which is this:
 * <p>
 * Daudin, J. J. (1980). Partial association measures and ann application to qualitative regression. Biometrika, 67(3),
 * 581-590.
 * <p>
 * We use Nadaraya-Watson kernel regression, though we further restrict the sample size to nearby points.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class ConditionalCorrelationIndependence {

    /**
     * The dataset supplied in the constructor.
     */
    private final DataSet dataSet;
    /**
     * The variables in datasSet.
     */
    private final List<Node> variables;
    /**
     * Map from nodes to their indices.
     */
    private final HashMap<Node, Integer> nodesHash;
    /**
     * The q value of the most recent test.
     */
    private double score;
    /**
     * Number of functions to use in the (truncated) basis.
     */
    private int numFunctions = 10;
    /**
     * Z cutoff for testing; depends on alpha.
     */
    private double cutoff;
    /**
     * Azzalini kernel widths are multiplied by this.
     */
    private double width = 1.0;
    /**
     * Kernel
     */
    private Kernel kernelMultiplier = Kernel.Gaussian;
    /**
     * Basis
     */
    private Basis basis = Basis.Polynomial;
    /**
     * The minimum sample size to use for the kernel regression.
     */
    private int kernelRegressionSampleSize = 100;

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation data implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet A data set containing only continuous columns.
     */
    public ConditionalCorrelationIndependence(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException();
        this.dataSet = DataTransforms.center(dataSet);

        this.variables = dataSet.getVariables();

        this.nodesHash = new HashMap<>();
        for (int i = 0; i < this.variables.size(); i++) {
            this.nodesHash.put(this.variables.get(i), i);
        }
    }

    /**
     * Returns the p-value of the test, x _||_ y | z. Can be compared to alpha.
     *
     * @param x  a {@link edu.cmu.tetrad.graph.Node} object
     * @param y  a {@link edu.cmu.tetrad.graph.Node} object
     * @param _z a {@link java.util.Set} object
     * @return This p-value.
     */
    public double isIndependent(Node x, Node y, Set<Node> _z) {
        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        try {
            Map<Node, Integer> nodesHash = new HashMap<>();
            for (int i = 0; i < this.variables.size(); i++) {
                nodesHash.put(this.variables.get(i), i);
            }

            List<Node> allNodes = new ArrayList<>(z);
            allNodes.add(x);
            allNodes.add(y);

            List<Integer> rows = getRows(this.dataSet, allNodes, nodesHash);

            if (rows.isEmpty()) return 0;

            double[] rx = residuals(x, z, rows);
            double[] ry = residuals(y, z, rows);

            // rx _||_ ry ?
            double score = independent(rx, ry);
            this.score = score;

            return score;
        } catch (Exception e) {
            TetradLogger.getInstance().log(e.getMessage());
            return 0;
        }
    }


    /**
     * Calculates the residuals of x regressed nonparametrically onto z. Left public so it can be accessed separately.
     *
     * @param x    a {@link edu.cmu.tetrad.graph.Node} object
     * @param z    a {@link java.util.List} object
     * @param rows a {@link java.util.List} object
     * @return a double[2][] array. The first double[] array contains the residuals for x, and the second double[] array
     * contains the residuals for y.
     */
    public double[] residuals(Node x, List<Node> z, List<Integer> rows) {
        int[] _rows = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) _rows[i] = rows.get(i);

        int[] _cols = new int[z.size() + 1];
        _cols[0] = this.nodesHash.get(x);
        for (int i = 0; i < z.size(); i++) _cols[1 + i] = this.nodesHash.get(z.get(i));

        DataSet _dataSet = (this.dataSet.subsetRowsColumns(_rows, _cols));

        for (int j = 0; j < _dataSet.getNumColumns(); j++) {
            scale(_dataSet, j);
        }

        double[][] _data = _dataSet.getDoubleData().transpose().toArray();

        if (_data.length == 0) {
            return new double[0];
        }

        if (z.isEmpty()) {
            return _data[0];
        }

        List<List<Integer>> _sortedIndices = new ArrayList<>();

        for (int z2 = 0; z2 < z.size(); z2++) {
            double[] w = _data[z2 + 1];
            List<Integer> sorted = new ArrayList<>();
            for (int t = 0; t < w.length; t++) sorted.add(t);
            sorted.sort(Comparator.comparingDouble(o -> w[o]));
            _sortedIndices.add(sorted);
        }

        List<Map<Integer, Integer>> _reverseLookup = new ArrayList<>();

        for (int z2 = 0; z2 < z.size(); z2++) {
            Map<Integer, Integer> m = new HashMap<>();

            for (int j = 0; j < _data[z2 + 1].length; j++) {
                m.put(_sortedIndices.get(z2).get(j), j);
            }

            _reverseLookup.add(m);
        }

        int _N = _data[0].length;

        double[] _residualsx = new double[_N];

        double[] _xdata = _data[0];

        double[] _sumx = new double[_N];

        double[] _totalWeightx = new double[_N];

        int[] _z = new int[z.size()];

        for (int m = 0; m < z.size(); m++) {
            _z[m] = m;
        }

        double _max = Double.NEGATIVE_INFINITY;

        for (double[] datum : _data) {
            double h = h(datum);
            if (h > _max) _max = h;
        }

        double h = _max;

        for (int i = 0; i < _N; i++) {
            Set<Integer> js = getCloseZs(_data, _z, i, this.kernelRegressionSampleSize,
                    _reverseLookup, _sortedIndices);

            for (int j : js) {
                double xj = _xdata[j];
                double d = distance(_data, _z, i, j);

                double k;

                if (this.kernelMultiplier == Kernel.Epinechnikov) {
                    k = kernelEpinechnikov(d, h);
                } else if (this.kernelMultiplier == Kernel.Gaussian) {
                    k = kernelGaussian(d, h);
                } else {
                    throw new IllegalStateException("Unsupported kernel type: " + this.kernelMultiplier);
                }

                _sumx[i] += k * xj;
                _totalWeightx[i] += k;
            }
        }

        for (int i = 0; i < _N; i++) {
            if (_totalWeightx[i] == 0) _totalWeightx[i] = 1;

            _residualsx[i] = _xdata[i] - _sumx[i] / _totalWeightx[i];

            if (Double.isNaN(_residualsx[i])) {
                _residualsx[i] = 0;
            }
        }

        return _residualsx;
    }


    /**
     * Sets the number of functions to use in (truncated) basis
     *
     * @param numFunctions This number.
     */
    public void setNumFunctions(int numFunctions) {
        this.numFunctions = numFunctions;
    }

    /**
     * Sets the kernel multiplier.
     *
     * @param kernelMultiplier This multiplier.
     */
    public void setKernelMultiplier(Kernel kernelMultiplier) {
        this.kernelMultiplier = kernelMultiplier;
    }

    /**
     * Sets the basis.
     *
     * @param basis This basis.
     * @see Basis
     */
    public void setBasis(Basis basis) {
        this.basis = basis;
    }

    /**
     * Returns the kernel width.
     *
     * @return This width.
     */
    public double getWidth() {
        return this.width;
    }

    /**
     * Sets the kernel width.
     *
     * @param width This width.
     */
    public void setWidth(double width) {
        this.width = width;
    }

    /**
     * Returns the p-value of the score.
     *
     * @param score The score.
     * @return This p-value.
     */
    public double getPValue(double score) {
        return 2.0 * (1.0 - new NormalDistribution(0, 1).cumulativeProbability(abs(score)));
    }

    /**
     * Returns the minimal scores value calculated by the method for the most recent independence check, less the cutoff
     * so that negative scores correspond to independence.
     *
     * @return This minimal score.
     */
    public double getScore() {
        return abs(this.score) - this.cutoff;//  alpha - getPValue();
    }

    /**
     * Sets the alpha cutoff.
     *
     * @param alpha This cutoff.
     */
    public void setAlpha(double alpha) {
        this.cutoff = getZForAlpha(alpha);
    }

    /**
     * Sets the kernel regression sample size.
     *
     * @param kernelRegressionSapleSize This sample size
     */
    public void setKernelRegressionSampleSize(int kernelRegressionSapleSize) {
        this.kernelRegressionSampleSize = kernelRegressionSapleSize;
    }

    /**
     * @return True, just in case the x and y vectors are independent, once undefined values have been removed. Left
     * public so it can be accessed separately.
     */
    private double independent(double[] x, double[] y) {
        double[] _x = new double[x.length];
        double[] _y = new double[y.length];

        double maxScore = Double.NEGATIVE_INFINITY;

        for (int m = 1; m <= this.numFunctions; m++) {
            for (int n = 1; n <= this.numFunctions; n++) {
                for (int i = 0; i < x.length; i++) {
                    _x[i] = function(m, x[i]);
                    _y[i] = function(n, y[i]);
                }

                double score = abs(nonparametricFisherZ(_x, _y));
                if (Double.isInfinite(score) || Double.isNaN(score)) continue;

                if (score > maxScore) {
                    maxScore = score;
                }
            }
        }

        return maxScore;
    }

    /**
     * Scales the values in a specific column of a given DataSet.
     *
     * @param dataSet The DataSet containing the values to be scaled.
     * @param col     The column index of the values to be scaled.
     */
    private void scale(DataSet dataSet, int col) {
        double max = Double.MIN_VALUE;
        double min = Double.MAX_VALUE;

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            double d = dataSet.getDouble(i, col);
            if (Double.isNaN(d)) continue;
            if (d > max) max = d;
            if (d < min) min = d;
        }

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            double d = dataSet.getDouble(i, col);
            if (Double.isNaN(d)) continue;
            dataSet.setDouble(i, col, min + (d - min) / (max - min));
        }
    }


    /**
     * Calculates the nonparametric Fisher Z test for the given arrays.
     *
     * @param _x An array of double values.
     * @param _y An array of double values.
     * @return The nonparametric Fisher Z test statistic.
     */
    private double nonparametricFisherZ(double[] _x, double[] _y) {

        // Testing the hypothesis that _x and _y are uncorrelated and assuming that 4th moments of _x and _y
        // are finite and that the sample is large.
        double[] __x = standardize(_x);
        double[] __y = standardize(_y);

        double r = covariance(__x, __y); // correlation
        int N = __x.length;

        // Non-parametric Fisher Z test.
        double z = 0.5 * sqrt(N) * (log(1.0 + r) - log(1.0 - r));

        return z / (sqrt((moment22(__x, __y))));
    }

    /**
     * Calculates the moment22 value for the given arrays x and y.
     *
     * @param x An array of double values.
     * @param y An array of double values.
     * @return The moment22 value.
     */
    private double moment22(double[] x, double[] y) {
        int N = x.length;
        double sum = 0.0;

        for (int j = 0; j < x.length; j++) {
            sum += x[j] * x[j] * y[j] * y[j];
        }

        return sum / N;
    }

    /**
     * Calculates a basis function value based on the given index and input value.
     *
     * @param index The index of the basis function.
     * @param x     The input value.
     * @return The basis function value.
     */
    private double function(int index, double x) {
        if (this.basis == Basis.Polynomial) {
            double g = 1.0;

            for (int i = 1; i <= index; i++) {
                g *= x;
            }

            if (abs(g) == Double.POSITIVE_INFINITY) g = Double.NaN;

            return g;
        } else if (this.basis == Basis.Cosine) {
            int i = (index + 1) / 2;

            if (index % 2 == 1) {
                return sin(i * x);
            } else {
                return cos(i * x);
            }
        } else {
            throw new IllegalStateException("That basis is not configured: " + this.basis);
        }
    }

    /**
     * Optimal bandwidth suggested by Bowman and Bowman and Azzalini (1997) q.31, using MAD.
     */
    private double h(double[] xCol) {
        double[] g = new double[xCol.length];
        double median = median(xCol);
        for (int j = 0; j < xCol.length; j++) g[j] = abs(xCol[j] - median);
        double mad = median(g);
        return (1.4826 * mad) * pow((4.0 / 3.0) / xCol.length, 0.2);
    }

    /**
     * Calculates the Epinechnikov kernel value for a given input and kernel width.
     *
     * @param z The input value to evaluate the kernel at.
     * @param h The kernel width.
     * @return The kernel value at the given input.
     */
    private double kernelEpinechnikov(double z, double h) {
        z /= getWidth() * h;
        if (abs(z) > 1) return 0.0;
        else return (/*0.75 **/ (1.0 - z * z));
    }

    /**
     * Calculates the Gaussian kernel value for a given input and kernel width.
     *
     * @param z The input value to evaluate the kernel at, which is divided by the product of the width and the current
     *          width of the data set.
     * @param h The kernel width.
     * @return The Gaussian kernel value at the given input.
     */
    private double kernelGaussian(double z, double h) {
        z /= getWidth() * h;
        return exp(-z * z);
    }

    /**
     * Calculates the Euclidean distance between two data points based on the given data and indices.
     *
     * @param data The data matrix.
     * @param z    The array of indices to consider in the calculation.
     * @param i    The index of the first data point.
     * @param j    The index of the second data point.
     * @return The Euclidean distance between the two data points.
     */
    private double distance(double[][] data, int[] z, int i, int j) {
        double sum = 0.0;

        for (int _z : z) {
            double d = (data[_z][i] - data[_z][j]) / 2.0;

            if (!Double.isNaN(d)) {
                sum += d * d;
            }
        }

        return sqrt(sum);
    }

    /**
     * Standardizes the given data array. No need to make a copy here.
     *
     * @param data The data array to be standardized.
     * @return The standardized data array.
     */
    private double[] standardize(double[] data) {
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

    /**
     * Returns a set of indices for data points that are close to the given data point <code>i</code> based on the
     * specified sample size and neighborhood information.
     *
     * @param _data         The data matrix.
     * @param _z            The indices of the variables used to calculate the distance.
     * @param i             The index of the data point for which close indices are calculated.
     * @param sampleSize    The desired sample size of close indices.
     * @param reverseLookup A list of maps containing the reverse lookup information for the variables used.
     * @param sortedIndices A list of lists containing the sorted indices information for the variables used.
     * @return A set of indices for data points that are close to the given data point i.
     */
    private Set<Integer> getCloseZs(double[][] _data, int[] _z, int i, int sampleSize,
                                    List<Map<Integer, Integer>> reverseLookup,
                                    List<List<Integer>> sortedIndices) {
        Set<Integer> js = new HashSet<>();

        if (sampleSize > _data[0].length) sampleSize = (int) ceil(0.8 * _data.length);
        if (_z.length == 0) return new HashSet<>();

        int radius = 0;

        while (true) {
            for (int z1 : _z) {
                int q = reverseLookup.get(z1).get(i);

                if (q - radius >= 0 && q - radius < _data[z1 + 1].length) {
                    int r2 = sortedIndices.get(z1).get(q - radius);
                    js.add(r2);
                }

                if (q + radius >= 0 && q + radius < _data[z1 + 1].length) {
                    int r2 = sortedIndices.get(z1).get(q + radius);
                    js.add(r2);
                }
            }

            if (js.size() >= sampleSize) return js;

            radius++;
        }
    }

    /**
     * Retrieves the list of row indices in the dataSet that have no NaN values for all variables in allVars.
     *
     * @param dataSet   The DataSet containing the data values.
     * @param allVars   The list of variables to check for NaN values.
     * @param nodesHash The map containing the mapping of nodes to their corresponding column indices in the dataSet.
     * @return The list of row indices that have no NaN values for all variables in allVars.
     */
    private List<Integer> getRows(DataSet dataSet, List<Node> allVars, Map<Node, Integer> nodesHash) {
        List<Integer> rows = new ArrayList<>();

        K:
        for (int k = 0; k < dataSet.getNumRows(); k++) {
            for (Node node : allVars) {
                if (Double.isNaN(dataSet.getDouble(k, nodesHash.get(node)))) continue K;
            }

            rows.add(k);
        }

        return rows;
    }

    /**
     * Gives a choice of kernels to use for the independence judgments for conditional correlation independence.
     *
     * @see ConditionalCorrelationIndependence
     */
    public enum Kernel {

        /**
         * The Epinechnikov kernel.
         */
        Epinechnikov,

        /**
         * The Gaussian kernel.
         */
        Gaussian
    }

    /**
     * Gives a choice of basis functions to use for judgments of independence for conditional correlation independence.
     *
     * @see ConditionalCorrelationIndependence
     */
    public enum Basis {

        /**
         * Polynomial basis.
         */
        Polynomial,

        /**
         * Cosine basis.
         */
        Cosine
    }
}






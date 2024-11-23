/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.*;

import static edu.cmu.tetrad.util.StatUtils.correlation;
import static edu.cmu.tetrad.util.StatUtils.getZForAlpha;
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
     * The data matrix representing the correlation data implied by the given data set. It is used in the independence
     * test calculations.
     */
    private final double[][] __data;
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
     * Basis
     */
    private Basis basis = Basis.Polynomial;

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation data implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet A data set containing only continuous columns.
     */
    public ConditionalCorrelationIndependence(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException();
        this.dataSet = DataTransforms.center(dataSet);

        __data = this.dataSet.getDoubleData().transpose().toArray();

        if (dataSet.getNumColumns() < 2) {
            throw new IllegalArgumentException("Data must have at least two columns");
        }

        this.variables = dataSet.getVariables();

        this.nodesHash = new HashMap<>();
        for (int i = 0; i < this.variables.size(); i++) {
            this.nodesHash.put(this.variables.get(i), i);
        }
    }

    /**
     * Computes the Gaussian kernel value between two vectors given a specific bandwidth.
     *
     * @param zi        The first input vector.
     * @param zj        The second input vector.
     * @param bandwidth The bandwidth parameter for the Gaussian kernel.
     * @return The computed Gaussian kernel value.
     */
    private static double gaussianKernel(double[] zi, double[] zj, double bandwidth) {
        double squaredDistance = 0.0;
        for (int k = 0; k < zi.length; k++) {
            double diff = zi[k] - zj[k];
            squaredDistance += diff * diff;
        }
        return Math.exp(-squaredDistance / (2 * bandwidth * bandwidth));
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

            if (rows.isEmpty()) return Double.NaN;

            double[] rx = residuals(x, z);
            double[] ry = residuals(y, z);

            // rx _||_ ry ?
            double score = independent(rx, ry, nodesHash.get(x), nodesHash.get(y));
            this.score = score;

            return score;
        } catch (Exception e) {
            TetradLogger.getInstance().log(e.getMessage());
            return Double.NaN;
        }
    }

    /**
     * Computes the residuals of a kernel regression for the given data points.
     *
     * @param x         The array of response variable values.
     * @param z         The array of predictor variable vectors.
     * @param bandwidth The bandwidth parameter for the Gaussian kernel.
     * @return An array of residuals from the kernel regression.
     */
    private double[] kernelRegressionResiduals(double[] x, double[][] z, double bandwidth) {
        int n = x.length;
        double[] residuals = new double[n];

        for (int i = 0; i < n; i++) {
            double weightSum = 0.0;
            double weightedXSum = 0.0;

            for (int j = 0; j < n; j++) {
                double kernel = gaussianKernel(z[i], z[j], bandwidth);
                weightSum += kernel;
                weightedXSum += kernel * x[j];
            }

            double fittedValue = weightedXSum / weightSum;
            residuals[i] = x[i] - fittedValue;
        }

        return residuals;
    }

    /**
     * Computes the residuals from a kernel regression of the response variable on the predictor variables.
     *
     * @param x The response variable represented as a Node.
     * @param z A list of predictor variables represented as Nodes.
     * @return An array of residuals from the kernel regression.
     */
    private double[] residuals(Node x, List<Node> z) {
        double[] _x = __data[nodesHash.get(x)];

        double[][] _z = new double[z.size()][];
        for (int i = 0; i < z.size(); i++) {
            _z[i] = __data[nodesHash.get(z.get(i))];
        }

        // Transpose _z
        double[][] zt = new double[_x.length][z.size()];
        for (int i = 0; i < z.size(); i++) {
            for (int j = 0; j < _z[i].length; j++) {
                zt[j][i] = _z[i][j];
            }
        }

        return kernelRegressionResiduals(_x, zt, width);
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
     * Determines the maximum absolute value of the nonparametric Fisher Z test statistic between transformed versions
     * of the given arrays `x` and `y` across different functions.
     *
     * @param x      The first array of input values.
     * @param y      The second array of input values.
     * @param indexX The index of the variable from array `x` to be used in the calculation.
     * @param indexY The index of the variable from array `y` to be used in the calculation.
     * @return The maximum absolute value of the nonparametric Fisher Z test statistic.
     */
    private double independent(double[] x, double[] y, int indexX, int indexY) {
        double[] _x = new double[x.length];
        double[] _y = new double[y.length];

        double maxZ = 0.0;

        for (int m = 0; m <= this.numFunctions; m++) {
            for (int n = 0; n <= this.numFunctions; n++) {
                for (int i = 0; i < x.length; i++) {
                    _x[i] = function(m, x[i]);
                    _y[i] = function(n, y[i]);
                }

                double z = abs(nonparametricFisherZ(_x, _y, indexX, indexY));

                if (Double.isNaN(z)) continue;

                // Maximize dependency
                if (z > maxZ) {
                    maxZ = z;
                }
            }
        }

        return maxZ;
    }

    /**
     * Calculates the nonparametric Fisher Z test for the given arrays.
     *
     * @param _x An array of double values.
     * @param _y An array of double values.
     * @return The nonparametric Fisher Z test statistic.
     */
    private double nonparametricFisherZ(double[] _x, double[] _y, int indexX, int indexY) {

        // Testing the hypothesis that _x and _y are uncorrelated and assuming that 4th moments of _x and _y
        // are finite and that the sample is large.
        double[] x = __data[indexX];
        double[] y = __data[indexY];

        double r = correlation(_x, _y); // correlation

        // Non-parametric Fisher Z test.
        double z = 0.5 * sqrt(_x.length) * (log(1.0 + r) - log(1.0 - r));

        System.out.println("|_x| = " + _x.length + " |x| = " + x.length + " m2x = " + m2(x) + " m2y = " + m2(y) +
                           " sqrt(moment22) = " + (sqrt(m22(x, y))));

        return z / (sqrt(m22(x, y)));
    }

    /**
     * Calculates the moment22 value for the given arrays x and y.
     *
     * @param x An array of double values.
     * @param y An array of double values.
     * @return The moment22 value.
     */
    private double m22(double[] x, double[] y) {
        double sum = 0.0;

        for (int i = 0; i < x.length; i++) {
            sum += x[i] * x[i] * y[i] * y[i];
        }

        return sum / x.length;
    }

    /**
     * Calculates the moment2 value for the given arrays x and y.
     *
     * @param x The array of data
     * @return The m2 statistic.
     */
    private double m2(double[] x) {
        double sum = 0.0;

        for (double v : x) {
            sum += v * v;
        }

        return sum / x.length;
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






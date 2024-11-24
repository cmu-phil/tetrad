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
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Vector;
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
    private final Matrix data;
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
     * Bandwidth.
     */
    private double bandwidth = 1.0;

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation data implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet A data set containing only continuous columns.
     */
    public ConditionalCorrelationIndependence(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException();
        this.dataSet = DataTransforms.standardizeData(dataSet);

        data = this.dataSet.getDoubleData();

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
     * @param bandwidth The bandwidth parameter for the Gaussian kernel.
     * @return The computed Gaussian kernel value.
     */
    private static double gaussianKernel(Matrix z, int i, int j, double bandwidth) {
        double squaredDistance = 0.0;
        for (int k = 0; k < z.getNumRows(); k++) {
            double diff = z.get(k, i) - z.get(k, j);// zi[k] - zj[k];
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

            Vector rx = residuals(x, z);
            Vector ry = residuals(y, z);

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
    private Vector kernelRegressionResiduals(Vector x, Matrix z, double bandwidth) {
        int n = x.size();
        Vector residuals = new Vector(n);

        for (int i = 0; i < n; i++) {
            double weightSum = 0.0;
            double weightedXSum = 0.0;

            for (int j = 0; j < n; j++) {
                double kernel = gaussianKernel(z, i, j, bandwidth);
                weightSum += kernel;
                weightedXSum += kernel * x.get(j);
            }

            double fittedValue = weightedXSum / weightSum;
            residuals.set(i, x.get(i) - fittedValue);
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
    private Vector residuals(Node x, List<Node> z) {
        Vector _x = data.getColumn(nodesHash.get(x));

        Matrix _z = new Matrix(data.getNumRows(), z.size());

        for (int i = 0; i < z.size(); i++) {
            _z.assignColumn(i, data.getColumn(nodesHash.get(z.get(i))));
        }

        return kernelRegressionResiduals(_x, _z.transpose(), bandwidth);
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
     * Returns the kernel width.
     *
     * @return This width.
     */
    public double getBandwidth() {
        return this.bandwidth;
    }

    /**
     * Sets the kernel width.
     *
     * @param bandwidth This width.
     */
    public void setBandwidth(double bandwidth) {
        this.bandwidth = bandwidth;
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
    private double independent(Vector x, Vector y, int indexX, int indexY) {
        Vector _x = new Vector(x.size());
        Vector _y = new Vector(y.size());

        double maxZ = 0.0;

        for (int m = 0; m <= this.numFunctions; m++) {
            for (int n = 0; n <= this.numFunctions; n++) {
                for (int i = 0; i < x.size(); i++) {
                    _x.set(i, function(m, x.get(i)));
                    _y.set(i, function(n, y.get(i)));
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
    private double nonparametricFisherZ(Vector _x, Vector _y, int indexX, int indexY) {

        // Testing the hypothesis that _x and _y are uncorrelated and assuming that 4th moments of _x and _y
        // are finite and that the sample is large.
        Vector x = data.getColumn(indexX);
        Vector y = data.getColumn(indexY);

        double r = correlation(_x, _y);

        // Non-parametric Fisher Z test.
        double z = 0.5 * sqrt(_x.size()) * (log(1.0 + r) - log(1.0 - r));

        return z / (sqrt(m22(_x, _y)));
    }

    /**
     * Calculates the moment22 value for the given arrays x and y.
     *
     * @param x An array of double values.
     * @param y An array of double values.
     * @return The moment22 value.
     */
    private double m22(Vector x, Vector y) {
        double sum = 0.0;

        for (int i = 0; i < x.size(); i++) {
            sum += x.get(i) * x.get(i) * y.get(i) * y.get(i);
        }

        return sum / x.size();
    }

    /**
     * Calculates a basis function value based on the given index and input value.
     *
     * @param index The index of the basis function.
     * @param x     The input value.
     * @return The basis function value.
     */
    private double function(int index, double x) {
        double g = 1.0;

        for (int i = 1; i <= index; i++) {
            g *= x;
        }

        if (abs(g) == Double.POSITIVE_INFINITY) g = Double.NaN;

        return g;
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
}






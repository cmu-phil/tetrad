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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

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
 * Daudin, J. J. (1980). Partial association measures and an application to qualitative regression. Biometrika, 67(3),
 * 581-590.
 * <p>
 * Updated 2024-11-24 josephramsey
 *
 * @author josephramsey
 */
public final class ConditionalCorrelationIndependence {
    private final Map<Node, Integer> nodesHash;
    private final Matrix data;
    private double score;
    private int numFunctions = 10;
    private double cutoff;
    private double bandwidth = 1.0;

    /**
     * Initializes a new instance of the ConditionalCorrelationIndependence class using the provided DataSet.
     *
     * @param dataSet The dataset to be used for the analysis. This dataset must not be null and will be standardized.
     * @throws NullPointerException if the provided dataset is null.
     */
    public ConditionalCorrelationIndependence(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException();
        dataSet = DataTransforms.standardizeData(dataSet);

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            if (dataSet.getVariables().get(j) instanceof ContinuousVariable) {
                scale(dataSet, j);
            }
        }

        data = dataSet.getDoubleData();

        this.nodesHash = new ConcurrentHashMap<>();

        for (int i = 0; i < dataSet.getVariables().size(); i++) {
            this.nodesHash.put(dataSet.getVariables().get(i), i);
        }
    }

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
     * Computes the Gaussian kernel value between two rows in a given matrix.
     *
     * @param z         The matrix containing the data points.
     * @param i         The index of the first row.
     * @param j         The index of the second row.
     * @param bandwidth The bandwidth parameter for the Gaussian kernel.
     * @return The computed Gaussian kernel value between the two rows.
     */
    private static double gaussianKernel(Matrix z, int i, int j, double bandwidth) {
        double squaredDistance = IntStream.range(0, z.getNumColumns())
                .mapToDouble(k -> {
                    double diff = z.get(i, k) - z.get(j, k);
                    return diff * diff;
                }).sum();
        return Math.exp(-squaredDistance / (2 * bandwidth * bandwidth));
    }

    /**
     * Determines whether two given nodes are independent given a set of conditioning nodes, and calculates a score.
     *
     * @param x  The first node.
     * @param y  The second node.
     * @param _z The set of conditioning nodes.
     * @return The score representing the level of independence between nodes x and y given the conditioning set _z.
     * Returns Double.NaN if the score cannot be computed or is not a number.
     */
    public double isIndependent(Node x, Node y, Set<Node> _z) {
        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        List<Node> allNodes = new ArrayList<>(z);
        allNodes.add(x);
        allNodes.add(y);

        List<Integer> rows = getRows(this.data, allNodes, nodesHash);

        if (rows.isEmpty()) {
            return Double.NaN;
        }

        Vector rx = residuals(x, z);
        Vector ry = residuals(y, z);

        double score = independent(rx, ry);
        this.score = score;

        if (Double.isNaN(score)) {
            return Double.NaN;
        }

        return score;
    }

    /**
     * Calculates the residuals of a kernel regression using the Gaussian kernel.
     *
     * @param x         The vector containing the response variable data points.
     * @param z         The matrix containing the predictor variable data points.
     * @param bandwidth The bandwidth parameter for the Gaussian kernel.
     * @return A vector containing the residuals from the kernel regression for each data point.
     */
    private Vector kernelRegressionResiduals(Vector x, Matrix z, double bandwidth) {
        int n = x.size();
        Vector residuals = new Vector(n);

        IntStream.range(0, n).parallel().forEach(i -> {
            double weightSum = 0.0;
            double weightedXSum = 0.0;

            for (int j = 0; j < n; j++) {
                double kernel = gaussianKernel(z, i, j, bandwidth);
                weightSum += kernel;
                weightedXSum += kernel * x.get(j);
            }

            double fittedValue = weightedXSum / weightSum;
            residuals.set(i, x.get(i) - fittedValue);
        });

        return residuals;
    }

    /**
     * Computes the residuals of a node x given a list of conditioning nodes z using kernel regression.
     *
     * @param x The node for which residuals need to be computed.
     * @param z The list of conditioning nodes.
     * @return A vector containing the residuals of node x after accounting for the conditioning nodes z.
     */
    private Vector residuals(Node x, List<Node> z) {
        Vector _x = data.getColumn(nodesHash.get(x));

        Matrix _z = new Matrix(data.getNumRows(), z.size());

        for (int i = 0; i < z.size(); i++) {
            _z.assignColumn(i, data.getColumn(nodesHash.get(z.get(i))));
        }

        return kernelRegressionResiduals(_x, _z, bandwidth);
    }

    /**
     * Sets the number of functions utilized in the ConditionalCorrelationIndependence analysis.
     *
     * @param numFunctions the number of functions to set. This value must be a positive integer.
     */
    public void setNumFunctions(int numFunctions) {
        this.numFunctions = numFunctions;
    }

    /**
     * Retrieves the current bandwidth parameter used in the ConditionalCorrelationIndependence analysis.
     *
     * @return The current value of the bandwidth parameter.
     */
    public double getBandwidth() {
        return this.bandwidth;
    }

    /**
     * Sets the bandwidth parameter for the ConditionalCorrelationIndependence analysis.
     *
     * @param bandwidth The bandwidth parameter to be set. This value is used in the Gaussian kernel calculation for
     *                  various methods in the analysis.
     */
    public void setBandwidth(double bandwidth) {
        this.bandwidth = bandwidth;
    }

    /**
     * Calculates the p-value for a given score using the cumulative distribution function (CDF) of a standard normal
     * distribution.
     *
     * @param score The score for which the p-value needs to be calculated. This score is typically a test statistic
     *              resulting from some statistical test.
     * @return The p-value corresponding to the given score, indicating the probability of obtaining a value at least as
     * extreme as the observed score under the null hypothesis.
     */
    public double getPValue(double score) {
        return 2.0 * (1.0 - new NormalDistribution(0, 1).cumulativeProbability(abs(score)));
    }

    /**
     * Retrieves the score modified by applying an absolute value and subtracting a cutoff value.
     *
     * @return The modified score which is calculated as the absolute value of the current score minus the cutoff value.
     */
    public double getScore() {
        return abs(this.score) - this.cutoff;
    }

    /**
     * Sets the alpha value and updates the cutoff accordingly.
     *
     * @param alpha The alpha value to be set. This is used to determine the cutoff for statistical testing and
     *              typically represents the significance level.
     */
    public void setAlpha(double alpha) {
        this.cutoff = getZForAlpha(alpha);
    }

    /**
     * Computes the maximum absolute non-parametric Fisher's Z value between two vectors, x and y, based on a range of
     * functions applied to the elements of these vectors.
     *
     * @param x The first vector containing data points.
     * @param y The second vector containing data points.
     * @return The maximum absolute value of the non-parametric Fisher's Z calculated between the transformed versions
     * of vectors x and y.
     */
    private double independent(Vector x, Vector y) {
        Vector _x = new Vector(x.size());
        Vector _y = new Vector(y.size());

        double max = 0.0;

        for (int m = 0; m <= this.numFunctions; m++) {
            for (int n = 0; n <= this.numFunctions; n++) {
                for (int i = 0; i < x.size(); i++) {
                    double fx = function(m, x.get(i));
                    double fy = function(n, y.get(i));

                    if (Double.isInfinite(fx) || Double.isInfinite(fy)
                        || Double.isNaN(fx) || Double.isNaN(fy)) {
                        continue;
                    }

                    _x.set(i, fx);
                    _y.set(i, fy);
                }

                double z = abs(nonparametricFisherZ(_x, _y));

                if (!Double.isNaN(z)) {
                    if (z > max) {
                        max = z;
                    }
                }
            }
        }

        return max;
    }

    /**
     * Computes the non-parametric Fisher's Z value for two vectors.
     *
     * @param _x The first vector containing data points.
     * @param _y The second vector containing data points.
     * @return The non-parametric Fisher's Z value between the two vectors.
     */
    private double nonparametricFisherZ(Vector _x, Vector _y) {
        double r = correlation(_x, _y);
        double z = 0.5 * sqrt(_x.size() - 3) * (log(1.0 + r) - log(1.0 - r));
        return z / (sqrt(m22(_x, _y)));
    }

    /**
     * Computes a normalized sum of the element-wise product of the squares of two vectors.
     *
     * @param x The first vector containing data points.
     * @param y The second vector containing data points.
     * @return The normalized sum of the element-wise product of the squares of the two vectors.
     */
    private double m22(Vector x, Vector y) {
        return IntStream.range(0, x.size())
                       .mapToDouble(i -> x.get(i) * x.get(i) * y.get(i) * y.get(i))
                       .sum() / x.size();
    }

    /**
     * Performs a calculation that involves repeatedly multiplying an initial value of `1.0` by the product of `0.95`
     * and a given parameter `x`, iterating `index` times.
     *
     * @param index The number of iterations to perform the multiplication.
     * @param x     The value to be multiplied by `0.95` in each iteration.
     * @return The result of the iterative multiplication.
     */
    private double function(int index, double x) {
        double g = 1.0;

        for (int i = 1; i <= index; i++) {
            g *= 0.95 * x;
        }

        return g;
    }

    /**
     * Retrieves the list of row indices from a given matrix that do not contain NaN values for any specified nodes.
     *
     * @param data      The matrix containing the data.
     * @param allVars   The list of nodes to be checked.
     * @param nodesHash A map associating each node with its respective column index in the matrix.
     * @return A list of row indices where none of the specified nodes' values are NaN in the matrix.
     */
    private List<Integer> getRows(Matrix data, List<Node> allVars, Map<Node, Integer> nodesHash) {
        List<Integer> rows = new ArrayList<>();

        for (int k = 0; k < data.getNumRows(); k++) {
            boolean hasNaN = false;
            for (Node node : allVars) {
                if (Double.isNaN(data.get(k, nodesHash.get(node)))) {
                    hasNaN = true;
                    break;
                }
            }
            if (!hasNaN) {
                rows.add(k);
            }
        }

        return rows;
    }
}

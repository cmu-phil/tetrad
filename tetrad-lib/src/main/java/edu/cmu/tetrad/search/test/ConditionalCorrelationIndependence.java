package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.util.FastMath;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static edu.cmu.tetrad.util.StatUtils.*;
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
public final class ConditionalCorrelationIndependence implements RowsSettable {
    /**
     * The map associating each node with its respective column index in the data matrix.
     */
    private final Map<Node, Integer> nodesHash;
    /**
     * The data matrix used for the analysis.
     */
    private final Matrix data;
    /**
     * The bandwidth adjustment factor.
     */
    private double scalingFactor = 2;
    /**
     * The number of functions used in the analysis.
     */
    private int numFunctions = 10;
    /**
     * The list of row indices to be used for the analysis. If no rows are set, all rows will be used.
     */
    private List<Integer> rows;
    /**
     * The significance level of the independence tests.
     */
    private double alpha = 0.05;

    /**
     * Initializes a new instance of the ConditionalCorrelationIndependence class using the provided DataSet.
     *
     * @param dataSet The dataset to be used for the analysis. This dataset must not be null and will be standardized.
     * @throws NullPointerException if the provided dataset is null.
     */
    public ConditionalCorrelationIndependence(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException();
        dataSet = DataTransforms.standardizeData(dataSet);
        this.data = dataSet.getDoubleData();
        this.nodesHash = new ConcurrentHashMap<>();

        for (var i = 0; i < dataSet.getVariables().size(); i++) {
            this.nodesHash.put(dataSet.getVariables().get(i), i);
        }
    }

    /**
     * Calculates the optimal bandwidth for node x using the Median Absolute Deviation (MAD) method.
     *
     * @param x The data for the column.
     * @return The optimal bandwidth for node x.
     */
    private static double optimalBandwidth(Vector x) {
        var _x = new Vector(standardizeData(x.toArray()));
        var N = _x.size();
        var g = new Vector(N);
        var central = median(_x.toArray());
        for (var j = 0; j < N; j++) g.set(j, abs(_x.get(j) - central));
        var mad = median(g.toArray());
        var sigmaRobust = 1.4826 * mad;
        return 1.06 * sigmaRobust * FastMath.pow(N, -0.20);
    }

    /**
     * Computes the Gaussian kernel value between two rows in a given matrix.
     *
     * @param z The matrix containing the data points.
     * @param i The index of the first row.
     * @param j The index of the second row.
     * @return The computed Gaussian kernel value between the two rows.
     */
    private static double gaussianKernel(Matrix z, int i, int j, double h, double scalingFactor) {
        double _h = h * scalingFactor;

        Vector difference = z.getRow(i).minus(z.getRow(j));
        double squaredDistance = difference.dotProduct(difference);

        return FastMath.exp(-squaredDistance / (2 * _h * _h));
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
        var z = new ArrayList<>(_z);
        Collections.sort(z);

        var allNodes = new ArrayList<>(z);
        allNodes.add(x);
        allNodes.add(y);

        var rows = getRows(this.data, allNodes, nodesHash);

        if (rows.isEmpty()) {
            return Double.NaN;
        }

        var rx = residuals(x, z, rows);
        var ry = residuals(y, z, rows);

        var score = independent(rx, ry);

        if (Double.isNaN(score)) {
            return Double.NaN;
        }

        return score;
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
        return 2.0 * (new NormalDistribution(0, 1).cumulativeProbability(-abs(score)));
    }

    /**
     * Retrieves the list of row indices currently set for the analysis. If no rows are set, return a list of all row
     * indices.
     *
     * @return A list of row indices.
     */
    @Override
    public List<Integer> getRows() {
        if (this.rows == null) {
            var allRows = new ArrayList<Integer>();

            for (var i = 0; i < this.data.getNumRows(); i++) {
                allRows.add(i);
            }

            return allRows;
        } else {
            return this.rows;
        }
    }

    /**
     * Sets the list of row indices
     *
     * @param rows The list of row indices to set.
     */
    @Override
    public void setRows(List<Integer> rows) {
        this.rows = rows;
    }

    /**
     * Retrieves the number of functions used in the ConditionalCorrelationIndependence analysis.
     *
     * @return The number of functions used in the analysis.
     */
    public int getNumFunctions() {
        return numFunctions;
    }

    /**
     * Sets the number of functions used in the ConditionalCorrelationIndependence analysis.
     *
     * @param numFunctions the number of functions to set. This value must be a positive integer.
     */
    public void setNumFunctions(int numFunctions) {
        this.numFunctions = numFunctions;
    }

    /**
     * Retrieves the kernel scaling factor.
     *
     * @return The scaling factor used in the analysis.
     */
    public double getScalingFactor() {
        return this.scalingFactor;
    }

    /**
     * Sets the bandwidth adjustment value for the ConditionalCorrelationIndependence analysis.
     * <p>
     * Default is 2.
     *
     * @param scalingFactor The new bandwidth adjustment factor to be used. This value adjusts the bandwidth
     *                            calculation for conditional independence tests and impacts the sensitivity of the
     *                            kernel-based analysis.
     */
    public void setScalingFactor(double scalingFactor) {
        this.scalingFactor = scalingFactor;
    }

    /**
     * Calculates the residuals of a nonlinear regression using a Gaussian kernel.
     *
     * @param x The vector containing the response variable data points.
     * @param z The matrix containing the predictor variable data points.
     * @return A vector containing the residuals from the kernel regression for each data point.
     */
    private Vector kernelRegressionResiduals(Vector x, Matrix z) {
        var n = x.size();
        var residuals = new Vector(n);

        var h = optimalBandwidth(x);

        for (var i = 0; i < n; i++) {
            var weightSum = 0.0;
            var weightedXSum = 0.0;

            for (var j = 0; j < n; j++) {
                var kernel = gaussianKernel(z, i, j, h, scalingFactor);
                weightSum += kernel;
                weightedXSum += kernel * x.get(j);
            }

            if (weightSum == 0.0) {
                residuals.set(i, 0.0);
                continue;
            }

            var fittedValue = weightedXSum / weightSum;
            residuals.set(i, x.get(i) - fittedValue);
        }

        return residuals;
    }

    /**
     * Computes the residuals of a node x given a list of conditioning nodes z using kernel regression.
     *
     * @param x The node for which residuals need to be computed.
     * @param z The list of conditioning nodes.
     * @return A vector containing the residuals of node x after accounting for the conditioning nodes z.
     */
    private Vector residuals(Node x, List<Node> z, List<Integer> rows) {
        var _x = data.getColumn(nodesHash.get(x));

        // Restrict x and z to the given rows and restrict z to the columns in z.
        var _rows = rows.stream().mapToInt(i -> i).toArray();
        var _cols = z.stream().mapToInt(nodesHash::get).toArray();

        var _z = data.getSelection(_rows, _cols);
        var __x = _x.getSelection(_rows);
        return kernelRegressionResiduals(__x, _z);
    }

    /**
     * Computes the maximum absolute non-parametric Fisher's Z value between two vectors, rx and ry, based on a range of
     * functions applied to the elements of these vectors.
     *
     * @param rx The first vector containing data points.
     * @param ry The second vector containing data points.
     * @return The maximum absolute value of the non-parametric Fisher's Z calculated between the transformed versions
     * of vectors rx and ry.
     */
    private double independent(Vector rx, Vector ry) {
        var rxBasis = new HashMap<Integer, Vector>();
        var ryBasis = new HashMap<Integer, Vector>();

        // Compute the orthogonal functions for rx and ry.
        initializeResidualBasisVectors(rx, rxBasis);
        initializeResidualBasisVectors(ry, ryBasis);

        double sum = 0.0;

        for (var m = 1; m <= this.numFunctions; m++) {
            for (var n = 1; n <= this.numFunctions; n++) {
                sum += (optimalBandwidth(rxBasis.get(m)) * optimalBandwidth(ryBasis.get(n)));
            }
        }

        var avg = sum / this.numFunctions * this.numFunctions;

        // Compute the maximum absolute non-parametric Fisher's Z value between the transformed vectors.
        // (I.e., we want to maximize dependence.)
        var max = 0.0;
        var min = Double.POSITIVE_INFINITY;

        for (var m = 1; m <= this.numFunctions; m++) {
            for (var n = 1; n <= this.numFunctions; n++) {
                if (rxBasis.containsKey(m) && ryBasis.containsKey(n)) {
                    double parameters = data.getNumRows() - 2 * this.numFunctions;
                    var z = abs(nonparametricFisherZ(rxBasis.get(m), ryBasis.get(n), parameters));

                    if (Double.isFinite(z)) {
                        if (z >= max) {
                            max = z;

                            if (getPValue(z) < min && getPValue(max) > this.alpha) {
                                min = z;
                            }
                        }
                    }
                }
            }
        }

        return Double.isInfinite(min) ? max : min;
    }

    /**
     * Initializes the residuals for a given vector by computing the orthogonal functions for each data point.
     *
     * @param rx The vector containing data points.
     * @param x  A map associating each orthogonal function with its respective vector.
     */
    private void initializeResidualBasisVectors(Vector rx, Map<Integer, Vector> x) {
        M:
        IntStream.range(1, this.numFunctions + 1).parallel().forEach(m -> {
            var _x = new Vector(rx.size());

            IntStream.range(0, rx.size()).forEach(i -> {
                var fx = orthogonalFunctionValue(1, m, rx.get(i));

                if (!Double.isInfinite(fx) && !Double.isNaN(fx)) {
                    _x.set(i, fx);
                }
            });

            synchronized (x) {
                x.put(m, _x);
            }
        });
    }

    /**
     * Computes the non-parametric Fisher's Z value for two vectors.
     *
     * @param _x The first vector containing data points.
     * @param _y The second vector containing data points.
     * @return The non-parametric Fisher's Z value between the two vectors.
     */
    private double nonparametricFisherZ(Vector _x, Vector _y, double parameters) {
        var r = correlation(_x, _y);
        var z = 0.5 * sqrt(parameters) * (log(1.0 + r) - log(1.0 - r));
        return z / (scalingFactor * (sqrt(m22(_x, _y))));
    }

    /**
     * Computes a normalized sum for the element-wise product for the squares of two vectors.
     *
     * @param x The first vector containing data points.
     * @param y The second vector containing data points.
     * @return The normalized sum of the element-wise product for the squares of the two vectors.
     */
    private double m22(Vector x, Vector y) {
        var m22 = 0.0;

        for (var i = 0; i < x.size(); i++) {
            m22 += x.get(i) * x.get(i) * y.get(i) * y.get(i);
        }

        return m22 / x.size();
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
        var _rows = getRows();
        var rows = new ArrayList<Integer>();

        for (var k : _rows) {
            var hasNaN = false;
            for (var node : allVars) {
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

    /**
     * Performs a permutation test to empirically determine the distribution of p-values under the null hypothesis.
     *
     * @param x               The first node.
     * @param y               The second node.
     * @param z               The set of conditioning nodes.
     * @param numPermutations The number of permutations to perform.
     * @return The mean p-value for the given number of permutations.
     */
    public double permutationTest(Node x, Node y, Set<Node> z, int numPermutations) {
        double[] pValues = new double[numPermutations];
        var originalRows = rows;
        List<Integer> rows = getRows();

        IntStream.range(0, numPermutations).parallel().forEach(i -> {
            var shuffledRows = new ArrayList<>(rows);
            Collections.shuffle(shuffledRows);
            this.rows = shuffledRows;
            double permutedScore = isIndependent(x, y, z);
            pValues[i] = getPValue(permutedScore);
        });

        this.rows = originalRows;
        return Arrays.stream(pValues).average().orElse(Double.NaN);
    }

    /**
     * Sets the significance level of the independence tests.
     *
     * @param alpha The new significance level to be used. This value must be in the range [0, 1].
     */
    public void setAlpha(double alpha) {
        if (!(alpha >= 0 && alpha <= 1)) {
            throw new IllegalArgumentException("Alpha must be in [0, 1]");
        }

        this.alpha = alpha;
    }
}
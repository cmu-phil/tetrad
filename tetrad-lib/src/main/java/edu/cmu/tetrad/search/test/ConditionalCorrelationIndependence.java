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
    private final Map<Node, Integer> nodesHash;
    private final Matrix data;
    private double score;
    private int numFunctions = 10;
    private List<Integer> rows;
    private double alpha = 0.05;
    private static double bandwidthAdjustment = 1.5;

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

        for (int i = 0; i < dataSet.getVariables().size(); i++) {
            this.nodesHash.put(dataSet.getVariables().get(i), i);
        }
    }

    public void setBandwidthAdjustment(double bandwidthAdjustment) {
        ConditionalCorrelationIndependence.bandwidthAdjustment = bandwidthAdjustment;
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

        Vector rx = residuals(x, z, rows);
        Vector ry = residuals(y, z, rows);

        double score = independent(rx, ry);

        this.score = score;

        if (Double.isNaN(score)) {
            return Double.NaN;
        }

        return score;
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
        return abs(this.score) - this.alpha;
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
            List<Integer> allRows = new ArrayList<>();

            for (int i = 0; i < this.data.getNumRows(); i++) {
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
     * Sets the alpha value; this is only used to calculate scores.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Computes the Gaussian kernel value between two rows in a given matrix.
     *
     * @param z The matrix containing the data points.
     * @param i The index of the first row.
     * @param j The index of the second row.
     * @return The computed Gaussian kernel value between the two rows.
     */
    private static double gaussianKernel(Matrix z, int i, int j, double h) {
        h *= bandwidthAdjustment;

        double squaredDistance = 0.0;
        int bound = z.getNumColumns();

        for (int k1 = 0; k1 < bound; k1++) {
            double diff = z.get(i, k1) - z.get(j, k1);
            double v = diff * diff;
            squaredDistance += v;
        }

        return Math.exp(-squaredDistance / (2 * h * h));
    }

    /**
     * Calculates the optimal bandwidth for node x using the Median Absolute Deviation (MAD) method.
     *
     * @param x The data for the column.
     * @return The optimal bandwidth for node x.
     */
    private static double optimalBandwidth(Vector x) {
        x = new Vector(standardizeData(x.toArray()));
        int N = x.size();
        Vector g = new Vector(N);
        double central = median(x.toArray());
        for (int j = 0; j < N; j++) g.set(j, abs(x.get(j) - central));
        double mad = median(g.toArray());
        double sigmaRobust = 1.4826 * mad;
        return 1.06 * sigmaRobust * FastMath.pow(N, -0.20);
    }

    /**
     * Calculates the residuals of a nonlinear regression using a Gaussian kernel.
     *
     * @param x          The vector containing the response variable data points.
     * @param z          The matrix containing the predictor variable data points.
     * @return A vector containing the residuals from the kernel regression for each data point.
     */
    private Vector kernelRegressionResiduals(Vector x, Matrix z) {
        int n = x.size();
        Vector residuals = new Vector(n);

        double h = optimalBandwidth(x);

        for (int i = 0; i < n; i++) {
            double weightSum = 0.0;
            double weightedXSum = 0.0;

            for (int j = 0; j < n; j++) {
                double kernel = gaussianKernel(z, i, j, h);
                weightSum += kernel;
                weightedXSum += kernel * x.get(j);
            }

            double fittedValue = weightedXSum / weightSum;
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
        Vector _x = data.getColumn(nodesHash.get(x));

        // Restrict x and z to the given rows and restrict z to the columns in z.
        int[] _rows = rows.stream().mapToInt(i -> i).toArray();
        int[] _cols = z.stream().mapToInt(nodesHash::get).toArray();

        Matrix _z = data.getSelection(_rows, _cols);
        Vector __x = _x.getSelection(_rows);
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
        Map<Integer, Vector> x = new HashMap<>();
        Map<Integer, Vector> y = new HashMap<>();

        // Compute the orthogonal functions for x and y.
        initializeResiduals(rx, x);
        initializeResiduals(ry, y);

        // Compute the maximum absolute non-parametric Fisher's Z value between the transformed vectors.
        // (I.e., we want to maximize dependence.)
        double max = 0.0;

        for (int m = 1; m <= this.numFunctions; m++) {
            for (int n = 1; n <= this.numFunctions; n++) {
                if (x.containsKey(m) && y.containsKey(n)) {
                    double z = abs(nonparametricFisherZ(x.get(m), y.get(n)));

                    if (!Double.isNaN(z)) {
                        if (z >= max) {
                            max = z;
                        }
                    }
                }
            }
        }

        return max;
    }

    /**
     * Initializes the residuals for a given vector by computing the orthogonal functions for each data point.
     *
     * @param rx The vector containing data points.
     * @param x  A map associating each orthogonal function with its respective vector.
     */
    private void initializeResiduals(Vector rx, Map<Integer, Vector> x) {
        M:
        for (int m = 1; m <= this.numFunctions; m++) {
            Vector _x = new Vector(rx.size());

            for (int i = 0; i < rx.size(); i++) {
                double fx = orthogonalFunctionValue(1, m, rx.get(i));

                if (Double.isInfinite(fx) || Double.isNaN(fx)) {
                    continue M;
                }

                _x.set(i, fx);
            }

            x.put(m, _x);
        }
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
        double z = 0.5 * sqrt(_x.size()) * (log(1.0 + r) - log(1.0 - r));
        return z / (bandwidthAdjustment * (sqrt(m22(_x, _y))));
    }

    /**
     * Computes a normalized sum for the element-wise product for the squares of two vectors.
     *
     * @param x The first vector containing data points.
     * @param y The second vector containing data points.
     * @return The normalized sum of the element-wise product for the squares of the two vectors.
     */
    private double m22(Vector x, Vector y) {
        double m22 = 0.0;

        for (int i = 0; i < x.size(); i++) {
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
        List<Integer> _rows = getRows();
        List<Integer> rows = new ArrayList<>();

        for (int k : _rows) {
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
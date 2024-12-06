package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.util.FastMath;
import org.ejml.simple.SimpleMatrix;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.IntStream;

import static edu.cmu.tetrad.util.StatUtils.median;
import static org.apache.commons.math3.util.FastMath.abs;
import static org.apache.commons.math3.util.FastMath.sqrt;

/**
 * Gives an implementation of the Kernel Independence Test (KCI) by Kun Zhang, which is a general test of conditional
 * independence. The reference is here:
 * <p>
 * Zhang, K., Peters, J., Janzing, D., and Schölkopf, B. (2012). Kernel-based conditional independence test and
 * application in causal discovery. arXiv preprint arXiv:1202.3775.
 * <p>
 * Please see that paper, especially Theorem 4 and Proposition 5.
 * <p>
 * Using optimal kernel bandwidths suggested by Bowman and Azzalini (1997):
 * <p>
 * Bowman, A. W., and Azzalini, A. (1997). Applied smoothing techniques for data analysis: the kernel approach with
 * S-Plus illustrations (Vol. 18). OUP Oxford.
 *
 * @author kunzhang
 * @author Vineet Raghu on 7/3/2016
 * @author josephramsey refactoring 7/4/2018, 12/6/2024
 * @version $Id: $Id
 */
public class Kci implements IndependenceTest {
    /**
     * The supplied data set, standardized
     */
    private final DataSet data;
    /**
     * Variables in data
     */
    private final List<Node> variables;
    /**
     * The bandwidth vector.
     */
    private final Vector h;
    /**
     * A normal distribution with 1 degree of freedom.
     */
    private final NormalDistribution normal = new NormalDistribution(0, 1);
    /**
     * Convenience map from nodes to their indices in the list of variables.
     */
    private final Map<Node, Integer> hash;
    /**
     * The identity matrix.
     */
    private final SimpleMatrix I;
    /**
     * The centering matrix.
     */
    private final SimpleMatrix H;
    /**
     * The sample size.
     */
    private final int N;
    /**
     * The alpha level of the test.
     */
    private double alpha;
    /**
     * True if the approximation algorithms should be used instead of Theorems 3 or 4.
     */
    private boolean approximate;
    /**
     * Eigenvalues greater than this time, the maximum will be kept.
     */
    private double threshold = 0.001;
    /**
     * Number of bootstraps for Theorem 4 and Proposition 5.
     */
    private int numBootstraps = 5000;
    /**
     * Azzalini optimal kernel widths will be multiplied by this.
     */
    private double widthMultiplier = 1.0;
    /**
     * Epsilon for Proposition 5.
     */
    private double epsilon = 0.001;
    /**
     * True if verbose output is enabled.
     */
    private boolean verbose = true;

    /**
     * Constructor.
     *
     * @param data  The dataset to analyze. Must be continuous.
     * @param alpha The alpha value of the test.
     */
    public Kci(DataSet data, double alpha) {
        this.data = DataTransforms.standardizeData(data);
        this.variables = data.getVariables();
        hash = getNodeIntegerMap();

        SimpleMatrix dataCols = new SimpleMatrix(this.data.getDoubleData().transpose().toArray());
        h = getH(dataCols);
        N = data.getNumRows();

        SimpleMatrix ones = getOnes(N);
        I = SimpleMatrix.identity(N);
        H = I.minus(ones.mult(ones.transpose()).scale(1.0 / N));

        this.alpha = alpha;
    }

    /**
     * Converts a SimpleMatrix to a 1D array.
     *
     * @param matrix The matrix to convert.
     * @return The 1D array.
     */
    public static double[] convertTo1DArray(SimpleMatrix matrix) {
        if (matrix.getNumCols() != 1) {
            throw new IllegalArgumentException("The matrix must have exactly one column:" + matrix.getNumCols());
        }

        // Get the number of rows
        int rows = matrix.getNumRows();

        // Create a 1D array to hold the values
        double[] result = new double[rows];

        // Extract values from the matrix
        for (int row = 0; row < rows; row++) {
            result[row] = matrix.get(row, 0); // Access each row in the single column
        }

        return result;
    }

    public static SimpleMatrix standardizeData(SimpleMatrix data) {
        SimpleMatrix data2 = data.createLike();

        double sum = 0.0;

        for (int j = 0; j < data.getNumCols(); j++) {


            for (int i = 0; i < data.getNumRows(); i++) {
                sum += data.get(i, j);
            }

            double mean = sum / data.getNumRows();

            for (int i = 0; i < data.getNumRows(); i++) {
                data2.set(i, j, data.get(i, j) - mean);
            }

            double norm = 0.0;

            for (int i = 0; i < data2.getNumRows(); i++) {
                double v = data2.get(i, j);
                norm += v * v;
            }

            norm = sqrt(norm / (data2.getNumRows() - 1));

            for (int i = 0; i < data2.getNumRows(); i++) {
                data2.set(i, j, data2.get(i, j) / norm);
            }


        }

        return data2;
    }

    private static @NotNull Map<Node, Integer> getHash(List<Node> allVars) {
        Map<Node, Integer> hash = new HashMap<>();
        for (int i = 0; i < allVars.size(); i++) hash.put(allVars.get(i), i);
        return hash;
    }

    private static @NotNull List<Node> getAllVars(Node x, Node y, Set<Node> z) {
        List<Node> allVars = new ArrayList<>();
        allVars.add(x);
        allVars.add(y);
        allVars.addAll(z);
        return allVars;
    }

    /**
     * Calculates the optimal bandwidth for node x using the Median Absolute Deviation (MAD) method suggested by Bowman
     * and Azzalini (1997) q.31.
     *
     * @param x     The node for which the optimal bandwidth is calculated.
     * @param _data The dataset from which the node's values are extracted.
     * @param hash  A map that maps each node in the dataset to its corresponding index.
     * @return The optimal bandwidth for node x.
     */
    private static double h(Node x, SimpleMatrix _data, Map<Node, Integer> hash) {
        SimpleMatrix xCol = _data.getColumn(hash.get(x));
        var _x = standardizeData(xCol);
        var N = _x.getNumRows();
        var g = new Vector(N);
        var central = median(convertTo1DArray(_x));
        for (var j = 0; j < N; j++) g.set(j, abs(_x.get(j) - central));
        var mad = median(g.toArray());
        var sigmaRobust = 1.4826 * mad;
        return 1.06 * sigmaRobust * FastMath.pow(N, -0.20);
    }

    /**
     * @throws UnsupportedOperationException since not implemented.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("Method not implemented.");
    }

    /**
     * Checks the independence between two nodes given a set of conditioning variables.
     *
     * @param x The first node.
     * @param y The second node.
     * @param z The set of conditioning variables.
     * @return The result of the independence test.
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        try {
            if (Thread.currentThread().isInterrupted()) {
                return new IndependenceResult(new IndependenceFact(x, y, z),
                        true, Double.NaN, Double.NaN);
            }

            List<Node> allVars = getAllVars(x, y, z);
            IndependenceFact fact = new IndependenceFact(x, y, z);

            SimpleMatrix _data = getSubsetMatrix(allVars);

            Map<Node, Integer> hash = getHash(allVars);
            SimpleMatrix h = getH(allVars);

            IndependenceResult result;

            if (z.isEmpty()) {
                result = isIndependentUnconditional(x, y, fact, _data, h, N, hash);
            } else {
                result = isIndependentConditional(x, y, z, fact, _data, N, H, I, h, hash);
            }

//            if (verbose) {
            double p = result.getPValue();

            if (result.isIndependent()) {
                TetradLogger.getInstance().log(fact + " INDEPENDENT p = " + p);

            } else {
                TetradLogger.getInstance().log(fact + " dependent p = " + p);
            }
//            }

            return new IndependenceResult(fact, result.isIndependent(),
                    result.getPValue(), getAlpha() - result.getPValue());
        } catch (SingularMatrixException e) {
            throw new RuntimeException("Singularity encountered when testing " +
                                       LogUtilsSearch.independenceFact(x, y, z));
        }
    }

    /**
     * Returns the list of variables over which this independence checker is capable of determinining independence
     * relations.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Returns the variable of the given name.
     *
     * @param name a {@link String} object representing the name of the variable to retrieve
     * @return the Node object representing the variable with the given name
     */
    public Node getVariable(String name) {
        return this.data.getVariable(name);
    }

    /**
     * Returns the significance level of the independence test.
     *
     * @return This alpha.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the alpha level for the test.
     *
     * @param alpha The alpha level to be set.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Returns a string representation of this test.
     *
     * @return This string.
     */
    public String toString() {
        return "KCI, alpha = " + new DecimalFormat("0.0###").format(getAlpha());
    }

    /**
     * Returns The data model for the independence test.
     *
     * @return This data.
     */
    public DataModel getData() {
        return this.data;
    }

    /**
     * Returns the covariance matrix.
     *
     * @return The covariance matrix.
     */
    public ICovarianceMatrix getCov() {
        throw new UnsupportedOperationException("Method not implemented.");
    }

    /**
     * Returns a list consisting of the dataset for this test.
     *
     * @return This dataset in a list.
     */
    public List<DataSet> getDataSets() {
        LinkedList<DataSet> L = new LinkedList<>();
        L.add(this.data);
        return L;
    }

    /**
     * Returns the sample size.
     *
     * @return This size.
     */
    public int getSampleSize() {
        return this.data.getNumRows();
    }

    /**
     * Returns alpha - p.
     *
     * @param result a {@link edu.cmu.tetrad.search.test.IndependenceResult} object
     * @return This number.
     */
    public double getScore(IndependenceResult result) {
        return getAlpha() - result.getPValue();
    }

    /**
     * Sets whether the approximate algorithm should be used.
     *
     * @param approximate True, if so.
     */
    public void setApproximate(boolean approximate) {
        this.approximate = approximate;
    }

    /**
     * Sets the width multiplier.
     *
     * @param widthMultiplier This multipler.
     */
    public void setScalingFactor(double widthMultiplier) {
        if (widthMultiplier <= 0) throw new IllegalStateException("Width must be > 0");
        this.widthMultiplier = widthMultiplier;
    }

    /**
     * Sets the number of bootstraps to do.
     *
     * @param numBootstraps This number.
     */
    public void setNumBootstraps(int numBootstraps) {
        if (numBootstraps < 1) throw new IllegalArgumentException("Num bootstraps should be >= 1: " + numBootstraps);
        this.numBootstraps = numBootstraps;
    }

    /**
     * Sets the threshold.
     *
     * @param threshold This number.
     */
    public void setThreshold(double threshold) {
        if (threshold < 0.0) throw new IllegalArgumentException("Threshold must be >= 0.0: " + threshold);
        this.threshold = threshold;
    }

    /**
     * Sets the epsilon.
     *
     * @param epsilon This number.
     */
    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }

    /**
     * Returns the value of the verbose flag.
     *
     * @return The value of the verbose flag.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets the verbosity of the method.
     *
     * @param verbose True if verbosity is enabled, false otherwise.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Calculates the approximate independence result using provided kernel matrices and parameters.
     *
     * @param kx   The kernel matrix for variable x.
     * @param ky   The kernel matrix for variable y.
     * @param kx1  A scaling factor related to the first dimension of kernel matrix kx.
     * @param kx2  A scaling factor related to the second dimension of kernel matrix kx.
     * @param fact The independence fact used to contextualize the test result.
     * @return An IndependenceResult object that encapsulates the result of the independence test, including whether the
     * variables are considered independent and the associated p-value.
     */
    private @NotNull IndependenceResult getIndependenceResultApproximate(SimpleMatrix kx, SimpleMatrix ky, double kx1, double kx2,
                                                                         IndependenceFact fact) {
        double sta = kx.mult(ky).trace();
        double k_appr = kx1 * kx1 / kx2;
        double theta_appr = kx2 / kx1;
        double p = 1.0 - new GammaDistribution(k_appr, theta_appr).cumulativeProbability(sta);
        boolean indep = p > getAlpha();
        return new IndependenceResult(fact, indep, p, getAlpha() - p);
    }

    /**
     * Returns the KCI independence result for the unconditional case. Uses Theorem 4 from the paper.
     *
     * @return true, just in case independence holds.
     */
    private IndependenceResult isIndependentUnconditional(Node x, Node y, IndependenceFact fact, SimpleMatrix _data,
                                                          SimpleMatrix _h, int N, Map<Node, Integer> hash) {
        SimpleMatrix ones = getOnes(N);

        SimpleMatrix H = SimpleMatrix.identity(N).minus(ones.mult(ones.transpose()).scale(1.0 / N));

        SimpleMatrix kx = MatrixUtils.center(kernelMatrix(_data, x, null, this.widthMultiplier, hash, N, _h), H);
        SimpleMatrix ky = MatrixUtils.center(kernelMatrix(_data, y, null, this.widthMultiplier, hash, N, _h), H);

        try {
            if (this.approximate) {
                return getIndependenceResultApproximate(kx, ky, kx.trace() * ky.trace() / N, 2 * kx.mult(kx).trace() * ky.mult(ky).trace() / (N * N), fact);
            } else {
                return theorem4(kx, ky, fact, N);
            }
        } catch (Exception e) {
            TetradLogger.getInstance().log(e.getMessage());
            return new IndependenceResult(fact, false, Double.NaN, Double.NaN);
        }
    }

    /*
     * Returns the KCI independence result for the conditional case. Uses Theorem 3 from the paper.
     *
     * @return true just in case independence holds.
     */
    private IndependenceResult isIndependentConditional(Node x, Node y, Set<Node> _z, IndependenceFact fact, SimpleMatrix _data,
                                                        int N, SimpleMatrix H, SimpleMatrix I, SimpleMatrix _h, Map<Node, Integer> hash) {
        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        try {
            SimpleMatrix KXZ = MatrixUtils.center(kernelMatrix(_data, x, z, this.widthMultiplier, hash, N, _h), H);
            SimpleMatrix Ky = MatrixUtils.center(kernelMatrix(_data, y, null, this.widthMultiplier, hash, N, _h), H);
            SimpleMatrix KZ = MatrixUtils.center(kernelMatrix(_data, null, z, this.widthMultiplier, hash, N, _h), H);

            SimpleMatrix Rz = (KZ.plus(I.scale(this.epsilon)).invert().scale(this.epsilon));

            SimpleMatrix kx = MatrixUtils.symmetrize(Rz.mult(KXZ).mult(Rz.transpose()));
            SimpleMatrix ky = MatrixUtils.symmetrize(Rz.mult(Ky).mult(Rz.transpose()));

            return proposition5(kx, ky, fact, N);
        } catch (Exception e) {
            TetradLogger.getInstance().log(e.getMessage());
            boolean indep = false;
            return new IndependenceResult(fact, indep, Double.NaN, Double.NaN);
        }
    }

    /**
     * Calculates the independence result using Theorem 4 from the paper.
     *
     * @param kernx The kernel matrix for node x.
     * @param kerny The kernel matrix for node y.
     * @param fact  The independence fact.
     * @param N     The sample size.
     * @return The independence result.
     */
    private IndependenceResult theorem4(SimpleMatrix kernx, SimpleMatrix kerny, IndependenceFact fact, int N) {

        double T = (1.0 / N) * (kernx.mult(kerny).trace());

        // Eigen decomposition of kernx and kerny.
        EigenReturn eigendecompositionx = new TopEigenvalues(kernx).invoke(false, threshold);
        List<Double> evx = eigendecompositionx.topEigenvalues();

        EigenReturn eigendecompositiony = new TopEigenvalues(kerny).invoke(false, threshold);
        List<Double> evy = eigendecompositiony.topEigenvalues();

        // Calculate formula (9).
        int sum = 0;

        for (int j = 0; j < this.numBootstraps; j++) {
            double tui = 0.0;

            for (double lambdax : evx) {
                for (double lambday : evy) {
                    tui += lambdax * lambday * getChisqSample();
                }
            }

            tui /= N * N;

            if (tui > T) sum++;
        }

        // Calculate p.
        double p = sum / (double) this.numBootstraps;
        boolean indep = p > getAlpha();
        return new IndependenceResult(fact, indep, p, getAlpha() - p);
    }

    /**
     * Calculates the independence test result for Proposition 5.
     *
     * @param kx   The matrix kx.
     * @param ky   The matrix ky.
     * @param fact The independence fact.
     * @param N    The size of the input dataset.
     * @return The independence result.
     */
    private IndependenceResult proposition5(SimpleMatrix kx, SimpleMatrix ky, IndependenceFact fact, int N) {
        double T = (1.0 / N) * kx.mult(ky).trace();

        EigenReturn eigendecompositionx = new TopEigenvalues(kx).invoke(true, threshold);
        SimpleMatrix vx = eigendecompositionx.V();
        SimpleMatrix dx = eigendecompositionx.D();

        EigenReturn eigendecompositiony = new TopEigenvalues(ky).invoke(true, threshold);
        SimpleMatrix vy = eigendecompositiony.V();
        SimpleMatrix dy = eigendecompositiony.D();

        // VD
        SimpleMatrix vdx = vx.mult(dx);
        SimpleMatrix vdy = vy.mult(dy);

        int prod = vx.getNumCols() * vy.getNumCols();
        SimpleMatrix UU = new SimpleMatrix(N, prod);

        // stack
        for (int i = 0; i < vx.getNumCols(); i++) {
            for (int j = 0; j < vy.getNumCols(); j++) {
                for (int k = 0; k < N; k++) {
                    UU.set(k, i * dy.getNumCols() + j, vdx.get(k, i) * vdy.get(k, j));
                }
            }
        }

        SimpleMatrix uuprod = prod > N ? UU.mult(UU.transpose()) : UU.transpose().mult(UU);

        if (this.approximate) {
            return getIndependenceResultApproximate(kx, ky, uuprod.trace(), 2.0 * uuprod.mult(uuprod).trace(), fact);
        } else {

            // Get top eigenvalues of that.
            EigenReturn eigendecompositionu = new TopEigenvalues(uuprod).invoke(false, threshold);
            List<Double> top = eigendecompositionu.topEigenvalues();

            // Calculate formulas (13) and (14).
            int sum = 0;

            for (int j = 0; j < this.numBootstraps; j++) {
                double s = 0.0;

                for (double lambdaStar : top) {
                    s += lambdaStar * getChisqSample();
                }

                s *= 1.0 / N;
                if (s > T) sum++;
            }

            double p = sum / (double) this.numBootstraps;
            boolean indep = p > getAlpha();
            return new IndependenceResult(fact, indep, p, getAlpha() - p);
        }
    }

    /**
     * Returns the Chi-square sample value.
     *
     * @return The Chi-square sample value.
     */
    private double getChisqSample() {
        double z = this.normal.sample();
        return z * z;
    }

    /**
     * Calculates the kernel matrix based on the given parameters.
     *
     * @param _data           the data matrix
     * @param x               the target node
     * @param z               the list of other nodes
     * @param widthMultiplier the width multiplier for the kernel
     * @param hash            the map of nodes to their indices
     * @param N               the number of data points
     * @param _h              the bandwidth vector
     * @return the calculated kernel matrix
     */
    private SimpleMatrix kernelMatrix(SimpleMatrix _data, Node x, List<Node> z, double widthMultiplier,
                                      Map<Node, Integer> hash, int N, SimpleMatrix _h) {

        List<Integer> _z = new ArrayList<>();
        if (x != null) _z.add(hash.get(x));
        if (z != null) z.forEach(node -> _z.add(hash.get(node)));

        double h = getH(_z, _h);
        double width = widthMultiplier * h;

        SimpleMatrix result = new SimpleMatrix(N, N);

        // Parallelize distance and kernel computation
        IntStream.range(0, N).parallel().forEach(i -> {
            for (int j = i + 1; j < N; j++) {
                double d = distance(_data, _z, i, j);
                double k = MatrixUtils.kernelGaussian(d, width);
                result.set(i, j, k);
                result.set(j, i, k);
            }
            result.set(i, i, MatrixUtils.kernelGaussian(0, width));
        });

        return result;
    }

    /**
     * Returns the value of h calculated based on the given list of indices and bandwidth vector.
     *
     * @param _z The list of indices.
     * @param _h The bandwidth vector.
     * @return The calculated h value.
     */
    private double getH(List<Integer> _z, SimpleMatrix _h) {
        double h = 0;

        for (int c : _z) {
            if (_h.get(c) > h) {
                h = _h.get(c);
            }
        }

        h *= sqrt(_z.size());
        return h;
    }

    /**
     * Calculate the Euclidean distance between two data points based on specified columns.
     *
     * @param data The data matrix containing the data points.
     * @param cols The list of column indices to be used for distance calculation.
     * @param i    The index of the first data point.
     * @param j    The index of the second data point.
     * @return The Euclidean distance between the two data points.
     */
    private double distance(SimpleMatrix data, List<Integer> cols, int i, int j) {
        double sum = 0.0;

        for (int col : cols) {
            double d = data.get(col, i) - data.get(col, j);
            sum += d * d;
        }

        return sum;
    }

    /**
     * Creates a column vector of size n filled with ones.
     *
     * @param n the size of the column vector to be created
     * @return a SimpleMatrix object representing the column vector of ones
     */
    private @NotNull SimpleMatrix getOnes(int n) {
        SimpleMatrix ones = new SimpleMatrix(n, 1);
        for (int j = 0; j < n; j++) ones.set(j, 0, 1);
        return ones;
    }

    /**
     * Computes the vector h based on the given data columns from a SimpleMatrix object.
     *
     * @param dataCols the SimpleMatrix object containing data columns used to compute the vector h
     * @return a Vector object representing the computed values
     */
    private Vector getH(SimpleMatrix dataCols) {
        Vector h = new Vector(variables.size());

        for (int i = 0; i < this.data.getNumColumns(); i++) {
            h.set(i, h(variables.get(i), dataCols, hash));
        }

        return h;
    }

    /**
     * Constructs a map associating each Node in the variables list with its corresponding index in the list. Each entry
     * in the map corresponds to a Node as the key and its index position as the value.
     *
     * @return a map where each Node from the variables list is mapped to its index.
     */
    private Map<Node, Integer> getNodeIntegerMap() {
        Map<Node, Integer> hash = new HashMap<>();

        for (int i = 0; i < variables.size(); i++) {
            hash.put(variables.get(i), i);
        }

        return hash;
    }

    /**
     * Computes and returns a SimpleMatrix object based on the provided list of nodes. Each node in the list corresponds
     * to a row in the resulting matrix. If a node's value is zero, it is replaced with the average of non-zero values.
     *
     * @param allVars a list of Node objects representing variables that determine the rows in the resulting
     *                SimpleMatrix. Each node is used to retrieve a corresponding value from an internal structure.
     * @return a SimpleMatrix object where each row corresponds to a node in the input list and contains either its
     * associated value or an averaged value.
     */
    private @NotNull SimpleMatrix getH(List<Node> allVars) {
        SimpleMatrix h = new SimpleMatrix(allVars.size(), data.getNumRows());
        int count = 0;

        double sum = 0.0;
        for (int i = 0; i < allVars.size(); i++) {
            h.set(i, this.h.get(this.hash.get(allVars.get(i))));

            if (h.get(i) != 0) {
                sum += h.get(i);
                count++;
            }
        }

        double avg = sum / count;

        for (int i = 0; i < h.getNumRows(); i++) {
            if (h.get(i) == 0) h.set(i, avg);
        }
        return h;
    }

    /**
     * Retrieves an array of column indices corresponding to the provided list of nodes.
     *
     * @param allVars a list of Node objects for which corresponding column indices are to be retrieved
     * @return an array of integers representing the column indices associated with each Node in the list
     */
    private int @NotNull [] getCols(List<Node> allVars) {
        int[] _cols = new int[allVars.size()];

        for (int i = 0; i < allVars.size(); i++) {
            Node key = allVars.get(i);
            _cols[i] = this.hash.get(key);
        }
        return _cols;
    }

    /**
     * Constructs and returns a simple matrix that is a subset of the original dataset, based on the given list of
     * variables.
     *
     * @param allVars the list of nodes representing the variables to include in the subset matrix.
     * @return a SimpleMatrix object containing the subset of data corresponding to the specified variables.
     */
    private SimpleMatrix getSubsetMatrix(List<Node> allVars) {
        DataSet data = this.data.subsetColumns(getCols(allVars));
        return new SimpleMatrix(data.getDoubleData().transpose().toArray());
    }

    /**
     * A record representing the result of an eigenvalue decomposition.
     * <p>
     * The EigenReturn record encapsulates the diagonal matrix of eigenvalues (D), the matrix of eigenvectors (V), and a
     * list containing the top eigenvalues.
     *
     * @param D              A SimpleMatrix containing the eigenvalues in its diagonal. The order of the eigenvalues
     *                       corresponds to the columns of the matrix V.
     * @param V              A SimpleMatrix where each column is an eigenvector of the original matrix. The columns are
     *                       ordered to match the eigenvalues in D.
     * @param topEigenvalues A list of doubles representing the top eigenvalues. This might be a subset of the
     *                       eigenvalues in D, typically those with the largest magnitude or the most significance for a
     *                       particular application.
     */
    public record EigenReturn(SimpleMatrix D, SimpleMatrix V, List<Double> topEigenvalues) {
    }
}

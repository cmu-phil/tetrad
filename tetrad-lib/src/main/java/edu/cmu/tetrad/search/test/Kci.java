package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
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
public class Kci implements IndependenceTest, RowsSettable {

    private static final Map<Integer, SimpleMatrix> onesHash = new HashMap<>();
    private static final Map<Integer, SimpleMatrix> identityMap = new HashMap<>();
    private static final Map<Integer, SimpleMatrix> hMap = new HashMap<>();
    /**
     * The dataset to analyze.
     */
    private final DataSet dataSet;
    /**
     * The supplied data set, standardized and as a SimpleMatrix.
     */
    private final SimpleMatrix data;
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
     * The kernel type.
     */
    private KernelType kernelType = KernelType.POLYNOMIAL;
    /**
     * Polynomial kernel degree.
     */
    private double polyDegree = 1.0;
    /**
     * Polynomial kernel constant.
     */
    private double polyConst = 1.0;
    /**
     * The rows used in the test.
     */
    private List<Integer> rows = null;

    /**
     * Constructor.
     *
     * @param data  The dataset to analyze. Must be continuous.
     * @param alpha The alpha value of the test.
     */
    public Kci(DataSet data, double alpha) {
        this.dataSet = data;
        this.data = standardizeData(new SimpleMatrix(dataSet.getDoubleData().toArray()));

        this.variables = data.getVariables();
        this.hash = getNodeIntegerMap();

        this.h = getH(this.data);

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
        SimpleMatrix data2 = data.copy();

        for (int j = 0; j < data.getNumCols(); j++) {
            double sum = 0.0;

            for (int i = 0; i < data.getNumRows(); i++) {
                sum += data2.get(i, j);
            }

            double mean = sum / data2.getNumRows();

            for (int i = 0; i < data2.getNumRows(); i++) {
                data2.set(i, j, data2.get(i, j) - mean);
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

    /**
     * Generates and returns a mapping of Node objects to their respective indices in the provided list.
     *
     * @param allVars a list of Node objects for which the hash map is to be created
     * @return a map where each Node in the list is associated with its index position
     */
    private static @NotNull Map<Node, Integer> getHash(List<Node> allVars) {
        Map<Node, Integer> hash = new HashMap<>();
        for (int i = 0; i < allVars.size(); i++) hash.put(allVars.get(i), i);
        return hash;
    }

    /**
     * Collects and returns a list of all variables, including the provided nodes and all elements of a given set.
     *
     * @param x the first node to be added to the list
     * @param y the second node to be added to the list
     * @param z a set of nodes to be added to the list
     * @return a list containing the nodes x, y, and all elements of the set z
     */
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
     * @param x         The node for which the optimal bandwidth is calculated.
     * @param data      The dataset from which the node's values are extracted.
     * @param nodesHash A map that maps each node in the dataset to its corresponding index.
     * @return The optimal bandwidth for node x.
     */
    private static double h(Node x, SimpleMatrix data, Map<Node, Integer> nodesHash) {
        var _x = data.getColumn(nodesHash.get(x));
//        var s = sd(_x.toArray2()[0]);
//        _x = standardizeData(_x);
        var N = _x.getNumRows();
        var g = new Vector(N);
        var central = median(convertTo1DArray(_x));
        for (var j = 0; j < N; j++) g.set(j, abs(_x.get(j) - central));
        var mad = median(g.toArray());
//        var sigmaRobust = 1.4826 * mad;
//        return 1.06 * sigmaRobust * FastMath.pow(N, -0.20);
        return 1.5716 * mad * FastMath.pow(N, -0.2);
    }

    private static @NotNull SimpleMatrix getI(int N) {
        SimpleMatrix I = identityMap.get(N);

        if (I == null) {
            I = SimpleMatrix.identity(N);
            identityMap.put(N, I);
        }

        return I;
    }

    private static SimpleMatrix getH(int N) {
        SimpleMatrix H = hMap.get(N);

        if (H == null) {
            H = getI(N).minus(getOnes(N).mult(getOnes(N).transpose()).scale(1.0 / N));
            hMap.put(N, H);
        }

        return H;
    }

    /**
     * Creates a column vector of size n filled with ones.
     *
     * @param n the size of the column vector to be created
     * @return a SimpleMatrix object representing the column vector of ones
     */
    private @NotNull
    static SimpleMatrix getOnes(int n) {
        SimpleMatrix ones = onesHash.get(n);

        if (ones == null) {
            ones = new SimpleMatrix(n, 1);
            for (int j = 0; j < n; j++) ones.set(j, 0, 1);
            onesHash.put(n, ones);
        }

        return ones;
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
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        try {
            List<Node> allVars = getAllVars(x, y, z);
            IndependenceFact fact = new IndependenceFact(x, y, z);
            SimpleMatrix _data = getSubsetMatrix(allVars);
            Map<Node, Integer> hash = getHash(allVars);
            SimpleMatrix h = getH(allVars);
            IndependenceResult result;

            if (z.isEmpty()) {
                result = isIndependentUnconditional(x, y, fact, _data, h, hash);
            } else {
                result = isIndependentConditional(x, y, z, fact, _data, h, hash);
            }

            double p = result.getPValue();

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            if (result.isIndependent()) {
                TetradLogger.getInstance().log(fact + " INDEPENDENT p = " + p);

            } else {
                TetradLogger.getInstance().log(fact + " dependent p = " + p);
            }

            return new IndependenceResult(fact, result.isIndependent(),
                    result.getPValue(), getAlpha() - result.getPValue());
        } catch (SingularMatrixException e) {
            throw new RuntimeException("Singularity encountered when testing " +
                                       LogUtilsSearch.independenceFact(x, y, z));
        } catch (Exception e) {
            TetradLogger.getInstance().log(e.getMessage());
            return new IndependenceResult(new IndependenceFact(x, y, z),
                    false, Double.NaN, Double.NaN);
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
        return this.dataSet.getVariable(name);
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
        return this.dataSet;
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
        L.add(this.dataSet);
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
                                                          SimpleMatrix _h, Map<Node, Integer> hash) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        List<Integer> _rows = listRows();
        int N = _rows.size();
        SimpleMatrix H = getH(N);
        SimpleMatrix k1 = kernelMatrix(_data, x, null, this.widthMultiplier, hash, _h, _rows);
        SimpleMatrix kx = H.mult(k1).mult(H);
        SimpleMatrix k = kernelMatrix(_data, y, null, this.widthMultiplier, hash, _h, _rows);
        SimpleMatrix ky = H.mult(k).mult(H);

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
                                                        SimpleMatrix _h, Map<Node, Integer> hash) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);
        List<Integer> _rows = listRows();
        int N = _rows.size();
        SimpleMatrix I = getI(N);
        SimpleMatrix H = getH(N);

        try {
            SimpleMatrix k4 = kernelMatrix(_data, x, z, this.widthMultiplier, hash, _h, _rows);
            SimpleMatrix KXZ = H.mult(k4).mult(H);
            SimpleMatrix k3 = kernelMatrix(_data, y, null, this.widthMultiplier, hash, _h, _rows);
            SimpleMatrix Ky = H.mult(k3).mult(H);
            SimpleMatrix k2 = kernelMatrix(_data, null, z, this.widthMultiplier, hash, _h, _rows);
            SimpleMatrix KZ = H.mult(k2).mult(H);
            SimpleMatrix Rz = (KZ.plus(I.scale(this.epsilon)).invert().scale(this.epsilon));
            SimpleMatrix k1 = Rz.mult(KXZ).mult(Rz.transpose());
            SimpleMatrix kx = k1.plus(k1.transpose()).scale(0.5);
            SimpleMatrix k = Rz.mult(Ky).mult(Rz.transpose());
            SimpleMatrix ky = k.plus(k.transpose()).scale(0.5);

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
    private IndependenceResult theorem4(SimpleMatrix kernx, SimpleMatrix kerny, IndependenceFact fact, int N) throws InterruptedException {

        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

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
    private IndependenceResult proposition5(SimpleMatrix kx, SimpleMatrix ky, IndependenceFact fact, int N) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

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
     * @param _h              the bandwidth vector
     * @param _rows           the list of rows to use
     * @return the calculated kernel matrix
     */
    private SimpleMatrix kernelMatrix(SimpleMatrix _data, Node x, List<Node> z, double widthMultiplier,
                                      Map<Node, Integer> hash, SimpleMatrix _h, List<Integer> _rows) throws InterruptedException {

        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        List<Integer> _z = new ArrayList<>();
        if (x != null) _z.add(hash.get(x));
        if (z != null) z.forEach(node -> _z.add(hash.get(node)));
        double h = getH(_z, _h); // For Gaussian.
        double width = widthMultiplier * h; // For Gaussian.
        SimpleMatrix result = new SimpleMatrix(_rows.size(), _rows.size());

        for (int i = 0; i < _rows.size(); i++) {
            for (int j = i; j < _rows.size(); j++) {
                if (kernelType == KernelType.GAUSSIAN) {
                    double k = getGaussianKernel(_data, _z, _rows.get(i), _rows.get(j), width);
                    result.set(i, j, k);
                    result.set(j, i, k);
                } else if (kernelType == KernelType.LINEAR) {
                    double k = getLinearKernel(_data, _z, _rows.get(i), _rows.get(j));
                    result.set(i, j, k);
                    result.set(j, i, k);
                } else if (kernelType == KernelType.POLYNOMIAL) {
                    double k = getPolynomialKernel(_data, _z, _rows.get(i), _rows.get(j), polyDegree, polyConst);
                    result.set(i, j, k);
                    result.set(j, i, k);
                } else {
                    throw new IllegalStateException("Unknown kernel type: " + kernelType);
                }
            }
        }

        return result;
    }

    /**
     * Computes the Gaussian kernel value between the i-th and j-th elements using the provided data matrix and list of
     * indices. The Gaussian kernel is a measure of similarity that decreases exponentially with the distance between
     * the points, scaled by the specified width.
     *
     * @param _data The matrix containing the data points.
     * @param _z    The list of indices mapping to specific data points in the matrix.
     * @param i     The index of the first point.
     * @param j     The index of the second point.
     * @param width The bandwidth parameter of the Gaussian kernel, controlling the spread.
     * @return The computed Gaussian kernel value, a double representing the similarity.
     */
    private double getGaussianKernel(SimpleMatrix _data, List<Integer> _z, int i, int j, double width) {
        double d = distance(_data, _z, i, j);
        d /= 2 * width;
        return Math.exp(-d);
    }

    /**
     * Computes the linear kernel between two data points from the input matrix.
     *
     * @param _data        The matrix containing the data points.
     * @param _z           A list of integer indices corresponding to the data points.
     * @param i            The index of the first data point.
     * @param j            The index of the second data point.
     * @param polyDegree   The degree of the polynomial, not used in this method.
     * @param polyConstant The constant of the polynomial, not used in this method.
     * @return The computed linear kernel value for the data points at indices i and j.
     */
    private double getLinearKernel(SimpleMatrix _data, List<Integer> _z, int i, int j) {
        return dot(_data, _z, i, j);
    }

    /**
     * Computes the polynomial kernel between two data points identified by their indices.
     *
     * @param _data        The data matrix containing all data points.
     * @param _z           A list of indices representing a subset of data points.
     * @param i            Index of the first data point in the subset list.
     * @param j            Index of the second data point in the subset list.
     * @param polyDegree   The degree of the polynomial kernel.
     * @param polyConstant The constant term added to the dot product before applying the power function.
     * @return The polynomial kernel value between the two specified data points.
     */
    private double getPolynomialKernel(SimpleMatrix _data, List<Integer> _z, int i, int j,
                                       double polyDegree, double polyConstant) {
        double d = dot(_data, _z, i, j);
        return Math.pow(d + polyConstant, polyDegree);
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
     * Calculate the dot product between two data points based on specified columns.
     *
     * @param data The data matrix containing the data points.
     * @param cols The list of column indices to be used for dot product calculation.
     * @param i    The index of the first data point.
     * @param j    The index of the second data point.
     * @return The dot product between the two data points.
     */
    private double dot(SimpleMatrix data, List<Integer> cols, int i, int j) {
        double sum = 0.0;

        for (int col : cols) {
            double d = data.get(col, i) * data.get(col, j);
            sum += d;
        }

        return sum;
    }

    /**
     * Computes the vector h based on the given data columns from a SimpleMatrix object.
     *
     * @param data the SimpleMatrix object containing data columns used to compute the vector h
     * @return a Vector object representing the computed values
     */
    private Vector getH(SimpleMatrix data) {
        Vector h = new Vector(variables.size());

        for (int i = 0; i < this.data.getNumCols(); i++) {
            h.set(i, h(variables.get(i), data, hash));
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

        // TODO See if this is a bottleneck and if so optimize
        DataSet data = this.dataSet.subsetColumns(getCols(allVars));
        return new SimpleMatrix(data.getDoubleData().transpose().toArray());
    }

    /**
     * Retrieves the rows from the dataSet that contain valid values for all variables.
     *
     * @return a list of row indices that contain valid values for all variables
     */
    private List<Integer> listRows() {
        if (this.rows != null) {
            return this.rows;
        }

        List<Integer> rows = new ArrayList<>();

        for (int k = 0; k < this.dataSet.getNumRows(); k++) {
            rows.add(k);
        }

        return rows;
    }

    /**
     * Sets the type of kernel to be used in computations.
     *
     * @param kernelType the KernelType to set
     */
    public void setKernelType(KernelType kernelType) {
        this.kernelType = kernelType;
    }

    /**
     * Sets the degree of the polynomial kernel, if used
     *
     * @param polyDegree the degree of the polynomial kernel to set
     */
    public void setPolyDegree(double polyDegree) {
        this.polyDegree = polyDegree;
    }

    /**
     * Sets the constant of the polynomial kernel, if used
     *
     * @param polyConst the constant of the polynomial kernel to set
     */
    public void setPolyConst(double polyConst) {
        this.polyConst = polyConst;
    }

    /**
     * Returns the rows used in the test.
     *
     * @return The rows used in the test.
     */
    public List<Integer> getRows() {
        return rows;
    }

    /**
     * Allows the user to set which rows are used in the test. Otherwise, all rows are used, except those with missing
     * values.
     */
    public void setRows(List<Integer> rows) {
        if (dataSet == null) {
            return;
        }
        if (rows == null) {
            this.rows = null;
        } else {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i) == null) throw new NullPointerException("Row " + i + " is null.");
                if (rows.get(i) < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            }

            this.rows = rows;
        }
    }

    /**
     * Represents the type of kernel to be used in a computation.
     *
     * <ul>
     * <li>GAUSSIAN: Indicates the use of a Gaussian kernel, commonly used in various machine learning algorithms for its smooth and bell-shaped curve characteristics.</li>
     * <li>POLYNOMIAL: Represents a polynomial kernel, which is useful for problems requiring the representation of the input data in a higher-dimensional space.</li>
     * </ul>
     */
    public enum KernelType {
        GAUSSIAN, LINEAR, POLYNOMIAL
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

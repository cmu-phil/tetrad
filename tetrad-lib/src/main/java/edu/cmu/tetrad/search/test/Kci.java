package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.Vector;
import edu.pitt.csb.mgm.EigenDecomposition;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.random.SynchronizedRandomGenerator;
import org.apache.commons.math3.random.Well44497b;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static edu.cmu.tetrad.util.StatUtils.median;
import static org.apache.commons.math3.util.FastMath.*;

/**
 * Gives an implementation of the Kernal Independence Test (KCI) by Kun Zhang, which is a general test of conditional
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
 * @author josephramsey refactoring 7/4/2018
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
    private final double[] h;
    /**
     * A normal distribution with 1 degree of freedom.
     */
    private final NormalDistribution normal = new NormalDistribution(new SynchronizedRandomGenerator(
            new Well44497b(193924L)), 0, 1);
    /**
     * Convenience map from nodes to their indices in the list of variables.
     */
    private final Map<Node, Integer> hash;
    /**
     * Record of independence facts
     */
    private final Map<IndependenceFact, IndependenceResult> facts = new ConcurrentHashMap<>();
    /**
     * The alpha level of the test.
     */
    private double alpha;
    /**
     * True if the approximation algorithms should be used instead of Theorems 3 or 4.
     */
    private boolean approximate;
    /**
     * Eigenvalues greater than this time the maximum will be kept.
     */
    private double threshold = 0.01;
    /**
     * Number of bostraps for Theorem 4 and Proposition 5.
     */
    private int numBootstraps = 5000;
    /**
     * Azzalini optimal kernel widths will be multiplied by this.
     */
    private double widthMultiplier = 1.0;
    /**
     * Epsilon for Propositio 5.
     */
    private double epsilon = 0.001;
    /**
     * True if verbose output should be printed.
     */
    private boolean verbose;

    /**
     * Constructor.
     *
     * @param data  The dataset to analyse. Must be continuous.
     * @param alpha The alpha value of the test.
     */
    public Kci(DataSet data, double alpha) {
        this.data = DataTransforms.standardizeData(data);

        this.variables = data.getVariables();
        int n = this.data.getNumRows();

        this.hash = new HashMap<>();

        for (int i = 0; i < variables.size(); i++) {
            this.hash.put(variables.get(i), i);
        }

        double[][] dataCols = this.data.getDoubleData().transpose().toArray();
        this.h = new double[variables.size()];

        for (int i = 0; i < this.data.getNumColumns(); i++) {
            this.h[i] = h(variables.get(i), dataCols, hash);
        }

        Matrix Ones = new Matrix(n, 1);
        for (int j = 0; j < n; j++) Ones.set(j, 0, 1);

        this.alpha = alpha;
    }


    /**
     * @throws UnsupportedOperationException since not implemneted.
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
        if (facts.containsKey(new IndependenceFact(x, y, z))) {
            return facts.get(new IndependenceFact(x, y, z));
        }

        try {

            if (Thread.currentThread().isInterrupted()) {
                return new IndependenceResult(new IndependenceFact(x, y, z),
                        true, Double.NaN, Double.NaN);
            }

            List<Node> allVars = new ArrayList<>();
            allVars.add(x);
            allVars.add(y);
            allVars.addAll(z);

            IndependenceFact fact = new IndependenceFact(x, y, z);

            if (facts.containsKey(fact)) {
                IndependenceResult result = facts.get(fact);

                if (verbose) {
                    double p = result.getPValue();

                    if (result.isIndependent()) {
                        TetradLogger.getInstance().forceLogMessage(fact + " INDEPENDENT p = " + p);
                    } else {
                        TetradLogger.getInstance().forceLogMessage(fact + " dependent p = " + p);
                    }
                }

                return new IndependenceResult(fact, result.isIndependent(), result.getPValue(), getAlpha() - result.getPValue());
            } else {
                List<Integer> rows = getRows(this.data);

                int[] _cols = new int[allVars.size()];

                for (int i = 0; i < allVars.size(); i++) {
                    Node key = allVars.get(i);
                    _cols[i] = this.hash.get(key);
                }

                int[] _rows = new int[rows.size()];
                for (int i = 0; i < rows.size(); i++) _rows[i] = rows.get(i);

                DataSet data = this.data.subsetRowsColumns(_rows, _cols);
                double[][] _data = data.getDoubleData().transpose().toArray();

                Map<Node, Integer> hash = new HashMap<>();
                for (int i = 0; i < allVars.size(); i++) hash.put(allVars.get(i), i);

                int N = data.getNumRows();

                Matrix ones = new Matrix(N, 1);
                for (int j = 0; j < N; j++) ones.set(j, 0, 1);

                Matrix I = Matrix.identity(N);
                Matrix H = I.minus(ones.times(ones.transpose()).scalarMult(1.0 / N));

                double[] h = new double[allVars.size()];
                int count = 0;

                double sum = 0.0;
                for (int i = 0; i < allVars.size(); i++) {
                    h[i] = this.h[this.hash.get(allVars.get(i))];

                    if (h[i] != 0) {
                        sum += h[i];
                        count++;
                    }
                }

                double avg = sum / count;

                for (int i = 0; i < h.length; i++) {
                    if (h[i] == 0) h[i] = avg;
                }

                IndependenceResult result = facts.get(fact);

                if (this.facts.get(fact) != null) {
                    IndependenceResult result1 = new IndependenceResult(fact, result.isIndependent(),
                            result.getPValue(), getAlpha() - result.getPValue());
                    facts.put(fact, result1);
                    return result1;
                } else {
                    if (z.isEmpty()) {
                        result = isIndependentUnconditional(x, y, fact, _data, h, N, hash);
                    } else {
                        result = isIndependentConditional(x, y, z, fact, _data, N, H, I, h, hash);
                    }
                }

                if (verbose) {
                    double p = result.getPValue();

                    if (result.isIndependent()) {
                        TetradLogger.getInstance().forceLogMessage(fact + " INDEPENDENT p = " + p);

                    } else {
                        TetradLogger.getInstance().forceLogMessage(fact + " dependent p = " + p);
                    }
                }

                IndependenceResult result1 = new IndependenceResult(fact, result.isIndependent(),
                        result.getPValue(), getAlpha() - result.getPValue());
                facts.put(fact, result1);
                return result1;
            }
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
     * Determines the independence between a set of nodes and a target node.
     *
     * @param z The set of nodes representing the conditioning variables.
     * @param y The target node.
     * @return True if the conditioning variables z are independent of the target node y, false otherwise.
     */
    public boolean determines(List<Node> z, Node y) {
        throw new UnsupportedOperationException();
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
    public void setWidthMultiplier(double widthMultiplier) {
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
     * Returns the KCI independence result for the unconditional case. Uses Theorem 4 from the paper.
     *
     * @return true just in case independence holds.
     */
    private IndependenceResult isIndependentUnconditional(Node x, Node y, IndependenceFact fact, double[][] _data,
                                                          double[] _h, int N,
                                                          Map<Node, Integer> hash) {
        Matrix Ones = new Matrix(N, 1);
        for (int j = 0; j < N; j++) Ones.set(j, 0, 1);

        Matrix H = Matrix.identity(N).minus(Ones.times(Ones.transpose()).scalarMult(1.0 / N));

        Matrix kx = center(kernelMatrix(_data, x, null, this.widthMultiplier, hash, N, _h), H);
        Matrix ky = center(kernelMatrix(_data, y, null, this.widthMultiplier, hash, N, _h), H);

        try {
            if (this.approximate) {
                double sta = kx.times(ky).trace();
                double mean_appr = kx.trace() * ky.trace() / N;
                double var_appr = 2 * kx.times(kx).trace() * ky.times(ky).trace() / (N * N);
                double k_appr = mean_appr * mean_appr / var_appr;
                double theta_appr = var_appr / mean_appr;
                double p = 1.0 - new GammaDistribution(k_appr, theta_appr).cumulativeProbability(sta);
                boolean indep = p > getAlpha();
                IndependenceResult result = new IndependenceResult(fact, indep, p, getAlpha() - p);
                this.facts.put(fact, result);
                return result;
            } else {
                return theorem4(kx, ky, fact, N);
            }
        } catch (Exception e) {
            TetradLogger.getInstance().forceLogMessage(e.getMessage());
            IndependenceResult result = new IndependenceResult(fact, false, 0.0, getAlpha());
            this.facts.put(fact, result);
            return result;
        }
    }

    /*
     * Returns the KCI independence result for the conditional case. Uses Theorem 3 from the paper.
     *
     * @return true just in case independence holds.
     */
    private IndependenceResult isIndependentConditional(Node x, Node y, Set<Node> _z, IndependenceFact fact, double[][] _data,
                                                        int N, Matrix H, Matrix I, double[] _h, Map<Node, Integer> hash) {
        Matrix kx;
        Matrix ky;

        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        try {
            Matrix KXZ = center(kernelMatrix(_data, x, z, this.widthMultiplier, hash, N, _h), H);
            Matrix Ky = center(kernelMatrix(_data, y, null, this.widthMultiplier, hash, N, _h), H);
            Matrix KZ = center(kernelMatrix(_data, null, z, this.widthMultiplier, hash, N, _h), H);

            Matrix Rz = (KZ.plus(I.scalarMult(this.epsilon)).inverse().scalarMult(this.epsilon));

            kx = symmetrized(Rz.times(KXZ).times(Rz.transpose()));
            ky = symmetrized(Rz.times(Ky).times(Rz.transpose()));

            return proposition5(kx, ky, fact, N);
        } catch (Exception e) {
            TetradLogger.getInstance().forceLogMessage(e.getMessage());
            boolean indep = false;
            IndependenceResult result = new IndependenceResult(fact, indep, 0.0, getAlpha());
            this.facts.put(fact, result);
            return result;
        }
    }

    /**
     * Calculates the independence result using Theorem 4 from the paper.
     *
     * @param kx   The kernel matrix for node x.
     * @param ky   The kernel matrix for node y.
     * @param fact The independence fact.
     * @param N    The sample size.
     * @return The independence result.
     */
    private IndependenceResult theorem4(Matrix kx, Matrix ky, IndependenceFact fact, int N) {

        double T = (1.0 / N) * (kx.times(ky).trace());

        // Eigen decomposition of kx and ky.
        Eigendecomposition eigendecompositionx = new Eigendecomposition(kx).invoke(false);
        List<Double> evx = eigendecompositionx.getTopEigenvalues();

        Eigendecomposition eigendecompositiony = new Eigendecomposition(ky).invoke(false);
        List<Double> evy = eigendecompositiony.getTopEigenvalues();

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
        IndependenceResult result = new IndependenceResult(fact, indep, p, getAlpha() - p);
        this.facts.put(fact, result);
        return result;
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
    private IndependenceResult proposition5(Matrix kx, Matrix ky, IndependenceFact fact, int N) {
        double T = (1.0 / N) * kx.times(ky).trace();

        Eigendecomposition eigendecompositionx = new Eigendecomposition(kx).invoke(true);
        Matrix vx = eigendecompositionx.getV();
        Matrix dx = eigendecompositionx.getD();

        Eigendecomposition eigendecompositiony = new Eigendecomposition(ky).invoke(true);
        Matrix vy = eigendecompositiony.getV();
        Matrix dy = eigendecompositiony.getD();

        // VD
        Matrix vdx = vx.times(dx);
        Matrix vdy = vy.times(dy);

        int prod = vx.getNumColumns() * vy.getNumColumns();
        Matrix UU = new Matrix(N, prod);

        // stack
        for (int i = 0; i < vx.getNumColumns(); i++) {
            for (int j = 0; j < vy.getNumColumns(); j++) {
                for (int k = 0; k < N; k++) {
                    UU.set(k, i * dy.getNumColumns() + j, vdx.get(k, i) * vdy.get(k, j));
                }
            }
        }

        Matrix uuprod = prod > N ? UU.times(UU.transpose()) : UU.transpose().times(UU);

        if (this.approximate) {
            double sta = kx.times(ky).trace();
            double mean_appr = uuprod.trace();
            double var_appr = 2.0 * uuprod.times(uuprod).trace();
            double k_appr = mean_appr * mean_appr / var_appr;
            double theta_appr = var_appr / mean_appr;
            double p = 1.0 - new GammaDistribution(k_appr, theta_appr).cumulativeProbability(sta);
            boolean indep = p > getAlpha();
            IndependenceResult result = new IndependenceResult(fact, indep, p, getAlpha() - p);
            this.facts.put(fact, result);
            return result;
        } else {

            // Get top eigenvalues of that.
            Eigendecomposition eigendecompositionu = new Eigendecomposition(uuprod).invoke(false);
            List<Double> eigenu = eigendecompositionu.getTopEigenvalues();

            // Calculate formulas (13) and (14).
            int sum = 0;

            for (int j = 0; j < this.numBootstraps; j++) {
                double s = 0.0;

                for (double lambdaStar : eigenu) {
                    s += lambdaStar * getChisqSample();
                }

                s *= 1.0 / N;

                if (s > T) sum++;
            }

            double p = sum / (double) this.numBootstraps;
            boolean indep = p > getAlpha();
            IndependenceResult result = new IndependenceResult(fact, indep, p, getAlpha() - p);
            this.facts.put(fact, result);
            return result;
        }
    }

    /**
     * Generates a series of integers from 0 to size-1.
     *
     * @param size The number of elements in the series.
     * @return A list of integers representing the series.
     */
    private List<Integer> series(int size) {
        List<Integer> series = new ArrayList<>();
        for (int i = 0; i < size; i++) series.add(i);
        return series;
    }

    /**
     * Calculates the centered matrix by performing matrix operations on the input matrices K and H.
     *
     * @param K The first matrix.
     * @param H The second matrix.
     * @return The resulting center matrix.
     */
    private Matrix center(Matrix K, Matrix H) {
        return H.times(K).times(H);
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
     * Calculates the optimal bandwidth for node x using the Median Absolute Deviation (MAD) method suggested by Bowman
     * and Azzalini (1997) q.31.
     *
     * @param x     The node for which the optimal bandwidth is calculated.
     * @param _data The dataset from which the node's values are extracted.
     * @param hash  A map that maps each node in the dataset to its corresponding index.
     * @return The optimal bandwidth for node x.
     */
    private double h(Node x, double[][] _data, Map<Node, Integer> hash) {
        double[] xCol = _data[hash.get(x)];
        double[] g = new double[xCol.length];
        double median = median(xCol);
        for (int j = 0; j < xCol.length; j++) g[j] = abs(xCol[j] - median);
        double mad = median(g);
        return (1.4826 * mad) * pow((4.0 / 3.0) / xCol.length, 0.2);
    }

    /**
     * Returns the top indices from the given array that have values greater than the threshold multiplied by the
     * maximum value in the array.
     *
     * @param prod       The array to filter.
     * @param allIndices The list of indices to consider.
     * @param threshold  The threshold value.
     * @return The list of top indices.
     */
    private List<Integer> getTopIndices(double[] prod, List<Integer> allIndices, double threshold) {
        double maxEig = prod[allIndices.get(0)];

        List<Integer> indices = new ArrayList<>();

        for (int i : allIndices) {
            if (prod[i] > maxEig * threshold) {
                indices.add(i);
            }
        }

        return indices;
    }

    /**
     * Returns the symmetrized matrix of the given input matrix.
     *
     * @param kx The input matrix.
     * @return The symmetrized matrix.
     */
    private Matrix symmetrized(Matrix kx) {
        return (kx.plus(kx.transpose())).scalarMult(0.5);
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
    private Matrix kernelMatrix(double[][] _data, Node x, List<Node> z, double widthMultiplier,
                                Map<Node, Integer> hash, int N, double[] _h) {

        List<Integer> _z = new ArrayList<>();

        if (x != null) {
            _z.add(hash.get(x));
        }

        if (z != null) {
            for (Node z2 : z) {
                _z.add(hash.get(z2));
            }
        }

        double h = getH(_z, _h);

        Matrix result = new Matrix(N, N);

        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                double d = distance(_data, _z, i, j);
                double k = kernelGaussian(d, widthMultiplier * h);
                result.set(i, j, k);
                result.set(j, i, k);
            }
        }

        double k = kernelGaussian(0, widthMultiplier * h);

        for (int i = 0; i < N; i++) {
            result.set(i, i, k);
        }

        return result;
    }

    /**
     * Returns the value of h calculated based on the given list of indices and bandwidth vector.
     *
     * @param _z The list of indices.
     * @param _h The bandwidth vector.
     * @return The calculated h value.
     */
    private double getH(List<Integer> _z, double[] _h) {
        double h = 0;

        for (int c : _z) {
            if (_h[c] > h) {
                h = _h[c];
            }
        }

        h *= sqrt(_z.size());
        return h;
    }

    /**
     * Computes the value of the Gaussian kernel function for the given input value and width.
     *
     * @param z     The input value.
     * @param width The width parameter of the Gaussian kernel.
     * @return The result of the Gaussian kernel function.
     */
    private double kernelGaussian(double z, double width) {
        z /= width;
        return exp(-z);
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
    private double distance(double[][] data, List<Integer> cols, int i, int j) {
        double sum = 0.0;

        for (int col : cols) {
            double d = (data[col][i] - data[col][j]);
            sum += d * d;
        }

        return sum;
    }

    /**
     * Retrieves the list of rows from the given DataSet.
     *
     * @param dataSet The DataSet from which to retrieve the rows.
     * @return A List of integers representing the row numbers in the DataSet.
     */
    private List<Integer> getRows(DataSet dataSet) {
        List<Integer> rows = new ArrayList<>();

        for (int k = 0; k < dataSet.getNumRows(); k++) {
            rows.add(k);
        }

        return rows;
    }

    /**
     * The Eigendecomposition class represents the decomposition of a square matrix into its eigenvalues and
     * eigenvectors. It provides methods to retrieve the eigenvalues, eigenvectors, and the top eigenvalues.
     */
    private class Eigendecomposition {
        private final Matrix k;
        private Matrix D;
        private Matrix V;
        private List<Double> topEigenvalues;

        /**
         * Construct a new Eigendecomposition object with the given matrix.
         *
         * @param k the matrix to be decomposed
         * @throws IllegalArgumentException if the matrix is empty
         */
        public Eigendecomposition(Matrix k) {
            if (k.getNumRows() == 0 || k.getNumColumns() == 0) {
                throw new IllegalArgumentException("Empty matrix to decompose. Please don't do that to me.");
            }

            this.k = k;
        }

        /**
         * Returns the matrix D.
         *
         * @return the matrix D
         */
        public Matrix getD() {
            return this.D;
        }

        /**
         * Returns the matrix V.
         *
         * @return the matrix V
         */
        public Matrix getV() {
            return this.V;
        }

        /**
         * Returns the list of top eigenvalues.
         *
         * @return the list of top eigenvalues
         */
        public List<Double> getTopEigenvalues() {
            return this.topEigenvalues;
        }

        /**
         * Performs eigendecomposition on a given matrix and optionally stores the eigenvectors.
         *
         * @param storeV a flag indicating whether to store the eigenvectors
         * @return the Eigendecomposition object on which this method is invoked
         */
        public Eigendecomposition invoke(boolean storeV) {
            List<Integer> topIndices;

            if (true) {
                EigenDecomposition ed = new EigenDecomposition(new BlockRealMatrix(this.k.toArray()));

                double[] arr = ed.getRealEigenvalues();

                List<Integer> indx = series(arr.length); // 1 2 3...
                topIndices = getTopIndices(arr, indx, Kci.this.threshold);

                this.topEigenvalues = new ArrayList<>();

                for (int t : topIndices) {
                    getTopEigenvalues().add(arr[t]);
                }

                if (storeV) {
                    this.D = new Matrix(topIndices.size(), topIndices.size());

                    for (int i = 0; i < topIndices.size(); i++) {
                        this.D.set(i, i, sqrt(arr[topIndices.get(i)]));
                    }

                    this.V = new Matrix(ed.getEigenvector(0).getDimension(), topIndices.size());

                    for (int i = 0; i < topIndices.size(); i++) {
                        RealVector t = ed.getEigenvector(topIndices.get(i));
                        this.V.assignColumn(i, new Vector(t.toArray()));
                    }
                }
            } else {
                SingularValueDecomposition svd = new SingularValueDecomposition(new BlockRealMatrix(k.toArray()));

                double[] evxAll = svd.getSingularValues();

                List<Integer> indx = series(evxAll.length); // 1 2 3...
                topIndices = getTopIndices(evxAll, indx, Kci.this.threshold);

                if (storeV) {
                    D = new Matrix(topIndices.size(), topIndices.size());

                    for (int i = 0; i < topIndices.size(); i++) {
                        D.set(i, i, topEigenvalues.get(i));
                    }

                    RealMatrix V0 = svd.getV();

                    V = new Matrix(V0.getRowDimension(), topIndices.size());

                    for (int i = 0; i < V.getNumColumns(); i++) {
                        double[] t = V0.getColumn(topIndices.get(i));
                        V.assignColumn(i, new Vector(t));
                    }
                }

                topEigenvalues = new ArrayList<>();

                for (int t : topIndices) {
                    getTopEigenvalues().add(evxAll[t]);
                }
            }

            return this;
        }

    }
}

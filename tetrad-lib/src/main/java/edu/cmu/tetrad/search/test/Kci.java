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
import org.apache.commons.math3.util.FastMath;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static edu.cmu.tetrad.util.StatUtils.median;
import static org.apache.commons.math3.util.FastMath.*;

/**
 *
 * Gives an implementation of the Kernal Independence Test (KCI) by Kun Zhang, which is a
 * general test of conditional independence. The reference is here:
 * <p>
 * Zhang, K., Peters, J., Janzing, D., and Schölkopf, B. (2012). Kernel-based conditional independence
 * test and application in causal discovery. arXiv preprint arXiv:1202.3775.
 * <p>
 * Please see that paper, especially Theorem 4 and Proposition 5.
 * <p>
 * Using optimal kernel bandwidths suggested by Bowman and Azzalini (1997):
 * <p>
 * Bowman, A. W., and Azzalini, A. (1997). Applied smoothing techniques for data analysis: the kernel
 * approach with S-Plus illustrations (Vol. 18). OUP Oxford.
 *
 * @author kunzhang
 * @author Vineet Raghu on 7/3/2016
 * @author josephramsey refactoring 7/4/2018
 * @version $Id: $Id
 */
public class Kci implements IndependenceTest {

    // The supplied data set, standardized
    private final DataSet data;
    // Variables in data
    private final List<Node> variables;
    private final double[] h;
    // A normal distribution with 1 degree of freedom.
    private final NormalDistribution normal = new NormalDistribution(new SynchronizedRandomGenerator(
            new Well44497b(193924L)), 0, 1);
    // Convenience map from nodes to their indices in the list of variables.
    private final Map<Node, Integer> hash;
    // Record of independence facts
    private final Map<IndependenceFact, IndependenceResult> facts = new ConcurrentHashMap<>();
    // The alpha level of the test.
    private double alpha;
    // True if the approximation algorithms should be used instead of Theorems 3 or 4.
    private boolean approximate;
    // Eigenvalues greater than this time the maximum will be kept.
    private double threshold = 0.01;
    // Number of bostraps for Theorem 4 and Proposition 5.
    private int numBootstraps = 5000;
    // Azzalini optimal kernel widths will be multiplied by this.
    private double widthMultiplier = 1.0;
    // Epsilon for Propositio 5.
    private double epsilon = 0.001;
    // True if verbose output should be printed.
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


    /** {@inheritDoc} */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("Method not implemented.");
    }

    /**
     * {@inheritDoc}
     *
     * Returns True if the given independence question is judged true, false if not. The independence question is of the
     * form x _||_ y | z, z = [z1,...,zn], where x, y, z1,...,zn are variables in the list returned by
     * getVariableNames().
     * @see IndependenceResult
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
     * {@inheritDoc}
     *
     * Returns the variable by the given name.
     */
    public Node getVariable(String name) {
        return this.data.getVariable(name);
    }

    /**
     * {@inheritDoc}
     *
     * Returns true if y is determined the variable in z.
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
     * {@inheritDoc}
     *
     * Sets the significance level.
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
     * <p>getCov.</p>
     *
     * @throws java.lang.UnsupportedOperationException Method not implemented.
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
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
     * @return This number.
     * @param result a {@link edu.cmu.tetrad.search.test.IndependenceResult} object
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
     * {@inheritDoc}
     *
     * Returns true if verbose output is printed.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * {@inheritDoc}
     *
     * Sets whether verbose output is printed.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /*
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
            e.printStackTrace();
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
            e.printStackTrace();
            boolean indep = false;
            IndependenceResult result = new IndependenceResult(fact, indep, 0.0, getAlpha());
            this.facts.put(fact, result);
            return result;
        }
    }

    private IndependenceResult theorem4(Matrix kx, Matrix ky, IndependenceFact fact, int N) {

        double T = (1.0 / N) * (kx.times(ky).trace());

        // Eigen decomposition of kx and ky.
        Eigendecomposition eigendecompositionx = new Eigendecomposition(kx).invoke();
        List<Double> evx = eigendecompositionx.getTopEigenvalues();

        Eigendecomposition eigendecompositiony = new Eigendecomposition(ky).invoke();
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

    private IndependenceResult proposition5(Matrix kx, Matrix ky, IndependenceFact fact, int N) {
        double T = (1.0 / N) * kx.times(ky).trace();

        Eigendecomposition eigendecompositionx = new Eigendecomposition(kx).invoke();
        Matrix vx = eigendecompositionx.getV();
        Matrix dx = eigendecompositionx.getD();

        Eigendecomposition eigendecompositiony = new Eigendecomposition(ky).invoke();
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
            Eigendecomposition eigendecompositionu = new Eigendecomposition(uuprod).invoke();
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

    private List<Integer> series(int size) {
        List<Integer> series = new ArrayList<>();
        for (int i = 0; i < size; i++) series.add(i);
        return series;
    }

    private Matrix center(Matrix K, Matrix H) {
        return H.times(K).times(H);
    }

    private double getChisqSample() {
        double z = this.normal.sample();
        return z * z;
    }

    // Optimal bandwidth qsuggested by Bowman and Azzalini (1997) q.31,
    // using MAD.
    private double h(Node x, double[][] _data, Map<Node, Integer> hash) {
        double[] xCol = _data[hash.get(x)];
        double[] g = new double[xCol.length];
        double median = median(xCol);
        for (int j = 0; j < xCol.length; j++) g[j] = abs(xCol[j] - median);
        double mad = median(g);
        return (1.4826 * mad) * pow((4.0 / 3.0) / xCol.length, 0.2);
    }

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

    private Matrix symmetrized(Matrix kx) {
        return (kx.plus(kx.transpose())).scalarMult(0.5);
    }

    private Matrix kernelMatrix(double[][] _data, Node x, List<Node> z, double widthMultiplier,
                                Map<Node, Integer> hash,
                                int N, double[] _h) {

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

    private double kernelGaussian(double z, double width) {
        z /= width;
        return exp(-z);
    }

    // Euclidean distance.
    private double distance(double[][] data, List<Integer> cols, int i, int j) {
        double sum = 0.0;

        for (int col : cols) {
            double d = (data[col][i] - data[col][j]);
            sum += d * d;
        }

        return sum;
    }

    private List<Integer> getRows(DataSet dataSet) {
        List<Integer> rows = new ArrayList<>();

        for (int k = 0; k < dataSet.getNumRows(); k++) {
            rows.add(k);
        }

        return rows;
    }

    private class Eigendecomposition {
        private final Matrix k;
        private Matrix D;
        private Matrix V;
        private List<Double> topEigenvalues;

        public Eigendecomposition(Matrix k) {
            if (k.getNumRows() == 0 || k.getNumColumns() == 0) {
                throw new IllegalArgumentException("Empty matrix to decompose. Please don't do that to me.");
            }

            this.k = k;
        }

        public Matrix getD() {
            return this.D;
        }

        public Matrix getV() {
            return this.V;
        }

        public List<Double> getTopEigenvalues() {
            return this.topEigenvalues;
        }

        public Eigendecomposition invoke() {
            List<Integer> topIndices;

            if (true) {
                EigenDecomposition ed = new EigenDecomposition(new BlockRealMatrix(this.k.toArray()));

                double[] arr = ed.getRealEigenvalues();

                List<Integer> indx = series(arr.length); // 1 2 3...
                topIndices = getTopIndices(arr, indx, Kci.this.threshold);

                this.D = new Matrix(topIndices.size(), topIndices.size());

                for (int i = 0; i < topIndices.size(); i++) {
                    this.D.set(i, i, sqrt(arr[topIndices.get(i)]));
                }

                this.topEigenvalues = new ArrayList<>();

                for (int t : topIndices) {
                    getTopEigenvalues().add(arr[t]);
                }

                this.V = new Matrix(ed.getEigenvector(0).getDimension(), topIndices.size());

                for (int i = 0; i < topIndices.size(); i++) {
                    RealVector t = ed.getEigenvector(topIndices.get(i));
                    this.V.assignColumn(i, new Vector(t.toArray()));
                }
            } else {
                SingularValueDecomposition svd = new SingularValueDecomposition(new BlockRealMatrix(k.toArray()));

                double[] evxAll = svd.getSingularValues();

                List<Integer> indx = series(evxAll.length); // 1 2 3...
                topIndices = getTopIndices(evxAll, indx, Kci.this.threshold);

                D = new Matrix(topIndices.size(), topIndices.size());

                for (int i = 0; i < topIndices.size(); i++) {
                    D.set(i, i, FastMath.sqrt(evxAll[topIndices.get(i)]));
                }

                RealMatrix V0 = svd.getV();

                V = new Matrix(V0.getRowDimension(), topIndices.size());

                for (int i = 0; i < V.getNumColumns(); i++) {
                    double[] t = V0.getColumn(topIndices.get(i));
                    V.assignColumn(i, new Vector(t));
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

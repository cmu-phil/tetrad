package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import edu.pitt.csb.mgm.EigenDecomposition;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.apache.commons.math3.random.SynchronizedRandomGenerator;
import org.apache.commons.math3.random.Well44497b;

import java.util.*;

import static com.google.common.primitives.Doubles.asList;
import static edu.cmu.tetrad.util.MathUtils.logChoose;
import static edu.cmu.tetrad.util.StatUtils.median;
import static java.lang.Math.*;

/***
 * Kernal Independence Test (KCI).
 *
 * Zhang, K., Peters, J., Janzing, D., & Schölkopf, B. (2012). Kernel-based conditional independence
 * test and application in causal discovery. arXiv preprint arXiv:1202.3775.
 *
 * Please see that paper, especially Theorem 4 and Proposition 5.
 *
 * Using optimal kernel bandwidths suggested by Bowman and Azzalini (1997):
 *
 * Bowman, A. W., & Azzalini, A. (1997). Applied smoothing techniques for data analysis: the kernel
 * approach with S-Plus illustrations (Vol. 18). OUP Oxford.
 *
 * @author Vineet Raghu on 7/3/2016
 * @author jdramsey refactoring 7/4/2018
 */
public class Kci implements IndependenceTest, ScoreForFact {

    // Sample size.
    private final int N;

    // Bowman and Azzalini optimal bandwidths for each variable.
    private final double[] h;

    // The supplied data set, standardized
    private final DataSet data;

    // The data stored in vertical columns.
    private final double[][] _data;

    // Variables in data
    private List<Node> variables;

    // The alpha level of the test.
    private double alpha;

    // P value used to judge independence. This is the last p value calculated.
    private double p;

    // Centering matrix.
    private TetradMatrix H;

    // Identity N x N
    private TetradMatrix I;

    // A normal distribution with 1 degree of freedom.
    private NormalDistribution normal = new NormalDistribution(new SynchronizedRandomGenerator(
            new Well44497b(193924L)), 0, 1);

    // True if the approximation algorithms should be used instead of Theorems 3 or 4.
    private boolean approximate = false;

    // Convenience map from nodes to their indices in the list of variables.
    private Map<Node, Integer> hash;

    // Eigenvalues greater than this time the maximum will be kept.
    private double threshold = 0.01;

    // Number of bostraps for Theorem 4 and Proposition 5.
    private int numBootstraps = 5000;

    // Azzalini optimal kernel widths will be multiplied by this.
    private double widthMultiplier = 1.0;

    // List of independent normal(1) samples to be reused.
    private static List<Double> samples = new ArrayList<>();

    // Record of independence facts
    private Map<IndependenceFact, Boolean> facts = new HashMap<>();

    // Record of independence pValues
    private Map<IndependenceFact, Double> pValues = new HashMap<>();

    // Epsilon for Propositio 5.
    private double epsilon = 0.001;

    private boolean verbose = false;
    private boolean fastFDR = false;

    /**
     * Constructor.
     *
     * @param data  The dataset to analyse. Must be continuous.
     * @param alpha The alpha value of the test.
     */
    public Kci(DataSet data, double alpha) {
        this.data = DataUtils.standardizeData(data);
        this.variables = data.getVariables();
        this._data = this.data.getDoubleData().transpose().toArray();
        this.N = this.data.getNumRows();
        this.I = TetradMatrix.identity(N);

        TetradMatrix Ones = new TetradMatrix(N, 1);
        for (int j = 0; j < N; j++) Ones.set(j, 0, 1);

        this.H = TetradMatrix.identity(N).minus(Ones.times(Ones.transpose()).scalarMult(1.0 / N));

        this.alpha = alpha;
        this.p = -1;

        hash = new HashMap<>();

        for (int i = 0; i < getVariables().size(); i++) {
            hash.put(getVariables().get(i), i);
        }

        h = new double[this.data.getNumColumns()];
        double sum = 0.0;
        int count = 0;

        for (int i = 0; i < this.data.getNumColumns(); i++) {
            h[i] = h(this.data.getVariables().get(i).toString());

            if (h[i] != 0) {
                sum += h[i];
                count++;
            }
        }

        double avg = sum / count;

        for (int i = 0; i < h.length; i++) {
            if (h[i] == 0) h[i] = avg;
        }
    }

    //====================================PUBLIC METHODS==================================//

    /**
     * Returns an Independence test for a subset of the variables.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true if the given independence question is judged true, false if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are variables in the list returned by
     * getVariableNames().
     */
    public boolean isIndependent(Node x, Node y, List<Node> z) {
        boolean independent;

        if (Thread.currentThread().isInterrupted()) {
            return false;
        }

        IndependenceFact fact = new IndependenceFact(x, y, z);

        if (facts.get(fact) != null) {
            independent = facts.get(fact);
            this.p = pValues.get(fact);
        } else {
            if (z.isEmpty()) {
                independent = isIndependentUnconditional(x, y, fact);
            } else {
                independent = isIndependentConditional(x, y, z, fact);
            }

            facts.put(fact, independent);
        }

        if (verbose) {
            double p = getPValue();

            if (independent) {
                System.out.println(fact + " INDEPENDENT p = " + p);
                TetradLogger.getInstance().log("info", fact + " Independent");

            } else {
                System.out.println(fact + " dependent p = " + p);
                TetradLogger.getInstance().log("info", fact.toString());
            }
        }

        return independent;
    }

    /**
     * Returns true if the given independence question is judged true, false if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are variables in the list returned by
     * getVariableNames().
     */
    public boolean isIndependent(Node x, Node y, Node... z) {
        LinkedList<Node> thez = new LinkedList<>();
        Collections.addAll(thez, z);
        return isIndependent(x, y, thez);
    }

    /**
     * Returns true if the given independence question is judged false, true if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are variables in the list returned by
     * getVariableNames().
     */
    public boolean isDependent(Node x, Node y, List<Node> z) {
        return !isIndependent(x, y, z);
    }

    /**
     * Returns true if the given independence question is judged false, true if not. The independence question is of the
     * form x _||_ y | z, z = <z1,...,zn>, where x, y, z1,...,zn are variables in the list returned by
     * getVariableNames().
     */
    public boolean isDependent(Node x, Node y, Node... z) {
        LinkedList<Node> thez = new LinkedList<>();
        Collections.addAll(thez, z);
        return isDependent(x, y, thez);
    }

    /**
     * Returns the probability associated with the most recently executed independence test, of Double.NaN if p value is
     * not meaningful for tis test.
     */
    public double getPValue() {
        return p;
    }

    /**
     * Returns the list of variables over which this independence checker is capable of determinining independence
     * relations.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Returns the variable by the given name.
     */
    public Node getVariable(String name) {
        return data.getVariable(name);
    }

    /**
     * Returns the list of names for the variables in getNodesInEvidence.
     */
    public List<String> getVariableNames() {
        return data.getVariableNames();
    }

    /**
     * Returns true if y is determined the variable in z.
     */
    public boolean determines(List<Node> z, Node y) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the significance level of the independence test.
     *
     * @throws UnsupportedOperationException if there is no significance level.
     */
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the significance level.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * @return The data model for the independence test.
     */
    public DataModel getData() {
        return data;
    }


    public ICovarianceMatrix getCov() {
        throw new UnsupportedOperationException();
    }

    public List<DataSet> getDataSets() {
        LinkedList<DataSet> L = new LinkedList<>();
        L.add(data);
        return L;
    }

    public int getSampleSize() {
        return data.getNumRows();
    }

    public List<TetradMatrix> getCovMatrices() {
        throw new UnsupportedOperationException();
    }

    public double getScore() {
        return getAlpha() - getPValue();
    }

    @Override
    public double getScoreForFact(IndependenceFact fact) {
        return getAlpha() - pValues.get(fact);
    }

    public boolean isApproximate() {
        return approximate;
    }

    public void setApproximate(boolean approximate) {
        this.approximate = approximate;
    }

    private double getWidthMultiplier() {
        return widthMultiplier;
    }

    public void setWidthMultiplier(double widthMultiplier) {
        if (widthMultiplier <= 0) throw new IllegalStateException("Width must be > 0");
        this.widthMultiplier = widthMultiplier;
    }

    private int getNumBootstraps() {
        return numBootstraps;
    }

    public void setNumBootstraps(int numBootstraps) {
        if (numBootstraps < 1) throw new IllegalArgumentException("Num bootstraps should be >= 1: " + numBootstraps);
        this.numBootstraps = numBootstraps;
    }


    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        if (threshold < 0.0) throw new IllegalArgumentException("Threshold must be >= 0.0: " + threshold);
        this.threshold = threshold;
    }

    public double getEpsilon() {
        return epsilon;
    }

    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }

    //====================================PRIVATE METHODS==================================//

    /**
     * KCI independence for the unconditional case. Uses Theorem 4 from the paper.
     *
     * @return true just in case independence holds.
     */
    private boolean isIndependentUnconditional(Node x, Node y, IndependenceFact fact) {
        TetradMatrix kx = center(kernelMatrix(_data, x, null, getWidthMultiplier()));
        TetradMatrix ky = center(kernelMatrix(_data, y, null, getWidthMultiplier()));

        try {
            if (isApproximate()) {
                double sta = kx.times(ky).trace();
                double mean_appr = kx.trace() * ky.trace() / N;
                double var_appr = 2 * kx.times(kx).trace() * ky.times(ky).trace() / (N * N);
                double k_appr = mean_appr * mean_appr / var_appr;
                double theta_appr = var_appr / mean_appr;
                double p_appr = 1.0 - new GammaDistribution(k_appr, theta_appr).cumulativeProbability(sta);
                p = p_appr;
                pValues.put(fact, p);

                if (fastFDR) {
                    final int d1 = 0; // reference
                    final int d2 = fact.getZ().size();
                    final int v = variables.size() - 2;

                    double alpha2 = (exp(log(alpha) + logChoose(v, d1) - logChoose(v, d2)));
                    final boolean independent = p > alpha2;

//                    if (independent) {
//                        System.out.println(fact + " INDEPENDENT p = " + p);
//                        TetradLogger.getInstance().log("info", fact + " Independent");
//
//                    } else {
//                        System.out.println(fact + " dependent p = " + p);
//                        TetradLogger.getInstance().log("info", fact.toString());
//                    }

                    return independent;
                } else {
                    final boolean independent = p > alpha;

//                    if (independent) {
//                        System.out.println(fact + " INDEPENDENT p = " + p);
//                        TetradLogger.getInstance().log("info", fact + " Independent");
//
//                    } else {
//                        System.out.println(fact + " dependent p = " + p);
//                        TetradLogger.getInstance().log("info", fact.toString());
//                    }

                    return independent;
                }
            } else {
                return theorem4(kx, ky, fact);
            }
        } catch (Exception e) {
            e.printStackTrace();
            pValues.put(fact, 0.0);
            facts.put(fact, false);
            return false;
        }
    }

    /**
     * KCI independence for the conditional case. Uses Theorem 3 from the paper.
     *
     * @return true just in case independence holds.
     */
    private boolean isIndependentConditional(Node x, Node y, List<Node> z, IndependenceFact fact) {
        TetradMatrix kx = null;
        TetradMatrix ky = null;

        try {
            TetradMatrix KXZ = center(kernelMatrix(_data, x, z, getWidthMultiplier()));
            TetradMatrix Ky = center(kernelMatrix(_data, y, null, getWidthMultiplier()));
            TetradMatrix KZ = center(kernelMatrix(_data, null, z, getWidthMultiplier()));

            TetradMatrix Rz = (KZ.plus(I.scalarMult(epsilon)).inverse().scalarMult(epsilon));

            kx = symmetrized(Rz.times(KXZ).times(Rz.transpose()));
            ky = symmetrized(Rz.times(Ky).times(Rz.transpose()));

            return proposition5(kx, ky, fact);
        } catch (Exception e) {
            e.printStackTrace();
            pValues.put(fact, 0.0);
            facts.put(fact, false);
            return false;
        }
    }

    private boolean theorem4(TetradMatrix kx, TetradMatrix ky, IndependenceFact fact) {

        double T = (1.0 / N) * (kx.times(ky).trace());

        // Eigen decomposition of kx and ky.
        Eigendecomposition eigendecompositionx = new Eigendecomposition(kx).invoke();
        List<Double> evx = eigendecompositionx.getTopEigenvalues();

        Eigendecomposition eigendecompositiony = new Eigendecomposition(ky).invoke();
        List<Double> evy = eigendecompositiony.getTopEigenvalues();

        // Calculate formula (9).
        int sum = 0;

        for (int j = 0; j < getNumBootstraps(); j++) {
            double tui = 0.0;

            for (double lambdax : evx) {
                for (double lambday : evy) {
                    tui += lambdax * lambday * getChisqSample();
                }
            }

            tui /= (double) (N * N);

            if (tui > T) sum++;
        }

        // Calculate p.
        p = sum / (double) getNumBootstraps();
        pValues.put(fact, this.p);

        if (fastFDR) {
            final int d1 = 0; // reference
            final int d2 = fact.getZ().size();
            final int v = variables.size() - 2;

            double alpha2 = (exp(log(alpha) + logChoose(v, d1) - logChoose(v, d2)));
            final boolean independent = p > alpha2;

            if (independent) {
                System.out.println(fact + " INDEPENDENT p = " + p);
                TetradLogger.getInstance().log("info", fact + " Independent");

            } else {
                System.out.println(fact + " dependent p = " + p);
                TetradLogger.getInstance().log("info", fact.toString());
            }

            return independent;
        } else {
            final boolean independent = p > alpha;

            if (independent) {
                System.out.println(fact + " INDEPENDENT p = " + p);
                TetradLogger.getInstance().log("info", fact + " Independent");

            } else {
                System.out.println(fact + " dependent p = " + p);
                TetradLogger.getInstance().log("info", fact.toString());
            }

            return independent;
        }
    }

    private boolean proposition5(TetradMatrix kx, TetradMatrix ky, IndependenceFact fact) {
        double T = (1.0 / N) * kx.times(ky).trace();

        Eigendecomposition eigendecompositionx = new Eigendecomposition(kx).invoke();
        TetradMatrix vx = eigendecompositionx.getV();
        TetradMatrix dx = eigendecompositionx.getD();

        Eigendecomposition eigendecompositiony = new Eigendecomposition(ky).invoke();
        TetradMatrix vy = eigendecompositiony.getV();
        TetradMatrix dy = eigendecompositiony.getD();

        // VD
        TetradMatrix vdx = vx.times(dx);
        TetradMatrix vdy = vy.times(dy);

        final int prod = vx.columns() * vy.columns();
        TetradMatrix UU = new TetradMatrix(N, prod);

        // stack
        for (int i = 0; i < vx.columns(); i++) {
            for (int j = 0; j < vy.columns(); j++) {
                for (int k = 0; k < N; k++) {
                    UU.set(k, i * dy.columns() + j, vdx.get(k, i) * vdy.get(k, j));
                }
            }
        }

        TetradMatrix uuprod = prod > N ? UU.times(UU.transpose()) : UU.transpose().times(UU);

        if (isApproximate()) {
            double sta = kx.times(ky).trace();
            double mean_appr = uuprod.trace();
            double var_appr = 2.0 * uuprod.times(uuprod).trace();
            double k_appr = mean_appr * mean_appr / var_appr;
            double theta_appr = var_appr / mean_appr;
            double p = 1.0 - new GammaDistribution(k_appr, theta_appr).cumulativeProbability(sta);
            pValues.put(fact, p);
            return p > getAlpha();
        } else {

            // Get top eigenvalues of that.
            Eigendecomposition eigendecompositionu = new Eigendecomposition(uuprod).invoke();
            List<Double> eigenu = eigendecompositionu.getTopEigenvalues();

            // We're going to reuse the samples.
            int sampleCount = -1;

            // Calculate formulas (13) and (14).
            int sum = 0;

            for (int j = 0; j < getNumBootstraps(); j++) {
                double s = 0.0;

                for (double lambdaStar : eigenu) {
                    s += lambdaStar * getChisqSample();
                }

                s *= 1.0 / N;

                if (s > T) sum++;
            }

            this.p = sum / (double) getNumBootstraps();
            pValues.put(fact, this.p);

            if (fastFDR) {
                final int d1 = 0; // reference
                final int d2 = fact.getZ().size();
                final int v = variables.size() - 2;

                double alpha2 = (exp(log(alpha) + logChoose(v, d1) - logChoose(v, d2)));
                final boolean independent = p > alpha2;

                if (independent) {
                    System.out.println(fact + " INDEPENDENT p = " + p);
                    TetradLogger.getInstance().log("info", fact + " Independent");

                } else {
                    System.out.println(fact + " dependent p = " + p);
                    TetradLogger.getInstance().log("info", fact.toString());
                }

                return independent;
            } else {
                final boolean independent = p > alpha;

                if (independent) {
                    System.out.println(fact + " INDEPENDENT p = " + p);
                    TetradLogger.getInstance().log("info", fact + " Independent");

                } else {
                    System.out.println(fact + " dependent p = " + p);
                    TetradLogger.getInstance().log("info", fact.toString());
                }

                return independent;
            }
        }
    }

    private List<Integer> series(int size) {
        List<Integer> series = new ArrayList<>();
        for (int i = 0; i < size; i++) series.add(i);
        return series;
    }

    private TetradMatrix center(TetradMatrix K) {
        return H.times(K).times(H);
    }

    private double getChisqSample() {
        double z = normal.sample();
        return z * z;
    }

    // Optimal bandwidth qsuggested by Bowman and Azzalini (1997) q.31,
    // using MAD.
    private double h(String x) {
        double[] xCol = _data[hash.get(data.getVariable(x))];
        double[] g = new double[xCol.length];
        double median = median(xCol);
        for (int j = 0; j < xCol.length; j++) g[j] = abs(xCol[j] - median);
        double mad = median(g);
        return (1.4826 * mad) * pow((4.0 / 3.0) / xCol.length, 0.2);
    }

    private List<Integer> getTopIndices(List<Double> prod, List<Integer> allIndices, double threshold) {
        double maxEig = prod.get(allIndices.get(0));

        List<Integer> indices = new ArrayList<>();

        for (int i : allIndices) {
            if (prod.get(i) > maxEig * threshold) {
                indices.add(i);
            }
        }

        return indices;
    }

    private TetradMatrix symmetrized(TetradMatrix kx) {
        return (kx.plus(kx.transpose())).scalarMult(0.5);
    }

    private TetradMatrix kernelMatrix(double[][] _data, Node x, List<Node> z, double widthMultiplier) {

        List<Integer> _z = new ArrayList<>();

        if (x != null) {
            _z.add(hash.get(x));
        }

        if (z != null) {
            for (Node z2 : z) {
                _z.add(hash.get(z2));
            }
        }

        double h = getH(_z);

        TetradMatrix result = new TetradMatrix(N, N);

        for (int i = 0; i < N; i++) {
            for (int j = i + 1; j < N; j++) {
                double d = distance(_data, _z, i, j);
                final double k = kernelGaussian(d, widthMultiplier * h);
                result.set(i, j, k);
                result.set(j, i, k);
            }
        }

        final double k = kernelGaussian(0, widthMultiplier * h);

        for (int i = 0; i < N; i++) {
            result.set(i, i, k);
        }

        return result;
    }

    private double getH(List<Integer> _z) {
        double h = 0;

        for (int c : _z) {
            if (this.h[c] > h) {
                h = this.h[c];
            }
        }

        h *= sqrt(_z.size());
        return h;
    }

    private double kernelGaussian(double z, double width) {
        if (width == 0) {
            throw new IllegalArgumentException("Width is zero.");
        }

        z /= width;
        return Math.exp(-z * z);
    }

    // Euclidean distance.
    private double distance(double[][] data, List<Integer> cols, int i, int j) {
        double sum = 0.0;

        for (int col : cols) {
            double d = (data[col][i] - data[col][j]) / 2;

            if (!Double.isNaN(d)) {
                sum += d * d;
            }
        }

        return sqrt(sum);
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private class Eigendecomposition {
        private TetradMatrix k;
        private List<Integer> topIndices;
        private TetradMatrix D;
        private TetradMatrix V;
        private List<Double> topEigenvalues;

        public Eigendecomposition(TetradMatrix k) {
            if (k.rows() == 0 || k.columns() == 0) {
                throw new IllegalArgumentException("Empty matrix to decompose. Please don't do that to me.");
            }

            this.k = k;
        }

        public TetradMatrix getD() {
            return D;
        }

        public TetradMatrix getV() {
            return V;
        }

        public List<Double> getTopEigenvalues() {
            return topEigenvalues;
        }

        public Eigendecomposition invoke() {
            if (true) {
                EigenDecomposition ed = new EigenDecomposition(k.getRealMatrix());

                List<Double> evxAll = asList(ed.getRealEigenvalues());
                List<Integer> indx = series(evxAll.size()); // 1 2 3...
                topIndices = getTopIndices(evxAll, indx, getThreshold());

                D = new TetradMatrix(topIndices.size(), topIndices.size());

                for (int i = 0; i < topIndices.size(); i++) {
                    D.set(i, i, Math.sqrt(evxAll.get(topIndices.get(i))));
                }

                topEigenvalues = new ArrayList<>();

                for (int t : topIndices) {
                    getTopEigenvalues().add(evxAll.get(t));
                }

                V = new TetradMatrix(ed.getEigenvector(0).getDimension(), topIndices.size());

                for (int i = 0; i < topIndices.size(); i++) {
                    RealVector t = ed.getEigenvector(topIndices.get(i));
                    V.assignColumn(i, new TetradVector(t));
                }
            } else {
                SingularValueDecomposition svd = new SingularValueDecomposition(k.getRealMatrix());

                List<Double> evxAll = asList(svd.getSingularValues());

                List<Integer> indx = series(evxAll.size()); // 1 2 3...
                topIndices = getTopIndices(evxAll, indx, getThreshold());

                D = new TetradMatrix(topIndices.size(), topIndices.size());

                for (int i = 0; i < topIndices.size(); i++) {
                    D.set(i, i, Math.sqrt(evxAll.get(topIndices.get(i))));
                }

                RealMatrix V0 = svd.getV();

                V = new TetradMatrix(V0.getRowDimension(), topIndices.size());

                for (int i = 0; i < V.columns(); i++) {
                    double[] t = V0.getColumn(topIndices.get(i));
                    V.assignColumn(i, new TetradVector(t));
                }

                topEigenvalues = new ArrayList<>();

                for (int t : topIndices) {
                    getTopEigenvalues().add(evxAll.get(t));
                }

            }

            return this;
        }
    }
}
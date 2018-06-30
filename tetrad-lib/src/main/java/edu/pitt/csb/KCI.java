package edu.pitt.csb;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SearchLogUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.pitt.csb.mgm.EigenDecomposition;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.random.SynchronizedRandomGenerator;
import org.apache.commons.math3.random.Well44497b;

import java.util.*;

import static edu.cmu.tetrad.util.StatUtils.median;
import static java.lang.Math.abs;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

/**
 * Kernel Based Conditional Independence Test
 * Code Written by: Vineet Raghu
 * Test published in: Kernel-based Conditional Independence Test and Application in Causal Discovery (Zhang et al.)
 *
 * @author vinee_000 on 7/3/2016
 * @author jdramsey refactoring 6/17/2018
 */
public class KCI implements IndependenceTest {
    private final int N;
    private final double[] h;
    private DataSet data;
    private double[][] _data;
    private double alpha;
    private double lastP;
    private TetradMatrix H;
    private TetradMatrix eye;
    private TetradMatrix lamEye;
    private ChiSquaredDistribution chisq = new ChiSquaredDistribution(new SynchronizedRandomGenerator(
            new Well44497b(193924L)), 1);
    private boolean bootstrap = true;
    private boolean approx = false;
    private Map<Node, Integer> hash;
    private double threshold = 1e-5;
    private int n_bootstrap = 100;
    private double width = 1.0;

    public KCI(DataSet data, double threshold) {
//        this.data = DataUtils.standardizeData(data);
        this.data = data;
        this._data = this.data.getDoubleData().scalarMult(.3).transpose().toArray();
        N = data.getNumRows();
        double lambda = 1.0 / N;
        this.eye = TetradMatrix.identity(N);
        this.H = eye.minus(TetradMatrix.ones(N, N).scalarMult(lambda));
        this.lamEye = eye.scalarMult(lambda);
        this.alpha = threshold;
        this.lastP = -1;

        hash = new HashMap<>();

        for (int i = 0; i < getVariables().size(); i++) {
            hash.put(getVariables().get(i), i);
        }

        h = new double[data.getNumColumns()];

        for (int i = 0; i < data.getNumColumns(); i++) {
            h[i] = h(data.getVariables().get(i).toString());
        }
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

        if (z.isEmpty()) {
            independent = isIndependentUncon(x, y);
        } else {
            independent = isIndependentCon(x, y, z);
        }

        if (independent) {
            System.out.println(SearchLogUtils.independenceFact(x, y, z) + " Independent");
        } else {
            System.out.println(SearchLogUtils.independenceFact(x, y, z));
        }

        return independent;
    }

    private boolean isIndependentUncon(Node x, Node y) {
        double width = getWidth();

        int[] _x = new int[]{hash.get(x)};
        int[] _y = new int[]{hash.get(y)};

        TetradMatrix kx = H.times(kernelMatrix(_data, width, _x)).times(H);
        TetradMatrix ky = H.times(kernelMatrix(_data, width, _y)).times(H);

        if (isApprox()) {
            double trace = kx.times(ky).trace();
            double mean_appr = kx.trace() * ky.trace() / N;
            double var_appr = 2 * kx.times(kx).trace() * ky.times(ky).trace() / (N * N);//can optimize by not actually performing matrix multiplication
            double k_appr = mean_appr * mean_appr / var_appr;
            double theta_appr = var_appr / mean_appr;
            GammaDistribution g = new GammaDistribution(k_appr, theta_appr);
            double p_appr = 1.0 - g.cumulativeProbability(trace);
            lastP = p_appr;
            return p_appr > alpha;
        } else {
            return compareToNull(kx, ky);
        }

    }

    private boolean compareToNull(TetradMatrix kx, TetradMatrix ky) {
        double trace = kx.times(ky).trace();

        EigenDecomposition ed1;
        EigenDecomposition ed2;

        try {
            ed1 = new EigenDecomposition(kx.plus(kx.transpose()).scalarMult(0.5).getRealMatrix());
            ed2 = new EigenDecomposition(ky.plus(ky.transpose()).scalarMult(0.5).getRealMatrix());
        } catch (Exception e) {
            System.out.println("Eigenvalue didn't converge");
            lastP = threshold + 0.01;
            return true;
        }

        double[] ev1 = ed1.getRealEigenvalues();
        double[] ev2 = ed2.getRealEigenvalues();
        double[] eigProd = new double[ev1.length * ev2.length];
        double maxEig = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < ev1.length; i++) {
            for (int j = 0; j < ev2.length; j++) {
                double curr = ev1[i] * ev2[j];
                if (curr > maxEig)
                    maxEig = curr;
                eigProd[i * ev2.length + j] = curr;
            }
        }

        ArrayList<Double> d = new ArrayList<>();

        for (double anEigProd : eigProd) {
            if (anEigProd > maxEig * threshold)
                d.add(anEigProd);
        }

        if (d.size() * N < 1E6) {
            double[][] f_rand1 = new double[d.size()][n_bootstrap];
            for (int i = 0; i < d.size(); i++) {
                for (int j = 0; j < n_bootstrap; j++) {
                    f_rand1[i][j] = chisq.sample();
                }
            }

            double[][] data = new double[1][d.size()];

            for (int i = 0; i < d.size(); i++) {
                data[0][i] = d.get(i);
            }

            TetradMatrix f_rand = new TetradMatrix(f_rand1);
            TetradMatrix ep = new TetradMatrix(data);
            ep = ep.scalarMult(1 / (double) N);
            double[][] nullDist = ep.times(f_rand).toArray();
            int sum = 0;

            for (double[] aNullDist : nullDist) {
                for (double anANullDist : aNullDist) {
                    if (anANullDist > trace)
                        sum++;
                }
            }

            double pval = (double) sum /  n_bootstrap;
            lastP = pval;

            return pval > alpha;
        } else {
            return false;
        }
    }

    private boolean isIndependentCon(Node x, Node y, List<Node> z) {
        try {
            n_bootstrap = 100;
            double width = getWidth();

            int[] colsY = new int[1];
            int[] colsXZ = new int[z.size() + 1];
            int[] colsZ = new int[z.size()];
            colsY[0] = hash.get(y);
            colsXZ[0] = hash.get(x);
            for (int j = 0; j < z.size(); j++) {
                colsZ[j] = hash.get(z.get(j));
                colsXZ[j + 1] = hash.get(z.get(j));
            }

            //  System.out.println("Time to setup preliminary kernel matrices: " + (System.nanoTime()-time));
            TetradMatrix Kx = H.times(kernelMatrix(_data, width, colsXZ)).times(H);
            TetradMatrix Ky = H.times(kernelMatrix(_data, width, colsY)).times(H);

            TetradMatrix KZ = new TetradMatrix(N, z.size());

            for (int i = 0; i < N; i++) {
                for (int j = 0; j < z.size(); j++) {
                    KZ.set(i, j, data.getDouble(i, data.getColumn(z.get(j))));
                }
            }

            KZ = H.times(kernelMatrix(_data, width, colsZ).times(H));
            KZ = eye.minus(KZ.times((KZ.plus(lamEye).inverse())));

            TetradMatrix kx = KZ.times(Kx).times(KZ.transpose());
            TetradMatrix ky = KZ.times(Ky).times(KZ.transpose());

//            if (true) {
            return compareToNull(kx, ky);
//            }

//            EigenDecomposition ed1;
//            EigenDecomposition ed2;
//
//            try {
//                ed1 = new EigenDecomposition(kx.plus(kx.transpose()).scalarMult(.5).getRealMatrix());
//                ed2 = new EigenDecomposition(ky.plus(ky.transpose()).scalarMult(.5).getRealMatrix());
//            } catch (Exception e) {
//                System.out.println("Eigenvalue didn't converge");
//                return false;
//            }
//
//            double[] evalues1 = ed1.getRealEigenvalues();
//            double[] evalues2 = ed2.getRealEigenvalues();
//
//            double max1 = 0;
//
//            for (double v : evalues1) {
//                if (v > max1)
//                    max1 = v;
//
//            }
//
//            double max2 = 0;
//
//            for (double v : evalues2) {
//                if (v > max2)
//                    max2 = v;
//            }
//
//            ArrayList<Double> eigenValuesX = new ArrayList<>();
//            ArrayList<Double> eigenValuesY = new ArrayList<>();
//
//            List<TetradVector> separateX = new ArrayList<>();
//            List<TetradVector> separateY = new ArrayList<>();
//
//            for (int i = 0; i < evalues1.length; i++) {
//                if (evalues1[i] > max1 * threshold) {
//                    eigenValuesX.add(evalues1[i]);
//                    separateX.add(new TetradVector(ed1.getEigenvector(i).toArray()));
//                }
//            }
//
//            for (int i = 0; i < evalues2.length; i++) {
//                if (evalues2[i] > max2 * threshold) {
//                    eigenValuesY.add(evalues2[i]);
//                    separateY.add(new TetradVector(ed2.getEigenvector(i).toArray()));
//                }
//            }
//
//            TetradMatrix eigenvectorsX = new TetradMatrix(this.N, separateX.size());
//            TetradMatrix eigenvectorsY = new TetradMatrix(this.N, separateY.size());
//
//            for (int i = 0; i < eigenValuesX.size(); i++) {
//                eigenvectorsX.assignColumn(i, separateX.get(i));
//            }
//
//            for (int i = 0; i < eigenValuesY.size(); i++) {
//                eigenvectorsY.assignColumn(i, separateY.get(i));
//            }
//
//            return judge(n_bootstrap, threshold, trace, eigenValuesX, eigenValuesY, eigenvectorsX, eigenvectorsY);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

//    private boolean judge(int n_bootstrap, double threshold, double trace,
//                          ArrayList<Double> eigenValuesX, ArrayList<Double> eigenValuesY,
//                          TetradMatrix eigenvectorsX, TetradMatrix eigenvectorsY) {
//        TetradMatrix DX = new TetradMatrix(eigenValuesX.size(), eigenValuesX.size());
//        TetradMatrix DY = new TetradMatrix(eigenValuesY.size(), eigenValuesY.size());
//
//        for (int i = 0; i < eigenValuesX.size(); i++) {
//            DX.set(i, i, Math.sqrt(eigenValuesX.get(i)));
//        }
//
//        for (int i = 0; i < eigenValuesY.size(); i++) {
//            DY.set(i, i, Math.sqrt(eigenValuesY.get(i)));
//        }
//
//        TetradMatrix prodX = eigenvectorsX.times(DX);
//        TetradMatrix prodY = eigenvectorsY.times(DY);
//
//        int numEigenvalueCombinations = prodX.columns() * prodY.columns();
//        TetradMatrix eigenCombinations = new TetradMatrix(N, numEigenvalueCombinations);
//
//        for (int i = 0; i < prodX.columns(); i++) {
//            for (int j = 0; j < prodY.columns(); j++) {
//                for (int k = 0; k < N; k++) {
//                    eigenCombinations.set(k, i * prodY.columns() + j, prodX.get(k, i) * prodY.get(k, j));
//                }
//            }
//        }
//
//        if (isApprox()) {
//            TetradMatrix uu_prod = eigenCombinations.transpose().times(eigenCombinations);
//            double mean_appr = uu_prod.trace();
//            double var_appr = 2 * uu_prod.times(uu_prod).trace();
//            double k_appr = mean_appr * mean_appr / var_appr;
//            double theta_appr = var_appr / mean_appr;
//            GammaDistribution g = new GammaDistribution(k_appr, theta_appr);
//
//            double p_appr = 1 - g.cumulativeProbability(trace);
//            lastP = p_appr;
//            return p_appr > alpha;
//        } else {
//            TetradMatrix prod = eigenCombinations.transpose().times(eigenCombinations);
//
//            EigenDecomposition ee;
//            try {
//                ee = new EigenDecomposition(prod.getRealMatrix());
//            } catch (Exception e) {
//                System.out.println("Eigenvalue Didn't converge conditional");
//                return true;
//            }
//
//            double[] evals = ee.getRealEigenvalues();
//            double[] valsToKeep = new double[numEigenvalueCombinations];
//            Arrays.sort(evals);
//            int count = 0;
//            for (int i = evals.length - 1; i >= 0; i--) {
//                valsToKeep[count] = evals[i];
//                count++;
//            }
//
//            double max = valsToKeep[0];
//            ArrayList<Double> finalVals = new ArrayList<>();
//
//            for (double val : valsToKeep) {
//                if (val >= max * threshold)
//                    finalVals.add(val);
//            }
//
//            if (finalVals.size() * N < 1E6) {
//                return compareToNull2(n_bootstrap, trace, finalVals);
//            } else {
//                System.out.println("Unimplemented iteratively calculating null");
//                return false;
//            }
//        }
//    }

//    private boolean compareToNull2(int n_bootstrap, double trace, ArrayList<Double> finalVals) {
//        double[][] frand1 = new double[finalVals.size()][n_bootstrap];
//        for (int i = 0; i < finalVals.size(); i++) {
//            for (int j = 0; j < n_bootstrap; j++) {
//                frand1[i][j] = chisq.sample();
//            }
//        }
//
//        double[][] eiguu = new double[1][finalVals.size()];
//
//        for (int j = 0; j < finalVals.size(); j++) {
//            eiguu[0][j] = finalVals.get(j);
//        }
//
//        TetradMatrix fr = new TetradMatrix(frand1);
//        TetradMatrix eig_uu = new TetradMatrix(eiguu);
//        TetradMatrix nullDist = eig_uu.times(fr);
//
//        int sum = 0;
//
//        for (int i = 0; i < nullDist.columns(); i++) {
//            if (nullDist.get(0, i) > trace)
//                sum++;
//        }
//
//        lastP = sum / (double) n_bootstrap;
//
//        return lastP > alpha;
//    }

    private TetradMatrix kernelMatrix(double[][] _data, double width, int[] cols) {
        TetradMatrix result = new TetradMatrix(N, N);
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                double d = distance(_data, cols, i, j);

                double h = 0.0;

                for (int c : cols) {
                    if (this.h[c] > h) {
                        h = this.h[c];
                    }
                }

                h *= sqrt(cols.length);

                result.set(i, j, kernelGaussian(d, width, h));
            }
        }
        return result;
    }

    private double kernelGaussian(double z, double width, double h) {
        z /= width * h;
        return Math.exp(-z);
    }

    // Euclidean distance.
    private double distance(double[][] data, int[] yCols, int i, int j) {
        double sum = 0.0;

        for (int yCol : yCols) {
            double d = data[yCol][i] - data[yCol][j];

            if (!Double.isNaN(d)) {
                sum += d * d;
            }
        }

        return sum;
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
        return lastP;
    }

    /**
     * Returns the list of variables over which this independence checker is capable of determinining independence
     * relations.
     */
    public List<Node> getVariables() {
        return data.getVariables();
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

        return false;
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
    public void setAlpha(double alpha2) {
        alpha = alpha2;
    }

    /**
     * '
     *
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

    public boolean isBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(boolean bootstrap) {
        this.bootstrap = bootstrap;
    }

    private boolean isApprox() {
        return approx;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        if (width <= 0) throw new IllegalStateException("Width must be > 0");
        this.width = width;
    }
}

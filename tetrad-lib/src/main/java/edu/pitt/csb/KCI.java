package edu.pitt.csb;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SearchLogUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.pitt.csb.mgm.EigenDecomposition;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.random.SynchronizedRandomGenerator;
import org.apache.commons.math3.random.Well44497b;

import java.util.*;

/**
 * Kernel Based Conditional Independence Test
 * Code Written by: Vineet Raghu
 * Test published in: Kernel-based Conditional Independence Test and Application in Causal Discovery (Zhang et al.)
 *
 * @author vinee_000 on 7/3/2016
 * @author jdramsey refactoring 6/17/2018
 */
public class KCI implements IndependenceTest {
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

    public KCI(DataSet data, double threshold) {
        this.data = DataUtils.standardizeData(data);
        this._data = this.data.getDoubleData().transpose().toArray();
        int T = data.getNumRows();
        this.eye = TetradMatrix.identity(T);
        this.H = eye.minus(TetradMatrix.ones(T, T).scalarMult(1.0 / T));
        double lambda = 1E-3;
        this.lamEye = eye.scalarMult(lambda);
        this.alpha = threshold;
        this.lastP = -1;
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
        System.out.println("Testing " + SearchLogUtils.independenceFact(x, y, z));

        if (z.isEmpty()) {
            return isIndependentUncon(x, y);
        } else {
            return isIndependentCon(x, y, z);
        }
    }

    private boolean isIndependentUncon(Node x, Node y) {
        int T = data.getNumRows();

        double[] xArr = _data[data.getColumn(x)];
        double[] yArr = _data[data.getColumn(y)];

        int T_BS = 1000;
        double thresh = 1E-6;
        double width = .8;

        if (T > 200)
            width = 0.5;
        if (T > 1200)
            width = 0.3;

        double theta = 1.0 / (width * width);
        double[] temp = new double[2];
        temp[0] = theta;
        temp[1] = 1;

        TetradMatrix kx = H.times(kernel(xArr, xArr, temp)).times(H);
        TetradMatrix ky = H.times(kernel(yArr, yArr, temp)).times(H);

        double trace = kx.times(ky).trace();

        if (isApprox()) {
            double mean_appr = kx.trace() * ky.trace() / T;
            double var_appr = 2 * kx.times(kx).trace() * ky.times(ky).trace() / (T * T);//can optimize by not actually performing matrix multiplication
            double k_appr = mean_appr * mean_appr / var_appr;
            double theta_appr = var_appr / mean_appr;
            GammaDistribution g = new GammaDistribution(k_appr, theta_appr);
            double p_appr = 1.0 - g.cumulativeProbability(trace);
            lastP = p_appr;
            return p_appr > alpha;
        } else {
            EigenDecomposition ed1;
            EigenDecomposition ed2;

            try {
                ed1 = new EigenDecomposition(kx.plus(kx.transpose()).scalarMult(0.5).getRealMatrix());
                ed2 = new EigenDecomposition(ky.plus(ky.transpose()).scalarMult(0.5).getRealMatrix());
            } catch (Exception e) {
                System.out.println("Eigenvalue didn't converge");
                lastP = thresh + 0.01;
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
                if (anEigProd > maxEig * thresh)
                    d.add(anEigProd);
            }

            if (d.size() * T < 1E6) {
                double[][] f_rand1 = new double[d.size()][T_BS];
                for (int i = 0; i < d.size(); i++) {
                    for (int j = 0; j < T_BS; j++) {
                        f_rand1[i][j] = chisq.sample();
                    }
                }

                double[][] data = new double[1][d.size()];

                for (int i = 0; i < d.size(); i++) {
                    data[0][i] = d.get(i);
                }

                TetradMatrix f_rand = new TetradMatrix(f_rand1);
                TetradMatrix ep = new TetradMatrix(data);
                ep = ep.scalarMult(1 / (double) T);
                double[][] nullDist = ep.times(f_rand).toArray();
                int sum = 0;

                for (double[] aNullDist : nullDist) {
                    for (double anANullDist : aNullDist) {
                        if (anANullDist > trace)
                            sum++;
                    }
                }

                double pval = (double) sum / T_BS;
                lastP = pval;

                return pval > alpha;
            } else {
                return false;
            }
        }

    }

    private boolean isIndependentCon(Node x, Node y, List<Node> z) {
        boolean unbiased = false;
        int T = data.getNumRows();
        double[] xArr = _data[data.getColumn(x)];
        double[] yArr = _data[data.getColumn(y)];

        int T_BS = 5000;

        double thres = 1E-5;
        int dim = z.size();

        double width;

        if (T <= 200)
            width = 1.2;
        else if (T < 1200)
            width = 0.7;
        else
            width = 0.4;

        double theta = 1 / (width * width * dim);

        TetradMatrix kernArg = new TetradMatrix(T, z.size() + 1);

        for (int i = 0; i < T; i++) {
            for (int j = 0; j < z.size() + 1; j++) {
                if (j == 0)
                    kernArg.set(i, j, xArr[i]);
                else
                    kernArg.set(i, j, data.getDouble(i, data.getColumn(z.get(j - 1))));
            }
        }

        //  System.out.println("Time to setup preliminary kernel matrices: " + (System.nanoTime()-time));
        double[] temp = new double[2];
        temp[0] = theta;
        temp[1] = 1;
        TetradMatrix Kx = H.times(kernel(kernArg, kernArg, temp)).times(H);
        TetradMatrix Ky = H.times(kernel(yArr, yArr, temp)).times(H);

        TetradMatrix KZ = new TetradMatrix(T, z.size());

        for (int i = 0; i < T; i++) {
            for (int j = 0; j < z.size(); j++) {
                KZ.set(i, j, data.getDouble(i, data.getColumn(z.get(j))));
            }
        }

        KZ = H.times(kernel(KZ, KZ, temp).times(H));

        KZ = eye.minus(KZ.times((KZ.plus(lamEye).inverse())));
        TetradMatrix KXZ = KZ.times(Kx).times(KZ.transpose());
        TetradMatrix KYZ = KZ.times(Ky).times(KZ.transpose());

        double sta = KXZ.times(KYZ).trace();

        EigenDecomposition ed1;
        EigenDecomposition ed2;

        try {
            ed1 = new EigenDecomposition(KXZ.plus(KXZ.transpose()).scalarMult(.5).getRealMatrix());
            ed2 = new EigenDecomposition(KYZ.plus(KYZ.transpose()).scalarMult(.5).getRealMatrix());
        } catch (Exception e) {
            System.out.println("Eigenvalue didn't converge");
            return false;
        }

        double[] evalues1 = ed1.getRealEigenvalues();
        double[] evalues2 = ed2.getRealEigenvalues();

        double max1 = 0;

        for (double anEvalues1 : evalues1) {
            if (anEvalues1 > max1)
                max1 = anEvalues1;

        }

        double max2 = 0;

        for (double anEvalues2 : evalues2) {
            if (anEvalues2 > max2)
                max2 = anEvalues2;
        }

        ArrayList<Integer> inds1 = new ArrayList<>();

        for (int i = 0; i < evalues1.length; i++) {
            if (evalues1[i] > max1 * thres)
                inds1.add(i);
        }

        ArrayList<Integer> inds2 = new ArrayList<>();

        for (int i = 0; i < evalues2.length; i++) {
            if (evalues2[i] > max2 * thres) {
                inds2.add(i);
            }
        }

        TetradMatrix eigKxz = new TetradMatrix(inds1.size(), inds1.size());
        TetradMatrix eigKyz = new TetradMatrix(inds2.size(), inds2.size());

        TetradMatrix tv1 = new TetradMatrix(ed1.getEigenvector(0).getDimension(), inds1.size());
        TetradMatrix tv2 = new TetradMatrix(ed2.getEigenvector(0).getDimension(), inds2.size());

        for (int i = 0; i < inds1.size(); i++) {
            eigKxz.set(i, i, Math.sqrt(evalues1[inds1.get(i)]));
            RealVector t = ed1.getEigenvector(inds1.get(i));

            for (int j = 0; j < t.getDimension(); j++) {
                tv1.set(j, i, t.getEntry(j));
            }
        }

        for (int i = 0; i < inds2.size(); i++) {
            eigKyz.set(i, i, Math.sqrt(evalues2[inds2.get(i)]));
            RealVector t = ed2.getEigenvector(inds2.get(i));
            for (int j = 0; j < t.getDimension(); j++) {
                tv2.set(j, i, t.getEntry(j));
            }
        }

        TetradMatrix eiv_prodx = tv1.times(eigKxz.transpose());
        TetradMatrix eiv_prody = tv2.times(eigKyz.transpose());

        int numx = eiv_prodx.columns();
        int numy = eiv_prody.columns();
        int size_u = numx * numy;
        TetradMatrix uu = new TetradMatrix(T, size_u);

        for (int i = 0; i < numx; i++) {
            for (int j = 0; j < numy; j++) {
                for (int k = 0; k < T; k++) {
                    uu.set(k, i * numy + j, eiv_prodx.get(k, i) * eiv_prody.get(k, j));
                }
            }
        }

        TetradMatrix uu_prod;

        if (size_u > T)
            uu_prod = uu.times(uu.transpose());
        else
            uu_prod = uu.transpose().times(uu);

        if (isBootstrap()) {
            EigenDecomposition ee;
            try {
                ee = new EigenDecomposition(uu_prod.getRealMatrix());
            } catch (Exception e) {
                System.out.println("Eigenvalue Didn't converge conditional");
                return true;
            }

            int num;

            if (T < size_u)
                num = T;
            else
                num = size_u;

            double[] evals = ee.getRealEigenvalues();
            double[] valsToKeep = new double[num];
            Arrays.sort(evals);
            int count = 0;
            for (int i = evals.length - 1; i >= 0; i--) {
                valsToKeep[count] = evals[i];
                count++;
            }

            double max = valsToKeep[0];
            ArrayList<Double> finalVals = new ArrayList<>();

            for (double aValsToKeep : valsToKeep) {
                if (aValsToKeep >= max * thres)
                    finalVals.add(aValsToKeep);
            }

            if (finalVals.size() * T < 1E6) {
                double[][] frand1 = new double[finalVals.size()][T_BS];
                for (int i = 0; i < finalVals.size(); i++) {
                    for (int j = 0; j < T_BS; j++) {
                        frand1[i][j] = chisq.sample();
                    }
                }

                double[][] eiguu = new double[1][finalVals.size()];

                for (int j = 0; j < finalVals.size(); j++) {
                    eiguu[0][j] = finalVals.get(j);
                }

                TetradMatrix fr = new TetradMatrix(frand1);
                TetradMatrix eig_uu = new TetradMatrix(eiguu);
                TetradMatrix nullDist;

                if (unbiased) {
                    System.out.println("Can only return unbiased if hyperparameters are learned");
                    return false;
                } else {
                    nullDist = eig_uu.times(fr);
                }

                int sum = 0;

                for (int i = 0; i < nullDist.columns(); i++) {
                    if (nullDist.get(0, i) > sta)
                        sum++;
                }

                lastP = sum / (double) T_BS;

                return lastP > alpha;
            } else {
                System.out.println("Unimplemented iteratively calculating null");
                return false;
            }
        } else {
            double mean_appr = uu_prod.trace();
            double var_appr = 2 * uu_prod.times(uu_prod).trace();
            double k_appr = mean_appr * mean_appr / var_appr;
            double theta_appr = var_appr / mean_appr;
            GammaDistribution g = new GammaDistribution(k_appr, theta_appr);

            double p_appr = 1 - g.cumulativeProbability(sta);
            lastP = p_appr;
            return p_appr > alpha;
        }
    }

    private static double[][] dist(double[] x, double[] y) {
        double[][] sum = new double[x.length][y.length];
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < y.length; j++) {
                final double d = x[i] - y[j];
                sum[i][j] = d * d;
            }
        }
        return sum;
    }

    private static double dist2(double[] x, double[] y) {
        double sum = 0;

        for (int i = 0; i < x.length; i++) {
            final double d = x[i] - y[i];
            sum += d * d;
        }

        return sum;
    }

    private static TetradMatrix kernel(TetradMatrix x, TetradMatrix xKern, double[] theta) {
        TetradMatrix result = new TetradMatrix(x.rows(), xKern.rows());
        for (int i = 0; i < x.rows(); i++) {
            double[] currRow = x.getRow(i).toArray();

            for (int j = 0; j < xKern.rows(); j++) {
                double[] secRow = xKern.getRow(j).toArray();
                result.set(i, j, Math.exp(-1 * dist2(currRow, secRow) * theta[0] / 2));
            }
        }
        return result;
    }

    private static TetradMatrix kernel(double[] x, double[] y, double[] theta) {
        double[][] n2 = dist(x, y);
        double wi2 = theta[0] / 2;
        TetradMatrix kx = new TetradMatrix(x.length, y.length);
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < y.length; j++) {
                kx.set(i, j, Math.exp(-1 * n2[i][j] * wi2));
            }
        }
        return kx;

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
}

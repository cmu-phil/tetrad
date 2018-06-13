package edu.pitt.csb;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import edu.cmu.tetrad.util.dist.ChiSquare;
import edu.pitt.csb.mgm.EigenDecomposition;
import edu.pitt.csb.mgm.IndTestMultinomialAJ;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.linear.RealVector;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;

/**
 * Created by vinee_000 on 7/3/2016.
 */
public class KCI implements IndependenceTest {


    /**
     * Kernel Based Conditional Independence Test
     * Code Written by: Vineet Raghu
     * Test published in: Kernel-based Conditional Independence Test and Application in Causal Discovery (Zhang et al.)
     */

    double alpha;
    double alpha2;
    DataSet dat;
    DataSet forInd;
    public Graph truth;
    int graphNum;
    double lastP;

    double lambdaMGM;
    IndTestMultinomialAJ ii;
    private ArrayList<Double> chiSquareRand;
    PrintStream out;
    private int cutoff = -1;
    private TetradMatrix Hmat;
    private TetradMatrix eye;
    private final double lambda = 1E-3;
    private TetradMatrix lamEye;
    private TetradMatrix[] kyArr;
    private boolean twoStage; //Are we doing the two stage test? Or just KCI?
    private boolean[][] stopTesting; //Once we found an edge to have a linear relationship, there's no need to test it for nonlinearity

    //First threshold is p-value for KCI, second is p-value for Likelihood Ratio
    public void setCutoff(int c) {
        cutoff = c;
    }


    public KCI(DataSet data, double threshold) {
        twoStage = false;
        stopTesting = new boolean[data.getNumColumns()][data.getNumColumns()];
        int T = data.getNumRows();
        kyArr = new TetradMatrix[data.getNumColumns()];
        double[][] H = new double[T][T];
        for (int i = 0; i < T; i++) {
            for (int j = 0; j < T; j++) {
                if (i == j)
                    H[i][j] = 1.0 - 1.0 / T;
                else
                    H[i][j] = -1.0 / T;
            }
        }
        Hmat = new TetradMatrix(H);
        eye = new TetradMatrix(T, T);
        for (int i = 0; i < T; i++)
            eye.set(i, i, 1);
        lamEye = eye.scalarMult(lambda);
        alpha = threshold;
        dat = data;
        forInd = dat.copy();
        lastP = -1;
        A:
        for (int i = 0; i < dat.getNumColumns(); i++) {
            try {
                double[] curr = new double[dat.getNumRows()];
                for (int j = 0; j < dat.getNumRows(); j++) {
                    curr[j] = dat.getDouble(j, i);

                }
                double m = mean(curr);
                double std = std(curr, m);

                for (int j = 0; j < dat.getNumRows(); j++) {
                    dat.setDouble(j, i, (dat.getDouble(j, i) - m) / std);
                }
            } catch (Exception e) {
                continue A;
            }
        }
        chiSquareRand = new ArrayList<Double>();
        ChiSquare cs = new ChiSquare();
        for (int i = 0; i < 1000 * dat.getNumRows(); i++) {
            chiSquareRand.add(cs.nextRandom());
        }
    }

    public KCI(DataSet data, double threshold, double threshold2, Graph g, double lamb) {
        twoStage = true;
        stopTesting = new boolean[data.getNumColumns()][data.getNumColumns()];
        int T = data.getNumRows();
        kyArr = new TetradMatrix[data.getNumColumns()];
        double[][] H = new double[T][T];
        for (int i = 0; i < T; i++) {
            for (int j = 0; j < T; j++) {
                if (i == j)
                    H[i][j] = 1.0 - 1.0 / T;
                else
                    H[i][j] = -1.0 / T;
            }
        }
        Hmat = new TetradMatrix(H);
        eye = new TetradMatrix(T, T);
        for (int i = 0; i < T; i++)
            eye.set(i, i, 1);
        lamEye = eye.scalarMult(lambda);
        truth = g;
        lambdaMGM = lamb;
        try {
            out = new PrintStream(new FileOutputStream("pvals.txt", true));
            out.println("Method\tP\tCond_Size\tX_Continuous\tY_Continuous\tX_Name\tY_Name\tZ_Names\tMGM_lambda");
        } catch (Exception e) {

        }
        alpha = threshold;
        alpha2 = threshold2;
        dat = data;
        forInd = dat.copy();
        ii = new IndTestMultinomialAJ(forInd, threshold2);
        lastP = -1;
        A:
        for (int i = 0; i < dat.getNumColumns(); i++) {
            try {
                double[] curr = new double[dat.getNumRows()];
                for (int j = 0; j < dat.getNumRows(); j++) {
                    curr[j] = dat.getDouble(j, i);

                }
                double m = mean(curr);
                double std = std(curr, m);

                for (int j = 0; j < dat.getNumRows(); j++) {
                    dat.setDouble(j, i, (dat.getDouble(j, i) - m) / std);
                }
            } catch (Exception e) {
                continue A;
            }
        }
        chiSquareRand = new ArrayList<Double>();
        ChiSquare cs = new ChiSquare();
        for (int i = 0; i < 1000 * dat.getNumRows(); i++) {
            chiSquareRand.add(cs.nextRandom());
        }
        //Normalize dataset data
        //Subtract by mean and divide by standard deviation

    }

    public KCI(DataSet data, double threshold, double threshold2) {
        stopTesting = new boolean[data.getNumColumns()][data.getNumColumns()];
        int T = data.getNumRows();
        kyArr = new TetradMatrix[data.getNumColumns()];
        double[][] H = new double[T][T];
        for (int i = 0; i < T; i++) {
            for (int j = 0; j < T; j++) {
                if (i == j)
                    H[i][j] = 1.0 - 1.0 / T;
                else
                    H[i][j] = -1.0 / T;
            }
        }
        Hmat = new TetradMatrix(H);
        eye = new TetradMatrix(T, T);
        for (int i = 0; i < T; i++)
            eye.set(i, i, 1);
        lamEye = eye.scalarMult(lambda);
        alpha = threshold;
        alpha2 = threshold2;
        dat = data;
        forInd = dat.copy();
        ii = new IndTestMultinomialAJ(forInd, threshold2);
        lastP = -1;
        A:
        for (int i = 0; i < dat.getNumColumns(); i++) {
            try {
                double[] curr = new double[dat.getNumRows()];
                for (int j = 0; j < dat.getNumRows(); j++) {
                    curr[j] = dat.getDouble(j, i);

                }
                double m = mean(curr);
                double std = std(curr, m);

                for (int j = 0; j < dat.getNumRows(); j++) {
                    dat.setDouble(j, i, (dat.getDouble(j, i) - m) / std);
                }
            } catch (Exception e) {
                continue A;
            }
        }
        chiSquareRand = new ArrayList<Double>();
        ChiSquare cs = new ChiSquare();
        for (int i = 0; i < 1000 * dat.getNumRows(); i++) {
            chiSquareRand.add(cs.nextRandom());
        }
        //Normalize dataset data
        //Subtract by mean and divide by standard deviation

    }

    public static double mean(double[] curr) {

        double sum = 0;
        int num = curr.length;
        for (int i = 0; i < num; i++)
            sum += curr[i];
        return sum / num;
    }

    public static double std(double[] curr, double mean) {
        double sum = 0;
        for (int i = 0; i < curr.length; i++) {
            sum += Math.pow((curr[i] - mean), 2);
        }
        sum = sum / curr.length;
        return Math.sqrt(sum);

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
        // System.out.println("Is " + x + " ind of " + y + " given " + z);
        ArrayList<Node> zzzz = new ArrayList<Node>();

        if (z == null) {
            return isIndependentUncon(x, y);
        }
        boolean xCont = false;
        boolean yCont = false;
       /* if(z.size() > cutoff)
            return false;*/
        if (ii != null) {
            if (z != null) {
                for (Node tempo : z)
                    zzzz.add(ii.getVariable(tempo.getName()));
            }
            try {

                ii.getData().setDouble(1, ii.getData().getColumn(ii.getVariable(x.getName())), ii.getData().getDouble(1, ii.getData().getColumn(ii.getVariable(x.getName()))));
                xCont = true;
                // System.out.println(x + ": is Continuous");
            } catch (Exception e) {
            }
            try {
                ii.getData().setDouble(1, ii.getData().getColumn(ii.getVariable(y.getName())), ii.getData().getDouble(1, ii.getData().getColumn(ii.getVariable(y.getName()))));
                yCont = true;
                //System.out.println(y + ": is Continuous");
            } catch (Exception ee) {
            }
            if (ii.isDependent(ii.getVariable(x.getName()), ii.getVariable(y.getName()), zzzz)) {
                lastP = ii.getPValue();
                List<Node> temp = new ArrayList<Node>();
                if (cutoff == zzzz.size()) {
                    stopTesting[ii.getData().getColumn(ii.getVariable(x.getName()))][ii.getData().getColumn(ii.getVariable(y.getName()))] = true;
                    stopTesting[ii.getData().getColumn(ii.getVariable(y.getName()))][ii.getData().getColumn(ii.getVariable(x.getName()))] = true;
                }
                if (truth != null) {
                    for (Node pz : zzzz)
                        temp.add(truth.getNode(pz.getName()));
                    if (!truth.isDSeparatedFrom(truth.getNode(x.getName()), truth.getNode(y.getName()), temp) && lastP > alpha)
                        out.println("MULT\t" + lastP + "\t" + temp.size() + "\t" + xCont + "\t" + yCont + "\t" + x.getName() + "\t" + y.getName() + "\t" + temp + "\t" + lambdaMGM);
                    out.flush();
                }
                return false;
            }
            if (stopTesting[ii.getData().getColumn(ii.getVariable(x.getName()))][ii.getData().getColumn(ii.getVariable(y.getName()))]) {
                lastP = ii.getPValue();
                // System.out.println(x + "," + y + " was previously dependent so not asking KCI");
                return true;
            }
            lastP = ii.getPValue();
        }
        List<Node> temp = new ArrayList<Node>();
        if (out != null && truth != null) {
            for (Node pz : zzzz)
                temp.add(truth.getNode(pz.getName()));
            if (!truth.isDSeparatedFrom(truth.getNode(x.getName()), truth.getNode(y.getName()), temp) && lastP > alpha)
                out.println("MULT\t" + lastP + "\t" + temp.size() + "\t" + xCont + "\t" + yCont + "\t" + x.getName() + "\t" + y.getName() + "\t" + temp + "\t" + lambdaMGM);
            out.flush();
        }
        if (z.isEmpty()) {
            boolean b = isIndependentUncon(x, y);

            return b;
        } else {
            boolean b = isIndependentCon(x, y, z);

            return b;


        }
    }

    private boolean isIndependentCon(Node x, Node y, List<Node> z) {
        boolean xCont = false;
        boolean yCont = false;
       /* if(z.size() > cutoff)
            return false;*/
        if (ii != null) {
            try {

                ii.getData().setDouble(1, ii.getData().getColumn(ii.getVariable(x.getName())), ii.getData().getDouble(1, ii.getData().getColumn(ii.getVariable(x.getName()))));
                xCont = true;
                // System.out.println(x + ": is Continuous");
            } catch (Exception e) {
            }
            try {
                ii.getData().setDouble(1, ii.getData().getColumn(ii.getVariable(y.getName())), ii.getData().getDouble(1, ii.getData().getColumn(ii.getVariable(y.getName()))));
                yCont = true;
                //System.out.println(y + ": is Continuous");
            } catch (Exception ee) {
            }
        }
        long time = System.nanoTime();
        boolean unbiased = false;
        boolean GP = unbiased;
        boolean bootstrap = false;
        int colnumX = dat.getColumn(x);
        int colnumY = dat.getColumn(y);
        int T = dat.getNumRows();
        double[] xArr = new double[T];
        double[] yArr = new double[T];
        for (int i = 0; i < T; i++) {
            xArr[i] = dat.getDouble(i, colnumX);
            yArr[i] = dat.getDouble(i, colnumY);
        }
        int num_eig = T;
        int T_BS = 5000;

        double thres = 1E-5;
        int dim = z.size();
        double width = 0;
        if (T <= 200)
            width = 1.2;
        else if (T < 1200)
            width = 0.7;
        else
            width = 0.4;
        double theta = 1 / (width * width * dim);
        // System.out.println("Initial Stuff: " + (System.nanoTime()-time));
        time = System.nanoTime();


        TetradMatrix kernArg = new TetradMatrix(T, z.size() + 1);
        for (int i = 0; i < T; i++) {
            for (int j = 0; j < z.size() + 1; j++) {
                if (j == 0)
                    kernArg.set(i, j, xArr[i]);
                else
                    kernArg.set(i, j, dat.getDouble(i, dat.getColumn(z.get(j - 1))) / 2);
            }
        }
        //  System.out.println("Time to setup preliminary kernel matrices: " + (System.nanoTime()-time));
        double[] temp = new double[2];
        temp[0] = theta;
        temp[1] = 1;
        TetradMatrix KX = kernel(kernArg, kernArg, temp);
        kernArg = null;
        time = System.nanoTime();
        KX = Hmat.times(KX.times(Hmat));
        TetradMatrix KY;
        if (kyArr[colnumY] != null)
            KY = kyArr[colnumY];
        else {
            double[][] ky = kernel(yArr, yArr, temp);
            KY = new TetradMatrix(ky);
            KY = Hmat.times(KY.times(Hmat));
            kyArr[colnumY] = KY;
        }


        TetradMatrix KXZ = new TetradMatrix(1, 1);
        TetradMatrix KYZ = new TetradMatrix(1, 1);
        double sta = 0;
        double df = 0;
        //System.out.println("Time for first two Matrix Multiplications: " + (System.nanoTime()-time));
        if (GP) {
            //TO DO optimize hyperparameters

        } else {
            time = System.nanoTime();
            TetradMatrix KZ = new TetradMatrix(T, z.size());
            for (int i = 0; i < T; i++) {
                for (int j = 0; j < z.size(); j++) {
                    KZ.set(i, j, dat.getDouble(i, dat.getColumn(z.get(j))));
                }
            }
            KZ = kernel(KZ, KZ, temp);
            KZ = Hmat.times(KZ.times(Hmat));

            KZ = eye.minus(KZ.times((KZ.plus(lamEye).inverse())));
            KXZ = KZ.times(KX.times(KZ.transpose()));
            KYZ = KZ.times(KY.times(KZ.transpose()));
            sta = KXZ.times(KYZ).trace();
            df = eye.minus(KZ).trace();
            // System.out.println("Time for setting up kernel matrices(Multiplication): " + (System.nanoTime()-time));
            // System.out.println(sta);
            //System.out.println(df);
        }
        EigenDecomposition ed1;
        EigenDecomposition ed2;
        try {
            time = System.nanoTime();
            ed1 = new EigenDecomposition(KXZ.plus(KXZ.transpose()).scalarMult(.5).getRealMatrix());
            ed2 = new EigenDecomposition(KYZ.plus(KYZ.transpose()).scalarMult(.5).getRealMatrix());
            // System.out.println("Time for EigenValue Decomp:" + (System.nanoTime()-time));
        } catch (Exception e) {
            System.out.println("Eigenvalue didn't converge");
            return true;
        }


        time = System.nanoTime();
        double[] evalues1 = ed1.getRealEigenvalues();
        double[] evalues2 = ed2.getRealEigenvalues();

        //find all indices in evalues1 and evalues2 where the eigenvalue is greater than max eigenvalue * thresh
        //keep only those indices
        //get eigenvectors corresponding to those indices as well

        double max1 = 0;
        for (int i = 0; i < evalues1.length; i++) {
            if (evalues1[i] > max1)
                max1 = evalues1[i];

        }
        double max2 = 0;
        for (int i = 0; i < evalues2.length; i++) {
            if (evalues2[i] > max2)
                max2 = evalues2[i];
        }
        ArrayList<Integer> inds1 = new ArrayList<Integer>();
        for (int i = 0; i < evalues1.length; i++) {
            if (evalues1[i] > max1 * thres)
                inds1.add(i);
        }
        ArrayList<Integer> inds2 = new ArrayList<Integer>();
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
            //columns of matrix are eigenvectors
            //rows are values of the e-vectors
        }

        for (int i = 0; i < inds2.size(); i++) {
            eigKyz.set(i, i, Math.sqrt(evalues2[inds2.get(i)]));
            RealVector t = ed2.getEigenvector(inds2.get(i));
            for (int j = 0; j < t.getDimension(); j++) {
                tv2.set(j, i, t.getEntry(j));
            }
        }

        //eigKXZ = diag(sqrt(evalues1))
        //tv1 = relevant eigenvectors from chosen eigenvalues

        TetradMatrix eiv_prodx = tv1.times(eigKxz.transpose());
        TetradMatrix eiv_prody = tv2.times(eigKyz.transpose());

        eigKxz = null;
        eigKyz = null;
        tv1 = null;
        tv2 = null;
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
        //System.out.println("Time to do a lot of multiplication before generating null: " + (System.nanoTime()-time));

        if (bootstrap) {
            //TO DO
            time = System.nanoTime();
            EigenDecomposition ee;
            try {
                ee = new EigenDecomposition(uu_prod.getRealMatrix());
            } catch (Exception e) {
                System.out.println("Eigenvalue Didn't converge conditional");
                return true;
            }
            int num = -1;
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
            ArrayList<Double> finalVals = new ArrayList<Double>();
            for (int i = 0; i < valsToKeep.length; i++) {
                if (valsToKeep[i] >= max * thres)
                    finalVals.add(valsToKeep[i]);
            }
            double cri = -1;
            double p_val = -1;

            if (finalVals.size() * T < 1E6) {
                double[][] frand1 = new double[finalVals.size()][T_BS];
                for (int i = 0; i < finalVals.size(); i++) {
                    for (int j = 0; j < T_BS; j++) {
                        frand1[i][j] = chiSquareRand.get((i * T_BS + j) % chiSquareRand.size());
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
                cri = Math.ceil((1 - alpha) * T_BS);
                int sum = 0;
                for (int i = 0; i < nullDist.columns(); i++) {
                    if (nullDist.get(0, i) > sta)
                        sum++;
                }
                lastP = sum / (double) T_BS;
                List<Node> tem = new ArrayList<Node>();
                if (out != null && truth != null) {
                    for (Node pz : z)
                        tem.add(truth.getNode(pz.getName()));
                    if (!truth.isDSeparatedFrom(truth.getNode(x.getName()), truth.getNode(y.getName()), tem) && lastP > alpha)
                        out.println("KCI\t" + lastP + "\t" + tem.size() + "\t" + xCont + "\t" + yCont + "\t" + x.getName() + "\t" + y.getName() + "\t" + tem + "\t" + lambdaMGM);
                    out.flush();

                }
                //  System.out.println("Time to generate null and get result: " + (System.nanoTime()-time));
                if (lastP > alpha) {
                    return true;
                } else {
                    return false;
                }
            } else {
                System.out.println("Unimplemented iteratively calculating null");
                return false;
            }
        } else {
            double cri_appr = -1;
            double p_appr = -1;
            double mean_appr = uu_prod.trace();
            double var_appr = 2 * uu_prod.times(uu_prod).trace();
            double k_appr = mean_appr * mean_appr / var_appr;
            double theta_appr = var_appr / mean_appr;
            GammaDistribution g = new GammaDistribution(k_appr, theta_appr);
            cri_appr = g.inverseCumulativeProbability(1 - alpha);
            //  System.out.println(cri_appr);
            p_appr = 1 - g.cumulativeProbability(sta);
            lastP = p_appr;
            List<Node> tem = new ArrayList<Node>();
            if (out != null && truth != null) {
                for (Node pz : z)
                    tem.add(truth.getNode(pz.getName()));
                if (!truth.isDSeparatedFrom(truth.getNode(x.getName()), truth.getNode(y.getName()), tem) && lastP > alpha)
                    out.println("KCI\t" + lastP + "\t" + tem.size() + "\t" + xCont + "\t" + yCont + "\t" + x.getName() + "\t" + y.getName() + "\t" + tem + "\t" + lambdaMGM);
                out.flush();
            }
            if (p_appr > alpha) {
                return true;
            } else {
                return false;
            }
        }
    }

    private boolean isIndependentUncon(Node x, Node y) {
        boolean xCont = false;
        boolean yCont = false;
       /* if(z.size() > cutoff)
            return false;*/
        if (ii != null) {
            try {
                ii.getData().setDouble(1, ii.getData().getColumn(ii.getVariable(x.getName())), ii.getData().getDouble(1, ii.getData().getColumn(ii.getVariable(x.getName()))));
                xCont = true;
                // System.out.println(x + ": is Continuous");
            } catch (Exception e) {
            }
            try {
                ii.getData().setDouble(1, ii.getData().getColumn(ii.getVariable(y.getName())), ii.getData().getDouble(1, ii.getData().getColumn(ii.getVariable(y.getName()))));
                yCont = true;
                //System.out.println(y + ": is Continuous");
            } catch (Exception ee) {
            }
            if (ii.isDependent(ii.getVariable(x.getName()), ii.getVariable(y.getName()))) {
                lastP = ii.getPValue();
                if (cutoff == 0) {
                    stopTesting[ii.getData().getColumn(ii.getVariable(x.getName()))][ii.getData().getColumn(ii.getVariable(y.getName()))] = true;
                    stopTesting[ii.getData().getColumn(ii.getVariable(y.getName()))][ii.getData().getColumn(ii.getVariable(x.getName()))] = true;
                }
                return false;
            }
        }
        List<Node> tem = Collections.emptyList();
        //System.out.println(x + " " + y);
        int columnNum = dat.getColumn(x);
        int columnNumY = dat.getColumn(y);
        int T = dat.getNumRows();

        double[] xArr = new double[T];
        double[] yArr = new double[T];

        for (int i = 0; i < T; i++) {
            xArr[i] = dat.getDouble(i, columnNum);
            yArr[i] = dat.getDouble(i, columnNumY);
        }

        boolean approx = false;
        int num_eig = T;
        if (T > 1000) {
            approx = true;
            num_eig = T / 2;
        }
        int T_BS = 1000;
        double lambda = 1E-3;
        double thresh = 1E-6;
        double width = .8;
        if (T > 200)
            width = 0.5;
        if (T > 1200)
            width = 0.3;
        double theta = 1 / (width * width);
        double[][] H = new double[T][T];
        for (int i = 0; i < T; i++) {
            for (int j = 0; j < T; j++) {
                if (i == j)
                    H[i][j] = 1.0 - 1.0 / T;
                else
                    H[i][j] = -1.0 / T;
            }
        }
        double[] temp = new double[2];
        temp[0] = theta;
        temp[1] = 1;
        TetradMatrix kx;
        TetradMatrix ky;
        TetradMatrix h = new TetradMatrix(H);
        if (kyArr[columnNum] != null) {
            kx = kyArr[columnNum];
        } else {
            double[][] Kx = kernel(xArr, xArr, temp);
            kx = new TetradMatrix(Kx);
            kx = h.times(kx);
            kx = kx.times(h);
            kyArr[columnNum] = kx;
        }
        if (kyArr[columnNumY] != null) {
            ky = kyArr[columnNumY];
        } else {
            double[][] Ky = kernel(yArr, yArr, temp);
            ky = new TetradMatrix(Ky);
            ky = h.times(ky);
            ky = ky.times(h);
            kyArr[columnNumY] = ky;
        }


        long time = System.currentTimeMillis();


        h = null;
        double sta = kx.times(ky).trace();
        // System.out.println("Time to calculate statistic: " + (System.currentTimeMillis()-time));
        //System.out.println(sta);
        double cri = -1;
        double p_val = -1;
        if (!approx) //Bootstrap
        {

            org.apache.commons.math3.linear.EigenDecomposition eda1 = null;
            org.apache.commons.math3.linear.EigenDecomposition eda2 = null;

            try {
                time = System.currentTimeMillis();
                eda1 = new org.apache.commons.math3.linear.EigenDecomposition(kx.plus(kx).scalarMult(0.5).getRealMatrix());
                eda2 = new org.apache.commons.math3.linear.EigenDecomposition(ky.plus(ky).scalarMult(0.5).getRealMatrix());

                // System.out.println("Time to do EigenValue: " + (System.currentTimeMillis()-time));
            } catch (Exception e) {
                System.out.println("Eigenvalue didn't converge");
                lastP = thresh + 0.01;
                return true;
            }


//            EigenDecomposition ed1;
//            EigenDecomposition ed2;
//            try {
//                time = System.currentTimeMillis();
//                ed1 = new EigenDecomposition(kx.plus(kx.transpose()).scalarMult(0.5).getRealMatrix());
//                ed2 = new EigenDecomposition(ky.plus(ky.transpose()).scalarMult(0.5).getRealMatrix());
//                // System.out.println("Time to do EigenValue: " + (System.currentTimeMillis()-time));
//            } catch (Exception e) {
//                System.out.println("Eigenvalue didn't converge");
//                lastP = thresh + 0.01;
//                return true;
//            }

            time = System.currentTimeMillis();
            double[] ev1 = eda1.getRealEigenvalues();
            double[] ev2 = eda2.getRealEigenvalues();
            double[] eigProd = new double[ev1.length * ev2.length];
            double maxEig = Double.MIN_VALUE;
            for (int i = 0; i < ev1.length; i++) {
                for (int j = 0; j < ev2.length; j++) {
                    double curr = ev1[i] * ev2[j];
                    if (curr > maxEig)
                        maxEig = curr;
                    eigProd[i * ev2.length + j] = curr;

                }
            }
            ArrayList<Double> d = new ArrayList<Double>();
            for (int i = 0; i < eigProd.length; i++) {
                if (eigProd[i] > maxEig * thresh)
                    d.add(eigProd[i]);
            }
            // System.out.println("Time to do some more multiplication: " + (System.currentTimeMillis()-time));
            if (d.size() * T < 1E6) {
                //THIS IS TAKING FOREVER
                //TO DO CACHE THE NULL AND REUSE THE CRAP OUT OF IT
                time = System.currentTimeMillis();
                //System.out.println(d);
                //ChiSquare chi = new ChiSquare(1);
                double[][] f_rand1 = new double[d.size()][T_BS];
                for (int i = 0; i < d.size(); i++) {
                    for (int j = 0; j < T_BS; j++) {
                        f_rand1[i][j] = chiSquareRand.get((i * T_BS + j) % chiSquareRand.size());
                    }
                }
                // System.out.println("Time to generate null: " + (System.currentTimeMillis()-time));
                time = System.currentTimeMillis();
                double[][] data = new double[1][d.size()];
                for (int i = 0; i < d.size(); i++)
                    data[0][i] = d.get(i);

                TetradMatrix f_rand = new TetradMatrix(f_rand1);
                TetradMatrix ep = new TetradMatrix(data);
                ep = ep.scalarMult(1 / (double) T);
                double[][] nullDist = ep.times(f_rand).toArray();
                int sum = 0;
                // System.out.println("Time for some multiplication before p-value: " + (System.currentTimeMillis()-time));
                time = System.currentTimeMillis();
                for (int i = 0; i < nullDist.length; i++) {

                    for (int j = 0; j < nullDist[i].length; j++) {
                        if (nullDist[i][j] > sta)
                            sum++;
                    }
                }
                //System.out.println("Got to bottom");
                double pval = (double) sum / T_BS;
                lastP = pval;
                if (truth != null && out != null) {
                    if (!truth.isDSeparatedFrom(truth.getNode(x.getName()), truth.getNode(y.getName()), tem) && lastP > alpha)
                        out.println("KCI\t" + lastP + "\t0" + "\t" + xCont + "\t" + yCont + "\t" + x.getName() + "\t" + y.getName() + "\t" + tem + "\t" + lambdaMGM);
                    out.flush();
                }
                //System.out.println("Time to n2 calculation of p-value: " + (System.currentTimeMillis()-time));
                if (pval < alpha) {
                    return false;
                } else {
                    return true;

                }
            } else {
                //TODO Iteratively calculate the null distribution!
                int length = 0;
                double[] nullDist = new double[T_BS];
                if (1E6 / T > 100)
                    length = (int) (1E6 / T);
                else
                    length = 100;
                int itmax = (int) Math.floor(d.size() / length);
                TetradVector nd = new TetradVector(nullDist);

                for (int iter = 0; iter < itmax; iter++) {
                    ChiSquare cs = new ChiSquare();
                    double[][] f_rand1 = new double[(int) length][T_BS];
                    for (int j = 0; j < length; j++) {
                        for (int k = 0; k < T_BS; k++) {
                            f_rand1[j][k] = cs.nextRandom();
                        }
                    }
                    int start = iter * length + 1;
                    int end = (iter + 1) * length;
                    //for(int index = start; index <=end; index++)
                    //{

                    //}

                    //need to iteratively compute null_distr here
                }
                System.out.println("TO DO CALCULATE NULL ITERATIVELY");

                return false;
                //do final computation, and then compute p-value as above
            }

            //need to get num_eig top eigenvalues here of KX + KX' /2 and same for KY


        } else {
            time = System.currentTimeMillis();
            double mean_appr = kx.trace() * ky.trace() / T;
            double var_appr = 2 * kx.times(kx).trace() * ky.times(ky).trace() / (T * T);//can optimize by not actually performing matrix multiplication
            double k_appr = mean_appr * mean_appr / var_appr;
            double theta_appr = var_appr / mean_appr;
            GammaDistribution g = new GammaDistribution(k_appr, theta_appr);
            double p_appr = 1 - g.cumulativeProbability(sta);
            lastP = p_appr;
            // System.out.println("Time to approximate p value: " + (System.currentTimeMillis()-time));
            if (out != null && truth != null) {
                if (!truth.isDSeparatedFrom(truth.getNode(x.getName()), truth.getNode(y.getName()), tem) && lastP > alpha)
                    out.println("KCI\t" + lastP + "\t0" + "\t" + xCont + "\t" + yCont + "\t" + x.getName() + "\t" + y.getName() + "\t" + null + "\t" + lambdaMGM);
                out.flush();
            }
            if (p_appr < alpha) {
                return false;
            } else {
                return true;
            }
        }

    }

    private static double[][] dist(double[] x, double[] y) {
        double[][] sum = new double[x.length][y.length];
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < y.length; j++) {
                sum[i][j] = Math.pow(x[i] - y[j], 2);

            }
        }
        return sum;
    }

    private static double dist2(double[] x, double[] y) {
        double sum = 0;
        for (int i = 0; i < x.length; i++) {
            sum += Math.pow(x[i] - y[i], 2);
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

    private static double[][] kernel(double[] x, double[] y, double[] theta) {
        double[][] n2 = dist(x, y);
        double wi2 = theta[0] / 2;
        double[][] kx = new double[x.length][y.length];
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < y.length; j++) {
                kx[i][j] = Math.exp(-1 * n2[i][j] * wi2);
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
        LinkedList<Node> thez = new LinkedList<Node>();
        for (Node s : z)
            thez.add(s);
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
        LinkedList<Node> thez = new LinkedList<Node>();
        for (Node s : z)
            thez.add(s);
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
        return dat.getVariables();
    }

    /**
     * Returns the variable by the given name.
     */
    public Node getVariable(String name) {
        return dat.getVariable(name);
    }

    /**
     * Returns the list of names for the variables in getNodesInEvidence.
     */
    public List<String> getVariableNames() {
        return dat.getVariableNames();
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
        return dat;
    }


    public ICovarianceMatrix getCov() {
        throw new UnsupportedOperationException();
    }

    public List<DataSet> getDataSets() {
        LinkedList<DataSet> L = new LinkedList<DataSet>();
        L.add(dat);
        return L;
    }

    public int getSampleSize() {
        return dat.getNumRows();
    }

    public List<TetradMatrix> getCovMatrices() {
        throw new UnsupportedOperationException();
    }

    public double getScore() {
        return 0;
    }
}

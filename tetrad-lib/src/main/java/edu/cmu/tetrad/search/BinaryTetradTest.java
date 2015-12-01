///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;

import java.util.List;


/**
 * A tetrad test for binary variables.
 *
 * @author Ricardo Silva
 * @deprecated
 */
public final class BinaryTetradTest implements TetradTest {
    private DataSet dataSet;
    //    private int rawdata1[][];
    private double pis[][];      //bivariate proportions only
    private double pis4[][][][]; //probabilities over four variables
    private int counts4[][][][][][][][]; //coefs over all groups of four variables
    private int values[][];
    private int indices[], sampleSize;

    private double prob[], tempProb, sig1, sig2, sig3, sig, stat;
    private boolean bvalues[];

    private static final double FUNC_TOLERANCE = 1.0e-4;
    private static final double PARAM_TOLERANCE = 1.0e-3;

    public BinaryTetradTest(DataSet dataSet, double sig) {
        this.dataSet = dataSet;
        this.sampleSize = dataSet.getNumRows();
        this.sig = sig;
        initialization();
    }

    public String[] getVarNames() {
        return dataSet.getVariableNames().toArray(new String[0]);
    }

    public List<Node> getVariables() {
        return dataSet.getVariables();
    }

    public DataSet getDataSet() {
        return this.dataSet;
    }

    private void initialization() {
        int numRows = this.dataSet.getNumRows();
        int numColumns = this.dataSet.getNumColumns();

        prob = new double[3];
        bvalues = new boolean[3];
        sig1 = sig / 3.;
        sig2 = 2. * sig / 3.;
        sig3 = sig;

//        this.rawdata = this.dataSet.getIntMatrixTransposed();

        // Store and order possible values
        this.values = new int[numColumns][];
        int tempValues[] = new int[2];
        boolean marked[] = new boolean[2];
        for (int i = 0; i < numColumns; i++) {
            int vSize = 0;
            rowloop:
            for (int j = 0; j < numRows; j++) {
//                int value = rawdata[i][j];
                int value = dataSet.getInt(j, i);
                for (int k = 0; k < vSize; k++) {
                    if (tempValues[k] == value) {
                        continue rowloop;
                    }
                }
                if (vSize < 2) {
                    tempValues[vSize++] = value;
                } else {
                    throw new RuntimeException(
                            "Maximum number of distinct values for a binary variable exceeded!");
                }
            }
            if (vSize == 1) {
                throw new RuntimeException(
                        "Error: variable assumes only one value!");
            }
            this.values[i] = new int[vSize];
            for (int j = 0; j < vSize; j++) {
                marked[j] = false;
            }
            for (int j = 0; j < vSize; j++) {
                int minValue = Integer.MAX_VALUE;
                int minIndexValue = -1;
                for (int k = 0; k < vSize; k++) {
                    if (!marked[k] && tempValues[k] < minValue) {
                        minValue = tempValues[k];
                        minIndexValue = k;
                    }
                }
                this.values[i][j] = minValue;
                marked[minIndexValue] = true;
            }
        }

        this.pis = new double[numColumns][numColumns];
        this.pis4 = new double[4][4][4][4];
        this.counts4 =
                new int[numColumns][numColumns][numColumns][numColumns][2][2][2][2];
        this.indices = new int[4];
        computeCounts();
        computeCounts4();
    }


    /**
     * Sample scores: the real deal. The way by which significance is tested will vary from case to case. We are also
     * using false discovery rate to make a mild adjustment in the p-values.
     */

    public int tetradScore(int v1, int v2, int v3, int v4) {
        evalTetradDifferences(v1, v2, v3, v4);
        for (int i = 0; i < 3; i++) {
            bvalues[i] = (prob[i] >= sig);
        }
        //Order p-values for FDR (false discovery rate) decision
        if (prob[1] < prob[0] && prob[1] < prob[2]) {
            tempProb = prob[0];
            prob[0] = prob[1];
            prob[1] = tempProb;
        } else if (prob[2] < prob[0] && prob[2] < prob[0]) {
            tempProb = prob[0];
            prob[0] = prob[2];
            prob[2] = tempProb;
        }
        if (prob[2] < prob[1]) {
            tempProb = prob[1];
            prob[1] = prob[2];
            prob[2] = tempProb;
        }
        if (prob[2] <= sig3) {
            return 0;
        }
        if (prob[1] <= sig2) {
            return 1;
        }
        if (prob[0] <= sig1) {
            //This is the case of 2 tetrad constraints holding, which is
            //a logical impossibility. On a future version we may come up with
            //better, more powerful ways of deciding what to do. Right now,
            //the default is to do just as follows:
            return 3;
        }
        return 3;
    }

    /**
     * Tests the tetrad (v1, v3) x (v2, v4) = (v1, v4) x (v2, v3)
     */
    public boolean tetradScore1(int v1, int v2, int v3, int v4) {
        if (tetradScore(v1, v2, v3, v4) != 1) {
            return false;
        }
        return bvalues[2];
    }

    public double getSignificance() {
        return this.sig;
    }

    public void setSignificance(double sig) {
        this.sig = sig;
    }

    /**
     * Tests if all tetrad constraints hold
     */
    public boolean tetradScore3(int v1, int v2, int v3, int v4) {
        return tetradScore(v1, v2, v3, v4) == 3;
    }

    public boolean tetradHolds(int v1, int v2, int v3, int v4) {
        evalTetradDifference(v1, v2, v3, v4);
        bvalues[0] = (prob[0] >= sig);
        return prob[0] >= sig;
    }

    public double tetradPValue(int v1, int v2, int v3, int v4) {
        evalTetradDifference(v1, v2, v3, v4);
        return prob[0];
    }

    public double tetradPValue(int i1, int j1, int k1, int l1, int i2, int j2, int k2, int l2) {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    private void computeCounts() {
        double cij[][] =
                new double[dataSet.getNumColumns()][dataSet.getNumColumns()];
        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                cij[i][j] = 0;
            }
        }
        for (int d = 0; d < dataSet.getNumRows(); d++) {
            for (int i = 0; i < dataSet.getNumColumns(); i++) {
                for (int j = 0; j < dataSet.getNumColumns(); j++) {
//                    cij[i][j] += rawdata[i][d] * rawdata[j][d];
                    cij[i][j] += dataSet.getInt(d, i) * dataSet.getInt(d, j);
                }
            }
        }
        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                this.pis[i][j] = cij[i][j] / dataSet.getNumRows();
            }
        }
    }

    private void computeCounts4(int indices[]) {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                for (int k = 0; k < 4; k++) {
                    for (int l = 0; l < 4; l++) {
                        pis4[i][j][k][l] = 0;
                    }
                }
            }
        }
        for (int d = 0; d < dataSet.getNumRows(); d++) {
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    for (int k = 0; k < 4; k++) {
                        for (int l = 0; l < 4; l++) {
//                            pis4[i][j][k][l] += rawdata[indices[i]][d] *
//                                    rawdata[indices[j]][d] *
//                                    rawdata[indices[k]][d] *
//                                    rawdata[indices[l]][d];

                            pis4[i][j][k][l] += dataSet.getInt(d, indices[i]) *
                                    dataSet.getInt(d, indices[j]) *
                                    dataSet.getInt(d, indices[k]) *
                                    dataSet.getInt(d, indices[l]);
                        }
                    }
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                for (int k = 0; k < 4; k++) {
                    for (int l = 0; l < 4; l++) {
                        pis4[i][j][k][l] /= dataSet.getNumRows();
                    }
                }
            }
        }
    }

    private void computeCounts4() {
        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                for (int k = 0; k < dataSet.getNumColumns(); k++) {
                    for (int l = 0; l < dataSet.getNumColumns(); l++) {
                        for (int x1 = 0; x1 < 2; x1++) {
                            for (int x2 = 0; x2 < 2; x2++) {
                                for (int x3 = 0; x3 < 2; x3++) {
                                    for (int x4 = 0; x4 < 2; x4++) {
                                        counts4[i][j][k][l][x1][x2][x3][x4] = 0;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (int d = 0; d < dataSet.getNumRows(); d++) {
            for (int i = 0; i < dataSet.getNumColumns(); i++) {
                for (int j = 0; j < dataSet.getNumColumns(); j++) {
                    for (int k = 0; k < dataSet.getNumColumns(); k++) {
                        for (int l = 0; l < dataSet.getNumColumns(); l++) {
//                            counts4[i][j][k][l][rawdata[i][d]][rawdata[j][d]][rawdata[k][d]][rawdata[l][d]]++;
                            counts4[i][j][k][l][dataSet.getInt(d,
                                    i)][dataSet.getInt(d, j)][dataSet.getInt(d,
                                    k)][dataSet.getInt(d, l)]++;
                        }
                    }
                }
            }
        }
    }

    private void evalTetradDifferences(int i, int j, int k, int l) {
        //System.out.println();
        /*prob[0] = tetradTest(i, j, k, l);
        prob[1] = tetradTest(i, j, l, k);
        prob[2] = tetradTest(i, k, l, j);
            System.out.println(i + " " + j + " " + k + " " + l);
            System.out.println(prob[0] + " " + prob[1] + " " + prob[2]);
        System.out.println(); System.out.println("**Alternative test"); System.out.println();
        prob[0] = tetradTest2(i, j, k, l);
        prob[1] = tetradTest2(i, j, l, k);
        prob[2] = tetradTest2(i, k, l, j);
            System.out.println(i + " " + j + " " + k + " " + l);
            System.out.println(prob[0] + " " + prob[1] + " " + prob[2]);
            System.exit(0);*/
        prob[0] = testOneTetrad(i, l, k, j);
        prob[1] = testOneTetrad(i, k, j, l);
        prob[2] = testOneTetrad(i, j, k, l);
        //if (printMessage)
        System.out.println(i + " " + j + " " + k + " " + l);
        System.out.println(prob[0] + " " + prob[1] + " " + prob[2]);
        System.exit(0);
        /*prob[0] = testOneTetradBootstrap(i, l, k, j);
        prob[1] = testOneTetradBootstrap(i, k, j, l);
        prob[2] = testOneTetradBootstrap(i, j, k, l);
        if (printMessage)
            System.out.println(i + " " + j + " " + k + " " + l);
            System.out.println(prob[0] + " " + prob[1] + " " + prob[2]); System.exit(0);*/
    }

    private void evalTetradDifference(int i, int j, int k, int l) {
        prob[0] = testOneTetrad(i, l, k, j);
    }

    private double tetradTest(int i, int j, int k, int l) {
        //System.out.println("Testing (" + i + ", " + j + ")(" + k + ", " + l + ") - (" +
        //                                 i + ", " + k + ")(" + j + ", " + l + ")");
        double a = this.pis[i][j], b = this.pis[k][l], c = this.pis[i][k], d =
                this.pis[j][l];
        double e = this.pis[i][i], f = this.pis[j][j], g = this.pis[k][k], h =
                this.pis[l][l];

        this.indices[0] = i;
        this.indices[1] = j;
        this.indices[2] = k;
        this.indices[3] = l;
        computeCounts4(this.indices);

        double pi[] = new double[8];
        pi[0] = a;
        pi[1] = b;
        pi[2] = c;
        pi[3] = d;
        pi[4] = e;
        pi[5] = f;
        pi[6] = g;
        pi[7] = h;
        double jacobian[] = new double[8];
        jacobian[0] = b - g * h;
        jacobian[1] = a - e * f;
        jacobian[2] = f * h - d;
        jacobian[3] = e * g - c;
        jacobian[4] = d * g - b * f;
        jacobian[5] = c * h - b * e;
        jacobian[6] = d * e - a * h;
        jacobian[7] = c * f - a * g;

        double pCov[][] = new double[8][8];
        for (int x = 0; x < 8; x++) {
            int idx1 = -1, idx2 = -1;
            switch (x) {
                /*case 0: idx1 = i; idx2 = j; break;
                case 1: idx1 = k; idx2 = l; break;
                case 2: idx1 = i; idx2 = k; break;
                case 3: idx1 = j; idx2 = l; break;
                case 4: idx1 = i; idx2 = i; break;
                case 5: idx1 = j; idx2 = j; break;
                case 6: idx1 = k; idx2 = k; break;
                case 7: idx1 = l; idx2 = l; break;*/
                case 0:
                    idx1 = 0;
                    idx2 = 1;
                    break;
                case 1:
                    idx1 = 2;
                    idx2 = 3;
                    break;
                case 2:
                    idx1 = 0;
                    idx2 = 2;
                    break;
                case 3:
                    idx1 = 1;
                    idx2 = 3;
                    break;
                case 4:
                    idx1 = 0;
                    idx2 = 0;
                    break;
                case 5:
                    idx1 = 1;
                    idx2 = 1;
                    break;
                case 6:
                    idx1 = 2;
                    idx2 = 2;
                    break;
                case 7:
                    idx1 = 3;
                    idx2 = 3;
                    break;
            }
            for (int y = x; y < 8; y++) {
                int idx3 = -1, idx4 = -1;
                switch (y) {
                    /*case 0: idx3 = i; idx4 = j; break;
                    case 1: idx3 = k; idx4 = l; break;
                    case 2: idx3 = i; idx4 = k; break;
                    case 3: idx3 = j; idx4 = l; break;
                    case 4: idx3 = i; idx4 = i; break;
                    case 5: idx3 = j; idx4 = j; break;
                    case 6: idx3 = k; idx4 = k; break;
                    case 7: idx3 = l; idx4 = l; break;*/
                    case 0:
                        idx3 = 0;
                        idx4 = 1;
                        break;
                    case 1:
                        idx3 = 2;
                        idx4 = 3;
                        break;
                    case 2:
                        idx3 = 0;
                        idx4 = 2;
                        break;
                    case 3:
                        idx3 = 1;
                        idx4 = 3;
                        break;
                    case 4:
                        idx3 = 0;
                        idx4 = 0;
                        break;
                    case 5:
                        idx3 = 1;
                        idx4 = 1;
                        break;
                    case 6:
                        idx3 = 2;
                        idx4 = 2;
                        break;
                    case 7:
                        idx3 = 3;
                        idx4 = 3;
                        break;
                }
                //System.out.println("idx1 = " + idx1 + " idx2 = " + idx2 + " idx3 = " + idx3 + " idx4 = " + idx4);
                pCov[x][y] = this.pis4[idx1][idx2][idx3][idx4] - pi[x] * pi[y];
                pCov[y][x] = pCov[x][y];
            }
        }

        double mle = (a - e * f) * (b - g * h) - (c - e * g) * (d - f * h);
        double var = MatrixUtils.innerProduct(
                MatrixUtils.product(jacobian, pCov), jacobian);
        double std = Math.sqrt(var / dataSet.getNumRows());
        stat = -Math.abs(mle) / std;
        //System.out.println("original stat = " + stat);
        return 2.0 * ProbUtils.normalCdf(stat);
    }

    public boolean oneFactorTest(int a, int b, int c, int d) {
        assert false;
        return false;
    }

    public boolean oneFactorTest(int a, int b, int c, int d, int e) {
        assert false;
        return false;
    }

    public boolean oneFactorTest(int a, int b, int c, int d, int e, int f) {
        assert false;
        return false;
    }

    public boolean twoFactorTest(int a, int b, int c, int d) {
        assert false;
        return false;
    }

    public boolean twoFactorTest(int a, int b, int c, int d, int e) {
        assert false;
        return false;
    }

    public boolean twoFactorTest(int a, int b, int c, int d, int e, int f) {
        assert false;
        return false;
    }

    public double testOneTetrad(int i, int j, int k, int l) {
        indices[0] = i;
        indices[1] = j;
        indices[2] = k;
        indices[3] = l;
        double params[] = new double[11];
        estimateTwoFactorModel(params);
        debugParams(params);
        return 1. - ProbUtils.chisqCdf(scoreParams(params), 4);
    }

    //-------------------------------------------------------------------------------------------------
    /**
     * Code to test a two-factor model. Necessary because MlBayesEstimator doesn't estimate latent
     * variable models. This code here is only for a two-factor, binary, latent variable model over four variables.
     * Not only makes it simpler, but also faster for our necessities in this test.
     *
     */

    /**
     * The probability p_ijkl is a function of a two-factor model eta1->i, eta1->j, eta2->k, eta2->l, eta1->eta2. The
     * respective parameters of p_ijkl are given in params[], and correspond to p_i|~eta1, p_j|~eta1, p_k|~eta2,
     * p_l|~eta2, p_i|eta1, p_j|eta1, p_k|eta2, p_l|eta2, p_eta1, p_eta2|~eta1, p_eta2|eta1, respectively.
     */

    private void twoFactorGradientInst(int values[], double params[],
                                       double gradient[]) {
        assert gradient.length == 11;
        assert values.length == 4;
        assert params.length == 11;

        double core[][] = new double[2][2];
        for (int n1 = 0; n1 < 2; n1++) {
            for (int n2 = 0; n2 < 2; n2++) {
                if (n1 == 1 && n2 == 1) {
                    core[n1][n2] = params[8] * params[10];
                } else if (n1 == 1 && n2 == 0) {
                    core[n1][n2] = params[8] * (1 - params[10]);
                } else if (n1 == 0 && n2 == 1) {
                    core[n1][n2] = (1. - params[8]) * params[9];
                } else {
                    core[n1][n2] = (1. - params[8]) * (1. - params[9]);
                }
                for (int v = 0; v < 2; v++) {
                    if (values[v] == 1) {
                        core[n1][n2] *= params[4 * n1 + v];
                    } else {
                        core[n1][n2] *= (1. - params[4 * n1 + v]);
                    }
                }
                for (int v = 2; v < 4; v++) {
                    if (values[v] == 1) {
                        core[n1][n2] *= params[4 * n2 + v];
                    } else {
                        core[n1][n2] *= (1. - params[4 * n2 + v]);
                    }
                }
            }
        }

        for (int c = 0; c < 2; c++) {
            if (values[c] == 1) {
                gradient[c] = (core[0][0] + core[0][1]) / params[c];
                gradient[4 + c] = (core[1][0] + core[1][1]) / params[4 + c];
            } else {
                gradient[c] = (core[0][0] + core[0][1]) / (1. - params[c]);
                gradient[4 + c] =
                        (core[1][0] + core[1][1]) / (1. - params[4 + c]);
            }
        }
        for (int c = 2; c < 4; c++) {
            if (values[c] == 1) {
                gradient[c] = (core[0][0] + core[1][0]) / params[c];
                gradient[4 + c] = (core[0][1] + core[1][1]) / params[4 + c];
            } else {
                gradient[c] = (core[0][0] + core[1][0]) / (1. - params[c]);
                gradient[4 + c] =
                        (core[0][1] + core[1][1]) / (1. - params[4 + c]);
            }
        }
        gradient[8] = (core[1][0] + core[1][1]) / params[8] -
                (core[0][0] + core[1][0]) / (1. - params[8]);
        gradient[9] = core[0][1] / params[9] - core[0][0] / (1. - params[9]);
        gradient[10] = core[1][1] / params[10] - core[1][0] / (1. - params[10]);
    }

    protected void computeTwoFactorGradient(double params[],
                                            double gradient[]) {
        double impProb[][][][] = new double[2][2][2][2];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int p = 0; p < 2; p++) {
                    for (int q = 0; q < 2; q++) {
                        impProb[i][j][p][q] = 0.;
                        for (int p1 = 0; p1 < 2; p1++) {
                            for (int p2 = 0; p2 < 2; p2++) {
                                impProb[i][j][p][q] += Math.pow(params[4 * p1],
                                        i) *
                                        Math.pow(1 - params[4 * p1], 1 - i) *
                                        Math.pow(params[4 * p1 + 1], j) *
                                        Math.pow(1 - params[4 * p1 + 1],
                                                1 - j) *
                                        Math.pow(params[4 * p2 + 2], p) *
                                        Math.pow(1 - params[4 * p2 + 2],
                                                1 - p) *
                                        Math.pow(params[4 * p2 + 3], q) *
                                        Math.pow(1 - params[4 * p2 + 3],
                                                1 - q) *
                                        Math.pow(params[8], p1) *
                                        Math.pow(1 - params[8], 1 - p1) *
                                        Math.pow(Math.pow(params[9], p2) *
                                                        Math.pow(1 - params[9], 1 - p2),
                                                1 - p1) * Math.pow(Math.pow(
                                        params[10], p2) *
                                        Math.pow(1 - params[10], 1 - p2), p1);
                            }
                        }
                    }
                }
            }
        }

        for (int i = 0; i < gradient.length; i++) {
            gradient[i] = 0.;
        }
        int values[] = new int[4];
        double subgradient[] = new double[11];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int k = 0; k < 2; k++) {
                    for (int l = 0; l < 2; l++) {
                        values[0] = i;
                        values[1] = j;
                        values[2] = k;
                        values[3] = l;
                        twoFactorGradientInst(values, params, subgradient);
                        double factor =
                                (double) counts4[indices[0]][indices[1]][indices[2]][indices[3]][i][j][k][l] /
                                        impProb[i][j][k][l];
                        for (int g = 0; g < gradient.length; g++) {
                            gradient[g] += factor * subgradient[g];
                        }
                    }
                }
            }
        }
    }

    private double paramsLikelihood(double params[]) {
        double impProb[][][][] = new double[2][2][2][2];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int p = 0; p < 2; p++) {
                    for (int q = 0; q < 2; q++) {
                        impProb[i][j][p][q] = 0.;
                        for (int p1 = 0; p1 < 2; p1++) {
                            for (int p2 = 0; p2 < 2; p2++) {
                                impProb[i][j][p][q] += Math.pow(params[4 * p1],
                                        i) *
                                        Math.pow(1 - params[4 * p1], 1 - i) *
                                        Math.pow(params[4 * p1 + 1], j) *
                                        Math.pow(1 - params[4 * p1 + 1],
                                                1 - j) *
                                        Math.pow(params[4 * p2 + 2], p) *
                                        Math.pow(1 - params[4 * p2 + 2],
                                                1 - p) *
                                        Math.pow(params[4 * p2 + 3], q) *
                                        Math.pow(1 - params[4 * p2 + 3],
                                                1 - q) *
                                        Math.pow(params[8], p1) *
                                        Math.pow(1 - params[8], 1 - p1) *
                                        Math.pow(Math.pow(params[9], p2) *
                                                        Math.pow(1 - params[9], 1 - p2),
                                                1 - p1) * Math.pow(Math.pow(
                                        params[10], p2) *
                                        Math.pow(1 - params[10], 1 - p2), p1);
                            }
                        }
                    }
                }
            }
        }
        double loglike = 0.;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int p = 0; p < 2; p++) {
                    for (int q = 0; q < 2; q++) {
                        loglike -=
                                counts4[indices[0]][indices[1]][indices[2]][indices[3]][i][j][p][q] *
                                        Math.log(impProb[i][j][p][q]);
                    }
                }
            }
        }
        return loglike;
    }

    /*private double paramsLikelihood(double params[])
    {
        double loglike = 0.;

        for (int d = 0; d < this.sampleSize; d++) {
            double sum = 0.;
            for (int l1 = 0; l1 < 2; l1++)
                for (int l2 = 0; l2 < 2; l2++) {
                    double outerProduct = 1.;
                    for (int i = 0; i < 2; i++) {
                        if (rawdata[indices[i]][d] == 1)
                            outerProduct *= params[4 * l1 + i];
                        else
                            outerProduct *= (1. - params[4 * l1 + i]);
                    }
                    for (int i = 2; i < 4; i++) {
                        if (rawdata[indices[i]][d] == 1)
                            outerProduct *= params[4 * l2 + i];
                        else
                            outerProduct *= (1. - params[4 * l2 + i]);
                    }
                    if (l1 == 1 && l2 == 1)
                        outerProduct *= params[8] * params[10];
                    else if (l1 == 1 && l2 == 0)
                        outerProduct *= params[8] * (1. - params[10]);
                    else if (l1 == 0 && l2 == 1)
                        outerProduct *= (1. - params[8]) * params[9];
                    else
                        outerProduct *= (1. - params[8]) * (1. - params[9]);
                    sum += outerProduct;
                }
            loglike -= Math.log(sum);
        }
        return loglike;
    }*/

    private double scoreParams(double params[]) {
        double impProb[][][][] = new double[2][2][2][2];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int p = 0; p < 2; p++) {
                    for (int q = 0; q < 2; q++) {
                        impProb[i][j][p][q] = 0.;
                        for (int p1 = 0; p1 < 2; p1++) {
                            for (int p2 = 0; p2 < 2; p2++) {
                                impProb[i][j][p][q] += Math.pow(params[4 * p1],
                                        i) *
                                        Math.pow(1 - params[4 * p1], 1 - i) *
                                        Math.pow(params[4 * p1 + 1], j) *
                                        Math.pow(1 - params[4 * p1 + 1],
                                                1 - j) *
                                        Math.pow(params[4 * p2 + 2], p) *
                                        Math.pow(1 - params[4 * p2 + 2],
                                                1 - p) *
                                        Math.pow(params[4 * p2 + 3], q) *
                                        Math.pow(1 - params[4 * p2 + 3],
                                                1 - q) *
                                        Math.pow(params[8], p1) *
                                        Math.pow(1 - params[8], 1 - p1) *
                                        Math.pow(Math.pow(params[9], p2) *
                                                        Math.pow(1 - params[9], 1 - p2),
                                                1 - p1) * Math.pow(Math.pow(
                                        params[10], p2) *
                                        Math.pow(1 - params[10], 1 - p2), p1);
                            }
                        }
                    }
                }
            }
        }
        double chisq = 0.;
        double prob;
        int count;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int p = 0; p < 2; p++) {
                    for (int q = 0; q < 2; q++) {
                        count =
                                counts4[indices[0]][indices[1]][indices[2]][indices[3]][i][j][p][q];
                        if (count != 0) {
                            prob = (double) count / dataSet.getNumRows();
                            chisq += count *
                                    Math.log(prob / impProb[i][j][p][q]);
                            //chisq += (prob - impProb[i][j][p][q]) * (prob - impProb[i][j][p][q]) / impProb[i][j][p][q];
                        }
                    }
                }
            }
        }
        return 2. * chisq;
    }

    private void debugParams(double params[]) {
        double impProb[][][][] = new double[2][2][2][2];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int p = 0; p < 2; p++) {
                    for (int q = 0; q < 2; q++) {
                        impProb[i][j][p][q] = 0.;
                        for (int p1 = 0; p1 < 2; p1++) {
                            for (int p2 = 0; p2 < 2; p2++) {
                                impProb[i][j][p][q] += Math.pow(params[4 * p1],
                                        i) *
                                        Math.pow(1 - params[4 * p1], 1 - i) *
                                        Math.pow(params[4 * p1 + 1], j) *
                                        Math.pow(1 - params[4 * p1 + 1],
                                                1 - j) *
                                        Math.pow(params[4 * p2 + 2], p) *
                                        Math.pow(1 - params[4 * p2 + 2],
                                                1 - p) *
                                        Math.pow(params[4 * p2 + 3], q) *
                                        Math.pow(1 - params[4 * p2 + 3],
                                                1 - q) *
                                        Math.pow(params[8], p1) *
                                        Math.pow(1 - params[8], 1 - p1) *
                                        Math.pow(Math.pow(params[9], p2) *
                                                        Math.pow(1 - params[9], 1 - p2),
                                                1 - p1) * Math.pow(Math.pow(
                                        params[10], p2) *
                                        Math.pow(1 - params[10], 1 - p2), p1);
                            }
                        }
                    }
                }
            }
        }
        double impProb2[][][][] = new double[2][2][2][2];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int p = 0; p < 2; p++) {
                    for (int q = 0; q < 2; q++) {
                        impProb2[i][j][p][q] = 0.;
                        for (int p1 = 0; p1 < 2; p1++) {
                            for (int p2 = 0; p2 < 2; p2++) {
                                double product = 1.;
                                if (i == 1) {
                                    product *= params[4 * p1];
                                } else {
                                    product *= 1 - params[4 * p1];
                                }
                                if (j == 1) {
                                    product *= params[4 * p1 + 1];
                                } else {
                                    product *= 1 - params[4 * p1 + 1];
                                }
                                if (p == 1) {
                                    product *= params[4 * p2 + 2];
                                } else {
                                    product *= 1 - params[4 * p2 + 2];
                                }
                                if (q == 1) {
                                    product *= params[4 * p2 + 3];
                                } else {
                                    product *= 1 - params[4 * p2 + 3];
                                }
                                if (p1 == 1 && p2 == 1) {
                                    product *= params[8] * params[10];
                                } else if (p1 == 1 && p2 == 0) {
                                    product *= params[8] * (1. - params[10]);
                                } else if (p1 == 0 && p2 == 1) {
                                    product *= (1. - params[8]) * params[9];
                                } else {
                                    product *=
                                            (1. - params[8]) * (1. - params[9]);
                                }
                                impProb2[i][j][p][q] += product;
                            }
                        }
                    }
                }
            }
        }
        int count;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int p = 0; p < 2; p++) {
                    for (int q = 0; q < 2; q++) {
                        count =
                                counts4[indices[0]][indices[1]][indices[2]][indices[3]][i][j][p][q];
                        if (count != 0) {
                            System.out.println(impProb[i][j][p][q] + " x " +
                                    (double) count / dataSet.getNumRows() +
                                    " x " + impProb2[i][j][p][q]);
                        }
                    }
                }
            }
        }
        System.out.println();
        for (int i = 0; i < 11; i++) {
            System.out.println(params[i]);
        }
        System.out.println();
    }

    private void estimateTwoFactorModel(double params[]) {
        double bestScore = Double.MAX_VALUE;
        double bestParams[] = new double[params.length];
        for (int i = 0; i < 5; i++) {
            for (int c = 0; c < 11; c++) {
                params[c] = RandomUtil.getInstance().nextDouble() / 2. + 0.2;
            }

            MultivariateOptimizer search = new PowellOptimizer(1e-7, 1e-7);

            PointValuePair pair = search.optimize(
                    new InitialGuess(params),
                    new ObjectiveFunction(new FittingFunction(this)),
                    GoalType.MINIMIZE,
                    new MaxEval(100000));

            double newScore = scoreParams(pair.getPoint());

            if (newScore < bestScore) {
                System.arraycopy(params, 0, bestParams, 0, params.length);
                bestScore = newScore;
            }
            //System.out.println(scoreParams(params));
            //for (int c = 0; c < 11; c++)
            //    System.out.println(params[c]); System.exit(0);
        }
        System.arraycopy(bestParams, 0, params, 0, params.length);
        //System.out.println();
    }

    static class FittingFunction implements MultivariateFunction {

        /**
         * The wrapped model.
         */
        private final BinaryTetradTest estimator;

        /**
         * Constructs a new CoefFittingFunction for the given Sem.
         */
        public FittingFunction(BinaryTetradTest estimator) {
            this.estimator = estimator;
        }

        /**
         * Computes the maximum likelihood function value for the given argument values as given by the optimizer. These
         * values are mapped to parameter values.
         */
        public double value(final double[] argument) {
            return this.estimator.paramsLikelihood(argument);
        }

        public double evaluate(final double[] argument, double gradient[]) {
            computeGradient(argument, gradient);
            return this.estimator.paramsLikelihood(argument);
        }

        public void computeGradient(final double[] argument,
                                    double[] gradient) {
            this.estimator.computeTwoFactorGradient(argument, gradient);
        }

        /**
         * @return the number of arguments. Required by the MultivariateFunction interface.
         */
        public int getNumArguments() {
            return 11;
        }

        /**
         * @return the lower bound of argument n. Required by the MultivariateFunction interface.
         */
        public double getLowerBound(final int n) {
            return 0.01;
        }

        /**
         * @return the upper bound of argument n. Required by the MultivariateFunction interface.
         */
        public double getUpperBound(final int n) {
            return 0.99;
        }
    }

    public double testOneTetradBootstrap(int i, int j, int k, int l) {
        indices[0] = i;
        indices[1] = j;
        indices[2] = k;
        indices[3] = l;
        double params[] = new double[11];
        estimateTwoFactorModel(params);

        String vNames[] = new String[4];
        for (int v1 = 0; v1 < 4; v1++) {
            vNames[v1] = dataSet.getVariableNames().get(indices[v1]);
        }

        Graph factorModel = new EdgeListGraph();
        Node eta1 = new GraphNode("eta1");
        eta1.setNodeType(NodeType.LATENT);
        factorModel.addNode(eta1);
        Node eta2 = new GraphNode("eta2");
        eta2.setNodeType(NodeType.LATENT);
        factorModel.addNode(eta2);
        for (int n = 0; n < 4; n++) {
            Node xi = new GraphNode(vNames[n]);
            factorModel.addNode(xi);
            if (n < 2) {
                factorModel.addDirectedEdge(eta1, xi);
            } else {
                factorModel.addDirectedEdge(eta2, xi);
            }
        }
        factorModel.addDirectedEdge(eta1, eta2);
        BayesPm bayesPm = new BayesPm(new Dag(factorModel));
        MlBayesIm bayesIm = new MlBayesIm(bayesPm);
        bayesIm.setProbability(0, 0, 0, 1. - params[8]);
        bayesIm.setProbability(0, 0, 1, params[8]);
        bayesIm.setProbability(1, 0, 0, 1. - params[9]);
        bayesIm.setProbability(1, 0, 1, params[9]);
        bayesIm.setProbability(1, 1, 0, 1. - params[10]);
        bayesIm.setProbability(1, 1, 1, params[10]);
        for (int n = 0; n < 4; n++) {
            bayesIm.setProbability(n + 2, 0, 0, 1 - params[n]);
            bayesIm.setProbability(n + 2, 0, 1, params[n]);
            bayesIm.setProbability(n + 2, 1, 0, 1 - params[n + 4]);
            bayesIm.setProbability(n + 2, 1, 1, params[n + 4]);
        }

        //System.out.println(bayesIm.toString()); System.exit(0);

        debugParams(params);

        int counts2[][] = new int[4][4];
        int counts4[][][][] = new int[4][4][4][4];
        tetradTest(i, k, l, j);
        indices[0] = i;
        indices[1] = j;
        indices[2] = k;
        indices[3] = l;
        double tetradPvalue = stat * stat;
        //System.out.println("tetrad stat = " + tetradPvalue);
        int successes = 0;
        for (int b = 0; b < 10; b++) {
            //System.out.println(b);
            DataSet dataBoot =
                    bayesIm.simulateData(dataSet.getNumRows(), false);
            for (int v1 = 0; v1 < 4; v1++) {
                for (int v2 = 0; v2 < 4; v2++) {
                    counts2[v1][v2] = 0;
                    for (int v3 = 0; v3 < 4; v3++) {
                        for (int v4 = 0; v4 < 4; v4++) {
                            counts4[v1][v2][v3][v4] = 0;
                        }
                    }
                }
            }
//            int rawDataBoot[][] = dataBoot.getIntMatrixTransposed();
//            for (int d = 0; d < dataBoot.getNumRows(); d++) {
//                for (int v1 = 0; v1 < 4; v1++) {
//                    for (int v2 = 0; v2 < 4; v2++) {
//                        counts2[v1][v2] += rawDataBoot[v1][d] *
//                                rawDataBoot[v2][d];
//                        for (int v3 = 0; v3 < 4; v3++) {
//                            for (int v4 = 0; v4 < 4; v4++) {
//                                counts4[v1][v2][v3][v4] += rawDataBoot[v1][d] *
//                                        rawDataBoot[v2][d] *
//                                        rawDataBoot[v3][d] *
//                                        rawDataBoot[v4][d];
//                            }
//                        }
//                    }
//                }
//            }
//            int rawDataBoot[][] = dataBoot.getIntMatrixTransposed();
            for (int d = 0; d < dataBoot.getNumRows(); d++) {
                for (int v1 = 0; v1 < 4; v1++) {
                    for (int v2 = 0; v2 < 4; v2++) {
                        counts2[v1][v2] +=
                                dataBoot.getInt(d, v1) * dataBoot.getInt(d, v2);
                        for (int v3 = 0; v3 < 4; v3++) {
                            for (int v4 = 0; v4 < 4; v4++) {
                                counts4[v1][v2][v3][v4] += dataBoot.getInt(d,
                                        v1) * dataBoot.getInt(d, v2) *
                                        dataBoot.getInt(d, v3) *
                                        dataBoot.getInt(d, v4);
                            }
                        }
                    }
                }
            }

            int counts42[][][][] = new int[2][2][2][2];
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 2; y++) {
                    for (int z = 0; z < 2; z++) {
                        for (int w = 0; w < 2; w++) {
                            counts42[x][y][z][w] = 0;
                        }
                    }
                }
            }
            for (int d = 0; d < dataBoot.getNumRows(); d++) {
//                counts42[rawDataBoot[0][d]][rawDataBoot[1][d]][rawDataBoot[2][d]][rawDataBoot[3][d]]++;
                counts42[dataBoot.getInt(d, 0)][dataBoot.getInt(d,
                        1)][dataBoot.getInt(d, 2)][dataBoot.getInt(d, 3)]++;
            }
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 2; y++) {
                    for (int z = 0; z < 2; z++) {
                        for (int w = 0; w < 2; w++) {
                            System.out.println(counts42[x][y][z][w] + " x " +
                                    this.counts4[i][j][k][l][x][y][z][w]);
                        }
                    }
                }
            }
            System.out.println();
            System.exit(0);

            double tetradBoot = tetradStat(0, 2, 3, 1, counts2, counts4);
            tetradBoot *= tetradBoot;
            //System.out.println("tetrad boot = " + tetradBoot);
            if (tetradBoot >= tetradPvalue) {
                successes++;
            }
        }
        return (double) successes / 10;
    }

    private double tetradStat(int i, int j, int k, int l, int counts2[][],
                              int counts4[][][][]) {
        double a = (double) counts2[i][j] / dataSet.getNumRows(), b =
                (double) counts2[k][l] / dataSet.getNumRows(), c =
                (double) counts2[i][k] / dataSet.getNumRows(), d =
                (double) counts2[j][l] / dataSet.getNumRows();
        double e = (double) counts2[i][i] / dataSet.getNumRows(), f =
                (double) counts2[j][j] / dataSet.getNumRows(), g =
                (double) counts2[k][k] / dataSet.getNumRows(), h =
                (double) counts2[l][l] / dataSet.getNumRows();

        double pi[] = new double[8];
        pi[0] = a;
        pi[1] = b;
        pi[2] = c;
        pi[3] = d;
        pi[4] = e;
        pi[5] = f;
        pi[6] = g;
        pi[7] = h;
        double jacobian[] = new double[8];
        jacobian[0] = b - g * h;
        jacobian[1] = a - e * f;
        jacobian[2] = f * h - d;
        jacobian[3] = e * g - c;
        jacobian[4] = d * g - b * f;
        jacobian[5] = c * h - b * e;
        jacobian[6] = d * e - a * h;
        jacobian[7] = c * f - a * g;

        double pCov[][] = new double[8][8];
        for (int x = 0; x < 8; x++) {
            int idx1 = -1, idx2 = -1;
            switch (x) {
                case 0:
                    idx1 = i;
                    idx2 = j;
                    break;
                case 1:
                    idx1 = k;
                    idx2 = l;
                    break;
                case 2:
                    idx1 = i;
                    idx2 = k;
                    break;
                case 3:
                    idx1 = j;
                    idx2 = l;
                    break;
                case 4:
                    idx1 = i;
                    idx2 = i;
                    break;
                case 5:
                    idx1 = j;
                    idx2 = j;
                    break;
                case 6:
                    idx1 = k;
                    idx2 = k;
                    break;
                case 7:
                    idx1 = l;
                    idx2 = l;
                    break;
            }
            for (int y = x; y < 8; y++) {
                int idx3 = -1, idx4 = -1;
                switch (y) {
                    case 0:
                        idx3 = i;
                        idx4 = j;
                        break;
                    case 1:
                        idx3 = k;
                        idx4 = l;
                        break;
                    case 2:
                        idx3 = i;
                        idx4 = k;
                        break;
                    case 3:
                        idx3 = j;
                        idx4 = l;
                        break;
                    case 4:
                        idx3 = i;
                        idx4 = i;
                        break;
                    case 5:
                        idx3 = j;
                        idx4 = j;
                        break;
                    case 6:
                        idx3 = k;
                        idx4 = k;
                        break;
                    case 7:
                        idx3 = l;
                        idx4 = l;
                        break;
                }
                pCov[x][y] = counts4[idx1][idx2][idx3][idx4] - pi[x] * pi[y];
                pCov[y][x] = pCov[x][y];
            }
        }

        double mle = (a - e * f) * (b - g * h) - (c - e * g) * (d - f * h);
        double var = MatrixUtils.innerProduct(
                MatrixUtils.product(jacobian, pCov), jacobian);
        double std = Math.sqrt(var / dataSet.getNumRows());
        System.out.println("stat = " + (-Math.abs(mle) / std));
        return -Math.abs(mle) / std;
    }


    public double tetradStat(int i, int j, int k, int l) {
        double a = this.pis[i][j], b = this.pis[k][l], c = this.pis[i][k], d =
                this.pis[j][l];
        double e = this.pis[i][i], f = this.pis[j][j], g = this.pis[k][k], h =
                this.pis[l][l];

        this.indices[0] = i;
        this.indices[1] = j;
        this.indices[2] = k;
        this.indices[3] = l;
        computeCounts4(this.indices);

        double pi[] = new double[8];
        pi[0] = a;
        pi[1] = b;
        pi[2] = c;
        pi[3] = d;
        pi[4] = e;
        pi[5] = f;
        pi[6] = g;
        pi[7] = h;
        double jacobian[] = new double[8];
        jacobian[0] = b - g * h;
        jacobian[1] = a - e * f;
        jacobian[2] = f * h - d;
        jacobian[3] = e * g - c;
        jacobian[4] = d * g - b * f;
        jacobian[5] = c * h - b * e;
        jacobian[6] = d * e - a * h;
        jacobian[7] = c * f - a * g;

        double pCov[][] = new double[8][8];
        for (int x = 0; x < 8; x++) {
            int idx1 = -1, idx2 = -1;
            switch (x) {
                case 0:
                    idx1 = 0;
                    idx2 = 1;
                    break;
                case 1:
                    idx1 = 2;
                    idx2 = 3;
                    break;
                case 2:
                    idx1 = 0;
                    idx2 = 2;
                    break;
                case 3:
                    idx1 = 1;
                    idx2 = 3;
                    break;
                case 4:
                    idx1 = 0;
                    idx2 = 0;
                    break;
                case 5:
                    idx1 = 1;
                    idx2 = 1;
                    break;
                case 6:
                    idx1 = 2;
                    idx2 = 2;
                    break;
                case 7:
                    idx1 = 3;
                    idx2 = 3;
                    break;
            }
            for (int y = x; y < 8; y++) {
                int idx3 = -1, idx4 = -1;
                switch (y) {
                    case 0:
                        idx3 = 0;
                        idx4 = 1;
                        break;
                    case 1:
                        idx3 = 2;
                        idx4 = 3;
                        break;
                    case 2:
                        idx3 = 0;
                        idx4 = 2;
                        break;
                    case 3:
                        idx3 = 1;
                        idx4 = 3;
                        break;
                    case 4:
                        idx3 = 0;
                        idx4 = 0;
                        break;
                    case 5:
                        idx3 = 1;
                        idx4 = 1;
                        break;
                    case 6:
                        idx3 = 2;
                        idx4 = 2;
                        break;
                    case 7:
                        idx3 = 3;
                        idx4 = 3;
                        break;
                }
                pCov[x][y] = this.pis4[idx1][idx2][idx3][idx4] - pi[x] * pi[y];
                pCov[y][x] = pCov[x][y];
            }
        }

        double mle = (a - e * f) * (b - g * h) - (c - e * g) * (d - f * h);
        double var = MatrixUtils.innerProduct(
                MatrixUtils.product(jacobian, pCov), jacobian);
        double std = Math.sqrt(var / dataSet.getNumRows());
        return mle / std;
    }

    public double tetradTest2(int i, int j, int k, int l) {
        double a = this.pis[i][j], b = this.pis[k][l], c = this.pis[i][k], d =
                this.pis[j][l];
        double e = this.pis[i][i], f = this.pis[j][j], g = this.pis[k][k], h =
                this.pis[l][l];

        this.indices[0] = i;
        this.indices[1] = j;
        this.indices[2] = k;
        this.indices[3] = l;
        computeCounts4(this.indices);

        double pi[] = new double[8];
        pi[0] = a;
        pi[1] = b;
        pi[2] = c;
        pi[3] = d;
        pi[4] = e;
        pi[5] = f;
        pi[6] = g;
        pi[7] = h;
        double jacobian[] = new double[8];
        jacobian[0] = b - g * h;
        jacobian[1] = a - e * f;
        jacobian[2] = f * h - d;
        jacobian[3] = e * g - c;
        jacobian[4] = d * g - b * f;
        jacobian[5] = c * h - b * e;
        jacobian[6] = d * e - a * h;
        jacobian[7] = c * f - a * g;

        double jacobian2[] = new double[16];
        jacobian2[0] = 0.;
        jacobian2[1] = jacobian[7];
        jacobian2[2] = jacobian[6];
        jacobian2[3] = jacobian[1] + jacobian[6] + jacobian[7];
        jacobian2[4] = jacobian[5];
        jacobian2[5] = jacobian[3] + jacobian[5] + jacobian[7];
        jacobian2[6] = jacobian[5] + jacobian[6];
        jacobian2[7] = jacobian[3] + jacobian[5] + jacobian[6] + jacobian[7];
        jacobian2[8] = jacobian[4];
        jacobian2[9] = jacobian[4] + jacobian[7];
        jacobian2[10] = jacobian[2] + jacobian[4] + jacobian[6];
        jacobian2[11] = jacobian[2] + jacobian[1] + jacobian[4] + jacobian[6] +
                jacobian[7];
        jacobian2[12] = jacobian[0] + jacobian[4] + jacobian[5];
        jacobian2[13] = jacobian[0] + jacobian[3] + jacobian[4] + jacobian[5] +
                jacobian[7];
        jacobian2[14] = jacobian[0] + jacobian[2] + jacobian[4] + jacobian[5] +
                jacobian[6];
        jacobian2[15] = jacobian[0] + jacobian[1] + jacobian[2] + jacobian[3] +
                jacobian[4] + jacobian[5] + jacobian[6] + jacobian[7];


        double pCov[][] = new double[16][16];
        for (int v1 = 0; v1 < 2; v1++) {
            for (int v2 = 0; v2 < 2; v2++) {
                for (int v3 = 0; v3 < 2; v3++) {
                    for (int v4 = 0; v4 < 2; v4++) {
                        for (int vv1 = 0; vv1 < 2; vv1++) {
                            for (int vv2 = 0; vv2 < 2; vv2++) {
                                for (int vv3 = 0; vv3 < 2; vv3++) {
                                    for (int vv4 = 0; vv4 < 2; vv4++) {
                                        int idx1 =
                                                8 * v1 + 4 * v2 + 2 * v3 + v4;
                                        int idx2 = 8 * vv1 + 4 * vv2 + 2 * vv3 +
                                                vv4;
                                        double prob1 =
                                                (double) this.counts4[i][j][k][l][v1][v2][v3][v4] /
                                                        sampleSize;
                                        double prob2 =
                                                (double) this.counts4[i][j][k][l][vv1][vv2][vv3][vv4] /
                                                        sampleSize;
                                        //System.out.println("idx1 = " + idx1 + ", idx2 = " + idx2);
                                        if (idx1 == idx2) {
                                            //System.out.println(v1 + " " + v2 + " " + v3 + " " + v4 + " = " + prob1);
                                            pCov[idx1][idx2] =
                                                    prob1 * (1. - prob1);
                                        } else {
                                            pCov[idx1][idx2] = -prob1 * prob2;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        double jacobian3[] = new double[15];
        for (int v = 0; v < 15; v++) {
            jacobian3[v] = jacobian2[v + 1];
        }
        double pCov3[][] = new double[15][15];
        for (int v = 0; v < 15; v++) {
            for (int v2 = 0; v2 < 15; v2++) {
                pCov3[v][v2] = pCov[v + 1][v2 + 1];
            }
        }
        double mle = (a - e * f) * (b - g * h) - (c - e * g) * (d - f * h);
        double var = MatrixUtils.innerProduct(
                MatrixUtils.product(jacobian3, pCov3), jacobian3);
        double std = Math.sqrt(var / dataSet.getNumRows());
        stat = -Math.abs(mle) / std;
        System.out.println("original stat = " + stat);
        return 2.0 * ProbUtils.normalCdf(stat);
    }

    public int tempTetradScore(int v1, int v2, int v3, int v4) {
        evalTetradDifferences(v1, v2, v3, v4);
        System.out.println(prob[0]);
        System.out.println(prob[1]);
        System.out.println(prob[2]);
        for (int i = 0; i < 3; i++) {
            bvalues[i] = (prob[i] >= sig);
        }
        //Order p-values for FDR (false discovery rate) decision
        if (prob[1] < prob[0] && prob[1] < prob[2]) {
            tempProb = prob[0];
            prob[0] = prob[1];
            prob[1] = tempProb;
        } else if (prob[2] < prob[0] && prob[2] < prob[0]) {
            tempProb = prob[0];
            prob[0] = prob[2];
            prob[2] = tempProb;
        }
        if (prob[2] < prob[1]) {
            tempProb = prob[1];
            prob[1] = prob[2];
            prob[2] = tempProb;
        }
        if (prob[2] <= sig3) {
            return 0;
        }
        if (prob[1] <= sig2) {
            return 1;
        }
        if (prob[0] <= sig1) {
            //This is the case of 2 tetrad constraints holding, which is
            //a logical impossibility. On a future version we may come up with
            //better, more powerful ways of deciding what to do. Right now,
            //the default is to do just as follows:
            return 3;
        }
        return 3;
    }


    public ICovarianceMatrix getCovMatrix() {
        return null;
    }
}






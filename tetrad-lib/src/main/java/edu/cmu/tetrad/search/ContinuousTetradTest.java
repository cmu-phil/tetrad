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

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.Arrays;
import java.util.List;

import static java.lang.Math.abs;

//import edu.cmu.tetrad.sem.MimBuildEstimator;

/**
 * Implements different tests of tetrad constraints: using Wishart's test (CPS, Wishart 1928); Bollen's test (Bollen,
 * 1990) or a more computationally intensive test that fits one/two factor Gaussian models. These tests are the core
 * statistical procedure of search algorithm BuildPureClusters and Purify.
 * <p>
 * References:
 * <p>
 * Bollen, K. (1990). "Outlier screening and distribution-free test for vanishing tetrads." Sociological Methods and
 * Research 19, 80-92.
 * <p>
 * Wishart, J. (1928). "Sampling errors in the theory of two factors". British Journal of Psychology 19, 180-187.
 *
 * @author Ricardo Silva
 */

public final class ContinuousTetradTest implements TetradTest {
    private double sig;
    private double sig1;
    private double sig2;
    private double sig3;
    private double[] prob;
    //    private double fourthMM[][][][];
    private boolean[] bvalues;
    private boolean outputMessage;
    private ICovarianceMatrix covMatrix;
    //    private CorrelationMatrix corrMatrix;
    private Matrix rho;
    private TestType sigTestType;
    private int sampleSize;
    private final DataSet dataSet;
    private OneFactorEstimator oneFactorEst4, oneFactorEst5, oneFactorEst6;
    private TwoFactorsEstimator twoFactorsEst4, twoFactorsEst5, twoFactorsEst6;
    private Matrix bufferMatrix;
    //    private Map<Tetrad, Double> tetradDifference;
    private List<Node> variables;
    DeltaTetradTest deltaTest;

    public ContinuousTetradTest(final DataSet dataSet, TestType sigTestType,
                                final double sig) {
        if (sigTestType == TestType.TETRAD_BOLLEN || sigTestType == null) {
            sigTestType = TestType.TETRAD_DELTA;
        }

        if (!(sigTestType == TestType.TETRAD_WISHART ||
                sigTestType == TestType.TETRAD_DELTA ||
                sigTestType == TestType.GAUSSIAN_FACTOR)) {
            throw new IllegalArgumentException("Unexpected type: " + sigTestType);
        }

        if (dataSet == null) {
            throw new NullPointerException("Data set must not be null.");
        }

//        deltaTest = new DeltaTetradTest(dataSet);

        this.covMatrix = new CovarianceMatrix(dataSet);
        this.dataSet = dataSet;
        this.sigTestType = sigTestType;
        setSignificance(sig);
        this.sampleSize = dataSet.getNumRows();
        this.variables = dataSet.getVariables();
//        if (sigTestType == TestType.TETRAD_DELTA) {
//            setCovMatrix(new CovarianceMatrix(dataSet));
//            fourthMM = getFourthMomentsMatrix(dataSet);
//        }


        initialization();
    }

    public ContinuousTetradTest(final ICovarianceMatrix covMatrix,
                                final TestType sigTestType, final double sig) {
        if (!(sigTestType == TestType.TETRAD_WISHART ||
                sigTestType == TestType.TETRAD_DELTA ||
                sigTestType == TestType.GAUSSIAN_FACTOR)) {
            throw new IllegalArgumentException("Unexpected type: " + sigTestType);
        }
        this.dataSet = null;

        this.deltaTest = new DeltaTetradTest(covMatrix);

//        this.corrMatrix = new CorrelationMatrix(covMatrix);
        this.setCovMatrix(covMatrix);
        this.sigTestType = sigTestType;
        setSignificance(sig);
        this.sampleSize = covMatrix.getSize();
        initialization();

        this.variables = covMatrix.getVariables();
    }

    public ContinuousTetradTest(final CorrelationMatrix correlationMatrix,
                                final TestType sigTestType, final double sig) {
        if (!(sigTestType == TestType.TETRAD_WISHART ||
                sigTestType == TestType.TETRAD_DELTA ||
                sigTestType == TestType.GAUSSIAN_FACTOR)) {
            throw new IllegalArgumentException("Unexpected type: " + sigTestType);
        }

        if (correlationMatrix == null) {
            throw new NullPointerException();
        }

        this.dataSet = null;
//        this.corrMatrix = correlationMatrix;
        this.setCovMatrix(correlationMatrix);
        this.sigTestType = sigTestType;
        setSignificance(sig);
        this.sampleSize = correlationMatrix.getSize();
        initialization();

        this.variables = correlationMatrix.getVariables();
    }

    public double getSignificance() {
        return this.sig;
    }

    public void setSignificance(final double sig) {
        this.sig = sig;
        this.sig1 = sig / 3.;
        this.sig2 = 2. * sig / 3.;
        this.sig3 = sig;
    }

    public DataSet getDataSet() {
        return this.dataSet;
    }

//    public CorrelationMatrix getCorrMatrix() {
//        return this.corrMatrix;
//    }

    @Override
    public ICovarianceMatrix getCovMatrix() {
        if (this.covMatrix != null) {
            return this.covMatrix;
        }
        if (this.dataSet != null) {
            this.covMatrix = new CovarianceMatrix(this.dataSet);
            return this.covMatrix;
        }
        throw new IllegalStateException();
//        return corrMatrix;
    }

    public String[] getVarNames() {
        return this.covMatrix.getVariableNames().toArray(new String[0]);
    }

    public List<Node> getVariables() {
        if (this.variables == null) {
            if (this.dataSet != null) {
                this.variables = this.dataSet.getVariables();
            } else if (getCovMatrix() != null) {
                this.variables = getCovMatrix().getVariables();
            }
        }

        return this.variables;
    }

    public TestType getTestType() {
        return this.sigTestType;
    }

    public void setTestType(final TestType sigTestType) {
        this.sigTestType = sigTestType;
    }

    private void initialization() {
        this.sampleSize = this.covMatrix.getSampleSize();
        this.outputMessage = false;
        this.prob = new double[3];
        this.bvalues = new boolean[3];
        this.oneFactorEst4 = new OneFactorEstimator(this.covMatrix, this.sig, 4);
        this.oneFactorEst5 = new OneFactorEstimator(this.covMatrix, this.sig, 5);
        this.oneFactorEst6 = new OneFactorEstimator(this.covMatrix, this.sig, 6);
        this.twoFactorsEst4 = new TwoFactorsEstimator(this.covMatrix, this.sig, 4);
        this.twoFactorsEst5 = new TwoFactorsEstimator(this.covMatrix, this.sig, 5);
        this.twoFactorsEst6 = new TwoFactorsEstimator(this.covMatrix, this.sig, 6);
        this.bufferMatrix = new Matrix(4, 4);
        this.rho = this.covMatrix.getMatrix();
    }

    public int tetradScore(final int v1, final int v2, final int v3, final int v4) {
        final boolean holds = wishartEvalTetradDifferences2(v1, v2, v3, v4, this.sig);
        if (!holds) return 1;
        else return 3;
    }

    /**
     * Tests the tetrad (v1, v3) x (v2, v4) = (v1, v4) x (v2, v3)
     */

    public boolean tetradScore1(final int v1, final int v2, final int v3, final int v4) {
        /*if (tetradHolds(v1, v3, v4, v2) != tetradHolds(v4, v2, v1, v3)) {
            System.out.println("!");
            modeX = true;
            tetradHolds(v1, v3, v4, v2);
            System.out.println(prob[0]);
            tetradHolds(v4, v2, v1, v3);
            System.out.println(prob[0]);
            System.exit(0);
        }*/
        return tetradHolds(v1, v3, v4, v2) && !tetradHolds(v1, v3, v2, v4) &&
                !tetradHolds(v1, v4, v2, v3);
    }

    /**
     * Tests if all tetrad constraints hold
     */

    public boolean tetradScore3(final int v1, final int v2, final int v3, final int v4) {
        if (this.sigTestType != TestType.GAUSSIAN_FACTOR) {
            return tetradScore(v1, v2, v3, v4) == 3;
        } else {
            return oneFactorTest(v1, v2, v3, v4);
        }
    }

    public boolean tetradHolds(final int v1, final int v2, final int v3, final int v4) {
        evalTetradDifference(v1, v2, v3, v4);
        this.bvalues[0] = (this.prob[0] >= this.sig);
        return this.prob[0] >= this.sig;
    }

    public double tetradPValue(final int v1, final int v2, final int v3, final int v4) {
        evalTetradDifference(v1, v2, v3, v4);
        return this.prob[0];
    }

    public double tetradPValue(final int i1, final int j1, final int k1, final int l1, final int i2, final int j2, final int k2, final int l2) {
        evalTetradDifference(i1, j1, k1, l1, i2, j2, k2, l2);
        return this.prob[0];
    }


    /**
     * --------------------------------------------------------------------------
     * PRIVATE METHODS
     */

//    /**
//     * Note: this implementation could be more optimized. This is the simplest way of computing this matrix, and will
//     * take exactly sampleSize * (corrMatrix.getSize() ^ 4) steps.
//     */
//
//    private double[][][][] getFourthMomentsMatrix(DataSet dataSet) {
//        printlnMessage(
//                "Bollen's test preparation: starting computation of fourth moments");
//        int numVars = corrMatrix.getSize();
//        double fourthMM[][][][] = new double[numVars][numVars][numVars][numVars];
//
//        double data[][] = dataSet.getDoubleData().transpose().toArray();
//        double means[] = new double[numVars];
//
//        for (int i = 0; i < numVars; i++) {
//            means[i] = 0.;
//        }
//
//        for (int d = 0; d < sampleSize; d++) {
//            for (int i = 0; i < numVars; i++) {
//                means[i] += data[i][d];
//            }
//        }
//
//        for (int i = 0; i < numVars; i++) {
//            means[i] /= sampleSize;
//        }
//
//        for (int i = 0; i < numVars; i++) {
//            for (int j = 0; j < numVars; j++) {
//                for (int k = 0; k < numVars; k++) {
//                    for (int t = 0; t < numVars; t++) {
//                        fourthMM[i][j][k][t] = 0.;
//                    }
//                }
//            }
//        }
//
//        for (int d = 0; d < sampleSize; d++) {
//            for (int i = 0; i < numVars; i++) {
//                for (int j = 0; j < numVars; j++) {
//                    for (int k = 0; k < numVars; k++) {
//                        for (int t = 0; t < numVars; t++) {
//                            fourthMM[i][j][k][t] += (data[i][d] - means[i]) *
//                                    (data[j][d] - means[j]) *
//                                    (data[k][d] - means[k]) *
//                                    (data[t][d] - means[t]);
//                        }
//                    }
//                }
//            }
//        }
//
//        for (int i = 0; i < numVars; i++) {
//            for (int j = 0; j < numVars; j++) {
//                for (int k = 0; k < numVars; k++) {
//                    for (int t = 0; t < numVars; t++) {
//                        fourthMM[i][j][k][t] /= sampleSize;
//                    }
//                }
//            }
//        }
//
//        printlnMessage("Done with fourth moments");
//        return fourthMM;
//    }
    private void evalTetradDifferences(final int i, final int j, final int k, final int l) {
        switch (this.sigTestType) {
            case TETRAD_BASED:
            case TETRAD_WISHART:
                wishartEvalTetradDifferences(i, j, k, l);
                break;
            case TETRAD_DELTA:
                bollenEvalTetradDifferences(i, j, k, l);
                break;
            default:
                /*
                 * The other tests are only for interface with Purify. The ContinuousTetradTest class is also
                 * used as a black box of arguments passed to Purify (e.g., see BuildPureClusters code), but it does
                 * not mean its internal tetrad tests are going to be used. See Purify.scoreBasedPurify(List) to
                 * see a situation when this happens.
                 */
                assert false;
        }
    }

    private void evalTetradDifference(final int i, final int j, final int k, final int l) {
        switch (this.sigTestType) {
            case TETRAD_BASED:
            case TETRAD_WISHART:
                wishartEvalTetradDifference(i, j, k, l);
                break;
            case TETRAD_DELTA:
                bollenEvalTetradDifference(i, j, k, l);
                break;
            default:
                assert false;
        }
    }

    private void evalTetradDifference(final int i1, final int j1, final int k1, final int l1, final int i2, final int j2, final int k2, final int l2) {
        wishartEvalTetradDifference(i1, j1, k1, l1, i2, j2, k2, l2);
    }


    /**
     * The asymptotic Wishart test for multivariate normal variables. See Wishart (1928).
     */

    private void wishartEvalTetradDifferences(final int i, final int j, final int k, final int l) {
        final double TAUijkl;
        final double TAUijlk;
        final double TAUiklj;
        double ratio;

        TAUijkl = this.rho.get(i, j) * this.rho.get(k, l) -
                this.rho.get(i, k) * this.rho.get(j, l);

        double SD = wishartTestTetradDifference(i, j, k, l);

        ratio = TAUijkl / SD;

//        prob[0] = 2.0 * RandomUtil.getInstance().normalCdf(0, 1, abs(ratio));
        this.prob[0] = 2.0 * ProbUtils.normalCdf(abs(ratio));

        TAUijlk = this.rho.get(i, j) * this.rho.get(k, l) -
                this.rho.get(i, l) * this.rho.get(j, k);

        SD = wishartTestTetradDifference(i, j, l, k);

        ratio = TAUijlk / SD;

//        prob[1] = 2.0 * RandomUtil.getInstance().normalCdf(0, 1, abs(ratio));
        this.prob[1] = 2.0 * ProbUtils.normalCdf(abs(ratio));

        TAUiklj = this.rho.get(i, k) * this.rho.get(j, l) -
                this.rho.get(i, l) * this.rho.get(j, k);

        SD = wishartTestTetradDifference(i, k, l, j);   // A C D B

        ratio = TAUiklj / SD;

//        prob[2] = 2.0 * RandomUtil.getInstance().normalCdf(0, 1, abs(ratio));
        this.prob[2] = 2.0 * ProbUtils.normalCdf(abs(ratio));
    }

    private boolean wishartEvalTetradDifferences2(final int i, final int j, final int k, final int l, final double alpha) {
        final double TAUijkl;
        final double TAUijlk;
        double TAUiklj;
        double ratio;

        TAUijkl = this.rho.get(i, j) * this.rho.get(k, l) -
                this.rho.get(i, k) * this.rho.get(j, l);

        double SD = wishartTestTetradDifference(i, j, k, l);

        ratio = TAUijkl / SD;

//        prob[0] = 2.0 * RandomUtil.getInstance().normalCdf(0, 1, abs(ratio));
        final boolean holds1 = 2.0 * ProbUtils.normalCdf(abs(ratio)) > alpha;

        TAUijlk = this.rho.get(i, j) * this.rho.get(k, l) -
                this.rho.get(i, l) * this.rho.get(j, k);

        SD = wishartTestTetradDifference(i, j, l, k);

        ratio = TAUijlk / SD;

//        prob[1] = 2.0 * RandomUtil.getInstance().normalCdf(0, 1, abs(ratio));
        final boolean holds2 = 2.0 * ProbUtils.normalCdf(abs(ratio)) > alpha;

        return holds1 && holds2;
    }

    private void wishartEvalTetradDifference(final int i, final int j, final int k, final int l) {
        final double TAUijkl;
        double ratio;

        TAUijkl = this.rho.get(i, j) * this.rho.get(k, l) - this.rho.get(i, k) * this.rho.get(j, l);

        final double SD = wishartTestTetradDifference(i, j, k, l);

        ratio = TAUijkl / SD;

        if (ratio > 0.0) {
            ratio = -ratio;
        }

        final double pValue = 2.0 * ProbUtils.normalCdf(ratio);

        this.prob[0] = pValue;

//        TetradLogger.getInstance().log("tetrads", new Tetrad(variables.get(i),
//                variables.get(j), variables.get(k), variables.get(l)).toString()
//                + " = 0, p = " + pValue);
    }

    private void wishartEvalTetradDifference(final int i1, final int j1, final int k1, final int l1, final int i2, final int j2, final int k2, final int l2) {
        final double TAUijkl;
        double ratio;

        TAUijkl = this.rho.get(i1, j1) * this.rho.get(k1, l1) -
                this.rho.get(i2, j2) * this.rho.get(k2, l2);

        final double SD = wishartTestTetradDifference(i1, j2, k2, l2);

        ratio = TAUijkl / SD;

        if (ratio > 0.0) {
            ratio = -ratio;
        }

        this.prob[0] = 2.0 * ProbUtils.normalCdf(ratio);
    }

    private double wishartTestTetradDifference(final int a0, final int a1, final int a2, final int a3) {
        this.bufferMatrix.set(0, 0, this.rho.get(a0, a0));
        this.bufferMatrix.set(0, 1, this.rho.get(a0, a1));
        this.bufferMatrix.set(0, 2, this.rho.get(a0, a2));
        this.bufferMatrix.set(0, 3, this.rho.get(a0, a3));
        this.bufferMatrix.set(1, 0, this.rho.get(a1, a0));
        this.bufferMatrix.set(1, 1, this.rho.get(a1, a1));
        this.bufferMatrix.set(1, 2, this.rho.get(a1, a2));
        this.bufferMatrix.set(1, 3, this.rho.get(a1, a3));
        this.bufferMatrix.set(2, 0, this.rho.get(a2, a0));
        this.bufferMatrix.set(2, 1, this.rho.get(a2, a1));
        this.bufferMatrix.set(2, 2, this.rho.get(a2, a2));
        this.bufferMatrix.set(2, 3, this.rho.get(a2, a3));
        this.bufferMatrix.set(3, 0, this.rho.get(a3, a0));
        this.bufferMatrix.set(3, 1, this.rho.get(a3, a1));
        this.bufferMatrix.set(3, 2, this.rho.get(a3, a2));
        this.bufferMatrix.set(3, 3, this.rho.get(a3, a3));


//        int[] indices = {a0, a1, a2, a3};
//        for (int i = 0; i < 4; i++) {
//            for (int j = 0; j < 4; j++) {
//                bufferMatrix.set(i, j, rho.get(indices[i], indices[j]));
//            }
//        }
//        TetradMatrix sub = rho.getSelection(indices, indices);
        final double product1 = this.rho.get(a0, a0) * this.rho.get(a3, a3) - this.rho.get(a0, a3) * this.rho.get(a0, a3);
        final double product2 = this.rho.get(a1, a1) * this.rho.get(a2, a2) - this.rho.get(a1, a2) * this.rho.get(a1, a2);
        final double n = this.sampleSize;
        final double product3 = (n + 1) / ((n - 1) * (n - 2)) * product1 * product2;
        final double determinant = determinant44(this.bufferMatrix);
        final double var = (product3 - determinant / (n - 2));
        return Math.sqrt(abs(var));
    }

    private double determinant44(final Matrix m) {
        final double a11 = m.get(0, 0);
        final double a12 = m.get(0, 1);
        final double a13 = m.get(0, 2);
        final double a14 = m.get(0, 3);

        final double a21 = m.get(1, 0);
        final double a22 = m.get(1, 1);
        final double a23 = m.get(1, 2);
        final double a24 = m.get(1, 3);

        final double a31 = m.get(2, 0);
        final double a32 = m.get(2, 1);
        final double a33 = m.get(2, 2);
        final double a34 = m.get(2, 3);

        final double a41 = m.get(3, 0);
        final double a42 = m.get(3, 1);
        final double a43 = m.get(3, 2);
        final double a44 = m.get(3, 3);

        return a14 * a23 * a32 * a41 - a13 * a24 * a32 * a41 - a14 * a22 * a33 * a41 +
                a12 * a24 * a33 * a41 + a13 * a22 * a34 * a41 - a12 * a23 * a34 * a41 -
                a14 * a23 * a31 * a42 + a13 * a24 * a31 * a42 + a14 * a21 * a33 * a42 -
                a11 * a24 * a33 * a42 - a13 * a21 * a34 * a42 + a11 * a23 * a34 * a42 +
                a14 * a22 * a31 * a43 - a12 * a24 * a31 * a43 - a14 * a21 * a32 * a43 +
                a11 * a24 * a32 * a43 + a12 * a21 * a34 * a43 - a11 * a22 * a34 * a43 -
                a13 * a22 * a31 * a44 + a12 * a23 * a31 * a44 + a13 * a21 * a32 * a44 -
                a11 * a23 * a32 * a44 - a12 * a21 * a33 * a44 + a11 * a22 * a33 * a44;
    }

    /**
     * The asymptotic distribution-free Bollen test. See Bollen (1990).
     */

    private void bollenEvalTetradDifferences(final int i, final int j, final int k, final int l) {
//        double TAUijkl, TAUijlk, TAUiklj;
//        double ratio;
//
//        TAUijkl = getCovMatrix().getValue(i, j) * getCovMatrix().getValue(k, l) -
//                getCovMatrix().getValue(i, k) * getCovMatrix().getValue(j, l);
//
//        double bt = bollenTetradStatistic(i, j, k, l);
//
//        ratio = TAUijkl / Math.sqrt(bt);
//
//        if (ratio > 0.0) {
//            ratio = -ratio;
//        }
//
//        prob[0] = 2.0 * ProbUtils.normalCdf(ratio);
//
//        TAUijlk = getCovMatrix().getValue(i, j) * getCovMatrix().getValue(k, l) -
//                getCovMatrix().getValue(i, l) * getCovMatrix().getValue(j, k);
//
//        bt = bollenTetradStatistic(i, j, l, k);
//
//        ratio = TAUijlk / Math.sqrt(bt);
//
//        if (ratio > 0.0) {
//            ratio = -ratio;
//        }
//
//        prob[1] = 2.0 * ProbUtils.normalCdf(ratio);
//
//        TAUiklj = getCovMatrix().getValue(i, k) * getCovMatrix().getValue(j, l) -
//                getCovMatrix().getValue(i, l) * getCovMatrix().getValue(j, k);
//
//        bt = bollenTetradStatistic(i, k, l, j);
//
//        ratio = TAUiklj / Math.sqrt(bt);
//
//        if (ratio > 0.0) {
//            ratio = -ratio;
//        }
//
//        prob[2] = 2.0 * ProbUtils.normalCdf(ratio);

        if (this.deltaTest == null) {
            if (this.dataSet != null) {
                this.deltaTest = new DeltaTetradTest(this.dataSet);
            } else {
                this.deltaTest = new DeltaTetradTest(this.covMatrix);
            }
        }

        final Node ci = getVariables().get(i);
        final Node cj = getVariables().get(j);
        final Node ck = getVariables().get(k);
        final Node cl = getVariables().get(l);

        this.deltaTest.calcChiSquare(new Tetrad(ci, cj, ck, cl));
        this.prob[0] = this.deltaTest.getPValue();

        this.deltaTest.calcChiSquare(new Tetrad(ci, cj, cl, ck));
        this.prob[1] = this.deltaTest.getPValue();

        this.deltaTest.calcChiSquare(new Tetrad(ci, ck, cl, cj));
        this.prob[2] = this.deltaTest.getPValue();
    }


    private void bollenEvalTetradDifference(final int i, final int j, final int k, final int l) {
//        double TAUijkl;
//        double ratio;
//
//        TAUijkl = getCovMatrix().getValue(i, j) * getCovMatrix().getValue(k, l) -
//                getCovMatrix().getValue(i, k) * getCovMatrix().getValue(j, l);
//
//        double bt = bollenTetradStatistic(i, j, k, l);
//
//        ratio = TAUijkl / Math.sqrt(bt);
//
//        if (ratio > 0.0) {
//            ratio = -ratio;
//        }
//
//        prob[0] = 2.0 * ProbUtils.normalCdf(ratio);

        final Node ci = getVariables().get(i);
        final Node cj = getVariables().get(j);
        final Node ck = getVariables().get(k);
        final Node cl = getVariables().get(l);

        if (this.deltaTest == null) {
            if (this.dataSet != null) {
                this.deltaTest = new DeltaTetradTest(this.dataSet);
            } else {
                this.deltaTest = new DeltaTetradTest(this.covMatrix);
            }
        }

        this.deltaTest.calcChiSquare(new Tetrad(ci, cj, ck, cl));
        this.prob[0] = this.deltaTest.getPValue();

        TetradLogger.getInstance().log("tetrads", new Tetrad(this.variables.get(i),
                this.variables.get(j), this.variables.get(k), this.variables.get(l))
                + " = 0, p = " + this.prob[0]);


    }

//    private double bollenTetradStatistic(int t1a, int t2, int t3, int t4) {
//        if (getCovMatrix() == null) {
//            throw new NullPointerException();
//        }
//
//        if (fourthMM == null) {
//            throw new NullPointerException();
//        }
//
//        double prod2323 = getCovMatrix().getValue(t2, t3) * getCovMatrix().getValue(t2, t3) *
//                fourthMM[t1a][t1a][t4][t4];
//        double prod1414 = getCovMatrix().getValue(t1a, t4) * getCovMatrix().getValue(t1a, t4) *
//                fourthMM[t2][t2][t3][t3];
//        double prod2424 = getCovMatrix().getValue(t2, t4) * getCovMatrix().getValue(t2, t4) *
//                fourthMM[t1a][t1a][t3][t3];
//        double prod1313 = getCovMatrix().getValue(t1a, t3) * getCovMatrix().getValue(t1a, t3) *
//                fourthMM[t2][t2][t4][t4];
//        double prod2314 = getCovMatrix().getValue(t2, t3) * getCovMatrix().getValue(t1a, t4) *
//                fourthMM[t1a][t2][t3][t4];
//        double prod2324 = getCovMatrix().getValue(t2, t3) * getCovMatrix().getValue(t2, t4) *
//                fourthMM[t1a][t1a][t3][t4];
//        double prod2313 = getCovMatrix().getValue(t2, t3) * getCovMatrix().getValue(t1a, t3) *
//                fourthMM[t1a][t2][t4][t4];
//        double prod1233 = getCovMatrix().getValue(t1a, t4) * getCovMatrix().getValue(t2, t4) *
//                fourthMM[t1a][t2][t3][t3];
//        double prod1413 = getCovMatrix().getValue(t1a, t4) * getCovMatrix().getValue(t1a, t3) *
//                fourthMM[t2][t2][t3][t4];
//        double prod2413 = getCovMatrix().getValue(t2, t4) * getCovMatrix().getValue(t1a, t3) *
//                fourthMM[t1a][t2][t3][t4];
//        double cov2314 = getCovMatrix().getValue(t2, t3) * getCovMatrix().getValue(t1a, t4);
//        double cov2413 = getCovMatrix().getValue(t2, t4) * getCovMatrix().getValue(t1a, t3);
//        double nStat = prod2323 + prod1414 + prod2424 + prod1313
//                + 2 * (prod2314 - prod2324 - prod2313 - prod1233 - prod1413 + prod2413)
//                - 4 * Math.pow(cov2314 - cov2413, 2.);
//        double stat = nStat / sampleSize;
//        if (stat < 0.) {
//            stat = 0.000001;
//        }
//        return stat;
//    }

    void printMessage(final String message) {
        if (this.outputMessage) {
            System.out.print(message);
        }
    }

    void printlnMessage(final String message) {
        if (this.outputMessage) {
            System.out.println(message);
        }
    }

    void printlnMessage() {
        if (this.outputMessage) {
            System.out.println();
        }
    }

    void printlnMessage(final boolean flag) {
        if (this.outputMessage) {
            System.out.println(flag);
        }
    }

    public void setCovMatrix(final ICovarianceMatrix covMatrix) {
        this.covMatrix = covMatrix;
    }

    public void setBollenTest(final DeltaTetradTest deltaTest) {
        this.deltaTest = deltaTest;
    }

    /*
     * This class is a easy, fast way of reusing one-factor models for
     * significance testing
     */

    abstract class SimpleFactorEstimator {
        ICovarianceMatrix sampleCov, subSampleCov;
        double sig;
        int[] indices;
        int nvar;
        SemPm semPm;
        String[] varNames, submatrixNames;

        /**
         * A maximum likelihood estimate of the parameters of a one factor model with four variables. Created to
         * simplify coding in BuildPureClusters.
         */
        public SimpleFactorEstimator(final ICovarianceMatrix sampleCov, final double sig,
                                     final int nvar) {
            this.sampleCov = sampleCov;
            this.sig = sig;
            this.nvar = nvar;
            this.varNames = sampleCov.getVariableNames().toArray(new String[0]);
            this.submatrixNames = new String[nvar];
        }

        public void refreshDataMatrix(final ICovarianceMatrix sampleCov) {
            this.sampleCov = sampleCov;
            this.varNames = sampleCov.getVariableNames().toArray(new String[0]);
        }

        public void init(final int[] indices) {
            Arrays.sort(indices);

            for (int i = 0; i < indices.length; i++) {
                this.submatrixNames[i] = this.varNames[indices[i]];
            }
            this.semPm = buildSemPm(indices);

            //For some implementation reason, semPm changes the order of the nodes:
            //it doesn't match the order in subMatrixNames anymore.
            //The following procedure is similar to fixVarOrder found in
            //other classes:
//            List<Node> semPmVars = semPm.getVariableNodes();
//            int index = 0;
//            for (Node ar : semPmVars) {
//                if (ar.getNodeType() != NodeType.LATENT) {
//                    submatrixNames[index++] = ar.toString();
//                }
//            }

            //Finally, get the correct submatrix
            this.subSampleCov = this.sampleCov.getSubmatrix(this.submatrixNames);
        }

        public boolean isSignificant() {
            throw new UnsupportedOperationException(); // Need to remove dependence on PAL.
//            MimBuildEstimator estimator =
//                    MimBuildEstimator.newInstance(subSampleCov, semPm, 3, 1);
//            estimator.estimate();
//            SemIm semIm = estimator.getEstimatedSem();
//            //System.out.println("Model p-value: " + semIm.getLikelihoodRatioP());
//            return semIm.getScore() > sig;
        }

        protected abstract SemPm buildSemPm(int[] indices);
    }

    class OneFactorEstimator extends SimpleFactorEstimator {
        static final long serialVersionUID = 23L;

        public OneFactorEstimator(final ICovarianceMatrix sampleCov, final double sig,
                                  final int nvar) {
            super(sampleCov, sig, nvar);
        }

        protected SemPm buildSemPm(final int[] values) {
            final Graph graph = new EdgeListGraph();
            final Node latent = new GraphNode("__l");
            latent.setNodeType(NodeType.LATENT);
            graph.addNode(latent);
            for (int i = 0; i < this.nvar; i++) {
                final Node node = new GraphNode(this.submatrixNames[i]);
                graph.addNode(node);
                graph.addDirectedEdge(latent, node);
            }
            this.semPm = new SemPm(graph);
            return this.semPm;
        }

    }

    class TwoFactorsEstimator extends SimpleFactorEstimator {
        static final long serialVersionUID = 23L;

        int nleft;

        public TwoFactorsEstimator(final ICovarianceMatrix sampleCov, final double sig,
                                   final int nvar) {
            super(sampleCov, sig, nvar);
        }

        public void init(final int[] indices, final int nleft) {
            this.nleft = nleft;
            this.init(indices);
        }

        protected SemPm buildSemPm(final int[] values) {
            final Graph graph = new EdgeListGraph();
            final Node latent1 = new GraphNode("__l1");
            final Node latent2 = new GraphNode("__l2");
            latent1.setNodeType(NodeType.LATENT);
            latent2.setNodeType(NodeType.LATENT);
            graph.addNode(latent1);
            graph.addNode(latent2);
            graph.addDirectedEdge(latent1, latent2);
            for (int i = 0; i < this.nvar; i++) {
                final Node node = new GraphNode(this.submatrixNames[i]);
                graph.addNode(node);
                if (i < this.nleft) {
                    graph.addDirectedEdge(latent1, node);
                } else {
                    graph.addDirectedEdge(latent2, node);
                }
            }
            this.semPm = new SemPm(graph);
            return this.semPm;
        }
    }

    public boolean oneFactorTest(final int v1, final int v2, final int v3, final int v4) {
        final int[] indices = {v1, v2, v3, v4};
        this.oneFactorEst4.init(indices);
        return this.oneFactorEst4.isSignificant();
    }

    public boolean oneFactorTest(final int v1, final int v2, final int v3, final int v4, final int v5) {
        final int[] indices = {v1, v2, v3, v4, v5};
        this.oneFactorEst5.init(indices);
        return this.oneFactorEst5.isSignificant();
    }

    public boolean oneFactorTest(final int v1, final int v2, final int v3, final int v4, final int v5,
                                 final int v6) {
        final int[] indices = {v1, v2, v3, v4, v5, v6};
        this.oneFactorEst6.init(indices);
        return this.oneFactorEst6.isSignificant();
    }

    public boolean twoFactorTest(final int v1, final int v2, final int v3, final int v4) {
        final int[] indices = {v1, v2, v3, v4};
        this.twoFactorsEst4.init(indices, 2);
        return this.twoFactorsEst4.isSignificant();
    }

    public boolean twoFactorTest(final int v1, final int v2, final int v3, final int v4, final int v5) {
        final int[] indices = {v1, v2, v3, v4, v5};
        this.twoFactorsEst5.init(indices, 3);
        return this.twoFactorsEst5.isSignificant();
    }

    public boolean twoFactorTest(final int v1, final int v2, final int v3, final int v4, final int v5,
                                 final int v6) {
        final int[] indices = {v1, v2, v3, v4, v5, v6};
        this.twoFactorsEst6.init(indices, 3);
        return this.twoFactorsEst6.isSignificant();
    }

    public int tempTetradScore(final int v1, final int v2, final int v3, final int v4) {
        evalTetradDifferences(v1, v2, v3, v4);
//        System.out.println(prob[0]);
//        System.out.println(prob[1]);
//        System.out.println(prob[2]);
        for (int i = 0; i < 3; i++) {
            this.bvalues[i] = (this.prob[i] >= this.sig);
        }
        //Order p-values for FDR (false discovery rate) decision
        double tempProb;
        if (this.prob[1] < this.prob[0] && this.prob[1] < this.prob[2]) {
            tempProb = this.prob[0];
            this.prob[0] = this.prob[1];
            this.prob[1] = tempProb;
        } else if (this.prob[2] < this.prob[0] && this.prob[2] < this.prob[0]) {
            tempProb = this.prob[0];
            this.prob[0] = this.prob[2];
            this.prob[2] = tempProb;
        }
        if (this.prob[2] < this.prob[1]) {
            tempProb = this.prob[1];
            this.prob[1] = this.prob[2];
            this.prob[2] = tempProb;
        }
        if (this.prob[2] <= this.sig3) {
            return 0;
        }
        if (this.prob[1] <= this.sig2) {
            return 1;
        }
        if (this.prob[0] <= this.sig1) {
            //This is the case of 2 tetrad constraints holding, which is
            //a logical impossibility. On a future version we may come up with
            //better, more powerful ways of deciding what to do. Right now,
            //the default is to do just as follows:
            return 3;
        }
        return 3;
    }

}






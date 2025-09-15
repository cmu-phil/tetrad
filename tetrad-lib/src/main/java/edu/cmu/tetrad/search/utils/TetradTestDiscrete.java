///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.util.FastMath;

import java.util.List;

//import edu.cmu.tetrad.sem.MimBuildEstimator;

/**
 * Implements a test of tetrad constraints with discrete variables. We are assuming that variables are ordinal or
 * binary. Such tests are a core statistical procedure in algorithm BuildPureClusters and Purify. An "underlying latent
 * variable" approach is used to test tetrads indirectly by fitting discrete one-factor and two-factor models. See
 * Bartholomew and Knott (1999) for details. A two-stage procedure for fitting polychorics correlations (Olsson, 1979)
 * and a chi-square test of tetrad constraints over those correlations is the key for this method. References:
 * Bartholomew, D. and Knott, M. (1999). Latent Variable Models and Factor Analysis, 2nd edition. Arnold. Olsson, Ulf
 * (1979). "Maximum likelihood estimation of the polychoric correlation coefficient". Psychometrika 44, 443-460. Stroud,
 * A. and Secrest D. (1966) Gaussian Quadrature Formulas. Prentice Hall.
 *
 * @author Ricardo Silva
 * @version $Id: $Id
 */
public final class TetradTestDiscrete implements TetradTest {

    /**
     * Max number of distinct values for a discrete variable.
     */
    private static final int MAX_VALUES = 50;

    /**
     * Grid size for optimizing the polychoric correlation.
     */
    private static final int RHO_GRID_SIZE = 1000;

    /**
     * Coefficients for Gaussian quadrature.
     */
    private static final double[] GHY = {5.55503518732646782452296868771,
            4.77399234341121942970150957712, 4.12199554749184002081690067728,
            3.53197287713767773917138228262, 2.97999120770459800253772781753,
            2.45355212451283800200073540616, 1.94496294918625384190191671547,
            1.44893425065073196265729314868, 0.961499634418369064279422271352,
            0.479450707079107576294598103513, 0.,
            -5.55503518732646782452296868771, -4.77399234341121942970150957712,
            -4.12199554749184002081690067728, -3.53197287713767773917138228262,
            -2.97999120770459800253772781753, -2.45355212451283800200073540616,
            -1.94496294918625384190191671547, -1.44893425065073196265729314868,
            -0.961499634418369064279422271352,
            -0.479450707079107576294598103513};
    /**
     * The dataset.
     */
    private final DataSet dataSet;
    /**
     * Whether to print out verbose information.
     */
    public boolean verbose;
    /**
     * the counts.
     */
    private int[][][][] counts; //bivariate coefs only

    /**
     * The values.
     */
    private int[][] values;

    /**
     * The value indices.
     */
    private int[] valueIndices;

    /**
     * The thresholds.
     */
    private double[][] thresholds;

    /**
     * The indices.
     */
    private int[] indices;

    /**
     * The current counts.
     */
    private int[][][][] currentCounts;

    /**
     * First variable in the current tetrad.
     */
    private int currentVar1;

    /**
     * Second variable in the current tetrad.
     */
    private int currentVar2;

    /**
     * The current fi buffer.
     */
    private double[][] currentFiBuffer;

    /**
     * The current pi.
     */
    private double[][] currentPi;

    /**
     * The current rho.
     */
    private double currentRho;

    /**
     * The rho grid.
     */
    private double[] rhoGrid;

    /**
     * The poly corr.
     */
    private double[][] polyCorr;

    /**
     * The one factor4 tests.
     */
    private int[][][][] oneFactor4Tests;

    /**
     * The two factor4 tests.
     */
    private int[][][][] twoFactor4Tests;

    /**
     * The probs.
     */
    private double[] prob;
    /**
     * The temp prob.
     */
    private double tempProb;

    /**
     * The sig1.
     */
    private double sig1;

    /**
     * The sig2.
     */
    private double sig2;

    /**
     * The sig3.
     */
    private double sig3;

    /**
     * The sig.
     */
    private double sig;

    /**
     * The bvalues.
     */
    private boolean[] bvalues;

    /**
     * <p>Constructor for TetradTestDiscrete.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @param sig     a double
     */
    public TetradTestDiscrete(DataSet dataSet, double sig) {
        this.dataSet = dataSet;
        this.sig = sig;
        initialization();
    }

    /**
     * <p>getVarNames.</p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    public String[] getVarNames() {
        return this.dataSet.getVariableNames().toArray(new String[0]);
    }

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        return this.dataSet.getVariables();
    }

    /**
     * <p>Getter for the field <code>dataSet</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }

    private void initialization() {
        for (int i = 0; i < TetradTestDiscrete.GHY.length; i++) {
            TetradTestDiscrete.GHY[i] *= FastMath.sqrt(2);
        }
        int numRows = this.dataSet.getNumRows();
        int numColumns = this.dataSet.getNumColumns();

        this.prob = new double[3];
        this.bvalues = new boolean[3];
        this.sig1 = this.sig / 3.;
        this.sig2 = 2. * this.sig / 3.;
        this.sig3 = this.sig;

        this.rhoGrid = new double[TetradTestDiscrete.RHO_GRID_SIZE];
        for (int i = 1; i < TetradTestDiscrete.RHO_GRID_SIZE; i++) {
            this.rhoGrid[i - 1] = -1. + (2. / TetradTestDiscrete.RHO_GRID_SIZE) * i;
        }

        // Store and order possible values
        this.values = new int[numColumns][];
        this.valueIndices = new int[numColumns];
        int[] tempValues = new int[TetradTestDiscrete.MAX_VALUES];
        boolean[] marked = new boolean[TetradTestDiscrete.MAX_VALUES];
        for (int i = 0; i < numColumns; i++) {
            int vSize = 0;
            rowloop:
            for (int j = 0; j < numRows; j++) {
                int value = this.dataSet.getInt(j, i);
                for (int k = 0; k < vSize; k++) {
                    if (tempValues[k] == value) {
                        continue rowloop;
                    }
                }
                if (vSize < TetradTestDiscrete.MAX_VALUES - 1) {
                    tempValues[vSize++] = value;
                } else {
                    throw new RuntimeException(
                            "Maximum number of distinct values for a discrete variable exceeded!");
                }
            }
            this.values[i] = new int[vSize];
            if (i == 0) {
                this.valueIndices[0] = 0;
            } else {
                this.valueIndices[i] = this.valueIndices[i - 1] + vSize - 1;
            }
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

        this.thresholds = new double[numColumns][];
        for (int i = 0; i < numColumns; i++) {
            this.thresholds[i] = new double[this.values[i].length - 1];
        }

        this.counts = new int[numColumns][numColumns][][];
//        computeCounts(this.coefs, this.rawdata);
        computeCounts(this.counts, this.dataSet);
        this.currentCounts = this.counts;
        this.polyCorr = getUnderlyingCorr(this.counts);
        this.oneFactor4Tests =
                new int[this.values.length][this.values.length][this.values.length][this.values.length];
        this.twoFactor4Tests =
                new int[this.values.length][this.values.length][this.values.length][this.values.length];
        //this.oneFactor5Tests = new int[this.values.length][this.values.length][this.values.length][this.values.length][this.values.length];
        resetCache();
    }

    /**
     * <p>resetCache.</p>
     */
    public void resetCache() {
        for (int v1 = 0; v1 < this.values.length; v1++) {
            for (int v2 = v1 + 1; v2 < this.values.length; v2++) {
                for (int v3 = v2 + 1; v3 < this.values.length; v3++) {
                    for (int v4 = v3 + 1; v4 < this.values.length; v4++) {
                        this.oneFactor4Tests[v1][v2][v3][v4] = 0;
                    }
                }
            }
        }
        for (int v1 = 0; v1 < this.values.length - 1; v1++) {
            for (int v2 = v1 + 1; v2 < this.values.length; v2++) {
                for (int v3 = 0; v3 < this.values.length - 1; v3++) {
                    for (int v4 = v3 + 1; v4 < this.values.length; v4++) {
                        this.twoFactor4Tests[v1][v2][v3][v4] = 0;
                    }
                }
            }
        }
    }

    /**
     * <p>getSignificance.</p>
     *
     * @return a double
     */
    public double getSignificance() {
        return this.sig;
    }

    /**
     * {@inheritDoc}
     */
    public void setSignificance(double sig) {
        this.sig = sig;
    }

    /**
     * {@inheritDoc}
     */
    public int tetradScore(int i, int j, int k, int l) {
        if (!oneFactorTest(i, j, k, l)) {
            twoFactorTest(i, l, j, k);
            this.prob[0] = this.tempProb;
            twoFactorTest(i, k, j, l);
            this.prob[1] = this.tempProb;
            twoFactorTest(i, j, k, l);
            this.prob[2] = this.tempProb;
            for (int c = 0; c < 3; c++) {
                this.bvalues[c] = (this.prob[c] >= this.sig);
            }
            //Order p-values for FDR (false discovery rate) decision
            if (this.prob[1] < this.prob[0] && this.prob[1] < this.prob[2]) {
                this.tempProb = this.prob[0];
                this.prob[0] = this.prob[1];
                this.prob[1] = this.tempProb;
            } else if (this.prob[2] < this.prob[0] && this.prob[2] < this.prob[0]) {
                this.tempProb = this.prob[0];
                this.prob[0] = this.prob[2];
                this.prob[2] = this.tempProb;
            }
            if (this.prob[2] < this.prob[1]) {
                this.tempProb = this.prob[1];
                this.prob[1] = this.prob[2];
                this.prob[2] = this.tempProb;
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
        }
        return 3;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Tests the tetrad (v1, v3) x (v2, v4) = (v1, v4) x (v2, v3), and only that.
     */
    public boolean tetradScore1(int v1, int v2, int v3, int v4) {
        if (oneFactorTest(v1, v2, v3, v4)) {
            return false;
        }
        return twoFactorTest(v1, v2, v3, v4);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Tests if all tetrad constraints hold
     */
    public boolean tetradScore3(int v1, int v2, int v3, int v4) {
        return oneFactorTest(v1, v2, v3, v4);
    }

    /**
     * {@inheritDoc}
     */
    public double tetradPValue(int v1, int v2, int v3, int v4) {
        twoFactorTest(v1, v2, v3, v4);
        return this.tempProb;
    }

    /**
     * {@inheritDoc}
     */
    public boolean tetradHolds(int i, int j, int k, int l) {
        twoFactorTest(i, l, j, k);
        this.prob[0] = this.tempProb;
        this.bvalues[0] = (this.prob[0] >= this.sig);
        return this.bvalues[0];
    }

    private void computeCounts(int[][][][] counts, DataSet data) {
        int numRows = this.dataSet.getNumRows();
        int numColumns = this.dataSet.getNumColumns();
        for (int i = 0; i < numColumns; i++) {
            for (int j = i; j < numColumns; j++) {
                counts[i][j] =
                        new int[this.values[i].length][this.values[j].length];
                counts[j][i] =
                        new int[this.values[j].length][this.values[i].length];
                for (int k = 0; k < this.values[i].length; k++) {
                    for (int q = 0; q < this.values[j].length; q++) {
                        counts[i][j][k][q] = counts[j][i][q][k] = 0;
                    }
                }
            }
        }

        for (int r = 0; r < numRows; r++) {
            for (int i = 0; i < numColumns; i++) {
                for (int j = i; j < numColumns; j++) {
                    counts[i][j][getValuePosition(data.getInt(r, i),
                            i)][getValuePosition(data.getInt(r, j), j)]++;
                }
            }
        }
        for (int i = 0; i < numColumns - 1; i++) {
            for (int j = i + 1; j < numColumns; j++) {
                for (int k = 0; k < this.values[i].length; k++) {
                    for (int q = 0; q < this.values[j].length; q++) {
                        counts[j][i][q][k] = counts[i][j][k][q];
                    }
                }
            }
        }
    }

    /**
     * @return the position of a specific value in a natural (0, 1, 2, ...) scale for a given variable.
     */

    private int getValuePosition(int value, int varNumber) {
        for (int i = 0; i < this.values[varNumber].length; i++) {
            if (this.values[varNumber][i] == value) {
                return i;
            }
        }
        assert false;
        return -1;
    }

    //*****************************************************************************************************
    // * ESTIMATION METHODS
    // */

    /**
     * See Olsson (1979) for details.
     */

    private double[][] getUnderlyingCorr(int[][][][] nextCounts) {
        double[][] outputCorr =
                new double[this.dataSet.getNumColumns()][this.dataSet.getNumColumns()];
        this.currentCounts = nextCounts;

        //Stage 1: estimation of thresholds
        for (int i = 0; i < this.dataSet.getNumColumns(); i++) {
            int c = 0;
            for (int j = 0; j < this.values[i].length - 1; j++) {
                c += this.currentCounts[i][i][j][j];
                this.thresholds[i][j] = ProbUtils.normalQuantile(
                        (double) c / this.dataSet.getNumRows());
            }
        }

        //Stage 2: estimation of polychoric correlations
        int[] indices = new int[2];
        for (int i = 0; i < this.dataSet.getNumColumns(); i++) {
            outputCorr[i][i] = 1.;
            for (int j = i + 1; j < this.dataSet.getNumColumns(); j++) {
                indices[0] = i;
                indices[1] = j;
                outputCorr[i][j] =
                        outputCorr[j][i] = estimatePolychoric(indices);
            }
        }
        for (int i = 0; i < outputCorr.length; i++) {
            for (int j = 0; j <= i; j++) {
                System.out.print((double) ((int) (100. * outputCorr[i][j])) /
                                 100. + "\t");
            }
            System.out.println();
        }

        return outputCorr;
    }

    /**
     * Estimate the polychoric correlation of two variables.
     */

    private double estimatePolychoric(int[] indices) {
        this.indices = indices;
        RandomUtil r = RandomUtil.getInstance();
        this.currentVar1 = indices[0];
        this.currentVar2 = indices[1];
        this.currentFiBuffer = new double[this.values[this.currentVar1].length + 1][
                this.values[this.currentVar2].length + 1];
        this.currentPi =
                new double[this.values[this.currentVar1].length][this.values[this.currentVar2].length];
        this.currentRho = r.nextDouble() / 2. + 0.2; //choose random correlation between 0.2 and 0.7
        this.currentRho = gridOptimizer();
        return this.currentRho;
    }

    /**
     * Cache some statistics for speeding up calculations.
     */

    private void computeFiBuffer() {
        for (int i = 0; i < this.values[this.currentVar1].length + 1; i++) {
            this.currentFiBuffer[i][0] = 0.;
            if (i == 0) {
                for (int j = 1;
                     j < this.values[this.currentVar2].length + 1; j++) {
                    this.currentFiBuffer[0][j] = 0.;
                }
            } else if (i < this.values[this.currentVar1].length) {
                for (int j = 1;
                     j < this.values[this.currentVar2].length + 1; j++) {
                    if (j < this.values[this.currentVar2].length) {
                        this.currentFiBuffer[i][j] = ProbUtils.biNormalCdf(
                                this.thresholds[this.currentVar1][i - 1],
                                this.thresholds[this.currentVar2][j - 1],
                                this.currentRho);
                    } else {
                        this.currentFiBuffer[i][j] = ProbUtils.normalCdf(
                                this.thresholds[this.currentVar1][i - 1]);
                    }
                }
            } else {
                for (int j = 1;
                     j < this.values[this.currentVar2].length + 1; j++) {
                    if (j < this.values[this.currentVar2].length) {
                        this.currentFiBuffer[i][j] = ProbUtils.normalCdf(
                                this.thresholds[this.currentVar2][j - 1]);
                    } else {
                        this.currentFiBuffer[i][j] = 1.;
                    }
                }
            }
        }
    }

    /**
     * Function to be minimized: -loglikelihood. It *is* assumed that computeFiBuffer and computeCurrentPi were called
     * before this method.
     */

    double currentScoreFunction() {
        double score = 0.;

        for (int i = 0; i < this.values[this.currentVar1].length; i++) {
            for (int j = 0; j < this.values[this.currentVar2].length; j++) {
                score -=
                        this.currentCounts[this.currentVar1][this.currentVar2][i][j] *
                        FastMath.log(this.currentPi[i][j]);
            }
        }
        return score;
    }

    /**
     * It *is* assumed that computeFiBuffer was called before this method.
     */

    private void computeCurrentPi() {
        for (int i = 0; i < this.values[this.currentVar1].length; i++) {
            for (int j = 0; j < this.values[this.currentVar2].length; j++) {
                this.currentPi[i][j] = this.currentFiBuffer[i + 1][j + 1] -
                                       this.currentFiBuffer[i][j + 1] -
                                       this.currentFiBuffer[i + 1][j] +
                                       this.currentFiBuffer[i][j];
            }
        }
    }

    /**
     * Optimizing the polychoric correlation by searching over a uniform grid in (-1, 1).
     */

    private double gridOptimizer() {
        double minValue = Double.MAX_VALUE;
        double bestRho = -1.;
        for (double v : this.rhoGrid) {
            this.currentRho = v;
            computeFiBuffer();
            computeCurrentPi();
            double score = currentScoreFunction();
            if (score < minValue) {
                minValue = score;
                bestRho = this.currentRho;
            }
        }
        return bestRho;
    }

    /**
     * {@inheritDoc}
     */
    public boolean oneFactorTest(int i, int j, int k, int l) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public boolean oneFactorTest(int i, int j, int k, int l, int x) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public boolean twoFactorTest(int i, int j, int k, int l) {
        throw new UnsupportedOperationException(); // Need to remove dependence on PAL.
    }

    /**
     * {@inheritDoc}
     */
    public boolean twoFactorTest(int i, int j, int k, int l, int x) {
        throw new UnsupportedOperationException(); // Need to remove dependence on PAL.

    }

    /**
     * {@inheritDoc}
     */
    public boolean twoFactorTest(int i, int j, int k, int l, int x, int y) {
        throw new UnsupportedOperationException(); // Need to remove dependence on PAL.
    }


    /**
     * <p>getCovMatrix.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public ICovarianceMatrix getCovMatrix() {
        return null;
    }
}







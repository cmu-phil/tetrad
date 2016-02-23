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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.Iterator;
import java.util.List;

//import edu.cmu.tetrad.sem.MimBuildEstimator;

/**
 * Implementation of a test of tetrad constraints with discrete variables. We are assuming that variables are ordinal or
 * binary. Such tests are a core statistical procedure in algorithms BuildPureClusters and Purify. </p> An "underlying
 * latent variable" approach is used to test tetrads indirectly by fitting discrete one-factor and two-factor models.
 * See Bartholomew and Knott (1999) for details. A two-stage procedure for fitting polychorics correlations (Olsson,
 * 1979) and a chi-square test of tetrad constraints over those correlations is the key for this method. </p>
 * References: </p> Bartholomew, D. and Knott, M. (1999). Latent Variable Models and Factor Analysis, 2nd edition.
 * Arnold. </p> Olsson, Ulf (1979). "Maximum likelihood estimation of the polychoric correlation coefficient".
 * Psychometrika 44, 443-460. </p> Stroud, A. and Secrest D. (1966) Gaussian Quadrature Formulas. Prentice Hall.
 *
 * @author Ricardo Silva
 */

public final class DiscreteTetradTest implements TetradTest {
    DataSet dataSet;
    //    int rawdata[][];
    int counts[][][][]; //bivariate coefs only
    int values[][], valueIndices[];
    private double prob[], tempProb, sig1, sig2, sig3, sig;
    private boolean bvalues[];
    double thresholds[][];
    int indices[];
    int currentCounts[][][][];
    int currentVar1, currentVar2;
    double currentFiBuffer[][];
    double currentPi[][];
    double currentRho;
    double rhoGrid[];
    double polyCorr[][];
    double btCovars[][][];

    private static final int MAX_VALUES = 50;
    private static final int RHO_GRID_SIZE = 1000;
    //private static final double FUNC_TOLERANCE = 1.0e-4;
    //private static final double PARAM_TOLERANCE = 1.0e-3;

    public boolean verbose = false;

    //Gaussian-Hermite points and weights (Stroud and Secrest, 1966)
    /*double GHY[] = {4.49999070730939155366438053053, 3.66995037340445253472922383312, 2.9671669279056032484,
                    2.325732486, 1.719992575, 1.136115585, 0.5650695832, 0.,
                  -0.5650695832, -1.136115585, -1.719992575, -2.325732486, -2.9671669279056032484, -3.66995037340445253472922383312, -4.49999070730939155366438053053};
    double GHW[] = {0.000000001522475804, 0.000001059115547, 0.0001000044412, 0.002778068842, 0.03078003387, 0.1584889157, 0.4120286974,
                  0.5641003087,
                  0.4120286974, 0.1584889157, 0.03078003387, 0.002778068842, 0.0001000044412, 0.000001059115547, 0.000000001522475804};*/
    //double GHY[] = {3.436159118, 2.532731674, 1.1756683649, 1.036610829, 0.3429013272, -1.036610829, -1.1756683649, -2.532731674, -3.436159118};
    //double GHW[] = {0.000007640432855, 0.001343645746, 0.03387439445, 0.2401386110, 0.6108626337, 0.2401386110, 0.03387439445, 0.001343645746, 0.000007640432855};
    //double GHY[] = {1.3190993201, 1.2266580584, 1.1468553289, 0.7235510187, 0., -0.7235510187, -1.1468553289, -1.2266580584, -1.3190993201};
    //double GHW[] = {0.0003960697726, 0.004943624275, 0.08847453739, 0.4326515590, 0.7202352156, 0.4326515590, 0.08847453739, 0.004943624275, 0.0003960697726};
    /*double GHY[] = {5.5550351873, 4.773992343, 4.12199547,
                    3.531972877, 2.979991207, 2.453552124,
                    1.944962949, 1.44893425, 0.9614996344, 0.479450707,
                    0.,
                    -5.5550351873, -4.773992343, -4.12199547,
                    -3.531972877, -2.979991207, -2.453552124,
                    -1.944962949, -1.44893425, -0.9614996344, -0.479450707};
    double GHW[] = {0.0000000000000372036507,
                    0.00000000008818611242, 0.0000000257123018, 0.000002171884898,
                    0.00007478398867, 0.001254982041, 0.01141406583, 0.06017964665, 0.192120324, 0.3816690736, 0.4790237031,
                    0.0000000000000372036507, 0.00000000008818611242, 0.0000000257123018, 0.000002171884898,
                    0.00007478398867, 0.001254982041, 0.01141406583, 0.06017964665, 0.192120324, 0.3816690736};*/

    private static double GHY[] = {5.55503518732646782452296868771,
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
    private static double GHW[] = {
            0.0000000000000372036507013604926215857501257,
            0.0000000000881861124204995159415949532010,
            0.0000000257123018005931370477558762345,
            0.00000217188489805666958287349836869,
            0.0000747839886731006116909785995138,
            0.00125498204172641054585210235726,
            0.0114140658374343833765845047287,
            0.0601796466589122671716641792812, 0.192120324066997756129082460739,
            0.381669073613502098270416641564, 0.479023703120177648419744153424,
            0.0000000000000372036507013604926215857501257,
            0.0000000000881861124204995159415949532010,
            0.0000000257123018005931370477558762345,
            0.00000217188489805666958287349836869,
            0.0000747839886731006116909785995138,
            0.00125498204172641054585210235726,
            0.0114140658374343833765845047287,
            0.0601796466589122671716641792812, 0.192120324066997756129082460739,
            0.381669073613502098270416641564, 0.479023703120177648419744153424};

    /**
     * @serial
     */
    int oneFactor4Tests[][][][], oneFactor5Tests[][][][][];

    /**
     * @serial
     */
    int twoFactor4Tests[][][][];

    boolean highPrecisionIntegral = false;

    public DiscreteTetradTest(DataSet dataSet, double sig) {
        this.dataSet = dataSet;
        this.sig = sig;
        initialization();
    }

    public String[] getVarNames() {
        return this.dataSet.getVariableNames().toArray(new String[0]);
    }

    public List<Node> getVariables() {
        return dataSet.getVariables();
    }

    public DataSet getDataSet() {
        return this.dataSet;
    }

    private void initialization() {
        for (int i = 0; i < GHY.length; i++) {
            GHY[i] *= Math.sqrt(2);
            GHW[i] /= Math.sqrt(Math.PI);
        }
        int numRows = this.dataSet.getNumRows();
        int numColumns = this.dataSet.getNumColumns();

        this.prob = new double[3];
        this.bvalues = new boolean[3];
        this.sig1 = this.sig / 3.;
        this.sig2 = 2. * this.sig / 3.;
        this.sig3 = this.sig;

        this.rhoGrid = new double[RHO_GRID_SIZE];
        for (int i = 1; i < RHO_GRID_SIZE; i++) {
            this.rhoGrid[i - 1] = -1. + (2. / RHO_GRID_SIZE) * i;
        }

//        this.rawdata = this.dataSet.getIntMatrixTransposed();

        // Store and order possible values
        this.values = new int[numColumns][];
        this.valueIndices = new int[numColumns];
        int tempValues[] = new int[MAX_VALUES];
        boolean marked[] = new boolean[MAX_VALUES];
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
                if (vSize < MAX_VALUES - 1) {
                    tempValues[vSize++] = value;
                } else {
                    throw new RuntimeException(
                            "Maximum number of distinct values for a discrete variable exceeded!");
                }
            }
            this.values[i] = new int[vSize];
            if (i == 0) {
                this.valueIndices[i] = 0;
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

    public void resetCache() {
        for (int v1 = 0; v1 < this.values.length; v1++) {
            for (int v2 = v1 + 1; v2 < this.values.length; v2++) {
                for (int v3 = v2 + 1; v3 < this.values.length; v3++) {
                    for (int v4 = v3 + 1; v4 < this.values.length; v4++) {
                        this.oneFactor4Tests[v1][v2][v3][v4] = 0;
                        //for (int v5 = v4 + 1; v5 < this.values.length; v5++)
                        //    this.oneFactor5Tests[v1][v2][v3][v4][v5] = 0;
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

    public double getSignificance() {
        return this.sig;
    }

    public void setSignificance(double sig) {
        this.sig = sig;
    }

    public void setHighPrecision(boolean p) {
        this.highPrecisionIntegral = p;
    }

    public boolean getHighPrecision() {
        return this.highPrecisionIntegral;
    }

    public int tetradScore(int i, int j, int k, int l) {
        if (oneFactorTest(i, j, k, l)) {
            return 3;
        } else {
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
            return 3;
        }
    }

    /**
     * Tests the tetrad (v1, v3) x (v2, v4) = (v1, v4) x (v2, v3), and only that.
     */

    public boolean tetradScore1(int v1, int v2, int v3, int v4) {
        if (oneFactorTest(v1, v2, v3, v4)) {
            return false;
        }
        return twoFactorTest(v1, v2, v3, v4);
    }

    /**
     * Tests if all tetrad constraints hold
     */

    public boolean tetradScore3(int v1, int v2, int v3, int v4) {
        return oneFactorTest(v1, v2, v3, v4);
    }

    public double tetradPValue(int v1, int v2, int v3, int v4) {
        twoFactorTest(v1, v2, v3, v4);
        return this.tempProb;
    }

    public double tetradPValue(int i1, int j1, int k1, int l1, int i2, int j2, int k2, int l2) {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean tetradHolds(int i, int j, int k, int l) {
        twoFactorTest(i, l, j, k);
        this.prob[0] = this.tempProb;
        this.bvalues[0] = (this.prob[0] >= this.sig);
        return this.bvalues[0];
    }

//    private void computeCounts(int coefs[][][][], int data[][]) {
//        int numRows = this.dataSet.getNumRows();
//        int numColumns = this.dataSet.getNumColumns();
//        for (int i = 0; i < numColumns; i++) {
//            for (int j = i; j < numColumns; j++) {
//                coefs[i][j] =
//                        new int[this.values[i].length][this.values[j].length];
//                coefs[j][i] =
//                        new int[this.values[j].length][this.values[i].length];
//                for (int k = 0; k < this.values[i].length; k++) {
//                    for (int q = 0; q < this.values[j].length; q++) {
//                        coefs[i][j][k][q] = coefs[j][i][q][k] = 0;
//                    }
//                }
//            }
//        }
//
//        for (int r = 0; r < numRows; r++) {
//            for (int i = 0; i < numColumns; i++) {
//                for (int j = i; j < numColumns; j++) {
//                    coefs[i][j][getValuePosition(data[i][r], i)][getValuePosition(
//                            data[j][r], j)]++;
//                }
//            }
//        }
//        for (int i = 0; i < numColumns - 1; i++) {
//            for (int j = i + 1; j < numColumns; j++) {
//                for (int k = 0; k < this.values[i].length; k++) {
//                    for (int q = 0; q < this.values[j].length; q++) {
//                        coefs[j][i][q][k] = coefs[i][j][k][q];
//                    }
//                }
//            }
//        }
//    }

    private void computeCounts(int counts[][][][], DataSet data) {
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
//                    coefs[i][j][getValuePosition(data[i][r], i)][getValuePosition(
//                            data[j][r], j)]++;
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

    private int[][][][] computeCounts4(int i, int j, int k, int l) {
        int newCounts[][][][] =
                new int[this.values[i].length][this.values[j].length][this.values[k].length][this.values[l].length];

        int numRows = this.dataSet.getNumRows();
        for (int v1 = 0; v1 < this.values[this.indices[0]].length; v1++) {
            for (int v2 = 0; v2 < this.values[this.indices[1]].length; v2++) {
                for (int v3 = 0; v3 < this.values[this.indices[2]].length; v3++) {
                    for (int v4 = 0;
                         v4 < this.values[this.indices[3]].length; v4++) {
                        newCounts[v1][v2][v3][v4] = 0;
                    }
                }
            }
        }

        for (int r = 0; r < numRows; r++) {
//            newCounts[getValuePosition(this.rawdata[i][r], i)][getValuePosition(
//                    this.rawdata[j][r], j)][getValuePosition(
//                            this.rawdata[k][r], k)][getValuePosition(
//                                    this.rawdata[l][r], l)]++;
            newCounts[getValuePosition(this.dataSet.getInt(r, i),
                    i)][getValuePosition(this.dataSet.getInt(r, j),
                    j)][getValuePosition(this.dataSet.getInt(r, k),
                    k)][getValuePosition(this.dataSet.getInt(r, l), l)]++;
        }

        return newCounts;
    }

    private int[][][][][] computeCounts5(int i, int j, int k, int l, int x) {
        int numRows = this.dataSet.getNumRows();
        int newCounts[][][][][] =
                new int[this.values[i].length][this.values[j].length][this.values[k].length][this.values[l].length][this.values[x].length];

        for (int v1 = 0; v1 < this.values[indices[0]].length; v1++) {
            for (int v2 = 0; v2 < this.values[indices[1]].length; v2++) {
                for (int v3 = 0; v3 < this.values[indices[2]].length; v3++) {
                    for (int v4 = 0; v4 < this.values[indices[3]].length; v4++) {
                        for (int v5 = 0;
                             v5 < this.values[indices[4]].length; v5++) {
                            newCounts[v1][v2][v3][v4][v5] = 0;
                        }
                    }
                }
            }
        }

        for (int r = 0; r < numRows; r++) {
//            newCounts[getValuePosition(this.rawdata[i][r], i)][getValuePosition(
//                    this.rawdata[j][r], j)][getValuePosition(
//                            this.rawdata[k][r], k)][getValuePosition(
//                                    this.rawdata[l][r], l)][getValuePosition(
//                                            this.rawdata[x][r], x)]++;
            newCounts[getValuePosition(this.dataSet.getInt(r, i),
                    i)][getValuePosition(this.dataSet.getInt(r, j),
                    j)][getValuePosition(this.dataSet.getInt(r, k),
                    k)][getValuePosition(this.dataSet.getInt(r, l),
                    l)][getValuePosition(this.dataSet.getInt(r, x), x)]++;
        }

        return newCounts;
    }

    private int[][][][][][] computeCounts6(int i, int j, int k, int l, int x,
                                           int y) {
        int numRows = this.dataSet.getNumRows();
        int newCounts[][][][][][] =
                new int[this.values[i].length][this.values[j].length][this.values[k].length][this.values[l].length][this.values[x].length][this.values[y].length];

        for (int v1 = 0; v1 < this.values[indices[0]].length; v1++) {
            for (int v2 = 0; v2 < this.values[indices[1]].length; v2++) {
                for (int v3 = 0; v3 < this.values[indices[2]].length; v3++) {
                    for (int v4 = 0; v4 < this.values[indices[3]].length; v4++) {
                        for (int v5 = 0;
                             v5 < this.values[indices[4]].length; v5++) {
                            for (int v6 = 0;
                                 v6 < this.values[indices[5]].length; v6++) {
                                newCounts[v1][v2][v3][v4][v5][v6] = 0;
                            }
                        }
                    }
                }
            }
        }

        for (int r = 0; r < numRows; r++) {
//            newCounts[getValuePosition(this.rawdata[i][r], i)][getValuePosition(
//                    this.rawdata[j][r], j)][getValuePosition(
//                            this.rawdata[k][r], k)][getValuePosition(
//                                    this.rawdata[l][r], l)][getValuePosition(
//                                            this.rawdata[x][r], x)][getValuePosition(
//                                                    this.rawdata[y][r], y)]++;
            newCounts[getValuePosition(this.dataSet.getInt(r, i),
                    i)][getValuePosition(this.dataSet.getInt(r, j),
                    j)][getValuePosition(this.dataSet.getInt(r, k),
                    k)][getValuePosition(this.dataSet.getInt(r, l),
                    l)][getValuePosition(this.dataSet.getInt(r, x),
                    x)][getValuePosition(this.dataSet.getInt(r, y), y)]++;
        }

        return newCounts;
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

    private double[][] getUnderlyingCorr(int nextCounts[][][][]) {
        double outputCorr[][] =
                new double[dataSet.getNumColumns()][dataSet.getNumColumns()];
        this.currentCounts = nextCounts;

        //Stage 1: estimation of thresholds
        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            int c = 0;
            for (int j = 0; j < this.values[i].length - 1; j++) {
                c += this.currentCounts[i][i][j][j];
                this.thresholds[i][j] = ProbUtils.normalQuantile(
                        (double) c / this.dataSet.getNumRows());
            }
        }

        //Stage 2: estimation of polychoric correlations
        int indices[] = new int[2];
        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            outputCorr[i][i] = 1.;
            for (int j = i + 1; j < dataSet.getNumColumns(); j++) {
                indices[0] = i;
                indices[1] = j;
                outputCorr[i][j] =
                        outputCorr[j][i] = estimatePolychoric(indices);
            }
        }
        String dummyNames[] = new String[outputCorr.length];
        for (int i = 0; i < outputCorr.length; i++) {
            dummyNames[i] = "L" + i;
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

    private double estimatePolychoric(int indices[]) {
        this.indices = indices;
        double start[] = new double[1];
        RandomUtil r = RandomUtil.getInstance();
        this.currentVar1 = indices[0];
        this.currentVar2 = indices[1];
        this.currentFiBuffer = new double[this.values[currentVar1].length + 1][
                this.values[currentVar2].length + 1];
        this.currentPi =
                new double[this.values[currentVar1].length][this.values[currentVar2].length];
        this.currentRho = start[0] = r.nextDouble() / 2. +
                0.2; //choose random correlation between 0.2 and 0.7
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
                    this.currentFiBuffer[i][j] = 0.;
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

    protected double currentScoreFunction() {
        double score = 0.;

        for (int i = 0; i < this.values[this.currentVar1].length; i++) {
            for (int j = 0; j < this.values[this.currentVar2].length; j++) {
                score -=
                        this.currentCounts[this.currentVar1][this.currentVar2][i][j] *
                                Math.log(this.currentPi[i][j]);
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
        for (int i = 0; i < this.rhoGrid.length; i++) {
            this.currentRho = this.rhoGrid[i];
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

    private int oneFactorCached(int indices[]) {
        int ordered[] = new int[indices.length];
        System.arraycopy(indices, 0, ordered, 0, indices.length);
        for (int i = 0; i < indices.length - 1; i++) {
            int min = ordered[i], minIndex = i;
            for (int j = i + 1; j < this.indices.length; j++) {
                if (ordered[j] < min) {
                    min = ordered[j];
                    minIndex = j;
                }
            }
            int temp = ordered[i];
            ordered[i] = min;
            ordered[minIndex] = temp;
        }
        if (indices.length == 4) {
            return this.oneFactor4Tests[ordered[0]][ordered[1]][ordered[2]][ordered[3]];
        } else
        //return  this.oneFactor5Tests[ordered[0]][ordered[1]][ordered[2]][ordered[3]][ordered[4]];
        {
            return 0;
        }
    }

    private int twoFactorCached(int indices[]) {
        int ordered[] = new int[indices.length];
        System.arraycopy(indices, 0, ordered, 0, indices.length);
        if (ordered[1] < ordered[0]) {
            int temp = ordered[1];
            ordered[1] = ordered[0];
            ordered[0] = temp;
        }
        if (ordered[3] < ordered[2]) {
            int temp = ordered[3];
            ordered[3] = ordered[2];
            ordered[2] = temp;
        }
        return this.twoFactor4Tests[ordered[0]][ordered[1]][ordered[2]][ordered[3]];
    }

    private void cacheOneFactorTest(int indices[], boolean result) {
        if (indices.length > 4) {
            return;
        }
        int ordered[] = new int[indices.length];
        System.arraycopy(indices, 0, ordered, 0, indices.length);
        for (int i = 0; i < indices.length - 1; i++) {
            int min = ordered[i], minIndex = i;
            for (int j = i + 1; j < this.indices.length; j++) {
                if (ordered[j] < min) {
                    min = ordered[j];
                    minIndex = j;
                }
            }
            int temp = ordered[i];
            ordered[i] = min;
            ordered[minIndex] = temp;
        }
        int intResult;
        if (result) {
            intResult = 1;
        } else {
            intResult = -1;
        }
        //if (indices.length == 4)
        this.oneFactor4Tests[ordered[0]][ordered[1]][ordered[2]][ordered[3]] =
                intResult;
        //else
        //    this.oneFactor5Tests[ordered[0]][ordered[1]][ordered[2]][ordered[3]][ordered[4]] = intResult;
    }

    private void cacheTwoFactorTest(int indices[], boolean result) {
        int ordered[] = new int[indices.length];
        System.arraycopy(indices, 0, ordered, 0, indices.length);
        if (ordered[1] < ordered[0]) {
            int temp = ordered[1];
            ordered[1] = ordered[0];
            ordered[0] = temp;
        }
        if (ordered[3] < ordered[2]) {
            int temp = ordered[3];
            ordered[3] = ordered[2];
            ordered[2] = temp;
        }
        int intResult;
        if (result) {
            intResult = 1;
        } else {
            intResult = -1;
        }
        this.twoFactor4Tests[ordered[0]][ordered[1]][ordered[2]][ordered[3]] =
                intResult;
    }

    public boolean oneFactorTest(int i, int j, int k, int l) {
        throw new UnsupportedOperationException(); // Need to remove dependence on PAL.
//        //System.out.println("oneFactorTest: " + i + " " + j + " " + k + " " + l);
//
//        this.indices = new int[4];
//        this.indices[0] = i;
//        this.indices[1] = j;
//        this.indices[2] = k;
//        this.indices[3] = l;
//
//        int cachedResult = oneFactorCached(this.indices);
//        if (cachedResult != 0) {
//            //System.out.println("Cached! = " + (cachedResult > 0));
//            return cachedResult > 0;
//        }
//        double ulCorr[][] = new double[4][4];
//        String vNames[] = new String[4];
//        for (int v1 = 0; v1 < 4; v1++) {
//            ulCorr[v1][v1] = 1.;
//            for (int v2 = v1 + 1; v2 < 4; v2++) {
//                ulCorr[v1][v2] = ulCorr[v2][v1] =
//                        this.polyCorr[this.indices[v1]][this.indices[v2]];
//            }
//            vNames[v1] = "xi" + v1;
//        }
//
//        if (MatrixUtils.determinant(ulCorr) <= 0) {
//            //System.out.println("* * WARNING: correlation matrix for variables " +
//            //        i + ", " + j + ", " + k + ", " + l + " is not positive definite.");
//            return false;
//        }
//
//        ICovarianceMatrix covarianceMatrix =
//                new CovarianceMatrix(DataUtils.createContinuousVariables(vNames), new TetradMatrix(ulCorr),
//                        this.dataSet.getNumRows());
//        Graph factorModel = new EdgeListGraph();
//        Node eta = new GraphNode("eta");
//        eta.setNodeType(NodeType.LATENT);
//        factorModel.addNode(eta);
//        for (int n = 0; n < 4; n++) {
//            Node xi = new GraphNode("xi" + n);
//            factorModel.addNode(xi);
//            factorModel.addDirectedEdge(eta, xi);
//        }
//        SemPm semPm = new SemPm(factorModel);
//        MimBuildEstimator est =
//                new MimBuildEstimator(covarianceMatrix, semPm, 5, 5);
//        //System.out.println("1.");
//        est.estimate();
//        if (est.getEstimatedSem().getScore() > this.sig) {
//            cacheOneFactorTest(this.indices, true);
//            return true;
//        }
//        if (est.getEstimatedSem().getScore() < 1.e-10) {
//            cacheOneFactorTest(this.indices, false);
//            return false;
//        }
//        //System.out.println("2.");
//        ICovarianceMatrix covMatrix = new CovarianceMatrix(DataUtils.createContinuousVariables(vNames), new TetradMatrix(est.getEstimatedSem().getImplCovarMeas().toArray()),
//                this.dataSet.getNumRows());
//        double m[][] = new CorrelationMatrix(covMatrix).getMatrix().toArray();
//        int counts4[][][][] = computeCounts4(i, j, k, l);
//        //System.out.println("2.5");
//        //double chisq = 0.;
//        int indices2[] = new int[4];
//        /*for (int v1 = 0; v1 < this.values[this.indices[0]].length; v1++) {
//            for (int v2 = 0; v2 < this.values[this.indices[1]].length; v2++) {
//                for (int v3 = 0; v3 < this.values[this.indices[2]].length; v3++) {
//                    for (int v4 = 0; v4 < this.values[this.indices[3]].length; v4++) {
//                        if (counts4[v1][v2][v3][v4] != 0) {
//                            indices2[0] = v1;
//                            indices2[1] = v2;
//                            indices2[2] = v3;
//                            indices2[3] = v4;
//                            double ph = piHat0(this.indices, indices2, m);
//                            System.out.println("P = " + (double) counts4[v1][v2][v3][v4] / dataSet.getMaxRowCount() +
//                                               ", P_hat = " + ph);
//                            //chisq += 2. * counts4[v1][v2][v3][v4] *
//                            //         Math.log((double) counts4[v1][v2][v3][v4] /
//                            //                 (dataSet.getMaxRowCount() * ph));
//                            chisq += (counts4[v1][v2][v3][v4] -
//                                      this.dataSet.getMaxRowCount() * ph) *
//                                         (counts4[v1][v2][v3][v4] - this.dataSet.getMaxRowCount() * ph) /
//                                      (this.dataSet.getMaxRowCount() * ph);
//                        }
//                    }
//                }
//            }
//        }*/
//        //System.out.println("2.6");
//        double chisq2 = 0.;
//        for (int v1 = 0; v1 < this.values[this.indices[0]].length; v1++) {
//            for (int v2 = 0; v2 < this.values[this.indices[1]].length; v2++) {
//                for (int v3 = 0; v3 < this.values[this.indices[2]].length; v3++) {
//                    for (int v4 = 0;
//                         v4 < this.values[this.indices[3]].length; v4++) {
//                        if (counts4[v1][v2][v3][v4] != 0) {
//                            indices2[0] = v1;
//                            indices2[1] = v2;
//                            indices2[2] = v3;
//                            indices2[3] = v4;
//                            double ph;
//                            if (this.highPrecisionIntegral) {
//                                ph = piHat0(this.indices, indices2, m);
//                            } else {
//                                ph = piHat1(this.indices, indices2,
//                                        est.getEstimatedSem());
//                            }
//                            //double ph = piHat0(this.indices, indices2, m);
//                            //System.out.println("P = " + (double) counts4[v1][v2][v3][v4] / dataSet.getMaxRowCount() +
//                            //                   ", P_hat = " + ph + " " + v1 + "" + v2 + "" + v3 + "" + v4);
//                            //System.exit(0);
//                            //chisq += 2. * counts4[v1][v2][v3][v4] *
//                            //         Math.log((double) counts4[v1][v2][v3][v4] /
//                            //                 (dataSet.getMaxRowCount() * ph));
//                            chisq2 += (counts4[v1][v2][v3][v4] -
//                                    this.dataSet.getNumRows() * ph) * (
//                                    counts4[v1][v2][v3][v4] -
//                                            this.dataSet.getNumRows() * ph) /
//                                    (this.dataSet.getNumRows() * ph);
//                        }
//                    }
//                }
//            }
//        }
//
//        int df = this.values[this.indices[0]].length *
//                this.values[this.indices[1]].length *
//                this.values[this.indices[2]].length *
//                this.values[this.indices[3]].length - 1 - (
//                this.values[this.indices[0]].length +
//                        this.values[this.indices[1]].length +
//                        this.values[this.indices[2]].length +
//                        this.values[this.indices[3]].length);
//
//        //System.out.println("3.");
//        //System.out.println("(" + i + " " + j + " " + k + " " + l + " chisq = " + (chisq) + ", prob = " + (1. - ProbUtils.chisqCdf(chisq, df)) + ")");
//        if (verbose) {
//            System.out.println("(" + i + " " + j + " " + k + " " + l +
//                    " chisq2 = " + (chisq2) + ", prob = " +
//                    (1. - ProbUtils.chisqCdf(chisq2, df)) + ")");
//        }
//
//        /*if (Math.abs(ProbUtils.chisqCdf(chisq2, df) - ProbUtils.chisqCdf(chisq, df)) > 0.1) {
//            System.out.println("!!!");
//            System.out.println(est.getEstimatedSem());
//            System.out.println("Thresholds: ");
//            for (int c = 0; c < 4; c++)
//                System.out.println(" - " + this.thresholds[this.indices[c]][0]);
//        }*/
//        //System.exit(0);
//        //System.out.println("Ugh!");
//        //return (1. - ProbUtils.chisqCdf(chisq, df)) > this.sig;
//        cacheOneFactorTest(this.indices,
//                (1. - ProbUtils.chisqCdf(chisq2, df)) > this.sig);
//
//        return (1. - ProbUtils.chisqCdf(chisq2, df)) > this.sig;
//        /*System.out.println("(" + i + " " + j + " " + k + " " + l + " chisq = " +
//                  est.getEstimatedSem().getChiSquare() + ", prob = " + est.getEstimatedSem().getLikelihoodRatioP() + ")");
//        System.out.println(1. - ProbUtils.chisqCdf(est.getEstimatedSem().getChiSquare(), est.getEstimatedSem().getDof() + 1));
//        return est.getEstimatedSem().getLikelihoodRatioP() > this.sig;*/
    }

    public boolean oneFactorTest(int i, int j, int k, int l, int x) {
        throw new UnsupportedOperationException(); // Need to remove dependence on PAL.
//        //System.out.println("oneFactorTest: " + i + " " + j + " " + k + " " + l + " " + x);
//
//        indices = new int[5];
//        indices[0] = i;
//        indices[1] = j;
//        indices[2] = k;
//        indices[3] = l;
//        indices[4] = x;
//
//        int cachedResult = oneFactorCached(this.indices);
//        if (cachedResult != 0) {
//            //System.out.println("Cached! = " + (cachedResult > 0));
//            return cachedResult > 0;
//        }
//
//        double ulCorr[][] = new double[5][5];
//        String vNames[] = new String[5];
//        for (int v1 = 0; v1 < 5; v1++) {
//            ulCorr[v1][v1] = 1.;
//            for (int v2 = v1 + 1; v2 < 5; v2++) {
//                ulCorr[v1][v2] = ulCorr[v2][v1] =
//                        this.polyCorr[indices[v1]][indices[v2]];
//            }
//            vNames[v1] = "xi" + v1;
//        }
//
//        if (MatrixUtils.determinant(ulCorr) <= 0) {
//            //System.out.println("* * WARNING: correlation matrix for variables " +
//            //        i + ", " + j + ", " + k + ", " + l + " is not positive definite.");
//            return false;
//        }
//
//        ICovarianceMatrix CovarianceMatrix =
//                new CovarianceMatrix(DataUtils.createContinuousVariables(vNames), new TetradMatrix(ulCorr),
//                        dataSet.getNumRows());
//        Graph factorModel = new EdgeListGraph();
//        Node eta = new GraphNode("eta");
//        eta.setNodeType(NodeType.LATENT);
//        factorModel.addNode(eta);
//        for (int n = 0; n < 5; n++) {
//            Node xi = new GraphNode("xi" + n);
//            factorModel.addNode(xi);
//            factorModel.addDirectedEdge(eta, xi);
//        }
//        SemPm semPm = new SemPm(factorModel);
//        MimBuildEstimator est =
//                new MimBuildEstimator(CovarianceMatrix, semPm, 5, 5);
//        est.estimate();
//        if (est.getEstimatedSem().getScore() > this.sig) {
//            cacheOneFactorTest(this.indices, true);
//            return true;
//        }
//        if (est.getEstimatedSem().getScore() < 1.e-10) {
//            cacheOneFactorTest(this.indices, false);
//            return false;
//        }
//
//        ICovarianceMatrix covMatrix = new CovarianceMatrix(DataUtils.createContinuousVariables(vNames), new TetradMatrix(est.getEstimatedSem().getImplCovarMeas().toArray()),
//                dataSet.getNumRows());
//        double m[][] = new CorrelationMatrix(covMatrix).getMatrix().toArray();
//        int counts5[][][][][] = computeCounts5(i, j, k, l, x);
//        double chisq = 0.;
//        int indices2[] = new int[5];
//        for (int v1 = 0; v1 < this.values[indices[0]].length; v1++) {
//            for (int v2 = 0; v2 < this.values[indices[1]].length; v2++) {
//                for (int v3 = 0; v3 < this.values[indices[2]].length; v3++) {
//                    for (int v4 = 0; v4 < this.values[indices[3]].length; v4++) {
//                        for (int v5 = 0;
//                             v5 < this.values[indices[4]].length; v5++) {
//                            if (counts5[v1][v2][v3][v4][v5] != 0) {
//                                indices2[0] = v1;
//                                indices2[1] = v2;
//                                indices2[2] = v3;
//                                indices2[3] = v4;
//                                indices2[4] = v5;
//                                //double ph = piHat0(indices, indices2, m);
//                                double ph;
//                                if (this.highPrecisionIntegral) {
//                                    ph = piHat0(this.indices, indices2, m);
//                                } else {
//                                    ph = piHat1(this.indices, indices2,
//                                            est.getEstimatedSem());
//                                }
//                                //System.out.println("P = " + (double) counts5[v1][v2][v3][v4] / dataSet.getMaxRowCount() +
//                                //                   ", P_hat = " + ph);
//                                //chisq += 2. * counts4[v1][v2][v3][v4] *
//                                //         Math.log((double) counts4[v1][v2][v3][v4] /
//                                //                 (dataSet.getMaxRowCount() * ph));
//                                chisq += (counts5[v1][v2][v3][v4][v5] -
//                                        dataSet.getNumRows() * ph) * (
//                                        counts5[v1][v2][v3][v4][v5] -
//                                                dataSet.getNumRows() * ph) /
//                                        (dataSet.getNumRows() * ph);
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        int df = this.values[indices[0]].length * this.values[indices[1]]
//                .length * this.values[indices[2]].length *
//                this.values[indices[3]].length * this.values[indices[4]]
//                .length - 1 - (this.values[indices[0]].length +
//                this.values[indices[1]].length + this.values[indices[2]]
//                .length + this.values[indices[3]].length +
//                this.values[indices[4]].length);
//
//        if (verbose) {
//            System.out.println("(" + i + " " + j + " " + k + " " + l + " " + x +
//                    " chisq = " + (chisq) + ", prob = " +
//                    (1. - ProbUtils.chisqCdf(chisq, df)) + ")");
//        }
//        //System.out.println("Ugh!");
//        cacheOneFactorTest(this.indices,
//                (1. - ProbUtils.chisqCdf(chisq, df)) > this.sig);
//        return (1. - ProbUtils.chisqCdf(chisq, df)) > this.sig;
    }

    public boolean oneFactorTest(int i, int j, int k, int l, int x, int y) {
        throw new UnsupportedOperationException(); // Need to remove dependence on PAL.
//        //System.out.println("oneFactorTest: " + i + " " + j + " " + k + " " + l + " " + x + " " + y);
//
//        indices = new int[6];
//        indices[0] = i;
//        indices[1] = j;
//        indices[2] = k;
//        indices[3] = l;
//        indices[4] = x;
//        indices[5] = y;
//
//        double ulCorr[][] = new double[6][6];
//        String vNames[] = new String[6];
//        for (int v1 = 0; v1 < 6; v1++) {
//            ulCorr[v1][v1] = 1.;
//            for (int v2 = v1 + 1; v2 < 6; v2++) {
//                ulCorr[v1][v2] = ulCorr[v2][v1] =
//                        this.polyCorr[indices[v1]][indices[v2]];
//            }
//            vNames[v1] = "xi" + v1;
//        }
//
//        if (MatrixUtils.determinant(ulCorr) <= 0) {
//            //System.out.println("* * WARNING: correlation matrix for variables " +
//            //        i + ", " + j + ", " + k + ", " + l + " is not positive definite.");
//            return false;
//        }
//
//        ICovarianceMatrix CovarianceMatrix =
//                new CovarianceMatrix(DataUtils.createContinuousVariables(vNames), new TetradMatrix(ulCorr),
//                        dataSet.getNumRows());
//        Graph factorModel = new EdgeListGraph();
//        Node eta = new GraphNode("eta");
//        eta.setNodeType(NodeType.LATENT);
//        factorModel.addNode(eta);
//        for (int n = 0; n < 6; n++) {
//            Node xi = new GraphNode("xi" + n);
//            factorModel.addNode(xi);
//            factorModel.addDirectedEdge(eta, xi);
//        }
//        SemPm semPm = new SemPm(factorModel);
//        MimBuildEstimator est =
//                new MimBuildEstimator(CovarianceMatrix, semPm, 5, 5);
//        est.estimate();
//        if (est.getEstimatedSem().getScore() > this.sig) {
//            return true;
//        }
//        if (est.getEstimatedSem().getScore() < 1.e-10) {
//            return false;
//        }
//
//        CorrelationMatrix correlationMatrix = new CorrelationMatrix(
//                new CovarianceMatrix(DataUtils.createContinuousVariables(vNames), new TetradMatrix(est.getEstimatedSem().getImplCovarMeas().toArray()),
//                        dataSet.getNumRows()));
//        double m[][] = correlationMatrix.getMatrix().toArray();
//        int counts6[][][][][][] = computeCounts6(i, j, k, l, x, y);
//        double chisq = 0.;
//        int indices2[] = new int[6];
//        for (int v1 = 0; v1 < this.values[indices[0]].length; v1++) {
//            for (int v2 = 0; v2 < this.values[indices[1]].length; v2++) {
//                for (int v3 = 0; v3 < this.values[indices[2]].length; v3++) {
//                    for (int v4 = 0; v4 < this.values[indices[3]].length; v4++) {
//                        for (int v5 = 0;
//                             v5 < this.values[indices[4]].length; v5++) {
//                            for (int v6 = 0;
//                                 v6 < this.values[indices[5]].length; v6++) {
//                                if (counts6[v1][v2][v3][v4][v5][v6] != 0) {
//                                    indices2[0] = v1;
//                                    indices2[1] = v2;
//                                    indices2[2] = v3;
//                                    indices2[3] = v4;
//                                    indices2[4] = v5;
//                                    indices2[5] = v6;
//                                    //double ph = piHat0(this.indices, indices2, m);
//                                    double ph;
//                                    if (this.highPrecisionIntegral) {
//                                        ph = piHat0(this.indices, indices2, m);
//                                    } else {
//                                        ph = piHat1(this.indices, indices2,
//                                                est.getEstimatedSem());
//                                    }
//                                    //System.out.println("P = " + (double) counts5[v1][v2][v3][v4] / dataSet.getMaxRowCount() +
//                                    //                   ", P_hat = " + ph);
//                                    //chisq += 2. * counts4[v1][v2][v3][v4] *
//                                    //         Math.log((double) counts4[v1][v2][v3][v4] /
//                                    //                 (dataSet.getMaxRowCount() * ph));
//                                    chisq += (counts6[v1][v2][v3][v4][v5][v6] -
//                                            dataSet.getNumRows() * ph) * (
//                                            counts6[v1][v2][v3][v4][v5][v6] -
//                                                    dataSet.getNumRows() * ph) /
//                                            (dataSet.getNumRows() * ph);
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        int df = this.values[indices[0]].length * this.values[indices[1]]
//                .length * this.values[indices[2]].length *
//                this.values[indices[3]].length * this.values[indices[4]]
//                .length * this.values[indices[5]].length - 1 - (
//                this.values[indices[0]].length + this.values[indices[1]]
//                        .length + this.values[indices[2]].length +
//                        this.values[indices[3]].length + this.values[indices[4]]
//                        .length + this.values[indices[5]].length);
//
//        if (verbose) {
//            System.out.println("(" + i + " " + j + " " + k + " " + l + " " + x +
//                    " chisq = " + (chisq) + ", prob = " +
//                    (1. - ProbUtils.chisqCdf(chisq, df)) + ")");
//        }
//        //System.out.println("Ugh!");
//        return 1. - ProbUtils.chisqCdf(chisq, df) > sig;
    }

    public boolean twoFactorTest(int i, int j, int k, int l) {
        throw new UnsupportedOperationException(); // Need to remove dependence on PAL.
//        //System.out.println("twoFactorTest: " + i + " " + j + " " + k + " " + l);
//
//        indices = new int[4];
//        indices[0] = i;
//        indices[1] = j;
//        indices[2] = k;
//        indices[3] = l;
//
//        int cachedResult = twoFactorCached(this.indices);
//        if (cachedResult != 0) {
//            //System.out.println("Cached! = " + (cachedResult > 0));
//            return cachedResult > 0;
//        }
//
//        double ulCorr[][] = new double[4][4];
//        String vNames[] = new String[4];
//        for (int v1 = 0; v1 < 4; v1++) {
//            ulCorr[v1][v1] = 1.;
//            for (int v2 = v1 + 1; v2 < 4; v2++) {
//                ulCorr[v1][v2] = ulCorr[v2][v1] =
//                        this.polyCorr[indices[v1]][indices[v2]];
//            }
//            vNames[v1] = "xi" + v1;
//        }
//
//        if (MatrixUtils.determinant(ulCorr) <= 0) {
//            //System.out.println("* * WARNING: correlation matrix for variables " +
//            //        i + ", " + j + ", " + k + ", " + l + " is not positive definite.");
//            this.tempProb = 0.;
//            return false;
//        }
//
//        ICovarianceMatrix CovarianceMatrix =
//                new CovarianceMatrix(DataUtils.createContinuousVariables(vNames), new TetradMatrix(ulCorr),
//                        dataSet.getNumRows());
//        Graph factorModel = new EdgeListGraph();
//        Node eta1 = new GraphNode("eta1");
//        eta1.setNodeType(NodeType.LATENT);
//        factorModel.addNode(eta1);
//        Node eta2 = new GraphNode("eta2");
//        eta2.setNodeType(NodeType.LATENT);
//        factorModel.addNode(eta2);
//        for (int n = 0; n < 4; n++) {
//            Node xi = new GraphNode("xi" + n);
//            factorModel.addNode(xi);
//            if (n < 2) {
//                factorModel.addDirectedEdge(eta1, xi);
//            } else {
//                factorModel.addDirectedEdge(eta2, xi);
//            }
//        }
//        factorModel.addDirectedEdge(eta1, eta2);
//        SemPm semPm = new SemPm(factorModel);
//        MimBuildEstimator est =
//                new MimBuildEstimator(CovarianceMatrix, semPm, 5, 5);
//        //System.out.println("1.");
//        est.estimate();
//        tempProb = est.getEstimatedSem().getScore();
//        if (est.getEstimatedSem().getScore() > this.sig) {
//            cacheTwoFactorTest(this.indices, true);
//            return true;
//        }
//        if (est.getEstimatedSem().getScore() < 1.e-10) {
//            cacheTwoFactorTest(this.indices, false);
//            return false;
//        }
//        //System.out.println("2.");
//        ICovarianceMatrix covMatrix = new CovarianceMatrix(DataUtils.createContinuousVariables(vNames), new TetradMatrix(est.getEstimatedSem().getImplCovarMeas().toArray()),
//                dataSet.getNumRows());
//        double m[][] = new CorrelationMatrix(covMatrix).getMatrix().toArray();
//        int counts4[][][][] = computeCounts4(i, j, k, l);
//        //System.out.println("2.5");
//        //double chisq = 0.;
//        int indices2[] = new int[4];
//        /*for (int v1 = 0; v1 < this.values[indices[0]].length; v1++) {
//            for (int v2 = 0; v2 < this.values[indices[1]].length; v2++) {
//                for (int v3 = 0; v3 < this.values[indices[2]].length; v3++) {
//                    for (int v4 = 0; v4 < this.values[indices[3]].length; v4++) {
//                        if (counts4[v1][v2][v3][v4] != 0) {
//                            indices2[0] = v1;
//                            indices2[1] = v2;
//                            indices2[2] = v3;
//                            indices2[3] = v4;
//                            double ph = piHat1(indices, indices2, m);
//                            //System.out.println("P = " + (double) counts4[v1][v2][v3][v4] / dataSet.getMaxRowCount() +
//                            //                   ", P_hat = " + ph);
//                            //chisq += 2. * counts4[v1][v2][v3][v4] *
//                            //         Math.log((double) counts4[v1][v2][v3][v4] /
//                            //                 (dataSet.getMaxRowCount() * ph));
//                            chisq += (counts4[v1][v2][v3][v4] - dataSet.getMaxRowCount() * ph) * (counts4[v1][v2][v3][v4] - dataSet.getMaxRowCount() * ph) / (dataSet.getMaxRowCount() * ph);
//                        }
//                    }
//                }
//            }
//        }*/
//
//        //System.out.println("2.6");
//        double chisq2 = 0.;
//        for (int v1 = 0; v1 < this.values[indices[0]].length; v1++) {
//            for (int v2 = 0; v2 < this.values[indices[1]].length; v2++) {
//                for (int v3 = 0; v3 < this.values[indices[2]].length; v3++) {
//                    for (int v4 = 0; v4 < this.values[indices[3]].length; v4++) {
//                        if (counts4[v1][v2][v3][v4] != 0) {
//                            indices2[0] = v1;
//                            indices2[1] = v2;
//                            indices2[2] = v3;
//                            indices2[3] = v4;
//                            //double ph = piHat1(indices, indices2, m);
//                            double ph;
//                            if (this.highPrecisionIntegral) {
//                                ph = piHat0(this.indices, indices2, m);
//                            } else {
//                                ph = piHat2(this.indices, indices2,
//                                        est.getEstimatedSem());
//                            }
//                            //System.out.println("P = " + (double) counts4[v1][v2][v3][v4] / dataSet.getMaxRowCount() +
//                            //                   ", P_hat = " + ph);
//                            //chisq += 2. * counts4[v1][v2][v3][v4] *
//                            //         Math.log((double) counts4[v1][v2][v3][v4] /
//                            //                 (dataSet.getMaxRowCount() * ph));
//                            chisq2 += (counts4[v1][v2][v3][v4] -
//                                    dataSet.getNumRows() * ph) * (
//                                    counts4[v1][v2][v3][v4] -
//                                            dataSet.getNumRows() * ph) /
//                                    (dataSet.getNumRows() * ph);
//                        }
//                    }
//                }
//            }
//        }
//
//        int df = this.values[indices[0]].length * this.values[indices[1]]
//                .length * this.values[indices[2]].length *
//                this.values[indices[3]].length - 1 - (this.values[indices[0]]
//                .length + this.values[indices[1]].length +
//                this.values[indices[2]].length + this.values[indices[3]]
//                .length);
//
//        //System.out.println("3.");
//        //System.out.println("(" + i + " " + j + " " + k + " " + l + " chisq = " + (chisq) + ", prob = " + (1. - ProbUtils.chisqCdf(chisq, df)) + ")");
//        if (verbose) {
//            System.out.println("(" + i + " " + j + " " + k + " " + l +
//                    " chisq2 = " + (chisq2) + ", prob = " +
//                    (1. - ProbUtils.chisqCdf(chisq2, df)) + ")");
//        }
//        //System.exit(0);
//        cacheTwoFactorTest(this.indices,
//                (1. - ProbUtils.chisqCdf(chisq2, df)) > this.sig);
//        tempProb = 1. - ProbUtils.chisqCdf(chisq2, df);
//        //System.out.println("Ugh!");
//        //System.out.println("END twoFactorTest: " + i + " " + j + " " + k + " " + l);
//        return tempProb > sig;
    }

    public boolean twoFactorTest(int i, int j, int k, int l, int x) {
        throw new UnsupportedOperationException(); // Need to remove dependence on PAL.

//        //System.out.println("twoFactorTest: " + i + " " + j + " " + k + " " + l + " " + x);
//
//        indices = new int[5];
//        indices[0] = i;
//        indices[1] = j;
//        indices[2] = k;
//        indices[3] = l;
//        indices[4] = x;
//
//        double ulCorr[][] = new double[5][5];
//        String vNames[] = new String[5];
//        for (int v1 = 0; v1 < 5; v1++) {
//            ulCorr[v1][v1] = 1.;
//            for (int v2 = v1 + 1; v2 < 5; v2++) {
//                ulCorr[v1][v2] = ulCorr[v2][v1] =
//                        this.polyCorr[indices[v1]][indices[v2]];
//            }
//            vNames[v1] = "xi" + v1;
//        }
//
//        if (MatrixUtils.determinant(ulCorr) <= 0) {
//            //System.out.println("* * WARNING: correlation matrix for variables " +
//            //        i + ", " + j + ", " + k + ", " + l + " is not positive definite.");
//            return false;
//        }
//
//        ICovarianceMatrix CovarianceMatrix =
//                new CovarianceMatrix(DataUtils.createContinuousVariables(vNames), new TetradMatrix(ulCorr),
//                        dataSet.getNumRows());
//        Graph factorModel = new EdgeListGraph();
//        Node eta1 = new GraphNode("eta1");
//        eta1.setNodeType(NodeType.LATENT);
//        factorModel.addNode(eta1);
//        Node eta2 = new GraphNode("eta2");
//        eta2.setNodeType(NodeType.LATENT);
//        factorModel.addNode(eta2);
//        for (int n = 0; n < 5; n++) {
//            Node xi = new GraphNode("xi" + n);
//            factorModel.addNode(xi);
//            if (n <= 2) {
//                factorModel.addDirectedEdge(eta1, xi);
//            } else {
//                factorModel.addDirectedEdge(eta2, xi);
//            }
//        }
//        factorModel.addDirectedEdge(eta1, eta2);
//        SemPm semPm = new SemPm(factorModel);
//        MimBuildEstimator est =
//                new MimBuildEstimator(CovarianceMatrix, semPm, 5, 5);
//        est.estimate();
//        if (est.getEstimatedSem().getScore() > this.sig) {
//            return true;
//        }
//        if (est.getEstimatedSem().getScore() < 1.e-10) {
//            return false;
//        }
//
//        CorrelationMatrix correlationMatrix = new CorrelationMatrix(
//                new CovarianceMatrix(DataUtils.createContinuousVariables(vNames), new TetradMatrix(est.getEstimatedSem().getImplCovarMeas().toArray()),
//                        dataSet.getNumRows()));
//        double m[][] = correlationMatrix.getMatrix().toArray();
//        int counts5[][][][][] = computeCounts5(i, j, k, l, x);
//        double chisq = 0.;
//        int indices2[] = new int[5];
//        for (int v1 = 0; v1 < this.values[indices[0]].length; v1++) {
//            for (int v2 = 0; v2 < this.values[indices[1]].length; v2++) {
//                for (int v3 = 0; v3 < this.values[indices[2]].length; v3++) {
//                    for (int v4 = 0; v4 < this.values[indices[3]].length; v4++) {
//                        for (int v5 = 0;
//                             v5 < this.values[indices[4]].length; v5++) {
//                            if (counts5[v1][v2][v3][v4][v5] != 0) {
//                                indices2[0] = v1;
//                                indices2[1] = v2;
//                                indices2[2] = v3;
//                                indices2[3] = v4;
//                                indices2[4] = v5;
//                                //double ph = piHat1(indices, indices2, m);
//                                double ph;
//                                if (this.highPrecisionIntegral) {
//                                    ph = piHat0(this.indices, indices2, m);
//                                } else {
//                                    ph = piHat2(this.indices, indices2,
//                                            est.getEstimatedSem());
//                                }
//                                //double ph = piHat2(indices, indices2, est.getEstimatedSem());
//                                //System.out.println("P = " + (double) counts5[v1][v2][v3][v4] / dataSet.getMaxRowCount() +
//                                //                   ", P_hat = " + ph);
//                                //chisq += 2. * counts4[v1][v2][v3][v4] *
//                                //         Math.log((double) counts4[v1][v2][v3][v4] /
//                                //                 (dataSet.getMaxRowCount() * ph));
//                                chisq += (counts5[v1][v2][v3][v4][v5] -
//                                        dataSet.getNumRows() * ph) * (
//                                        counts5[v1][v2][v3][v4][v5] -
//                                                dataSet.getNumRows() * ph) /
//                                        (dataSet.getNumRows() * ph);
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        int df = this.values[indices[0]].length * this.values[indices[1]]
//                .length * this.values[indices[2]].length *
//                this.values[indices[3]].length * this.values[indices[4]]
//                .length - 1 - (this.values[indices[0]].length +
//                this.values[indices[1]].length + this.values[indices[2]]
//                .length + this.values[indices[3]].length +
//                this.values[indices[4]].length + 1);
//
//        if (verbose) {
//            System.out.println("(" + i + " " + j + " " + k + " " + l + " " + x +
//                    " chisq = " + (chisq) + ", prob = " +
//                    (1. - ProbUtils.chisqCdf(chisq, df)) + ")");
//        }
//        //System.out.println("Ugh!");
//        //System.out.println("END twoFactorTest: " + i + " " + j + " " + k + " " + l + " " + x);
//        return (1. - ProbUtils.chisqCdf(chisq, df)) > sig;
    }

    public boolean twoFactorTest(int i, int j, int k, int l, int x, int y) {
        throw new UnsupportedOperationException(); // Need to remove dependence on PAL.
//        //System.out.println("twoFactorTest: " + i + " " + j + " " + k + " " + l + " " + x + " " + y);
//
//        indices = new int[6];
//        indices[0] = i;
//        indices[1] = j;
//        indices[2] = k;
//        indices[3] = l;
//        indices[4] = x;
//        indices[5] = y;
//
//        double ulCorr[][] = new double[6][6];
//        String vNames[] = new String[6];
//        for (int v1 = 0; v1 < 6; v1++) {
//            ulCorr[v1][v1] = 1.;
//            for (int v2 = v1 + 1; v2 < 6; v2++) {
//                ulCorr[v1][v2] = ulCorr[v2][v1] =
//                        this.polyCorr[indices[v1]][indices[v2]];
//            }
//            vNames[v1] = "xi" + v1;
//        }
//
//        if (MatrixUtils.determinant(ulCorr) <= 0) {
//            //System.out.println("* * WARNING: correlation matrix for variables " +
//            //        i + ", " + j + ", " + k + ", " + l + " is not positive definite.");
//            return false;
//        }
//
//        ICovarianceMatrix CovarianceMatrix =
//                new CovarianceMatrix(DataUtils.createContinuousVariables(vNames), new TetradMatrix(ulCorr),
//                        dataSet.getNumRows());
//        Graph factorModel = new EdgeListGraph();
//        Node eta1 = new GraphNode("eta1");
//        eta1.setNodeType(NodeType.LATENT);
//        factorModel.addNode(eta1);
//        Node eta2 = new GraphNode("eta2");
//        eta2.setNodeType(NodeType.LATENT);
//        factorModel.addNode(eta2);
//        for (int n = 0; n < 6; n++) {
//            Node xi = new GraphNode("xi" + n);
//            factorModel.addNode(xi);
//            if (n <= 2) {
//                factorModel.addDirectedEdge(eta1, xi);
//            } else {
//                factorModel.addDirectedEdge(eta2, xi);
//            }
//        }
//        factorModel.addDirectedEdge(eta1, eta2);
//        SemPm semPm = new SemPm(factorModel);
//        MimBuildEstimator est =
//                new MimBuildEstimator(CovarianceMatrix, semPm, 5, 5);
//        est.estimate();
//        if (est.getEstimatedSem().getScore() > this.sig) {
//            return true;
//        }
//        if (est.getEstimatedSem().getScore() < 1.e-10) {
//            return false;
//        }
//
//        CorrelationMatrix correlationMatrix = new CorrelationMatrix(
//                new CovarianceMatrix(DataUtils.createContinuousVariables(vNames), new TetradMatrix(est.getEstimatedSem().getImplCovarMeas().toArray()),
//                        dataSet.getNumRows()));
//        double m[][] = correlationMatrix.getMatrix().toArray();
//        int counts6[][][][][][] = computeCounts6(i, j, k, l, x, y);
//        double chisq = 0.;
//        int indices2[] = new int[6];
//        for (int v1 = 0; v1 < this.values[indices[0]].length; v1++) {
//            for (int v2 = 0; v2 < this.values[indices[1]].length; v2++) {
//                for (int v3 = 0; v3 < this.values[indices[2]].length; v3++) {
//                    for (int v4 = 0; v4 < this.values[indices[3]].length; v4++) {
//                        for (int v5 = 0;
//                             v5 < this.values[indices[4]].length; v5++) {
//                            for (int v6 = 0;
//                                 v6 < this.values[indices[5]].length; v6++) {
//                                if (counts6[v1][v2][v3][v4][v5][v6] != 0) {
//                                    indices2[0] = v1;
//                                    indices2[1] = v2;
//                                    indices2[2] = v3;
//                                    indices2[3] = v4;
//                                    indices2[4] = v5;
//                                    indices2[5] = v6;
//                                    //double ph = piHat1(indices, indices2, m);
//                                    double ph;
//                                    if (this.highPrecisionIntegral) {
//                                        ph = piHat0(this.indices, indices2, m);
//                                    } else {
//                                        ph = piHat2(this.indices, indices2,
//                                                est.getEstimatedSem());
//                                    }
//                                    //double ph = piHat2(indices, indices2, est.getEstimatedSem());
//                                    //System.out.println("P = " + (double) counts5[v1][v2][v3][v4] / dataSet.getMaxRowCount() +
//                                    //                   ", P_hat = " + ph);
//                                    //chisq += 2. * counts4[v1][v2][v3][v4] *
//                                    //         Math.log((double) counts4[v1][v2][v3][v4] /
//                                    //                 (dataSet.getMaxRowCount() * ph));
//                                    chisq += (counts6[v1][v2][v3][v4][v5][v6] -
//                                            dataSet.getNumRows() * ph) * (
//                                            counts6[v1][v2][v3][v4][v5][v6] -
//                                                    dataSet.getNumRows() * ph) /
//                                            (dataSet.getNumRows() * ph);
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        int df = this.values[indices[0]].length * this.values[indices[1]]
//                .length * this.values[indices[2]].length *
//                this.values[indices[3]].length * this.values[indices[4]]
//                .length * this.values[indices[5]].length - 1 - (
//                this.values[indices[0]].length + this.values[indices[1]]
//                        .length + this.values[indices[2]].length +
//                        this.values[indices[3]].length + this.values[indices[4]]
//                        .length + this.values[indices[5]].length + 1);
//
//        if (verbose) {
//            System.out.println("(" + i + " " + j + " " + k + " " + l + " " + x +
//                    " chisq = " + (chisq) + ", prob = " +
//                    (1. - ProbUtils.chisqCdf(chisq, df)) + ")");
//        }
//        //System.out.println("Ugh!");
//        //System.out.println("END twoFactorTest: " + i + " " + j + " " + k + " " + l + " " + x + " " + y);
//        return 1. - ProbUtils.chisqCdf(chisq, df) > sig;
    }

    private double piHat0(int indices[], int v[], double m[][]) {
        double a[] = new double[indices.length];
        double b[] = new double[indices.length];
        for (int i = 0; i < indices.length; i++) {
            if (v[i] == 0) {
                a[i] = Double.NEGATIVE_INFINITY;
            } else {
                a[i] = this.thresholds[indices[i]][v[i] - 1];
            }
            if (v[i] == this.values[indices[i]].length - 1) {
                b[i] = Double.POSITIVE_INFINITY;
            } else {
                b[i] = this.thresholds[indices[i]][v[i]];
            }
        }
        //create copy of m, since the next method modifies it
        double mScratch[][] = new double[m.length][m.length];
        for (int i = 0; i < m.length; i++) {
            for (int j = 0; j < m.length; j++) {
                mScratch[i][j] = m[i][j];
            }
        }
        return ProbUtils.multinormalProb(a, b, mScratch);
    }

    /**
     * For one-factor models.
     */
    private double piHat1(int indices[], int v[], SemIm semIm) {
        double stdE[] = new double[indices.length];
        double stdU[] = new double[indices.length];
        double coeff[] = new double[indices.length];
        Node etaNode = semIm.getSemPm().getGraph().getNode("eta");
        double varEta = semIm.getParamValue(etaNode, etaNode);
        double stdEta = Math.sqrt(semIm.getParamValue(etaNode, etaNode));
        for (int i = 0; i < indices.length; i++) {
            Node uNode = semIm.getSemPm().getGraph().getNode("xi" + i);
            Node uParent = null, uError = null;
            for (Iterator<Node> it =
                 semIm.getSemPm().getGraph().getParents(uNode)
                         .iterator(); it.hasNext(); ) {
                Node parent = it.next();
                if (parent.getNodeType() == NodeType.LATENT) {
                    uParent = parent;
                } else {
                    uError = parent;
                }
            }
            if (i == 0) {
                coeff[i] = 1.;
            } else {
                coeff[i] = semIm.getParamValue(uParent, uNode);
            }
            stdE[i] = Math.sqrt(semIm.getParamValue(uError, uError));
            stdU[i] = Math.sqrt(coeff[i] * coeff[i] * varEta +
                    semIm.getParamValue(uError, uError));
        }

        double l = 0.;
        for (int t = 0; t < GHY.length; t++) {
            double tValue = GHW[t];
            for (int i = 0; i < indices.length; i++) {
                int numValues = this.values[indices[i]].length;
                if (v[i] == 0) {
                    tValue *= ProbUtils.normalCdf((
                            this.thresholds[indices[i]][0] * stdU[i] -
                                    coeff[i] * GHY[t] * stdEta) / stdE[i]);
                } else if (v[i] == numValues - 1) {
                    tValue *= (1. - ProbUtils.normalCdf((
                            this.thresholds[indices[i]][numValues - 2] *
                                    stdU[i] - coeff[i] * GHY[t] * stdEta) /
                            stdE[i]));
                } else {
                    tValue *= ProbUtils.normalCdf((
                            this.thresholds[indices[i]][v[i]] * stdU[i] -
                                    coeff[i] * GHY[t] * stdEta) / stdE[i]) -
                            ProbUtils.normalCdf((this.thresholds[indices[i]][
                                    v[i] - 1] * stdU[i] -
                                    coeff[i] * GHY[t] * stdEta) / stdE[i]);
                }
            }
            l += tValue;
        }

        return l;
    }

    /**
     * For two factor models.
     */
    private double piHat2(int indices[], int v[], SemIm semIm) {
        SemGraph graph = semIm.getSemPm().getGraph();
        graph.setShowErrorTerms(true);

        Node etaNode1 = graph.getNode("eta1");
        double varEta1 = semIm.getParamValue(etaNode1, etaNode1);
        double stdEta1 = Math.sqrt(semIm.getParamValue(etaNode1, etaNode1));
        Node etaNode2 = graph.getNode("eta2");
        double coeffEta = semIm.getParamValue(etaNode1, etaNode2);
        Node errorEta2;
        if (graph.getParents(etaNode2).get(0) == etaNode1) {
            errorEta2 = graph.getParents(etaNode2)
                    .get(1);
        } else {
            errorEta2 = graph.getParents(etaNode2)
                    .get(0);
        }
        double varEta2 = coeffEta * coeffEta * varEta1 +
                semIm.getParamValue(errorEta2, errorEta2);
        double stdEtaError2 =
                Math.sqrt(semIm.getParamValue(errorEta2, errorEta2));

        double stdE[] = new double[indices.length];
        double varEta[] = new double[indices.length];
        double stdU[] = new double[indices.length];
        double coeff[] = new double[indices.length];

        for (int i = 0; i < indices.length; i++) {
            Node uNode = graph.getNode("xi" + i);
            Node uParent = null, uError = null;
            for (Node node : graph.getParents(uNode)) {
                Node parent = node;
                if (parent.getNodeType() == NodeType.LATENT) {
                    uParent = parent;
                } else {
                    uError = parent;
                }
            }
            if (i == 0 || (i == 2 && indices.length == 4) ||
                    (i == 3 && indices.length > 4)) {
                coeff[i] = 1.;
            } else {
                coeff[i] = semIm.getParamValue(uParent, uNode);
            }
            if (uParent == etaNode1) {
                varEta[i] = varEta1;
            } else {
                varEta[i] = varEta2;
            }
            stdE[i] = Math.sqrt(semIm.getParamValue(uError, uError));
            stdU[i] = Math.sqrt(coeff[i] * coeff[i] * varEta[i] +
                    semIm.getParamValue(uError, uError));
        }

        double l = 0.;
        for (int t1 = 0; t1 < GHY.length; t1++) {
            for (int t2 = 0; t2 < GHY.length; t2++) {
                double tValue = GHW[t1] * GHW[t2];
                double eta1 = GHY[t1] * stdEta1;
                double eta2 = eta1 * coeffEta + GHY[t2] * stdEtaError2;
                for (int i = 0; i < indices.length; i++) {
                    double eta;
                    if (indices.length == 4) {
                        if (i < 2) {
                            eta = eta1;
                        } else {
                            eta = eta2;
                        }
                    } else {
                        if (i < 3) {
                            eta = eta1;
                        } else {
                            eta = eta2;
                        }
                    }
                    int numValues = this.values[indices[i]].length;
                    if (v[i] == 0) {
                        tValue *= ProbUtils.normalCdf((
                                this.thresholds[indices[i]][0] * stdU[i] -
                                        coeff[i] * eta) / stdE[i]);
                    } else if (v[i] == numValues - 1) {
                        tValue *= (1. - ProbUtils.normalCdf((
                                this.thresholds[indices[i]][numValues - 2] *
                                        stdU[i] - coeff[i] * eta) / stdE[i]));
                    } else {
                        tValue *= ProbUtils.normalCdf((
                                this.thresholds[indices[i]][v[i]] * stdU[i] -
                                        coeff[i] * eta) / stdE[i]) -
                                ProbUtils.normalCdf((
                                        this.thresholds[indices[i]][v[i] - 1] *
                                                stdU[i] - coeff[i] * eta) /
                                        stdE[i]);
                    }
                }
                l += tValue;
            }
        }

        return l;
    }


    public ICovarianceMatrix getCovMatrix() {
        return null;
    }
}






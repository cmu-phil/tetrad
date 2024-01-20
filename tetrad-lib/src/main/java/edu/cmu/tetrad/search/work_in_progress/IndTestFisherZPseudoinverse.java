///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.util.FastMath;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Checks independence of X _||_ Y | Z for variables X and Y and list Z of variables. Partial correlations are
 * calculated using pseudoinverses, so linearly dependent variables do not throw exceptions. Must supply a continuous
 * data set; don't know how to do this with covariance or correlation matrices.
 *
 * @author josephramsey
 * @author Frank Wimberly adapted IndTestCramerT for Fisher's Z
 */
public final class IndTestFisherZPseudoinverse implements IndependenceTest {

    private static final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    private final Matrix data;

    // The variables of the correlation matrix, in order. (Unmodifiable list.)
    private final List<Node> variables;

    private final DataSet dataSet;

    // The significance level of the independence tests.
    private double alpha;

    // The cutoff value for 'alpha' area in the two tails of the partial correlation distribution function.
    private double thresh = Double.NaN;

    //The value of the Fisher's Z statistic associated with the last calculated partial correlation.
    private double fishersZ;
    private boolean verbose;

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation matrix implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet A data set containing only continuous columns.
     * @param alpha   The alpha level of the test.
     */
    public IndTestFisherZPseudoinverse(DataSet dataSet, double alpha) {
        if (!(alpha >= 0 && alpha <= 1)) {
            throw new IllegalArgumentException("Alpha mut be in [0, 1]");
        }

        this.dataSet = dataSet;

        this.data = dataSet.getDoubleData();
        this.variables = Collections.unmodifiableList(this.dataSet.getVariables());
        setAlpha(alpha);
    }

    //==========================PUBLIC METHODS=============================//

    /**
     * Creates a new IndTestCramerT instance for a subset of the variables.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        return null;
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning variables z.
     *
     * @param xVar the one variable being compared.
     * @param yVar the second variable being compared.
     * @param _z   the list of conditioning variables.
     * @return True iff x _||_ y | z.
     * @throws RuntimeException if a matrix singularity is encountered.
     */
    public IndependenceResult checkIndependence(Node xVar, Node yVar, Set<Node> _z) {
        if (_z == null) {
            throw new NullPointerException();
        }

        for (Node node : _z) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        int size = z.size();
        int[] zCols = new int[size];

        int xIndex = getVariables().indexOf(xVar);
        int yIndex = getVariables().indexOf(yVar);

        for (int i = 0; i < z.size(); i++) {
            zCols[i] = getVariables().indexOf(z.get(i));
        }

        Vector x = this.data.getColumn(xIndex);
        Vector y = this.data.getColumn(yIndex);

        CovarianceMatrix cov = new CovarianceMatrix(dataSet);

        SemBicScore.CovAndCoefs covAndCoefsX = SemBicScore.getCovAndCoefs(xIndex, zCols, this.data,
                cov, true, true);
        SemBicScore.CovAndCoefs covAndCoefsY = SemBicScore.getCovAndCoefs(yIndex, zCols, this.data,
                cov, true, true);

        int[] rows = new int[this.data.getNumRows()];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = i;
        }

        Matrix selection = data.getSelection(rows, zCols);
        Vector xPred = selection.times(covAndCoefsX.b()).getColumn(0);
        Vector yPred = selection.times(covAndCoefsY.b()).getColumn(0);

        Vector xRes = xPred.minus(x);
        Vector yRes = yPred.minus(y);

        // Note that r will be NaN if either xRes or yRes is constant.
        double r = StatUtils.correlation(xRes.toArray(), yRes.toArray());

        if (Double.isNaN(this.thresh)) {
            this.thresh = cutoffGaussian();
        }

        if (Double.isNaN(r)) {
            if (this.verbose) {
                TetradLogger.getInstance().log("independencies", LogUtilsSearch.independenceFactMsg(xVar, yVar, _z, getPValue()));
            }
            return new IndependenceResult(new IndependenceFact(xVar, yVar, _z), false, Double.NaN, Double.NaN);
        }

        if (r > 1) r = 1;
        if (r < -1) r = -1;

        this.fishersZ = FastMath.sqrt(sampleSize() - z.size() - 3.0) *
                0.5 * (FastMath.log(1.0 + r) - FastMath.log(1.0 - r));

        if (Double.isNaN(this.fishersZ)) {
            throw new IllegalArgumentException("The Fisher's Z " +
                    "score for independence fact " + xVar + " _||_ " + yVar +
                    " | " + z + " is undefined.");
        }

        boolean indFisher = !(FastMath.abs(this.fishersZ) > this.thresh);

        if (this.verbose) {
            TetradLogger.getInstance().log("independencies", LogUtilsSearch.independenceFactMsg(xVar, yVar, _z, getPValue()));
        }

        if (Double.isNaN(getPValue())) {
            throw new RuntimeException("Undefined p-value encountered for test: " + LogUtilsSearch.independenceFact(xVar, yVar, _z));
        }

        if (this.verbose) {
            if (indFisher) {
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFactMsg(xVar, yVar, _z, getPValue()));
            }
        }

        return new IndependenceResult(new IndependenceFact(xVar, yVar, _z), indFisher, getPValue(), getAlpha() - getPValue());
    }

    /**
     * @return the probability associated with the most recently computed independence test.
     */
    public double getPValue() {
        return 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, FastMath.abs(this.fishersZ)));
    }

    /**
     * Gets the getModel significance level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level at which independence judgments should be made.  Affects the cutoff for partial
     * correlations to be considered statistically equal to zero.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range.");
        }

        this.alpha = alpha;
    }

    /**
     * @return the list of variables over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    public String toString() {
        return "Fisher's Z - Pseudoinverse, alpha = " + IndTestFisherZPseudoinverse.nf.format(getAlpha());
    }

    /**
     * Returns the data being analyzed.
     *
     * @return This data.
     */
    public DataSet getData() {
        return this.dataSet;
    }

    /**
     * Returns True just in case verbose output should be printed.
     *
     * @return This.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns true just in case the varialbe in zList determine xVar.
     *
     * @return True, if so.
     */
    public boolean determines(List<Node> zList, Node xVar) {
        if (zList == null) {
            throw new NullPointerException();
        }

        if (zList.isEmpty()) {
            return false;
        }

        for (Node node : zList) {
            if (node == null) {
                throw new NullPointerException();
            }
        }

        int size = zList.size();
        int[] zCols = new int[size];

        int xIndex = getVariables().indexOf(xVar);
        Vector x = this.data.getColumn(xIndex);

        for (int i = 0; i < zList.size(); i++) {
            zCols[i] = getVariables().indexOf(zList.get(i));
        }

        CovarianceMatrix cov = new CovarianceMatrix(dataSet);

        SemBicScore.CovAndCoefs covAndCoefsX = SemBicScore.getCovAndCoefs(xIndex, zCols, this.data,
                cov, true, true);

        int[] rows = new int[this.data.getNumRows()];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = i;
        }

        Matrix selection = data.getSelection(rows, zCols);
        Vector xPred = selection.times(covAndCoefsX.b()).getColumn(0);
        Vector xRes = xPred.minus(x);

        double SSE = 0;

        for (int i = 0; i < xRes.size(); i++) {
            SSE += xRes.get(i) * xRes.get(i);
        }

        double variance = SSE / (this.data.getNumRows() - (zList.size() + 1));

        boolean determined = variance < getAlpha();

        if (determined) {
            StringBuilder sb = new StringBuilder();
            sb.append("Determination found: ").append(xVar).append(
                    " is determined by {");

            for (int i = 0; i < zList.size(); i++) {
                sb.append(zList.get(i));

                if (i < zList.size() - 1) {
                    sb.append(", ");
                }
            }

            sb.append("}");

            sb.append(" SSE = ").append(IndTestFisherZPseudoinverse.nf.format(SSE));

            TetradLogger.getInstance().log("independencies", sb.toString());
            System.out.println(sb);
        }

        return determined;
    }

    /**
     * Computes that value x such that P(abs(N(0,1) > x) < alpha.  Note that this is a two-sided test of the null
     * hypothesis that the Fisher's Z value, which is distributed as N(0,1) is not equal to 0.0.
     */
    private double cutoffGaussian() {
        double upperTail = 1.0 - getAlpha() / 2.0;
        final double epsilon = 1e-14;

        // Find an upper bound.
        double lowerBound = -1.0;
        double upperBound = 0.0;

        while (RandomUtil.getInstance().normalCdf(0, 1, upperBound) < upperTail) {
            lowerBound += 1.0;
            upperBound += 1.0;
        }

        while (upperBound >= lowerBound + epsilon) {
            double midPoint = lowerBound + (upperBound - lowerBound) / 2.0;

            if (RandomUtil.getInstance().normalCdf(0, 1, midPoint) <= upperTail) {
                lowerBound = midPoint;
            } else {
                upperBound = midPoint;
            }
        }

        return lowerBound;
    }

    private int sampleSize() {
        return this.data.getNumRows();
    }
}






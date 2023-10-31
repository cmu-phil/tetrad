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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.*;

import static org.apache.commons.math3.util.FastMath.*;

/**
 * <p>Calculates independence from multiple datasets from using the Fisher method
 * of pooling independence results. See this paper for details:</p>
 *
 * <p>Tillman, R. E., &amp; Eberhardt, F. (2014). Learning causal structure from
 * multiple datasets with similar variable sets. Behaviormetrika, 41(1), 41-64.</p>
 *
 * @author robertillman
 * @author josephramsey
 */
public final class IndTestFisherZFisherPValue implements IndependenceTest {
    private final List<Node> variables;
    private final int sampleSize;
    private final List<DataSet> dataSets;
    private final List<ICovarianceMatrix> ncov;
    private final Map<Node, Integer> variablesMap;
    private double alpha;
    private double pValue = Double.NaN;
    private boolean verbose;


    /**
     * Constructor.
     *
     * @param dataSets The continuous datasets to analyze.
     * @param alpha    The alpha significance cutoff value.
     */
    public IndTestFisherZFisherPValue(List<DataSet> dataSets, double alpha) {

        this.sampleSize = dataSets.get(0).getNumRows();
        setAlpha(alpha);
        this.ncov = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            this.ncov.add(new CovarianceMatrix(dataSet));
        }

        this.variables = dataSets.get(0).getVariables();
        this.variablesMap = new HashMap<>();
        for (int i = 0; i < this.variables.size(); i++) {
            this.variablesMap.put(this.variables.get(i), i);
        }

        for (DataSet dataSet : dataSets) {
            ((List<IndependenceTest>) new ArrayList<IndependenceTest>()).add(new IndTestFisherZ(dataSet, alpha));
        }

        this.dataSets = dataSets;
    }

    /**
     * @throws UnsupportedOperationException Not implemented.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning variables z.
     *
     * @param x  the one variable being compared.
     * @param y  the second variable being compared.
     * @param _z the list of conditioning variables.
     * @return True iff x _||_ y | z.
     * @throws RuntimeException if a matrix singularity is encountered.
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {
        try {
            List<Node> z = new ArrayList<>();
            Collections.sort(z);

            int[] all = new int[z.size() + 2];
            all[0] = this.variablesMap.get(x);
            all[1] = this.variablesMap.get(y);
            for (int i = 0; i < z.size(); i++) {
                all[i + 2] = this.variablesMap.get(z.get(i));
            }

            List<Double> pValues = new ArrayList<>();

            for (ICovarianceMatrix iCovarianceMatrix : this.ncov) {
                Matrix _ncov = iCovarianceMatrix.getSelection(all, all);
                Matrix inv = _ncov.inverse();
                double r = -inv.get(0, 1) / sqrt(inv.get(0, 0) * inv.get(1, 1));
                double __z = sqrt(this.sampleSize - z.size() - 3.0) * 0.5 * (log(1.0 + r) - log(1.0 - r));
                double pvalue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(__z)));
                pValues.add(pvalue);
            }

            Collections.sort(pValues);
            int n = 0;
            double tf = 0.0;

            int numZeros = 0;

            for (double p : pValues) {
                if (p == 0) {
                    numZeros++;
                    continue;
                }
                tf += -2.0 * log(p);
                n++;
            }

            if (numZeros >= pValues.size() / 2)
                return new IndependenceResult(new IndependenceFact(x, y, _z), false, Double.NaN, Double.NaN);

            if (tf == 0) throw new IllegalArgumentException(
                    "For the Fisher method, all component p values in the calculation may not be zero, " +
                            "\nsince not all p values can be ignored. Maybe try calculating AR residuals.");
            double p = 1.0 - ProbUtils.chisqCdf(tf, 2 * n);

            if (Double.isNaN(p)) {
                throw new RuntimeException("Undefined p-value encountered for test: " + LogUtilsSearch.independenceFact(x, y, _z));
            }

            this.pValue = p;

            boolean independent = p > this.alpha;

            if (this.verbose) {
                if (independent) {
                    TetradLogger.getInstance().forceLogMessage(
                            LogUtilsSearch.independenceFactMsg(x, y, _z, this.pValue));
                }
            }


            return new IndependenceResult(new IndependenceFact(x, y, _z), independent, p, getAlpha() - p);
        } catch (SingularMatrixException e) {
            throw new RuntimeException("Singularity encountered when testing " +
                    LogUtilsSearch.independenceFact(x, y, _z));
        }
    }

    /**
     * Gets the getModel significance level.
     *
     * @return this alpha.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level at which independence judgments should be made.  Affects the cutoff for partial
     * correlations to be considered statistically equal to zero.
     *
     * @param alpha This alpha.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range.");
        }

        this.alpha = alpha;
    }

    /**
     * Returns the list of variables over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * @throws UnsupportedOperationException Not implemented.
     */
    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the concatenated data.
     *
     * @return This data
     */
    public DataSet getData() {
        return DataTransforms.concatenate(this.dataSets);
    }

    /**
     * Returns the covariance matrix of the concatenated data.
     *
     * @return This covariance matrix.
     */
    public ICovarianceMatrix getCov() {
        List<DataSet> _dataSets = new ArrayList<>();

        for (DataSet d : this.dataSets) {
            _dataSets.add(DataTransforms.standardizeData(d));
        }

        return new CovarianceMatrix(DataTransforms.concatenate(_dataSets));
    }

    /**
     * Returns a string representation of this test.
     *
     * @return This string.
     */
    public String toString() {
        return "Fisher Z, Fisher P Value Percent = " + round(.5 * 100);
    }

    /**
     * Returns True if verbose output should be printed.
     *
     * @return True, if so.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output is printed.
     *
     * @param verbose True, if so.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}



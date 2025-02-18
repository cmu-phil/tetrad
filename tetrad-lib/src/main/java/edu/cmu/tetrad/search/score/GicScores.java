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

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Matrix;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.util.FastMath;

import java.util.List;

import static org.apache.commons.math3.util.FastMath.*;

/**
 * Implements scores motivated by the Generalized Information Criterion (GIC) approach as given in Kim et al. (2012).
 * <p>
 * Kim, Y., Kwon, S., &amp; Choi, H. (2012). Consistent model selection criteria on high dimensions. The Journal of
 * Machine Learning Research, 13(1), 1037-1057.
 * <p>
 * As for all scores in Tetrad, higher scores mean more dependence, and negative scores indicate independence.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GicScores implements Score {

    // The sample size of the covariance matrix.
    private final int sampleSize;
    Matrix data;
    // The dataset.
    private DataSet dataSet;
    // The correlation matrix.
    private ICovarianceMatrix covariances;
    // The variables of the covariance matrix.
    private List<Node> variables;
    // True if verbose output should be sent to out.
    private boolean verbose = false;
    // The rule type to use.
    private RuleType ruleType = RuleType.MANUAL;
    // Sample size or equivalent sample size.
    private double N;
    // Manually set lambda, by default log(n);
    private double lambda;
    private boolean calculateRowSubsets = false;
    //    private boolean calculateSquareEuclideanNorms = false;
    private double penaltyDiscount = 1;
    // True if the pseudo-inverse should be used.
    private boolean enableRegularization = true;

    /**
     * Constructs the score using a covariance matrix.
     *
     * @param covariances The covariance matrix.
     */
    public GicScores(ICovarianceMatrix covariances) {
        if (covariances == null) {
            throw new NullPointerException();
        }

        setCovariances(covariances);
        this.variables = covariances.getVariables();
        this.sampleSize = covariances.getSampleSize();
        this.setLambda(log(this.sampleSize));
    }

    /**
     * Constructs the score using a covariance matrix.
     *
     * @param dataSet               The continuous dataset to analyze.
     * @param precomputeCovariances Whether the covariances should be precomputed or computed on the fly. True if
     */
    public GicScores(DataSet dataSet, boolean precomputeCovariances) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        ICovarianceMatrix covarianceMatrix = (SimpleDataLoader.getCovarianceMatrix(dataSet, precomputeCovariances));

        this.data = dataSet.getDoubleData();
        this.dataSet = dataSet;

        if (!dataSet.existsMissingValue()) {
            setCovariances(covarianceMatrix);// new CovarianceMatrix(dataSet, false));
            this.variables = covariances.getVariables();
            this.sampleSize = covariances.getSampleSize();
            calculateRowSubsets = false;
            return;
        }

        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        calculateRowSubsets = true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Calculates the sample likelihood and BIC score for index i given its parents in a simple SEM model.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Calculates the sample likelihood and BIC score for index i given its parents in a simple SEM model.
     *
     * @param i       The node.
     * @param parents The parents.
     * @return The score.
     */
    public double localScore(int i, int... parents) {
        double sn = 12;

        if (parents.length > sn) return Double.NEGATIVE_INFINITY;
        final int k = parents.length;

        // Only do this once.
        double pn = variables.size();
        pn = min(pn, sn);
        double n = N;
        double varry;

        try {
            varry = SemBicScore.getResidualVariance(i, parents, data, covariances, calculateRowSubsets, this.enableRegularization);
        } catch (SingularMatrixException e) {
            throw new RuntimeException("Singularity encountered when scoring " +
                                       LogUtilsSearch.getScoreFact(i, parents, variables));
        }

        double lambda;

        // Defaults to the manually set lambda.
        if (ruleType == RuleType.MANUAL) {
            lambda = this.lambda;
        } else if (ruleType == RuleType.BIC) {
            lambda = log(n);
        } else if (ruleType == RuleType.GIC2) {

            // Following Kim, Y., Kwon, S., & Choi, H. (2012). Consistent model selection criteria on high dimensions.
            // The Journal 0of Machine Learning Research, 13(1), 1037-1057.
            lambda = pow(n, .33);
        } else if (ruleType == RuleType.RIC) {

            // Following Kim, Y., Kwon, S., & Choi, H. (2012). Consistent model selection criteria on high dimensions.
            // The Journal 0of Machine Learning Research, 13(1), 1037-1057.
            lambda = 2.2 * (log(pn));
        } else if (ruleType == RuleType.RICc) {

            // Following Kim, Y., Kwon, S., & Choi, H. (2012). Consistent model selection criteria on high dimensions.
            // The Journal of Machine Learning Research, 13(1), 1037-1057.
            lambda = 2 * (log(pn) + log(log(pn)));
        } else if (ruleType == RuleType.GIC5) {

            // Following Kim, Y., Kwon, S., & Choi, H. (2012). Consistent model selection criteria on high dimensions.
            // The Journal of Machine Learning Research, 13(1), 1037-1057.
            lambda = log(log(n)) * (log(pn));
        } else if (ruleType == RuleType.GIC6) {

            // Following Kim, Y., Kwon, S., & Choi, H. (2012). Consistent model selection criteria on high dimensions.
            // The Journal of Machine Learning Research, 13(1), 1037-1057.
            lambda = log(n) * log(pn);
        } else {
            throw new IllegalStateException("That lambda rule is not configured: " + ruleType);
        }

        return -(n / 2.0) * log(varry) - lambda * getPenaltyDiscount() * k;
    }

    /**
     * Returns the sample size.
     *
     * @return This size.
     */
    public ICovarianceMatrix getCovariances() {
        return covariances;
    }

    /**
     * Sets the covariance matrix.
     *
     * @param covariances The covariance matrix.
     */
    private void setCovariances(ICovarianceMatrix covariances) {
        this.covariances = covariances;
        this.N = covariances.getSampleSize();
    }

    /**
     * Returns the sample size.
     *
     * @return This size.
     */
    public int getSampleSize() {
        return sampleSize;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns true if an edge with this bump is an effect edge.
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * Returns the dataset.
     *
     * @return The dataset.
     */
    public DataSet getDataSet() {
        return dataSet;
    }

    /**
     * Returns true if verbose output should be sent to out.
     *
     * @return True if verbose output should be sent to out.
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether verbose output should be sent to out.
     *
     * @param verbose True if verbose output should be sent to out.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the variables of the dataset.
     */
    @Override
    public List<Node> getVariables() {
        return variables;
    }

    /**
     * Sets the variables of the dataset.
     *
     * @param variables The variables of the dataset.
     */
    public void setVariables(List<Node> variables) {
        if (covariances != null) {
            covariances.setVariables(variables);
        }

        this.variables = variables;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the max degree of the graph for some algorithms.
     */
    @Override
    public int getMaxDegree() {
        return (int) FastMath.ceil(log(sampleSize));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a judgment of whether the variable in z determine y exactly.
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        int i = variables.indexOf(y);

        int[] k = new int[z.size()];

        for (int t = 0; t < z.size(); t++) {
            k[t] = variables.indexOf(z.get(t));
        }

        double v = localScore(i, k);

        return Double.isNaN(v);
    }

    /**
     * Sets the rule type.
     *
     * @param ruleType The rule type.
     * @see RuleType
     */
    public void setRuleType(RuleType ruleType) {
        this.ruleType = ruleType;
    }

    /**
     * Sets the lambda parameter.
     *
     * @param lambda The lambda parameter.
     */
    public void setLambda(double lambda) {
        this.lambda = lambda;
    }

    /**
     * Returns the penalty discount.
     *
     * @return The penalty discount.
     */
    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    /**
     * Sets the penalty discount.
     *
     * @param penaltyDiscount The penalty discount.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Returns a string for this object.
     *
     * @return A string for this object.
     */
    public String toString() {
        return "Generalized Information Criterion Score";
    }

    /**
     * Sets whether to use the pseudo-inverse when calculating the score.
     *
     * @param enableRegularization True, if so.
     */
    public void setEnableRegularization(boolean enableRegularization) {
        this.enableRegularization = enableRegularization;
    }

    /**
     * Gives the options for the rules to use for calculating the scores. The "GIC" rules, and RICc, are the rules
     * proposed in the Kim et al. paper for generalized information criteria.
     *
     * @see GicScores
     */
    public enum RuleType {

        /**
         * The lambda is set manually.
         */
        MANUAL,

        /**
         * The Bayesian Information Criterion.
         */
        BIC,

        /**
         * BIC using Nandy et al.'s formulation.
         */
        NANDY,

        /**
         * The GIC2 rule.
         */
        GIC2,

        /**
         * The RIC rule.
         */
        RIC,

        /**
         * The RICc rule.
         */
        RICc,

        /**
         * The GIC5 rule.
         */
        GIC5,

        /**
         * The GIC6 rule.
         */
        GIC6
    }
}



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

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.work_in_progress.MagSemBicScore;
import org.apache.commons.math3.util.FastMath;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements a conditional Gaussian BIC score for FGS, which calculates a BIC score for mixed discrete/Gaussian data
 * using the conditional Gaussian likelihood function (see). The reference is here:
 * <p>
 * Andrews, B., Ramsey, J., &amp; Cooper, G. F. (2018). Scoring Bayesian networks of mixed variables. International
 * journal of data science and analytics, 6, 3-18.
 * <p>
 * As for all scores in Tetrad, higher scores mean more dependence, and negative scores indicate independence.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see ConditionalGaussianLikelihood
 * @see DegenerateGaussianScore
 */
public class ConditionalGaussianScore implements Score {
    // The dataset.
    private final DataSet dataSet;
    // The variables of the dataset.
    private final List<Node> variables;
    // Likelihood function
    private final ConditionalGaussianLikelihood likelihood;
    // The penalty discount.
    private double penaltyDiscount;
    // The number of categories to discretize.
    private int numCategoriesToDiscretize = 3;
    // The structure prior.
    private double structurePrior = 0;

    /**
     * Constructs the score using a covariance matrix.
     *
     * @param dataSet         A dataset with a mixture of continuous and discrete variables. It may be all continuous or
     *                        all discrete.
     * @param penaltyDiscount A multiplier on the penalty term in the BIC score.
     * @param discretize      When a discrete variable is a child of a continuous variable, one (expensive) way to solve
     *                        the problem is to do a numerical integration. A less expensive (and often more accurate)
     *                        way to solve the problem is to discretize the child with a certain number of discrete
     *                        categories. if this parameter is set to True, a separate copy of all variables is
     *                        maintained that is discretized in this way, and these are substituted for the discrete
     *                        children when this sort of problem needs to be solved. This information needs to be known
     *                        in the constructor since one needs to know right away whether ot create this separate
     *                        discretized version of the continuous columns.
     * @see #setNumCategoriesToDiscretize
     */
    public ConditionalGaussianScore(DataSet dataSet, double penaltyDiscount, boolean discretize) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.penaltyDiscount = penaltyDiscount;

        this.likelihood = new ConditionalGaussianLikelihood(dataSet);

        this.likelihood.setNumCategoriesToDiscretize(this.numCategoriesToDiscretize);
        this.likelihood.setDiscretize(discretize);
    }

    /**
     * Calculates the sample likelihood and BIC score for index i given its parents in a simple SEM model.
     *
     * @param i       The index of the child.
     * @param parents The indices of the parents.
     * @return The score.,
     */
    public double localScore(int i, int... parents) {
        List<Integer> rows = getRows(i, parents);
        this.likelihood.setRows(rows);

        ConditionalGaussianLikelihood.Ret ret = this.likelihood.getLikelihood(i, parents);

        double lik = ret.getLik();
        int k = ret.getDof();

        double score = 2.0 * (lik + getStructurePrior(parents)) - getPenaltyDiscount() * k * FastMath.log(rows.size());

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return Double.NEGATIVE_INFINITY;
        } else {
            return score;
        }
    }

    /**
     * Calculates localScore(y | z, x) - localScore(z).
     *
     * @param x The index of the child.
     * @param z The indices of the parents.
     * @param y a int
     * @return The score difference.
     */
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Returns the sample size of the data.
     *
     * @return This size.
     */
    public int getSampleSize() {
        return this.dataSet.getNumRows();
    }

    /**
     * {@inheritDoc}
     * <p>
     * A method for FGES for determining whether an edge counts as an effect edges for this score bump.
     *
     * @see Fges
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the variables of the data.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the max degree recommended for the search form the MagSemBicScore and Fges.
     *
     * @see MagSemBicScore
     * @see Fges
     */
    @Override
    public int getMaxDegree() {
        return (int) FastMath.ceil(FastMath.log(this.dataSet.getNumRows()));
    }

    /**
     * Returns the penalty discount for this score, which is a multiplier on the penalty term of the BIC score.
     *
     * @return This penalty discount.
     */
    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    /**
     * Sets the penalty discount for this score, which is a multiplier on the penalty discount of the BIC score.
     *
     * @param penaltyDiscount This penalty discount.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Sets tne number of categories used to discretize, when this optimization is used.
     *
     * @param numCategoriesToDiscretize This number.
     */
    public void setNumCategoriesToDiscretize(int numCategoriesToDiscretize) {
        this.numCategoriesToDiscretize = numCategoriesToDiscretize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "Conditional Gaussian Score Penalty " + nf.format(this.penaltyDiscount);
    }

    /**
     * <p>Setter for the field <code>structurePrior</code>.</p>
     *
     * @param structurePrior a double
     */
    public void setStructurePrior(double structurePrior) {
        this.structurePrior = structurePrior;
    }

    private List<Integer> getRows(int i, int[] parents) {
        List<Integer> rows = new ArrayList<>();

        K:
        for (int k = 0; k < this.dataSet.getNumRows(); k++) {
            if (this.variables.get(i) instanceof DiscreteVariable) {
                if (this.dataSet.getInt(k, i) == -99) continue;
            } else if (this.variables.get(i) instanceof ContinuousVariable) {
                this.dataSet.getInt(k, i);
            }

            for (int p : parents) {
                if (this.variables.get(i) instanceof DiscreteVariable) {
                    if (this.dataSet.getInt(k, p) == -99) continue K;
                } else if (this.variables.get(i) instanceof ContinuousVariable) {
                    this.dataSet.getInt(k, p);
                }
            }

            rows.add(k);
        }

        return rows;
    }

    private double getStructurePrior(int[] parents) {
        if (this.structurePrior <= 0) {
            return 0;
        } else {
            int k = parents.length;
            double n = this.dataSet.getNumColumns() - 1;
            double p = this.structurePrior / n;
            return k * FastMath.log(p) + (n - k) * FastMath.log(1.0 - p);
        }
    }
}





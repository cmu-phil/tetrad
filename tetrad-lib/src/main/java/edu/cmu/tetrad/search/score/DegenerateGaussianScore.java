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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * =This implements the degenerate Gaussian BIC score for FGES. The degenerate Gaussian score replaces each discrete
 * variable in the data with a list of 0/1 continuous indicator columns for each of the categories but one (the last one
 * implied). This data, now all continuous, is given to the SEM BIC score and methods used to help determine conditional
 * independence for the mixed continuous/discrete case from this information. The reference is as follows:
 * <p>
 * Andrews, B., Ramsey, J., &amp; Cooper, G. F. (2019, July). Learning high-dimensional directed acyclic graphs with
 * mixed data-types. In The 2019 ACM SIGKDD Workshop on Causal Discovery (pp. 4-21). PMLR.
 * <p>
 * As for all scores in Tetrad, higher scores mean more dependence, and negative scores indicate independence.
 *
 * @author Bryan Andrews
 * @version $Id: $Id
 */
public class DegenerateGaussianScore implements Score, EffectiveSampleSizeSettable {
    // The mixed variables of the original dataset.
    private final List<Node> variables;
    // The embedding map.
    private final Map<Integer, List<Integer>> embedding;
    // The SEM BIC score.
    private final SemBicScore bic;
    private final int sampleSize;
    private int nEff;

    /**
     * Constructs the score using a dataset.
     *
     * @param dataSet               The dataset.
     * @param precomputeCovariances True if covariances should be precomputed.
     * @param lambda                Singularity lambda
     */
    public DegenerateGaussianScore(DataSet dataSet, boolean precomputeCovariances, double lambda) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();

        // Expand the discrete columns to give indicators for each category. For the continuous variables, we
        // wet the truncation limit to 1, on the contrqact that the first polynomial for any basis will be just
        // x itself. These are asssumed to be Gaussian for this test. Basis scale -1 will do no scaling.
        Embedding.EmbeddedData embeddedData = Embedding.getEmbeddedData(
                dataSet, 1, 1, -1);
        DataSet convertedData = embeddedData.embeddedData();
        this.embedding = embeddedData.embedding();

        this.bic = new SemBicScore(convertedData, precomputeCovariances);
        this.bic.setEffectiveSampleSize(this.nEff);
        this.bic.setLambda(lambda);
        this.bic.setStructurePrior(0);

        setEffectiveSampleSize(-1);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model.
     *
     * @param i       The child indes.
     * @param parents The indices of the parents.
     * @return a double
     */
    public double localScore(int i, int... parents) {
        double score = 0;

        List<Integer> A = new ArrayList<>(this.embedding.get(i));
        List<Integer> B = new ArrayList<>();
        for (int i_ : parents) {
            B.addAll(this.embedding.get(i_));
        }

        for (Integer i_ : A) {
            int[] parents_ = new int[B.size()];
            for (int i__ = 0; i__ < B.size(); i__++) {
                parents_[i__] = B.get(i__);
            }
            score += this.bic.localScore(i_, parents_);
            B.add(i_);
        }

        return score;
    }

    /**
     * Calculates localScore(y | z, x) - localScore(z).
     *
     * @param x A node.
     * @param y TAhe node.
     * @param z A set of nodes.
     * @return The score difference.
     */
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the list of variables.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * {@inheritDoc}
     * <p>
     * True if an edge with the given bump is an effect edge.
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return this.bic.isEffectEdge(bump);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the sample sizE.
     */
    @Override
    public int getSampleSize() {
        return this.bic.getSampleSize();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the max degree.
     */
    @Override
    public int getMaxDegree() {
        return this.bic.getMaxDegree();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a string for this object.
     */
    @Override
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.00");
        return "Degenerate Gaussian Score Penalty " + nf.format(this.bic.getPenaltyDiscount());
    }

    /**
     * Returns the penalty discount.
     *
     * @return The penalty discount.
     */
    public double getPenaltyDiscount() {
        return this.bic.getPenaltyDiscount();
    }

    /**
     * Sets the penalty discount.
     *
     * @param penaltyDiscount The penalty discount.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.bic.setPenaltyDiscount(penaltyDiscount);
    }

    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = nEff < 0 ? this.sampleSize : nEff;
        if (bic == null) {
            throw new IllegalStateException("bic is null");
        }
        this.bic.setEffectiveSampleSize(this.nEff);
    }
}


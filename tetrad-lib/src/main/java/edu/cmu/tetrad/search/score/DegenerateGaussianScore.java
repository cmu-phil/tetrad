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
import edu.cmu.tetrad.data.EmCovarianceEstimator;
import edu.cmu.tetrad.data.missing.MissingDataPolicy;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MissingValueSupport;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
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
        this(dataSet, precomputeCovariances, lambda, null);
    }

    /**
     * Constructs the score from a dataset with an explicit missing-data specification. Supported policies on a
     * dataset with missing values are LISTWISE (complete cases only) and TESTWISE: the embedding propagates a
     * missing source entry to NaN in every derived column of that variable, and the underlying
     * {@link SemBicScore} then computes each family's covariance over the rows complete on that family's embedded
     * columns. EM_COVARIANCE is not offered, because the indicator columns of the embedding are not Gaussian and
     * an EM-estimated covariance of them has no interpretation. A null spec on missing data is treated as FAIL,
     * matching the behavior since the Phase 1 refactor (before which missing data silently produced statistically
     * undefined scores).
     *
     * @param dataSet               The dataset.
     * @param precomputeCovariances True if covariances should be precomputed.
     * @param lambda                Singularity lambda
     * @param spec                  The missing-data specification, or null (equivalent to FAIL on missing data).
     * @throws IllegalArgumentException      If the dataset has missing values and the policy is FAIL or
     *                                       EM_COVARIANCE.
     * @throws UnsupportedOperationException If the policy is MULTIPLE_IMPUTATION (handled by a search wrapper, not
     *                                       by a single score; see Phase 3).
     */
    public DegenerateGaussianScore(DataSet dataSet, boolean precomputeCovariances, double lambda,
                                   MissingDataSpec spec) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        boolean testwise = false;
        boolean emCovariance = false;

        // Held before any deletion or embedding: the effective sample size is a fact about the raw variables.
        // Computing it after embedding would be wrong for MEAN_PAIRWISE, which averages over pairs and so would
        // weight each variable by the width of its embedding block -- a five-category discrete variable counting
        // twenty-five times a continuous one. MIN_PAIRWISE is invariant under embedding, since a missing source
        // entry becomes NaN in every derived column of that variable, so the minimum over embedded pairs equals
        // the minimum over variable pairs; but it costs nothing to compute both on the raw data.
        DataSet rawData = dataSet;

        if (dataSet.existsMissingValue()) {
            MissingDataPolicy policy = spec == null ? MissingDataPolicy.FAIL : spec.getPolicy();

            switch (policy) {
                case LISTWISE -> dataSet = MissingDataUtils.listwiseDelete(dataSet);
                case TESTWISE -> testwise = true;
                case EM_COVARIANCE -> emCovariance = true;
                case MULTIPLE_IMPUTATION -> throw new UnsupportedOperationException(
                        "DegenerateGaussianScore: MULTIPLE_IMPUTATION is handled by a search wrapper over imputed "
                                + "datasets, not by a single score.");
                default -> throw new IllegalArgumentException(
                        "DegenerateGaussianScore: The dataset contains missing values and the missing-data policy "
                                + "is " + policy + ". This score supports LISTWISE, TESTWISE and EM_COVARIANCE on "
                                + "missing data. " + MissingDataUtils.briefSummary(dataSet));
            }
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

        // Under TESTWISE the embedded data carries NaN wherever the source was missing, and SemBicScore's own
        // test-wise path takes over; the explicit spec avoids its legacy-default warning.
        //
        // Under EM_COVARIANCE the EM estimate is taken of the *embedded* matrix, which is the right place for it:
        // this score's founding assumption is already that the indicator columns may be treated as jointly
        // Gaussian for scoring, and EM under that same working model is not an additional assumption but the
        // existing one carried through to incomplete data. Nothing downstream ever inspects a filled-in
        // indicator -- EM yields sufficient statistics and this score consumes only a covariance -- and what EM
        // accumulates for an indicator column is E[1{V = c} | observed], a conditional category probability. The
        // embedding drops a reference category, so an indicator block is full rank and the estimate is not
        // degenerate by construction.
        //
        // The honest caveat: under a Gaussian conditional those expectations can fall outside [0, 1], so the
        // implied category probabilities need not be coherent. That is the same approximation the score already
        // makes on complete data, but here it also applies to the filled-in portion, so its size grows with the
        // missingness rate rather than staying fixed. Compare against TESTWISE on real data rather than assuming
        // either dominates.
        if (emCovariance) {
            EmCovarianceEstimator estimator = new EmCovarianceEstimator(convertedData);
            estimator.setRidge(spec.getEmRidge());
            estimator.setTolerance(spec.getEmTolerance());
            estimator.setMaxIterations(spec.getEmMaxIterations());
            this.bic = new SemBicScore(estimator.estimate());
        } else if (testwise) {
            this.bic = new SemBicScore(convertedData, precomputeCovariances, MissingDataSpec.testwise());
        } else {
            this.bic = new SemBicScore(convertedData, precomputeCovariances);
        }
        this.bic.setEffectiveSampleSize(this.nEff);
        this.bic.setLambda(lambda);
        this.bic.setStructurePrior(0);

        // Under test-wise deletion the penalty already scales with each family's own row count, but the
        // likelihood term is multiplied by nEff, which defaults to the full row count. Crediting the fit with N
        // rows' worth of information while charging the penalty for the family's actual rows biases every score
        // in the direction of more edges. An ESS mode other than FULL_N discounts the likelihood to match.
        // Applied only under TESTWISE: LISTWISE has already reduced the data to complete cases, whose row count
        // is the honest sample size for every family.
        setEffectiveSampleSize(testwise || emCovariance
                ? MissingDataUtils.effectiveSampleSize(rawData, spec) : -1);
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

    /**
     * {@inheritDoc}
     * <p>
     * TESTWISE: the embedding propagates missing source entries to every derived column, and the underlying
     * SemBicScore then computes each family's statistics over the rows complete on that family.
     */
    @Override
    public MissingValueSupport getMissingValueSupport() {
        return MissingValueSupport.TESTWISE;
    }
}

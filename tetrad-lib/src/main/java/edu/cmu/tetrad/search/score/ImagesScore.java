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

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.Grasp;

import java.util.List;

/**
 * Implements a score that aggregates results over multiple component scores, one per dataset, for use with the IMaGES
 * algorithm. The idea is that one picks an algorithm that takes (only) a score as input, such as FGES or GRaSP or BOSS,
 * constructs an ImagesScore with a list of component scores defined over the same (object-identical) variables, and
 * feeds this aggregate score to the algorithm. One then runs the algorithm to obtain an estimate of the common
 * structure across the datasets.
 * <p>
 * The aggregate score is a weighted average of the component scores. Two weighting schemes are provided, which differ
 * in how they behave when the component datasets have unequal sample sizes:
 * <ul>
 * <li>{@link WeightingScheme#SAMPLE_UNIFORM}: The component scores are averaged with equal weights. Since
 * log-likelihood differences scale (roughly) linearly with sample size, this weights each <em>sample</em> equally
 * across datasets, so that larger datasets have proportionally more influence on the result ("one sample, one vote").
 * This is the fixed-effects pooling rule, and it coincides with the historical behavior of this class. When all sample
 * sizes are equal, the two schemes agree.</li>
 * <li>{@link WeightingScheme#DATASET_WEIGHTED}: Each component score k is first rescaled by the factor
 * (mean sample size) / N_k, which equalizes the effective sample sizes of the components, and then a weighted average
 * is taken using a user-supplied weight schedule (by default uniform). With the default uniform schedule, each
 * <em>dataset</em> gets an equal vote regardless of its sample size ("one dataset, one vote"). Non-uniform schedules
 * may be supplied via {@link #setWeights(double[])}, e.g., to downweight datasets judged less reliable. Since the
 * rescaled penalty term still grows logarithmically in N, BIC consistency is preserved.</li>
 * </ul>
 * In both schemes, the effective weights are fixed at construction (or when a new weight schedule is set) and are
 * normalized to sum to 1, so score differences are on the same scale as a single-dataset score.
 * <p>
 * NaN handling: if any component score returns NaN (or an infinite value) for a given local score or local score
 * difference, the aggregate returns NaN for that call. Component scores are <em>not</em> silently dropped from the
 * average, since doing so would cause different local models to be scored by averages over different subsets of the
 * datasets, so that the search would no longer be maximizing a single well-defined objective function. (Undefined
 * component scores typically indicate near-singularity for the candidate parent set in some dataset; the remedy is to
 * address that dataset or model, not to change the electorate mid-election.) For the same reason,
 * {@link #localScoreDiff(int, int, int[])} aggregates the component score differences with the same fixed weights and
 * the same NaN policy, so that it is consistent with differences of {@link #localScore(int, int[])}.
 * <p>
 * Importantly, only the variables from the first score will be returned from the getVariables method, so it is up to
 * the user to ensure that all the scores share the same (object-identical) variables.
 * <p>
 * As for all scores in Tetrad, higher scores mean more dependence, and negative scores indicate independence.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see Fges
 * @see Grasp
 * @see Boss
 */
public class ImagesScore implements Score {

    /**
     * The weighting scheme used to aggregate component scores when sample sizes differ.
     */
    public enum WeightingScheme {

        /**
         * Plain (equally weighted) average of the component scores. Each sample counts equally across datasets, so
         * datasets influence the result in proportion to their sample sizes.
         */
        SAMPLE_UNIFORM,

        /**
         * Weighted average of the component scores after rescaling each by (mean N) / N_k, so that each dataset's
         * vote is independent of its sample size. The weight schedule is uniform by default and may be set via
         * {@link ImagesScore#setWeights(double[])}.
         */
        DATASET_WEIGHTED
    }

    // The component scores, one per dataset.
    private final List<Score> scores;
    // The variables of the first component score.
    private final List<Node> variables;
    // The sample sizes of the component scores, cached at construction.
    private final int[] sampleSizes;
    // The weighting scheme in use.
    private final WeightingScheme weightingScheme;
    // The user-supplied weight schedule over datasets (uniform by default); relevant for DATASET_WEIGHTED.
    private double[] weights;
    // The effective (normalized) weights actually applied to the component scores.
    private double[] effectiveWeights;

    /**
     * Constructs an IMaGES score using the given list of individual scores, using the SAMPLE_UNIFORM weighting scheme
     * (a plain average of the component scores, which reproduces the historical behavior of this class).
     *
     * @param scores The list of scores.
     */
    public ImagesScore(List<Score> scores) {
        this(scores, WeightingScheme.DATASET_WEIGHTED);
    }

    /**
     * Constructs an IMaGES score using the given list of individual scores and the given weighting scheme. For
     * DATASET_WEIGHTED, the weight schedule is uniform by default and may be changed via {@link #setWeights(double[])}.
     *
     * @param scores          The list of scores.
     * @param weightingScheme The weighting scheme to use; see {@link WeightingScheme}.
     */
    public ImagesScore(List<Score> scores, WeightingScheme weightingScheme) {
        if (scores == null) {
            throw new NullPointerException("Scores list is null.");
        }

        if (scores.isEmpty()) {
            throw new IllegalArgumentException("Scores list is empty.");
        }

        for (Score score : scores) {
            if (score == null) {
                throw new NullPointerException("Component score is null.");
            }
        }

        if (weightingScheme == null) {
            throw new NullPointerException("Weighting scheme is null.");
        }

        this.scores = scores;
        this.weightingScheme = weightingScheme;
        this.variables = scores.get(0).getVariables();

        this.sampleSizes = new int[scores.size()];

        for (int k = 0; k < scores.size(); k++) {
            this.sampleSizes[k] = scores.get(k).getSampleSize();

            if (this.sampleSizes[k] <= 0) {
                throw new IllegalArgumentException("Component score " + k + " reports a nonpositive sample size: "
                        + this.sampleSizes[k]);
            }
        }

        this.weights = new double[scores.size()];

        for (int k = 0; k < scores.size(); k++) {
            this.weights[k] = 1.0;
        }

        recomputeEffectiveWeights();
    }

    /**
     * Sets the weight schedule over datasets. This is relevant only for the DATASET_WEIGHTED scheme (an exception is
     * thrown otherwise, to avoid the misleading impression that a schedule is being applied). Weights must be positive
     * and finite; they need not be normalized, as normalization is handled internally. The default schedule is
     * uniform, i.e., each dataset gets an equal vote.
     *
     * @param weights The weight for each dataset, in the order of the scores list supplied to the constructor.
     */
    public void setWeights(double[] weights) {
        if (this.weightingScheme != WeightingScheme.DATASET_WEIGHTED) {
            throw new IllegalStateException("A weight schedule may only be set for the DATASET_WEIGHTED scheme.");
        }

        if (weights == null) {
            throw new NullPointerException("Weights array is null.");
        }

        if (weights.length != this.scores.size()) {
            throw new IllegalArgumentException("Expecting one weight per dataset: expected " + this.scores.size()
                    + " but got " + weights.length + ".");
        }

        for (double weight : weights) {
            if (Double.isNaN(weight) || Double.isInfinite(weight) || weight <= 0.0) {
                throw new IllegalArgumentException("Weights must be positive and finite: " + weight);
            }
        }

        this.weights = weights.clone();
        recomputeEffectiveWeights();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the weighted average of the individual score differences returned from each component score from their
     * localScoreDiff methods, using the same fixed effective weights as localScore, so that this method is consistent
     * with differences of localScore. If any component returns an undefined (NaN or infinite) difference, NaN is
     * returned.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) throws InterruptedException {
        double sum = 0.0;

        for (int k = 0; k < this.scores.size(); k++) {
            double _score = this.scores.get(k).localScoreDiff(x, y, z);

            if (Double.isNaN(_score) || Double.isInfinite(_score)) {
                return Double.NaN;
            }

            sum += this.effectiveWeights[k] * _score;
        }

        return sum;
    }

    /**
     * Returns the (aggregate) local score for a variable given its parents, obtained as the weighted average of the
     * local scores from each component score, under the weighting scheme in use. If any component returns an undefined
     * (NaN or infinite) score, NaN is returned; components are never silently dropped from the average.
     *
     * @param i       The variable whose score is needed.
     * @param parents The indices of the parents.
     * @return This score.
     */
    public double localScore(int i, int[] parents) {
        double sum = 0.0;

        for (int k = 0; k < this.scores.size(); k++) {
            double _score = this.scores.get(k).localScore(i, parents);

            if (Double.isNaN(_score) || Double.isInfinite(_score)) {
                return Double.NaN;
            }

            sum += this.effectiveWeights[k] * _score;
        }

        return sum;
    }

    /**
     * Returns the local score for a variable given its parents from a single component score, unweighted. This is
     * provided for callers that need per-dataset scores; it does not participate in the aggregation.
     *
     * @param i       The variable whose score is needed.
     * @param parents The indices of the parents.
     * @param index   The index of the component score to use.
     * @return This score.
     */
    public double localScore(int i, int[] parents, int index) {
        return localScoreOneDataSet(i, parents, index);
    }

    /**
     * Returns the (aggregate) local score for a variable given one of its parents, obtained as the weighted average of
     * the local scores from each component score, under the weighting scheme in use. If any component returns an
     * undefined (NaN or infinite) score, NaN is returned.
     *
     * @param i      The variable whose score is needed.
     * @param parent The parent.
     * @return This score.
     */
    public double localScore(int i, int parent) {
        double sum = 0.0;

        for (int k = 0; k < this.scores.size(); k++) {
            double _score = this.scores.get(k).localScore(i, parent);

            if (Double.isNaN(_score) || Double.isInfinite(_score)) {
                return Double.NaN;
            }

            sum += this.effectiveWeights[k] * _score;
        }

        return sum;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the (aggregate) local node score, obtained as the weighted average of the local scores from each
     * component score, under the weighting scheme in use. If any component returns an undefined (NaN or infinite)
     * score, NaN is returned.
     */
    public double localScore(int i) {
        double sum = 0.0;

        for (int k = 0; k < this.scores.size(); k++) {
            double _score = this.scores.get(k).localScore(i);

            if (Double.isNaN(_score) || Double.isInfinite(_score)) {
                return Double.NaN;
            }

            sum += this.effectiveWeights[k] * _score;
        }

        return sum;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a judgment for FGES whether a score with the bump is for an effect edge.
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return scores.get(0).isEffectEdge(bump);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the variables.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns an effective sample size for the aggregate score, matching the weighting scheme in use. For
     * SAMPLE_UNIFORM this is the mean of the component sample sizes (the aggregate is an average of the component
     * scores, so the mean N is the scale at which its likelihood terms operate); for DATASET_WEIGHTED it is the
     * weighted mean of the equalized sample sizes, which is again the mean of the component sample sizes. In both
     * cases, then, the (rounded) mean sample size is returned. Note that this is a convention; consumers requiring the
     * total number of samples across datasets should sum the component sample sizes themselves.
     */
    @Override
    public int getSampleSize() {
        double sum = 0.0;

        for (int sampleSize : this.sampleSizes) {
            sum += sampleSize;
        }

        return (int) Math.round(sum / this.sampleSizes.length);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the max degree from the first score.
     */
    @Override
    public int getMaxDegree() {
        return scores.get(0).getMaxDegree();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the 'determines' judgment from the first score.
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        return scores.get(0).determines(z, y);
    }

    /**
     * Recomputes the effective (normalized) weights applied to the component scores, based on the weighting scheme,
     * the user-supplied weight schedule, and the component sample sizes.
     */
    private void recomputeEffectiveWeights() {
        int K = this.scores.size();
        this.effectiveWeights = new double[K];

        if (this.weightingScheme == WeightingScheme.SAMPLE_UNIFORM) {

            // A plain average; each sample counts equally, so each dataset's influence is proportional to its N.
            for (int k = 0; k < K; k++) {
                this.effectiveWeights[k] = 1.0 / K;
            }
        } else {

            // Equalize effective sample sizes by rescaling each component by (mean N) / N_k, then apply the
            // user-supplied schedule and normalize.
            double meanN = 0.0;

            for (int k = 0; k < K; k++) {
                meanN += this.sampleSizes[k];
            }

            meanN /= K;

            double total = 0.0;

            for (int k = 0; k < K; k++) {
                this.effectiveWeights[k] = this.weights[k] * (meanN / this.sampleSizes[k]);
                total += this.effectiveWeights[k];
            }

            for (int k = 0; k < K; k++) {
                this.effectiveWeights[k] /= total;
            }
        }
    }

    private double localScoreOneDataSet(int i, int[] parents, int index) {
        return this.scores.get(index).localScore(i, parents);
    }
}
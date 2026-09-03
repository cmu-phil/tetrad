/// ////////////////////////////////////////////////////////////////////////////
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

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.StatUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.cmu.tetrad.util.TMath.log;

/**
 * Calculates the basis function BIC score for a given dataset. This is a generalization of the Degenerate Gaussian
 * score by adding basis functions of the continuous variables and retains the function of the degenerate Gaussian for
 * discrete variables by adding indicator variables per category.
 * <p>
 * This version uses covariance matrices to calculate likelihoods.
 *
 * @author bryanandrews
 * @author josephramsey
 * @see DegenerateGaussianScore
 */
//@Deprecated(since = "7.9", forRemoval = false)
public class BasisFunctionBicScore implements Score {
    /**
     * A list containing nodes that represent the variables in the basis function score.
     */
    private final List<Node> variables;
    /**
     * A mapping used to store the embeddings of basis functions for continuous variables and indicator variables per
     * category for discrete variables. The key is an integer representing the index of the basis function variable or
     * indicator variable.
     */
    private final Map<Integer, List<Integer>> embedding;
    /**
     * An instance of SemBicScore used to compute the BIC (Bayesian Information Criterion) score for evaluating the fit
     * of a statistical model to a data set within the context of structural equation modeling (SEM).
     */
    private final SemBicScore bic;
    /**
     * The truncation limit the embedding was built with, kept so the null of the per-pair LRT can be re-estimated
     * from the raw data by permutation without holding the embedded data in memory.
     */
    private final int truncationLimit;
    /**
     * Whether continuous variables were rank-transformed to [-1, 1] before the Legendre embedding. When true the
     * null of the per-pair LRT is chi-square on its nominal degrees of freedom and the exact penalty-discount
     * calibration applies; when false it has a power-law tail and must be estimated by permutation. See
     * {@link Embedding#RANK_TRANSFORM}.
     */
    private final boolean rankTransform;
    /**
     * Represents the penalty discount factor used in the Basis Function BIC (Bayesian Information Criterion) score
     * calculations. This value modifies the penalty applied for model complexity in BIC scoring, allowing for
     * adjustments in the likelihood penalty term.
     */
    private double penaltyDiscount = 2;
    /**
     * When calculation the score for X = &lt;X1 = X, X2, X3,..., Xp&gt; use the equation for X1 only, if true;
     * otherwise, use equations for all of X1, X2,...,Xp.
     */
    private boolean doOneEquationOnly;

    /**
     * Constructs a BasisFunctionBicScore object with the specified parameters.
     *
     * @param dataSet         the data set on which the score is to be calculated.
     * @param truncationLimit the truncation limit of the basis.
     * @param lambda          Singularity lambda
     * @see StatUtils#basisFunctionValue(int, int, double)
     */
    public BasisFunctionBicScore(DataSet dataSet, int truncationLimit, double lambda) {
        this(dataSet, truncationLimit, lambda, false);
    }

    /**
     * Constructs a BasisFunctionBicScore object with the specified parameters.
     *
     * @param dataSet                the data set on which the score is to be calculated.
     * @param truncationLimit        the truncation limit of the basis.
     * @param lambda                 Singularity lambda
     * @param adaptiveBasisSelection if true, basis columns beyond the linear term that fail a BIC-crossing screen
     *                               against every other variable's embedded block are dropped from the embedding
     *                               before scoring, so that the effective truncation is chosen by the data and the
     *                               score converges as the truncation limit is raised past the data-supported order.
     *                               The screen is computed once from the full-sample embedded correlation matrix,
     *                               independently of any graph, so the pruned embedding is a single fixed embedding
     *                               and score equivalence is preserved.
     * @see Embedding#pruneUninformativeBasisColumns(DataSet, Map, edu.cmu.tetrad.data.ICovarianceMatrix)
     * @see StatUtils#basisFunctionValue(int, int, double)
     */
    public BasisFunctionBicScore(DataSet dataSet, int truncationLimit, double lambda, boolean adaptiveBasisSelection) {
        this(dataSet, truncationLimit, lambda, adaptiveBasisSelection, false);
    }

    /**
     * As {@link #BasisFunctionBicScore(DataSet, int, double, boolean)}, with the option to rank-transform continuous
     * variables to [-1, 1] before the Legendre embedding. See {@link Embedding#RANK_TRANSFORM} for why this is
     * recommended: it gives the score's LRT an exact chi-square null and removes leverage-driven spurious edges,
     * at the cost of modeling the copula rather than the joint distribution with its marginals.
     *
     * @param dataSet                the data
     * @param truncationLimit        the truncation limit of the basis
     * @param lambda                 the singularity lambda
     * @param adaptiveBasisSelection see the four-argument constructor
     * @param rankTransform          if true, rank-transform continuous variables before embedding
     */
    public BasisFunctionBicScore(DataSet dataSet, int truncationLimit, double lambda, boolean adaptiveBasisSelection,
                                 boolean rankTransform) {
        this.variables = dataSet.getVariables();
        this.truncationLimit = truncationLimit;
        this.rankTransform = rankTransform;

        // Using the Legendre basis.
        Embedding.EmbeddedData result = Embedding.getEmbeddedData(dataSet, truncationLimit, 1,
                rankTransform ? Embedding.RANK_TRANSFORM : 1);
        DataSet embeddedData = result.embeddedData();

        // We will zero out the correlations that are very close to zero.
        CorrelationMatrix correlationMatrix = new CorrelationMatrix(embeddedData);

        // With adaptive basis selection, higher-order basis columns that cannot produce a BIC-positive pairwise
        // association with any other variable's block are dropped from the embedding. The correlation matrix and
        // the underlying SemBicScore are left at full size; the pruned columns are simply never referenced.
        this.embedding = adaptiveBasisSelection
                ? Embedding.pruneUninformativeBasisColumns(dataSet, result.embedding(), correlationMatrix)
                : result.embedding();

        this.bic = new SemBicScore(correlationMatrix);
        this.bic.setPenaltyDiscount(penaltyDiscount);
        this.bic.setLambda(lambda);

        // We will be using a singularity lambda to avoid singularity exceptions.
        this.bic.setLambda(lambda);

        // We will be modifying the penalty term in the BIC score calculation, so we set the structure prior to 0.
        this.bic.setStructurePrior(0);

    }

    /**
     * Constructs a BasisFunctionBicScore that scores against an externally supplied embedding - a map from
     * variable index to the embedded columns to use for that variable. The correlation matrix and the underlying
     * SemBicScore are built at full size from this data set's own embedding, exactly as in the other constructors;
     * the supplied map only controls which columns are referenced. This is how a multi-data-set algorithm aiming
     * at a single common model (e.g. IMaGES) shares one basis-column decision across all of its data sets, so
     * that every data set scores the identical parameterization: with per-data-set adaptive selection, different
     * data sets can keep different columns, and the summed score then compares models whose effective embeddings
     * differ across data sets.
     *
     * <p>The supplied embedding must be consistent with this data set's own (unpruned) embedding layout: same
     * variable keys, and for each variable a subsequence of its unpruned columns. This holds automatically when
     * the map is derived (e.g. by union) from per-data-set decisions over data sets with the same variables,
     * types, and categories, as {@code Embedding.getEmbeddedData} lays such data sets out identically.
     *
     * @param dataSet         the data set on which the score is to be calculated. Must not be null.
     * @param truncationLimit the truncation limit of the basis. Must be a positive integer.
     * @param lambda          Singularity lambda. Must be non-negative.
     * @param embedding       the embedding (variable index to embedded columns) to score against. Must not be null.
     * @param rankTransform   whether to apply a rank transformation to the unit interval.
     */
    public BasisFunctionBicScore(DataSet dataSet, int truncationLimit, double lambda,
                                 Map<Integer, List<Integer>> embedding, boolean rankTransform) {
        this.variables = dataSet.getVariables();
        this.truncationLimit = truncationLimit;
        this.rankTransform = rankTransform;

        Embedding.EmbeddedData result = Embedding.getEmbeddedData(dataSet, truncationLimit, 1,
                rankTransform ? Embedding.RANK_TRANSFORM : 1);
        DataSet embeddedData = result.embeddedData();

        CorrelationMatrix correlationMatrix = new CorrelationMatrix(embeddedData);

        // Validate consistency with this data set's own layout.
        Map<Integer, List<Integer>> own = result.embedding();
        if (!own.keySet().equals(embedding.keySet())) {
            throw new IllegalArgumentException("Supplied embedding has different variable keys than this data "
                    + "set's embedding.");
        }
        for (Map.Entry<Integer, List<Integer>> e : embedding.entrySet()) {
            if (!own.get(e.getKey()).containsAll(e.getValue())) {
                throw new IllegalArgumentException("Supplied embedding references columns not present in this "
                        + "data set's embedding for variable index " + e.getKey() + ".");
            }
        }

        this.embedding = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> e : embedding.entrySet()) {
            this.embedding.put(e.getKey(), new ArrayList<>(e.getValue()));
        }

        this.bic = new SemBicScore(correlationMatrix);
        this.bic.setPenaltyDiscount(penaltyDiscount);
        this.bic.setLambda(lambda);
        this.bic.setStructurePrior(0);
    }

    /**
     * Computes the adaptive basis-column decision for a single data set: the embedding that
     * {@code new BasisFunctionBicScore(dataSet, truncationLimit, lambda, true)} would score against. Exposed so
     * that multi-data-set callers can combine per-data-set decisions (e.g. by union) into one common embedding.
     *
     * @param dataSet         the data set.
     * @param truncationLimit the truncation limit of the basis.
     * @return the pruned embedding for this data set.
     */
    public static Map<Integer, List<Integer>> adaptivePrunedEmbedding(DataSet dataSet, int truncationLimit) {
        Embedding.EmbeddedData result = Embedding.getEmbeddedData(dataSet, truncationLimit, 1, 1);
        CorrelationMatrix correlationMatrix = new CorrelationMatrix(result.embeddedData());
        return Embedding.pruneUninformativeBasisColumns(dataSet, result.embedding(), correlationMatrix);
    }

    /**
     * Computes the unpruned embedding layout for a single data set, for callers that need to combine or
     * validate basis-column decisions across data sets.
     *
     * @param dataSet         the data set.
     * @param truncationLimit the truncation limit of the basis.
     * @return the full (unpruned) embedding for this data set.
     */
    public static Map<Integer, List<Integer>> fullEmbedding(DataSet dataSet, int truncationLimit) {
        return Embedding.getEmbeddedData(dataSet, truncationLimit, 1, 1).embedding();
    }

    /**
     * Returns a copy of the embedding this score is scoring against (variable index to embedded columns).
     *
     * @return a copy of the embedding.
     */
    public Map<Integer, List<Integer>> getEmbedding() {
        Map<Integer, List<Integer>> copy = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> e : this.embedding.entrySet()) {
            copy.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return copy;
    }

    /**
     * Calculates the local score for a given node and its parent nodes.
     *
     * @param i       The index of the node whose score is being calculated.
     * @param parents The indices for the parent nodes of the given node.
     * @return The calculated local score as a double value.
     */
    public double localScore(int i, int... parents) {

        // Chain-rule decomposition over the embedded components of X = <X1, ..., Xp>:
        //
        //   BIC(X | Z) = BIC(X1 | Z) + BIC(X2 | Z, X1) + ... + BIC(Xp | Z, X1, ..., X{p-1}).
        //
        // Each component is conditioned on the earlier components of its own block, so the
        // summed log-likelihoods telescope to the joint Gaussian log-likelihood of the whole
        // embedded block given the parents' blocks. This is what makes the score a
        // (penalized) joint likelihood and hence score-equivalent: all DAGs in a Markov
        // equivalence class receive the same total score.
        //
        // Changes from the pre-2026-8 implementation: the conditioning step (B.add below) had
        // been commented out, so each component was scored against the parents' blocks only,
        // treating the components as conditionally independent given the parents. Since the
        // components are deterministic transforms of a single variable, their residuals given
        // the parents are generally correlated, and that diagonal-residual sum is not a joint
        // likelihood and is not score-equivalent across Markov-equivalent DAGs.
        List<Integer> A = new ArrayList<>(this.embedding.get(i));

        if (doOneEquationOnly) {
            A = A.subList(0, 1);
        }

        List<Integer> B = new ArrayList<>();
        for (int i_ : parents) {
            B.addAll(this.embedding.get(i_));
        }

        double sumLik = 0.0;
        int sumDof = 0;

        for (Integer i_ : A) {
            int[] parents_ = new int[B.size()];
            for (int i__ = 0; i__ < B.size(); i__++) {
                parents_[i__] = B.get(i__);
            }

            SemBicScore.LikelihoodResult result = this.bic.getLikelihoodAndDof(i_, parents_);

            sumLik += result.lik();
            sumDof += result.dof();

            B.add(i_);
        }

        return 2 * sumLik - penaltyDiscount * sumDof * log(getSampleSize());
    }

    /*
     * Calculates the difference in the local score when a node `x` is added to the set of parent nodes `z` for a node
     * `y`.
     *
     * @param x The index of the node to be added.
     * @param y The index of the node whose score difference is being calculated.
     * @param z The indices of the parent nodes of the node `y`.
     * @return The difference in the local score as a double value.
     */
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Retrieves the list of nodes representing the variables in the basis function score.
     *
     * @return a list containing the nodes that represent the variables in the basis function score.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Determines if the given bump value represents an effect edge.
     *
     * @param bump the bump value to be evaluated.
     * @return true if the bump is an effect edge, false otherwise.
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return this.bic.isEffectEdge(bump);
    }

    /**
     * Retrieves the sample size from the underlying BIC score component.
     *
     * @return the sample size as an integer
     */
    @Override
    public int getSampleSize() {
        return this.bic.getSampleSize();
    }

    /**
     * The number of embedded components per variable, in variable order: the basis-function count for a continuous
     * variable, the number of categories minus one for a discrete one (after any adaptive pruning). Adding a parent
     * x to y costs size[y] * size[x] degrees of freedom, so this is the input
     * {@link PenaltyDiscountCalibration#pairDofHistogram(int[])} needs to calibrate the penalty discount for this
     * score.
     *
     * @return One entry per variable.
     */
    public int[] embeddingBlockSizes() {
        int[] sizes = new int[this.variables.size()];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = Math.max(1, this.embedding.get(i).size());
        }
        return sizes;
    }

    /**
     * Estimates, by permutation on the raw data, the null distribution of this score's gain for adding one parent,
     * per degrees-of-freedom class. Re-embeds the data with this score's truncation limit and restricts to the
     * columns this score actually uses (after any adaptive pruning), so the statistic is the one the search sees.
     *
     * <p>Use the result with {@link PenaltyDiscountCalibration#penaltyDiscountForFalseDiscoveryRateFitted} rather
     * than the exact chi-square version: the embedded components of a variable are not jointly Gaussian, so the
     * LRT's null has the chi-square mean but roughly double the variance, and a chi-square calibration sets the
     * penalty discount far too low for this score.</p>
     *
     * @param rawData         The data this score was built from.
     * @param samplesPerClass Null draws per df class; a few hundred is plenty.
     * @param seed            Seed for reproducible calibration.
     * @return Map from df to fitted null.
     */
    public Map<Integer, PenaltyDiscountCalibration.NullFit> fitNullsByPermutation(DataSet rawData, int samplesPerClass,
                                                                                  long seed) {
        Embedding.EmbeddedData result = Embedding.getEmbeddedData(rawData, this.truncationLimit, 1,
                this.rankTransform ? Embedding.RANK_TRANSFORM : 1);
        DataSet embedded = result.embeddedData();
        int n = embedded.getNumRows(), total = embedded.getNumColumns();
        double[][] columns = new double[total][n];
        for (int j = 0; j < total; j++) {
            for (int i = 0; i < n; i++) columns[j][i] = embedded.getDouble(i, j);
        }
        List<int[]> blocks = new ArrayList<>();
        for (int v = 0; v < this.variables.size(); v++) {
            List<Integer> cols = this.embedding.get(v);
            int[] b = new int[cols.size()];
            for (int j = 0; j < b.length; j++) b[j] = cols.get(j);
            blocks.add(b);
        }
        return PenaltyDiscountCalibration.fitNullsByPermutation(columns, blocks, samplesPerClass, seed);
    }

    /**
     * Retrieves the maximum degree from the underlying BIC score component.
     *
     * @return the maximum degree as an integer.
     */
    @Override
    public int getMaxDegree() {
        return this.bic.getMaxDegree();
    }

    /**
     * Returns a string representation of the BasisFunctionBicScore object.
     *
     * @return A string detailing the degenerate Gaussian score penalty with the penalty discount formatted to two
     * decimal places.
     */
    @Override
    public String toString() {
        return "Basis Function BIC Score (BF-BIC)";
    }

    /**
     * The penalty discount in use, which may have been set automatically by the algcomparison wrapper.
     *
     * @return c.
     */
    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    /**
     * Sets the penalty discount value, which is used to adjust the penalty term in the BIC score calculation.
     *
     * @param penaltyDiscount The multiplier on the penalty term for this score.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
        this.bic.setPenaltyDiscount(penaltyDiscount);
    }

    /**
     * Whether continuous variables were rank-transformed before embedding.
     *
     * @return true if so.
     */
    public boolean isRankTransform() {
        return this.rankTransform;
    }

    /**
     * When calculation the score for X = &lt;X1 = X, X2, X3,..., Xp&gt; use the equation for X1 only, if true;
     * otherwise, use equations for all of X1, X2,...,Xp.
     *
     * @param doOneEquationOnly True if only the equation for X1 is to be used for X = X1,...,Xp.     *
     */
    public void setDoOneEquationOnly(boolean doOneEquationOnly) {
        this.doOneEquationOnly = doOneEquationOnly;
    }
}


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

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.search.score.EdgePriorScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.EdgePriors;

import java.util.Objects;

/**
 * A small, Python-friendly facade over the edge-prior machinery for <i>score-based</i> search,
 * intended to be driven from JPype. This is the companion to {@link EdgePriorFacade}: that class
 * wraps an independence test for PC, this one wraps a score for BOSS or FGES.
 *
 * <p>The intended flow, once per locus and then once per random-subspace repeat:
 *
 * <pre>
 *   // once per locus: annotation is n_snp x n_trait, standardised inside.
 *   EdgePriorScoreFacade prior = EdgePriorScoreFacade.fromAnnotation(
 *           snpNames, traitNames, annotation, tau);
 *
 *   // once per repeat: wrap whatever score you already build on the sub-covariance.
 *   SemBicScore base = new SemBicScore(subCov);
 *   base.setPenaltyDiscount(20.0);                          // your tuned value, as usual
 *   Score score = prior.wrapScore(base);
 *   Graph g = new PermutationSearch(new Boss(score)).search();   // or new Fges(score).search()
 * </pre>
 *
 * <p><b>Why this side is simpler than the PC side.</b> An independence test speaks in p-values
 * and a significance level, so the prior had to be translated into per-edge thresholds -- which
 * is where the anchoring at the tuned alpha, the convexity (Jensen) drift, and the mean-one
 * rescaling on {@link EdgePriorFacade} all came from. A score speaks in penalised log-likelihood,
 * which is the prior's native currency: an independent Bernoulli(p_ij) prior on each adjacency
 * contributes exactly 2 * beta_ij to the (2 log L)-scale score for each present edge, where
 * beta_ij = log(p_ij / (1 - p_ij)). Adding parent X to node Y is then accepted iff
 *
 * <pre>
 *   -n log(1 - r^2_{XY.S}) &gt; lambda log(n) - 2 beta_XY,
 * </pre>
 *
 * i.e. the prior is a per-edge rebate on the BIC penalty (Heckerman, Geiger and Chickering 1995,
 * specialised to per-pair log-odds). Nothing needs anchoring: beta = 0 adds zero, so tau = 0
 * reproduces the tuned score bit for bit with no further machinery. There is likewise no mean-one
 * rescaling here -- that step exists on the test side to hold the average significance level
 * fixed, and a score has no significance level to hold.
 *
 * <p><b>Two properties this preserves, and why they matter.</b> First, decomposability: in a DAG
 * each adjacency is oriented exactly one way, so beta_XY is charged to exactly one local score
 * (the child's) and nothing is double counted; BOSS's best-move step and FGES's forward and
 * backward phases need no modification. Second, score equivalence: the prior is a function of the
 * skeleton only, and all DAGs in a Markov equivalence class share a skeleton, so the prior sum is
 * constant within a class and the wrapped score remains score-equivalent -- the property both
 * algorithms' correctness arguments rely on. Both properties fail for priors on oriented edges,
 * which is why the underlying {@link EdgePriors} store rejects an asymmetric matrix. Genuinely
 * directional prior knowledge (a phenotype cannot cause a germline variant) belongs in the tier /
 * background-knowledge constraint, not here.
 *
 * <p><b>beta is not comparable across algorithms.</b> The rebate 2 * beta is measured against
 * each algorithm's own toll for adding an edge. For PC at significance level alpha the toll is
 * c0^2 with c0 = Phi^-1(1 - alpha/2), about 6.6 at alpha = 0.01; for a SEM-BIC score with penalty
 * discount lambda the toll is lambda * log(n), about 216 at lambda = 20 and n = 50,000. The same
 * beta = 1 therefore rebates roughly 30% of PC's toll but under 1% of the score's. If one tau is
 * to mean the same thing to PC and to BOSS/FGES -- for instance so that all three algorithms can
 * share the x-axis of a sweep -- build the prior with
 * {@link #fromAnnotationTollScaled(String[], String[], double[][], double, double, int)}, which
 * expresses strength as a fraction of the score's own toll. One can share the toll-fraction scale
 * or the raw log-odds scale across algorithms, but not both.
 *
 * <p><b>Threading.</b> Instances are immutable and safe to share across parallel repeats. Build
 * one facade per locus and hand the same object to every worker; the only per-repeat work is
 * {@link #wrapScore(Score)}, which restricts the locus prior to that repeat's variables.
 *
 * @author josephramsey
 * @see EdgePriorFacade
 * @see EdgePriorScore
 * @see EdgePriors
 */
public final class EdgePriorScoreFacade {

    /**
     * The locus prior as symmetric log-odds, keyed by variable name. Immutable; restricted per
     * repeat by {@link #wrapScore(Score)}.
     */
    private final EdgePriors logOdds;

    /**
     * Constructs a facade around an already-built log-odds store. Private: use the static
     * factories, which perform the standardisation and validation.
     *
     * @param logOdds The locus prior, holding {@link EdgePriors.Semantics#LOG_ODDS}.
     */
    private EdgePriorScoreFacade(EdgePriors logOdds) {
        this.logOdds = logOdds;
    }

    /**
     * Builds a locus score prior from a per-(SNP, trait) annotation, on the raw log-odds scale:
     * beta_jk = tau * standardised(annotation)_jk.
     *
     * <p>The annotation is standardised to mean zero and unit standard deviation over its finite
     * entries before scaling by tau. Standardising to mean zero is what keeps tau = 0 an honest
     * baseline and prevents a mostly-positive biological annotation from uniformly rebating every
     * edge's penalty, which would just densify the graph and confound "the prior helped" with
     * "the penalty was lowered". Non-finite entries (use {@code Double.NaN} for a SNP-trait pair
     * with no annotation) contribute no prior and do not enter the standardisation.
     *
     * <p>Note the shape: the annotation is indexed by (SNP, trait), not by SNP alone, so the
     * prior can support a SNP's edge to one trait without supporting its edge to another. A
     * trait-agnostic prior -- one score per SNP applied to every trait -- protects or suppresses
     * all of a SNP's trait edges together and thereby biases mechanism classification toward
     * pleiotropy; collapse to one score per SNP only if the annotation genuinely is
     * trait-agnostic, and say so if you do.
     *
     * <p>SNP-SNP and trait-trait pairs are not represented in the annotation and carry no prior:
     * their beta is zero and the wrapped score treats them exactly as the unwrapped score does.
     *
     * @param snpNames   Names of the SNP nodes, length n_snp. These must match, byte for byte,
     *                   the variable names of the scores this facade will later wrap.
     * @param traitNames Names of the trait nodes, length n_trait. Same matching requirement.
     * @param annotation An n_snp x n_trait matrix of exogenous prior scores; higher means more
     *                   prior support for that SNP-trait edge, {@code NaN} means no information.
     *                   Do not pre-standardise; this method does it.
     * @param tau        Prior strength on the raw log-odds scale. tau = 0 reproduces the
     *                   unmodified score exactly. Negative tau inverts the annotation.
     * @return The facade.
     * @throws IllegalArgumentException If the annotation's shape disagrees with the name arrays,
     *                                  if tau is not finite, or if the names contain duplicates
     *                                  or empty strings (checked by the underlying store).
     * @throws NullPointerException     If any argument is null.
     */
    public static EdgePriorScoreFacade fromAnnotation(String[] snpNames, String[] traitNames,
                                                      double[][] annotation, double tau) {
        double[][] beta = betaFromAnnotation(snpNames, traitNames, annotation, tau);
        String[] names = concat(snpNames, traitNames);
        EdgePriors lo = EdgePriors.fromMatrix(
                java.util.Arrays.asList(names), beta, EdgePriors.Semantics.LOG_ODDS);
        return new EdgePriorScoreFacade(lo);
    }

    /**
     * Builds a locus score prior on the toll-fraction scale, so that one tau is comparable across
     * PC, BOSS, and FGES despite their very different operating points.
     *
     * <p>Here beta_jk = tau * standardised(annotation)_jk * lambda * log(n) / 2, which makes
     * tau * s_jk the <i>fraction of the score's own penalty toll</i> rebated for that edge:
     * tau * s = 0 is the tuned point, and tau * s = 1 rebates the whole toll, making the edge
     * free to add -- the required-knowledge limit. Since the corresponding fraction on the PC
     * side is measured against PC's toll c0^2, a given tau then buys the same proportional
     * rebate from every algorithm, which is what a shared sweep axis requires.
     *
     * <p>Pass the same lambda (penalty discount) and n (sample size) the wrapped score itself
     * uses. These are not a new dependency: the score already contains both, so -- unlike the
     * anchoring argument on the test side -- no summary-statistic ambiguity about n is introduced
     * here that the score did not already have.
     *
     * @param snpNames   Names of the SNP nodes, length n_snp; must match the scores' variable
     *                   names byte for byte.
     * @param traitNames Names of the trait nodes, length n_trait; same requirement.
     * @param annotation An n_snp x n_trait matrix of exogenous prior scores; higher means more
     *                   support, {@code NaN} means no information. Do not pre-standardise.
     * @param tau        Prior strength on the toll-fraction scale; tau * s = 1 is required
     *                   knowledge, tau = 0 reproduces the unmodified score exactly.
     * @param lambda     The penalty discount of the score this prior will wrap.
     * @param n          The sample size the score uses.
     * @return The facade.
     * @throws IllegalArgumentException If shapes disagree, tau is not finite, lambda is not
     *                                  positive and finite, or n is less than 2.
     * @throws NullPointerException     If any array argument is null.
     */
    public static EdgePriorScoreFacade fromAnnotationTollScaled(String[] snpNames, String[] traitNames,
                                                                double[][] annotation, double tau,
                                                                double lambda, int n) {
        if (!(lambda > 0.0) || !Double.isFinite(lambda)) {
            throw new IllegalArgumentException("lambda must be positive and finite: " + lambda);
        }

        if (n < 2) {
            throw new IllegalArgumentException("n must be at least 2: " + n);
        }

        double scale = tau * lambda * Math.log(n) / 2.0;
        double[][] beta = betaFromAnnotation(snpNames, traitNames, annotation, scale);
        String[] names = concat(snpNames, traitNames);
        EdgePriors lo = EdgePriors.fromMatrix(
                java.util.Arrays.asList(names), beta, EdgePriors.Semantics.LOG_ODDS);
        return new EdgePriorScoreFacade(lo);
    }

    /**
     * Builds a locus score prior directly from a symmetric matrix of prior log-odds, for callers
     * who want to construct beta themselves rather than derive it from an annotation. Prefer
     * {@link #fromAnnotation(String[], String[], double[][], double)} unless there is a reason
     * not to: this method performs no standardisation, so the caller is responsible for keeping
     * the zero point meaningful.
     *
     * @param names Variable names indexing both dimensions of {@code beta}, in the same order;
     *              must match the scores' variable names byte for byte.
     * @param beta  A symmetric names.length x names.length matrix of prior log-odds. Zero means
     *              no prior for that pair. beta_ij = log(p_ij / (1 - p_ij)) for prior adjacency
     *              probability p_ij.
     * @return The facade.
     * @throws IllegalArgumentException If the matrix is ragged, non-square, asymmetric, contains
     *                                  non-finite entries, or the names contain duplicates
     *                                  (checked by the underlying store).
     * @throws NullPointerException     If either argument is null.
     */
    public static EdgePriorScoreFacade fromLogOdds(String[] names, double[][] beta) {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(beta, "beta");

        EdgePriors lo = EdgePriors.fromMatrix(
                java.util.Arrays.asList(names), beta, EdgePriors.Semantics.LOG_ODDS);
        return new EdgePriorScoreFacade(lo);
    }

    /**
     * Wraps a score built on a repeat's sub-covariance, restricting the locus prior to that
     * repeat's variables. This is the only per-repeat call, and it is cheap; the facade itself is
     * shared across repeats.
     *
     * <p>The returned score adds 2 * beta_XY inside {@code localScore} and
     * {@code localScoreDiff} and delegates everything else, so it can be handed to BOSS or FGES
     * exactly where the unwrapped score would have gone. With every beta zero (tau = 0) the
     * wrapped score's decisions are identical to the delegate's.
     *
     * @param subsetScore The score for this repeat, e.g. a {@code SemBicScore} on the principal
     *                    submatrix over the sampled SNPs and the traits, with its penalty
     *                    discount already set. Its variable names must be a subset of the names
     *                    this facade was built with.
     * @return A prior-adjusted {@link Score}, ready to hand to BOSS or FGES.
     * @throws NullPointerException If {@code subsetScore} is null.
     */
    public Score wrapScore(Score subsetScore) {
        Objects.requireNonNull(subsetScore, "subsetScore");
        EdgePriors restricted = this.logOdds.restrictTo(subsetScore.getVariables());
        return new EdgePriorScore(subsetScore, restricted);
    }

    /**
     * Returns the number of variable pairs carrying a non-neutral prior at the locus. For a full
     * annotation this is n_snp * n_trait, less any {@code NaN} entries; it is zero at tau = 0 and
     * zero for a degenerate (constant) annotation. A quick sanity check from Python that the
     * annotation actually attached: if this is unexpectedly zero, the usual causes are tau = 0, a
     * constant annotation, or -- if it is nonzero here but the graphs still match tau = 0 -- a
     * name mismatch between the annotation and the covariance variables, which this count cannot
     * detect because it is computed before any test or score is seen.
     *
     * @return That count.
     */
    public int numPriorPairs() {
        return this.logOdds.size();
    }

    /**
     * Returns a string representation of this facade, reporting the number of pairs carrying a
     * prior.
     *
     * @return This string.
     */
    public String toString() {
        return "EdgePriorScoreFacade[priorPairs=" + this.logOdds.size() + "]";
    }

    // ---- private helpers ---------------------------------------------------------------------
    //
    // These mirror the private logic in EdgePriorFacade. Duplicated rather than shared so that
    // this class stands alone and the already-tested test-side path is not disturbed.

    /**
     * Concatenates SNP names and trait names into the single name list the underlying store is
     * indexed by, SNPs first.
     */
    private static String[] concat(String[] snpNames, String[] traitNames) {
        Objects.requireNonNull(snpNames, "snpNames");
        Objects.requireNonNull(traitNames, "traitNames");

        String[] names = new String[snpNames.length + traitNames.length];
        System.arraycopy(snpNames, 0, names, 0, snpNames.length);
        System.arraycopy(traitNames, 0, names, snpNames.length, traitNames.length);
        return names;
    }

    /**
     * Standardises the finite annotation entries to mean zero and unit SD, scales by
     * {@code scale}, and lays the result into the SNP-trait blocks of a full symmetric beta
     * matrix, leaving SNP-SNP and trait-trait blocks at zero. If the finite entries are constant
     * or fewer than two (degenerate SD), every beta is zero.
     */
    private static double[][] betaFromAnnotation(String[] snpNames, String[] traitNames,
                                                 double[][] annotation, double scale) {
        Objects.requireNonNull(snpNames, "snpNames");
        Objects.requireNonNull(traitNames, "traitNames");
        Objects.requireNonNull(annotation, "annotation");

        int nSnp = snpNames.length;
        int nTrait = traitNames.length;

        if (!Double.isFinite(scale)) {
            throw new IllegalArgumentException("tau (or its scaling) must be finite: " + scale);
        }

        if (annotation.length != nSnp) {
            throw new IllegalArgumentException("annotation has " + annotation.length
                    + " rows but " + nSnp + " SNP names were given.");
        }

        for (int j = 0; j < nSnp; j++) {
            if (annotation[j].length != nTrait) {
                throw new IllegalArgumentException("annotation row " + j + " has length "
                        + annotation[j].length + "; expected " + nTrait + ".");
            }
        }

        // Standardise finite entries to mean zero, unit SD.
        double sum = 0.0;
        long count = 0;

        for (double[] row : annotation) {
            for (double v : row) {
                if (Double.isFinite(v)) {
                    sum += v;
                    count++;
                }
            }
        }

        double mean = (count > 0) ? sum / count : 0.0;
        double ss = 0.0;

        for (double[] row : annotation) {
            for (double v : row) {
                if (Double.isFinite(v)) {
                    ss += (v - mean) * (v - mean);
                }
            }
        }

        double sd = (count > 1) ? Math.sqrt(ss / (count - 1)) : 0.0;
        boolean degenerate = !(sd > 0.0);

        int p = nSnp + nTrait;
        double[][] beta = new double[p][p];

        for (int j = 0; j < nSnp; j++) {
            for (int k = 0; k < nTrait; k++) {
                double a = annotation[j][k];
                double b = (degenerate || !Double.isFinite(a)) ? 0.0 : scale * (a - mean) / sd;
                beta[j][nSnp + k] = b;
                beta[nSnp + k][j] = b;
            }
        }

        return beta;
    }
}

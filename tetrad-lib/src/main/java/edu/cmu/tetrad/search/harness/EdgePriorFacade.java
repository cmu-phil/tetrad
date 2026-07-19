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

import edu.cmu.tetrad.search.test.EdgePriorTest;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.EdgePriors;

import java.util.Objects;

/**
 * A small, Python-friendly facade over the edge-prior machinery, intended to be driven from JPype.
 *
 * <p>The intended flow, once per locus and then once per random-subspace repeat:
 *
 * <pre>
 *   // once per locus: annotation is n_snp x n_trait, standardised and anchored inside.
 *   EdgePriorFacade prior = EdgePriorFacade.fromAnnotation(
 *           snpNames, traitNames, annotation, tau, alpha);
 *
 *   // once per repeat: wrap whatever test you already build on the sub-covariance.
 *   IndependenceTest test = prior.wrap(new IndTestFisherZ(subCov, alpha));
 *   Graph g = new Pc(test).search();
 * </pre>
 *
 * <p>Everything the last few design rounds settled is baked in, so the caller cannot get it wrong:
 *
 * <ul>
 *   <li>The prior is <b>anchored</b> at the tuned alpha, so tau = 0 reproduces the unmodified
 *       pipeline exactly and the construction needs no sample size n.
 *   <li>The annotation is <b>standardised to mean zero</b>, and the resulting weights are
 *       <b>rescaled to mean one</b>, so a non-zero tau redistributes significance across edges
 *       rather than loosening the test globally. {@link #meanAlpha()} reports the average
 *       threshold for the referee table.
 *   <li>The annotation is indexed by <b>(SNP, trait)</b>, not by SNP alone, so a prior can say
 *       different things about a SNP's effect on different traits. A trait-agnostic prior biases
 *       mechanism classification toward pleiotropy; this facade does not force that on you.
 *   <li>The per-repeat {@link #wrap(IndependenceTest)} call <b>restricts</b> the locus prior to the
 *       repeat's variables automatically, which is the one step that would otherwise throw.
 * </ul>
 *
 * <p>Instances are immutable and safe to share across parallel repeats.
 */
public final class EdgePriorFacade {

    private final EdgePriors weights;
    private final double alpha;
    private final double meanAlpha;

    private EdgePriorFacade(EdgePriors weights, double alpha, double meanAlpha) {
        this.weights = weights;
        this.alpha = alpha;
        this.meanAlpha = meanAlpha;
    }

    /**
     * Builds a locus prior from a per-(SNP, trait) annotation.
     *
     * <p>The annotation is standardised to mean zero and unit standard deviation over its finite
     * entries; non-finite entries (use {@code Double.NaN} for a SNP-trait pair you have no
     * annotation for) contribute no prior. The standardised score is scaled by {@code tau} to give
     * prior log-odds, anchored at {@code alpha}, and rescaled to mean one.
     *
     * @param snpNames    Names of the SNP nodes, length n_snp. Must match the node names the tests
     *                    will use.
     * @param traitNames  Names of the trait nodes, length n_trait.
     * @param annotation  An n_snp x n_trait matrix of exogenous prior scores. Higher means more
     *                    prior support for that SNP-trait edge. {@code NaN} means "no information".
     * @param tau         Prior strength. {@code tau = 0} reproduces the unmodified pipeline exactly.
     * @param alpha       The tuned significance level to anchor at.
     * @return The facade.
     * @throws IllegalArgumentException If shapes disagree, names are empty or duplicated, or alpha
     *                                  is out of range.
     */
    public static EdgePriorFacade fromAnnotation(String[] snpNames, String[] traitNames,
                                                 double[][] annotation, double tau, double alpha) {
        Objects.requireNonNull(snpNames, "snpNames");
        Objects.requireNonNull(traitNames, "traitNames");
        Objects.requireNonNull(annotation, "annotation");

        int nSnp = snpNames.length;
        int nTrait = traitNames.length;

        if (!(alpha > 0.0 && alpha < 1.0)) {
            throw new IllegalArgumentException("alpha must be in (0, 1): " + alpha);
        }

        if (!Double.isFinite(tau)) {
            throw new IllegalArgumentException("tau must be finite: " + tau);
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

        // Standardise the finite entries to mean zero, unit SD.
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
        boolean degenerate = !(sd > 0.0);   // all-equal or too few: no information

        // Assemble the full (n_snp + n_trait) log-odds matrix. SNP-SNP and trait-trait stay 0.
        int p = nSnp + nTrait;
        String[] names = new String[p];
        System.arraycopy(snpNames, 0, names, 0, nSnp);
        System.arraycopy(traitNames, 0, names, nSnp, nTrait);

        double[][] beta = new double[p][p];

        for (int j = 0; j < nSnp; j++) {
            for (int k = 0; k < nTrait; k++) {
                double a = annotation[j][k];
                double b = (degenerate || !Double.isFinite(a)) ? 0.0 : tau * (a - mean) / sd;
                beta[j][nSnp + k] = b;
                beta[nSnp + k][j] = b;
            }
        }

        return fromLogOdds(names, beta, alpha, true);
    }

    /**
     * Builds a locus prior directly from a symmetric matrix of prior log-odds, for callers who
     * want to construct beta themselves. Prefer {@link #fromAnnotation} unless you have a reason
     * not to.
     *
     * @param names             Variable names indexing both dimensions of {@code beta}.
     * @param beta              A symmetric matrix of prior log-odds. Zero means no prior for that
     *                          pair.
     * @param alpha             The tuned significance level to anchor at.
     * @param normalizeToMeanOne Whether to rescale the anchored weights to mean one. Leave true
     *                          unless you are deliberately not conserving the significance budget.
     * @return The facade.
     * @throws IllegalArgumentException If the matrix is not symmetric, shapes disagree, or alpha is
     *                                  out of range.
     */
    public static EdgePriorFacade fromLogOdds(String[] names, double[][] beta, double alpha,
                                              boolean normalizeToMeanOne) {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(beta, "beta");

        if (!(alpha > 0.0 && alpha < 1.0)) {
            throw new IllegalArgumentException("alpha must be in (0, 1): " + alpha);
        }

        EdgePriors logOdds = EdgePriors.fromMatrix(
                java.util.Arrays.asList(names), beta, EdgePriors.Semantics.LOG_ODDS);

        EdgePriors anchored = logOdds.toWeightsAnchoredAtAlpha(alpha);
        double meanAlpha = anchored.meanWeight() * alpha;   // before normalisation: the drift

        EdgePriors weights = normalizeToMeanOne ? anchored.normalizedToMeanOne() : anchored;

        return new EdgePriorFacade(weights, alpha, meanAlpha);
    }

    /**
     * Wraps a test built on a repeat's sub-covariance, restricting the locus prior to that repeat's
     * variables. This is the only per-repeat call.
     *
     * @param subsetTest The test for this repeat, e.g. an {@code IndTestFisherZ} on the principal
     *                   submatrix over the sampled SNPs and the traits.
     * @return A prior-adjusted test, ready to hand to PC.
     */
    public IndependenceTest wrap(IndependenceTest subsetTest) {
        Objects.requireNonNull(subsetTest, "subsetTest");
        EdgePriors restricted = this.weights.restrictTo(subsetTest.getVariables());
        return new EdgePriorTest(subsetTest, restricted, this.alpha);
    }

    /**
     * Returns the base significance level this prior is anchored at.
     *
     * @return The alpha.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Returns the average significance level over the pairs carrying a prior, <i>before</i> the
     * mean-one rescaling. This is the density-drift diagnostic: report {@code meanAlpha() / alpha}
     * at each tau in a sweep. It is 1 at tau = 0 and grows above 1 as tau increases, which is
     * exactly the effect the mean-one rescaling then removes.
     *
     * @return The mean pre-normalisation alpha_ij.
     */
    public double meanAlpha() {
        return this.meanAlpha;
    }

    /**
     * Returns the number of variable pairs carrying a non-neutral prior at the locus.
     *
     * @return That count.
     */
    public int numPriorPairs() {
        return this.weights.size();
    }

    /**
     * Returns a string representation of this facade.
     *
     * @return This string.
     */
    public String toString() {
        return "EdgePriorFacade[alpha=" + this.alpha + ", priorPairs=" + this.weights.size()
                + ", meanAlpha(pre-norm)=" + this.meanAlpha + "]";
    }
}

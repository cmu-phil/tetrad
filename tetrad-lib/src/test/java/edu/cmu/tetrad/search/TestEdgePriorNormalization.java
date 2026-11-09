package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.harness.AnnotationNormalization;
import edu.cmu.tetrad.search.harness.EdgePriorScoreFacade;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Matrix;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the selectable annotation-normalisation modes on the edge-prior facades.
 *
 * <p>These tests reference {@link AnnotationNormalization} and the mode-taking factories and so
 * do not compile against a pre-patch jar, which is the required fail-on-unpatched behaviour; the
 * behavioural assertions then pin down what the modes do. The central fact under test: the legacy
 * pooled z-score is unbounded, so under toll scaling at tau = 0.5 an annotation outlier fully
 * rebates the BIC penalty and forces its edge into the graph, while the bounded modes keep every
 * adjustment in [-1, 1] and cannot force an edge at any tau below 1.
 *
 * @author josephramsey
 */
public class TestEdgePriorNormalization {

    private static final String[] SNPS = {"snp1", "snp2", "snp3", "snp4", "snp5"};
    private static final String[] TRAITS = {"t1", "t2"};
    private static final double LAMBDA = 2.0;
    private static final int N = 1000;

    /**
     * An annotation whose first trait column carries a huge outlier weight on snp5. Pooled
     * z-scoring gives that entry a z of about 2.84, which at tau = 0.5 is beyond the
     * required-knowledge limit; centred log min-max keeps it at about 0.71.
     */
    private static double[][] outlierAnnotation() {
        return new double[][]{
                {1.0, 1.0},
                {2.0, 2.0},
                {3.0, 3.0},
                {4.0, 4.0},
                {1000.0, 5.0}};
    }

    /**
     * An identity covariance over the five SNPs and two traits: every pair is independent, so
     * without a prior no edge is worth adding at any positive penalty.
     */
    private static Score identityScore() {
        List<Node> nodes = new ArrayList<>();

        for (String name : SNPS) {
            nodes.add(new ContinuousVariable(name));
        }

        for (String name : TRAITS) {
            nodes.add(new ContinuousVariable(name));
        }

        int p = nodes.size();
        double[][] m = new double[p][p];

        for (int i = 0; i < p; i++) {
            m[i][i] = 1.0;
        }

        SemBicScore score = new SemBicScore(new CovarianceMatrix(nodes, new Matrix(m), N));
        score.setPenaltyDiscount(LAMBDA);
        return score;
    }

    /**
     * The bounded modes produce adjustments with zero mean per trait column, bounded in [-1, 1],
     * with spread exactly 1 for the min-max modes, outlier or no outlier.
     */
    @Test
    public void testBoundedModesCenteredAndBounded() {
        double[][] ann = outlierAnnotation();

        for (AnnotationNormalization mode : new AnnotationNormalization[]{
                AnnotationNormalization.CENTERED_MIN_MAX,
                AnnotationNormalization.CENTERED_LOG_MIN_MAX}) {
            double[][] a = mode.adjust(ann);

            for (int k = 0; k < TRAITS.length; k++) {
                double sum = 0.0;
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;

                for (int j = 0; j < SNPS.length; j++) {
                    double v = a[j][k];
                    assertTrue(mode + " produced NaN where data exists", !Double.isNaN(v));
                    assertTrue(mode + " out of bounds: " + v, v >= -1.0 && v <= 1.0);
                    sum += v;
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }

                assertEquals(mode + " column " + k + " not mean-centred", 0.0,
                        sum / SNPS.length, 1e-12);
                assertEquals(mode + " column " + k + " spread is not 1", 1.0, max - min, 1e-12);
            }
        }

        // Robust clipped: bounded, zero median, the outlier saturates at the clip.
        double[][] r = AnnotationNormalization.ROBUST_CLIPPED.adjust(ann);

        for (int j = 0; j < SNPS.length; j++) {
            for (int k = 0; k < TRAITS.length; k++) {
                assertTrue("ROBUST_CLIPPED out of bounds: " + r[j][k],
                        r[j][k] >= -1.0 && r[j][k] <= 1.0);
            }
        }

        assertEquals("outlier should saturate at the clip", 1.0, r[4][0], 1e-12);
    }

    /**
     * Passing Z_SCORE to the mode-taking factories reproduces the legacy factories exactly: same
     * wrapped-score decisions, bit for bit.
     */
    @Test
    public void testZScoreModeMatchesLegacy() throws Exception {
        double[][] ann = outlierAnnotation();

        EdgePriorScoreFacade legacy = EdgePriorScoreFacade.fromAnnotationTollScaled(
                SNPS, TRAITS, ann, 0.3, LAMBDA, N);
        EdgePriorScoreFacade mode = EdgePriorScoreFacade.fromAnnotationTollScaled(
                SNPS, TRAITS, ann, 0.3, LAMBDA, N, AnnotationNormalization.Z_SCORE);

        Score a = legacy.wrapScore(identityScore());
        Score b = mode.wrapScore(identityScore());

        for (int snp = 0; snp < SNPS.length; snp++) {
            for (int trait = SNPS.length; trait < SNPS.length + TRAITS.length; trait++) {
                assertEquals(a.localScoreDiff(snp, trait), b.localScoreDiff(snp, trait), 0.0);
            }
        }
    }

    /**
     * The headline failure mode and its fix. At tau = 0.5 the legacy z-score puts the outlier
     * pair beyond the required-knowledge limit -- the wrapped score adds snp5--t1 even though the
     * data say the pair is independent -- while centred log min-max keeps every pair below the
     * limit and the edge stays out.
     */
    @Test
    public void testLegacySaturatesWhereBoundedDoesNot() throws Exception {
        double[][] ann = outlierAnnotation();
        double tau = 0.5;

        EdgePriorScoreFacade legacy = EdgePriorScoreFacade.fromAnnotationTollScaled(
                SNPS, TRAITS, ann, tau, LAMBDA, N, AnnotationNormalization.Z_SCORE);
        EdgePriorScoreFacade bounded = EdgePriorScoreFacade.fromAnnotationTollScaled(
                SNPS, TRAITS, ann, tau, LAMBDA, N, AnnotationNormalization.CENTERED_LOG_MIN_MAX);

        assertTrue("legacy z-score should cross the required-knowledge limit here",
                legacy.numForcedPairs(LAMBDA, N) > 0);
        assertEquals("bounded mode at tau < 1 cannot cross the required-knowledge limit",
                0, bounded.numForcedPairs(LAMBDA, N));

        // Behavioural check on independent data: snp5 (index 4) and t1 (index 5).
        Score legacyScore = legacy.wrapScore(identityScore());
        Score boundedScore = bounded.wrapScore(identityScore());

        assertTrue("forced edge should be added despite independence",
                legacyScore.localScoreDiff(4, 5) > 0.0);
        assertTrue("bounded prior should not add an edge the data reject",
                boundedScore.localScoreDiff(4, 5) < 0.0);
    }

    /**
     * NONE passes precomputed adjustments through with no re-standardisation: the prior added to
     * each local decision is exactly tau * a * lambda * log(n), not a rescaled version of it.
     * This is the guard against the silent-restandardisation pitfall, where bounded adjustments
     * computed in Python were fed to a factory that z-scored them back to unit variance.
     */
    @Test
    public void testNonePassesThroughUnchanged() throws Exception {
        double tau = 0.5;
        double[][] a = {
                {0.25, Double.NaN},
                {-0.25, 0.0},
                {0.5, 0.0},
                {-0.5, 0.0},
                {0.0, 0.0}};

        EdgePriorScoreFacade facade = EdgePriorScoreFacade.fromAnnotationTollScaled(
                SNPS, TRAITS, a, tau, LAMBDA, N, AnnotationNormalization.NONE);

        Score base = identityScore();
        Score wrapped = facade.wrapScore(identityScore());

        for (int snp = 0; snp < SNPS.length; snp++) {
            for (int k = 0; k < TRAITS.length; k++) {
                int trait = SNPS.length + k;
                double adj = Double.isNaN(a[snp][k]) ? 0.0 : a[snp][k];
                double expected = base.localScoreDiff(snp, trait)
                        + tau * adj * LAMBDA * Math.log(N);
                assertEquals("NONE re-standardised a precomputed adjustment",
                        expected, wrapped.localScoreDiff(snp, trait), 1e-10);
            }
        }

        // Out-of-range precomputed adjustments are not rejected, but they are counted.
        double[][] forced = {
                {2.5, 0.0}, {0.0, 0.0}, {0.0, 0.0}, {0.0, 0.0}, {0.0, 0.0}};
        EdgePriorScoreFacade forcing = EdgePriorScoreFacade.fromAnnotationTollScaled(
                SNPS, TRAITS, forced, tau, LAMBDA, N, AnnotationNormalization.NONE);
        assertEquals(1, forcing.numForcedPairs(LAMBDA, N));
    }

    /**
     * Missing annotations and degenerate columns receive neutral adjustments: NaN entries carry
     * no prior, a constant column carries no prior, and an entirely constant annotation
     * reproduces the unmodified search.
     */
    @Test
    public void testNeutralHandling() {
        // Column t2 is constant; snp3-t1 is missing.
        double[][] ann = {
                {1.0, 7.0},
                {2.0, 7.0},
                {Double.NaN, 7.0},
                {4.0, 7.0},
                {8.0, 7.0}};

        EdgePriorScoreFacade facade = EdgePriorScoreFacade.fromAnnotation(
                SNPS, TRAITS, ann, 0.5, AnnotationNormalization.CENTERED_LOG_MIN_MAX);

        // Only the four informative t1 entries carry a prior.
        assertEquals(4, facade.numPriorPairs());

        // A fully constant annotation is degenerate under every mode: no prior at all.
        double[][] constant = new double[SNPS.length][TRAITS.length];

        for (double[] row : constant) {
            java.util.Arrays.fill(row, 3.0);
        }

        for (AnnotationNormalization mode : new AnnotationNormalization[]{
                AnnotationNormalization.Z_SCORE,
                AnnotationNormalization.CENTERED_MIN_MAX,
                AnnotationNormalization.CENTERED_LOG_MIN_MAX,
                AnnotationNormalization.ROBUST_CLIPPED}) {
            assertEquals(mode + " should be neutral for a constant annotation", 0,
                    EdgePriorScoreFacade.fromAnnotation(SNPS, TRAITS, constant, 0.5, mode)
                            .numPriorPairs());
        }
    }
}

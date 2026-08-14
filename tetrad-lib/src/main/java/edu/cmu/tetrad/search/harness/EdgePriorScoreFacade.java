package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.search.score.EdgePriorScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.EdgePriors;

import java.util.Objects;

/**
 * The score-side companion to EdgePriorFacade, for BOSS and FGES.
 *
 * Simpler than the test side: a score speaks the prior's native language -- the Bernoulli
 * prior on adjacencies is just a per-edge rebate on the BIC penalty -- so there is no
 * anchoring, no alpha, no p-value weighting and no mean-one rescaling. beta = 0 is an exact
 * no-op (the wrapper adds zero), so tau = 0 reproduces the tuned score bit for bit.
 *
 * beta is NOT comparable across algorithms. The rebate 2*beta is measured against each
 * algorithm's toll: PC's toll is c0^2 (~6.6 at alpha=0.01); a score's is lambda*log(n)
 * (~216 at lambda=20, n=50000). Same beta rebates ~30% of PC's toll but under 1% of the
 * score's. For a tau comparable across algorithms use fromAnnotationTollScaled.
 *
 * Instances are immutable and safe to share across parallel repeats.
 */
public final class EdgePriorScoreFacade {

    private final EdgePriors logOdds;

    private EdgePriorScoreFacade(EdgePriors logOdds) {
        this.logOdds = logOdds;
    }

    /** beta = tau * standardised(annotation), on the raw log-odds scale. */
    public static EdgePriorScoreFacade fromAnnotation(String[] snpNames, String[] traitNames,
                                                      double[][] annotation, double tau) {
        double[][] beta = betaFromAnnotation(snpNames, traitNames, annotation, tau);
        String[] names = concat(snpNames, traitNames);
        EdgePriors lo = EdgePriors.fromMatrix(
                java.util.Arrays.asList(names), beta, EdgePriors.Semantics.LOG_ODDS);
        return new EdgePriorScoreFacade(lo);
    }

    /**
     * beta_ij = tau * s_ij * lambda * log(n) / 2, so tau*s_ij is the fraction of the score's
     * penalty toll rebated for that edge. tau*s = 1 rebates the whole toll (required
     * knowledge); tau*s = 0 is the tuned point. Pass the same lambda and n the score uses.
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

    /** Direct construction from a symmetric log-odds matrix. */
    public static EdgePriorScoreFacade fromLogOdds(String[] names, double[][] beta) {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(beta, "beta");
        EdgePriors lo = EdgePriors.fromMatrix(
                java.util.Arrays.asList(names), beta, EdgePriors.Semantics.LOG_ODDS);
        return new EdgePriorScoreFacade(lo);
    }

    /** The only per-repeat call: restrict to this repeat's variables and wrap. */
    public Score wrapScore(Score subsetScore) {
        Objects.requireNonNull(subsetScore, "subsetScore");
        EdgePriors restricted = this.logOdds.restrictTo(subsetScore.getVariables());
        return new EdgePriorScore(subsetScore, restricted);
    }

    public int numPriorPairs() {
        return this.logOdds.size();
    }

    public String toString() {
        return "EdgePriorScoreFacade[priorPairs=" + this.logOdds.size() + "]";
    }

    // ---- shared helpers (mirror EdgePriorFacade; duplicated to stand alone) ----

    private static String[] concat(String[] snpNames, String[] traitNames) {
        Objects.requireNonNull(snpNames, "snpNames");
        Objects.requireNonNull(traitNames, "traitNames");
        String[] names = new String[snpNames.length + traitNames.length];
        System.arraycopy(snpNames, 0, names, 0, snpNames.length);
        System.arraycopy(traitNames, 0, names, snpNames.length, traitNames.length);
        return names;
    }

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

        double sum = 0.0;
        long count = 0;
        for (double[] row : annotation) {
            for (double v : row) {
                if (Double.isFinite(v)) { sum += v; count++; }
            }
        }
        double mean = (count > 0) ? sum / count : 0.0;
        double ss = 0.0;
        for (double[] row : annotation) {
            for (double v : row) {
                if (Double.isFinite(v)) { ss += (v - mean) * (v - mean); }
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
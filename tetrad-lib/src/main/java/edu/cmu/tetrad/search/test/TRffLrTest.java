package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.TRffBicScore;
import edu.cmu.tetrad.util.TMath;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.FDistribution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Likelihood-ratio CI test based on nested {@link TRffBicScore} local fits.
 *
 * <p>Tests X &perp; Y | Z by comparing, on a common row subset,</p>
 * <pre>
 *   reduced: Y ~ Z
 *   full:    Y ~ Z + extra(X)
 * </pre>
 * <p>with statistic {@code D = 2 (llFull - llRed)} and degrees of freedom approximated by
 * the difference in ridge effective degrees of freedom {@code ddf = edfFull - edfRed}.</p>
 *
 * <p><b>Reference distribution.</b> For a discrete child the deviance is referred to
 * {@code ChiSq(ddf)}. For a continuous child the Student-t scale is profiled per model, so
 * {@code D = n log(sigmaRed^2 / sigmaFull^2)} up to the t-reweighting; the chi-square
 * approximation for such statistics requires {@code ddf << n} and inflates badly when the
 * extra block's degrees of freedom are a nontrivial fraction of n (the full model absorbs
 * noise into its scale estimate, and {@code n log(.)} grows past {@code ChiSq(ddf)}).
 * Continuous children therefore use the classical F-form instead:
 * {@code Lambda = exp(D/n)}, {@code F = (Lambda - 1) (n - edfFull) / ddf}, referred to
 * {@code F(ddf, n - edfFull)}. This reduces to the chi-square in the {@code ddf << n}
 * regime and remains calibrated when it does not hold.</p>
 *
 * <p><b>Nesting.</b> The reduced and full models are fit on a single shared design matrix in
 * which the reduced model's columns are a prefix of the full model's columns (see
 * {@link TRffBicScore#nestedLocalFits(int, int[], int, int[])}). With the ridge penalty
 * applied identically to shared coordinates, the full penalized optimum is at least the
 * reduced penalized optimum, so D is nonnegative up to profiling and convergence tolerance.
 * (An earlier version of this test compared fits over different random-feature bases, which
 * were not nested; D could then be negative and the chi-square reference was unfounded.)</p>
 *
 * <p><b>Symmetrization.</b> By default the test is computed in both directions - (child Y,
 * added X) and (child X, added Y) - and the larger (more conservative) p-value is reported,
 * so that {@code checkIndependence(x, y, z)} and {@code checkIndependence(y, x, z)} agree,
 * as constraint-based algorithms expect. Each direction controls its level, so the maximum
 * does as well. Set {@link #setSymmetrized(boolean)} to false to test only the (child Y)
 * direction at half the cost.</p>
 *
 * <p><b>Calibration caveats.</b> The chi-square reference with fractional ridge-edf degrees
 * of freedom is an approximation, not a Wilks result: the fits are penalized, the features
 * are random, and for continuous children the scale is profiled under a Student-t
 * likelihood. Empirically the test holds its level in linear-Gaussian, nonlinear, and
 * heavy-tailed settings at moderate n, but it should be treated as experimental.</p>
 */
public final class TRffLrTest implements IndependenceTest {

    private final TRffBicScore score;
    private final List<Node> variables;
    private boolean verbose = false;
    private double alpha = 0.01;
    private boolean symmetrized = true;

    /**
     * Constructs a TRffLrTest instance using the given score.
     *
     * @param score the TRffBicScore instance used to evaluate the nested local fits; must
     *              not be null.
     * @throws NullPointerException if the score parameter is null.
     */
    public TRffLrTest(TRffBicScore score) {
        if (score == null) throw new NullPointerException("score");
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
    }

    /**
     * Evaluates the statistical independence of two nodes given a conditioning set using
     * nested TRFF likelihood-ratio fits.
     *
     * @param x  the first node to test independence.
     * @param y  the second node to test independence.
     * @param _z the set of conditioning nodes for the independence test.
     * @return an {@code IndependenceResult} containing the independence decision, the
     * p-value, and additional metrics derived from the test.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {
        IndependenceFact fact = new IndependenceFact(x, y, _z);

        int xi = variables.indexOf(x);
        int yi = variables.indexOf(y);
        if (xi < 0 || yi < 0) {
            return new IndependenceResult(fact, false, Double.NaN, Double.NaN);
        }

        int[] zi = new int[_z.size()];
        int k = 0;
        for (Node node : _z) {
            int idx = variables.indexOf(node);
            if (idx < 0) {
                return new IndependenceResult(fact, false, Double.NaN, Double.NaN);
            }
            zi[k++] = idx;
        }
        Arrays.sort(zi);

        double p1 = directionalPValue(yi, xi, zi);
        double p;

        if (symmetrized) {
            double p2 = directionalPValue(xi, yi, zi);

            if (Double.isNaN(p1)) p = p2;
            else if (Double.isNaN(p2)) p = p1;
            else p = TMath.max(p1, p2); // conservative: reject only if both directions reject
        } else {
            p = p1;
        }

        if (Double.isNaN(p)) {
            IndependenceResult r = new IndependenceResult(fact, false, Double.NaN, Double.NaN);
            if (verbose) TetradLogger.getInstance().log("TRFF LR test result: " + r);
            return r;
        }

        boolean indep = (p > alpha);
        IndependenceResult r = new IndependenceResult(fact, indep, p, alpha - p);
        if (verbose) TetradLogger.getInstance().log("TRFF LR test result: " + r);
        return r;
    }

    /**
     * One direction of the test: child ~ Z versus child ~ Z + added, on rows complete for
     * {child} ∪ Z ∪ {added}. Returns NaN if the fits are unusable.
     */
    private double directionalPValue(int child, int added, int[] zi) {
        try {
            int[] union = new int[zi.length + 1];
            System.arraycopy(zi, 0, union, 0, zi.length);
            union[zi.length] = added;
            Arrays.sort(union);

            int[] rows = score.validRowsForUnion(child, union);
            int nUsed = (rows == null) ? score.getEffectiveSampleSize() : rows.length;
            if (nUsed < 10) return Double.NaN;

            TRffBicScore.NestedFits fits = score.nestedLocalFits(child, zi, added, rows);
            var red = fits.reduced();
            var full = fits.full();

            if (!Double.isFinite(red.logLik()) || !Double.isFinite(full.logLik())
                || !Double.isFinite(red.edf()) || !Double.isFinite(full.edf())) {
                return Double.NaN;
            }

            double d = 2.0 * (full.logLik() - red.logLik());
            if (!Double.isFinite(d) || d < 0.0) d = 0.0; // negatives only via profiling/convergence slack

            double ddf = full.edf() - red.edf();
            if (!Double.isFinite(ddf) || ddf < 1e-8) return Double.NaN;

            double p;

            if (variables.get(child) instanceof DiscreteVariable) {
                // Multinomial deviance: no profiled scale; chi-square reference.
                ChiSquaredDistribution chi2 = new ChiSquaredDistribution(ddf);
                p = 1.0 - chi2.cumulativeProbability(d);
            } else {
                // Continuous child with profiled scale: F-form (see class Javadoc).
                double denomDf = nUsed - full.edf();
                if (!(denomDf >= 10.0)) return Double.NaN; // model nearly saturates the data

                double lambda = TMath.exp(d / nUsed); // sigmaRed^2 / sigmaFull^2 analog
                double f = (lambda - 1.0) * denomDf / ddf;
                if (!Double.isFinite(f) || f < 0.0) f = 0.0;

                FDistribution fDist = new FDistribution(ddf, denomDf);
                p = 1.0 - fDist.cumulativeProbability(f);
            }

            return TMath.max(0.0, TMath.min(1.0, p));
        } catch (RuntimeException e) {
            TetradLogger.getInstance().log("TRFF LR test error: " + e.getMessage());
            return Double.NaN;
        }
    }

    /**
     * Returns the alpha value, the threshold for determining statistical significance.
     *
     * @return the alpha value as a double.
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the significance level (alpha) used for determining independence.
     *
     * @param alpha the significance level to be used in the independence test; must be a
     *              value in the range [0, 1].
     */
    @Override
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Indicates whether the test is symmetrized over the (x, y) pair.
     *
     * @return true if both directions are computed and the more conservative p-value is
     * reported; false if only the (child y) direction is computed.
     */
    public boolean isSymmetrized() {
        return symmetrized;
    }

    /**
     * Sets whether the test is symmetrized over the (x, y) pair. When true (the default),
     * both directions are computed and the larger p-value is reported, so the test result
     * does not depend on argument order; when false, only the (child y) direction is
     * computed, at half the cost.
     *
     * @param symmetrized true to symmetrize, false for the directional test.
     */
    public void setSymmetrized(boolean symmetrized) {
        this.symmetrized = symmetrized;
    }

    /**
     * Retrieves the list of variables involved in the test.
     *
     * @return a list of {@code Node} objects representing the variables in the test.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the data model associated with the current test instance.
     *
     * @return the {@code DataModel} object representing the dataset.
     */
    @Override
    public DataModel getData() {
        return score.getDataModel();
    }

    /**
     * Indicates whether verbose mode is enabled.
     *
     * @return {@code true} if verbose mode is enabled; {@code false} otherwise.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Configures the verbose mode for this test instance.
     *
     * @param verbose a boolean flag indicating whether verbose mode should be enabled.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns a string representation of this test.
     *
     * @return a string describing the TRffLrTest instance.
     */
    @Override
    public String toString() {
        return "TRFF LR CI test (TRffBicScore)";
    }
}

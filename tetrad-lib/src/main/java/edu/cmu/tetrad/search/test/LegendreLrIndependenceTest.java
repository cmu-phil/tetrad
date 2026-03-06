package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.LegendreBicScore;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.util.FastMath;

import java.util.*;

import static java.lang.Float.NaN;

/**
 * Likelihood-ratio CI test based on MinimaxLegendreScore local fits.
 * <p>
 * Tests X ⟂ Y | Z by comparing:
 * reduced: Y ~ Z
 * full:    Y ~ Z ∪ {X}
 * <p>
 * Statistic: D = 2 (ll_full - ll_red)
 * df approx: Δdf = edf_full - edf_red
 * p = 1 - CDF_ChiSq( D ; Δdf )
 * <p>
 * V1: forces nesting by disabling interactions during the test (recommended).
 */
public final class LegendreLrIndependenceTest implements IndependenceTest {

    private final LegendreBicScore score;
    private final List<Node> variables;
    private final boolean disableInteractionsForTest;
    private boolean verbose = false;
    private double alpha = 0.01;

    /**
     * Constructs a LegendreLrIndependenceTest instance with the provided scoring method.
     * The test checks for statistical independence using Legendre's minimax score.
     *
     * @param score the scoring mechanism used to evaluate statistical independence.
     *              Must be an instance of MinimaxLegendreScore.
     */
    public LegendreLrIndependenceTest(LegendreBicScore score) {
        this(score, true);
    }

    /**
     * Constructs a LegendreLrIndependenceTest instance with the provided scoring mechanism
     * and an option to disable interactions during the test.
     * The test checks for statistical independence using Legendre's minimax score.
     *
     * @param score the scoring mechanism used to evaluate statistical independence.
     *              Must be an instance of MinimaxLegendreScore. Cannot be null.
     * @param disableInteractionsForTest a boolean indicating whether interactions should be
     *                                    disabled during the test.
     */
    public LegendreLrIndependenceTest(LegendreBicScore score, boolean disableInteractionsForTest) {
        if (score == null) throw new NullPointerException("score");
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
        this.disableInteractionsForTest = disableInteractionsForTest;
    }

    private static boolean getUseInteractions(LegendreBicScore s) throws Exception {
        var f = LegendreBicScore.class.getDeclaredField("useInteractions");
        f.setAccessible(true);
        return (boolean) f.get(s);
    }

    // --- boilerplate ---

    private static int getInteractionMaxParents(LegendreBicScore s) throws Exception {
        var f = LegendreBicScore.class.getDeclaredField("interactionMaxParents");
        f.setAccessible(true);
        return (int) f.get(s);
    }

    /**
     * Checks whether two nodes, x and y, are statistically independent given a set of conditioning nodes z.
     * The method evaluates the independence based on Legendre's minimax scoring function and computes a
     * p-value using a likelihood ratio test.
     *
     * @param x the first node whose independence is to be tested. Must be present in the list of variables.
     * @param y the second node whose independence is to be tested. Must be present in the list of variables.
     * @param _z the set of nodes to condition on during the test. All nodes in the set must be present in
     *           the list of variables.
     * @return an IndependenceResult object containing the result of the independence test, including
     *         whether the nodes are independent, the p-value, and additional diagnostic information.
     * @throws InterruptedException if the independence test is interrupted during execution.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) throws InterruptedException {
        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        IndependenceFact fact = new IndependenceFact(x, y, _z);

        int xi = variables.indexOf(x);
        int yi = variables.indexOf(y);
        if (xi < 0 || yi < 0) {
            return new IndependenceResult(fact, false, Double.NaN, Double.NaN);
        }

        int[] zi = new int[z.size()];
        for (int i = 0; i < z.size(); i++) {
            int idx = variables.indexOf(z.get(i));
            if (idx < 0) {
                return new IndependenceResult(fact, false, Double.NaN, Double.NaN);
            }
            zi[i] = idx;
        }
        Arrays.sort(zi); // keep deterministic; also helps caching

        boolean prevInteractions = false;
        int prevK = 0;

        try {
            // Ensure nesting (recommended with your current interaction rule)
            if (disableInteractionsForTest) {
                prevInteractions = getUseInteractions(score);
                prevK = getInteractionMaxParents(score);
                score.setUseInteractions(false);
                // (Optional) also force maxParents=0, but useInteractions=false already kills them:
                // score.setInteractionMaxParents(0);
            }

            // Build Z ∪ {X}
            int[] zPlusX = new int[zi.length + 1];
            System.arraycopy(zi, 0, zPlusX, 0, zi.length);
            zPlusX[zi.length] = xi;
            Arrays.sort(zPlusX);

            // Use the SAME rows for both reduced/full (critical under missingness)
            int[] rows = score.validRowsForUnion(yi, zPlusX);
            int nUsed = (rows == null) ? score.getEffectiveSampleSize() : rows.length;
            if (nUsed < 10) {
                return new IndependenceResult(fact, false, Double.NaN, Double.NaN);
            }

            // reduced: Y ~ Z  (on common rows)
            var red = score.localFitOnRows(yi, zi, rows);

            // full: Y ~ Z ∪ {X} (on same rows)
            var full = score.localFitOnRows(yi, zPlusX, rows);

            if (!Double.isFinite(red.logLik()) || !Double.isFinite(full.logLik())
                    || !Double.isFinite(red.edf()) || !Double.isFinite(full.edf())) {
                return new IndependenceResult(fact, false, Double.NaN, Double.NaN);
            }

            double D = 2.0 * (full.logLik() - red.logLik());
            if (!(D >= 0.0) || !Double.isFinite(D)) D = 0.0;

            double ddf = full.edf() - red.edf();
            if (!Double.isFinite(ddf) || ddf < 1e-8) {
                return new IndependenceResult(fact, true, NaN, NaN);
            }

            ChiSquaredDistribution chi2 = new ChiSquaredDistribution(ddf);
            double p = 1.0 - chi2.cumulativeProbability(D);
            p = FastMath.max(0.0, FastMath.min(1.0, p));

            boolean indep = (p > getAlpha());
            return new IndependenceResult(fact, indep, p, getAlpha() - p);

        } catch (Exception e) {
            TetradLogger.getInstance().log("Legendre LR test error: " + e.getMessage());
            return new IndependenceResult(fact, false, Double.NaN, Double.NaN);

        } finally {
            if (disableInteractionsForTest) {
                try {
                    score.setUseInteractions(prevInteractions);
                    score.setInteractionMaxParents(prevK);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean isABoolean(double p) {
        return p > getAlpha();
    }

    /**
     * Retrieves the significance level (alpha) used by the independence test.
     *
     * @return the significance level (alpha) as a double. This value represents
     *         the threshold for determining statistical independence in the test.
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the significance level (alpha) used by the independence test.
     * This value determines the threshold at which the test decides
     * whether two variables are statistically independent.
     *
     * @param alpha the significance level to set, represented as a double.
     *              Must be a value between 0 and 1.
     */
    @Override
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Retrieves the list of variables used in the statistical independence test.
     *
     * @return a List of Node objects representing the variables included in the test.
     *         The returned list is a copy, ensuring that modifications to the returned
     *         list do not affect the original data structure.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the current data model associated with the independence test.
     *
     * @return the DataModel instance representing the data used in the test.
     */
    @Override
    public DataModel getData() {
        return score.getDataModel();
    }

    /**
     * Checks whether verbose output is enabled for the independence test.
     * Verbose output provides additional diagnostic or logging information
     * during the execution of the test.
     *
     * @return true if verbose output is enabled; false otherwise.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    // ---------------------------------------------------------------------
    // Temporary reflection-based accessors if you don't want to add getters.
    // Prefer adding getters in MinimaxLegendreScore instead.
    // ---------------------------------------------------------------------

    /**
     * Sets the verbosity level for the independence test. When verbose output is
     * enabled, additional diagnostic or logging information may be provided during
     * the execution of the test.
     *
     * @param verbose a boolean value indicating whether verbose output should be
     *                enabled (true) or disabled (false).
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns a string representation of the LegendreLrIndependenceTest.
     *
     * @return a descriptive string identifying the test as "Legendre LR CI test (MinimaxLegendreScore)".
     */
    @Override
    public String toString() {
        return "Legendre LR CI test (MinimaxLegendreScore)";
    }
}
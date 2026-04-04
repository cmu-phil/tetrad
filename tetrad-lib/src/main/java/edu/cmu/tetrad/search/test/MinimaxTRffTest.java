package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.LegendreBicScore;
import edu.cmu.tetrad.search.score.TRffBicScore;
import edu.cmu.tetrad.util.NaturalSort;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import edu.cmu.tetrad.util.TMath;

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
public final class MinimaxTRffTest implements IndependenceTest {

    private final TRffBicScore score;
    private final List<Node> variables;
    private final boolean disableInteractionsForTest;
    private boolean verbose = false;
    private double alpha = 0.01;

    /**
     * Constructs a MinimaxTRffTest instance using the given score and a default configuration
     * for interaction testing.
     *
     * @param score the MinimaxTRffBicScore instance used to evaluate the test.
     */
    public MinimaxTRffTest(TRffBicScore score) {
        this(score, true);
    }

    /**
     * Constructs a MinimaxTRffTest instance using the given score and a configuration
     * option to enable or disable interaction testing.
     *
     * @param score the MinimaxTRffBicScore instance used to evaluate the test; must not be null.
     * @param disableInteractionsForTest a boolean flag indicating whether interactions
     * should be disabled for the test.
     * @throws NullPointerException if the score parameter is null.
     */
    public MinimaxTRffTest(TRffBicScore score, boolean disableInteractionsForTest) {
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
     * Evaluates the statistical independence of two nodes given a conditioning set
     * using the Minimax TRff test. This method determines if {@code x} and {@code y}
     * are independent given the set of nodes {@code _z} based on a scoring mechanism.
     *
     * @param x the first node to test independence.
     * @param y the second node to test independence.
     * @param _z the set of conditioning nodes for the independence test; the nodes in
     *           this set condition the relationship between {@code x} and {@code y}.
     * @return an {@code IndependenceResult} containing the independence decision, the p-value,
     *         and additional metrics derived from the test.
     * @throws InterruptedException if the operation is interrupted during execution.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) throws InterruptedException {
        List<Node> z = new ArrayList<>(_z);
        z.sort(NaturalSort.naturalComparator());;

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
//                prevInteractions = getUseInteractions(score);
//                prevK = getInteractionMaxParents(score);
//                score.setUseInteractions(false);
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
                IndependenceResult independenceResult = new IndependenceResult(fact, false, Double.NaN, Double.NaN);

                if (verbose) {
                    TetradLogger.getInstance().log("Minimax TRff test result: " + independenceResult);
                }

                return independenceResult;
            }

            // reduced: Y ~ Z  (on common rows)
            var red = score.localFitOnRows(yi, zi, rows);

            // full: Y ~ Z ∪ {X} (on same rows)
            var full = score.localFitOnRows(yi, zPlusX, rows);

            if (!Double.isFinite(red.logLik()) || !Double.isFinite(full.logLik())
                    || !Double.isFinite(red.edf()) || !Double.isFinite(full.edf())) {
                IndependenceResult independenceResult = new IndependenceResult(fact, false, Double.NaN, Double.NaN);

                if (verbose) {
                    TetradLogger.getInstance().log("Minimax TRff test result: " + independenceResult);
                }

                return independenceResult;
            }

            double D = 2.0 * (full.logLik() - red.logLik());
            if (!(D >= 0.0) || !Double.isFinite(D)) D = 0.0;

            double ddf = full.edf() - red.edf();
            if (!Double.isFinite(ddf) || ddf < 1e-8) {
                IndependenceResult independenceResult = new IndependenceResult(fact, true, NaN, NaN);

                if (verbose) {
                    TetradLogger.getInstance().log("Minimax TRff test result: " + independenceResult);
                }

                return independenceResult;
            }

            ChiSquaredDistribution chi2 = new ChiSquaredDistribution(ddf);
            double p = 1.0 - chi2.cumulativeProbability(D);
            p = TMath.max(0.0, TMath.min(1.0, p));

            boolean indep = (p > getAlpha());
            IndependenceResult independenceResult = new IndependenceResult(fact, indep, p, getAlpha() - p);

            if (verbose) {
                TetradLogger.getInstance().log("Minimax TRff test result: " + independenceResult);
            }

            return independenceResult;

        } catch (Exception e) {
            TetradLogger.getInstance().log("Legendre LR test error: " + e.getMessage());
            return new IndependenceResult(fact, false, Double.NaN, Double.NaN);

        } finally {
            if (disableInteractionsForTest) {
                try {
//                    score.setUseInteractions(prevInteractions);
//                    score.setInteractionMaxParents(prevK);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean isABoolean(double p) {
        return p > getAlpha();
    }

    /**
     * Returns the alpha value, which is typically used as the threshold for
     * determining statistical significance in the context of independence tests.
     *
     * @return the alpha value as a double.
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the significance level (alpha) used in statistical tests for determining
     * independence. The alpha value is typically used as the threshold for rejecting
     * the null hypothesis, and it must be a value between 0 and 1.
     *
     * @param alpha the significance level to be used in the independence test; must
     *              be a value in the range [0, 1].
     */
    @Override
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Retrieves the list of variables involved in the Minimax TRff Test.
     * This method provides access to the set of nodes that are being considered
     * in the statistical evaluation of independence.
     *
     * @return a list of {@code Node} objects representing the variables in the test.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the data model associated with the current Minimax TRff Test instance.
     * The returned {@code DataModel} provides access to the dataset used for computations
     * and analyses in the context of this test.
     *
     * @return the {@code DataModel} object representing the dataset.
     */
    @Override
    public DataModel getData() {
        return score.getDataModel();
    }

    /**
     * Indicates whether verbose mode is enabled for logging or debugging purposes.
     *
     * @return {@code true} if verbose mode is enabled; {@code false} otherwise.
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
     * Configures the verbose mode for the MinimaxTRffTest instance.
     * When verbose mode is enabled, additional logging or debugging information
     * may be provided during the execution of the test.
     *
     * @param verbose a boolean flag indicating whether verbose mode should
     *                be enabled ({@code true}) or disabled ({@code false}).
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns a string representation of the MinimaxTRffTest instance.
     * This representation provides a human-readable description of the test,
     * including its name and type.
     *
     * @return a string describing the MinimaxTRffTest instance.
     */
    @Override
    public String toString() {
        return "Legendre LR CI test (MinimaxLegendreScore)";
    }
}
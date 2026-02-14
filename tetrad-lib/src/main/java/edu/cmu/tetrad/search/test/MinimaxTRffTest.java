package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.MinimaxLegendreScore;
import edu.cmu.tetrad.search.score.MinimaxTRffBicScore;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

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

    private final MinimaxTRffBicScore score;
    private final List<Node> variables;
    private final boolean disableInteractionsForTest;
    private boolean verbose = false;
    private double alpha = 0.01;

    public MinimaxTRffTest(MinimaxTRffBicScore score) {
        this(score, true);
    }

    public MinimaxTRffTest(MinimaxTRffBicScore score, boolean disableInteractionsForTest) {
        if (score == null) throw new NullPointerException("score");
        this.score = score;
        this.variables = new ArrayList<>(score.getVariables());
        this.disableInteractionsForTest = disableInteractionsForTest;
    }

    private static boolean getUseInteractions(MinimaxLegendreScore s) throws Exception {
        var f = MinimaxLegendreScore.class.getDeclaredField("useInteractions");
        f.setAccessible(true);
        return (boolean) f.get(s);
    }

    // --- boilerplate ---

    private static int getInteractionMaxParents(MinimaxLegendreScore s) throws Exception {
        var f = MinimaxLegendreScore.class.getDeclaredField("interactionMaxParents");
        f.setAccessible(true);
        return (int) f.get(s);
    }

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
            p = Math.max(0.0, Math.min(1.0, p));

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

    @Override
    public double getAlpha() {
        return alpha;
    }

    @Override
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    @Override
    public DataModel getData() {
        return score.getDataModel();
    }

    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    // ---------------------------------------------------------------------
    // Temporary reflection-based accessors if you don't want to add getters.
    // Prefer adding getters in MinimaxLegendreScore instead.
    // ---------------------------------------------------------------------

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public String toString() {
        return "Legendre LR CI test (MinimaxLegendreScore)";
    }
}
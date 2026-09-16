package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.SemBicScore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertTrue;

/**
 * Tests the residual variance floor (the xi adjustment of Li et al., "On Causal Discovery in the Presence of
 * Deterministic Relations", NeurIPS 2024, Eq. 3) on SemBicScore.
 * <p>
 * Data: V1, V2 iid standard normal; V3 = V1 + V2 exactly (a minimal deterministic cluster); V4 = V3 + noise
 * (V3 -&gt; V4 is the bridge edge). The floor must (a) make the deterministic variable's local score finite and
 * larger than any non-determining alternative, and (b) leave the frugality comparison to the BIC penalty, so
 * that the true minimal bridge parent {V3} beats the substitutes {V1, V2} and {V1, V2, V3}, which determine V4's
 * systematic part equally well but with more parameters.
 * <p>
 * This test does not compile against the unpatched branch (setResidualVarianceFloor does not exist), and its
 * assertions fail if the floor is not applied in the likelihood.
 */
public class TestSemBicResidualVarianceFloor {

    @Test
    public void testFloorMakesDeterministicScoresFiniteAndFrugal() {
        int n = 1000;
        Random rng = new Random(38);

        double[][] d = new double[n][4];
        for (int r = 0; r < n; r++) {
            double v1 = rng.nextGaussian();
            double v2 = rng.nextGaussian();
            double v3 = v1 + v2;                       // deterministic, exactly
            double v4 = v3 + rng.nextGaussian();       // bridge child
            d[r][0] = v1;
            d[r][1] = v2;
            d[r][2] = v3;
            d[r][3] = v4;
        }

        List<Node> vars = new ArrayList<>();
        for (int j = 1; j <= 4; j++) vars.add(new ContinuousVariable("V" + j));
        DataSet data = new BoxDataSet(new DoubleDataBox(d), vars);

        SemBicScore score = new SemBicScore(data, true);
        score.setPenaltyDiscount(1.0);
        score.setLambda(1e-8);                  // ridge, so singular parent submatrices do not throw
        score.setResidualVarianceFloor(1e-8);   // the xi floor under test

        int v1 = 0, v2 = 1, v3 = 2, v4 = 3;

        // (a) The deterministic variable's score is finite with the floor on, and beats a non-determining
        // parent set by a wide margin (its residual variance is xi rather than order 1).
        double detScore = score.localScore(v3, v1, v2);
        double nonDetScore = score.localScore(v3, v1);
        assertTrue("Score of V3 | {V1, V2} should be finite with the floor on", Double.isFinite(detScore));
        assertTrue("Determining parents should far outscore non-determining ones", detScore > nonDetScore);

        // The likelihood of the deterministic variable must be pinned by the floor, not by rounding noise: with
        // sigma-hat-squared at rounding level, log(sigma^2 + xi) is log xi to high accuracy. Without the floor,
        // the log term reflects machine epsilon (or diverges), and this assertion fails by a wide margin.
        double xi = 1e-8;
        double expectedLik = -0.5 * n * (Math.log(2 * Math.PI * xi) + 1);
        double lik = score.getLikelihood(v3, new int[]{v1, v2});
        assertTrue("Deterministic likelihood should equal the floor value, got " + lik + " vs " + expectedLik,
                Math.abs(lik - expectedLik) < 1e-3 * Math.abs(expectedLik));

        // (b) Frugality on the bridge set: {V3} ties {V1, V2} and {V1, V2, V3} in likelihood (each determines
        // V4's systematic part), so the BIC penalty must decide, preferring the smallest set.
        double sTrue = score.localScore(v4, v3);
        double sSubstitute = score.localScore(v4, v1, v2);
        double sRedundant = score.localScore(v4, v1, v2, v3);
        assertTrue("All bridge candidates should be finite", Double.isFinite(sTrue)
                && Double.isFinite(sSubstitute) && Double.isFinite(sRedundant));
        assertTrue("BIC should prefer the minimal bridge parent {V3} over {V1, V2}", sTrue > sSubstitute);
        assertTrue("BIC should prefer the minimal bridge parent {V3} over {V1, V2, V3}", sTrue > sRedundant);
    }
}

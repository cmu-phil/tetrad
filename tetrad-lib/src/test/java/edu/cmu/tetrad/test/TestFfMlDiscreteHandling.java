package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.FfMl;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the additive-by-default discrete handling of the FFML score (interactionLambda).
 *
 * <p>Design under test: the mixed kernel is k = k_add + k_cont + lambda * (k_cont x k_cat),
 * where k_add is the linear kernel of concatenated centered per-parent one-hot indicators.
 * At the default lambda = 0, discrete parents contribute BF/DG-style additive main effects
 * only, and catRho is inert; lambda &gt; 0 restores the product-kernel (per-level response
 * functions) with catRho pooling. Rationale: the Kronecker map multiplies effective capacity
 * per discrete parent, which at small effective sample sizes prices out real additive
 * effects (observed as dropped autoregressive edges for binary variables conditional on
 * continuous covariates).
 */
public class TestFfMlDiscreteHandling {

    /**
     * Builds a mixed data set with columns [X (cont), D (binary), Y (cont)].
     */
    private static DataSet mixedData(double[] x, int[] d, double[] y) {
        int n = x.length;
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new DiscreteVariable("D", 2));
        vars.add(new ContinuousVariable("Y"));
        MixedDataBox box = new MixedDataBox(vars, n);
        DataSet ds = new BoxDataSet(box, vars);
        for (int i = 0; i < n; i++) {
            ds.setDouble(i, 0, x[i]);
            ds.setInt(i, 1, d[i]);
            ds.setDouble(i, 2, y[i]);
        }
        return ds;
    }

    /**
     * Additive-truth regime: Y = 0.6*D + 0.5*X + noise.
     */
    private static DataSet additiveTruth(long seed, int n) {
        Random rng = new Random(seed);
        double[] x = new double[n];
        int[] d = new int[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = rng.nextGaussian();
            d[i] = rng.nextDouble() < 0.5 ? 0 : 1;
            y[i] = 0.6 * d[i] + 0.5 * x[i] + 0.5 * rng.nextGaussian();
        }
        return mixedData(x, d, y);
    }

    /**
     * Pure-interaction regime: Y = X * (2D - 1) + noise. D has no additive main effect on Y;
     * it only flips the sign of X's effect, which an additive discrete treatment cannot
     * represent but the product kernel can.
     */
    private static DataSet interactionTruth(long seed, int n) {
        Random rng = new Random(seed);
        double[] x = new double[n];
        int[] d = new int[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = rng.nextGaussian();
            d[i] = rng.nextDouble() < 0.5 ? 0 : 1;
            y[i] = x[i] * (2 * d[i] - 1) + 0.3 * rng.nextGaussian();
        }
        return mixedData(x, d, y);
    }

    /**
     * Delta of adding D as a parent of Y given X, i.e. score(Y | X, D) - score(Y | X).
     * Column order in mixedData: X=0, D=1, Y=2.
     */
    private static double addDGivenX(FfMl score) {
        return score.localScore(2, 0, 1) - score.localScore(2, 0);
    }

    /**
     * The default must be the additive (BF/DG-style) discrete handling.
     */
    @Test
    public void testDefaultIsAdditive() {
        FfMl score = new FfMl(additiveTruth(1L, 200));
        assertEquals(0.0, score.getInteractionLambda(), 0.0);
    }

    /**
     * catRho only parameterizes the interaction (product-kernel) term, so at lambda = 0 it
     * must have no effect on scores.
     */
    @Test
    public void testCatRhoInertAtLambdaZero() {
        DataSet data = additiveTruth(2L, 200);
        FfMl a = new FfMl(data);
        a.setCatRho(0.1);
        FfMl b = new FfMl(data);
        b.setCatRho(0.9);
        assertEquals(addDGivenX(a), addDGivenX(b), 1e-9);
    }

    /**
     * Additive-truth regime: the additive treatment must detect the discrete main effect,
     * and must assign it at least as much evidence as the capacity-heavier product-kernel
     * treatment does.
     */
    @Test
    public void testAdditiveMainEffectRecovered() {
        DataSet data = additiveTruth(3L, 200);

        FfMl additive = new FfMl(data); // lambda = 0 default
        double dAdd = addDGivenX(additive);
        assertTrue("Additive main effect should be detected at lambda=0; delta=" + dAdd,
                dAdd > 5.0);

        FfMl product = new FfMl(data);
        product.setInteractionLambda(1.0);
        double dProd = addDGivenX(product);
        assertTrue("Additive treatment should not lose to the product kernel on additive "
                        + "truth; additive=" + dAdd + " product=" + dProd,
                dAdd >= dProd);
    }

    /**
     * Pure-interaction regime: the product kernel (lambda &gt; 0) must recover the
     * interaction and must beat the additive treatment, which cannot represent it. This test
     * documents the price of the additive default; lambda is the calibration dial.
     */
    @Test
    public void testInteractionRecoveredAtPositiveLambda() {
        DataSet data = interactionTruth(4L, 300);

        FfMl additive = new FfMl(data); // lambda = 0 default
        double dAdd = addDGivenX(additive);

        FfMl product = new FfMl(data);
        product.setInteractionLambda(1.0);
        double dProd = addDGivenX(product);

        assertTrue("Product kernel should detect a pure interaction; delta=" + dProd,
                dProd > 10.0);
        assertTrue("Product kernel should beat additive on pure-interaction truth; "
                        + "additive=" + dAdd + " product=" + dProd,
                dProd > dAdd + 10.0);
    }

    /**
     * A many-level discrete parent must be scoreable at lambda = 0 (the additive block is
     * only Sum(L) columns wide; the Kronecker caps apply only to the interaction term).
     */
    @Test
    public void testManyLevelDiscreteParentFiniteAtLambdaZero() {
        int n = 400;
        int levels = 60; // exceeds MAX_DISCRETE_LEVELS_FOR_KRONECKER = 50
        Random rng = new Random(5L);
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new DiscreteVariable("D", levels));
        vars.add(new ContinuousVariable("Y"));
        MixedDataBox box = new MixedDataBox(vars, n);
        DataSet ds = new BoxDataSet(box, vars);
        for (int i = 0; i < n; i++) {
            double x = rng.nextGaussian();
            int d = i % levels; // every level observed
            ds.setDouble(i, 0, x);
            ds.setInt(i, 1, d);
            ds.setDouble(i, 2, 0.5 * x + 0.1 * d + rng.nextGaussian());
        }
        FfMl score = new FfMl(ds);
        double s = score.localScore(2, 0, 1);
        assertTrue("Many-level discrete parent should be finite at lambda=0; got " + s,
                Double.isFinite(s));
    }

    /**
     * With lambda &gt; 0 and a discrete parent large enough to trip the Kronecker dimension
     * threshold, the n-by-n kernel fallback must produce a finite score under the new
     * K = Kadd + Kcont * (1 + lambda * Kcat) assembly.
     */
    @Test
    public void testNxNFallbackFiniteAtPositiveLambda() {
        int n = 150;
        int levels = 40; // 50 features * 40 levels = 2000 > n -> NxN path
        Random rng = new Random(6L);
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new DiscreteVariable("D", levels));
        vars.add(new ContinuousVariable("Y"));
        MixedDataBox box = new MixedDataBox(vars, n);
        DataSet ds = new BoxDataSet(box, vars);
        for (int i = 0; i < n; i++) {
            double x = rng.nextGaussian();
            int d = i % levels;
            ds.setDouble(i, 0, x);
            ds.setInt(i, 1, d);
            ds.setDouble(i, 2, 0.5 * x + rng.nextGaussian());
        }
        FfMl score = new FfMl(ds);
        score.setInteractionLambda(1.0);
        double s = score.localScore(2, 0, 1);
        assertTrue("NxN fallback should be finite at lambda>0; got " + s, Double.isFinite(s));
    }
}

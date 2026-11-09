package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.independence.ConditionalGaussianLrt;
import edu.cmu.tetrad.algcomparison.score.ConditionalGaussianBicScore;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.ConditionalGaussianLikelihood;
import edu.cmu.tetrad.search.score.ConditionalGaussianScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndTestConditionalGaussianLrt;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Regression tests for effective-sample-size support in ConditionalGaussianScore and
 * IndTestConditionalGaussianLrt (added 2026-8-25), and for the ConditionalGaussianBicScore wrapper fix that routes
 * MIN_SAMPLE_SIZE_PER_CELL to setMinSampleSizePerCell rather than setNumCategoriesToDiscretize.
 * <p>
 * The central semantic check is duplication invariance: a dataset in which every row appears k times carries the
 * same information as the original, so scoring or testing the duplicated data with nEff equal to the original
 * row count must reproduce the original score or p-value. Without nEff, the duplicated data must look like k times
 * as much evidence. The pre-patch classes fail these tests at compile time (no EffectiveSampleSizeSettable) and,
 * for the wrapper, at run time.
 */
public class TestConditionalGaussianEffectiveSampleSize {

    private static final int N = 400;
    private static final int K = 5;             // duplication factor
    private static final double TOL = 1e-8;

    // Variables: X1, X2 continuous; D1 (3 levels), D2 (2 levels) discrete.
    // X1 -> X2, X1 -> D1, D1 -> D2. Cells are well populated so min-cell gates never bind.
    private static DataSet mixedData(long seed) {
        Random rng = new Random(seed);
        double[] x1 = new double[N], x2 = new double[N];
        int[] d1 = new int[N], d2 = new int[N];
        for (int i = 0; i < N; i++) {
            x1[i] = rng.nextGaussian();
            x2[i] = 0.8 * x1[i] + rng.nextGaussian();
            double u = x1[i] + 0.7 * rng.nextGaussian();
            d1[i] = u < -0.5 ? 0 : (u < 0.5 ? 1 : 2);
            d2[i] = rng.nextDouble() < (0.2 + 0.3 * d1[i]) ? 1 : 0;
        }
        return mixed(N, new String[]{"X1", "X2", "D1", "D2"},
                new double[][]{x1, x2}, new int[][]{d1, d2}, new int[]{3, 2});
    }

    private static DataSet mixed(int n, String[] names, double[][] cont, int[][] disc, int[] numCats) {
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < cont.length; j++) vars.add(new ContinuousVariable(names[j]));
        for (int j = 0; j < disc.length; j++) {
            List<String> cats = new ArrayList<>();
            for (int c = 0; c < numCats[j]; c++) cats.add("c" + c);
            vars.add(new DiscreteVariable(names[cont.length + j], cats));
        }
        DataSet d = new BoxDataSet(new MixedDataBox(vars, n), vars);
        for (int j = 0; j < cont.length; j++)
            for (int i = 0; i < n; i++) d.setDouble(i, j, cont[j][i]);
        for (int j = 0; j < disc.length; j++)
            for (int i = 0; i < n; i++) d.setInt(i, cont.length + j, disc[j][i]);
        return d;
    }

    /** Each row of d repeated k times, in original order (row i of d at rows k*i .. k*i+k-1). */
    private static DataSet duplicate(DataSet d, int k) {
        List<Node> vars = d.getVariables();
        int n = d.getNumRows();
        DataSet out = new BoxDataSet(new MixedDataBox(vars, n * k), vars);
        for (int i = 0; i < n; i++) {
            for (int r = 0; r < k; r++) {
                for (int j = 0; j < vars.size(); j++) {
                    if (vars.get(j) instanceof ContinuousVariable) out.setDouble(k * i + r, j, d.getDouble(i, j));
                    else out.setInt(k * i + r, j, d.getInt(i, j));
                }
            }
        }
        return out;
    }

    private static int col(DataSet d, String name) {
        return d.getVariables().indexOf(d.getVariable(name));
    }

    // ------------------------------------------------------------------ score

    @Test
    public void scoreImplementsInterfaceAndDefaultsToRowCount() {
        DataSet d = mixedData(1);
        ConditionalGaussianScore s = new ConditionalGaussianScore(d, 1.0, true);
        assertTrue(s instanceof EffectiveSampleSizeSettable);
        assertEquals(N, s.getEffectiveSampleSize());
        s.setEffectiveSampleSize(37);
        assertEquals(37, s.getEffectiveSampleSize());
        s.setEffectiveSampleSize(-1);
        assertEquals(N, s.getEffectiveSampleSize());
        s.setEffectiveSampleSize(0);   // zero is meaningless; treated as unset
        assertEquals(N, s.getEffectiveSampleSize());
    }

    @Test
    public void scoreUnchangedWhenEffectiveSampleSizeUnset() {
        DataSet d = mixedData(2);
        ConditionalGaussianScore s = new ConditionalGaussianScore(d, 2.0, true);
        ConditionalGaussianLikelihood lik = new ConditionalGaussianLikelihood(d);
        lik.setDiscretize(true);
        lik.setNumCategoriesToDiscretize(3);
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < N; i++) rows.add(i);

        int x2 = col(d, "X2"), x1 = col(d, "X1"), d1 = col(d, "D1"), d2 = col(d, "D2");
        int[][] families = {{x2, x1}, {d1, x1}, {d2, d1}, {x2}, {d1, x1, d2}};
        for (int[] f : families) {
            int child = f[0];
            int[] parents = java.util.Arrays.copyOfRange(f, 1, f.length);
            ConditionalGaussianLikelihood.Ret ret = lik.getLikelihood(child, parents, rows);
            double expected = 2.0 * ret.getLik() - 2.0 * ret.getDof() * Math.log(N);
            assertEquals(expected, s.localScore(child, parents), TOL);
        }
    }

    @Test
    public void scoreScalesLikelihoodAndPenaltyByRatio() {
        DataSet d = mixedData(3);
        double c = 1.5;
        ConditionalGaussianScore s = new ConditionalGaussianScore(d, c, true);
        int nEff = N / 4;
        s.setEffectiveSampleSize(nEff);

        ConditionalGaussianLikelihood lik = new ConditionalGaussianLikelihood(d);
        lik.setDiscretize(true);
        lik.setNumCategoriesToDiscretize(3);
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < N; i++) rows.add(i);
        double r = nEff / (double) N;

        int x2 = col(d, "X2"), x1 = col(d, "X1"), d1 = col(d, "D1");
        int[][] families = {{x2, x1}, {d1, x1}, {x2, x1, d1}};
        for (int[] f : families) {
            int child = f[0];
            int[] parents = java.util.Arrays.copyOfRange(f, 1, f.length);
            ConditionalGaussianLikelihood.Ret ret = lik.getLikelihood(child, parents, rows);
            double expected = 2.0 * r * ret.getLik() - c * ret.getDof() * Math.log(r * N);
            assertEquals(expected, s.localScore(child, parents), TOL);
        }
    }

    @Test
    public void duplicatedRowsScoreAsOriginalWhenEffectiveSampleSizeIsOriginalN() {
        DataSet d = mixedData(4);
        DataSet dk = duplicate(d, K);
        assertEquals(N * K, dk.getNumRows());

        ConditionalGaussianScore orig = new ConditionalGaussianScore(d, 2.0, true);
        ConditionalGaussianScore dup = new ConditionalGaussianScore(dk, 2.0, true);
        ConditionalGaussianScore dupEff = new ConditionalGaussianScore(dk, 2.0, true);
        dupEff.setEffectiveSampleSize(N);

        int x2 = col(d, "X2"), x1 = col(d, "X1"), d1 = col(d, "D1"), d2 = col(d, "D2");
        int[][] families = {{x2, x1}, {d1, x1}, {d2, d1}, {x2, x1, d1}, {d2}, {x1}};
        for (int[] f : families) {
            int child = f[0];
            int[] parents = java.util.Arrays.copyOfRange(f, 1, f.length);
            double so = orig.localScore(child, parents);
            double sk = dup.localScore(child, parents);
            double se = dupEff.localScore(child, parents);

            // With nEff = N the duplicated data reproduces the original score exactly.
            assertEquals("family " + java.util.Arrays.toString(f), so, se, 1e-6 * Math.max(1, Math.abs(so)));

            // Without nEff, the K-fold copy looks like K times the evidence: for an edge that is truly present,
            // the score difference (child | parents) - (child | {}) is about K times larger.
            if (parents.length > 0) {
                double bumpO = so - orig.localScore(child);
                double bumpK = sk - dup.localScore(child);
                assertTrue("bump should inflate under duplication for " + java.util.Arrays.toString(f),
                        bumpK > 1.5 * bumpO);
            }
        }
    }

    // ------------------------------------------------------------------- test

    @Test
    public void lrtImplementsInterfaceAndDefaultsToRowCount() {
        DataSet d = mixedData(5);
        IndTestConditionalGaussianLrt t = new IndTestConditionalGaussianLrt(d, 0.05, true);
        assertTrue(t instanceof EffectiveSampleSizeSettable);
        assertEquals(N, t.getEffectiveSampleSize());
        t.setEffectiveSampleSize(50);
        assertEquals(50, t.getEffectiveSampleSize());
        t.setEffectiveSampleSize(-1);
        List<Integer> half = new ArrayList<>();
        for (int i = 0; i < N / 2; i++) half.add(i);
        t.setRows(half);
        assertEquals(N / 2, t.getEffectiveSampleSize());   // unset: reports rows in use
        t.setEffectiveSampleSize(50);
        assertEquals(50, t.getEffectiveSampleSize());       // set: survives setRows (ratio semantics)
    }

    @Test
    public void lrtPValueUnchangedWhenUnsetAndDuplicationInvariantWhenSet() {
        DataSet d = mixedData(6);
        DataSet dk = duplicate(d, K);
        Node x1 = d.getVariable("X1"), x2 = d.getVariable("X2"), d1 = d.getVariable("D1"), d2 = d.getVariable("D2");

        IndTestConditionalGaussianLrt orig = new IndTestConditionalGaussianLrt(d, 0.05, true);
        IndTestConditionalGaussianLrt origUnset = new IndTestConditionalGaussianLrt(d, 0.05, true);
        origUnset.setEffectiveSampleSize(-1);
        IndTestConditionalGaussianLrt dup = new IndTestConditionalGaussianLrt(dk, 0.05, true);
        IndTestConditionalGaussianLrt dupEff = new IndTestConditionalGaussianLrt(dk, 0.05, true);
        dupEff.setEffectiveSampleSize(N);

        // Pairs: a dependent continuous pair, a mixed pair, a discrete pair, and a conditional independence.
        Object[][] facts = {
                {x1, x2, Collections.emptySet()},
                {x1, d1, Collections.emptySet()},
                {d1, d2, Collections.emptySet()},
                {x2, d1, Collections.singleton(x1)},   // X2 _||_ D1 | X1 in the generating model
        };
        for (Object[] f : facts) {
            @SuppressWarnings("unchecked")
            java.util.Set<Node> z = (java.util.Set<Node>) f[2];
            double pO = orig.checkIndependence((Node) f[0], (Node) f[1], z).getPValue();
            double pU = origUnset.checkIndependence((Node) f[0], (Node) f[1], z).getPValue();
            double pK = dup.checkIndependence((Node) f[0], (Node) f[1], z).getPValue();
            double pE = dupEff.checkIndependence((Node) f[0], (Node) f[1], z).getPValue();

            assertEquals("unset must equal default", pO, pU, 0.0);
            assertEquals("nEff = N on duplicated data must reproduce the original p-value", pO, pE,
                    1e-9 * Math.max(1e-300, pO) + 1e-12);
            if (z.isEmpty()) {
                // Marginally dependent pairs: duplication without nEff shrinks the p-value.
                assertTrue("duplication should inflate evidence for " + f[0] + "," + f[1], pK < pO || pO < 1e-12);
            }
        }
    }

    // --------------------------------------------------------------- wrappers

    @Test
    public void wrappersExposeAndApplyEffectiveSampleSize() throws InterruptedException {
        DataSet d = mixedData(7);
        DataSet dk = duplicate(d, K);

        ConditionalGaussianBicScore scoreWrapper = new ConditionalGaussianBicScore();
        ConditionalGaussianLrt testWrapper = new ConditionalGaussianLrt();
        assertTrue(scoreWrapper.getParameters().contains(Params.EFFECTIVE_SAMPLE_SIZE));
        assertTrue(testWrapper.getParameters().contains(Params.EFFECTIVE_SAMPLE_SIZE));

        Parameters plain = new Parameters();
        plain.set(Params.PENALTY_DISCOUNT, 2.0);
        Parameters withEff = new Parameters();
        withEff.set(Params.PENALTY_DISCOUNT, 2.0);
        withEff.set(Params.EFFECTIVE_SAMPLE_SIZE, N);

        Score so = scoreWrapper.getScore(d, plain);
        Score se = scoreWrapper.getScore(dk, withEff);
        int x2 = col(d, "X2"), x1 = col(d, "X1");
        assertEquals(so.localScore(x2, x1), se.localScore(x2, x1), 1e-6);

        IndependenceTest to = testWrapper.getTest(d, plain);
        IndependenceTest te = testWrapper.getTest(dk, withEff);
        Node vx1 = d.getVariable("X1"), vx2 = d.getVariable("X2");
        double pO = to.checkIndependence(vx1, vx2, Collections.emptySet()).getPValue();
        double pE = te.checkIndependence(vx1, vx2, Collections.emptySet()).getPValue();
        assertEquals(pO, pE, 1e-9 * Math.max(1e-300, pO) + 1e-12);
    }

    /**
     * Before the fix, the wrapper passed MIN_SAMPLE_SIZE_PER_CELL to setNumCategoriesToDiscretize, so a score built
     * with numCategoriesToDiscretize = 3 and minSampleSizePerCell = 4 actually discretized into 4 bins. The
     * discretization path is exercised when a continuous variable is a parent of a discrete child.
     */
    @Test
    public void wrapperRoutesMinSampleSizePerCellToTheRightSetter() {
        DataSet d = mixedData(8);
        int d1 = col(d, "D1"), x1 = col(d, "X1");

        Parameters p = new Parameters();
        p.set(Params.PENALTY_DISCOUNT, 1.0);
        p.set(Params.DISCRETIZE, true);
        p.set(Params.NUM_CATEGORIES_TO_DISCRETIZE, 3);
        p.set(Params.MIN_SAMPLE_SIZE_PER_CELL, 4);
        Score wrapped = new ConditionalGaussianBicScore().getScore(d, p);

        ConditionalGaussianScore direct3 = new ConditionalGaussianScore(d, 1.0, true);
        direct3.setNumCategoriesToDiscretize(3);
        direct3.setMinSampleSizePerCell(4);
        ConditionalGaussianScore direct4 = new ConditionalGaussianScore(d, 1.0, true);
        direct4.setNumCategoriesToDiscretize(4);
        direct4.setMinSampleSizePerCell(4);

        double sw = wrapped.localScore(d1, x1);
        double s3 = direct3.localScore(d1, x1);
        double s4 = direct4.localScore(d1, x1);
        assertNotEquals("test has no teeth if 3 and 4 bins score alike", s3, s4, 1e-6);
        assertEquals("wrapper must honor numCategoriesToDiscretize = 3", s3, sw, TOL);
    }
}

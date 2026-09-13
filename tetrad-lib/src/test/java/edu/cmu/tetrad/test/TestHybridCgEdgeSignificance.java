package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgEdgeSignificance;
import edu.cmu.tetrad.hybridcg.HybridCgEdgeSignificance.Result;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link HybridCgEdgeSignificance}: hand-computed degrees of freedom on fully populated fixtures, null
 * calibration of the per-edge LRT at seeded resampling, power on strong edges, the shareVariance df accounting, the
 * per-stratum coefficient t-tests, and available-case handling of missing values.
 * <p>
 * The calibration checks compare an empirical rejection rate over seeded replications against a band around the
 * nominal level; the bands are wide enough to absorb Monte Carlo error at the given replication counts and the known
 * mild anti-conservatism of the LRT in small strata, and the seed pins the draws, so these tests are deterministic.
 */
public final class TestHybridCgEdgeSignificance {

    // ---------------------------------------------------------------- fixtures

    /** X -> Y <- Z (continuous), D -> Y (discrete, 3 categories). */
    private record ContFixture(HybridCgPm pm, HybridCgIm im, Node x, Node z, Node d, Node y) {
    }

    private static ContFixture contFixture(double coefX, double coefZ, double[] meansByD) {
        Node x = new ContinuousVariable("X");
        Node z = new ContinuousVariable("Z");
        Node y = new ContinuousVariable("Y");
        Node d = new DiscreteVariable("D", List.of("a", "b", "c"));
        Graph g = new EdgeListGraph(List.of(x, z, d, y));
        g.addDirectedEdge(x, y);
        g.addDirectedEdge(z, y);
        g.addDirectedEdge(d, y);

        Map<Node, Boolean> flags = new LinkedHashMap<>();
        flags.put(x, false);
        flags.put(z, false);
        flags.put(y, false);
        flags.put(d, true);
        Map<Node, List<String>> cats = new LinkedHashMap<>();
        cats.put(d, List.of("a", "b", "c"));

        HybridCgPm pm = new HybridCgPm(g, List.of(x, z, d, y), flags, cats);
        HybridCgIm im = new HybridCgIm(pm);

        for (Node v : List.of(x, z)) {
            int vi = pm.indexOf(v);
            im.setMean(vi, 0, 0.0);
            im.setVariance(vi, 0, 1.0);
        }
        int di = pm.indexOf(d);
        for (int k = 0; k < 3; k++) im.setProbability(di, 0, k, 1.0 / 3.0);

        int yi = pm.indexOf(y);
        int[] cps = pm.getContinuousParents(yi);
        int tx = -1, tz = -1;
        for (int t = 0; t < cps.length; t++) {
            if (pm.getNodes()[cps[t]].equals(x)) tx = t;
            if (pm.getNodes()[cps[t]].equals(z)) tz = t;
        }
        for (int row = 0; row < pm.getNumRows(yi); row++) {
            im.setMean(yi, row, meansByD[row]);
            im.setCoefficient(yi, row, tx, coefX);
            im.setCoefficient(yi, row, tz, coefZ);
            im.setVariance(yi, row, 1.0);
        }
        return new ContFixture(pm, im, x, z, d, y);
    }

    /** D1(3) -> C(2) <- Xc (continuous, 3 bins via cutpoints at -0.5, 0.5). */
    private record DiscFixture(HybridCgPm pm, HybridCgIm im, Node d1, Node xc, Node c) {
    }

    private static DiscFixture discFixture(boolean cDependsOnD1, boolean cDependsOnXc) {
        Node d1 = new DiscreteVariable("D1", List.of("a", "b", "c"));
        Node xc = new ContinuousVariable("Xc");
        Node c = new DiscreteVariable("C", List.of("no", "yes"));
        Graph g = new EdgeListGraph(List.of(d1, xc, c));
        g.addDirectedEdge(d1, c);
        g.addDirectedEdge(xc, c);

        Map<Node, Boolean> flags = new LinkedHashMap<>();
        flags.put(d1, true);
        flags.put(xc, false);
        flags.put(c, true);
        Map<Node, List<String>> cats = new LinkedHashMap<>();
        cats.put(d1, List.of("a", "b", "c"));
        cats.put(c, List.of("no", "yes"));

        HybridCgPm pm = new HybridCgPm(g, List.of(d1, xc, c), flags, cats);
        pm.setContParentCutpointsForDiscreteChild(c, Map.of(xc, new double[]{-0.5, 0.5}));

        HybridCgIm im = new HybridCgIm(pm);
        int d1i = pm.indexOf(d1);
        for (int k = 0; k < 3; k++) im.setProbability(d1i, 0, k, 1.0 / 3.0);
        int xci = pm.indexOf(xc);
        im.setMean(xci, 0, 0.0);
        im.setVariance(xci, 0, 1.0);

        int ci = pm.indexOf(c);
        int[] dims = pm.getRowDims(ci);
        for (int row = 0; row < pm.getNumRows(ci); row++) {
            int d1Val = row / dims[1];
            int binVal = row % dims[1];
            double pYes = 0.5;
            if (cDependsOnD1) pYes += 0.15 * (d1Val - 1);
            if (cDependsOnXc) pYes += 0.15 * (binVal - 1);
            pYes = Math.min(0.9, Math.max(0.1, pYes));
            im.setProbability(ci, row, 0, 1.0 - pYes);
            im.setProbability(ci, row, 1, pYes);
        }
        return new DiscFixture(pm, im, d1, xc, c);
    }

    private static DataSet sample(HybridCgIm im, int n) {
        return im.toDataSet(im.sample(n));
    }

    private static Result resultFor(Map<Edge, Result> map, Graph g, Node parent, Node child) {
        return map.get(g.getEdge(parent, child));
    }

    // ---------------------------------------------------------------- df hand checks

    /**
     * Continuous child Y with continuous parents {X, Z} and discrete parent D (3 categories), all strata populated:
     * dropping Z frees one coefficient per stratum (df 3); dropping D collapses 3 regressions of 3 mean-parameters
     * plus 3 variances (12) to one of 3 plus 1 (4), df 8.
     */
    @Test
    public void testDfContinuousChild() {
        RandomUtil.getInstance().setSeed(884422L);
        ContFixture f = contFixture(1.0, 0.0, new double[]{0, 0, 0});
        DataSet ds = sample(f.im, 2000);
        Map<Edge, Result> res = HybridCgEdgeSignificance.compute(f.im, ds);
        Graph g = f.pm.getGraph();

        assertEquals(3, resultFor(res, g, f.z, f.y).df());
        assertEquals(8, resultFor(res, g, f.d, f.y).df());
    }

    /**
     * Discrete child C (2 categories) with row dimensions 3 (D1) x 3 (Xc bins), all cells populated: dropping either
     * dimension gives (3 - 1)(2 - 1) per configuration of the other, times 3 configurations, df 6.
     */
    @Test
    public void testDfDiscreteChild() {
        RandomUtil.getInstance().setSeed(884423L);
        DiscFixture f = discFixture(true, true);
        DataSet ds = sample(f.im, 5000);
        Map<Edge, Result> res = HybridCgEdgeSignificance.compute(f.im, ds);
        Graph g = f.pm.getGraph();

        assertEquals(6, resultFor(res, g, f.d1, f.c).df());
        assertEquals(6, resultFor(res, g, f.xc, f.c).df());
    }

    /**
     * With shareVariance, the per-stratum variances collapse to one in both models, so dropping the discrete parent
     * frees only the mean-parameters (df 6, not 8), and dropping a continuous parent is unchanged (df 3).
     */
    @Test
    public void testDfShareVariance() {
        RandomUtil.getInstance().setSeed(884424L);
        ContFixture f = contFixture(1.0, 0.5, new double[]{0, 1, 2});
        DataSet ds = sample(f.im, 2000);
        Map<Edge, Result> res = HybridCgEdgeSignificance.compute(f.im, ds, true);
        Graph g = f.pm.getGraph();

        assertEquals(6, resultFor(res, g, f.d, f.y).df());
        assertEquals(3, resultFor(res, g, f.z, f.y).df());
    }

    // ---------------------------------------------------------------- calibration and power

    /**
     * Z -> Y is in the graph but its coefficient is zero in every stratum; at alpha = 0.05 over 200 seeded
     * replications of n = 300 the rejection rate should sit near the nominal level.
     */
    @Test
    public void testNullCalibrationContinuousParent() {
        RandomUtil.getInstance().setSeed(884425L);
        int reps = 200, rejections = 0;
        for (int rep = 0; rep < reps; rep++) {
            ContFixture f = contFixture(1.0, 0.0, new double[]{0.0, 0.5, 1.0});
            DataSet ds = sample(f.im, 300);
            Result r = resultFor(HybridCgEdgeSignificance.compute(f.im, ds), f.pm.getGraph(), f.z, f.y);
            if (r.significantAt(0.05)) rejections++;
        }
        double rate = rejections / (double) reps;
        assertTrue("null rejection rate = " + rate, rate >= 0.015 && rate <= 0.10);
    }

    /**
     * The CPT of C does not vary over the D1 dimension, so D1 -> C is null; rejection rate at alpha = 0.05 over 200
     * seeded replications of n = 600 should sit near the nominal level.
     */
    @Test
    public void testNullCalibrationDiscreteChild() {
        RandomUtil.getInstance().setSeed(884426L);
        int reps = 200, rejections = 0;
        for (int rep = 0; rep < reps; rep++) {
            DiscFixture f = discFixture(false, true);
            DataSet ds = sample(f.im, 600);
            Result r = resultFor(HybridCgEdgeSignificance.compute(f.im, ds), f.pm.getGraph(), f.d1, f.c);
            if (r != null && r.significantAt(0.05)) rejections++;
        }
        double rate = rejections / (double) reps;
        assertTrue("null rejection rate = " + rate, rate >= 0.015 && rate <= 0.10);
    }

    /** Strong edges of all four kinds should be detected at these effect sizes and sample sizes. */
    @Test
    public void testPower() {
        RandomUtil.getInstance().setSeed(884427L);
        int reps = 20, hitsX = 0, hitsD = 0, hitsD1 = 0, hitsXc = 0;
        for (int rep = 0; rep < reps; rep++) {
            ContFixture f = contFixture(1.0, 0.0, new double[]{0.0, 1.0, 2.0});
            DataSet ds = sample(f.im, 500);
            Map<Edge, Result> res = HybridCgEdgeSignificance.compute(f.im, ds);
            if (resultFor(res, f.pm.getGraph(), f.x, f.y).significantAt(0.05)) hitsX++;
            if (resultFor(res, f.pm.getGraph(), f.d, f.y).significantAt(0.05)) hitsD++;

            DiscFixture fd = discFixture(true, true);
            DataSet ds2 = sample(fd.im, 1500);
            Map<Edge, Result> res2 = HybridCgEdgeSignificance.compute(fd.im, ds2);
            if (resultFor(res2, fd.pm.getGraph(), fd.d1, fd.c).significantAt(0.05)) hitsD1++;
            if (resultFor(res2, fd.pm.getGraph(), fd.xc, fd.c).significantAt(0.05)) hitsXc++;
        }
        assertTrue("X->Y hits = " + hitsX, hitsX >= reps - 1);
        assertTrue("D->Y hits = " + hitsD, hitsD >= reps - 1);
        assertTrue("D1->C hits = " + hitsD1, hitsD1 >= reps - 2);
        assertTrue("Xc->C hits = " + hitsXc, hitsXc >= reps - 2);
    }

    // ---------------------------------------------------------------- coefficient t-tests

    /**
     * The per-stratum t-test for the null coefficient of Z should reject near the nominal level, and strata too small
     * to leave residual degrees of freedom should report NaN rather than a number.
     */
    @Test
    public void testCoefficientTTests() {
        RandomUtil.getInstance().setSeed(884428L);
        int reps = 150, rejections = 0, tested = 0;
        Integer tz = null;
        for (int rep = 0; rep < reps; rep++) {
            ContFixture f = contFixture(1.0, 0.0, new double[]{0, 1, 2});
            DataSet ds = sample(f.im, 300);
            int yi = f.pm.indexOf(f.y);
            if (tz == null) {
                int[] cps = f.pm.getContinuousParents(yi);
                for (int t = 0; t < cps.length; t++) if (f.pm.getNodes()[cps[t]].equals(f.z)) tz = t;
            }
            double[][][] p = HybridCgEdgeSignificance.coefficientPValues(f.im, ds);
            for (int row = 0; row < 3; row++) {
                double pv = p[yi][row][tz];
                if (Double.isNaN(pv)) continue;
                tested++;
                if (pv <= 0.05) rejections++;
            }
        }
        double rate = rejections / (double) tested;
        assertTrue("null t-test rejection rate = " + rate, rate >= 0.02 && rate <= 0.09);

        ContFixture f = contFixture(1.0, 0.0, new double[]{0, 0, 0});
        DataSet tiny = sample(f.im, 6);
        double[][][] p = HybridCgEdgeSignificance.coefficientPValues(f.im, tiny);
        int yi = f.pm.indexOf(f.y);
        for (double[] row : p[yi]) for (double v : row) assertTrue(Double.isNaN(v));
    }

    // ---------------------------------------------------------------- missing data

    /**
     * Cases with a missing value anywhere in the child's family are dropped (available-case), matching the
     * estimator's policy: n reflects the drop, no exception is thrown, and p-values stay in [0, 1].
     */
    @Test
    public void testAvailableCaseHandling() {
        RandomUtil.getInstance().setSeed(884429L);
        ContFixture f = contFixture(1.0, 0.5, new double[]{0, 1, 2});
        DataSet ds = sample(f.im, 500);
        int yCol = ds.getColumnIndex(ds.getVariable("Y"));
        int dCol = ds.getColumnIndex(ds.getVariable("D"));
        for (int r = 0; r < 50; r++) ds.setDouble(r, yCol, Double.NaN);
        for (int r = 50; r < 80; r++) ds.setInt(r, dCol, -99);

        Map<Edge, Result> res = HybridCgEdgeSignificance.compute(f.im, ds);
        assertFalse(res.isEmpty());
        for (Result r : res.values()) {
            assertEquals(420, r.n());
            if (r.testable()) {
                assertTrue(r.pValue() >= 0.0 && r.pValue() <= 1.0);
            }
        }
    }
}

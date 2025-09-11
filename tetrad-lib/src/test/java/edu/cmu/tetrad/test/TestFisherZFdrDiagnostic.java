package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.CachingIndependenceTest;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndTestFisherZ.ShrinkageMode;
import edu.cmu.tetrad.sem.CyclicStableUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.*;

import static java.lang.Math.abs;
import static org.junit.Assert.assertTrue;

/**
 * Diagnostic grid for FDR-on-large-p's as a reporting tool (not used inside algorithms).
 * - Builds the "simple" cyclic graph X->Y, W->Z, and Y<->Z,
 * - Simulates with stable cyclic init (fixed spectral radius),
 * - Runs Fisher-Z (Ledoit–Wolf),
 * - Counts "extras" (accepted independencies not among the two we expect),
 * - Applies BH to 1-p (so large p's become small p's) and reports the reduction.
 */
public class TestFisherZFdrDiagnostic {

    // ---- knobs ----
    static final double ALPHA = 0.01;     // accept independence if p > ALPHA
    static final double Q_FDR = 0.05;     // BH level, applied to 1 - p
    static final Double TAU_R = 0.02;     // optional |rho| gate for "practical" independence (null to disable)

    static final int N_TRIALS = 50;       // trials per cell (seeds 1000..1000+N_TRIALS-1)
    static final double CYCLIC_COEF_LOW  = 0.20;
    static final double CYCLIC_COEF_HIGH = 1.00;

    // grid
    static final int[]    N_GRID   = { 200, 500, 1000 };
    static final double[] R_GRID   = { 0.9, 0.7, 0.5 }; // spectral radius targets (harder → 0.9)
    static final boolean  POSITIVE_ONLY = true;         // keep simple & stable for this diagnostic

    @Test
    public void fdrDiagnosticGrid() throws Exception {
        // header
        System.out.println("\n=== FisherZ FDR Diagnostic Grid (reporting only) ===");
        System.out.println("FDR is NOT applied inside CCD/FCI; this test just reports how many 'extras' collapse after FDR.\n");

        // easy sanity cell: N=1000, r=0.6
        sanityCellExpectedIndependencies(1000, 0.6);

        // grid sweep
        for (int n : N_GRID) {
            for (double r : R_GRID) {
                CellSummary summary = runCell(n, r, N_TRIALS);
                printSummary(n, r, summary);
            }
        }
    }

    /* ===== core cell runner ===== */

    private static CellSummary runCell(int N, double radius, int trials) throws Exception {
        int extrasRaw = 0, extrasFdr = 0, testsTotal = 0;
        int okExpectedBoth = 0;

        for (int t = 0; t < trials; t++) {

            // graph: X->Y, W->Z, Y<->Z
            Node x = new ContinuousVariable("x");
            Node y = new ContinuousVariable("Y");
            Node z = new ContinuousVariable("Z");
            Node w = new ContinuousVariable("w");

            List<Node> nodes = Arrays.asList(x, y, z, w);
            Graph g = new EdgeListGraph(nodes);
            g.addDirectedEdge(x, y);
            g.addDirectedEdge(w, z);
            g.addDirectedEdge(y, z);
            g.addDirectedEdge(z, y);

            // parameters
            Parameters pars = new Parameters();
            pars.set(Params.COEF_SYMMETRIC, !POSITIVE_ONLY);
            pars.set(Params.COEF_LOW, 0.30);
            pars.set(Params.COEF_HIGH, 0.60);

            // simulate with fixed spectral radius for SCCs
            SemIm.CyclicSimResult result = CyclicStableUtils.simulateStableProductCapped(
                    g, N, radius, CYCLIC_COEF_LOW, CYCLIC_COEF_HIGH,
                    RandomUtil.getInstance().nextLong(), pars);

            // Fisher-Z + Ledoit–Wolf
            IndTestFisherZ base = new IndTestFisherZ(result.dataSet(), ALPHA);
            base.setShrinkageMode(ShrinkageMode.LEDOIT_WOLF);
            CachingIndependenceTest test = new CachingIndependenceTest(base);

            // expected independencies
            boolean e1 = test.checkIndependence(x, w).isIndependent();
            boolean e2 = test.checkIndependence(x, w, new LinkedHashSet<>(Arrays.asList(y, z))).isIndependent();
            if (e1 && e2) okExpectedBoth++;

            // enumerate tests up to |Z|<=2 & collect extras
            List<CI> extras = new ArrayList<>();
            int m = 0;

            for (int i = 0; i < nodes.size(); i++) {
                for (int j = 0; j < nodes.size(); j++) {
                    if (i == j) continue;
                    Node a = nodes.get(i), b = nodes.get(j);

                    // |C| = 0
                    m++;
                    var r0 = test.checkIndependence(a, b);
                    Double rho0 = base.getLastR();
                    if (!isExpectedPair(a, b, x, w) && acceptIndependence(r0.getPValue(), rho0)) {
                        extras.add(new CI(a, b, Collections.emptySet(), r0.getPValue(), rho0));
                    }

                    // |C| = 1
                    for (int k = 0; k < nodes.size(); k++) {
                        if (k == i || k == j) continue;
                        Node c = nodes.get(k);
                        Set<Node> C1 = Set.of(c);
                        m++;
                        var r1 = test.checkIndependence(a, b, C1);
                        Double rho1 = base.getLastR();
                        if (!isExpectedPair(a, b, x, w) && acceptIndependence(r1.getPValue(), rho1)) {
                            extras.add(new CI(a, b, C1, r1.getPValue(), rho1));
                        }
                    }

                    // |C| = 2
                    for (int k = 0; k < nodes.size(); k++) {
                        if (k == i || k == j) continue;
                        for (int l = k + 1; l < nodes.size(); l++) {
                            if (l == i || l == j) continue;
                            Node c = nodes.get(k), d = nodes.get(l);
                            Set<Node> C2 = new LinkedHashSet<>(Arrays.asList(c, d));
                            m++;
                            var r2 = test.checkIndependence(a, b, C2);
                            Double rho2 = base.getLastR();
                            boolean isExpCond = isExpectedPair(a, b, x, w) && C2.equals(new LinkedHashSet<>(Arrays.asList(y, z)));
                            if (!isExpCond && acceptIndependence(r2.getPValue(), rho2)) {
                                extras.add(new CI(a, b, C2, r2.getPValue(), rho2));
                            }
                        }
                    }
                }
            }

            testsTotal += m;
            extrasRaw += extras.size();
            extrasFdr += benjaminiHochbergOnLargeP(extras, Q_FDR).size();
        }

        return new CellSummary(trials, testsTotal / trials, okExpectedBoth, extrasRaw / (double) trials, extrasFdr / (double) trials);
    }

    /* ===== minimal assertion: easy cell should pass both expected independencies often ===== */

    private static void sanityCellExpectedIndependencies(int N, double radius) throws Exception {
        CellSummary s = runCell(N, radius, 5);
        // Expect both independencies pass frequently in easy setting
        assertTrue("Expected independencies too weak in easy cell",
                s.okExpectedBoth >= 4 /* of 5 */);
    }

    /* ===== helpers ===== */

    private static boolean isExpectedPair(Node a, Node b, Node X, Node W) {
        return (a == X && b == W) || (a == W && b == X);
    }

    private static boolean acceptIndependence(double p, Double rhoAbs) {
        if (p <= ALPHA) return false;
        if (TAU_R != null && rhoAbs != null && abs(rhoAbs) > TAU_R) return false;
        return true;
    }

    /** Apply BH to 1-p (so "large p" becomes "small p'"), keep items that survive. */
    private static <T extends CI> List<T> benjaminiHochbergOnLargeP(List<T> items, double q) {
        if (items.isEmpty()) return items;
        record Pair(int idx, double pPrime) {}
        List<Pair> arr = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            arr.add(new Pair(i, 1.0 - items.get(i).p()));
        }
        arr.sort(Comparator.comparingDouble(a -> a.pPrime)); // ascending p'
        int m = arr.size(), k = -1;
        for (int i = 1; i <= m; i++) {
            double thr = (i * q) / m;
            if (arr.get(i - 1).pPrime <= thr) k = i;
        }
        if (k < 0) return Collections.emptyList();

        List<Integer> keepIdx = new ArrayList<>();
        for (int i = 0; i < k; i++) keepIdx.add(arr.get(i).idx);
        keepIdx.sort((i1, i2) -> Double.compare(items.get(i2).p(), items.get(i1).p())); // show by decreasing p
        List<T> kept = new ArrayList<>();
        for (int idx : keepIdx) kept.add(items.get(idx));
        return kept;
    }

    private static void printSummary(int N, double r, CellSummary s) {
        System.out.printf(Locale.US,
                "N=%4d, radius=%.2f  |  expected-both=%2d/%2d, extras(raw)=%.2f, extras(FDR)=%.2f  (m≈%d tests/run)%n",
                N, r, s.okExpectedBoth, s.trials, s.extrasRawMean, s.extrasFdrMean, s.testsPerRun);
    }

    /* ===== tiny data holders ===== */

    private record CI(Node a, Node b, Set<Node> cond, double p, Double r) {}

    private record CellSummary(int trials, int testsPerRun, int okExpectedBoth,
                               double extrasRawMean, double extrasFdrMean) {}
}
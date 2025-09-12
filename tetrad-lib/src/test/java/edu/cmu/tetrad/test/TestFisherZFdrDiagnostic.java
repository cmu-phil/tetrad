package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Ccd;
import edu.cmu.tetrad.search.test.CachingIndependenceTest;
import edu.cmu.tetrad.search.test.IndTestFdrWrapper;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.CyclicStableUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.*;

import static edu.cmu.tetrad.search.test.IndTestFisherZ.ShrinkageMode.LEDOIT_WOLF;
import static java.lang.Math.abs;
import static org.junit.Assert.assertTrue;

/**
 * Diagnostic grid for FDR-on-large-p's as a reporting tool (not used inside algorithms). - Builds the "simple" cyclic
 * graph X->Y, W->Z, and Y<->Z, - Simulates with stable cyclic init (fixed spectral radius), - Runs Fisher-Z
 * (Ledoit–Wolf), - Counts "extras" (accepted independencies not among the two we expect), - Applies BH to 1-p (so large
 * p's become small p's) and reports the reduction.
 */
public class TestFisherZFdrDiagnostic {

    // ---- knobs ----
    static final double ALPHA = 0.01;     // accept independence if p > ALPHA
    static final double Q_FDR = 0.01;     // BH level, applied to 1 - p
    static final Double TAU_R = 0.02;     // optional |rho| gate for "practical" independence (null to disable)

    static final int N_TRIALS = 50;       // trials per cell (seeds 1000..1000+N_TRIALS-1)
    static final int N = 2000;
    static final double CYCLIC_COEF_LOW = 0.20;
    static final double CYCLIC_COEF_HIGH = 0.80;

    // grid
    static final int[] N_GRID = {200, 500, 1000};
    static final double[] R_GRID = {0.9, 0.7, 0.5}; // spectral radius targets (harder → 0.9)
    static final boolean POSITIVE_ONLY = true;         // keep simple & stable for this diagnostic

    // 1) Respect N, and pick which stabilizer you want.
    private static CellSummary runCell(int N, double radius, int trials) throws Exception {
        int extrasRaw = 0, extrasFdr = 0, testsTotal = 0;
        int okExpectedBoth = 0;

        for (int t = 0; t < trials; t++) {
            SemIm.CyclicSimResult result = getCanonicalModelData(N, radius); // <-- use args
            List<Node> vars = result.dataSet().getVariables();

            // 2) Bind nodes by name (order-agnostic)
            Node x = find(vars, "x");
            Node w = find(vars, "w");
            Node y = find(vars, "Y");
            Node z = find(vars, "Z");
            final Set<Node> YZ = new LinkedHashSet<>(Arrays.asList(y, z)); // reuse

            IndTestFisherZ base = new IndTestFisherZ(result.dataSet(), ALPHA);
            base.setShrinkageMode(LEDOIT_WOLF);
            CachingIndependenceTest test = new CachingIndependenceTest(base);

            boolean e1 = test.checkIndependence(x, w).isIndependent();
            boolean e2 = test.checkIndependence(x, w, YZ).isIndependent();
            if (e1 && e2) okExpectedBoth++;

            List<CI> extras = new ArrayList<>();
            int m = 0;

            for (int i = 0; i < vars.size(); i++) {
                for (int j = 0; j < vars.size(); j++) {
                    if (i == j) continue;
                    Node a = vars.get(i), b = vars.get(j);

                    // |C| = 0
                    m++;
                    var r0 = test.checkIndependence(a, b);
                    Double rho0 = base.getLastR(); // ensure this exists
                    if (!isExpectedPair(a, b, x, w) && acceptIndependence(r0.getPValue(), rho0)) {
                        extras.add(new CI(a, b, Collections.emptySet(), r0.getPValue(), rho0));
                    }

                    // |C| = 1
                    for (int k = 0; k < vars.size(); k++) {
                        if (k == i || k == j) continue;
                        Node c = vars.get(k);
                        Set<Node> C1 = Set.of(c);
                        m++;
                        var r1 = test.checkIndependence(a, b, C1);
                        Double rho1 = base.getLastR();
                        if (!isExpectedPair(a, b, x, w) && acceptIndependence(r1.getPValue(), rho1)) {
                            extras.add(new CI(a, b, C1, r1.getPValue(), rho1));
                        }
                    }

                    // |C| = 2
                    for (int k = 0; k < vars.size(); k++) {
                        if (k == i || k == j) continue;
                        for (int l = k + 1; l < vars.size(); l++) {
                            if (l == i || l == j) continue;
                            Node c = vars.get(k), d = vars.get(l);
                            Set<Node> C2 = new LinkedHashSet<>(Arrays.asList(c, d));
                            m++;
                            var r2 = test.checkIndependence(a, b, C2);
                            Double rho2 = base.getLastR();
                            boolean isExpCond = isExpectedPair(a, b, x, w) && C2.equals(YZ);
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

        return new CellSummary(trials, testsTotal / trials, okExpectedBoth,
                extrasRaw / (double) trials, extrasFdr / (double) trials);
    }

    // 3) Make the generator’s parameter mean what it says.
//    (A) If you meant FIXED RADIUS:
    private static SemIm.CyclicSimResult getCanonicalModelData(int N, double radius) {
        // graph: X->Y, W->Z, Y<->Z
        Node x = new ContinuousVariable("x");
        Node y = new ContinuousVariable("Y");
        Node z = new ContinuousVariable("Z");
        Node w = new ContinuousVariable("w");

        Graph g = new EdgeListGraph(Arrays.asList(x, y, z, w));
        g.addDirectedEdge(x, y);
        g.addDirectedEdge(w, z);
        g.addDirectedEdge(y, z);
        g.addDirectedEdge(z, y);

        Parameters pars = new Parameters();
        pars.set(Params.COEF_SYMMETRIC, !POSITIVE_ONLY);
        pars.set(Params.COEF_LOW, 0.30);
        pars.set(Params.COEF_HIGH, 0.60);

        return CyclicStableUtils.simulateStableFixedRadius(
                g, N, radius, CYCLIC_COEF_LOW, CYCLIC_COEF_HIGH,
                RandomUtil.getInstance().nextLong(), pars
        );
    }

    // 4) Robust node finder by name
    private static Node find(List<Node> vars, String name) {
        for (Node v : vars) if (name.equals(v.getName())) return v;
        throw new IllegalStateException("Variable not found: " + name);
    }


    private static void sanityCellExpectedIndependencies(int N, double radius) throws Exception {
        CellSummary s = runCell(N, radius, 5);
        // Expect both independencies pass frequently in easy setting
        assertTrue("Expected independencies too weak in easy cell",
                s.okExpectedBoth >= 4 /* of 5 */);
    }

    /* ===== core cell runner ===== */

    private static boolean isExpectedPair(Node a, Node b, Node X, Node W) {
        return (a == X && b == W) || (a == W && b == X);
    }

    private static boolean acceptIndependence(double p, Double rhoAbs) {
        if (p <= ALPHA) return false;
        if (TAU_R != null && rhoAbs != null && abs(rhoAbs) > TAU_R) return false;
        return true;
    }

    /* ===== minimal assertion: easy cell should pass both expected independencies often ===== */

    /**
     * Apply BH to 1-p (so "large p" becomes "small p'"), keep items that survive.
     */
    private static <T extends CI> List<T> benjaminiHochbergOnLargeP(List<T> items, double q) {
        if (items.isEmpty()) return items;
        record Pair(int idx, double pPrime) {
        }
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

    /* ===== helpers ===== */

    private static void printSummary(int N, double r, CellSummary s) {
        System.out.printf(Locale.US,
                "N=%4d, radius=%.2f  |  expected-both=%2d/%2d, extras(raw)=%.2f, extras(FDR)=%.2f  (m≈%d tests/run)%n",
                N, r, s.okExpectedBoth, s.trials, s.extrasRawMean, s.extrasFdrMean, s.testsPerRun);
    }

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

    @Test
    public void fdrDiagnosticGridWithCyclicStable() throws Exception {
        SemIm.CyclicSimResult result = getCanonicalModelData(N, 0.6);

        IndTestFisherZ base = new IndTestFisherZ(result.dataSet(), ALPHA);
        base.setShrinkageMode(LEDOIT_WOLF);

        IndTestFdrWrapper wrap = new IndTestFdrWrapper(base, IndTestFdrWrapper.FdrMode.BH, /*q=*/Q_FDR, IndTestFdrWrapper.Scope.BY_COND_SET);
        wrap.setVerbose(true);

        int maxEpochs = 5, tauChanges = 0;
        int changes;

        wrap.startRecordingEpoch();
        Ccd ccd = new Ccd(wrap);
        ccd.setVerbose(true);
        Graph g = ccd.search();   // Epoch 0: record p's, baseline decisions (or just cache-only)

        // Freeze FDR cutoffs from observed p's
        wrap.computeCutoffsFromRecordedPvals();

        for (int epoch = 1; epoch <= maxEpochs; epoch++) {
            g = ccd.search();     // decisions now use α* (global or per-|Z|)
            changes = wrap.countMindChangesAndSnapshot();
            if (changes <= tauChanges) break;
        }

        System.out.println(g);
    }

    private record CI(Node a, Node b, Set<Node> cond, double p, Double r) {
    }

    private record CellSummary(int trials, int testsPerRun, int okExpectedBoth,
                               double extrasRawMean, double extrasFdrMean) {
    }
}
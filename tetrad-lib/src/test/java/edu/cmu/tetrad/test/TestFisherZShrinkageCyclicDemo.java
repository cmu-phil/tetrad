///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.CachingIndependenceTest;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndTestFisherZ.ShrinkageMode;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.*;

import static java.lang.Math.abs;

public class TestFisherZShrinkageCyclicDemo {

    // Experiment knobs
    static final int N_TRIALS = 20;         // number of seeds/runs
    static final double COEF_LOW = 0.20;    // keep cycle product far from 1
    static final double COEF_HIGH = 0.40;

    // Ridge value when using RIDGE mode
    static final double RIDGE = 1e-3;

    // --- knobs you can tweak ---
    static final double ALPHA = 0.01;   // raw alpha for accepting independence (p > alpha)
    static final int N = 1000;   // sample size per run
    static final double Q_FDR = 0.05;   // FDR level (for large p's = accepted independencies)
    static final Double TAU_R = 0.02;   // practical independence threshold on |rho| (null to disable)

    static void printIndependencies() throws InterruptedException {
        // ---- graph under test ----
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

        // ---- parameters (with sane defaults if unset) ----
        Parameters params = new Parameters();
        params.set(Params.COEF_LOW, 0.30);
        params.set(Params.COEF_HIGH, 0.60);
        params.set(Params.COEF_SYMMETRIC, false);

        params.set(Params.CYCLIC_COEF_LOW, 0.2);
        params.set(Params.CYCLIC_COEF_HIGH, 1.0);
        params.set(Params.CYCLIC_RADIUS, 0.6);        // for FixedRadius
        params.set(Params.CYCLIC_MAX_PROD, 0.5);      // for ProductCapped
        params.set(Params.CYCLIC_COEF_STYLE, 0);      // 0=Auto, 1=FixedRadius, 2=MaxProd, 3=Baseline

        params.set(Params.SAMPLE_SIZE, 1000);
        params.set(Params.SEED, RandomUtil.getInstance().nextLong());

        SemIm.Result result = SemIm.simulatePossibleShrinkage(params, g);

        // ---- independence test with shrinkage + (optional) pinv ----
        IndTestFisherZ base = new IndTestFisherZ(result.dataSet(), 0.01);
        base.setShrinkageMode(result.shrinkageMode());
        if (result.shrinkageMode() == ShrinkageMode.RIDGE)
            base.setRidge(params.getDouble(Params.REGULARIZATION_LAMBDA));

        CachingIndependenceTest test = new CachingIndependenceTest(base);
        test.setVerbose(false);

        // ---- expected independencies ----
        Set<Node> yz = new LinkedHashSet<>(Arrays.asList(y, z));

        var rExp1 = test.checkIndependence(x, w);
        Double rhoExp1 = tryGetLastR(base);

        var rExp2 = test.checkIndependence(x, w, yz);
        Double rhoExp2 = tryGetLastR(base);

        System.out.println("Expected independencies:");
        System.out.printf(Locale.US, "  X â W          : p = %8.5f%s%n",
                rExp1.getPValue(), fmtR(rhoExp1));
        System.out.printf(Locale.US, "  X â W | {Y,Z}  : p = %8.5f%s%n",
                rExp2.getPValue(), fmtR(rhoExp2));

        // ---- enumerate tests up to |C|<=2; collect extras (p > alpha + optional |rho| gate) ----
        List<CI> extras = new ArrayList<>();
        int m = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;
                Node a = nodes.get(i), b = nodes.get(j);

                // |C| = 0
                m++;
                var r0 = test.checkIndependence(a, b);
                Double rho0 = tryGetLastR(base);
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
                    Double rho1 = tryGetLastR(base);
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
                        Double rho2 = tryGetLastR(base);
                        boolean isExpCond = isExpectedPair(a, b, x, w) && C2.equals(yz);
                        if (!isExpCond && acceptIndependence(r2.getPValue(), rho2)) {
                            extras.add(new CI(a, b, C2, r2.getPValue(), rho2));
                        }
                    }
                }
            }
        }

        // ---- reporting ----
        extras.sort(Comparator.comparingDouble((CI ci) -> ci.p).reversed());

        System.out.println();
        System.out.printf("Other independencies passing raw Î±=%.3g (count=%d of %d tests):%n",
                ALPHA, extras.size(), m);
        printCIList(extras, 20);

        List<CI> extrasFdr = benjaminiHochbergOnLargeP(extras, Q_FDR);
        System.out.println();
        System.out.printf("Other independencies after FDR (q=%.3g on large pâs): count=%d of %d%n",
                Q_FDR, extrasFdr.size(), extras.size());
        printCIList(extrasFdr, 20);

        System.out.println();
        System.out.printf("Note: N=%d, shrinkage=%s, ridge=%g; coefâ[%.2f, %.2f], Î±=%.3g, tests m=%d%n",
                result.N(), result.shrinkageMode(), params.getDouble(Params.REGULARIZATION_LAMBDA), params.getDouble(Params.COEF_LOW),
                params.getDouble(Params.COEF_HIGH), ALPHA, m);
    }

    private static boolean isExpectedPair(Node a, Node b, Node X, Node W) {
        // unordered {a,b} == {X,W}
        return (a == X && b == W) || (a == W && b == X);
    }



    /* -------- helpers local to this method (safe getters & shrinkage parser) -------- */

    /* ---------- helpers ---------- */

    private static boolean acceptIndependence(double p, Double rhoAbs) {
        // p > alpha AND (optional) |rho| <= TAU_R
        if (p <= ALPHA) return false;
        if (TAU_R != null && rhoAbs != null && Math.abs(rhoAbs) > TAU_R) return false;
        return true;
    }

    private static void printCIList(List<?> itemsRaw, int maxShow) {
        @SuppressWarnings("unchecked")
        List<Record> items = (List<Record>) itemsRaw;
        int shown = 0;
        for (Object o : items) {
            if (shown++ >= maxShow) {
                System.out.println("  â¦");
                break;
            }
            var ci = (CI) o;
            String cond = ci.cond.isEmpty() ? "â" : prettySet(ci.cond);
            String rho = (ci.r == null) ? "" : String.format(Locale.US, "  |Ï|=%7.4f", Math.abs(ci.r));
            System.out.printf(Locale.US, "  %s â %s | %s : p=%8.5f%s%n",
                    ci.a.getName(), ci.b.getName(), cond, ci.p, rho);
        }
    }

    private static String prettySet(Set<Node> s) {
        StringBuilder b = new StringBuilder("[");
        boolean first = true;
        for (Node n : s) {
            if (!first) b.append(", ");
            b.append(n.getName());
            first = false;
        }
        b.append("]");
        return b.toString();
    }

    private static String fmtR(Double r) {
        if (r == null) return "";
        return String.format(Locale.US, "   |Ï| = %7.4f", Math.abs(r));
    }

    // âFDR on large pâsâ: apply BH to p' = 1 - p, i.e., prefer *larger* p-values.
// This is a pragmatic filter for accepted-independence lists.
    private static <CI> List<CI> benjaminiHochbergOnLargeP(List<CI> items, double q) {
        if (items.isEmpty()) return items;
        class Pair {
            final int idx;
            final double pPrime;

            Pair(int i, double v) {
                idx = i;
                pPrime = v;
            }
        }
        List<Pair> arr = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            @SuppressWarnings("unchecked") var ci = (TestFisherZShrinkageCyclicDemo.CI) items.get(i);
            double pPrime = 1.0 - ci.p();                // big p â small p'
            arr.add(new Pair(i, pPrime));
        }
        arr.sort(Comparator.comparingDouble(a -> a.pPrime)); // ascending p'
        int m = arr.size(), k = -1;
        for (int i = 1; i <= m; i++) {
            double thresh = (i * q) / m;
            if (arr.get(i - 1).pPrime <= thresh) k = i;  // largest k satisfying BH
        }
        if (k < 0) return Collections.emptyList();

        // Keep the top-k by largest p (i.e., smallest p')
        List<Integer> keepIdx = new ArrayList<>();
        for (int i = 0; i < k; i++) keepIdx.add(arr.get(i).idx);

        // Sort chosen indices by decreasing p for nicer output
        keepIdx.sort((i1, i2) -> {
            var ci1 = (TestFisherZShrinkageCyclicDemo.CI) items.get(i1);
            var ci2 = (TestFisherZShrinkageCyclicDemo.CI) items.get(i2);
            return Double.compare(ci2.p(), ci1.p());
        });

        List<CI> kept = new ArrayList<>();
        for (int idx : keepIdx) kept.add(items.get(idx));
        return kept;
    }

    private static Double tryGetLastR(IndTestFisherZ base) {
        try {
            return (Double) IndTestFisherZ.class.getMethod("getLastR").invoke(base);
        } catch (Exception e) {
            return null;
        }
    }

    private static void banner(String s) {
        String line = "================================================================================";
        System.out.println(line);
        System.out.println(center(s, line.length()));
        System.out.println(line);
    }

    private static String center(String s, int width) {
        if (s.length() >= width) return s;
        int pad = (width - s.length()) / 2;
        return " ".repeat(pad) + s;
    }

    private static String padRight(String s, int w) {
        if (s.length() >= w) return s;
        return s + " ".repeat(w - s.length());
    }

    /* ===== helpers ===== */

    @Test
    public void checkIndependenciesForSimpleExmaple() {
        try {
            for (int i = 0; i < 10; i++) {
                System.out.println("\n\n== Run " + (i + 1));
                TestFisherZShrinkageCyclicDemo.printIndependencies();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void runMode(String label, ShrinkageMode mode, double ridge) throws Exception {
        final Node x = new ContinuousVariable("x");
        final Node y = new ContinuousVariable("Y");
        final Node z = new ContinuousVariable("Z");
        final Node w = new ContinuousVariable("w");

        // Accumulators
        int successBoth = 0; // X â W  AND  X â W | {Y,Z}
        double sumAbsR_xw = 0.0, sumP_xw = 0.0;
        double sumAbsR_xw_yz = 0.0, sumP_xw_yz = 0.0;

        // Keep some example rows to show Peter
        final int K = Math.min(5, N_TRIALS);
        List<String> sampleRows = new ArrayList<>();

        for (int t = 0; t < N_TRIALS; t++) {
            // Seed the whole pipeline
            RandomUtil.getInstance().setSeed(1000 + t);

            // Build graph: X->Y, W->Z, Y->Z, Z->Y  (feedback block on Y,Z)
            Graph g = new EdgeListGraph(Arrays.asList(x, y, z, w));
            g.addDirectedEdge(x, y);
            g.addDirectedEdge(w, z);
            g.addDirectedEdge(y, z);
            g.addDirectedEdge(z, y);

            // Parameterize SEM with conservative gains: coef in [COEF_LOW, COEF_HIGH],
            // asymmetric (positive), fixed noise variances default to Tetrad's.
            Parameters par = new Parameters();
            par.set(Params.COEF_SYMMETRIC, false);
            par.set(Params.COEF_LOW, COEF_LOW);
            par.set(Params.COEF_HIGH, COEF_HIGH);

            SemPm pm = new SemPm(g);
            SemIm im = new SemIm(pm, par);
            DataSet ds = im.simulateData(N, false);

            // Build Fisher-Z with given shrinkage
            IndTestFisherZ base = new IndTestFisherZ(ds, ALPHA);
            base.setShrinkageMode(mode);
            if (mode == ShrinkageMode.RIDGE) base.setRidge(ridge);

            // (Optional) also pass through lambda used by your StatUtils if desired
            base.setLambda(0.0);

            CachingIndependenceTest test = new CachingIndependenceTest(base);

            // Two population truths to check:
            // 1) X â W
            boolean indep_xw = test.checkIndependence(x, w).isIndependent();

            // 2) X â W | {Y, Z}
            Set<Node> yz = new LinkedHashSet<>(Arrays.asList(y, z));
            boolean indep_xw_yz = test.checkIndependence(x, w, yz).isIndependent();

            // For reporting, fetch p-values & |rho|
            double p_xw = test.checkIndependence(x, w).getPValue();
            double r_xw = ((IndTestFisherZ) test.getBaseTest()).getRho(); // helper below

            double p_xw_yz = test.checkIndependence(x, w, yz).getPValue();
            double r_xw_yz = ((IndTestFisherZ) test.getBaseTest()).getRho();

            sumAbsR_xw += abs(r_xw);
            sumP_xw += p_xw;

            sumAbsR_xw_yz += abs(r_xw_yz);
            sumP_xw_yz += p_xw_yz;

            if (indep_xw && indep_xw_yz) successBoth++;

            if (t < K) {
                sampleRows.add(String.format(Locale.US,
                        " trial=%2d  XâW: p=%8.5f |Ï|=%7.4f   XâW|{Y,Z}: p=%8.5f |Ï|=%7.4f",
                        t + 1, p_xw, abs(r_xw), p_xw_yz, abs(r_xw_yz)));
            }
        }

        // Print summary
        System.out.println("ââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââ");
        System.out.printf("â Mode: %-12s  (Shrinkage=%-12s)%37sâ%n", label, mode, "");
        if (mode == ShrinkageMode.RIDGE) {
            System.out.printf("â   ridge = %.0e%64sâ%n", ridge, "");
        }
        System.out.println("ââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââ¤");
        System.out.printf("â Success rate (both independences true): %3d / %3d = %6.2f%%%21sâ%n",
                successBoth, N_TRIALS, 100.0 * successBoth / N_TRIALS, "");
        System.out.println("ââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââ¤");
        System.out.printf("â Averages over trials:%57sâ%n", "");
        System.out.printf("â   X â W         :  mean |Ï| = %7.4f   mean p = %8.5f%17sâ%n",
                sumAbsR_xw / N_TRIALS, sumP_xw / N_TRIALS, "");
        System.out.printf("â   X â W | {Y,Z} :  mean |Ï| = %7.4f   mean p = %8.5f%17sâ%n",
                sumAbsR_xw_yz / N_TRIALS, sumP_xw_yz / N_TRIALS, "");
        System.out.println("ââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââ¤");
        System.out.printf("â Example trials:%62sâ%n", "");
        for (String row : sampleRows) {
            System.out.printf("â %s %sâ%n", row, padRight("", 74 - row.length()));
        }
        System.out.println("ââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââ");
        System.out.println();
    }

    // record used above
    record CI(Node a, Node b, Set<Node> cond, double p, Double r) {
    }

    /* Small hook to read the last |rho| used inside Fisher-Z */
    private static class RhoPeek extends CachingIndependenceTest {
        public RhoPeek(IndTestFisherZ base) {
            super(base);
        }

        public IndTestFisherZ getBaseTest() {
            return (IndTestFisherZ) super.getBaseTest();
        }
    }
}

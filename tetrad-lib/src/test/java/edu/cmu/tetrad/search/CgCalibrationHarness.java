package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndTestConditionalGaussianLrt;
import org.apache.commons.math3.distribution.UniformRealDistribution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Hand-run calibration harness for the conditional Gaussian LRT
 * (IndTestConditionalGaussianLrt), motivated by the insurance-data session in
 * which the CG test certified a graph the BF-LRT rejected, and by prior
 * reports of CG trouble in the Markov checker.
 *
 * <p>Two levels:
 *
 * <p>LEVEL 1 (single-fact size): for each scenario, R replications of data
 * generated with X _||_ Y | Z true in the conditional Gaussian family; the
 * CG-LRT p-value for that single fact is recorded. Reported: rejection rates
 * at alpha .05/.01 (nominal: .05/.01) and the Anderson-Darling uniformity
 * p-value of the R p-values (should not be systematically tiny). Structural
 * suspects the scenarios target:
 * (a) cell pruning (minSampleSizePerCell) making the two LRT models fit on
 *     DIFFERENT row supports with differently-renormalized multinomials;
 * (b) dof counted over ALL cells (f(A) is a product of category counts)
 *     including cells that were pruned or are empty;
 * (c) the discretize path (continuous predictors of a discrete target
 *     replaced by 3-category surrogates);
 * (d) target asymmetry: checkIndependence(x, y, z) always models y as the
 *     target, so (x,y|z) and (y,x|z) are different tests on mixed pairs.
 *
 * <p>LEVEL 2 (Markov-checker size): data generated from a TRUE conditional
 * Gaussian DAG; the Markov check (ordered local Markov) is run on the true
 * graph with the CG test; the fraction of replications with ad_ind < .05 is
 * reported (nominal: .05).
 *
 * <p>Run: java -cp <classes>:tetrad-current.jar edu.cmu.tetrad.search.CgCalibrationHarness
 */
public final class CgCalibrationHarness {

    private static final long SEED = 20260812L;

    public static void main(String[] args) {
        int R = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        int RMC = args.length > 1 ? Integer.parseInt(args[1]) : 200;

        System.out.println("=== LEVEL 1: single-fact size (R = " + R + ") ===");
        System.out.println("scenario                                     n      rej@.05  rej@.01  AD-unif-p");

        for (int n : new int[]{200, 1000}) {
            scenarioAllContinuous(R, n);
            scenarioBalancedDiscreteZ(R, n);
            scenarioRareCellDiscreteZ(R, n, 4);
            scenarioRareCellDiscreteZ(R, n, 1);   // pruning mechanism isolation
            scenarioSparseDiscreteTable(R, n);
            scenarioDiscreteXContinuousY(R, n);
            scenarioDiscreteTarget(R, n, true);
            scenarioDiscreteTarget(R, n, false);
        }

        scenarioOrderAsymmetry(R, 500);
        scenarioPowerSanity(R, 500);
        scenarioMixedPowerDiscTarget(R, 500);
        scenarioMixedPowerDiscX(R, 500);
        scenarioMarginalMixture(R, 500);
        scenarioMarginalMixture(R, 1500);
        scenarioMarginalMixture(R, 5000);
        scenarioFloorSensitivity(R, 200);
        scenarioIntegerValuedYBootstrap(R, 800);

        System.out.println();
        System.out.println("=== LEVEL 2: Markov-checker size on TRUE CG DAG (R = " + RMC + ") ===");
        markovLevel(RMC, 500, false);
        markovLevel(RMC, 500, true);
        markovLevel(RMC, 1500, false);
        markovLevel(RMC, 1500, true);
    }

    // ---------- Level 1 scenarios ----------

    /** S1: X <- Z -> Y, all continuous linear Gaussian. Baseline sanity. */
    private static void scenarioAllContinuous(int R, int n) {
        List<Double> ps = new ArrayList<>();
        Random rng = new Random(SEED + n);
        for (int r = 0; r < R; r++) {
            double[] z = new double[n], x = new double[n], y = new double[n];
            for (int i = 0; i < n; i++) {
                z[i] = rng.nextGaussian();
                x[i] = 0.8 * z[i] + rng.nextGaussian();
                y[i] = -0.6 * z[i] + rng.nextGaussian();
            }
            DataSet d = mixed(n, new String[]{"X", "Y", "Z"}, new double[][]{x, y, z}, new int[][]{}, new int[]{});
            ps.add(p(d, "X", "Y", List.of("Z"), true, 4));
        }
        report("S1 all-continuous, X_||_Y|Z", n, ps);
    }

    /** S2: Z discrete 3 balanced categories; X, Y continuous with cell-specific means. Null. */
    private static void scenarioBalancedDiscreteZ(int R, int n) {
        List<Double> ps = new ArrayList<>();
        Random rng = new Random(SEED + 1000 + n);
        double[] muX = {-1, 0, 1}, muY = {2, 0, -2};
        for (int r = 0; r < R; r++) {
            int[] z = new int[n];
            double[] x = new double[n], y = new double[n];
            for (int i = 0; i < n; i++) {
                z[i] = rng.nextInt(3);
                x[i] = muX[z[i]] + rng.nextGaussian();
                y[i] = muY[z[i]] + (1 + 0.3 * z[i]) * rng.nextGaussian();
            }
            DataSet d = mixed(n, new String[]{"X", "Y", "Z"}, new double[][]{x, y}, new int[][]{z}, new int[]{3});
            ps.add(p(d, "X", "Y", List.of("Z"), true, 4));
        }
        report("S2 Z disc(3) balanced, X_||_Y|Z", n, ps);
    }

    /** S3: Z discrete 6 categories with rare cells (probs .4,.3,.15,.1,.03,.02): pruning active. */
    private static void scenarioRareCellDiscreteZ(int R, int n, int minCell) {
        List<Double> ps = new ArrayList<>();
        Random rng = new Random(SEED + 2000 + n);
        double[] cum = {.4, .7, .85, .95, .98, 1.0};
        for (int r = 0; r < R; r++) {
            int[] z = new int[n];
            double[] x = new double[n], y = new double[n];
            for (int i = 0; i < n; i++) {
                double u = rng.nextDouble();
                int c = 0;
                while (u > cum[c]) c++;
                z[i] = c;
                x[i] = c + rng.nextGaussian();
                y[i] = -c + rng.nextGaussian();
            }
            DataSet d = mixed(n, new String[]{"X", "Y", "Z"}, new double[][]{x, y}, new int[][]{z}, new int[]{6});
            ps.add(p(d, "X", "Y", List.of("Z"), true, minCell));
        }
        report("S3 Z disc(6) rare cells, minCell=" + minCell, n, ps);
    }

    /**
     * S10 (added 2026-8-25): all-discrete sparse table. X, Y, Z 4-level with skewed marginals (.55/.30/.10/.05),
     * X and Y each depend on Z, X _||_ Y | Z; at these n the 64 (x, y, z) cells carry many sampling zeros. Targets
     * the multinomial dof rule of getLikelihoodRatio: counting target levels only where observed per conditioning
     * cell deflates dof on sparse tables (rejection .21-.42); charging (r - 1) per observed conditioning cell gives
     * ~.07. Pinned by TestCgLrtSparseTableDof.
     */
    private static void scenarioSparseDiscreteTable(int R, int n) {
        List<Double> ps = new ArrayList<>();
        Random rng = new Random(SEED + 9000 + n);
        for (int r = 0; r < R; r++) {
            DataSet d = TestCgLrtSparseTableDof.sparseNull(n, rng);
            ps.add(p(d, "X", "Y", List.of("Z"), true, 4));
        }
        report("S10 sparse disc(4)x(4)|disc(4) table", n, ps);
    }

    /**
     * S4: X discrete(3) depends on continuous Z; Y continuous depends on Z.
     * X _||_ Y | Z. Target Y continuous; X joins the discrete set in model 1,
     * so model 1 partitions rows into X's cells while model 0 has no cells.
     */
    private static void scenarioDiscreteXContinuousY(int R, int n) {
        List<Double> ps = new ArrayList<>();
        Random rng = new Random(SEED + 3000 + n);
        for (int r = 0; r < R; r++) {
            double[] z = new double[n], y = new double[n];
            int[] x = new int[n];
            for (int i = 0; i < n; i++) {
                z[i] = rng.nextGaussian();
                double p1 = 1.0 / (1.0 + Math.exp(-1.5 * z[i]));
                double u = rng.nextDouble();
                x[i] = u < 0.5 * p1 ? 0 : (u < p1 ? 1 : 2);
                y[i] = 0.7 * z[i] + rng.nextGaussian();
            }
            DataSet d = mixed(n, new String[]{"Y", "Z", "X"}, new double[][]{y, z}, new int[][]{x}, new int[]{3});
            ps.add(p(d, "X", "Y", List.of("Z"), true, 4));
        }
        report("S4 X disc(3)<-Z cont->Y cont", n, ps);
    }

    /**
     * S5: target discrete. Y discrete(3) depends on discrete W; X continuous
     * depends on W. X _||_ Y | W. Testing (X, Y | W) with Y the modeled
     * target triggers the discretize path for X when discretize = true.
     */
    private static void scenarioDiscreteTarget(int R, int n, boolean discretize) {
        List<Double> ps = new ArrayList<>();
        Random rng = new Random(SEED + 4000 + n);
        for (int r = 0; r < R; r++) {
            int[] w = new int[n], y = new int[n];
            double[] x = new double[n];
            for (int i = 0; i < n; i++) {
                w[i] = rng.nextInt(2);
                double u = rng.nextDouble();
                y[i] = w[i] == 0 ? (u < .5 ? 0 : (u < .8 ? 1 : 2))
                                 : (u < .2 ? 0 : (u < .5 ? 1 : 2));
                x[i] = (w[i] == 0 ? -1 : 1) + rng.nextGaussian();
            }
            DataSet d = mixed(n, new String[]{"X", "Y", "W"}, new double[][]{x}, new int[][]{y, w}, new int[]{3, 2});
            ps.add(p(d, "X", "Y", List.of("W"), discretize, 4));
        }
        report("S5 Y disc target, discretize=" + discretize, n, ps);
    }

    /** S6: order asymmetry on S5-style data: p(X,Y|W) vs p(Y,X|W). */
    private static void scenarioOrderAsymmetry(int R, int n) {
        Random rng = new Random(SEED + 5000);
        List<Double> pXY = new ArrayList<>(), pYX = new ArrayList<>();
        double maxAbs = 0, sumAbs = 0;
        for (int r = 0; r < R; r++) {
            int[] w = new int[n], y = new int[n];
            double[] x = new double[n];
            for (int i = 0; i < n; i++) {
                w[i] = rng.nextInt(2);
                double u = rng.nextDouble();
                y[i] = w[i] == 0 ? (u < .5 ? 0 : (u < .8 ? 1 : 2))
                                 : (u < .2 ? 0 : (u < .5 ? 1 : 2));
                x[i] = (w[i] == 0 ? -1 : 1) + rng.nextGaussian();
            }
            DataSet d = mixed(n, new String[]{"X", "Y", "W"}, new double[][]{x}, new int[][]{y, w}, new int[]{3, 2});
            double p1 = p(d, "X", "Y", List.of("W"), true, 4);
            double p2 = p(d, "Y", "X", List.of("W"), true, 4);
            pXY.add(p1);
            pYX.add(p2);
            double a = Math.abs(p1 - p2);
            sumAbs += a;
            if (a > maxAbs) maxAbs = a;
        }
        report("S6 order (X,Y|W): Y disc target", n, pXY);
        report("S6 order (Y,X|W): X cont target", n, pYX);
        System.out.printf("   S6 |p1-p2|: mean %.4f, max %.4f%n", sumAbs / R, maxAbs);
    }

    /** S7: power sanity: S1 plus a direct X -> Y effect of 0.3; rejection should be high. */
    private static void scenarioPowerSanity(int R, int n) {
        List<Double> ps = new ArrayList<>();
        Random rng = new Random(SEED + 6000);
        for (int r = 0; r < R; r++) {
            double[] z = new double[n], x = new double[n], y = new double[n];
            for (int i = 0; i < n; i++) {
                z[i] = rng.nextGaussian();
                x[i] = 0.8 * z[i] + rng.nextGaussian();
                y[i] = -0.6 * z[i] + 0.3 * x[i] + rng.nextGaussian();
            }
            DataSet d = mixed(n, new String[]{"X", "Y", "Z"}, new double[][]{x, y, z}, new int[][]{}, new int[]{});
            ps.add(p(d, "X", "Y", List.of("Z"), true, 4));
        }
        report("S7 POWER (beta=.3; want high rej)", n, ps);
    }

    /** S7b POWER: Y disc(3) truly depends on X cont given W disc(2). */
    private static void scenarioMixedPowerDiscTarget(int R, int n) {
        List<Double> ps = new ArrayList<>();
        Random rng = new Random(SEED + 9000);
        for (int r = 0; r < R; r++) {
            int[] w = new int[n], y = new int[n];
            double[] x = new double[n];
            for (int i = 0; i < n; i++) {
                w[i] = rng.nextInt(2);
                x[i] = (w[i] == 0 ? -1 : 1) + rng.nextGaussian();
                double s = 0.5 * x[i] + (w[i] == 0 ? -.3 : .3);
                double p0 = 1 / (1 + Math.exp(-s)), u = rng.nextDouble();
                y[i] = u < 0.6 * p0 ? 0 : (u < p0 ? 1 : 2);
            }
            DataSet d = mixed(n, new String[]{"X", "Y", "W"}, new double[][]{x}, new int[][]{y, w}, new int[]{3, 2});
            ps.add(p(d, "X", "Y", List.of("W"), true, 4));
        }
        report("S7b POWER mixed pair (want high)", n, ps);
    }

    /** S7c POWER: X disc(3) shifts Y cont given Z cont. */
    private static void scenarioMixedPowerDiscX(int R, int n) {
        List<Double> ps = new ArrayList<>();
        Random rng = new Random(SEED + 9500);
        for (int r = 0; r < R; r++) {
            double[] z = new double[n], y = new double[n];
            int[] x = new int[n];
            for (int i = 0; i < n; i++) {
                z[i] = rng.nextGaussian();
                x[i] = rng.nextInt(3);
                y[i] = 0.7 * z[i] + 0.3 * x[i] + rng.nextGaussian();
            }
            DataSet d = mixed(n, new String[]{"Y", "Z", "X"}, new double[][]{y, z}, new int[][]{x}, new int[]{3});
            ps.add(p(d, "X", "Y", List.of("Z"), true, 4));
        }
        report("S7c POWER disc X on cont Y (want high)", n, ps);
    }

    /**
     * S8: marginal-mixture fact. Data exactly from the Level-2 CG DAG, but the
     * tested fact (C1, C3 | C2) omits the discrete variables, so the CG model
     * fits Gaussians to Gaussian MIXTURES. The fact is a true null and partial
     * correlations vanish (linearity), but non-Gaussian tails make the LRT
     * converge to c * chi2 with c != 1: miscalibration that GROWS INTO view
     * with n. This is inherent to the CG model class (its Gaussianity holds
     * conditional on ALL discrete variables, not marginally), not a code bug.
     */
    private static void scenarioMarginalMixture(int R, int n) {
        List<Double> ps = new ArrayList<>();
        Random rng = new Random(SEED + 8000 + n);
        for (int r = 0; r < R; r++) {
            int[] d1 = new int[n], d2 = new int[n];
            double[] c1 = new double[n], c2 = new double[n], c3 = new double[n];
            for (int i = 0; i < n; i++) {
                d1[i] = rng.nextInt(3);
                d2[i] = rng.nextInt(2);
                c1[i] = new double[]{-1, 0, 1}[d1[i]] + rng.nextGaussian();
                c2[i] = 0.7 * c1[i] + (d2[i] == 0 ? -0.5 : 0.5) + rng.nextGaussian();
                c3[i] = -0.8 * c2[i] + rng.nextGaussian();
            }
            DataSet d = mixed(n, new String[]{"C1", "C2", "C3", "D1", "D2"},
                    new double[][]{c1, c2, c3}, new int[][]{d1, d2}, new int[]{3, 2});
            ps.add(p(d, "C1", "C3", List.of("C2"), true, 4));
        }
        report("S8 marginal mixture (C1,C3|C2)", n, ps);
    }

    /**
     * S3 floor sensitivity: same rare-cell design, eligibility floor raised by
     * setting minSampleSizePerCell to multiples of (kMax + 1) = 3. Dropping
     * cells by conditioning-cell size is selection on z only, so size should
     * recover as the floor rises past the regime where 4-6 row cells fit
     * 5-parameter bivariate Gaussians.
     */
    private static void scenarioFloorSensitivity(int R, int n) {
        for (int floor : new int[]{6, 9, 12}) {
            List<Double> ps = new ArrayList<>();
            Random rng = new Random(SEED + 2000 + n);
            double[] cum = {.4, .7, .85, .95, .98, 1.0};
            for (int r = 0; r < R; r++) {
                int[] z = new int[n];
                double[] x = new double[n], y = new double[n];
                for (int i = 0; i < n; i++) {
                    double u = rng.nextDouble();
                    int c = 0;
                    while (u > cum[c]) c++;
                    z[i] = c;
                    x[i] = c + rng.nextGaussian();
                    y[i] = -c + rng.nextGaussian();
                }
                DataSet d = mixed(n, new String[]{"X", "Y", "Z"}, new double[][]{x, y}, new int[][]{z}, new int[]{6});
                ps.add(p(d, "X", "Y", List.of("Z"), true, floor));
            }
            report("S3 floor sensitivity, minCell=" + floor, n, ps);
        }
    }

    // ---------- Level 2: Markov checker on the true graph ----------

    /**
     * True CG DAG: D1(3), D2(2) discrete exogenous; C1 <- D1; C2 <- C1, D2;
     * C3 <- C2. Continuous children linear Gaussian with cell-specific
     * intercepts. rareCells = true makes D1's third category rare (prob .04)
     * to activate pruning at moderate n.
     */
    private static void markovLevel(int R, int n, boolean rareCells) {
        Random rng = new Random(SEED + 7000 + n + (rareCells ? 1 : 0));
        int rejections = 0;
        List<Double> adPs = new ArrayList<>();

        for (int r = 0; r < R; r++) {
            int[] d1 = new int[n], d2 = new int[n];
            double[] c1 = new double[n], c2 = new double[n], c3 = new double[n];
            for (int i = 0; i < n; i++) {
                double u = rng.nextDouble();
                d1[i] = rareCells ? (u < .48 ? 0 : (u < .96 ? 1 : 2))
                                  : (u < 1.0 / 3 ? 0 : (u < 2.0 / 3 ? 1 : 2));
                d2[i] = rng.nextInt(2);
                c1[i] = new double[]{-1, 0, 1}[d1[i]] + rng.nextGaussian();
                c2[i] = 0.7 * c1[i] + (d2[i] == 0 ? -0.5 : 0.5) + rng.nextGaussian();
                c3[i] = -0.8 * c2[i] + rng.nextGaussian();
            }
            DataSet d = mixed(n, new String[]{"C1", "C2", "C3", "D1", "D2"},
                    new double[][]{c1, c2, c3}, new int[][]{d1, d2}, new int[]{3, 2});

            Graph g = new EdgeListGraph(d.getVariables());
            g.addDirectedEdge(node(d, "D1"), node(d, "C1"));
            g.addDirectedEdge(node(d, "C1"), node(d, "C2"));
            g.addDirectedEdge(node(d, "D2"), node(d, "C2"));
            g.addDirectedEdge(node(d, "C2"), node(d, "C3"));

            IndTestConditionalGaussianLrt test = new IndTestConditionalGaussianLrt(d, 0.01, true);
            MarkovCheck mc = new MarkovCheck(g, test, ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY);
            mc.setParallelized(false);
            try {
                mc.generateAllResults();
            } catch (Exception e) {
                System.out.println("   MC rep " + r + " failed: " + e.getMessage());
                continue;
            }
            double ad = mc.getAndersonDarlingP(true);
            adPs.add(ad);
            if (ad < 0.05) rejections++;

            for (edu.cmu.tetrad.search.test.IndependenceResult res : mc.getResults(true)) {
                String key = res.getFact().toString();
                factPs.computeIfAbsent(key, kk -> new ArrayList<>()).add(res.getPValue());
            }
        }

        double rej = rejections / (double) adPs.size();
        System.out.printf("MC true-graph, n=%d, rareCells=%b: ad_ind<.05 in %.3f of %d reps (nominal .05)%n",
                n, rareCells, rej, adPs.size());

        for (Map.Entry<String, List<Double>> e : new java.util.TreeMap<>(factPs).entrySet()) {
            int rj = 0;
            double sum = 0;
            for (double p : e.getValue()) {
                if (p < .05) rj++;
                sum += p;
            }
            System.out.printf("   fact %-28s rej@.05=%.3f meanP=%.3f (N=%d)%n",
                    e.getKey(), rj / (double) e.getValue().size(), sum / e.getValue().size(), e.getValue().size());
        }
        factPs.clear();
    }

    private static final Map<String, List<Double>> factPs = new java.util.HashMap<>();

    // ---------- utilities ----------

    private static int pFailures = 0;

    private static double p(DataSet d, String x, String y, List<String> z, boolean discretize, int minCell) {
        IndTestConditionalGaussianLrt test = new IndTestConditionalGaussianLrt(d, 0.05, discretize);
        test.setMinSampleSizePerCell(minCell);
        List<Node> zs = new ArrayList<>();
        for (String s : z) zs.add(node(d, s));
        try {
            test.checkIndependence(node(d, x), node(d, y), new java.util.HashSet<>(zs));
        } catch (RuntimeException e) {
            pFailures++;
            return Double.NaN;
        }
        return test.getPValue();
    }

    private static Node node(DataSet d, String name) {
        return d.getVariable(name);
    }

    /**
     * Builds a mixed DataSet. contNames come first in the same order as cont
     * arrays; then discrete. names must list continuous then discrete in the
     * SAME combined order used by the cont/disc arrays.
     */
    /**
     * S9: robustness with an integer-valued "continuous" target under bootstrap resampling (the
     * contraceptive-method crash of 2026-8-12). X disc(4) and Y (integer counts) are independent given
     * Z1 disc(4), Z2 disc(4), Z3 disc(2); each rep draws a base sample of size n and then a bootstrap
     * resample of it, which readily makes Y constant within (X, Z) eligibility cells. Before the
     * degeneracy screen this threw "Undefined likelihood" on most reps; the ASSERTION here is
     * exceptions = 0. The rejection-rate line is diagnostic only and is expected to be far above
     * nominal, for two reasons independent of the screen (verified by ablation 2026-8-13: Gaussian Y
     * on the base sample, where the screen never fires, rejects 0.62): (1) the many-small-cells
     * regime - 128 fine cells averaging ~6 rows - compounds the per-cell chi-square inflation that S3
     * shows in miniature; (2) with-replacement resampling duplicates rows, violating the iid
     * assumption of the LRT and pushing rejection to ~1. Size on bootstrap resamples is not a promise
     * of this test; bootstrap search aggregates by edge frequency, not by calibrated p-values.
     */
    private static void scenarioIntegerValuedYBootstrap(int R, int n) {
        Random rng = new Random(2026);
        List<Double> ps = new ArrayList<>();
        int exceptions = 0;

        for (int rep = 0; rep < R; rep++) {
            int[] x = new int[n];
            int[] z1 = new int[n];
            int[] z2 = new int[n];
            int[] z3 = new int[n];
            double[] y = new double[n];

            for (int i = 0; i < n; i++) {
                x[i] = rng.nextInt(4);
                z1[i] = rng.nextInt(4);
                z2[i] = rng.nextInt(4);
                z3[i] = rng.nextInt(2);
                // Small integer counts depending on Z only: many ties, cells easily constant.
                double lambda = 0.5 + z1[i] * 0.7;
                int count = 0;
                double l = Math.exp(-lambda), q = 1.0;
                do {
                    q *= rng.nextDouble();
                    if (q >= l) count++;
                } while (q >= l && count < 12);
                y[i] = count;
            }

            // Bootstrap resample of the base sample.
            int[] xb = new int[n];
            int[] z1b = new int[n];
            int[] z2b = new int[n];
            int[] z3b = new int[n];
            double[] yb = new double[n];
            for (int i = 0; i < n; i++) {
                int r = rng.nextInt(n);
                xb[i] = x[r];
                z1b[i] = z1[r];
                z2b[i] = z2[r];
                z3b[i] = z3[r];
                yb[i] = y[r];
            }

            DataSet data = mixed(n, new String[]{"Y", "X", "Z1", "Z2", "Z3"},
                    new double[][]{yb}, new int[][]{xb, z1b, z2b, z3b}, new int[]{4, 4, 4, 2});

            IndTestConditionalGaussianLrt test = new IndTestConditionalGaussianLrt(data, 0.05, true);
            List<Node> vars = test.getVariables();
            Set<Node> z = new HashSet<>(Arrays.asList(vars.get(2), vars.get(3), vars.get(4)));

            try {
                ps.add(test.checkIndependence(vars.get(1), vars.get(0), z).getPValue());
            } catch (Exception e) {
                exceptions++;
            }
        }

        System.out.printf("S9 integer Y, bootstrap resample: exceptions=%d / %d (ASSERTION: 0)%n", exceptions, R);
        report("S9 size, diagnostic only (inflated; see doc)", n, ps);
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

    private static void report(String label, int n, List<Double> ps) {
        List<Double> valid = new ArrayList<>();
        for (double p : ps) if (!Double.isNaN(p)) valid.add(p);
        int r05 = 0, r01 = 0;
        for (double p : valid) {
            if (p < 0.05) r05++;
            if (p < 0.01) r01++;
        }
        double adP;
        try {
            GeneralAndersonDarlingTest ad = new GeneralAndersonDarlingTest(
                    new ArrayList<>(valid), new UniformRealDistribution(0, 1));
            adP = ad.getP();
        } catch (Exception e) {
            adP = Double.NaN;
        }
        String fail = valid.size() < ps.size() ? ("  [" + (ps.size() - valid.size()) + " threw]") : "";
        System.out.printf("%-42s %5d   %6.3f   %6.3f   %8.4g%s%n",
                label, n, r05 / (double) valid.size(), r01 / (double) valid.size(), adP, fail);
    }

    private CgCalibrationHarness() {
    }
}

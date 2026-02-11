package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.GeneralAndersonDarlingTest;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.sem.GeneralNoiseSimulation;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.UniformityTest;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/**
 * CI Test Harness for Type I error + p-value uniformity diagnostics.
 * <p>
 * Core idea:
 * - Sample CI facts (X ⟂ Y | Z) with |Z| in [kMin, kMax].
 * - Keep only facts implied independent by the true graph (via MsepTest).
 * - Evaluate a list of IndependenceTest instances on the SAME set of facts.
 * - Output:
 * (1) CSV of p-values: rows=facts, cols=tests
 * (2) CSV of decisions: rows=facts, cols=tests@alpha (or per-alpha files if you prefer)
 * (3) Summary text report with Type I rate and KS/AD uniformity p-values.
 * <p>
 * Notes:
 * - For now, assumes "true graph can be a DAG", but MsepTest works for DAG/CPDAG/MAG/PAG.
 * - If you want also implied-dependence cases (Type II-ish sanity checks), it’s easy to add.
 */
public final class CiTestHarness {

    // ===================== Public API =====================

    private static List<Node> nodesOf(List<Node> vars, int[] idx) {
        ArrayList<Node> out = new ArrayList<>(idx.length);
        for (int j : idx) out.add(vars.get(j));
        return out;
    }

    private static String canonicalKey(int x, int y, int[] z) {
        // x<y assumed
        StringBuilder sb = new StringBuilder();
        sb.append(x).append('|').append(y).append('|');
        for (int i = 0; i < z.length; i++) {
            if (i > 0) sb.append('.');
            sb.append(z[i]);
        }
        return sb.toString();
    }

    private static double[] sanitizePValues(List<Double> ps) {
        double[] out = new double[ps.size()];
        for (int i = 0; i < ps.size(); i++) {
            double v = ps.get(i);
            if (!Double.isFinite(v)) v = 1.0; // treat NaN as “very non-rejecting”
            if (v < 0) v = 0;
            if (v > 1) v = 1;
            out[i] = v;
        }
        return out;
    }

    private static double clampOpen01(double x) {
        // avoid log(0) / log(1)
        final double eps = 1e-15;
        if (x <= eps) return eps;
        if (x >= 1.0 - eps) return 1.0 - eps;
        return x;
    }

    private static String csv(String s) {
        if (s == null) return "";
        boolean need = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (!need) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ===================== Output helpers =====================

    private static String formatDouble(double x) {
        if (!Double.isFinite(x)) return "";
        return String.format(Locale.US, "%.8g", x);
    }

    public static void main(String[] args) throws Exception {
        // You’ll likely call this from your own simulation pipeline instead of main().
        // Left here as a “how to wire it” sketch.
        System.out.println("CiTestHarness loaded.");

        CiTestHarness harness = new CiTestHarness();

        int numSamples = 1000;

        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        Graph trueGraph = RandomGraph.randomGraph(vars, 0, 100,
                100, 100, 100, false);

        Function<Double, Double> activation = Math::tanh;

        GeneralNoiseSimulation sim = new GeneralNoiseSimulation(trueGraph, numSamples,
                new BetaDistribution(2, 5), new int[]{100, 100, 100, 100, 100},
                5, activation);
        DataSet dataSet = sim.generateData();

        List<IndependenceWrapper> tests = new ArrayList<>();
        tests.add(new FisherZ());

        Parameters params = new Parameters();

        Config config = new Config(
                0, 0,
                100,
                100,
                5233L,
                new double[]{0.01, 0.05},
                25   // progressEvery (pick whatever)
        );


        Result result = harness.run(trueGraph, dataSet, tests, params, config);
    }

    /**
     * Main entry: run harness on given true graph, dataset, and CI tests.
     *
     * @param trueGraph the ground-truth graph used ONLY for implied-independence filtering
     * @param data      the dataset used by the statistical tests
     * @param tests     list of CI test wrappers
     * @param params    parameters for statistical tests
     * @param cfg       harness configuration
     */
    public Result run(Graph trueGraph,
                      DataSet data,
                      List<IndependenceWrapper> tests,
                      Parameters params,
                      Config cfg) {

        trueGraph = GraphUtils.replaceNodes(trueGraph, data.getVariables());

        Objects.requireNonNull(trueGraph, "trueGraph");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(tests, "tests");
        Objects.requireNonNull(cfg, "cfg");

        if (tests.isEmpty()) throw new IllegalArgumentException("tests must be non-empty");

        List<Node> vars = data.getVariables();
        int p = vars.size();

        // 1) Build separation oracle from trueGraph (works for DAG/CPDAG/MAG/PAG)
        MsepTest implied = makeMsepTest(trueGraph);

        // 2) Sample implied-independent facts (Type I setting)
        List<CiFact> facts = sampleImpliedIndependentFacts(implied, p, cfg);

        // 3) Evaluate each test on each fact
        int T = tests.size();
        int F = facts.size();
        double[][] pvals = new double[F][T];
        int A = cfg.alphas.length;
        int[][][] decisions = new int[A][F][T];

        // per-test p-value collectors for uniformity diagnostics
        List<List<Double>> pvalsByTest = new ArrayList<>(T);
        for (int t = 0; t < T; t++) pvalsByTest.add(new ArrayList<>(F));

        for (int f = 0; f < F; f++) {
            CiFact fact = facts.get(f);
            Node X = vars.get(fact.x);
            Node Y = vars.get(fact.y);
            List<Node> Z = nodesOf(vars, fact.z);

            for (int t = 0; t < T; t++) {
                IndependenceWrapper independenceWrapper = tests.get(t);
                IndependenceTest test = independenceWrapper.getTest(data, new Parameters());

                double pv = pValueOf(test, X, Y, Z);
                pvals[f][t] = pv;
                pvalsByTest.get(t).add(pv);

                for (int a = 0; a < A; a++) {
                    decisions[a][f][t] = decisionOf(pv, cfg.alphas[a]);
                }
            }

            if ((f + 1) % cfg.progressEvery == 0) {
                System.out.println("[Harness] evaluated facts: " + (f + 1) + "/" + F);
                // Optional: uncomment to print running KS/AD for each test
                // printRunningDiagnostics(testNames, pvalsByTest);
            }
        }

        // 4) Final uniformity p-values per test
        Uniformity[] uni = new Uniformity[T];
        for (int t = 0; t < T; t++) {
            double[] ps = sanitizePValues(pvalsByTest.get(t));
            uni[t] = new Uniformity(ksUniformPValue(ps), adUniformPValue(ps));
        }

        return new Result(facts, pvals, decisions, uni);
    }

    // ===================== Core sampling logic =====================

    public void writePValuesCsv(File out, List<String> testNames, List<CiFact> facts, double[][] pvals) throws IOException {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8))) {
            pw.print("fact,x,y,z");
            for (String name : testNames) pw.print("," + csv(name));
            pw.println();

            for (int i = 0; i < facts.size(); i++) {
                CiFact f = facts.get(i);
                pw.print(csv(f.toString()));
                pw.print("," + f.x);
                pw.print("," + f.y);
                pw.print("," + csv(Arrays.toString(f.z)));
                for (int t = 0; t < testNames.size(); t++) {
                    pw.print("," + formatDouble(pvals[i][t]));
                }
                pw.println();
            }
        }
    }

    public void writeDecisionsCsv(File out,
                                  List<String> testNames,
                                  double[] alphas,
                                  List<CiFact> facts,
                                  int[][][] decisions) throws IOException {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8))) {
            pw.print("fact,x,y,z");
            for (double a : alphas) {
                for (String name : testNames) {
                    pw.print("," + csv(name + "@alpha=" + a));
                }
            }
            pw.println();

            for (int i = 0; i < facts.size(); i++) {
                CiFact f = facts.get(i);
                pw.print(csv(f.toString()));
                pw.print("," + f.x);
                pw.print("," + f.y);
                pw.print("," + csv(Arrays.toString(f.z)));

                for (int ai = 0; ai < alphas.length; ai++) {
                    for (int t = 0; t < testNames.size(); t++) {
                        pw.print("," + decisions[ai][i][t]);
                    }
                }
                pw.println();
            }
        }
    }

    public void writeSummaryReport(File out,
                                   List<String> testNames,
                                   double[] alphas,
                                   double[][] pvals,
                                   int[][][] decisions,
                                   Uniformity[] uniformity) throws IOException {

        int F = pvals.length;
        int T = testNames.size();

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8))) {
            pw.println("CI Test Harness Summary");
            pw.println("Facts tested (implied independent): " + F);
            pw.println();

            for (int t = 0; t < T; t++) {
                pw.println("Test: " + testNames.get(t));

                // Type I error rate: Type I error = declared dependent when truth is independent
                for (int ai = 0; ai < alphas.length; ai++) {
                    int dep = 0;
                    for (int f = 0; f < F; f++) dep += decisions[ai][f][t];
                    double rate = (F == 0) ? Double.NaN : (dep / (double) F);
                    pw.println("  alpha=" + alphas[ai] + "  Type I error rate: " + formatDouble(rate));
                }

                Uniformity u = uniformity[t];
                pw.println("  KS uniformity p-value: " + formatDouble(u.ksPValue()));
                pw.println("  AD uniformity p-value: " + formatDouble(u.adPValue()));
                pw.println();
            }
        }
    }

    /**
     * Builds an MsepTest over the provided variable list. This is the “truth oracle” for implied independences.
     * Isolating here makes it easy to tweak later if you want different separation semantics.
     */
    private MsepTest makeMsepTest(Graph trueGraph) {
        // MsepTest in Tetrad expects a graph and variables; adjust constructor if your signature differs.
        return new MsepTest(trueGraph);
    }

    // ===================== CI test evaluation =====================

    /**
     * Sample CI facts that are implied independent by MsepTest (Type I setting).
     * <p>
     * Strategy:
     * - Rejection sample:
     * choose unordered (x,y),
     * choose k in [kMin,kMax],
     * sample Z of size k from V \ {x,y},
     * keep if implied independent.
     */
    private List<CiFact> sampleImpliedIndependentFacts(MsepTest implied, int p, Config cfg) {
        SplittableRandom rng = new SplittableRandom(cfg.seed);
        List<CiFact> out = new ArrayList<>(cfg.nFacts);

        // prevent duplicates (optional but helpful)
        HashSet<String> seen = new HashSet<>(cfg.nFacts * 2);

        for (int f = 0; f < cfg.nFacts; f++) {
            CiFact fact = null;

            for (int attempt = 0; attempt < cfg.maxAttemptsPerFact; attempt++) {
                int x = rng.nextInt(p);
                int y = rng.nextInt(p - 1);
                if (y >= x) y++;
                if (x > y) {
                    int tmp = x;
                    x = y;
                    y = tmp;
                } // unordered

                int k = (cfg.kMin == cfg.kMax) ? cfg.kMin : (cfg.kMin + rng.nextInt(cfg.kMax - cfg.kMin + 1));
                int[] z = sampleConditioningSet(rng, p, x, y, k);

                // implied independence?
                if (isImpliedIndependent(implied, x, y, z)) {
                    String key = canonicalKey(x, y, z);
                    if (seen.add(key)) {
                        fact = new CiFact(x, y, z);
                        break;
                    }
                }
            }

            if (fact == null) {
                throw new IllegalStateException("Failed to find implied-independent fact after "
                        + cfg.maxAttemptsPerFact + " attempts at f=" + f
                        + " (try increasing maxAttemptsPerFact or loosening k-range).");
            }

            out.add(fact);
        }

        return out;
    }

    /**
     * Sampling conditioning set Z uniformly without replacement from V \ {x,y}.
     */
    private int[] sampleConditioningSet(SplittableRandom rng, int p, int x, int y, int k) {
        if (k == 0) return new int[0];
        if (k > p - 2) k = p - 2;

        // reservoir-ish sampling using a shuffled pool of eligible indices
        int[] pool = new int[p - 2];
        int idx = 0;
        for (int v = 0; v < p; v++) {
            if (v == x || v == y) continue;
            pool[idx++] = v;
        }

        // Fisher–Yates partial shuffle for first k elements
        for (int i = 0; i < k; i++) {
            int j = i + rng.nextInt(pool.length - i);
            int tmp = pool[i];
            pool[i] = pool[j];
            pool[j] = tmp;
        }

        int[] z = Arrays.copyOf(pool, k);
        Arrays.sort(z);
        return z;
    }

    // ===================== Uniformity diagnostics (KS + AD) =====================

    /**
     * True if implied independence holds in the true graph.
     * Isolated so you can adjust details (e.g., exclude adjacencies, require minimality, etc.).
     */
    private boolean isImpliedIndependent(MsepTest implied, int x, int y, int[] z) {
        // MsepTest can check with node indices or Nodes; here we’ll use Nodes via implied.getVariables().
        List<Node> vars = implied.getVariables();
        Node X = vars.get(x);
        Node Y = vars.get(y);
        List<Node> Z = nodesOf(vars, z);
        try {
            return implied.checkIndependence(X, Y, new HashSet<>(Z)).isIndependent();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the p-value for X _||_ Y | Z for the given test.
     * Adjust this method if your IndependenceTest API differs.
     */
    private double pValueOf(IndependenceTest test, Node x, Node y, List<Node> z) {
        try {
            double p = test.checkIndependence(x, y, new HashSet<>(z)).getPValue();
            if (!Double.isFinite(p)) return Double.NaN;
            // clamp to [0,1] defensively
            if (p < 0) p = 0;
            if (p > 1) p = 1;
            return p;
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    // ===================== (Optional) true DAG creation hook =====================

    /**
     * Decision encoding:
     * 0 = declared independent
     * 1 = declared dependent (Type I error in this harness, since truth is independent)
     */
    private int decisionOf(double pValue, double alpha) {
        if (!Double.isFinite(pValue)) return 0; // treat NaN as “don’t reject”
        return (pValue <= alpha) ? 1 : 0;
    }

    // ===================== Small utilities =====================

    /**
     * KS test p-value for Uniform(0,1). Uses asymptotic approximation (good once n is moderately large).
     */
    private double ksUniformPValue(double[] pvals) {
        List<Double> pValues = new ArrayList<>(pvals.length);
        for (double p : pvals) pValues.add(p);
        return UniformityTest.getKsPValue(pValues, 0.0, 1.0);
    }

    /**
     * Anderson–Darling uniformity p-value.
     * <p>
     * We compute the AD statistic for U(0,1) and then use a common approximation for the p-value.
     * If you already have a preferred AD implementation in Tetrad, swap it in here.
     */
    private double adUniformPValue(double[] pvals) {
        List<Double> pValues = new ArrayList<>(pvals.length);
        for (double p : pvals) pValues.add(p);
        GeneralAndersonDarlingTest _generalAndersonDarlingTest = new GeneralAndersonDarlingTest(pValues, new UniformRealDistribution(0, 1));
        double _aSquared = _generalAndersonDarlingTest.getASquared();
        double _aSquaredStar = _generalAndersonDarlingTest.getASquaredStar();
        return 1. - _generalAndersonDarlingTest.getProbTail(pValues.size(), _aSquaredStar);
    }

    /**
     * Hook for creating a true DAG if you want the harness to also generate graphs.
     * For now you said “true graph can be a DAG for now” — so you may supply it externally.
     * <p>
     * If you want this harness to create DAGs internally, implement this using your preferred Tetrad utilities.
     */
    private Graph createTrueDag(List<Node> variables, long seed) {
        throw new UnsupportedOperationException("Implement if you want harness-created DAGs.");
    }

    // Optional: running diagnostics printout
    @SuppressWarnings("unused")
    private void printRunningDiagnostics(List<String> testNames, List<List<Double>> pvalsByTest) {
        for (int t = 0; t < testNames.size(); t++) {
            double[] ps = sanitizePValues(pvalsByTest.get(t));
            double ks = ksUniformPValue(ps);
            double ad = adUniformPValue(ps);
            System.out.println("  " + testNames.get(t) + ": KS=" + formatDouble(ks) + " AD=" + formatDouble(ad));
        }
    }

    public record Config(
            int kMin,
            int kMax,
            int nFacts,
            int maxAttemptsPerFact,
            long seed,
            double[] alphas,
            int progressEvery
    ) {
        public Config {
            if (kMin < 0 || kMax < kMin) throw new IllegalArgumentException("Bad kMin/kMax");
            if (nFacts <= 0) throw new IllegalArgumentException("nFacts must be > 0");
            if (maxAttemptsPerFact <= 0) throw new IllegalArgumentException("maxAttemptsPerFact must be > 0");
            if (alphas == null || alphas.length == 0) throw new IllegalArgumentException("alphas must be non-empty");
            if (progressEvery <= 0) progressEvery = 50;
        }
    }

    public record CiFact(int x, int y, int[] z) {
        @Override
        public String toString() {
            return "X" + x + " _||_ X" + y + " | " + Arrays.toString(z);
        }
    }

    public static final class Result {
        public final List<CiFact> facts;          // size = nFacts
        public final double[][] pvals;            // [fact][test]
        public final int[][][] decisions;         // [alphaIndex][fact][test]  (0=indep, 1=dep)
        public final Uniformity[] uniformity;     // per test (final)

        public Result(List<CiFact> facts, double[][] pvals, int[][][] decisions, Uniformity[] uniformity) {
            this.facts = facts;
            this.pvals = pvals;
            this.decisions = decisions;
            this.uniformity = uniformity;
        }
    }

    // ===================== Example usage (optional) =====================

    public record Uniformity(double ksPValue, double adPValue) {
    }
}
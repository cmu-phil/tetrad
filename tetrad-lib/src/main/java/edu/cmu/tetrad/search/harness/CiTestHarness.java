package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.algcomparison.independence.*;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
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
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.UniformityTest;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Function;

/**
 * CI Test Harness for Type I error + p-value uniformity diagnostics.
 * <p>
 * Core idea:x
 * - Sample CI facts (X ⟂ Y | Z) with |Z| in [kMin, kMax].
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
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        return nf.format(x);
//        if (!Double.isFinite(x)) return "";
//        return String.format(Locale.US, "%.8g", x);
    }

    public static void main(String[] args) throws Exception {
        // You’ll likely call this from your own simulation pipeline instead of main().
        // Left here as a “how to wire it” sketch.
        System.out.println("CiTestHarness loaded.");

        List<IndependenceWrapper> tests = new ArrayList<>();
        tests.add(new FisherZ());
        tests.add(new FfCi());
        tests.add(new Gcm());
        tests.add(new ClKciPython());
        tests.add(new Kci());
        tests.add(new MinimaxCITest());
        tests.add(new Rcit());
        tests.add(new BasisFunctionBlocksIndTest());

        Parameters params = new Parameters();
        params.set(Params.MINIMAX_PERMUTATIONS, 500);
        params.set(Params.NUM_MEASURES, 50);
        params.set(Params.SAMPLE_SIZE, 1000);
        params.set(Params.AVG_DEGREE, 2);
        params.set(Params.HIDDEN_DIMENSIONS, "100,100,100,100,100");
        params.set(Params.AM_BETA_ALPHA, 2.0);
        params.set(Params.AM_BETA_BETA, 5.0);
        params.set(Params.INPUT_SCALE, 5.0);
        params.set(Params.STANDARDIZE, false);
        params.set(Params.NUM_RUNS, 1);
        params.set(Params.MEASUREMENT_VARIANCE, 0.0);
        params.set(Params.RANDOMIZE_COLUMNS, false);
        params.set(Params.PROB_REMOVE_COLUMN, 0.0);

        CiTestHarness harness = new CiTestHarness();

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < params.getInt(Params.NUM_MEASURES); i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        Graph trueGraph = RandomGraph.randomGraph(vars, 0, params.getInt(Params.NUM_MEASURES),
                100, 100, 100, false);

        Function<Double, Double> activation = Math::tanh;

        GeneralNoiseSimulation sim = new GeneralNoiseSimulation(trueGraph, params.getInt(Params.SAMPLE_SIZE),
                new BetaDistribution(2, 5), new int[]{100, 100, 100, 100, 100},
                5, activation);
        DataSet dataSet1 = sim.generateData();

        Simulation simulation = new edu.cmu.tetrad.algcomparison.simulation.GeneralNoiseSimulation(new SingleGraph(trueGraph));
        simulation.createData(params, true);
        DataSet dataSet2 = (DataSet) simulation.getDataModel(0);

        double[] alphas = {0.001, 0.01, 0.05};

        Config config = new Config(
                0, 4,
                200,   // nFactsIndep
                200,   // nFactsDep  (or 0 to default to nFactsIndep)
                200,
                5233L,
                alphas,
                25
        );

        List<String> testNames = new ArrayList<>();
        for (IndependenceWrapper test : tests) {
            testNames.add(test.getDescription());
        }

        Result result = harness.run(trueGraph, dataSet2, tests, params, config);

        harness.writePValues(new File("ci_test_pvalues_indep." + config.kMin + "." + config.kMax + ".txt"), testNames, result.indepFacts, result.indepPvals);
        harness.writePValues(new File("ci_test_pvalues_dep." + config.kMin + "." + config.kMax + ".txt"), testNames, result.depFacts, result.depPvals);

        harness.writeDecisions(new File("ci_test_decisions_indep." + config.kMin + "." + config.kMax + ".txt"), testNames, alphas, result.indepFacts, result.indepDecisions);
        harness.writeDecisions(new File("ci_test_decisions_dep." + config.kMin + "." + config.kMax + ".txt"), testNames, alphas, result.depFacts, result.depDecisions);

        harness.writeSummaryReport(
                new File("ci_test_summary" + config.kMin + "." + config.kMax + ".txt"),
                testNames,
                alphas,
                result
        );
    }

    private static void writeConfig(PrintWriter pw, Config cfg) {
        pw.println("Config");
        pw.println("  kMin: " + cfg.kMin());
        pw.println("  kMax: " + cfg.kMax());
        pw.println("  nFactsIndep: " + cfg.nFactsIndep());
        pw.println("  nFactsDep: " + cfg.nFactsDep());
        pw.println("  maxAttemptsPerFact: " + cfg.maxAttemptsPerFact());
        pw.println("  seed: " + cfg.seed());
        pw.println("  alphas: " + Arrays.toString(cfg.alphas()));
        pw.println("  progressEvery: " + cfg.progressEvery());
        pw.println();
    }

    // ===================== Core sampling logic =====================

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

        List<IndependenceTest> _tests = new ArrayList<>();
        for (IndependenceWrapper test : tests) _tests.add(test.getTest(data, params));

        Objects.requireNonNull(trueGraph, "trueGraph");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(tests, "tests");
        Objects.requireNonNull(cfg, "cfg");
        if (tests.isEmpty()) throw new IllegalArgumentException("tests must be non-empty");

        List<Node> vars = data.getVariables();
        int p = vars.size();

        // 1) Truth oracle
        MsepTest implied = makeMsepTest(trueGraph);

        // 2) Sample both pools
//        List<CiFact> indepFacts = sampleImpliedIndependentFacts(implied, p, cfg);
//        List<CiFact> depFacts = sampleImpliedDependentFacts(implied, p, cfg);

        // 2) Sample a single pool then bucket by truth (m-sep)
        Buckets buckets = sampleAndBucketFacts(implied, p, cfg);
        List<CiFact> indepFacts = buckets.indepFacts;
        List<CiFact> depFacts = buckets.depFacts;

        HashSet<String> s = new HashSet<>();
        for (CiFact f : indepFacts) if (!s.add(canonicalKey(f.x, f.y, f.z))) throw new AssertionError();
        for (CiFact f : depFacts) if (!s.add(canonicalKey(f.x, f.y, f.z))) throw new AssertionError();

        // 3) Evaluate
        int T = tests.size();
        int A = cfg.alphas.length;

        double[][] indepPvals = new double[indepFacts.size()][T];
        int[][][] indepDecisions = new int[A][indepFacts.size()][T];

        double[][] depPvals = new double[depFacts.size()][T];
        int[][][] depDecisions = new int[A][depFacts.size()][T];

        // uniformity diagnostics are meaningful for the *independent* pool
        List<List<Double>> pvalsByTestIndep = new ArrayList<>(T);
        for (int t = 0; t < T; t++) pvalsByTestIndep.add(new ArrayList<>(indepFacts.size()));

        // --- indep pool ---
        for (int f = 0; f < indepFacts.size(); f++) {
            CiFact fact = indepFacts.get(f);
            Node X = vars.get(fact.x);
            Node Y = vars.get(fact.y);
            List<Node> Z = nodesOf(vars, fact.z);

            for (int t = 0; t < T; t++) {
                double pv = pValueOf(_tests.get(t), X, Y, Z);
                indepPvals[f][t] = pv;
                pvalsByTestIndep.get(t).add(pv);

                for (int a = 0; a < A; a++) {
                    indepDecisions[a][f][t] = decisionOf(pv, cfg.alphas[a]); // 1 = reject (dependent)
                }
            }

            if ((f + 1) % cfg.progressEvery == 0) {
                System.out.println("[Harness] evaluated INDEP facts: " + (f + 1) + "/" + indepFacts.size());
            }
        }

        // --- dep pool ---
        for (int f = 0; f < depFacts.size(); f++) {
            CiFact fact = depFacts.get(f);
            Node X = vars.get(fact.x);
            Node Y = vars.get(fact.y);
            List<Node> Z = nodesOf(vars, fact.z);

            for (int t = 0; t < T; t++) {
                double pv = pValueOf(_tests.get(t), X, Y, Z);
                depPvals[f][t] = pv;

                for (int a = 0; a < A; a++) {
                    depDecisions[a][f][t] = decisionOf(pv, cfg.alphas[a]); // 1 = reject (dependent)
                }
            }

            if ((f + 1) % cfg.progressEvery == 0) {
                System.out.println("[Harness] evaluated DEP facts: " + (f + 1) + "/" + depFacts.size());
            }
        }

        // 4) Final uniformity on indep pool p-values
        Uniformity[] uni = new Uniformity[T];
        for (int t = 0; t < T; t++) {
            double[] ps = sanitizePValues(pvalsByTestIndep.get(t));
            uni[t] = new Uniformity(ksUniformPValue(ps), adUniformPValue(ps));
        }

        return new Result(cfg,
                indepFacts, indepPvals, indepDecisions,
                depFacts, depPvals, depDecisions,
                uni);
    }

    public void writePValues(File out, List<String> testNames, List<CiFact> facts, double[][] pvals) throws IOException {
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

    public void writeDecisions(File out,
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
                                   Result r) throws IOException {

        int T = testNames.size();
        int F0 = r.indepPvals.length; // truth independent
        int F1 = r.depPvals.length;   // truth dependent

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8))) {
            pw.println("CI Test Harness Summary");
            pw.println("Facts tested (truth independent): " + F0);
            pw.println("Facts tested (truth dependent):   " + F1);
            pw.println();

            writeConfig(pw, r.cfg);   // <--- add this

            for (int t = 0; t < T; t++) {
                pw.println("Test: " + testNames.get(t));

                for (int ai = 0; ai < alphas.length; ai++) {
                    // Type I error: reject when truth is independent
                    int reject0 = 0;
                    for (int f = 0; f < F0; f++) reject0 += r.indepDecisions[ai][f][t]; // 1=reject
                    double typeI = (F0 == 0) ? Double.NaN : (reject0 / (double) F0);

                    // Type II error: fail-to-reject when truth is dependent
                    int failReject1 = 0;
                    for (int f = 0; f < F1; f++) {
                        int rej = r.depDecisions[ai][f][t]; // 1=reject
                        if (rej == 0) failReject1++;
                    }
                    double typeII = (F1 == 0) ? Double.NaN : (failReject1 / (double) F1);

                    // Power = P(reject | truth dependent) = 1 - Type II
                    double power = Double.isNaN(typeII) ? Double.NaN : (1.0 - typeII);

                    pw.println("  alpha=" + alphas[ai]
                            + "  Type I: " + formatDouble(typeI)
                            + "  Type II: " + formatDouble(typeII)
                            + "  Power: " + formatDouble(power));
                }

                Uniformity u = r.uniformity[t];
                pw.println("  KS uniformity p-value (indep pool): " + formatDouble(u.ksPValue()));
                pw.println("  AD uniformity p-value (indep pool): " + formatDouble(u.adPValue()));
                pw.println();
            }
        }
    }

    // ===================== CI test evaluation =====================

    /**
     * Builds an MsepTest over the provided variable list. This is the “truth oracle” for implied independences.
     * Isolating here makes it easy to tweak later if you want different separation semantics.
     */
    private MsepTest makeMsepTest(Graph trueGraph) {
        // MsepTest in Tetrad expects a graph and variables; adjust constructor if your signature differs.
        return new MsepTest(trueGraph);
    }

    // ===================== Uniformity diagnostics (KS + AD) =====================

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

    // ===================== (Optional) true DAG creation hook =====================

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

    // ===================== Small utilities =====================

    /**
     * Decision encoding:
     * 0 = declared independent
     * 1 = declared dependent (Type I error in this harness, since truth is independent)
     */
    private int decisionOf(double pValue, double alpha) {
        if (!Double.isFinite(pValue)) return 0; // treat NaN as “don’t reject”
        return (pValue <= alpha) ? 1 : 0;
    }

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

    /**
     * Sample facts once, dedupe, then bucket into implied-independent vs implied-dependent
     * using the Msep oracle. No rejection sampling per bucket.
     * <p>
     * We keep sampling until we have nFactsIndep and nFactsDep (or until we hit maxAttemptsPerFact,
     * interpreted here as "max attempts per requested fact" across both buckets).
     */
    private Buckets sampleAndBucketFacts(MsepTest implied, int p, Config cfg) {
        SplittableRandom rng = new SplittableRandom(cfg.seed);

        int targetIndep = cfg.nFactsIndep;
        int targetDep = cfg.nFactsDep;// (cfg.nFactsDep <= 0) ? cfg.nFactsIndep : cfg.nFactsDep;

        ArrayList<CiFact> indep = new ArrayList<>(targetIndep);
        ArrayList<CiFact> dep = new ArrayList<>(targetDep);

        // Dedup across *all* facts so the pools are disjoint and you don't waste evaluation.
        HashSet<String> seen = new HashSet<>((targetIndep + targetDep) * 2);

        // Total attempts budget: interpret maxAttemptsPerFact as per-target-fact attempts.
        long maxAttempts = (long) cfg.maxAttemptsPerFact * (targetIndep + targetDep);
        long attempts = 0;

        while ((indep.size() < targetIndep || dep.size() < targetDep) && attempts < maxAttempts) {
            attempts++;

            int x = rng.nextInt(p);
            int y = rng.nextInt(p - 1);
            if (y >= x) y++;
            if (x > y) {
                int tmp = x;
                x = y;
                y = tmp;
            }

            int k = (cfg.kMin == cfg.kMax) ? cfg.kMin : (cfg.kMin + rng.nextInt(cfg.kMax - cfg.kMin + 1));
            int[] z = sampleConditioningSet(rng, p, x, y, k);

            String key = canonicalKey(x, y, z);
            if (!seen.add(key)) continue;

            CiFact fact = new CiFact(x, y, z);

            boolean truthIndep = isImpliedIndependent(implied, x, y, z);

            if (truthIndep) {
                if (indep.size() < targetIndep) indep.add(fact);
            } else {
                if (dep.size() < targetDep) dep.add(fact);
            }
        }

        if (indep.size() < targetIndep || dep.size() < targetDep) {
            throw new IllegalStateException(
                    "Failed to fill buckets within attempt budget. "
                            + "needed indep=" + targetIndep + ", got " + indep.size()
                            + "; needed dep=" + targetDep + ", got " + dep.size()
                            + "; attempts=" + attempts + "/" + maxAttempts
                            + ". Try increasing maxAttemptsPerFact, widening k-range, "
                            + "or increasing p / changing the true graph density."
            );
        }

        return new Buckets(indep, dep);
    }

    public record Config(
            int kMin,
            int kMax,
            int nFactsIndep,          // facts implied independent (Type I)
            int nFactsDep,            // facts implied dependent (Type II)
            int maxAttemptsPerFact,
            long seed,
            double[] alphas,
            int progressEvery
    ) {
        public Config {
            if (kMin < 0 || kMax < kMin) throw new IllegalArgumentException("Bad kMin/kMax");
            if (nFactsIndep <= 0) throw new IllegalArgumentException("nFactsIndep must be > 0");
            if (nFactsDep <= 0) nFactsDep = nFactsIndep; // default
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
        public final Config cfg;                // <--- add this
        public final List<CiFact> indepFacts;
        public final double[][] indepPvals;
        public final int[][][] indepDecisions;

        public final List<CiFact> depFacts;
        public final double[][] depPvals;
        public final int[][][] depDecisions;

        public final Uniformity[] uniformity;

        public Result(Config cfg,                              // <--- add this param
                      List<CiFact> indepFacts,
                      double[][] indepPvals,
                      int[][][] indepDecisions,
                      List<CiFact> depFacts,
                      double[][] depPvals,
                      int[][][] depDecisions,
                      Uniformity[] uniformity) {
            this.cfg = Objects.requireNonNull(cfg, "cfg");      // <--- add this
            this.indepFacts = indepFacts;
            this.indepPvals = indepPvals;
            this.indepDecisions = indepDecisions;
            this.depFacts = depFacts;
            this.depPvals = depPvals;
            this.depDecisions = depDecisions;
            this.uniformity = uniformity;
        }
    }

    // ===================== Example usage (optional) =====================

    public record Uniformity(double ksPValue, double adPValue) {
    }

    private record Buckets(List<CiFact> indepFacts, List<CiFact> depFacts) {
    }
}
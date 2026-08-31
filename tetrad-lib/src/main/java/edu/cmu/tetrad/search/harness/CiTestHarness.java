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
import edu.cmu.tetrad.sem.DistributionSampler;
import edu.cmu.tetrad.sem.GeneralNoiseSimulation;
import edu.cmu.tetrad.util.*;
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
public final class  CiTestHarness {

    /**
     * Default constructor for the CiTestHarness class.
     * Initializes an instance of the harness for running conditional independence tests
     * on datasets using statistical simulations and configurations specified in other methods.
     */
    public CiTestHarness() {

    }

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

    /**
     * Entry point of the program. Demonstrates the usage of the `CiTestHarness` by setting up
     * a statistical simulation, running conditional independence tests, and writing outputs to files.
     *
     * @param args command-line arguments (not used in this implementation)
     * @throws Exception if an error occurs during file operations or test execution
     */
    public static void main(String[] args) throws Exception {
        // You’ll likely call this from your own simulation pipeline instead of main().
        // Left here as a “how to wire it” sketch.
        System.out.println("CiTestHarness loaded.");

        List<IndependenceWrapper> tests = new ArrayList<>();
        tests.add(new FisherZ());
        tests.add(new Kci());
        tests.add(new ClKciPython());
//        tests.add(new Rcit());
        tests.add(new Gcm());

        // Taking these test out of the interface. jdramsey 2026-2-16
//        tests.add(new BasisFunctionBlocksIndTest());
//        tests.add(new BasisFunctionLrt());
//        tests.add(new MinimaxCITest());
//        tests.add(new MinimaxTRffIndTest());
        tests.add(new FfCi());

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
        params.set(Params.RCIT_APPROX, 1);

        CiTestHarness harness = new CiTestHarness();

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < params.getInt(Params.NUM_MEASURES); i++) {
            vars.add(new ContinuousVariable("X" + i));
        }

        Graph trueGraph = RandomGraph.randomGraph(vars, 0, params.getInt(Params.NUM_MEASURES),
                100, 100, 100, false);

        Function<Double, Double> activation = Math::tanh;

        DistributionSampler distributionSampler = new DistributionSampler(new BetaDistribution(2, 5));

        GeneralNoiseSimulation sim = new GeneralNoiseSimulation(trueGraph, params.getInt(Params.SAMPLE_SIZE),
                distributionSampler, new int[]{100, 100, 100, 100, 100},
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
     * Executes a series of independence tests on a dataset using a given true graph and configuration,
     * and evaluates the results against predefined criteria.
     *
     * @param trueGraph the ground truth graph, which represents the true dependencies
     *                  and independencies in the dataset. Must not be null.
     * @param data      the dataset containing variables and data values to be tested. Must not be null.
     * @param tests     a list of independence test wrappers to be executed on the dataset. Must be non-empty and not null.
     * @param params    parameters to configure the setup and behavior of the independence tests. Must not be null.
     * @param cfg       configuration settings for sampling, evaluation, and progress reporting. Must not be null.
     * @return a {@link Result} object containing the evaluation results, including calculated p-values,
     * decisions for both independent and dependent tests, and uniformity diagnostics.
     * @throws NullPointerException     if any of the input arguments {@code trueGraph}, {@code data}, {@code tests}, or {@code cfg} is null.
     * @throws IllegalArgumentException if the {@code tests} list is empty.
     * @throws AssertionError           if duplicate facts are detected in the independent or dependent pools.
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

    /**
     * Writes a CSV file containing p-values for conditional independence tests.
     * The output includes headers for the fact description, variables x and y,
     * conditioning set z, and test names, followed by rows of data corresponding
     * to each fact and its associated p-values for each test.
     *
     * @param out       the file to which the results will be written
     * @param testNames a list of names of the statistical tests whose p-values are recorded
     * @param facts     a list of conditional independence facts, where each fact contains
     *                  information about the variables being tested and the conditioning set
     * @param pvals     a 2D array of p-values, where each row corresponds to a fact and
     *                  each column corresponds to a test
     * @throws IOException if there is an error during file writing
     */
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

    /**
     * Writes a CSV file containing decisions for conditional independence tests.
     * The output includes headers for fact descriptions, variables x, y, and z,
     * followed by test names and their corresponding decision values arranged by
     * test names and alpha levels.
     *
     * @param out       the file to which the decisions will be written
     * @param testNames a list of names of the statistical tests under evaluation
     * @param alphas    an array of significance levels (alpha values) for which
     *                  decisions were computed
     * @param facts     a list of conditional independence facts, where each fact
     *                  contains information about the variables being tested and the
     *                  conditioning set
     * @param decisions a 3D array where decisions[ai][i][t] represents the decision
     *                  for the i-th fact using the t-th test at the ai-th alpha level.
     *                  A value of 0 signifies independent, while 1 signifies dependent.
     * @throws IOException if an error occurs during file writing
     */
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

    /**
     * Generates a summary report of the statistical tests and writes it to the specified output file.
     * <p>
     * The report includes the number of facts tested for truth independence and dependence, configuration details,
     * and per-test summary statistics such as type I error, type II error, power, and uniformity p-values.
     *
     * @param out       The file to which the summary report will be written.
     * @param testNames A list of test names corresponding to the evaluated tests.
     * @param alphas    An array of significance levels (alpha values) used for the statistical tests.
     * @param r         A Result object containing statistical test results, decisions, and uniformity measures.
     * @throws IOException If an I/O error occurs while writing to the output file.
     */
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
     * Creates an instance of MsepTest using the provided true graph.
     * This method encapsulates the initialization of an MsepTest object,
     * which is used for determining m-separation properties in a graph.
     *
     * @param trueGraph the input graph representing the ground-truth structure
     *                  upon which m-separation tests are conducted
     * @return an initialized MsepTest object configured with the provided graph
     */
    private MsepTest makeMsepTest(Graph trueGraph) {
        // MsepTest in Tetrad expects a graph and variables; adjust constructor if your signature differs.
        return new MsepTest(trueGraph);
    }

    // ===================== Uniformity diagnostics (KS + AD) =====================

    /**
     * Generates a pseudo-random sample of integers from a set of eligible indices
     * based on the input parameters, excluding specified indices.
     *
     * @param p the size of the original set of indices from which sampling is performed
     * @param x the index to exclude from the sampling pool
     * @param y another index to exclude from the sampling pool
     * @param k the number of indices to sample, subject to adjustment if {@code k > p - 2}
     * @return an array of {@code k} distinct pseudo-randomly sampled, sorted integers
     * from the eligible indices, or an empty array if {@code k == 0}
     */
    private int[] sampleConditioningSet(int p, int x, int y, int k) {
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
            int j = i + RandomUtil.getInstance().nextInt(pool.length - i);
            int tmp = pool[i];
            pool[i] = pool[j];
            pool[j] = tmp;
        }

        int[] z = Arrays.copyOf(pool, k);
        Arrays.sort(z);
        return z;
    }

    /**
     * Determines whether the given conditional independence test implies independence
     * between two nodes (x and y) given a set of conditioning nodes (z).
     *
     * @param implied The conditional independence test to be evaluated.
     * @param x       Index of the first node in the independence check.
     * @param y       Index of the second node in the independence check.
     * @param z       Array of indices representing the conditioning set of nodes.
     * @return true if the conditional independence test implies independence,
     * false otherwise.
     * @throws RuntimeException if an exception occurs during the independence check process.
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
     * Calculates the p-value of the independence test for the given nodes.
     *
     * @param test the independence test to be used for computation
     * @param x    the first node in the test
     * @param y    the second node in the test
     * @param z    the list of conditioning nodes
     * @return the computed p-value clamped between 0 and 1, or Double.NaN in case of an error or an invalid result
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
     * Determines the decision of a hypothesis test based on the provided p-value and significance level.
     *
     * @param pValue the p-value from the statistical test; must be a finite number
     * @param alpha  the significance level threshold for rejecting the null hypothesis
     * @return 1 if the p-value is less than or equal to alpha, indicating rejection of the null hypothesis;
     * 0 if the p-value is greater than alpha, or if the p-value is not finite
     */
    private int decisionOf(double pValue, double alpha) {
        if (!Double.isFinite(pValue)) return 0; // treat NaN as “don’t reject”
        return (pValue <= alpha) ? 1 : 0;
    }

    /**
     * Calculates the Kolmogorov-Smirnov (KS) test p-value to evaluate the uniformity
     * of the given array of p-values within the range [0, 1].
     *
     * @param pvals an array of p-values to be tested for uniformity
     * @return the p-value resulting from the KS test
     */
    private double ksUniformPValue(double[] pvals) {
        List<Double> pValues = new ArrayList<>(pvals.length);
        for (double p : pvals) pValues.add(p);
        return UniformityTest.getKsPValue(pValues, 0.0, 1.0);
    }

    /**
     * Computes the p-value for a set of input p-values using the Anderson-Darling
     * test for uniformity over the interval [0, 1].
     *
     * @param pvals an array of p-values to be tested for uniformity
     * @return the p-value from the Anderson-Darling test indicating the probability
     * of observing a test statistic at least as extreme as the one calculated
     * assuming the null hypothesis of uniformity is true
     */
    private double adUniformPValue(double[] pvals) {
        List<Double> pValues = new ArrayList<>(pvals.length);
        for (double p : pvals) pValues.add(p);
        GeneralAndersonDarlingTest _generalAndersonDarlingTest = new GeneralAndersonDarlingTest(pValues, new UniformRealDistribution(0, 1));
        double _aSquared = _generalAndersonDarlingTest.getASquared();
        // A-squared, not A-squared-star: the null is a fully specified Uniform(0, 1) with no estimated parameters,
        // and getProbTail is the tail for the uninflated statistic. See MarkovCheck.calcStats.
        return 1. - _generalAndersonDarlingTest.getProbTail(pValues.size(), _aSquared);
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

            int x = RandomUtil.getInstance().nextInt(p);
            int y = RandomUtil.getInstance().nextInt(p - 1);
            if (y >= x) y++;
            if (x > y) {
                int tmp = x;
                x = y;
                y = tmp;
            }

            int k = (cfg.kMin == cfg.kMax) ? cfg.kMin : (cfg.kMin + RandomUtil.getInstance().nextInt(cfg.kMax - cfg.kMin + 1));
            int[] z = sampleConditioningSet(p, x, y, k);

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

    /**
     * Represents a configuration record that encapsulates various parameters
     * used for the generation and evaluation of facts.
     *
     * @param kMin               The minimum value of parameter k. Must be non-negative.
     *                           Represents the lower bound of a range.
     * @param kMax               The maximum value of parameter k. Must be greater than or equal to kMin.
     *                           Represents the upper bound of a range.
     * @param nFactsIndep        The number of independent facts (Type I). Must be greater than 0.
     * @param nFactsDep          The number of dependent facts (Type II). Defaults to nFactsIndep if &lt;= 0.
     * @param maxAttemptsPerFact The maximum number of attempts allowed for generating a single fact. Must be greater than 0.
     * @param seed               The seed value used for randomization. Ensures deterministic behavior when specified.
     * @param alphas             An array of alpha values to be used in mathematical computations. Cannot be null or empty.
     * @param progressEvery      Indicates how often progress should be reported (as a frequency). Defaults to 50 if &lt;= 0.
     */
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
        /**
         * Represents a configuration record that encapsulates various parameters
         * used for the generation and evaluation of facts.
         *
         * @param kMin               The minimum value of parameter k. Must be non-negative.
         *                           Represents the lower bound of a range.
         * @param kMax               The maximum value of parameter k. Must be greater than or equal to kMin.
         *                           Represents the upper bound of a range.
         * @param nFactsIndep        The number of independent facts (Type I). Must be greater than 0.
         * @param nFactsDep          The number of dependent facts (Type II). Defaults to nFactsIndep if &lt;= 0.
         * @param maxAttemptsPerFact The maximum number of attempts allowed for generating a single fact. Must be greater than 0.
         * @param seed               The seed value used for randomization. Ensures deterministic behavior when specified.
         * @param alphas             An array of alpha values to be used in mathematical computations. Cannot be null or empty.
         * @param progressEvery      Indicates how often progress should be reported (as a frequency). Defaults to 50 if &lt;= 0.
         */
        public Config {
            if (kMin < 0 || kMax < kMin) throw new IllegalArgumentException("Bad kMin/kMax");
            if (nFactsIndep <= 0) throw new IllegalArgumentException("nFactsIndep must be > 0");
            if (nFactsDep <= 0) nFactsDep = nFactsIndep; // default
            if (maxAttemptsPerFact <= 0) throw new IllegalArgumentException("maxAttemptsPerFact must be > 0");
            if (alphas == null || alphas.length == 0) throw new IllegalArgumentException("alphas must be non-empty");
            if (progressEvery <= 0) progressEvery = 50;
        }
    }

    /**
     * Immutable data container that represents a CiFact consisting of two integer values and an array of integers.
     * <p>
     * This class is implemented as a record, providing a compact and immutable way to manage data.
     * It includes the overridden toString method that formats the output as a string representation
     * of the object with specific components (x, y, and z).
     *
     * @param x The first
     * @param y The second
     * @param z The conditioning
     */
    public record CiFact(int x, int y, int[] z) {
        @Override
        public String toString() {
            return "X" + x + " _||_ X" + y + " | " + Arrays.toString(z);
        }
    }

    /**
     * Represents the result of a computation or analysis process.
     * <p>
     * This class encapsulates information such as configuration details,
     * independent and dependent facts, their respective p-values and decisions,
     * as well as uniformity statistics.
     */
    public static final class Result {

        /**
         * Represents the configuration used for generating and evaluating facts.
         * This field is immutable and encapsulates parameters that influence
         * the generation of independent and dependent facts, the number of attempts
         * allowed for generating facts, the randomization seed, alpha values used
         * in calculations, and progress reporting frequency.
         */
        public final Config cfg;

        /**
         * A list of independent (Type I) facts generated or analyzed during a computational process.
         * <p>
         * Each element in the list is a {@code CiFact} record that encapsulates relevant information
         * about a specific fact, including its associated values and contextual data.
         * <p>
         * This variable can be {@code null} or an empty list if no independent facts are defined
         * or if the analysis process produces no such outcomes.
         */
        public final List<CiFact> indepFacts;

        /**
         * A 2D array representing the p-values corresponding to independent (Type I) facts.
         * Each element in the matrix is a p-value calculated during an analysis process,
         * indicating the statistical significance of a hypothesis test applied to the data.
         * <p>
         * Characteristics:
         * - Each row corresponds to a specific independent fact or category.
         * - Each column represents a distinct hypothesis test or statistical measurement
         * associated with the corresponding fact.
         * <p>
         * The dimensions and interpretation of this array depend on the specific computation
         * or analysis performed.
         */
        public final double[][] indepPvals;

        /**
         * A 3D array representing the decisions or classifications derived from independent facts.
         * Each entry in the array corresponds to decisions made during the analysis based on
         * independent (Type I) facts.
         * <p>
         * Structure:
         * - The first dimension typically corresponds to a grouping or classification level.
         * - The second and third dimensions vary depending on the computational process and represent
         * the detailed decision data for specific groups or configurations.
         * <p>
         * Usage Context:
         * This variable is populated during the computation or evaluation of independent facts
         * and is often utilized in contexts where detailed decision-making data must be stored
         * and analyzed systematically.
         */
        public final int[][][] indepDecisions;

        /**
         * A list of dependent (Type II) facts.
         * This field stores instances of {@link CiFact} that represent contextualized informational facts
         * identified as dependent during the analysis or computation process.
         * <p>
         * Characteristics:
         * - May be null or empty.
         * - Each {@link CiFact} in the list encapsulates integer values and associated contextual data.
         * <p>
         * Role in Analysis:
         * Dependent facts (Type II) are derived based on specific dependencies or relationships identified
         * between data points or variables through computation.
         */
        public final List<CiFact> depFacts;

        /**
         * A two-dimensional array representing p-values associated with dependent (Type II) facts.
         * Each element in the array corresponds to a statistical outcome derived during
         * the analysis process for specific dependent facts.
         * <p>
         * The dimensions and content of this array are determined by the nature of the
         * computation or analysis performed. Typically, the rows correspond to individual
         * dependent facts, and the columns represent specific tests, metrics, or
         * conditions applied during the analysis.
         * <p>
         * This field is immutable and initialized during the construction of the {@code Result} object.
         */
        public final double[][] depPvals;

        /**
         * A 3D array representing the decisions or classifications derived from dependent facts.
         * Each element in the array is typically the outcome of a specific computation or analysis
         * process associated with dependent (Type II) contextualized informational facts.
         * <p>
         * - The first dimension corresponds to a grouping such as tests or configurations.
         * - The second dimension corresponds to the specific dependent facts under consideration.
         * - The third dimension corresponds to indices or levels of decision granularity.
         * <p>
         * The exact structure and semantics of this array are determined by the specific
         * algorithms or workflows applied during the analysis.
         */
        public final int[][][] depDecisions;

        /**
         * An array of uniformity test results represented using the {@link Uniformity} record.
         * <p>
         * Each element in the array provides statistical outputs from Kolmogorov-Smirnov (KS)
         * and Anderson-Darling (AD) tests, used to assess how closely a dataset aligns
         * with a uniform distribution. These results play a critical role in determining
         * the uniformity characteristics of the data analyzed in the corresponding {@link Result}.
         * <p>
         * This field is immutable and intended to store the outcomes of uniformity analyses
         * conducted during a computation or evaluation process.
         */
        public final Uniformity[] uniformity;

        /**
         * Constructs a new Result instance, representing the outcome of a computation or analysis process.
         *
         * @param cfg            The configuration used for generating and evaluating facts. Must not be null.
         * @param indepFacts     A list of independent (Type I) facts. May be null or empty.
         * @param indepPvals     A 2D array containing p-values corresponding to independent facts.
         *                       Its dimensions and content depend on the analysis performed.
         * @param indepDecisions A 3D array representing the decisions or classifications
         *                       derived from independent facts. The exact structure depends on the process.
         * @param depFacts       A list of dependent (Type II) facts. May be null or empty.
         * @param depPvals       A 2D array containing p-values corresponding to dependent facts.
         *                       Its dimensions and content depend on the analysis performed.
         * @param depDecisions   A 3D array representing the decisions or classifications
         *                       derived from dependent facts. The exact structure depends on the process.
         * @param uniformity     An array containing uniformity test results (KS and AD tests),
         *                       representing how closely the data aligns with a uniform distribution.
         */
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

    /**
     * Represents the uniformity test results using Kolmogorov-Smirnov (KS) and Anderson-Darling (AD) tests.
     * <p>
     * This record encapsulates the p-values obtained from the KS and AD tests,
     * which are statistical tests used to evaluate whether a given dataset comes
     * from a uniform distribution.
     *
     * @param ksPValue the p-value resulting from the Kolmogorov-Smirnov test.
     *                 A higher value indicates stronger evidence that the dataset
     *                 follows a uniform distribution.
     * @param adPValue the p-value resulting from the Anderson-Darling test.
     *                 A higher value indicates stronger evidence that the dataset
     *                 follows a uniform distribution.
     */
    public record Uniformity(double ksPValue, double adPValue) {
    }

    /**
     * Represents a container for categorizing CiFact instances into two separate lists:
     * independent facts and dependent facts.
     * <p>
     * This record is immutable and provides a compact, thread-safe data structure
     * for organizing and accessing categorized facts.
     *
     * @param indepFacts the list of independent CiFact instances
     * @param depFacts   the list of dependent CiFact instances
     */
    private record Buckets(List<CiFact> indepFacts, List<CiFact> depFacts) {
    }
}
package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.algcomparison.statistic.AdjacencyPrecision;
import edu.cmu.tetrad.algcomparison.statistic.AdjacencyRecall;
import edu.cmu.tetrad.algcomparison.statistic.ArrowheadPrecision;
import edu.cmu.tetrad.algcomparison.statistic.ArrowheadRecall;
import edu.cmu.tetrad.algcomparison.statistic.StructuralHammingDistance;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.EmCovarianceEstimator;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.MissingnessInjector;
import edu.cmu.tetrad.data.MissingnessInjector.Mechanism;
import edu.cmu.tetrad.data.missing.ImputationSearch;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Stage 3 of the missing-data experiments: how well does BOSS recover the graph, and the causes of a designated
 * target, under each missingness mechanism and each way Tetrad has of handling missing values?
 *
 * <p>Stage 2 ({@link MissingDataScoreDiagnostics}) asked whether each policy's score ranks the truth above its
 * neighbours. This asks what the search makes of that, on graphs and data closer to the CHEM setting: a linear SEM
 * with positive coefficients, Gaussian or skewed errors, and a target standing in for the final grade.</p>
 *
 * <h2>Design</h2>
 *
 * <ul>
 *   <li><b>Graph and data.</b> A random DAG; coefficients drawn uniformly from [{@value #COEF_LO},
 *   {@value #COEF_HI}], all positive, so that a negative estimate is a sign error. Errors are Gaussian, or
 *   centered exponential (skewed, as course scores often are), scaled to a random variance in [1, 2].</li>
 *   <li><b>Target.</b> The sink with the most parents, ties broken by most ancestors. Being a sink, its adjacencies are exactly its parents, so
 *   adjacency recall and precision at the target are recall and precision of its causes. It is never deleted,
 *   as the final grade is nearly always present in CHEM. The root with the most descendants is also never
 *   deleted, so that MAR mechanisms have a fully observed driver.</li>
 *   <li><b>Mechanisms.</b> The seven of Stage 2: MCAR; MAR driven by the root and by the target; monotone
 *   attrition along the causal order without and with sequential MAR drivers; self-masking; colluder.</li>
 *   <li><b>Arms.</b> BOSS with SemBicScore under ORACLE (complete data), LISTWISE, TESTWISE, EM_COV with the
 *   FULL_N and MIN_PAIRWISE effective sample sizes, multiple imputation pooled by edge frequency, and PAIRWISE
 *   (see below).</li>
 * </ul>
 *
 * <h2>The PAIRWISE arm</h2>
 *
 * <p>A candidate repair of test-wise deletion, not yet a Tetrad policy. Stage 2 found that TESTWISE's errors come
 * from scoring each parent set on its own rows: when adding a parent shrinks the rows, the shared part of the fit
 * is re-estimated on a different sample, and that noise, scaled to the full n, swamps the penalty. PAIRWISE keeps
 * the available-case idea but estimates every moment once: each correlation on the rows complete on its pair,
 * rescaled by each column's standard deviation over all its rows, shrunk toward the identity just enough to be
 * positive definite, and scored with the smallest pairwise count as n. Every local score then reads the same
 * matrix, so comparisons share their estimation noise and the score is score-equivalent. Like TESTWISE it needs no
 * model of the missingness, and unlike EM it does not need a Gaussian working model of the data, so the same
 * construction could serve the embedded scores (degenerate Gaussian, basis-function) where EM is unavailable. Like
 * TESTWISE it is consistent under MCAR, not under MAR in general.</p>
 *
 * <h2>Settings</h2>
 *
 * <p>Arguments of the form {@code key=value} override the defaults: {@code reps}, {@code n}, {@code p},
 * {@code edges}, {@code coefLo}, {@code coefHi}. A bare number is taken as {@code reps}. For example,
 * {@code n=250 p=24 edges=36 coefLo=0.2 coefHi=0.6} is closer to CHEM's size and signal strength.</p>
 *
 * <h2>Output</h2>
 *
 * <p>For each error distribution and mechanism, two tables.</p>
 *
 * <ul>
 *   <li><b>Search</b>: adjacency and arrowhead precision and recall against the true CPDAG, SHD, and recall and
 *   precision of the target's parents, as mean and standard error over repetitions; and seconds per run.</li>
 *   <li><b>Estimation</b>: the target regressed on its <i>true</i> parents, so that search error is out of the
 *   picture, using all complete data, only the rows complete on the target and its parents, and the EM covariance.
 *   Reported as mean absolute error and mean signed error of the coefficients, and the share of coefficients
 *   estimated negative although every true one is positive. This is the negative-coefficient question in
 *   simulation.</li>
 * </ul>
 *
 * <p>Every repetition is also written to {@value #CSV_FILE} in the working directory, one row per arm, for
 * plotting.</p>
 *
 * <h2>Expectations</h2>
 *
 * <p>From Stage 2's smoke run, conjectured, not shown: TESTWISE over-adds edges, so its adjacency precision should
 * trail its recall, and it is not score-equivalent, so its orientations may be driven by the missingness pattern;
 * EM_COV FULL_N should also over-add; EM_COV MIN_PAIRWISE should sit closest to the oracle. There is no
 * common-rows arm here: a search needs a score that implements it, which is Stage 4.</p>
 *
 * <p>Run {@link #main(String[])} from IntelliJ. An optional first argument overrides the number of repetitions,
 * which is useful for a quick check. With the defaults, expect several minutes; the multiple-imputation arm is
 * the slowest and can be switched off with {@link #RUN_MULTIPLE_IMPUTATION}.</p>
 *
 * @author josephramsey
 */
public final class MissingDataRecoveryHarness {

    // ---------------------------------------------------------------- configuration

    /** Variables in the simulated graph. */
    private static final int NUM_VARS = 20;

    /** Edges in the simulated graph. */
    private static final int NUM_EDGES = 30;

    /** Rows simulated before deletion. */
    private static final int SAMPLE_SIZE = 1000;

    /** Default repetitions per condition; each draws a fresh graph and SEM, shared by all mechanisms. */
    private static final int NUM_REPS = 10;

    /** BIC penalty discount for every arm. */
    private static final double PENALTY_DISCOUNT = 2.0;

    /** Coefficient range; all positive, so a negative estimate is a sign error. */
    private static final double COEF_LO = 0.3, COEF_HI = 0.9;

    /** Marginal missingness rate for every deleted variable under the per-cell mechanisms. */
    private static final double RATE = 0.3;

    /** Row propensity for every mechanism. */
    private static final double ROW_PROPENSITY = 0.0;

    /** Logit-scale weight on a standardized driver value. */
    private static final double VALUE_WEIGHT = 1.5;

    /** Logit-scale weight on a driver's indicator, for the colluder mechanism. */
    private static final double INDICATOR_WEIGHT = 2.0;

    /** Cumulative dropout rate at the first and last attrition step, linear in between. */
    private static final double ATTRITION_FIRST = 0.05, ATTRITION_LAST = 0.5;

    /** Whether to run the multiple-imputation arm, the slowest. */
    private static final boolean RUN_MULTIPLE_IMPUTATION = true;

    /** Imputations for the multiple-imputation arm. */
    private static final int NUM_IMPUTATIONS = 5;

    /** Seconds any single search may take before it is abandoned; 0 disables the limit. */
    private static final int ARM_TIMEOUT_SECONDS = 120;

    // Settings in effect, from the defaults above unless overridden on the command line.
    private static int numVars = NUM_VARS, numEdges = NUM_EDGES, sampleSize = SAMPLE_SIZE;
    private static double coefLo = COEF_LO, coefHi = COEF_HI;

    /** Per-repetition results, written to the working directory. */
    private static final String CSV_FILE = "missing_data_recovery.csv";

    private MissingDataRecoveryHarness() {
    }

    // ---------------------------------------------------------------- errors, arms, mechanisms

    private enum Errors { GAUSSIAN, SKEWED }

    private interface Arm {
        Graph search(DataSet complete, DataSet missing) throws Exception;
    }

    private static Map<String, Arm> arms() {
        Map<String, Arm> arms = new LinkedHashMap<>();
        arms.put("ORACLE", (complete, missing) -> boss(score(complete, null)));
        arms.put("LISTWISE", (complete, missing) -> boss(score(missing, MissingDataSpec.listwise())));
        arms.put("TESTWISE", (complete, missing) -> boss(score(missing, MissingDataSpec.testwise())));
        arms.put("EM_COV FULL_N", (complete, missing) -> boss(score(missing, MissingDataSpec.emCovariance()
                .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.FULL_N))));
        arms.put("EM_COV MIN_PAIRWISE", (complete, missing) -> boss(score(missing, MissingDataSpec.emCovariance()
                .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MIN_PAIRWISE))));
        arms.put("PAIRWISE", (complete, missing) -> boss(pairwiseScore(missing)));
        if (RUN_MULTIPLE_IMPUTATION) {
            arms.put("MI (m=" + NUM_IMPUTATIONS + ", pooled)", (complete, missing) -> {
                Parameters params = new Parameters();
                params.set(Params.PENALTY_DISCOUNT, PENALTY_DISCOUNT);
                params.set(Params.NUM_STARTS, 1);
                params.set(Params.USE_BES, false);
                params.set(Params.VERBOSE, false);
                ImputationSearch.Result result = ImputationSearch.search(missing,
                        new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Boss(
                                new edu.cmu.tetrad.algcomparison.score.SemBicScore()),
                        params, null, MissingDataSpec.multipleImputation(NUM_IMPUTATIONS));
                return plainCopy(result.pooledGraph);
            });
        }
        return arms;
    }

    private static SemBicScore score(DataSet data, MissingDataSpec spec) {
        SemBicScore score = new SemBicScore(data, true, spec);
        // Set after construction: the test-wise path sets its own discount in the constructor.
        score.setPenaltyDiscount(PENALTY_DISCOUNT);
        return score;
    }

    /**
     * The PAIRWISE score: SemBicScore on a pairwise-deletion correlation matrix rescaled to covariances, made
     * positive definite by the least shrinkage of the off-diagonal toward zero, with the smallest pairwise complete
     * count as the sample size. See the class comment.
     */
    private static SemBicScore pairwiseScore(DataSet data) {
        int n = data.getNumRows(), p = data.getNumColumns();
        double[][] x = new double[n][p];
        for (int i = 0; i < n; i++) for (int j = 0; j < p; j++) x[i][j] = data.getDouble(i, j);

        double[] sd = new double[p];
        for (int j = 0; j < p; j++) {
            double s = 0, ss = 0;
            int m = 0;
            for (int i = 0; i < n; i++) {
                if (Double.isNaN(x[i][j])) continue;
                s += x[i][j];
                ss += x[i][j] * x[i][j];
                m++;
            }
            if (m < 2) throw new IllegalArgumentException("Fewer than two values in column " + j + ".");
            double mean = s / m;
            sd[j] = Math.sqrt(Math.max(0.0, (ss - m * mean * mean) / (m - 1)));
        }

        double[][] corr = new double[p][p];
        int minCount = n;
        for (int a = 0; a < p; a++) {
            corr[a][a] = 1.0;
            for (int b = a + 1; b < p; b++) {
                double sa = 0, sb = 0, saa = 0, sbb = 0, sab = 0;
                int m = 0;
                for (int i = 0; i < n; i++) {
                    double u = x[i][a], v = x[i][b];
                    if (Double.isNaN(u) || Double.isNaN(v)) continue;
                    sa += u; sb += v; saa += u * u; sbb += v * v; sab += u * v;
                    m++;
                }
                minCount = Math.min(minCount, m);
                double r = 0.0;
                if (m > 2) {
                    double cab = sab - sa * sb / m, caa = saa - sa * sa / m, cbb = sbb - sb * sb / m;
                    if (caa > 0 && cbb > 0) r = cab / Math.sqrt(caa * cbb);
                }
                corr[a][b] = corr[b][a] = r;
            }
        }

        // The least shrinkage of the off-diagonal that leaves the matrix comfortably positive definite.
        double lo = 0.0, hi = 1.0;
        if (!positiveDefinite(corr, 1.0)) {
            for (int step = 0; step < 40; step++) {
                double mid = 0.5 * (lo + hi);
                if (positiveDefinite(corr, 1.0 - mid)) hi = mid;
                else lo = mid;
            }
        } else {
            hi = 0.0;
        }
        double keep = 1.0 - hi;

        double[][] cov = new double[p][p];
        for (int a = 0; a < p; a++) {
            for (int b = 0; b < p; b++) {
                double r = a == b ? 1.0 : keep * corr[a][b];
                cov[a][b] = r * sd[a] * sd[b];
            }
        }

        SemBicScore score = new SemBicScore(
                new edu.cmu.tetrad.data.CovarianceMatrix(data.getVariables(), cov, Math.max(minCount, p + 2)));
        score.setPenaltyDiscount(PENALTY_DISCOUNT);
        return score;
    }

    /** Whether the correlation matrix with off-diagonal scaled by {@code keep} has a Cholesky factor with a margin. */
    private static boolean positiveDefinite(double[][] corr, double keep) {
        int p = corr.length;
        double[][] l = new double[p][p];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j <= i; j++) {
                double s = i == j ? 1.0 : keep * corr[i][j];
                for (int k = 0; k < j; k++) s -= l[i][k] * l[j][k];
                if (i == j) {
                    if (s < 1e-3) return false;
                    l[i][i] = Math.sqrt(s);
                } else {
                    l[i][j] = s / l[j][j];
                }
            }
        }
        return true;
    }

    private static Graph boss(SemBicScore score) throws InterruptedException {
        Boss boss = new Boss(score);
        boss.setUseBes(false);
        boss.setNumStarts(1);
        return new PermutationSearch(boss).search();
    }

    private record Injection(double[] rates, Mechanism mechanism) {
    }

    private interface MechanismFactory {
        Injection build(Sim s);
    }

    private static Map<String, MechanismFactory> mechanisms() {
        Map<String, MechanismFactory> m = new LinkedHashMap<>();
        m.put("MCAR", s -> new Injection(perCellRates(s), Mechanism.none()));
        m.put("MAR (root)", s -> {
            Mechanism mech = Mechanism.none();
            for (int j : s.deleted) mech = mech.withValueEffect(j, s.root, VALUE_WEIGHT);
            return new Injection(perCellRates(s), mech);
        });
        m.put("MAR (target)", s -> {
            // Low target values delete earlier variables, as a weak student might skip work.
            Mechanism mech = Mechanism.none();
            for (int j : s.deleted) mech = mech.withValueEffect(j, s.target, -VALUE_WEIGHT);
            return new Injection(perCellRates(s), mech);
        });
        m.put("ATTRITION (MCAR)", s -> new Injection(attritionRates(s), Mechanism.none().withAttrition(s.deleted)));
        m.put("ATTRITION (seq. MAR)", s -> {
            Mechanism mech = Mechanism.none().withAttrition(s.deleted);
            for (int t = 1; t < s.deleted.length; t++) {
                mech = mech.withValueEffect(s.deleted[t], s.deleted[t - 1], -VALUE_WEIGHT);
            }
            return new Injection(attritionRates(s), mech);
        });
        m.put("SELF-MASKING", s -> {
            Mechanism mech = Mechanism.none();
            for (int j : s.deleted) mech = mech.withSelfMasking(j, -VALUE_WEIGHT);
            return new Injection(perCellRates(s), mech);
        });
        m.put("COLLUDER", s -> {
            Mechanism mech = Mechanism.none();
            for (int t = 1; t < s.deleted.length; t++) {
                mech = mech.withValueEffect(s.deleted[t], s.deleted[t - 1], -VALUE_WEIGHT)
                        .withIndicatorEffect(s.deleted[t], s.deleted[t - 1], INDICATOR_WEIGHT);
            }
            return new Injection(perCellRates(s), mech);
        });
        return m;
    }

    private static double[] perCellRates(Sim s) {
        double[] rates = new double[s.p];
        for (int j : s.deleted) rates[j] = RATE;
        return rates;
    }

    private static double[] attritionRates(Sim s) {
        double[] rates = new double[s.p];
        int k = s.deleted.length;
        for (int t = 0; t < k; t++) {
            rates[s.deleted[t]] = k == 1 ? ATTRITION_FIRST
                    : ATTRITION_FIRST + (ATTRITION_LAST - ATTRITION_FIRST) * t / (k - 1.0);
        }
        return rates;
    }

    // ---------------------------------------------------------------- main

    /** Search statistics, in output order. */
    private static final String[] SEARCH_STATS = {"adjP", "adjR", "arrP", "arrR", "SHD", "tgtR", "tgtP", "sec"};

    /** Estimation methods, in output order. */
    private static final String[] ESTIMATORS = {"COMPLETE DATA", "FAMILY ROWS", "EM COVARIANCE"};

    /**
     * Runs the sweep.
     *
     * @param args Optional overrides, {@code key=value}: reps, n, p, edges, coefLo, coefHi; a bare number is reps.
     * @throws Exception If simulation or output fails.
     */
    public static void main(String[] args) throws Exception {
        int reps = NUM_REPS;
        for (String arg : args) {
            String[] kv = arg.split("=", 2);
            if (kv.length == 1) { reps = Integer.parseInt(kv[0]); continue; }
            switch (kv[0].trim()) {
                case "reps" -> reps = Integer.parseInt(kv[1]);
                case "n" -> sampleSize = Integer.parseInt(kv[1]);
                case "p" -> numVars = Integer.parseInt(kv[1]);
                case "edges" -> numEdges = Integer.parseInt(kv[1]);
                case "coefLo" -> coefLo = Double.parseDouble(kv[1]);
                case "coefHi" -> coefHi = Double.parseDouble(kv[1]);
                default -> throw new IllegalArgumentException("Unknown setting: " + kv[0]);
            }
        }
        Map<String, Arm> arms = arms();
        Map<String, MechanismFactory> mechanisms = mechanisms();

        System.out.printf("Missing-data recovery: p=%d, edges=%d, n=%d, reps=%d, penalty=%.1f, rate=%.2f,"
                          + " value weight=%.1f, coefficients in [%.1f, %.1f]%n%n",
                numVars, numEdges, sampleSize, reps, PENALTY_DISCOUNT, RATE, VALUE_WEIGHT, coefLo, coefHi);

        try (PrintWriter csv = new PrintWriter(new FileWriter(CSV_FILE))) {
            csv.println("errors,mechanism,rep,completeRows,arm," + String.join(",", SEARCH_STATS));

            for (Errors errors : Errors.values()) {
                // stats[mechanism][arm] -> per-statistic accumulators; est[mechanism][estimator] likewise.
                Map<String, Map<String, Acc[]>> stats = new LinkedHashMap<>();
                Map<String, Map<String, Acc[]>> est = new LinkedHashMap<>();
                Map<String, Acc> completeRows = new LinkedHashMap<>();
                for (String mech : mechanisms.keySet()) {
                    Map<String, Acc[]> row = new LinkedHashMap<>();
                    for (String arm : arms.keySet()) row.put(arm, Acc.array(SEARCH_STATS.length));
                    stats.put(mech, row);
                    Map<String, Acc[]> e = new LinkedHashMap<>();
                    for (String m : ESTIMATORS) e.put(m, Acc.array(3));
                    est.put(mech, e);
                    completeRows.put(mech, new Acc());
                }

                for (int rep = 0; rep < reps; rep++) {
                    RandomUtil.getInstance().setSeed(7000L + rep);
                    Sim s = Sim.draw(errors);
                    Graph trueCpdag = GraphTransforms.dagToCpdag(s.dag);

                    for (Map.Entry<String, MechanismFactory> me : mechanisms.entrySet()) {
                        RandomUtil.getInstance().setSeed(11000L + rep);
                        Injection inj = me.getValue().build(s);
                        MissingnessInjector.Result injected = MissingnessInjector.inject(s.data,
                                new MissingnessInjector.Spec(inj.rates, ROW_PROPENSITY), inj.mechanism);
                        DataSet missing = injected.data();
                        completeRows.get(me.getKey()).add(injected.report().completeRows());

                        for (Map.Entry<String, Arm> ae : arms.entrySet()) {
                            long t0 = System.currentTimeMillis();
                            Graph g;
                            try {
                                g = runWithTimeout(ae.getValue(), s.data, missing);
                            } catch (TimeoutException e) {
                                System.out.printf("  [%s / %s / %s] rep %d timed out after %ds%n", errors,
                                        me.getKey(), ae.getKey(), rep, ARM_TIMEOUT_SECONDS);
                                continue;
                            } catch (Exception e) {
                                System.out.printf("  [%s / %s / %s] rep %d failed: %s%n", errors, me.getKey(),
                                        ae.getKey(), rep, e.getMessage());
                                continue;
                            }
                            double seconds = (System.currentTimeMillis() - t0) / 1000.0;
                            // The search returns the dataset's variable objects; compare on the truth's.
                            g = GraphUtils.replaceNodes(g, trueCpdag.getNodes());

                            double[] v = searchStats(trueCpdag, g, s, missing, seconds);
                            Acc[] a = stats.get(me.getKey()).get(ae.getKey());
                            StringBuilder line = new StringBuilder();
                            for (int k = 0; k < v.length; k++) {
                                if (!Double.isNaN(v[k])) a[k].add(v[k]);
                                line.append(',').append(Double.isNaN(v[k]) ? "" : String.format("%.4f", v[k]));
                            }
                            // Names are quoted: an arm name such as "MI (m=5, pooled)" contains a comma.
                            csv.printf("%s,\"%s\",%d,%d,\"%s\"%s%n", errors, me.getKey(), rep,
                                    injected.report().completeRows(), ae.getKey(), line);
                        }

                        estimate(s, missing, est.get(me.getKey()));
                    }
                    csv.flush();
                    System.out.printf("%s rep %d done%n", errors, rep);
                }

                printTables(errors, mechanisms.keySet(), stats, est, completeRows);
            }
        }

        System.out.println("Search: adj/arr = adjacency/arrowhead precision (P) and recall (R) against the true");
        System.out.println("CPDAG; SHD blank where an arm's graph is not a legal PDAG; tgtR/tgtP = recall/precision of");
        System.out.println("the target's parents. Each cell is mean (standard error) over repetitions; runs = repetitions");
        System.out.println("that produced a graph (LISTWISE fails when almost no complete rows remain).");
        System.out.println("Estimation: target on its true parents; |err| and bias are coefficient errors; neg = share");
        System.out.println("of coefficients estimated negative (all true ones are positive).");
        System.out.println("Per-repetition rows written to " + new java.io.File(CSV_FILE).getAbsolutePath());
    }

    private static double[] searchStats(Graph trueCpdag, Graph g, Sim s, DataSet data, double seconds) {
        double shd = new StructuralHammingDistance().getValue(trueCpdag, g, data);
        // The target is a sink, so its true adjacencies are exactly its parents.
        Node gTarget = g.getNode(s.names.get(s.target));
        int truePar = s.parents[s.target].length, found = 0;
        for (int k : s.parents[s.target]) {
            if (g.isAdjacentTo(gTarget, g.getNode(s.names.get(k)))) found++;
        }
        int adj = g.getAdjacentNodes(gTarget).size();
        double tgtR = truePar == 0 ? Double.NaN : found / (double) truePar;
        double tgtP = adj == 0 ? Double.NaN : found / (double) adj;

        return new double[]{
                new AdjacencyPrecision().getValue(trueCpdag, g, data),
                new AdjacencyRecall().getValue(trueCpdag, g, data),
                new ArrowheadPrecision().getValue(trueCpdag, g, data),
                new ArrowheadRecall().getValue(trueCpdag, g, data),
                shd < 0 ? Double.NaN : shd,
                tgtR, tgtP, seconds};
    }

    // ---------------------------------------------------------------- estimation

    /**
     * Regresses the target on its true parents three ways and accumulates |error|, signed error and the share of
     * negative estimates.
     */
    private static void estimate(Sim s, DataSet missing, Map<String, Acc[]> acc) {
        int[] par = s.parents[s.target];
        if (par.length == 0) return;
        int[] fam = new int[par.length + 1];
        System.arraycopy(par, 0, fam, 0, par.length);
        fam[par.length] = s.target;

        record(acc.get("COMPLETE DATA"), s, par, regress(sampleCov(s.data, fam, false), par.length));
        record(acc.get("FAMILY ROWS"), s, par, regress(sampleCov(missing, fam, true), par.length));

        double[][] em = null;
        try {
            ICovarianceMatrix cov = new EmCovarianceEstimator(missing).estimate();
            Map<String, Integer> index = new HashMap<>();
            List<Node> vars = cov.getVariables();
            for (int j = 0; j < vars.size(); j++) index.put(vars.get(j).getName(), j);
            em = new double[fam.length][fam.length];
            for (int a = 0; a < fam.length; a++) {
                for (int b = 0; b < fam.length; b++) {
                    em[a][b] = cov.getValue(index.get(s.names.get(fam[a])), index.get(s.names.get(fam[b])));
                }
            }
        } catch (RuntimeException e) {
            System.out.println("  EM covariance failed: " + e.getMessage());
        }
        if (em != null) record(acc.get("EM COVARIANCE"), s, par, regress(em, par.length));
    }

    private static void record(Acc[] acc, Sim s, int[] par, double[] b) {
        if (b == null) return;
        for (int k = 0; k < par.length; k++) {
            double err = b[k] - s.coef[s.target][par[k]];
            acc[0].add(Math.abs(err));
            acc[1].add(err);
            acc[2].add(b[k] < 0 ? 1.0 : 0.0);
        }
    }

    /**
     * The covariance of the given columns, the last being the response; if {@code completeOnly}, over the rows
     * complete on all of them. Null if too few rows.
     */
    private static double[][] sampleCov(DataSet data, int[] cols, boolean completeOnly) {
        int q = cols.length;
        List<double[]> rows = new ArrayList<>();
        for (int i = 0; i < data.getNumRows(); i++) {
            double[] r = new double[q];
            boolean ok = true;
            for (int a = 0; a < q; a++) {
                r[a] = data.getDouble(i, cols[a]);
                if (Double.isNaN(r[a])) ok = false;
            }
            if (ok || !completeOnly) rows.add(r);
        }
        int m = rows.size();
        if (m <= q + 1) return null;
        double[] mean = new double[q];
        for (double[] r : rows) for (int a = 0; a < q; a++) mean[a] += r[a] / m;
        double[][] c = new double[q][q];
        for (double[] r : rows) {
            for (int a = 0; a < q; a++) {
                for (int b = 0; b < q; b++) c[a][b] += (r[a] - mean[a]) * (r[b] - mean[b]) / (m - 1);
            }
        }
        return c;
    }

    /**
     * Coefficients of the last variable on the first k, from a covariance matrix, by Gaussian elimination with
     * partial pivoting. Null if the matrix is null or singular.
     */
    private static double[] regress(double[][] c, int k) {
        if (c == null) return null;
        double[][] a = new double[k][k + 1];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) a[i][j] = c[i][j];
            a[i][k] = c[i][k];
        }
        for (int col = 0; col < k; col++) {
            int piv = col;
            for (int r = col + 1; r < k; r++) if (Math.abs(a[r][col]) > Math.abs(a[piv][col])) piv = r;
            if (Math.abs(a[piv][col]) < 1e-12) return null;
            double[] tmp = a[col];
            a[col] = a[piv];
            a[piv] = tmp;
            for (int r = 0; r < k; r++) {
                if (r == col) continue;
                double f = a[r][col] / a[col][col];
                for (int j = col; j <= k; j++) a[r][j] -= f * a[col][j];
            }
        }
        double[] b = new double[k];
        for (int i = 0; i < k; i++) b[i] = a[i][k] / a[i][i];
        return b;
    }

    // ---------------------------------------------------------------- output

    private static void printTables(Errors errors, Iterable<String> mechanisms,
                                    Map<String, Map<String, Acc[]>> stats, Map<String, Map<String, Acc[]>> est,
                                    Map<String, Acc> completeRows) {
        System.out.println();
        System.out.println("################ errors: " + errors + " ################");
        for (String mech : mechanisms) {
            System.out.printf("%n=== %s  (mean complete rows %.0f of %d) ===%n", mech,
                    completeRows.get(mech).mean(), sampleSize);
            System.out.printf("%-22s", "search");
            for (String h : SEARCH_STATS) System.out.printf(" %15s", h);
            System.out.printf(" %5s%n", "runs");
            for (Map.Entry<String, Acc[]> e : stats.get(mech).entrySet()) {
                System.out.printf("%-22s", e.getKey());
                for (Acc a : e.getValue()) System.out.printf(" %15s", a.cell());
                // Repetitions that produced a graph; a failed or timed-out search is left out of the means.
                System.out.printf(" %5d%n", e.getValue()[SEARCH_STATS.length - 1].n);
            }
            System.out.printf("%-22s %15s %15s %15s%n", "estimation", "|err|", "bias", "neg");
            for (Map.Entry<String, Acc[]> e : est.get(mech).entrySet()) {
                System.out.printf("%-22s", e.getKey());
                for (Acc a : e.getValue()) System.out.printf(" %15s", a.cell());
                System.out.println();
            }
        }
        System.out.println();
    }

    /** A running mean and standard error. */
    private static final class Acc {
        private double sum, sumSq;
        private int n;

        static Acc[] array(int k) {
            Acc[] a = new Acc[k];
            for (int i = 0; i < k; i++) a[i] = new Acc();
            return a;
        }

        void add(double v) {
            sum += v;
            sumSq += v * v;
            n++;
        }

        double mean() {
            return n == 0 ? Double.NaN : sum / n;
        }

        String cell() {
            if (n == 0) return "-";
            double m = sum / n;
            if (n == 1) return String.format("%.3f", m);
            double var = Math.max(0.0, (sumSq - n * m * m) / (n - 1));
            return String.format("%.3f (%.3f)", m, Math.sqrt(var / n));
        }
    }

    // ---------------------------------------------------------------- simulation

    /**
     * A simulated linear SEM: the DAG on the data's variables, its parents and coefficients in column indices, the
     * complete data, the fully observed root and target, and the deleted variables in causal order.
     */
    private static final class Sim {
        int p;
        List<String> names;
        Graph dag;
        DataSet data;
        int[][] parents;
        double[][] coef;
        int[] order;
        int root, target;
        int[] deleted;

        static Sim draw(Errors errors) {
            Sim s = new Sim();
            s.p = numVars;
            Graph g = RandomGraph.randomGraph(numVars, 0, numEdges, 100, 100, 100, false);

            List<Node> vars = new ArrayList<>();
            s.names = new ArrayList<>();
            for (Node n : g.getNodes()) {
                vars.add(new ContinuousVariable(n.getName()));
                s.names.add(n.getName());
            }
            s.dag = GraphUtils.replaceNodes(g, vars);

            Map<String, Integer> index = new HashMap<>();
            for (int j = 0; j < s.p; j++) index.put(s.names.get(j), j);
            s.parents = new int[s.p][];
            for (int j = 0; j < s.p; j++) {
                s.parents[j] = s.dag.getParents(vars.get(j)).stream()
                        .mapToInt(n -> index.get(n.getName())).toArray();
            }
            s.order = topologicalOrder(s.parents);

            RandomUtil r = RandomUtil.getInstance();
            s.coef = new double[s.p][s.p];
            double[] errSd = new double[s.p];
            for (int j = 0; j < s.p; j++) {
                for (int k : s.parents[j]) s.coef[j][k] = coefLo + (coefHi - coefLo) * r.nextDouble();
                errSd[j] = Math.sqrt(1.0 + r.nextDouble());
            }

            double[][] x = new double[sampleSize][s.p];
            for (int i = 0; i < sampleSize; i++) {
                for (int j : s.order) {
                    double e = errors == Errors.GAUSSIAN ? r.nextGaussian(0, 1)
                            : -Math.log(1.0 - r.nextDouble()) - 1.0; // centered Exp(1): mean 0, variance 1
                    double v = errSd[j] * e;
                    for (int k : s.parents[j]) v += s.coef[j][k] * x[i][k];
                    x[i][j] = v;
                }
            }
            s.data = new BoxDataSet(new DoubleDataBox(x), vars);

            boolean[][] anc = ancestry(s.parents, s.order);
            int bestRoot = -1, bestSink = -1, mostDesc = -1, mostAnc = -1;
            for (int j : s.order) {
                int desc = 0, ancs = 0;
                for (int k = 0; k < s.p; k++) {
                    if (anc[j][k]) desc++;
                    if (anc[k][j]) ancs++;
                }
                boolean isSink = true;
                for (int k = 0; k < s.p; k++) for (int q : s.parents[k]) if (q == j) isSink = false;
                if (s.parents[j].length == 0 && desc > mostDesc) { mostDesc = desc; bestRoot = j; }
                // The target is the sink with the most parents, ties to the most ancestors: like a final grade,
                // the end point of many paths with several direct causes.
                int score = s.parents[j].length * 1000 + ancs;
                if (isSink && score > mostAnc) { mostAnc = score; bestSink = j; }
            }
            if (bestSink < 0 || bestSink == bestRoot || s.parents[bestSink].length == 0) {
                throw new IllegalStateException("No target with parents in this graph; try another seed.");
            }
            s.root = bestRoot;
            s.target = bestSink;

            List<Integer> del = new ArrayList<>();
            for (int j : s.order) if (j != s.root && j != s.target) del.add(j);
            s.deleted = del.stream().mapToInt(Integer::intValue).toArray();
            return s;
        }

        private static int[] topologicalOrder(int[][] parents) {
            int p = parents.length;
            int[] indeg = new int[p];
            for (int j = 0; j < p; j++) indeg[j] = parents[j].length;
            int[] out = new int[p];
            boolean[] done = new boolean[p];
            for (int t = 0; t < p; t++) {
                int next = -1;
                for (int j = 0; j < p; j++) if (!done[j] && indeg[j] == 0) { next = j; break; }
                if (next < 0) throw new IllegalStateException("The graph has a cycle.");
                done[next] = true;
                out[t] = next;
                for (int k = 0; k < p; k++) for (int q : parents[k]) if (q == next) indeg[k]--;
            }
            return out;
        }

        /** anc[a][b] is true when a is a proper ancestor of b. */
        private static boolean[][] ancestry(int[][] parents, int[] order) {
            int p = parents.length;
            boolean[][] anc = new boolean[p][p];
            for (int j : order) {
                for (int q : parents[j]) {
                    anc[q][j] = true;
                    for (int a = 0; a < p; a++) if (anc[a][q]) anc[a][j] = true;
                }
            }
            return anc;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Graph runWithTimeout(Arm arm, DataSet complete, DataSet missing) throws Exception {
        if (ARM_TIMEOUT_SECONDS <= 0) return arm.search(complete, missing);
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "missing-data-arm");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<Graph> future = executor.submit(() -> arm.search(complete, missing));
            try {
                return future.get(ARM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw e;
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception ex) throw ex;
                throw e;
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * A plain copy of a graph: same nodes and endpoints, no edge properties. Graphs pooled over imputations carry
     * annotations that the comparison statistics do not accept.
     */
    private static Graph plainCopy(Graph graph) {
        Graph out = new EdgeListGraph(graph.getNodes());
        for (Edge edge : graph.getEdges()) {
            out.addEdge(new Edge(edge.getNode1(), edge.getNode2(), edge.getEndpoint1(), edge.getEndpoint2()));
        }
        return out;
    }
}

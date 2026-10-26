package edu.cmu.tetrad.study.missingness;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.missing.MiceLiteImputer;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.ImagesScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Companion study to {@link NabiIdentificationProbe}: does the Nabi et al. (2020) full-law identification
 * boundary (no self-censoring, no colluders) predict where practical causal discovery methods degrade?
 *
 * <p>To be explicit about what this is NOT: none of the arms below implements the paper's odds-ratio
 * identifying functional, and full-law identification is neither necessary nor sufficient for any of them --
 * test-wise deletion is governed by admissible-separator-type conditions (Tu et al. 2019; Witte et al. 2021),
 * imputation by MAR-style recoverability, and the paper itself notes the target law can be identified when the
 * full law is not. So this study measures whether the theoretical boundary happens to coincide with the
 * practical-degradation boundary, which is an empirical question the literature leaves open.</p>
 *
 * <p>Linear Gaussian SEM, p = 10, 20 edges, 4 variables incomplete at a 30% rate each (NaN-marked
 * missingness, unlike the zero-coded dropout study). Three mechanisms, one per side of their boundary:</p>
 * <ul>
 *   <li>ICIN_MNAR (identified side): each incomplete variable's indicator depends on the TRUE values of two
 *       OTHER incomplete variables -- genuinely MNAR, since the drivers are themselves sometimes missing --
 *       with no self-censoring edges, and no edges between indicators, hence no colluders. By their Theorem 1
 *       the full law is identified;</li>
 *   <li>SELF_CENSOR (not identified): each indicator depends on its own variable's value (logistic
 *       self-masking, the NaN-marked cousin of the dropout study's mechanism);</li>
 *   <li>COLLUDER (not identified): incomplete variables are paired; within a pair (a, b), R_b depends on the
 *       value of a third variable, and R_a depends on the VALUE of X_b and on R_b, forming the colluder
 *       X_b -> R_a <- R_b.</li>
 * </ul>
 *
 * <p>Arms: ORACLE BOSS on the complete data; LISTWISE BOSS; TESTWISE PC (Fisher-Z, testwise spec); MICE-PMM
 * m = 5 + IMaGES BOSS (chains in parallel); MVN-EM covariance BOSS; MEAN-IMPUTE BOSS. All continuous.</p>
 *
 * <p>Predictions on record: SELF_CENSOR hurts every arm (both boundaries agree there). The interesting cells
 * are ICIN_MNAR -- identified full law, yet no arm uses the identifying functional, so degradation here would
 * show the boundary is not sufficient for practical methods -- and COLLUDER, where degradation beyond
 * ICIN_MNAR's would be consistent with the boundary mattering, though not proof that it does.</p>
 *
 * <p>Repetitions run concurrently (RandomUtil is thread-local; each rep seeds its own thread). Run from
 * IntelliJ; one line per arm per rep streams as it finishes. Nothing outside this file is touched.</p>
 *
 * @author josephramsey
 */
public final class NabiBoundaryStudy {

    private NabiBoundaryStudy() {
    }

    // ---------------------------------------------------------------- configuration

    /** Variables and edges in the simulated graph. */
    private static final int NUM_VARS = 10;
    private static final int NUM_EDGES = 20;

    /** Rows simulated. */
    private static final int SAMPLE_SIZE = 10_000;

    /** Repetitions per condition. */
    private static final int NUM_REPS = 5;

    /** Incomplete variables per dataset, and the missingness rate for each. */
    private static final int NUM_MISSING_VARS = 4;
    private static final double MISSING_RATE = 0.30;

    /** Slope of the logistic mechanisms on the standardized scale. */
    private static final double SLOPE = 1.5;

    /** Additive logit bump when the colluding indicator is itself missing. */
    private static final double COLLUDER_GAMMA = 1.0;

    /** Imputations for the MICE arm. */
    private static final int NUM_IMPUTATIONS = 5;

    /** BIC penalty discount for every score arm. */
    private static final double PENALTY_DISCOUNT = 2.0;

    /** Fisher-Z alpha. */
    private static final double ALPHA = 0.05;

    /** Base seed for the MICE chains. */
    private static final long MICE_SEED = 8675309L;

    /** Stream one line per arm per rep. */
    private static final boolean STREAM_PROGRESS = true;

    /** Threads for concurrent repetitions. */
    private static final int REP_THREADS =
            Math.max(1, Math.min(NUM_REPS, Runtime.getRuntime().availableProcessors() / 2));

    // ---------------------------------------------------------------- mechanisms

    private enum Mechanism {ICIN_MNAR, SELF_CENSOR, COLLUDER}

    /**
     * Injects NaN missingness under the given mechanism into a copy of the data, using this thread's seeded
     * RandomUtil. The incomplete variables are a random subset of NUM_MISSING_VARS columns.
     */
    private static DataSet injectMissing(DataSet complete, Mechanism mechanism) {
        DataSet out = complete.copy();
        int n = out.getNumRows();
        int p = out.getNumColumns();
        RandomUtil rand = RandomUtil.getInstance();

        List<Integer> order = new ArrayList<>();
        for (int j = 0; j < p; j++) order.add(j);
        for (int i = order.size() - 1; i > 0; i--) Collections.swap(order, i, rand.nextInt(i + 1));
        List<Integer> missingVars = order.subList(0, NUM_MISSING_VARS);

        // Standardized columns of the complete data, for the logistic drivers.
        double[][] z = new double[p][n];
        for (int j = 0; j < p; j++) {
            double mean = 0;
            for (int i = 0; i < n; i++) mean += complete.getDouble(i, j);
            mean /= n;
            double var = 0;
            for (int i = 0; i < n; i++) {
                double d = complete.getDouble(i, j) - mean;
                var += d * d;
            }
            double sd = Math.sqrt(var / (n - 1));
            if (sd == 0) sd = 1;
            for (int i = 0; i < n; i++) z[j][i] = (complete.getDouble(i, j) - mean) / sd;
        }

        switch (mechanism) {
            case ICIN_MNAR -> {
                // R_m depends on the true values of two OTHER incomplete variables. No self-censoring, no
                // R -> R edges: identified by Theorem 1, yet genuinely MNAR because the drivers are themselves
                // sometimes missing in the released data.
                for (int k = 0; k < missingVars.size(); k++) {
                    int m = missingVars.get(k);
                    int d1 = missingVars.get((k + 1) % missingVars.size());
                    int d2 = missingVars.get((k + 2) % missingVars.size());

                    double[] linear = new double[n];
                    for (int i = 0; i < n; i++) linear[i] = SLOPE * 0.5 * (z[d1][i] + z[d2][i]);
                    double a = calibrateIntercept(linear, MISSING_RATE);

                    for (int i = 0; i < n; i++) {
                        if (rand.nextDouble() < logistic(a + linear[i])) out.setDouble(i, m, Double.NaN);
                    }
                }
            }

            case SELF_CENSOR -> {
                for (int m : missingVars) {
                    double[] linear = new double[n];
                    for (int i = 0; i < n; i++) linear[i] = SLOPE * z[m][i];
                    double a = calibrateIntercept(linear, MISSING_RATE);
                    for (int i = 0; i < n; i++) {
                        if (rand.nextDouble() < logistic(a + linear[i])) out.setDouble(i, m, Double.NaN);
                    }
                }
            }

            case COLLUDER -> {
                // Pairs (a, b): R_b depends on the value of a fully observed third column; then R_a depends on
                // the VALUE of X_b and on R_b: the colluder X_b -> R_a <- R_b. No self-censoring anywhere.
                List<Integer> observedVars = order.subList(NUM_MISSING_VARS, p);

                for (int k = 0; k + 1 < missingVars.size(); k += 2) {
                    int a = missingVars.get(k);
                    int b = missingVars.get(k + 1);
                    int driverOfB = observedVars.get((k / 2) % observedVars.size());

                    double[] linearB = new double[n];
                    for (int i = 0; i < n; i++) linearB[i] = SLOPE * z[driverOfB][i];
                    double interceptB = calibrateIntercept(linearB, MISSING_RATE);
                    boolean[] bMissing = new boolean[n];
                    for (int i = 0; i < n; i++) {
                        bMissing[i] = rand.nextDouble() < logistic(interceptB + linearB[i]);
                        if (bMissing[i]) out.setDouble(i, b, Double.NaN);
                    }

                    double[] linearA = new double[n];
                    for (int i = 0; i < n; i++) {
                        linearA[i] = SLOPE * z[b][i] + COLLUDER_GAMMA * (bMissing[i] ? 1 : 0);
                    }
                    double interceptA = calibrateIntercept(linearA, MISSING_RATE);
                    for (int i = 0; i < n; i++) {
                        if (rand.nextDouble() < logistic(interceptA + linearA[i])) out.setDouble(i, a, Double.NaN);
                    }
                }
            }
        }

        return out;
    }

    private static double logistic(double t) {
        return 1.0 / (1.0 + Math.exp(-t));
    }

    /** Bisects the intercept a so that the mean of logistic(a + linear_i) hits the target rate. Callers fold
     *  slopes and bumps into the linear term. */
    private static double calibrateIntercept(double[] linear, double rate) {
        double lo = -20, hi = 20;
        for (int it = 0; it < 60; it++) {
            double a = 0.5 * (lo + hi);
            double mean = 0;
            for (double d : linear) mean += logistic(a + d);
            mean /= linear.length;
            if (mean < rate) lo = a;
            else hi = a;
        }
        return 0.5 * (lo + hi);
    }

    // ---------------------------------------------------------------- arms

    private interface Arm {
        Graph search(DataSet complete, DataSet missing) throws Exception;
    }

    private static Map<String, Arm> arms() {
        Map<String, Arm> arms = new LinkedHashMap<>();

        arms.put("ORACLE BOSS", (complete, missing) -> boss(semBic(complete)));

        arms.put("LISTWISE BOSS", (complete, missing) -> {
            SemBicScore score = new SemBicScore(missing, true, MissingDataSpec.listwise());
            score.setPenaltyDiscount(PENALTY_DISCOUNT);
            return boss(score);
        });

        arms.put("TESTWISE PC", (complete, missing) ->
                pc(new IndTestFisherZ(missing, ALPHA, MissingDataSpec.testwise())));

        arms.put("MICE + IMaGES BOSS", (complete, missing) -> {
            List<DataSet> imputed = miceImpute(missing);
            List<Score> scores = new ArrayList<>();
            for (DataSet d : imputed) scores.add(semBic(d));
            return boss(new ImagesScore(scores));
        });

        arms.put("EM-COV BOSS", (complete, missing) -> {
            SemBicScore score = new SemBicScore(missing, true, MissingDataSpec.emCovariance());
            score.setPenaltyDiscount(PENALTY_DISCOUNT);
            return boss(score);
        });

        arms.put("MEAN IMPUTE BOSS", (complete, missing) -> boss(semBic(meanImpute(missing))));

        return arms;
    }

    // ---------------------------------------------------------------- main

    /**
     * Entry point: one table per mechanism, reps in parallel.
     *
     * @param args Unused.
     * @throws Exception If a condition fails irrecoverably.
     */
    public static void main(String[] args) throws Exception {
        System.out.printf("Nabi boundary study: p=%d, edges=%d, n=%d, reps=%d, %d vars missing at %.0f%%, "
                          + "alpha=%.2f, penalty=%.1f, MICE m=%d, rep threads=%d%n",
                NUM_VARS, NUM_EDGES, SAMPLE_SIZE, NUM_REPS, NUM_MISSING_VARS, 100 * MISSING_RATE,
                ALPHA, PENALTY_DISCOUNT, NUM_IMPUTATIONS, REP_THREADS);
        System.out.println("Mechanisms, one per side of the Nabi et al. full-law identification boundary:");
        System.out.println("ICIN_MNAR (MNAR, full law identified), SELF_CENSOR (not identified), COLLUDER");
        System.out.println("(not identified). No arm implements their identifying functional, so this measures");
        System.out.println("whether the theoretical boundary coincides with practical degradation, not whether");
        System.out.println("the theorem is true.");
        System.out.println();

        for (Mechanism mechanism : Mechanism.values()) {
            runCondition(mechanism);
        }
    }

    private static void runCondition(Mechanism mechanism) throws Exception {
        System.out.printf("=== mechanism = %s ===%n", mechanism);

        List<String> armNames = new ArrayList<>(arms().keySet());
        Map<String, double[]> totals = new LinkedHashMap<>();
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, Integer> failures = new LinkedHashMap<>();
        for (String name : armNames) {
            totals.put(name, new double[7]);
            counts.put(name, new int[7]);
            failures.put(name, 0);
        }

        ExecutorService repPool = Executors.newFixedThreadPool(REP_THREADS);

        try {
            List<Future<Map<String, double[]>>> futures = new ArrayList<>();
            for (int rep = 0; rep < NUM_REPS; rep++) {
                final int theRep = rep;
                futures.add(repPool.submit((Callable<Map<String, double[]>>) () -> runRep(mechanism, theRep)));
            }

            for (Future<Map<String, double[]>> future : futures) {
                Map<String, double[]> repResult = future.get();
                for (String name : armNames) {
                    double[] stats = repResult.get(name);
                    if (stats == null) {
                        failures.merge(name, 1, Integer::sum);
                        continue;
                    }
                    double[] t = totals.get(name);
                    int[] c = counts.get(name);
                    for (int s = 0; s < 7; s++) {
                        if (Double.isFinite(stats[s])) {
                            t[s] += stats[s];
                            c[s]++;
                        }
                    }
                }
            }
        } finally {
            repPool.shutdownNow();
        }

        System.out.printf("%-20s %8s %8s %8s %8s %8s %6s %8s%n",
                "method", "adjPrec", "adjRec", "arrPrec", "arrRec", "SHD", "#E", "seconds");

        for (String name : armNames) {
            double[] t = totals.get(name);
            int[] c = counts.get(name);
            int fail = failures.get(name);
            System.out.printf("%-20s %8s %8s %8s %8s %8s %6s %8s%s%n",
                    name, mean(t, c, 0, "%.3f"), mean(t, c, 1, "%.3f"), mean(t, c, 2, "%.3f"),
                    mean(t, c, 3, "%.3f"), mean(t, c, 4, "%.1f"), mean(t, c, 5, "%.1f"), mean(t, c, 6, "%.1f"),
                    fail > 0 ? "   (" + fail + " rep(s) failed)" : "");
        }

        System.out.println();
    }

    private static Map<String, double[]> runRep(Mechanism mechanism, int rep) {
        RandomUtil.getInstance().setSeed(50_000L * (mechanism.ordinal() + 1) + rep);

        Graph dag = RandomGraph.randomGraph(NUM_VARS, 0, NUM_EDGES, 100, 100, 100, false);
        SemIm im = new SemIm(new SemPm(dag));
        DataSet complete;
        try {
            complete = im.simulateData(SAMPLE_SIZE, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Graph trueCpdag = GraphTransforms.dagToCpdag(dag);
        DataSet missing = injectMissing(complete, mechanism);

        Map<String, double[]> results = new LinkedHashMap<>();

        for (Map.Entry<String, Arm> entry : arms().entrySet()) {
            long t0 = System.currentTimeMillis();
            Graph est;

            try {
                est = entry.getValue().search(complete, missing);
            } catch (Exception e) {
                if (STREAM_PROGRESS) {
                    System.out.printf("  rep %d  %-20s FAILED: %s%n", rep, entry.getKey(), e.getMessage());
                }
                continue;
            }

            double seconds = (System.currentTimeMillis() - t0) / 1000.0;
            if (STREAM_PROGRESS) {
                System.out.printf("  rep %d  %-20s %6.1fs%n", rep, entry.getKey(), seconds);
            }

            est = edu.cmu.tetrad.graph.GraphUtils.replaceNodes(est, trueCpdag.getNodes());

            double[] stats = new double[7];
            stats[0] = new AdjacencyPrecision().getValue(trueCpdag, est, missing);
            stats[1] = new AdjacencyRecall().getValue(trueCpdag, est, missing);
            stats[2] = new ArrowheadPrecision().getValue(trueCpdag, est, missing);
            stats[3] = new ArrowheadRecall().getValue(trueCpdag, est, missing);
            double shd = new StructuralHammingDistance().getValue(trueCpdag, est, missing);
            stats[4] = shd < 0 ? Double.NaN : shd;
            stats[5] = est.getNumEdges();
            stats[6] = seconds;

            results.put(entry.getKey(), stats);
        }

        return results;
    }

    // ---------------------------------------------------------------- helpers

    private static String mean(double[] sums, int[] counts, int slot, String format) {
        if (counts[slot] == 0) return "n/a";
        return String.format(format, sums[slot] / counts[slot]);
    }

    private static List<DataSet> miceImpute(DataSet data) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(NUM_IMPUTATIONS);
        try {
            List<Future<DataSet>> futures = new ArrayList<>();
            for (int k = 0; k < NUM_IMPUTATIONS; k++) {
                final long seed = MICE_SEED + k;
                futures.add(pool.submit(() -> new MiceLiteImputer().impute(data, 1, seed).get(0)));
            }
            List<DataSet> out = new ArrayList<>();
            for (Future<DataSet> f : futures) out.add(f.get());
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    private static DataSet meanImpute(DataSet data) {
        DataSet out = data.copy();
        for (int j = 0; j < out.getNumColumns(); j++) {
            double sum = 0;
            int count = 0;
            for (int i = 0; i < out.getNumRows(); i++) {
                double v = out.getDouble(i, j);
                if (!Double.isNaN(v)) {
                    sum += v;
                    count++;
                }
            }
            double mean = count > 0 ? sum / count : 0.0;
            for (int i = 0; i < out.getNumRows(); i++) {
                if (Double.isNaN(out.getDouble(i, j))) out.setDouble(i, j, mean);
            }
        }
        return out;
    }

    private static SemBicScore semBic(DataSet data) {
        SemBicScore score = new SemBicScore(data, true);
        score.setPenaltyDiscount(PENALTY_DISCOUNT);
        return score;
    }

    private static Graph pc(IndTestFisherZ test) throws InterruptedException {
        Pc pc = new Pc(test);
        pc.setVerbose(false);
        return pc.search();
    }

    private static Graph boss(Score score) throws InterruptedException {
        edu.cmu.tetrad.search.Boss boss = new edu.cmu.tetrad.search.Boss(score);
        boss.setUseBes(false);
        boss.setNumStarts(1);
        return new PermutationSearch(boss).search();
    }
}

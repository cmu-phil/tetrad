package edu.cmu.tetrad.study;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.MissingnessInjector;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.Grasp;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps out where test-wise deletion fails and where it is fine, before any decision about its place in the
 * interface. The {@link BossMultipleImputationStudy} showed test-wise scoring in BOSS over-adding edges badly in
 * one cell of the space (Gaussian, MCAR, twenty percent missing, n of one thousand); this study sweeps the axes
 * that one cell held fixed.
 *
 * <h2>The four questions, one axis each</h2>
 *
 * <ul>
 *   <li><b>Algorithm.</b> Is the damage specific to BOSS, or does any search over a test-wise-deleted score
 *   inherit it? Every score arm runs under BOSS, FGES, and GRaSP. PC with a test-wise Fisher Z is included as
 *   its own pair of arms, since per-test deletion in constraint-based search is the estimator the literature's
 *   consistency results are actually about, and the incoherence argument against test-wise <i>scores</i> (the
 *   two sides of a score comparison computed on different rows) does not apply to it.</li>
 *   <li><b>Penalty versus likelihood.</b> Test-wise deletion's failure could come from the penalty using each
 *   family's own row count, or from the likelihood comparison itself being incoherent across row sets. The
 *   three TESTWISE arms (fullN, meanPairwise, minPairwise effective-sample-size modes) vary only the
 *   sample-size convention; if they close most of the gap to EM, the story is penalty miscalibration, which is
 *   fixable, not incoherence, which is not.</li>
 *   <li><b>Rate.</b> At low rates, competing parent sets share most of their rows and the incoherence should be
 *   mild. The sweep over {@link #MISSING_RATES} shows where the damage turns on.</li>
 *   <li><b>Sample size.</b> Incoherence is a property of the objective, so its damage should persist as n
 *   grows; noise-driven damage should shrink. Put more than one value in {@link #SAMPLE_SIZES} to see
 *   which.</li>
 * </ul>
 *
 * <p>The multiple-imputation arms are deliberately absent: that question belongs to the other study, and leaving
 * them out keeps every cell here to fast single searches, so the full sweep is minutes.</p>
 *
 * <p>Run this from IntelliJ; it streams a table per (mechanism, n, rate) cell. The simulated data are exactly
 * multivariate normal, which is the setting most favorable to the covariance-based arms; the mixed-data version
 * of this comparison, where the literature's own simulations favored deletion, is a separate follow-on.</p>
 *
 * @author josephramsey
 */
public final class TestwiseDeletionStudy {

    /**
     * Default constructor for the TestwiseDeletionStudy class.
     */
    public TestwiseDeletionStudy() {

    }

    // ---------------------------------------------------------------- configuration

    /** Variables in the simulated graph. */
    private static final int NUM_VARS = 15;

    /** Average degree of the simulated graph. */
    private static final double AVG_DEGREE = 3.0;

    /** Edges implied by the average degree: p * d / 2, rounded. */
    private static final int NUM_EDGES = (int) Math.round(NUM_VARS * AVG_DEGREE / 2.0);

    /** Repetitions per cell. Each uses a fresh graph, SEM, dataset, and missingness draw. */
    private static final int NUM_REPS = 10;

    /** Fractions of each variable's values deleted; one table per value. */
    private static final double[] MISSING_RATES = {0.05, 0.10, 0.20, 0.30};

    /**
     * Rows simulated before deletion; one pass per value. {1000} by default; {500, 1000, 2000, 5000} at a fixed
     * rate is the persistence check described in the class Javadoc.
     */
    private static final int[] SAMPLE_SIZES = {1000};

    /** BIC penalty discount for every score arm. */
    private static final double PENALTY_DISCOUNT = 2.0;

    /** Alpha for the PC arms' Fisher Z tests. */
    private static final double ALPHA = 0.05;

    /** Logit-scale weight of a driver's value on a target's deletion, for the MAR and MNAR mechanisms. */
    private static final double MECHANISM_WEIGHT = 1.5;

    /** Seconds any single arm may take before it is abandoned for the rep; 0 disables the limit. */
    private static final int ARM_TIMEOUT_SECONDS = 300;

    /**
     * The missingness mechanisms to run; one pass per value. MCAR by default. The injector calibrates the
     * marginal rates in every case, so the mechanisms differ only in what the deletion depends on.
     */
    private static final MechanismKind[] MECHANISMS = {MechanismKind.MCAR};

    /**
     * The mechanism kinds this study knows how to build; the constructions match
     * {@link BossMultipleImputationStudy}.
     */
    private enum MechanismKind {

        /** Deletion independent of everything. Every arm is consistent; the comparison is of efficiency. */
        MCAR,

        /**
         * Every third variable is fully observed and its value drives deletion of the following two. Deletion
         * depends only on observed values, so EM is valid here and the deletion arms are not.
         */
        MAR,

        /** Every variable's own value drives its own deletion. No arm is valid; this shows degradation. */
        SELF_MASKING
    }

    // ---------------------------------------------------------------- engines and arms

    /**
     * The score-based search engines the score arms are crossed with.
     */
    private enum Engine {

        /** BOSS via PermutationSearch, one start, no BES. */
        BOSS {
            @Override
            Graph run(Score score) throws InterruptedException {
                edu.cmu.tetrad.search.Boss boss = new edu.cmu.tetrad.search.Boss(score);
                boss.setUseBes(false);
                boss.setNumStarts(1);
                return new PermutationSearch(boss).search();
            }
        },

        /** FGES at its defaults. */
        FGES {
            @Override
            Graph run(Score score) throws InterruptedException {
                return new Fges(score).search();
            }
        },

        /** GRaSP at its defaults, one start, CPDAG output. */
        GRASP {
            @Override
            Graph run(Score score) throws InterruptedException {
                Grasp grasp = new Grasp(score);
                grasp.setNumStarts(1);
                grasp.bestOrder(score.getVariables());
                return grasp.getGraph(true);
            }
        };

        abstract Graph run(Score score) throws InterruptedException;
    }

    private interface Arm {
        Graph search(DataSet complete, DataSet missing) throws Exception;
    }

    /**
     * The score variants each engine runs: a display name and the spec that configures SEM-BIC for it, with null
     * meaning complete data.
     */
    private static Map<String, MissingDataSpec> scoreVariants() {
        Map<String, MissingDataSpec> variants = new LinkedHashMap<>();

        variants.put("ORACLE", null);
        variants.put("LISTWISE", MissingDataSpec.listwise());
        variants.put("TESTWISE (fullN)", MissingDataSpec.testwise()
                .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.FULL_N));
        variants.put("TESTWISE (meanPairwise)", MissingDataSpec.testwise()
                .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MEAN_PAIRWISE));
        variants.put("TESTWISE (minPairwise)", MissingDataSpec.testwise()
                .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MIN_PAIRWISE));
        variants.put("PAIRWISE_COV (meanPairwise)", MissingDataSpec.pairwiseCovariance()
                .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MEAN_PAIRWISE));
        variants.put("EM_COV (meanPairwise)", MissingDataSpec.emCovariance()
                .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MEAN_PAIRWISE));

        return variants;
    }

    private static Map<String, Arm> arms() {
        Map<String, Arm> arms = new LinkedHashMap<>();

        for (Engine engine : Engine.values()) {
            for (Map.Entry<String, MissingDataSpec> variant : scoreVariants().entrySet()) {
                MissingDataSpec spec = variant.getValue();

                arms.put(engine + " " + variant.getKey(), (complete, missing) ->
                        engine.run(scoreFor(spec == null ? complete : missing, spec)));
            }
        }

        // PC with test-wise Fisher Z: per-test deletion, the estimator the consistency literature defends. Its
        // covariance counterpart is included for the same contrast the score arms draw.
        arms.put("PC ORACLE", (complete, missing) ->
                new Pc(new IndTestFisherZ(complete, ALPHA)).search());
        arms.put("PC TESTWISE", (complete, missing) ->
                new Pc(new IndTestFisherZ(missing, ALPHA, MissingDataSpec.testwise())).search());
        arms.put("PC EM_COV (meanPairwise)", (complete, missing) ->
                new Pc(new IndTestFisherZ(missing, ALPHA, MissingDataSpec.emCovariance()
                        .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MEAN_PAIRWISE))).search());

        return arms;
    }

    /**
     * SEM-BIC for a spec. The penalty must be set after construction: the constructor's test-wise branch resets
     * the discount to 1.0, so setting it beforehand would silently leave the test-wise arms at a different
     * penalty than the rest.
     */
    private static SemBicScore scoreFor(DataSet data, MissingDataSpec spec) {
        SemBicScore score = new SemBicScore(data, true, spec);
        score.setPenaltyDiscount(PENALTY_DISCOUNT);
        return score;
    }

    // ---------------------------------------------------------------- main

    /**
     * The entry point: for each mechanism, sample size, and missing rate, simulates data, injects missingness,
     * runs every arm on every rep, and prints a summary table of adjacency precision and recall, arrowhead
     * precision and recall, structural Hamming distance where defined, and seconds per rep.
     *
     * @param args Command-line arguments; not used.
     * @throws Exception If any error occurs during simulation or search.
     */
    public static void main(String[] args) throws Exception {
        System.out.printf("Test-wise deletion study: p=%d, avg degree=%.1f (%d edges), reps=%d, penalty=%.1f, "
                          + "alpha=%.2f%n%n",
                NUM_VARS, AVG_DEGREE, NUM_EDGES, NUM_REPS, PENALTY_DISCOUNT, ALPHA);

        for (MechanismKind kind : MECHANISMS) {
            for (int n : SAMPLE_SIZES) {
                for (double rate : MISSING_RATES) {
                    runCell(kind, n, rate);
                }
            }
        }
    }

    private static void runCell(MechanismKind kind, int sampleSize, double rate) throws Exception {
        System.out.printf("=== mechanism %s, n = %d, missing = %.0f%% ===%n", kind, sampleSize, 100 * rate);

        Map<String, Arm> arms = arms();
        Map<String, double[]> totals = new LinkedHashMap<>();
        for (String name : arms.keySet()) totals.put(name, new double[6]);

        // Arms whose graphs are not legal PDAGs, so that SHD is undefined rather than zero.
        Set<String> shdUndefined = new LinkedHashSet<>();

        int[] completeCases = new int[NUM_REPS];

        for (int rep = 0; rep < NUM_REPS; rep++) {
            RandomUtil.getInstance().setSeed(1000L + rep);

            Graph dag = RandomGraph.randomGraph(NUM_VARS, 0, NUM_EDGES, 100, 100, 100, false);
            SemIm im = new SemIm(new SemPm(dag));
            DataSet complete = im.simulateData(sampleSize, false);

            Graph trueCpdag = GraphTransforms.dagToCpdag(dag);

            MissingnessInjector.Result injected = inject(complete, kind, rate);
            DataSet missing = injected.data();
            completeCases[rep] = injected.report().completeRows();

            for (Map.Entry<String, Arm> entry : arms.entrySet()) {
                long t0 = System.currentTimeMillis();
                Graph est;

                try {
                    est = runWithTimeout(entry.getValue(), complete, missing);
                } catch (java.util.concurrent.TimeoutException e) {
                    System.out.printf("  [%s] rep %d timed out after %ds; arm abandoned for this rep%n",
                            entry.getKey(), rep, ARM_TIMEOUT_SECONDS);
                    continue;
                } catch (Exception e) {
                    System.out.printf("  [%s] rep %d failed: %s%n", entry.getKey(), rep, e.getMessage());
                    continue;
                }

                double seconds = (System.currentTimeMillis() - t0) / 1000.0;

                // The searched graph carries the dataset's variable objects and the true graph carries the
                // simulator's; the confusion matrices compare node identity.
                est = edu.cmu.tetrad.graph.GraphUtils.replaceNodes(est, trueCpdag.getNodes());

                double[] t = totals.get(entry.getKey());

                t[0] += new AdjacencyPrecision().getValue(trueCpdag, est, missing);
                t[1] += new AdjacencyRecall().getValue(trueCpdag, est, missing);
                t[2] += new ArrowheadPrecision().getValue(trueCpdag, est, missing);
                t[3] += new ArrowheadRecall().getValue(trueCpdag, est, missing);
                t[4] += shd(trueCpdag, est, missing, entry.getKey(), shdUndefined);
                t[5] += seconds;
            }

            System.out.flush();
        }

        System.out.println();
        System.out.printf("%-38s %8s %8s %8s %8s %8s %9s%n",
                "method", "adjPrec", "adjRec", "arrPrec", "arrRec", "SHD", "seconds");

        for (Map.Entry<String, double[]> entry : totals.entrySet()) {
            double[] t = entry.getValue();
            boolean undefined = shdUndefined.contains(entry.getKey());

            System.out.printf("%-38s %8.3f %8.3f %8.3f %8.3f %8s %9.1f%n",
                    entry.getKey(), t[0] / NUM_REPS, t[1] / NUM_REPS, t[2] / NUM_REPS, t[3] / NUM_REPS,
                    undefined ? "n/a" : String.format("%.1f", t[4] / NUM_REPS), t[5] / NUM_REPS);
        }

        if (!shdUndefined.isEmpty()) {
            System.out.println("(SHD is n/a for " + String.join(", ", shdUndefined)
                               + ": the graph is not a legal PDAG, so the measure is undefined for it. The"
                               + " adjacency and arrowhead columns are still comparable.)");
        }

        double meanComplete = 0;
        for (int c : completeCases) meanComplete += c;
        meanComplete /= NUM_REPS;

        System.out.printf("(mean complete cases available to LISTWISE: %.1f of %d)%n%n",
                meanComplete, sampleSize);
    }

    /**
     * Builds the injection for a mechanism kind at the given rate, holding the marginal rate throughout. The
     * constructions match {@link BossMultipleImputationStudy}.
     */
    private static MissingnessInjector.Result inject(DataSet complete, MechanismKind kind, double rate) {
        int p = complete.getNumColumns();

        if (kind == MechanismKind.MCAR) {
            return MissingnessInjector.inject(complete,
                    MissingnessInjector.Spec.uniform(p, rate, 0.0));
        }

        if (kind == MechanismKind.SELF_MASKING) {
            MissingnessInjector.Mechanism mechanism = MissingnessInjector.Mechanism.none();

            for (int j = 0; j < p; j++) {
                mechanism = mechanism.withSelfMasking(j, MECHANISM_WEIGHT);
            }

            return MissingnessInjector.inject(complete,
                    MissingnessInjector.Spec.uniform(p, rate, 0.0), mechanism);
        }

        // MAR: every third variable (0, 3, 6, ...) is fully observed and drives deletion of the following two.
        // The drivers' rates are 0, so the targets' rates are raised to keep the overall fraction at the given
        // rate.
        double[] rates = new double[p];
        MissingnessInjector.Mechanism mechanism = MissingnessInjector.Mechanism.none();

        int numDrivers = 0;
        for (int j = 0; j < p; j += 3) numDrivers++;

        double targetRate = rate * p / (double) (p - numDrivers);

        for (int j = 0; j < p; j++) {
            if (j % 3 == 0) {
                rates[j] = 0.0;
            } else {
                rates[j] = targetRate;
                mechanism = mechanism.withValueEffect(j, j - (j % 3), MECHANISM_WEIGHT);
            }
        }

        return MissingnessInjector.inject(complete, new MissingnessInjector.Spec(rates, 0.0), mechanism);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Structural Hamming distance, or 0 with the arm recorded as undefined. The measure is defined only between
     * legal PDAGs and returns a -99 sentinel otherwise (PC can orient both ways under collider conflicts);
     * averaging the sentinel in would quietly report -99 as a distance, which is worse than reporting nothing.
     */
    private static double shd(Graph trueCpdag, Graph est, DataSet data, String armName, Set<String> undefined) {
        double value = new StructuralHammingDistance().getValue(trueCpdag, est, data);

        if (value < 0) {
            undefined.add(armName);
            return 0.0;
        }

        return value;
    }

    /**
     * Runs one arm, abandoning it after {@link #ARM_TIMEOUT_SECONDS}. A search that ignores interruption will
     * keep a core busy until it finishes, so the timeout bounds the reported wait rather than the actual work.
     */
    private static Graph runWithTimeout(Arm arm, DataSet complete, DataSet missing) throws Exception {
        if (ARM_TIMEOUT_SECONDS <= 0) return arm.search(complete, missing);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "study-arm");
            t.setDaemon(true);
            return t;
        });

        try {
            java.util.concurrent.Future<Graph> future = executor.submit(() -> arm.search(complete, missing));

            try {
                return future.get(ARM_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                throw cause instanceof Exception ex ? ex : new RuntimeException(cause);
            } catch (java.util.concurrent.TimeoutException e) {
                future.cancel(true);
                throw e;
            }
        } finally {
            executor.shutdownNow();
        }
    }
}

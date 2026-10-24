package edu.cmu.tetrad.study;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Boss;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.MissingnessInjector;
import edu.cmu.tetrad.data.missing.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compares BOSS accuracy under the ways Tetrad has of handling missing values, with the two multiple-imputation
 * engines as separate arms: chained equations ({@link MiceLiteImputer}) and conditional draws from an EM-fit
 * multivariate normal ({@link MvnImputer}). The prior contenders--listwise, testwise, EM covariance, and pairwise
 * covariance--are included for reference, along with mean imputation as the naive baseline and the complete data
 * as the ceiling.
 *
 * <p>Each multiple-imputation engine appears twice, because there are two defensible ways to pool m completed
 * datasets for a graph search, and they answer different questions:</p>
 *
 * <ul>
 *   <li><b>Graph pooling</b>: run BOSS on each completed dataset and keep the edges that appear in at least half
 *   of the m graphs (the {@link ImputationSearch} path, which is what a user of the MULTIPLE_IMPUTATION policy
 *   gets). The pooled graph can carry conflicting orientations, so it need not be a legal PDAG and SHD can be
 *   undefined for it.</li>
 *   <li><b>Covariance pooling</b>: average the m completed-data covariance matrices and run BOSS once on the
 *   average, at the mean-pairwise effective sample size. This is the Rubin-style pooling of the sufficient
 *   statistic rather than of the answers, always yields a legal CPDAG, and is the natural head-to-head comparison
 *   with EM_COVARIANCE, from which it differs only by the imputation noise.</li>
 * </ul>
 *
 * <p>Run this from IntelliJ; it streams a line per failure as it goes and a summary table per mechanism. With the
 * defaults below it is a few minutes, most of it in the EM fits inside the MVN imputer.</p>
 *
 * <h2>What to expect, stated before running</h2>
 *
 * <p>The simulated data are exactly multivariate normal, so the MVN imputer's model is correctly specified and
 * MICE with linear conditionals is as well; the two should land close to each other. Under MCAR every arm is
 * consistent and the comparison is of efficiency only; differences of kind need the MAR or self-masking
 * mechanism, which is one entry in {@link #MECHANISMS} away. Under self-masking (MNAR) no arm is valid, and the
 * point of including it is to see how gracefully each degrades.</p>
 *
 * @author josephramsey
 */
public final class BossMultipleImputationStudy {

    /**
     * Default constructor for the BossMultipleImputationStudy class.
     */
    public BossMultipleImputationStudy() {

    }

    // ---------------------------------------------------------------- configuration

    /** Variables in the simulated graph. */
    private static final int NUM_VARS = 15;

    /** Average degree of the simulated graph. */
    private static final double AVG_DEGREE = 3.0;

    /** Edges implied by the average degree: p * d / 2, rounded. For 15 and 3 this is 23 (degree 3.07). */
    private static final int NUM_EDGES = (int) Math.round(NUM_VARS * AVG_DEGREE / 2.0);

    /** Rows simulated before any deletion. */
    private static final int SAMPLE_SIZE = 1000;

    /** Repetitions per mechanism. Each uses a fresh graph, SEM, dataset, and missingness draw. */
    private static final int NUM_REPS = 10;

    /** Fraction of each variable's values deleted. */
    private static final double MISSING_RATE = 0.20;

    /** Imputations for the multiple-imputation arms. */
    private static final int NUM_IMPUTATIONS = 10;

    /** BIC penalty discount for every arm, so the comparison is of missing-data handling only. */
    private static final double PENALTY_DISCOUNT = 2.0;

    /** Logit-scale weight of a driver's value on a target's deletion, for the MAR and MNAR mechanisms. */
    private static final double MECHANISM_WEIGHT = 1.5;

    /**
     * Seconds any single arm may take before it is abandoned and reported as timed out; 0 disables the limit.
     */
    private static final int ARM_TIMEOUT_SECONDS = 300;

    /**
     * The missingness mechanisms to run. MCAR alone by default, matching the request; add MAR or SELF_MASKING
     * here to see the differences of kind that MCAR cannot show. The injector calibrates the marginal rates to
     * MISSING_RATE in every case, so the mechanisms differ only in what the deletion depends on.
     */
    private static final MechanismKind[] MECHANISMS = {MechanismKind.MCAR};

    /**
     * The mechanism kinds this study knows how to build.
     */
    private enum MechanismKind {

        /** Deletion independent of everything. Every arm is consistent; the comparison is of efficiency. */
        MCAR,

        /**
         * Every third variable is fully observed and its value drives deletion of the following two. Deletion
         * depends only on observed values, so EM and multiple imputation are valid and the deletion arms are not.
         */
        MAR,

        /** Every variable's own value drives its own deletion. No arm is valid; this shows degradation. */
        SELF_MASKING
    }

    // ---------------------------------------------------------------- arms

    private interface Arm {
        Graph search(DataSet complete, DataSet missing, long repSeed) throws Exception;
    }

    private static Map<String, Arm> arms() {
        Map<String, Arm> arms = new LinkedHashMap<>();

        // Ceiling: what BOSS recovers with nothing deleted.
        arms.put("ORACLE (no deletion)", (complete, missing, seed) -> boss(scoreFor(complete, null)));

        // ---- The prior contenders. ----

        arms.put("LISTWISE", (complete, missing, seed) ->
                boss(scoreFor(missing, MissingDataSpec.listwise())));

        arms.put("TESTWISE", (complete, missing, seed) ->
                boss(scoreFor(missing, MissingDataSpec.testwise())));

        arms.put("PAIRWISE_COV (meanPairwise)", (complete, missing, seed) ->
                boss(scoreFor(missing, MissingDataSpec.pairwiseCovariance()
                        .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MEAN_PAIRWISE))));

        arms.put("EM_COV (meanPairwise)", (complete, missing, seed) ->
                boss(scoreFor(missing, MissingDataSpec.emCovariance()
                        .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MEAN_PAIRWISE))));

        arms.put("MEAN IMPUTE (baseline)", (complete, missing, seed) ->
                boss(scoreFor(meanImpute(missing), null)));

        // ---- Multiple imputation, graph pooling: BOSS per completed dataset, edges kept by frequency. ----

        arms.put("MI MICE (m=" + NUM_IMPUTATIONS + ", graphs pooled)", (complete, missing, seed) ->
                graphPooledMi(missing, new MiceLiteImputer(), seed));

        arms.put("MI MVN-EM (m=" + NUM_IMPUTATIONS + ", graphs pooled)", (complete, missing, seed) ->
                graphPooledMi(missing, new MvnImputer(miSpec(seed)), seed));

        // ---- Multiple imputation, covariance pooling: one BOSS run on the average covariance. ----

        arms.put("MI MICE (m=" + NUM_IMPUTATIONS + ", cov pooled)", (complete, missing, seed) ->
                covPooledMi(missing, new MiceLiteImputer(), seed));

        arms.put("MI MVN-EM (m=" + NUM_IMPUTATIONS + ", cov pooled)", (complete, missing, seed) ->
                covPooledMi(missing, new MvnImputer(miSpec(seed)), seed));

        return arms;
    }

    /**
     * The multiple-imputation spec for a rep: m imputations, seeded by the rep so reruns reproduce.
     */
    private static MissingDataSpec miSpec(long repSeed) {
        return MissingDataSpec.multipleImputation(NUM_IMPUTATIONS).withSeed(repSeed);
    }

    /**
     * The MULTIPLE_IMPUTATION policy as a user gets it: impute m datasets with the given engine, run BOSS on
     * each, pool the graphs by edge frequency. Copied into a plain graph so the comparison statistics see no
     * sampling annotations; SHD may still be undefined for it (see the class Javadoc).
     */
    private static Graph graphPooledMi(DataSet missing, MultipleImputer imputer, long repSeed) throws Exception {
        ImputationSearch.Result result = ImputationSearch.search(
                missing, new Boss(new SemBicScore()), bossParams(), imputer, miSpec(repSeed));
        return plainCopy(result.pooledGraph);
    }

    /**
     * Rubin-style pooling of the sufficient statistic: impute m datasets with the given engine, average their
     * covariance matrices, and run BOSS once on the average at the mean-pairwise effective sample size--the same
     * sample-size convention the EM_COV arm uses, so the two arms differ only in the matrix.
     */
    private static Graph covPooledMi(DataSet missing, MultipleImputer imputer, long repSeed) throws Exception {
        List<DataSet> completed = imputer.impute(missing, NUM_IMPUTATIONS, repSeed);

        Matrix sum = null;

        for (DataSet dataSet : completed) {
            Matrix cov = new CovarianceMatrix(dataSet).getMatrix();
            if (sum == null) sum = cov.copy();
            else sum = sum.plus(cov);
        }

        if (sum == null) throw new IllegalStateException("The imputer returned no completed datasets.");

        Matrix avg = sum.scalarMult(1.0 / completed.size());

        int ess = MissingDataUtils.effectiveSampleSize(missing, MissingDataSpec.testwise()
                .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MEAN_PAIRWISE));

        ICovarianceMatrix pooled = new CovarianceMatrix(missing.getVariables(), avg, ess);

        edu.cmu.tetrad.search.score.SemBicScore score = new edu.cmu.tetrad.search.score.SemBicScore(pooled);
        score.setPenaltyDiscount(PENALTY_DISCOUNT);

        return boss(score);
    }

    // ---------------------------------------------------------------- main

    /**
     * The entry point: for each mechanism, simulates data, injects missingness, runs every arm on every rep, and
     * prints a summary table of adjacency precision and recall, arrowhead precision and recall, structural
     * Hamming distance where defined, and seconds per rep.
     *
     * @param args Command-line arguments; not used.
     * @throws Exception If any error occurs during simulation or search.
     */
    public static void main(String[] args) throws Exception {
        System.out.printf("BOSS multiple-imputation study: p=%d, avg degree=%.1f (%d edges), n=%d, "
                          + "missing=%.0f%%, m=%d, reps=%d, penalty=%.1f%n",
                NUM_VARS, AVG_DEGREE, NUM_EDGES, SAMPLE_SIZE, 100 * MISSING_RATE, NUM_IMPUTATIONS,
                NUM_REPS, PENALTY_DISCOUNT);
        System.out.println();

        for (MechanismKind kind : MECHANISMS) {
            runMechanism(kind);
        }
    }

    private static void runMechanism(MechanismKind kind) throws Exception {
        System.out.println("=== mechanism " + kind + " ===");

        if (kind == MechanismKind.MCAR) {
            System.out.println("Deletion is independent of the data; every arm is consistent, and these numbers");
            System.out.println("compare efficiency, not bias.");
        } else if (kind == MechanismKind.MAR) {
            System.out.println("Deletion depends only on fully observed drivers; EM and multiple imputation are");
            System.out.println("valid here and the deletion arms are not.");
        } else {
            System.out.println("Each variable's own value drives its own deletion; no arm is valid here, and the");
            System.out.println("question is how gracefully each degrades.");
        }

        System.out.printf("%-38s %8s %8s %8s %8s %8s %9s%n",
                "method", "adjPrec", "adjRec", "arrPrec", "arrRec", "SHD", "seconds");

        Map<String, Arm> arms = arms();
        Map<String, double[]> totals = new LinkedHashMap<>();
        for (String name : arms.keySet()) totals.put(name, new double[6]);

        // Arms whose graphs are not legal PDAGs, so that SHD is undefined rather than zero.
        Set<String> shdUndefined = new LinkedHashSet<>();

        int[] completeCases = new int[NUM_REPS];

        for (int rep = 0; rep < NUM_REPS; rep++) {
            long repSeed = 1000L + rep;
            RandomUtil.getInstance().setSeed(repSeed);

            Graph dag = RandomGraph.randomGraph(NUM_VARS, 0, NUM_EDGES, 100, 100, 100, false);
            SemIm im = new SemIm(new SemPm(dag));
            DataSet complete = im.simulateData(SAMPLE_SIZE, false);

            Graph trueCpdag = GraphTransforms.dagToCpdag(dag);

            MissingnessInjector.Result injected = inject(complete, kind);
            DataSet missing = injected.data();
            completeCases[rep] = injected.report().completeRows();

            for (Map.Entry<String, Arm> entry : arms.entrySet()) {
                long t0 = System.currentTimeMillis();
                Graph est;

                try {
                    est = runWithTimeout(entry.getValue(), complete, missing, repSeed);
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

        double meanComplete = 0;
        for (int c : completeCases) meanComplete += c;
        meanComplete /= NUM_REPS;

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

        System.out.printf("(mean complete cases available to LISTWISE: %.1f of %d)%n%n",
                meanComplete, SAMPLE_SIZE);
    }

    /**
     * Builds the injection for a mechanism kind, holding the marginal rate at MISSING_RATE throughout.
     */
    private static MissingnessInjector.Result inject(DataSet complete, MechanismKind kind) {
        int p = complete.getNumColumns();

        if (kind == MechanismKind.MCAR) {
            return MissingnessInjector.inject(complete,
                    MissingnessInjector.Spec.uniform(p, MISSING_RATE, 0.0));
        }

        if (kind == MechanismKind.SELF_MASKING) {
            MissingnessInjector.Mechanism mechanism = MissingnessInjector.Mechanism.none();

            for (int j = 0; j < p; j++) {
                mechanism = mechanism.withSelfMasking(j, MECHANISM_WEIGHT);
            }

            return MissingnessInjector.inject(complete,
                    MissingnessInjector.Spec.uniform(p, MISSING_RATE, 0.0), mechanism);
        }

        // MAR: every third variable (0, 3, 6, ...) is fully observed and drives deletion of the following two.
        // The drivers' rates are 0, so the targets' rates are raised to keep the overall fraction at
        // MISSING_RATE.
        double[] rates = new double[p];
        MissingnessInjector.Mechanism mechanism = MissingnessInjector.Mechanism.none();

        int numDrivers = 0;
        for (int j = 0; j < p; j += 3) numDrivers++;

        double targetRate = MISSING_RATE * p / (double) (p - numDrivers);

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
     * legal PDAGs and returns a -99 sentinel otherwise; averaging the sentinel in would quietly report -99 as a
     * distance, which is worse than reporting nothing.
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
     * A plain copy of a graph: same nodes, same endpoints, no edge properties or attributes. Graphs built by
     * sampling carry annotations that the comparison statistics do not accept.
     */
    private static Graph plainCopy(Graph graph) {
        Graph out = new edu.cmu.tetrad.graph.EdgeListGraph(graph.getNodes());

        for (edu.cmu.tetrad.graph.Edge edge : graph.getEdges()) {
            out.addEdge(new edu.cmu.tetrad.graph.Edge(edge.getNode1(), edge.getNode2(),
                    edge.getEndpoint1(), edge.getEndpoint2()));
        }

        return out;
    }

    private static Parameters bossParams() {
        Parameters params = new Parameters();
        params.set(Params.PENALTY_DISCOUNT, PENALTY_DISCOUNT);
        params.set(Params.NUM_STARTS, 1);
        params.set(Params.USE_BES, false);
        params.set(Params.VERBOSE, false);
        return params;
    }

    private static edu.cmu.tetrad.search.score.SemBicScore scoreFor(DataSet data, MissingDataSpec spec) {
        edu.cmu.tetrad.search.score.SemBicScore score =
                new edu.cmu.tetrad.search.score.SemBicScore(data, true, spec);
        score.setPenaltyDiscount(PENALTY_DISCOUNT);
        return score;
    }

    private static Graph boss(edu.cmu.tetrad.search.score.Score score) throws InterruptedException {
        edu.cmu.tetrad.search.Boss boss = new edu.cmu.tetrad.search.Boss(score);
        boss.setUseBes(false);
        boss.setNumStarts(1);
        return new PermutationSearch(boss).search();
    }

    /**
     * Fills each missing cell with its column mean. Present as a baseline, not a recommendation: this shrinks
     * residual variance and pulls the correlation matrix toward whatever the observed means imply.
     */
    private static DataSet meanImpute(DataSet data) {
        DataSet out = data.copy();

        for (int j = 0; j < out.getNumColumns(); j++) {
            double sum = 0;
            int count = 0;

            for (int i = 0; i < out.getNumRows(); i++) {
                double v = out.getDouble(i, j);
                if (Double.isFinite(v)) { sum += v; count++; }
            }

            double mean = count > 0 ? sum / count : 0.0;

            for (int i = 0; i < out.getNumRows(); i++) {
                if (!Double.isFinite(out.getDouble(i, j))) out.setDouble(i, j, mean);
            }
        }

        return out;
    }

    /**
     * Runs one arm, abandoning it after {@link #ARM_TIMEOUT_SECONDS}. A search that ignores interruption will
     * keep a core busy until it finishes, so the timeout bounds the reported wait rather than the actual work.
     */
    private static Graph runWithTimeout(Arm arm, DataSet complete, DataSet missing, long repSeed) throws Exception {
        if (ARM_TIMEOUT_SECONDS <= 0) return arm.search(complete, missing, repSeed);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "study-arm");
            t.setDaemon(true);
            return t;
        });

        try {
            java.util.concurrent.Future<Graph> future =
                    executor.submit(() -> arm.search(complete, missing, repSeed));

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

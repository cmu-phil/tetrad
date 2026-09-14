package edu.cmu.tetrad.study;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Boss;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.score.BasisFunctionBicScore;
import edu.cmu.tetrad.algcomparison.score.DegenerateGaussianBicScore;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.LeeHastieSimulation;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.data.missing.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Compares the ways Tetrad currently has of running BOSS on data with missing values, on simulated data whose
 * missingness is injected by {@link MissingnessInjector} with a chem-shaped rate profile.
 *
 * <p>Run this from IntelliJ; it streams a line per condition as it goes. With the defaults below it is a few
 * minutes.</p>
 *
 * <h2>What this can and cannot show</h2>
 *
 * <p>{@link MissingnessInjector} is MCAR: the latent row propensity is independent of the data, so it clusters the
 * pattern without making deletion depend on any value. Under MCAR listwise deletion is <b>consistent</b>, so this
 * study compares methods on <b>efficiency</b> -- how much structure each recovers from the information that
 * survives -- and cannot show the bias differences that are the main statistical reason to prefer EM or multiple
 * imputation. Reading an EM win here as "EM corrects bias" would be wrong; it would be "EM uses more of the data".
 * The bias comparison needs the MAR term that is not yet in the injector.</p>
 *
 * <p>The row propensity is the interesting dial precisely because it does not change the marginal rates: at a fixed
 * level of missingness it moves cases between "many rows lightly hit" and "few rows heavily hit", and complete-case
 * methods care enormously about which.</p>
 *
 * @author josephramsey
 */
public final class BossMissingDataStudy {

    // ---------------------------------------------------------------- configuration

    /** Variables in the simulated graph. */
    private static final int NUM_VARS = 20;

    /** Edges in the simulated graph. */
    private static final int NUM_EDGES = 40;

    /** Rows simulated before any deletion. */
    private static final int SAMPLE_SIZE = 1000;

    /** Repetitions per condition. Each uses a fresh graph, SEM and dataset. */
    private static final int NUM_REPS = 10;

    /** Row propensities to sweep. 0 deletes independently; 2 clusters losses hard. */
    private static final double[] ROW_PROPENSITIES = {0.0, 1.0, 2.0};

    /** Imputations for the multiple-imputation arm. */
    private static final int NUM_IMPUTATIONS = 10;

    /** BIC penalty discount for every arm, so the comparison is of missing-data handling only. */
    private static final double PENALTY_DISCOUNT = 2.0;

    /** Percent of variables made discrete in the mixed arm. */
    private static final double PERCENT_DISCRETE = 50.0;

    /** Categories per discrete variable in the mixed arm. */
    private static final int NUM_CATEGORIES = 3;

    /** Truncation limit for the basis-function embedding. */
    private static final int TRUNCATION_LIMIT = 3;

    /**
     * Basis-function BIC is superlinear in the variable count, and at this size its arms dominate the wall clock --
     * on the order of minutes each where the degenerate-Gaussian arms take seconds. Set false to skip them and get
     * the DG comparison quickly; the skipped arms are named in the output so their absence is not silent.
     */
    private static final boolean RUN_BASIS_FUNCTION_ARMS = false;

    /**
     * Seconds any single arm may take before it is abandoned and reported as timed out. One slow or pathological
     * arm should not stall a sweep; 0 disables the limit.
     */
    private static final int ARM_TIMEOUT_SECONDS = 120;

    /**
     * Chem-shaped missingness profile interpolated to NUM_VARS, capped at 0.6, in a non-monotone order so that a
     * variable's rate is not predictable from its position. Mean rate about 0.22, matching the chem dataset.
     */
    private static final double[] RATE_PROFILE = {
            0.066, 0.370, 0.249, 0.124, 0.442, 0.467, 0.203, 0.492, 0.132, 0.320,
            0.571, 0.053, 0.279, 0.600, 0.028, 0.028, 0.017, 0.012, 0.000, 0.000
    };

    // ---------------------------------------------------------------- arms

    private interface Arm {
        Graph search(DataSet complete, DataSet withMissing) throws Exception;
    }

    private static Map<String, Arm> arms() {
        Map<String, Arm> arms = new LinkedHashMap<>();

        // Upper bound: what BOSS recovers with nothing deleted.
        arms.put("ORACLE (no deletion)", (complete, missing) -> boss(scoreFor(complete, null)));

        // Complete cases only. Consistent under MCAR; the question is how many cases survive.
        arms.put("LISTWISE", (complete, missing) ->
                boss(scoreFor(missing, MissingDataSpec.listwise())));

        // Per-family available cases, with the penalty taking each family's own row count.
        arms.put("TESTWISE", (complete, missing) ->
                boss(scoreFor(missing, MissingDataSpec.testwise())));

        // EM covariance, under each of the three effective-sample-size conventions. FULL_N asserts the covariance
        // carries as much information as N complete rows, which it does not; the other two discount it.
        for (MissingDataSpec.EffectiveSampleSizeMode mode : MissingDataSpec.EffectiveSampleSizeMode.values()) {
            arms.put("EM_COV (" + mode + ")", (complete, missing) ->
                    boss(scoreFor(missing, MissingDataSpec.emCovariance().withEssMode(mode))));
        }

        // Multiple imputation: BOSS on each completed dataset, graphs pooled by edge frequency.
        arms.put("MI (m=" + NUM_IMPUTATIONS + ", pooled)", (complete, missing) -> {
            Parameters params = bossParams();
            ImputationSearch.Result result = ImputationSearch.search(
                    missing, new Boss(new SemBicScore()), params, null,
                    MissingDataSpec.multipleImputation(NUM_IMPUTATIONS));
            // Copied into a plain graph so the comparison statistics see no sampling annotations. This does not
            // make the structural Hamming distance defined for it -- see shd below.
            return plainCopy(result.pooledGraph);
        });

        // Naive baseline, included because it is what people reach for. Conditional-mean filling shrinks residual
        // variance and pulls correlations toward the column means' own structure.
        arms.put("MEAN IMPUTE (baseline)", (complete, missing) ->
                boss(scoreFor(meanImpute(missing), null)));

        return arms;
    }

    /**
     * Arms for mixed data. The embedded scores (degenerate Gaussian, basis-function BIC) take LISTWISE and
     * TESTWISE only -- EM covariance of indicator columns has no interpretation -- so the sweep here is over the
     * effective-sample-size mode under TESTWISE, which is the thing the ESS patch newly exposes.
     */
    private static Map<String, Arm> mixedArms() {
        Map<String, Arm> arms = new LinkedHashMap<>();

        arms.put("DG ORACLE", (complete, missing) ->
                boss(new edu.cmu.tetrad.search.score.DegenerateGaussianScore(complete, true, 0.0)));
        arms.put("BF ORACLE", (complete, missing) ->
                boss(new edu.cmu.tetrad.search.score.BasisFunctionBicScore(
                        complete, TRUNCATION_LIMIT, 0.0, false, false, null)));

        arms.put("DG LISTWISE", (complete, missing) ->
                boss(new edu.cmu.tetrad.search.score.DegenerateGaussianScore(
                        missing, true, 0.0, MissingDataSpec.listwise())));
        arms.put("BF LISTWISE", (complete, missing) ->
                boss(new edu.cmu.tetrad.search.score.BasisFunctionBicScore(
                        missing, TRUNCATION_LIMIT, 0.0, false, false, MissingDataSpec.listwise())));

        for (MissingDataSpec.EffectiveSampleSizeMode mode : MissingDataSpec.EffectiveSampleSizeMode.values()) {
            MissingDataSpec testwise = MissingDataSpec.testwise().withEssMode(mode);

            arms.put("DG TESTWISE (" + mode + ")", (complete, missing) ->
                    boss(new edu.cmu.tetrad.search.score.DegenerateGaussianScore(missing, true, 0.0, testwise)));
            arms.put("BF TESTWISE (" + mode + ")", (complete, missing) ->
                    boss(new edu.cmu.tetrad.search.score.BasisFunctionBicScore(
                            missing, TRUNCATION_LIMIT, 0.0, false, false, testwise)));

            // EM on the embedded matrix: every row informs every family, rather than each family seeing only its
            // own complete rows. For DG this is the score's existing Gaussian working model carried through to
            // incomplete data; for BF it additionally breaks the deterministic relations among basis columns,
            // which is why the two are reported separately rather than pooled.
            MissingDataSpec em = MissingDataSpec.emCovariance().withEssMode(mode);

            arms.put("DG EM_COV (" + mode + ")", (complete, missing) ->
                    boss(new edu.cmu.tetrad.search.score.DegenerateGaussianScore(missing, true, 0.0, em)));
            arms.put("BF EM_COV (" + mode + ")", (complete, missing) ->
                    boss(new edu.cmu.tetrad.search.score.BasisFunctionBicScore(
                            missing, TRUNCATION_LIMIT, 0.0, false, false, em)));

            // The arms above construct the score class directly, which is what let an interface-level blocker go
            // unnoticed while these numbers looked fine: the wrapper's gate rejected "em" before the score was
            // built, so the study measured a configuration no user could select. These two take the wrapper path
            // instead, with the parameters an interface would set. They should track their direct counterparts;
            // if one throws or diverges, the wiring has drifted rather than the statistics.
            // TestMissingDataPolicyWiring checks the same agreement in under two seconds.
            arms.put("DG EM_COV via wrapper (" + mode + ")", (complete, missing) ->
                    boss(new DegenerateGaussianBicScore().getScore(missing, wrapperParams("em", mode))));
            arms.put("BF EM_COV via wrapper (" + mode + ")", (complete, missing) ->
                    boss(new BasisFunctionBicScore().getScore(missing, wrapperParams("em", mode))));
        }

        if (!RUN_BASIS_FUNCTION_ARMS) {
            int before = arms.size();
            arms.keySet().removeIf(name -> name.startsWith("BF "));
            System.out.printf("(skipping %d basis-function arm(s); set RUN_BASIS_FUNCTION_ARMS to include them)%n",
                    before - arms.size());
        }

        return arms;
    }

    // ---------------------------------------------------------------- main

    public static void main(String[] args) throws Exception {
        System.out.printf("BOSS missing-data study: p=%d, edges=%d, n=%d, reps=%d, penalty=%.1f%n",
                NUM_VARS, NUM_EDGES, SAMPLE_SIZE, NUM_REPS, PENALTY_DISCOUNT);
        System.out.println("Missingness is MCAR (clustered when row propensity > 0). Listwise deletion is");
        System.out.println("consistent here; these numbers compare efficiency, not bias.");
        System.out.println();

        runPhase("CONTINUOUS", arms(), false);
        runPhase("MIXED (" + (int) PERCENT_DISCRETE + "% discrete, " + NUM_CATEGORIES + " categories)",
                mixedArms(), true);
    }

    private static void runPhase(String label, Map<String, Arm> arms, boolean mixed) throws Exception {
        System.out.println("################ " + label + " ################");
        System.out.println();

        for (double lambda : ROW_PROPENSITIES) {
            System.out.printf("=== row propensity lambda = %.1f ===%n", lambda);
            System.out.printf("%-34s %8s %8s %8s %8s %8s %9s%n",
                    "method", "adjPrec", "adjRec", "arrPrec", "arrRec", "SHD", "seconds");

            Map<String, double[]> totals = new LinkedHashMap<>();
            for (String name : arms.keySet()) totals.put(name, new double[6]);

            // Arms whose graphs are not legal PDAGs, so that SHD is undefined rather than zero.
            Set<String> shdUndefined = new LinkedHashSet<>();

            int[] completeCases = new int[NUM_REPS];

            for (int rep = 0; rep < NUM_REPS; rep++) {
                RandomUtil.getInstance().setSeed(1000L + rep);

                Graph dag;
                DataSet complete;

                if (mixed) {
                    Parameters simParams = new Parameters();
                    simParams.set(Params.NUM_MEASURES, NUM_VARS);
                    simParams.set(Params.AVG_DEGREE, 2.0 * NUM_EDGES / (double) NUM_VARS);
                    simParams.set(Params.SAMPLE_SIZE, SAMPLE_SIZE);
                    simParams.set(Params.NUM_RUNS, 1);
                    simParams.set(Params.PERCENT_DISCRETE, PERCENT_DISCRETE);
                    simParams.set(Params.NUM_CATEGORIES, NUM_CATEGORIES);
                    simParams.set(Params.DIFFERENT_GRAPHS, false);

                    LeeHastieSimulation sim = new LeeHastieSimulation(new RandomForward());
                    sim.createData(simParams, true);

                    dag = sim.getTrueGraph(0);
                    complete = (DataSet) sim.getDataModel(0);
                } else {
                    dag = RandomGraph.randomGraph(NUM_VARS, 0, NUM_EDGES, 100, 100, 100, false);
                    SemIm im = new SemIm(new SemPm(dag));
                    complete = im.simulateData(SAMPLE_SIZE, false);
                }

                Graph trueCpdag = GraphTransforms.dagToCpdag(dag);

                MissingnessInjector.Result injected = MissingnessInjector.inject(
                        complete, new MissingnessInjector.Spec(RATE_PROFILE, lambda));
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
//                    System.out.printf("  rep %d  %-34s %6.1fs%n", rep, entry.getKey(), seconds);

                    // The searched graph carries the dataset's variable objects and the true graph carries the
                    // simulator's; the confusion matrices compare node identity, so without this every statistic
                    // comes back 0 and SHD comes back as its sentinel.
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

                System.out.printf("%-34s %8.3f %8.3f %8.3f %8.3f %8s %9.1f%n",
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
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Structural Hamming distance, or 0 with the arm recorded as undefined.
     *
     * <p>The measure is defined only between legal PDAGs, and returns a -99 sentinel otherwise -- for a bidirected
     * or partially oriented edge, or a cycle. Pooling graphs by edge frequency across imputations readily produces
     * such edges, since separate searches can orient the same adjacency both ways. Averaging the sentinel in would
     * have quietly reported an SHD of -99 as though it were a distance, which is worse than reporting nothing.</p>
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

    /**
     * The parameters an interface would set for a given missing-data policy and effective-sample-size mode.
     */
    private static Parameters wrapperParams(String policy, MissingDataSpec.EffectiveSampleSizeMode mode) {
        Parameters params = bossParams();
        params.set(Params.MISSING_DATA_POLICY, policy);
        params.set(Params.MISSING_ESS_MODE, essModeToken(mode));
        params.set(Params.TRUNCATION_LIMIT, TRUNCATION_LIMIT);
        params.set(Params.SINGULARITY_LAMBDA, 0.0);
        params.set(Params.PRECOMPUTE_COVARIANCES, true);
        return params;
    }

    /** The parameter token for an ESS mode: FULL_N is written "fullN", and so on. */
    private static String essModeToken(MissingDataSpec.EffectiveSampleSizeMode mode) {
        return switch (mode) {
            case FULL_N -> "fullN";
            case MIN_PAIRWISE -> "minPairwise";
            case MEAN_PAIRWISE -> "meanPairwise";
        };
    }

    /**
     * Runs one arm, abandoning it after {@link #ARM_TIMEOUT_SECONDS}. The thread is interrupted and left to unwind;
     * a search that ignores interruption will keep a core busy until it finishes, so a timeout bounds the reported
     * wait rather than the actual work.
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

    /**
     * Applies the study's penalty discount to a directly-constructed score. The embedded score constructors do not
     * take one, so without this the direct arms ran at their class default while the wrapper arms ran at
     * PENALTY_DISCOUNT, and the two sets were not comparable -- a harness bug the wrapper arms exposed on their
     * first run.
     */
    private static edu.cmu.tetrad.search.score.Score penalized(edu.cmu.tetrad.search.score.Score score) {
        if (score instanceof edu.cmu.tetrad.search.score.DegenerateGaussianScore dg) {
            dg.setPenaltyDiscount(PENALTY_DISCOUNT);
        } else if (score instanceof edu.cmu.tetrad.search.score.BasisFunctionBicScore bf) {
            bf.setPenaltyDiscount(PENALTY_DISCOUNT);
        }

        return score;
    }

    private static Graph boss(edu.cmu.tetrad.search.score.Score score) throws InterruptedException {
        score = penalized(score);

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
}

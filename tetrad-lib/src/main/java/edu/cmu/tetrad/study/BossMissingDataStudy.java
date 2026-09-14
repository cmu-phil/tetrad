package edu.cmu.tetrad.study;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Boss;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
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
import java.util.Map;

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
            return result.pooledGraph;
        });

        // Naive baseline, included because it is what people reach for. Conditional-mean filling shrinks residual
        // variance and pulls correlations toward the column means' own structure.
        arms.put("MEAN IMPUTE (baseline)", (complete, missing) ->
                boss(scoreFor(meanImpute(missing), null)));

        return arms;
    }

    // ---------------------------------------------------------------- main

    public static void main(String[] args) throws Exception {
        System.out.printf("BOSS missing-data study: p=%d, edges=%d, n=%d, reps=%d, penalty=%.1f%n",
                NUM_VARS, NUM_EDGES, SAMPLE_SIZE, NUM_REPS, PENALTY_DISCOUNT);
        System.out.println("Missingness is MCAR (clustered when row propensity > 0). Listwise deletion is");
        System.out.println("consistent here; these numbers compare efficiency, not bias.");
        System.out.println();

        Map<String, Arm> arms = arms();

        for (double lambda : ROW_PROPENSITIES) {
            System.out.printf("=== row propensity lambda = %.1f ===%n", lambda);
            System.out.printf("%-26s %8s %8s %8s %8s %8s %9s%n",
                    "method", "adjPrec", "adjRec", "arrPrec", "arrRec", "SHD", "seconds");

            Map<String, double[]> totals = new LinkedHashMap<>();
            for (String name : arms.keySet()) totals.put(name, new double[6]);

            int[] completeCases = new int[NUM_REPS];

            for (int rep = 0; rep < NUM_REPS; rep++) {
                RandomUtil.getInstance().setSeed(1000L + rep);

                Graph dag = RandomGraph.randomGraph(NUM_VARS, 0, NUM_EDGES, 100, 100, 100, false);
                Graph trueCpdag = GraphTransforms.dagToCpdag(dag);

                SemIm im = new SemIm(new SemPm(dag));
                DataSet complete = im.simulateData(SAMPLE_SIZE, false);

                MissingnessInjector.Result injected = MissingnessInjector.inject(
                        complete, new MissingnessInjector.Spec(RATE_PROFILE, lambda));
                DataSet missing = injected.data();
                completeCases[rep] = injected.report().completeRows();

                for (Map.Entry<String, Arm> entry : arms.entrySet()) {
                    long t0 = System.currentTimeMillis();
                    Graph est;

                    try {
                        est = entry.getValue().search(complete, missing);
                    } catch (Exception e) {
                        System.out.printf("  [%s] rep %d failed: %s%n", entry.getKey(), rep, e.getMessage());
                        continue;
                    }

                    double seconds = (System.currentTimeMillis() - t0) / 1000.0;

                    // The searched graph carries the dataset's variable objects and the true graph carries the
                    // simulator's; the confusion matrices compare node identity, so without this every statistic
                    // comes back 0 and SHD comes back as its sentinel.
                    est = edu.cmu.tetrad.graph.GraphUtils.replaceNodes(est, trueCpdag.getNodes());

                    double[] t = totals.get(entry.getKey());

                    t[0] += new AdjacencyPrecision().getValue(trueCpdag, est, missing);
                    t[1] += new AdjacencyRecall().getValue(trueCpdag, est, missing);
                    t[2] += new ArrowheadPrecision().getValue(trueCpdag, est, missing);
                    t[3] += new ArrowheadRecall().getValue(trueCpdag, est, missing);
                    t[4] += new StructuralHammingDistance().getValue(trueCpdag, est, missing);
                    t[5] += seconds;
                }

                System.out.print(".");
                System.out.flush();
            }

            System.out.println();

            double meanComplete = 0;
            for (int c : completeCases) meanComplete += c;
            meanComplete /= NUM_REPS;

            for (Map.Entry<String, double[]> entry : totals.entrySet()) {
                double[] t = entry.getValue();
                System.out.printf("%-26s %8.3f %8.3f %8.3f %8.3f %8.1f %9.1f%n",
                        entry.getKey(), t[0] / NUM_REPS, t[1] / NUM_REPS, t[2] / NUM_REPS,
                        t[3] / NUM_REPS, t[4] / NUM_REPS, t[5] / NUM_REPS);
            }

            System.out.printf("(mean complete cases available to LISTWISE: %.1f of %d)%n%n",
                    meanComplete, SAMPLE_SIZE);
        }
    }

    // ---------------------------------------------------------------- helpers

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

    private static Graph boss(edu.cmu.tetrad.search.score.SemBicScore score) throws InterruptedException {
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

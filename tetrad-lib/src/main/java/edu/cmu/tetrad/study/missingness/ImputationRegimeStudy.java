package edu.cmu.tetrad.study.missingness;

import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.LeeHastieSimulation;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.MissingnessInjector;
import edu.cmu.tetrad.data.missing.MiceLiteImputer;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.BasisFunctionBicScore;
import edu.cmu.tetrad.search.score.DegenerateGaussianScore;
import edu.cmu.tetrad.search.score.ImagesScore;
import edu.cmu.tetrad.search.score.Score;
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
 * Does MICE imputation with IMaGES-style pooling generalize beyond the linear Gaussian regime? Two phases, both
 * BOSS at 20% MCAR missingness:
 *
 * <ul>
 *   <li><b>MIXED</b>: Lee-Hastie simulation, half the variables discrete, scored by degenerate-Gaussian BIC. This
 *   is the cell where the missing-data literature's own simulations favored deletion over imputation in every
 *   scenario, so a MICE+IMaGES win here is the interesting outcome, not the expected one.</li>
 *   <li><b>NONLINEAR</b>: continuous data from an additive nonlinear SEM (each edge's function drawn from linear,
 *   quadratic, cubic, or tanh), scored by basis-function BIC at truncation 3. The hazard for imputation here is
 *   specific: MICE's donors are matched on a linear fitted mean, so the imputed cells' dependence is flattened
 *   toward linearity even while their margins stay honest, and the embedded score consumes exactly the dependence.</li>
 * </ul>
 *
 * <p>Arms per phase: oracle (complete data), listwise, test-wise deletion at fullN and at minPairwise, EM on the
 * embedded matrix at meanPairwise (the covariance-estimating representative; for BF this breaks the deterministic
 * relations among basis columns, a known caveat), and MICE m = 5 pooled IMaGES-style (one BOSS run over the
 * average of the completed datasets' scores).</p>
 *
 * <p>Run from IntelliJ. The DG phase is quick; the BF phase is the wall clock, since every IMaGES local score
 * evaluates five basis-function scores. An arm that exceeds the timeout is abandoned for that rep and said so.</p>
 *
 * @author josephramsey
 */
public final class ImputationRegimeStudy {

    /**
     * Default constructor for the ImputationRegimeStudy class.
     */
    public ImputationRegimeStudy() {

    }

    // ---------------------------------------------------------------- configuration

    /** Variables in the simulated graph. */
    private static final int NUM_VARS = 15;

    /** Average degree of the simulated graph. */
    private static final double AVG_DEGREE = 3.0;

    /** Edges implied by the average degree. */
    private static final int NUM_EDGES = (int) Math.round(NUM_VARS * AVG_DEGREE / 2.0);

    /** Rows simulated before any deletion. */
    private static final int SAMPLE_SIZE = 1000;

    /** Repetitions per phase. */
    private static final int NUM_REPS = 10;

    /** Fraction of each variable's values deleted, MCAR. */
    private static final double MISSING_RATE = 0.20;

    /** Imputations for the MICE arms. */
    private static final int NUM_IMPUTATIONS = 5;

    /** BIC penalty discount for every arm. */
    private static final double PENALTY_DISCOUNT = 2.0;

    /** Percent of variables made discrete in the mixed phase. */
    private static final double PERCENT_DISCRETE = 50.0;

    /** Categories per discrete variable in the mixed phase. */
    private static final int NUM_CATEGORIES = 3;

    /** Truncation limit for the basis-function embedding in the nonlinear phase. */
    private static final int TRUNCATION_LIMIT = 3;

    /** Seconds any single arm may take before it is abandoned for the rep; 0 disables the limit. */
    private static final int ARM_TIMEOUT_SECONDS = 600;

    // ---------------------------------------------------------------- score factories

    /** A score for one (possibly incomplete or completed) dataset, under a spec; null spec means complete data. */
    private interface ScoreFactory {
        Score build(DataSet data, MissingDataSpec spec);
    }

    private static Score dg(DataSet data, MissingDataSpec spec) {
        DegenerateGaussianScore s = spec == null
                ? new DegenerateGaussianScore(data, true, 0.0)
                : new DegenerateGaussianScore(data, true, 0.0, spec);
        s.setPenaltyDiscount(PENALTY_DISCOUNT);
        return s;
    }

    private static Score bf(DataSet data, MissingDataSpec spec) {
        BasisFunctionBicScore s = new BasisFunctionBicScore(data, TRUNCATION_LIMIT, 0.0, false, false, spec);
        s.setPenaltyDiscount(PENALTY_DISCOUNT);
        return s;
    }

    // ---------------------------------------------------------------- arms

    private interface Arm {
        Graph search(DataSet complete, DataSet missing, long repSeed) throws Exception;
    }

    private static Map<String, Arm> arms(String scoreName, ScoreFactory f) {
        Map<String, Arm> arms = new LinkedHashMap<>();

        arms.put(scoreName + " ORACLE", (complete, missing, seed) -> boss(f.build(complete, null)));

        arms.put(scoreName + " LISTWISE", (complete, missing, seed) ->
                boss(f.build(missing, MissingDataSpec.listwise())));

        arms.put(scoreName + " TESTWISE (fullN)", (complete, missing, seed) ->
                boss(f.build(missing, MissingDataSpec.testwise()
                        .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.FULL_N))));

        arms.put(scoreName + " TESTWISE (minPairwise)", (complete, missing, seed) ->
                boss(f.build(missing, MissingDataSpec.testwise()
                        .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MIN_PAIRWISE))));

        // EM on the embedded matrix, the covariance-estimating representative. For the basis-function score this
        // additionally breaks the deterministic relations among the basis columns of one variable, which is why
        // this arm is labeled with the score rather than treated as the same estimator across phases.
        arms.put(scoreName + " EM_COV (meanPairwise)", (complete, missing, seed) ->
                boss(f.build(missing, MissingDataSpec.emCovariance()
                        .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MEAN_PAIRWISE))));

        arms.put(scoreName + " MICE m=" + NUM_IMPUTATIONS + " + IMaGES", (complete, missing, seed) -> {
            List<DataSet> completed = new MiceLiteImputer().impute(missing, NUM_IMPUTATIONS, seed);

            List<Score> scores = new ArrayList<>(completed.size());
            for (DataSet c : completed) scores.add(f.build(c, null));

            return boss(new ImagesScore(scores));
        });

        return arms;
    }

    private static Graph boss(Score score) throws InterruptedException {
        Boss boss = new Boss(score);
        boss.setUseBes(false);
        boss.setNumStarts(1);
        return new PermutationSearch(boss).search();
    }

    // ---------------------------------------------------------------- simulations

    /** The mixed-phase data: a Lee-Hastie simulation and its true DAG. */
    private record SimResult(Graph dag, DataSet data) {
    }

    private static SimResult simulateMixed() {
        Parameters simParams = new Parameters();
        simParams.set(Params.NUM_MEASURES, NUM_VARS);
        simParams.set(Params.AVG_DEGREE, AVG_DEGREE);
        simParams.set(Params.SAMPLE_SIZE, SAMPLE_SIZE);
        simParams.set(Params.NUM_RUNS, 1);
        simParams.set(Params.PERCENT_DISCRETE, PERCENT_DISCRETE);
        simParams.set(Params.NUM_CATEGORIES, NUM_CATEGORIES);
        simParams.set(Params.DIFFERENT_GRAPHS, false);

        LeeHastieSimulation sim = new LeeHastieSimulation(new RandomForward());
        sim.createData(simParams, true);

        return new SimResult(sim.getTrueGraph(0), (DataSet) sim.getDataModel(0));
    }

    /**
     * The nonlinear-phase data: an additive nonlinear SEM over a random DAG. Each variable is the sum, over its
     * parents, of a per-edge function of the standardized parent -- drawn uniformly from x, x^2, x^3, and tanh(2x),
     * each standardized and given a coefficient drawn from ±[0.5, 1.2] -- plus unit Gaussian noise. Additive in
     * the parents, so inside the model class the basis-function score (truncation 3 covers the polynomials; tanh
     * is approximated) is designed for; hand-rolled so the functional class is explicit in the study rather than
     * inherited from a simulation default.
     */
    private static SimResult simulateNonlinear() {
        Graph dag = RandomGraph.randomGraph(NUM_VARS, 0, NUM_EDGES, 100, 100, 100, false);
        List<Node> order = dag.paths().getValidOrder(dag.getNodes(), true);

        RandomUtil r = RandomUtil.getInstance();
        double[][] cols = new double[NUM_VARS][SAMPLE_SIZE];
        Map<Node, Integer> idx = new LinkedHashMap<>();
        List<Node> nodes = dag.getNodes();
        for (int j = 0; j < nodes.size(); j++) idx.put(nodes.get(j), j);

        for (Node node : order) {
            int j = idx.get(node);
            double[] col = new double[SAMPLE_SIZE];

            for (int i = 0; i < SAMPLE_SIZE; i++) col[i] = r.nextGaussian(0, 1);

            for (Node parent : dag.getParents(node)) {
                double[] p = standardize(cols[idx.get(parent)]);
                int fn = r.nextInt(4);
                double coef = (r.nextDouble() < 0.5 ? -1 : 1) * (0.5 + 0.7 * r.nextDouble());

                double[] fp = new double[SAMPLE_SIZE];
                for (int i = 0; i < SAMPLE_SIZE; i++) {
                    fp[i] = switch (fn) {
                        case 0 -> p[i];
                        case 1 -> p[i] * p[i];
                        case 2 -> p[i] * p[i] * p[i];
                        default -> Math.tanh(2 * p[i]);
                    };
                }

                fp = standardize(fp);
                for (int i = 0; i < SAMPLE_SIZE; i++) col[i] += coef * fp[i];
            }

            cols[j] = col;
        }

        // The dataset's variables must be ContinuousVariable, not the graph's GraphNodes: the injector deletes
        // only continuous or discrete variables and silently skips others, and the scores expect typed variables.
        // Names match the graph's nodes, and replaceNodes aligns the comparison downstream.
        List<Node> dataVars = new ArrayList<>(NUM_VARS);
        for (Node node : nodes) dataVars.add(new edu.cmu.tetrad.data.ContinuousVariable(node.getName()));

        DataSet data = new edu.cmu.tetrad.data.BoxDataSet(
                new edu.cmu.tetrad.data.DoubleDataBox(SAMPLE_SIZE, NUM_VARS), dataVars);
        for (int j = 0; j < NUM_VARS; j++)
            for (int i = 0; i < SAMPLE_SIZE; i++)
                data.setDouble(i, j, cols[j][i]);

        return new SimResult(dag, data);
    }

    private static double[] standardize(double[] x) {
        int n = x.length;
        double mu = 0;
        for (double v : x) mu += v;
        mu /= n;
        double ss = 0;
        for (double v : x) ss += (v - mu) * (v - mu);
        double sd = Math.sqrt(ss / (n - 1));
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = sd > 0 ? (x[i] - mu) / sd : 0.0;
        return out;
    }

    // ---------------------------------------------------------------- main

    /**
     * The entry point: runs the mixed phase (degenerate-Gaussian score) and the nonlinear phase (basis-function
     * score), printing a summary table per phase.
     *
     * @param args Command-line arguments; not used.
     * @throws Exception If any error occurs during simulation or search.
     */
    public static void main(String[] args) throws Exception {
        System.out.printf("Imputation regime study: p=%d, avg degree=%.1f (%d edges), n=%d, missing=%.0f%% MCAR, "
                          + "m=%d, reps=%d, penalty=%.1f%n%n",
                NUM_VARS, AVG_DEGREE, NUM_EDGES, SAMPLE_SIZE, 100 * MISSING_RATE, NUM_IMPUTATIONS,
                NUM_REPS, PENALTY_DISCOUNT);

        runPhase("MIXED (" + (int) PERCENT_DISCRETE + "% discrete, " + NUM_CATEGORIES
                 + " categories; DG-BIC)", arms("DG", ImputationRegimeStudy::dg), true);
        runPhase("NONLINEAR (additive nonlinear SEM; BF-BIC trunc " + TRUNCATION_LIMIT + ")",
                arms("BF", ImputationRegimeStudy::bf), false);
    }

    private static void runPhase(String label, Map<String, Arm> arms, boolean mixed) throws Exception {
        System.out.println("=== " + label + " ===");

        Map<String, double[]> totals = new LinkedHashMap<>();
        Map<String, Integer> counted = new LinkedHashMap<>();
        for (String name : arms.keySet()) {
            totals.put(name, new double[6]);
            counted.put(name, 0);
        }

        Set<String> shdUndefined = new LinkedHashSet<>();

        for (int rep = 0; rep < NUM_REPS; rep++) {
            RandomUtil.getInstance().setSeed(1000L + rep);
            long repSeed = 1000L + rep;

            SimResult sim = mixed ? simulateMixed() : simulateNonlinear();
            Graph trueCpdag = GraphTransforms.dagToCpdag(sim.dag());

            DataSet missing = MissingnessInjector.inject(sim.data(),
                    MissingnessInjector.Spec.uniform(NUM_VARS, MISSING_RATE, 0.0)).data();

            for (Map.Entry<String, Arm> entry : arms.entrySet()) {
                long t0 = System.currentTimeMillis();
                Graph est;

                try {
                    est = runWithTimeout(entry.getValue(), sim.data(), missing, repSeed);
                } catch (java.util.concurrent.TimeoutException e) {
                    System.out.printf("  [%s] rep %d timed out after %ds; arm abandoned for this rep%n",
                            entry.getKey(), rep, ARM_TIMEOUT_SECONDS);
                    continue;
                } catch (Exception e) {
                    System.out.printf("  [%s] rep %d failed: %s%n", entry.getKey(), rep, e.getMessage());
                    continue;
                }

                double seconds = (System.currentTimeMillis() - t0) / 1000.0;

                est = edu.cmu.tetrad.graph.GraphUtils.replaceNodes(est, trueCpdag.getNodes());

                double[] t = totals.get(entry.getKey());
                counted.merge(entry.getKey(), 1, Integer::sum);

                t[0] += new AdjacencyPrecision().getValue(trueCpdag, est, missing);
                t[1] += new AdjacencyRecall().getValue(trueCpdag, est, missing);
                t[2] += new ArrowheadPrecision().getValue(trueCpdag, est, missing);
                t[3] += new ArrowheadRecall().getValue(trueCpdag, est, missing);

                double shd = new StructuralHammingDistance().getValue(trueCpdag, est, missing);
                if (shd < 0) {
                    shdUndefined.add(entry.getKey());
                } else {
                    t[4] += shd;
                }

                t[5] += seconds;
            }

            System.out.flush();
        }

        System.out.printf("%-30s %5s %8s %8s %8s %8s %8s %9s%n",
                "method", "reps", "adjPrec", "adjRec", "arrPrec", "arrRec", "SHD", "seconds");

        for (Map.Entry<String, double[]> entry : totals.entrySet()) {
            double[] t = entry.getValue();
            int k = counted.get(entry.getKey());
            boolean undefined = shdUndefined.contains(entry.getKey());

            if (k == 0) {
                System.out.printf("%-30s %5d%n", entry.getKey(), 0);
                continue;
            }

            System.out.printf("%-30s %5d %8.3f %8.3f %8.3f %8.3f %8s %9.1f%n",
                    entry.getKey(), k, t[0] / k, t[1] / k, t[2] / k, t[3] / k,
                    undefined ? "n/a" : String.format("%.1f", t[4] / k), t[5] / k);
        }

        if (!shdUndefined.isEmpty()) {
            System.out.println("(SHD is n/a for " + String.join(", ", shdUndefined)
                               + ": some graph was not a legal PDAG.)");
        }

        System.out.println();
    }

    /** Runs one arm, abandoning it after {@link #ARM_TIMEOUT_SECONDS}. */
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

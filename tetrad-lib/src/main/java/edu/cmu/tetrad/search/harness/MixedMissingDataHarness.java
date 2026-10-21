package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.score.BasisFunctionBicScore;
import edu.cmu.tetrad.algcomparison.score.DegenerateGaussianBicScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.LeeHastieSimulation;
import edu.cmu.tetrad.algcomparison.statistic.AdjacencyPrecision;
import edu.cmu.tetrad.algcomparison.statistic.AdjacencyRecall;
import edu.cmu.tetrad.algcomparison.statistic.ArrowheadPrecision;
import edu.cmu.tetrad.algcomparison.statistic.ArrowheadRecall;
import edu.cmu.tetrad.algcomparison.statistic.StructuralHammingDistance;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.MissingnessInjector;
import edu.cmu.tetrad.data.MissingnessInjector.Mechanism;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.FileWriter;
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
 * Stage 4 of the missing-data experiments: on mixed continuous and discrete data, which way of handling missing
 * values works best with the embedded scores, degenerate Gaussian BIC (DG-BIC) and optionally basis-function BIC
 * (BF-BIC)?
 *
 * <p>Stages 2 and 3 used SEM-BIC on continuous data, where EM_COVARIANCE with the minimum pairwise effective sample
 * size was best and PAIRWISE_COVARIANCE second. CHEM is mixed, and the embedded scores fit Gaussian models to
 * indicator or basis columns, so neither result carries over without testing. Every arm builds its score through
 * the same algcomparison wrapper and parameters the interface uses, so this also checks the wiring.</p>
 *
 * <h2>Design</h2>
 *
 * <ul>
 *   <li><b>Data.</b> Lee-Hastie mixed simulation on a random forward DAG: a set percentage of variables discrete with
 *   a set number of categories, the rest continuous.</li>
 *   <li><b>Target.</b> The sink with the most parents, preferring a continuous one, standing in for the final grade;
 *   never deleted. The root with the most descendants is also never deleted, so that MAR mechanisms have a fully
 *   observed driver.</li>
 *   <li><b>Mechanisms.</b> The seven of Stages 2 and 3. Value effects read a discrete driver's category index.</li>
 *   <li><b>Arms.</b> For each score: the complete-data oracle; LISTWISE; and TESTWISE, EM_COVARIANCE and
 *   PAIRWISE_COVARIANCE, each with the FULL_N and MIN_PAIRWISE effective sample sizes. BOSS for every search.</li>
 * </ul>
 *
 * <h2>Output</h2>
 *
 * <p>For each score and mechanism: adjacency and arrowhead precision and recall against the true CPDAG, SHD, and
 * recall and precision of the target's parents, as mean (standard error) over repetitions; seconds; and the number
 * of repetitions that produced a graph. Every repetition is also written to {@value #CSV_FILE}.</p>
 *
 * <h2>Settings</h2>
 *
 * <p>Arguments {@code key=value} override the defaults: {@code reps}, {@code n}, {@code p}, {@code edges},
 * {@code pctDiscrete}, {@code categories}, {@code penalty}, {@code bf} (true to add the BF-BIC arms, which are much
 * slower). A bare number is {@code reps}. For example, {@code n=250 p=24 edges=36} is close to CHEM's size.</p>
 *
 * @author josephramsey
 */
public final class MixedMissingDataHarness {

    // ---------------------------------------------------------------- defaults

    private static final int NUM_REPS = 10;
    private static final int SAMPLE_SIZE = 1000;
    private static final int NUM_VARS = 20;
    private static final int NUM_EDGES = 30;
    private static final double PERCENT_DISCRETE = 50.0;
    private static final int NUM_CATEGORIES = 3;
    private static final double PENALTY_DISCOUNT = 2.0;

    /** Marginal missingness rate for every deleted variable under the per-cell mechanisms. */
    private static final double RATE = 0.3;

    /** Logit-scale weight on a standardized driver value. */
    private static final double VALUE_WEIGHT = 1.5;

    /** Logit-scale weight on a driver's indicator, for the colluder mechanism. */
    private static final double INDICATOR_WEIGHT = 2.0;

    /** Cumulative dropout rate at the first and last attrition step, linear in between. */
    private static final double ATTRITION_FIRST = 0.05, ATTRITION_LAST = 0.5;

    /** Truncation limit for the basis-function score. */
    private static final int TRUNCATION_LIMIT = 3;

    /** Seconds any single search may take before it is abandoned; 0 disables the limit. */
    private static final int ARM_TIMEOUT_SECONDS = 180;

    /** Per-repetition results, written to the working directory. */
    private static final String CSV_FILE = "mixed_missing_data.csv";

    // Settings in effect.
    private static int reps = NUM_REPS, sampleSize = SAMPLE_SIZE, numVars = NUM_VARS, numEdges = NUM_EDGES,
            numCategories = NUM_CATEGORIES;
    private static double percentDiscrete = PERCENT_DISCRETE, penaltyDiscount = PENALTY_DISCOUNT;
    private static boolean runBasisFunction = false;

    private MixedMissingDataHarness() {
    }

    // ---------------------------------------------------------------- arms

    /** One arm: a score wrapper, the missing-data policy and ESS mode it is given, and whether it sees complete data. */
    private record Arm(String name, ScoreWrapper wrapper, String policy, String essMode, boolean oracle) {
    }

    private static List<Arm> arms() {
        List<Arm> arms = new ArrayList<>();
        Map<String, ScoreWrapper> scores = new LinkedHashMap<>();
        scores.put("DG", new DegenerateGaussianBicScore());
        if (runBasisFunction) scores.put("BF", new BasisFunctionBicScore());

        for (Map.Entry<String, ScoreWrapper> s : scores.entrySet()) {
            String sc = s.getKey();
            arms.add(new Arm(sc + " ORACLE", s.getValue(), null, null, true));
            arms.add(new Arm(sc + " LISTWISE", s.getValue(), "listwise", "fullN", false));
            for (String policy : new String[]{"testwise", "em", "pairwise"}) {
                for (String ess : new String[]{"fullN", "minPairwise"}) {
                    arms.add(new Arm(sc + " " + policy.toUpperCase() + " " + ess, s.getValue(), policy, ess,
                            false));
                }
            }
        }
        return arms;
    }

    private static Parameters parameters(Arm arm) {
        Parameters p = new Parameters();
        p.set(Params.PENALTY_DISCOUNT, penaltyDiscount);
        p.set(Params.STRUCTURE_PRIOR, 0.0);
        p.set(Params.SINGULARITY_LAMBDA, 0.0);
        p.set(Params.PRECOMPUTE_COVARIANCES, true);
        p.set(Params.EFFECTIVE_SAMPLE_SIZE, -1);
        p.set(Params.TRUNCATION_LIMIT, TRUNCATION_LIMIT);
        p.set(Params.ADAPTIVE_BASIS_SELECTION, false);
        p.set(Params.BASIS_RANK_TRANSFORM, false);
        if (arm.policy != null) {
            p.set(Params.MISSING_DATA_POLICY, arm.policy);
            p.set(Params.MISSING_ESS_MODE, arm.essMode);
        }
        return p;
    }

    private static Graph search(Arm arm, DataSet complete, DataSet missing) throws InterruptedException {
        Score score = arm.wrapper.getScore(arm.oracle ? complete : missing, parameters(arm));
        Boss boss = new Boss(score);
        boss.setUseBes(false);
        boss.setNumStarts(1);
        return new PermutationSearch(boss).search();
    }

    // ---------------------------------------------------------------- mechanisms

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

    private static final String[] STATS = {"adjP", "adjR", "arrP", "arrR", "SHD", "tgtR", "tgtP", "sec"};

    /**
     * Runs the comparison.
     *
     * @param args Optional overrides, {@code key=value}; see the class comment.
     * @throws Exception If simulation or output fails.
     */
    public static void main(String[] args) throws Exception {
        parseArgs(args);
        List<Arm> arms = arms();
        Map<String, MechanismFactory> mechanisms = mechanisms();

        System.out.printf("Mixed missing-data comparison: p=%d, edges=%d, n=%d, reps=%d, %.0f%% discrete with %d"
                          + " categories, penalty=%.1f, rate=%.2f, value weight=%.1f%s%n%n",
                numVars, numEdges, sampleSize, reps, percentDiscrete, numCategories, penaltyDiscount, RATE,
                VALUE_WEIGHT, runBasisFunction ? ", with BF-BIC" : " (DG-BIC only; bf=true adds BF-BIC)");

        Map<String, Map<String, Acc[]>> stats = new LinkedHashMap<>();
        Map<String, Acc> completeRows = new LinkedHashMap<>();
        for (String mech : mechanisms.keySet()) {
            Map<String, Acc[]> row = new LinkedHashMap<>();
            for (Arm arm : arms) row.put(arm.name, Acc.array(STATS.length));
            stats.put(mech, row);
            completeRows.put(mech, new Acc());
        }

        try (PrintWriter csv = new PrintWriter(new FileWriter(CSV_FILE))) {
            csv.println("mechanism,rep,completeRows,arm," + String.join(",", STATS));

            for (int rep = 0; rep < reps; rep++) {
                RandomUtil.getInstance().setSeed(21000L + rep);
                Sim s = Sim.draw();
                Graph trueCpdag = GraphTransforms.dagToCpdag(s.dag);

                for (Map.Entry<String, MechanismFactory> me : mechanisms.entrySet()) {
                    RandomUtil.getInstance().setSeed(31000L + rep);
                    Injection inj = me.getValue().build(s);
                    MissingnessInjector.Result injected = MissingnessInjector.inject(s.data,
                            new MissingnessInjector.Spec(inj.rates, 0.0), inj.mechanism);
                    DataSet missing = injected.data();
                    completeRows.get(me.getKey()).add(injected.report().completeRows());

                    for (Arm arm : arms) {
                        long t0 = System.currentTimeMillis();
                        Graph g;
                        try {
                            g = runWithTimeout(arm, s.data, missing);
                        } catch (TimeoutException e) {
                            System.out.printf("  [%s / %s] rep %d timed out after %ds%n", me.getKey(), arm.name,
                                    rep, ARM_TIMEOUT_SECONDS);
                            continue;
                        } catch (Exception e) {
                            System.out.printf("  [%s / %s] rep %d failed: %s%n", me.getKey(), arm.name, rep,
                                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                            continue;
                        }
                        double seconds = (System.currentTimeMillis() - t0) / 1000.0;
                        g = GraphUtils.replaceNodes(g, trueCpdag.getNodes());

                        double[] v = searchStats(trueCpdag, g, s, missing, seconds);
                        Acc[] a = stats.get(me.getKey()).get(arm.name);
                        StringBuilder line = new StringBuilder();
                        for (int k = 0; k < v.length; k++) {
                            if (!Double.isNaN(v[k])) a[k].add(v[k]);
                            line.append(',').append(Double.isNaN(v[k]) ? "" : String.format("%.4f", v[k]));
                        }
                        csv.printf("\"%s\",%d,%d,\"%s\"%s%n", me.getKey(), rep,
                                injected.report().completeRows(), arm.name, line);
                    }
                }
                csv.flush();
                System.out.printf("rep %d done%n", rep);
            }
        }

        for (String mech : mechanisms.keySet()) {
            System.out.printf("%n=== %s  (mean complete rows %.0f of %d) ===%n", mech,
                    completeRows.get(mech).mean(), sampleSize);
            System.out.printf("%-26s", "arm");
            for (String h : STATS) System.out.printf(" %15s", h);
            System.out.printf(" %5s%n", "runs");
            for (Map.Entry<String, Acc[]> e : stats.get(mech).entrySet()) {
                System.out.printf("%-26s", e.getKey());
                for (Acc a : e.getValue()) System.out.printf(" %15s", a.cell());
                System.out.printf(" %5d%n", e.getValue()[STATS.length - 1].n);
            }
        }
        System.out.println();
        System.out.println("adj/arr = adjacency/arrowhead precision (P) and recall (R) against the true CPDAG; SHD");
        System.out.println("blank where a graph is not a legal PDAG; tgtR/tgtP = recall/precision of the target's");
        System.out.println("parents. Mean (standard error) over repetitions; runs = repetitions that produced a graph.");
        System.out.println("Per-repetition rows written to " + new java.io.File(CSV_FILE).getAbsolutePath());
    }

    private static void parseArgs(String[] args) {
        for (String arg : args) {
            String[] kv = arg.split("=", 2);
            if (kv.length == 1) {
                reps = Integer.parseInt(kv[0]);
                continue;
            }
            switch (kv[0].trim()) {
                case "reps" -> reps = Integer.parseInt(kv[1]);
                case "n" -> sampleSize = Integer.parseInt(kv[1]);
                case "p" -> numVars = Integer.parseInt(kv[1]);
                case "edges" -> numEdges = Integer.parseInt(kv[1]);
                case "pctDiscrete" -> percentDiscrete = Double.parseDouble(kv[1]);
                case "categories" -> numCategories = Integer.parseInt(kv[1]);
                case "penalty" -> penaltyDiscount = Double.parseDouble(kv[1]);
                case "bf" -> runBasisFunction = Boolean.parseBoolean(kv[1]);
                default -> throw new IllegalArgumentException("Unknown setting: " + kv[0]);
            }
        }
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

        return new double[]{
                new AdjacencyPrecision().getValue(trueCpdag, g, data),
                new AdjacencyRecall().getValue(trueCpdag, g, data),
                new ArrowheadPrecision().getValue(trueCpdag, g, data),
                new ArrowheadRecall().getValue(trueCpdag, g, data),
                shd < 0 ? Double.NaN : shd,
                truePar == 0 ? Double.NaN : found / (double) truePar,
                adj == 0 ? Double.NaN : found / (double) adj,
                seconds};
    }

    private static Graph runWithTimeout(Arm arm, DataSet complete, DataSet missing) throws Exception {
        if (ARM_TIMEOUT_SECONDS <= 0) return search(arm, complete, missing);
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mixed-missing-arm");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<Graph> future = executor.submit(() -> search(arm, complete, missing));
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

    // ---------------------------------------------------------------- simulation

    /**
     * A simulated mixed dataset: the DAG on the data's variables, parents in column indices, the fully observed root
     * and target, and the deleted variables in causal order.
     */
    private static final class Sim {
        int p;
        List<String> names;
        Graph dag;
        DataSet data;
        int[][] parents;
        int root, target;
        int[] deleted;

        static Sim draw() {
            Parameters sp = new Parameters();
            sp.set(Params.NUM_MEASURES, numVars);
            sp.set(Params.AVG_DEGREE, 2.0 * numEdges / (double) numVars);
            sp.set(Params.SAMPLE_SIZE, sampleSize);
            sp.set(Params.NUM_RUNS, 1);
            sp.set(Params.PERCENT_DISCRETE, percentDiscrete);
            sp.set(Params.NUM_CATEGORIES, numCategories);
            sp.set(Params.DIFFERENT_GRAPHS, false);
            sp.set(Params.VERBOSE, false);
            LeeHastieSimulation sim = new LeeHastieSimulation(new RandomForward());
            sim.createData(sp, true);

            Sim s = new Sim();
            s.data = (DataSet) sim.getDataModel(0);
            s.p = s.data.getNumColumns();
            s.names = new ArrayList<>();
            for (int j = 0; j < s.p; j++) s.names.add(s.data.getVariable(j).getName());
            s.dag = GraphUtils.replaceNodes(sim.getTrueGraph(0), s.data.getVariables());

            Map<String, Integer> index = new HashMap<>();
            for (int j = 0; j < s.p; j++) index.put(s.names.get(j), j);
            s.parents = new int[s.p][];
            for (int j = 0; j < s.p; j++) {
                Node node = s.dag.getNode(s.names.get(j));
                s.parents[j] = s.dag.getParents(node).stream().mapToInt(n -> index.get(n.getName())).toArray();
            }
            int[] order = topologicalOrder(s.parents);
            boolean[][] anc = ancestry(s.parents, order);

            int bestRoot = -1, bestTarget = -1, mostDesc = -1, bestScore = -1;
            for (int j : order) {
                int desc = 0, ancs = 0;
                for (int k = 0; k < s.p; k++) {
                    if (anc[j][k]) desc++;
                    if (anc[k][j]) ancs++;
                }
                boolean isSink = true;
                for (int k = 0; k < s.p; k++) for (int q : s.parents[k]) if (q == j) isSink = false;
                if (s.parents[j].length == 0 && desc > mostDesc) {
                    mostDesc = desc;
                    bestRoot = j;
                }
                // Most parents first, then continuous over discrete (the final grade is continuous), then ancestors.
                boolean continuous = s.data.getVariable(j) instanceof ContinuousVariable;
                int score = s.parents[j].length * 100000 + (continuous ? 10000 : 0) + ancs;
                if (isSink && s.parents[j].length > 0 && score > bestScore) {
                    bestScore = score;
                    bestTarget = j;
                }
            }
            if (bestTarget < 0 || bestTarget == bestRoot) {
                throw new IllegalStateException("No target with parents in this graph; try another seed.");
            }
            s.root = bestRoot;
            s.target = bestTarget;

            List<Integer> del = new ArrayList<>();
            for (int j : order) if (j != s.root && j != s.target) del.add(j);
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
                for (int j = 0; j < p; j++) {
                    if (!done[j] && indeg[j] == 0) {
                        next = j;
                        break;
                    }
                }
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
}

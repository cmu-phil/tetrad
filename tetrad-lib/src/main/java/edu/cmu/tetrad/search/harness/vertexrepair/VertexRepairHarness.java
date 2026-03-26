package edu.cmu.tetrad.search.harness.vertexrepair;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.vertex_repair.VertexRepairSearch;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulation harness for studying the effect of Vertex Repair on the output
 * of PC, FGES, and BOSS on linear Gaussian data.
 * <p>
 * Setup:
 * - 10 variables, average degree 3, linear Gaussian
 * - Sample sizes: 500 and 2000
 * - 100 repetitions per (algorithm x sample size) condition
 * <p>
 * For each repetition, we record before-repair and after-repair values of:
 * - SHD (structural Hamming distance to true CPDAG)
 * - Adjacency precision, recall, F1
 * - Arrowhead precision, recall, F1
 * - Number of Markov violations (global, at alpha=0.05)
 * - Model-P (global Markov Checker KS p-value)
 * - Edge count
 * <p>
 * Output: a CSV file suitable for analysis in R or Python.
 * <p>
 * Package: edu.cmu.tetrad.search.harness.vertexrepair
 */
public class VertexRepairHarness {

    // -----------------------------------------------------------------------
    // Simulation parameters
    // -----------------------------------------------------------------------

    private static final int NUM_VARS = 10;
    private static final int AVG_DEGREE = 3;
    private static final int MAX_DEGREE = 6;
    private static final int[] SAMPLE_SIZES = {500, 2000};
    private static final int NUM_REPS = 10;
    private static final double ALPHA = 0.05;   // for CI tests
    private static final double PENALTY = 2.0;    // BIC penalty discount for FGES/BOSS

    // Vertex repair parameters (mirror search() defaults)
    private static final int MAX_STEPS_PER_NODE = 4;
    private static final int MAX_SWEEPS = 50;
    private static final int MAX_EDITS = 200;

    // -----------------------------------------------------------------------
    // Algorithm names (used as column labels)
    // -----------------------------------------------------------------------

    private static final String[] ALGORITHMS = {"PC", "FGES", "BOSS"};

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws IOException, InterruptedException {

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String outFile = "vertex_repair_results_" + timestamp + ".csv";

        System.out.println("Vertex Repair Simulation Harness");
        System.out.println("Output file: " + outFile);
        System.out.println("Vars=" + NUM_VARS + "  AvgDeg=" + AVG_DEGREE
                + "  Reps=" + NUM_REPS + "  Alpha=" + ALPHA);
        System.out.println();

        record RunConfig(int n, int rep, String alg) {
        }

        List<RunConfig> configs = new ArrayList<>();
        for (int n : SAMPLE_SIZES) {
            for (int rep = 0; rep < NUM_REPS; rep++) {
                for (String alg : ALGORITHMS) {
                    configs.add(new RunConfig(n, rep, alg));
                }
            }
        }

        int totalRuns = configs.size();
        AtomicInteger runCounter = new AtomicInteger(0);
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        System.out.println("Parallelism: " + parallelism + " threads");

        ForkJoinPool pool = new ForkJoinPool(parallelism);

        try {
            pool.submit(() ->
                    configs.parallelStream().forEach(cfg -> {
                        try {
                            long seed = cfg.rep() * 1000L + cfg.n();
                            RandomUtil.getInstance().setSeed(seed);

                            List<Node> vars = new ArrayList<>();
                            for (int i = 0; i < NUM_VARS; i++) {
                                vars.add(new ContinuousVariable("X" + i));
                            }

                            Graph trueDag = RandomGraph.randomGraphRandomForwardEdges(
                                    vars, 0,
                                    (int) Math.round(AVG_DEGREE * NUM_VARS / 2.0),
                                    MAX_DEGREE, MAX_DEGREE, MAX_DEGREE, false);

                            Graph trueCpdag = GraphTransforms.dagToCpdag(trueDag);

                            SemPm semPm = new SemPm(trueDag);
                            SemIm semIm = new SemIm(semPm);
                            DataSet data = semIm.simulateData(cfg.n(), false);

                            IndependenceTest fisherZ = new IndTestFisherZ(data, ALPHA);
                            SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
                            score.setPenaltyDiscount(PENALTY);

                            Graph estimated = runAlgorithm(cfg.alg(), fisherZ, score, data);
                            if (estimated == null) {
                                System.err.printf("  WARNING: %s returned null (n=%d rep=%d); skipping.%n",
                                        cfg.alg(), cfg.n(), cfg.rep() + 1);
                                return;
                            }

                            Graph estimatedCpdag = toCpdagSafe(estimated);
                            if (estimatedCpdag == null) {
                                System.err.printf("  WARNING: Could not canonicalize %s output (n=%d rep=%d); skipping.%n",
                                        cfg.alg(), cfg.n(), cfg.rep() + 1);
                                return;
                            }

                            Stats before = evaluate(estimatedCpdag, trueCpdag, data, ALPHA);

                            IndependenceTest testForRepair = new CachedIndependenceQueries(
                                    new IndTestFisherZ(data, ALPHA));
                            VertexRepairSearch vrs = new VertexRepairSearch(
                                    testForRepair,
                                    estimatedCpdag,
                                    new Knowledge(),
                                    ConditioningSetType.RECURSIVE_BLOCKING);

                            Graph repaired = vrs.search(
                                    estimatedCpdag,
                                    VertexRepairSearch.RepairGraphType.CPDAG,
                                    MAX_STEPS_PER_NODE,
                                    MAX_SWEEPS,
                                    MAX_EDITS);

                            Graph repairedCpdag = toCpdagSafe(repaired);
                            if (repairedCpdag == null) {
                                repairedCpdag = estimatedCpdag;
                            }

                            Stats after = evaluate(repairedCpdag, trueCpdag, data, ALPHA);
                            results.add(csvRow(cfg.alg(), cfg.n(), cfg.rep() + 1, seed, before, after));

                            int done = runCounter.incrementAndGet();
                            if (done % 50 == 0) {
                                System.out.printf("  Completed %d / %d%n", done, totalRuns);
                            }

                        } catch (Exception e) {
                            System.err.printf("  ERROR (n=%d rep=%d alg=%s): %s%n",
                                    cfg.n(), cfg.rep() + 1, cfg.alg(), e.getMessage());
                        }
                    })
            ).get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            pool.shutdown();
        }

        results.sort(Comparator.naturalOrder());

        try (PrintWriter out = new PrintWriter(new FileWriter(outFile))) {
            out.println(csvHeader());
            results.forEach(out::println);
        }

        System.out.println("\nDone. " + results.size() + " rows written to: " + outFile);
    }

    // -----------------------------------------------------------------------
    // Run a named algorithm and return its CPDAG output
    // -----------------------------------------------------------------------

    private static Graph runAlgorithm(String alg,
                                      IndependenceTest test,
                                      SemBicScore score,
                                      DataSet data) {
        try {
            return switch (alg) {
                case "PC" -> runPc(test);
                case "FGES" -> runFges(score);
                case "BOSS" -> runBoss(score);
                default -> throw new IllegalArgumentException("Unknown algorithm: " + alg);
            };
        } catch (Exception e) {
            System.err.println("  Algorithm " + alg + " threw: " + e.getMessage());
            return null;
        }
    }

    private static Graph runPc(IndependenceTest test) throws InterruptedException {
        Pc pc = new Pc(test);
        pc.setFasStable(true);
        return pc.search();
    }

    private static Graph runFges(SemBicScore score) throws InterruptedException {
        Fges fges = new Fges(score);
        return fges.search();
    }

    private static Graph runBoss(SemBicScore score) throws InterruptedException {
        Boss boss = new Boss(score);
        boss.setNumStarts(1);
        PermutationSearch search = new PermutationSearch(boss);
        return search.search();
    }

    // -----------------------------------------------------------------------
    // Canonicalize to CPDAG (returns null if impossible)
    // -----------------------------------------------------------------------

    private static Graph toCpdagSafe(Graph g) {
        if (g == null) return null;
        try {
            if (g.paths().isLegalDag()) return GraphTransforms.dagToCpdag(g);
            if (g.paths().isLegalCpdag()) return g;
            if (g.paths().isLegalPdag()) {
                Graph dag = GraphTransforms.dagFromCpdag(g);
                return GraphTransforms.dagToCpdag(dag);
            }
            // Fall back: orient adjacencies by node name order to get a DAG
            Graph dag = seedDagFromSkeleton(g);
            if (dag == null) return null;
            return GraphTransforms.dagToCpdag(dag);
        } catch (Exception e) {
            return null;
        }
    }

    private static Graph seedDagFromSkeleton(Graph g) {
        List<Node> nodes = new ArrayList<>(g.getNodes());
        nodes.sort(Comparator.comparing(Node::getName));

        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) idx.put(nodes.get(i).getName(), i);

        Graph dag = new EdgeListGraph(nodes);
        Set<String> seen = new HashSet<>();

        for (Edge e : g.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            if (a == null || b == null) continue;
            String key = a.getName().compareTo(b.getName()) <= 0
                    ? a.getName() + "|" + b.getName()
                    : b.getName() + "|" + a.getName();
            if (!seen.add(key)) continue;

            Node da = dag.getNode(a.getName()), db = dag.getNode(b.getName());
            if (da == null || db == null) continue;

            int ia = idx.getOrDefault(a.getName(), 0);
            int ib = idx.getOrDefault(b.getName(), 0);

            if (ia <= ib) dag.addEdge(new Edge(da, db, Endpoint.TAIL, Endpoint.ARROW));
            else dag.addEdge(new Edge(db, da, Endpoint.TAIL, Endpoint.ARROW));
        }

        return dag.paths().isLegalDag() ? dag : null;
    }

    // -----------------------------------------------------------------------
    // Evaluate a CPDAG against the true CPDAG and the data
    // -----------------------------------------------------------------------

    private static Stats evaluate(Graph estimated, Graph trueCpdag,
                                  DataSet data, double alpha) {

        estimated = GraphUtils.replaceNodes(estimated, trueCpdag.getNodes());

        // --- Structural metrics (using Tetrad's built-in statistics) ---
        double shd = new StructuralHammingDistance().getValue(trueCpdag, estimated, data, new Parameters());

        double adjPrec = new AdjacencyPrecision().getValue(trueCpdag, estimated, data, new Parameters());
        double adjRec = new AdjacencyRecall().getValue(trueCpdag, estimated, data, new Parameters());
        double adjF1 = f1(adjPrec, adjRec);

        double arrPrec = new ArrowheadPrecision().getValue(trueCpdag, estimated, data, new Parameters());
        double arrRec = new ArrowheadRecall().getValue(trueCpdag, estimated, data, new Parameters());
        double arrF1 = f1(arrPrec, arrRec);

        int edges = estimated.getNumEdges();

        // --- Markov check stats ---
        MarkovCheck mc = new MarkovCheck(estimated, new IndTestFisherZ(data, alpha),
                ConditioningSetType.RECURSIVE_BLOCKING);
        mc.generateResults(true, true);

        int violations = countViolations(mc);
        double modelP = mc.getKsPValue(true);

        return new Stats(shd, adjPrec, adjRec, adjF1,
                arrPrec, arrRec, arrF1,
                edges, violations, modelP);
    }

    private static int countViolations(MarkovCheck mc) {
        List<IndependenceResult> results = mc.getResults(true);
        if (results == null) return 0;
        int v = 0;
        for (IndependenceResult r : results) {
            if (r != null && !r.isIndependent()) v++;
        }
        return v;
    }

    private static double f1(double prec, double rec) {
        double denom = prec + rec;
        return (denom > 0) ? 2.0 * prec * rec / denom : 0.0;
    }

    // -----------------------------------------------------------------------
    // CSV helpers
    // -----------------------------------------------------------------------

    private static String csvHeader() {
        return "algorithm,sampleSize,rep,seed," +
                "shd_before,adjPrec_before,adjRec_before,adjF1_before," +
                "arrPrec_before,arrRec_before,arrF1_before," +
                "edges_before,violations_before,modelP_before," +
                "shd_after,adjPrec_after,adjRec_after,adjF1_after," +
                "arrPrec_after,arrRec_after,arrF1_after," +
                "edges_after,violations_after,modelP_after," +
                "delta_shd,delta_violations,delta_modelP";
    }

    private static String csvRow(String alg, int n, int rep, long seed,
                                 Stats before, Stats after) {
        return String.join(",",
                alg,
                String.valueOf(n),
                String.valueOf(rep),
                String.valueOf(seed),
                fmt(before.shd),
                fmt(before.adjPrec), fmt(before.adjRec), fmt(before.adjF1),
                fmt(before.arrPrec), fmt(before.arrRec), fmt(before.arrF1),
                String.valueOf(before.edges),
                String.valueOf(before.violations),
                fmt(before.modelP),
                fmt(after.shd),
                fmt(after.adjPrec), fmt(after.adjRec), fmt(after.adjF1),
                fmt(after.arrPrec), fmt(after.arrRec), fmt(after.arrF1),
                String.valueOf(after.edges),
                String.valueOf(after.violations),
                fmt(after.modelP),
                fmt(after.shd - before.shd),
                String.valueOf(after.violations - before.violations),
                fmt(after.modelP - before.modelP)
        );
    }

    private static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "NA";
        return String.format("%.6f", v);
    }

    // -----------------------------------------------------------------------
    // Simple stats container
    // -----------------------------------------------------------------------

    private record Stats(
            double shd,
            double adjPrec, double adjRec, double adjF1,
            double arrPrec, double arrRec, double arrF1,
            int edges,
            int violations,
            double modelP
    ) {
    }
}

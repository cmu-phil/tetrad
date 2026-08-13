/// ////////////////////////////////////////////////////////////////////////////
// VertexRepairSpeedBenchmark.java                                           //
//                                                                           //
// Hand-run benchmark comparing VertexRepairSearch wall time and repair      //
// quality under (a) full per-edit invalidation (the pre-2026-8-13 default   //
// and the current default) and (b) affected-only invalidation                //
// (setAffectedOnlyInvalidation(true)).                                      //
//                                                                           //
// This class deliberately compiles against only the published-jar API of    //
// VertexRepairSearch. The setAffectedOnlyInvalidation setter is invoked via //
// reflection so the SAME harness binary can be run against a pristine       //
// tetrad-current.jar (old code: setter absent, mode skipped) and against a  //
// patched classpath (new code: both modes run). Timing the old jar and the  //
// patched classes with one binary keeps the comparison apples-to-apples.    //
//                                                                           //
// Run:  java -cp <patched-out:>harness-out:tetrad-current.jar \             //
//            edu.cmu.tetrad.search.harness.VertexRepairSpeedBenchmark        //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.search.MarkovCheck;
import edu.cmu.tetrad.search.VertexRepairSearch;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hand-run wall-time benchmark for {@link VertexRepairSearch}, comparing full per-edit
 * invalidation against affected-only invalidation on simulated CPDAG and PAG repair
 * problems. Reports per-run wall time, number of edits applied, final Markov violation
 * count, and adjacency precision/recall against the true CPDAG/PAG, plus per-cell
 * averages.
 *
 * <p>Not JUnit-wired; run {@code main} by hand. Configuration constants are at the top.
 */
public final class VertexRepairSpeedBenchmark {

    // =========================================================================
    // Configuration — edit before running
    // =========================================================================

    /** Numbers of measured nodes for the CPDAG scenario. */
    private static final int[] CPDAG_SIZES = {10, 14};
    /** Numbers of measured nodes for the PAG scenario. */
    private static final int[] PAG_SIZES = {10};
    /** Latent confounders for the PAG scenario. */
    private static final int PAG_NUM_LATENTS = 2;
    /** Runs per (scenario, size) cell. */
    private static final int NUM_RUNS = 3;
    /** Sample size for simulated data. */
    private static final int SAMPLE_SIZE = 1000;
    /** Average degree of the random true DAG. */
    private static final double AVG_DEGREE = 3.0;
    /** Number of edge removals and additions applied to the true DAG before projection. */
    private static final int NUM_REMOVALS = 3;
    private static final int NUM_ADDITIONS = 3;
    /** Fisher Z alpha. */
    private static final double ALPHA = 0.01;
    /** Conditioning set type used by repair and by the final violation count. */
    private static final ConditioningSetType CONDITIONING_TYPE =
            ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY;
    /** Base RNG seed; run r in a cell uses BASE_SEED + r. */
    private static final long BASE_SEED = 38492L;
    /** Wall-clock cap per repair run, in milliseconds; runs exceeding it are cancelled. */
    private static final long TIME_LIMIT_MS = 20 * 60 * 1000L;

    private VertexRepairSpeedBenchmark() {
    }

    /**
     * Entry point.
     *
     * @param args ignored
     * @throws Exception if something goes wrong
     */
    public static void main(String[] args) throws Exception {
        boolean hasAffectedOnly = affectedOnlySetter() != null;
        System.out.println("VertexRepairSearch speed benchmark");
        System.out.println("  setAffectedOnlyInvalidation available: " + hasAffectedOnly);
        System.out.println("  conditioning type: " + CONDITIONING_TYPE
                + ", alpha=" + ALPHA + ", n=" + SAMPLE_SIZE
                + ", corrupt=-" + NUM_REMOVALS + "/+" + NUM_ADDITIONS
                + ", runs/cell=" + NUM_RUNS);
        System.out.println();

        List<CellSummary> summaries = new ArrayList<>();

        for (int size : CPDAG_SIZES) {
            summaries.addAll(runCell("CPDAG", size, 0,
                    VertexRepairSearch.AdjustmentGraphType.CPDAG, hasAffectedOnly));
        }
        for (int size : PAG_SIZES) {
            summaries.addAll(runCell("PAG", size, PAG_NUM_LATENTS,
                    VertexRepairSearch.AdjustmentGraphType.PAG, hasAffectedOnly));
        }

        System.out.println();
        System.out.println("==== Summary (averages over " + NUM_RUNS + " runs) ====");
        System.out.printf("%-8s %-6s %-10s %12s %8s %8s %8s %8s%n",
                "scenario", "nodes", "mode", "wall(ms)", "edits", "viol", "adjPrec", "adjRec");
        for (CellSummary cs : summaries) {
            System.out.printf("%-8s %-6d %-10s %12.0f %8.1f %8.1f %8.3f %8.3f%n",
                    cs.scenario, cs.numNodes, cs.mode, cs.avgWallMs, cs.avgEdits,
                    cs.avgViolations, cs.avgAdjPrecision, cs.avgAdjRecall);
        }
    }

    // =========================================================================
    // One (scenario, size) cell
    // =========================================================================

    private static List<CellSummary> runCell(String scenario, int numMeasured, int numLatents,
                                             VertexRepairSearch.AdjustmentGraphType graphType,
                                             boolean hasAffectedOnly) throws Exception {
        List<String> modes = new ArrayList<>();
        modes.add("full");
        if (hasAffectedOnly) modes.add("affected");

        Map<String, List<RunResult>> byMode = new LinkedHashMap<>();
        for (String m : modes) byMode.put(m, new ArrayList<>());

        for (int run = 0; run < NUM_RUNS; run++) {
            long seed = BASE_SEED + run;
            Problem prob = makeProblem(scenario, numMeasured, numLatents, seed);

            System.out.printf("%s n=%d run=%d: truth edges=%d, start edges=%d, start viol=%d%n",
                    scenario, numMeasured, run,
                    prob.truth.getNumEdges(), prob.start.getNumEdges(),
                    countViolations(prob.start, prob.testForCounting));

            Graph[] repairedByMode = new Graph[modes.size()];
            for (int mi = 0; mi < modes.size(); mi++) {
                String mode = modes.get(mi);
                RunResult rr = runOne(prob, graphType, seed, "affected".equals(mode));
                byMode.get(mode).add(rr);
                repairedByMode[mi] = rr.repaired;
                System.out.printf("  mode=%-8s wall=%8d ms  edits=%3d  viol=%3d  adjP=%.3f adjR=%.3f%s%n",
                        mode, rr.wallMs, rr.edits, rr.violations,
                        rr.adjPrecision, rr.adjRecall, rr.timedOut ? "  [TIMED OUT]" : "");
            }

            if (modes.size() == 2 && repairedByMode[0] != null && repairedByMode[1] != null) {
                boolean same = repairedByMode[0].equals(repairedByMode[1]);
                System.out.println("  repaired graphs identical across modes: " + same);
            }
        }

        List<CellSummary> out = new ArrayList<>();
        for (String mode : modes) {
            out.add(CellSummary.of(scenario, numMeasured, mode, byMode.get(mode)));
        }
        return out;
    }

    // =========================================================================
    // Problem construction
    // =========================================================================

    private static Problem makeProblem(String scenario, int numMeasured, int numLatents,
                                       long seed) throws Exception {
        RandomUtil.getInstance().setSeed(seed);

        int numNodes = numMeasured + numLatents;
        int numEdges = (int) Math.round(AVG_DEGREE * numNodes / 2.0);
        Graph trueDag = RandomGraph.randomGraph(numNodes, numLatents, numEdges,
                100, 100, 100, false);

        // Simulate, then restrict data to measured variables.
        SemPm pm = new SemPm(trueDag);
        SemIm im = new SemIm(pm);
        DataSet full = im.simulateData(SAMPLE_SIZE, false);
        List<Node> measuredData = new ArrayList<>();
        for (Node v : full.getVariables()) {
            Node inDag = trueDag.getNode(v.getName());
            if (inDag == null || inDag.getNodeType() != NodeType.LATENT) measuredData.add(v);
        }
        DataSet data = full.subsetColumns(measuredData);

        // Truth over the measured margin.
        Graph truth = numLatents > 0
                ? GraphTransforms.dagToPag(trueDag, false)
                : GraphTransforms.dagToCpdag(trueDag);

        // Corrupt the true DAG (acyclicity-preserving), then project, so the starting
        // graph is a legal CPDAG/PAG that genuinely differs from the truth.
        Graph corruptDag = new EdgeListGraph(trueDag);
        List<Edge> removable = new ArrayList<>(corruptDag.getEdges());
        RandomUtil.shuffle(removable);
        for (int i = 0; i < Math.min(NUM_REMOVALS, removable.size()); i++) {
            corruptDag.removeEdge(removable.get(i));
        }
        List<Node> measuredDagNodes = new ArrayList<>();
        for (Node v : corruptDag.getNodes()) {
            if (v.getNodeType() != NodeType.LATENT) measuredDagNodes.add(v);
        }
        int added = 0;
        for (int attempts = 0; attempts < 200 && added < NUM_ADDITIONS; attempts++) {
            int i = RandomUtil.getInstance().nextInt(measuredDagNodes.size());
            int j = RandomUtil.getInstance().nextInt(measuredDagNodes.size());
            if (i == j) continue;
            Node x = measuredDagNodes.get(i), y = measuredDagNodes.get(j);
            if (corruptDag.isAdjacentTo(x, y)) continue;
            if (corruptDag.paths().existsDirectedPath(y, x)) continue; // keep acyclic
            corruptDag.addDirectedEdge(x, y);
            added++;
        }
        Graph start = numLatents > 0
                ? GraphTransforms.dagToPag(corruptDag, false)
                : GraphTransforms.dagToCpdag(corruptDag);

        IndependenceTest testForCounting = new IndTestFisherZ(data, ALPHA);
        return new Problem(scenario, truth, start, data, testForCounting);
    }

    // =========================================================================
    // One repair run
    // =========================================================================

    private static RunResult runOne(Problem prob, VertexRepairSearch.AdjustmentGraphType graphType,
                                    long seed, boolean affectedOnly) throws Exception {
        IndependenceTest test = new IndTestFisherZ(prob.data, ALPHA);
        VertexRepairSearch repair = new VertexRepairSearch(prob.start, test, CONDITIONING_TYPE);
        repair.setGraphType(graphType);
        repair.setRepairStrategy(VertexRepairSearch.RepairStrategy.GLOBAL_QUEUE);
        repair.setSeed(seed);

        // Set the invalidation mode explicitly in both directions (when the setter
        // exists) rather than relying on the class default, which flipped to
        // affected-only on 2026-8-13.
        Method m = affectedOnlySetter();
        if (m != null) {
            m.invoke(repair, affectedOnly);
        } else if (affectedOnly) {
            throw new IllegalStateException("setter unexpectedly missing");
        }

        AtomicInteger edits = new AtomicInteger();
        repair.addRepairListener(new VertexRepairSearch.RepairListener() {
            @Override
            public void editApplied(VertexRepairSearch.CandidateEdit edit, Graph g) {
                edits.incrementAndGet();
            }
        });

        // Watchdog: cancel if the run exceeds the wall-clock cap.
        final boolean[] timedOut = {false};
        Timer watchdog = new Timer(true);
        watchdog.schedule(new TimerTask() {
            @Override
            public void run() {
                timedOut[0] = true;
                repair.cancel();
            }
        }, TIME_LIMIT_MS);

        long t0 = System.currentTimeMillis();
        Graph repaired;
        try {
            repaired = repair.search();
        } finally {
            watchdog.cancel();
        }
        long wallMs = System.currentTimeMillis() - t0;

        int violations = countViolations(repaired, prob.testForCounting);
        double[] pr = adjacencyPrecisionRecall(prob.truth, repaired);

        return new RunResult(wallMs, edits.get(), violations, pr[0], pr[1],
                timedOut[0], repaired);
    }

    // =========================================================================
    // Metrics
    // =========================================================================

    private static int countViolations(Graph g, IndependenceTest test) throws Exception {
        Set<IndependenceFact> facts = MarkovCheck.computeAllImpliedFacts(g, CONDITIONING_TYPE);
        Set<String> seen = new HashSet<>();
        int violations = 0;
        for (IndependenceFact f : facts) {
            if (f == null) continue;
            String key = VertexRepairSearch.factKey(f);
            if (!seen.add(key)) continue;
            Node x = resolve(test, f.getX());
            Node y = resolve(test, f.getY());
            Set<Node> z = new LinkedHashSet<>();
            boolean ok = (x != null && y != null);
            for (Node w : f.getZ()) {
                Node rw = resolve(test, w);
                if (rw == null) {
                    ok = false;
                    break;
                }
                z.add(rw);
            }
            if (!ok) continue;
            IndependenceResult r = test.checkIndependence(x, y, z);
            if (r != null && !r.isIndependent()) violations++;
        }
        return violations;
    }

    private static Node resolve(IndependenceTest test, Node n) {
        if (n == null || n.getName() == null) return null;
        for (Node v : test.getVariables()) {
            if (n.getName().equals(v.getName())) return v;
        }
        return null;
    }

    private static double[] adjacencyPrecisionRecall(Graph truth, Graph est) {
        Set<String> t = adjacencyKeys(truth);
        Set<String> e = adjacencyKeys(est);
        int tp = 0;
        for (String k : e) if (t.contains(k)) tp++;
        double prec = e.isEmpty() ? 1.0 : tp / (double) e.size();
        double rec = t.isEmpty() ? 1.0 : tp / (double) t.size();
        return new double[]{prec, rec};
    }

    private static Set<String> adjacencyKeys(Graph g) {
        Set<String> keys = new HashSet<>();
        for (Edge e : g.getEdges()) {
            String a = e.getNode1().getName(), b = e.getNode2().getName();
            keys.add(a.compareTo(b) <= 0 ? a + "~" + b : b + "~" + a);
        }
        return keys;
    }

    private static Method affectedOnlySetter() {
        try {
            return VertexRepairSearch.class.getMethod("setAffectedOnlyInvalidation", boolean.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    // =========================================================================
    // Records
    // =========================================================================

    private record Problem(String scenario, Graph truth, Graph start, DataSet data,
                           IndependenceTest testForCounting) {
    }

    private record RunResult(long wallMs, int edits, int violations,
                             double adjPrecision, double adjRecall,
                             boolean timedOut, Graph repaired) {
    }

    private record CellSummary(String scenario, int numNodes, String mode,
                               double avgWallMs, double avgEdits, double avgViolations,
                               double avgAdjPrecision, double avgAdjRecall) {

        static CellSummary of(String scenario, int numNodes, String mode, List<RunResult> runs) {
            double w = 0, ed = 0, v = 0, p = 0, r = 0;
            for (RunResult rr : runs) {
                w += rr.wallMs;
                ed += rr.edits;
                v += rr.violations;
                p += rr.adjPrecision;
                r += rr.adjRecall;
            }
            int n = Math.max(1, runs.size());
            return new CellSummary(scenario, numNodes, mode,
                    w / n, ed / n, v / n, p / n, r / n);
        }
    }
}

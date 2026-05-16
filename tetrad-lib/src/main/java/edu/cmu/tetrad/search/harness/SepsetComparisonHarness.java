package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.RecursiveBlocking;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * Harness comparing the number of independence tests (d-separation oracle queries)
 * required by two methods to find a separating set for each non-adjacent pair
 * in a random DAG:
 *
 * <ol>
 *   <li>Exhaustive enumeration (PC-style): subsets of Adj(x) and Adj(y) in
 *       greedy depth order.</li>
 *   <li>Iterative-deepening recursive blocking (RB): calls
 *       blockPathsRecursivelyFull with increasing path-length caps.</li>
 * </ol>
 *
 * <p>Output is a CSV with one row per (rep, pair, method), suitable for
 * plotting in Python, followed by a summary table printed to stdout.</p>
 */
public class SepsetComparisonHarness {

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    private static final int[]  NODE_COUNTS   = {10, 20, 50};
    private static final int[]  AVG_DEGREES   = {2, 4, 6};
    private static final int    REPS          = 20;
    private static final int    PAIRS_PER_REP = 20;
    private static final String OUTPUT_FILE   = "sepset_comparison.csv";

    // Cap for exhaustive enumeration.
    private static final int MAX_EXHAUSTIVE_TESTS = 10_000;

    // Parameters for iterative-deepening RB.
    private static final int MAX_RECURSION_DEPTH = 20;  // ceiling = p when -1
    private static final int RB_DEPTH         = -1;  // no cap on |Z|
    private static final int RB_MAX_RADIUS    = -1;
    private static final int RB_NEAR_ENDPOINT = 1;   // 1=near x, 2=near y, 3=near either

    // Per-pair timeout in milliseconds.
    private static final long PAIR_TIMEOUT_MS = 5_000;

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws IOException {

        List<SummaryRow> summaryRows = new ArrayList<>();

        try (PrintWriter out = new PrintWriter(new FileWriter(OUTPUT_FILE))) {

            out.println("nodes,avg_degree,rep,x,y,method,test_count,set_size,outcome");

            for (int p : NODE_COUNTS) {
                for (int avgDeg : AVG_DEGREES) {
                    int numEdges = (p * avgDeg) / 2;

                    // Exhaustive accumulators
                    long totalExhMs      = 0;
                    int  exhCount        = 0;
                    int  exhTimedOut     = 0;
                    int  exhSucceeded    = 0;
                    long exhTotalTests   = 0;
                    long exhTotalSetSize = 0;

                    // RB accumulators
                    long totalRbMs      = 0;
                    int  rbCount        = 0;
                    int  rbSucceeded    = 0;
                    int  rbUnblockable  = 0;
                    int  rbNull         = 0;
                    long rbTotalTests   = 0;
                    long rbTotalSetSize = 0;

                    long conditionStartMs = System.currentTimeMillis();
                    System.out.printf("%n=== p=%d, avgDeg=%d ===%n", p, avgDeg);

                    for (int rep = 1; rep <= REPS; rep++) {

                        Graph      dag    = generateRandomForwardDag(p, numEdges);
                        MsepTest   oracle = new MsepTest(dag);
                        List<Node> nodes  = dag.getNodes();

                        int pairsFound = 0;
                        int attempts   = 0;

                        while (pairsFound < PAIRS_PER_REP) {

                            if (++attempts > PAIRS_PER_REP * 20) {
                                System.out.printf(
                                        "  rep %d: only found %d non-adjacent pairs "
                                                + "after %d attempts, moving on%n",
                                        rep, pairsFound, attempts - 1);
                                break;
                            }

                            Node x = nodes.get(
                                    RandomUtil.getInstance().nextInt(nodes.size()));
                            Node y = nodes.get(
                                    RandomUtil.getInstance().nextInt(nodes.size()));

                            if (x == y || dag.isAdjacentTo(x, y)) continue;

                            // ---- 1. Exhaustive enumeration ----
                            long t0 = System.nanoTime();
                            ExhaustiveResult exh = exhaustiveEnumeration(x, y, dag, oracle);
                            long exhNs = System.nanoTime() - t0;

                            totalExhMs += exhNs / 1_000_000;
                            exhCount++;
                            if (exh.timedOut) {
                                exhTimedOut++;
                            } else if (exh.setSize >= 0) {
                                exhSucceeded++;
                                exhTotalTests   += exh.testCount;
                                exhTotalSetSize += exh.setSize;
                            }

                            String exhOutcome = exh.timedOut ? "timed_out"
                                    : exh.setSize >= 0 ? "success" : "failed";

                            System.out.printf(
                                    "  [exh] p=%d deg=%d rep=%d (%s,%s) "
                                            + "tests=%d setSize=%d time=%.3fms%s%n",
                                    p, avgDeg, rep,
                                    x.getName(), y.getName(),
                                    exh.testCount, exh.setSize,
                                    exhNs / 1e6,
                                    exh.timedOut ? " TIMED_OUT" : "");

                            out.printf("%d,%d,%d,%s,%s,exhaustive,%d,%d,%s%n",
                                    p, avgDeg, rep,
                                    x.getName(), y.getName(),
                                    exh.testCount, exh.setSize,
                                    exhOutcome);

                            // ---- 2. Iterative-deepening RB ----
                            long t1 = System.nanoTime();
                            RbResult rb = iterativeDeepeningRb(x, y, dag, oracle, p);
                            long rbNs = System.nanoTime() - t1;

                            totalRbMs += rbNs / 1_000_000;
                            rbCount++;

                            String rbOutcome;
                            if (rb.unblockable) {
                                rbUnblockable++;
                                rbOutcome = "unblockable";
                                System.out.printf(
                                        "  [rb ] UNBLOCKABLE p=%d deg=%d rep=%d (%s,%s) "
                                                + "time=%.3fms%n",
                                        p, avgDeg, rep,
                                        x.getName(), y.getName(), rbNs / 1e6);
                            } else if (rb.setSize < 0) {
                                rbNull++;
                                rbOutcome = "indeterminate";
                                System.out.printf(
                                        "  [rb ] INDETERMINATE p=%d deg=%d rep=%d (%s,%s) "
                                                + "time=%.3fms%n",
                                        p, avgDeg, rep,
                                        x.getName(), y.getName(), rbNs / 1e6);
                            } else {
                                rbSucceeded++;
                                rbOutcome = "success";
                                rbTotalTests   += rb.testCount;
                                rbTotalSetSize += rb.setSize;
                                System.out.printf(
                                        "  [rb ] p=%d deg=%d rep=%d (%s,%s) "
                                                + "tests=%d setSize=%d time=%.3fms%n",
                                        p, avgDeg, rep,
                                        x.getName(), y.getName(),
                                        rb.testCount, rb.setSize, rbNs / 1e6);
                            }

                            out.printf("%d,%d,%d,%s,%s,recursive_blocking,%d,%d,%s%n",
                                    p, avgDeg, rep,
                                    x.getName(), y.getName(),
                                    rb.testCount, rb.setSize,
                                    rbOutcome);

                            pairsFound++;
                        }

                        if (rep % 10 == 0) {
                            System.out.printf(
                                    "  -- rep %3d/%d  exh=%.3fms  rb=%.3fms  "
                                            + "exhTimeouts=%d  rbIndet=%d%n",
                                    rep, REPS,
                                    exhCount > 0 ? (double) totalExhMs / exhCount : 0.0,
                                    rbCount  > 0 ? (double) totalRbMs  / rbCount  : 0.0,
                                    exhTimedOut, rbNull);
                        }
                    }

                    long conditionMs = System.currentTimeMillis() - conditionStartMs;

                    System.out.printf(
                            "%nFinished p=%d avgDeg=%d in %.1fs%n"
                                    + "  Exhaustive : %d pairs  avg=%.3fms  "
                                    + "succeeded=%d  timeouts=%d (limit=%d)%n"
                                    + "  RB         : %d pairs  avg=%.3fms  "
                                    + "succeeded=%d  unblockable=%d  indeterminate=%d%n",
                            p, avgDeg, conditionMs / 1000.0,
                            exhCount,
                            exhCount > 0 ? (double) totalExhMs / exhCount : 0.0,
                            exhSucceeded, exhTimedOut, MAX_EXHAUSTIVE_TESTS,
                            rbCount,
                            rbCount > 0 ? (double) totalRbMs / rbCount : 0.0,
                            rbSucceeded, rbUnblockable, rbNull);

                    summaryRows.add(new SummaryRow(
                            p, avgDeg,
                            exhCount, exhSucceeded, exhTimedOut,
                            exhCount     > 0 ? (double) totalExhMs     / exhCount     : 0.0,
                            exhSucceeded > 0 ? (double) exhTotalTests  / exhSucceeded : 0.0,
                            exhSucceeded > 0 ? (double) exhTotalSetSize / exhSucceeded : 0.0,
                            rbCount, rbSucceeded, rbUnblockable, rbNull,
                            rbCount      > 0 ? (double) totalRbMs      / rbCount      : 0.0,
                            rbSucceeded  > 0 ? (double) rbTotalTests   / rbSucceeded  : 0.0,
                            rbSucceeded  > 0 ? (double) rbTotalSetSize / rbSucceeded  : 0.0,
                            conditionMs / 1000.0));
                }
            }
        }

        printSummaryTable(summaryRows);
        System.out.println("\nOutput written to " + OUTPUT_FILE);
    }

    // -----------------------------------------------------------------------
    // Iterative-deepening recursive blocking
    // -----------------------------------------------------------------------

    private static RbResult iterativeDeepeningRb(
            Node x, Node y, Graph dag, MsepTest oracle, int p) {

        int ceiling = (MAX_RECURSION_DEPTH < 0) ? p : MAX_RECURSION_DEPTH;

        try {
            for (int pathLen = 0; pathLen <= ceiling; pathLen++) {

                if (Thread.currentThread().isInterrupted()) {
                    return new RbResult(0, -1, false);
                }

                long deadlineMs = System.currentTimeMillis() + PAIR_TIMEOUT_MS;

                RecursiveBlocking.BlockingResult result =
                        RecursiveBlocking.blockPathsRecursivelyFull(
                                dag, x, y,
                                Set.of(), Set.of(),
                                pathLen,
                                RB_DEPTH,
                                RB_MAX_RADIUS,
                                RB_NEAR_ENDPOINT,
                                true,        // ignoreDirectEdge
                                deadlineMs);

                if (result.found()) {
                    Set<Node> Z = result.blockingSet();
                    if (oracle.checkIndependence(x, y, Z).isIndependent()) {
                        return new RbResult(1, Z.size(), false);
                    }
                    // RB found a graphical separator but the oracle rejected it.
                    return new RbResult(1, -1, false);
                }

                if (!result.indeterminate()) {
                    // Definitive UNBLOCKABLE — no separator exists.
                    return new RbResult(0, -1, true);
                }

                // INDETERMINATE — try next path length.
            }

            // Ceiling reached without resolution.
            return new RbResult(0, -1, false);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RbResult(0, -1, false);
        } catch (TimeoutException e) {
            return new RbResult(0, -1, false);
        }
    }

    // -----------------------------------------------------------------------
    // Exhaustive enumeration (PC-style)
    // -----------------------------------------------------------------------

    private static ExhaustiveResult exhaustiveEnumeration(
            Node x, Node y, Graph dag, MsepTest oracle) {

        int testCount = 0;

        List<Node> adjX = new ArrayList<>(dag.getAdjacentNodes(x));
        adjX.remove(y);

        List<Node> adjY = new ArrayList<>(dag.getAdjacentNodes(y));
        adjY.remove(x);

        int maxDepth = Math.max(adjX.size(), adjY.size());

        for (int depth = 0; depth <= maxDepth; depth++) {

            for (List<Node> subset : subsetsOfSize(adjX, depth)) {
                if (testCount >= MAX_EXHAUSTIVE_TESTS) {
                    return new ExhaustiveResult(testCount, -1, true);
                }
                Set<Node> Z = new HashSet<>(subset);
                testCount++;
                if (oracle.checkIndependence(x, y, Z).isIndependent()) {
                    return new ExhaustiveResult(testCount, Z.size(), false);
                }
            }

            for (List<Node> subset : subsetsOfSize(adjY, depth)) {
                if (testCount >= MAX_EXHAUSTIVE_TESTS) {
                    return new ExhaustiveResult(testCount, -1, true);
                }
                Set<Node> Z = new HashSet<>(subset);
                testCount++;
                if (oracle.checkIndependence(x, y, Z).isIndependent()) {
                    return new ExhaustiveResult(testCount, Z.size(), false);
                }
            }
        }

        return new ExhaustiveResult(testCount, -1, false);
    }

    // -----------------------------------------------------------------------
    // Graph generation
    // -----------------------------------------------------------------------

    private static Graph generateRandomForwardDag(int numNodes, int numEdges) {
        return RandomGraph.randomGraph(numNodes, 0, numEdges, 100, 100, 100, false);
    }

    // -----------------------------------------------------------------------
    // Subset enumeration utilities
    // -----------------------------------------------------------------------

    private static List<List<Node>> subsetsOfSize(List<Node> list, int size) {
        List<List<Node>> result = new ArrayList<>();
        subsetsOfSizeHelper(list, size, 0, new ArrayList<>(), result);
        return result;
    }

    private static void subsetsOfSizeHelper(
            List<Node> list, int size, int start,
            List<Node> current, List<List<Node>> result) {

        if (current.size() == size) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < list.size(); i++) {
            current.add(list.get(i));
            subsetsOfSizeHelper(list, size, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    // -----------------------------------------------------------------------
    // Summary table
    // -----------------------------------------------------------------------

    private static void printSummaryTable(List<SummaryRow> rows) {
        System.out.println();
        System.out.println("=".repeat(110));
        System.out.println("SUMMARY TABLE");
        System.out.println("=".repeat(110));
        System.out.printf(
                "%-6s %-8s  %-30s  %-40s%n",
                "", "", "--- Exhaustive ---", "--- Recursive Blocking ---");
        System.out.printf(
                "%-6s %-8s  %-7s %-10s %-9s %-9s  "
                        + "%-7s %-10s %-12s %-9s %-9s%n",
                "p", "avgDeg",
                "pairs", "succeeded", "timeouts", "avg tests",
                "pairs", "succeeded", "indeterminate", "avg tests", "avg |Z|");
        System.out.println("-".repeat(110));
        for (SummaryRow r : rows) {
            System.out.printf(
                    "%-6d %-8d  %-7d %-10d %-9d %-9.2f  "
                            + "%-7d %-10d %-13d %-9.2f %-9.2f%n",
                    r.p, r.avgDeg,
                    r.exhPairs, r.exhSucceeded, r.exhTimedOut, r.exhAvgTests,
                    r.rbPairs, r.rbSucceeded, r.rbIndeterminate,
                    r.rbAvgTests, r.rbAvgSetSize);
        }
        System.out.println("=".repeat(110));
    }

    // -----------------------------------------------------------------------
    // Result and summary containers
    // -----------------------------------------------------------------------

    private static class ExhaustiveResult {
        final int     testCount;
        final int     setSize;   // -1 if no separator found
        final boolean timedOut;

        ExhaustiveResult(int testCount, int setSize, boolean timedOut) {
            this.testCount = testCount;
            this.setSize   = setSize;
            this.timedOut  = timedOut;
        }
    }

    private static class RbResult {
        final int     testCount;
        final int     setSize;     // -1 if no separator found or indeterminate
        final boolean unblockable; // true iff RB returned definitive UNBLOCKABLE

        RbResult(int testCount, int setSize, boolean unblockable) {
            this.testCount   = testCount;
            this.setSize     = setSize;
            this.unblockable = unblockable;
        }
    }

    private static class SummaryRow {
        final int    p, avgDeg;
        final int    exhPairs, exhSucceeded, exhTimedOut;
        final double exhAvgMs, exhAvgTests, exhAvgSetSize;
        final int    rbPairs, rbSucceeded, rbUnblockable, rbIndeterminate;
        final double rbAvgMs, rbAvgTests, rbAvgSetSize;
        final double totalSecs;

        SummaryRow(int p, int avgDeg,
                   int exhPairs, int exhSucceeded, int exhTimedOut,
                   double exhAvgMs, double exhAvgTests, double exhAvgSetSize,
                   int rbPairs, int rbSucceeded, int rbUnblockable, int rbIndeterminate,
                   double rbAvgMs, double rbAvgTests, double rbAvgSetSize,
                   double totalSecs) {
            this.p               = p;
            this.avgDeg          = avgDeg;
            this.exhPairs        = exhPairs;
            this.exhSucceeded    = exhSucceeded;
            this.exhTimedOut     = exhTimedOut;
            this.exhAvgMs        = exhAvgMs;
            this.exhAvgTests     = exhAvgTests;
            this.exhAvgSetSize   = exhAvgSetSize;
            this.rbPairs         = rbPairs;
            this.rbSucceeded     = rbSucceeded;
            this.rbUnblockable   = rbUnblockable;
            this.rbIndeterminate = rbIndeterminate;
            this.rbAvgMs         = rbAvgMs;
            this.rbAvgTests      = rbAvgTests;
            this.rbAvgSetSize    = rbAvgSetSize;
            this.totalSecs       = totalSecs;
        }
    }
}

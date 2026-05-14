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
 * required by three methods to find a separating set for each non-adjacent pair
 * in a random DAG:
 *
 * <ol>
 *   <li>Exhaustive enumeration (PC-style): subsets of Adj(x) and Adj(y) in
 *       greedy depth order.</li>
 *   <li>Iterative-deepening recursive blocking (RB): calls
 *       blockPathsRecursivelyFull with increasing path-length caps.</li>
 *   <li>Hybrid: exhaustive enumeration first; if it fails, falls back to
 *       iterative-deepening RB.</li>
 * </ol>
 *
 * <p>Output is a CSV with one row per (rep, pair, method), suitable for
 * plotting in Python.</p>
 */
public class SepsetComparisonHarness {

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    private static final int[]  NODE_COUNTS   = {20};//10, 20, 50};
    private static final int[]  AVG_DEGREES   = {6};//2, 4, 6};
    private static final int    REPS          = 100;
    private static final int    PAIRS_PER_REP = 100;
    private static final String OUTPUT_FILE   = "sepset_comparison.csv";

    // Cap for exhaustive enumeration.
    private static final int MAX_EXHAUSTIVE_TESTS = 10_000;

    // Parameters for iterative-deepening RB (used in both pure-RB and hybrid).
    private static final int RB_MAX_PATH_LEN  = -1;
    private static final int RB_DEPTH         = -1;
    private static final int RB_MAX_RADIUS    = 1;
    private static final int RB_NEAR_ENDPOINT = 3; // 1 = near x 2 = near y 3 = near either

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws IOException {

        try (PrintWriter out = new PrintWriter(new FileWriter(OUTPUT_FILE))) {

            out.println("nodes,avg_degree,rep,x,y,method,test_count,set_size");

            for (int p : NODE_COUNTS) {
                for (int avgDeg : AVG_DEGREES) {
                    int numEdges = (p * avgDeg) / 2;

                    // Accumulators for all three methods
                    long totalExhMs      = 0, totalRbMs     = 0, totalHybMs    = 0;
                    int  exhCount        = 0, rbCount       = 0, hybCount      = 0;
                    int  exhTimedOut     = 0;
                    int  rbUnblockable   = 0, rbNull        = 0;
                    int  hybUnblockable  = 0, hybNull       = 0;
                    int  hybUsedRb       = 0; // how often hybrid fell back to RB

                    long conditionStartMs = System.currentTimeMillis();
                    System.out.printf("%n=== p=%d, avgDeg=%d ===%n", p, avgDeg);

                    for (int rep = 0; rep < REPS; rep++) {

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
                                        rep, pairsFound, attempts);
                                break;
                            }

                            Node x = nodes.get(
                                    RandomUtil.getInstance().nextInt(nodes.size()));
                            Node y = nodes.get(
                                    RandomUtil.getInstance().nextInt(nodes.size()));

                            if (x == y || dag.isAdjacentTo(x, y)) continue;

                            // ---- 1. Exhaustive enumeration ----
                            long t0 = System.nanoTime();
                            ExhaustiveResult exh = exhaustiveEnumeration(
                                    x, y, dag, oracle);
                            long exhNs = System.nanoTime() - t0;

                            totalExhMs += exhNs / 1_000_000;
                            exhCount++;
                            if (exh.timedOut) exhTimedOut++;

                            System.out.printf(
                                    "  [exh] p=%d deg=%d rep=%d (%s,%s) "
                                            + "tests=%d setSize=%d time=%.3fms%s%n",
                                    p, avgDeg, rep,
                                    x.getName(), y.getName(),
                                    exh.testCount, exh.setSize,
                                    exhNs / 1e6,
                                    exh.timedOut ? " TIMED_OUT" : "");

                            out.printf("%d,%d,%d,%s,%s,exhaustive,%d,%d%n",
                                    p, avgDeg, rep,
                                    x.getName(), y.getName(),
                                    exh.testCount, exh.setSize);

                            // ---- 2. Iterative-deepening RB ----
                            long t1 = System.nanoTime();
                            RbResult rb = iterativeDeepeningRb(x, y, dag, oracle, p);
                            long rbNs = System.nanoTime() - t1;

                            totalRbMs += rbNs / 1_000_000;
                            rbCount++;

                            if (rb.unblockable) {
                                rbUnblockable++;
                                System.out.printf(
                                        "  [rb ] UNBLOCKABLE "
                                                + "p=%d deg=%d rep=%d (%s,%s) time=%.3fms%n",
                                        p, avgDeg, rep,
                                        x.getName(), y.getName(), rbNs / 1e6);
                            } else if (rb.setSize < 0) {
                                rbNull++;
                                System.out.printf(
                                        "  [rb ] INDETERMINATE "
                                                + "p=%d deg=%d rep=%d (%s,%s) time=%.3fms%n",
                                        p, avgDeg, rep,
                                        x.getName(), y.getName(), rbNs / 1e6);
                            } else {
                                System.out.printf(
                                        "  [rb ] p=%d deg=%d rep=%d (%s,%s) "
                                                + "tests=%d setSize=%d time=%.3fms%n",
                                        p, avgDeg, rep,
                                        x.getName(), y.getName(),
                                        rb.testCount, rb.setSize, rbNs / 1e6);
                            }

                            out.printf("%d,%d,%d,%s,%s,recursive_blocking,%d,%d%n",
                                    p, avgDeg, rep,
                                    x.getName(), y.getName(),
                                    rb.testCount, rb.setSize);

                            // ---- 3. Hybrid: exhaustive first, then RB ----
                            long t2 = System.nanoTime();
                            HybridResult hyb = hybridRb(x, y, dag, oracle, p);
                            long hybNs = System.nanoTime() - t2;

                            totalHybMs += hybNs / 1_000_000;
                            hybCount++;
                            if (hyb.usedRb) hybUsedRb++;

                            if (hyb.unblockable) {
                                hybUnblockable++;
                                System.out.printf(
                                        "  [hyb] UNBLOCKABLE "
                                                + "p=%d deg=%d rep=%d (%s,%s) time=%.3fms%n",
                                        p, avgDeg, rep,
                                        x.getName(), y.getName(), hybNs / 1e6);
                            } else if (hyb.setSize < 0) {
                                hybNull++;
                                System.out.printf(
                                        "  [hyb] INDETERMINATE "
                                                + "p=%d deg=%d rep=%d (%s,%s) "
                                                + "usedRb=%b time=%.3fms%n",
                                        p, avgDeg, rep,
                                        x.getName(), y.getName(),
                                        hyb.usedRb, hybNs / 1e6);
                            } else {
                                System.out.printf(
                                        "  [hyb] p=%d deg=%d rep=%d (%s,%s) "
                                                + "tests=%d setSize=%d usedRb=%b time=%.3fms%n",
                                        p, avgDeg, rep,
                                        x.getName(), y.getName(),
                                        hyb.testCount, hyb.setSize,
                                        hyb.usedRb, hybNs / 1e6);
                            }

                            out.printf("%d,%d,%d,%s,%s,hybrid,%d,%d%n",
                                    p, avgDeg, rep,
                                    x.getName(), y.getName(),
                                    hyb.testCount, hyb.setSize);

                            pairsFound++;
                        }

                        if ((rep + 1) % 10 == 0) {
                            System.out.printf(
                                    "  -- rep %3d/%d  "
                                            + "exh=%.3fms  rb=%.3fms  hyb=%.3fms  "
                                            + "exhTimeouts=%d  rbIndet=%d  hybIndet=%d  "
                                            + "hybUsedRb=%d%n",
                                    rep + 1, REPS,
                                    exhCount > 0
                                            ? (double) totalExhMs / exhCount : 0.0,
                                    rbCount  > 0
                                            ? (double) totalRbMs  / rbCount  : 0.0,
                                    hybCount > 0
                                            ? (double) totalHybMs / hybCount : 0.0,
                                    exhTimedOut, rbNull, hybNull, hybUsedRb);
                        }
                    }

                    long conditionMs = System.currentTimeMillis() - conditionStartMs;
                    System.out.printf(
                            "%nFinished p=%d avgDeg=%d in %.1fs%n"
                                    + "  Exhaustive : %d pairs  avg=%.3fms  "
                                    + "timeouts=%d (limit=%d)%n"
                                    + "  RB         : %d pairs  avg=%.3fms  "
                                    + "unblockable=%d  indeterminate=%d%n"
                                    + "  Hybrid     : %d pairs  avg=%.3fms  "
                                    + "unblockable=%d  indeterminate=%d  usedRb=%d%n",
                            p, avgDeg, conditionMs / 1000.0,
                            exhCount,
                            exhCount > 0 ? (double) totalExhMs / exhCount : 0.0,
                            exhTimedOut, MAX_EXHAUSTIVE_TESTS,
                            rbCount,
                            rbCount  > 0 ? (double) totalRbMs  / rbCount  : 0.0,
                            rbUnblockable, rbNull,
                            hybCount,
                            hybCount > 0 ? (double) totalHybMs / hybCount : 0.0,
                            hybUnblockable, hybNull, hybUsedRb);
                }
            }
        }

        System.out.println("\nOutput written to " + OUTPUT_FILE);
    }

    // -----------------------------------------------------------------------
    // Method 2: Iterative-deepening recursive blocking
    // -----------------------------------------------------------------------

    private static RbResult iterativeDeepeningRb(
            Node x, Node y, Graph dag, MsepTest oracle, int p) {

        try {
            int ceiling = (RB_MAX_PATH_LEN < 0) ? p : RB_MAX_PATH_LEN;

            for (int pathLen = 0; pathLen <= ceiling; pathLen++) {

                if (Thread.currentThread().isInterrupted()) {
                    return new RbResult(0, -1, false);
                }

                RecursiveBlocking.BlockingResult result = null;
                try {
                    result = RecursiveBlocking.blockPathsRecursivelyFull(
                            dag, x, y,
                            Set.of(), Set.of(),
                            pathLen,
                            RB_DEPTH,
                            RB_MAX_RADIUS,
                            RB_NEAR_ENDPOINT,
                            true,
                            Long.MAX_VALUE);
                } catch (TimeoutException e) {
                    throw new RuntimeException(e);
                }

                if (result.found()) {
                    Set<Node> Z = result.blockingSet();
                    int testCount = 0;

                    testCount++;
                    if (oracle.checkIndependence(x, y, Z).isIndependent()) {
                        return new RbResult(testCount, Z.size(), false);
                    }

                    return new RbResult(testCount, -1, false);
                }

                if (!result.indeterminate()) {
                    // UNBLOCKABLE — no separator exists within the constrained search space
                    return new RbResult(0, -1, true);
                }
            }

            // Ceiling reached without resolution
            return new RbResult(0, -1, false);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RbResult(0, -1, false);
        }
    }

    // -----------------------------------------------------------------------
    // Method 3: Hybrid — exhaustive first, fall back to RB if exhaustive fails
    // -----------------------------------------------------------------------

    private static HybridResult hybridRb(
            Node x, Node y, Graph dag, MsepTest oracle, int p) {

        // First try exhaustive enumeration over adjacency sets.
        ExhaustiveResult exh = exhaustiveEnumeration(x, y, dag, oracle);

        if (exh.setSize >= 0) {
            // Exhaustive succeeded — return its result, no RB needed.
            return new HybridResult(exh.testCount, exh.setSize, false, false);
        }

        // Exhaustive failed (timed out or found no separator in adj sets).
        // Fall back to iterative-deepening RB, accumulating the test count.
        RbResult rb = iterativeDeepeningRb(x, y, dag, oracle, p);

        int totalTests = exh.testCount + rb.testCount;

        if (rb.unblockable) {
            return new HybridResult(totalTests, -1, true, true);
        }
        if (rb.setSize < 0) {
            return new HybridResult(totalTests, -1, false, true);
        }

        return new HybridResult(totalTests, rb.setSize, false, true);
    }

    // -----------------------------------------------------------------------
    // Method 1: Exhaustive enumeration (PC-style)
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
    // Result containers
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

    private static class HybridResult {
        final int     testCount;   // exhaustive tests + RB tests (if fallback used)
        final int     setSize;     // -1 if no separator found
        final boolean unblockable;
        final boolean usedRb;      // true iff RB fallback was invoked

        HybridResult(int testCount, int setSize,
                     boolean unblockable, boolean usedRb) {
            this.testCount   = testCount;
            this.setSize     = setSize;
            this.unblockable = unblockable;
            this.usedRb      = usedRb;
        }
    }
}

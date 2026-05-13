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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Harness comparing the number of independence tests (d-separation oracle queries)
 * required by exhaustive enumeration (PC-style) vs. iterative-deepening recursive
 * blocking to find a separating set for each non-adjacent pair in a random DAG.
 *
 * <p>Output is a CSV file with one row per (run, pair, method), suitable for
 * plotting in Python.</p>
 */
public class SepsetComparisonHarness {

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    private static final int[] NODE_COUNTS = {10, 20, 50};
    private static final int[] AVG_DEGREES = {2, 4, 6};
    private static final int REPS = 100;
    private static final int PAIRS_PER_REP = 100;
    private static final String OUTPUT_FILE = "sepset_comparison.csv";

    // Maximum number of tests exhaustive enumeration will run before giving up.
    private static final int MAX_EXHAUSTIVE_TESTS = 10_000;

    // Parameters passed to iterative-deepening RB.
    private static final int RB_MAX_PATH_LEN = -1;
    private static final int RB_DEPTH = -1;
    private static final int RB_MAX_RADIUS = -1;
    private static final int RB_NEAR_ENDPOINT = 1;

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws IOException {

        try (PrintWriter out = new PrintWriter(new FileWriter(OUTPUT_FILE))) {

            out.println("nodes,avg_degree,rep,x,y,method,test_count,set_size");

            for (int p : NODE_COUNTS) {
                for (int avgDeg : AVG_DEGREES) {
                    int numEdges = (p * avgDeg) / 2;

                    long totalExhMs = 0;
                    long totalRbMs = 0;
                    int exhCount = 0;
                    int rbCount = 0;
                    int exhTimedOut = 0;
                    int rbUnblockable = 0;
                    int rbNull = 0;

                    long conditionStartMs = System.currentTimeMillis();
                    System.out.printf("%n=== p=%d, avgDeg=%d ===%n", p, avgDeg);

                    for (int rep = 0; rep < REPS; rep++) {

                        Graph dag = generateRandomForwardDag(p, numEdges);
                        MsepTest oracle = new MsepTest(dag);
                        List<Node> nodes = dag.getNodes();

                        int pairsFound = 0;
                        int attempts = 0;

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

                            // ---- Exhaustive enumeration ----
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

                            // ---- Iterative-deepening recursive blocking ----
                            long t1 = System.nanoTime();
                            RbResult rb = iterativeDeepeningRb(x, y, dag, oracle, p);
                            long rbNs = System.nanoTime() - t1;

                            totalRbMs += rbNs / 1_000_000;
                            rbCount++;

                            if (rb.unblockable) {
                                rbUnblockable++;
                                System.out.printf(
                                        "  [rb ] UNEXPECTED UNBLOCKABLE "
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

                            pairsFound++;
                        }

                        if ((rep + 1) % 10 == 0) {
                            System.out.printf(
                                    "  -- rep %3d/%d  "
                                            + "exh avg=%.3fms  rb avg=%.3fms  "
                                            + "exhTimeouts=%d  rbUnblockable=%d  "
                                            + "rbIndeterminate=%d%n",
                                    rep + 1, REPS,
                                    exhCount > 0
                                            ? (double) totalExhMs / exhCount : 0.0,
                                    rbCount > 0
                                            ? (double) totalRbMs / rbCount : 0.0,
                                    exhTimedOut, rbUnblockable, rbNull);
                        }
                    }

                    long conditionMs = System.currentTimeMillis() - conditionStartMs;
                    System.out.printf(
                            "%nFinished p=%d avgDeg=%d in %.1fs%n"
                                    + "  Exhaustive   : %d pairs  avg=%.3fms/pair  "
                                    + "timeouts=%d (limit=%d tests)%n"
                                    + "  Iter.Deep.RB : %d pairs  avg=%.3fms/pair  "
                                    + "unblockable=%d (should be 0)  "
                                    + "indeterminate=%d%n",
                            p, avgDeg, conditionMs / 1000.0,
                            exhCount,
                            exhCount > 0 ? (double) totalExhMs / exhCount : 0.0,
                            exhTimedOut, MAX_EXHAUSTIVE_TESTS,
                            rbCount,
                            rbCount > 0 ? (double) totalRbMs / rbCount : 0.0,
                            rbUnblockable, rbNull);
                }
            }
        }

        System.out.println("\nOutput written to " + OUTPUT_FILE);
    }

    // -----------------------------------------------------------------------
    // Iterative-deepening recursive blocking
    // -----------------------------------------------------------------------

    private static RbResult iterativeDeepeningRb(
            Node x, Node y, Graph dag, MsepTest oracle, int p) {

        try {
            RecursiveBlocking.BlockingResult result =
                    RecursiveBlocking.blockPathsIterativeDeepening(
                            dag, x, y,
                            Set.of(), Set.of(),
                            RB_MAX_PATH_LEN,
                            RB_DEPTH,
                            RB_MAX_RADIUS,
                            RB_NEAR_ENDPOINT,
                            true);

            if (!result.found()) {

                boolean isUnblockable = !result.indeterminate();

                if (isUnblockable) {
                    // Diagnostic: verify whether a separating set actually exists
                    // by asking the oracle directly over all subsets of adjacents.
                    // If one is found, RB returned a spurious UNBLOCKABLE.
                    Set<Node> trueZ = findSepsetExhaustive(x, y, dag, oracle);

                    System.out.printf(
                            "  [rb ] UNBLOCKABLE for (%s, %s)%n"
                                    + "        adj(x)=%s%n"
                                    + "        adj(y)=%s%n"
                                    + "        exhaustive sep set = %s%n",
                            x.getName(), y.getName(),
                            dag.getAdjacentNodes(x),
                            dag.getAdjacentNodes(y),
                            trueZ == null ? "NONE FOUND" : trueZ.toString());

                    if (trueZ != null) {
                        System.out.println(
                                "        --> SPURIOUS UNBLOCKABLE: "
                                        + "sep set exists but RB did not find it");
                    } else {
                        System.out.println(
                                "        --> GENUINE: no sep set in adj(x) or adj(y)");
                    }
                }

                return new RbResult(0, -1, isUnblockable);
            }

            Set<Node> Z = result.blockingSet();
            int testCount = 0;

            testCount++;
            if (oracle.checkIndependence(x, y, Z).isIndependent()) {
                return new RbResult(testCount, Z.size(), false);
            }

            // Blocking set found graphically but oracle didn't confirm
            return new RbResult(testCount, -1, false);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RbResult(0, -1, false);
        }
    }

    /**
     * Exhaustive search for a separating set in Adj(x) u Adj(y), used only
     * for diagnostic purposes when RB returns an unexpected UNBLOCKABLE.
     * Returns the first separating set found, or null if none exists.
     */
    private static Set<Node> findSepsetExhaustive(
            Node x, Node y, Graph dag, MsepTest oracle) {

        List<Node> adjX = new ArrayList<>(dag.getAdjacentNodes(x));
        adjX.remove(y);
        List<Node> adjY = new ArrayList<>(dag.getAdjacentNodes(y));
        adjY.remove(x);

        int maxDepth = Math.max(adjX.size(), adjY.size());

        for (int depth = 0; depth <= maxDepth; depth++) {
            for (List<Node> subset : subsetsOfSize(adjX, depth)) {
                Set<Node> Z = new HashSet<>(subset);
                if (oracle.checkIndependence(x, y, Z).isIndependent()) return Z;
            }
            for (List<Node> subset : subsetsOfSize(adjY, depth)) {
                Set<Node> Z = new HashSet<>(subset);
                if (oracle.checkIndependence(x, y, Z).isIndependent()) return Z;
            }
        }
        return null;
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
    // Result containers
    // -----------------------------------------------------------------------

    private static class ExhaustiveResult {
        final int testCount;
        final int setSize;
        final boolean timedOut;

        ExhaustiveResult(int testCount, int setSize, boolean timedOut) {
            this.testCount = testCount;
            this.setSize = setSize;
            this.timedOut = timedOut;
        }
    }

    private static class RbResult {
        final int testCount;
        final int setSize;
        final boolean unblockable;

        RbResult(int testCount, int setSize, boolean unblockable) {
            this.testCount = testCount;
            this.setSize = setSize;
            this.unblockable = unblockable;
        }
    }
}

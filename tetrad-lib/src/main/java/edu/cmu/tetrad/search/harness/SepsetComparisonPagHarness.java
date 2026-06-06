package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.RecursiveBlocking;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Harness measuring the number of independence tests (m-separation oracle queries)
 * required by iterative-deepening recursive blocking to find a separating set for
 * each sampled non-adjacent pair in a random PAG.
 *
 * <p>Exhaustive enumeration over Possible-D-SEP sets is infeasible in the PAG
 * setting due to the size of those sets, and has been omitted.</p>
 *
 * <p>Output is a CSV with one row per (rep, pair), suitable for plotting in
 * Python, followed by a summary table printed to stdout.</p>
 */
public class SepsetComparisonPagHarness {

    /**
     * Default constructor for the SepsetComparisonPagHarness class.
     *
     * This constructor initializes an instance of the SepsetComparisonPagHarness. It is primarily
     * used to facilitate execution of the application's core functionality, including the
     * generation of random PAGs (Partial Ancestral Graphs) and evaluation of separability tests.
     */
    public SepsetComparisonPagHarness() {}

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    private static final int[]  NODE_COUNTS   = {10, 20, 50};
    private static final int[]  AVG_DEGREES   = {2, 4, 6};
    private static final int    NUM_LATENTS   = 4;
    private static final int    REPS          = 20;
    private static final int    PAIRS_PER_REP = 20;
    private static final String OUTPUT_FILE   = "sepset_comparison.csv";

    // Parameters for iterative-deepening RB.
    private static final int RB_RECURSION_DEPTH = 20;  // ceiling for iterative deepening
    private static final int RB_DEPTH           = -1;  // no cap on |Z|
    private static final int RB_MAX_RADIUS      = -1;  // no radius constraint
    private static final int RB_NEAR_ENDPOINT   = 1;   // 1=near x, 2=near y, 3=near either

    // Per-pair timeout in milliseconds.
    private static final long PAIR_TIMEOUT_MS = 5_000;

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------

    /**
     * Entry point for the SepsetComparisonPagHarness application. This method performs
     * an experimental evaluation by generating random PAGs (Partial Ancestral Graphs),
     * executing separability tests on pairs of nodes, and recording results for analysis.
     * The results are output to a CSV file and summarized in the console.
     *
     * @param args Command-line arguments. Currently, no arguments are used within the application.
     *             This parameter is left available for future extensions.
     * @throws IOException If an I/O error occurs while writing to the output file.
     */
    public static void main(String[] args) throws IOException {

        // Summary accumulators: one row per (p, avgDeg) condition.
        List<SummaryRow> summaryRows = new ArrayList<>();

        try (PrintWriter out = new PrintWriter(new FileWriter(OUTPUT_FILE))) {

            out.println("nodes,avg_degree,rep,x,y,test_count,set_size,outcome");

            for (int p : NODE_COUNTS) {
                for (int avgDeg : AVG_DEGREES) {
                    int numEdges = (p * avgDeg) / 2;

                    long totalRbMs     = 0;
                    int  rbCount       = 0;
                    int  rbSucceeded   = 0;
                    int  rbUnblockable = 0;
                    int  rbNull        = 0;
                    long totalSetSize  = 0;  // accumulated over successful pairs only
                    int  totalTests    = 0;  // accumulated test counts (always 1 per success)

                    long conditionStartMs = System.currentTimeMillis();
                    System.out.printf("%n=== p=%d, avgDeg=%d ===%n", p, avgDeg);

                    for (int rep = 1; rep <= REPS; rep++) {

                        Graph      pag    = generateRandomPag(p, NUM_LATENTS, numEdges,
                                RB_RECURSION_DEPTH);
                        MsepTest   oracle = new MsepTest(pag);
                        List<Node> nodes  = pag.getNodes();

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

                            if (x == y || pag.paths().markovBlanket(x).contains(y)) continue;

                            long t1 = System.nanoTime();
                            RbResult rb = rbResult(x, y, pag, oracle);
                            long rbNs = System.nanoTime() - t1;

                            totalRbMs += rbNs / 1_000_000;
                            rbCount++;

                            String outcome;
                            if (rb.unblockable) {
                                rbUnblockable++;
                                outcome = "unblockable";
                                System.out.printf(
                                        "  [rb] UNBLOCKABLE p=%d deg=%d rep=%d (%s,%s) "
                                                + "time=%.3fms%n",
                                        p, avgDeg, rep,
                                        x.getName(), y.getName(), rbNs / 1e6);
                            } else if (rb.setSize < 0) {
                                rbNull++;
                                outcome = "indeterminate";
                                System.out.printf(
                                        "  [rb] INDETERMINATE p=%d deg=%d rep=%d (%s,%s) "
                                                + "time=%.3fms%n",
                                        p, avgDeg, rep,
                                        x.getName(), y.getName(), rbNs / 1e6);
                            } else {
                                rbSucceeded++;
                                outcome = "success";
                                totalSetSize += rb.setSize;
                                totalTests   += rb.testCount;
                                System.out.printf(
                                        "  [rb] p=%d deg=%d rep=%d (%s,%s) "
                                                + "tests=%d setSize=%d time=%.3fms%n",
                                        p, avgDeg, rep,
                                        x.getName(), y.getName(),
                                        rb.testCount, rb.setSize, rbNs / 1e6);
                            }

                            out.printf("%d,%d,%d,%s,%s,%d,%d,%s%n",
                                    p, avgDeg, rep,
                                    x.getName(), y.getName(),
                                    rb.testCount, rb.setSize,
                                    outcome);

                            pairsFound++;
                        }

                        if (rep % 10 == 0) {
                            System.out.printf(
                                    "  -- rep %3d/%d  avg=%.3fms  "
                                            + "succeeded=%d  unblockable=%d  indeterminate=%d%n",
                                    rep, REPS,
                                    rbCount > 0 ? (double) totalRbMs / rbCount : 0.0,
                                    rbSucceeded, rbUnblockable, rbNull);
                        }
                    }

                    long conditionMs = System.currentTimeMillis() - conditionStartMs;

                    System.out.printf(
                            "%nFinished p=%d avgDeg=%d in %.1fs%n"
                                    + "  RB: %d pairs  avg=%.3fms  "
                                    + "succeeded=%d  unblockable=%d  indeterminate=%d%n",
                            p, avgDeg, conditionMs / 1000.0,
                            rbCount,
                            rbCount > 0 ? (double) totalRbMs / rbCount : 0.0,
                            rbSucceeded, rbUnblockable, rbNull);

                    summaryRows.add(new SummaryRow(
                            p, avgDeg,
                            rbCount, rbSucceeded, rbUnblockable, rbNull,
                            rbCount      > 0 ? (double) totalRbMs  / rbCount      : 0.0,
                            rbSucceeded  > 0 ? (double) totalTests / rbSucceeded  : 0.0,
                            rbSucceeded  > 0 ? (double) totalSetSize / rbSucceeded : 0.0,
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

    private static RbResult rbResult(
            Node x, Node y, Graph pag, MsepTest oracle) {

        int ceiling = RB_RECURSION_DEPTH;

        try {
//            for (int recursiveDepth = 0; recursiveDepth <= ceiling; recursiveDepth++) {

                if (Thread.currentThread().isInterrupted()) {
                    return new RbResult(0, -1, false);
                }

                long deadlineMs = System.currentTimeMillis() + PAIR_TIMEOUT_MS;

                RecursiveBlocking.BlockingResult result =   
                        RecursiveBlocking.blockPathsRecursively(
                                pag, x, y,
                                Set.of(), Set.of(),
                                RB_RECURSION_DEPTH,
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
                    // RB found a graphical separator but the oracle rejected it
                    // (can happen in finite samples; here it means the PAG
                    // m-separation and the oracle disagree — treat as failure).
                    return new RbResult(1, -1, false);
                }

                if (!result.indeterminate()) {
                    // Definitive UNBLOCKABLE at this depth — no separator exists.
                    return new RbResult(0, -1, true);
                }

                // INDETERMINATE — recursion depth cap was hit; try next depth.
//            }

            // Ceiling reached without finding a separator or proving impossibility.
            return new RbResult(0, -1, false);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RbResult(0, -1, false);
        } catch (TimeoutException e) {
            return new RbResult(0, -1, false);
        }
    }

    // -----------------------------------------------------------------------
    // Graph generation
    // -----------------------------------------------------------------------

    private static Graph generateRandomPag(int numNodes, int numLatents,
                                           int numEdges, int recursiveDepth) {
        while (true) {
            System.out.println("Generating random graph...");

            Graph graph = RandomGraph.randomGraph(
                    numNodes, numLatents, numEdges, 100, 100, 100, false);
            MagToPag magToPag = new MagToPag(GraphTransforms.dagToMag(graph));
            magToPag.setRecursiveDepth(recursiveDepth);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Graph> future = executor.submit(() -> magToPag.convert(false, false));

            try {
                Graph pag = future.get(5, TimeUnit.SECONDS);
                executor.shutdownNow();
                return pag;
            } catch (TimeoutException e) {
                future.cancel(true);
                executor.shutdownNow();
                // PAG conversion timed out — try a fresh random graph.
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                executor.shutdownNow();
                throw new RuntimeException(e);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Summary table
    // -----------------------------------------------------------------------

    private static void printSummaryTable(List<SummaryRow> rows) {
        System.out.println();
        System.out.println("=".repeat(90));
        System.out.println("SUMMARY TABLE");
        System.out.println("=".repeat(90));
        System.out.printf(
                "%-6s %-8s %-7s %-10s %-12s %-13s %-10s %-10s %-10s%n",
                "p", "avgDeg", "pairs",
                "succeeded", "unblockable", "indeterminate",
                "avg ms", "avg tests", "avg |Z|");
        System.out.println("-".repeat(90));
        for (SummaryRow r : rows) {
            System.out.printf(
                    "%-6d %-8d %-7d %-10d %-12d %-13d %-10.2f %-10.2f %-10.2f%n",
                    r.p, r.avgDeg, r.totalPairs,
                    r.succeeded, r.unblockable, r.indeterminate,
                    r.avgMs, r.avgTests, r.avgSetSize);
        }
        System.out.println("=".repeat(90));
    }

    // -----------------------------------------------------------------------
    // Result and summary containers
    // -----------------------------------------------------------------------

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
        final int    totalPairs, succeeded, unblockable, indeterminate;
        final double avgMs, avgTests, avgSetSize;

        SummaryRow(int p, int avgDeg,
                   int totalPairs, int succeeded, int unblockable, int indeterminate,
                   double avgMs, double avgTests, double avgSetSize,
                   double totalSecs) {
            this.p             = p;
            this.avgDeg        = avgDeg;
            this.totalPairs    = totalPairs;
            this.succeeded     = succeeded;
            this.unblockable   = unblockable;
            this.indeterminate = indeterminate;
            this.avgMs         = avgMs;
            this.avgTests      = avgTests;
            this.avgSetSize    = avgSetSize;
        }
    }
}

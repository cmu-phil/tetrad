///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software; see LICENSE for details.                   //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness.tsc;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.RandomMim;
import edu.cmu.tetrad.graph.RandomMim.LatentGroupSpec;
import edu.cmu.tetrad.graph.RandomMim.LatentLinkMode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Top-level harness for the TSC simulation study.
 *
 * <p>Runs two separate sub-studies:
 * <ol>
 *   <li><b>Study 1 — Rank-1 MIM.</b>  Four groups, each with one latent,
 *       cluster sizes drawn uniformly from {3, 4, 5, 6}.
 *       Algorithms: TSC, BPC, FOFC.
 *       Sample sizes: 200, 500, 1000, 2000, 5000.</li>
 *   <li><b>Study 2 — Rank-2 MIM.</b>  Four groups, each with two latents
 *       (bifactor), cluster sizes drawn uniformly from {5, 6, 7, 8}.
 *       Algorithms: TSC (minRedundancy=2), FTFC.
 *       Sample sizes: 500, 1000, 2000, 5000.</li>
 * </ol>
 *
 * <p>The replication loop is parallelised over replications using a fixed
 * thread pool of size {@code Runtime.getRuntime().availableProcessors()}.
 * Each replication runs entirely in its own thread with no shared mutable
 * state.  Note: {@code RandomUtil.getInstance()} may return a shared
 * singleton; if its implementation is not thread-safe, draws across
 * replications will not be independent.  Replace with a thread-local RNG
 * if strict independence is required.
 *
 * @author josephramsey
 */
public final class SimulationStudy {

    // -----------------------------------------------------------------------
    // Configuration constants
    // -----------------------------------------------------------------------

    private static final int     NUM_GROUPS          = 4;
    private static final int[]   RANK1_SIZES         = {3, 4, 5, 6};
    private static final int[]   RANK2_SIZES         = {5, 6, 7, 8};
    private static final int[]   RANK1_SAMPLE_SIZES  = {500, 1000, 2000, 5000};
    private static final int[]   RANK2_SAMPLE_SIZES  = {500, 1000, 2000, 5000, 10000, 20000};
    private static final double  RANK1_ALPHA         = 0.01;
    private static final double  RANK2_ALPHA         = 0.01;

    // -----------------------------------------------------------------------
    // Mutable settings
    // -----------------------------------------------------------------------

    private int numReplications = 100;

    /**
     * Sets the number of replications per condition (default 100).
     *
     * @param n must be &ge; 1.
     */
    public void setNumReplications(int n) {
        if (n < 1) throw new IllegalArgumentException("numReplications must be >= 1.");
        this.numReplications = n;
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    /**
     * Runs both studies and prints results to standard output.
     *
     * @return a list of two {@link StudyResult} objects, rank-1 first.
     */
    public List<StudyResult> runAll() {
        List<StudyResult> results = new ArrayList<>();
//        results.add(runStudy1());
        results.add(runStudy2());
        return results;
    }

    // -----------------------------------------------------------------------
    // Study 1: Rank-1 MIM
    // -----------------------------------------------------------------------

    /**
     * Runs Study 1 (rank-1 MIM) and returns its results.
     */
    public StudyResult runStudy1() {
        System.out.println("=".repeat(72));
        System.out.println("STUDY 1 — Rank-1 MIM");
        System.out.println("  Groups: " + NUM_GROUPS
                + "  Cluster sizes: uniformly from " + Arrays.toString(RANK1_SIZES));
        System.out.println("  Algorithms: TSC, BPC, FOFC   alpha=" + RANK1_ALPHA
                + "  replications=" + numReplications);
        System.out.println("=".repeat(72));

        List<AlgorithmRunner> runners = List.of(
                new TscRunner(RANK1_ALPHA, 1,1, 1),
                new BpcRunner(RANK1_ALPHA),
                new FofcRunner(RANK1_ALPHA)
        );

        StudyResult result = runStudy("Rank-1 MIM", RANK1_SAMPLE_SIZES, runners, 1);
        printTable(result);
        return result;
    }

    // -----------------------------------------------------------------------
    // Study 2: Rank-2 MIM
    // -----------------------------------------------------------------------

    /**
     * Runs Study 2 (rank-2 MIM) and returns its results.
     */
    public StudyResult runStudy2() {
        System.out.println("=".repeat(72));
        System.out.println("STUDY 2 — Rank-2 MIM");
        System.out.println("  Groups: " + NUM_GROUPS
                + "  Cluster sizes: uniformly from " + Arrays.toString(RANK2_SIZES));
        System.out.println("  Algorithms: TSC (minRedundancy=2), FTFC   alpha=" + RANK2_ALPHA
                + "  replications=" + numReplications);
        System.out.println("=".repeat(72));

        List<AlgorithmRunner> runners = List.of(
                new TscRunner(RANK2_ALPHA, 1, 2, 2)
//                new FtfcRunner(RANK2_ALPHA)
        );

        StudyResult result = runStudy("Rank-2 MIM", RANK2_SAMPLE_SIZES, runners, 2);
        printTable(result);
        return result;
    }

    // -----------------------------------------------------------------------
    // Core replication loop (parallelised over replications)
    // -----------------------------------------------------------------------

    private StudyResult runStudy(
            String name,
            int[] sampleSizes,
            List<AlgorithmRunner> runners,
            int rank) {

        int numAlgs = runners.size();
        int numN    = sampleSizes.length;
        int nThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);

        // accumulators[algIdx][nIdx]
        double[][] sumP    = new double[numAlgs][numN];
        double[][] sumR    = new double[numAlgs][numN];
        double[][] sumF1   = new double[numAlgs][numN];
        int[][]    empties = new int[numAlgs][numN];

        for (int nIdx = 0; nIdx < numN; nIdx++) {
            final int n        = sampleSizes[nIdx];
            final int nIdxFin  = nIdx;
            AtomicInteger done = new AtomicInteger(0);
            System.out.printf("  n = %5d  [", n);
            System.out.flush();

            // Collect one ReplicationResult per replication in parallel
            List<Future<ReplicationResult>> futures = new ArrayList<>(numReplications);

            for (int rep = 0; rep < numReplications; rep++) {
                futures.add(pool.submit(() -> runOneReplication(rank, n, runners)));
            }

            // Accumulate results as futures complete
            for (Future<ReplicationResult> future : futures) {
                try {
                    ReplicationResult rr = future.get();
                    for (int algIdx = 0; algIdx < numAlgs; algIdx++) {
                        BestJaccardScorer.ClusterScore score = rr.scores()[algIdx];
                        if (!score.isEmpty()) {
                            sumP[algIdx][nIdxFin]  += score.precision();
                            sumR[algIdx][nIdxFin]  += score.recall();
                            sumF1[algIdx][nIdxFin] += score.f1();
                        } else {
                            // recall = 0 for empty; precision not counted
                            empties[algIdx][nIdxFin]++;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    // A replication threw — treat as all-empty for safety
                    System.err.println("Replication failed: " + e.getCause().getMessage());
                    for (int algIdx = 0; algIdx < numAlgs; algIdx++) {
                        empties[algIdx][nIdxFin]++;
                    }
                }

                int d = done.incrementAndGet();
                if (d % 10 == 0) { System.out.print("."); System.out.flush(); }
            }
            System.out.println("]");
        }

        pool.shutdown();

        // ---- Build result ----
        List<ConditionResult> conditions = new ArrayList<>();
        for (int nIdx = 0; nIdx < numN; nIdx++) {
            int n = sampleSizes[nIdx];
            List<AlgorithmSummary> algSummaries = new ArrayList<>();
            for (int algIdx = 0; algIdx < numAlgs; algIdx++) {
                int nonEmpty = numReplications - empties[algIdx][nIdx];
                double meanP  = nonEmpty > 0 ? sumP[algIdx][nIdx]  / nonEmpty          : Double.NaN;
                double meanR  =               sumR[algIdx][nIdx]  / numReplications;
                double meanF1 = nonEmpty > 0 ? sumF1[algIdx][nIdx] / nonEmpty          : Double.NaN;
                algSummaries.add(new AlgorithmSummary(
                        runners.get(algIdx).label(),
                        meanP, meanR, meanF1,
                        empties[algIdx][nIdx], numReplications));
            }
            conditions.add(new ConditionResult(n, algSummaries));
        }
        return new StudyResult(name, conditions);
    }

    // -----------------------------------------------------------------------
    // Single replication (runs entirely in one thread)
    // -----------------------------------------------------------------------

    private static ReplicationResult runOneReplication(
            int rank,
            int n,
            List<AlgorithmRunner> runners) {

        // Generate graph
        List<LatentGroupSpec> specs = randomSpecs(rank, NUM_GROUPS);
        Graph graph = RandomMim.constructRandomMim(
                specs,
                null,                       // random ~20% meta-edges
                0, 0, 0,                    // no impurities
                LatentLinkMode.CORRESPONDING
        );

        // True clusters
        List<Set<Node>> trueClusters = TrueClusterExtractor.extractClusters(graph);

        // Parameterise and simulate
        DataSet data = SemParameterizer.defaults().parameterizeAndSimulate(graph, n);

        // Run each algorithm and score
        BestJaccardScorer.ClusterScore[] scores =
                new BestJaccardScorer.ClusterScore[runners.size()];

        for (int algIdx = 0; algIdx < runners.size(); algIdx++) {
            List<Set<Node>> recovered;
            try {
                recovered = runners.get(algIdx).run(data);

                System.out.println("recovered: " + recovered);

            } catch (Exception e) {
                recovered = Collections.emptyList();
            }
            scores[algIdx] = BestJaccardScorer.score(trueClusters, recovered);
        }

        return new ReplicationResult(scores);
    }

    // -----------------------------------------------------------------------
    // Random spec generation
    // -----------------------------------------------------------------------

    private static List<LatentGroupSpec> randomSpecs(int rank, int numGroups) {
        int[] sizePool = (rank == 1) ? RANK1_SIZES : RANK2_SIZES;
        List<LatentGroupSpec> specs = new ArrayList<>(numGroups);
        for (int g = 0; g < numGroups; g++) {
            int size = sizePool[RandomUtil.getInstance().nextInt(sizePool.length)];
            specs.add(new LatentGroupSpec(1, rank, size));
        }
        return specs;
    }

    // -----------------------------------------------------------------------
    // Result printing
    // -----------------------------------------------------------------------

    private static void printTable(StudyResult result) {
        System.out.println();
        System.out.println(result.studyName());
        System.out.println();

        List<ConditionResult> conditions = result.conditions();
        if (conditions.isEmpty()) return;
        List<AlgorithmSummary> algs = conditions.get(0).algorithmSummaries();

        StringBuilder header = new StringBuilder(String.format("%-6s", "n"));
        for (AlgorithmSummary a : algs) {
            header.append(String.format("  %-23s", a.label() + " P / R / F1"));
        }
        System.out.println(header);
        System.out.println("-".repeat(header.length()));

        for (ConditionResult cond : conditions) {
            StringBuilder row = new StringBuilder(String.format("%-6d", cond.sampleSize()));
            for (AlgorithmSummary a : cond.algorithmSummaries()) {
                String cell = String.format("  %5.3f / %5.3f / %5.3f",
                        nanToMinus(a.meanPrecision()),
                        nanToMinus(a.meanRecall()),
                        nanToMinus(a.meanF1()));
                if (a.emptyCount() > 0) cell += String.format(" [%d]", a.emptyCount());
                row.append(String.format("  %-23s", cell.trim()));
            }
            System.out.println(row);
        }
        System.out.println();
    }

    private static double nanToMinus(double v) {
        return Double.isNaN(v) ? -1.0 : v;
    }

    // -----------------------------------------------------------------------
    // Internal record types
    // -----------------------------------------------------------------------

    /** Scores for all algorithms on one replication. */
    private record ReplicationResult(BestJaccardScorer.ClusterScore[] scores) {}

    // -----------------------------------------------------------------------
    // Public result types
    // -----------------------------------------------------------------------

    /**
     * Aggregated results for one complete study (one rank condition).
     *
     * @param studyName  human-readable name of the study.
     * @param conditions one {@link ConditionResult} per sample-size level.
     */
    public record StudyResult(String studyName, List<ConditionResult> conditions) {}

    /**
     * Results for one sample-size level within a study.
     *
     * @param sampleSize         the nominal sample size {@code n}.
     * @param algorithmSummaries one summary per algorithm.
     */
    public record ConditionResult(int sampleSize, List<AlgorithmSummary> algorithmSummaries) {}

    /**
     * Mean metrics for one algorithm at one sample-size level.
     * Precision and F1 are averaged over non-empty replications only.
     * Recall is averaged over all replications (zero for empty ones).
     * {@code emptyCount} is the number of replications where the algorithm
     * returned no clusters.
     *
     * @param label         algorithm label.
     * @param meanPrecision mean best-Jaccard precision (non-empty reps only).
     * @param meanRecall    mean best-Jaccard recall (all reps).
     * @param meanF1        mean F1 (non-empty reps only).
     * @param emptyCount    replications returning no clusters.
     * @param totalReps     total replications for this condition.
     */
    public record AlgorithmSummary(
            String label,
            double meanPrecision,
            double meanRecall,
            double meanF1,
            int    emptyCount,
            int    totalReps) {}

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------

    /**
     * Runs both studies with default settings.
     * Optional integer argument sets the number of replications (default 100).
     */
    public static void main(String[] args) {
        SimulationStudy study = new SimulationStudy();
        if (args.length > 0) {
            try {
                study.setNumReplications(Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                System.err.println("Usage: SimulationStudy [numReplications]");
                System.exit(1);
            }
        }
        study.runAll();
    }
}

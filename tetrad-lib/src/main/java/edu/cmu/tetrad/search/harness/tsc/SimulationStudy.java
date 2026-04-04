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

import java.text.ParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Top-level harness for the TSC simulation study.
 *
 * <p>Runs two separate sub-studies:
 * <ol>
 *   <li><b>Study 1 — Rank-1 MIM.</b>  Four groups, each with one latent.
 *       Algorithms: TSC, BPC, FOFC.</li>
 *   <li><b>Study 2 — Rank-2 MIM.</b>  Four groups, each with two latents
 *       (bifactor).  Algorithms: TSC, FTFC.</li>
 * </ol>
 *
 * <h2>F1 computation</h2>
 * <p>Precision is averaged over non-empty replications only (replications
 * where the algorithm returned at least one cluster).  Recall is averaged
 * over <em>all</em> replications, with empty replications contributing
 * recall = 0.  The reported F1 is computed from these two means:
 * <pre>
 *   F1 = 2 * meanPrecision * meanRecall / (meanPrecision + meanRecall)
 * </pre>
 * This correctly penalises high empty-count algorithms whose per-replication
 * precision looks good but whose overall recall is near zero.
 *
 * @author josephramsey
 */
public final class SimulationStudy {

    /**
     * Constructs a new instance of the {@code SimulationStudy} class.
     * This class is designed to perform statistical simulations involving
     * multiple groups and conditions, evaluating rank-1 and rank-2 measurement
     * models (MIM). It provides methods to configure replication settings
     * and run simulations for each study or both studies collectively.
     *
     * By default, the number of replications for each condition is set to 100.
     * Users can modify this using the {@link #setNumReplications(int)} method.
     * The simulation includes rank-specific configurations for sample sizes,
     * significance levels, and analysis procedures.
     */
    public SimulationStudy() {

    }

    // -----------------------------------------------------------------------
    // Configuration constants
    // -----------------------------------------------------------------------

    private static final int     NUM_GROUPS          = 4;
    private static final int[]   RANK1_SIZES         = {5, 6};
    private static final int[]   RANK2_SIZES         = {7, 8};
    private static final int[]   RANK1_SAMPLE_SIZES  = {500, 1000, 2000, 5000, 10000, 20000};
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
     * @return the results of Study 1.
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
                new TscRunner(RANK1_ALPHA, 0, 1, 1),
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
     * @return the results of Study 2.
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
                new TscRunner(RANK2_ALPHA, 0, 2, 2),
                new FtfcRunner(RANK2_ALPHA)
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

        int numAlgs  = runners.size();
        int numN     = sampleSizes.length;
        int nThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);

        // Accumulators: sumP and sumR accumulated per the rules described in
        // the class Javadoc.  F1 is derived from meanP and meanR at report time.
        double[][] sumP    = new double[numAlgs][numN];
        double[][] sumR    = new double[numAlgs][numN];
        int[][]    empties = new int[numAlgs][numN];

        for (int nIdx = 0; nIdx < numN; nIdx++) {
            final int n       = sampleSizes[nIdx];
            final int nIdxFin = nIdx;
            AtomicInteger done = new AtomicInteger(0);
            System.out.printf("  n = %5d  [", n);
            System.out.flush();

            List<Future<ReplicationResult>> futures = new ArrayList<>(numReplications);
            for (int rep = 0; rep < numReplications; rep++) {
                futures.add(pool.submit(() -> runOneReplication(rank, n, runners)));
            }

            for (Future<ReplicationResult> future : futures) {
                try {
                    ReplicationResult rr = future.get();
                    for (int algIdx = 0; algIdx < numAlgs; algIdx++) {
                        BestJaccardScorer.ClusterScore score = rr.scores()[algIdx];

                        if (!score.isEmpty()) {
                            // Precision accumulated over non-empty replications only.
                            sumP[algIdx][nIdxFin] += score.precision();
                        } else {
                            empties[algIdx][nIdxFin]++;
                        }

                        // Recall accumulated over ALL replications (zero for empties).
                        sumR[algIdx][nIdxFin] += score.recall();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    System.err.println("Replication failed: " + e.getCause().getMessage());
                    for (int algIdx = 0; algIdx < numAlgs; algIdx++) {
                        empties[algIdx][nIdxFin]++;
                        // recall gets 0 implicitly (sumR not incremented)
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

                // meanP: over non-empty replications only.
                double meanP = nonEmpty > 0
                        ? sumP[algIdx][nIdx] / nonEmpty
                        : Double.NaN;

                // meanR: over all replications (empties contribute 0).
                double meanR = sumR[algIdx][nIdx] / numReplications;

                // F1: derived from meanP and meanR so it correctly reflects
                // both the quality of found clusters and the empty-run rate.
                double meanF1;
                if (Double.isNaN(meanP) || Double.isNaN(meanR)) {
                    meanF1 = Double.NaN;
                } else {
                    double denom = meanP + meanR;
                    meanF1 = (denom == 0.0) ? 0.0 : 2.0 * meanP * meanR / denom;
                }

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
    // Single replication
    // -----------------------------------------------------------------------

    private static ReplicationResult runOneReplication(
            int rank,
            int n,
            List<AlgorithmRunner> runners) {

        List<LatentGroupSpec> specs = randomSpecs(rank, NUM_GROUPS);
        Graph graph = RandomMim.constructRandomMim(
                specs,
                NUM_GROUPS,                         // fixed meta-edge count
                0, 0, 0,
                LatentLinkMode.CORRESPONDING
        );

        List<Set<Node>> trueClusters = TrueClusterExtractor.extractClusters(graph);
        DataSet data = null;
        try {
            data = SemParameterizer.defaults().parameterizeAndSimulate(graph, n);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        BestJaccardScorer.ClusterScore[] scores =
                new BestJaccardScorer.ClusterScore[runners.size()];

        for (int algIdx = 0; algIdx < runners.size(); algIdx++) {
            List<Set<Node>> recovered;
            try {
                recovered = runners.get(algIdx).run(data);
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

        // Header
        StringBuilder header = new StringBuilder(String.format("%-6s", "n"));
        for (AlgorithmSummary a : algs) {
            header.append(String.format("  %-23s", a.label() + " P / R / F1"));
        }
        System.out.println(header);
        System.out.println("-".repeat(header.length()));

        for (ConditionResult cond : conditions) {
            StringBuilder row = new StringBuilder(String.format("%-6d", cond.sampleSize()));
            for (AlgorithmSummary a : cond.algorithmSummaries()) {
                String cell = String.format("%5.3f / %5.3f / %5.3f",
                        nanToMinus(a.meanPrecision()),
                        nanToMinus(a.meanRecall()),
                        nanToMinus(a.meanF1()));
                if (a.emptyCount() > 0) cell += String.format(" [%d]", a.emptyCount());
                row.append(String.format("  %-23s", cell));
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
     *
     * <p><b>Averaging conventions:</b>
     * <ul>
     *   <li>{@code meanPrecision} — averaged over non-empty replications only.</li>
     *   <li>{@code meanRecall} — averaged over all replications (empty = 0).</li>
     *   <li>{@code meanF1} — derived as {@code 2*P*R/(P+R)} from the two means
     *       above, so it correctly penalises high empty-count algorithms.</li>
     * </ul>
     *
     * @param label         algorithm label, e.g. {@code "TSC"}.
     * @param meanPrecision mean best-Jaccard precision (non-empty reps only).
     * @param meanRecall    mean best-Jaccard recall (all reps).
     * @param meanF1        F1 derived from meanPrecision and meanRecall.
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
     * @param args ignored.
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

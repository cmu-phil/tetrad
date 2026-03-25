///////////////////////////////////////////////////////////////////////////////
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software; see LICENSE for details.                   //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness.tsbi;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.graph.RandomMim.LatentGroupSpec;
import edu.cmu.tetrad.graph.RandomMim.LatentLinkMode;
import edu.cmu.tetrad.search.harness.tsc.SemParameterizer;
import edu.cmu.tetrad.search.harness.tsc.TrueClusterExtractor;
import edu.cmu.tetrad.search.test.TrekSeparationBlocksIndependence;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulation harness for the TSBI (Trek-Separation Block Independence) test.
 *
 * <p>Evaluates the structural-model recovery of the PC algorithm when its
 * conditional independence oracle is {@link TrekSeparationBlocksIndependence}.
 * The true cluster partition is supplied directly to PC+TSBI on each replication,
 * so the results measure structural-search performance in isolation from
 * cluster-recovery performance.
 *
 * <h2>Study design</h2>
 * <ul>
 *   <li><b>Model.</b>  Rank-1 MIM with {@value #NUM_GROUPS} clusters,
 *       {@value #INDICATORS_PER_CLUSTER} indicators per cluster, and
 *       {@value #NUM_META_EDGES} structural (latent-to-latent) edges, generated
 *       by {@code RandomMim.constructRandomMim}.  One independent parameter draw
 *       is made per replication (edge coefficients from {@code U(0.2, 1.2)},
 *       error variances from {@code U(1, 3)}).</li>
 *   <li><b>Algorithm.</b>  PC with TSBI as its CI test, at significance level
 *       {@value #ALPHA}.  The true clusters are passed directly as the
 *       {@code BlockSpec} to TSBI.</li>
 *   <li><b>Sample sizes.</b>  500, 1000, 2000, 5000, 10000, 20000.</li>
 *   <li><b>Replications.</b>  100 per cell (configurable).</li>
 * </ul>
 *
 * <h2>Metrics</h2>
 * <p>Adjacency and arrowhead precision, recall, and F1 are computed per
 * replication by {@link StructuralGraphScorer} and averaged over all
 * replications.  Because structural-graph recovery can degenerate (trivial
 * empty or complete recovered graphs) but not be "empty" in the same discrete
 * sense as cluster-finding, all replications contribute equally to all averages.
 *
 * <h2>Output</h2>
 * <p>Results are printed to standard output as a LaTeX-ready table.
 *
 * @author josephramsey
 * @see TsbiRunner
 * @see StructuralGraphScorer
 */
public final class SimulationStudyTsbi {

    // -----------------------------------------------------------------------
    // Study configuration
    // -----------------------------------------------------------------------

    /** Number of latent clusters in each generated MIM. */
    private static final int    NUM_GROUPS              = 10;

    /** Number of observed indicators per cluster. */
    private static final int    INDICATORS_PER_CLUSTER  = 5;

    /** Number of directed structural edges among the latent clusters. */
    private static final int    NUM_META_EDGES          = 10;

    /** Significance level for the Wilks rank test inside TSBI. */
    private static final double ALPHA                   = 0.01;

    /** Sample sizes at which the study is evaluated. */
    private static final int[]  SAMPLE_SIZES            = {500, 1000, 2000, 5000, 10000, 20000};

    // -----------------------------------------------------------------------
    // Mutable settings
    // -----------------------------------------------------------------------

    private int numReplications = 100;

    /**
     * Sets the number of replications per sample-size condition.
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
     * Runs the study and prints results to standard output.
     *
     * @return the {@link StudyResult} containing all condition-level summaries.
     */
    public StudyResult run() {
        System.out.println("=".repeat(72));
        System.out.println("TSBI Simulation Study — Rank-1 MIM, Structural Graph Recovery");
        System.out.printf("  Clusters: %d   Indicators/cluster: %d   Structural edges: %d%n",
                NUM_GROUPS, INDICATORS_PER_CLUSTER, NUM_META_EDGES);
        System.out.printf("  Algorithm: PC + TSBI (true clusters supplied)   alpha=%.2f%n",
                ALPHA);
        System.out.printf("  Replications: %d%n", numReplications);
        System.out.println("=".repeat(72));

        int numN = SAMPLE_SIZES.length;
        int nThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);

        // Accumulators — all replications contribute equally.
        double[] sumAdjP = new double[numN];
        double[] sumAdjR = new double[numN];
        double[] sumAhdP = new double[numN];
        double[] sumAhdR = new double[numN];
        int[]    failed  = new int[numN];

        for (int nIdx = 0; nIdx < numN; nIdx++) {
            final int n       = SAMPLE_SIZES[nIdx];
            final int nIdxFin = nIdx;

            AtomicInteger done = new AtomicInteger(0);
            System.out.printf("  n = %6d  [", n);
            System.out.flush();

            List<Future<ReplicationResult>> futures = new ArrayList<>(numReplications);
            for (int rep = 0; rep < numReplications; rep++) {
                futures.add(pool.submit(() -> runOneReplication(n)));
            }

            for (Future<ReplicationResult> future : futures) {
                try {
                    ReplicationResult rr = future.get();
                    StructuralGraphScorer.GraphScore gs = rr.score();
                    sumAdjP[nIdxFin] += nanToZero(gs.adjPrecision());
                    sumAdjR[nIdxFin] += nanToZero(gs.adjRecall());
                    sumAhdP[nIdxFin] += nanToZero(gs.ahdPrecision());
                    sumAhdR[nIdxFin] += nanToZero(gs.ahdRecall());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failed[nIdxFin]++;
                } catch (ExecutionException e) {
                    System.err.println("Replication failed: " + e.getCause().getMessage());
                    failed[nIdxFin]++;
                }
                int d = done.incrementAndGet();
                if (d % 10 == 0) { System.out.print("."); System.out.flush(); }
            }
            System.out.println("]");
        }

        pool.shutdown();

        // ---- Build result ----
        List<ConditionResult> conditions = new ArrayList<>(numN);
        for (int nIdx = 0; nIdx < numN; nIdx++) {
            int good = numReplications - failed[nIdx];
            if (good == 0) good = 1;  // avoid divide-by-zero in pathological cases

            double meanAdjP = sumAdjP[nIdx] / good;
            double meanAdjR = sumAdjR[nIdx] / good;
            double meanAhdP = sumAhdP[nIdx] / good;
            double meanAhdR = sumAhdR[nIdx] / good;

            conditions.add(new ConditionResult(
                    SAMPLE_SIZES[nIdx],
                    meanAdjP, meanAdjR, f1(meanAdjP, meanAdjR),
                    meanAhdP, meanAhdR, f1(meanAhdP, meanAhdR),
                    failed[nIdx], numReplications));
        }

        StudyResult result = new StudyResult(conditions);
        printTable(result);
        return result;
    }

    // -----------------------------------------------------------------------
    // Single replication
    // -----------------------------------------------------------------------

    private static ReplicationResult runOneReplication(int n) {

        // ---- Generate random rank-1 MIM ----
        List<LatentGroupSpec> specs = new ArrayList<>(NUM_GROUPS);
        for (int g = 0; g < NUM_GROUPS; g++) {
            specs.add(new LatentGroupSpec(1, 1, INDICATORS_PER_CLUSTER));
        }
        Graph mim = RandomMim.constructRandomMim(
                specs,
                NUM_META_EDGES,
                0, 0, 0,
                LatentLinkMode.CORRESPONDING);

        // ---- True clusters and latent leaders ----
        List<Set<Node>>   trueClusters = TrueClusterExtractor.extractClusters(mim);
        List<Node>            trueLeaders  = TrueClusterExtractor.extractLatentLeaders(mim);

        // ---- Simulate data ----
        DataSet data = SemParameterizer.defaults().parameterizeAndSimulate(mim, n);

        // ---- True structural graph (latent-to-latent subgraph of MIM) ----
//        Graph trueStructural = StructuralGraphScorer.extractTrueStructural(mim);
        Graph trueStructural = GraphTransforms.dagToCpdag(
                StructuralGraphScorer.extractTrueStructural(mim));

        // ---- Run PC + TSBI with true clusters ----
        Graph recovered;
        try {
            recovered = new TsbiRunner(ALPHA, n).run(data, trueClusters, trueLeaders);
        } catch (Exception e) {
            // On any failure return a zero-score replication.
            throw new RuntimeException("PC+TSBI failed: " + e.getMessage(), e);
        }

        // ---- Score structural graph recovery ----
        StructuralGraphScorer.GraphScore score =
                StructuralGraphScorer.score(trueStructural, recovered);

        return new ReplicationResult(score);
    }

    // -----------------------------------------------------------------------
    // Printing
    // -----------------------------------------------------------------------

    private static void printTable(StudyResult result) {
        System.out.println();
        System.out.println("Results: PC + TSBI (true clusters)");
        System.out.println();
        System.out.printf("%-8s  %-6s %-6s %-6s  %-6s %-6s %-6s%n",
                "n", "AdjP", "AdjR", "AdjF1", "AhdP", "AhdR", "AhdF1");
        System.out.println("-".repeat(54));

        for (ConditionResult c : result.conditions()) {
            System.out.printf("%-8d  %.4f %.4f %.4f  %.4f %.4f %.4f%n",
                    c.sampleSize(),
                    c.meanAdjPrecision(), c.meanAdjRecall(),    c.meanAdjF1(),
                    c.meanAhdPrecision(), c.meanAhdRecall(),    c.meanAhdF1());
        }
        System.out.println();
    }

    // -----------------------------------------------------------------------
    // Small helpers
    // -----------------------------------------------------------------------

    private static double f1(double p, double r) {
        if (Double.isNaN(p) || Double.isNaN(r)) return Double.NaN;
        double d = p + r;
        return d == 0.0 ? 0.0 : 2.0 * p * r / d;
    }

    private static double nanToZero(double v) {
        return Double.isNaN(v) ? 0.0 : v;
    }

    // -----------------------------------------------------------------------
    // Internal record types
    // -----------------------------------------------------------------------

    private record ReplicationResult(StructuralGraphScorer.GraphScore score) {}

    // -----------------------------------------------------------------------
    // Public result types
    // -----------------------------------------------------------------------

    /**
     * Aggregated results for the complete TSBI study.
     *
     * @param conditions one {@link ConditionResult} per sample-size level.
     */
    public record StudyResult(List<ConditionResult> conditions) {}

    /**
     * Mean metrics for one sample-size level.
     *
     * @param sampleSize        nominal sample size {@code n}.
     * @param meanAdjPrecision  mean adjacency precision over replications.
     * @param meanAdjRecall     mean adjacency recall over replications.
     * @param meanAdjF1         F1 derived from mean precision and recall.
     * @param meanAhdPrecision  mean arrowhead precision over replications.
     * @param meanAhdRecall     mean arrowhead recall over replications.
     * @param meanAhdF1         F1 derived from mean precision and recall.
     * @param failedCount       replications that threw an exception.
     * @param totalReps         total replications attempted.
     */
    public record ConditionResult(
            int    sampleSize,
            double meanAdjPrecision,
            double meanAdjRecall,
            double meanAdjF1,
            double meanAhdPrecision,
            double meanAhdRecall,
            double meanAhdF1,
            int    failedCount,
            int    totalReps) {}

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------

    /**
     * Runs the study with default settings.
     * Optional integer argument overrides the number of replications (default 100).
     */
    public static void main(String[] args) {
        SimulationStudyTsbi study = new SimulationStudyTsbi();
        if (args.length > 0) {
            try {
                study.setNumReplications(Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                System.err.println("Usage: SimulationStudyTsbi [numReplications]");
                System.exit(1);
            }
        }
        study.run();
    }
}

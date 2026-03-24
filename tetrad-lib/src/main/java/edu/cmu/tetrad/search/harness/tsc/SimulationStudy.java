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

/**
 * Top-level harness for the TSC simulation study.
 *
 * <p>Runs two separate sub-studies:
 * <ol>
 *   <li><b>Study 1 — Rank-1 MIM.</b>  Four groups, each with one latent,
 *       cluster sizes drawn uniformly from {2, 3, 4, 5}.
 *       Algorithms: TSC, BPC, FOFC.
 *       Sample sizes: 200, 500, 1000, 2000, 5000.</li>
 *   <li><b>Study 2 — Rank-2 MIM.</b>  Four groups, each with two latents
 *       (bifactor), cluster sizes drawn uniformly from {3, 4, 5, 6, 7}.
 *       Algorithms: TSC, FTFC.
 *       Sample sizes: 500, 1000, 2000, 5000.</li>
 * </ol>
 *
 * <p>Each condition is replicated {@code numReplications} times (default 100).
 * For each replication a fresh MIM graph is generated, independently
 * parameterised from U(0.2, 1.2) coefficients and U(0.5, 1.5) variances,
 * and data are simulated by ancestral sampling.  Precision, recall, and F1
 * are computed via best-Jaccard matching against the true clusters.
 *
 * <p>Results are printed as plain-text tables to standard output and also
 * returned as {@link StudyResult} objects for programmatic access.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   SimulationStudy study = new SimulationStudy();
 *   study.setNumReplications(100);
 *   study.runAll();
 * }</pre>
 *
 * @author josephramsey
 */
public final class SimulationStudy {

    // -----------------------------------------------------------------------
    // Configuration constants
    // -----------------------------------------------------------------------

    private static final int   NUM_GROUPS       = 4;
    private static final int[] RANK1_SIZES      = {2, 3, 4, 5};
    private static final int[] RANK2_SIZES      = {3, 4, 5, 6, 7};
    private static final int[] RANK1_SAMPLE_SIZES = {200, 500, 1000, 2000, 5000};
    private static final int[] RANK2_SAMPLE_SIZES = {500, 1000, 2000, 5000};
    private static final double ALPHA            = 0.01;

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
        results.add(runStudy1());
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
        System.out.println("  Algorithms: TSC, BPC, FOFC   alpha=" + ALPHA
                + "  replications=" + numReplications);
        System.out.println("=".repeat(72));

        List<AlgorithmRunner> runners = List.of(
                new TscRunner(ALPHA, 1),
                new BpcRunner(ALPHA),
                new FofcRunner(ALPHA)
        );

        StudyResult result = runStudy(
                "Rank-1 MIM",
                RANK1_SAMPLE_SIZES,
                runners,
                /* rank */ 1
        );

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
        System.out.println("  Algorithms: TSC, FTFC   alpha=" + ALPHA
                + "  replications=" + numReplications);
        System.out.println("=".repeat(72));

        List<AlgorithmRunner> runners = List.of(
                new TscRunner(ALPHA, 2),
                new FtfcRunner(ALPHA)
        );

        StudyResult result = runStudy(
                "Rank-2 MIM",
                RANK2_SAMPLE_SIZES,
                runners,
                /* rank */ 2
        );

        printTable(result);
        return result;
    }

    // -----------------------------------------------------------------------
    // Core replication loop
    // -----------------------------------------------------------------------

    private StudyResult runStudy(
            String name,
            int[] sampleSizes,
            List<AlgorithmRunner> runners,
            int rank) {

        int numAlgs = runners.size();
        int numN    = sampleSizes.length;

        // accumulators[algIdx][nIdx] -> running sums of {P, R, F1, emptyCount}
        double[][] sumP     = new double[numAlgs][numN];
        double[][] sumR     = new double[numAlgs][numN];
        double[][] sumF1    = new double[numAlgs][numN];
        int[][]    empties  = new int[numAlgs][numN];

        for (int nIdx = 0; nIdx < numN; nIdx++) {
            int n = sampleSizes[nIdx];
            System.out.printf("  n = %5d  [", n);

            for (int rep = 0; rep < numReplications; rep++) {
                // ---- Generate graph ----
                List<LatentGroupSpec> specs = randomSpecs(rank, NUM_GROUPS);
                Graph graph = RandomMim.constructRandomMim(
                        specs,
                        null,                          // random ~20% meta-edges
                        0, 0, 0,                       // no impurities
                        LatentLinkMode.CORRESPONDING
                );

                // ---- True clusters ----
                List<Set<Node>> trueClusters = TrueClusterExtractor.extractClusters(graph);

                // ---- Parameterise and simulate ----
                DataSet data = SemParameterizer.defaults().parameterizeAndSimulate(graph, n);

                // ---- Run each algorithm ----
                for (int algIdx = 0; algIdx < numAlgs; algIdx++) {
                    AlgorithmRunner runner = runners.get(algIdx);
                    List<Set<Node>> recovered;
                    try {
                        recovered = runner.run(data);
                    } catch (Exception e) {
                        // Treat any exception as an empty result so one
                        // bad replication does not abort the whole study.
                        recovered = Collections.emptyList();
                    }

                    BestJaccardScorer.ClusterScore score =
                            BestJaccardScorer.score(trueClusters, recovered);

                    if (!score.isEmpty()) {
                        sumP[algIdx][nIdx]  += score.precision();
                        sumR[algIdx][nIdx]  += score.recall();
                        sumF1[algIdx][nIdx] += score.f1();
                    } else {
                        // Empty result: recall = 0, precision convention = 1,
                        // but we record as a distinct empty-count rather than
                        // inflating the precision mean.
                        sumR[algIdx][nIdx]  += 0.0;
                        empties[algIdx][nIdx]++;
                    }
                }

                if ((rep + 1) % 10 == 0) System.out.print(".");
            }
            System.out.println("]");
        }

        // ---- Build result ----
        List<ConditionResult> conditions = new ArrayList<>();
        for (int nIdx = 0; nIdx < numN; nIdx++) {
            int n = sampleSizes[nIdx];
            List<AlgorithmSummary> algSummaries = new ArrayList<>();
            for (int algIdx = 0; algIdx < numAlgs; algIdx++) {
                int nonEmpty = numReplications - empties[algIdx][nIdx];
                double meanP  = nonEmpty > 0
                        ? sumP[algIdx][nIdx] / nonEmpty   : Double.NaN;
                double meanR  = sumR[algIdx][nIdx] / numReplications;
                double meanF1 = nonEmpty > 0
                        ? sumF1[algIdx][nIdx] / nonEmpty  : Double.NaN;
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
    // Random spec generation
    // -----------------------------------------------------------------------

    /**
     * Generates a list of {@link LatentGroupSpec} objects for {@code numGroups}
     * groups, each with the given {@code rank} and a cluster size drawn uniformly
     * from the appropriate size array.
     */
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

        // Header
        List<ConditionResult> conditions = result.conditions();
        if (conditions.isEmpty()) return;
        List<AlgorithmSummary> algs = conditions.get(0).algorithmSummaries();

        // Column headers
        StringBuilder header = new StringBuilder(String.format("%-6s", "n"));
        for (AlgorithmSummary a : algs) {
            header.append(String.format("  %-19s", a.label() + " P / R / F1"));
        }
        System.out.println(header);
        System.out.println("-".repeat(header.length()));

        for (ConditionResult cond : conditions) {
            StringBuilder row = new StringBuilder(String.format("%-6d", cond.sampleSize()));
            for (AlgorithmSummary a : cond.algorithmSummaries()) {
                row.append(String.format("  %5.3f / %5.3f / %5.3f",
                        nanToMinus(a.meanPrecision()),
                        nanToMinus(a.meanRecall()),
                        nanToMinus(a.meanF1())));
                if (a.emptyCount() > 0) {
                    row.append(String.format(" [%d empty]", a.emptyCount()));
                }
            }
            System.out.println(row);
        }
        System.out.println();
    }

    private static double nanToMinus(double v) {
        return Double.isNaN(v) ? -1.0 : v;
    }

    // -----------------------------------------------------------------------
    // Result types
    // -----------------------------------------------------------------------

    /**
     * Aggregated results for one complete study (one rank condition).
     *
     * @param studyName  human-readable name of the study.
     * @param conditions one {@link ConditionResult} per sample-size level.
     */
    public record StudyResult(
            String studyName,
            List<ConditionResult> conditions) {
    }

    /**
     * Results for one sample-size level within a study.
     *
     * @param sampleSize         the nominal sample size {@code n}.
     * @param algorithmSummaries one summary per algorithm, in the order they
     *                           were passed to the study.
     */
    public record ConditionResult(
            int sampleSize,
            List<AlgorithmSummary> algorithmSummaries) {
    }

    /**
     * Mean metrics for one algorithm at one sample-size level, averaged over
     * all replications.
     *
     * <p>Precision and F1 are averaged only over non-empty replications
     * (replications where the algorithm returned at least one cluster).
     * Recall is averaged over all replications (zero for empty ones).
     * {@code emptyCount} records how many replications returned nothing,
     * which is a distinct failure mode worth tracking separately.
     *
     * @param label         algorithm label, e.g. {@code "TSC"}.
     * @param meanPrecision mean best-Jaccard precision (non-empty reps only).
     * @param meanRecall    mean best-Jaccard recall (all reps).
     * @param meanF1        mean F1 (non-empty reps only).
     * @param emptyCount    number of replications where the algorithm returned
     *                      no clusters.
     * @param totalReps     total number of replications for this condition.
     */
    public record AlgorithmSummary(
            String label,
            double meanPrecision,
            double meanRecall,
            double meanF1,
            int    emptyCount,
            int    totalReps) {
    }

    // -----------------------------------------------------------------------
    // Main method for direct execution
    // -----------------------------------------------------------------------

    /**
     * Runs both studies with default settings and prints results to standard
     * output.  Accepts an optional integer argument for the number of
     * replications (default 100).
     *
     * <pre>{@code
     *   java edu.cmu.tetrad.search.harness.tsc.SimulationStudy
     *   java edu.cmu.tetrad.search.harness.tsc.SimulationStudy 20
     * }</pre>
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

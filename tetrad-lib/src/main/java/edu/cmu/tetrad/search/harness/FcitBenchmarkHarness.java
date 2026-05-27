///////////////////////////////////////////////////////////////////////////////
// FcitBenchmarkHarness.java                                                 //
//                                                                           //
// Serial benchmark harness for comparing seven causal-discovery algorithms: //
//   1 LV-Heuristic   2 FCIT   3 BOSS-FCI                                   //
//   4 GRaSP-FCI      5 GFCI   6 ICD   7 FCI                                //
//                                                                           //
// Runs every (condition × algorithm × run) sequentially in a single JVM.   //
// Wall-clock time is measured per algorithm run via System.nanoTime().      //
// CovarianceMatrix is constructed once per run (outside timing) and shared  //
// across all 7 algorithms for that run.                                     //
//                                                                           //
// ICD (algorithm 6) is run on a separate thread with a 30-second timeout.  //
// If any single run of ICD exceeds the timeout, ALL stats for that run are  //
// recorded as NaN (written as "", which pandas reads as NaN). Furthermore,  //
// once ICD times out on any run within a condition, ALL subsequent runs     //
// within that condition are also skipped and recorded as missing — ICD is   //
// considered permanently too slow for that condition.                       //
//                                                                           //
// Usage:                                                                    //
//   java -Xmx8g -cp tetrad-current.jar                                      //
//        edu.cmu.tetrad.search.harness.FcitBenchmarkHarness                 //
//        [--out results.tsv] [--numRuns 20] [--seed 42]                     //
//        [--timeout 30]                                                      //
//                                                                           //
// Stat semantics                                                             //
// ──────────────                                                             //
// DAG-based  (true reference = true DAG, latent nodes tagged LATENT):       //
//   *->-Prec   TrueDagPrecisionArrow                                        //
//   -->-Prec   TrueDagPrecisionTails                                        //
//   <->-Lat    BidirectedLatentPrecision  (* when numLatents == 0)          //
//                                                                           //
// PAG-based  (true reference = true PAG from dagToMag → MagToPag):         //
//   AHP    ArrowheadPrecision                                               //
//   AHPC   ArrowheadPrecisionCommonEdges                                    //
//   AHR    ArrowheadRecall                                                  //
//   AHRC   ArrowheadRecallCommonEdges                                       //
//   AP     AdjacencyPrecision                                               //
//   AR     AdjacencyRecall                                                  //
//   PAG    LegalPag (fraction of runs where estimated PAG == true PAG)      //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The FcitBenchmarkHarness class serves as a benchmarking tool for evaluating various causal
 * inference algorithms. It includes methods for generating random directed acyclic graphs
 * (DAGs), simulating data from those DAGs, running different algorithms, and computing
 * performance metrics based on the estimated graphs.
 */
public class FcitBenchmarkHarness {

    // ────────────────────────────────────────────────────────────────────────
    // Constants
    // ────────────────────────────────────────────────────────────────────────

    private static final int    NUM_ALGS         = 7;
    private static final double DEFAULT_ALPHA    = 0.01;
    private static final double PENALTY_DISCOUNT = 2.0;
    private static final int    FCIT_DEPTH       = 3;

    /** Algorithm index for ICD — the only one subject to the timeout. */
    private static final int    ICD_ALG_INDEX    = 5; // 0-based; alg #6 in 1-based output

    /** Missing-value token that pandas reads as NaN when passed to read_csv. */
    private static final String MISSING          = "";

    private static final String HEADER = String.join("\t",
            "Alg", "avgDegree", "numLatents", "numMeasures", "numRuns", "sampleSize",
            "*->-Prec", "-->-Prec", "<->-Lat-Prec",
            "AHP", "AHPC", "AHR", "AHRC",
            "AP", "AR",
            "E-Wall", "PAG"
    );

    // ────────────────────────────────────────────────────────────────────────
    // Timeout (seconds) — overridable via --timeout on the command line
    // ────────────────────────────────────────────────────────────────────────

    private static int timeoutSeconds = 30;

    /**
     * Default constructor for the FcitBenchmarkHarness class.
     */
    public FcitBenchmarkHarness() {}

    // ────────────────────────────────────────────────────────────────────────
    // Simulation grid
    // ────────────────────────────────────────────────────────────────────────

    private static List<int[]> buildConditions() {
        List<int[]> conds = new ArrayList<>();
        int[] degrees      = {2, 4, 6};
        int[] latentCounts = {0, 3, 6};
        int   measures     = 20;
        int[] sampleSizes  = {200, 500, 1000, 5000};
        for (int deg : degrees)
            for (int nlat : latentCounts)
                for (int n : sampleSizes)
                    conds.add(new int[]{deg, nlat, measures, n});
        return conds;
    }

    // ────────────────────────────────────────────────────────────────────────
    // main
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Executes the main benchmark logic for evaluating graph structure learning algorithms
     * under various conditions with controlled parameters. Outputs results to a file in TSV format.
     *
     * @param args Command-line arguments. Supported options:
     *             --out &lt;path&gt;: Specifies the output file path. Default is "fcit_benchmark_results.tsv".
     *             --numRuns &lt;int&gt;: Number of benchmark runs per condition. Default is 20.
     *             --seed &lt;long&gt;: Initial random seed for reproducibility. Default is 42.
     *             --timeout &lt;int&gt;: Per-run timeout in seconds for ICD. Default is 30.
     *
     * @throws Exception If an error occurs during file I/O, parsing, or algorithm execution.
     */
    public static void main(String[] args) throws Exception {

        String outPath = "fcit_benchmark_results.tsv";
        int    numRuns = 20;
        long   seed    = 42L;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--out"     -> outPath        = args[++i];
                case "--numRuns" -> numRuns        = Integer.parseInt(args[++i]);
                case "--seed"    -> seed           = Long.parseLong(args[++i]);
                case "--timeout" -> timeoutSeconds = Integer.parseInt(args[++i]);
            }
        }

        System.out.printf("ICD timeout per run: %d seconds%n", timeoutSeconds);

        // Stat objects — stateless, reused across all runs
        Statistic dagArrowPrec = new TrueDagPrecisionArrow();
        Statistic dagDirPrec   = new TrueDagPrecisionTails();
        Statistic dagBiDir     = new BidirectedLatentPrecision();
        Statistic pagAHP       = new ArrowheadPrecision();
        Statistic pagAHPC      = new ArrowheadPrecisionCommonEdges();
        Statistic pagAHR       = new ArrowheadRecall();
        Statistic pagAHRC      = new ArrowheadRecallCommonEdges();
        Statistic pagAP        = new AdjacencyPrecision();
        Statistic pagAR        = new AdjacencyRecall();
        Statistic pagExact     = new LegalPag();

        List<int[]> conditions = buildConditions();

        // Single-thread executor reused for all ICD timeout calls
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (PrintWriter out = new PrintWriter(
                Files.newBufferedWriter(Path.of(outPath)))) {

            out.println(HEADER);

            for (int ci = 0; ci < conditions.size(); ci++) {
                int[] c           = conditions.get(ci);
                int   avgDeg      = c[0];
                int   numLatents  = c[1];
                int   numMeasures = c[2];
                int   sampleSize  = c[3];
                int   totalNodes  = numMeasures + numLatents;

                System.out.printf("[%d/%d]  avgDeg=%d  latents=%d  measures=%d  n=%d%n",
                        ci + 1, conditions.size(), avgDeg, numLatents, numMeasures, sampleSize);

                // Accumulators: [alg 0-6][stat 0-10]
                // Slots: 0 *->-Prec  1 -->-Prec  2 <->-Lat
                //        3 AHP  4 AHPC  5 AHR  6 AHRC  7 AP  8 AR
                //        9 E-Wall  10 PAG
                final int NS = 11;
                double[][] sums = new double[NUM_ALGS][NS];
                int[][]    cnts = new int[NUM_ALGS][NS];

                // Once ICD times out on any run within this condition, all subsequent
                // runs are skipped for ICD and recorded as missing.
                boolean icdAbandoned = false;

                for (int run = 0; run < numRuns; run++) {
                    long runSeed = seed + (long) run * 100_003L;
                    RandomUtil.getInstance().setSeed(runSeed);

                    Graph   trueDag = buildRandomDag(totalNodes, avgDeg, numLatents, runSeed);
                    Graph   truePag = computeTruePag(trueDag);
                    DataSet data    = simulateData(trueDag, sampleSize, runSeed);

                    CovarianceMatrix cov = new CovarianceMatrix(data);

                    for (int ai = 0; ai < NUM_ALGS; ai++) {

                        Graph  est;
                        double wallSec;

                        if (ai == ICD_ALG_INDEX) {
                            // ── ICD: skip entirely if already abandoned this condition ──
                            if (icdAbandoned) {
                                System.err.printf("  run=%d alg=%d (ICD) skipped (previously timed out)%n",
                                        run + 1, ai + 1);
                                continue; // leave accumulators empty → missing in output
                            }

                            // ── ICD: run on a separate thread with timeout ──────────────
                            final CovarianceMatrix covFinal = cov;
                            AtomicReference<Graph>     resultRef = new AtomicReference<>(null);
                            AtomicReference<Double>    wallRef   = new AtomicReference<>(Double.NaN);
                            AtomicReference<Throwable> errRef    = new AtomicReference<>(null);

                            Future<?> future = executor.submit(() -> {
                                try {
                                    IndTestFisherZ icdTest = new IndTestFisherZ(covFinal, DEFAULT_ALPHA);
                                    long t0 = System.nanoTime();
                                    Graph g = runIcd(icdTest);
                                    wallRef.set((System.nanoTime() - t0) / 1e9);
                                    resultRef.set(g);
                                } catch (Throwable t) {
                                    errRef.set(t);
                                }
                            });

                            boolean timedOut = false;
                            try {
                                future.get(timeoutSeconds, TimeUnit.SECONDS);
                            } catch (TimeoutException e) {
                                future.cancel(true);
                                timedOut = true;
                                System.err.printf(
                                        "  run=%d alg=%d (ICD) TIMED OUT after %ds — "
                                                + "abandoning ICD for this condition%n",
                                        run + 1, ai + 1, timeoutSeconds);
                            } catch (ExecutionException e) {
                                System.err.printf("  run=%d alg=%d (ICD) failed: %s%n",
                                        run + 1, ai + 1, e.getCause().getMessage());
                                timedOut = true;
                            }

                            if (timedOut || resultRef.get() == null) {
                                // Mark ICD as abandoned for all remaining runs in this condition
                                icdAbandoned = true;
                                continue; // skip accumulation for this run
                            }

                            est     = resultRef.get();
                            wallSec = wallRef.get();

                        } else {
                            // ── All other algorithms: run on the main thread ────────────
                            try {
                                SemBicScore    score = new SemBicScore(cov);
                                score.setPenaltyDiscount(PENALTY_DISCOUNT);
                                IndTestFisherZ test  = new IndTestFisherZ(cov, DEFAULT_ALPHA);

                                long t0 = System.nanoTime();
                                est     = runAlgorithm(ai, score, test);
                                wallSec = (System.nanoTime() - t0) / 1e9;
                            } catch (Exception e) {
                                System.err.printf("  run=%d alg=%d failed: %s%n",
                                        run + 1, ai + 1, e.getMessage());
                                continue;
                            }
                        }

                        est     = GraphUtils.replaceNodes(est,     trueDag.getNodes());
                        truePag = GraphUtils.replaceNodes(truePag, trueDag.getNodes());

                        accum(sums, cnts, ai, 9,  wallSec);
                        accum(sums, cnts, ai, 0,  safeTrueDag(dagArrowPrec, est, trueDag, data));
                        accum(sums, cnts, ai, 1,  safeTrueDag(dagDirPrec,   est, trueDag, data));
                        accum(sums, cnts, ai, 2,  safeTrueDag(dagBiDir,     est, trueDag, data));
                        accum(sums, cnts, ai, 3,  safe(pagAHP,   est, truePag, data));
                        accum(sums, cnts, ai, 4,  safe(pagAHPC,  est, truePag, data));
                        accum(sums, cnts, ai, 5,  safe(pagAHR,   est, truePag, data));
                        accum(sums, cnts, ai, 6,  safe(pagAHRC,  est, truePag, data));
                        accum(sums, cnts, ai, 7,  safe(pagAP,    est, truePag, data));
                        accum(sums, cnts, ai, 8,  safe(pagAR,    est, truePag, data));
                        accum(sums, cnts, ai, 10, safe(pagExact, est, truePag, data));

                        System.err.printf("  run=%d alg=%d  wall=%.2fs%n",
                                run + 1, ai + 1, wallSec);
                    }

                    System.err.printf("  run %d/%d done%n", run + 1, numRuns);
                }

                if (icdAbandoned) {
                    System.out.printf("  ICD abandoned for this condition (timed out).%n");
                }

                // Write one row per algorithm for this condition
                for (int ai = 0; ai < NUM_ALGS; ai++) {
                    String biDirStr = (numLatents == 0)
                            ? "*" : fmtAvg(sums, cnts, ai, 2);

                    out.println(String.join("\t",
                            String.valueOf(ai + 1),
                            String.valueOf(avgDeg),
                            String.valueOf(numLatents),
                            String.valueOf(numMeasures),
                            String.valueOf(numRuns),
                            String.valueOf(sampleSize),
                            fmtAvg(sums, cnts, ai, 0),   // *->-Prec
                            fmtAvg(sums, cnts, ai, 1),   // -->-Prec
                            biDirStr,                      // <->-Lat-Prec
                            fmtAvg(sums, cnts, ai, 3),   // AHP
                            fmtAvg(sums, cnts, ai, 4),   // AHPC
                            fmtAvg(sums, cnts, ai, 5),   // AHR
                            fmtAvg(sums, cnts, ai, 6),   // AHRC
                            fmtAvg(sums, cnts, ai, 7),   // AP
                            fmtAvg(sums, cnts, ai, 8),   // AR
                            fmtAvg(sums, cnts, ai, 9),   // E-Wall
                            fmtAvg(sums, cnts, ai, 10)   // PAG
                    ));
                }
                out.flush();
            }
        } finally {
            executor.shutdownNow();
        }

        System.out.println("Done.  Results written to " + outPath);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Algorithm dispatch
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Dispatch for algorithms 0-4 and 6 (all except ICD which is handled inline).
     * Index mapping (0-based):
     *   0 LV-Heuristic  1 FCIT  2 BOSS-FCI  3 GRaSP-FCI  4 GFCI  5 ICD(unused here)  6 FCI
     */
    private static Graph runAlgorithm(int ai, SemBicScore score, IndTestFisherZ test) {
        return switch (ai) {
            case 0 -> runLvHeuristic(score);
            case 1 -> runFcit(score, test);
            case 2 -> runBossFci(score, test);
            case 3 -> runGraspFci(score, test);
            case 4 -> runGfci(score, test);
            // case 5 is ICD — handled separately in main with timeout logic
            case 6 -> runFci(test);
            default -> throw new IllegalStateException("Unknown alg index: " + ai);
        };
    }

    private static Graph runLvHeuristic(SemBicScore score) {
        try {
            return new LvHeuristic(score).search();
        } catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    private static Graph runFcit(SemBicScore score, IndTestFisherZ test) {
        try {
            Fcit fcit = new Fcit(test, score);
            fcit.setDepth(FCIT_DEPTH);
            return fcit.search();
        } catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    private static Graph runBossFci(SemBicScore score, IndTestFisherZ test) {
        try {
            return new BossFci(test, score).search();
        } catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    private static Graph runGraspFci(SemBicScore score, IndTestFisherZ test) {
        try {
            return new GraspFci(test, score).search();
        } catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    private static Graph runGfci(SemBicScore score, IndTestFisherZ test) {
        try {
            return new Gfci(test, score).search();
        } catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    private static Graph runFci(IndTestFisherZ test) {
        try {
            return new Fci(test).search();
        } catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    private static Graph runIcd(IndTestFisherZ test) {
        try {
            return new Icd(test).search();
        } catch (InterruptedException e) {
            // Thread was interrupted by the timeout cancellation — propagate cleanly
            Thread.currentThread().interrupt();
            throw new RuntimeException("ICD interrupted", e);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Graph / data generation
    // ────────────────────────────────────────────────────────────────────────

    private static Graph buildRandomDag(int totalNodes, int avgDeg,
                                        int numLatents, long seed) {
        RandomUtil.getInstance().setSeed(seed);
        int numEdges = (int) Math.round(totalNodes * avgDeg / 2.0);
        return RandomGraph.randomGraph(
                totalNodes, numLatents, numEdges, 100, 100, 100, false, seed);
    }

    private static Graph computeTruePag(Graph trueDag) {
        return new MagToPag(GraphTransforms.dagToMag(trueDag)).convert(false, false);
    }

    private static DataSet simulateData(Graph trueDag, int sampleSize, long seed) {
        SemPm pm = new SemPm(trueDag);
        SemIm im = new SemIm(pm);
        RandomUtil.getInstance().setSeed(seed);
        try {
            return im.simulateData(sampleSize, false);
        } catch (ParseException e) { throw new RuntimeException(e); }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Utility helpers
    // ────────────────────────────────────────────────────────────────────────

    private static double safe(Statistic s, Graph est, Graph truth, DataSet data) {
        try {
            return s.getValue(truth, est, data);
        } catch (Exception e) { return Double.NaN; }
    }

    private static double safeTrueDag(Statistic s, Graph est, Graph truth,
                                      DataSet data) {
        try {
            return s.getValue(truth, truth, est, data, new Parameters());
        } catch (Exception e) { return Double.NaN; }
    }

    private static void accum(double[][] sums, int[][] cnts, int ai, int si, double v) {
        if (Double.isFinite(v)) { sums[ai][si] += v; cnts[ai][si]++; }
    }

    private static double avg(double[][] sums, int[][] cnts, int ai, int si) {
        return cnts[ai][si] == 0 ? Double.NaN : sums[ai][si] / cnts[ai][si];
    }

    /**
     * Formats an averaged statistic for TSV output. NaN (either from no finite
     * observations or from all runs timing out) is written as an empty string,
     * which pandas.read_csv() interprets as NaN by default.
     */
    private static String fmtAvg(double[][] sums, int[][] cnts, int ai, int si) {
        double v = avg(sums, cnts, ai, si);
        return Double.isNaN(v) ? MISSING : String.format("%.4f", v);
    }
}

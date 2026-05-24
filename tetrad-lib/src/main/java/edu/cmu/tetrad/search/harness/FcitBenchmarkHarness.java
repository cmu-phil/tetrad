///////////////////////////////////////////////////////////////////////////////
// FcitBenchmarkHarness.java                                                 //
//                                                                           //
// Serial benchmark harness for comparing six causal-discovery algorithms:   //
//   1 LV-Heuristic   2 FCIT   3 BOSS-FCI                                   //
//   4 GRaSP-FCI      5 GFCI   6 FCI                                        //
//                                                                           //
// Runs every (condition × algorithm × run) sequentially in a single JVM.   //
// Wall-clock time is measured per algorithm run via System.nanoTime().      //
// CovarianceMatrix is constructed once per run (outside timing) and shared  //
// across all 6 algorithms for that run.                                     //
//                                                                           //
// Usage:                                                                    //
//   java -Xmx8g -cp tetrad-current.jar                                      //
//        edu.cmu.tetrad.search.harness.FcitBenchmarkHarness                 //
//        [--out results.tsv] [--numRuns 20] [--seed 42]                    //
//                                                                           //
// Stat semantics                                                            //
// ──────────────                                                            //
// DAG-based  (true reference = true DAG, latent nodes tagged LATENT):      //
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

public class FcitBenchmarkHarness {

    // ────────────────────────────────────────────────────────────────────────
    // Constants
    // ────────────────────────────────────────────────────────────────────────

    private static final int    NUM_ALGS         = 6;
    private static final double DEFAULT_ALPHA    = 0.01;
    private static final double PENALTY_DISCOUNT = 2.0;
    private static final int    FCIT_DEPTH       = 3;

    private static final String HEADER = String.join("\t",
            "Alg", "avgDegree", "numLatents", "numMeasures", "numRuns", "sampleSize",
            "*->-Prec", "-->-Prec", "<->-Lat-Prec",
            "AHP", "AHPC", "AHR", "AHRC",
            "AP", "AR",
            "E-Wall", "PAG"
    );

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

    public static void main(String[] args) throws Exception {

        String outPath = "fcit_benchmark_results.tsv";
        int    numRuns = 20;
        long   seed    = 42L;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--out"     -> outPath = args[++i];
                case "--numRuns" -> numRuns = Integer.parseInt(args[++i]);
                case "--seed"    -> seed    = Long.parseLong(args[++i]);
            }
        }

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

        try (PrintWriter out = new PrintWriter(
                Files.newBufferedWriter(Path.of(outPath)))) {

            out.println(HEADER);

            for (int ci = 0; ci < conditions.size(); ci++) {
                int[] c          = conditions.get(ci);
                int   avgDeg     = c[0];
                int   numLatents = c[1];
                int   numMeasures = c[2];
                int   sampleSize = c[3];
                int   totalNodes = numMeasures + numLatents;

                System.out.printf("[%d/%d]  avgDeg=%d  latents=%d  measures=%d  n=%d%n",
                        ci + 1, conditions.size(), avgDeg, numLatents, numMeasures, sampleSize);

                // Accumulators: [alg 0-5][stat 0-10]
                // Slots: 0 *->-Prec  1 -->-Prec  2 <->-Lat
                //        3 AHP  4 AHPC  5 AHR  6 AHRC  7 AP  8 AR
                //        9 E-Wall  10 PAG
                final int NS = 11;
                double[][] sums = new double[NUM_ALGS][NS];
                int[][]    cnts = new int[NUM_ALGS][NS];

                for (int run = 0; run < numRuns; run++) {
                    long runSeed = seed + (long) run * 100_003L;
                    RandomUtil.getInstance().setSeed(runSeed);

                    Graph   trueDag = buildRandomDag(totalNodes, avgDeg, numLatents, runSeed);
                    Graph   truePag = computeTruePag(trueDag);
                    DataSet data    = simulateData(trueDag, sampleSize, runSeed);

                    // Build covariance matrix once per run, shared across all algorithms.
                    // This is intentionally outside the per-algorithm timing block.
                    CovarianceMatrix cov = new CovarianceMatrix(data);

                    for (int ai = 0; ai < NUM_ALGS; ai++) {
                        Graph  est;
                        double wallSec;
                        try {
                            // Fresh score and test per algorithm using the shared cov matrix
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

                        est    = GraphUtils.replaceNodes(est,    trueDag.getNodes());
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

                // Write one row per algorithm for this condition
                for (int ai = 0; ai < NUM_ALGS; ai++) {
                    String biDirStr = (numLatents == 0)
                            ? "*" : fmt(avg(sums, cnts, ai, 2));

                    out.println(String.join("\t",
                            String.valueOf(ai + 1),
                            String.valueOf(avgDeg),
                            String.valueOf(numLatents),
                            String.valueOf(numMeasures),
                            String.valueOf(numRuns),
                            String.valueOf(sampleSize),
                            fmt(avg(sums, cnts, ai, 0)),   // *->-Prec
                            fmt(avg(sums, cnts, ai, 1)),   // -->-Prec
                            biDirStr,                       // <->-Lat-Prec
                            fmt(avg(sums, cnts, ai, 3)),   // AHP
                            fmt(avg(sums, cnts, ai, 4)),   // AHPC
                            fmt(avg(sums, cnts, ai, 5)),   // AHR
                            fmt(avg(sums, cnts, ai, 6)),   // AHRC
                            fmt(avg(sums, cnts, ai, 7)),   // AP
                            fmt(avg(sums, cnts, ai, 8)),   // AR
                            fmt(avg(sums, cnts, ai, 9)),   // E-Wall
                            fmt(avg(sums, cnts, ai, 10))   // PAG
                    ));
                }
                out.flush();
            }
        }

        System.out.println("Done.  Results written to " + outPath);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Algorithm dispatch
    // ────────────────────────────────────────────────────────────────────────

    private static Graph runAlgorithm(int ai, SemBicScore score, IndTestFisherZ test) {
        return switch (ai) {
            case 0 -> runLvHeuristic(score);
            case 1 -> runFcit(score, test);
            case 2 -> runBossFci(score, test);
            case 3 -> runGraspFci(score, test);
            case 4 -> runGfci(score, test);
            case 5 -> runFci(test);
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

    private static String fmt(double v) {
        return Double.isNaN(v) ? "*" : String.format("%.4f", v);
    }
}

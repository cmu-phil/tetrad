///////////////////////////////////////////////////////////////////////////////
// FcitBenchmarkHarness100.java                                              //
//                                                                           //
// Benchmark harness for Table 3: 100-node graphs.                           //
//   avg. degree 6, 10 latents, N=1000, 20 runs                              //
//                                                                           //
// Only DAG-based statistics are computed (no dagToMag / true PAG needed):   //
//   *->-Prec   TrueDagPrecisionArrow                                        //
//   -->-Prec   TrueDagPrecisionTails                                        //
//   <->-Lat    BidirectedLatentPrecision                                    //
//   E-Wall     wall-clock seconds                                           //
//   PAG        LegalPag                                                     //
//                                                                           //
// Output: LaTeX table printed to stdout (redirect to a .tex file if needed) //
//                                                                           //
// Usage:                                                                    //
//   java -Xmx16g -cp tetrad-current.jar                                     //
//        edu.cmu.tetrad.search.harness.FcitBenchmarkHarness100              //
//        [--numRuns 20] [--seed 42]                                         //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;

import java.text.ParseException;

public class FcitBenchmarkHarness100 {

    // ────────────────────────────────────────────────────────────────────────
    // Fixed condition for Table 3
    // ────────────────────────────────────────────────────────────────────────

    private static final int    NUM_MEASURES     = 100;
    private static final int    NUM_LATENTS      = 6;
    private static final int    AVG_DEGREE       = 4;
    private static final int    SAMPLE_SIZE      = 1000;
    private static final double DEFAULT_ALPHA    = 0.01;
    private static final double PENALTY_DISCOUNT = 2.0;
    private static final int DEPTH = 7;   // match paper's d=7 for 100-node

    private static final int NUM_ALGS = 6;

    private static final String[] ALG_LABELS = {
            "LV-Heuristic", "FCIT", "BOSS-FCI", "GRaSP-FCI", "GFCI", "FCI"
    };

    // ────────────────────────────────────────────────────────────────────────
    // main
    // ────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        int  numRuns = 20;
        long seed    = 42L;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--numRuns" -> numRuns = Integer.parseInt(args[++i]);
                case "--seed"    -> seed    = Long.parseLong(args[++i]);
            }
        }

        // Stat objects — stateless, reused across all runs
        Statistic dagArrowPrec = new TrueDagPrecisionArrow();
        Statistic dagDirPrec   = new TrueDagPrecisionTails();
        Statistic dagBiDir     = new BidirectedLatentPrecision();
        Statistic pagExact     = new LegalPag();

        int totalNodes = NUM_MEASURES + NUM_LATENTS;

        // Accumulators: [alg 0-5][stat 0-4]
        // Slots: 0 *->-Prec  1 -->-Prec  2 <->-Lat  3 E-Wall  4 PAG
        final int NS = 5;
        double[][] sums = new double[NUM_ALGS][NS];
        int[][]    cnts = new int[NUM_ALGS][NS];

        for (int run = 0; run < numRuns; run++) {
            long runSeed = seed + (long) run * 100_003L;
            RandomUtil.getInstance().setSeed(runSeed);

            System.err.printf("run %d/%d — generating graph and data...%n",
                    run + 1, numRuns);

            Graph   trueDag = buildRandomDag(totalNodes, AVG_DEGREE, NUM_LATENTS, runSeed);
            DataSet data    = simulateData(trueDag, SAMPLE_SIZE, runSeed);
            CovarianceMatrix cov = new CovarianceMatrix(data);

            // True PAG is NOT computed here — DAG-based stats only

            for (int ai = 0; ai < NUM_ALGS; ai++) {
                Graph  est;
                double wallSec;
                try {
                    SemBicScore    score = new SemBicScore(cov);
                    score.setPenaltyDiscount(PENALTY_DISCOUNT);
                    IndTestFisherZ test  = new IndTestFisherZ(cov, DEFAULT_ALPHA);

                    long t0 = System.nanoTime();
                    est     = runAlgorithm(ai, score, test);
                    wallSec = (System.nanoTime() - t0) / 1e9;
                } catch (Exception e) {
                    System.err.printf("  alg=%s failed: %s%n",
                            ALG_LABELS[ai], e.getMessage());
                    continue;
                }

                est = GraphUtils.replaceNodes(est, trueDag.getNodes());

                accum(sums, cnts, ai, 0, safeTrueDag(dagArrowPrec, est, trueDag, data));
                accum(sums, cnts, ai, 1, safeTrueDag(dagDirPrec,   est, trueDag, data));
                accum(sums, cnts, ai, 2, safeTrueDag(dagBiDir,     est, trueDag, data));
                accum(sums, cnts, ai, 3, wallSec);

                // LegalPag takes (trueGraph, estGraph, data) — pass trueDag as
                // the reference; LegalPag only inspects the estimated graph
                // for structural validity, so the reference is not used.
                accum(sums, cnts, ai, 4, safePag(pagExact, est, trueDag, data));

                System.err.printf("  alg=%-14s  wall=%7.1fs%n",
                        ALG_LABELS[ai], wallSec);
            }

            System.err.printf("run %d/%d done%n", run + 1, numRuns);
        }

        // ── emit LaTeX table ─────────────────────────────────────────────
        printLatexTable(sums, cnts, numRuns);
    }

    // ────────────────────────────────────────────────────────────────────────
    // LaTeX output
    // ────────────────────────────────────────────────────────────────────────

    private static void printLatexTable(double[][] sums, int[][] cnts, int numRuns) {
        System.out.println("\\begin{table}[htbp]");
        System.out.println("\\centering");
        System.out.printf(
                "\\caption{Comparison of algorithms, 100-node graphs (avg.\\ degree %d, " +
                        "%d latents, $N{=}%d$, %d runs). " +
                        "Note that LV-Heuristic does not orient bidirected edges. " +
                        "E-Wall is in seconds.}%n",
                AVG_DEGREE, NUM_LATENTS, SAMPLE_SIZE, numRuns);
        System.out.println("\\begin{tabular}{lccccc}");
        System.out.println("\\toprule");
        System.out.println(
                "\\textbf{Algorithm} & " +
                        "\\textbf{$*\\rightarrow$-Prec} & " +
                        "\\textbf{$\\rightarrow$-Prec} &\n" +
                        "  \\textbf{$\\leftrightarrow$-Lat-Prec} & " +
                        "\\textbf{E-Wall (s)} & " +
                        "\\textbf{PAG} \\\\");
        System.out.println("\\midrule");

        for (int ai = 0; ai < NUM_ALGS; ai++) {
            double arrowPrec = avg(sums, cnts, ai, 0);
            double dirPrec   = avg(sums, cnts, ai, 1);
            double biDir     = avg(sums, cnts, ai, 2);
            double wall      = avg(sums, cnts, ai, 3);
            double pag       = avg(sums, cnts, ai, 4);

            // LV-Heuristic by design cannot orient bidirected edges → *
            String biDirStr = (ai == 0) ? "*" : fmtStat(biDir);

            System.out.printf("%-14s & %s & %s & %s & %s & %s \\\\%n",
                    ALG_LABELS[ai],
                    fmtStat(arrowPrec),
                    fmtStat(dirPrec),
                    biDirStr,
                    fmtWall(wall),
                    fmtStat(pag));
        }

        System.out.println("\\bottomrule");
        System.out.println("\\end{tabular}");
        System.out.println("\\label{tab:100node}");
        System.out.println("\\end{table}");
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
        try { return new LvHeuristic(score).search(); }
        catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    private static Graph runFcit(SemBicScore score, IndTestFisherZ test) {
        try {
            Fcit fcit = new Fcit(test, score);
            fcit.setVerbose(true);
            fcit.setDepth(DEPTH);
            return fcit.search();
        } catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    private static Graph runBossFci(SemBicScore score, IndTestFisherZ test) {
        try {
            BossFci bossFci = new BossFci(test, score);
            bossFci.setDepth(DEPTH);
            return bossFci.search(); }
        catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    private static Graph runGraspFci(SemBicScore score, IndTestFisherZ test) {
        try {
            GraspFci graspFci = new GraspFci(test, score);
            graspFci.setDepth(DEPTH);
            return graspFci.search(); }
        catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    private static Graph runGfci(SemBicScore score, IndTestFisherZ test) {
        try {
            Gfci gfci = new Gfci(test, score);
            gfci.setDepth(DEPTH);
            return gfci.search(); }
        catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    private static Graph runFci(IndTestFisherZ test) {
        try {
            Fci fci = new Fci(test);
            fci.setDepth(DEPTH);
            return fci.search(); }
        catch (InterruptedException e) { throw new RuntimeException(e); }
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

    private static DataSet simulateData(Graph trueDag, int sampleSize, long seed) {
        SemPm pm = new SemPm(trueDag);
        SemIm im = new SemIm(pm);
        RandomUtil.getInstance().setSeed(seed);
        try { return im.simulateData(sampleSize, false); }
        catch (ParseException e) { throw new RuntimeException(e); }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Utility helpers
    // ────────────────────────────────────────────────────────────────────────

    private static double safeTrueDag(Statistic s, Graph est, Graph truth, DataSet data) {
        try { return s.getValue(truth, truth, est, data, new Parameters()); }
        catch (Exception e) { return Double.NaN; }
    }

    private static double safePag(Statistic s, Graph est, Graph truth, DataSet data) {
        try { return s.getValue(truth, est, data); }
        catch (Exception e) { return Double.NaN; }
    }

    private static void accum(double[][] sums, int[][] cnts, int ai, int si, double v) {
        if (Double.isFinite(v)) { sums[ai][si] += v; cnts[ai][si]++; }
    }

    private static double avg(double[][] sums, int[][] cnts, int ai, int si) {
        return cnts[ai][si] == 0 ? Double.NaN : sums[ai][si] / cnts[ai][si];
    }

    private static String fmtStat(double v) {
        return Double.isNaN(v) ? "*" : String.format("%.4f", v);
    }

    private static String fmtWall(double v) {
        // Format wall time with one decimal place for readability
        return Double.isNaN(v) ? "*" : String.format("%.1f", v);
    }
}

/// ////////////////////////////////////////////////////////////////////////////
// VertexRepairBattery.java                                                  //
//                                                                           //
// Drop-in addition to VertexRepairSimulation.java.                         //
//                                                                           //
// Call runFullBattery() instead of run() to sweep all combinations of      //
// starting graph × average degree × sample size, print one progress line   //
// per run, and emit a LaTeX table at the end.                              //
//                                                                           //
// Paste this class into the same package as VertexRepairSimulation, or     //
// merge the methods directly into that class.                              //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;

import java.text.DecimalFormat;
import java.util.*;
import java.util.prefs.Preferences;

public class VertexRepairBattery {

    // =========================================================================
    // Battery configuration — edit these before running
    // =========================================================================

    private static final int NUM_NODES = 10;
    private static final int NUM_RUNS  = 20;      // runs per cell

    private static final double[] AVG_DEGREES   = {2.0, 4.0};
    private static final int[]    SAMPLE_SIZES  = {500, 1000, 10000};

    private static final VertexRepairSimulation.StartingGraph[] SCENARIOS = {
            VertexRepairSimulation.StartingGraph.PC,
            VertexRepairSimulation.StartingGraph.FGES,
            VertexRepairSimulation.StartingGraph.BOSS,
            VertexRepairSimulation.StartingGraph.EMPTY
    };

    // Fixed parameters — must match the paper's stated settings
    private static final double ALPHA            = 0.01;
    private static final double PENALTY_DISCOUNT = 2.0;
    private static final double PRUNE_ALPHA      = 0.05;

    private static final ConditioningSetType              CONDITIONING_TYPE =
            ConditioningSetType.RECURSIVE_BLOCKING;
    private static final VertexRepairSearch.RepairStrategy REPAIR_STRATEGY  =
            VertexRepairSearch.RepairStrategy.GLOBAL_QUEUE;
    private static final VertexRepairSearch.AdjustmentGraphType GRAPH_TYPE  =
            VertexRepairSearch.AdjustmentGraphType.CPDAG;

    // =========================================================================
    // Entry point
    // =========================================================================

    public static void main(String[] args) throws Exception {
        new VertexRepairBattery().runFullBattery();
    }

    public void runFullBattery() throws Exception {
        Preferences.userRoot().putBoolean("useAndersonDarling", false);

        // results keyed by (scenario, avgDegree, sampleSize)
        Map<CellKey, CellResult> results = new LinkedHashMap<>();

        int totalRuns = SCENARIOS.length * AVG_DEGREES.length
                * SAMPLE_SIZES.length * NUM_RUNS;
        int runsDone  = 0;

        System.out.printf("VertexRepair battery: %d scenarios × %d degrees × "
                        + "%d sample sizes × %d runs = %d total runs%n%n",
                SCENARIOS.length, AVG_DEGREES.length,
                SAMPLE_SIZES.length, NUM_RUNS, totalRuns);

        for (VertexRepairSimulation.StartingGraph scenario : SCENARIOS) {
            for (double avgDegree : AVG_DEGREES) {
                for (int sampleSize : SAMPLE_SIZES) {

                    CellKey key = new CellKey(scenario, avgDegree, sampleSize);
                    CellResult cell = new CellResult();
                    results.put(key, cell);

                    for (int run = 1; run <= NUM_RUNS; run++) {
                        runsDone++;
                        RandomUtil.getInstance().setSeed(run * 17L + 31L);

                        // --- simulate ---
                        int numEdges = (int) Math.round(NUM_NODES * avgDegree / 2.0);
                        List<Node> variables = new ArrayList<>();
                        for (int i = 0; i < NUM_NODES; i++)
                            variables.add(new ContinuousVariable("x" + i));

                        Graph trueDAG = RandomGraph.randomGraphRandomForwardEdges(
                                variables, 0, numEdges, 100, 100, 100, false);
                        Graph trueCpdag = GraphTransforms.dagToCpdag(trueDAG);

                        SemIm semIm = new SemIm(new SemPm(trueDAG));
                        DataSet data = semIm.simulateData(sampleSize, false);

                        IndependenceTest fisherZ = new IndTestFisherZ(data, ALPHA);
                        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
                        score.setPenaltyDiscount(PENALTY_DISCOUNT);

                        // --- starting graph ---
                        Graph startGraph = buildStartingGraph(
                                scenario, data, fisherZ, score, trueDAG.getNodes());
                        startGraph = GraphUtils.replaceNodes(startGraph, trueDAG.getNodes());

                        // --- repair ---
                        VertexRepairSearch repair = new VertexRepairSearch(
                                startGraph, fisherZ, CONDITIONING_TYPE);
                        repair.setGraphType(GRAPH_TYPE);
                        repair.setRepairStrategy(REPAIR_STRATEGY);
                        repair.setUseAndersonDarling(false);
                        repair.setPruneAlpha(PRUNE_ALPHA);
                        Graph repaired = repair.search();

                        // --- stats ---
                        Parameters params = new Parameters();
                        RunStats startStats  = computeStats(trueCpdag, startGraph,  data, params);
                        RunStats repairedStats = computeStats(trueCpdag, repaired,  data, params);

                        cell.accumulateStart(startStats);
                        cell.accumulateRepaired(repairedStats);

                        // progress line — one per run, not verbose
                        System.out.printf(
                                "[%4d/%4d] %-6s deg=%.1f n=%6d run=%2d | "
                                        + "Start AdjF1=%.3f ArrF1=%.3f KS=%.3f | "
                                        + "Repaired AdjF1=%.3f ArrF1=%.3f KS=%.3f%n",
                                runsDone, totalRuns,
                                scenario, avgDegree, sampleSize, run,
                                startStats.adjF1,   startStats.arrF1,
                                startStats.markovKS,
                                repairedStats.adjF1, repairedStats.arrF1,
                                repairedStats.markovKS);
                    }
                }
            }
        }

        // emit LaTeX
        System.out.println();
        System.out.println("% ============================================================");
        System.out.println("% LaTeX tables — paste directly into the paper");
        System.out.println("% ============================================================");
        System.out.println();
        emitLatexTables(results);
    }

    // =========================================================================
    // LaTeX table emission
    // =========================================================================

    /**
     * Emits one table per (scenario, avgDegree) pair.
     * Rows = sample sizes.
     * Columns = Start and Repaired for each of 5 statistics.
     */
    private void emitLatexTables(Map<CellKey, CellResult> results) {
        DecimalFormat df2 = new DecimalFormat("0.00");
        DecimalFormat df3 = new DecimalFormat("0.000");

        for (VertexRepairSimulation.StartingGraph scenario : SCENARIOS) {
            for (double avgDegree : AVG_DEGREES) {

                String caption = String.format(
                        "VertexRepair results with %s starting graph, "
                                + "average degree %.0f, %d nodes, %d runs per cell. "
                                + "Each cell shows Start / Repaired.",
                        scenario, avgDegree, NUM_NODES, NUM_RUNS);

                String label = String.format("tab:%s-deg%s",
                        scenario.name().toLowerCase(),
                        String.valueOf((int) avgDegree));

                // table header
                System.out.println("\\begin{table}[ht]");
                System.out.println("\\centering");
                System.out.println("\\small");
                System.out.printf("\\caption{%s}%n", caption);
                System.out.printf("\\label{%s}%n", label);
                // 1 col for N, then 5 stats × 2 (Start/Rep) = 10 cols
                System.out.println("\\begin{tabular}{r " + "cc ".repeat(5) + "}");
                System.out.println("\\toprule");

                // stat group header row
                System.out.println("& \\multicolumn{2}{c}{Adj F1}"
                        + " & \\multicolumn{2}{c}{Arr F1}"
                        + " & \\multicolumn{2}{c}{Arr Prec (com.)}"
                        + " & \\multicolumn{2}{c}{Arr Rec (com.)}"
                        + " & \\multicolumn{2}{c}{Markov KS $p$} \\\\");

                // Start/Rep sub-header
                System.out.println("$n$"
                        + " & Start & Rep"
                        + " & Start & Rep"
                        + " & Start & Rep"
                        + " & Start & Rep"
                        + " & Start & Rep \\\\");
                System.out.println("\\midrule");

                // one data row per sample size
                for (int sampleSize : SAMPLE_SIZES) {
                    CellKey key = new CellKey(scenario, avgDegree, sampleSize);
                    CellResult cell = results.get(key);
                    if (cell == null) continue;

                    CellResult.Averages s = cell.startAverages();
                    CellResult.Averages r = cell.repairedAverages();

                    System.out.printf(
                            "%6d & %s & %s & %s & %s & %s & %s & %s & %s & %s & %s \\\\%n",
                            sampleSize,
                            df2.format(s.adjF1),        df2.format(r.adjF1),
                            df2.format(s.arrF1),        df2.format(r.arrF1),
                            df2.format(s.arrPrecCommon),df2.format(r.arrPrecCommon),
                            df2.format(s.arrRecCommon), df2.format(r.arrRecCommon),
                            df3.format(s.markovKS),     df3.format(r.markovKS));
                }

                System.out.println("\\bottomrule");
                System.out.println("\\end{tabular}");
                System.out.println("\\end{table}");
                System.out.println();
            }
        }
    }

    // =========================================================================
    // Starting-graph builders
    // =========================================================================

    private Graph buildStartingGraph(
            VertexRepairSimulation.StartingGraph scenario,
            DataSet data, IndependenceTest test,
            edu.cmu.tetrad.search.score.Score score,
            List<Node> nodes) throws Exception {
        return switch (scenario) {
            case BOSS  -> { Boss b = new Boss(score);
                yield new PermutationSearch(b).search(); }
            case PC    -> new Pc(test).search();
            case FGES  -> new Fges(score).search();
            case EMPTY -> new EdgeListGraph(nodes);
        };
    }

    // =========================================================================
    // Statistics
    // =========================================================================

    private RunStats computeStats(Graph trueCpdag, Graph estimated,
                                  DataSet data, Parameters params) {
        Graph est = estimated;
        try {
            if (estimated.paths().isLegalDag())
                est = GraphTransforms.dagToCpdag(estimated);
            else if (estimated.paths().isLegalCpdag()
                    || estimated.paths().isLegalPdag()) {
                Graph dag = GraphTransforms.dagFromCpdag(estimated);
                est = GraphTransforms.dagToCpdag(dag);
            }
        } catch (Exception ignored) {}

        RunStats s = new RunStats();
        s.adjF1         = new F1Adj().getValue(trueCpdag, est, data, params);
        s.arrF1         = new F1Arrow().getValue(trueCpdag, est, data, params);
        s.arrPrecCommon = new ArrowheadPrecisionCommonEdges()
                .getValue(trueCpdag, est, data, params);
        s.arrRecCommon  = new ArrowheadRecallCommonEdges()
                .getValue(trueCpdag, est, data, params);
        s.markovKS      = new MarkovCheckKolmogorovSmirnoffP(
                new edu.cmu.tetrad.algcomparison.independence.FisherZ(),
                ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY, params)
                .getValue(trueCpdag, est, data, params);
        return s;
    }

    // =========================================================================
    // Inner types
    // =========================================================================

    /** Identifies one experimental cell. */
    private record CellKey(
            VertexRepairSimulation.StartingGraph scenario,
            double avgDegree,
            int sampleSize) {}

    /** One run's worth of the five reported statistics. */
    private static class RunStats {
        double adjF1, arrF1, arrPrecCommon, arrRecCommon, markovKS;
    }

    /** Accumulates stats over NUM_RUNS runs for one cell, computes averages. */
    private static class CellResult {

        // start-graph accumulators
        private double sAdjF1, sArrF1, sArrPC, sArrRC, sKS;
        // repaired accumulators
        private double rAdjF1, rArrF1, rArrPC, rArrRC, rKS;
        private int count = 0;

        void accumulateStart(RunStats s) {
            sAdjF1 += nanSafe(s.adjF1);
            sArrF1 += nanSafe(s.arrF1);
            sArrPC += nanSafe(s.arrPrecCommon);
            sArrRC += nanSafe(s.arrRecCommon);
            sKS    += nanSafe(s.markovKS);
        }

        void accumulateRepaired(RunStats s) {
            rAdjF1 += nanSafe(s.adjF1);
            rArrF1 += nanSafe(s.arrF1);
            rArrPC += nanSafe(s.arrPrecCommon);
            rArrRC += nanSafe(s.arrRecCommon);
            rKS    += nanSafe(s.markovKS);
            count++;
        }

        Averages startAverages()    { return avg(sAdjF1, sArrF1, sArrPC, sArrRC, sKS); }
        Averages repairedAverages() { return avg(rAdjF1, rArrF1, rArrPC, rArrRC, rKS); }

        private Averages avg(double aF1, double rF1, double aPC, double aRC, double ks) {
            Averages a = new Averages();
            if (count == 0) return a;
            a.adjF1         = aF1 / count;
            a.arrF1         = rF1 / count;
            a.arrPrecCommon = aPC  / count;
            a.arrRecCommon  = aRC  / count;
            a.markovKS      = ks   / count;
            return a;
        }

        private static double nanSafe(double v) { return Double.isNaN(v) ? 0.0 : v; }

        static class Averages {
            double adjF1, arrF1, arrPrecCommon, arrRecCommon, markovKS;
        }
    }
}

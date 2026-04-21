/// ////////////////////////////////////////////////////////////////////////////
// VertexRepairBattery2.java                                                 //
//                                                                           //
// Updated battery sweeping conditioning types as well as scenarios,        //
// degrees, and sample sizes.                                                //
//                                                                           //
// Statistics reported: Adj F1, Adj Precision, Arr Precision,               //
// Arr Precision (common edges), Markov KS p-value.                         //
//                                                                           //
// Outer loop: conditioning type                                             //
// Inner loops: scenario x avg degree x sample size                         //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.algcomparison.independence.FisherZ;
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

/**
 * The VertexRepairBattery2 class is responsible for conducting and evaluating a comprehensive
 * suite of tests on the VertexRepair algorithm under various configurations. This includes
 * simulating data, building graphs, applying the VertexRepair algorithm, and aggregating
 * performance metrics to systematically analyze the algorithm's behavior.
 */
public class VertexRepairBattery2 {

    // =========================================================================
    // Battery configuration
    // =========================================================================

    private static final int NUM_NODES = 10;
    private static final int NUM_RUNS  = 20;

    private static final double[] AVG_DEGREES  = {2.0, 3.0, 4.0};
    private static final int[]    SAMPLE_SIZES = {500, 1000, 5000, 10_000};

    /**
     * Constructs a new VertexRepairBattery2 object. This constructor is private to ensure
     * that instances can only be created internally, promoting encapsulation and controlled usage.
     */
    public VertexRepairBattery2() {}

    private static final VertexRepairSimulation.StartingGraph[] SCENARIOS = {
            VertexRepairSimulation.StartingGraph.PC,
            VertexRepairSimulation.StartingGraph.FGES,
            VertexRepairSimulation.StartingGraph.BOSS,
    };

    private static final ConditioningSetType[] CONDITIONING_TYPES = {
            ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY,
    };

    // Fixed parameters
    private static final double ALPHA            = 0.01;
    private static final double PENALTY_DISCOUNT = 4.0;
    private static final double PRUNE_ALPHA      = 1.0;

    private static final VertexRepairSearch.RepairStrategy      REPAIR_STRATEGY =
            VertexRepairSearch.RepairStrategy.GLOBAL_QUEUE;
    private static final VertexRepairSearch.AdjustmentGraphType GRAPH_TYPE      =
            VertexRepairSearch.AdjustmentGraphType.CPDAG;

    // =========================================================================
    // Entry point
    // =========================================================================

    /**
     * The entry point of the application.
     *
     * @param args Command-line arguments passed to the application. These arguments
     *             are not used in the current implementation.
     * @throws Exception If any error occurs while executing the comprehensive test
     *                   battery for the VertexRepair algorithm.
     */
    public static void main(String[] args) throws Exception {
        new VertexRepairBattery2().runFullBattery();
    }

    /**
     * Executes a comprehensive battery of tests on the VertexRepair algorithm using a variety of
     * configurations and parameters. The method iterates over all combinations of conditioning types,
     * scenarios, average degrees, and sample sizes, followed by multiple simulation runs for each
     * combination, to evaluate the algorithm's performance.
     *
     * The following steps are performed during each simulation run:
     * 1. Generate a true graph (DAG) and its corresponding CPDAG.
     * 2. Simulate data from the true graph using a structural equation model.
     * 3. Build a starting graph based on the specified configuration.
     * 4. Repair the starting graph using the VertexRepair algorithm.
     * 5. Compute and accumulate performance statistics for both the starting and repaired graphs.
     * 6. Print detailed progress logs to the console for each run.
     *
     * Results are aggregated into a map with keys representing unique (conditioning type, scenario,
     * average degree, sample size) combinations. After all runs are completed, LaTeX tables are
     * emitted to summarize the performance metrics across the configurations.
     *
     * @throws Exception If any error occurs during the simulation process. This exception may include
     */
    public void runFullBattery() throws Exception {
        Preferences.userRoot().putBoolean("useAndersonDarling", false);

        // results keyed by (conditioningType, scenario, avgDegree, sampleSize)
        Map<CellKey, CellResult> results = new LinkedHashMap<>();

        int totalRuns = CONDITIONING_TYPES.length * SCENARIOS.length
                * AVG_DEGREES.length * SAMPLE_SIZES.length * NUM_RUNS;
        int runsDone  = 0;

        System.out.printf("VertexRepair battery 2: %d conditioning types x %d scenarios "
                        + "x %d degrees x %d sample sizes x %d runs = %d total runs%n%n",
                CONDITIONING_TYPES.length, SCENARIOS.length, AVG_DEGREES.length,
                SAMPLE_SIZES.length, NUM_RUNS, totalRuns);

        for (ConditioningSetType condType : CONDITIONING_TYPES) {
            for (VertexRepairSimulation.StartingGraph scenario : SCENARIOS) {
                for (double avgDegree : AVG_DEGREES) {
                    for (int sampleSize : SAMPLE_SIZES) {

                        CellKey key = new CellKey(condType, scenario, avgDegree, sampleSize);
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
                            startGraph = GraphUtils.replaceNodes(
                                    startGraph, trueDAG.getNodes());

                            // --- repair ---
                            VertexRepairSearch repair = new VertexRepairSearch(
                                    startGraph, fisherZ, condType);
                            repair.setGraphType(GRAPH_TYPE);
                            repair.setRepairStrategy(REPAIR_STRATEGY);
                            repair.setUseAndersonDarling(false);
                            repair.setPruneAlpha(PRUNE_ALPHA);
                            Graph repaired = repair.search();

                            // --- stats ---
                            Parameters params = new Parameters();
                            RunStats startStats    = computeStats(trueCpdag, startGraph, data, params);
                            RunStats repairedStats = computeStats(trueCpdag, repaired,   data, params);

                            cell.accumulateStart(startStats);
                            cell.accumulateRepaired(repairedStats);

                            // one progress line per run
                            System.out.printf(
                                    "[%5d/%5d] %-32s %-6s deg=%.1f n=%6d run=%2d | "
                                            + "Start AdjF1=%.3f AdjR=%.3f ArrF1=%.3f ArrP=%.3f ArrR=%.3f ArrPC=%.3f ArrRC=%.3f KS=%.3f | "
                                            + "Rep   AdjF1=%.3f AdjR=%.3f ArrF1=%.3f ArrP=%.3f ArrR=%.3f ArrPC=%.3f ArrRC=%.3f KS=%.3f%n",
                                    runsDone, totalRuns,
                                    condType, scenario, avgDegree, sampleSize, run,
                                    startStats.adjF1,      startStats.adjRec,
                                    startStats.arrF1,      startStats.arrPrec,
                                    startStats.arrRec,     startStats.arrPrecCommon,
                                    startStats.arrRecCommon, startStats.markovKS,
                                    repairedStats.adjF1,      repairedStats.adjRec,
                                    repairedStats.arrF1,      repairedStats.arrPrec,
                                    repairedStats.arrRec,     repairedStats.arrPrecCommon,
                                    repairedStats.arrRecCommon, repairedStats.markovKS);
                        }
                    }
                }
            }
        }

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

    private void emitLatexTables(Map<CellKey, CellResult> results) {
        DecimalFormat df2 = new DecimalFormat("0.00");
        DecimalFormat df3 = new DecimalFormat("0.000");

        // ------------------------------------------------------------------
        // Per-scenario main tables: Adj Prec, Adj Rec, Arr Prec, Arr Rec
        // ------------------------------------------------------------------
        for (VertexRepairSimulation.StartingGraph scenario : SCENARIOS) {

            String tag = scenario.name().toLowerCase();

            System.out.printf("%%%n%% %s starting graph — main table%n%%%n", scenario);
            System.out.println("\\begin{table}[ht]");
            System.out.println("\\centering\\small");
            System.out.printf(
                    "\\caption{VertexRepair results with %s starting graph, OLM conditioning, "
                            + "%d nodes, %d runs per cell. Each cell shows Start / Repaired.}%n",
                    scenario, NUM_NODES, NUM_RUNS);
            System.out.printf("\\label{tab:%s-main}%n", tag);

            System.out.println("\\begin{tabular}{r r cc cc cc cc}");
            System.out.println("\\toprule");

            System.out.println("& & \\multicolumn{2}{c}{Adj Prec}"
                    + " & \\multicolumn{2}{c}{Adj Rec}"
                    + " & \\multicolumn{2}{c}{Arr Prec}"
                    + " & \\multicolumn{2}{c}{Arr Rec} \\\\");

            System.out.println("\\cmidrule(lr){3-4}\\cmidrule(lr){5-6}"
                    + "\\cmidrule(lr){7-8}\\cmidrule(lr){9-10}");

            System.out.println("deg & $n$ & S & R & S & R & S & R & S & R \\\\");
            System.out.println("\\midrule");

            for (double avgDegree : AVG_DEGREES) {
                boolean firstRow = true;
                for (int sampleSize : SAMPLE_SIZES) {
                    CellKey key = new CellKey(
                            CONDITIONING_TYPES[0], scenario, avgDegree, sampleSize);
                    CellResult cell = results.get(key);
                    CellResult.Averages s = cell.startAverages();
                    CellResult.Averages r = cell.repairedAverages();

                    String degStr = firstRow ? String.format("%.0f", avgDegree) : "";
                    firstRow = false;

                    System.out.printf(
                            "%s & %6d & %s & %s & %s & %s & %s & %s & %s & %s \\\\%n",
                            degStr, sampleSize,
                            df2.format(s.adjPrec), df2.format(r.adjPrec),
                            df2.format(s.adjRec),  df2.format(r.adjRec),
                            df2.format(s.arrPrec), df2.format(r.arrPrec),
                            df2.format(s.arrRec),  df2.format(r.arrRec));
                }
                if (avgDegree != AVG_DEGREES[AVG_DEGREES.length - 1]) {
                    System.out.println("\\midrule");
                }
            }

            System.out.println("\\bottomrule");
            System.out.println("\\end{tabular}");
            System.out.println("\\end{table}");
            System.out.println();
        }

        // ------------------------------------------------------------------
        // Consolidated KS p table: PC, FGES, BOSS side by side (emitted once)
        // ------------------------------------------------------------------
        System.out.println("%");
        System.out.println("% Consolidated KS p table: PC, FGES, BOSS");
        System.out.println("%");
        System.out.println("\\begin{table}[ht]");
        System.out.println("\\centering\\small");
        System.out.printf(
                "\\caption{Markov KS $p$-value for PC, FGES, and BOSS starting graphs, "
                        + "OLM conditioning, %d nodes, %d runs per cell. "
                        + "Each cell shows Start / Repaired.}%n",
                NUM_NODES, NUM_RUNS);
        System.out.println("\\label{tab:ks-consolidated}");

        System.out.println("\\begin{tabular}{r r cc cc cc}");
        System.out.println("\\toprule");

        System.out.println("& & \\multicolumn{2}{c}{PC}"
                + " & \\multicolumn{2}{c}{FGES}"
                + " & \\multicolumn{2}{c}{BOSS} \\\\");

        System.out.println("\\cmidrule(lr){3-4}\\cmidrule(lr){5-6}\\cmidrule(lr){7-8}");
        System.out.println("deg & $n$ & S & R & S & R & S & R \\\\");
        System.out.println("\\midrule");

        VertexRepairSimulation.StartingGraph[] ksScenarios = {
                VertexRepairSimulation.StartingGraph.PC,
                VertexRepairSimulation.StartingGraph.FGES,
                VertexRepairSimulation.StartingGraph.BOSS
        };

        for (double avgDegree : AVG_DEGREES) {
            boolean firstRow = true;
            for (int sampleSize : SAMPLE_SIZES) {
                String degStr = firstRow ? String.format("%.0f", avgDegree) : "";
                firstRow = false;

                StringBuilder row = new StringBuilder();
                row.append(String.format("%s & %6d", degStr, sampleSize));

                for (VertexRepairSimulation.StartingGraph sc : ksScenarios) {
                    CellKey key = new CellKey(CONDITIONING_TYPES[0], sc, avgDegree, sampleSize);
                    CellResult cell = results.get(key);
                    CellResult.Averages s = cell.startAverages();
                    CellResult.Averages r = cell.repairedAverages();
                    row.append(String.format(" & %s & %s",
                            df3.format(s.markovKS), df3.format(r.markovKS)));
                }

                row.append(" \\\\");
                System.out.println(row);
            }
            if (avgDegree != AVG_DEGREES[AVG_DEGREES.length - 1]) {
                System.out.println("\\midrule");
            }
        }

        System.out.println("\\bottomrule");
        System.out.println("\\end{tabular}");
        System.out.println("\\end{table}");
        System.out.println();
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
        s.adjPrec       = new AdjacencyPrecision().getValue(trueCpdag, est, data, params);
        s.adjRec        = new AdjacencyRecall().getValue(trueCpdag, est, data, params);
        s.adjF1         = new F1Adj().getValue(trueCpdag, est, data, params);
        s.arrPrec       = new ArrowheadPrecision().getValue(trueCpdag, est, data, params);
        s.arrRec        = new ArrowheadRecall().getValue(trueCpdag, est, data, params);
        s.arrF1         = new F1Arrow().getValue(trueCpdag, est, data, params);
        s.arrPrecCommon = new ArrowheadPrecisionCommonEdges()
                .getValue(trueCpdag, est, data, params);
        s.arrRecCommon  = new ArrowheadRecallCommonEdges()
                .getValue(trueCpdag, est, data, params);
        s.markovKS      = new MarkovCheckKolmogorovSmirnoffP(
                new FisherZ(),
                ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY, params)
                .getValue(trueCpdag, est, data, params);
        return s;
    }

// =========================================================================
// Inner types
// =========================================================================

    private record CellKey(
            ConditioningSetType condType,
            VertexRepairSimulation.StartingGraph scenario,
            double avgDegree,
            int sampleSize) {}

    private static class RunStats {
        double adjPrec, adjRec, adjF1;
        double arrPrec, arrRec, arrF1;
        double arrPrecCommon, arrRecCommon;
        double markovKS;
    }

    private static class CellResult {
        private double sAdjP, sAdjR, sAdjF1;
        private double sArrP, sArrR, sArrF1;
        private double sArrPC, sArrRC, sKS;

        private double rAdjP, rAdjR, rAdjF1;
        private double rArrP, rArrR, rArrF1;
        private double rArrPC, rArrRC, rKS;

        private int count = 0;

        void accumulateStart(RunStats s) {
            sAdjP  += nanSafe(s.adjPrec);
            sAdjR  += nanSafe(s.adjRec);
            sAdjF1 += nanSafe(s.adjF1);
            sArrP  += nanSafe(s.arrPrec);
            sArrR  += nanSafe(s.arrRec);
            sArrF1 += nanSafe(s.arrF1);
            sArrPC += nanSafe(s.arrPrecCommon);
            sArrRC += nanSafe(s.arrRecCommon);
            sKS    += nanSafe(s.markovKS);
        }

        void accumulateRepaired(RunStats s) {
            rAdjP  += nanSafe(s.adjPrec);
            rAdjR  += nanSafe(s.adjRec);
            rAdjF1 += nanSafe(s.adjF1);
            rArrP  += nanSafe(s.arrPrec);
            rArrR  += nanSafe(s.arrRec);
            rArrF1 += nanSafe(s.arrF1);
            rArrPC += nanSafe(s.arrPrecCommon);
            rArrRC += nanSafe(s.arrRecCommon);
            rKS    += nanSafe(s.markovKS);
            count++;
        }

        Averages startAverages()    {
            return avg(sAdjP, sAdjR, sAdjF1, sArrP, sArrR, sArrF1, sArrPC, sArrRC, sKS);
        }
        Averages repairedAverages() {
            return avg(rAdjP, rAdjR, rAdjF1, rArrP, rArrR, rArrF1, rArrPC, rArrRC, rKS);
        }

        private Averages avg(double ap, double ar, double af1,
                             double arrp, double arrr, double arrf1,
                             double arpc, double arrc, double ks) {
            Averages a = new Averages();
            if (count == 0) return a;
            a.adjPrec       = ap   / count;
            a.adjRec        = ar   / count;
            a.adjF1         = af1  / count;
            a.arrPrec       = arrp / count;
            a.arrRec        = arrr / count;
            a.arrF1         = arrf1 / count;
            a.arrPrecCommon = arpc / count;
            a.arrRecCommon  = arrc / count;
            a.markovKS      = ks   / count;
            return a;
        }

        private static double nanSafe(double v) {
            return Double.isNaN(v) ? 0.0 : v;
        }

        static class Averages {
            double adjPrec, adjRec, adjF1;
            double arrPrec, arrRec, arrF1;
            double arrPrecCommon, arrRecCommon;
            double markovKS;
        }
    }
}

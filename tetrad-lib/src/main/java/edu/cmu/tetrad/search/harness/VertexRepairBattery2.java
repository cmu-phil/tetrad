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

    private static final double[] AVG_DEGREES  = {2.0, 4.0};
    private static final int[]    SAMPLE_SIZES = {500, 1000, 10000};

    /**
     * Constructs a new VertexRepairBattery2 object. This constructor is private to ensure
     * that instances can only be created internally, promoting encapsulation and controlled usage.
     */
    public VertexRepairBattery2() {}

    private static final VertexRepairSimulation.StartingGraph[] SCENARIOS = {
            VertexRepairSimulation.StartingGraph.PC,
            VertexRepairSimulation.StartingGraph.FGES,
            VertexRepairSimulation.StartingGraph.BOSS,
            VertexRepairSimulation.StartingGraph.EMPTY
    };

    private static final ConditioningSetType[] CONDITIONING_TYPES = {
            ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY,
            ConditioningSetType.RECURSIVE_BLOCKING,
    };

    // Fixed parameters
    private static final double ALPHA            = 0.01;
    private static final double PENALTY_DISCOUNT = 2.0;
    private static final double PRUNE_ALPHA      = 0.20;

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
                                            + "Start AdjF1=%.3f AdjP=%.3f ArrP=%.3f ArrPC=%.3f KS=%.3f | "
                                            + "Rep   AdjF1=%.3f AdjP=%.3f ArrP=%.3f ArrPC=%.3f KS=%.3f%n",
                                    runsDone, totalRuns,
                                    condType, scenario, avgDegree, sampleSize, run,
                                    startStats.adjF1,      startStats.adjPrec,
                                    startStats.arrPrec,    startStats.arrPrecCommon,
                                    startStats.markovKS,
                                    repairedStats.adjF1,   repairedStats.adjPrec,
                                    repairedStats.arrPrec, repairedStats.arrPrecCommon,
                                    repairedStats.markovKS);
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

    /**
     * One table per (scenario, avgDegree).
     * Rows = sample sizes.
     * Column groups = conditioning types, each showing Start / Repaired
     * for the five statistics.
     *
     * Because five statistics x two phases x four conditioning types would be
     * unreadably wide, we emit two sub-tables per cell:
     *   Sub-table A: Adj F1, Adj Precision, Arr Precision
     *   Sub-table B: Arr Precision (common), Markov KS p
     * Each sub-table has rows = sample sizes and columns =
     * conditioning type x {Start, Rep}.
     */
    private void emitLatexTables(Map<CellKey, CellResult> results) {
        DecimalFormat df2 = new DecimalFormat("0.00");
        DecimalFormat df3 = new DecimalFormat("0.000");

        String[] condLabels = {"RB", "RA", "OLM", "SE"};

        for (VertexRepairSimulation.StartingGraph scenario : SCENARIOS) {
            for (double avgDegree : AVG_DEGREES) {

                String tag = String.format("%s-deg%d",
                        scenario.name().toLowerCase(), (int) avgDegree);

                // ------------------------------------------------------------------
                // Sub-table A: Adj F1, Adj Precision, Arr Precision
                // Columns: N | (Start Rep) x 4 conditioning types x 3 stats
                // We group by conditioning type, each with Start/Rep for 3 stats
                // ------------------------------------------------------------------
                System.out.printf(
                        "%%%n%% %s  avg degree %.0f  — Sub-table A: "
                                + "Adj F1 / Adj Prec / Arr Prec%n%%%n",
                        scenario, avgDegree);
                System.out.println("\\begin{table}[ht]");
                System.out.println("\\centering\\small");
                System.out.printf(
                        "\\caption{%s starting graph, avg degree %.0f, %d nodes, "
                                + "%d runs. RB = Recursive Blocking, RA = Recursive Adjustment, "
                                + "OLM = Ordered Local Markov, SE = Sink Elimination. "
                                + "Each cell: Start / Repaired.}%n",
                        scenario, avgDegree, NUM_NODES, NUM_RUNS);
                System.out.printf("\\label{tab:%s-A}%n", tag);

                // 1 col for n, then 4 cond types x 3 stats x 2 phases = 24 cols
                // Group headers by conditioning type
                StringBuilder colSpec = new StringBuilder("r");
                for (int i = 0; i < 4; i++) colSpec.append(" cc cc cc");
                // That's r + 4*(cc cc cc) but we want grouping by cond type
                // Simpler: r + 24 c's
                System.out.println("\\begin{tabular}{r " + "cc cc cc cc cc cc cc cc ".trim() + "}");
                System.out.println("\\toprule");

                // Row 1: conditioning type headers spanning 6 cols each
                StringBuilder row1 = new StringBuilder("$n$");
                for (String cl : condLabels)
                    row1.append(" & \\multicolumn{6}{c}{").append(cl).append("}");
                System.out.println(row1 + " \\\\");

                // cmidrule separators
                int col = 2;
                for (int i = 0; i < 4; i++) {
                    System.out.printf("\\cmidrule(lr){%d-%d}", col, col + 5);
                    col += 6;
                }
                System.out.println();

                // Row 2: stat headers spanning 2 cols each within each cond type
                StringBuilder row2 = new StringBuilder();
                for (int i = 0; i < 4; i++)
                    row2.append(" & \\multicolumn{2}{c}{Adj F1}"
                            + " & \\multicolumn{2}{c}{Adj Prec}"
                            + " & \\multicolumn{2}{c}{Arr Prec}");
                System.out.println(row2 + " \\\\");

                // Row 3: Start/Rep for each stat
                StringBuilder row3 = new StringBuilder("$n$");
                for (int i = 0; i < 12; i++) row3.append(" & S & R");
                System.out.println(row3 + " \\\\");
                System.out.println("\\midrule");

                // Data rows
                for (int sampleSize : SAMPLE_SIZES) {
                    StringBuilder row = new StringBuilder(
                            String.format("%6d", sampleSize));
                    for (ConditioningSetType ct : CONDITIONING_TYPES) {
                        CellKey key = new CellKey(ct, scenario, avgDegree, sampleSize);
                        CellResult cell = results.get(key);
                        CellResult.Averages s = cell.startAverages();
                        CellResult.Averages r = cell.repairedAverages();
                        row.append(String.format(
                                " & %s & %s & %s & %s & %s & %s",
                                df2.format(s.adjF1),   df2.format(r.adjF1),
                                df2.format(s.adjPrec), df2.format(r.adjPrec),
                                df2.format(s.arrPrec), df2.format(r.arrPrec)));
                    }
                    System.out.println(row + " \\\\");
                }

                System.out.println("\\bottomrule");
                System.out.println("\\end{tabular}");
                System.out.println("\\end{table}");
                System.out.println();

                // ------------------------------------------------------------------
                // Sub-table B: Arr Prec (common), Markov KS p
                // ------------------------------------------------------------------
                System.out.printf(
                        "%%%n%% %s  avg degree %.0f  — Sub-table B: "
                                + "Arr Prec (common) / Markov KS p%n%%%n",
                        scenario, avgDegree);
                System.out.println("\\begin{table}[ht]");
                System.out.println("\\centering\\small");
                System.out.printf(
                        "\\caption{%s starting graph, avg degree %.0f — "
                                + "Arr Prec (common edges) and Markov KS $p$-value "
                                + "(continuation of previous table).}%n",
                        scenario, avgDegree);
                System.out.printf("\\label{tab:%s-B}%n", tag);

                // 1 col for n, then 4 cond types x 2 stats x 2 phases = 16 cols
                System.out.println("\\begin{tabular}{r cc cc cc cc cc cc cc cc}");
                System.out.println("\\toprule");

                // Row 1: conditioning type headers spanning 4 cols each
                StringBuilder rb1 = new StringBuilder("$n$");
                for (String cl : condLabels)
                    rb1.append(" & \\multicolumn{4}{c}{").append(cl).append("}");
                System.out.println(rb1 + " \\\\");

                col = 2;
                for (int i = 0; i < 4; i++) {
                    System.out.printf("\\cmidrule(lr){%d-%d}", col, col + 3);
                    col += 4;
                }
                System.out.println();

                // Row 2: stat headers
                StringBuilder rb2 = new StringBuilder();
                for (int i = 0; i < 4; i++)
                    rb2.append(" & \\multicolumn{2}{c}{Arr Prec (com.)}"
                            + " & \\multicolumn{2}{c}{KS $p$}");
                System.out.println(rb2 + " \\\\");

                // Row 3: Start/Rep
                StringBuilder rb3 = new StringBuilder("$n$");
                for (int i = 0; i < 8; i++) rb3.append(" & S & R");
                System.out.println(rb3 + " \\\\");
                System.out.println("\\midrule");

                for (int sampleSize : SAMPLE_SIZES) {
                    StringBuilder row = new StringBuilder(
                            String.format("%6d", sampleSize));
                    for (ConditioningSetType ct : CONDITIONING_TYPES) {
                        CellKey key = new CellKey(ct, scenario, avgDegree, sampleSize);
                        CellResult cell = results.get(key);
                        CellResult.Averages s = cell.startAverages();
                        CellResult.Averages r = cell.repairedAverages();
                        row.append(String.format(
                                " & %s & %s & %s & %s",
                                df2.format(s.arrPrecCommon), df2.format(r.arrPrecCommon),
                                df3.format(s.markovKS),      df3.format(r.markovKS)));
                    }
                    System.out.println(row + " \\\\");
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
        s.adjPrec       = new AdjacencyPrecision().getValue(trueCpdag, est, data, params);
        s.arrPrec       = new ArrowheadPrecision().getValue(trueCpdag, est, data, params);
        s.arrPrecCommon = new ArrowheadPrecisionCommonEdges()
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
        double adjF1, adjPrec, arrPrec, arrPrecCommon, markovKS;
    }

    private static class CellResult {
        private double sAdjF1, sAdjP, sArrP, sArrPC, sKS;
        private double rAdjF1, rAdjP, rArrP, rArrPC, rKS;
        private int count = 0;

        void accumulateStart(RunStats s) {
            sAdjF1 += nanSafe(s.adjF1);
            sAdjP  += nanSafe(s.adjPrec);
            sArrP  += nanSafe(s.arrPrec);
            sArrPC += nanSafe(s.arrPrecCommon);
            sKS    += nanSafe(s.markovKS);
        }

        void accumulateRepaired(RunStats s) {
            rAdjF1 += nanSafe(s.adjF1);
            rAdjP  += nanSafe(s.adjPrec);
            rArrP  += nanSafe(s.arrPrec);
            rArrPC += nanSafe(s.arrPrecCommon);
            rKS    += nanSafe(s.markovKS);
            count++;
        }

        Averages startAverages()    {
            return avg(sAdjF1, sAdjP, sArrP, sArrPC, sKS);
        }
        Averages repairedAverages() {
            return avg(rAdjF1, rAdjP, rArrP, rArrPC, rKS);
        }

        private Averages avg(double f1, double ap, double arp, double arpc, double ks) {
            Averages a = new Averages();
            if (count == 0) return a;
            a.adjF1         = f1   / count;
            a.adjPrec       = ap   / count;
            a.arrPrec       = arp  / count;
            a.arrPrecCommon = arpc / count;
            a.markovKS      = ks   / count;
            return a;
        }

        private static double nanSafe(double v) {
            return Double.isNaN(v) ? 0.0 : v;
        }

        static class Averages {
            double adjF1, adjPrec, arrPrec, arrPrecCommon, markovKS;
        }
    }
}

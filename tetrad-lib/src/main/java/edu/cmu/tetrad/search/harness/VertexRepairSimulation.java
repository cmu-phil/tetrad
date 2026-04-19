/// ////////////////////////////////////////////////////////////////////////////
// Simulation study for VertexRepairSearch.                                  //
//                                                                           //
// Runs VertexRepairSearch starting from four different initial graphs       //
// (BOSS, PC, FGES, or Empty) and reports averaged statistics over           //
// multiple trials.                                                          //
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
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Simulation harness for VertexRepairSearch.
 *
 * <p>Four starting-graph scenarios are available via {@link StartingGraph}:
 * <ul>
 *   <li>{@code BOSS} — run BOSS to get an initial CPDAG, then repair.</li>
 *   <li>{@code PC}   — run PC to get an initial CPDAG, then repair.</li>
 *   <li>{@code FGES} — run FGES to get an initial CPDAG, then repair.</li>
 *   <li>{@code EMPTY} — start from a graph with no edges, then repair.</li>
 * </ul>
 *
 * <p>Set {@link #scenario} to the desired value and call {@link #run()}.
 *
 * <p>Statistics reported (averaged over {@link #NUM_RUNS} runs):
 * <ul>
 *   <li>Adjacency Precision</li>
 *   <li>Adjacency Recall</li>
 *   <li>Adjacency F1</li>
 *   <li>Arrowhead Precision</li>
 *   <li>Arrowhead Recall</li>
 *   <li>Arrowhead F1</li>
 *   <li>Arrowhead Precision (common edges only)</li>
 *   <li>Arrowhead Recall (common edges only)</li>
 *   <li>Markov Check KS p-value</li>
 *   <li>Number of edges</li>
 *   <li>BIC score</li>
 *   <li>Percentage of runs where adjacency F1 improves after repair</li>
 * </ul>
 */
public class VertexRepairSimulation {

    /**
     * Default constructor for the VertexRepairSimulation class.
     *
     * Initializes a new instance of the VertexRepairSimulation class.
     * This constructor sets up the necessary state for running the vertex
     * repair simulation process on directed acyclic graphs (DAGs).
     */
    public VertexRepairSimulation() {}

    // =========================================================================
    // Scenario enum — set this to choose the starting graph
    // =========================================================================

    /**
     * Which starting-graph scenario to use. Change this to try different scenarios.
     */
    public static final StartingGraph scenario = StartingGraph.BOSS;

    // =========================================================================
    // Simulation parameters — edit these as desired
    // =========================================================================

    /**
     * Conditioning set type passed to VertexRepairSearch.
     */
    private static final ConditioningSetType CONDITIONING_TYPE =
            ConditioningSetType.RECURSIVE_ADJUSTMENT;

    /**
     * Average degree (expected edges per node).
     */
    private static final double AVG_DEGREE = 4.0;

    /**
     * Number of nodes in the simulated graph.
     */
    private static final int NUM_NODES = 10;

    /**
     * Sample size for each simulated data set.
     */
    private static final int SAMPLE_SIZE = 1000;
    /**
     * Number of independent simulation runs to average over.
     */
    private static final int NUM_RUNS = 10;

    /**
     * Repair strategy passed to VertexRepairSearch.
     */
    private static final VertexRepairSearch.RepairStrategy REPAIR_STRATEGY =
            VertexRepairSearch.RepairStrategy.GLOBAL_QUEUE;
    /**
     * Graph type passed to VertexRepairSearch.
     */
    private static final VertexRepairSearch.AdjustmentGraphType GRAPH_TYPE =
            VertexRepairSearch.AdjustmentGraphType.CPDAG;
    /**
     * Alpha for the independence test.
     */
    private static final double ALPHA = 0.01;

    /**
     * Penalty discount for the score
     */
    private static final double PENALTY_DISCOUNT = 2.0;

    /**
     * Alpha for pruning.
     */
    private static final double PRUNE_ALPHA = 0.2;

    /**
     * The entry point of the VertexRepairSimulation application.
     * This method starts the simulation process by invoking the run method
     * of the VertexRepairSimulation class, which orchestrates the execution
     * of the vertex repair algorithm on directed acyclic graphs (DAGs).
     *
     * @param args command line arguments passed to the program; currently unused
     *             but reserved for potential future use.
     * @throws Exception if an error occurs during the execution of the simulation.
     */
    public static void main(String[] args) throws Exception {
        new VertexRepairSimulation().run();
    }

    // =========================================================================
    // Entry point
    // =========================================================================

    /**
     * Executes the VertexRepairSearch simulation process for a specified number of runs.
     * This method initializes configurations, simulates data, computes statistics, and
     * evaluates the effectiveness of repairing strategies on directed acyclic graphs (DAGs).
     *
     * The simulation involves the following steps:
     * 1. Simulating random DAGs and generating corresponding linear Gaussian data.
     * 2. Building the required independence tests and scoring mechanisms (e.g., SEM BIC score).
     * 3. Producing the starting graph based on a predefined scenario, evaluating key metrics,
     *    and accumulating initial statistics.
     * 4. Applying the VertexRepairSearch algorithm to refine the starting graph.
     * 5. Computing and accumulating final statistics for the repaired graph.
     *
     * Various metrics such as adjacency precision, adjacency recall, adjacency F1 score,
     * arrow precision, arrow recall, arrow F1 score, Markov equivalence, number of edges,
     * and Bayesian Information Criterion (BIC) are output and analyzed for performance.
     *
     * @throws Exception if any errors occur during the simulation or processing.
     */
    public void run() throws Exception {
        Preferences.userRoot().putBoolean("useAndersonDarling", false);

        System.out.println("=================================================");
        System.out.printf("VertexRepairSearch Simulation%n");
        System.out.printf("Scenario      : %s%n", scenario);
        System.out.printf("Nodes         : %d%n", NUM_NODES);
        System.out.printf("Avg degree    : %.1f%n", AVG_DEGREE);
        System.out.printf("Sample size   : %d%n", SAMPLE_SIZE);
        System.out.printf("Runs          : %d%n", NUM_RUNS);
        System.out.printf("Repair strategy: %s%n", REPAIR_STRATEGY);
        System.out.println("=================================================");

        // Accumulators for each statistic
        double sumAdjPrec = 0, sumAdjRec = 0, sumAdjF1 = 0;
        double sumArrPrec = 0, sumArrRec = 0, sumArrF1 = 0;
        double sumArrPrecCommon = 0, sumArrRecCommon = 0;
        double sumMarkovKS = 0;
        double sumEdges = 0, sumBic = 0;
        int cntAdjPrec = 0, cntAdjRec = 0, cntAdjF1 = 0;
        int cntArrPrec = 0, cntArrRec = 0, cntArrF1 = 0;
        int cntArrPrecCommon = 0, cntArrRecCommon = 0;
        int cntMarkovKS = 0;
        int cntEdges = 0, cntBic = 0;

        // Starting-graph accumulators
        double sumStartAdjPrec = 0, sumStartAdjRec = 0, sumStartAdjF1 = 0;
        double sumStartArrPrec = 0, sumStartArrRec = 0, sumStartArrF1 = 0;
        double sumStartArrPrecCommon = 0, sumStartArrRecCommon = 0;
        double sumStartMarkovKS = 0;
        double sumStartEdges = 0, sumStartBic = 0;
        int cntStartAdjPrec = 0, cntStartAdjRec = 0, cntStartAdjF1 = 0;
        int cntStartArrPrec = 0, cntStartArrRec = 0, cntStartArrF1 = 0;
        int cntStartArrPrecCommon = 0, cntStartArrRecCommon = 0;
        int cntStartMarkovKS = 0;
        int cntStartEdges = 0, cntStartBic = 0;

        // Adjacency F1 improvement tracking:
        // - cntAdjF1Comparable: runs where BOTH start and repaired adj F1 are defined
        // - cntAdjF1Improved:   subset of above where repaired adj F1 > start adj F1
        int cntAdjF1Comparable = 0;
        int cntAdjF1Improved   = 0;

        DecimalFormat df = new DecimalFormat("0.0000");

        for (int run = 1; run <= NUM_RUNS; run++) {
            RandomUtil.getInstance().setSeed(run * 17L + 31L);

            // ------------------------------------------------------------------
            // 1. Simulate a random DAG and linear Gaussian data
            // ------------------------------------------------------------------
            int numEdges = (int) Math.round(NUM_NODES * AVG_DEGREE / 2.0);

            List<Node> variables = new ArrayList<>();
            for (int i = 0; i < NUM_NODES; i++) {
                ContinuousVariable var = new ContinuousVariable("x" + i);
                variables.add(var);
            }

            Graph trueDAG = RandomGraph.randomGraphRandomForwardEdges(
                    variables, 0, numEdges, 100, 100, 100, false);

            SemPm semPm = new SemPm(trueDAG);
            SemIm semIm = new SemIm(semPm);
            DataSet data = semIm.simulateData(SAMPLE_SIZE, false);

            // True CPDAG for comparison
            Graph trueCpdag = GraphTransforms.dagToCpdag(trueDAG);

            // ------------------------------------------------------------------
            // 2. Build independence test (Fisher Z for continuous data) and SEM BIC
            // ------------------------------------------------------------------
            IndependenceTest fisherZ = buildFisherZ(data);
            SemBicScore score = buildSemBic(data);

            // ------------------------------------------------------------------
            // 3. Produce the starting graph according to the chosen scenario
            // ------------------------------------------------------------------
            Graph startingGraph = buildStartingGraph(data, fisherZ, score, trueDAG.getNodes());
            startingGraph = GraphUtils.replaceNodes(startingGraph, trueDAG.getNodes());

            // Stats on the starting graph (before repair)
            Statistics startStats = computeStats(trueCpdag, startingGraph, data, fisherZ, score);

            System.out.printf("Start  | AdjP=%-6s AdjR=%-6s AdjF1=%-6s " +
                            "ArrP=%-6s ArrR=%-6s ArrF1=%-6s " +
                            "ArrPC=%-6s ArrRC=%-6s MarkovKS=%-6s " +
                            "Edges=%-4s BIC=%-10s%n",
                    df.format(startStats.adjPrec), df.format(startStats.adjRec),
                    df.format(startStats.adjF1),
                    df.format(startStats.arrPrec), df.format(startStats.arrRec),
                    df.format(startStats.arrF1),
                    df.format(startStats.arrPrecCommon), df.format(startStats.arrRecCommon),
                    df.format(startStats.markovKS),
                    startStats.numEdges,
                    Double.isNaN(startStats.bic) ? "N/A" : df.format(startStats.bic));

            // Starting graph accumulation
            if (!Double.isNaN(startStats.adjPrec))       { sumStartAdjPrec       += startStats.adjPrec;       cntStartAdjPrec++; }
            if (!Double.isNaN(startStats.adjRec))        { sumStartAdjRec        += startStats.adjRec;        cntStartAdjRec++; }
            if (!Double.isNaN(startStats.adjF1))         { sumStartAdjF1         += startStats.adjF1;         cntStartAdjF1++; }
            if (!Double.isNaN(startStats.arrPrec))       { sumStartArrPrec       += startStats.arrPrec;       cntStartArrPrec++; }
            if (!Double.isNaN(startStats.arrRec))        { sumStartArrRec        += startStats.arrRec;        cntStartArrRec++; }
            if (!Double.isNaN(startStats.arrF1))         { sumStartArrF1         += startStats.arrF1;         cntStartArrF1++; }
            if (!Double.isNaN(startStats.arrPrecCommon)) { sumStartArrPrecCommon += startStats.arrPrecCommon; cntStartArrPrecCommon++; }
            if (!Double.isNaN(startStats.arrRecCommon))  { sumStartArrRecCommon  += startStats.arrRecCommon;  cntStartArrRecCommon++; }
            if (!Double.isNaN(startStats.markovKS))      { sumStartMarkovKS      += startStats.markovKS;      cntStartMarkovKS++; }
            if (!Double.isNaN(startStats.numEdges))      { sumStartEdges         += startStats.numEdges;      cntStartEdges++; }
            if (!Double.isNaN(startStats.bic))           { sumStartBic           += startStats.bic;           cntStartBic++; }

            // ------------------------------------------------------------------
            // 4. Run VertexRepairSearch
            // ------------------------------------------------------------------
            VertexRepairSearch repair = new VertexRepairSearch(
                    startingGraph, fisherZ, CONDITIONING_TYPE);
            repair.setGraphType(GRAPH_TYPE);
            repair.setRepairStrategy(REPAIR_STRATEGY);
            repair.setUseAndersonDarling(false);
            repair.setPruneAlpha(PRUNE_ALPHA);

            Graph repairedGraph = repair.search();

            // ------------------------------------------------------------------
            // 5. Compute statistics
            // ------------------------------------------------------------------
            Statistics stats = computeStats(trueCpdag, repairedGraph, data, fisherZ, score);

            // Track adjacency F1 improvement (only count runs where both values are defined)
            boolean improvedThisRun = false;
            if (!Double.isNaN(startStats.adjF1) && !Double.isNaN(stats.adjF1)) {
                cntAdjF1Comparable++;
                if (stats.adjF1 >= startStats.adjF1) {
                    cntAdjF1Improved++;
                    improvedThisRun = true;
                }
            }

            System.out.printf("Run %2d | AdjP=%-6s AdjR=%-6s AdjF1=%-6s " +
                            "ArrP=%-6s ArrR=%-6s ArrF1=%-6s " +
                            "ArrPC=%-6s ArrRC=%-6s MarkovKS=%-6s " +
                            "Edges=%-4s BIC=%-10s AdjF1Improved=%s%n",
                    run,
                    df.format(stats.adjPrec), df.format(stats.adjRec),
                    df.format(stats.adjF1),
                    df.format(stats.arrPrec), df.format(stats.arrRec),
                    df.format(stats.arrF1),
                    df.format(stats.arrPrecCommon), df.format(stats.arrRecCommon),
                    df.format(stats.markovKS),
                    stats.numEdges,
                    Double.isNaN(stats.bic) ? "N/A" : df.format(stats.bic),
                    improvedThisRun ? "Y" : "N");

            // Repaired graph accumulation
            if (!Double.isNaN(stats.adjPrec))       { sumAdjPrec       += stats.adjPrec;       cntAdjPrec++; }
            if (!Double.isNaN(stats.adjRec))        { sumAdjRec        += stats.adjRec;        cntAdjRec++; }
            if (!Double.isNaN(stats.adjF1))         { sumAdjF1         += stats.adjF1;         cntAdjF1++; }
            if (!Double.isNaN(stats.arrPrec))       { sumArrPrec       += stats.arrPrec;       cntArrPrec++; }
            if (!Double.isNaN(stats.arrRec))        { sumArrRec        += stats.arrRec;        cntArrRec++; }
            if (!Double.isNaN(stats.arrF1))         { sumArrF1         += stats.arrF1;         cntArrF1++; }
            if (!Double.isNaN(stats.arrPrecCommon)) { sumArrPrecCommon += stats.arrPrecCommon; cntArrPrecCommon++; }
            if (!Double.isNaN(stats.arrRecCommon))  { sumArrRecCommon  += stats.arrRecCommon;  cntArrRecCommon++; }
            if (!Double.isNaN(stats.markovKS))      { sumMarkovKS      += stats.markovKS;      cntMarkovKS++; }
            if (!Double.isNaN(stats.numEdges))      { sumEdges         += stats.numEdges;      cntEdges++; }
            if (!Double.isNaN(stats.bic))           { sumBic           += stats.bic;           cntBic++; }
        }

        // ------------------------------------------------------------------
        // 6. Report settings and averages
        // ------------------------------------------------------------------
        printSettings();
        System.out.printf("STARTING GRAPH AVERAGES (n=%d runs):%n", NUM_RUNS);
        System.out.printf("  Adjacency Precision              : %s  (n=%d)%n", avg(sumStartAdjPrec,       cntStartAdjPrec,       df), cntStartAdjPrec);
        System.out.printf("  Adjacency Recall                 : %s  (n=%d)%n", avg(sumStartAdjRec,        cntStartAdjRec,        df), cntStartAdjRec);
        System.out.printf("  Adjacency F1                     : %s  (n=%d)%n", avg(sumStartAdjF1,         cntStartAdjF1,         df), cntStartAdjF1);
        System.out.printf("  Arrowhead Precision              : %s  (n=%d)%n", avg(sumStartArrPrec,       cntStartArrPrec,       df), cntStartArrPrec);
        System.out.printf("  Arrowhead Recall                 : %s  (n=%d)%n", avg(sumStartArrRec,        cntStartArrRec,        df), cntStartArrRec);
        System.out.printf("  Arrowhead F1                     : %s  (n=%d)%n", avg(sumStartArrF1,         cntStartArrF1,         df), cntStartArrF1);
        System.out.printf("  Arrowhead Precision (common adj) : %s  (n=%d)%n", avg(sumStartArrPrecCommon, cntStartArrPrecCommon, df), cntStartArrPrecCommon);
        System.out.printf("  Arrowhead Recall    (common adj) : %s  (n=%d)%n", avg(sumStartArrRecCommon,  cntStartArrRecCommon,  df), cntStartArrRecCommon);
        System.out.printf("  Markov KS p-value                : %s  (n=%d)%n", avg(sumStartMarkovKS,      cntStartMarkovKS,      df), cntStartMarkovKS);
        System.out.printf("  Number of edges                  : %s  (n=%d)%n", avg(sumStartEdges,         cntStartEdges,         df), cntStartEdges);
        System.out.printf("  BIC score                        : %s  (n=%d)%n", avg(sumStartBic,           cntStartBic,           df), cntStartBic);
        System.out.println("-------------------------------------------------");
        System.out.printf("REPAIRED GRAPH AVERAGES (n=%d runs):%n", NUM_RUNS);
        System.out.printf("  Adjacency Precision              : %s  (n=%d)%n", avg(sumAdjPrec,       cntAdjPrec,       df), cntAdjPrec);
        System.out.printf("  Adjacency Recall                 : %s  (n=%d)%n", avg(sumAdjRec,        cntAdjRec,        df), cntAdjRec);
        System.out.printf("  Adjacency F1                     : %s  (n=%d)%n", avg(sumAdjF1,         cntAdjF1,         df), cntAdjF1);
        System.out.printf("  Arrowhead Precision              : %s  (n=%d)%n", avg(sumArrPrec,       cntArrPrec,       df), cntArrPrec);
        System.out.printf("  Arrowhead Recall                 : %s  (n=%d)%n", avg(sumArrRec,        cntArrRec,        df), cntArrRec);
        System.out.printf("  Arrowhead F1                     : %s  (n=%d)%n", avg(sumArrF1,         cntArrF1,        df), cntArrF1);
        System.out.printf("  Arrowhead Precision (common adj) : %s  (n=%d)%n", avg(sumArrPrecCommon, cntArrPrecCommon, df), cntArrPrecCommon);
        System.out.printf("  Arrowhead Recall    (common adj) : %s  (n=%d)%n", avg(sumArrRecCommon,  cntArrRecCommon,  df), cntArrRecCommon);
        System.out.printf("  Markov KS p-value                : %s  (n=%d)%n", avg(sumMarkovKS,      cntMarkovKS,      df), cntMarkovKS);
        System.out.printf("  Number of edges                  : %s  (n=%d)%n", avg(sumEdges,         cntEdges,         df), cntEdges);
        System.out.printf("  BIC score                        : %s  (n=%d)%n", avg(sumBic,           cntBic,           df), cntBic);
        System.out.println("-------------------------------------------------");
        System.out.printf("IMPROVEMENT SUMMARY:%n");
        System.out.printf("  Adjacency F1 improved after repair: %s  (%d of %d comparable runs)%n",
                cntAdjF1Comparable == 0
                        ? "N/A"
                        : df.format(100.0 * cntAdjF1Improved / cntAdjF1Comparable) + "%",
                cntAdjF1Improved, cntAdjF1Comparable);
        System.out.println("=================================================");
    }

    private String avg(double sum, int count, DecimalFormat df) {
        return count == 0 ? "N/A" : df.format(sum / count);
    }

    private static @NotNull SemBicScore buildSemBic(DataSet data) {
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(PENALTY_DISCOUNT);
        return score;
    }

    private void printSettings() {
        System.out.println("=================================================");
        System.out.println("SETTINGS");
        System.out.println("-------------------------------------------------");
        System.out.printf("  Scenario                         : %s%n", scenario);
        System.out.printf("  Nodes                            : %d%n", NUM_NODES);
        System.out.printf("  Average degree                   : %.1f%n", AVG_DEGREE);
        System.out.printf("  Number of edges                  : %d%n", (int) Math.round(NUM_NODES * AVG_DEGREE / 2.0));
        System.out.printf("  Sample size                      : %d%n", SAMPLE_SIZE);
        System.out.printf("  Number of runs                   : %d%n", NUM_RUNS);
        System.out.println("-------------------------------------------------");
        System.out.printf("  Independence test                : Fisher Z%n");
        System.out.printf("  Alpha                            : %.4f%n", ALPHA);
        System.out.printf("  Score                            : SEM BIC%n");
        System.out.printf("  Penalty discount                 : %.1f%n", PENALTY_DISCOUNT);
        System.out.printf("  Prune alpha                      : %.4f%n", PRUNE_ALPHA);
        System.out.println("-------------------------------------------------");
        System.out.printf("  Graph type                       : %s%n", GRAPH_TYPE);
        System.out.printf("  Repair strategy                  : %s%n", REPAIR_STRATEGY);
        System.out.printf("  Conditioning set type            : %s%n", CONDITIONING_TYPE);
        System.out.println("=================================================");
    }

    private Graph buildStartingGraph(DataSet data, IndependenceTest test, edu.cmu.tetrad.search.score.Score score,
                                     List<Node> nodes) throws Exception {
        return switch (scenario) {
            case BOSS -> runBoss(data, score);
            case PC -> runPc(test);
            case FGES -> runFges(score);
            case EMPTY -> emptyGraph(nodes);
        };
    }

    // =========================================================================
    // Starting-graph construction
    // =========================================================================

    private Graph runBoss(DataSet data, edu.cmu.tetrad.search.score.Score score) throws Exception {
        Boss boss = new Boss(score);
        PermutationSearch ps = new PermutationSearch(boss);
        return ps.search();
    }

    private Graph runPc(IndependenceTest test) throws Exception {
        Pc pc = new Pc(test);
        return pc.search();
    }

    private Graph runFges(edu.cmu.tetrad.search.score.Score score) throws Exception {
        Fges fges = new Fges(score);
        return fges.search();
    }

    private Graph emptyGraph(List<Node> nodes) {
        return new EdgeListGraph(nodes);
    }

    private IndependenceTest buildFisherZ(DataSet data) {
        return new IndTestFisherZ(data, ALPHA);
    }

    // =========================================================================
    // Independence test construction
    // =========================================================================

    /**
     * Computes all reported statistics for one run.
     *
     * <p>Each statistic is obtained from a Tetrad {@link Statistic} implementation.
     * Edge count is read directly from the graph. BIC is computed via
     */
    private Statistics computeStats(Graph trueCpdag, Graph estimated,
                                    DataSet data, IndependenceTest test,
                                    SemBicScore score) {
        Statistics s = new Statistics();
        Parameters params = new Parameters();

        // Ensure estimated is in CPDAG form for fair comparison
        Graph estCpdag = estimated;
        try {
            if (estimated.paths().isLegalDag()) {
                estCpdag = GraphTransforms.dagToCpdag(estimated);
            } else if (estimated.paths().isLegalCpdag() || estimated.paths().isLegalPdag()) {
                Graph dag = GraphTransforms.dagFromCpdag(estimated);
                estCpdag = GraphTransforms.dagToCpdag(dag);
            }
        } catch (Exception ignored) {
            // leave as-is if canonicalization fails
        }

        s.adjPrec       = new AdjacencyPrecision().getValue(trueCpdag, estCpdag, data, params);
        s.adjRec        = new AdjacencyRecall().getValue(trueCpdag, estCpdag, data, params);
        s.adjF1         = new F1Adj().getValue(trueCpdag, estCpdag, data, params);
        s.arrPrec       = new ArrowheadPrecision().getValue(trueCpdag, estCpdag, data, params);
        s.arrRec        = new ArrowheadRecall().getValue(trueCpdag, estCpdag, data, params);
        s.arrF1         = new F1Arrow().getValue(trueCpdag, estCpdag, data, params);
        s.arrPrecCommon = new ArrowheadPrecisionCommonEdges().getValue(trueCpdag, estCpdag, data, params);
        s.arrRecCommon  = new ArrowheadRecallCommonEdges().getValue(trueCpdag, estCpdag, data, params);

        // Markov KS: evaluated on the estimated graph against the data
        s.markovKS = new MarkovCheckKolmogorovSmirnoffP(
                new FisherZ(), ConditioningSetType.ORDERED_LOCAL_MARKOV_PROPERTY, params)
                .getValue(trueCpdag, estCpdag, data, params);

        s.numEdges = new NumberEdgesEst().getValue(trueCpdag, estCpdag, data, params);
        s.bic = new BicEst().getValue(trueCpdag, estCpdag, data, params);

        return s;
    }

    // =========================================================================
    // Hooks — fill these in
    // =========================================================================

    /**
     * Returns the number of edges in {@code graph}.
     *
     * <p>Currently delegates to {@link Graph#getNumEdges()}. Override or adjust
     * if you need a different edge-counting convention (e.g. counting only
     * directed edges, or working on the CPDAG form).
     */
    private double countEdges(Graph graph) {
        return graph.getNumEdges();
    }

    /**
     * Which algorithm to use to produce the initial graph before repair.
     * Set {@link VertexRepairSimulation#scenario} to the desired value.
     */
    public enum StartingGraph {
        /**
         * Run BOSS and use its output as the starting graph.
         */
        BOSS,
        /**
         * Run PC and use its output as the starting graph.
         */
        PC,
        /**
         * Run FGES and use its output as the starting graph.
         */
        FGES,
        /**
         * Start from an empty graph (no edges).
         */
        EMPTY
    }

    // =========================================================================
    // Inner types
    // =========================================================================

    /**
     * Holds one run's worth of statistics.
     */
    private static class Statistics {
        double adjPrec, adjRec, adjF1;
        double arrPrec, arrRec, arrF1;
        double arrPrecCommon, arrRecCommon;
        double markovKS;
        double numEdges;
        double bic;
    }
}

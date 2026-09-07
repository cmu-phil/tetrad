package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.Flop;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.FlopInitialOrder;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;

import java.util.List;

/**
 * Six-arm study on linear Gaussian data. Arms:
 * <ol>
 *     <li>BOSS-R: BOSS, 21 starts, random restarts (current default behavior).</li>
 *     <li>BOSS-ILS: BOSS, 21 starts, ILS restarts (useIlsRestarts option).</li>
 *     <li>BOSS-ILS-FO: BOSS-ILS started from the FLOP principled initial order.</li>
 *     <li>FLOP0: FLOP, no restarts (single search from the principled initial order).</li>
 *     <li>FLOP20: FLOP, 20 ILS restarts (21 searches, matching the BOSS arms' search count).</li>
 *     <li>FLOP-T: FLOP, time-matched to BOSS-ILS on the same instance. The restart budget is chosen per
 *     instance as (BOSS-ILS wall-clock) / (FLOP per-search cost estimated from the FLOP20 arm), capped at 500;
 *     the chosen budget is printed at the end of each row. This arm tests whether FLOP's cheaper searches,
 *     given equal wall-clock rather than equal restarts, reach the BOSS arms' BIC plateau.</li>
 * </ol>
 * Per arm: wall-clock ms, SHD of the estimated CPDAG against the CPDAG of the true DAG, and dBIC: common-scorer
 * SemBicScore BIC of the estimate minus that of the true DAG (Tetrad convention, higher is better; positive =
 * beat the truth).
 * <p>
 * Drop into tetrad-lib/src/test/java/edu/cmu/tetrad/search/harness/ and run main. One row prints per instance.
 * <p>
 * Caveats for interpretation: the BOSS arms use 4 threads while FLOP is single-threaded, so times are not a
 * like-for-like measure of algorithmic cost; and SemIm's default coefficient ranges admit weaker edges than the
 * FLOP paper's +/-[0.25, 1], so absolute SHDs will run higher than the paper's figures. Arm-to-arm comparisons
 * within a row are unaffected.
 */
public class BossIlsFlopStudy {

    private static final int NUM_ARMS = 6;
    private static final int MAX_TIME_MATCHED_RESTARTS = 500;

    /**
     * Constructs a new instance of the BossIlsFlopStudy class.
     *
     * This constructor initializes the object and allows access to the methods
     * and functionality of the BossIlsFlopStudy class, which is designed to analyze
     * and compare the performance of BOSS and FLOP variants on specific tasks such
     * as random DAG simulations and evaluations based on runtime, structural
     * Hamming distance (SHD), and Bayesian Information Criterion (BIC) differences.
     */
    public BossIlsFlopStudy() {
    }

    /**
     * The main entry point for the application, responsible for the execution and comparison of different
     * algorithms (BOSS and FLOP variants) across a specified number of repetitions. The method performs
     * simulations using random DAGs, evaluates algorithms based on metrics such as runtime, structural
     * Hamming distance (SHD), and difference in Bayesian Information Criterion (BIC), and summarizes the results.
     *
     * @param args Command-line arguments passed to the program (not used directly in the implementation).
     * @throws Exception If any error occurs during execution, such as issues in data simulation or algorithm processing.
     */
    public static void main(String[] args) throws Exception {
        int p = 100;
        int avgDeg = 6;
        int n = 1000;
        int reps = 20;
        double penalty = 2.0;
        int numStarts = 21;      // BOSS arms: 1 initial + 20 restarts.
        int numRestarts = 20;    // FLOP20: 20 ILS restarts after the initial search.
        long seedBase = 1000L;

        int numEdges = p * avgDeg / 2;

        System.out.printf("p=%d avgDeg=%d n=%d penalty=%.1f reps=%d starts=%d%n",
                p, avgDeg, n, penalty, reps, numStarts);
        System.out.println("Per arm: ms / shd / dBIC (est minus true; positive = beat the truth)");
        System.out.println("rep |        BOSS-R |      BOSS-ILS |   BOSS-ILS-FO |         FLOP0 |        FLOP20 |        FLOP-T");

        long[] sumMs = new long[NUM_ARMS];
        long[] sumShd = new long[NUM_ARMS];
        double[] sumDBic = new double[NUM_ARMS];
        long sumTRestarts = 0;

        for (int rep = 0; rep < reps; rep++) {
            long seed = seedBase + rep;

            Graph trueDag = RandomGraph.randomGraph(p, 0, numEdges, 100, 100, 100, false);
            SemIm im = new SemIm(new SemPm(trueDag));
            DataSet data = im.simulateData(n, false);

            SemBicScore refScore = new SemBicScore(data, true);
            refScore.setPenaltyDiscount(penalty);
            double trueBic = bic(trueDag, refScore);

            long[] ms = new long[NUM_ARMS];
            int[] shd = new int[NUM_ARMS];
            double[] dBic = new double[NUM_ARMS];

            // Arm 0: BOSS, random restarts.
            runBoss(data, penalty, numStarts, false, null, seed, trueDag, refScore, trueBic, ms, shd, dBic, 0);

            // Arm 1: BOSS, ILS restarts.
            runBoss(data, penalty, numStarts, true, null, seed, trueDag, refScore, trueBic, ms, shd, dBic, 1);

            // Arm 2: BOSS, ILS restarts, FLOP initial order.
            List<Node> flopOrder = FlopInitialOrder.initialOrder(data);
            runBoss(data, penalty, numStarts, true, flopOrder, seed, trueDag, refScore, trueBic, ms, shd, dBic, 2);

            // Arm 3: FLOP0.
            runFlop(data, penalty, 0, seed, trueDag, refScore, trueBic, ms, shd, dBic, 3);

            // Arm 4: FLOP20.
            runFlop(data, penalty, numRestarts, seed, trueDag, refScore, trueBic, ms, shd, dBic, 4);

            // Arm 5: FLOP time-matched to BOSS-ILS, per-search cost estimated from the FLOP20 arm.
            double perSearchMs = Math.max(1.0, ms[4] / (double) (numRestarts + 1));
            int tRestarts = (int) Math.min(MAX_TIME_MATCHED_RESTARTS,
                    Math.max(numRestarts, Math.round(ms[1] / perSearchMs) - 1));
            runFlop(data, penalty, tRestarts, seed, trueDag, refScore, trueBic, ms, shd, dBic, 5);
            sumTRestarts += tRestarts;

            System.out.printf("%3d |", rep);
            for (int a = 0; a < NUM_ARMS; a++) {
                System.out.printf(" %5d/%3d/%5.0f |", ms[a], shd[a], dBic[a]);
                sumMs[a] += ms[a];
                sumShd[a] += shd[a];
                sumDBic[a] += dBic[a];
            }
            System.out.printf(" (T: r=%d)%n", tRestarts);
        }

        System.out.print("AVG |");
        for (int a = 0; a < NUM_ARMS; a++) {
            System.out.printf(" %5d/%5.1f/%4.0f |", sumMs[a] / reps, (double) sumShd[a] / reps, sumDBic[a] / reps);
        }
        System.out.printf(" (T: avg r=%d)%n", sumTRestarts / reps);
    }

    private static void runBoss(DataSet data, double penalty, int numStarts, boolean ils, List<Node> initialOrder,
                                long seed, Graph trueDag, SemBicScore refScore, double trueBic,
                                long[] ms, int[] shd, double[] dBic, int arm) throws Exception {
        SemBicScore score = new SemBicScore(data, true);
        score.setPenaltyDiscount(penalty);
        Boss boss = new Boss(score);
        boss.setNumStarts(numStarts);
        boss.setNumThreads(4);
        boss.setUseBes(false);
        boss.setUseIlsRestarts(ils);
        PermutationSearch ps = new PermutationSearch(boss);
        ps.setSeed(seed);
        if (initialOrder != null) ps.setOrder(initialOrder);

        long t0 = System.currentTimeMillis();
        Graph est = ps.search();
        ms[arm] = System.currentTimeMillis() - t0;
        shd[arm] = GraphSearchUtils.structuralhammingdistance(trueDag, est, true);
        dBic[arm] = bic(est, refScore) - trueBic;
    }

    private static void runFlop(DataSet data, double penalty, int numRestarts, long seed, Graph trueDag,
                                SemBicScore refScore, double trueBic,
                                long[] ms, int[] shd, double[] dBic, int arm) throws Exception {
        Flop flop = new Flop(data);
        flop.setPenaltyDiscount(penalty);
        flop.setNumRestarts(numRestarts);
        flop.setSeed(seed);

        long t0 = System.currentTimeMillis();
        Graph est = flop.search();
        ms[arm] = System.currentTimeMillis() - t0;
        shd[arm] = GraphSearchUtils.structuralhammingdistance(trueDag, est, true);
        dBic[arm] = bic(est, refScore) - trueBic;
    }

    /**
     * Common-scorer BIC (Tetrad convention, higher is better) of the given graph; if the graph is a CPDAG, a DAG
     * is extracted from it first. The true DAG is scored as-is.
     */
    private static double bic(Graph graph, SemBicScore score) {
        Graph dag = graph.paths().isLegalDag() ? graph : GraphTransforms.dagFromCpdag(graph);
        List<Node> scoreVars = score.getVariables();
        double total = 0.0;

        for (Node node : dag.getNodes()) {
            List<Node> parents = dag.getParents(node);
            int[] pa = new int[parents.size()];
            for (int i = 0; i < pa.length; i++) {
                pa[i] = indexOfByName(scoreVars, parents.get(i).getName());
            }
            total += score.localScore(indexOfByName(scoreVars, node.getName()), pa);
        }

        return total;
    }

    private static int indexOfByName(List<Node> vars, String name) {
        for (int i = 0; i < vars.size(); i++) if (vars.get(i).getName().equals(name)) return i;
        throw new IllegalArgumentException("Variable not found: " + name);
    }
}

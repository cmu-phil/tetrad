/// ////////////////////////////////////////////////////////////////////////////
// VarsortabilityHarness.java
//
// Tests whether BOSS performance correlates with varsortability — i.e. whether
// BOSS is sensitive to (and potentially exploiting) this simulation artifact.
//
// Design
// ------
// 1. Generate random DAGs of a fixed size.
// 2. For each DAG, simulate data at MULTIPLE varsortability levels by scaling
//    error variances so that marginal variances are either:
//      (a) monotone with causal order  → high varsortability (≈ 1)
//      (b) random / reversed           → low varsortability  (≈ 0)
//    This is done WITHOUT changing the graph or the structural coefficients,
//    so any performance difference is purely due to the variance pattern.
// 3. Run BOSS on each dataset and record SHD and Adjacency F1.
// 4. Print a summary table of mean SHD and F1 grouped by varsortability bucket.
//
// Varsortability (Reisach et al. 2021)
// -------------------------------------
// VS = fraction of edges (i→j) where Var(X_i) < Var(X_j).
// A value near 1 means downstream nodes have higher marginal variance —
// the artifact that benefits gradient-based methods.
//
// How we control VS
// -----------------
// Rather than heuristically scaling error variances by topological rank,
// we ANALYTICALLY SOLVE for the error variances O that produce a prescribed
// marginal variance profile. This guarantees coverage of the full [0,1] VS
// range regardless of graph structure or coefficient magnitudes.
//
//   HIGH VS:  target marginal variances increase monotonically with topo rank
//   LOW VS:   target marginal variances decrease monotonically with topo rank
//   MID VS:   linearly interpolate between the two extremes
//
// Given a target marginal variance vector v*, we solve for O exactly via:
//   O[i] = v*[i] - sum_{j,k<i} B[i][j] B[i][k] Sigma[j][k]
// where Sigma is propagated using the target variances on the diagonal.
//
// Usage
// -----
// Drop this file into the Tetrad source tree, adjust the package and imports
// to match your version, and run main(). Output goes to stdout as a table.
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;  // adjust package as needed

import edu.cmu.tetrad.algcomparison.simulation.DaoSimulation;
import edu.cmu.tetrad.algcomparison.statistic.StructuralHammingDistance;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.LegendreBicScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.*;

/**
 * Harness to test whether BOSS performance correlates with varsortability.
 *
 * <p>Run {@link #main(String[])} to execute the experiment and print results.
 */
public class VarsortabilityHarness {

    /**
     * Constructs a new VarsortabilityHarness.
     */
    public VarsortabilityHarness() {}

    // -----------------------------------------------------------------------
    // Experiment parameters — adjust freely
    // -----------------------------------------------------------------------

    /** Number of random DAGs to generate (each tested at all VS conditions). */
    private static final int NUM_GRAPHS = 30;

    /** Number of variables per DAG. */
    private static final int NUM_NODES = 20;

    /** Average number of edges per node (expected edges ≈ NUM_NODES * AVG_DEGREE / 2). */
    private static final double AVG_DEGREE = 4.0;

    /** Number of data rows per dataset. */
    private static final int SAMPLE_SIZE = 1000;

    /** BIC penalty discount for BOSS. */
    private static final double PENALTY_DISCOUNT = 2.0;

    /** Number of varsortability levels to test between 0 and 1 (inclusive). */
    private static final int NUM_VS_LEVELS = 5;

    /** Fixed coefficient magnitude range for structural coefficients. */
//    private static final double COEF_LO = 0.3;
//    private static final double COEF_HI = 0.9;

    private static final double COEF_LO = -0.9;
    private static final double COEF_HI = 0.9;

    /**
     * Marginal variance range for the prescribed profile.
     * VAR_LO is assigned to the "unfavoured" end of the topological order;
     * VAR_HI to the "favoured" end. Increase VAR_HI if ActualVS is not
     * reaching the extremes.
     */
//    private static final double VAR_LO = 0.5;
//    private static final double VAR_HI = 4.0;

    private static final double VAR_LO = 0.5;  // was 0.5
    private static final double VAR_HI = 8.0;  // was 4.0

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    /**
     * Executes the main simulation and evaluation routine to assess
     * the sensitivity of the BOSS algorithm to varsortability (VS).
     * The program generates random directed acyclic graphs (DAGs), simulates
     * data for different levels of varsortability, and evaluates the BOSS
     * algorithm's performance via various metrics such as Structural Hamming
     * Distance (SHD) and F1 scores for adjacency and arrowhead structures.
     *
     * @param args Command-line arguments (not used in this program).
     */
    public static void main(String[] args) {
        RandomUtil.getInstance().setSeed(42L);

        double[] vsLevels = linspace(0.0, 1.0, NUM_VS_LEVELS);

        double[][] sumSHD = new double[NUM_VS_LEVELS][1];
        double[][] sumAdjF1 = new double[NUM_VS_LEVELS][1];
        double[][] sumArrowF1 = new double[NUM_VS_LEVELS][1];
        double[][] sumVS = new double[NUM_VS_LEVELS][1];
        int[] counts = new int[NUM_VS_LEVELS];

        System.out.println("Running " + NUM_GRAPHS + " graphs × " + NUM_VS_LEVELS
                + " VS levels × 1 dataset each...\n");

        for (int g = 0; g < NUM_GRAPHS; g++) {

            // 1. Generate a random DAG and coefficient matrix B
            Graph dag = randomDag(NUM_NODES, AVG_DEGREE);
            List<Node> order = DaoSimulation.soficOrder(dag);
            int p = order.size();
            double[][] B = sampleCoefficients(dag, order, p);

            for (int vi = 0; vi < NUM_VS_LEVELS; vi++) {
                double targetVS = vsLevels[vi];

                // 2. Analytically solve for error variances that produce the
                //    desired marginal variance profile for this targetVS
                double[] O = designErrorVariances(B, targetVS, p);

                // 3. Simulate data
                double[][] X = simulate(B, O, p, SAMPLE_SIZE);

                // 4. Compute actual varsortability of this dataset
                double[] margVar = computeMarginalVariances(B, O, p);
                double actualVS = computeVarsortability(dag, order, margVar);

                // 5. Build Tetrad DataSet
                DataSet dataSet = buildDataSet(X, order, p, SAMPLE_SIZE);

                // 6. Run BOSS
                Graph estimated = runBoss(dataSet);

                // 7. Compare to true CPDAG
                Graph trueCpdag = GraphTransforms.dagToCpdag(dag);
                GraphUtils.replaceNodes(estimated, trueCpdag.getNodes());

                Parameters params = new Parameters();
                double shd = new StructuralHammingDistance().getValue(trueCpdag, estimated, params);
                double adjF1 = adjacencyF1(trueCpdag, estimated);
                double arrowF1 = arrowheadF1(trueCpdag, estimated);

                sumSHD[vi][0] += shd;
                sumAdjF1[vi][0] += adjF1;
                sumArrowF1[vi][0] += arrowF1;
                sumVS[vi][0] += actualVS;
                counts[vi]++;
            }

            if ((g + 1) % 10 == 0)
                System.out.println("  Completed graph " + (g + 1) + " / " + NUM_GRAPHS);
        }

        // Print results table
        System.out.println();
        System.out.printf("%-12s  %-12s  %-10s  %-10s  %-12s%n",
                "TargetVS", "ActualVS", "MeanSHD", "MeanAdjF1", "MeanArrowF1");
        System.out.println("-".repeat(62));

        for (int vi = 0; vi < NUM_VS_LEVELS; vi++) {
            int n = counts[vi];
            System.out.printf("%-12.3f  %-12.3f  %-10.3f  %-10.3f  %-12.3f%n",
                    vsLevels[vi],
                    sumVS[vi][0] / n,
                    sumSHD[vi][0] / n,
                    sumAdjF1[vi][0] / n,
                    sumArrowF1[vi][0] / n);
        }

        System.out.println();
        System.out.println("If BOSS is sensitive to varsortability, SHD should decrease");
        System.out.println("(and F1 increase) monotonically from low VS to high VS.");
        System.out.println("Flat results across the table → BOSS is NOT exploiting VS.");
    }

    // -----------------------------------------------------------------------
    // Core: analytically solve for error variances given a target VS level
    // -----------------------------------------------------------------------

    /**
     * Designs error variances O such that the resulting marginal variances
     * follow a prescribed profile aimed at the given targetVS.
     *
     * <p>Step 1: build a target marginal variance vector by interpolating
     * between a monotone-increasing profile (VS≈1) and a monotone-decreasing
     * profile (VS≈0), both spanning [VAR_LO, VAR_HI].
     *
     * <p>Step 2: call {@link #solveErrorVariances} to find the unique O that
     * produces exactly those marginal variances under B.
     *
     * @param B        lower-triangular coefficient matrix (p×p)
     * @param targetVS desired varsortability in [0, 1]
     * @param p        number of variables
     * @return error variance vector O (length p), all entries &gt; 0
     */
    private static double[] designErrorVariances(double[][] B, double targetVS, int p) {
        double[] targetMargVar = new double[p];

        for (int i = 0; i < p; i++) {
            double rank = (i + 0.5) / p;
            double vsHigh = VAR_LO + (VAR_HI - VAR_LO) * rank;           // increasing → VS≈1
            double vsLow = VAR_LO + (VAR_HI - VAR_LO) * (1.0 - rank);   // decreasing → VS≈0
            targetMargVar[i] = (1.0 - targetVS) * vsLow + targetVS * vsHigh;
        }

        return solveErrorVariances(B, targetMargVar, p);
    }

    /**
     * Given a lower-triangular coefficient matrix B and a desired marginal
     * variance vector v*, solves for the error variance vector O such that
     * the linear SEM X = BX + e (with Var(e_i) = O[i]) produces exactly
     * Var(X_i) = v*[i].
     *
     * <p>The solution proceeds in topological order:
     * <pre>
     *   O[i] = v*[i] − Σ_{j,k &lt; i} B[i][j] B[i][k] Σ[j][k]
     * </pre>
     * where Σ is built up using the prescribed variances on the diagonal.
     * O[i] is floored at 1e-6 to prevent non-positive values when the
     * coefficient-driven contribution already exceeds v*[i].
     *
     * @param B             lower-triangular coefficient matrix (p×p)
     * @param targetMargVar desired marginal variance vector (length p)
     * @param p             number of variables
     * @return error variance vector O (length p)
     */
    private static double[] solveErrorVariances(
            double[][] B, double[] targetMargVar, int p) {

        double[][] Sigma = new double[p][p];
        double[] O = new double[p];

        for (int i = 0; i < p; i++) {
            // Off-diagonal covariances with predecessors
            for (int j = 0; j < i; j++) {
                double cij = 0.0;
                for (int k = 0; k < i; k++) cij += B[i][k] * Sigma[k][j];
                Sigma[i][j] = cij;
                Sigma[j][i] = cij;
            }

            // Variance contribution from structural coefficients
            double contrib = 0.0;
            for (int j = 0; j < i; j++)
                for (int k = 0; k < i; k++)
                    contrib += B[i][j] * B[i][k] * Sigma[j][k];

            // Solve for O[i], floored at small positive value
            O[i] = Math.max(targetMargVar[i] - contrib, 1e-6);

            // Pin the diagonal to the target so downstream covariances are consistent
            Sigma[i][i] = targetMargVar[i];
        }

        return O;
    }

    // -----------------------------------------------------------------------
    // Varsortability computation
    // -----------------------------------------------------------------------

    /**
     * Computes varsortability (Reisach et al. 2021):
     * fraction of directed edges i→j where Var(X_i) &lt; Var(X_j).
     */
    static double computeVarsortability(Graph dag, List<Node> order, double[] margVar) {
        Map<Node, Integer> idx = new HashMap<>();
        for (int i = 0; i < order.size(); i++) idx.put(order.get(i), i);

        int consistent = 0, total = 0;
        for (Edge e : dag.getEdges()) {
            if (!Edges.isDirectedEdge(e)) continue;
            int tail = idx.get(Edges.getDirectedEdgeTail(e));
            int head = idx.get(Edges.getDirectedEdgeHead(e));
            if (margVar[tail] < margVar[head]) consistent++;
            total++;
        }
        return total == 0 ? Double.NaN : (double) consistent / total;
    }

    /**
     * Computes exact marginal variances by propagating the full covariance
     * matrix in topological order.
     */
    static double[] computeMarginalVariances(double[][] B, double[] O, int p) {
        double[][] Sigma = new double[p][p];

        for (int i = 0; i < p; i++) {
            for (int j = 0; j < i; j++) {
                double cij = 0.0;
                for (int k = 0; k < i; k++) cij += B[i][k] * Sigma[k][j];
                Sigma[i][j] = cij;
                Sigma[j][i] = cij;
            }
            double vii = O[i];
            for (int j = 0; j < i; j++)
                for (int k = 0; k < i; k++)
                    vii += B[i][j] * B[i][k] * Sigma[j][k];
            Sigma[i][i] = vii;
        }

        double[] result = new double[p];
        for (int i = 0; i < p; i++) result[i] = Sigma[i][i];
        return result;
    }

    // -----------------------------------------------------------------------
    // Random DAG and coefficient generation
    // -----------------------------------------------------------------------

    private static Graph randomDag(int p, double avgDegree) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < p; i++) nodes.add(new ContinuousVariable("X" + (i + 1)));

        Graph dag = new EdgeListGraph(nodes);
        double prob = avgDegree / (p - 1);

        for (int i = 1; i < p; i++)
            for (int j = 0; j < i; j++)
                if (RandomUtil.getInstance().nextDouble() < prob)
                    dag.addDirectedEdge(nodes.get(j), nodes.get(i));

        return dag;
    }

    private static double[][] sampleCoefficients(Graph dag, List<Node> order, int p) {
        Map<Node, Integer> idx = new HashMap<>();
        for (int i = 0; i < p; i++) idx.put(order.get(i), i);

        double[][] B = new double[p][p];
        for (Edge e : dag.getEdges()) {
            if (!Edges.isDirectedEdge(e)) continue;
            int tail = idx.get(Edges.getDirectedEdgeTail(e));
            int head = idx.get(Edges.getDirectedEdgeHead(e));
            double mag = COEF_LO + RandomUtil.getInstance().nextDouble() * (COEF_HI - COEF_LO);
            double sign = RandomUtil.getInstance().nextDouble() < 0.5 ? 1.0 : -1.0;
            B[head][tail] = sign * mag;
        }
        return B;
    }

    // -----------------------------------------------------------------------
    // Simulation
    // -----------------------------------------------------------------------

    private static double[][] simulate(double[][] B, double[] O, int p, int n) {
        double[][] X = new double[n][p];
        for (int s = 0; s < n; s++) {
            for (int i = 0; i < p; i++) {
                double xi = Math.sqrt(O[i]) * RandomUtil.getInstance().nextGaussian();
                for (int j = 0; j < i; j++) xi += B[i][j] * X[s][j];
                X[s][i] = xi;
            }
        }
        return X;
    }

    // -----------------------------------------------------------------------
    // BOSS execution
    // -----------------------------------------------------------------------

    private static Graph runBoss(DataSet dataSet) {
//        Score score = new edu.cmu.tetrad.search.score.SemBicScore(new CovarianceMatrix(dataSet));
//        ((SemBicScore) score).setPenaltyDiscount(PENALTY_DISCOUNT);
//
        Score score = new edu.cmu.tetrad.search.score.EbicScore(new CovarianceMatrix(dataSet));
//        Score score = new LegendreBicScore(dataSet);

        edu.cmu.tetrad.search.Boss boss = new edu.cmu.tetrad.search.Boss(score);
        boss.setNumStarts(1);
        boss.setVerbose(false);

        try {
            return new PermutationSearch(boss).search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // -----------------------------------------------------------------------
    // Graph comparison metrics
    // -----------------------------------------------------------------------

    private static double adjacencyF1(Graph truth, Graph estimated) {
        Set<String> trueAdj = adjacencySet(truth);
        Set<String> estAdj = adjacencySet(estimated);
        int tp = intersection(trueAdj, estAdj);
        int fp = estAdj.size() - tp;
        int fn = trueAdj.size() - tp;
        double precision = tp + fp == 0 ? 0.0 : (double) tp / (tp + fp);
        double recall = tp + fn == 0 ? 0.0 : (double) tp / (tp + fn);
        return f1(precision, recall);
    }

    private static double arrowheadF1(Graph truth, Graph estimated) {
        Set<String> trueArr = arrowheadSet(truth);
        Set<String> estArr = arrowheadSet(estimated);
        int tp = intersection(trueArr, estArr);
        int fp = estArr.size() - tp;
        int fn = trueArr.size() - tp;
        double precision = tp + fp == 0 ? 0.0 : (double) tp / (tp + fp);
        double recall = tp + fn == 0 ? 0.0 : (double) tp / (tp + fn);
        return f1(precision, recall);
    }

    private static Set<String> adjacencySet(Graph g) {
        Set<String> s = new HashSet<>();
        for (Edge e : g.getEdges()) {
            String a = e.getNode1().getName(), b = e.getNode2().getName();
            s.add(a.compareTo(b) < 0 ? a + "~" + b : b + "~" + a);
        }
        return s;
    }

    private static Set<String> arrowheadSet(Graph g) {
        Set<String> s = new HashSet<>();
        for (Edge e : g.getEdges()) {
            if (e.getEndpoint2() == Endpoint.ARROW)
                s.add(e.getNode1().getName() + "->" + e.getNode2().getName());
            if (e.getEndpoint1() == Endpoint.ARROW)
                s.add(e.getNode2().getName() + "->" + e.getNode1().getName());
        }
        return s;
    }

    private static int intersection(Set<String> a, Set<String> b) {
        int count = 0;
        for (String s : a) if (b.contains(s)) count++;
        return count;
    }

    private static double f1(double p, double r) {
        return p + r == 0 ? 0.0 : 2 * p * r / (p + r);
    }

    // -----------------------------------------------------------------------
    // DataSet construction
    // -----------------------------------------------------------------------

    private static DataSet buildDataSet(double[][] X, List<Node> order, int p, int n) {
        List<Node> vars = new ArrayList<>();
        for (Node node : order) vars.add(new ContinuousVariable(node.getName()));
        DataSet ds = new BoxDataSet(new VerticalDoubleDataBox(n, p), vars);
        for (int r = 0; r < n; r++)
            for (int c = 0; c < p; c++)
                ds.setDouble(r, c, X[r][c]);
        return ds;
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private static double[] linspace(double lo, double hi, int n) {
        double[] v = new double[n];
        for (int i = 0; i < n; i++) v[i] = lo + i * (hi - lo) / (n - 1);
        return v;
    }
}

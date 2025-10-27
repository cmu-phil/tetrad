package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Harness for benchmarking adjustment set generation and estimating total effects.
 *
 * What it does:
 *  1) Build a random DAG with 20 nodes and ~40 edges.
 *  2) Create a SEM (SemPm, SemIm) on the true graph; simulate data.
 *  3) For every ordered pair (x,y), get up to K=5 adjustment sets via RA.
 *  4) True total effect: SemIm.getTotalEffect(x,y).
 *  5) Sample estimate for each set Z: OLS of y ~ x + Z (coefficient on x).
 *  6) Report MAE vs truth across all (x,y,Z) and total time spent in adjustmentSets().
 *
 * CLI args (optional):
 *   --n  <int>    sample size for simulation (default 5000)
 *   --k  <int>    max sets per (x,y) (default 5)
 *   --L  <int>    path length cap for RA (default 7)
 *   --rad <int>   RA radius (default 3)
 *   --hug <int>   target hugging (default 1)
 */
public class AdjustmentHarness {

    // java ... AdjustmentHarness --n 20000 --k 8 --L 9 --rad 4 --hug 1
    public static void main(String[] args) {
        // Defaults; can be overridden by CLI flags
        int N = 5000;
        int K = 5;
        int L = 7;
        int RADIUS = 3;
        int TGT_HUG = 1;
        String GRAPH_TYPE = "PDAG"; // RandomGraph constructed below is acyclic

        // Parse simple CLI overrides
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--n":   N = Integer.parseInt(args[++i]); break;
                case "--k":   K = Integer.parseInt(args[++i]); break;
                case "--L":   L = Integer.parseInt(args[++i]); break;
                case "--rad": RADIUS = Integer.parseInt(args[++i]); break;
                case "--hug": TGT_HUG = Integer.parseInt(args[++i]); break;
                default: /* ignore */ ;
            }
        }

        // Reproducibility
        RandomUtil.getInstance().setSeed(12345L);

        // --- 1) Random DAG with ~40 edges (tweak generator params as needed) ---
        // Signature: RandomGraph.randomGraph(numVars, numLatents, numEdges, maxInDeg, maxOutDeg, maxDegree, allowCycles)
        Graph G = RandomGraph.randomGraph(20, 0, 40, 100, 100, 100, false);
        List<Node> vars = G.getNodes();

        // --- 2) SEM on the true graph + simulate data ---
        SemPm pm = new SemPm(G);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(N, false);

        // --- 3) Loop over ordered pairs (x,y) and collect adjustment sets ---
        long adjCallNanos = 0L;
        double absErrSum = 0.0;
        long estCount = 0L;

        for (int i = 0; i < vars.size(); i++) {
            for (int j = 0; j < vars.size(); j++) {
                if (i == j) continue;

                Node x = vars.get(i);
                Node y = vars.get(j);

                // Time ONLY the adjustmentSets call
                long t0 = System.nanoTime();
                List<Set<Node>> sets = G.paths().adjustmentSets(
                        x, y, GRAPH_TYPE, K, RADIUS, TGT_HUG, L);
                long t1 = System.nanoTime();
                adjCallNanos += (t1 - t0);

                if (sets == null || sets.isEmpty()) continue;

                // --- 4) True total effect from the SEM ---
                double trueTE = im.getTotalEffect(x, y);

                // --- 5) Estimate for each set: OLS of y ~ x + Z, coef on x ---
                for (Set<Node> Z : sets) {
                    double betaHat = olsCoefXGivenZ(data, y, x, Z);
                    double ae = Math.abs(betaHat - trueTE);
                    absErrSum += ae;
                    estCount++;
                }
            }
        }

        // --- 6) Print summary metrics ---
        double mae = (estCount > 0) ? (absErrSum / estCount) : Double.NaN;
        double adjMs = adjCallNanos / 1_000_000.0;

        System.out.println("\n=== Adjustment Harness Summary ===");
        System.out.println("Nodes: " + vars.size() + ", Edges: " + G.getNumEdges());
        System.out.println("Sample size N = " + N);
        System.out.println("Params: K=" + K + ", L=" + L + ", radius=" + RADIUS + ", tgt_hug=" + TGT_HUG);
        System.out.println("Graph type: " + GRAPH_TYPE);
        System.out.println("Estimates computed: " + estCount);
        System.out.printf(Locale.ROOT, "MAE vs true total effect: %.6f%n", mae);
        System.out.printf(Locale.ROOT, "Total time in adjustmentSets(): %.3f ms%n", adjMs);
    }

    /**
     * Returns the OLS coefficient on X in the regression: Y ~ X + Z
     * Prefers Tetrad's RegressionDataset if available; otherwise uses a small
     * QR-based fallback. Coefficients include an intercept.
     */
    private static double olsCoefXGivenZ(DataSet data, Node y, Node x, Set<Node> Z) {
        // --- Preferred path: Tetrad's regression API (if present) ---
        try {
            // edu.cmu.tetrad.regression.RegressionDataset / RegressionResult
            Class<?> regCls = Class.forName("edu.cmu.tetrad.regression.RegressionDataset");
            Object reg = regCls.getConstructor(DataSet.class).newInstance(data);

            // Build predictors list in order: [x] + Z (stable order)
            List<Node> predictors = new ArrayList<>();
            predictors.add(x);
            if (Z != null && !Z.isEmpty()) {
                // deterministic ordering for coefficient index stability
                List<Node> zList = new ArrayList<>(Z);
                zList.sort(Comparator.comparing(Node::getName));
                predictors.addAll(zList);
            }

            Object rr = regCls.getMethod("regress", Node.class, List.class).invoke(reg, y, predictors);
            // rr.getCoef(): double[] with intercept first, then predictors in given order
            double[] coefs = (double[]) rr.getClass().getMethod("getCoef").invoke(rr);
            return coefs[1]; // index 1 = coefficient on x (index 0 is intercept)

        } catch (Throwable ignored) {
            // Fall through to minimal OLS (no external deps).
        }

        // --- Fallback: tiny least-squares via normal equations + ridge for stability ---
        // Build design matrix: column 0 = 1 (intercept), column 1 = x, columns 2.. = Z (sorted)
        final int n = data.getNumRows();
        List<Node> zList = new ArrayList<>();
        if (Z != null && !Z.isEmpty()) {
            zList.addAll(Z);
            zList.sort(Comparator.comparing(Node::getName));
        }
        final int p = 1 /*x*/ + zList.size();
        double[][] X = new double[n][p + 1]; // +1 for intercept
        double[] Y = new double[n];

        int col = 0;
        // intercept
        for (int r = 0; r < n; r++) X[r][col] = 1.0;
        col++;

        // x
        int xIdx = data.getColumn(data.getVariable(x.getName()));
        for (int r = 0; r < n; r++) X[r][col] = data.getDouble(r, xIdx);
        col++;

        // Z columns
        for (Node z : zList) {
            int zi = data.getColumn(data.getVariable(z.getName()));
            for (int r = 0; r < n; r++) X[r][col] = data.getDouble(r, zi);
            col++;
        }

        // y vector
        int yIdx = data.getColumn(data.getVariable(y.getName()));
        for (int r = 0; r < n; r++) Y[r] = data.getDouble(r, yIdx);

        // Solve (X^T X + λI) b = X^T Y with small ridge λ for numerical stability
        double lambda = 1e-8;
        double[][] XtX = mulT(X, X);                // (p+1) x (p+1)
        for (int d = 0; d < XtX.length; d++) XtX[d][d] += lambda;
        double[] XtY = mulTv(X, Y);                 // (p+1)

        double[] beta = solveSymmetric(XtX, XtY);   // intercept + predictors
        return beta[1]; // coefficient on x
    }

    // ---------- Tiny linear algebra helpers (fallback path only) ----------

    private static double[][] mulT(double[][] A, double[][] B) {
        // return A^T * B  (A: n x m, B: n x k) -> m x k
        int n = A.length, m = A[0].length, k = B[0].length;
        double[][] C = new double[m][k];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < k; j++) {
                double s = 0.0;
                for (int r = 0; r < n; r++) s += A[r][i] * B[r][j];
                C[i][j] = s;
            }
        }
        return C;
    }

    private static double[] mulTv(double[][] A, double[] y) {
        // return A^T * y  (A: n x m) -> m
        int n = A.length, m = A[0].length;
        double[] v = new double[m];
        for (int j = 0; j < m; j++) {
            double s = 0.0;
            for (int i = 0; i < n; i++) s += A[i][j] * y[i];
            v[j] = s;
        }
        return v;
    }

    private static double[] solveSymmetric(double[][] S, double[] b) {
        // Basic Cholesky solve for SPD (S + tiny ridge ensures SPD in practice)
        int n = S.length;
        double[][] L = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = S[i][j];
                for (int k = 0; k < j; k++) sum -= L[i][k] * L[j][k];
                if (i == j) {
                    L[i][j] = Math.sqrt(Math.max(sum, 1e-12));
                } else {
                    L[i][j] = sum / L[j][j];
                }
            }
        }
        // Solve L y = b
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = b[i];
            for (int k = 0; k < i; k++) sum -= L[i][k] * y[k];
            y[i] = sum / L[i][i];
        }
        // Solve L^T x = y
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = y[i];
            for (int k = i + 1; k < n; k++) sum -= L[k][i] * x[k];
            x[i] = sum / L[i][i];
        }
        return x;
    }
}
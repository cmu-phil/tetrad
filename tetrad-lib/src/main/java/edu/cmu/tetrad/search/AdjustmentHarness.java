package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * PDAG harness for benchmarking adjustment set generation and estimating total effects.
 *
 * What it does:
 *  1) Build a random DAG with 20 nodes and ~40 edges.
 *  2) Create a SEM (SemPm, SemIm) on the true graph; simulate data.
 *  3) For every ordered pair (x,y), get up to K adjustment sets via RA.
 *  4) True total effect: SemIm.getTotalEffect(x,y).
 *  5) Sample estimate for each set Z: OLS of y ~ x + Z (coefficient on x).
 *  6) Report MAE vs truth and total time spent in adjustmentSets().
 *  7) Print Z-size breakdown; optionally write adjustment_estimates.csv.
 *
 * CLI (optional):
 *   --n <int>         sample size (default 5000)
 *   --k <int>         max sets per (x,y) (default 5)
 *   --L <int>         RA path-length cap (default 7)
 *   --rad <int>       RA radius (default 3)
 *   --hug <int>       target hugging (default 1)
 *   --seed <long>     RNG seed (default 12345)
 *   --write-csv       write adjustment_estimates.csv (strategy=tetrad_target)
 */
public class AdjustmentHarness {

    public static void main(String[] args) {
        // Defaults (PDAG requested)
        int N = 5000;
        int K = 5;
        int L = 7;
        int RADIUS = 3;
        int TGT_HUG = 1;
        long SEED = 12345L;
        boolean WRITE_CSV = false;

        // Parse simple CLI flags
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--n":        N = Integer.parseInt(args[++i]); break;
                case "--k":        K = Integer.parseInt(args[++i]); break;
                case "--L":        L = Integer.parseInt(args[++i]); break;
                case "--rad":      RADIUS = Integer.parseInt(args[++i]); break;
                case "--hug":      TGT_HUG = Integer.parseInt(args[++i]); break;
                case "--seed":     SEED = Long.parseLong(args[++i]); break;
                case "--write-csv": WRITE_CSV = true; break;
                default: /* ignore */ ;
            }
        }

        final String GRAPH_TYPE = "PDAG"; // <- per your request

        // Reproducibility
        RandomUtil.getInstance().setSeed(SEED);

        // --- 1) Random DAG with ~40 edges ---
        Graph G = RandomGraph.randomGraph(20, 0, 40, 100, 100, 100, false);
        List<Node> vars = G.getNodes();

        // --- 2) SEM on the true graph + simulate data ---
        SemPm pm = new SemPm(G);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(N, false);

        // Optional CSV
        PrintWriter csv = null;
        if (WRITE_CSV) {
            try {
                csv = new PrintWriter(new FileWriter("adjustment_estimates.csv"));
                csv.println("X,Y,strategy,Z,est,has_truth,true,abs_err,reason,Z_size,gen_ms_pair,ols_ms_row");
            } catch (Exception e) {
                System.err.println("Could not open adjustment_estimates.csv for writing: " + e.getMessage());
                csv = null;
            }
        }

        // --- 3–5) Iterate pairs, time adjustmentSets, compute estimates ---
        long adjCallNanos = 0L;
        double absErrSum = 0.0;
        long estCount = 0L;

        // Z-size tallies for a quick breakdown
        Map<Integer, Long> zsizeCounts = new TreeMap<>();

        for (int i = 0; i < vars.size(); i++) {
            for (int j = 0; j < vars.size(); j++) {
                if (i == j) continue;

                Node x = vars.get(i);
                Node y = vars.get(j);

                long t0 = System.nanoTime();
                List<Set<Node>> sets = G.paths().adjustmentSets(x, y, GRAPH_TYPE, K, RADIUS, TGT_HUG, L);
                long t1 = System.nanoTime();
                double genMsPair = (t1 - t0) / 1_000_000.0;
                adjCallNanos += (t1 - t0);

                if (sets == null || sets.isEmpty()) continue;

                // Truth from SEM
                double trueTE = im.getTotalEffect(x, y);

                // each Z: OLS coef on x in y ~ x + Z
                for (Set<Node> Z : sets) {
                    int zSize = (Z == null) ? 0 : Z.size();
                    zsizeCounts.put(zSize, zsizeCounts.getOrDefault(zSize, 0L) + 1);

                    long e0 = System.nanoTime();
                    double betaHat = olsCoefXGivenZ(data, y, x, Z);
                    long e1 = System.nanoTime();
                    double olsMs = (e1 - e0) / 1_000_000.0;

                    double ae = Math.abs(betaHat - trueTE);
                    absErrSum += ae;
                    estCount++;

                    if (csv != null) {
                        // Strategy label aligned with your analyzer
                        final String strategy = "tetrad_target";
                        csv.printf(Locale.ROOT,
                                "%s,%s,%s,%s,%.10f,%s,%.10f,%.10f,%s,%d,%.6f,%.6f%n",
                                x.getName(),
                                y.getName(),
                                strategy,
                                setToBraced(Z),                 // "{A,B}" or "NA"
                                betaHat,
                                "true",                          // has_truth
                                trueTE,
                                ae,
                                "ok",                            // reason
                                zSize,
                                genMsPair,
                                olsMs
                        );
                    }
                }
            }
        }

        if (csv != null) {
            csv.close();
            System.out.println("Wrote adjustment_estimates.csv");
        }

        // --- 6) Summary metrics ---
        double mae = (estCount > 0) ? (absErrSum / estCount) : Double.NaN;
        double adjMs = adjCallNanos / 1_000_000.0;

        System.out.println("\n=== Adjustment Harness Summary (PDAG) ===");
        System.out.println("Nodes: " + vars.size() + ", Edges: " + G.getNumEdges());
        System.out.println("Sample size N = " + N);
        System.out.println("Params: K=" + K + ", L=" + L + ", radius=" + RADIUS + ", tgt_hug=" + TGT_HUG);
        System.out.println("Graph type: " + GRAPH_TYPE);
        System.out.println("Estimates computed: " + estCount);
        System.out.printf(Locale.ROOT, "MAE vs true total effect: %.6f%n", mae);
        System.out.printf(Locale.ROOT, "Total time in adjustmentSets(): %.3f ms%n", adjMs);

        // --- 7) Z-size breakdown ---
        System.out.println("\nCounts by Z_size (all rows):");
        for (Map.Entry<Integer, Long> e : zsizeCounts.entrySet()) {
            System.out.printf(Locale.ROOT, "Z=%d -> %d%n", e.getKey(), e.getValue());
        }
    }

    // ---------- Helpers ----------

    private static String setToBraced(Set<Node> Z) {
        if (Z == null) return "NA";
        if (Z.isEmpty()) return "{}";
        List<String> names = new ArrayList<>();
        for (Node n : Z) names.add(n.getName());
        Collections.sort(names);
        return "{" + String.join(",", names) + "}";
    }

    /**
     * Returns the OLS coefficient on X in the regression: Y ~ X + Z
     * Preferred: Tetrad's RegressionDataset; fallback: tiny ridge-regularized solver.
     */
    private static double olsCoefXGivenZ(DataSet data, Node y, Node x, Set<Node> Z) {
        // Preferred: RegressionDataset if available
        try {
            Class<?> regCls = Class.forName("edu.cmu.tetrad.regression.RegressionDataset");
            Object reg = regCls.getConstructor(DataSet.class).newInstance(data);

            List<Node> predictors = new ArrayList<>();
            predictors.add(x);
            if (Z != null && !Z.isEmpty()) {
                List<Node> zList = new ArrayList<>(Z);
                zList.sort(Comparator.comparing(Node::getName));
                predictors.addAll(zList);
            }

            Object rr = regCls.getMethod("regress", Node.class, List.class).invoke(reg, y, predictors);
            double[] coefs = (double[]) rr.getClass().getMethod("getCoef").invoke(rr);
            return coefs[1]; // 0 = intercept, 1 = x
        } catch (Throwable ignored) {
            // Fall through
        }

        // Fallback: simple normal-equations with tiny ridge
        final int n = data.getNumRows();
        List<Node> zList = new ArrayList<>();
        if (Z != null && !Z.isEmpty()) {
            zList.addAll(Z);
            zList.sort(Comparator.comparing(Node::getName));
        }

        final int p = 1 /*x*/ + zList.size();
        double[][] X = new double[n][p + 1]; // +1 intercept
        double[] Y = new double[n];

        int col = 0;
        for (int r = 0; r < n; r++) X[r][col] = 1.0; // intercept
        col++;

        int xIdx = data.getColumn(data.getVariable(x.getName()));
        for (int r = 0; r < n; r++) X[r][col] = data.getDouble(r, xIdx);
        col++;

        for (Node z : zList) {
            int zi = data.getColumn(data.getVariable(z.getName()));
            for (int r = 0; r < n; r++) X[r][col] = data.getDouble(r, zi);
            col++;
        }

        int yIdx = data.getColumn(data.getVariable(y.getName()));
        for (int r = 0; r < n; r++) Y[r] = data.getDouble(r, yIdx);

        double lambda = 1e-8;
        double[][] XtX = mulT(X, X);
        for (int d = 0; d < XtX.length; d++) XtX[d][d] += lambda;
        double[] XtY = mulTv(X, Y);

        double[] beta = solveSymmetric(XtX, XtY);
        return beta[1]; // coefficient on x
    }

    private static double[][] mulT(double[][] A, double[][] B) {
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
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = b[i];
            for (int k = 0; k < i; k++) sum -= L[i][k] * y[k];
            y[i] = sum / L[i][i];
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = y[i];
            for (int k = i + 1; k < n; k++) sum -= L[k][i] * x[k];
            x[i] = sum / L[i][i];
        }
        return x;
    }
}
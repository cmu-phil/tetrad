package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.*;

/**
 * GIN (Matlab-style): cluster -> merge overlaps -> orient latents by GIN test (IT-mode).
 *
 * Mirrors the Matlab reference implementation structure:
 * - Find_Causal_Clusters.m
 * - Merge_Overlapping_Cluster.m
 * - GIN_Condition_Test.m
 * - Find_CO_by_IT.m  (IT = independence testing; MI mode stub provided)
 *
 * Differences vs Matlab:
 * - Uses EJML (0.44.0) SimpleMatrix/SVD/EVD.
 * - The independence test is provided via RawMarginalIndependenceTest (supports multivariate Y).
 * - Optional whitening for the Σ-blocks before SVD for numerical stability.
 *
 * Output:
 * - Graph with a latent node per cluster, directed edges between latents by GIN condition (greedy, p-descending).
 * - Latent -> observed edges attach each cluster's indicators.
 */
public class Gin {

    // ----------------------------- Modes -----------------------------

    public enum OrderMode { IT, MI } // Matlab default is MI; we implement IT here and stub MI.

    // ----------------------------- Params ----------------------------

    private final double alpha;
    private final RawMarginalIndependenceTest test;
    private final OrderMode orderMode;

    private boolean verbose = false;
    private boolean whitenBeforeSVD = true;       // numerical guard (mirrors Matlab's better behavior)
    private double ridge = 1e-8;                  // tiny ridge for Σ_YY, Σ_ZZ when whitening

    // ----------------------------- State -----------------------------

    private DataSet data;
    private CorrelationMatrix corr;
    private SimpleMatrix cov;                     // Σ of observed
    private List<Node> vars;                      // observed nodes

    // ----------------------------- Ctor ------------------------------

    public Gin(double alpha, RawMarginalIndependenceTest test) {
        this(alpha, test, OrderMode.IT);
    }

    public Gin(double alpha, RawMarginalIndependenceTest test, OrderMode orderMode) {
        this.alpha = alpha;
        this.test = Objects.requireNonNull(test, "test");
        this.orderMode = orderMode == null ? OrderMode.IT : orderMode;
    }

    public void setVerbose(boolean v) { this.verbose = v; }
    public void setWhitenBeforeSVD(boolean w) { this.whitenBeforeSVD = w; }
    public void setRidge(double r) { this.ridge = Math.max(0.0, r); }

    // ----------------------------- API -------------------------------

    /** Run GIN pipeline (Matlab-style). */
    public Graph search(DataSet data) {
        this.data = data;
        this.corr = new CorrelationMatrix(data);
        this.cov = new SimpleMatrix(corr.getMatrix().getSimpleMatrix());
        this.vars = data.getVariables();

        // ---- 1) Find causal clusters (Find_Causal_Clusters + Merge_Overlapping_Cluster)
        List<List<Integer>> clusters = findCausalClusters();

        if (verbose) {
            TetradLogger.getInstance().log("[GIN] clusters=" + clustersAsNames(clusters));
        }

        // ---- 2) Build graph with a latent per cluster
        Graph g = new EdgeListGraph();
        for (Node v : vars) g.addNode(v);

        List<Node> latents = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            Node L = new GraphNode("L" + (i + 1));
            L.setNodeType(NodeType.LATENT);
            g.addNode(L);
            latents.add(L);
            for (int idx : clusters.get(i)) {
                g.addDirectedEdge(L, vars.get(idx));
            }
        }

        // ---- 3) Orient latent-latent edges (Find_CO_by_IT / GIN_Condition_Test)
        if (orderMode == OrderMode.IT) {
            orientByIndependence(g, clusters, latents);
        } else {
            // Stub for MI mode (Matlab default). You can plug a kernel-MI scorer here.
            if (verbose) {
                TetradLogger.getInstance().log("[GIN] MI mode requested but not implemented; falling back to IT.");
            }
            orientByIndependence(g, clusters, latents);
        }

        return g;
    }

    // ---------------------- Find_Causal_Clusters ---------------------

    /**
     * Matlab-style cluster discovery:
     *  - Scan remaining variables, collect 2-seeds that pass a rank<=1 Wilks-like screen vs rest
     *  - Require within-pair dependence (Fisher-z)
     *  - Merge overlapping clusters to a fixed point.
     */
    private List<List<Integer>> findCausalClusters() {
        int p = vars.size();
        Set<Integer> unassigned = new LinkedHashSet<>();
        for (int i = 0; i < p; i++) unassigned.add(i);

        List<List<Integer>> seeds = new ArrayList<>();

        // Simple 2-seed search emulating Matlab's initial block finding
        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                if (!unassigned.contains(i) || !unassigned.contains(j)) continue;

                // Build Y={i,j}, X=rest
                int[] yIdx = new int[]{i, j};
                int[] xIdx = new int[p - 2];
                int t = 0;
                for (int k = 0; k < p; k++) if (k != i && k != j) xIdx[t++] = k;

                double pWilks = RankTests.rankLeByWilks(cov, xIdx, yIdx, data.getNumRows(), 1);
                if (verbose) {
                    TetradLogger.getInstance().log(String.format("[GIN] Wilks p for (%s,%s) = %.3g",
                            vars.get(i).getName(), vars.get(j).getName(), pWilks));
                }
                if (pWilks < alpha) continue; // need rank<=1 not rejected

                // Require the pair to be (marginally) dependent
                if (!pairDependent(i, j)) continue;

                List<Integer> c = new ArrayList<>(List.of(i, j));
                seeds.add(c);
                // (Matlab often does not "consume" variables yet; we follow with merge later)
            }
        }

        // Merge overlaps to a fixed point (Merge_Overlapping_Cluster)
        Set<List<Integer>> merged = mergeOverlappingClusters(new LinkedHashSet<>(seeds));

        // Optional: try to greedily grow each merged block by checking that
        // adding a var keeps rank<=1 vs the rest and within-block pairs stay dependent.
        // (The Matlab code includes block growth guarded by tests; we keep it minimal.)
        return new ArrayList<>(merged);
    }

    /** Merge overlapping clusters to a fixed point, with deterministic sorted members. */
    private Set<List<Integer>> mergeOverlappingClusters(Set<List<Integer>> clusters) {
        // normalize
        Set<List<Integer>> work = new LinkedHashSet<>();
        for (List<Integer> c : clusters) {
            List<Integer> s = new ArrayList<>(new LinkedHashSet<>(c));
            Collections.sort(s);
            work.add(s);
        }
        boolean changed;
        do {
            changed = false;
            List<List<Integer>> list = new ArrayList<>(work);
            boolean[] used = new boolean[list.size()];
            Set<List<Integer>> next = new LinkedHashSet<>();

            for (int i = 0; i < list.size(); i++) {
                if (used[i]) continue;
                Set<Integer> acc = new LinkedHashSet<>(list.get(i));
                used[i] = true;
                for (int j = i + 1; j < list.size(); j++) {
                    if (used[j]) continue;
                    List<Integer> cj = list.get(j);
                    boolean overlap = false;
                    for (int v : cj) { if (acc.contains(v)) { overlap = true; break; } }
                    if (overlap) {
                        acc.addAll(cj);
                        used[j] = true;
                        changed = true;
                    }
                }
                List<Integer> merged = new ArrayList<>(acc);
                Collections.sort(merged);
                next.add(merged);
            }
            work = next;
        } while (changed);
        return work;
    }

    // ------------------------ Find_CO_by_IT --------------------------

    /**
     * IT-mode orientation like Matlab's Find_CO_by_IT + GIN_Condition_Test:
     * For each ordered pair (Z -> Y), form e = omega^T Y via Σ blocks (optionally whitened),
     * test e ⟂ Z with the provided RawMarginalIndependenceTest (multivariate if available),
     * then greedily add edges with highest p-values first, avoiding cycles.
     */
    private void orientByIndependence(Graph g, List<List<Integer>> clusters, List<Node> latents) {
        List<EdgeCand> cands = new ArrayList<>();

        for (int i = 0; i < clusters.size(); i++) {
            for (int j = 0; j < clusters.size(); j++) {
                if (i == j) continue;
                if (Thread.currentThread().isInterrupted()) return;

                List<Integer> Z = clusters.get(i);
                List<Integer> Y = clusters.get(j);
                if (Z.isEmpty() || Y.isEmpty()) continue;

                double[] e = computeE(Y, Z);             // Matlab's GIN projection
                double p = pValueEvsZ(e, Z);             // GIN_Condition_Test core

                if (verbose) {
                    TetradLogger.getInstance().log(String.format(
                            "[GIN] p(e ⟂ Z) for L%d->L%d : p=%.4g", i+1, j+1, p));
                }

                cands.add(new EdgeCand(i, j, p));
            }
        }

        // Greedy add edges with p >= alpha (fail to reject dependence → treat as cause)
        cands.sort(Comparator.comparingDouble((EdgeCand c) -> c.p).reversed());
        for (EdgeCand c : cands) {
            if (Thread.currentThread().isInterrupted()) return;
            if (c.p >= alpha) {
                Node from = latents.get(c.i);
                Node to   = latents.get(c.j);
                if (!g.isAncestorOf(to, from)) {
                    g.addDirectedEdge(from, to);
                }
            }
        }
    }

    // ---------------------- GIN_Condition_Test -----------------------

    /**
     * Build e = Y_c * ω where ω is the right singular vector of Σ_ZY associated with the smallest singular value.
     * If whitening is enabled, use A = Σ_ZZ^{-1/2} Σ_ZY Σ_YY^{-1/2}, take v_min, then ω = Σ_YY^{-1/2} v_min.
     */
    private double[] computeE(List<Integer> Y, List<Integer> Z) {
        final int n = data.getNumRows();
        if (Y == null || Z == null || Y.isEmpty() || Z.isEmpty()) {
            return new double[n];
        }

        // Σ blocks
        SimpleMatrix Syy = subCov(cov, Y, Y);
        SimpleMatrix Szz = subCov(cov, Z, Z);
        SimpleMatrix Szy = subCov(cov, Z, Y);

        SimpleMatrix omega;

        if (whitenBeforeSVD) {
            // A = Σ_ZZ^{-1/2} Σ_ZY Σ_YY^{-1/2}
            SimpleMatrix SzzInvH = invSqrtSym(Szz, ridge);
            SimpleMatrix SyyInvH = invSqrtSym(Syy, ridge);
            SimpleMatrix A = SzzInvH.mult(Szy).mult(SyyInvH);

            SimpleSVD<SimpleMatrix> svd = A.svd();
            SimpleMatrix V = svd.getV();
            int minCol = V.numCols() - 1; // smallest σ
            SimpleMatrix vmin = V.extractVector(false, minCol);

            omega = SyyInvH.mult(vmin); // map back: ω
        } else {
            // plain Σ_ZY SVD (right singular vector of smallest σ)
            SimpleSVD<SimpleMatrix> svd = Szy.svd();
            SimpleMatrix V = svd.getV();
            int minCol = V.numCols() - 1;
            omega = V.extractVector(false, minCol);
        }

        // Yc (centered columns)
        SimpleMatrix Yc = new SimpleMatrix(n, Y.size());
        for (int j = 0; j < Y.size(); j++) {
            int col = Y.get(j);
            double mean = 0.0;
            for (int i = 0; i < n; i++) mean += data.getDouble(i, col);
            mean /= n;
            for (int i = 0; i < n; i++) {
                Yc.set(i, j, data.getDouble(i, col) - mean);
            }
        }

        // e = Yc * ω
        return Yc.mult(omega).getDDRM().getData();
    }

    /** p-value for (e ⟂ Z) using the multivariate overload; interface provides Fisher fallback. */
    private double pValueEvsZ(double[] e, List<Integer> Z) {
        final int n = e.length;
        if (Z == null || Z.isEmpty()) return 1.0;

        // Build n x |Z| block (columns = Z variables)
        double[][] Zcols = new double[n][Z.size()];
        for (int j = 0; j < Z.size(); j++) {
            int col = Z.get(j);
            for (int i = 0; i < n; i++) {
                Zcols[i][j] = data.getDouble(i, col);
            }
        }

        try {
            double p = test.computePValue(e, Zcols);   // uses KCI multivariate if implemented, else Fisher fallback
            if (!Double.isFinite(p)) return 1.0;
            if (p < 0) return 0.0;
            if (p > 1) return 1.0;
            return p;
        } catch (InterruptedException ex) {
            // Propagate interruption in the same style as elsewhere
            throw new RuntimeException(ex);
        }
    }

    /** Build n x |Z| matrix with columns = Z variables from the DataSet. */
    private double[][] buildZBlock(List<Integer> Z, int n) {
        double[][] Zcols = new double[n][Z.size()];
        for (int j = 0; j < Z.size(); j++) {
            int col = Z.get(j);
            for (int i = 0; i < n; i++) {
                Zcols[i][j] = data.getDouble(i, col);
            }
        }
        return Zcols;
    }

    // --------------------------- Utils -------------------------------

    private boolean pairDependent(int a, int b) {
        double r = corr.getValue(a, b);
        if (Double.isNaN(r)) return false;
        int n = corr.getSampleSize();
        double q = 0.5 * (log1p(r) - log1p(-r));    // Fisher z
        double z = sqrt(Math.max(n - 3.0, 1.0)) * abs(q);
        double p2 = 2.0 * (1.0 - normalCdf(z));
        return p2 < alpha;
    }

    private static double normalCdf(double zAbs) {
        // Approximate Φ(z) for z>=0; use Java's erf if you like, but this is fine here.
        // For simplicity we piggy-back on error function via Apache Math if present.
        // If not available in your build, replace with a small rational approx.
        return 0.5 * (1.0 + erf(zAbs / sqrt(2.0)));
    }

    // crude erf fallback (if Apache isn't in classpath). Replace if you prefer another implementation.
    private static double erf(double x) {
        // Abramowitz-Stegun 7.1.26
        double t = 1.0 / (1.0 + 0.5 * abs(x));
        double tau = t * exp(-x*x - 1.26551223 +
                             1.00002368 * t +
                             0.37409196 * t*t +
                             0.09678418 * pow(t,3) -
                             0.18628806 * pow(t,4) +
                             0.27886807 * pow(t,5) -
                             1.13520398 * pow(t,6) +
                             1.48851587 * pow(t,7) -
                             0.82215223 * pow(t,8) +
                             0.17087277 * pow(t,9));
        return (x >= 0) ? 1.0 - tau : tau - 1.0;
    }

    private static double clamp01(double p) {
        if (!Double.isFinite(p)) return 1.0;
        if (p < 0) return 0.0;
        if (p > 1) return 1.0;
        return p;
    }

    /** Fisher's method, returns upper-tail p-value (combine "independence" p's). */
    private static double fisherUpper(List<Double> pvals) {
        if (pvals == null || pvals.isEmpty()) return 1.0;
        double stat = 0.0; int k = 0;
        for (double p : pvals) {
            double pc = Math.max(Math.min(p, 1.0), 1e-300);
            stat += -2.0 * Math.log(pc);
            k++;
        }
        int df = 2 * k;
        // Convert stat ~ χ^2_df to upper-tail p. Use a simple regularized gamma approx:
        return chisqUpperP(stat, df);
    }

    // Simple χ^2 upper-tail using incomplete gamma; for robustness replace with Apache if available
    private static double chisqUpperP(double x, int df) {
        // Using regularized gamma Q(s, x/2) with s=df/2
        double s = df / 2.0;
        double y = x / 2.0;
        return regularizedGammaQ(s, y);
    }

    // Lanczos-based regularized Gamma Q(s, x); minimal implementation for independence combine
    private static double regularizedGammaQ(double s, double x) {
        if (x < 0 || s <= 0) return 1.0;
        if (x == 0) return 1.0;
        if (x < s + 1.0) {
            // use P(s,x) series and return 1 - P
            double sum = 1.0 / s;
            double term = sum;
            double ap = s;
            for (int n = 1; n < 200; n++) {
                ap += 1.0;
                term *= x / ap;
                sum += term;
                if (abs(term) < 1e-15) break;
            }
            double gln = logGamma(s);
            double P = sum * exp(-x + s * log(x) - gln);
            return Math.max(0.0, Math.min(1.0, 1.0 - P));
        } else {
            // continued fraction for Q
            double gln = logGamma(s);
            double a0 = 1.0; double a1 = x;
            double b0 = 0.0; double b1 = 1.0;
            double fac = 1.0;
            double gOld = 0.0, g = b1;
            for (int n = 1; n < 200; n++) {
                double an = n;
                double ana = an - s;
                a0 = (a1 + a0 * ana) * fac;
                b0 = (b1 + b0 * ana) * fac;
                double anf = an * fac;
                a1 = x * a0 + anf * a1;
                b1 = x * b0 + anf * b1;
                if (a1 != 0) {
                    fac = 1.0 / a1;
                    g = b1 * fac;
                    if (abs((g - gOld) / g) < 1e-12) break;
                    gOld = g;
                }
            }
            double Q = exp(-x + s * log(x) - gln) * g;
            return Math.max(0.0, Math.min(1.0, Q));
        }
    }

    // Log Gamma via Lanczos (sufficient accuracy here)
    private static double logGamma(double x) {
        double[] c = {
                76.18009172947146,   -86.50532032941677,
                24.01409824083091,   -1.231739572450155,
                0.001208650973866179, -0.000005395239384953
        };
        double y = x;
        double tmp = x + 5.5;
        tmp -= (x + 0.5) * log(tmp);
        double ser = 1.000000000190015;
        for (double v : c) { y += 1.0; ser += v / y; }
        return -tmp + log(2.5066282746310005 * ser / x);
    }

    private SimpleMatrix subCov(SimpleMatrix S, List<Integer> rows, List<Integer> cols) {
        SimpleMatrix out = new SimpleMatrix(rows.size(), cols.size());
        for (int i = 0; i < rows.size(); i++) {
            for (int j = 0; j < cols.size(); j++) {
                out.set(i, j, S.get(rows.get(i), cols.get(j)));
            }
        }
        return out;
    }

    /** Symmetric inverse square-root via EVD with ridge. */
    private static SimpleMatrix invSqrtSym(SimpleMatrix A, double ridge) {
        SimpleMatrix Ar = A.copy();
        if (ridge > 0) {
            // Ar += ridge * I
            DMatrixRMaj d = Ar.getDDRM();
            int n = d.getNumRows();
            for (int i = 0; i < n; i++) {
                d.add(i, i, ridge);
            }
        }

        SimpleEVD<SimpleMatrix> evd = Ar.eig();
        int n = Ar.numRows();
        SimpleMatrix U = new SimpleMatrix(n, n);
        SimpleMatrix Dm = new SimpleMatrix(n, n);
        for (int i = 0; i < n; i++) {
            double ev = evd.getEigenvalue(i).getReal();
            SimpleMatrix ui = evd.getEigenVector(i);
            if (ui == null) {
                // fall back to identity direction (rare)
                ui = new SimpleMatrix(n, 1);
                ui.set(i, 0, 1.0);
            }
            // normalize ui
            double norm = ui.normF();
            if (norm > 0) ui = ui.divide(norm);
            U.insertIntoThis(0, i, ui);
            double v = (ev > 1e-12) ? 1.0 / sqrt(ev) : 0.0;
            Dm.set(i, i, v);
        }
        return U.mult(Dm).mult(U.transpose());
    }

    private String clustersAsNames(List<List<Integer>> clusters) {
        return clusters.stream()
                .map(cl -> cl.stream().map(i -> vars.get(i).getName()).collect(Collectors.toList()).toString())
                .collect(Collectors.joining(" | "));
    }

    // --------------------------- Helper ------------------------------

    private static final class EdgeCand {
        final int i; final int j; final double p;
        EdgeCand(int i, int j, double p) { this.i = i; this.j = j; this.p = p; }
    }
}
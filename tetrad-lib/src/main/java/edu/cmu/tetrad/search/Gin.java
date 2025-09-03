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
 * <p>
 * Mirrors the MATLAB reference structure:
 *   - Find_Causal_Clusters.m
 *   - Merge_Overlapping_Cluster.m
 *   - GIN_Condition_Test.m
 *   - Find_CO_by_IT.m (IT = independence testing; MI mode is stubbed)
 * <p>
 * Differences:
 *   - EJML (0.44.0) linear algebra
 *   - User-supplied RawMarginalIndependenceTest (supports multivariate Y)
 *   - Optional whitening of covariance blocks before SVD
 */
public class Gin {

    // ----------------------------- Modes/params -----------------------------

    private final double alpha;
    private final RawMarginalIndependenceTest test;
    private final OrderMode orderMode;

    private boolean verbose = false;
    private boolean whitenBeforeSVD = true; // numerical guard (mirrors MATLAB stability)
    private double ridge = 1e-8;            // tiny ridge for Σ_YY and Σ_ZZ when whitening
    private double addMargin = 1e-3;        // tiny cushion above alpha for adding edges (set 0 for original behavior)

    // ----------------------------- State -----------------------------

    private DataSet data;
    private CorrelationMatrix corr;
    private SimpleMatrix cov;               // Σ of observed
    private List<Node> vars;                // observed nodes

    // ----------------------------- Ctors -----------------------------

    public Gin(double alpha, RawMarginalIndependenceTest test) {
        this(alpha, test, OrderMode.IT);
    }

    public Gin(double alpha, RawMarginalIndependenceTest test, OrderMode orderMode) {
        this.alpha = alpha;
        this.test = Objects.requireNonNull(test, "test");
        this.orderMode = orderMode == null ? OrderMode.IT : orderMode;
    }

    // ----------------------------- Config -----------------------------

    public void setVerbose(boolean v) { this.verbose = v; }
    public void setWhitenBeforeSVD(boolean w) { this.whitenBeforeSVD = w; }
    public void setRidge(double r) { this.ridge = Math.max(0.0, r); }
    /** Set to 0.0 to revert to “p ≥ alpha” rule without cushion. */
    public void setAddMargin(double m) { this.addMargin = Math.max(0.0, m); }

    // ----------------------------- API -------------------------------

    /** Run GIN pipeline (Matlab-style). */
    public Graph search(DataSet data) {
        this.data = data;
        this.corr = new CorrelationMatrix(data);
        this.cov = new SimpleMatrix(corr.getMatrix().getSimpleMatrix());
        this.vars = data.getVariables();

        // 1) Find causal clusters (Find_Causal_Clusters + Merge_Overlapping_Cluster)
        List<List<Integer>> clusters = findCausalClusters();

        // Fallback: if no seeds survived, use singletons so the pipeline remains usable.
        if (clusters.isEmpty()) {
            List<List<Integer>> singletons = new ArrayList<>();
            for (int i = 0; i < vars.size(); i++) singletons.add(List.of(i));
            clusters = singletons;
            if (verbose) {
                TetradLogger.getInstance().log("[GIN] No seed clusters found; falling back to singletons.");
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("[GIN] clusters=" + clustersAsNames(clusters));
        }

        // 2) Build graph with a latent per cluster
        Graph g = new EdgeListGraph();
        for (Node v : vars) g.addNode(v);

        List<Node> latents = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            Node L = new GraphNode("L" + (i + 1));
            L.setNodeType(NodeType.LATENT);
            g.addNode(L);
            latents.add(L);
            for (int idx : clusters.get(i)) g.addDirectedEdge(L, vars.get(idx));
        }

        // 3) Orient latent-latent edges (Find_CO_by_IT / GIN_Condition_Test)
        if (orderMode == OrderMode.IT) {
            orientByIndependence(g, clusters, latents);
        } else {
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
     *  - Scan all 2-seeds satisfying Wilks rank<=1 vs rest
     *  - Require within-pair dependence (Fisher-Z)
     *  - Merge overlapping seeds to a fixed point
     */
    private List<List<Integer>> findCausalClusters() {
        int p = vars.size();
        List<List<Integer>> seeds = new ArrayList<>();

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {

                // Y={i,j}, X=rest
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

                // Require marginal dependence within pair
                if (!pairDependent(i, j)) continue;

                seeds.add(new ArrayList<>(List.of(i, j)));
            }
        }

        // Merge overlaps to a fixed point
        Set<List<Integer>> merged = mergeOverlappingClusters(new LinkedHashSet<>(seeds));
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

    // ---------------------- Orientation (IT-mode) --------------------

    /**
     * IT-mode orientation akin to MATLAB: for each ordered pair (Z -> Y),
     * form e = Y_c * ω (ω from smallest σ of Σ_ZY or whitened variant),
     * test e ⟂ Z with RawMarginalIndependenceTest (multivariate if available),
     * then add edges greedily in descending p, avoiding cycles.
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

                double[] e = computeE(Y, Z);
                double p = pValueEvsZ(e, Z);

                if (verbose) {
                    TetradLogger.getInstance().log(String.format(
                            "[GIN] p(e ⟂ Z) for L%d->L%d : p=%.4g", i + 1, j + 1, p));
                }

                cands.add(new EdgeCand(i, j, p));
            }
        }

        // Greedy add edges with p >= alpha + addMargin
        cands.sort(Comparator.comparingDouble((EdgeCand c) -> c.p).reversed());
        for (EdgeCand c : cands) {
            if (Thread.currentThread().isInterrupted()) return;
            if (c.p >= alpha + addMargin) {
                Node from = latents.get(c.i);
                Node to = latents.get(c.j);
                if (!g.isAncestorOf(to, from)) {
                    g.addDirectedEdge(from, to);
                }
            }
        }
    }

    // ---------------------- GIN projection e ------------------------

    /**
     * Build e = Y_c * ω where ω is the right singular vector (smallest σ).
     * If whitening is enabled, use A = Σ_ZZ^{-1/2} Σ_ZY Σ_YY^{-1/2}, take v_min, then ω = Σ_YY^{-1/2} v_min.
     */
    private double[] computeE(List<Integer> Y, List<Integer> Z) {
        final int n = data.getNumRows();
        if (Y == null || Z == null || Y.isEmpty() || Z.isEmpty()) return new double[n];

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
            SimpleMatrix W = svd.getW();
            SimpleMatrix V = svd.getV();

            int r = Math.min(W.getNumRows(), W.getNumCols());
            int minIdx = 0;
            double minSv = Double.POSITIVE_INFINITY;
            for (int i = 0; i < r; i++) {
                double sv = W.get(i, i);
                if (sv < minSv) { minSv = sv; minIdx = i; }
            }
            SimpleMatrix vmin = V.extractVector(false, minIdx);
            omega = SyyInvH.mult(vmin);
        } else {
            // plain Σ_ZY SVD
            SimpleSVD<SimpleMatrix> svd = Szy.svd();
            SimpleMatrix W = svd.getW();
            SimpleMatrix V = svd.getV();

            int r = Math.min(W.getNumRows(), W.getNumCols());
            int minIdx = 0;
            double minSv = Double.POSITIVE_INFINITY;
            for (int i = 0; i < r; i++) {
                double sv = W.get(i, i);
                if (sv < minSv) { minSv = sv; minIdx = i; }
            }
            omega = V.extractVector(false, minIdx);
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

    // ---------------------- e ⟂ Z p-value ---------------------------

    /** p-value for (e ⟂ Z) using multivariate overload; interface provides Fisher fallback. */
    private double pValueEvsZ(double[] e, List<Integer> Z) {
        final int n = e.length;
        if (Z == null || Z.isEmpty()) return 1.0;

        // Build n x |Z| block
        double[][] Zcols = new double[n][Z.size()];
        for (int j = 0; j < Z.size(); j++) {
            int col = Z.get(j);
            for (int i = 0; i < n; i++) Zcols[i][j] = data.getDouble(i, col);
        }

        try {
            double p = test.computePValue(e, Zcols);
            if (!Double.isFinite(p)) return 1.0;
            if (p < 0) return 0.0;
            if (p > 1) return 1.0;
            return p;
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    // ---------------------- Math helpers -----------------------------

    /** Symmetric inverse square-root via EVD with ridge. */
    private static SimpleMatrix invSqrtSym(SimpleMatrix A, double ridge) {
        SimpleMatrix Ar = A.copy();
        if (ridge > 0) {
            DMatrixRMaj d = Ar.getDDRM();
            int n = d.getNumRows();
            for (int i = 0; i < n; i++) d.add(i, i, ridge);
        }
        SimpleEVD<SimpleMatrix> evd = Ar.eig();
        int n = Ar.getNumRows();
        SimpleMatrix U = new SimpleMatrix(n, n);
        SimpleMatrix Dm = new SimpleMatrix(n, n);
        for (int i = 0; i < n; i++) {
            double ev = evd.getEigenvalue(i).getReal();
            SimpleMatrix ui = evd.getEigenVector(i);
            if (ui == null) {
                ui = new SimpleMatrix(n, 1);
                ui.set(i, 0, 1.0);
            }
            double norm = ui.normF();
            if (norm > 0) ui = ui.divide(norm);
            U.insertIntoThis(0, i, ui);
            double v = (ev > 1e-12) ? 1.0 / sqrt(ev) : 0.0;
            Dm.set(i, i, v);
        }
        return U.mult(Dm).mult(U.transpose());
    }

    /** Pairwise (unconditional) dependence by Fisher-Z at level alpha. */
    private boolean pairDependent(int a, int b) {
        double r = corr.getValue(a, b);
        if (Double.isNaN(r)) return false;
        // Guard against |r|=1
        r = Math.max(-0.999999, Math.min(0.999999, r));
        int n = Math.max(corr.getSampleSize(), 4);
        double q = 0.5 * (log1p(r) - log1p(-r)); // Fisher z
        double z = sqrt(n - 3.0) * abs(q);
        double p2 = 2.0 * (1.0 - normalCdf(z));
        return p2 < alpha;
    }

    private static double normalCdf(double zAbs) {
        return 0.5 * (1.0 + erf(zAbs / sqrt(2.0)));
    }

    // Abramowitz–Stegun 7.1.26 erf approximation (sufficient here)
    private static double erf(double x) {
        double t = 1.0 / (1.0 + 0.5 * abs(x));
        double tau = t * exp(-x * x - 1.26551223 +
                             1.00002368 * t +
                             0.37409196 * t * t +
                             0.09678418 * pow(t, 3) -
                             0.18628806 * pow(t, 4) +
                             0.27886807 * pow(t, 5) -
                             1.13520398 * pow(t, 6) +
                             1.48851587 * pow(t, 7) -
                             0.82215223 * pow(t, 8) +
                             0.17087277 * pow(t, 9));
        return (x >= 0) ? 1.0 - tau : tau - 1.0;
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

    private String clustersAsNames(List<List<Integer>> clusters) {
        return clusters.stream()
                .map(cl -> cl.stream().map(i -> vars.get(i).getName()).toList().toString())
                .collect(Collectors.joining(" | "));
    }

    public enum OrderMode { IT, MI } // MATLAB default is MI; we implement IT and stub MI.

    // --------------------------- Helper ------------------------------

    private static final class EdgeCand {
        final int i, j;
        final double p;
        EdgeCand(int i, int j, double p) { this.i = i; this.j = j; this.p = p; }
    }
}
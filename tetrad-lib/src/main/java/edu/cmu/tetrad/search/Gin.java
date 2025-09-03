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
 * GIN (Matlab-style): cluster -> merge overlaps -> orient latents by GIN test (IT-mode). Lightweight anti-clique
 * controls: - asymmetry gate (p_ij - p_ji >= delta) - max in-degree cap - small margin above alpha - singular-gap check
 * on the projection SVD
 */
public class Gin {

    // ----------------------------- Modes/params -----------------------------

    private final double alpha;
    private final RawMarginalIndependenceTest test;
    private final OrderMode orderMode;
    private final List<Node> nodes;

    private boolean verbose = false;
    private boolean whitenBeforeSVD = true; // numerical guard
    private double ridge = 1e-8;            // ridge for Σ_YY/Σ_ZZ when whitening

    // Anti-clique knobs (cheap):
    private double addMargin = 1e-3;        // require p >= alpha + margin
    private double asymmetryDelta = 0.05;   // require p_ij - p_ji >= delta
    private int maxInDegree = 0;         // 0 or negative disables capping
    private double gapThreshold = 0.90;     // accept if (σ_min / σ_next) <= gapThreshold

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
        this.orderMode = (orderMode == null) ? OrderMode.IT : orderMode;
        this.nodes = ((IndependenceTest) test).getVariables();
    }

    // ----------------------------- Config -----------------------------

    /**
     * Symmetric inverse square-root via EVD with ridge.
     */
    private static SimpleMatrix invSqrtSym(SimpleMatrix A, double ridge) {
        SimpleMatrix Ar = A.copy();
        if (ridge > 0) {
            DMatrixRMaj d = Ar.getDDRM();
            int n = d.getNumRows();
            for (int i = 0; i < n; i++) d.add(i, i, ridge);
        }
        SimpleEVD<SimpleMatrix> evd = Ar.eig();
        int n = Ar.numRows();
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

    public void setVerbose(boolean v) {
        this.verbose = v;
    }

    public void setWhitenBeforeSVD(boolean w) {
        this.whitenBeforeSVD = w;
    }

    public void setRidge(double r) {
        this.ridge = Math.max(0.0, r);
    }

    /**
     * Set to 0.0 to revert to “p ≥ alpha” without cushion.
     */
    public void setAddMargin(double m) {
        this.addMargin = Math.max(0.0, m);
    }

    // ----------------------------- API -------------------------------

    /**
     * Asymmetry cushion: require p_ij - p_ji ≥ delta to add i->j.
     */
    public void setAsymmetryDelta(double d) {
        this.asymmetryDelta = Math.max(0.0, d);
    }

    // ---------------------- Find_Causal_Clusters ---------------------

    /**
     * Max number of parents per latent; ≤0 disables the cap.
     */
    public void setMaxInDegree(int k) {
        this.maxInDegree = k;
    }

    /**
     * Require σ_min / σ_next ≤ threshold to accept the projection direction.
     */
    public void setGapThreshold(double t) {
        this.gapThreshold = Math.min(1.0, Math.max(0.0, t));
    }

    // ---------------------- Orientation (fast IT-mode) --------------------

    /**
     * Run GIN pipeline (Matlab-style).
     */
    public Graph search(DataSet data) {
        this.data = data;
        this.corr = new CorrelationMatrix(data);
        this.cov = new SimpleMatrix(corr.getMatrix().getSimpleMatrix());
        this.vars = data.getVariables();

        // 1) Find causal clusters (Find_Causal_Clusters + Merge_Overlapping_Cluster)
        List<List<Integer>> clusters = findCausalClusters();

        // Fallback to singletons if nothing survived
        if (clusters.isEmpty()) {
            List<List<Integer>> singletons = new ArrayList<>();
            for (int i = 0; i < vars.size(); i++) singletons.add(List.of(i));
            clusters = singletons;
            if (verbose) TetradLogger.getInstance().log("[GIN] No seed clusters found; falling back to singletons.");
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
            orientByIndependenceFast(g, clusters, latents);
        } else {
            if (verbose) TetradLogger.getInstance().log("[GIN] MI mode requested but not implemented; using IT.");
            orientByIndependenceFast(g, clusters, latents);
        }

        return g;
    }

    // ---------------------- GIN projection e ------------------------

    private List<List<Integer>> findCausalClusters() {
        Tsc tsc = new Tsc(data.getVariables(), new CorrelationMatrix(data));
        tsc.setAlpha(alpha);
        tsc.setMinRedundancy(0);

        List<List<Integer>> seeds = new ArrayList<>();

        for (Set<Integer> seed : tsc.findClusters().keySet()) {
            seeds.add(new ArrayList<>(seed));
        }

        System.out.println(seeds);

        return seeds;
    }

    // ---------------------- e ⟂ Z p-value ---------------------------

    /**
     * Compute both directions once per pair (i<j) to halve work. Apply: - singular-gap guard (cheap) - asymmetry gate:
     * p_ij - p_ji >= delta - in-degree cap - margin above alpha
     */
    private void orientByIndependenceFast(Graph g, List<List<Integer>> clusters, List<Node> latents) {
        int m = clusters.size();
        List<EdgeCand> cands = new ArrayList<>(2 * m * (m - 1) / 2);

        // Precompute both directions once per unordered pair
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                if (Thread.currentThread().isInterrupted()) return;

                List<Integer> Zi = clusters.get(i);
                List<Integer> Yj = clusters.get(j);
                List<Integer> Zj = clusters.get(j);
                List<Integer> Yi = clusters.get(i);

                if (verbose) {
                    TetradLogger.getInstance().log("Checking <" + names(Yj) + ">; <" + names(Zi)
                                                    + "> and <" + names(Yi) + ">; <" + names(Zj) + ">");
                }

                // i -> j
                ProjResult proj_ij = computeProjection(Yj, Zi);
                double p_ij = (proj_ij.acceptable) ? pValueEvsZ(proj_ij.e, Zi) : 0.0;

                // j -> i
                ProjResult proj_ji = computeProjection(Yi, Zj);
                double p_ji = (proj_ji.acceptable) ? pValueEvsZ(proj_ji.e, Zj) : 0.0;

                if (verbose) {
                    TetradLogger.getInstance().log(String.format(
                            "[GIN] pair L%d↔L%d : p_ij=%.4g (gap=%.3f) | p_ji=%.4g (gap=%.3f)",
                            i + 1, j + 1, p_ij, proj_ij.gapRatio, p_ji, proj_ji.gapRatio));
                }

                // Store both directions with cross-p value for asymmetry gate
                cands.add(new EdgeCand(i, j, p_ij, p_ji));
                cands.add(new EdgeCand(j, i, p_ji, p_ij));
            }
        }

        // Greedy add edges with anti-clique gates
        cands.sort(Comparator.comparingDouble((EdgeCand c) -> c.p).reversed());
        int[] inDeg = new int[m];

        for (EdgeCand c : cands) {
            if (Thread.currentThread().isInterrupted()) return;

            // basic acceptance
            if (c.p < alpha + addMargin) continue;

            // asymmetry gate
            if (c.p - c.pOpp < asymmetryDelta) continue;

            Node from = latents.get(c.i);
            Node to = latents.get(c.j);

            // acyclicity + in-degree cap
            if (!g.isAncestorOf(to, from) && (maxInDegree <= 0 || inDeg[c.j] < maxInDegree)) {
                g.addDirectedEdge(from, to);
                inDeg[c.j]++;
            }
        }
    }

    private String names(List<Integer> yj) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < yj.size(); i++) {
            sb.append(nodes.get(yj.get(i)));
            if (i < yj.size() - 1) sb.append(", ");
        }

        return sb.toString();
    }

    // ---------------------- Math helpers -----------------------------

    /**
     * Compute projection e = Y_c * ω. Also compute gapRatio = σ_min / σ_next and apply (cheap) acceptability test:
     * acceptable := (gapRatio <= gapThreshold)
     */
    private ProjResult computeProjection(List<Integer> Y, List<Integer> Z) {
        final int n = data.getNumRows();
        if (Y == null || Z == null || Y.isEmpty() || Z.isEmpty()) {
            return new ProjResult(new double[n], Double.POSITIVE_INFINITY, false);
        }

        // Σ blocks
        SimpleMatrix Syy = subCov(cov, Y, Y);
        SimpleMatrix Szz = subCov(cov, Z, Z);
        SimpleMatrix Szy = subCov(cov, Z, Y);

        // Yc (centered)
        SimpleMatrix Yc = new SimpleMatrix(n, Y.size());
        for (int j = 0; j < Y.size(); j++) {
            int col = Y.get(j);
            double mean = 0.0;
            for (int i = 0; i < n; i++) mean += data.getDouble(i, col);
            mean /= n;
            for (int i = 0; i < n; i++) Yc.set(i, j, data.getDouble(i, col) - mean);
        }

        SimpleMatrix omega;
        double gapRatio;

        if (whitenBeforeSVD) {
            // A = Σ_ZZ^{-1/2} Σ_ZY Σ_YY^{-1/2}
            SimpleMatrix SzzInvH = invSqrtSym(Szz, ridge);
            SimpleMatrix SyyInvH = invSqrtSym(Syy, ridge);
            SimpleMatrix A = SzzInvH.mult(Szy).mult(SyyInvH);

            SimpleSVD<SimpleMatrix> svd = A.svd();
            SimpleMatrix W = svd.getW();
            SimpleMatrix V = svd.getV();

            // smallest and next-smallest σ
            int r = Math.min(W.numRows(), W.numCols());
            // Collect singulars
            double minSv = Double.POSITIVE_INFINITY, nextSv = Double.POSITIVE_INFINITY;
            int minIdx = -1;
            for (int i = 0; i < r; i++) {
                double sv = W.get(i, i);
                if (sv < minSv) {
                    nextSv = minSv;
                    minSv = sv;
                    minIdx = i;
                } else if (sv < nextSv) {
                    nextSv = sv;
                }
            }
            gapRatio = (nextSv == 0.0) ? 0.0 : (minSv / nextSv);
            if (minIdx < 0) minIdx = r - 1;

            SimpleMatrix vmin = V.extractVector(false, minIdx);
            omega = SyyInvH.mult(vmin);

        } else {
            // plain Σ_ZY SVD
            SimpleSVD<SimpleMatrix> svd = Szy.svd();
            SimpleMatrix W = svd.getW();
            SimpleMatrix V = svd.getV();

            int r = Math.min(W.numRows(), W.numCols());
            double minSv = Double.POSITIVE_INFINITY, nextSv = Double.POSITIVE_INFINITY;
            int minIdx = -1;
            for (int i = 0; i < r; i++) {
                double sv = W.get(i, i);
                if (sv < minSv) {
                    nextSv = minSv;
                    minSv = sv;
                    minIdx = i;
                } else if (sv < nextSv) {
                    nextSv = sv;
                }
            }
            gapRatio = (nextSv == 0.0) ? 0.0 : (minSv / nextSv);
            if (minIdx < 0) minIdx = r - 1;

            omega = V.extractVector(false, minIdx);
        }

        double[] e = Yc.mult(omega).getDDRM().getData();
        boolean ok = (gapRatio <= gapThreshold) || !Double.isFinite(gapRatio);
        return new ProjResult(e, gapRatio, ok);
    }

    private double pValueEvsZ(double[] e, List<Integer> Z) {
        final int n = e.length;
        if (Z == null || Z.isEmpty()) return 1.0;

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

    /**
     * Pairwise (unconditional) dependence by Fisher-Z at level alpha.
     */
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
                .map(cl -> cl.stream().map(i -> vars.get(i).getName()).collect(Collectors.toList()).toString())
                .collect(Collectors.joining(" | "));
    }

    public enum OrderMode {IT, MI} // MI stubbed

    private static final class ProjResult {
        final double[] e;
        final double gapRatio;   // σ_min / σ_next (<=1); Double.POSITIVE_INFINITY if rank-1
        final boolean acceptable;

        ProjResult(double[] e, double gapRatio, boolean acceptable) {
            this.e = e;
            this.gapRatio = gapRatio;
            this.acceptable = acceptable;
        }
    }

    // --------------------------- Helper ------------------------------

    private static final class EdgeCand {
        final int i, j;     // direction i -> j
        final double p;     // p(i->j)
        final double pOpp;  // p(j->i) for asymmetry gate

        EdgeCand(int i, int j, double p, double pOpp) {
            this.i = i;
            this.j = j;
            this.p = p;
            this.pOpp = pOpp;
        }
    }
}
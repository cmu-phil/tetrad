///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Math.sqrt;

/**
 * Minimal GIN: 1) Get clusters from TSC. 2) Add one latent per cluster, connect to its observed children. 3) For each
 * unordered latent pair {i,j}: - Compute smallest-Ï projection for i->j (Y=j, Z=i) and for j->i (Y=i, Z=j) - p_ij =
 * p(e(Y|Z) â Z) via RawMarginalIndependenceTest - If max(p_ij, p_ji) >= alpha, add ONLY the better direction.
 * <p>
 * Notes: â¢ Allows cliques/cycles intentionally (no gates, no acyclicity checks). â¢ Uses covariance (matches centering).
 * â¢ Whitening enabled by default; small ridge helps stability.
 */
public class Gin {

    // ----------------------------- Params -----------------------------
    private final double alpha;
    private final RawMarginalIndependenceTest test;
    private final List<Node> nodes;           // for pretty logging names
    private boolean verbose = false;
    private boolean whitenBeforeSVD = true;   // numerical guard (default ON)
    private double ridge = 3e-8;              // tiny ridge for whitening
    // ----------------------------- State -----------------------------
    private DataSet data;
    private SimpleMatrix cov;                 // covariance of observed
    private List<Node> vars;                  // observed nodes
    private List<List<Integer>> clusters;     // TSC clusters (indices into vars)

    // ----------------------------- Ctors -----------------------------

    /**
     * Constructs a Gin instance with the specified significance level and independence test.
     *
     * @param alpha the significance level to be used for statistical tests, typically in the range [0, 1]
     * @param test the raw marginal independence test instance, used to compute p-values for independence testing
     * @throws NullPointerException if the provided test is null
     */
    public Gin(double alpha, RawMarginalIndependenceTest test) {
        this.alpha = alpha;
        this.test = Objects.requireNonNull(test, "test");
        this.nodes = ((IndependenceTest) test).getVariables();
    }

    private static boolean hasVariance(double[] e) {
        int n = e.length;
        if (n <= 1) return false;
        double m = 0.0;
        for (double v : e) m += v;
        m /= n;
        double s2 = 0.0;
        for (double v : e) {
            double d = v - m;
            s2 += d * d;
        }
        return s2 > 0.0;
    }

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

    // ----------------------------- Options ---------------------------

    /**
     * Sets the verbose mode for logging or output. When enabled, additional
     * details may be provided for debugging or informational purposes.
     *
     * @param v true to enable verbose mode, false to disable it
     */
    public void setVerbose(boolean v) {
        this.verbose = v;
    }

    /**
     * Sets whether whitening should be applied before performing Singular Value Decomposition (SVD).
     *
     * @param w true if whitening should be applied before SVD, false otherwise
     */
    public void setWhitenBeforeSVD(boolean w) {
        this.whitenBeforeSVD = w;
    }

    /**
     * Sets the ridge value for regularization. The ridge value is used as a
     * regularization parameter to ensure numerical stability in computations.
     * It is constrained to be non-negative.
     *
     * @param r the ridge value to set. If the provided value is negative, it
     *          will be set to 0.0.
     */
    public void setRidge(double r) {
        this.ridge = Math.max(0.0, r);
    }

    // ---------------------- Orientation (basic) ----------------------

    // ----------------------------- API -------------------------------

    /**
     * Searches and constructs a causal graph representation using the provided dataset.
     * The method identifies clusters, builds the latent measurement structure,
     * and performs basic GIN (Generalized Independent Noise) orientation.
     *
     * @param data the dataset containing variables and their covariance information,
     *             used to construct the graph.
     * @return a graphical representation (Graph) that includes both observed and
     *         latent variables with directed edges based on relationships derived
     *         from the analysis.
     */
    public Graph search(DataSet data) {
        this.data = data;
        // Use covariance (not correlation) to match the centered Y used to form e
        this.cov = new SimpleMatrix(data.getCovarianceMatrix().getSimpleMatrix());
        this.vars = data.getVariables();

        // 1) TSC clusters
        this.clusters = findClustersTSC();
        if (clusters.isEmpty()) {
            clusters = new ArrayList<>();
            for (int i = 0; i < vars.size(); i++) clusters.add(List.of(i));
            if (verbose) TetradLogger.getInstance().log("[GIN] No clusters; using singletons.");
        }
        if (verbose) {
            TetradLogger.getInstance().log("[GIN] clusters=" + clustersAsNames(clusters));
        }

        // 2) Build latent measurement structure
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

        // 3) Basic GIN orientation (no gates)
        orientBasicGIN(g, clusters, latents);
        return g;
    }

    // ---------------------- Projection e = Yc * Ï --------------------

    // ---------------------- TSC clusters -----------------------------
    private List<List<Integer>> findClustersTSC() {
        Tsc tsc = new Tsc(data.getVariables(), new CorrelationMatrix(data));
        tsc.setAlpha(alpha);
        tsc.setMinRedundancy(0);

        List<List<Integer>> out = new ArrayList<>();
        for (Set<Integer> seed : tsc.findClusters().keySet()) {
            out.add(new ArrayList<>(seed));
        }
        return out;
    }

    /**
     * For each unordered {i,j}: p_ij = p(e(Y=j | Z=i) â Z=i), p_ji = p(e(Y=i | Z=j) â Z=j). If max(p_ij, p_ji) >=
     * alpha, add the larger direction (ties broken toward i->j).
     */
    private void orientBasicGIN(Graph g, List<List<Integer>> clusters, List<Node> latents) {
        final int m = clusters.size();

        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                if (Thread.currentThread().isInterrupted()) return;

                List<Integer> Zi = clusters.get(i);
                List<Integer> Yj = clusters.get(j);
                List<Integer> Zj = clusters.get(j);
                List<Integer> Yi = clusters.get(i);

                // i -> j
                ProjResult proj_ij = computeProjection(Yj, Zi);
                double p_ij = (proj_ij.ok) ? pValueEvsZ(proj_ij.e, Zi) : 0.0;

                // j -> i
                ProjResult proj_ji = computeProjection(Yi, Zj);
                double p_ji = (proj_ji.ok) ? pValueEvsZ(proj_ji.e, Zj) : 0.0;

                if (verbose) {
                    TetradLogger.getInstance().log(String.format(
                            "[GIN] L%dâL%d  p_ij=%.4g (Ïmin=%.3g) | p_ji=%.4g (Ïmin=%.3g)",
                            i + 1, j + 1, p_ij, proj_ij.sigmaMin, p_ji, proj_ji.sigmaMin));
                }

                if (p_ij >= alpha || p_ji >= alpha) {
                    if (p_ij >= p_ji) g.addDirectedEdge(latents.get(i), latents.get(j));
                    else g.addDirectedEdge(latents.get(j), latents.get(i));
                }
            }
        }
    }

    /**
     * Compute smallest-Ï right singular direction: A = Î£_ZZ^{-1/2} Î£_ZY Î£_YY^{-1/2}   (if whitening), else A = Î£_ZY.
     * Return e = Yc * Ï, where Ï = Î£_YY^{-1/2} v_min (or v_min if no whitening).
     */
    private ProjResult computeProjection(List<Integer> Y, List<Integer> Z) {
        final int n = data.getNumRows();
        if (Y == null || Z == null || Y.isEmpty() || Z.isEmpty()) {
            return new ProjResult(new double[n], Double.NaN, false);
        }

        // Î£ blocks
        SimpleMatrix Syy = subCov(cov, Y, Y);
        SimpleMatrix Szz = subCov(cov, Z, Z);
        SimpleMatrix Szy = subCov(cov, Z, Y);

        // Center Y
        SimpleMatrix Yc = new SimpleMatrix(n, Y.size());
        for (int j = 0; j < Y.size(); j++) {
            int col = Y.get(j);
            double mean = 0.0;
            for (int i = 0; i < n; i++) mean += data.getDouble(i, col);
            mean /= n;
            for (int i = 0; i < n; i++) Yc.set(i, j, data.getDouble(i, col) - mean);
        }

        // A and mapBack
        SimpleMatrix A, mapBack;
        if (whitenBeforeSVD) {
            SimpleMatrix SzzInvH = invSqrtSym(Szz, ridge);
            SimpleMatrix SyyInvH = invSqrtSym(Syy, ridge);
            A = SzzInvH.mult(Szy).mult(SyyInvH);
            mapBack = SyyInvH;
        } else {
            A = Szy;
            mapBack = SimpleMatrix.identity(Syy.getNumRows());
        }

        SimpleSVD<SimpleMatrix> svd = A.svd();
        SimpleMatrix W = svd.getW();
        SimpleMatrix V = svd.getV();

        int r = Math.min(Math.min(W.getNumRows(), W.getNumCols()), V.getNumCols());
        if (r <= 0) return new ProjResult(new double[n], Double.NaN, false);

        // find smallest singular value index
        int minIdx = 0;
        double sigmaMin = W.get(0, 0);
        for (int k = 1; k < r; k++) {
            double sv = W.get(k, k);
            if (sv < sigmaMin) {
                sigmaMin = sv;
                minIdx = k;
            }
        }

        SimpleMatrix vmin = V.extractVector(false, minIdx);
        SimpleMatrix omega = mapBack.mult(vmin);
        double[] e = Yc.mult(omega).getDDRM().getData();

        // Minimal sanity: require some variance (avoid all-zero e)
        boolean ok = hasVariance(e);
        return new ProjResult(e, sigmaMin, ok);
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

    // ---------------------- Utilities ---------------------------
    private SimpleMatrix subCov(SimpleMatrix S, List<Integer> rows, List<Integer> cols) {
        SimpleMatrix out = new SimpleMatrix(rows.size(), cols.size());
        for (int i = 0; i < rows.size(); i++) {
            for (int j = 0; j < cols.size(); j++) {
                out.set(i, j, S.get(rows.get(i), cols.get(j)));
            }
        }
        return out;
    }

    private String clustersAsNames(List<List<Integer>> cl) {
        return cl.stream()
                .map(c -> c.stream().map(i -> vars.get(i).getName()).toList().toString())
                .collect(Collectors.joining(" | "));
    }

    // --------------------------- Helper types -----------------------
    private static final class ProjResult {
        final double[] e;
        final double sigmaMin;
        final boolean ok;

        ProjResult(double[] e, double sigmaMin, boolean ok) {
            this.e = e;
            this.sigmaMin = sigmaMin;
            this.ok = ok;
        }
    }
}

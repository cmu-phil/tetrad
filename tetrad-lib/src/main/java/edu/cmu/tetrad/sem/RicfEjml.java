package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import edu.cmu.tetrad.util.TMath;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.NormOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * RICF for ADMGs (directed + bidirected edges only), i.e. NO selection bias (no undirected edges / UG block).
 *
 * This version is an EJML rewrite with two key correctness fixes:
 *  1) B is inverted using a general inverse (LU), NOT an SPD/Cholesky inverse.
 *  2) Omega is symmetrized at the end and diagonals are stabilized.
 *
 * Notes:
 *  - Variable order is taken from covMatrix.getVariableNames().
 *  - Graph must contain nodes with those names.
 */
public final class RicfEjml {

    /**
     * Constructs an instance of the {@code RicfEjml} class.
     *
     * This class contains implementations for the Residual Iterative Conditional Fitting (RICF)
     * algorithm, which is used for parameter estimation in additive directed mixed graphs (ADMGs).
     */
    public RicfEjml() {}

    /**
     * Represents the result of the Residual Iterative Conditional Fitting (RICF) algorithm
     * for additive directed mixed graphs (ADMGs). This class encapsulates the estimated
     * structural parameters, the number of iterations performed, and a convergence metric.
     */
    public static final class RicfResult {
        private final DMatrixRMaj sigmaHat;   // implied covariance
        private final DMatrixRMaj bHat;       // B = I - Beta
        private final DMatrixRMaj omegaHat;   // residual/error covariance (includes bidirected structure)
        private final int iters;
        private final double diff;
        private final double logLik;          // Gaussian log-likelihood of the fitted model

        /**
         * Constructs a RicfResult object that contains the results of the Ricf algorithm.
         *
         * @param sigmaHat The implied covariance matrix.
         * @param bHat The matrix representing B = I - Beta.
         * @param omegaHat The residual or error covariance matrix, which includes bidirected structure.
         * @param iters The number of iterations performed by the algorithm.
         * @param diff The difference or convergence metric from the algorithm.
         * @param logLik The Gaussian log-likelihood of the fitted model.
         */
        public RicfResult(DMatrixRMaj sigmaHat, DMatrixRMaj bHat, DMatrixRMaj omegaHat, int iters, double diff, double logLik) {
            this.sigmaHat = sigmaHat;
            this.bHat = bHat;
            this.omegaHat = omegaHat;
            this.iters = iters;
            this.diff = diff;
            this.logLik = logLik;
        }

        /**
         * Retrieves the implied covariance matrix (sigmaHat) resulting from the Ricf algorithm.
         *
         * @return The implied covariance matrix represented as a DMatrixRMaj object.
         */
        public DMatrixRMaj getSigmaHat() { return sigmaHat; }

        /**
         * Retrieves the matrix representing B = I - Beta resulting from the Ricf algorithm.
         *
         * @return The matrix B = I - Beta, represented as a DMatrixRMaj object.
         */
        public DMatrixRMaj getBhat()     { return bHat; }

        /**
         * Retrieves the residual or error covariance matrix (omegaHat), which includes bidirected structure,
         * resulting from the Ricf algorithm.
         *
         * @return The residual or error covariance matrix represented as a DMatrixRMaj object.
         */
        public DMatrixRMaj getOmegahat() { return omegaHat; }

        /**
         * Retrieves the number of iterations performed by the algorithm.
         *
         * @return The number of iterations as an integer.
         */
        public int getIters()            { return iters; }

        /**
         * Retrieves the difference or convergence metric resulting from the Ricf algorithm.
         *
         * @return The difference or convergence metric as a double.
         */
        public double getDiff()          { return diff; }

        /**
         * Retrieves the Gaussian log-likelihood of the fitted model.
         *
         * <p>This is the multivariate-normal log-likelihood evaluated at the implied
         * covariance matrix (sigmaHat), computed as
         * {@code -(n/2) * (p*log(2*pi) + log|sigmaHat| + tr(sigmaHat^{-1} S))},
         * where {@code n} is the sample size, {@code p} the number of variables, and
         * {@code S} the sample covariance matrix.</p>
         *
         * @return The Gaussian log-likelihood as a double.
         */
        public double getLogLik()        { return logLik; }

        /**
         * Returns a string representation of the RicfResult object.
         * The string includes the number of iterations performed and the
         * convergence difference metric resulting from the Ricf algorithm.
         *
         * @return A string representation of the object.
         */
        @Override public String toString() {
            return "RicfResult{iters=" + iters + ", diff=" + diff + ", logLik=" + logLik + "}";
        }
    }

    /**
     * Implements the Residual Iterative Conditional Fitting (RICF) algorithm for additive directed mixed graphs (ADMGs).
     * The method estimates the structural parameters (e.g., regression coefficients and variances) of a model defined on the ADMG.
     *
     * @param admg       An instance of the {@code Graph} class representing the ADMG. It must not be {@code null}.
     *                   Each variable in the covariance matrix must correspond to a node in this graph.
     * @param covMatrix  An instance of the {@code ICovarianceMatrix} interface representing the sample covariance matrix.
     *                   The dimensions of the covariance matrix must match the number of variables in the graph.
     * @param tol        A positive threshold for the stopping criterion. The algorithm terminates when the maximum absolute difference
     *                   in estimated parameters across successive iterations is less than this value.
     * @param maxIters   The maximum number of iterations to perform. The algorithm terminates if this limit is reached before convergence.
     * @return           A {@code RicfResult} containing the estimated regression coefficients, variances, covariance matrix,
     *                   total iterations performed, and the final difference measure for the stopping criterion.
     * @throws NullPointerException     If either {@code admg} or {@code covMatrix} is {@code null}.
     * @throws IllegalArgumentException If the dimensions of the covariance matrix do not match the variables in the graph,
     *                                   or if the graph is missing variables specified in the covariance matrix.
     */
    public RicfResult ricf(Graph admg, ICovarianceMatrix covMatrix, double tol, int maxIters) {
        Objects.requireNonNull(admg, "admg");
        Objects.requireNonNull(covMatrix, "covMatrix");

        List<String> varNames = covMatrix.getVariableNames();
        int p = covMatrix.getDimension();
        if (p != varNames.size()) throw new IllegalArgumentException("covMatrix dimension mismatch.");

        // Map names -> nodes (validate existence)
        List<Node> nodes = new ArrayList<>(p);
        List<String> missing = new ArrayList<>();
        for (String name : varNames) {
            Node v = admg.getNode(name);
            if (v == null) missing.add(name);
            nodes.add(v);
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("RICF: Graph is missing variables from covariance matrix: " + missing);
        }

        // S = sample covariance in cov order
        SimpleMatrix S = new SimpleMatrix(covMatrix.getMatrix().toArray());
        int n = covMatrix.getSampleSize();

        if (p == 1) {
            DMatrixRMaj s = S.getDDRM().copy();
            double logLik1 = gaussianLogLikelihood(S, S, n);
            return new RicfResult(s, CommonOps_DDRM.identity(1), s, 1, 0.0, logLik1);
        }

        // B starts at identity; Omega starts at diag(S)
        SimpleMatrix B = SimpleMatrix.identity(p);
        SimpleMatrix Omega = diag(diag(S));

        // Precompute parents/spouses index lists in cov order
        int[][] parents = parentIndices(admg, nodes);
        int[][] spouses = spouseIndices(admg, nodes);

        int it = 0;
        double diff = Double.POSITIVE_INFINITY;

        // Optional: one-shot initialization for nodes with no spouses (matches your Colt code spirit)
        // (This helps convergence but isn't required for correctness.)
        initializeNoSpouseNodesOnce(S, B, Omega, parents, spouses);

        while (it < maxIters && diff > tol) {
            it++;

            SimpleMatrix Bold = B.copy();
            SimpleMatrix OmegaOld = Omega.copy();

            for (int v = 0; v < p; v++) {
                int[] parv = parents[v];
                int[] spov = spouses[v];

                if (spov.length == 0) {
                    // In ADMG RICF, if there are no spouses, Beta update is basically regression;
                    // we already initialized; further updates are optional. Keeping it simple/stable:
                    continue;
                }

                int[] vcomp = complement(p, v);
                int[] all = range(p);

                // oInv = inv(Omega[vcomp, vcomp])
                SimpleMatrix Omega_vc_vc = Omega.extractMatrix(vcomp[0], vcomp[vcomp.length - 1] + 1,
                        vcomp[0], vcomp[vcomp.length - 1] + 1);
                // NOTE: extractMatrix needs contiguous ranges; vcomp is not contiguous generally.
                // So use explicit submatrix helper:
                Omega_vc_vc = select(Omega, vcomp, vcomp);

                SimpleMatrix oInv = safeInverseSPD(Omega_vc_vc, 1e-10);

//                // Z = oInv[spov, vcomp] * B[vcomp, all]  => |sp| x p
//                SimpleMatrix Z = select(oInv, indexOfSub(vcomp, spov), range(vcomp.length))  // WRONG mapping
//                        .mult(select(B, vcomp, all));                                      // too messy

//                int[] all = range(B.numCols());        // B is p x p, so all is 0..p-1

                int[] spovPosInVcomp = positionsIn(vcomp, spov);   // if spov are global indices
                int[] vcompPosInVcomp = range(vcomp.length);       // 0..|vcomp|-1

                SimpleMatrix Z = select(oInv, spovPosInVcomp, vcompPosInVcomp)
                        .mult(select(B, vcomp, all));              // B uses global indices

                // Z = oInv[spov, vcomp] * B[vcomp, all]  => |spov| x p
//                SimpleMatrix Z = select(oInv, spov, vcomp)
//                        .mult(select(B, vcomp, all));

                // Better: build Z explicitly:
                SimpleMatrix oInv_sp_vc = select(oInv, mapToVcompIndices(vcomp, spov), range(vcomp.length));
                SimpleMatrix B_vc_all   = select(B, vcomp, all);
                Z = oInv_sp_vc.mult(B_vc_all);

                if (parv.length > 0) {
                    // XX is block matrix:
                    // [ S(par,par)             S(par,all) Z' ]
                    // [ (S(par,all) Z')'       Z S Z'      ]
                    int lpa = parv.length;
                    int lspo = spov.length;

                    SimpleMatrix XX = new SimpleMatrix(lpa + lspo, lpa + lspo);

                    SimpleMatrix S_par_par = select(S, parv, parv);
                    XX.insertIntoThis(0, 0, S_par_par);

                    SimpleMatrix S_par_all = select(S, parv, all);
                    SimpleMatrix UR = S_par_all.mult(Z.transpose());          // lpa x lspo
                    XX.insertIntoThis(0, lpa, UR);
                    XX.insertIntoThis(lpa, 0, UR.transpose());

                    SimpleMatrix LR = Z.mult(S).mult(Z.transpose());          // lspo x lspo
                    XX.insertIntoThis(lpa, lpa, LR);

                    // YX = [ S(v,par) , S(v,all) Z' ]'  (column)
                    SimpleMatrix YX = new SimpleMatrix(lpa + lspo, 1);
                    SimpleMatrix S_v_par = select(S, new int[]{v}, parv).transpose();      // lpa x 1
                    YX.insertIntoThis(0, 0, S_v_par);

                    SimpleMatrix S_v_all = select(S, new int[]{v}, all);                   // 1 x p
                    SimpleMatrix S_v_all_Zt = S_v_all.mult(Z.transpose()).transpose();     // lspo x 1
                    YX.insertIntoThis(lpa, 0, S_v_all_Zt);

                    // temp = XX^{-1} * YX
                    SimpleMatrix temp = XX.solve(YX);

                    // Update B[v,par] = -temp[0:lpa]
                    for (int k = 0; k < lpa; k++) {
                        B.set(v, parv[k], -temp.get(k, 0));
                    }

                    // Update Omega[v,sp] and symmetric
                    for (int k = 0; k < lspo; k++) {
                        double val = temp.get(lpa + k, 0);
                        Omega.set(v, spov[k], val);
                        Omega.set(spov[k], v, val);
                    }

                    // Variance update:
                    // tempVar = Svv - temp' * YX
                    double Svv = S.get(v, v);
                    double tempDotYX = temp.transpose().mult(YX).get(0, 0);
                    double tempVar = Svv - tempDotYX;

                    // add Omega[v,sp] * oInv[sp,sp] * Omega[sp,v]
                    SimpleMatrix Omega_v_sp = select(Omega, new int[]{v}, spov);           // 1 x lspo
                    SimpleMatrix oInv_sp_sp  = select(oInv, mapToVcompIndices(vcomp, spov), mapToVcompIndices(vcomp, spov));
                    SimpleMatrix Omega_sp_v  = Omega_v_sp.transpose();                     // lspo x 1
                    double add = Omega_v_sp.mult(oInv_sp_sp).mult(Omega_sp_v).get(0, 0);

                    Omega.set(v, v, tempVar + add);

                } else {
                    // par empty, spouse nonempty:
                    // XX = Z S Z'
                    // YX = (S(v,all) Z')'
                    SimpleMatrix XX = Z.mult(S).mult(Z.transpose());
                    SimpleMatrix YX = select(S, new int[]{v}, all).mult(Z.transpose()).transpose(); // lspo x 1

                    SimpleMatrix temp = XX.solve(YX);

                    for (int k = 0; k < spov.length; k++) {
                        double val = temp.get(k, 0);
                        Omega.set(v, spov[k], val);
                        Omega.set(spov[k], v, val);
                    }

                    double Svv = S.get(v, v);
                    double tempDotYX = temp.transpose().mult(YX).get(0, 0);
                    double tempVar = Svv - tempDotYX;

                    SimpleMatrix Omega_v_sp = select(Omega, new int[]{v}, spov);
                    SimpleMatrix oInv_sp_sp  = select(oInv, mapToVcompIndices(vcomp, spov), mapToVcompIndices(vcomp, spov));
                    double add = Omega_v_sp.mult(oInv_sp_sp).mult(Omega_v_sp.transpose()).get(0, 0);

                    Omega.set(v, v, tempVar + add);
                }
            }

            // diff = ||Omega - OmegaOld||_1 + ||B - Bold||_1
            diff = NormOps_DDRM.normP1(Omega.minus(OmegaOld).getDDRM())
                    + NormOps_DDRM.normP1(B.minus(Bold).getDDRM());
        }

        // Symmetrize Omega and stabilize diagonal
        Omega = symmetrize(Omega);
        stabilizeDiagonal(Omega, 1e-8);

        // sigmaHat = inv(B) * Omega * inv(B')  (GENERAL inverse, not SPD)
        SimpleMatrix invB  = B.invert();
        SimpleMatrix invBt = B.transpose().invert();
        SimpleMatrix sigmaHat = invB.mult(Omega).mult(invBt);

        // Gaussian log-likelihood of the fitted model, evaluated at sigmaHat.
        double logLik = gaussianLogLikelihood(sigmaHat, S, n);

        return new RicfResult(sigmaHat.getDDRM().copy(), B.getDDRM().copy(), Omega.getDDRM().copy(), it, diff, logLik);
    }

    // ---------------- helpers ----------------

    /**
     * Multivariate-normal log-likelihood evaluated at a model-implied covariance matrix.
     *
     * <p>Computes {@code -(n/2) * (p*log(2*pi) + log|sigma| + tr(sigma^{-1} S))}, the standard
     * Gaussian (maximized over the mean) log-likelihood for {@code n} i.i.d. observations with
     * sample covariance {@code S} under a model with implied covariance {@code sigma}.</p>
     *
     * @param sigma model-implied covariance (p x p, symmetric positive definite).
     * @param S     sample covariance (p x p).
     * @param n     sample size.
     * @return the Gaussian log-likelihood.
     */
    private static double gaussianLogLikelihood(SimpleMatrix sigma, SimpleMatrix S, int n) {
        int p = sigma.numRows();
        double logDet = logDetSPD(sigma);
        double trace = sigma.invert().mult(S).trace();
        return -0.5 * n * (p * Math.log(2.0 * Math.PI) + logDet + trace);
    }

    /**
     * Stable log-determinant for a symmetric positive-definite matrix via Cholesky.
     * Falls back to log|det| (LU) if the Cholesky decomposition fails.
     */
    private static double logDetSPD(SimpleMatrix A) {
        int n = A.numRows();
        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(n, true);
        DMatrixRMaj copy = A.getDDRM().copy();
        if (chol.decompose(copy)) {
            DMatrixRMaj L = chol.getT(null);
            double logDet = 0.0;
            for (int i = 0; i < n; i++) {
                logDet += 2.0 * Math.log(L.get(i, i));
            }
            return logDet;
        }
        // Fallback: general determinant (may be less stable / could be non-positive numerically).
        return Math.log(Math.abs(A.determinant()));
    }

    private static int[][] parentIndices(Graph g, List<Node> nodes) {
        int p = nodes.size();
        int[][] out = new int[p][];
        for (int j = 0; j < p; j++) {
            Node child = nodes.get(j);
            List<Integer> pars = new ArrayList<>();
            for (Node par : g.getParents(child)) {
                int i = nodes.indexOf(par);
                if (i >= 0) pars.add(i);
            }
            out[j] = pars.stream().mapToInt(x -> x).toArray();
        }
        return out;
    }

    private static int[][] spouseIndices(Graph g, List<Node> nodes) {
        int p = nodes.size();
        int[][] out = new int[p][];
        for (int v = 0; v < p; v++) {
            Node nv = nodes.get(v);
            List<Integer> sp = new ArrayList<>();
            for (Edge e : g.getEdges(nv)) {
                if (Edges.isBidirectedEdge(e)) {
                    Node other = e.getDistalNode(nv);
                    int idx = nodes.indexOf(other);
                    if (idx >= 0) sp.add(idx);
                }
            }
            out[v] = sp.stream().distinct().sorted().mapToInt(x -> x).toArray();
        }
        return out;
    }

    private static void initializeNoSpouseNodesOnce(SimpleMatrix S, SimpleMatrix B, SimpleMatrix Omega,
                                                    int[][] parents, int[][] spouses) {
        int p = S.numRows();
        for (int v = 0; v < p; v++) {
            if (spouses[v].length != 0) continue;
            int[] parv = parents[v];
            if (parv.length == 0) continue;

            // beta = S(v,par) * inv(S(par,par))
            SimpleMatrix S_par_par = select(S, parv, parv);
            SimpleMatrix S_v_par   = select(S, new int[]{v}, parv);
            SimpleMatrix betaRow   = S_v_par.mult(S_par_par.invert()); // 1 x |par|

            for (int k = 0; k < parv.length; k++) {
                B.set(v, parv[k], -betaRow.get(0, k));
            }

            // Omega(v,v) = S(v,v) - beta * S(par,v)
            SimpleMatrix S_par_v = select(S, parv, new int[]{v}); // |par| x 1
            double Svv = S.get(v, v);
            double sub = betaRow.mult(S_par_v).get(0, 0);
            Omega.set(v, v, TMath.max(1e-8, Svv - sub));
        }
    }

    private static SimpleMatrix safeInverseSPD(SimpleMatrix A, double jitter0) {
        // Cholesky-based solve; if it fails, jitter diagonal and retry.
        // SimpleMatrix.invert() is general (LU), but for Omega blocks we prefer SPD stability.
        SimpleMatrix M = A.copy();
        double jitter = jitter0;
        for (int t = 0; t < 10; t++) {
            try {
                // Try Cholesky via solve(I)
                SimpleMatrix I = SimpleMatrix.identity(M.numRows());
                return M.solve(I);
            } catch (RuntimeException ex) {
                for (int i = 0; i < M.numRows(); i++) {
                    M.set(i, i, M.get(i, i) + jitter);
                }
                jitter *= 10.0;
            }
        }
        // Fallback to general inverse if all else fails
        return A.invert();
    }

//    private static SimpleMatrix diag(SimpleMatrix v) {
//        int n = v.numRows();
//        SimpleMatrix D = new SimpleMatrix(n, n);
//        for (int i = 0; i < n; i++) D.set(i, i, v.get(i, 0));
//        return D;
//    }
//
//    private static SimpleMatrix diag(SimpleMatrix A) {
//        int n = A.numRows();
//        SimpleMatrix v = new SimpleMatrix(n, 1);
//        for (int i = 0; i < n; i++) v.set(i, 0, A.get(i, i));
//        return v;
//    }

    /**
     * If input is a vector (n×1 or 1×n), return an n×n diagonal matrix.
     * If input is a square matrix (n×n), return its diagonal as an n×1 column vector.
     */
    private static SimpleMatrix diag(SimpleMatrix M) {
        int rows = M.numRows();
        int cols = M.numCols();

        // Case 1: vector -> diagonal matrix
        if (rows == 1 || cols == 1) {
            int n = TMath.max(rows, cols);
            SimpleMatrix D = new SimpleMatrix(n, n);

            for (int i = 0; i < n; i++) {
                double v = (rows == 1) ? M.get(0, i) : M.get(i, 0);
                D.set(i, i, v);
            }
            return D;
        }

        // Case 2: square matrix -> diagonal vector
        if (rows == cols) {
            int n = rows;
            SimpleMatrix v = new SimpleMatrix(n, 1);
            for (int i = 0; i < n; i++) {
                v.set(i, 0, M.get(i, i));
            }
            return v;
        }

        throw new IllegalArgumentException(
                "diag(): input must be a vector or a square matrix, got "
                        + rows + "×" + cols
        );
    }

    private static SimpleMatrix diag(SimpleMatrix A, boolean dummy) { return diag(diag(A)); }

    private static SimpleMatrix symmetrize(SimpleMatrix A) {
        return A.plus(A.transpose()).scale(0.5);
    }

    private static void stabilizeDiagonal(SimpleMatrix A, double eps) {
        for (int i = 0; i < A.numRows(); i++) {
            double d = A.get(i, i);
            if (!(d > 0.0)) A.set(i, i, eps);
        }
    }

    private static int[] range(int p) {
        int[] r = new int[p];
        for (int i = 0; i < p; i++) r[i] = i;
        return r;
    }

    private static int[] complement(int p, int v) {
        int[] out = new int[p - 1];
        int k = 0;
        for (int i = 0; i < p; i++) if (i != v) out[k++] = i;
        return out;
    }

    private static int[] selectIndices(int[] base, Set<Integer> keep) {
        return keep.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int[] mapToVcompIndices(int[] vcomp, int[] indicesInP) {
        // Given absolute indices (0..p-1) in indicesInP, return their positions within vcomp.
        Map<Integer, Integer> pos = new HashMap<>();
        for (int i = 0; i < vcomp.length; i++) pos.put(vcomp[i], i);

        int[] out = new int[indicesInP.length];
        for (int i = 0; i < indicesInP.length; i++) {
            Integer k = pos.get(indicesInP[i]);
            if (k == null) throw new IllegalStateException("index not in vcomp: " + indicesInP[i]);
            out[i] = k;
        }
        return out;
    }

//    private static SimpleMatrix select(SimpleMatrix A, int[] rows, int[] cols) {
//        SimpleMatrix out = new SimpleMatrix(rows.length, cols.length);
//        for (int i = 0; i < rows.length; i++) {
//            for (int j = 0; j < cols.length; j++) {
//                out.set(i, j, A.get(rows[i], cols[j]));
//            }
//        }
//        return out;
//    }

    private static SimpleMatrix select(SimpleMatrix M, int[] rows, int[] cols) {
        int R = M.numRows(), C = M.numCols();

        for (int r : rows) {
            if (r < 0 || r >= R) {
                throw new IllegalArgumentException("select(): row index out of bounds r=" + r +
                        " for matrix " + R + "x" + C + " rows=" + Arrays.toString(rows) +
                        " cols=" + Arrays.toString(cols));
            }
        }
        for (int c : cols) {
            if (c < 0 || c >= C) {
                throw new IllegalArgumentException("select(): col index out of bounds c=" + c +
                        " for matrix " + R + "x" + C + " rows=" + Arrays.toString(rows) +
                        " cols=" + Arrays.toString(cols));
            }
        }

        SimpleMatrix out = new SimpleMatrix(rows.length, cols.length);
        for (int i = 0; i < rows.length; i++) {
            int r = rows[i];
            for (int j = 0; j < cols.length; j++) {
                out.set(i, j, M.get(r, cols[j]));
            }
        }
        return out;
    }

    private static int[] positionsIn(int[] base, int[] subset) {
        // base: e.g., vcomp (global indices)
        // subset: e.g., spov (global indices) OR vcomp itself
        // returns: positions of each subset element within base
        Map<Integer, Integer> pos = new HashMap<>(base.length * 2);
        for (int i = 0; i < base.length; i++) pos.put(base[i], i);

        int[] out = new int[subset.length];
        for (int i = 0; i < subset.length; i++) {
            Integer p = pos.get(subset[i]);
            if (p == null) {
                throw new IllegalArgumentException("positionsIn(): element " + subset[i] +
                        " not found in base " + Arrays.toString(base));
            }
            out[i] = p;
        }
        return out;
    }
}
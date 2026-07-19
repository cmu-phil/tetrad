package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Residual Iterative Conditional Fitting (RICF) for Gaussian ADMG / MAG models
 * (directed and bidirected edges only; no undirected/selection block).
 *
 * <p>This is a clean EJML port of Bryan Andrews' reference Python implementation
 * ({@code ricf.py}). It follows that implementation step for step, including its
 * variable conventions, so the two agree to machine precision on the same inputs.</p>
 *
 * Conventions (as in the Python)
 * <ul>
 *   <li>{@code IB} is the {@code I - Beta} matrix: it starts at the identity and its
 *       off-diagonal entry {@code IB[v, par] = -Beta[v, par]} holds the negated
 *       structural coefficient of parent {@code par} in the equation for {@code v}.</li>
 *   <li>{@code Omega} is the error covariance, carrying the bidirected structure in its
 *       off-diagonals.</li>
 *   <li>The implied covariance is {@code Sigma = inv(IB) * Omega * inv(IB)^T}, and the
 *       direct-effects matrix is {@code Beta = I - IB} (this is the {@code B} returned by
 *       the Python).</li>
 * </ul>
 *
 * <p><b>Note on naming:</b> the earlier version of this class exposed {@code getBhat()}
 * which returned {@code IB} (i.e. {@code I - Beta}). Here the accessors are unambiguous:
 * {@link RicfResult#getBeta()} returns the direct-effects matrix {@code Beta = I - IB}
 * (matching the Python's returned {@code B}), and {@link RicfResult#getIMinusBeta()}
 * returns {@code IB} (the matrix used to reconstruct {@code Sigma}).</p>
 *
 * <p>Variable order is taken from {@code covMatrix.getVariableNames()}; the graph must
 * contain nodes with those names.</p>
 */
public final class RicfEjml {

    /** Default convergence tolerance (matches the Python default). */
    public static final double DEFAULT_TOL = 1e-8;

    /** Default maximum number of sweeps (matches the Python default). */
    public static final int DEFAULT_MAX_ITERS = 1000;

    /**
     * Constructs an instance of {@code RicfEjml}. The class is stateless; all inputs
     * are passed to {@link #ricf(Graph, ICovarianceMatrix, double, int)}.
     */
    public RicfEjml() {
    }

    /**
     * Result of RICF: the implied covariance, the error covariance, both coefficient
     * matrices ({@code Beta} and {@code I - Beta}), the sweep count, the final
     * convergence metric, and the Gaussian log-likelihood of the fitted model.
     */
    public static final class RicfResult {
        private final DMatrixRMaj sigmaHat;   // implied covariance = inv(IB) Omega inv(IB)^T
        private final DMatrixRMaj omegaHat;   // error covariance (bidirected structure)
        private final DMatrixRMaj beta;       // direct-effects matrix Beta = I - IB
        private final DMatrixRMaj iMinusBeta; // IB = I - Beta (reconstructs Sigma)
        private final int iters;
        private final double diff;
        private final double logLik;

        /**
         * Constructs a result holder.
         *
         * @param sigmaHat   implied covariance matrix.
         * @param omegaHat   error covariance matrix (includes bidirected structure).
         * @param beta       direct-effects matrix {@code Beta = I - IB}.
         * @param iMinusBeta the {@code IB = I - Beta} matrix.
         * @param iters      number of sweeps performed.
         * @param diff       final convergence metric (entrywise L1 change).
         * @param logLik     Gaussian log-likelihood of the fitted model.
         */
        public RicfResult(DMatrixRMaj sigmaHat, DMatrixRMaj omegaHat, DMatrixRMaj beta,
                          DMatrixRMaj iMinusBeta, int iters, double diff, double logLik) {
            this.sigmaHat = sigmaHat;
            this.omegaHat = omegaHat;
            this.beta = beta;
            this.iMinusBeta = iMinusBeta;
            this.iters = iters;
            this.diff = diff;
            this.logLik = logLik;
        }

        /**
         * @return the implied (model) covariance matrix {@code Sigma}.
         */
        public DMatrixRMaj getSigmaHat() {
            return sigmaHat;
        }

        /**
         * @return the error covariance matrix {@code Omega} (its off-diagonals carry the
         * bidirected structure).
         */
        public DMatrixRMaj getOmegaHat() {
            return omegaHat;
        }

        /**
         * @return the direct-effects matrix {@code Beta = I - IB}. Entry {@code Beta[i][j]}
         * is the structural coefficient of variable {@code j} in the equation for variable
         * {@code i} (nonzero only where {@code j} is a parent of {@code i}). This matches
         * the {@code B} returned by the reference Python.
         */
        public DMatrixRMaj getBeta() {
            return beta;
        }

        /**
         * @return the {@code IB = I - Beta} matrix, i.e. the matrix satisfying
         * {@code Sigma = inv(IB) * Omega * inv(IB)^T}.
         */
        public DMatrixRMaj getIMinusBeta() {
            return iMinusBeta;
        }

        /**
         * @return the number of sweeps performed (0-based index of the last completed
         * sweep, matching the Python's returned {@code it}).
         */
        public int getIterations() {
            return iters;
        }

        /**
         * @return the final convergence metric: the entrywise L1 change in {@code IB} plus
         * that in {@code Omega} on the last sweep.
         */
        public double getDiff() {
            return diff;
        }

        /**
         * Gaussian log-likelihood of the fitted model, evaluated at the implied covariance.
         *
         * <p>Computed as {@code -(n/2) * (p*log(2*pi) + log|Sigma| + tr(Sigma^{-1} S))},
         * where {@code n} is the sample size, {@code p} the number of variables, {@code S}
         * the sample covariance, and {@code Sigma} the implied covariance.</p>
         *
         * @return the log-likelihood.
         */
        public double getLogLik() {
            return logLik;
        }

        @Override
        public String toString() {
            return "RicfResult{iters=" + iters + ", diff=" + diff + ", logLik=" + logLik + "}";
        }
    }

    /**
     * Runs RICF with the default tolerance and iteration cap.
     *
     * @param admg      the ADMG/MAG (directed + bidirected edges only).
     * @param covMatrix the sample covariance matrix.
     * @return the fitted {@link RicfResult}.
     */
    public RicfResult ricf(Graph admg, ICovarianceMatrix covMatrix) {
        return ricf(admg, covMatrix, DEFAULT_TOL, DEFAULT_MAX_ITERS);
    }

    /**
     * Runs Residual Iterative Conditional Fitting for a Gaussian ADMG/MAG.
     *
     * @param admg      the ADMG/MAG; every variable of {@code covMatrix} must be a node here.
     *                  Only directed and bidirected edges are used.
     * @param covMatrix the sample covariance matrix (must be square and positive definite).
     * @param tol       positive convergence tolerance on the entrywise L1 parameter change.
     * @param maxIters  maximum number of sweeps.
     * @return the fitted {@link RicfResult}.
     * @throws NullPointerException     if {@code admg} or {@code covMatrix} is null.
     * @throws IllegalArgumentException if dimensions mismatch, a variable is missing from the
     *                                  graph, or {@code S} is not positive definite.
     */
    public RicfResult ricf(Graph admg, ICovarianceMatrix covMatrix, double tol, int maxIters) {
        Objects.requireNonNull(admg, "admg");
        Objects.requireNonNull(covMatrix, "covMatrix");

        List<String> varNames = covMatrix.getVariableNames();
        int p = covMatrix.getDimension();
        if (p != varNames.size()) {
            throw new IllegalArgumentException("RICF: covMatrix dimension does not match variable count.");
        }

        // Map covariance variable order -> graph nodes (validate existence).
        List<Node> nodes = new ArrayList<>(p);
        List<String> missing = new ArrayList<>();
        for (String name : varNames) {
            Node v = admg.getNode(name);
            if (v == null) missing.add(name);
            nodes.add(v);
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("RICF: graph is missing variables from covariance matrix: " + missing);
        }

        // S = sample covariance in covariance order.
        SimpleMatrix S = new SimpleMatrix(covMatrix.getMatrix().toArray());
        int n = covMatrix.getSampleSize();

        if (S.numRows() != S.numCols()) {
            throw new IllegalArgumentException("RICF: S must be square.");
        }
        if (!isPositiveDefinite(S)) {
            throw new IllegalArgumentException("RICF: S must be positive definite.");
        }

        int[] all = range(p);

        // Parents and spouses (siblings) in covariance order.
        int[][] pars = parentIndices(admg, nodes);
        int[][] sibs = spouseIndices(admg, nodes);

        // IB = I - Beta starts at identity; Omega starts at diag(S).
        SimpleMatrix IB = SimpleMatrix.identity(p);
        SimpleMatrix Omega = new SimpleMatrix(p, p);
        for (int i = 0; i < p; i++) Omega.set(i, i, S.get(i, i));

        int it = 0;
        double diff = Double.POSITIVE_INFINITY;

        for (it = 0; it < maxIters; it++) {
            SimpleMatrix ibOld = IB.copy();
            SimpleMatrix omegaOld = Omega.copy();

            for (int v = 0; v < p; v++) {
                int[] par = pars[v];
                int[] sib = sibs[v];

                // ---- no siblings ----
                if (sib.length == 0) {
                    // Regress v on its parents once, on the first sweep. With no spouses this
                    // least-squares fit does not depend on Omega, so it never needs revisiting.
                    if (par.length > 0 && it == 0) {
                        SimpleMatrix Spar = submatrix(S, par, par);
                        SimpleMatrix Svpar = submatrix(S, row(v), par);        // 1 x |par|
                        SimpleMatrix ibRow = Svpar.mult(Spar.invert()).scale(-1.0); // -S[v,par] inv(S[par,par])
                        for (int k = 0; k < par.length; k++) IB.set(v, par[k], ibRow.get(0, k));

                        SimpleMatrix Sparv = submatrix(S, par, row(v));        // |par| x 1
                        Omega.set(v, v, S.get(v, v) + ibRow.mult(Sparv).get(0, 0));
                    }
                    continue;
                }

                // ---- some siblings ----
                int[] mask = complement(p, v);
                SimpleMatrix oInv = submatrix(Omega, mask, mask).invert();     // (p-1) x (p-1)
                int[] sibPos = posInMask(sib, v);

                // Z = oInv[sib, mask] * IB[mask, :]   =>  |sib| x p
                SimpleMatrix Z = submatrix(oInv, sibPos, range(p - 1))
                        .mult(submatrix(IB, mask, all));

                double tempVar;

                if (par.length == 0) {
                    // XX = Z S Z^T ;  YX = (S[v,:] Z^T)^T ;  coef = XX^{-1} YX
                    SimpleMatrix XX = Z.mult(S).mult(Z.transpose());
                    SimpleMatrix YX = submatrix(S, row(v), all).mult(Z.transpose()).transpose(); // |sib| x 1
                    SimpleMatrix coef = XX.solve(YX);

                    for (int k = 0; k < sib.length; k++) {
                        double val = coef.get(k, 0);
                        Omega.set(v, sib[k], val);
                        Omega.set(sib[k], v, val);
                    }
                    tempVar = S.get(v, v) - coef.transpose().mult(YX).get(0, 0);
                } else {
                    int lpa = par.length;
                    int lsib = sib.length;

                    // XX = [ S[par,par]        S[par,:] Z^T ]
                    //      [ (S[par,:] Z^T)^T  Z S Z^T      ]
                    SimpleMatrix XX = new SimpleMatrix(lpa + lsib, lpa + lsib);
                    XX.insertIntoThis(0, 0, submatrix(S, par, par));
                    SimpleMatrix ur = submatrix(S, par, all).mult(Z.transpose()); // lpa x lsib
                    XX.insertIntoThis(0, lpa, ur);
                    XX.insertIntoThis(lpa, 0, ur.transpose());
                    XX.insertIntoThis(lpa, lpa, Z.mult(S).mult(Z.transpose()));

                    // YX = [ S[v,par]^T ; (S[v,:] Z^T)^T ]
                    SimpleMatrix YX = new SimpleMatrix(lpa + lsib, 1);
                    YX.insertIntoThis(0, 0, submatrix(S, row(v), par).transpose());
                    YX.insertIntoThis(lpa, 0, submatrix(S, row(v), all).mult(Z.transpose()).transpose());

                    SimpleMatrix coef = XX.solve(YX);   // (lpa + lsib) x 1

                    for (int k = 0; k < lpa; k++) IB.set(v, par[k], -coef.get(k, 0));
                    for (int k = 0; k < lsib; k++) {
                        double val = coef.get(lpa + k, 0);
                        Omega.set(v, sib[k], val);
                        Omega.set(sib[k], v, val);
                    }
                    tempVar = S.get(v, v) - coef.transpose().mult(YX).get(0, 0);
                }

                // Omega[v,v] = tempVar + Omega[v,sib] * oInv[sib,sib] * Omega[sib,v]
                SimpleMatrix omegaVsib = submatrix(Omega, row(v), sib);        // 1 x |sib|
                SimpleMatrix oInvSibSib = submatrix(oInv, sibPos, sibPos);
                double add = omegaVsib.mult(oInvSibSib).mult(omegaVsib.transpose()).get(0, 0);
                Omega.set(v, v, tempVar + add);
            }

            // Entrywise L1 change (matches the Python's np.sum(np.abs(...))).
            diff = elementL1Diff(IB, ibOld) + elementL1Diff(Omega, omegaOld);
            if (diff < tol) break;
        }

        // Omega is symmetric by construction; project to kill any float drift.
        Omega = symmetrize(Omega);

        SimpleMatrix invIB = IB.invert();
        SimpleMatrix sigmaHat = invIB.mult(Omega).mult(invIB.transpose());
        SimpleMatrix beta = SimpleMatrix.identity(p).minus(IB);

        double logLik = gaussianLogLikelihood(sigmaHat, S, n);

        return new RicfResult(
                sigmaHat.getDDRM().copy(),
                Omega.getDDRM().copy(),
                beta.getDDRM().copy(),
                IB.getDDRM().copy(),
                it, diff, logLik);
    }

    // ---------------- likelihood ----------------

    /**
     * Multivariate-normal log-likelihood at a model-implied covariance:
     * {@code -(n/2) * (p*log(2*pi) + log|sigma| + tr(sigma^{-1} S))}.
     *
     * @param sigma implied covariance (p x p, SPD).
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
     * Stable log-determinant of an SPD matrix via Cholesky, with an LU fallback.
     */
    private static double logDetSPD(SimpleMatrix A) {
        int n = A.numRows();
        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(n, true);
        DMatrixRMaj copy = A.getDDRM().copy();
        if (chol.decompose(copy)) {
            DMatrixRMaj L = chol.getT(null);
            double logDet = 0.0;
            for (int i = 0; i < n; i++) logDet += 2.0 * Math.log(L.get(i, i));
            return logDet;
        }
        return Math.log(Math.abs(A.determinant()));
    }

    private static boolean isPositiveDefinite(SimpleMatrix A) {
        int n = A.numRows();
        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(n, true);
        return chol.decompose(A.getDDRM().copy());
    }

    // ---------------- graph -> index lists ----------------

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
            out[j] = toSortedArray(pars);
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
                    int idx = nodes.indexOf(e.getDistalNode(nv));
                    if (idx >= 0 && idx != v && !sp.contains(idx)) sp.add(idx);
                }
            }
            out[v] = toSortedArray(sp);
        }
        return out;
    }

    // ---------------- small helpers ----------------

    /** Submatrix by explicit (possibly non-contiguous) row and column index sets. */
    private static SimpleMatrix submatrix(SimpleMatrix M, int[] rows, int[] cols) {
        SimpleMatrix out = new SimpleMatrix(rows.length, cols.length);
        for (int i = 0; i < rows.length; i++) {
            int r = rows[i];
            for (int j = 0; j < cols.length; j++) out.set(i, j, M.get(r, cols[j]));
        }
        return out;
    }

    private static int[] row(int v) {
        return new int[]{v};
    }

    private static int[] range(int p) {
        int[] r = new int[p];
        for (int i = 0; i < p; i++) r[i] = i;
        return r;
    }

    /** Ascending indices 0..p-1 with v removed. */
    private static int[] complement(int p, int v) {
        int[] out = new int[p - 1];
        int k = 0;
        for (int i = 0; i < p; i++) if (i != v) out[k++] = i;
        return out;
    }

    /**
     * Position of a global index within the ascending complement-of-{@code v} ordering.
     * Since the complement is {@code 0..p-1} with {@code v} removed, this is a closed form.
     */
    private static int posInMask(int g, int v) {
        return g < v ? g : g - 1;
    }

    private static int[] posInMask(int[] gs, int v) {
        int[] out = new int[gs.length];
        for (int i = 0; i < gs.length; i++) out[i] = posInMask(gs[i], v);
        return out;
    }

    private static double elementL1Diff(SimpleMatrix A, SimpleMatrix B) {
        double s = 0.0;
        int r = A.numRows(), c = A.numCols();
        for (int i = 0; i < r; i++)
            for (int j = 0; j < c; j++)
                s += Math.abs(A.get(i, j) - B.get(i, j));
        return s;
    }

    private static SimpleMatrix symmetrize(SimpleMatrix A) {
        return A.plus(A.transpose()).scale(0.5);
    }

    private static int[] toSortedArray(List<Integer> xs) {
        return xs.stream().distinct().sorted().mapToInt(Integer::intValue).toArray();
    }
}

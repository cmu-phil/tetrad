package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Two-Step algorithm (Kun Zhang et al.; Sanchez-Romero et al., Network Neuroscience 2019).
 * Linear, non-Gaussian errors; cycles allowed. Estimates B in X = B X + E (column -> row).
 *
 * Step 1: Adaptive Lasso -> sparse mask M (or external mask).
 * Step 2: ICA (symmetric FastICA) to estimate A ~ (I - B)^{-1}, then B = I - A^{-1};
 *         project to mask and refine with constrained per-row OLS.
 *
 * Orientation: B[i,j] != 0 implies edge j -> i.
 */
@Deprecated
public final class TwoStep2 {

    public static final class Result {
        public final SimpleMatrix B;   // p x p
        public final Graph graph;      // edges j->i iff B[i,j] != 0 after threshold
        public Result(SimpleMatrix B, Graph g) { this.B = B; this.graph = g; }
    }

    // ----- Parameters -----
    private double lambda = 0.05;           // L1 penalty base value
    private boolean normalizeLossByN = false; // If true, objective ~ (1/2n)||...||^2 + λ*L1 (we use λ_eff = λ*n)
    private double alassoEps = 1e-6;        // Lasso convergence tol
    private int alassoMaxIter = 500;
    private boolean useAdaptiveLasso = true;
    private double adaptiveGamma = 1.0;     // weights w_j = 1 / (|beta_ols_j| + eps)^gamma

    private int icaMaxIter = 1000;
    private double icaTol = 1e-5;

    private double maskThreshold = 1e-4;    // threshold for step-1 support (mask)
    private double coefThreshold = 1e-4;    // threshold for final graph pruning

    // 2-cycle breaker
    private boolean breakTwoCyclesEnabled = true;
    private double twoCycleRatio = 1.5;     // keep stronger dir if |a| >= ratio * |b|
    private double twoCycleMinAbs = 1e-4;   // ignore pairs below this magnitude

    private long randomSeed = 123L;         // ICA init seed
    private boolean verbose = true;         // print warnings/info to stderr
    private double condWarnThreshold = 1e8; // warn if cond2(A) > this before inversion

    // External mask support (1 = allowed nonzero)
    private boolean useExternalMask = false;
    private SimpleMatrix externalMask;      // p x p, zero diag, 0/1 elsewhere

    // Diagnostics
    private SimpleMatrix lastA = null;      // ICA mixing matrix
    private boolean icaConverged = true;
    private int icaIters = 0;

    // ----- Setters / getters -----
    public void setLambda(double lambda) { this.lambda = lambda; }
    public void setNormalizeLossByN(boolean flag) { this.normalizeLossByN = flag; }
    public boolean isNormalizeLossByN() { return normalizeLossByN; }
    public void setUseAdaptiveLasso(boolean flag) { this.useAdaptiveLasso = flag; }
    public void setAdaptiveGamma(double gamma) { this.adaptiveGamma = gamma; }
    public void setIcaMaxIter(int n) { this.icaMaxIter = n; }
    public void setIcaTol(double t) { this.icaTol = t; }
    public void setCoefThreshold(double t) { this.coefThreshold = t; }
    public void setMaskThreshold(double t) { this.maskThreshold = t; }
    public void setRandomSeed(long seed) { this.randomSeed = seed; }
    public void setVerbose(boolean v) { this.verbose = v; }
    public void setCondWarnThreshold(double thr) { this.condWarnThreshold = thr; }

    public void setBreakTwoCyclesEnabled(boolean enabled) { this.breakTwoCyclesEnabled = enabled; }
    public void setTwoCycleRatio(double ratio) { this.twoCycleRatio = ratio; }
    public void setTwoCycleMinAbs(double minAbs) { this.twoCycleMinAbs = minAbs; }

    /** Provide external mask like two_step_CD_mask.m (1 = allowed, 0 = forced zero). */
    public void setExternalMask(SimpleMatrix mask) {
        this.externalMask = mask.copy();
        this.useExternalMask = true;
    }

    public SimpleMatrix getLastA() { return lastA; }
    public boolean isIcaConverged() { return icaConverged; }
    public int getIcaIters() { return icaIters; }

    // ----- Main -----
    /** Returns B and directed graph (j->i if B[i,j] != 0). */
    public Result search(DataSet data) {
        SimpleMatrix X = standardizeCols(toMatrix(data)); // n x p
        int p = X.numCols();

        SimpleMatrix M = useExternalMask ? sanitizeMask(externalMask) : buildMaskWithAdaptiveLasso(X);

        SimpleMatrix A = fastIcaMixing(X, icaMaxIter, icaTol);
        this.lastA = A;

        double condA = cond2(A);
        if (condA > condWarnThreshold && verbose) {
            System.err.printf(Locale.ROOT,
                    "[TwoStep] Warning: A ill-conditioned (cond2 ~ %.3e > %.3e). Inversion may amplify noise.%n",
                    condA, condWarnThreshold);
        }

        SimpleMatrix Ainv;
        try { Ainv = A.invert(); }
        catch (Exception ex) {
            throw new IllegalStateException("Failed to invert A (mixing). Check data conditioning.", ex);
        }
        SimpleMatrix B = identity(p).minus(Ainv);

        forceMaskAndDiag(B, M);
        B = refineByConstrainedLS(X, B, M);

        if (breakTwoCyclesEnabled) {
            double minAbs = Math.max(twoCycleMinAbs, coefThreshold);
            B = breakTwoCycles(B, twoCycleRatio, minAbs);
        }

        Graph g = toGraph(B, data.getVariables(), coefThreshold);
        return new Result(B, g);
    }

    // ---------- Step 1: adaptive Lasso mask ----------
    private SimpleMatrix buildMaskWithAdaptiveLasso(SimpleMatrix X) {
        int p = X.numCols();
        SimpleMatrix M = new SimpleMatrix(p, p);
        for (int i = 0; i < p; i++) {
            int[] predIdx = IntStream.concat(IntStream.range(0, i), IntStream.range(i + 1, p)).toArray();
            SimpleMatrix Z = extractColumns(X, predIdx);
            SimpleMatrix y = X.extractVector(false, i);

            SimpleMatrix betaOls = safeOls(Z, y);

            double eps = 1e-6;
            double[] w = new double[predIdx.length];
            for (int k = 0; k < w.length; k++) {
                double b = Math.abs(betaOls.get(k, 0));
                w[k] = useAdaptiveLasso ? 1.0 / Math.pow(b + eps, adaptiveGamma) : 1.0;
            }

            // λ_eff: if normalizeLossByN, match (1/2n)||...||^2 + λ L1
            double lamEff = lambda * (normalizeLossByN ? X.numRows() : 1.0);
            SimpleMatrix beta = lassoCoordinateDescent(Z, y, w, lamEff, alassoEps, alassoMaxIter);

            for (int k = 0; k < predIdx.length; k++) {
                if (Math.abs(beta.get(k, 0)) > maskThreshold) {
                    int j = predIdx[k];
                    M.set(i, j, 1.0);
                }
            }
            M.set(i, i, 0.0);
        }
        return M;
    }

    private SimpleMatrix lassoCoordinateDescent(SimpleMatrix Z, SimpleMatrix y,
                                                double[] w, double lambda,
                                                double tol, int maxIter) {
        final int n = Z.numRows(), m = Z.numCols();
        SimpleMatrix beta = new SimpleMatrix(m, 1);
        SimpleMatrix r = y.copy(); // r = y - Z*beta (beta=0)

        double[] zj2 = new double[m];
        for (int j = 0; j < m; j++) {
            double s = 0;
            for (int i = 0; i < n; i++) s += Z.get(i, j) * Z.get(i, j);
            zj2[j] = s + 1e-12;
        }

        for (int it = 0; it < maxIter; it++) {
            double maxDelta = 0.0;

            for (int j = 0; j < m; j++) {
                double zjTr = 0.0;
                for (int i = 0; i < n; i++) zjTr += Z.get(i, j) * r.get(i, 0);
                double rho = zjTr + zj2[j] * beta.get(j, 0);

                double bjOld = beta.get(j, 0);
                double bjNew = softThreshold(rho, lambda * w[j]) / zj2[j];
                double db = bjNew - bjOld;
                if (db != 0.0) {
                    for (int i = 0; i < n; i++) r.set(i, 0, r.get(i, 0) - Z.get(i, j) * db);
                    beta.set(j, 0, bjNew);
                    maxDelta = Math.max(maxDelta, Math.abs(db));
                }
            }

            if (maxDelta < tol) return beta;
        }
        if (verbose) {
            System.err.println("[TwoStep] Note: Lasso CD hit maxIter without reaching tol; returning last iterate.");
        }
        return beta;
    }

    private static double softThreshold(double z, double t) {
        if (z > t) return z - t;
        if (z < -t) return z + t;
        return 0.0;
    }

    // ---------- Step 2: FastICA (symmetric) ----------
    private SimpleMatrix fastIcaMixing(SimpleMatrix X, int maxIter, double tol) {
        SimpleMatrix Xc = centerCols(X);
        WhitenResult Wht = whiten(Xc);
        SimpleMatrix Xw = Wht.whitened; // n x p
        int p = Xw.numCols();

        SimpleMatrix W = randomOrthonormal(p, new Random(randomSeed));

        icaConverged = false;
        icaIters = 0;
        for (int iter = 0; iter < maxIter; iter++) {
            SimpleMatrix WX = Xw.mult(W.transpose()); // n x p
            SimpleMatrix G = applyTanh(WX);
            SimpleMatrix GprimeMean = meanColumns(oneMinusSquareTanh(WX)); // 1 x p

            SimpleMatrix E_xg = Xw.transpose().mult(G).divide(Math.max(1, Xw.numRows())); // p x p
            SimpleMatrix term = diag(GprimeMean).mult(W);

            SimpleMatrix Wnew = E_xg.minus(term);
            Wnew = symmetricDecorrelate(Wnew);

            double delta = froNorm(Wnew.minus(W));
            W = Wnew;
            icaIters = iter + 1;
            if (delta < tol) {
                icaConverged = true;
                break;
            }
        }

        if (!icaConverged && verbose) {
            System.err.printf(Locale.ROOT,
                    "[TwoStep] Warning: FastICA did not converge in %d iterations (tol=%.1e).%n",
                    maxIter, tol);
        }

        return Wht.dewhitening.mult(W.invert()); // A ≈ dewhitening * W^{-1}
    }

    private static class WhitenResult {
        final SimpleMatrix whitened;    // n x p
        final SimpleMatrix dewhitening; // p x p
        WhitenResult(SimpleMatrix w, SimpleMatrix d) { whitened = w; dewhitening = d; }
    }

    private WhitenResult whiten(SimpleMatrix Xc) {
        int n = Xc.numRows(), p = Xc.numCols();
        SimpleMatrix C = Xc.transpose().mult(Xc).divide(Math.max(1, n));
        SimpleEVD<SimpleMatrix> evd = C.eig();

        SimpleMatrix V = new SimpleMatrix(p, p);
        SimpleMatrix D = new SimpleMatrix(p, p);
        for (int i = 0; i < p; i++) {
            double eig = evd.getEigenvalue(i).getReal();
            if (Double.isNaN(eig) || Double.isInfinite(eig)) eig = 0.0;
            eig = Math.max(eig, 1e-12);
            D.set(i, i, eig);

            SimpleMatrix v = evd.getEigenVector(i);
            if (v == null) {
                for (int r = 0; r < p; r++) V.set(r, i, (r == i) ? 1.0 : 0.0);
            } else {
                for (int r = 0; r < p; r++) V.set(r, i, v.get(r, 0));
            }
        }
        SimpleMatrix Dm12 = new SimpleMatrix(p, p);
        SimpleMatrix Dp12 = new SimpleMatrix(p, p);
        for (int i = 0; i < p; i++) {
            double val = Math.sqrt(D.get(i, i));
            double inv = 1.0 / Math.max(val, 1e-12);
            Dm12.set(i, i, inv);
            Dp12.set(i, i, Math.max(val, 1e-12));
        }
        SimpleMatrix whitening = V.mult(Dm12).mult(V.transpose());
        SimpleMatrix dewhitening = V.mult(Dp12).mult(V.transpose());
        SimpleMatrix Xw = Xc.mult(whitening);
        return new WhitenResult(Xw, dewhitening);
    }

    private SimpleMatrix symmetricDecorrelate(SimpleMatrix W) {
        SimpleMatrix S = W.mult(W.transpose());
        SimpleEVD<SimpleMatrix> evd = S.eig();
        int p = W.numRows();
        SimpleMatrix V = new SimpleMatrix(p, p);
        SimpleMatrix Dm12 = new SimpleMatrix(p, p);
        for (int i = 0; i < p; i++) {
            double eig = evd.getEigenvalue(i).getReal();
            if (Double.isNaN(eig) || Double.isInfinite(eig)) eig = 0.0;
            eig = Math.max(eig, 1e-12);
            Dm12.set(i, i, 1.0 / Math.sqrt(eig));

            SimpleMatrix v = evd.getEigenVector(i);
            if (v == null) {
                for (int r = 0; r < p; r++) V.set(r, i, (r == i) ? 1.0 : 0.0);
            } else {
                for (int r = 0; r < p; r++) V.set(r, i, v.get(r, 0));
            }
        }
        SimpleMatrix P = V.mult(Dm12).mult(V.transpose());
        return P.mult(W);
    }

    // ---------- Two-cycle breaker ----------
    /** For each unordered pair (i,j), if both directions survive, keep only the stronger if magnitudes differ by 'ratio'. */
    private SimpleMatrix breakTwoCycles(SimpleMatrix B, double ratio, double minAbs) {
        int p = B.numRows();
        SimpleMatrix out = B.copy();
        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                double a = Math.abs(out.get(i, j));
                double b = Math.abs(out.get(j, i));
                if (a < minAbs) a = 0.0;
                if (b < minAbs) b = 0.0;
                if (a > 0.0 && b > 0.0) {
                    if (a >= b * ratio) {
                        out.set(j, i, 0.0); // keep j->i (B[i,j])
                    } else if (b >= a * ratio) {
                        out.set(i, j, 0.0); // keep i->j (B[j,i])
                    }
                    // else similar magnitudes: keep both; user can raise 'ratio' to prune more.
                }
            }
        }
        return out;
    }

    // ---------- Masked refinement & utilities ----------
    private void forceMaskAndDiag(SimpleMatrix B, SimpleMatrix M) {
        int p = B.numRows();
        for (int i = 0; i < p; i++) {
            B.set(i, i, 0.0);
            for (int j = 0; j < p; j++) {
                if (i == j || M.get(i, j) == 0.0) B.set(i, j, 0.0);
            }
        }
    }

    private SimpleMatrix refineByConstrainedLS(SimpleMatrix X, SimpleMatrix B0, SimpleMatrix M) {
        int p = X.numCols();
        SimpleMatrix B = B0.copy();
        for (int i = 0; i < p; i++) {
            List<Integer> parents = new ArrayList<>();
            for (int j = 0; j < p; j++) if (j != i && M.get(i, j) != 0.0) parents.add(j);
            if (parents.isEmpty()) {
                for (int j = 0; j < p; j++) B.set(i, j, 0.0);
                continue;
            }
            int[] idx = parents.stream().mapToInt(Integer::intValue).toArray();
            SimpleMatrix Z = extractColumns(X, idx);
            SimpleMatrix y = X.extractVector(false, i);
            SimpleMatrix beta = safeOls(Z, y);
            for (int k = 0; k < idx.length; k++) B.set(i, idx[k], beta.get(k, 0));
            for (int j : idx) if (Math.abs(B.get(i, j)) < coefThreshold) B.set(i, j, 0.0);
        }
        for (int d = 0; d < p; d++) B.set(d, d, 0.0);
        return B;
    }

    /** Extract all rows and selected columns. */
    public static SimpleMatrix extractColumns(SimpleMatrix X, int[] idx) {
        int n = X.numRows();
        SimpleMatrix out = new SimpleMatrix(n, idx.length);
        for (int j = 0; j < idx.length; j++) {
            int col = idx[j];
            for (int i = 0; i < n; i++) out.set(i, j, X.get(i, col));
        }
        return out;
    }

    private static SimpleMatrix safeOls(SimpleMatrix Z, SimpleMatrix y) {
        int m = Z.numCols();
        double eps = 1e-8;
        SimpleMatrix ZtZ = Z.transpose().mult(Z);
        for (int i = 0; i < m; i++) ZtZ.set(i, i, ZtZ.get(i, i) + eps);
        return ZtZ.invert().mult(Z.transpose()).mult(y);
    }

    private static SimpleMatrix centerCols(SimpleMatrix X) {
        int n = X.numRows(), p = X.numCols();
        SimpleMatrix out = X.copy();
        for (int j = 0; j < p; j++) {
            double mu = 0.0;
            for (int i = 0; i < n; i++) mu += X.get(i, j);
            mu /= n;
            for (int i = 0; i < n; i++) out.set(i, j, X.get(i, j) - mu);
        }
        return out;
    }

    private static SimpleMatrix standardizeCols(SimpleMatrix X) {
        int n = X.numRows(), p = X.numCols();
        SimpleMatrix out = X.copy();
        for (int j = 0; j < p; j++) {
            double mu = 0.0, s2 = 0.0;
            for (int i = 0; i < n; i++) mu += X.get(i, j);
            mu /= n;
            for (int i = 0; i < n; i++) { double v = X.get(i, j) - mu; s2 += v*v; }
            double sd = Math.sqrt(Math.max(s2 / Math.max(n-1,1), 1e-12));
            for (int i = 0; i < n; i++) out.set(i, j, (X.get(i, j) - mu) / sd);
        }
        return out;
    }

    private static SimpleMatrix toMatrix(DataSet data) {
        int n = data.getNumRows(), p = data.getNumColumns();
        SimpleMatrix X = new SimpleMatrix(n, p);
        for (int i = 0; i < n; i++) for (int j = 0; j < p; j++) X.set(i, j, data.getDouble(i, j));
        return X;
    }

    private static SimpleMatrix identity(int p) {
        SimpleMatrix I = new SimpleMatrix(p, p);
        for (int i = 0; i < p; i++) I.set(i, i, 1.0);
        return I;
    }

    private static SimpleMatrix diag(SimpleMatrix rowVec) {
        int p = rowVec.numCols();
        SimpleMatrix D = new SimpleMatrix(p, p);
        for (int i = 0; i < p; i++) D.set(i, i, rowVec.get(0, i));
        return D;
    }

    private static double froNorm(SimpleMatrix A) {
        double s = 0.0;
        for (int i = 0; i < A.numRows(); i++)
            for (int j = 0; j < A.numCols(); j++)
                s += A.get(i, j) * A.get(i, j);
        return Math.sqrt(s);
    }

    private static SimpleMatrix applyTanh(SimpleMatrix M) {
        SimpleMatrix out = M.copy();
        for (int i = 0; i < M.numRows(); i++)
            for (int j = 0; j < M.numCols(); j++)
                out.set(i, j, Math.tanh(M.get(i, j)));
        return out;
    }

    private static SimpleMatrix oneMinusSquareTanh(SimpleMatrix M) {
        SimpleMatrix out = M.copy();
        for (int i = 0; i < M.numRows(); i++)
            for (int j = 0; j < M.numCols(); j++) {
                double t = Math.tanh(M.get(i, j));
                out.set(i, j, 1.0 - t*t);
            }
        return out;
    }

    private static SimpleMatrix meanColumns(SimpleMatrix M) {
        int n = M.numRows(), p = M.numCols();
        SimpleMatrix out = new SimpleMatrix(1, p);
        for (int j = 0; j < p; j++) {
            double s = 0.0;
            for (int i = 0; i < n; i++) s += M.get(i, j);
            out.set(0, j, s / Math.max(1, n));
        }
        return out;
    }

    private static SimpleMatrix randomOrthonormal(int p, Random rnd) {
        SimpleMatrix R = new SimpleMatrix(p, p);
        for (int i = 0; i < p; i++)
            for (int j = 0; j < p; j++)
                R.set(i, j, rnd.nextGaussian());
        SimpleMatrix Q = new SimpleMatrix(p, p);
        for (int j = 0; j < p; j++) {
            SimpleMatrix v = R.extractVector(true, j).transpose();
            for (int k = 0; k < j; k++) {
                SimpleMatrix qk = Q.extractVector(true, k).transpose();
                double denom = qk.dot(qk) + 1e-12;
                double proj = v.dot(qk) / denom;
                v = v.minus(qk.scale(proj));
            }
            double norm = Math.sqrt(v.dot(v) + 1e-12);
            for (int i = 0; i < p; i++) Q.set(i, j, v.get(i, 0) / norm);
        }
        return Q.transpose();
    }

    private static Graph toGraph(SimpleMatrix B, List<Node> vars, double thr) {
        Graph g = new EdgeListGraph(vars);
        int p = B.numRows();
        for (int i = 0; i < p; i++) for (int j = 0; j < p; j++) {
            if (i == j) continue;
            if (Math.abs(B.get(i, j)) > thr) g.addDirectedEdge(vars.get(j), vars.get(i));
        }
        return g;
    }

    private static SimpleMatrix sanitizeMask(SimpleMatrix M) {
        int p = M.numRows();
        if (M.numCols() != p) throw new IllegalArgumentException("Mask must be square.");
        SimpleMatrix out = new SimpleMatrix(p, p);
        for (int i = 0; i < p; i++) for (int j = 0; j < p; j++) {
            double v = (i == j) ? 0.0 : (M.get(i, j) != 0.0 ? 1.0 : 0.0);
            out.set(i, j, v);
        }
        return out;
    }

    // ---------- small linear-algebra helper ----------
    /** 2-norm condition number via eigenvalues of A^T A (sqrt(lambda_max/lambda_min)). */
    private static double cond2(SimpleMatrix A) {
        SimpleMatrix AtA = A.transpose().mult(A);
        SimpleEVD<SimpleMatrix> evd = AtA.eig();
        double maxEv = 0.0;
        double minEv = Double.POSITIVE_INFINITY;
        for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
            double ev = evd.getEigenvalue(i).getReal();
            if (Double.isNaN(ev) || ev <= 0.0) continue;
            maxEv = Math.max(maxEv, ev);
            minEv = Math.min(minEv, ev);
        }
        if (!(maxEv > 0.0) || !(minEv > 0.0)) return Double.POSITIVE_INFINITY;
        return Math.sqrt(maxEv / minEv);
    }
}
package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.List;

/** Cyclic SEM scorer: Gaussian, diagonal Omega, stability guard, BIC. */
public final class CyclicSemScorer {

    public static final class ScoreResult {
        public final double bic;
        public final boolean stable;
        public final int params;
        ScoreResult(double bic, boolean stable, int params) {
            this.bic = bic; this.stable = stable; this.params = params;
        }
    }

    private double stabilityTol = 0.999;
    private boolean higherIsBetterBic = false; // If true, returns 2L - k ln n; else returns -2L + k ln n
    private double ridgeOmega = 1e-9;

    public CyclicSemScorer withStabilityTol(double tol) { this.stabilityTol = tol; return this; }
    public CyclicSemScorer withHigherIsBetterBic(boolean v) { this.higherIsBetterBic = v; return this; }
    public CyclicSemScorer withRidgeOmega(double v) { this.ridgeOmega = v; return this; }

    public ScoreResult score(DataSet data, Graph g) {
        List<Node> nodes = g.getNodes();
        int p = nodes.size();
        int n = data.getNumRows();

        // Xc: n x p (column order matches g.getNodes())
        SimpleMatrix X  = toMatrix(data, nodes);
        SimpleMatrix Xc = centerColumns(X);

        // Build B (p x p) with zeros except B[j,i] if j -> i exists
        SimpleMatrix B = new SimpleMatrix(p, p);
        int edgeCount = 0;

        for (int i = 0; i < p; i++) {
            Node ni = nodes.get(i);
            List<Node> pa = g.getParents(ni);
            if (pa.isEmpty()) continue;

            int k = pa.size();
            int[] idx = new int[k];
            for (int t = 0; t < k; t++) idx[t] = nodes.indexOf(pa.get(t));

            SimpleMatrix Xi  = Xc.extractVector(false, i);   // n x 1
            SimpleMatrix Xpa = extractColumns(Xc, idx);      // n x k
            SimpleMatrix beta = lsSolve(Xpa, Xi);            // k x 1

            for (int t = 0; t < k; t++) {
                int j = idx[t];
                double bji = beta.get(t);
                if (Math.abs(bji) > 1e-12) { // epsilon to avoid counting numerical noise
                    B.set(j, i, bji);
                    edgeCount++;
                }
            }
        }

        // Stability: spectral radius of B
        double rho = spectralRadius(B);
        if (!(rho < stabilityTol) || !Double.isFinite(rho)) {
            double bad = higherIsBetterBic ? -Double.MAX_VALUE : Double.POSITIVE_INFINITY;
            return new ScoreResult(bad, false, edgeCount + p);
        }

        // Residuals per node & Omega diagonal
        double[] sigmaE = new double[p];
        for (int i = 0; i < p; i++) {
            SimpleMatrix Xi = Xc.extractVector(false, i); // n x 1
            SimpleMatrix fitted = new SimpleMatrix(n, 1);
            for (int j = 0; j < p; j++) {
                double bji = B.get(j, i);
                if (bji == 0.0) continue;
                fitted = fitted.plus(Xc.extractVector(false, j).scale(bji));
            }
            SimpleMatrix ri = Xi.minus(fitted);
            sigmaE[i] = Math.max(ri.elementPower(2).elementSum() / Math.max(1, n), ridgeOmega);
        }

        // Sigma = (I - B^T)^{-1} Omega (I - B)^{-1}
        SimpleMatrix I  = SimpleMatrix.identity(p);
        SimpleMatrix Bt = B.transpose();
        SimpleMatrix A  = I.minus(Bt);
        SimpleMatrix At = I.minus(B);
        SimpleMatrix Ainv  = safeInverse(A);
        SimpleMatrix Atinv = safeInverse(At);
        if (Ainv == null || Atinv == null) {
            double bad = higherIsBetterBic ? -Double.MAX_VALUE : Double.POSITIVE_INFINITY;
            return new ScoreResult(bad, false, edgeCount + p);
        }

        SimpleMatrix Omega = SimpleMatrix.identity(p);
        for (int i = 0; i < p; i++) Omega.set(i, i, sigmaE[i]);

        SimpleMatrix Sigma = Ainv.mult(Omega).mult(Atinv);

        // Empirical covariance S (MLE scaling)
        SimpleMatrix S = cov(Xc);

        // Log-likelihood under Gaussian:
        // L = -n/2 [ log|Sigma| + tr(S Sigma^{-1}) + p log(2π) ]
        SimpleMatrix SigmaInv = safeInverse(Sigma);
        if (SigmaInv == null) {
            double bad = higherIsBetterBic ? -Double.MAX_VALUE : Double.POSITIVE_INFINITY;
            return new ScoreResult(bad, false, edgeCount + p);
        }
        double logDetSigma = logDetPSD(Sigma);
        double tr = SigmaInv.mult(S).trace();

        double log2pi = Math.log(2 * Math.PI);
        double L = -0.5 * n * (logDetSigma + tr + p * log2pi);

        int k = edgeCount + p; // nonzeros in B + diagonal Omega params
        double bic = higherIsBetterBic ? (2.0 * L - k * Math.log(n))
                : (-2.0 * L + k * Math.log(n));

        return new ScoreResult(bic, true, k);
    }

    // ===== helpers =====

    /** Extract n × k submatrix of chosen columns. */
    private static SimpleMatrix extractColumns(SimpleMatrix X, int[] cols) {
        int n = X.numRows();
        int k = cols.length;
        SimpleMatrix out = new SimpleMatrix(n, k);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < k; j++) {
                out.set(i, j, X.get(i, cols[j]));
            }
        }
        return out;
    }

    /** Node-safe matrix fill (preserves Graph node ↔ DataSet column alignment). */
    private static SimpleMatrix toMatrix(DataSet data, List<Node> nodes) {
        int n = data.getNumRows(), p = nodes.size();
        SimpleMatrix X = new SimpleMatrix(n, p);
        for (int j = 0; j < p; j++) {
            Node v = nodes.get(j);
            for (int i = 0; i < n; i++) {
                X.set(i, j, data.getDouble(i, j));
            }
        }
        return X;
    }

    private static SimpleMatrix centerColumns(SimpleMatrix X) {
        int n = X.numRows(), p = X.numCols();
        SimpleMatrix Y = X.copy();
        for (int j = 0; j < p; j++) {
            double m = 0.0;
            for (int i = 0; i < n; i++) m += Y.get(i, j);
            m /= n;
            for (int i = 0; i < n; i++) Y.set(i, j, Y.get(i, j) - m);
        }
        return Y;
    }

    private static SimpleMatrix cov(SimpleMatrix Xc) {
        int n = Xc.numRows();
        SimpleMatrix S = Xc.transpose().mult(Xc).divide(n);
        // tiny ridge for numerical safety
        double eps = 1e-12;
        for (int i = 0; i < S.numRows(); i++) S.set(i, i, S.get(i, i) + eps);
        return S;
    }

    private static SimpleMatrix lsSolve(SimpleMatrix A, SimpleMatrix y) {
        double lambda = 1e-8;
        SimpleMatrix At = A.transpose();
        SimpleMatrix AtA = At.mult(A);
        for (int d = 0; d < AtA.numRows(); d++) AtA.set(d, d, AtA.get(d, d) + lambda);
        return AtA.solve(At.mult(y));
    }

    // replace your spectralRadius(...) with this
    private static double spectralRadius(SimpleMatrix B) {
        // Guard NaN/Inf early
        for (int r = 0; r < B.numRows(); r++) {
            for (int c = 0; c < B.numCols(); c++) {
                double v = B.get(r, c);
                if (!Double.isFinite(v)) return Double.POSITIVE_INFINITY;
            }
        }

        // 1) Try eigenvalues (fast, exact when it converges)
        try {
            SimpleEVD<SimpleMatrix> evd = B.eig();
            double rho = 0.0;
            for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
                double re = evd.getEigenvalue(i).getReal();
                double im = evd.getEigenvalue(i).getImaginary();
                double mag = Math.hypot(re, im);
                if (Double.isFinite(mag) && mag > rho) rho = mag;
            }
            if (Double.isFinite(rho)) return rho;
        } catch (RuntimeException ignore) {
            // fall through to safe bounds
        }

        // 2) Fallback A: spectral norm upper bound via SVD
        try {
            SimpleSVD<SimpleMatrix> svd = B.svd();
            // largest singular value is the (0,0) entry of W in SimpleSVD
            double sigmaMax = 0.0;
            SimpleMatrix W = svd.getW();
            int d = Math.min(W.numRows(), W.numCols());
            for (int i = 0; i < d; i++) {
                sigmaMax = Math.max(sigmaMax, Math.abs(W.get(i, i)));
            }
            if (Double.isFinite(sigmaMax)) return sigmaMax; // ρ(B) ≤ ||B||₂
        } catch (RuntimeException ignore) {
            // continue to norm bounds
        }

        // 3) Fallback B: cheap induced-norm bounds
        double norm1 = 0.0;  // max column sum
        for (int c = 0; c < B.numCols(); c++) {
            double s = 0.0;
            for (int r = 0; r < B.numRows(); r++) s += Math.abs(B.get(r, c));
            norm1 = Math.max(norm1, s);
        }
        double normInf = 0.0; // max row sum
        for (int r = 0; r < B.numRows(); r++) {
            double s = 0.0;
            for (int c = 0; c < B.numCols(); c++) s += Math.abs(B.get(r, c));
            normInf = Math.max(normInf, s);
        }
        double bound = Math.min(norm1, normInf); // both bound ρ(B)
        return bound;
    }

    private static SimpleMatrix safeInverse(SimpleMatrix A) {
        try {
            return A.pseudoInverse(); // robust; equals inverse when well-conditioned
        } catch (Exception e) {
            return null;
        }
    }

    private static double logDetPSD(SimpleMatrix S) {
        SimpleEVD<SimpleMatrix> evd = S.eig();
        double sum = 0.0;
        for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
            double lam = evd.getEigenvalue(i).getReal(); // PSD -> real
            lam = Math.max(lam, 1e-15);
            sum += Math.log(lam);
        }
        return sum;
    }
}
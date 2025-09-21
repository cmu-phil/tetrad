package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;

import java.util.*;

/**
 * CAM scorer using penalized cubic B-splines (P-splines; Eilers & Marx).
 * - Main effects only (additive model), one smooth per parent: f_y(x_j)
 * - For each parent, builds a cubic B-spline basis with M basis functions
 * - Penalizes second differences of coefficients (D2^T D2) with smoothing λ
 * - Fits by backfitting on partial residuals; chooses λ_j by GCV over a log grid
 * - Score is BIC with effective df = sum_j trace(H_j) + 1 (intercept)
 *
 * No external deps; uses EJML for linear algebra.
 */
public final class CamAdditivePsplineBic implements AdditiveLocalScorer {

    // ----- config knobs -----
    private final DataSet data;
    private final List<Node> vars;
    private final int N, P;

    private int numBasis = 8;           // slightly smaller basis for speed & stability
    private int splineDegree = 3;       // cubic
    private int penaltyOrder = 2;       // second-difference penalty
    private double ridge = 1e-6;        // tiny ridge for stability
    private double penaltyDiscount = 1.0;
    private int maxBackfitIters = 12;
    private double tol = 1e-5;

    private final double[] ybuf;        // scratch buffers
    private final double[] xbuf;

    // Precompute spline bases per variable (keyed by variable index + config)
    private final Map<String, Precomp> precomp = new HashMap<>();

    public CamAdditivePsplineBic(DataSet raw) {
        this.data = Objects.requireNonNull(raw);
        this.vars = new ArrayList<>(raw.getVariables());
        this.N = raw.getNumRows();
        this.P = raw.getNumColumns();
        this.ybuf = new double[N];
        this.xbuf = new double[N];
    }

    // ---- setters for knobs ----
    public CamAdditivePsplineBic setNumBasis(int m) {
        this.numBasis = Math.max(4, m);
        return this;
    }

    public CamAdditivePsplineBic setPenaltyOrder(int d) {
        this.penaltyOrder = Math.max(1, Math.min(3, d));
        return this;
    }

    public CamAdditivePsplineBic setRidge(double r) {
        this.ridge = Math.max(0.0, r);
        return this;
    }

    public CamAdditivePsplineBic setPenaltyDiscount(double c) {
        this.penaltyDiscount = c;
        return this;
    }

    public CamAdditivePsplineBic setMaxBackfitIters(int it) {
        this.maxBackfitIters = Math.max(1, it);
        return this;
    }

    public CamAdditivePsplineBic setTol(double t) {
        this.tol = Math.max(1e-8, t);
        return this;
    }

    /**
     * Local score: BIC(Y | parents) under additive P-splines, with λ_j by GCV via backfitting.
     */
    public double localScore(Node y, Collection<Node> parents) {
        int yIdx = indexOf(y);
        int[] paIdx = parents.stream().mapToInt(this::indexOf).toArray();
        return localScore(yIdx, paIdx);
    }

    /**
     * Local score by index.
     */
    public double localScore(int yIndex, int... parentIdxs) {
        if (parentIdxs == null) parentIdxs = new int[0];
        if (parentIdxs.length == 0) {
            // Intercept-only model
            double rss = 0.0;
            double mean = mean(col(yIndex));
            for (int i = 0; i < N; i++) {
                double e = val(i, yIndex) - mean;
                rss += e * e;
            }
            double bic = N * Math.log(Math.max(1e-300, rss / N)) + penaltyDiscount * Math.log(N) * 1.0;
            return bic;
        }

        // Extract y
        double[] y = col(yIndex);

        // Build per-parent basis & penalty
        int m = numBasis;
        ParentBlock[] blocks = new ParentBlock[parentIdxs.length];
        for (int j = 0; j < parentIdxs.length; j++) {
            int pj = parentIdxs[j];
            double[] x = col(pj);
            blocks[j] = buildParentBlock(pj, x, m, splineDegree, penaltyOrder);
        }

        // Backfitting: initialize f_j = 0, intercept = mean(y)
        double mu = mean(y);
        for (ParentBlock b : blocks) {
            Arrays.fill(b.beta, 0.0);
            Arrays.fill(b.fit, 0.0);
        }

        // Iterate updates
        double prevRss = Double.POSITIVE_INFINITY;
        for (int iter = 0; iter < maxBackfitIters; iter++) {
            // residual excluding current component: r = y - (mu + sum_{k≠j} f_k)
            for (int j = 0; j < blocks.length; j++) {
                ParentBlock bj = blocks[j];

                // form r = y - (mu + sum_k f_k) + f_j (i.e., partial residual for j)
                double rssAll = 0.0;
                for (int i = 0; i < N; i++) {
                    double sumfk = 0.0;
                    for (int k = 0; k < blocks.length; k++) sumfk += blocks[k].fit[i];
                    double r = y[i] - (mu + sumfk) + bj.fit[i];
                    bj.r[i] = r;
                    rssAll += (y[i] - (mu + sumfk)) * (y[i] - (mu + sumfk));
                }

                // Refit component j on partial residual with λ chosen by GCV
                refitComponentGCV(bj);

                if (Thread.interrupted())
                    return Double.POSITIVE_INFINITY; // let outer search handle cancellation gracefully
            }

            // Update intercept to absorb mean of residual
            double resMean = 0.0;
            for (int i = 0; i < N; i++) {
                double sumfk = 0.0;
                for (ParentBlock b : blocks) sumfk += b.fit[i];
                resMean += (y[i] - sumfk);
            }
            mu = resMean / N;

            // Check convergence using RSS
            double rss = 0.0;
            for (int i = 0; i < N; i++) {
                double sumfk = 0.0;
                for (ParentBlock b : blocks) sumfk += b.fit[i];
                double e = y[i] - (mu + sumfk);
                rss += e * e;
            }
            if (Math.abs(prevRss - rss) / Math.max(1.0, prevRss) < tol) break;
            prevRss = rss;
        }

        // Final RSS and effective df
        double rss = 0.0;
        for (int i = 0; i < N; i++) {
            double sumfk = 0.0;
            for (ParentBlock b : blocks) sumfk += b.fit[i];
            double e = y[i] - (mu + sumfk);
            rss += e * e;
        }
        double edf = 1.0; // intercept
        for (ParentBlock b : blocks) edf += b.edf;

        double bic = N * Math.log(Math.max(1e-300, rss / N)) + penaltyDiscount * Math.log(N) * edf;
        return bic;
    }

    // ====================== internals ======================

    private int indexOf(Node n) {
        return vars.indexOf(n);
    }

    private double val(int i, int j) {
        return data.getDouble(i, j);
    }

    private double[] col(int j) {
        for (int i = 0; i < N; i++) xbuf[i] = data.getDouble(i, j);
        return Arrays.copyOf(xbuf, N);
    }

    private static double mean(double[] a) {
        double s = 0;
        for (double v : a) s += v;
        return s / a.length;
    }

    /**
     * Build B-spline basis & penalty for one parent, including scaling x to [0,1].
     */
    private ParentBlock buildParentBlock(int varIndex, double[] xRaw, int m, int degree, int penOrder) {
        String key = varIndex + ":m=" + m + ":p=" + degree + ":d=" + penOrder;
        Precomp pc = precomp.get(key);
        if (pc == null) {
            pc = computePrecomp(xRaw, m, degree, penOrder);
            precomp.put(key, pc);
        }
        return new ParentBlock(pc.B, pc.P, pc.BtB);
    }

    private Precomp computePrecomp(double[] xRaw, int m, int degree, int penOrder) {
        double xmin = Arrays.stream(xRaw).min().orElse(0.0);
        double xmax = Arrays.stream(xRaw).max().orElse(1.0);
        double span = (xmax > xmin) ? (xmax - xmin) : 1.0;
        double[] x = new double[N];
        for (int i = 0; i < N; i++) x[i] = (xRaw[i] - xmin) / span; // scale to [0,1]

        int K = Math.max(2, m - degree - 1);
        double[] knots = knotsOpenUniform(K, degree);

        int M = m;
        DMatrixRMaj B = new DMatrixRMaj(N, M);
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < M; j++) {
                B.set(i, j, bsplineBasis(j, degree, x[i], knots));
            }
        }

        DMatrixRMaj D = diffMatrix(M, penOrder);
        DMatrixRMaj P = new DMatrixRMaj(M, M);
        CommonOps_DDRM.multTransA(D, D, P); // P = D^T D

        DMatrixRMaj BtB = new DMatrixRMaj(M, M);
        CommonOps_DDRM.multTransA(B, B, BtB); // B^T B

        return new Precomp(B, P, BtB);
    }

    /**
     * Choose λ by minimizing GCV on current partial residuals; update beta, fit, and edf.
     */
    private void refitComponentGCV(ParentBlock pb) {
        // Use cached BtB and compute BtR once
        DMatrixRMaj BtB = pb.BtB;                     // M x M (cached)
        DMatrixRMaj BtR = new DMatrixRMaj(pb.M, 1);   // M x 1
        CommonOps_DDRM.multTransA(pb.B, vec(pb.r), BtR);  // B^T r

        // Early exit: if ||B^T r|| is tiny, keep this component zero
        double normBtR = 0.0;
        for (int i = 0; i < BtR.getNumElements(); i++) normBtR += BtR.data[i] * BtR.data[i];
        if (normBtR < 1e-10) {
            Arrays.fill(pb.beta, 0.0);
            Arrays.fill(pb.fit, 0.0);
            pb.edf = 0.0;
            pb.lastLambda = (pb.lastLambda > 0 ? pb.lastLambda : 1.0);
            return;
        }

        // Lambda grid: warm-start around lastLambda if available; otherwise coarse grid
        double[] lambdas = (pb.lastLambda > 0)
                ? gridAround(pb.lastLambda, 1e-4, 1e4)
                : logspace(-3, 4, 12); // 1e-3 .. 1e4

        double bestGcv = Double.POSITIVE_INFINITY;
        double[] bestBeta = null;
        double bestEdF = 0.0;
        double bestLam = -1.0;

        // Pre-alloc matrices
        DMatrixRMaj M = new DMatrixRMaj(pb.M, pb.M);
        DMatrixRMaj I = identity(pb.M);
        DMatrixRMaj XtX = BtB; // use cached BtB directly

        for (double lam : lambdas) {
            // M = BtB + lam * P + ridge * I
            M.setTo(XtX);
            CommonOps_DDRM.addEquals(M, lam, pb.P);
            if (ridge > 0) CommonOps_DDRM.addEquals(M, ridge, I);

            double[] beta = solveSPD(M, BtR);
            if (beta == null) continue;

            // edf = trace( M^{-1} * BtB )
            DMatrixRMaj S = solveSPDForRight(M, XtX);
            double edf = trace(S);

            // RSS = || r - B beta ||^2
            double rss = 0.0;
            for (int i = 0; i < N; i++) {
                double fi = 0.0;
                for (int j = 0; j < pb.M; j++) fi += pb.B.get(i, j) * beta[j];
                double e = pb.r[i] - fi;
                rss += e * e;
            }
            double denom = Math.max(1e-12, N - Math.min(N - 1, edf));
            double gcv = (rss / N) / Math.pow(denom / N, 2.0);

            if (gcv < bestGcv) {
                bestGcv = gcv;
                bestBeta = beta;
                bestEdF = edf;
                bestLam = lam;
            }
        }

        // Update with best beta
        if (bestBeta == null) {
            Arrays.fill(pb.beta, 0.0);
            Arrays.fill(pb.fit, 0.0);
            pb.edf = 0.0;
            pb.lastLambda = (pb.lastLambda > 0 ? pb.lastLambda : 1.0);
        } else {
            System.arraycopy(bestBeta, 0, pb.beta, 0, pb.M);
            // compute fit and center it to zero-mean so intercept absorbs the average
            double meanFi = 0.0;
            for (int i = 0; i < N; i++) {
                double fi = 0.0;
                for (int j = 0; j < pb.M; j++) fi += pb.B.get(i, j) * bestBeta[j];
                pb.fit[i] = fi;
                meanFi += fi;
            }
            meanFi /= N;
            for (int i = 0; i < N; i++) pb.fit[i] -= meanFi;
            pb.edf = bestEdF;
            pb.lastLambda = (bestLam > 0 ? bestLam : (pb.lastLambda > 0 ? pb.lastLambda : 1.0));
        }
    }

    // ---------- helper structures & numerics ----------

    private static final class ParentBlock {
        final DMatrixRMaj B;    // N x M
        final DMatrixRMaj P;    // M x M (penalty)
        final DMatrixRMaj BtB;  // M x M (cached B^T B)
        final int M;
        final double[] beta;    // M
        final double[] fit;     // N
        final double[] r;       // N (partial residual)
        double edf;
        double lastLambda = -1.0; // warm-start (negative means unset)

        ParentBlock(DMatrixRMaj B, DMatrixRMaj P, DMatrixRMaj BtB) {
            this.B = B;
            this.P = P;
            this.BtB = BtB;
            this.M = B.numCols;
            this.beta = new double[M];
            this.fit = new double[B.numRows];
            this.r = new double[B.numRows];
            this.edf = 0.0;
        }
    }

    private static final class Precomp {
        final DMatrixRMaj B;   // N x M
        final DMatrixRMaj P;   // M x M
        final DMatrixRMaj BtB; // M x M
        final int M;

        Precomp(DMatrixRMaj B, DMatrixRMaj P, DMatrixRMaj BtB) {
            this.B = B;
            this.P = P;
            this.BtB = BtB;
            this.M = B.numCols;
        }
    }

    private static DMatrixRMaj vec(double[] v) {
        DMatrixRMaj m = new DMatrixRMaj(v.length, 1);
        System.arraycopy(v, 0, m.data, 0, v.length);
        return m;
    }

    private static DMatrixRMaj identity(int n) {
        DMatrixRMaj I = new DMatrixRMaj(n, n);
        for (int i = 0; i < n; i++) I.set(i, i, 1.0);
        return I;
    }

    private static double[] logspace(double a, double b, int k) {
        double[] out = new double[k];
        double da = (b - a) / (k - 1);
        for (int i = 0; i < k; i++) out[i] = Math.pow(10.0, a + da * i);
        return out;
    }

    private static double trace(DMatrixRMaj A) {
        int n = Math.min(A.numRows, A.numCols);
        double s = 0;
        for (int i = 0; i < n; i++) s += A.get(i, i);
        return s;
    }

    /**
     * Solve SPD system M x = b using a generic dense solver. Returns x, or null on failure.
     */
    private static double[] solveSPD(DMatrixRMaj M, DMatrixRMaj b) {
        DMatrixRMaj x = new DMatrixRMaj(M.numCols, b.numCols);
        boolean ok = CommonOps_DDRM.solve(M, b, x);
        if (!ok) return null;
        return Arrays.copyOf(x.data, x.getNumElements());
    }

    /**
     * Solve SPD system M X = B for X using a generic dense solver.
     */
    private static DMatrixRMaj solveSPDForRight(DMatrixRMaj M, DMatrixRMaj B) {
        DMatrixRMaj X = new DMatrixRMaj(M.numCols, B.numCols);
        boolean ok = CommonOps_DDRM.solve(M, B, X);
        if (!ok) return new DMatrixRMaj(M.numCols, B.numCols);
        return X;
    }

    /**
     * Open-uniform knot vector on [0,1] with K interior knots and degree p.
     */
    private static double[] knotsOpenUniform(int K, int p) {
        int M = K + 2 * p + 2; // total knots
        double[] t = new double[M];
        // first p+1 zeros
        for (int i = 0; i <= p; i++) t[i] = 0.0;
        // interior equally spaced
        for (int i = 1; i <= K; i++) t[p + i] = (double) i / (K + 1);
        // last p+1 ones
        for (int i = 0; i <= p; i++) t[p + K + i + 1] = 1.0;
        return t;
    }

    /**
     * Cox–de Boor recursion for B-spline basis N_{j,p}(x).
     */
    private static double bsplineBasis(int j, int p, double x, double[] t) {
        if (p == 0) {
            return (x >= t[j] && x < t[j + 1]) || (x == 1.0 && t[j + 1] == 1.0) ? 1.0 : 0.0;
        }
        double left = 0.0, right = 0.0;
        double denom1 = t[j + p] - t[j];
        if (denom1 > 0) left = (x - t[j]) / denom1 * bsplineBasis(j, p - 1, x, t);
        double denom2 = t[j + p + 1] - t[j + 1];
        if (denom2 > 0) right = (t[j + p + 1] - x) / denom2 * bsplineBasis(j + 1, p - 1, x, t);
        return left + right;
    }

    /**
     * d-th order difference matrix (size (M-d) x M).
     */
    private static DMatrixRMaj diffMatrix(int M, int d) {
        DMatrixRMaj D = eye(M);
        for (int k = 0; k < d; k++) {
            int rows = D.numRows - 1;
            DMatrixRMaj Dnext = new DMatrixRMaj(rows, D.numCols);
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < D.numCols; j++) {
                    double v = D.get(i + 1, j) - D.get(i, j);
                    Dnext.set(i, j, v);
                }
            }
            D = Dnext;
        }
        return D;
    }

    private static DMatrixRMaj eye(int n) {
        DMatrixRMaj I = new DMatrixRMaj(n, n);
        for (int i = 0; i < n; i++) I.set(i, i, 1.0);
        return I;
    }

    /**
     * Symmetric log-grid around a center lambda, clipped to [minLam,maxLam].
     */
    private static double[] gridAround(double center, double minLam, double maxLam) {
        // factors ~ {1/25, 1/10, 1/4, 1, 4, 10, 25}
        double[] mult = {0.04, 0.1, 0.25, 1.0, 4.0, 10.0, 25.0};
        double[] out = new double[mult.length];
        for (int i = 0; i < mult.length; i++) {
            double v = center * mult[i];
            if (v < minLam) v = minLam;
            if (v > maxLam) v = maxLam;
            out[i] = v;
        }
        // deduplicate monotone
        Arrays.sort(out);
        int u = 0;
        for (int i = 1; i < out.length; i++) if (out[i] != out[u]) out[++u] = out[i];
        return Arrays.copyOf(out, u + 1);
    }
}
package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

import static java.lang.Double.NaN;

/**
 * RCIT (Randomized Conditional Independence Test) / RCoT (if doRcit=false).
 * <p>
 * Translation of the causal-learn RCIT (originally from R).
 * Reference: Strobl, Zhang, Visweswaran (2019), JCI 7(1).
 *
 * <ul>
 *   <li>Standardize X, Y, Z (z-score, ddof=1)</li>
 *   <li>Bandwidths (σ) via median pairwise Euclidean distance using first min(n,500) rows</li>
 *   <li>Random Fourier Features (RFF): sqrt(2) * cos(W X^T + b), W ~ N(0, 1/σ), b ~ U[0, 2π]</li>
 *   <li>Statistic: n * || Cxy - Cxz Czz^{-1} Czy ||_F^2 (RIT if Z empty)</li>
 *   <li>Null: Gamma (Satterthwaite–Welch) / Edgeworth (HBE/LPB4) / Chi2, or permutation</li>
 * </ul>
 *
 * Parameters supported via setters or via constructor(data, Parameters) legacy keys:
 * <ul>
 *   <li>num features: X/Y (numFeatXY), Z (numFeatZ)</li>
 *   <li>approx: 1=LPB4, 2=HBE, 3=GAMMA, 4=CHI2, 5=PERMUTATION</li>
 *   <li>permutations: for PERMUTATION approx</li>
 *   <li>doRcit: true=RCIT (augment Y with Z), false=RCoT</li>
 *   <li>lambda: ridge added to Czz before inversion</li>
 *   <li>centerFeatures: whether to z-score the RFF features before covariance</li>
 *   <li>seed: RNG seed</li>
 * </ul>
 */
public final class IndTestRcit implements IndependenceTest {

    // ---------------- core data ----------------
    private final DataSet data;
    private final List<Node> vars;
    private final int n;
    private final Random rng;

    // ---------------- hyperparams ----------------
    private int numFeatXY = 5;      // features for X and Y (default aligns with causal-learn)
    private int numFeatZ  = 100;    // features for Z
    private Approx approx = Approx.GAMMA;
    private int permutations = 0;   // used only if approx == PERMUTATION
    private boolean doRcit = true;  // true => RCIT (augment Y with Z); false => RCoT
    private double lambda = 1e-10;  // ridge for Czz
    private boolean centerFeatures = true;

    // ---------------- IndependenceTest state ----------------
    private double alpha = 0.05;
    private double lastP = NaN;
    private boolean verbose = false;

    public IndTestRcit(DataSet dataSet) {
        this(dataSet, new Parameters());
    }

    /**
     * Optional constructor that can read legacy keys if present (kept very light).
     */
    public IndTestRcit(DataSet dataSet, Parameters params) {
        this.data = Objects.requireNonNull(dataSet, "data");
        this.vars = Collections.unmodifiableList(new ArrayList<>(dataSet.getVariables()));
        this.n = dataSet.getNumRows();

        long seed = params.getLong("rcit.seed", 1729L);
        this.rng = new Random(seed);

        // legacy names (won’t override later setter calls from wrapper)
        this.numFeatZ  = Math.max(1, params.getInt("rcit.numF", 100));
        this.numFeatXY = Math.max(1, params.getInt("rcit.numF2", 5));
        this.permutations = Math.max(0, params.getInt("rcit.permutations", 0));
        this.doRcit = params.getBoolean("rcit.rcit", true);

        String approxStr = params.getString("rcit.approx", "gamma");
        setApproximationFromInt(switch (approxStr.toLowerCase(Locale.ROOT)) {
            case "perm", "permutation" -> 5;
            case "chi2", "chi-sq", "chisq" -> 4;
            case "hbe" -> 2;
            case "lpb4", "lpd4" -> 1;
            default -> 3; // gamma
        });

        this.lambda = Math.max(1e-12, params.getDouble("rcit.lambda", this.lambda));
        this.centerFeatures = params.getBoolean("rcit.centerFeatures", true);
    }

    // ---------------- setters for wrapper wiring ----------------

    /** 1=LPB4, 2=HBE, 3=GAMMA, 4=CHI2, 5=PERMUTATION */
    public void setApproximationFromInt(int approxCode) {
        switch (approxCode) {
            case 1 -> this.approx = Approx.LPB4;
            case 2 -> this.approx = Approx.HBE;
            case 3 -> this.approx = Approx.GAMMA;
            case 4 -> this.approx = Approx.CHI2;
            case 5 -> this.approx = Approx.PERMUTATION;
            default -> this.approx = Approx.GAMMA;
        }
    }
    /** true => RCIT (augment Y with Z), false => RCoT */
    public void setDoRcit(boolean doRcit) { this.doRcit = doRcit; }
    /** Ridge added to Czz before inversion. */
    public void setLambda(double lambda) { this.lambda = Math.max(1e-12, lambda); }
    /** Number of permutations for PERMUTATION approx (0 = disabled). */
    public void setPermutations(int permutations) { this.permutations = Math.max(0, permutations); }
    /** Whether to z-score the RFF features prior to covariance. */
    public void setCenterFeatures(boolean centerFeatures) { this.centerFeatures = centerFeatures; }
    /** Feature count for X and Y. */
    public void setNumFeaturesXY(int d) { this.numFeatXY = Math.max(1, d); }
    /** Feature count for Z. */
    public void setNumFeaturesZ(int d) { this.numFeatZ = Math.max(1, d); }
    /** RNG seed. */
    public void setSeed(long seed) { this.rng.setSeed(seed); }

    // ---------------- IndependenceTest ----------------

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
        final List<Node> Z = (z == null) ? Collections.emptyList() : new ArrayList<>(z);

        if (x.equals(y)) {
            if (verbose) TetradLogger.getInstance().log(new IndependenceFact(x, y, new HashSet<>(Z)) + " x == y");
            lastP = 0.0;
            return new IndependenceResult(new IndependenceFact(x, y, new HashSet<>(Z)), false, lastP, alpha - lastP, false);
        }
        if (n < 5) {
            if (verbose) TetradLogger.getInstance().log(new IndependenceFact(x, y, new HashSet<>(Z)) + " n < 5");
            lastP = 1.0;
            return new IndependenceResult(new IndependenceFact(x, y, new HashSet<>(Z)), true, lastP, alpha - lastP, false);
        }

        // Data matrices (n x d)
        SimpleMatrix X  = cols(data, Collections.singletonList(x));
        SimpleMatrix Y  = cols(data, Collections.singletonList(y));
        SimpleMatrix Zm = Z.isEmpty() ? new SimpleMatrix(n, 0) : cols(data, Z);

        // Standardize raw columns
        zscoreInPlace(X); zscoreInPlace(Y); zscoreInPlace(Zm);

        // RCIT: augment Y with Z before features, else RCoT uses Y alone
        SimpleMatrix Yaug = (doRcit && Zm.getNumCols() > 0) ? hstack(Y, Zm) : Y;

        // Bandwidths via median pairwise distance on first r1 rows
        int r1 = Math.min(n, 500);
        double sigX = medianPairwiseDistance(X.rows(0, r1));
        double sigY = medianPairwiseDistance(Yaug.rows(0, r1));
        double sigZ = (Zm.getNumCols() == 0) ? 1.0 : medianPairwiseDistance(Zm.rows(0, r1));

        // Random Fourier Features
        SimpleMatrix fX = rff(X,    numFeatXY, sigX, rng);
        SimpleMatrix fY = rff(Yaug, numFeatXY, sigY, rng);
        SimpleMatrix fZ = (Zm.getNumCols() == 0) ? null : rff(Zm, numFeatZ, sigZ, rng);

        if (centerFeatures) { zscoreInPlace(fX); zscoreInPlace(fY); if (fZ != null) zscoreInPlace(fZ); }

        // Covariances
        SimpleMatrix Cxy = cov(fX, fY);
        final double stat;
        double p;

        if (fZ == null || fZ.getNumCols() == 0) {
            // ---------------- RIT (no conditioning) ----------------
            stat = n * frob2(Cxy);

            SimpleMatrix resX = fX.minus(colMeanRow(fX));
            SimpleMatrix resY = fY.minus(colMeanRow(fY));
            SimpleMatrix Cov  = kronResCov(resX, resY);
            double[] eig      = positiveEigs(Cov);

            switch (approx) {
                case PERMUTATION -> {
                    if (permutations > 0) {
                        int greater = 0;
                        for (int b = 0; b < permutations; b++) {
                            int[] perm = randomPermutation(n, rng);
                            SimpleMatrix fYp = permuteRows(fY, perm);
                            double s = n * frob2(cov(fX, fYp));
                            if (s >= stat) greater++;
                        }
                        p = (greater + 1.0) / (permutations + 1.0);
                    } else {
                        p = gammaApproxP(stat, eig);
                    }
                }
                case HBE   -> p = edgeworthP(stat, eig, false);
                case LPB4  -> p = edgeworthP(stat, eig, true);
                case CHI2  -> p = chi2ApproxP(n, vec(Cxy), Cov);
                case GAMMA -> p = gammaApproxP(stat, eig);
                default -> p = gammaApproxP(stat, eig);
            }
        } else {
            // ---------------- RCIT (with Z) ----------------
            SimpleMatrix Czz = cov(fZ, fZ);
            SimpleMatrix A   = Czz.plus(SimpleMatrix.identity(Czz.getNumRows()).scale(lambda));
            SimpleMatrix iCzz = A.pseudoInverse();

            SimpleMatrix Cxz = cov(fX, fZ);
            SimpleMatrix Czy = cov(fZ, fY);

            SimpleMatrix Cxy_z = Cxy.minus(Cxz.mult(iCzz).mult(Czy));
            stat = n * frob2(Cxy_z);

            if (approx == Approx.PERMUTATION && permutations > 0) {
                int greater = 0;
                for (int b = 0; b < permutations; b++) {
                    int[] perm = randomPermutation(n, rng);
                    SimpleMatrix fYp = permuteRows(fY, perm);
                    SimpleMatrix Cyp  = cov(fX, fYp);
                    SimpleMatrix Czyp = cov(fZ, fYp);
                    SimpleMatrix Cxy_z_p = Cyp.minus(Cxz.mult(iCzz).mult(Czyp));
                    double s = n * frob2(Cxy_z_p);
                    if (s >= stat) greater++;
                }
                p = (greater + 1.0) / (permutations + 1.0);
            } else {
                // Residuals to form Cov of elementwise products
                SimpleMatrix z_iCzz = fZ.mult(iCzz);                // n x Fz
                SimpleMatrix e_x_z  = z_iCzz.mult(Cxz.transpose()); // n x Fx
                SimpleMatrix e_y_z  = z_iCzz.mult(Czy);             // n x Fy
                SimpleMatrix resX   = fX.minus(e_x_z);
                SimpleMatrix resY   = fY.minus(e_y_z);

                SimpleMatrix Cov = kronResCov(resX, resY);
                double[] eig     = positiveEigs(Cov);

                switch (approx) {
                    case HBE   -> p = edgeworthP(stat, eig, false);
                    case LPB4  -> p = edgeworthP(stat, eig, true);
                    case CHI2  -> p = chi2ApproxP(n, vec(Cxy_z), Cov);
                    case GAMMA -> p = gammaApproxP(stat, eig);
                    default -> p = gammaApproxP(stat, eig);
                }
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log(new IndependenceFact(x, y, new HashSet<>(Z)) + " p = " + p);
        }

        lastP = clamp01(p);
        boolean indep = (lastP > alpha);
        return new IndependenceResult(new IndependenceFact(x, y, new HashSet<>(Z)), indep, lastP, alpha - lastP);
    }

    public double getPValue() { return lastP; }
    @Override public List<Node> getVariables() { return vars; }
    @Override public double getAlpha() { return alpha; }
    @Override public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha in (0,1)");
        this.alpha = alpha;
    }
    @Override public DataSet getData() { return data; }
    @Override public boolean isVerbose() { return verbose; }
    @Override public void setVerbose(boolean verbose) { this.verbose = verbose; }

    // ---------------- helpers ----------------

    /** Extract columns for nodes => n x d SimpleMatrix. */
    private static SimpleMatrix cols(DataSet ds, List<Node> vv) {
        int n = ds.getNumRows();
        int d = vv.size();
        SimpleMatrix M = new SimpleMatrix(n, d);
        for (int j = 0; j < d; j++) {
            int col = ds.getColumn(vv.get(j));
            if (col < 0) {
                col = ds.getVariableNames().indexOf(vv.get(j).getName());
                if (col < 0) throw new IllegalArgumentException("Variable not found: " + vv.get(j).getName());
            }
            for (int i = 0; i < n; i++) M.set(i, j, ds.getDouble(i, col));
        }
        return M;
    }

    /** z-score columns, ddof=1. */
    private static void zscoreInPlace(SimpleMatrix M) {
        int n = M.getNumRows(), d = M.getNumCols();
        if (n < 2 || d == 0) return;
        for (int j = 0; j < d; j++) {
            double sum = 0, sumsq = 0;
            for (int i = 0; i < n; i++) { double v = M.get(i, j); sum += v; sumsq += v * v; }
            double mean = sum / n;
            double var  = (sumsq - n * mean * mean) / (n - 1);
            double sd   = (var > 0) ? Math.sqrt(var) : 1.0;
            for (int i = 0; i < n; i++) M.set(i, j, (M.get(i, j) - mean) / sd);
        }
    }

    /** cov(A,B) = A^T B / (n-1), assumes column-centered (zscored). */
    private static SimpleMatrix cov(SimpleMatrix A, SimpleMatrix B) {
        int n = A.getNumRows();
        return A.transpose().mult(B).scale(1.0 / (n - 1));
    }

    /** Frobenius norm squared. */
    private static double frob2(SimpleMatrix M) {
        double s = 0.0;
        for (int i = 0; i < M.getNumElements(); i++) { double v = M.get(i); s += v * v; }
        return s;
    }

    /** Concatenate horizontally. */
    private static SimpleMatrix hstack(SimpleMatrix A, SimpleMatrix B) {
        if (A.getNumRows() != B.getNumRows()) throw new IllegalArgumentException("Row mismatch");
        SimpleMatrix out = new SimpleMatrix(A.getNumRows(), A.getNumCols() + B.getNumCols());
        out.insertIntoThis(0, 0, A);
        out.insertIntoThis(0, A.getNumCols(), B);
        return out;
    }

    /** Column means replicated to n rows (for convenience). */
    private static SimpleMatrix colMeanRow(SimpleMatrix M) {
        int n = M.getNumRows(), d = M.getNumCols();
        SimpleMatrix r = new SimpleMatrix(1, d);
        for (int j = 0; j < d; j++) {
            double s = 0; for (int i = 0; i < n; i++) s += M.get(i, j);
            r.set(0, j, s / n);
        }
        return tileRow(r, n);
    }

    private static SimpleMatrix tileRow(SimpleMatrix row1xd, int n) {
        SimpleMatrix out = new SimpleMatrix(n, row1xd.getNumCols());
        for (int i = 0; i < n; i++) out.insertIntoThis(i, 0, row1xd);
        return out;
    }

    /** Random Fourier Features for RBF: sqrt(2)*cos(W X^T + b), with W ~ N(0, 1/σ). */
    private static SimpleMatrix rff(SimpleMatrix X, int numF, double sigma, Random rng) {
        int n = X.getNumRows(), d = X.getNumCols();
        if (sigma <= 0 || !Double.isFinite(sigma)) sigma = 1.0;

        SimpleMatrix W = new SimpleMatrix(numF, d);
        double sd = 1.0 / sigma;
        for (int i = 0; i < numF; i++)
            for (int j = 0; j < d; j++)
                W.set(i, j, rng.nextGaussian() * sd);

        double twoPi = 2.0 * Math.PI;
        double[] b = new double[numF];
        for (int i = 0; i < numF; i++) b[i] = rng.nextDouble() * twoPi;

        SimpleMatrix Xt = X.transpose();            // d x n
        SimpleMatrix WX = W.mult(Xt);               // numF x n
        for (int i = 0; i < numF; i++)
            for (int j = 0; j < n; j++)
                WX.set(i, j, WX.get(i, j) + b[i]);

        SimpleMatrix feat = new SimpleMatrix(n, numF);
        double scale = Math.sqrt(2.0);
        for (int i = 0; i < numF; i++)
            for (int j = 0; j < n; j++)
                feat.set(j, i, scale * Math.cos(WX.get(i, j)));

        return feat;
    }

    /** Median pairwise Euclidean distance (ignoring zeros). */
    private static double medianPairwiseDistance(SimpleMatrix A) {
        int n = A.getNumRows();
        if (n <= 1 || A.getNumCols() == 0) return 1.0;
        List<Double> dists = new ArrayList<>(n * (n - 1) / 2);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double ss = 0.0;
                for (int k = 0; k < A.getNumCols(); k++) {
                    double diff = A.get(i, k) - A.get(j, k);
                    ss += diff * diff;
                }
                double dist = Math.sqrt(ss);
                if (dist > 0 && Double.isFinite(dist)) dists.add(dist);
            }
        }
        if (dists.isEmpty()) return 1.0;
        Collections.sort(dists);
        int m = dists.size();
        return (m % 2 == 1) ? dists.get(m / 2) : 0.5 * (dists.get(m / 2 - 1) + dists.get(m / 2));
    }

    /** Build Cov for residual elementwise products; returns symmetric PSD matrix whose eigs define the null. */
    private static SimpleMatrix kronResCov(SimpleMatrix resX, SimpleMatrix resY) {
        int Fx = resX.getNumCols(), Fy = resY.getNumCols(), q = Fx * Fy, n = resX.getNumRows();
        SimpleMatrix Z = new SimpleMatrix(n, q);
        int idx = 0;
        for (int a = 0; a < Fx; a++) {
            for (int b = 0; b < Fy; b++) {
                for (int i = 0; i < n; i++) Z.set(i, idx, resX.get(i, a) * resY.get(i, b));
                idx++;
            }
        }
        return Z.transpose().mult(Z).scale(1.0 / n);
    }

    /** Positive eigenvalues of a symmetric PSD matrix (SimpleMatrix.eig). */
    private static double[] positiveEigs(SimpleMatrix Cov) {
        SimpleEVD<SimpleMatrix> evd = Cov.eig();
        int m = evd.getNumberOfEigenvalues();
        ArrayList<Double> pos = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            double lam = evd.getEigenvalue(i).getReal();
            if (lam > 0 && Double.isFinite(lam)) pos.add(lam);
        }
        double[] e = new double[pos.size()];
        for (int i = 0; i < e.length; i++) e[i] = pos.get(i);
        return e;
    }

    /** Satterthwaite–Welch Gamma p-value for weighted chi-square sum with eigenvalues eig. */
    private static double gammaApproxP(double stat, double[] eig) {
        if (eig.length == 0) return (stat <= 1e-12) ? 1.0 : 0.0;
        double s1 = 0.0, s2 = 0.0;
        for (double l : eig) { s1 += l; s2 += l*l; }
        double mu = s1, var = 2.0 * s2;
        if (mu <= 0 || var <= 0) return (stat <= 1e-12) ? 1.0 : 0.0;
        double k = (mu*mu)/var;     // shape
        double theta = var/mu;      // scale
        GammaDistribution gd = new GammaDistribution(k, theta);
        return 1.0 - gd.cumulativeProbability(stat);
    }

    /** Cornish–Fisher tail using skewness (HBE-like) or skew+kurtosis (LPB4-like). */
    private static double edgeworthP(double stat, double[] eig, boolean useKurtosis) {
        if (eig.length == 0) return (stat <= 1e-12) ? 1.0 : 0.0;

        double s1=0, s2=0, s3=0, s4=0;
        for (double l : eig) { s1+=l; s2+=l*l; s3+=l*l*l; s4+=l*l*l*l; }
        double mu  = s1;
        double var = 2.0 * s2;
        if (var <= 0) return (stat <= 1e-12) ? 1.0 : 0.0;

        double sigma = Math.sqrt(var);
        double t = (stat - mu) / sigma;

        double gamma1 = (8.0 * s3) / Math.pow(var, 1.5);   // skew
        double gamma2 = (48.0 * s4) / (var * var);         // excess kurtosis

        double z = t + (gamma1/6.0) * (t*t - 1.0);
        if (useKurtosis) {
            z += (gamma2/24.0) * (t*t*t - 3.0*t)
                 - (gamma1*gamma1/36.0) * (2.0*t*t*t - 5.0*t);
        }
        NormalDistribution nd = new NormalDistribution();
        return 1.0 - nd.cumulativeProbability(z);
    }

    /** Chi-square approx: Q = n * vec(C)^T pinv(Cov) vec(C), df = #positive eigs. */
    private static double chi2ApproxP(double n, SimpleMatrix Cvec, SimpleMatrix Cov) {
        SimpleMatrix iCov = Cov.pseudoInverse();
        SimpleMatrix tmp  = iCov.mult(Cvec);
        double Q = n * Cvec.dot(tmp);
        int df = 0;
        SimpleEVD<SimpleMatrix> evd = Cov.eig();
        for (int i = 0; i < evd.getNumberOfEigenvalues(); i++)
            if (evd.getEigenvalue(i).getReal() > 1e-12) df++;
        df = Math.max(df, 1);
        ChiSquaredDistribution chi2 = new ChiSquaredDistribution(df);
        return 1.0 - chi2.cumulativeProbability(Q);
    }

    /** vec(C) column-stacked. */
    private static SimpleMatrix vec(SimpleMatrix M) {
        SimpleMatrix v = new SimpleMatrix(M.getNumElements(), 1);
        int k = 0;
        for (int j = 0; j < M.numCols(); j++)
            for (int i = 0; i < M.numRows(); i++)
                v.set(k++, 0, M.get(i, j));
        return v;
    }

    private static int[] randomPermutation(int n, Random rng) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = p[i]; p[i] = p[j]; p[j] = t;
        }
        return p;
    }

    private static SimpleMatrix permuteRows(SimpleMatrix M, int[] perm) {
        SimpleMatrix out = new SimpleMatrix(M.getNumRows(), M.getNumCols());
        for (int i = 0; i < perm.length; i++)
            for (int j = 0; j < M.getNumCols(); j++)
                out.set(i, j, M.get(perm[i], j));
        return out;
    }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    // ---------------- enum ----------------
    private enum Approx { LPB4, HBE, GAMMA, CHI2, PERMUTATION }
}
package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

import static java.lang.Double.NaN;

/**
 * RCIT / RCoT (Strobl, Zhang, Visweswaran 2019) implemented in the same
 * “meta-architecture” as {@link FfCiContinuous}, but with RCIT-style feature blocks.
 *
 * <p>Core statistic (feature space):
 * <pre>
 *   FX = phi(X),  FY = psi(Y)  (or psi([Y,Z]) if doRcit && Z nonempty),  FZ = eta(Z)
 *   RX = FX - Proj_Z(FX)   (ridge)
 *   RY = FY - Proj_Z(FY)   (ridge)
 *   stat = n * || cov(RX, RY) ||_F^2
 * </pre>
 *
 * <p>Null approximations are delegated to {@code QuadraticFormPValues}.
 *
 * <p>Design goal: keep everything as close to {@link FfCiContinuous} as sensible,
 * changing only what’s needed for RCIT/RCoT behavior (notably the optional Y-augmentation).
 */
public final class Rcit implements IndependenceTest, RowsSettable {

    // ---------------- core data ----------------
    private final DataSet data;
    private final List<Node> vars;

    // Feature cache (same style as FfCiContinuous)
    private final Map<String, SimpleMatrix> featCache = new HashMap<>();

    // Active rows state
    private List<Integer> rows = null;
    private int n;

    // ---------------- knobs ----------------
    private double alpha = 0.05;
    private double lastP = NaN;
    private boolean verbose = false;

    // Ridge (absolute, like FfCiContinuous; RCIT paper uses ridge in regression step)
    private double lambda = .001;

    // Feature-space approximation
    private Approx approx = Approx.GAMMA;
    private int permutations = 200;

    // RCIT-specific feature sizing
    private int numFeatXY = 10;     // features for X and (usually) Y
    private int numFeatZ = 100;     // features for Z
    private int numFeatYAug = 50;   // features for augmented [Y,Z] block (RCIT mode)
    private boolean doRcit = true;  // true=RCIT (augment Y with Z when Z nonempty), false=RCoT (no augmentation)

    // RFF/ORF machinery (copied from FfCiContinuous)
    private FeatureType featureType = FeatureType.RFF;
    private int bwMaxRows = 500;
    private double bandwidthMultiplier = 1.0;
    private long seed = 1729L;

    // --------------------------------------------------------------------
    // Construction
    // --------------------------------------------------------------------

    public Rcit(DataSet dataSet) {
        this(dataSet, new Parameters());
    }

    public Rcit(DataSet dataSet, Parameters params) {
        this.data = Objects.requireNonNull(dataSet, "data");
        this.vars = Collections.unmodifiableList(new ArrayList<>(dataSet.getVariables()));
        this.n = getActiveRowCount();

        // Optional: read some params if you want parity with other tests
        this.seed = params.getLong("rcit.seed", this.seed);
        this.lambda = params.getDouble("rcit.lambda", this.lambda);
        this.numFeatXY = params.getInt("rcit.numFeatXY", this.numFeatXY);
        this.numFeatZ = params.getInt("rcit.numFeatZ", this.numFeatZ);
        this.numFeatYAug = params.getInt("rcit.numFeatYAug", this.numFeatYAug);
        this.doRcit = params.getBoolean("rcit.doRcit", this.doRcit);
        this.bwMaxRows = params.getInt("rcit.bwMaxRows", this.bwMaxRows);
        this.bandwidthMultiplier = params.getDouble("rcit.bandwidthMultiplier", this.bandwidthMultiplier);

        invalidateFeatureCache();
    }

    // --------------------------------------------------------------------
    // Core math utilities (kept identical in spirit to FfCiContinuous)
    // --------------------------------------------------------------------

    /** z-score columns, ddof=1. */
    private static void zscoreInPlace(SimpleMatrix M) {
        int n = M.getNumRows(), d = M.getNumCols();
        if (n < 2 || d == 0) return;

        for (int j = 0; j < d; j++) {
            double sum = 0, sumsq = 0;
            for (int i = 0; i < n; i++) {
                double v = M.get(i, j);
                sum += v;
                sumsq += v * v;
            }
            double mean = sum / n;
            double var = (sumsq - n * mean * mean) / (n - 1);
            double sd = (var > 0) ? Math.sqrt(var) : 1.0;
            for (int i = 0; i < n; i++) {
                M.set(i, j, (M.get(i, j) - mean) / sd);
            }
        }
    }

    /** cov(A,B) = A^T B / (n-1), assumes column-centered. */
    private static SimpleMatrix cov(SimpleMatrix A, SimpleMatrix B) {
        int n = A.getNumRows();
        return A.transpose().mult(B).scale(1.0 / (n - 1));
    }

    /** Frobenius norm squared. */
    private static double frob2(SimpleMatrix M) {
        double s = 0.0;
        double[] a = M.getDDRM().data;
        for (double v : a) s += v * v;
        return s;
    }

    /**
     * Ridge residualization on covariance scale (same as FfCiContinuous):
     * X - Z (Czz + λI)^{-1} Czx
     */
    private static SimpleMatrix ridgeResidual(SimpleMatrix X, SimpleMatrix Z, double lambda) {
        if (Z == null || Z.getNumCols() == 0) return X;

        int n = Z.getNumRows();
        double denom = Math.max(1.0, n - 1.0);

        SimpleMatrix Czz = Z.transpose().mult(Z).scale(1.0 / denom);
        SimpleMatrix Czx = Z.transpose().mult(X).scale(1.0 / denom);

        SimpleMatrix A = Czz.plus(SimpleMatrix.identity(Czz.getNumRows())
                .scale(Math.max(1e-18, lambda)));

        SimpleMatrix B = A.solve(Czx);  // (Czz + λI)^{-1} Czx
        return X.minus(Z.mult(B));
    }

    /** Covariance of elementwise residual products. */
    private static SimpleMatrix kronResCov(SimpleMatrix RX, SimpleMatrix RY) {
        int n = RX.getNumRows();
        int fx = RX.getNumCols();
        int fy = RY.getNumCols();
        SimpleMatrix Z = new SimpleMatrix(n, fx * fy);

        int idx = 0;
        for (int i = 0; i < fx; i++) {
            for (int j = 0; j < fy; j++) {
                for (int r = 0; r < n; r++) {
                    Z.set(r, idx, RX.get(r, i) * RY.get(r, j));
                }
                idx++;
            }
        }

        return Z.transpose().mult(Z).scale(1.0 / (n - 1));
    }

    /** Positive eigenvalues only. */
    private static double[] positiveEigs(SimpleMatrix Cov) {
        SimpleEVD<SimpleMatrix> evd = Cov.eig();
        List<Double> out = new ArrayList<>();

        for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
            double v = evd.getEigenvalue(i).getReal();
            if (v > 1e-12 && Double.isFinite(v)) out.add(v);
        }

        double[] e = new double[out.size()];
        for (int i = 0; i < e.length; i++) e[i] = out.get(i);
        return e;
    }

    /** Center columns in-place (for feature matrices). */
    private static void subtractColumnMeansInPlace(SimpleMatrix M) {
        int n = M.getNumRows(), d = M.getNumCols();
        if (n == 0 || d == 0) return;

        for (int j = 0; j < d; j++) {
            double s = 0.0;
            for (int i = 0; i < n; i++) s += M.get(i, j);
            double mean = s / n;
            for (int i = 0; i < n; i++) M.set(i, j, M.get(i, j) - mean);
        }
    }

    /** cov(A, permuted(B)) = A^T * B_perm / (n-1), assumes centered. */
    private static SimpleMatrix covWithPermutedB(SimpleMatrix A, SimpleMatrix B, int[] perm) {
        int n = A.getNumRows();
        int p = A.getNumCols();
        int q = B.getNumCols();
        SimpleMatrix C = new SimpleMatrix(p, q);

        for (int i = 0; i < n; i++) {
            int bi = perm[i];
            for (int a = 0; a < p; a++) {
                double av = A.get(i, a);
                for (int b = 0; b < q; b++) {
                    C.set(a, b, C.get(a, b) + av * B.get(bi, b));
                }
            }
        }
        return C.scale(1.0 / (n - 1));
    }

    // Marsaglia polar gaussian from SplittableRandom (same as FfCiContinuous)
    private static double nextGaussian(SplittableRandom rng) {
        double u, v, s;
        do {
            u = 2.0 * rng.nextDouble() - 1.0;
            v = 2.0 * rng.nextDouble() - 1.0;
            s = u * u + v * v;
        } while (s >= 1.0 || s == 0.0);
        return u * Math.sqrt(-2.0 * Math.log(s) / s);
    }

    /** ORF: block-orthogonal rows in blocks of size d. */
    private static double[][] sampleOrthogonalW(int mFeatures, int d, double wStd, SplittableRandom rng) {
        double[][] W = new double[mFeatures][d];
        if (d <= 0) return W;

        int filled = 0;
        while (filled < mFeatures) {
            int block = Math.min(d, mFeatures - filled);

            double[][] Q = new double[block][d];
            for (int i = 0; i < block; i++) {
                for (int j = 0; j < d; j++) Q[i][j] = nextGaussian(rng);
            }

            // Gram–Schmidt rows of Q
            for (int i = 0; i < block; i++) {
                for (int k = 0; k < i; k++) {
                    double dot = 0.0;
                    for (int j = 0; j < d; j++) dot += Q[i][j] * Q[k][j];
                    for (int j = 0; j < d; j++) Q[i][j] -= dot * Q[k][j];
                }
                double norm2 = 0.0;
                for (int j = 0; j < d; j++) norm2 += Q[i][j] * Q[i][j];
                double norm = Math.sqrt(Math.max(1e-18, norm2));
                for (int j = 0; j < d; j++) Q[i][j] /= norm;
            }

            for (int i = 0; i < block; i++) {
                double r = chiRadius(d, rng);
                double s = wStd * r;
                int outRow = filled + i;
                for (int j = 0; j < d; j++) W[outRow][j] = s * Q[i][j];
            }

            filled += block;
        }

        return W;
    }

    private static double chiRadius(int d, SplittableRandom rng) {
        double ss = 0.0;
        for (int k = 0; k < d; k++) {
            double g = nextGaussian(rng);
            ss += g * g;
        }
        return Math.sqrt(Math.max(1e-18, ss));
    }

    /**
     * Median of pairwise squared distances for up to maxRows points.
     * Deterministic subsampling via evenly spaced indices.
     */
    private static double medianDistanceSquaredND(double[][] Z, int maxRows) {
        int n = Z.length;
        int d = (n == 0) ? 0 : Z[0].length;
        if (n < 3 || d == 0) return 1.0;

        int m = Math.min(n, Math.max(3, maxRows));

        int[] idx = new int[m];
        if (m == n) {
            for (int i = 0; i < m; i++) idx[i] = i;
        } else {
            for (int i = 0; i < m; i++) idx[i] = (int) Math.floor((i * (long) (n - 1)) / (double) (m - 1));
        }

        int cnt = m * (m - 1) / 2;
        double[] d2 = new double[cnt];
        int t = 0;

        for (int a = 1; a < m; a++) {
            int i = idx[a];
            for (int b = 0; b < a; b++) {
                int j = idx[b];
                double dist2 = 0.0;
                for (int k = 0; k < d; k++) {
                    double diff = Z[i][k] - Z[j][k];
                    dist2 += diff * diff;
                }
                d2[t++] = dist2;
            }
        }

        Arrays.sort(d2, 0, t);
        int firstPos = 0;
        while (firstPos < t && d2[firstPos] <= 0) firstPos++;
        if (firstPos >= t) return 1.0;
        int mid = firstPos + (t - firstPos) / 2;
        return d2[mid];
    }

    /** z-score raw columns, ddof=1 (for double[][] blocks). */
    private static void zscoreInPlace(double[][] M) {
        int n = M.length;
        if (n < 2) return;
        int d = M[0].length;
        if (d == 0) return;

        for (int j = 0; j < d; j++) {
            double sum = 0.0, sumsq = 0.0;
            for (int i = 0; i < n; i++) {
                double v = M[i][j];
                sum += v;
                sumsq += v * v;
            }
            double mean = sum / n;
            double var = (sumsq - n * mean * mean) / (n - 1);
            double sd = (var > 0) ? Math.sqrt(var) : 1.0;
            for (int i = 0; i < n; i++) M[i][j] = (M[i][j] - mean) / sd;
        }
    }

    // --------------------------------------------------------------------
    // IndependenceTest
    // --------------------------------------------------------------------

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z)
            throws InterruptedException {

        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");

        List<Node> Z = (z == null) ? List.of() : new ArrayList<>(z);

        // keep consistent determinism
        Z.sort(Comparator.comparing(Node::getName));

        // Update n for current rows
        this.n = getActiveRowCount();

        if (x.equals(y)) {
            lastP = 0.0;
            IndependenceFact fact = new IndependenceFact(x, y, new HashSet<>(Z));
            return new IndependenceResult(fact, false, lastP, alpha - lastP);
        }

        if (n < 5) {
            lastP = 1.0;
            IndependenceFact fact = new IndependenceFact(x, y, new HashSet<>(Z));
            return new IndependenceResult(fact, true, lastP, alpha - lastP);
        }

        // ------------------------------------------------------------
        // 1) Feature blocks (FfCiContinuous architecture)
        // ------------------------------------------------------------
        SimpleMatrix FX = rffOrOrfFeaturesFor(List.of(x), numFeatXY, "X");

        final SimpleMatrix FY;
        if (doRcit && !Z.isEmpty()) {
            // RCIT: augment Y-block with Z block (as in your earlier intent).
            ArrayList<Node> yAug = new ArrayList<>(1 + Z.size());
            yAug.add(y);
            yAug.addAll(Z);
            FY = rffOrOrfFeaturesFor(yAug, numFeatYAug, "Yaug");
        } else {
            // RCoT (or unconditional): plain Y features
            FY = rffOrOrfFeaturesFor(List.of(y), numFeatXY, "Y");
        }

        SimpleMatrix FZ = Z.isEmpty() ? null : rffOrOrfFeaturesFor(Z, numFeatZ, "Z");

        // IMPORTANT: follow FfCiContinuous pattern (zscore after centering)
        zscoreInPlace(FX);
        zscoreInPlace(FY);
        if (FZ != null) zscoreInPlace(FZ);

        // ------------------------------------------------------------
        // 2) Residualize on Z (ridge) — same as FfCiContinuous
        // ------------------------------------------------------------
        SimpleMatrix RX = ridgeResidual(FX, FZ, lambda);
        SimpleMatrix RY = ridgeResidual(FY, FZ, lambda);

        // Ensure centered residuals before covariance/statistic (matches cov() assumption).
        subtractColumnMeansInPlace(RX);
        subtractColumnMeansInPlace(RY);

        // ------------------------------------------------------------
        // 3) Statistic
        // ------------------------------------------------------------
        SimpleMatrix Cxy = cov(RX, RY);
        double stat = n * frob2(Cxy);

        // ------------------------------------------------------------
        // 4) P-value (exactly the same machinery as FfCiContinuous)
        // ------------------------------------------------------------
        double p;

        if (approx == Approx.PERMUTATION && permutations > 0) {
            int greater = 0;

            // Fisher–Yates permutation of row indices.
            int[] perm = new int[n];
            for (int i = 0; i < n; i++) perm[i] = i;

            // Deterministic per-fact permutation RNG (stable across calls)
            long permSeed = seedForPermutation(x, y, Z);
            SplittableRandom prng = new SplittableRandom(permSeed);

            for (int b = 0; b < permutations; b++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                for (int i = n - 1; i > 0; i--) {
                    int j = prng.nextInt(i + 1);
                    int t = perm[i];
                    perm[i] = perm[j];
                    perm[j] = t;
                }

                SimpleMatrix Cb = covWithPermutedB(RX, RY, perm);
                double statB = n * frob2(Cb);
                if (statB >= stat) greater++;
            }

            p = (greater + 1.0) / (permutations + 1.0);

        } else {
            SimpleMatrix Cov = kronResCov(RX, RY);
            double[] eig = positiveEigs(Cov);

            p = switch (approx) {
                case GAMMA -> QuadraticFormPValues.gammaSatterthwaiteP(stat, eig);
                case SADDLEPOINT -> QuadraticFormPValues.saddlepointLugannaniRiceP(stat, eig);
                case DAVIES_IMHOF -> QuadraticFormPValues.daviesP(stat, eig);
                default -> QuadraticFormPValues.gammaSatterthwaiteP(stat, eig);
            };
        }

        if (!Double.isFinite(p)) p = 1.0;
        p = Math.min(1.0, Math.max(0.0, p));
        lastP = p;

        IndependenceFact fact = new IndependenceFact(x, y, new HashSet<>(Z));

        if (verbose) {
            TetradLogger.getInstance().log(fact + " p=" + p
                    + " stat=" + stat
                    + " approx=" + approx
                    + (approx == Approx.PERMUTATION ? (" perms=" + permutations) : "")
                    + " doRcit=" + doRcit);
        }

        return new IndependenceResult(fact, p > alpha, p, alpha - p);
    }

    // --------------------------------------------------------------------
    // RCIT / RCoT setters (keep them; don’t drop methods)
    // --------------------------------------------------------------------

    public void setDoRcit(boolean doRcit) {
        this.doRcit = doRcit;
        invalidateFeatureCache();
    }

    public boolean isDoRcit() {
        return doRcit;
    }

    public void setNumFeaturesXY(int d) {
        this.numFeatXY = Math.max(1, d);
        invalidateFeatureCache();
    }

    public void setNumFeaturesZ(int d) {
        this.numFeatZ = Math.max(1, d);
        invalidateFeatureCache();
    }

    public void setNumFeaturesYAug(int d) {
        this.numFeatYAug = Math.max(1, d);
        invalidateFeatureCache();
    }

    public void setApproximation(Approx approx) {
        this.approx = Objects.requireNonNull(approx, "approx");
    }

    public void setPermutations(int permutations) {
        this.permutations = Math.max(0, permutations);
    }

    public void setLambda(double lambda) {
        this.lambda = lambda;
        invalidateFeatureCache();
    }

    public void setFeatureType(FeatureType featureType) {
        this.featureType = Objects.requireNonNull(featureType, "featureType");
        invalidateFeatureCache();
    }

    public void setBwMaxRows(int bwMaxRows) {
        this.bwMaxRows = bwMaxRows;
        invalidateFeatureCache();
    }

    public void setBandwidthMultiplier(double bandwidthMultiplier) {
        if (!(bandwidthMultiplier > 0) || !Double.isFinite(bandwidthMultiplier)) {
            throw new IllegalArgumentException("bandwidthMultiplier must be > 0 and finite");
        }
        this.bandwidthMultiplier = bandwidthMultiplier;
        invalidateFeatureCache();
    }

    public void setSeed(long seed) {
        this.seed = seed;
        invalidateFeatureCache();
    }

    public double getPValue() {
        return lastP;
    }

    // --------------------------------------------------------------------
    // Feature building (copied from FfCiContinuous, only lightly refactored)
    // --------------------------------------------------------------------

    /**
     * Builds RFF/ORF features for the block of variables 'vs'.
     *
     * <p>- Extracts raw continuous columns for vars in vs over the active rows
     * <br>- z-scores raw columns
     * <br>- chooses bw2 using median pairwise distance^2 (continuous only)
     * <br>- maps to RFF or ORF features for an RBF kernel
     * <br>- centers feature columns (so cov(A,B) = A^T B/(n-1) is correct after centering)
     */
    private SimpleMatrix rffOrOrfFeaturesFor(List<Node> vs, int mFeatures, String tag) {
        Objects.requireNonNull(vs, "vs");
        if (mFeatures <= 0) return new SimpleMatrix(getActiveRowCount(), 0);

        final String key = featKey(tag, vs, mFeatures);
        SimpleMatrix cached = featCache.get(key);
        if (cached != null) return cached;

        final int n = getActiveRowCount();
        final double[][] Zraw = extractRawBlock(vs);   // n x d
        zscoreInPlace(Zraw);                           // standardize raw columns first

        double bw2 = 1.0;
        if (Zraw.length > 0 && Zraw[0].length > 0) {
            bw2 = medianDistanceSquaredND(Zraw, Math.min(n, bwMaxRows));
            if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;
            bw2 *= (bandwidthMultiplier * bandwidthMultiplier);
            if (bw2 < 1e-12) bw2 = 1e-12;
        }

        long localSeed = seedForBlock(tag, vs) ^ seed;
        double[][] Phi = rffFeatures(Zraw, mFeatures, bw2, localSeed);

        SimpleMatrix M = new SimpleMatrix(Phi);

        // Center features (cov() assumes centered)
        subtractColumnMeansInPlace(M);

        featCache.put(key, M);
        return M;
    }

    private String featKey(String tag, List<Node> vs, int mFeatures) {
        ArrayList<String> names = new ArrayList<>(vs.size());
        for (Node v : vs) names.add(v.getName());
        names.sort(String::compareTo);

        StringBuilder sb = new StringBuilder(220);
        sb.append("RCIT|tag=").append(tag)
                .append("|n=").append(getActiveRowCount())
                .append("|rowsHash=").append(activeRowsHash())
                .append("|m=").append(mFeatures)
                .append("|ft=").append(featureType.name())
                .append("|bwMult=").append(Double.doubleToLongBits(bandwidthMultiplier))
                .append("|bwMax=").append(bwMaxRows)
                .append("|seed=").append(seed)
                .append("|doRcit=").append(doRcit ? 1 : 0)
                .append("|vars=");
        for (String s : names) sb.append(s).append(",");
        return sb.toString();
    }

    /** Deterministic seed for a block; makes results stable across runs. */
    private long seedForBlock(String tag, List<Node> block) {
        long h = 1469598103934665603L; // FNV-ish
        h = 1099511628211L * (h ^ tag.hashCode());

        ArrayList<String> names = new ArrayList<>(block.size());
        for (Node v : block) names.add(v.getName());
        names.sort(String::compareTo);
        for (String s : names) h = 1099511628211L * (h ^ s.hashCode());

        h = 1099511628211L * (h ^ getActiveRowCount());
        h = 1099511628211L * (h ^ activeRowsHash());
        return h;
    }

    /** Deterministic seed for permutation loop (stable per fact). */
    private long seedForPermutation(Node x, Node y, List<Node> Z) {
        long h = 1469598103934665603L;
        h = 1099511628211L * (h ^ x.getName().hashCode());
        h = 1099511628211L * (h ^ y.getName().hashCode());
        for (Node z : Z) h = 1099511628211L * (h ^ z.getName().hashCode());
        h = 1099511628211L * (h ^ getActiveRowCount());
        h = 1099511628211L * (h ^ activeRowsHash());
        h = 1099511628211L * (h ^ (doRcit ? 1 : 0));
        return h ^ seed;
    }

    /**
     * Extract raw data for vars in vs over the active rows.
     * Assumes all variables are continuous.
     */
    private double[][] extractRawBlock(List<Node> vs) {
        final int n = getActiveRowCount();
        final int d = vs.size();
        double[][] Z = new double[n][d];

        for (int j = 0; j < d; j++) {
            Node v = vs.get(j);
            int col = data.getColumn(v);
            if (col < 0) throw new IllegalArgumentException("Variable not found: " + v.getName());

            for (int i = 0; i < n; i++) {
                int row = activeRowIndex(i);
                Z[i][j] = data.getDouble(row, col);
            }
        }
        return Z;
    }

    /**
     * RFF/ORF features for RBF kernel k(x,x') = exp(-||x-x'||^2 / bw2).
     * Uses:
     * wStd = sqrt(2 / bw2), phi = sqrt(2/m) cos(Wx + b)
     */
    private double[][] rffFeatures(double[][] Z, int mFeatures, double bw2, long seed) {
        final int n = Z.length;
        final int d = (n == 0) ? 0 : Z[0].length;

        double[][] Phi = new double[n][mFeatures];
        if (n == 0) return Phi;

        // Handle d=0: constant features (cos(b))
        if (d == 0) {
            SplittableRandom rng0 = new SplittableRandom(seed);
            double scale0 = Math.sqrt(2.0 / mFeatures);
            double[] b0 = new double[mFeatures];
            for (int j = 0; j < mFeatures; j++) b0[j] = 2.0 * Math.PI * rng0.nextDouble();
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < mFeatures; j++) Phi[i][j] = scale0 * Math.cos(b0[j]);
            }
            return Phi;
        }

        if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;

        final double wStd = Math.sqrt(2.0 / bw2);
        final double scale = Math.sqrt(2.0 / mFeatures);
        SplittableRandom rng = new SplittableRandom(seed);

        double[][] W;
        double[] b = new double[mFeatures];

        if (featureType == FeatureType.RFF) {
            W = new double[mFeatures][d];
            for (int j = 0; j < mFeatures; j++) {
                for (int k = 0; k < d; k++) W[j][k] = wStd * nextGaussian(rng);
                b[j] = 2.0 * Math.PI * rng.nextDouble();
            }
        } else if (featureType == FeatureType.ORF) {
            W = sampleOrthogonalW(mFeatures, d, wStd, rng);
            for (int j = 0; j < mFeatures; j++) b[j] = 2.0 * Math.PI * rng.nextDouble();
        } else {
            throw new IllegalArgumentException("featureType must be RFF or ORF");
        }

        for (int i = 0; i < n; i++) {
            double[] Zi = Z[i];
            for (int j = 0; j < mFeatures; j++) {
                double dot = 0.0;
                double[] wj = W[j];
                for (int k = 0; k < d; k++) dot += wj[k] * Zi[k];
                Phi[i][j] = scale * Math.cos(dot + b[j]);
            }
        }

        return Phi;
    }

    private int activeRowsHash() {
        if (rows == null) return 0;
        int h = 1;
        for (int r : rows) h = 31 * h + r;
        return h;
    }

    private void invalidateFeatureCache() {
        featCache.clear();
    }

    // --------------------------------------------------------------------
    // RowsSettable
    // --------------------------------------------------------------------

    @Override
    public List<Integer> getRows() {
        return rows;
    }

    @Override
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            this.rows = null;
            this.n = data.getNumRows();
            invalidateFeatureCache();
            return;
        }

        for (int i = 0; i < rows.size(); i++) {
            Integer r = rows.get(i);
            if (r == null) throw new NullPointerException("Row " + i + " is null.");
            if (r < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            if (r >= data.getNumRows()) throw new IllegalArgumentException("Row " + i + " out of bounds: " + r);
        }

        this.rows = new ArrayList<>(rows);
        this.n = this.rows.size();
        invalidateFeatureCache();
    }

    private int getActiveRowCount() {
        return (rows == null) ? data.getNumRows() : rows.size();
    }

    private int activeRowIndex(int i) {
        return (rows == null) ? i : rows.get(i);
    }

    // --------------------------------------------------------------------
    // IndependenceTest boilerplate
    // --------------------------------------------------------------------

    @Override
    public List<Node> getVariables() {
        return vars;
    }

    @Override
    public double getAlpha() {
        return alpha;
    }

    @Override
    public void setAlpha(double a) {
        if (!(a > 0 && a < 1)) throw new IllegalArgumentException("alpha in (0,1)");
        alpha = a;
    }

    @Override
    public DataSet getData() {
        return data;
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void setVerbose(boolean v) {
        verbose = v;
    }

    // --------------------------------------------------------------------
    // Enums
    // --------------------------------------------------------------------

    public enum FeatureType {
        RFF,
        ORF
    }

    public enum Approx {
        GAMMA,
        SADDLEPOINT,
        DAVIES_IMHOF,
        PERMUTATION
    }
}
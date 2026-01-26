package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.GammaDistribution;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

import static java.lang.Double.NaN;

/**
 * FF-CI (Fast Fourier / Feature-based Conditional Independence Test).
 * <p>
 * This implementation fixes ridge-scaling and covariance inconsistencies
 * so that analytic null approximations (Gamma / HBE / LPB4) are calibrated.
 * <p>
 * External behavior and API are unchanged.
 */
public final class FfCi implements IndependenceTest, RowsSettable {

    // ---------------- core data ----------------
    private final DataSet data;
    private final List<Node> vars;
    private final Random rng = new Random(1729L);
    // Add these fields to FfCi (or adapt to your existing knobs):
    private final double bandwidthMultiplier = 1.0;
    // Optional but recommended cache:
    private final Map<String, SimpleMatrix> featCache = new HashMap<>();
    // Active rows state
    private List<Integer> rows = null;
    private int n;
    // ---------------- hyperparams ----------------
    private double lambda = .001;
    private Approx approx = Approx.GAMMA;
    private double alpha = 0.05;
    private double lastP = NaN;
    private int permutations = 200;
    private int numFeatXY = 10;
    private int numFeatZ = 100;
    private FeatureType featureType = FeatureType.RFF;
    private int bwMaxRows = 500;
    private long seed = 1729L;
    private boolean verbose = false;

    /**
     * Constructs FF-CI with default parameters.
     */
    public FfCi(DataSet dataSet) {
        this(dataSet, new Parameters());
    }

    // --------------------------------------------------------------------
    // Core math utilities
    // --------------------------------------------------------------------

    /**
     * Constructs FF-CI with parameters.
     */
    public FfCi(DataSet dataSet, Parameters params) {
        this.data = Objects.requireNonNull(dataSet, "data");
        this.vars = Collections.unmodifiableList(new ArrayList<>(dataSet.getVariables()));
        this.n = getActiveRowCount();
    }

    /**
     * z-score columns, ddof=1.
     */
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
            for (int i = 0; i < n; i++)
                M.set(i, j, (M.get(i, j) - mean) / sd);
        }
    }

    /**
     * cov(A,B) = A^T B / (n-1), assumes column-centered.
     */
    private static SimpleMatrix cov(SimpleMatrix A, SimpleMatrix B) {
        int n = A.getNumRows();
        return A.transpose().mult(B).scale(1.0 / (n - 1));
    }

    /**
     * Frobenius norm squared.
     */
    private static double frob2(SimpleMatrix M) {
        double s = 0.0;
        double[] a = M.getDDRM().data;
        for (double v : a) s += v * v;
        return s;
    }

    /**
     * Ridge residualization on covariance scale:
     * <p>
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

    /**
     * Covariance of elementwise residual products.
     */
    private static SimpleMatrix kronResCov(SimpleMatrix RX, SimpleMatrix RY) {
        int n = RX.getNumRows();
        int fx = RX.getNumCols();
        int fy = RY.getNumCols();
        SimpleMatrix Z = new SimpleMatrix(n, fx * fy);

        int idx = 0;
        for (int i = 0; i < fx; i++)
            for (int j = 0; j < fy; j++) {
                for (int r = 0; r < n; r++)
                    Z.set(r, idx, RX.get(r, i) * RY.get(r, j));
                idx++;
            }

        return Z.transpose().mult(Z).scale(1.0 / (n - 1));
    }

    /**
     * Positive eigenvalues only.
     */
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

    /**
     * Gamma (Satterthwaite–Welch) approximation.
     */
    private static double gammaApproxP(double stat, double[] eig) {
        if (eig.length == 0) return (stat <= 1e-12) ? 1.0 : 0.0;

        double s1 = 0, s2 = 0;
        for (double l : eig) {
            s1 += l;
            s2 += l * l;
        }

        double mu = s1;
        double var = 2.0 * s2;
        if (mu <= 0 || var <= 0) return 1.0;

        double k = mu * mu / var;
        double theta = var / mu;

        GammaDistribution gd = new GammaDistribution(k, theta);
        return 1.0 - gd.cumulativeProbability(stat);
    }

    // --------------------------------------------------------------------
    // IndependenceTest
    // --------------------------------------------------------------------

    // --------------------------------------------------------------------
    // RowsSettable
    // --------------------------------------------------------------------

    /**
     * cov(A, permuted(B)) = A^T * B_perm / (n-1), assumes centered
     */
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

    // Marsaglia polar gaussian from SplittableRandom
    private static double nextGaussian(SplittableRandom rng) {
        double u, v, s;
        do {
            u = 2.0 * rng.nextDouble() - 1.0;
            v = 2.0 * rng.nextDouble() - 1.0;
            s = u * u + v * v;
        } while (s >= 1.0 || s == 0.0);
        return u * Math.sqrt(-2.0 * Math.log(s) / s);
    }

    /**
     * ORF: block-orthogonal rows in blocks of size d.
     */
    private static double[][] sampleOrthogonalW(int mFeatures, int d, double wStd, SplittableRandom rng) {
        double[][] W = new double[mFeatures][d];
        if (d <= 0) return W;

        int filled = 0;
        while (filled < mFeatures) {
            int block = Math.min(d, mFeatures - filled);

            double[][] Q = new double[block][d];
            for (int i = 0; i < block; i++)
                for (int j = 0; j < d; j++)
                    Q[i][j] = nextGaussian(rng);

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

    // --------------------------------------------------------------------
    // Data helpers
    // --------------------------------------------------------------------

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

    /**
     * z-score raw columns, ddof=1 (for double[][] blocks).
     */
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

    /**
     * Center columns in-place (for feature matrices).
     */
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

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z)
            throws InterruptedException {

        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

        List<Node> Z = (z == null) ? List.of() : new ArrayList<>(z);

//        SimpleMatrix X = col(x);
//        SimpleMatrix Y = col(y);
//        SimpleMatrix Zm = Z.isEmpty() ? null : cols(Z);

        SimpleMatrix X = rffOrOrfFeaturesFor(List.of(x), numFeatXY, /*tag*/"X");
        SimpleMatrix Y = rffOrOrfFeaturesFor(List.of(y), numFeatXY, /*tag*/"Y");
        SimpleMatrix Zm = Z.isEmpty() ? null : rffOrOrfFeaturesFor(Z, numFeatZ, /*tag*/"Z");

        zscoreInPlace(X);
        zscoreInPlace(Y);
        if (Zm != null) zscoreInPlace(Zm);

        SimpleMatrix RX = ridgeResidual(X, Zm, lambda);
        SimpleMatrix RY = ridgeResidual(Y, Zm, lambda);

        // Ensure centered residuals before covariance/statistic (matches cov() assumption).
        subtractColumnMeansInPlace(RX);
        subtractColumnMeansInPlace(RY);

        SimpleMatrix Cxy = cov(RX, RY);
        double stat = n * frob2(Cxy);

        double p;

        // --------------------------------------------------------------------
        // Permutation option (reference / diagnostic calibration)
        // --------------------------------------------------------------------
        if (approx == Approx.PERMUTATION && permutations > 0) {
            int greater = 0;

            // Fisher–Yates permutation of row indices.
            int[] perm = new int[n];
            for (int i = 0; i < n; i++) perm[i] = i;

            for (int b = 0; b < permutations; b++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

                // Shuffle perm in-place (uniform random permutation)
                for (int i = n - 1; i > 0; i--) {
                    int j = rng.nextInt(i + 1);
                    int t = perm[i];
                    perm[i] = perm[j];
                    perm[j] = t;
                }

                // General (works even if X/Y later become multi-column):
                SimpleMatrix Cb = covWithPermutedB(RX, RY, perm);
                double statB = n * frob2(Cb);

                if (statB >= stat) greater++;
            }

            p = (greater + 1.0) / (permutations + 1.0);

        } else {
            // ----------------------------------------------------------------
            // Analytic approximations (Gamma / HBE / LPB4)
            // ----------------------------------------------------------------
            SimpleMatrix Cov = kronResCov(RX, RY);
            double[] eig = positiveEigs(Cov);

            p = switch (approx) {
                case GAMMA -> QuadraticFormPValues.gammaSatterthwaiteP(stat, eig);
                case SADDLEPOINT -> QuadraticFormPValues.saddlepointLugannaniRiceP(stat, eig);// edgeworthP(stat, eig, true);
                case DAVIES_IMHOF -> QuadraticFormPValues.daviesP(stat, eig);
                default -> QuadraticFormPValues.gammaSatterthwaiteP(stat, eig);
            };
        }

        p = Math.min(1.0, Math.max(0.0, p));
        lastP = p;

        IndependenceFact fact = new IndependenceFact(x, y, new HashSet<>(Z));

        if (verbose) {
            TetradLogger.getInstance().log(fact + " p=" + p
                    + " stat=" + stat
                    + " approx=" + approx
                    + (approx == Approx.PERMUTATION ? (" perms=" + permutations) : ""));
        }

        return new IndependenceResult(
                fact,
                p > alpha, p, alpha - p
        );
    }

    @Override
    public List<Integer> getRows() {
        return rows;
    }

    @Override
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            this.rows = null;
            this.n = data.getNumRows();
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

    private SimpleMatrix col(Node v) {
        int c = data.getColumn(v);
        SimpleMatrix M = new SimpleMatrix(n, 1);
        for (int i = 0; i < n; i++) {
            int row = activeRowIndex(i);
            M.set(i, 0, data.getDouble(row, c));
        }
        return M;
    }

    private SimpleMatrix cols(List<Node> vs) {
        SimpleMatrix M = new SimpleMatrix(n, vs.size());
        for (int j = 0; j < vs.size(); j++) {
            int c = data.getColumn(vs.get(j));
            for (int i = 0; i < n; i++) {
                int row = activeRowIndex(i);
                M.set(i, j, data.getDouble(row, c));
            }
        }
        return M;
    }

    public void setLambda(double lambda) {
        this.lambda = lambda;
        invalidateFeatureCache();
    }

    public void setApprox(Approx approx) {
        if (approx == null) throw new NullPointerException("approx");
        this.approx = approx;
    }

    public void setPermutations(int permutations) {
        this.permutations = permutations;
    }

    public void setNumFeatXY(int numFeatXY) {
        this.numFeatXY = numFeatXY;
        invalidateFeatureCache();
    }

    public void setNumFeatZ(int numFeatZ) {
        this.numFeatZ = numFeatZ;
        invalidateFeatureCache();
    }

    public void setFeatureType(FeatureType featureType) {
        this.featureType = featureType;
        invalidateFeatureCache();
    }

    public void setBwMaxRows(int bwMaxRows) {
        this.bwMaxRows = bwMaxRows;
        invalidateFeatureCache();
    }

    public void setSeed(long seed) {
        this.seed = seed;
        invalidateFeatureCache();
    }

    /**
     * Builds RFF/ORF features for the block of variables 'vs'.
     * <p>
     * - Extracts raw continuous columns for vars in vs over the active rows
     * - z-scores raw columns
     * - chooses bw2 using median pairwise distance^2 (continuous only)
     * - maps to RFF or ORF features for an RBF kernel
     * - centers feature columns (so cov(A,B) = A^T B/(n-1) is correct after centering)
     */
    private SimpleMatrix rffOrOrfFeaturesFor(List<Node> vs, int mFeatures, String tag) {
        Objects.requireNonNull(vs, "vs");
        if (mFeatures <= 0) return new SimpleMatrix(getActiveRowCount(), 0);

        // Cache key: depends on vars, active rows, m, bw knobs, feature type, seed, tag
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

        // Center features (your cov() assumes centered).
        subtractColumnMeansInPlace(M);

        featCache.put(key, M);
        return M;
    }

    /**
     * Build cache key for a feature block.
     */
    private String featKey(String tag, List<Node> vs, int mFeatures) {
        ArrayList<String> names = new ArrayList<>(vs.size());
        for (Node v : vs) names.add(v.getName());
        names.sort(String::compareTo);

        StringBuilder sb = new StringBuilder(160);
        sb.append("RFFORF|tag=").append(tag)
                .append("|n=").append(getActiveRowCount())
                .append("|rowsHash=").append(activeRowsHash())
                .append("|m=").append(mFeatures)
                .append("|ft=").append(featureType.name())
                .append("|bwMult=").append(Double.doubleToLongBits(bandwidthMultiplier))
                .append("|bwMax=").append(bwMaxRows)
                .append("|seed=").append(seed)
                .append("|vars=");
        for (String s : names) sb.append(s).append(",");
        return sb.toString();
    }

    /**
     * Deterministic seed for a block; makes results stable across runs.
     */
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

    /**
     * Extract raw data for vars in vs over the active rows.
     * Assumes all variables are continuous; if you later want mixed, handle discrete elsewhere.
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
                int row = activeRowIndex(i); // uses RowsSettable if you have it; see helpers below
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
    // RowsSettable hooks (use these if you have RowsSettable restored)
    // If you don't have row-selection yet, you can simplify:
    //   getActiveRowCount() -> data.getNumRows()
    //   activeRowIndex(i)   -> i
    //   activeRowsHash()    -> 0
    // --------------------------------------------------------------------

    @Override
    public List<Node> getVariables() {
        return vars;
    }

    // --------------------------------------------------------------------

    @Override
    public double getAlpha() {
        return alpha;
    }

    @Override
    public void setAlpha(double a) {
        alpha = a;
        invalidateFeatureCache();
    }

    @Override
    public DataSet getData() {
        return data;
    }

    public double getPValue() {
        return lastP;
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void setVerbose(boolean v) {
        verbose = v;
    }

    public void setApproximation(Approx approx) {
        this.approx = approx;
        invalidateFeatureCache();
    }

    public enum FeatureType {RFF, ORF}

    public enum Approx {GAMMA, SADDLEPOINT, DAVIES_IMHOF, PERMUTATION}
}

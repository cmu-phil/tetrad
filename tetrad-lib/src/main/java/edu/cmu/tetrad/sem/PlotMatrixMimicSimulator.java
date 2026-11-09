package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.distribution.NormalDistribution;
import edu.cmu.tetrad.util.TMath;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;

import java.util.*;
import java.util.stream.IntStream;

/**
 * PlotMatrixMimicSimulator (continuous-only, v1)
 *
 * <p>Goal: given a continuous dataset X (n x p), simulate a new dataset X' (m x p)
 * such that:</p>
 * <ul>
 *   <li>Each marginal distribution is approximately preserved (histograms match closely)
 *       via inverse empirical CDF mapping.</li>
 *   <li>Pairwise dependence structure is mimicked via a Gaussian copula:
 *       we compute normal-scores Z from ranks, estimate Corr(Z), then sample
 *       multivariate normal with that correlation and map back through empirical quantiles.</li>
 * </ul>
 *
 * <p>Optional "novel mechanism": we can apply a mild nonlinear distortion in latent
 * Gaussian space (still mapped back through empirical marginals), which changes the
 * joint in a controlled way while keeping histograms identical.</p>
 *
 * <p>Notes:</p>
 * <ul>
 *   <li>Continuous-only for now (throws if any DiscreteVariable is present).</li>
 *   <li>Missing values are handled by listwise deletion across all columns by default.</li>
 *   <li>Correlation matrix is regularized with diagonal jitter if Cholesky fails.</li>
 * </ul>
 *
 * <p>Typical usage:</p>
 * <pre>
 *   PlotMatrixMimicSimulator sim = new PlotMatrixMimicSimulator(data);
 *   sim.setNoveltyStrength(0.15);   // 0.0 = off
 *   DataSet fake = sim.simulate(2000, 12345L);
 * </pre>
 */
public final class PlotMatrixMimicSimulator {

    // ---------------- configuration ----------------

    private static final NormalDistribution STD_NORMAL = new NormalDistribution(0, 1);
    private final List<Node> variables;
    private final int p;
    /**
     * Clean data used for fitting (nClean x p).
     */
    private final double[][] X;

    // ---------------- learned state ----------------
    /**
     * nClean
     */
    private final int n;
    /**
     * For each variable j: sorted observed values (length n).
     */
    private final double[][] sortedVals;
    /**
     * Estimated correlation of normal-scores (p x p).
     */
    private final DMatrixRMaj R;
    /**
     * 0.0 = no novelty (pure Gaussian-copula mimic).
     * >0 adds a mild nonlinear distortion in latent space, keeping marginals fixed.
     * Reasonable range: 0.0 .. 0.25
     */
    private double noveltyStrength = 0.0;
    /**
     * If Cholesky fails, add jitter * I and retry.
     */
    private double cholJitter = 1e-8;
    /**
     * Max retries for Cholesky with increasing jitter.
     */
    private int cholRetries = 8;
    /**
     * If true, listwise-delete rows with any NaN.
     */
    private boolean listwiseDeleteMissing = true;

    // ---------------- construction ----------------

    /**
     * Constructs a new instance of PlotMatrixMimicSimulator, designed for processing continuous-only
     * datasets and generating matrices used in copula normal-score simulations. This constructor
     * validates the input dataset and preprocesses it for further computations, such as empirical CDF
     * mappings and rank-based transformations.
     *
     * @param data the dataset to be used for the simulation. It must contain only continuous variables,
     *             and cannot be null. The dataset should have at least 10 rows after optional
     *             listwise deletion of rows with missing values.
     * @throws NullPointerException if the provided dataset is null.
     * @throws IllegalArgumentException if the dataset contains any non-continuous variables,
     *                                  or has fewer than 10 rows after preprocessing.
     */
    public PlotMatrixMimicSimulator(DataSet data) {
        Objects.requireNonNull(data, "data");

        this.variables = data.getVariables();
        this.p = variables.size();

        // Continuous-only check
        for (Node v : variables) {
            if (!(v instanceof ContinuousVariable)) {
                throw new IllegalArgumentException(
                        "PlotMatrixMimicSimulator v1 is continuous-only; found non-continuous: " + v.getName());
            }
        }

        // Extract raw matrix
        double[][] raw = new double[data.getNumRows()][p];
        for (int r = 0; r < data.getNumRows(); r++) {
            for (int j = 0; j < p; j++) {
                raw[r][j] = data.getDouble(r, j);
            }
        }

        // Clean rows
        this.X = listwiseDeleteMissing ? listwiseDelete(raw) : raw;
        this.n = X.length;
        if (n < 10) throw new IllegalArgumentException("Too few rows after cleaning: n=" + n);

        // Sorted values for inverse empirical CDF
        this.sortedVals = new double[p][n];
        for (int j = 0; j < p; j++) {
            double[] col = new double[n];
            for (int i = 0; i < n; i++) col[i] = X[i][j];
            Arrays.sort(col);
            sortedVals[j] = col;
        }

        // Normal-scores Z via ranks, then correlation
        double[][] Z = normalScoresFromRanks(X);   // n x p
        this.R = corrMatrix(Z);                    // p x p

        // Ensure diagonal exactly 1
        for (int j = 0; j < p; j++) R.set(j, j, 1.0);

        // Symmetrize defensively
        symmetrizeInPlace(R);
    }

    // ---------------- public API ----------------

    private static double[][] normalScoresFromRanks(double[][] X) {
        int n = X.length;
        int p = X[0].length;
        double[][] Z = new double[n][p];

        for (int j = 0; j < p; j++) {
            double[] col = new double[n];
            for (int i = 0; i < n; i++) col[i] = X[i][j];

            int[] rank = ranksAverageTies(col); // 1..n (with ties averaged)
            for (int i = 0; i < n; i++) {
                // Blom-type plotting position: (r - 0.5)/n
                double u = (rank[i] - 0.5) / n;
                // Convert to normal scores
                Z[i][j] = STD_NORMAL.inverseCumulativeProbability(u);
            }

            // Standardize (mean 0, var 1) to stabilize corr
            standardizeInPlace(getColumnView(Z, j));
        }

        return Z;
    }

    private static DMatrixRMaj corrMatrix(double[][] Z) {
        int n = Z.length;
        int p = Z[0].length;

        // Compute means
        double[] mean = new double[p];
        for (int j = 0; j < p; j++) {
            double s = 0;
            for (int i = 0; i < n; i++) s += Z[i][j];
            mean[j] = s / n;
        }

        // Compute covariance then convert to correlation
        DMatrixRMaj C = new DMatrixRMaj(p, p);
        double[] var = new double[p];

        for (int a = 0; a < p; a++) {
            double sa = 0;
            for (int i = 0; i < n; i++) {
                double da = Z[i][a] - mean[a];
                sa += da * da;
            }
            var[a] = sa / (n - 1.0);
        }

        for (int a = 0; a < p; a++) {
            for (int b = 0; b <= a; b++) {
                double s = 0;
                for (int i = 0; i < n; i++) {
                    double da = Z[i][a] - mean[a];
                    double db = Z[i][b] - mean[b];
                    s += da * db;
                }
                double cov = s / (n - 1.0);
                double denom = TMath.sqrt(TMath.max(1e-18, var[a] * var[b]));
                double corr = cov / denom;
                // clamp a hair to avoid numerical >1
                corr = TMath.max(-0.999999, TMath.min(0.999999, corr));
                C.set(a, b, corr);
            }
        }

        // mirror
        for (int a = 0; a < p; a++) {
            for (int b = 0; b < a; b++) C.set(b, a, C.get(a, b));
        }

        // diagonal
        for (int j = 0; j < p; j++) C.set(j, j, 1.0);

        return C;
    }

    /**
     * Mildly alters the copula (tail/shape) while leaving marginals fixed after inverse-CDF mapping.
     * <p>
     * We distort each coordinate by a random power transform:
     * z <- sign(z) * |z|^q
     * with q in [1 - s, 1 + s], then re-standardize per column.
     */
    private static void applyLatentNovelty(double[][] Z, long seed, double s) {
        int n = Z.length;
        int p = Z[0].length;

        for (int j = 0; j < p; j++) {
            double q = 1.0 + (2.0 * RandomUtil.getInstance().nextDouble() - 1.0) * s; // [1-s, 1+s]
            if (q < 0.2) q = 0.2;

            // transform
            for (int i = 0; i < n; i++) {
                double z = Z[i][j];
                double a = TMath.abs(z);
                double t = TMath.pow(a, q);
                Z[i][j] = TMath.copySign(t, z);
            }

            // re-standardize
            double[] col = getColumnView(Z, j);
            standardizeInPlace(col);

            // write back (since getColumnView allocates)
            for (int i = 0; i < n; i++) Z[i][j] = col[i];
        }
    }

    /**
     * Inverse empirical CDF using sorted values with linear interpolation.
     * sorted.length must be >= 2.
     */
    private static double empiricalQuantile(double[] sorted, double u) {
        int n = sorted.length;
        if (n == 1) return sorted[0];

        double x = u * (n - 1);
        int i = (int) TMath.floor(x);
        int j = TMath.min(n - 1, i + 1);
        double t = x - i;

        double a = sorted[i];
        double b = sorted[j];
        return a + t * (b - a);
    }

    private static double[][] listwiseDelete(double[][] raw) {
        int n = raw.length;
        int p = raw[0].length;

        int[] keep = new int[n];
        int m = 0;

        outer:
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                if (Double.isNaN(raw[i][j]) || !Double.isFinite(raw[i][j])) continue outer;
            }
            keep[m++] = i;
        }

        double[][] out = new double[m][p];
        for (int r = 0; r < m; r++) {
            out[r] = Arrays.copyOf(raw[keep[r]], p);
        }
        return out;
    }

    /**
     * Returns integer "average tie ranks" scaled by 1 (still integer by rounding).
     * For copula normal-scores we mainly need an order-preserving mapping; this is fine.
     * <p>
     * Output ranks are in 1..n.
     */
    private static int[] ranksAverageTies(double[] x) {
        int n = x.length;
        Integer[] idx = IntStream.range(0, n).boxed().toArray(Integer[]::new);
        Arrays.sort(idx, Comparator.comparingDouble(i -> x[i]));

        int[] r = new int[n];

        int pos = 0;
        while (pos < n) {
            int start = pos;
            double v = x[idx[pos]];
            while (pos + 1 < n && Double.compare(x[idx[pos + 1]], v) == 0) pos++;
            int end = pos;

            // average rank in [start+1, end+1]
            double avg = (start + 1 + end + 1) / 2.0;
            int avgInt = (int) TMath.rint(avg);

            for (int k = start; k <= end; k++) {
                r[idx[k]] = avgInt;
            }
            pos++;
        }

        return r;
    }

    // ---------------- core: copula fitting ----------------

    private static void standardizeInPlace(double[] x) {
        int n = x.length;
        double mean = 0.0;
        for (double v : x) mean += v;
        mean /= n;

        double ss = 0.0;
        for (int i = 0; i < n; i++) {
            x[i] -= mean;
            ss += x[i] * x[i];
        }
        double var = ss / TMath.max(1, (n - 1));
        double sd = TMath.sqrt(TMath.max(1e-12, var));
        for (int i = 0; i < n; i++) x[i] /= sd;
    }

    // ---------------- sampling ----------------

    private static void symmetrizeInPlace(DMatrixRMaj A) {
        int n = A.numRows;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double v = 0.5 * (A.get(i, j) + A.get(j, i));
                A.set(i, j, v);
                A.set(j, i, v);
            }
        }
    }

    // ---------------- novelty in latent space ----------------

    private static double[] getColumnView(double[][] M, int col) {
        int n = M.length;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = M[i][col];
        return out;
    }

    // ---------------- inverse empirical CDF ----------------

    private static DataSet toDataSet(double[][] X, List<Node> varsTemplate) {
        int n = X.length;
        int p = X[0].length;

        List<Node> vars = new ArrayList<>(p);
        for (int j = 0; j < p; j++) {
            vars.add(new ContinuousVariable(varsTemplate.get(j).getName()));
        }

        DoubleDataBox box = new DoubleDataBox(X);
        return new BoxDataSet(box, vars);
    }

    // ---------------- helpers: missingness cleaning ----------------

    /**
     * Getter for noveltyStrength.
     * @return the novelty strength parameter
     */
    public double getNoveltyStrength() {
        return noveltyStrength;
    }

    // ---------------- helpers: ranking with ties ----------------

    /**
     * Setter for noveltyStrength.
     * @param noveltyStrength the novelty strength parameter
     */
    public void setNoveltyStrength(double noveltyStrength) {
        if (!(noveltyStrength >= 0.0) || !Double.isFinite(noveltyStrength)) {
            throw new IllegalArgumentException("noveltyStrength must be finite and >= 0");
        }
        this.noveltyStrength = noveltyStrength;
    }

    // ---------------- helpers: numeric ----------------

    /**
     * Setter for listwiseDeleteMissing.
     * @param listwiseDeleteMissing the listwise delete missing flag
     */
    public void setListwiseDeleteMissing(boolean listwiseDeleteMissing) {
        this.listwiseDeleteMissing = listwiseDeleteMissing;
    }

    /**
     * Getter for cholJitter.
     * @param cholJitter the jitter parameter
     */
    public void setCholJitter(double cholJitter) {
        if (!(cholJitter > 0) || !Double.isFinite(cholJitter)) {
            throw new IllegalArgumentException("cholJitter must be finite and > 0");
        }
        this.cholJitter = cholJitter;
    }

    /**
     * Sets the maximum number of retries allowed for the Cholesky decomposition process.
     * The provided value is constrained to a minimum of 1 to ensure at least one retry is performed.
     *
     * @param cholRetries the maximum number of retries for the Cholesky decomposition; must be a positive integer.
     */
    public void setCholRetries(int cholRetries) {
        this.cholRetries = TMath.max(1, cholRetries);
    }

    /**
     * Simulates a synthetic dataset by sampling from a multivariate normal distribution,
     * applying optional nonlinear distortions, and mapping the latent variables to real values
     * through inverse empirical cumulative distribution functions.
     *
     * @param m the number of samples to generate; must be greater than or equal to 1
     * @param seed the seed for the random number generator to ensure reproducibility
     * @return a DataSet containing the simulated data
     * @throws IllegalArgumentException if the specified number of samples (m) is less than 1
     */
    public DataSet simulate(int m, long seed) {
        if (m < 1) throw new IllegalArgumentException("m must be >= 1");

        // 1) Sample latent Z' ~ N(0, R)
        double[][] Znew = sampleMVN(m, R, seed);

        // 2) Optional novelty: mild nonlinear distortion in latent space (keeps mean/var ~ 0/1)
        if (noveltyStrength > 0) {
            applyLatentNovelty(Znew, seed ^ 0x9E3779B97F4A7C15L, noveltyStrength);
        }

        // 3) Map Z -> U via Phi, then U -> X via inverse empirical CDF
        double[][] Xnew = new double[m][p];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < p; j++) {
                double u = STD_NORMAL.cumulativeProbability(Znew[i][j]);
                // clamp to avoid numerical edge cases
                if (u <= 0) u = 1e-12;
                if (u >= 1) u = 1 - 1e-12;
                Xnew[i][j] = empiricalQuantile(sortedVals[j], u);
            }
        }

        // 4) Return as Tetrad DataSet
        return toDataSet(Xnew, variables);
    }

    private double[][] sampleMVN(int m, DMatrixRMaj R, long seed) {
        int p = R.numRows;
        DMatrixRMaj A = R.copy();

        // Cholesky with jitter retries
        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);

        double jitter = cholJitter;
        boolean ok = chol.decompose(A);
        int tries = 0;

        while (!ok && tries < cholRetries) {
            tries++;
            A = R.copy();
            for (int j = 0; j < p; j++) A.add(j, j, jitter);
            ok = chol.decompose(A);
            jitter *= 10.0;
        }

        if (!ok) {
            throw new IllegalStateException("Cholesky failed even after jitter retries; R may be badly non-PSD.");
        }

        DMatrixRMaj L = chol.getT(null); // lower-tri

        double[][] Z = new double[m][p];

        // For each row: g ~ N(0,I), z = L g
        double[] g = new double[p];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < p; j++) g[j] = RandomUtil.getInstance().nextGaussian();

            for (int a = 0; a < p; a++) {
                double s = 0;
                for (int b = 0; b <= a; b++) {
                    s += L.get(a, b) * g[b];
                }
                Z[i][a] = s;
            }
        }

        return Z;
    }
}
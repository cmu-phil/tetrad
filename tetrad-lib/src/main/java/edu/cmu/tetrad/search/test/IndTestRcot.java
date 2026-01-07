package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

import static java.lang.Double.NaN;

/**
 * RCoT (Randomized Conditional Correlation Test) using Random Fourier Features + residualization + CCA.
 *
 * Mirrors the IndTestRcit interface shape but uses a "true" RCoT-style statistic:
 *  - Build RFF features fX, fY, fZ.
 *  - Residualize fX and fY on fZ with ridge regression (projection).
 *  - Compute canonical correlations between residuals via CCA.
 *  - Wilks' Lambda with Bartlett chi-square approximation yields p-value.
 *
 * References (conceptual lineage):
 *  - RCoT family: random feature maps + conditional correlation / CCA on residuals.
 *  - Uses the same RFF & bandwidth median heuristic style as IndTestRcit.
 *
 * Notes:
 *  - If Z is empty, reduces to unconditional CCA test on (fX, fY).
 *  - Continuous only (DataSet.getDouble).
 */
public final class IndTestRcot implements IndependenceTest, RowsSettable {

    // ---------------- core data ----------------
    private final DataSet data;
    private final List<Node> vars;
    private int n;
    private final Random rng;

    // ---------------- hyperparams ----------------
    private int numFeatXY = 5;       // features for X and Y (keep default aligned with IndTestRcit)
    private int numFeatZ  = 100;     // features for Z
    private double lambda = 1e-10;   // ridge for Z projection + covariance stabilization
    private boolean centerFeatures = true;

    // ---------------- IndependenceTest state ----------------
    private double alpha = 0.05;
    private double lastP = NaN;
    private List<Integer> rows = null;  // null => all rows
    private boolean verbose = false;

    /**
     * Constructs an instance of IndTestRcot with default parameters.
     *
     * @param dataSet the dataset; must not be null
     */
    public IndTestRcot(DataSet dataSet) {
        this(dataSet, new Parameters());
    }

    /**
     * Constructs an instance of IndTestRcot with Parameters (legacy keys match IndTestRcit style).
     *
     * Supported keys (same prefix style as rcit to make wrapper wiring easy):
     *  - rcit.seed
     *  - rcit.numF2  (XY features)
     *  - rcit.numF   (Z features)
     *  - rcit.lambda
     *  - rcit.centerFeatures
     *
     * @param dataSet the dataset; must not be null
     * @param params  parameters; must not be null
     */
    public IndTestRcot(DataSet dataSet, Parameters params) {
        this.data = Objects.requireNonNull(dataSet, "data");
        this.vars = Collections.unmodifiableList(new ArrayList<>(dataSet.getVariables()));
        this.n = getActiveRowCount();

        long seed = params.getLong("rcit.seed", 1729L);
        this.rng = new Random(seed);

        // mirror legacy names used by IndTestRcit
        this.numFeatZ  = Math.max(1, params.getInt("rcit.numF", 100));
        this.numFeatXY = Math.max(1, params.getInt("rcit.numF2", 5));
        this.lambda = Math.max(1e-12, params.getDouble("rcit.lambda", this.lambda));
        this.centerFeatures = params.getBoolean("rcit.centerFeatures", true);
    }

    // ---------------- setters for wrapper wiring ----------------

    public void setLambda(double lambda) {
        this.lambda = Math.max(1e-12, lambda);
    }

    public void setCenterFeatures(boolean centerFeatures) {
        this.centerFeatures = centerFeatures;
    }

    public void setNumFeaturesXY(int d) {
        this.numFeatXY = Math.max(1, d);
    }

    public void setNumFeaturesZ(int d) {
        this.numFeatZ = Math.max(1, d);
    }

    public void setSeed(long seed) {
        this.rng.setSeed(seed);
    }

    // ---------------- core matrix helpers (mirrors IndTestRcit style) ----------------

    private SimpleMatrix cols(DataSet ds, List<Node> vv) {
        int n = getActiveRowCount();
        int d = vv.size();
        SimpleMatrix M = new SimpleMatrix(n, d);

        for (int j = 0; j < d; j++) {
            int col = ds.getColumn(vv.get(j));
            if (col < 0) {
                col = ds.getVariableNames().indexOf(vv.get(j).getName());
                if (col < 0) throw new IllegalArgumentException("Variable not found: " + vv.get(j).getName());
            }
            for (int i = 0; i < n; i++) {
                int row = activeRowIndex(i);
                M.set(i, j, ds.getDouble(row, col));
            }
        }
        return M;
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
            for (int i = 0; i < n; i++) M.set(i, j, (M.get(i, j) - mean) / sd);
        }
    }

    /**
     * cov(A) = A^T A / (n-1), assumes column-centered (zscored).
     */
    private static SimpleMatrix cov(SimpleMatrix A) {
        int n = A.getNumRows();
        return A.transpose().mult(A).scale(1.0 / (n - 1));
    }

    /**
     * cov(A,B) = A^T B / (n-1), assumes column-centered (zscored).
     */
    private static SimpleMatrix cov(SimpleMatrix A, SimpleMatrix B) {
        int n = A.getNumRows();
        return A.transpose().mult(B).scale(1.0 / (n - 1));
    }

    /**
     * Random Fourier Features for RBF: sqrt(2)*cos(W X^T + b), with W ~ N(0, 1/σ).
     */
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

    /**
     * Median pairwise Euclidean distance (ignoring zeros).
     */
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

    /**
     * Residualize A on Z (ridge): R = A - Z * ( (Z'Z + λI)^-1 Z' A ).
     */
    private static SimpleMatrix residualizeOnZ(SimpleMatrix A, SimpleMatrix Z, double lambda) {
        int n = Z.getNumRows();
        int dz = Z.getNumCols();
        if (dz == 0) return A;

        // G = Z'Z/(n-1) + λI
        SimpleMatrix G = cov(Z).plus(SimpleMatrix.identity(dz).scale(lambda));
        SimpleMatrix iG = G.pseudoInverse();

        // Beta = iG * cov(Z,A)   (dz x da)
        SimpleMatrix ZA = cov(Z, A);        // dz x da
        SimpleMatrix Beta = iG.mult(ZA);    // dz x da

        // Fit = Z * Beta  (n x da)
        SimpleMatrix Fit = Z.mult(Beta);

        return A.minus(Fit);
    }

    /**
     * Computes Wilks' Lambda p-value via Bartlett's chi-square approximation for CCA between RX and RY.
     *
     * Steps:
     *  - Sxx, Syy, Sxy (ridge-stabilized)
     *  - M = inv(Sxx) Sxy inv(Syy) Syx ; eigenvalues are r_i^2
     *  - Lambda = Π (1 - r_i^2)
     *  - T = -c ln(Lambda),  c = (n - 1) - (p+q+1)/2,  df = p*q
     */
    private static double pValueWilksBartlett(SimpleMatrix RX, SimpleMatrix RY, double ridge) {
        int n = RX.getNumRows();
        int p = RX.getNumCols();
        int q = RY.getNumCols();
        if (n < 5 || p < 1 || q < 1) return 1.0;

        // Sxx, Syy, Sxy (use cov = / (n-1))
        SimpleMatrix Sxx = cov(RX).plus(SimpleMatrix.identity(p).scale(ridge));
        SimpleMatrix Syy = cov(RY).plus(SimpleMatrix.identity(q).scale(ridge));
        SimpleMatrix Sxy = cov(RX, RY); // p x q
        SimpleMatrix Syx = Sxy.transpose();

        // A = inv(Sxx) Sxy ; B = inv(Syy) Syx ; M = A B
        SimpleMatrix A = Sxx.pseudoInverse().mult(Sxy); // p x q
        SimpleMatrix B = Syy.pseudoInverse().mult(Syx); // q x p
        SimpleMatrix M = A.mult(B);                      // p x p

        // eigenvalues of M are r^2 (clamp)
        SimpleEVD<SimpleMatrix> evd = M.eig();
        int m = evd.getNumberOfEigenvalues();
        ArrayList<Double> r2 = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            double val = evd.getEigenvalue(i).getReal();
            if (!Double.isFinite(val)) continue;
            if (val < 0) val = 0;
            if (val > 1) val = 1;
            r2.add(val);
        }
        if (r2.isEmpty()) return 1.0;

        // take k = min(p,q) largest r^2
        r2.sort(Double::compareTo);
        int k = Math.min(p, q);
        double logLambda = 0.0;
        for (int t = 0; t < k; t++) {
            double v = r2.get(r2.size() - 1 - t);
            double oneMinus = 1.0 - v;
            if (oneMinus <= 1e-12) oneMinus = 1e-12;
            logLambda += Math.log(oneMinus);
        }

        double c = (n - 1.0) - 0.5 * (p + q + 1.0);
        if (c <= 1e-6) c = 1e-6;

        double T = -c * logLambda;
        double df = (double) p * (double) q;
        if (df <= 0) return 1.0;

        ChiSquaredDistribution chi2 = new ChiSquaredDistribution(df);
        double pval = 1.0 - chi2.cumulativeProbability(T);
        if (pval < 0) pval = 0;
        if (pval > 1) pval = 1;
        return pval;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    // ---------------- IndependenceTest ----------------

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");

        this.n = getActiveRowCount();
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
        SimpleMatrix X  = cols(data, Collections.singletonList(x)); // n x 1
        SimpleMatrix Y  = cols(data, Collections.singletonList(y)); // n x 1
        SimpleMatrix Zm = Z.isEmpty() ? new SimpleMatrix(n, 0) : cols(data, Z); // n x |Z|

        // Standardize raw columns (matches IndTestRcit behavior)
        zscoreInPlace(X);
        zscoreInPlace(Y);
        zscoreInPlace(Zm);

        // Bandwidths via median pairwise distance on first r1 rows
        int r1 = Math.min(n, 500);
        double sigX = medianPairwiseDistance(X.rows(0, r1));
        double sigY = medianPairwiseDistance(Y.rows(0, r1));
        double sigZ = (Zm.getNumCols() == 0) ? 1.0 : medianPairwiseDistance(Zm.rows(0, r1));

        // Random Fourier Features
        SimpleMatrix fX = rff(X,  numFeatXY, sigX, rng);                  // n x Fx
        SimpleMatrix fY = rff(Y,  numFeatXY, sigY, rng);                  // n x Fy
        SimpleMatrix fZ = (Zm.getNumCols() == 0) ? null : rff(Zm, numFeatZ, sigZ, rng); // n x Fz

        if (centerFeatures) {
            zscoreInPlace(fX);
            zscoreInPlace(fY);
            if (fZ != null) zscoreInPlace(fZ);
        }

        // Residualize on Z (true RCoT step)
        SimpleMatrix RX = (fZ == null) ? fX : residualizeOnZ(fX, fZ, lambda);
        SimpleMatrix RY = (fZ == null) ? fY : residualizeOnZ(fY, fZ, lambda);

        // CCA / Wilks / Bartlett p-value
        double p = pValueWilksBartlett(RX, RY, lambda);

        if (verbose) {
            TetradLogger.getInstance().log(new IndependenceFact(x, y, new HashSet<>(Z)) + " p = " + p);
        }

        lastP = clamp01(p);
        boolean indep = (lastP > alpha);
        return new IndependenceResult(new IndependenceFact(x, y, new HashSet<>(Z)), indep, lastP, alpha - lastP);
    }

    // ---------------- RowsSettable ----------------

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
    }

    private int getActiveRowCount() {
        return (rows == null) ? data.getNumRows() : rows.size();
    }

    private int activeRowIndex(int i) {
        return (rows == null) ? i : rows.get(i);
    }

    // ---------------- IndependenceTest misc ----------------

    public double getPValue() {
        return lastP;
    }

    @Override
    public List<Node> getVariables() {
        return vars;
    }

    @Override
    public double getAlpha() {
        return alpha;
    }

    @Override
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha in (0,1)");
        this.alpha = alpha;
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
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public String toString() {
        return "RCoT (RFF + residualization + CCA/Wilks)";
    }
}
package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;

import java.util.*;

/**
 * NG-SEM-BIC local score for continuous variables.
 *
 * Idea (Option B):
 *   1) Fit OLS (optionally ridge-stabilized) regression of X_j on parents X_S.
 *   2) Compute residuals e = y - X beta.
 *   3) Score residuals under a non-Gaussian noise likelihood (default Laplace).
 *   4) Add a BIC-style penalty ~ df * log(n).
 *
 * Higher is better (Tetrad score convention).
 *
 * Notes:
 * - If centerData==true, y and each parent column are z-scored; then we omit the intercept.
 * - If centerData==false, we include an intercept column by default (recommended if you turn off centering).
 * - Missingness: testwise deletion on {target} ∪ parents.
 */
public final class NgSemBicScore implements Score, EffectiveSampleSizeSettable {

    // -------------------- data --------------------
    private final DataSet dataSet;
    private final List<Node> variables;
    private final int sampleSize;
    private final boolean calculateRowSubsets;

    /** cols[varIndex][row] (may contain NaNs). */
    private final double[][] cols;

    /** Effective sample size (defaults to sampleSize). */
    private int nEff;

    /** Degrees of freedom for Student-t residuals (fixed). */
    private double studentTNu = 4.0;

    /** Strength of kurtosis reward. Typical starting values: 0.01 to 0.2. */
    private double kurtosisGamma = 0.05;

    /** Optional: cap |excess kurtosis| to prevent a single outlier from dominating. */
    private double kurtosisCap = 10.0; // set <=0 to disable capping


    /** Optional score cache. */
    private final Map<Long, Double> localScoreCache = new HashMap<>();

    // -------------------- knobs --------------------

    /** Penalty discount multiplier (like SemBicScore). */
    private double penaltyDiscount = 1.0;

    /** If true, z-score y and each parent column; if false, we usually include intercept. */
    private boolean centerData = true;

    /** If centerData==false, include intercept column (recommended). */
    private boolean includeInterceptWhenNotCentered = true;

    /**
     * Ridge stabilizer added to XtX diagonal.
     * Set to 0 for pure OLS; small positive helps when XtX is ill-conditioned.
     */
    private double ridge = 1e-8;

    /** Small clamp to avoid log(0) or division by 0 for scale MLE. */
    private double minScale = 1e-12;

    /** Residual noise model. Start with Laplace; we can add StudentT later. */
    private NoiseModel noiseModel = NoiseModel.LAPLACE;

    // -------------------- construction --------------------

    public NgSemBicScore(DataSet dataSet) {
        this.dataSet = Objects.requireNonNull(dataSet, "dataSet");
        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        setEffectiveSampleSize(-1);

        this.calculateRowSubsets = dataSet.existsMissingValue();

        int p = variables.size();
        this.cols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            for (int r = 0; r < sampleSize; r++) {
                cols[j][r] = dataSet.getDouble(r, j);
            }
        }
    }

    // -------------------- Score interface --------------------

    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScore(int target, int... parents) {
        Arrays.sort(parents);

        long key = cacheKey(target, parents);
        Double cached = localScoreCache.get(key);
        if (cached != null) return cached;

        int[] all = concat(target, parents);
        int[] rows = calculateRowSubsets ? validRows(all) : null;

        int n = (rows == null) ? nEff : rows.length;
        if (n < 10) {
            localScoreCache.put(key, Double.NaN);
            return Double.NaN;
        }

        // y
        double[] y = extract1D(target, rows, n);

        final int p = parents.length;

        // No parents case: just score y as noise (after optional centering).
        if (p == 0) {
            double[] yy = y.clone();
            if (centerData) zscoreInPlace(yy);

            double ll = residualLogLik(yy);
            // df: just noise scale (and maybe intercept, but if p=0 and centerData==false, intercept is the location;
            // we’re not estimating a separate location here. Keep it simple: count 1 scale parameter.)
            double df = noiseDf();
            double score = 2.0 * ll - penaltyDiscount * df * Math.log(n);
            localScoreCache.put(key, score);
            return score;
        }

        // Build design matrix X (n x m)
        // If centerData==true: no intercept column.
        // If centerData==false and includeInterceptWhenNotCentered==true: include intercept.
        final boolean useIntercept = (!centerData) && includeInterceptWhenNotCentered;
        final int m = p + (useIntercept ? 1 : 0);

        double[][] Xraw = extractND(parents, rows, n, p);

        // Optionally standardize y and X columns
        double[] yy = y.clone();
        double[][] XX = Xraw;

        if (centerData) {
            zscoreInPlace(yy);
            zscoreInPlace(XX);
        }

        // Convert to EJML DMatrixRMaj X (n x m)
        DMatrixRMaj X = new DMatrixRMaj(n, m);
        int col = 0;
        if (useIntercept) {
            for (int i = 0; i < n; i++) X.set(i, col, 1.0);
            col++;
        }
        for (int j = 0; j < p; j++, col++) {
            for (int i = 0; i < n; i++) X.set(i, col, XX[i][j]);
        }

        // Solve (X^T X + ridge I) beta = X^T y
        DMatrixRMaj XtX = new DMatrixRMaj(m, m);
        CommonOps_DDRM.multTransA(X, X, XtX);
        if (ridge > 0) addDiagonalInPlace(XtX, ridge);

        DMatrixRMaj Xty = new DMatrixRMaj(m, 1);
        multTransA_vec(X, yy, Xty);

        LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.symmPosDef(m);
        if (!solver.setA(XtX)) {
            // fall back: try a bit more ridge
            double extra = Math.max(1e-10, ridge);
            boolean ok = false;
            for (int k = 0; k < 6; k++) {
                DMatrixRMaj A = XtX.copy();
                addDiagonalInPlace(A, extra);
                if (solver.setA(A)) { ok = true; XtX = A; break; }
                extra *= 10.0;
            }
            if (!ok) {
                localScoreCache.put(key, Double.NaN);
                return Double.NaN;
            }
        }

        DMatrixRMaj beta = new DMatrixRMaj(m, 1);
        solver.solve(Xty, beta);

        // Residuals e = y - X beta
        double[] e = new double[n];
        for (int i = 0; i < n; i++) {
            double fit = 0.0;
            int base = i * m;
            for (int j = 0; j < m; j++) {
                fit += X.data[base + j] * beta.data[j];
            }
            e[i] = yy[i] - fit;
        }

        double ll = residualLogLik(e);

        double bonus = 0.0;
        if (nonGaussianBonus == NonGaussianBonus.KURTOSIS) {
            bonus = kurtosisBonus(e, n);
        }

// df for BIC penalty (see section 3 below for improved df by model)
        double df = m + noiseDf();

        double score = 2.0 * ll + bonus - penaltyDiscount * df * Math.log(n);

        localScoreCache.put(key, score);
        return score;
    }

    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(Math.log(Math.max(3, nEff)));
    }

    @Override
    public boolean determines(List<Node> z, Node yNode) {
        int i = variables.indexOf(yNode);
        int[] parents = new int[z.size()];
        for (int t = 0; t < z.size(); t++) parents[t] = variables.indexOf(z.get(t));

        try {
            double s = localScore(i, parents);
            return Double.isNaN(s) || Double.isInfinite(s);
        } catch (RuntimeException e) {
            TetradLogger.getInstance().log(e.getMessage());
            return true;
        }
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    public DataModel getDataModel() {
        return dataSet;
    }

    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        clearCache();
    }

    @Override
    public String toString() {
        return "NG-SEM-BIC (" + noiseModel + (nonGaussianBonus == NonGaussianBonus.NONE ? "" : ", " + nonGaussianBonus) + ")";
    }

    // -------------------- public knobs --------------------

    public double getPenaltyDiscount() { return penaltyDiscount; }
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (penaltyDiscount <= 0) throw new IllegalArgumentException("penaltyDiscount must be > 0");
        this.penaltyDiscount = penaltyDiscount;
        clearCache();
    }

    public boolean isCenterData() { return centerData; }
    public void setCenterData(boolean centerData) {
        this.centerData = centerData;
        clearCache();
    }

    public boolean isIncludeInterceptWhenNotCentered() { return includeInterceptWhenNotCentered; }
    public void setIncludeInterceptWhenNotCentered(boolean includeInterceptWhenNotCentered) {
        this.includeInterceptWhenNotCentered = includeInterceptWhenNotCentered;
        clearCache();
    }

    public double getRidge() { return ridge; }
    public void setRidge(double ridge) {
        if (ridge < 0) throw new IllegalArgumentException("ridge must be >= 0");
        this.ridge = ridge;
        clearCache();
    }

    public NoiseModel getNoiseModel() { return noiseModel; }
    public void setNoiseModel(NoiseModel noiseModel) {
        this.noiseModel = Objects.requireNonNull(noiseModel, "noiseModel");
        clearCache();
    }

    public double getMinScale() { return minScale; }
    public void setMinScale(double minScale) {
        if (minScale <= 0) throw new IllegalArgumentException("minScale must be > 0");
        this.minScale = minScale;
        clearCache();
    }

    public double getStudentTNu() {
        return studentTNu;
    }

    public void setStudentTNu(double nu) {
        if (nu <= 2.0) {
            throw new IllegalArgumentException("Student-t nu must be > 2");
        }
        this.studentTNu = nu;
        clearCache();
    }

    // -------------------- NG knob --------------------

    public double getKurtosisGamma() { return kurtosisGamma; }
    public void setKurtosisGamma(double g) {
        if (g < 0) throw new IllegalArgumentException("kurtosisGamma must be >= 0");
        this.kurtosisGamma = g;
        // clearCache(); // if you cache local scores
    }

    public double getKurtosisCap() { return kurtosisCap; }
    public void setKurtosisCap(double c) { this.kurtosisCap = c; }

    /** Optional non-Gaussian bonus on residuals (e.g., kurtosis magnitude). */
    private NonGaussianBonus nonGaussianBonus = NonGaussianBonus.NONE;

    public NonGaussianBonus getNonGaussianBonus() { return nonGaussianBonus; }

    public void setNonGaussianBonus(NonGaussianBonus bonus) {
        this.nonGaussianBonus = Objects.requireNonNull(bonus, "bonus");
        clearCache();
    }

    // -------------------- core statistic --------------------
    private static double excessKurtosis(double[] e) {
        int n = e.length;
        if (n < 8) return 0.0;

        // mean
        double mean = 0.0;
        for (double v : e) mean += v;
        mean /= n;

        // central moments m2, m4 (using 1/n normalization)
        double m2 = 0.0, m4 = 0.0;
        for (double v : e) {
            double d = v - mean;
            double d2 = d * d;
            m2 += d2;
            m4 += d2 * d2;
        }
        m2 /= n;
        m4 /= n;

        if (!(m2 > 0) || !Double.isFinite(m2) || !Double.isFinite(m4)) return 0.0;

        double g2 = (m4 / (m2 * m2)) - 3.0; // excess kurtosis
        if (!Double.isFinite(g2)) return 0.0;
        return g2;
    }

    private double kurtosisBonus(double[] resid, int n) {
        if (kurtosisGamma <= 0) return 0.0;
        double g2 = excessKurtosis(resid);

        if (kurtosisCap > 0 && Double.isFinite(kurtosisCap)) {
            if (g2 > kurtosisCap) g2 = kurtosisCap;
            else if (g2 < -kurtosisCap) g2 = -kurtosisCap;
        }

        // reward magnitude of non-Gaussianity, symmetric in sign
        return kurtosisGamma * n * (g2 * g2);
    }

    // -------------------- internals --------------------

    public enum NoiseModel {
        GAUSSIAN,
        LAPLACE,
        STUDENT_T,
        LOG_COSH
    }

    public enum NonGaussianBonus {
        NONE,
        KURTOSIS
    }

    private void clearCache() {
        localScoreCache.clear();
    }

    private double residualLogLik(double[] e) {
        int n = e.length;

        switch (noiseModel) {

            case LAPLACE -> {
                // Laplace(0, b): loglik = -n log(2b) - (1/b) sum |e|
                double sumAbs = 0.0;
                for (double v : e) sumAbs += Math.abs(v);

                double b = sumAbs / n;
                if (!(b > 0) || !Double.isFinite(b)) b = minScale;
                if (b < minScale) b = minScale;

                return -n * Math.log(2.0 * b) - (sumAbs / b);
            }

            case STUDENT_T -> {
                final double nu = studentTNu;

                // sigma^2 = mean squared residual
                double sumSq = 0.0;
                for (double v : e) sumSq += v * v;

                double sigma2 = sumSq / n;
                if (!(sigma2 > 0) || !Double.isFinite(sigma2)) sigma2 = minScale * minScale;
                if (sigma2 < minScale * minScale) sigma2 = minScale * minScale;

                double sigma = Math.sqrt(sigma2);

                double c =
                        org.apache.commons.math3.special.Gamma.logGamma((nu + 1.0) / 2.0)
                        - org.apache.commons.math3.special.Gamma.logGamma(nu / 2.0)
                        - 0.5 * Math.log(nu * Math.PI)
                        - Math.log(sigma);

                double ll = 0.0;
                for (double v : e) {
                    double z = (v * v) / (nu * sigma2);
                    ll += c - 0.5 * (nu + 1.0) * Math.log1p(z);
                }
                return ll;
            }

            case GAUSSIAN -> {
                // Gaussian(0, sigma^2) with sigma MLE:
                // ll = -n/2 * (log(2π) + 1 + log(sigma^2))
                double sumSq = 0.0;
                for (double v : e) sumSq += v * v;

                double sigma2 = sumSq / n;
                if (!(sigma2 > 0) || !Double.isFinite(sigma2)) sigma2 = minScale * minScale;
                if (sigma2 < minScale * minScale) sigma2 = minScale * minScale;

                return -0.5 * n * (Math.log(2.0 * Math.PI) + 1.0 + Math.log(sigma2));
            }

            case LOG_COSH -> {
                return logCoshLogLik(e);
            }

            default -> throw new IllegalStateException("Unknown noise model");
        }
    }

    private double noiseDf() {
        return switch (noiseModel) {
            case GAUSSIAN -> 1.0;
            case LAPLACE -> 1.0;
            case STUDENT_T -> 1.0; // nu fixed
            case LOG_COSH -> 1.0;  // scale s
        };
    }

    private static void zscoreInPlace(double[] x) {
        int n = x.length;
        if (n < 2) return;
        double sum = 0.0, sumsq = 0.0;
        for (double v : x) { sum += v; sumsq += v * v; }
        double mean = sum / n;
        double var = (sumsq - n * mean * mean) / (n - 1);
        double sd = (var > 0) ? Math.sqrt(var) : 1.0;
        for (int i = 0; i < n; i++) x[i] = (x[i] - mean) / sd;
    }

    private static void zscoreInPlace(double[][] X) {
        int n = X.length;
        if (n == 0) return;
        int d = X[0].length;
        if (n < 2 || d == 0) return;

        for (int j = 0; j < d; j++) {
            double sum = 0.0, sumsq = 0.0;
            for (int i = 0; i < n; i++) {
                double v = X[i][j];
                sum += v;
                sumsq += v * v;
            }
            double mean = sum / n;
            double var = (sumsq - n * mean * mean) / (n - 1);
            double sd = (var > 0) ? Math.sqrt(var) : 1.0;
            for (int i = 0; i < n; i++) X[i][j] = (X[i][j] - mean) / sd;
        }
    }

    private static void addDiagonalInPlace(DMatrixRMaj M, double v) {
        int n = Math.min(M.numRows, M.numCols);
        for (int i = 0; i < n; i++) M.add(i, i, v);
    }

    private static void multTransA_vec(DMatrixRMaj A, double[] x, DMatrixRMaj out) {
        int n = A.numRows;
        int m = A.numCols;
        if (out.numRows != m || out.numCols != 1) throw new IllegalArgumentException("out dim mismatch");
        Arrays.fill(out.data, 0.0);
        for (int i = 0; i < n; i++) {
            double xi = x[i];
            int idx = i * m;
            for (int j = 0; j < m; j++) out.data[j] += A.data[idx + j] * xi;
        }
    }

    private double[] extract1D(int varIndex, int[] rows, int n) {
        double[] x = new double[n];
        if (rows == null) {
            for (int i = 0; i < n; i++) x[i] = cols[varIndex][i];
        } else {
            for (int i = 0; i < n; i++) x[i] = cols[varIndex][rows[i]];
        }
        return x;
    }

    private double[][] extractND(int[] vars, int[] rows, int n, int d) {
        double[][] Z = new double[n][d];
        if (rows == null) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < d; j++) Z[i][j] = cols[vars[j]][i];
            }
        } else {
            for (int i = 0; i < n; i++) {
                int r = rows[i];
                for (int j = 0; j < d; j++) Z[i][j] = cols[vars[j]][r];
            }
        }
        return Z;
    }

    private static double logCosh(double x) {
        // stable: log(cosh(x)) = |x| + log(1 + exp(-2|x|)) - log 2
        double ax = Math.abs(x);
        return ax + Math.log1p(Math.exp(-2.0 * ax)) - Math.log(2.0);
    }

    private double logCoshLogLik(double[] e) {
        int n = e.length;

        // pick a scale s (roughly comparable to sigma): use mean absolute deviation
        double sumAbs = 0.0;
        for (double v : e) sumAbs += Math.abs(v);
        double s = sumAbs / n;
        if (!(s > 0) || !Double.isFinite(s)) s = minScale;
        if (s < minScale) s = minScale;

        // loglik up to an additive constant:
        // ll = - n*log(s) - sum log cosh(e/s)  (+ constant not depending on parents)
        double ll = -n * Math.log(s);
        double invS = 1.0 / s;
        for (double v : e) ll -= logCosh(v * invS);

        return ll;
    }

    private int[] validRows(int[] vars) {
        int[] tmp = new int[sampleSize];
        int m = 0;

        outer:
        for (int r = 0; r < sampleSize; r++) {
            for (int v : vars) {
                double val = cols[v][r];
                if (Double.isNaN(val)) continue outer;
            }
            tmp[m++] = r;
        }
        return Arrays.copyOf(tmp, m);
    }

    private static int[] concat(int i, int[] parents) {
        int[] all = new int[parents.length + 1];
        all[0] = i;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }

    private static long cacheKey(int target, int[] parents) {
        long h = 1469598103934665603L;
        h = (h ^ target) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        return h;
    }
}
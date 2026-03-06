package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TMath;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;

import java.util.*;

/**
 * The HeavyTailSemBicScore class implements a scoring mechanism for assessing structural equation models (SEMs) with
 * heavy-tailed data. It extends functionalities from the Score class and the EffectiveSampleSizeSettable interface,
 * adding capabilities for local scoring, kurtosis adjustments, centering options, and noise modeling.
 * <p>
 * The scoring methodology incorporates adjustments for heavy-tailed distributions using metrics like kurtosis and ridge
 * regularization. It supports efficient calculation of scores through caching and includes options for penalizing model
 * complexity.
 * <p>
 * This is not a score-equivalent score, meaning that DAGs in the same Markov equivalence class may receive different
 * scores. As a result, it is more suited to a DAG-based search strategy like BOSS and less suited to, say, FGES, which
 * relies on score-equivalence.
 */
public final class HeavyTailSemBicScore implements Score, EffectiveSampleSizeSettable {

    // -------------------- data --------------------
    private final DataSet dataSet;
    private final List<Node> variables;
    private final int sampleSize;
    private final boolean calculateRowSubsets;

    /**
     * cols[varIndex][row] (may contain NaNs).
     */
    private final double[][] cols;
    /**
     * Optional score cache.
     */
    private final Map<Long, Double> localScoreCache = new HashMap<>();
    /**
     * Effective sample size (defaults to sampleSize).
     */
    private int nEff;
    /**
     * Degrees of freedom for Student-t residuals (fixed).
     */
    private double studentTNu = 4.0;
    /**
     * Strength of kurtosis reward. Typical starting values: 0.01 to 0.2.
     */
    private double kurtosisGamma = 0.05;
    /**
     * Optional: cap |excess kurtosis| to prevent a single outlier from dominating.
     */
    private double kurtosisCap = 10.0; // set <=0 to disable capping

    // -------------------- knobs --------------------
    /**
     * Penalty discount multiplier (like SemBicScore).
     */
    private double penaltyDiscount = 1.0;

    /**
     * If true, z-score y and each parent column; if false, we usually include intercept.
     */
    private boolean centerData = true;

    /**
     * If centerData==false, include intercept column (recommended).
     */
    private boolean includeInterceptWhenNotCentered = true;

    /**
     * Ridge stabilizer added to XtX diagonal. Set to 0 for pure OLS; small positive helps when XtX is ill-conditioned.
     */
    private double ridge = 1e-8;

    /**
     * Small clamp to avoid log(0) or division by 0 for scale MLE.
     */
    private double minScale = 1e-12;

    /**
     * Residual noise model. Start with Laplace; we can add StudentT later.
     */
    private NoiseModel noiseModel = NoiseModel.STUDENT_T;

    // -------------------- construction --------------------
    /**
     * Optional non-Gaussian bonus on residuals (e.g., kurtosis magnitude).
     */
    private NonGaussianBonus nonGaussianBonus = NonGaussianBonus.NONE;

    // -------------------- Score interface --------------------

    /**
     * Constructs an instance of the HeavyTailSemBicScore class with the specified data set. This score is used for
     * statistical analysis with adjustments for heavy-tail distributions.
     *
     * @param dataSet the input data set used to calculate the bic score, must not be null
     */
    public HeavyTailSemBicScore(DataSet dataSet) {
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

    private static void zscoreInPlace(double[] x) {
        int n = x.length;
        if (n < 2) return;
        double sum = 0.0, sumsq = 0.0;
        for (double v : x) {
            sum += v;
            sumsq += v * v;
        }
        double mean = sum / n;
        double var = (sumsq - n * mean * mean) / (n - 1);
        double sd = (var > 0) ? TMath.sqrt(var) : 1.0;
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
            double sd = (var > 0) ? TMath.sqrt(var) : 1.0;
            for (int i = 0; i < n; i++) X[i][j] = (X[i][j] - mean) / sd;
        }
    }

    private static void addDiagonalInPlace(DMatrixRMaj M, double v) {
        int n = TMath.min(M.numRows, M.numCols);
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

    private static double logCosh(double x) {
        // stable: log(cosh(x)) = |x| + log(1 + exp(-2|x|)) - log 2
        double ax = TMath.abs(x);
        return ax + TMath.log1p(TMath.exp(-2.0 * ax)) - TMath.log(2.0);
    }

    private static int[] concat(int i, int[] parents) {
        int[] all = new int[parents.length + 1];
        all[0] = i;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    private static long cacheKey(int target, int[] parents) {
        long h = 1469598103934665603L;
        h = (h ^ target) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        return h;
    }

    /**
     * Computes the difference in local scores between two configurations of a target variable and its parent set. The
     * method calculates the difference between the local score of {@code y} with the parent set {@code z} extended by
     * {@code x}, and the local score of {@code y} with parent set {@code z}.
     *
     * @param x the variable to be added to the current parent set {@code z}
     * @param y the target variable for which the local score is computed
     * @param z the current parent set of the target variable {@code y}
     * @return the difference in local scores when {@code x} is added to the parent set {@code z} for the target
     * {@code y}
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Computes the local score for a given target variable and its set of parent variables. The method evaluates the
     * goodness of fit of a statistical model for the target given the specified parent variables, penalized by the
     * model complexity.
     *
     * @param target  the target variable for which the local score is computed
     * @param parents the array of parent variables influencing the target variable
     * @return the computed local score as a double value, or {@code Double.NaN} if the computation is not feasible
     * (e.g., insufficient effective sample size or solver issues)
     */
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
            double score = 2.0 * ll - penaltyDiscount * df * TMath.log(n);
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
            double extra = TMath.max(1e-10, ridge);
            boolean ok = false;
            for (int k = 0; k < 6; k++) {
                DMatrixRMaj A = XtX.copy();
                addDiagonalInPlace(A, extra);
                if (solver.setA(A)) {
                    ok = true;
                    XtX = A;
                    break;
                }
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

        double score = 2.0 * ll + bonus - penaltyDiscount * df * TMath.log(n);

        localScoreCache.put(key, score);
        return score;
    }

    // -------------------- public knobs --------------------

    /**
     * Retrieves the list of variable nodes associated with the current score calculation.
     *
     * @return a list of {@code Node} objects representing the variables used in the model.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the sample size from the underlying data set.
     *
     * @return the sample size, which is the number of rows in the data set.
     */
    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    /**
     * Calculates the maximum degree of the model based on the effective sample size (nEff). The degree is determined as
     * the ceiling of the logarithm (base 2) of the maximum value between 3 and the effective sample size.
     *
     * @return the maximum degree of the model as an integer.
     */
    @Override
    public int getMaxDegree() {
        return (int) TMath.ceil(TMath.log(TMath.max(3, nEff)));
    }

    /**
     * Determines whether a specific node is conditionally independent of a target node given a set of parent nodes,
     * based on the local score. The method evaluates the local score of the target node with the given parent set to
     * establish the relationship.
     *
     * @param z     the list of parent nodes to condition on
     * @param yNode the target node to evaluate conditional independence
     * @return {@code true} if the computed local score is {@code Double.NaN} or {@code Double.isInfinite}; otherwise,
     * {@code false}
     */
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

    /**
     * Determines whether the given bump value indicates the presence of an "effect edge".
     *
     * @param bump a double value representing the magnitude of a certain effect or change
     * @return {@code true} if the bump value is greater than 0, indicating an effect edge; {@code false} otherwise
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * Retrieves the data model associated with the {@code HeavyTailSemBicScore} instance.
     *
     * @return the {@code DataModel} currently being used by the instance.
     */
    public DataModel getDataModel() {
        return dataSet;
    }

    /**
     * Retrieves the effective sample size used in the statistical model. The effective sample size represents the
     * adjusted number of samples that account for certain model-based penalties or adjustments, such as heavy-tail
     * distributions or regularization.
     *
     * @return the effective sample size as an integer.
     */
    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    /**
     * Sets the effective sample size used in the statistical model. If the provided value is less than 0, the method
     * assigns the sample size of the dataset to the effective sample size. This adjustment allows for flexible
     * configuration of the effective sample size based on user input or defaults.
     *
     * @param nEff the effective sample size to be set; if less than 0, the sample size of the dataset is used instead
     */
    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
        clearCache();
    }

    /**
     * Returns a string representation of the {@code HeavyTailSemBicScore} instance. The representation includes the
     * noise model and, if applicable, details about the non-Gaussian bonus.
     *
     * @return a string summarizing the state of the object, including the noise model and, if set, the non-Gaussian
     * bonus.
     */
    @Override
    public String toString() {
        return "NG-SEM-BIC (" + noiseModel + (nonGaussianBonus == NonGaussianBonus.NONE ? "" : ", " + nonGaussianBonus) + ")";
    }

    /**
     * Retrieves the penalty discount used in the penalization of model complexity. The penalty discount is a factor
     * that adjusts the weight of penalties applied to models based on their complexity or robustness requirements.
     *
     * @return the penalty discount as a double value.
     */
    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    /**
     * Sets the penalty discount used for regularization and penalization in the statistical model. The penalty discount
     * adjusts the weight of model penalties, influencing model complexity. This value must be greater than 0;
     * otherwise, an {@code IllegalArgumentException} is thrown.
     *
     * @param penaltyDiscount the penalty discount to set; must be a positive double value
     * @throws IllegalArgumentException if {@code penaltyDiscount} is less than or equal to 0
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        if (penaltyDiscount <= 0) throw new IllegalArgumentException("penaltyDiscount must be > 0");
        this.penaltyDiscount = penaltyDiscount;
        clearCache();
    }

    /**
     * Checks whether the data is configured to be centered.
     *
     * @return true if the data is set to be centered, false otherwise
     */
    public boolean isCenterData() {
        return centerData;
    }

    /**
     * Sets the centerData flag to determine if data should be centered, and clears the cache.
     *
     * @param centerData a boolean value indicating whether the data should be centered
     */
    public void setCenterData(boolean centerData) {
        this.centerData = centerData;
        clearCache();
    }

    /**
     * Determines whether the intercept should be included when the data is not centered.
     *
     * @return true if the intercept is included when not centered, false otherwise
     */
    public boolean isIncludeInterceptWhenNotCentered() {
        return includeInterceptWhenNotCentered;
    }

    // -------------------- NG knob --------------------

    /**
     * Sets the includeInterceptWhenNotCentered flag to determine if the intercept should be included when the data is
     * not centered, and clears the cache.
     *
     * @param includeInterceptWhenNotCentered a boolean value indicating whether the intercept should be included when
     *                                        not centered
     */
    public void setIncludeInterceptWhenNotCentered(boolean includeInterceptWhenNotCentered) {
        this.includeInterceptWhenNotCentered = includeInterceptWhenNotCentered;
        clearCache();
    }

    /**
     * Retrieves the ridge parameter used in the score calculation.
     *
     * @return the ridge parameter
     */
    public double getRidge() {
        return ridge;
    }

    /**
     * Sets the ridge parameter used in the score calculation, and clears the cache.
     *
     * @param ridge the ridge parameter to set; must be non-negative
     */
    public void setRidge(double ridge) {
        if (ridge < 0) throw new IllegalArgumentException("ridge must be >= 0");
        this.ridge = ridge;
        clearCache();
    }

    /**
     * Retrieves the noise model used in the score calculation.
     *
     * @return the noise model
     */
    public NoiseModel getNoiseModel() {
        return noiseModel;
    }

    /**
     * Sets the noise model used in the score calculation, and clears the cache.
     *
     * @param noiseModel the noise model to set; must not be null
     */
    public void setNoiseModel(NoiseModel noiseModel) {
        this.noiseModel = Objects.requireNonNull(noiseModel, "noiseModel");
        clearCache();
    }

    /**
     * Retrieves the minimum scale value.
     *
     * @return the minimum scale as a double
     */
    public double getMinScale() {
        return minScale;
    }

    /**
     * Sets the minimum scale value for the component. Ensures that the provided scale is greater than zero. An
     * {@link IllegalArgumentException} is thrown if the provided value is invalid.
     *
     * @param minScale the minimum scale value to be set; must be greater than 0
     */
    public void setMinScale(double minScale) {
        if (minScale <= 0) throw new IllegalArgumentException("minScale must be > 0");
        this.minScale = minScale;
        clearCache();
    }

    /**
     * Retrieves the value of the Student's t-distribution degrees of freedom (ν).
     *
     * @return the degrees of freedom (ν) for the Student's t-distribution as a double.
     */
    public double getStudentTNu() {
        return studentTNu;
    }

    /**
     * Sets the degrees of freedom (nu) for the Student's t-distribution.
     *
     * @param nu The degrees of freedom for the Student's t-distribution. Must be greater than 2.
     * @throws IllegalArgumentException If the provided nu is less than or equal to 2.
     */
    public void setStudentTNu(double nu) {
        if (nu <= 2.0) {
            throw new IllegalArgumentException("Student-t nu must be > 2");
        }
        this.studentTNu = nu;
        clearCache();
    }

    // -------------------- internals --------------------

    /**
     * Retrieves the kurtosis (fourth standardized moment) of the gamma distribution.
     * <p>
     * The kurtosis is a measure of the "tailedness" of the probability distribution, giving insight into the
     * extremities of the dataset relative to a normal distribution.
     *
     * @return the kurtosis of the gamma distribution as a double value
     */
    public double getKurtosisGamma() {
        return kurtosisGamma;
    }

    /**
     * Sets the kurtosis gamma value for the object. The kurtosis gamma value must be a non-negative number. If a
     * negative value is provided, an IllegalArgumentException will be thrown.
     *
     * @param g the kurtosis gamma value to set; must be greater than or equal to 0
     */
    public void setKurtosisGamma(double g) {
        if (g < 0) throw new IllegalArgumentException("kurtosisGamma must be >= 0");
        this.kurtosisGamma = g;
        // clearCache(); // if you cache local scores
    }

    /**
     * Retrieves the kurtosis cap value. The kurtosis cap is a threshold that limits the kurtosis calculation for
     * statistical or data analysis purposes.
     *
     * @return the kurtosis cap value as a double.
     */
    public double getKurtosisCap() {
        return kurtosisCap;
    }

    /**
     * Sets the kurtosis cap value.
     *
     * @param c the value to set as the kurtosis cap
     */
    public void setKurtosisCap(double c) {
        this.kurtosisCap = c;
    }

    /**
     * Retrieves the NonGaussianBonus object associated with this instance.
     *
     * @return the NonGaussianBonus object currently assigned. May be null if not assigned.
     */
    public NonGaussianBonus getNonGaussianBonus() {
        return nonGaussianBonus;
    }

    /**
     * Sets the non-Gaussian bonus for this instance. This method assigns the provided {@code NonGaussianBonus} object
     * to the internal state and ensures it is not null. Additionally, it clears any related cached data.
     *
     * @param bonus the non-Gaussian bonus to be set; must not be null
     * @throws NullPointerException if {@code bonus} is null
     */
    public void setNonGaussianBonus(NonGaussianBonus bonus) {
        this.nonGaussianBonus = Objects.requireNonNull(bonus, "bonus");
        clearCache();
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

    private void clearCache() {
        localScoreCache.clear();
    }

    private double residualLogLik(double[] e) {
        int n = e.length;

        switch (noiseModel) {

            case LAPLACE -> {
                // Laplace(0, b): loglik = -n log(2b) - (1/b) sum |e|
                double sumAbs = 0.0;
                for (double v : e) sumAbs += TMath.abs(v);

                double b = sumAbs / n;
                if (!(b > 0) || !Double.isFinite(b)) b = minScale;
                if (b < minScale) b = minScale;

                return -n * TMath.log(2.0 * b) - (sumAbs / b);
            }

            case STUDENT_T -> {
                final double nu = studentTNu;

                // sigma^2 = mean squared residual
                double sumSq = 0.0;
                for (double v : e) sumSq += v * v;

                double sigma2 = sumSq / n;
                if (!(sigma2 > 0) || !Double.isFinite(sigma2)) sigma2 = minScale * minScale;
                if (sigma2 < minScale * minScale) sigma2 = minScale * minScale;

                double sigma = TMath.sqrt(sigma2);

                double c =
                        org.apache.commons.math3.special.Gamma.logGamma((nu + 1.0) / 2.0)
                        - org.apache.commons.math3.special.Gamma.logGamma(nu / 2.0)
                        - 0.5 * TMath.log(nu * TMath.PI)
                        - TMath.log(sigma);

                double ll = 0.0;
                for (double v : e) {
                    double z = (v * v) / (nu * sigma2);
                    ll += c - 0.5 * (nu + 1.0) * TMath.log1p(z);
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

                return -0.5 * n * (TMath.log(2.0 * TMath.PI) + 1.0 + TMath.log(sigma2));
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

    private double logCoshLogLik(double[] e) {
        int n = e.length;

        // pick a scale s (roughly comparable to sigma): use mean absolute deviation
        double sumAbs = 0.0;
        for (double v : e) sumAbs += TMath.abs(v);
        double s = sumAbs / n;
        if (!(s > 0) || !Double.isFinite(s)) s = minScale;
        if (s < minScale) s = minScale;

        // loglik up to an additive constant:
        // ll = - n*log(s) - sum log cosh(e/s)  (+ constant not depending on parents)
        double ll = -n * TMath.log(s);
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

    /**
     * Appends an integer to the end of an array, creating a new array with the additional element.
     *
     * @param z the original array to which the integer will be appended
     * @param x the integer value to append to the array
     * @return a new array containing all elements of the original array followed by the appended integer
     */
    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }

    /**
     * Represents different types of statistical noise models used in data processing, signal processing, or
     * optimization problems. These models define the characteristics of noise within a given dataset and are commonly
     * used for tasks such as regression, anomaly detection, or robust estimation.
     */
    public enum NoiseModel {
        /**
         * A noise model based on the Gaussian (normal) distribution.
         */
        GAUSSIAN,
        /**
         * A noise model based on the Laplace distribution, also known as the double exponential distribution.
         */
        LAPLACE,
        /**
         * A noise model based on the Student's t-distribution. This distribution is often used to model data with
         * heavier tails than the normal distribution, making it robust to outliers in a dataset. It is particularly
         * useful in statistical applications where the assumption of normally distributed errors may not hold.
         */
        STUDENT_T,
        /**
         * A noise model derived from the logarithm of the hyperbolic cosine function, commonly used in robust
         * regression techniques.
         */
        LOG_COSH
    }

    /**
     * An enumeration representing the types of non-Gaussian bonuses. These bonuses are typically used in statistical or
     * machine learning contexts to account for deviations from Gaussian distributions.
     */
    public enum NonGaussianBonus {
        /**
         * Represents the absence of a non-Gaussian bonus. This value is used to indicate that no adjustments or bonuses
         * related to deviations from Gaussian distributions should be applied.
         */
        NONE,
        /**
         * Represents a non-Gaussian bonus based on kurtosis. Kurtosis measures the "tailedness" of a statistical
         * distribution, with higher kurtosis values indicating heavier tails and more outliers. This value is used in
         * contexts where deviations from Gaussian distributions, particularly in terms of kurtosis, influence the
         * computation or adjustment of scores.
         */
        KURTOSIS
    }
}
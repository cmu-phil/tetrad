package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;
import org.ejml.interfaces.linsol.LinearSolverDense;

import java.util.*;

/**
 * Kernel-based marginal likelihood score of Huang et al. (2018) for continuous variables.
 *
 * <p>
 * This score implements the kernel marginal score proposed by
 * Huang, Zhang, Schölkopf, and Zhang (2018) for evaluating the fit of a
 * regression of a target variable {@code X} on a parent set {@code Z}
 * using reproducing kernel Hilbert space (RKHS) methods.
 * The score is derived from kernel ridge regression and can be interpreted
 * as a surrogate marginal likelihood obtained by integrating out the
 * regression function in an RKHS.
 * </p>
 *
 * <p>Model and Interpretation</p>
 *
 * <p>
 * Let {@code X} be a univariate response and {@code Z} a (possibly multivariate)
 * set of predictors. Huang et al. consider a kernel regression model
 * in which the conditional mean function of {@code X} given {@code Z}
 * lies in an RKHS associated with a positive-definite kernel.
 * The score is constructed from centered kernel Gram matrices
 * {@code Kx} (for the response) and {@code Kz} (for the predictors),
 * together with a ridge parameter {@code lambda}.
 * </p>
 *
 * <p>
 * After kernel centering, the score depends on the matrix
 * {@code A = Kz + n * lambda * I}, and is computed via the expression
 * </p>
 *
 * <pre>
 *   S = - (n / 2) * log | (n * lambda / 2) * (Kx * A^{-2} * Kx) |  + const,
 * </pre>
 *
 * <p>
 * where {@code n} is the sample size and {@code |·|} denotes a
 * (pseudo-)determinant. The implementation evaluates this expression
 * using stable linear-algebraic operations (Cholesky decompositions and solves),
 * avoiding explicit matrix inversion.
 * </p>
 *
 * <p>Numerical Characteristics</p>
 *
 * <p>
 * Because the centered kernel matrices are singular by construction,
 * the matrix whose determinant appears in the score is typically
 * positive semidefinite rather than strictly positive definite.
 * As a result, the score relies on either a pseudo-determinant
 * (based on eigenvalues) or an implicit ridge regularization
 * to ensure numerical stability.
 * </p>
 *
 * <p>
 * In practice, this makes the Huang marginal score more sensitive to
 * kernel bandwidth choice, regularization strength, and numerical noise
 * than a full Gaussian process marginal likelihood.
 * While the score can perform well in some nonlinear regression settings,
 * it may exhibit instability in greedy score-based causal search
 * when parent sets differ by small changes.
 * </p>
 *
 * <p>Comparison to Other Kernel Scores</p>
 *
 * <ul>
 *   <li>
 *     Unlike the exact kernel marginal likelihood (KML),
 *     this score does not correspond to a full probabilistic
 *     marginal likelihood over functions.
 *   </li>
 *   <li>
 *     Compared to operator-based surrogates, it is more principled
 *     but still approximate.
 *   </li>
 *   <li>
 *     Compared to KML or its low-rank variants (e.g., RFF-ML / KFF-ML),
 *     it is generally less stable for greedy structure search,
 *     though computationally cheaper than exact GP scoring.
 *   </li>
 * </ul>
 *
 * <p>Implementation Notes</p>
 *
 * <ul>
 *   <li>Uses Gaussian RBF kernels with a median-distance bandwidth heuristic.</li>
 *   <li>Centers all kernel Gram matrices in feature space.</li>
 *   <li>Handles missing data via row-wise deletion over {@code X ∪ Z}.</li>
 *   <li>Avoids explicit matrix inversion; relies on Cholesky factorizations.</li>
 * </ul>
 *
 * <p>
 * Higher scores indicate better fit, consistent with Tetrad {@link Score}
 * conventions.
 * </p>
 *
 * @see edu.cmu.tetrad.search.score.KernelMarginalLikelihoodScore
 * @see FfMl
 */
public final class HuangMarginalLikelihoodScore implements Score, EffectiveSampleSizeSettable {

    // -------------------- configuration --------------------

    /**
     * Ridge parameter (lambda). Huang uses n*lambda in the stabilization term. Must be > 0.
     */
    private double lambda = 1e-3;

    /**
     * Small jitter added to G for logdet stability; will be adaptively increased if needed.
     */
    private double jitter = 1e-10;

    /**
     * Represents a multiplier used to adjust the bandwidth.
     * This value is used to scale the bandwidth in various calculations.
     * The default value is 1.0, which indicates no scaling is applied.
     * A value greater than 1.0 increases the bandwidth, while a value less
     * than 1.0 decreases it.
     */
    private double bandwidthMultiplier = 1.0;

    /**
     * If true, compute valid row subsets when missing values exist.
     */
    private final boolean calculateRowSubsets;

    /**
     * The data set used for computing marginal likelihood scores within the
     * HuangMarginalLikelihoodScore class. This dataset forms the basis for
     * statistical operations, kernel computations, and score evaluations.
     * <p>
     * This field is immutable and initialized during the construction of the
     * HuangMarginalLikelihoodScore instance. It must not be null, and attempting
     * to construct an instance with a null dataset will result in a
     * NullPointerException.
     */
    private final DataSet dataSet;

    /**
     * A list of variables represented as {@link Node} objects used in the scoring algorithm.
     * This field contains the set of variables associated with the data model and is
     * integral to various calculations, such as computing marginal likelihood scores.
     * The list is initialized based on the dataset provided at the time of object construction.
     * It is immutable once set.
     */
    private final List<Node> variables;

    /**
     * Represents the original sample size for the dataset used in the scoring algorithm.
     * This variable holds the total number of data points in the dataset and is constant
     * throughout the lifecycle of the object.
     */
    private final int sampleSize;

    /**
     * Cached columns (full length, may include NaNs). cols[varIndex][row].
     */
    private final double[][] cols;

    /**
     * Effective sample size (defaults to sampleSize).
     */
    private int nEff;

    /**
     * Optional: lightweight cache to avoid recomputing kernels for repeated parent sets.
     * Key is (target i, parents array) -> score.
     * You can delete this if you’d rather not cache at first.
     */
    private final Map<Long, Double> localScoreCache = new HashMap<>();

    // -------------------- construction --------------------

    /**
     * Constructs a HuangMarginalScore object by initializing it with the given dataset
     * and precomputing internal structures required for scoring.
     *
     * @param dataSet The input data set to be used for computing marginal scores.
     *                Must not be null; a NullPointerException is thrown if the input is null.
     */
    public HuangMarginalLikelihoodScore(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("dataSet");
        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        setEffectiveSampleSize(-1);

        this.calculateRowSubsets = dataSet.existsMissingValue();

        // Extract columns once for speed.
        int p = variables.size();
        this.cols = new double[p][sampleSize];
        for (int j = 0; j < p; j++) {
            for (int r = 0; r < sampleSize; r++) {
                cols[j][r] = dataSet.getDouble(r, j);
            }
        }
    }

    // -------------------- Score interface --------------------

    /**
     * Computes the difference between two local scores:
     * `score(y | z ∪ {x})` and `score(y | z)`. This method evaluates
     * the impact of adding a variable `x` to the conditioning set `z`
     * for predicting the target variable `y`.
     *
     * @param x The index of the variable being added to the conditioning set.
     * @param y The index of the target variable whose score is being evaluated.
     * @param z An array containing the indices of the conditioning variables.
     * @return The difference between the local scores:
     * `score(y | z ∪ {x}) - score(y | z)`.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        // score(y | z ∪ {x}) - score(y | z)
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Computes the local score for a given target variable and a set of parent variables.
     * The local score reflects the statistical relationship between the target variable and
     * its conditioning parents based on kernel methods. This method uses precomputed results
     * from a cache if available; otherwise, it computes the score and caches it.
     *
     * @param i       The index of the target variable whose local score is being computed.
     * @param parents An array of indices representing the parent variables used for computation.
     *                This array may be empty if the target variable has no parents.
     * @return The computed local score as a double. NaN is returned if there are insufficient
     * effective samples or other conditions make the score indeterminable.
     */
    @Override
    public double localScore(int i, int... parents) {
        Arrays.sort(parents);

        long key = cacheKey(i, parents);
        Double cached = localScoreCache.get(key);
        if (cached != null) return cached;

        // Valid rows for {i} ∪ parents, only if missing values exist.
        int[] all = concat(i, parents);
        int[] rows = calculateRowSubsets ? validRows(all) : null;

        // If too few rows, return NaN (FGES will typically ignore / treat as determinism).
        int n = (rows == null) ? nEff : rows.length;
        if (n < 3) return Double.NaN;

        // Build Kx (n x n) from target column, and Kz from parent matrix (n x n).
        DMatrixRMaj Kx = rbfGram1D(i, rows);
//        DMatrixRMaj Kz = (parents.length == 0) ? zeroGram(n) : rbfGramND(parents, rows);
        DMatrixRMaj Kz = (parents.length == 0) ? identityGram(n) : rbfGramND(parents, rows);

        // Center both.
        centerInPlace(Kx);
        centerInPlace(Kz);

        // Score via Huang Eq. (6), using Appendix A1 formula:
        // SigmaHat = (n*lambda/2) * Kx * (Kz + n*lambda I)^(-2) * Kx
        // We compute D = (Kz + n*lambda I)^(-1) * Kx, then G = D^T D = Kx * A^(-2) * Kx.
        double ll = huangLogLikelihoodFromCenteredKernels(Kx, Kz, n, lambda, jitter);

        // Cache & return.
        localScoreCache.put(key, ll);
        return ll;
    }

    private static DMatrixRMaj identityGram(int n) {
        DMatrixRMaj I = new DMatrixRMaj(n, n);
        for (int i = 0; i < n; i++) I.set(i, i, 1.0);
        return I;
    }

    /**
     * Retrieves the list of variables used in the scoring process.
     * The returned list contains all variables associated with the dataset.
     *
     * @return A new {@code List} of {@code Node} objects representing the variables.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Retrieves the sample size used in the scoring process.
     *
     * @return The total number of rows in the dataset, representing the sample size as an integer.
     */
    @Override
    public int getSampleSize() {
        return dataSet.getNumRows();
    }

    /**
     * Computes the maximum degree based on a heuristic approach.
     * The calculation is performed as the ceiling of the logarithm
     * of the effective sample size (nEff). A minimum value of 3 is
     * considered for the logarithmic calculation to ensure robustness.
     *
     * @return the maximum allowed degree as an integer
     */
    @Override
    public int getMaxDegree() {
        // Similar heuristic to SEM BIC: ceil(log(n)).
        // Adjust if you want something else.
        return (int) Math.ceil(Math.log(Math.max(3, nEff)));
    }

    /**
     * Determines whether the given variable y is conditionally independent of the
     * set of variables z based on a computed score. This is typically used in
     * causal structure discovery algorithms.
     *
     * @param z the list of parent nodes to check for conditional independence
     * @param y the target node to test against the parent set
     * @return true if the score calculation results in NaN or infinity, indicating
     * an error or undefined state; otherwise, false
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        int i = variables.indexOf(y);
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
     * Determines whether a given edge has an effect based on the specified bump value.
     *
     * @param bump The numerical value used to evaluate the edge's effect. A positive value indicates
     *             that the edge is considered to have an effect.
     * @return {@code true} if the bump value is greater than 0, indicating the edge has an effect;
     * {@code false} otherwise.
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * Retrieves the data model associated with this instance.
     *
     * @return The {@code DataModel} representing the dataset used for scoring.
     */
    public DataModel getDataModel() {
        return dataSet;
    }

    /**
     * Retrieves the effective sample size used in the scoring algorithm.
     * The effective sample size is a parameter that reflects the number of
     * data points effectively contributing to the score calculation.
     *
     * @return The effective sample size as an integer.
     */
    @Override
    public int getEffectiveSampleSize() {
        return nEff;
    }

    /**
     * Sets the effective sample size for score calculations.
     * If the provided effective sample size is negative, the method defaults to using the original sample size.
     *
     * @param nEff The effective sample size to be set. If less than 0, the original sample size is used.
     */
    @Override
    public void setEffectiveSampleSize(int nEff) {
        this.nEff = (nEff < 0) ? this.sampleSize : nEff;
    }


    /**
     * Sets the multiplier used to adjust the bandwidth in calculations.
     * The value must be greater than 0 and finite.
     *
     * @param m the value to set as the bandwidth multiplier
     * @throws IllegalArgumentException if the value is not greater than 0 or is not finite
     */
    public void setBandwidthMultiplier(double m) {
        if (!(m > 0) || !Double.isFinite(m)) {
            throw new IllegalArgumentException("bandwidthMultiplier must be > 0 and finite");
        }
        this.bandwidthMultiplier = m;
        localScoreCache.clear();
    }

    /**
     * Returns a string representation of the HuangMarginalScore object.
     *
     * @return A string describing the Huang Kernel Marginal Score as "Huang Kernel Marginal Score (continuous)".
     */
    @Override
    public String toString() {
        return "Huang Kernel Marginal Score (continuous)";
    }

    /**
     * Sets the value of the lambda parameter and clears the local score cache.
     * The lambda parameter is a positive value that may be used in computations
     * involving kernel-based methods, such as controlling the regularization strength.
     * If the provided value is less than or equal to 0, an {@link IllegalArgumentException}
     * is thrown.
     *
     * @param lambda The value to set for the lambda parameter. Must be greater than 0.
     * @throws IllegalArgumentException if the provided lambda value is less than or equal to 0.
     */
    public void setLambda(double lambda) {
        if (lambda <= 0) throw new IllegalArgumentException("lambda must be > 0");
        this.lambda = lambda;
        localScoreCache.clear();
    }

    /**
     * Sets the value of the jitter parameter and clears the local score cache.
     * The jitter parameter is a small positive value added to the diagonal of certain
     * matrices during computations to improve numerical stability.
     * If the provided jitter value is less than or equal to 0, an {@code IllegalArgumentException} is thrown.
     *
     * @param jitter The value to set for the jitter parameter. Must be a positive double.
     * @throws IllegalArgumentException if the provided jitter value is less than or equal to 0.
     */
    public void setJitter(double jitter) {
        if (jitter <= 0) throw new IllegalArgumentException("jitter must be > 0");
        this.jitter = jitter;
        localScoreCache.clear();
    }

    private double huangLogLikelihoodFromCenteredKernels(DMatrixRMaj KxCentered,
                                                         DMatrixRMaj KzCentered,
                                                         int n,
                                                         double lambda,
                                                         double jitter) {
        if (n <= 2) return Double.NaN;
        if (!(lambda > 0) || !Double.isFinite(lambda)) return Double.NaN;
        if (!(jitter > 0) || !Double.isFinite(jitter)) return Double.NaN;

        // Huang Eq.(6): A = Kz~ + n*lambda*I
        final double nl = n * lambda;

        DMatrixRMaj A = KzCentered.copy();
        addDiagonalInPlace(A, nl);

        // Factorize A with escalating jitter (SPD expected after adding nl*I).
        CholeskyDecomposition_F64<DMatrixRMaj> cholA = DecompositionFactory_DDRM.chol(true);

        double epsA = 0.0;
        DMatrixRMaj Af = A;

        boolean ok = false;
        for (int k = 0; k < 8; k++) {
            Af = (k == 0) ? A : A.copy();
            if (k > 0) addDiagonalInPlace(Af, epsA);

            if (cholA.decompose(Af)) {
                ok = true;
                break;
            }
            epsA = (epsA == 0.0) ? jitter : epsA * 10.0;
        }
        if (!ok) return Double.NaN;

        // Solve A * D = Kx~  => D = A^{-1} Kx~
        LinearSolverDense<DMatrixRMaj> solverA = LinearSolverFactory_DDRM.symmPosDef(n);
        if (!solverA.setA(Af)) return Double.NaN;

        DMatrixRMaj D = new DMatrixRMaj(n, n);
        solverA.solve(KxCentered, D);

        // M = D^T D = Kx~ * A^{-2} * Kx~
        DMatrixRMaj M = new DMatrixRMaj(n, n);
        CommonOps_DDRM.multTransA(D, D, M);   // M = D^T * D

        // Numerical symmetry cleanup (optional but helpful).
        // If you have symmetrizeInPlace available, use it; otherwise omit.
        // symmetrizeInPlace(M);

        // Ridge determinant to avoid log|M| = -inf due to centering-induced rank deficiency.
        // This is the key practical choice.
        double logDetM = logDetSpdWithJitter(M, jitter);
        if (!Double.isFinite(logDetM)) return Double.NaN;

        // log | (n*lambda/2) * M | = n*log(n*lambda/2) + log|M|
        // (since M is n x n)
        double scale = nl / 2.0;
        if (!(scale > 0) || !Double.isFinite(scale)) return Double.NaN;

        double logDetScaled = n * Math.log(scale) + logDetM;

        // Huang Eq.(6), dropping constants:
        // S = -(n/2) * log| (n*lambda/2) * M | - n/2  (+ const)
        // The "-n/2" term does not depend on parents, so you can omit it if you want.
        return -0.5 * n * logDetScaled - 0.5 * n;
    }

    /**
     * log|M + eps I| with escalating eps until Cholesky succeeds.
     * This implements the "ridge determinant" option for PSD/near-singular M.
     */
    private static double logDetSpdWithJitter(DMatrixRMaj M, double jitter) {
        int n = M.numRows;
        if (M.numCols != n) throw new IllegalArgumentException("M must be square");

        CholeskyDecomposition_F64<DMatrixRMaj> chol = DecompositionFactory_DDRM.chol(true);

        double eps = 0.0;
        for (int k = 0; k < 10; k++) {
            DMatrixRMaj Mf = M.copy();
            if (k > 0) addDiagonalInPlace(Mf, eps);

            if (chol.decompose(Mf)) {
                DMatrixRMaj L = chol.getT(null);
                double sum = 0.0;
                for (int i = 0; i < n; i++) {
                    double di = L.get(i, i);
                    if (!(di > 0) || !Double.isFinite(di)) return Double.NaN;
                    sum += Math.log(di);
                }
                return 2.0 * sum;
            }
            eps = (eps == 0.0) ? jitter : eps * 10.0;
        }

        return Double.NaN;
    }

    private static void addDiagonalInPlace(DMatrixRMaj M, double v) {
        int n = Math.min(M.numRows, M.numCols);
        for (int i = 0; i < n; i++) {
            M.add(i, i, v);
        }
    }

    // -------------------- kernels --------------------

    private DMatrixRMaj rbfGram1D(int varIndex, int[] rows) {
        int n = (rows == null) ? nEff : rows.length;
        double[] x = extract1D(varIndex, rows);

        double bw2 = medianDistanceSquared1D(x);
        bw2 *= bandwidthMultiplier * bandwidthMultiplier;
        if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;

        // RBF: exp( -||xi-xj||^2 / (2*sigma^2) ), with sigma^2 = bw2/2  => exp( -||..||^2 / bw2 )
        double invBw = 1.0 / bw2;

        DMatrixRMaj K = new DMatrixRMaj(n, n);
        for (int i = 0; i < n; i++) {
            K.set(i, i, 1.0);
            double xi = x[i];
            for (int j = 0; j < i; j++) {
                double d = xi - x[j];
                double v = Math.exp(-(d * d) * invBw);
                K.set(i, j, v);
                K.set(j, i, v);
            }
        }
        return K;
    }

    private DMatrixRMaj rbfGramND(int[] parentIdx, int[] rows) {
        int n = (rows == null) ? nEff : rows.length;
        int d = parentIdx.length;

        // Extract Z matrix: n x d
        double[][] Z = new double[n][d];
        for (int r = 0; r < n; r++) {
            int row = (rows == null) ? r : rows[r];
            for (int j = 0; j < d; j++) {
                Z[r][j] = cols[parentIdx[j]][row];
            }
        }

        double bw2 = medianDistanceSquaredND(Z);
        bw2 *= bandwidthMultiplier * bandwidthMultiplier;
        if (!(bw2 > 0) || !Double.isFinite(bw2)) bw2 = 1.0;

        double invBw = 1.0 / bw2;

        DMatrixRMaj K = new DMatrixRMaj(n, n);
        for (int i = 0; i < n; i++) {
            K.set(i, i, 1.0);
            for (int j = 0; j < i; j++) {
                double dist2 = 0.0;
                for (int k = 0; k < d; k++) {
                    double diff = Z[i][k] - Z[j][k];
                    dist2 += diff * diff;
                }
                double v = Math.exp(-dist2 * invBw);
                K.set(i, j, v);
                K.set(j, i, v);
            }
        }

        return K;
    }

    private static DMatrixRMaj zeroGram(int n) {
        return new DMatrixRMaj(n, n);
    }

    /**
     * Centers a symmetric Gram matrix in-place: K <- H K H, where H = I - (1/n)11^T.
     */
    private static void centerInPlace(DMatrixRMaj K) {
        int n = K.numRows;
        if (n == 0) return;

        double[] rowMean = new double[n];
        double grand = 0.0;

        // Row means and grand mean.
        for (int i = 0; i < n; i++) {
            double s = 0.0;
            for (int j = 0; j < n; j++) s += K.get(i, j);
            rowMean[i] = s / n;
            grand += s;
        }
        grand /= (double) (n * n);

        // Since K is symmetric, colMean == rowMean, but we’ll keep it explicit.
        double[] colMean = rowMean;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double v = K.get(i, j) - rowMean[i] - colMean[j] + grand;
                K.set(i, j, v);
            }
        }
    }

    private int[] validRows(int[] vars) {
        // vars are column indices; keep rows where all vars are non-NaN
        int n = sampleSize;
        int[] tmp = new int[n];
        int m = 0;

        outer:
        for (int r = 0; r < n; r++) {
            for (int v : vars) {
                double val = cols[v][r];
                if (Double.isNaN(val)) continue outer;
            }
            tmp[m++] = r;
        }

        return Arrays.copyOf(tmp, m);
    }

    private static double medianDistanceSquared1D(double[] x) {
        int n = x.length;
        if (n < 3) return 1.0;

        // Collect upper-triangular distances^2 (excluding zeros if possible).
        double[] d2 = new double[n * (n - 1) / 2];
        int idx = 0;
        for (int i = 1; i < n; i++) {
            double xi = x[i];
            for (int j = 0; j < i; j++) {
                double d = xi - x[j];
                double v = d * d;
                d2[idx++] = v;
            }
        }

        Arrays.sort(d2, 0, idx);

        // Median of nonzero if possible.
        int firstPos = 0;
        while (firstPos < idx && d2[firstPos] <= 0) firstPos++;
        if (firstPos >= idx) return 1.0;

        int mid = firstPos + (idx - firstPos) / 2;
        return d2[mid];
    }

    private static double medianDistanceSquaredND(double[][] Z) {
        int n = Z.length;
        int d = Z[0].length;
        if (n < 3) return 1.0;

        double[] d2 = new double[n * (n - 1) / 2];
        int idx = 0;

        for (int i = 1; i < n; i++) {
            for (int j = 0; j < i; j++) {
                double dist2 = 0.0;
                for (int k = 0; k < d; k++) {
                    double diff = Z[i][k] - Z[j][k];
                    dist2 += diff * diff;
                }
                d2[idx++] = dist2;
            }
        }

        Arrays.sort(d2, 0, idx);

        int firstPos = 0;
        while (firstPos < idx && d2[firstPos] <= 0) firstPos++;
        if (firstPos >= idx) return 1.0;

        int mid = firstPos + (idx - firstPos) / 2;
        return d2[mid];
    }

    private double[] extract1D(int varIndex, int[] rows) {
        int n = (rows == null) ? nEff : rows.length;
        double[] x = new double[n];

        if (rows == null) {
            // Use first nEff rows (standard Tetrad assumes full rows; adjust if you prefer).
            for (int r = 0; r < n; r++) x[r] = cols[varIndex][r];
        } else {
            for (int r = 0; r < n; r++) x[r] = cols[varIndex][rows[r]];
        }

        return x;
    }

    public int[] append(int[] z, int x) {
        int[] out = Arrays.copyOf(z, z.length + 1);
        out[z.length] = x;
        return out;
    }

    @Override
    public double localScoreDiff(int x, int y) {
        return Score.super.localScoreDiff(x, y);
    }

    @Override
    public double localScore(int node, int parent) {
        return Score.super.localScore(node, parent);
    }

    @Override
    public double localScore(int node) {
        return Score.super.localScore(node);
    }

    @Override
    public Node getVariable(String targetName) {
        return Score.super.getVariable(targetName);
    }

    private static int[] concat(int i, int[] parents) {
        int[] all = new int[parents.length + 1];
        all[0] = i;
        System.arraycopy(parents, 0, all, 1, parents.length);
        return all;
    }

    private static long cacheKey(int i, int[] parents) {
        // Simple stable hash -> 64-bit key. Good enough for an in-memory cache.
        long h = 1469598103934665603L;
        h = (h ^ i) * 1099511628211L;
        for (int p : parents) h = (h ^ p) * 1099511628211L;
        return h;
    }
}
///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
////////////////  ///////////////////////////////////////////////////////////////

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estimates the mean and covariance matrix of a continuous dataset with missing values by maximum likelihood, under a
 * saturated multivariate Gaussian model, using the EM algorithm. The result is exposed as an {@link ICovarianceMatrix},
 * so that it may be handed to any score or test in Tetrad that consumes a covariance matrix (SemBicScore, ImagesScore
 * via its components, FisherZ, and so on) with no further changes.
 * <p>
 * <b>Why a saturated model.</b> In the linear-Gaussian case the saturated Gaussian is the union of all candidate DAG
 * models over the given variables, so it contains the true model whatever that model is, and imputation under it is
 * valid (if statistically inefficient) for any subsequent structure search. Imputing under a <em>sparse</em> model
 * instead would bias the completed data toward the structure assumed by that model, which is exactly the failure mode
 * to avoid when the completed data are about to be used to choose a structure. This is the cheap approximation to
 * structural EM, in which imputation under the current best graph alternates with re-search; if that is wanted, this
 * class supplies the E-step machinery for its first pass.
 * <p>
 * <b>Why this rather than deletion.</b> A search comparing parent sets A and B for a variable i requires that both be
 * scored on the same data. Under any family-wise deletion scheme the rows contributing to a family are the rows
 * complete on {i} union Pa(i), so the effective sample becomes a function of the candidate parent set: different
 * candidates are then judged against different subpopulations, which under MAR have different distributions, and the
 * search is no longer maximizing a single well-defined objective. Family-wise deletion additionally breaks score
 * equivalence, since distinct DAGs within one Markov equivalence class have distinct families and hence distinct
 * complete-case subsets. Estimating a single covariance matrix from all rows, once, before any scoring, sidesteps
 * both problems: score decomposability and score equivalence are preserved downstream, and every row informs every
 * family.
 * <p>
 * <b>Assumptions.</b> (i) The data are multivariate normal. (ii) The missingness is ignorable (MAR, with distinct
 * parameters). Neither is checkable from the data at hand, and the second is the one that bites: if values are missing
 * <em>because</em> of what they would have been, or because of an unrecorded cause of the variables under study, the
 * estimates here are biased, and the appropriate machinery is missingness graphs and recoverability rather than EM.
 * <p>
 * <b>Sample size.</b> The returned covariance matrix reports the number of rows used as its sample size. That number
 * overstates the information actually carried by the data whenever values are missing, so any BIC-style penalty
 * computed from it will under-penalize. {@link #getPairwiseCounts()} is provided for callers that wish to substitute
 * a smaller effective sample size; see also {@link #getMinPairwiseCount()}. No effective-N correction is applied here,
 * since the appropriate correction depends on the consuming score.
 * <p>
 * The numerical core, {@link #emEstimate(double[][], int, double, double)}, has no dependencies on the rest of Tetrad
 * and may be tested or reused independently.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class EmCovarianceEstimator {

    private static final double LOG_2PI = Math.log(2.0 * Math.PI);

    private final DataSet dataSet;
    private int maxIterations = 500;
    private double tolerance = 1.0e-7;
    private double ridge = 0.0;

    private Result result;
    private int[][] pairwiseCounts;

    /**
     * Constructs an estimator for the given continuous dataset, whose missing values are represented as
     * {@link Double#NaN}.
     *
     * @param dataSet The dataset. Must be continuous.
     */
    public EmCovarianceEstimator(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException("Dataset is null.");
        }

        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("This estimator requires a continuous dataset.");
        }

        this.dataSet = dataSet;
    }

    /**
     * Convenience method: estimates and returns the covariance matrix for the given dataset using default settings.
     *
     * @param dataSet The dataset.
     * @return The ML covariance matrix estimate under the saturated Gaussian model.
     */
    public static ICovarianceMatrix estimateCovariance(DataSet dataSet) {
        return new EmCovarianceEstimator(dataSet).estimate();
    }

    /**
     * Runs EM and returns the estimated covariance matrix. Repeated calls return the same result without recomputing.
     *
     * @return The estimated covariance matrix, with sample size equal to the number of rows used.
     */
    public ICovarianceMatrix estimate() {
        if (this.result == null) {
            compute();
        }

        List<Node> variables = this.dataSet.getVariables();
        return new CovarianceMatrix(variables, new Matrix(this.result.sigma), this.result.numRowsUsed);
    }

    /**
     * Returns the estimated variable means, in the order of the dataset's variables.
     *
     * @return The estimated means.
     */
    public double[] getMeans() {
        if (this.result == null) {
            compute();
        }

        return this.result.mu.clone();
    }

    /**
     * Returns the observed-data log likelihood at the final parameter estimates. This is the quantity EM maximizes;
     * it is comparable across models fit to the same data, but not across datasets.
     *
     * @return The observed-data log likelihood.
     */
    public double getLogLikelihood() {
        if (this.result == null) {
            compute();
        }

        return this.result.logLikelihood;
    }

    /**
     * Returns the number of EM iterations performed.
     *
     * @return The number of iterations.
     */
    public int getNumIterations() {
        if (this.result == null) {
            compute();
        }

        return this.result.iterations;
    }

    /**
     * Returns true if EM met the convergence tolerance before the iteration cap was reached. A false value does not
     * necessarily indicate a bad estimate--EM is often slow near the optimum--but it does warrant inspection.
     *
     * @return True if converged.
     */
    public boolean isConverged() {
        if (this.result == null) {
            compute();
        }

        return this.result.converged;
    }

    /**
     * Returns the number of rows used, i.e., the number of rows having at least one observed value. Rows with no
     * observed values carry no information about the parameters and are dropped.
     *
     * @return The number of rows used.
     */
    public int getNumRowsUsed() {
        if (this.result == null) {
            compute();
        }

        return this.result.numRowsUsed;
    }

    /**
     * Returns the matrix of pairwise observed counts, where entry (j, k) is the number of rows on which variables j
     * and k are both observed. This is a diagnostic: it indicates how much data actually informs each entry of the
     * estimated covariance matrix, which the single reported sample size cannot convey.
     *
     * @return The pairwise observed counts.
     */
    public int[][] getPairwiseCounts() {
        if (this.result == null) {
            compute();
        }

        int p = this.pairwiseCounts.length;
        int[][] copy = new int[p][];

        for (int j = 0; j < p; j++) {
            copy[j] = this.pairwiseCounts[j].clone();
        }

        return copy;
    }

    /**
     * Returns the smallest pairwise observed count, a conservative candidate for an effective sample size.
     *
     * @return The minimum over j, k of the number of rows on which both j and k are observed.
     */
    public int getMinPairwiseCount() {
        if (this.result == null) {
            compute();
        }

        int min = Integer.MAX_VALUE;

        for (int[] row : this.pairwiseCounts) {
            for (int count : row) {
                min = Math.min(min, count);
            }
        }

        return min;
    }

    /**
     * Sets the maximum number of EM iterations. The default is 500.
     *
     * @param maxIterations The maximum number of iterations; must be positive.
     */
    public void setMaxIterations(int maxIterations) {
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("Maximum iterations must be positive: " + maxIterations);
        }

        this.maxIterations = maxIterations;
        this.result = null;
    }

    /**
     * Sets the convergence tolerance, applied to the largest absolute change in any mean or covariance entry between
     * successive iterations, scaled by the corresponding standard deviations. The default is 1e-7.
     *
     * @param tolerance The tolerance; must be positive.
     */
    public void setTolerance(double tolerance) {
        if (tolerance <= 0.0) {
            throw new IllegalArgumentException("Tolerance must be positive: " + tolerance);
        }

        this.tolerance = tolerance;
        this.result = null;
    }

    /**
     * Sets a ridge added to the diagonal of the covariance estimate at each M-step, as a fraction of the mean
     * diagonal entry. The default is 0. A small positive value (e.g., 1e-8) may be used to keep the estimate
     * comfortably positive definite when variables are nearly collinear; note that any positive value biases the
     * estimate toward independence, so it should be as small as will do the job.
     *
     * @param ridge The ridge fraction; must be nonnegative.
     */
    public void setRidge(double ridge) {
        if (ridge < 0.0) {
            throw new IllegalArgumentException("Ridge must be nonnegative: " + ridge);
        }

        this.ridge = ridge;
        this.result = null;
    }

    private void compute() {
        double[][] data = this.dataSet.getDoubleData().toArray();
        this.result = emEstimate(data, this.maxIterations, this.tolerance, this.ridge);
        this.pairwiseCounts = pairwiseCounts(data);
    }

    /**
     * Counts, for each pair of columns, the number of rows on which both are observed.
     */
    private static int[][] pairwiseCounts(double[][] data) {
        int p = data[0].length;
        int[][] counts = new int[p][p];

        for (double[] row : data) {
            for (int j = 0; j < p; j++) {
                if (Double.isNaN(row[j])) continue;

                for (int k = j; k < p; k++) {
                    if (!Double.isNaN(row[k])) {
                        counts[j][k]++;
                        if (k != j) counts[k][j]++;
                    }
                }
            }
        }

        return counts;
    }

    //=========================================================================
    // Numerical core. No dependencies outside java.*, so that it can be
    // tested and reused independently of Tetrad.
    //=========================================================================

    /**
     * The result of an EM run: the estimated mean and covariance, together with convergence information.
     */
    public static final class Result {

        /**
         * The estimated means.
         */
        public final double[] mu;

        /**
         * The estimated covariance matrix.
         */
        public final double[][] sigma;

        /**
         * The observed-data log likelihood at the returned estimates, evaluated after the final M-step.
         */
        public final double logLikelihood;

        /**
         * The observed-data log likelihood at the parameters <em>entering</em> each iteration, in order, followed by
         * the value at the returned estimates. EM guarantees this sequence is nondecreasing; a decrease indicates an
         * implementation error.
         */
        public final double[] logLikelihoodTrace;

        /**
         * The number of iterations performed.
         */
        public final int iterations;

        /**
         * True if the convergence tolerance was met.
         */
        public final boolean converged;

        /**
         * The number of rows having at least one observed value.
         */
        public final int numRowsUsed;

        private Result(double[] mu, double[][] sigma, double logLikelihood, double[] logLikelihoodTrace,
                       int iterations, boolean converged, int numRowsUsed) {
            this.mu = mu;
            this.sigma = sigma;
            this.logLikelihood = logLikelihood;
            this.logLikelihoodTrace = logLikelihoodTrace;
            this.iterations = iterations;
            this.converged = converged;
            this.numRowsUsed = numRowsUsed;
        }
    }

    /**
     * Estimates the mean and covariance of a multivariate normal by EM from data with missing values, coded as
     * {@link Double#NaN}.
     * <p>
     * Each iteration computes, for every row, the conditional expectation of the missing entries given the observed
     * entries under the current parameters, together with the conditional covariance of the missing entries, and
     * accumulates the expected complete-data sufficient statistics; the M-step then sets the parameters to the
     * corresponding sample moments. Rows are grouped by missingness pattern so that the matrix factorizations are
     * done once per pattern rather than once per row, which is the difference between a usable and an unusable
     * implementation on data whose patterns are few relative to n.
     * <p>
     * The observed-data log likelihood is computed at the parameters current at the start of each iteration and
     * recorded in the trace, so that the monotonicity guaranteed by EM can be checked.
     *
     * @param data          The n x p data matrix, with NaN for missing values.
     * @param maxIterations The maximum number of iterations.
     * @param tolerance     The convergence tolerance on scaled parameter change.
     * @param ridge         A ridge added to the covariance diagonal each M-step, as a fraction of the mean diagonal.
     * @return The estimation result.
     */
    public static Result emEstimate(double[][] data, int maxIterations, double tolerance, double ridge) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data is null or empty.");
        }

        int p = data[0].length;

        // Group rows by missingness pattern, dropping rows with no observed values, which carry no information about
        // the parameters. Grouping is what makes this practical: the matrix factorizations below are done once per
        // pattern rather than once per row.
        Map<String, List<double[]>> patterns = groupByPattern(data, p);

        if (patterns.isEmpty()) {
            throw new IllegalArgumentException("Every row is entirely missing.");
        }

        List<double[]> kept = new ArrayList<>();

        for (List<double[]> rows : patterns.values()) {
            kept.addAll(rows);
        }

        int n = kept.size();

        double[] mu = new double[p];
        double[][] sigma = new double[p][p];
        initialize(kept, mu, sigma);

        double[] trace = new double[maxIterations];
        int iterations = 0;
        boolean converged = false;
        double logLikelihood = Double.NaN;

        for (int iter = 0; iter < maxIterations; iter++) {
            double[] t1 = new double[p];
            double[][] t2 = new double[p][p];
            double loglik = 0.0;

            for (Map.Entry<String, List<double[]>> entry : patterns.entrySet()) {
                String key = entry.getKey();
                List<double[]> rows = entry.getValue();

                int q = 0;

                for (int j = 0; j < p; j++) {
                    if (key.charAt(j) == '1') q++;
                }

                int r = p - q;
                int[] o = new int[q];
                int[] m = new int[r];

                for (int j = 0, a = 0, b = 0; j < p; j++) {
                    if (key.charAt(j) == '1') o[a++] = j;
                    else m[b++] = j;
                }

                // A = Sigma_OO, factored once for this pattern.
                double[][] a = submatrix(sigma, o, o);
                double[][] l = cholesky(a);
                double logDetA = 2.0 * logDiagonalSum(l);

                // Bcoef = Sigma_MO A^{-1}, and the conditional covariance of the missing block.
                double[][] z = null;      // q x r, equals A^{-1} Sigma_OM
                double[][] bCoef = null;  // r x q
                double[][] condCov = null;

                if (r > 0) {
                    double[][] sigmaOM = submatrix(sigma, o, m);
                    z = cholSolveMatrix(l, sigmaOM);
                    bCoef = transpose(z);
                    condCov = subtract(submatrix(sigma, m, m), multiply(bCoef, sigmaOM));
                }

                double[] muO = subvector(mu, o);
                double[] muM = subvector(mu, m);

                for (double[] row : rows) {
                    double[] d = new double[q];

                    for (int j = 0; j < q; j++) {
                        d[j] = row[o[j]] - muO[j];
                    }

                    double[] y = cholSolveVector(l, d);
                    double quad = 0.0;

                    for (int j = 0; j < q; j++) {
                        quad += d[j] * y[j];
                    }

                    loglik += -0.5 * (q * LOG_2PI + logDetA + quad);

                    // Expected complete-data row under the current parameters.
                    double[] e = new double[p];

                    for (int j = 0; j < q; j++) {
                        e[o[j]] = row[o[j]];
                    }

                    if (r > 0) {
                        for (int k = 0; k < r; k++) {
                            double sum = muM[k];

                            for (int j = 0; j < q; j++) {
                                sum += bCoef[k][j] * d[j];
                            }

                            e[m[k]] = sum;
                        }
                    }

                    for (int j = 0; j < p; j++) {
                        t1[j] += e[j];

                        for (int k = j; k < p; k++) {
                            t2[j][k] += e[j] * e[k];
                        }
                    }
                }

                // The conditional covariance of the missing block is the same for every row of this pattern, so it
                // enters the second moment accumulator once per row, added here in bulk.
                if (r > 0) {
                    int count = rows.size();

                    for (int j = 0; j < r; j++) {
                        for (int k = 0; k < r; k++) {
                            int jj = m[j];
                            int kk = m[k];

                            if (jj <= kk) {
                                t2[jj][kk] += count * condCov[j][k];
                            }
                        }
                    }
                }
            }

            // Fill the lower triangle of the second moment accumulator.
            for (int j = 0; j < p; j++) {
                for (int k = 0; k < j; k++) {
                    t2[j][k] = t2[k][j];
                }
            }

            trace[iter] = loglik;
            logLikelihood = loglik;
            iterations = iter + 1;

            // M-step.
            double[] muNew = new double[p];

            for (int j = 0; j < p; j++) {
                muNew[j] = t1[j] / n;
            }

            double[][] sigmaNew = new double[p][p];

            for (int j = 0; j < p; j++) {
                for (int k = 0; k < p; k++) {
                    sigmaNew[j][k] = t2[j][k] / n - muNew[j] * muNew[k];
                }
            }

            // Symmetrize against accumulated floating point asymmetry.
            for (int j = 0; j < p; j++) {
                for (int k = j + 1; k < p; k++) {
                    double avg = 0.5 * (sigmaNew[j][k] + sigmaNew[k][j]);
                    sigmaNew[j][k] = avg;
                    sigmaNew[k][j] = avg;
                }
            }

            if (ridge > 0.0) {
                double meanDiagonal = 0.0;

                for (int j = 0; j < p; j++) {
                    meanDiagonal += sigmaNew[j][j];
                }

                meanDiagonal /= p;

                for (int j = 0; j < p; j++) {
                    sigmaNew[j][j] += ridge * meanDiagonal;
                }
            }

            double change = scaledChange(mu, sigma, muNew, sigmaNew);

            mu = muNew;
            sigma = sigmaNew;

            if (change < tolerance) {
                converged = true;
                break;
            }
        }

        // The value accumulated during the E-step is the likelihood at the parameters entering the final iteration,
        // not at the parameters returned. Evaluate once more at the returned estimates so that the reported value
        // and the last trace entry correspond to the covariance matrix actually handed back.
        logLikelihood = observedLogLikelihood(patterns, p, mu, sigma);

        double[] finalTrace = new double[iterations + 1];
        System.arraycopy(trace, 0, finalTrace, 0, iterations);
        finalTrace[iterations] = logLikelihood;

        return new Result(mu, sigma, logLikelihood, finalTrace, iterations, converged, n);
    }

    /**
     * Returns the observed-data log likelihood of the given data under a multivariate normal with the given mean and
     * covariance, ignoring missing entries in the manner appropriate under MAR: each row contributes the marginal
     * density of its observed coordinates. This is the objective EM maximizes, exposed separately so that callers may
     * evaluate it at parameters of their own choosing--for instance, to form a BIC from a fitted model, or to check
     * that a reported estimate is in fact a maximum.
     *
     * @param data  The n x p data matrix, with NaN for missing values.
     * @param mu    The mean vector.
     * @param sigma The covariance matrix.
     * @return The observed-data log likelihood.
     */
    public static double observedLogLikelihood(double[][] data, double[] mu, double[][] sigma) {
        int p = mu.length;
        return observedLogLikelihood(groupByPattern(data, p), p, mu, sigma);
    }

    private static double observedLogLikelihood(Map<String, List<double[]>> patterns, int p,
                                                double[] mu, double[][] sigma) {
        double loglik = 0.0;

        for (Map.Entry<String, List<double[]>> entry : patterns.entrySet()) {
            String key = entry.getKey();
            int q = 0;

            for (int j = 0; j < p; j++) {
                if (key.charAt(j) == '1') q++;
            }

            if (q == 0) continue;

            int[] o = new int[q];

            for (int j = 0, a = 0; j < p; j++) {
                if (key.charAt(j) == '1') o[a++] = j;
            }

            double[][] l = cholesky(submatrix(sigma, o, o));
            double logDet = 2.0 * logDiagonalSum(l);
            double[] muO = subvector(mu, o);

            for (double[] row : entry.getValue()) {
                double[] d = new double[q];

                for (int j = 0; j < q; j++) {
                    d[j] = row[o[j]] - muO[j];
                }

                double[] y = cholSolveVector(l, d);
                double quad = 0.0;

                for (int j = 0; j < q; j++) {
                    quad += d[j] * y[j];
                }

                loglik += -0.5 * (q * LOG_2PI + logDet + quad);
            }
        }

        return loglik;
    }

    /**
     * Groups rows having at least one observed value by missingness pattern, keyed by a string of '1' for observed and
     * '0' for missing. A LinkedHashMap is used so that iteration order, and hence the order of floating point
     * accumulation, is deterministic across runs.
     */
    private static Map<String, List<double[]>> groupByPattern(double[][] data, int p) {
        Map<String, List<double[]>> patterns = new LinkedHashMap<>();

        for (double[] row : data) {
            StringBuilder key = new StringBuilder(p);
            boolean any = false;

            for (int j = 0; j < p; j++) {
                boolean observed = !Double.isNaN(row[j]);
                key.append(observed ? '1' : '0');
                any |= observed;
            }

            if (any) {
                patterns.computeIfAbsent(key.toString(), k -> new ArrayList<>()).add(row);
            }
        }

        return patterns;
    }

    /**
     * Sets starting values: available-case means, available-case variances on the diagonal, and zero covariances.
     * A diagonal start is positive definite whenever every variable has at least two distinct observed values, which
     * a start from the available-case covariance matrix would not guarantee, since available-case covariance matrices
     * need not be positive semidefinite.
     */
    private static void initialize(List<double[]> rows, double[] mu, double[][] sigma) {
        int p = mu.length;
        int[] counts = new int[p];

        for (double[] row : rows) {
            for (int j = 0; j < p; j++) {
                if (!Double.isNaN(row[j])) {
                    mu[j] += row[j];
                    counts[j]++;
                }
            }
        }

        for (int j = 0; j < p; j++) {
            if (counts[j] == 0) {
                throw new IllegalArgumentException("Variable at index " + j + " has no observed values.");
            }

            mu[j] /= counts[j];
        }

        for (double[] row : rows) {
            for (int j = 0; j < p; j++) {
                if (!Double.isNaN(row[j])) {
                    double d = row[j] - mu[j];
                    sigma[j][j] += d * d;
                }
            }
        }

        for (int j = 0; j < p; j++) {
            if (counts[j] < 2) {
                throw new IllegalArgumentException("Variable at index " + j + " has fewer than two observed values.");
            }

            sigma[j][j] /= counts[j] - 1;

            if (sigma[j][j] <= 0.0 || Double.isNaN(sigma[j][j])) {
                throw new IllegalArgumentException("Variable at index " + j + " has zero or undefined variance.");
            }
        }
    }

    /**
     * Returns the largest absolute change in any parameter between iterations, with covariance entries scaled by the
     * corresponding standard deviations and means by their own standard deviations, so that the tolerance is
     * interpretable independently of the units of the variables.
     */
    private static double scaledChange(double[] mu, double[][] sigma, double[] muNew, double[][] sigmaNew) {
        int p = mu.length;
        double max = 0.0;

        for (int j = 0; j < p; j++) {
            double scale = Math.sqrt(Math.max(sigmaNew[j][j], 1.0e-300));
            max = Math.max(max, Math.abs(muNew[j] - mu[j]) / scale);
        }

        for (int j = 0; j < p; j++) {
            for (int k = j; k < p; k++) {
                double scale = Math.sqrt(Math.max(sigmaNew[j][j] * sigmaNew[k][k], 1.0e-300));
                max = Math.max(max, Math.abs(sigmaNew[j][k] - sigma[j][k]) / scale);
            }
        }

        return max;
    }

    //=========================================================================
    // Small dense linear algebra helpers, on plain arrays.
    //=========================================================================

    /**
     * Returns the lower triangular Cholesky factor of a symmetric positive definite matrix, retrying with escalating
     * jitter on the diagonal if the factorization fails. Jitter is reported by way of an exception only when it
     * exceeds a level at which the matrix should be regarded as singular.
     */
    private static double[][] cholesky(double[][] a) {
        int n = a.length;

        if (n == 0) {
            return new double[0][0];
        }

        double trace = 0.0;

        for (int i = 0; i < n; i++) {
            trace += a[i][i];
        }

        double scale = Math.max(trace / n, 1.0e-300);

        for (int attempt = 0; attempt < 12; attempt++) {
            double jitter = attempt == 0 ? 0.0 : scale * Math.pow(10.0, -14.0 + attempt);
            double[][] l = new double[n][n];
            boolean ok = true;

            for (int i = 0; i < n && ok; i++) {
                for (int j = 0; j <= i; j++) {
                    double sum = a[i][j] + (i == j ? jitter : 0.0);

                    for (int k = 0; k < j; k++) {
                        sum -= l[i][k] * l[j][k];
                    }

                    if (i == j) {
                        if (sum <= 0.0 || Double.isNaN(sum)) {
                            ok = false;
                            break;
                        }

                        l[i][j] = Math.sqrt(sum);
                    } else {
                        l[i][j] = sum / l[j][j];
                    }
                }
            }

            if (ok) {
                return l;
            }
        }

        throw new IllegalArgumentException("Covariance submatrix is singular or indefinite; the variables may be "
                + "collinear, or the missingness pattern may leave some parameters unidentified. Consider setting a "
                + "small ridge.");
    }

    private static double logDiagonalSum(double[][] l) {
        double sum = 0.0;

        for (int i = 0; i < l.length; i++) {
            sum += Math.log(l[i][i]);
        }

        return sum;
    }

    private static double[] cholSolveVector(double[][] l, double[] b) {
        int n = b.length;
        double[] y = new double[n];

        for (int i = 0; i < n; i++) {
            double sum = b[i];

            for (int k = 0; k < i; k++) {
                sum -= l[i][k] * y[k];
            }

            y[i] = sum / l[i][i];
        }

        double[] x = new double[n];

        for (int i = n - 1; i >= 0; i--) {
            double sum = y[i];

            for (int k = i + 1; k < n; k++) {
                sum -= l[k][i] * x[k];
            }

            x[i] = sum / l[i][i];
        }

        return x;
    }

    private static double[][] cholSolveMatrix(double[][] l, double[][] b) {
        int n = b.length;
        int c = n == 0 ? 0 : b[0].length;
        double[][] x = new double[n][c];

        for (int col = 0; col < c; col++) {
            double[] rhs = new double[n];

            for (int i = 0; i < n; i++) {
                rhs[i] = b[i][col];
            }

            double[] sol = cholSolveVector(l, rhs);

            for (int i = 0; i < n; i++) {
                x[i][col] = sol[i];
            }
        }

        return x;
    }

    private static double[][] submatrix(double[][] m, int[] rows, int[] cols) {
        double[][] s = new double[rows.length][cols.length];

        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                s[i][j] = m[rows[i]][cols[j]];
            }
        }

        return s;
    }

    private static double[] subvector(double[] v, int[] idx) {
        double[] s = new double[idx.length];

        for (int i = 0; i < idx.length; i++) {
            s[i] = v[idx[i]];
        }

        return s;
    }

    private static double[][] transpose(double[][] m) {
        int r = m.length;
        int c = r == 0 ? 0 : m[0].length;
        double[][] t = new double[c][r];

        for (int i = 0; i < r; i++) {
            for (int j = 0; j < c; j++) {
                t[j][i] = m[i][j];
            }
        }

        return t;
    }

    private static double[][] multiply(double[][] a, double[][] b) {
        int r = a.length;
        int inner = r == 0 ? 0 : a[0].length;
        int c = inner == 0 ? 0 : b[0].length;
        double[][] m = new double[r][c];

        for (int i = 0; i < r; i++) {
            for (int k = 0; k < inner; k++) {
                double aik = a[i][k];

                for (int j = 0; j < c; j++) {
                    m[i][j] += aik * b[k][j];
                }
            }
        }

        return m;
    }

    private static double[][] subtract(double[][] a, double[][] b) {
        int r = a.length;
        int c = r == 0 ? 0 : a[0].length;
        double[][] m = new double[r][c];

        for (int i = 0; i < r; i++) {
            for (int j = 0; j < c; j++) {
                m[i][j] = a[i][j] - b[i][j];
            }
        }

        return m;
    }
}

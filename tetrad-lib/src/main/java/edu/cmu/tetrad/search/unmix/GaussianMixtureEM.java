/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.util.TetradLogger;

import java.util.Arrays;

/**
 * Lightweight Gaussian Mixture EM with full or diagonal covariance.
 */
public final class GaussianMixtureEM {

    /**
     * Constructs a new instance of the GaussianMixtureEM class.
     * <p>
     * This constructor initializes the object that implements the Expectation-Maximization (EM) algorithm for training
     * a Gaussian Mixture Model (GMM). The EM algorithm iteratively fits GMMs to input data by alternating between
     * estimating soft assignments of data points to clusters (E-step) and maximizing the model parameters based on
     * these assignments (M-step).
     * <p>
     * The GaussianMixtureEM is designed for clustering and density estimation in multidimensional data.
     */
    public GaussianMixtureEM() {
    }

    /**
     * Fits a Gaussian Mixture Model (GMM) to the input data using the Expectation-Maximization (EM) algorithm.
     * <p>
     * This method estimates the parameters of a GMM by alternating between the E-step, which computes the soft
     * assignments of data points to clusters, and the M-step, which updates the model parameters based on these
     * assignments. It also includes features such as annealing and initialization using K-means.
     *
     * @param X   A 2D array representing the input data, where each row corresponds to a d-dimensional data point
     *            and each column corresponds to a feature.
     * @param cfg Configuration object that specifies the settings for fitting the model, including the number of
     *            clusters (K), initialization parameters, convergence criteria, and optimization-related parameters.
     * @return    A trained {@code Model} object representing the fitted Gaussian Mixture Model, which includes the
     *            learned parameters (weights, means, covariances), log-likelihood of the data, and final responsibilities.
     * @throws IllegalArgumentException If the input data is empty, or if the number of clusters (K) in the configuration
     *                                  is less than or equal to zero.
     */
    public static Model fit(double[][] X, Config cfg) {
        int n = X.length;
        int d = n == 0 ? 0 : X[0].length;
        if (n == 0 || cfg.K <= 0) throw new IllegalArgumentException("Empty data or K<=0");

        // --- init via k-means (hard) then moments
        int[] z = KMeans.clusterWithRestarts(X, cfg.K, /*iters*/50, cfg.seed, cfg.kmeansRestarts).labels;
        double[] w = new double[cfg.K];
        double[][] mu = new double[cfg.K][d];
        double[][][] S = allocCov(cfg.K, d, cfg.covType);
        hardMoments(X, z, w, mu, S, cfg.covType, cfg.ridge);

        // working responsibilities
        double[][] R = new double[n][cfg.K];
        double prevLL = Double.NEGATIVE_INFINITY;

        for (int it = 0; it < cfg.maxIters; it++) {
            // --- annealing schedule: beta â (0,1], climbs to 1 across annealSteps
            double beta = 1.0;
            if (cfg.annealSteps > 0) {
                double t = Math.min(1.0, (it + 1) / (double) cfg.annealSteps);
                beta = cfg.annealStartT + t * (1.0 - cfg.annealStartT);
                beta = Math.max(1e-6, Math.min(1.0, beta));
            }

            // E-step (tempered)
            double llTemp = eStep(X, w, mu, S, cfg.covType, R, beta);

            // M-step (with shrinkage / ridge knobs)
            mStep(X, R, w, mu, S, cfg.covType, cfg.ridge, cfg.covShrinkage, cfg.covRidgeRel);

            // --- logging hook ---
            if (cfg.isLogIntermediate()) {
                logState(it, llTemp, w, mu, S, R);
            }

            // convergence check uses the same objective we just optimized (tempered LL)
            if (Math.abs(llTemp - prevLL) < cfg.tol * (1 + Math.abs(prevLL))) {
                prevLL = llTemp;
                break;
            }
            prevLL = llTemp;
        }

        // --- Finalize: recompute TRUE (untempered) responsibilities & log-likelihood
        double[][] Rfinal = new double[n][cfg.K];
        double trueLL = eStep(X, w, mu, S, cfg.covType, Rfinal, 1.0);

        return new Model(cfg.K, d, cfg.covType, w, mu, S, trueLL, Rfinal);
    }

    private static void logState(int iter, double ll, double[] w, double[][] mu, double[][][] S, double[][] R) {
        TetradLogger.getInstance().log(String.format("Iter %d  LL=%.4f  Weights=%s  Means=%s", iter, ll, Arrays.toString(w), Arrays.deepToString(mu)));
        // You could also dump covariances or a small slice of R if desired
    }

    private static double eStep(double[][] X, double[] w, double[][] mu, double[][][] S, CovarianceType covType, double[][] R, double beta) {
        int n = X.length, d = X[0].length, K = w.length;
        double ll = 0.0;
        double[] logw = new double[K];
        for (int k = 0; k < K; k++) logw[k] = Math.log(Math.max(w[k], 1e-12));

        for (int i = 0; i < n; i++) {
            double[] xi = X[i];
            double[] logp = new double[K];
            double max = Double.NEGATIVE_INFINITY;
            for (int k = 0; k < K; k++) {
                logp[k] = logGaussian(xi, mu[k], S[k], covType) + logw[k];
                if (logp[k] > max) max = logp[k];
            }
            // temper the posteriors: scale log-likelihoods by beta â (0,1], betaâ1 recovers standard EM
            for (int k = 0; k < K; k++) logp[k] = beta * logp[k];

            // log-sum-exp
            double maxT = Double.NEGATIVE_INFINITY;
            for (int k = 0; k < K; k++) if (logp[k] > maxT) maxT = logp[k];
            double sum = 0.0;
            for (int k = 0; k < K; k++) sum += Math.exp(logp[k] - maxT);
            double logsum = maxT + Math.log(sum);

            for (int k = 0; k < K; k++) R[i][k] = Math.exp(logp[k] - logsum);
            ll += logsum;
        }
        return ll;
    }

    private static double logGaussian(double[] x, double[] m, double[][] S, CovarianceType type) {
        int d = x.length;
        double quad, logdet;
        if (type == CovarianceType.DIAGONAL) {
            quad = 0.0;
            logdet = 0.0;
            for (int j = 0; j < d; j++) {
                double v = Math.max(S[j][0], 1e-12);
                double z = x[j] - m[j];
                quad += (z * z) / v;
                logdet += Math.log(v);
            }
        } else {
            // FULL: use simple Cholesky
            Chol ch = Chol.decompose(S);
            double[] y = ch.solve(sub(x, m));
            quad = dot(sub(x, m), y);
            logdet = 2.0 * ch.logDiagSum;
        }
        return -0.5 * (d * Math.log(2 * Math.PI) + logdet + quad);
    }

    // ---------- M-step ----------
    // Backward-compatible wrapper (uses only absolute ridge; no shrinkage / relative ridge)
    private static void mStep(double[][] X, double[][] R, double[] w, double[][] mu, double[][][] S, CovarianceType covType, double ridgeAbs) {
        mStep(X, R, w, mu, S, covType, ridgeAbs, /*shrinkage*/ 0.0, /*ridgeRel*/ 0.0);
    }

    /**
     * Full M-step with covariance shrinkage and relative ridge. - shrinkage Î» in [0,1]: shrinks Î£_k toward spherical
     * target (FULL) or mean variance (DIAGONAL) - ridgeAbs â¥ 0: absolute ridge added to diagonal - ridgeRel â¥ 0:
     * relative ridge = ridgeRel * Ï (Ï = avg variance) added to diagonal
     */
    private static void mStep(double[][] X, double[][] R, double[] w, double[][] mu, double[][][] S, CovarianceType covType, double ridgeAbs, double shrinkage, double ridgeRel) {
        final int n = X.length;
        final int d = (n == 0) ? 0 : X[0].length;
        final int K = w.length;

        // --- soft counts and means ---
        double[] rk = new double[K];
        for (int k = 0; k < K; k++) {
            rk[k] = 0.0;
            Arrays.fill(mu[k], 0.0);
        }

        for (int i = 0; i < n; i++) {
            double[] xi = X[i];
            double[] Ri = R[i];
            for (int k = 0; k < K; k++) {
                double rik = Ri[k];
                rk[k] += rik;
                for (int j = 0; j < d; j++) mu[k][j] += rik * xi[j];
            }
        }

        double sumw = 0.0;
        for (int k = 0; k < K; k++) {
            double denom = Math.max(rk[k], 1e-12);
            for (int j = 0; j < d; j++) mu[k][j] /= denom;

            w[k] = denom / Math.max(n, 1);
            w[k] = Math.max(w[k], 1e-12);
            sumw += w[k];
        }
        for (int k = 0; k < K; k++) w[k] /= sumw;

        // --- covariances (raw ML) ---
        if (covType == CovarianceType.DIAGONAL) {
            for (int k = 0; k < K; k++)
                for (int j = 0; j < d; j++)
                    S[k][j][0] = 0.0;

            for (int i = 0; i < n; i++) {
                double[] xi = X[i];
                double[] Ri = R[i];
                for (int k = 0; k < K; k++) {
                    double rik = Ri[k];
                    if (rik == 0.0) continue;
                    for (int j = 0; j < d; j++) {
                        double z = xi[j] - mu[k][j];
                        S[k][j][0] += rik * z * z;
                    }
                }
            }

            // --- shrinkage + ridge ---
            double lam = Math.min(Math.max(shrinkage, 0.0), 1.0);
            double rel = Math.max(ridgeRel, 0.0);
            for (int k = 0; k < K; k++) {
                double denom = Math.max(rk[k], 1e-12);

                // raw variances
                double meanVar = 0.0;
                for (int j = 0; j < d; j++) {
                    S[k][j][0] = S[k][j][0] / denom;
                    meanVar += S[k][j][0];
                }
                meanVar /= Math.max(1, d);

                // shrink toward mean variance
                if (lam > 0.0) {
                    for (int j = 0; j < d; j++) {
                        S[k][j][0] = (1.0 - lam) * S[k][j][0] + lam * meanVar;
                    }
                }

                // absolute + relative ridge
                double bump = ridgeAbs + rel * meanVar;
                for (int j = 0; j < d; j++) S[k][j][0] += bump;
            }

        } else { // FULL
            for (int k = 0; k < K; k++)
                for (int a = 0; a < d; a++)
                    Arrays.fill(S[k][a], 0.0);

            for (int i = 0; i < n; i++) {
                double[] xi = X[i];
                double[] Ri = R[i];
                for (int k = 0; k < K; k++) {
                    double rik = Ri[k];
                    if (rik == 0.0) continue;
                    for (int a = 0; a < d; a++) {
                        double za = xi[a] - mu[k][a];
                        for (int b = 0; b < d; b++) {
                            double zb = xi[b] - mu[k][b];
                            S[k][a][b] += rik * za * zb;
                        }
                    }
                }
            }

            double lam = Math.min(Math.max(shrinkage, 0.0), 1.0);
            double rel = Math.max(ridgeRel, 0.0);
            for (int k = 0; k < K; k++) {
                double denom = Math.max(rk[k], 1e-12);

                // raw ML covariance
                double trace = 0.0;
                for (int a = 0; a < d; a++) {
                    for (int b = 0; b < d; b++) S[k][a][b] /= denom;
                    trace += S[k][a][a];
                }
                double tau = trace / Math.max(1, d); // avg variance

                // shrink toward spherical tau*I
                if (lam > 0.0) {
                    for (int a = 0; a < d; a++) {
                        for (int b = 0; b < d; b++) {
                            double target = (a == b) ? tau : 0.0;
                            S[k][a][b] = (1.0 - lam) * S[k][a][b] + lam * target;
                        }
                    }
                }

                // absolute + relative ridge
                double bump = ridgeAbs + rel * tau;
                for (int a = 0; a < d; a++) S[k][a][a] += bump;
            }
        }
    }

    // ---------- E-step ----------

    // ---------- init moments from hard labels ----------
    private static void hardMoments(double[][] X, int[] z, double[] w, double[][] mu, double[][][] S, CovarianceType covType, double ridge) {
        final int n = X.length;
        final int d = (n == 0) ? 0 : X[0].length;
        final int K = w.length;

        // counts per cluster
        int[] cnt = new int[K];
        for (int i = 0; i < n; i++) {
            int k = z[i];
            if (k >= 0 && k < K) cnt[k]++;
        }

        // weights: allow empty clusters but give them tiny mass; then normalize
        double sumw = 0.0;
        for (int k = 0; k < K; k++) {
            w[k] = (cnt[k] > 0) ? (cnt[k] / (double) n) : 1e-12;
            sumw += w[k];
        }
        if (sumw <= 0) sumw = 1.0;
        for (int k = 0; k < K; k++) w[k] /= sumw;

        // means
        for (int k = 0; k < K; k++) Arrays.fill(mu[k], 0.0);
        for (int i = 0; i < n; i++) {
            int k = z[i];
            if (k < 0 || k >= K) continue;
            double[] xi = X[i];
            for (int j = 0; j < d; j++) mu[k][j] += xi[j];
        }
        for (int k = 0; k < K; k++) {
            double denom = Math.max(cnt[k], 1); // avoid div-by-zero
            for (int j = 0; j < d; j++) mu[k][j] /= denom;
        }

        // covariances
        if (covType == CovarianceType.DIAGONAL) {
            for (int k = 0; k < K; k++) {
                for (int j = 0; j < d; j++) {
                    double s2 = 0.0;
                    for (int i = 0; i < n; i++) {
                        if (z[i] != k) continue;
                        double diff = X[i][j] - mu[k][j];
                        s2 += diff * diff;
                    }
                    double denom = Math.max(cnt[k], 1);
                    S[k][j][0] = s2 / denom + ridge;
                }
            }
        } else { // FULL
            for (int k = 0; k < K; k++) {
                // zero S_k
                for (int a = 0; a < d; a++) Arrays.fill(S[k][a], 0.0);

                for (int i = 0; i < n; i++) {
                    if (z[i] != k) continue;
                    for (int a = 0; a < d; a++) {
                        double za = X[i][a] - mu[k][a];
                        for (int b = 0; b < d; b++) {
                            double zb = X[i][b] - mu[k][b];
                            S[k][a][b] += za * zb;
                        }
                    }
                }
                double denom = Math.max(cnt[k], 1);
                for (int a = 0; a < d; a++) {
                    for (int b = 0; b < d; b++) S[k][a][b] /= denom;
                    S[k][a][a] += ridge; // regularize diagonal
                }
            }
        }
    }

    private static double[][][] allocCov(int K, int d, CovarianceType type) {
        double[][][] S = new double[K][][];
        for (int k = 0; k < K; k++) {
            if (type == CovarianceType.DIAGONAL) {
                S[k] = new double[d][1];
            } else {
                S[k] = new double[d][d];
            }
        }
        return S;
    }

    // ---------- M-step ----------

    private static double[] sub(double[] a, double[] b) {
        double[] r = new double[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[i] - b[i];
        return r;
    }

    // ---------- init moments from hard labels ----------

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    // ---------- helpers ----------

    private static void zero(double[][] A) {
        for (double[] row : A) Arrays.fill(row, 0.0);
    }

    /**
     * Computes the probability density function (PDF) of a multivariate Gaussian distribution.
     * <p>
     * The method calculates the likelihood of the data point `x` under a Gaussian distribution specified by the mean
     * vector and covariance matrix. It supports both full and diagonal covariance types.
     *
     * @param x       The data point array (d-dimensional vector) for which the PDF is computed.
     * @param mean    The mean vector of the Gaussian distribution (d-dimensional vector).
     * @param cov     The covariance matrix of the Gaussian distribution. For diagonal covariance, only the diagonal
     *                elements are used.
     * @param covType The type of the covariance matrix, either FULL or DIAGONAL.
     * @return The value of the Gaussian PDF for the given data point `x`.
     */
    public static double gaussianPdf(double[] x, double[] mean, double[][] cov, CovarianceType covType) {
        int d = x.length;
        double[] diff = new double[d];
        for (int i = 0; i < d; i++) diff[i] = x[i] - mean[i];

        if (covType == CovarianceType.DIAGONAL) {
            double logdet = 0.0, quad = 0.0;
            for (int j = 0; j < d; j++) {
                double v = Math.max(cov[j][0], 1e-12);
                double z = diff[j];
                quad += (z * z) / v;
                logdet += Math.log(v);
            }
            double logp = -0.5 * (d * Math.log(2 * Math.PI) + logdet + quad);
            return Math.exp(logp);
        } else {
            Chol ch = Chol.decompose(cov);
            double[] y = ch.solve(diff);
            double quad = dot(diff, y);
            double logdet = 2.0 * ch.logDiagSum;
            double logp = -0.5 * (d * Math.log(2 * Math.PI) + logdet + quad);
            return Math.exp(logp);
        }
    }

    /**
     * Specifies the type of covariance matrix used in the Gaussian Mixture Model (GMM) implemented by the
     * GaussianMixtureEM class.
     * <p>
     * The covariance type determines the complexity and flexibility of the model: - FULL: Each component in the GMM has
     * its own full covariance matrix. - DIAGONAL: Each component in the GMM has a diagonal covariance matrix.
     * <p>
     * The choice of covariance type affects the computational cost and the type of relationships that can be modeled by
     * the GMM.
     */
    public enum CovarianceType {

        /**
         * Represents the FULL covariance type for the Gaussian Mixture Model (GMM).
         * <p>
         * The FULL covariance type specifies that each component in the GMM has its own full covariance matrix. This
         * allows for the modeling of complex relationships between features but increases computational cost compared
         * to simpler covariance types, such as DIAGONAL.
         */
        FULL,

        /**
         * Represents the DIAGONAL covariance type for the Gaussian Mixture Model (GMM).
         * <p>
         * The DIAGONAL covariance type specifies that each component in the GMM has a diagonal covariance matrix. This
         * type of covariance matrix assumes that the features of the data are uncorrelated and have different
         * variances.
         * <p>
         * Using a diagonal covariance matrix reduces the complexity and computational cost of the model compared to a
         * FULL covariance matrix. However, it imposes stronger assumptions on the data and may not capture correlations
         * between features effectively.
         */
        DIAGONAL
    }

    /**
     * Represents the Gaussian Mixture Model (GMM) computed and used in the GaussianMixtureEM class. This class
     * encapsulates the parameters of the GMM, as well as related information such as soft cluster assignments
     * (responsibilities) and the model's overall log-likelihood.
     * <p>
     * The parameters of the GMM include the number of components (K), the dimensionality of the data, the component
     * weights, the mean vectors, and the covariance matrices. The type of covariance matrices is also specified (e.g.,
     * full or diagonal).
     */
    public static final class Model {
        /**
         * Represents the number of mixture components in the Gaussian Mixture Model (GMM).
         * <p>
         * This variable defines the distinct number of clusters or distributions that comprise the overall mixture
         * model. Each component is characterized by its own set of parameters, such as mean, covariance, and weight,
         * and contributes to the overall probability density represented by the model.
         * <p>
         * K is a critical parameter in the construction and evaluation of the GMM and directly impacts the model's
         * complexity and its ability to fit the training data.
         */
        public final int K;
        /**
         * Represents the dimensionality of the data in the Gaussian Mixture Model (GMM).
         * <p>
         * This value defines the number of features or dimensions in the data that the model is designed to handle. It
         * is specified during the initialization of the model and remains constant throughout its lifecycle.
         */
        public final int d;
        /**
         * The weights for each mixture component in the Gaussian Mixture Model (GMM).
         * <p>
         * This array specifies the mixing weights associated with the components of the model. The size of the array
         * corresponds to the number of mixture components (K) in the model. Each weight represents the proportional
         * contribution of a particular component to the overall model and must satisfy the condition that the weights
         * sum to 1.
         */
        public final double[] weights;          // K
        /**
         * The mean vectors of the mixture components in the Gaussian Mixture Model (GMM).
         * <p>
         * Each row of the array represents the mean vector of a single mixture component, and the number of rows
         * corresponds to the number of mixture components (K). Each row's length corresponds to the dimensionality of
         * the data (d). This parameter defines the center of the clusters formed by each component in the feature
         * space.
         * <p>
         * Dimensions: K x d.
         */
        public final double[][] means;          // K x d
        /**
         * The covariance matrices of the mixture components in the Gaussian Mixture Model (GMM).
         *
         * <p>This is a three-dimensional array with dimensions <code>K × d × d</code>, where:</p>
         * <ul>
         *   <li><b>K</b> represents the number of mixture components.</li>
         *   <li><b>d</b> represents the dimensionality of the data.</li>
         * </ul>
         *
         * <p>The structure of the covariance values depends on the covariance type (<code>covType</code>):</p>
         * <ul>
         *   <li>If <code>covType</code> is <b>FULL</b>, each covariance matrix is of size <code>d × d</code>
         *       and stored in full in this array.</li>
         *   <li>If <code>covType</code> is <b>DIAGONAL</b>, only the diagonal elements of each covariance
         *       matrix are stored, and they are represented as <code>[k][j][0]</code>, where <code>k</code>
         *       is the component index and <code>j</code> is the dimension.</li>
         * </ul>
         */
        public final double[][][] covs;         // K x d x d  (or diagonal in [k][j][0] if DIAGONAL)
        /**
         * Specifies the type of covariance matrix used in the Gaussian Mixture Model (GMM).
         * <p>
         * The covariance type determines the structure of the covariance matrices for the mixture components. It can
         * either be FULL, where each component has its own full covariance matrix, or DIAGONAL, where each component
         * has a diagonal covariance matrix that assumes uncorrelated features.
         * <p>
         * The choice of covariance type affects the model's flexibility and computational complexity.
         */
        public final CovarianceType covType;
        /**
         * Represents the overall log-likelihood of the Gaussian Mixture Model (GMM).
         * <p>
         * The log-likelihood is a measure of how well the model explains the given data, with higher values indicating
         * a better fit. It is computed based on the model parameters (e.g., weights, means, and covariance matrices)
         * and the observed data.
         * <p>
         * This value is commonly used for tasks such as model evaluation and comparison, and it may also serve as input
         * to model selection criteria.
         */
        public final double logLikelihood;
        /**
         * A two-dimensional array representing the responsibilities or soft cluster assignments for each data point in
         * the Gaussian Mixture Model (GMM).
         *
         * <p>Each row corresponds to a data point, and each column corresponds to a
         * mixture component. The value at position <code>[i][j]</code> represents the responsibility (i.e., the
         * probability) of the <code>j</code>-th mixture component for the <code>i</code>-th data point.</p>
         *
         * <p><b>Dimensions:</b></p>
         * <ul>
         *   <li><b>n</b> – Number of data points.</li>
         *   <li><b>K</b> – Number of mixture components.</li>
         * </ul>
         */
        public final double[][] responsibilities; // n x K

        /**
         * Constructs an instance of the Gaussian Mixture Model (GMM) with the specified parameters.
         *
         * @param K       the number of mixture components
         * @param d       the dimensionality of the data
         * @param covType the type of covariance matrix used (FULL or DIAGONAL)
         * @param w       the weights of the mixture components
         * @param mu      the mean vectors of the mixture components
         * @param covs    the covariance matrices of the mixture components
         * @param ll      the overall log-likelihood of the model
         * @param resp    the responsibilities (soft cluster assignments) for each data point
         */
        public Model(int K, int d, CovarianceType covType, double[] w, double[][] mu, double[][][] covs, double ll, double[][] resp) {
            this.K = K;
            this.d = d;
            this.covType = covType;
            this.weights = w;
            this.means = mu;
            this.covs = covs;
            this.logLikelihood = ll;
            this.responsibilities = resp;
        }

        /**
         * Computes the Bayesian Information Criterion (BIC) for the Gaussian Mixture Model (GMM) given the number of
         * rows used to fit the model.
         * <p>
         * The BIC is a criterion used for model selection based on the trade-off between model fit (log-likelihood) and
         * model complexity (number of parameters). A lower BIC indicates a better balance between fit and complexity.
         *
         * @param n the number of rows used to fit the model (i.e., the sample size)
         * @return the Bayesian Information Criterion (BIC) value
         */
        public double bic(int n) {
            // n = number of rows used to fit the model
            int d = this.d;
            int K = this.K;

            int covParamsPerComp = (covType == CovarianceType.FULL) ? (d * (d + 1)) / 2 : d; // DIAGONAL

            int numParams = (K - 1)            // weights (sum to 1)
                            + K * d                    // means
                            + K * covParamsPerComp;    // covariance params

            return -2.0 * this.logLikelihood + numParams * Math.log(Math.max(1, n));
        }
    }

    /**
     * Configuration class for the Gaussian Mixture Model (GMM) Expectation-Maximization (EM) algorithm.
     * <p>
     * The Config class contains various tunable parameters that control the behavior of the GMM fitting process,
     * including options for covariance type, maximum iterations, tolerance, and regularization.
     */
    public static final class Config {
        /**
         * The number of mixture components in the Gaussian Mixture Model (GMM).
         * <p>
         * This parameter determines the number of clusters to fit during the Expectation-Maximization (EM) algorithm. A
         * higher value of K results in more finely-grained cluster representations but increases the computational
         * complexity of the fitting process.
         */
        public int K;
        /**
         * Specifies the type of covariance matrix to be used in the Gaussian Mixture Model (GMM)
         * Expectation-Maximization (EM) algorithm.
         *
         * <p>This variable defines the covariance structure for each component in the GMM:</p>
         * <ul>
         *   <li><b>FULL</b>: Each component has its own full covariance matrix, allowing for the modeling
         *       of complex feature relationships.</li>
         *   <li><b>DIAGONAL</b>: Each component has a diagonal covariance matrix, which assumes uncorrelated
         *       features with different variances, reducing computational complexity.</li>
         * </ul>
         *
         * <p>The choice of covariance type affects the model's flexibility, computational cost, and
         * the ability to capture feature relationships.</p>
         */
        public CovarianceType covType = CovarianceType.DIAGONAL;
        /**
         * Specifies the maximum number of iterations for the Expectation-Maximization (EM) algorithm in the Gaussian
         * Mixture Model (GMM) fitting process.
         *
         * <p>This parameter determines the upper limit on the number of iterations the EM algorithm can
         * execute before it terminates, regardless of whether it has converged. Adjusting this value can balance
         * computational efficiency and model accuracy:</p>
         *
         * <ul>
         *   <li>A lower value may result in premature termination before convergence, potentially leading
         *       to suboptimal cluster fitting.</li>
         *   <li>A higher value allows more iterations for convergence but increases computation time.</li>
         * </ul>
         *
         * <p><b>Default value:</b> 200</p>
         */
        public int maxIters = 200;
        /**
         * A threshold value used for numerical tolerance in computations to determine acceptable precision or
         * convergence in iterative algorithms. Its default value is set to 1e-5.
         */
        public double tol = 1e-5;
        /**
         * The seed value used for random number generation within the configuration. It ensures reproducibility of
         * results by fixing the initial state of the random number generator.
         */
        public long seed = 13L;
        /**
         * Represents the ridge parameter used as a regularization term in covariance computation. This value helps to
         * improve numerical stability and prevent overfitting by adding a small constant to the diagonal of the
         * covariance matrix. It is particularly useful when working with data that might lead to singular or
         * near-singular covariance matrices.
         */
        public double ridge = 1e-6;  // covariance regularizer
        /**
         * Specifies the number of restart attempts for the k-means algorithm during initialization. This value
         * determines how many times the k-means algorithm will be run with different initializations to potentially
         * improve the quality of clustering results. A higher value increases the chances of finding a better
         * clustering but may also increase the computational cost.
         */
        public int kmeansRestarts = 5; // for init
        /**
         * The relative weighting of the covariance ridge regularization term. This value is typically used to ensure
         * numerical stability during covariance matrix computations by adding a scaled identity matrix to the
         * covariance matrix. A higher value increases the regularization effect, while a lower value decreases it. The
         * exact interpretation and impact depend on the context of its use within the algorithm.
         */
        public double covRidgeRel;
        /**
         * Represents the shrinkage parameter used in estimating covariance matrices. Shrinkage is applied to regularize
         * covariance matrix calculations and can be used to improve stability, particularly for high-dimensional data
         * or small sample sizes. Typically, this parameter is a value between 0 and 1, where 0 indicates no shrinkage
         * and 1 corresponds to complete shrinkage to the identity matrix.
         */
        public double covShrinkage;
        /**
         * The number of annealing steps to be performed during the specified optimization process. This variable
         * controls the level of discrete temperature decreases in an annealing-based optimization.
         */
        public int annealSteps;
        /**
         * The starting temperature used for the annealing process. This variable defines the initial temperature and is
         * typically used in optimization algorithms that utilize simulated annealing.
         */
        public double annealStartT;
        /**
         * Indicates whether intermediate steps of the computation should be logged.
         * <p>
         * This variable can be used to enable or disable logging of intermediate states, which may be useful for
         * debugging or monitoring the progress of computations. By default, it is set to {@code false}.
         */
        private boolean logIntermediate = false;

        /**
         * Default constructor for the Config class. Initializes a new instance of the Config object with default
         * settings.
         */
        public Config() {
        }

        /**
         * Creates and returns a copy of this configuration instance. The copy will have the same values for all fields
         * as the original instance.
         *
         * @return a new Config object that is a copy of the current instance
         */
        public Config copy() {
            Config copy = new Config();
            copy.K = K;
            copy.covType = covType;
            copy.maxIters = maxIters;
            copy.tol = tol;
            copy.ridge = ridge;
            copy.kmeansRestarts = kmeansRestarts;
            copy.covRidgeRel = covRidgeRel;
            copy.covShrinkage = covShrinkage;
            copy.annealSteps = annealSteps;
            copy.annealStartT = annealStartT;
            return copy;

        }

        /**
         * Returns whether intermediate steps of the computation should be logged.
         *
         * @return true if intermediate steps should be logged, false otherwise
         */
        public boolean isLogIntermediate() {
            return logIntermediate;
        }

        /**
         * Sets whether intermediate steps of the computation should be logged.
         *
         * @param logIntermediate true to enable logging, false to disable
         */
        public void setLogIntermediate(boolean logIntermediate) {
            this.logIntermediate = logIntermediate;
        }
    }

    /**
     * tiny Cholesky with log|L| tracked; minimal checks, assume SPD with ridge.
     */
    private static final class Chol {
        final double[][] L;
        final int n;
        final double logDiagSum;

        private Chol(double[][] L, double logDiagSum) {
            this.L = L;
            this.n = L.length;
            this.logDiagSum = logDiagSum;
        }

        static Chol decompose(double[][] A) {
            int n = A.length;
            double[][] L = new double[n][n];
            double logDiagSum = 0.0;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j <= i; j++) {
                    double s = A[i][j];
                    for (int k = 0; k < j; k++) s -= L[i][k] * L[j][k];
                    if (i == j) {
                        double v = Math.max(s, 1e-12);
                        L[i][j] = Math.sqrt(v);
                        logDiagSum += Math.log(L[i][j]);
                    } else {
                        L[i][j] = s / L[j][j];
                    }
                }
            }
            return new Chol(L, logDiagSum);
        }

        double[] solve(double[] b) {
            double[] y = b.clone();
            // forward
            for (int i = 0; i < n; i++) {
                for (int k = 0; k < i; k++) y[i] -= L[i][k] * y[k];
                y[i] /= L[i][i];
            }
            // back
            for (int i = n - 1; i >= 0; i--) {
                for (int k = i + 1; k < n; k++) y[i] -= L[k][i] * y[k];
                y[i] /= L[i][i];
            }
            return y;
        }
    }
}

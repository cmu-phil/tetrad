package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.util.TetradLogger;

import java.util.Arrays;

/**
 * Lightweight Gaussian Mixture EM with full or diagonal covariance.
 */
public final class GaussianMixtureEM {

    /**
     * Fit a GMM by EM on X (n x d). Returns the model with responsibilities (soft labels).
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
            // --- annealing schedule: beta ∈ (0,1], climbs to 1 across annealSteps
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

    private static void logState(int iter, double ll, double[] w, double[][] mu,
                                 double[][][] S, double[][] R) {
        TetradLogger.getInstance().log(String.format(
                "Iter %d  LL=%.4f  Weights=%s  Means=%s",
                iter, ll, Arrays.toString(w), Arrays.deepToString(mu)
        ));
        // You could also dump covariances or a small slice of R if desired
    }

    private static double eStep(double[][] X, double[] w, double[][] mu, double[][][] S,
                                CovarianceType covType, double[][] R, double beta) {
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
            // temper the posteriors: scale log-likelihoods by beta ∈ (0,1], beta→1 recovers standard EM
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
    private static void mStep(double[][] X, double[][] R, double[] w, double[][] mu, double[][][] S,
                              CovarianceType covType, double ridgeAbs) {
        mStep(X, R, w, mu, S, covType, ridgeAbs, /*shrinkage*/ 0.0, /*ridgeRel*/ 0.0);
    }

    /**
     * Full M-step with covariance shrinkage and relative ridge.
     *  - shrinkage λ in [0,1]: shrinks Σ_k toward spherical target (FULL) or mean variance (DIAGONAL)
     *  - ridgeAbs ≥ 0: absolute ridge added to diagonal
     *  - ridgeRel ≥ 0: relative ridge = ridgeRel * τ (τ = avg variance) added to diagonal
     */
    private static void mStep(double[][] X, double[][] R, double[] w, double[][] mu, double[][][] S,
                              CovarianceType covType, double ridgeAbs, double shrinkage, double ridgeRel) {
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
    private static void hardMoments(double[][] X, int[] z, double[] w, double[][] mu, double[][][] S,
                                    CovarianceType covType, double ridge) {
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
     * Specifies the type of covariance matrix used in the Gaussian Mixture Model (GMM)
     * implemented by the GaussianMixtureEM class.
     *
     * The covariance type determines the complexity and flexibility of the model:
     * - FULL: Each component in the GMM has its own full covariance matrix.
     * - DIAGONAL: Each component in the GMM has a diagonal covariance matrix.
     *
     * The choice of covariance type affects the computational cost and the type
     * of relationships that can be modeled by the GMM.
     */
    public enum CovarianceType {

        /**
         * Represents the FULL covariance type for the Gaussian Mixture Model (GMM).
         *
         * The FULL covariance type specifies that each component in the GMM has its own
         * full covariance matrix. This allows for the modeling of complex relationships
         * between features but increases computational cost compared to simpler covariance
         * types, such as DIAGONAL.
         */
        FULL,

        /**
         * Represents the DIAGONAL covariance type for the Gaussian Mixture Model (GMM).
         *
         * The DIAGONAL covariance type specifies that each component in the GMM has
         * a diagonal covariance matrix. This type of covariance matrix assumes that
         * the features of the data are uncorrelated and have different variances.
         *
         * Using a diagonal covariance matrix reduces the complexity and computational
         * cost of the model compared to a FULL covariance matrix. However, it imposes
         * stronger assumptions on the data and may not capture correlations between
         * features effectively.
         */
        DIAGONAL}

    /**
     * Represents the Gaussian Mixture Model (GMM) computed and used in the
     * GaussianMixtureEM class. This class encapsulates the parameters of
     * the GMM, as well as related information such as soft cluster assignments
     * (responsibilities) and the model's overall log-likelihood.
     *
     * The parameters of the GMM include the number of components (K), the
     * dimensionality of the data, the component weights, the mean vectors,
     * and the covariance matrices. The type of covariance matrices is
     * also specified (e.g., full or diagonal).
     */
    public static final class Model {
        public final int K, d;
        public final double[] weights;          // K
        public final double[][] means;          // K x d
        public final double[][][] covs;         // K x d x d  (or diagonal in [k][j][0] if DIAGONAL)
        public final CovarianceType covType;
        public final double logLikelihood;
        public final double[][] responsibilities; // n x K

        public Model(int K, int d, CovarianceType covType,
                     double[] w, double[][] mu, double[][][] covs,
                     double ll, double[][] resp) {
            this.K = K;
            this.d = d;
            this.covType = covType;
            this.weights = w;
            this.means = mu;
            this.covs = covs;
            this.logLikelihood = ll;
            this.responsibilities = resp;
        }

        public double bic(int n) {
            // n = number of rows used to fit the model
            int d = this.d;
            int K = this.K;

            int covParamsPerComp = (covType == CovarianceType.FULL)
                    ? (d * (d + 1)) / 2
                    : d; // DIAGONAL

            int numParams = (K - 1)            // weights (sum to 1)
                            + K * d                    // means
                            + K * covParamsPerComp;    // covariance params

            return -2.0 * this.logLikelihood + numParams * Math.log(Math.max(1, n));
        }
    }

    public static final class Config {
        public int K;
        public CovarianceType covType = CovarianceType.DIAGONAL;
        public int maxIters = 200;
        public double tol = 1e-5;
        public long seed = 13L;
        public double ridge = 1e-6;  // covariance regularizer
        public int kmeansRestarts = 5; // for init
        public double covRidgeRel;
        public double covShrinkage;
        public int annealSteps;
        public double annealStartT;
        private boolean logIntermediate = false;

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

        public boolean isLogIntermediate() {
            return logIntermediate;
        }

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
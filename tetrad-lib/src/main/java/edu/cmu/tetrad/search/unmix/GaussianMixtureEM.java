package edu.cmu.tetrad.search.unmix;

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

        double prevLL = Double.NEGATIVE_INFINITY;
        double[][] R = new double[n][cfg.K];

        for (int it = 0; it < cfg.maxIters; it++) {
            // E-step: responsibilities
            double ll = eStep(X, w, mu, S, cfg.covType, R);
            // M-step: update moments from soft R
            mStep(X, R, w, mu, S, cfg.covType, cfg.ridge);

            if (Math.abs(ll - prevLL) < cfg.tol * (1 + Math.abs(prevLL))) {
                prevLL = ll;
                break;
            }
            prevLL = ll;
        }
        return new Model(cfg.K, d, cfg.covType, w, mu, S, prevLL, R);
    }

    private static double eStep(double[][] X, double[] w, double[][] mu, double[][][] S,
                                CovarianceType covType, double[][] R) {
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
            // log-sum-exp
            double sum = 0.0;
            for (int k = 0; k < K; k++) sum += Math.exp(logp[k] - max);
            double logsum = max + Math.log(sum);
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

    private static void mStep(double[][] X, double[][] R, double[] w, double[][] mu, double[][][] S,
                              CovarianceType covType, double ridge) {
        int n = X.length, d = X[0].length, K = w.length;

        // weights, means
        for (int k = 0; k < K; k++) {
            double rk = 0.0;
            Arrays.fill(mu[k], 0.0);
            for (int i = 0; i < n; i++) {
                double rik = R[i][k];
                rk += rik;
                for (int j = 0; j < d; j++) mu[k][j] += rik * X[i][j];
            }
            w[k] = Math.max(rk, 1e-12) / n;
            for (int j = 0; j < d; j++) mu[k][j] /= Math.max(rk, 1e-12);
        }

        // covariances
        for (int k = 0; k < K; k++) {
            if (covType == CovarianceType.DIAGONAL) {
                Arrays.stream(S[k]).forEach(row -> Arrays.fill(row, 0.0));
                double rk = Math.max(w[k] * n, 1e-12);
                for (int i = 0; i < n; i++) {
                    double rik = R[i][k];
                    for (int j = 0; j < d; j++) {
                        double z = X[i][j] - mu[k][j];
                        S[k][j][0] += rik * z * z;
                    }
                }
                for (int j = 0; j < d; j++) S[k][j][0] = S[k][j][0] / rk + ridge;
            } else {
                zero(S[k]);
                double rk = Math.max(w[k] * n, 1e-12);
                for (int i = 0; i < n; i++) {
                    double rik = R[i][k];
                    for (int a = 0; a < d; a++) {
                        double za = X[i][a] - mu[k][a];
                        for (int b = 0; b < d; b++) {
                            double zb = X[i][b] - mu[k][b];
                            S[k][a][b] += rik * za * zb;
                        }
                    }
                }
                for (int a = 0; a < d; a++) for (int b = 0; b < d; b++) S[k][a][b] = S[k][a][b] / rk;
                for (int j = 0; j < d; j++) S[k][j][j] += ridge;
            }
        }
    }

    // ---------- E-step ----------

    private static void hardMoments(double[][] X, int[] z, double[] w, double[][] mu, double[][][] S,
                                    CovarianceType covType, double ridge) {
        int n = X.length, d = X[0].length, K = w.length;
        int[] cnt = new int[K];
        for (int i = 0; i < n; i++) cnt[z[i]]++;
        for (int k = 0; k < K; k++) w[k] = Math.max(cnt[k], 1) / (double) n;

        // means
        for (int k = 0; k < K; k++) Arrays.fill(mu[k], 0.0);
        for (int i = 0; i < n; i++) {
            int k = z[i];
            for (int j = 0; j < d; j++) mu[k][j] += X[i][j];
        }
        for (int k = 0; k < K; k++) for (int j = 0; j < d; j++) mu[k][j] /= Math.max(cnt[k], 1);

        // cov
        if (covType == CovarianceType.DIAGONAL) {
            for (int k = 0; k < K; k++) {
                for (int j = 0; j < d; j++) {
                    double s2 = 0.0;
                    for (int i = 0; i < n; i++)
                        if (z[i] == k) {
                            double zt = X[i][j] - mu[k][j];
                            s2 += zt * zt;
                        }
                    S[k][j][0] = s2 / Math.max(cnt[k], 1) + ridge;
                }
            }
        } else {
            for (int k = 0; k < K; k++) {
                for (int a = 0; a < d; a++)
                    for (int b = 0; b < d; b++) {
                        double s = 0.0;
                        for (int i = 0; i < n; i++)
                            if (z[i] == k) {
                                double za = X[i][a] - mu[k][a];
                                double zb = X[i][b] - mu[k][b];
                                s += za * zb;
                            }
                        S[k][a][b] = s / Math.max(cnt[k], 1);
                    }
                for (int j = 0; j < d; j++) S[k][j][j] += ridge;
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
            // cov[k][j][0] holds variance for j-th dimension
            double det = 1.0, quad = 0.0;
            for (int j = 0; j < d; j++) {
                double var = cov[j][0]; // diagonal stored as cov[j][0]
                det *= var;
                quad += (diff[j] * diff[j]) / var;
            }
            double norm = 1.0 / (Math.pow(2 * Math.PI, d / 2.0) * Math.sqrt(det));
            return norm * Math.exp(-0.5 * quad);
        } else {
            // FULL covariance
            // build matrix objects for inversion/determinant
            org.ejml.simple.SimpleMatrix Sigma = new org.ejml.simple.SimpleMatrix(cov);
            double det = Sigma.determinant();
            if (det <= 0) det = 1e-12; // safeguard
            org.ejml.simple.SimpleMatrix inv = Sigma.invert();

            org.ejml.simple.SimpleMatrix v = new org.ejml.simple.SimpleMatrix(d, 1, true, diff);
            double quad = v.transpose().mult(inv).mult(v).get(0);

            double norm = 1.0 / (Math.pow(2 * Math.PI, d / 2.0) * Math.sqrt(det));
            return norm * Math.exp(-0.5 * quad);
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
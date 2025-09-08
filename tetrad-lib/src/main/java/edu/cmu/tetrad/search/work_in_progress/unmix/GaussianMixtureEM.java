package edu.cmu.tetrad.search.work_in_progress.unmix;

import java.util.Arrays;

/** Lightweight Gaussian Mixture EM with full or diagonal covariance. */
public final class GaussianMixtureEM {

    public enum CovarianceType { FULL, DIAGONAL }

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
            this.K = K; this.d = d; this.covType = covType;
            this.weights = w; this.means = mu; this.covs = covs;
            this.logLikelihood = ll; this.responsibilities = resp;
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
    }

    /** Fit a GMM by EM on X (n x d). Returns the model with responsibilities (soft labels). */
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
                prevLL = ll; break;
            }
            prevLL = ll;
        }
        return new Model(cfg.K, d, cfg.covType, w, mu, S, prevLL, R);
    }

    // ---------- E-step ----------

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
            quad = 0.0; logdet = 0.0;
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

    // ---------- init moments from hard labels ----------

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
                    for (int i = 0; i < n; i++) if (z[i] == k) {
                        double zt = X[i][j] - mu[k][j]; s2 += zt * zt;
                    }
                    S[k][j][0] = s2 / Math.max(cnt[k], 1) + ridge;
                }
            }
        } else {
            for (int k = 0; k < K; k++) {
                for (int a = 0; a < d; a++) for (int b = 0; b < d; b++) {
                    double s = 0.0;
                    for (int i = 0; i < n; i++) if (z[i] == k) {
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

    // ---------- helpers ----------

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

    private static double[] sub(double[] a, double[] b) {
        double[] r = new double[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[i] - b[i];
        return r;
    }
    private static double dot(double[] a, double[] b) {
        double s = 0.0; for (int i = 0; i < a.length; i++) s += a[i]*b[i]; return s;
    }
    private static void zero(double[][] A) {
        for (double[] row : A) Arrays.fill(row, 0.0);
    }

    /** tiny Cholesky with log|L| tracked; minimal checks, assume SPD with ridge. */
    private static final class Chol {
        final double[][] L; final int n; final double logDiagSum;
        private Chol(double[][] L, double logDiagSum) { this.L = L; this.n = L.length; this.logDiagSum = logDiagSum; }
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
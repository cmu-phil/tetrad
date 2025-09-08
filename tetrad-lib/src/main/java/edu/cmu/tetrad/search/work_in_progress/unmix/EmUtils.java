package edu.cmu.tetrad.search.work_in_progress.unmix;

public final class EmUtils {

    private EmUtils() { }

    /**
     * BIC for a GMM model; larger (less negative) is better.
     * @param m   the fitted GMM model
     * @param n   number of data points
     */
    public static double bic(GaussianMixtureEM.Model m, int n) {
        int K = m.K, d = m.d;
        int params;
        if (m.covType == GaussianMixtureEM.CovarianceType.DIAGONAL) {
            params = (K - 1)                    // weights (sum to 1)
                     + K * d                      // means
                     + K * d;                     // variances (one per dim per cluster)
        } else {
            params = (K - 1) + K * d + K * (d * (d + 1)) / 2;
        }
        return 2.0 * m.logLikelihood - params * Math.log(Math.max(n,1));
    }

    /**
     * Hard labels by MAP responsibility.
     * @param responsibilities n x K matrix of membership probabilities
     */
    public static int[] mapLabels(double[][] responsibilities) {
        int n = responsibilities.length, K = responsibilities[0].length;
        int[] z = new int[n];
        for (int i = 0; i < n; i++) {
            int arg = 0; double best = responsibilities[i][0];
            for (int k = 1; k < K; k++) {
                if (responsibilities[i][k] > best) {
                    best = responsibilities[i][k];
                    arg = k;
                }
            }
            z[i] = arg;
        }
        return z;
    }
}
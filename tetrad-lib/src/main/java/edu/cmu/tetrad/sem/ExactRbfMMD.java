package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.TMath;

final class ExactRbfMMD {

    private ExactRbfMMD() {}

    static double compute(double[][] X,
                          double[][] Y,
                          double sigma,
                          int maxRows) {

        if (X == null || Y == null) throw new NullPointerException("X/Y");
        if (X.length < 2 || Y.length < 2)
            throw new IllegalArgumentException("Need at least 2 rows in each sample");

        int dim = X[0].length;
        double sig = (sigma > 0.0) ? sigma : 1.0;
        double inv2sig2 = 1.0 / (2.0 * sig * sig);

        // Optional truncation
        int n = (maxRows > 0) ? TMath.min(maxRows, X.length) : X.length;
        int m = (maxRows > 0) ? TMath.min(maxRows, Y.length) : Y.length;

        double sumXX = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                sumXX += rbf(X[i], X[j], dim, inv2sig2);
            }
        }
        sumXX *= 2.0 / (n * (n - 1));

        double sumYY = 0.0;
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                sumYY += rbf(Y[i], Y[j], dim, inv2sig2);
            }
        }
        sumYY *= 2.0 / (m * (m - 1));

        double sumXY = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                sumXY += rbf(X[i], Y[j], dim, inv2sig2);
            }
        }
        sumXY *= 2.0 / (n * m);

        double mmd2 = sumXX + sumYY - sumXY;
        return TMath.max(0.0, mmd2);
    }

    private static double rbf(double[] a,
                              double[] b,
                              int dim,
                              double inv2sig2) {
        double dist2 = 0.0;
        for (int i = 0; i < dim; i++) {
            double d = a[i] - b[i];
            dist2 += d * d;
        }
        return TMath.exp(-dist2 * inv2sig2);
    }
}
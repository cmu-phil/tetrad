package edu.cmu.tetrad.util;

import org.ejml.simple.SimpleMatrix;

import java.util.Random;

public final class EssLikePython {

    /**
     * Mirrors your Python: 1) Column-standardize (ddof=0) across ALL rows. 2) Sample m rows without replacement (m ≈
     * sqrt(2N) typical). 3) Row-standardize those m rows (ddof=0). 4) Compute full row–row correlation matrix and
     * average its off-diagonal. 5) ESS = N / (1 + (N-1)*avgCorr).
     *
     * @param X                N x P data (rows=samples, cols=features)
     * @param sampleSize       desired number of rows to use (e.g., (int)Math.sqrt(2*N))
     * @param clampNonnegative if true, clamp avgCorr to [0, 0.999999] before ESS
     * @param rng              random (null → default)
     */
    public static Result estimateLikePython(SimpleMatrix X,
                                            int sampleSize,
                                            boolean clampNonnegative,
                                            Random rng) {
        if (rng == null) rng = new Random(0);

        final int N = X.getNumRows();
        final int P = X.getNumCols();
        if (N < 2 || P < 1) return new Result(0.0, N, Math.max(0, N));

        // 1) Column-standardize with ddof=0 (population)
        SimpleMatrix Z = new SimpleMatrix(N, P);
        for (int j = 0; j < P; j++) {
            double mean = 0.0;
            for (int i = 0; i < N; i++) mean += X.get(i, j);
            mean /= N;

            double varPop = 0.0;
            for (int i = 0; i < N; i++) {
                double d = X.get(i, j) - mean;
                varPop += d * d;
            }
            varPop /= N; // ddof=0
            double sd = Math.sqrt(Math.max(varPop, 1e-24));

            for (int i = 0; i < N; i++) {
                Z.set(i, j, (X.get(i, j) - mean) / sd);
            }
        }


        // Row standardize Z
        for (int i = 0; i < N; i++) {
            double mean = 0.0;
            for (int j = 0; j < P; j++) mean += Z.get(i, j);
            mean /= P;

            double varPop = 0.0;
            for (int j = 0; j < P; j++) {
                double d = Z.get(i, j) - mean;
                varPop += d * d;
            }
            varPop /= P; // ddof=0
            double sd = Math.sqrt(Math.max(varPop, 1e-24));

            for (int j = 0; j < P; j++) {
                Z.set(i, j, (Z.get(i, j) - mean) / sd);
            }
        }

        // 2) Sample m rows without replacement (like df.sample(..., random_state=0))
        int m = Math.min(sampleSize, N);
        int[] rows = shuffledRange(N, rng);
        int[] pick = new int[m];
        System.arraycopy(rows, 0, pick, 0, m);

        // 3) Build Y = selected m rows; then row-standardize (ddof=0)
        SimpleMatrix Y = new SimpleMatrix(m, P);
        for (int t = 0; t < m; t++) {
            int i = pick[t];
            for (int j = 0; j < P; j++) Y.set(t, j, Z.get(i, j));
        }

        double[] rMean = new double[m];
        double[] rStd = new double[m];
        for (int i = 0; i < m; i++) {
            double mean = 0.0;
            for (int j = 0; j < P; j++) mean += Y.get(i, j);
            mean /= P;
            rMean[i] = mean;

            double varPop = 0.0;
            for (int j = 0; j < P; j++) {
                double d = Y.get(i, j) - mean;
                varPop += d * d;
            }
            varPop /= P; // ddof=0
            rStd[i] = Math.sqrt(Math.max(varPop, 1e-24));
        }

        for (int i = 0; i < m; i++) {
            double mi = rMean[i], si = rStd[i];
            for (int j = 0; j < P; j++) Y.set(i, j, (Y.get(i, j) - mi) / si);
        }

        // 4) Full row–row correlation on Y (since each row is mean 0, sd 1 with ddof=0, the corr reduces to dot/P)
        double sumOffDiag = 0.0;
        long numPairs = 0L;
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                double dot = 0.0;
                for (int k = 0; k < P; k++) dot += Y.get(i, k) * Y.get(j, k);
                // With ddof=0 row standardization, Pearson reduces to dot/P (not P-1)
                double rij = dot / P;
                sumOffDiag += rij;
                numPairs++;
            }
        }

        double avgCorr = (numPairs > 0) ? (sumOffDiag / numPairs) : 0.0;

        // 5) ESS with optional clamp to keep it in (0, N]
        if (clampNonnegative && avgCorr < 0.0) avgCorr = 0.0;
        if (clampNonnegative && avgCorr >= 0.999999) avgCorr = 0.999999;

        double ess = N / (1.0 + (N - 1.0) * avgCorr);
        return new Result(avgCorr, ess, m);
    }

    private static int[] shuffledRange(int n, Random rng) {
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = idx[i];
            idx[i] = idx[j];
            idx[j] = tmp;
        }
        return idx;
    }

    // ----- helpers -----

    public static final class Result {
        public final double avgRowCorr; // same definition as Python function
        public final double ess;        // N / (1 + (N-1)*avgRowCorr) (optionally clamped)
        public final int mRowsUsed;

        Result(double avgRowCorr, double ess, int mRowsUsed) {
            this.avgRowCorr = avgRowCorr;
            this.ess = ess;
            this.mRowsUsed = mRowsUsed;
        }

        @Override
        public String toString() {
            return String.format("avgRowCorr=%.6f, ESS=%.2f, m=%d", avgRowCorr, ess, mRowsUsed);
        }
    }
}
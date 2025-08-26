package edu.cmu.tetrad.util;

import edu.cmu.tetrad.data.DataTransforms;
import org.ejml.simple.SimpleMatrix;

import java.util.Set;

public final class RowCorrelationEffN {

    /**
     * Estimates average pairwise row correlation (by sampling pairs) and returns Neff = N / (1 + (N-1)*rhoHat). Columns
     * are standardized first.
     * <p>
     * If the sampled average correlation is < 0, we clamp it to 0 so Neff = N. If it’s >= 1, we clamp slightly below 1
     * to avoid division-by-zero.
     *
     * @param X                data matrix N x P (rows = samples, cols = features)
     * @param maxPairsToSample number of random row pairs to sample (cap at C(N,2))
     */
    public static Result estimate(SimpleMatrix X,
                                  int maxPairsToSample,
                                  int N) {
        X = DataTransforms.standardizeData(new Matrix(X)).getSimpleMatrix();

        double[][] _X = X.toArray2();
        double sumR = 0.0;
        int used = 0;

        int numTries = X.getNumRows() * (X.getNumRows() - 1) / 2;
        if (numTries > maxPairsToSample) {
            numTries = maxPairsToSample;
        }

        for (int i = 0; i < numTries; i++) {
            int j = RandomUtil.getInstance().nextInt(X.getNumRows());
            int k = RandomUtil.getInstance().nextInt(X.getNumRows());

            if (j <= k) {
                numTries--;
                continue;
            }

            double r = StatUtils.correlation(_X[j], _X[k]);
            sumR += r;
            used++;
        }

        double rhoHat = (used > 0) ? (sumR / used) : 0.0;

        System.out.println("Rho Hat: " + rhoHat);

        if (rhoHat < 0.0) rhoHat = 0.0;
        if (rhoHat >= 0.999999) rhoHat = 0.999999;

        double neff = N / (1.0 + (N - 1.0) * rhoHat);
        return new Result(rhoHat, neff, used);
    }

    private static double cosineAcrossFeatures(SimpleMatrix Z, int i, int j, double[] rowNorm) {
        double dot = 0.0;
        int P = Z.getNumCols();
        for (int k = 0; k < P; k++) dot += Z.get(i, k) * Z.get(j, k);
        return dot / (rowNorm[i] * rowNorm[j]);
    }

    // ----------- helpers -----------

    private static double pearsonAcrossFeatures(SimpleMatrix Z, int i, int j, double[] rowMean, double[] rowStd) {
        double num = 0.0;
        int P = Z.getNumCols();
        double mi = rowMean[i], mj = rowMean[j];
        double si = rowStd[i], sj = rowStd[j];
        for (int k = 0; k < P; k++) {
            double ai = Z.get(i, k) - mi;
            double bj = Z.get(j, k) - mj;
            num += ai * bj;
        }
        return num / ((P - 1.0) * si * sj);
    }

    public static final class Result {
        public final double avgRowCorrelation; // rho_hat after clamping to [0,1)
        public final double effN;              // N / (1 + (N-1)*rho_hat)
        public final int pairsUsed;

        private Result(double avgRowCorrelation, double effN, int pairsUsed) {
            this.avgRowCorrelation = avgRowCorrelation;
            this.effN = effN;
            this.pairsUsed = pairsUsed;
        }

        @Override
        public String toString() {
            return String.format("rho_hat=%.6f, Neff=%.2f, pairs=%d", avgRowCorrelation, effN, pairsUsed);
        }
    }
}
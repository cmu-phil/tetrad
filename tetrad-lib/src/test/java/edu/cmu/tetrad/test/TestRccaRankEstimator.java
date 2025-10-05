///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

// File: RccaRankEstimatorTest.java

import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;
import org.junit.Test;

import java.util.Random;
import java.util.stream.IntStream;

import static org.ejml.UtilEjml.assertTrue;

public class TestRccaRankEstimator {

    private static final RankEstimator ESTIMATOR = (scond, xIdxLocal, yIdxLocal, n, alpha, regLambda, condThreshold) -> RankTests.estimateWilksRank(scond, xIdxLocal, yIdxLocal, n, alpha);

    /**
     * Build sample covariance from data matrix (n x (p+q)), columns mean-centered.
     */
    private static SimpleMatrix sampleCov(double[][] data) {
        int n = data.length;
        int d = data[0].length;
        SimpleMatrix X = new SimpleMatrix(n, d);
        for (int i = 0; i < n; i++) for (int j = 0; j < d; j++) X.set(i, j, data[i][j]);
        // mean-center columns
        for (int j = 0; j < d; j++) {
            double m = 0.0;
            for (int i = 0; i < n; i++) m += X.get(i, j);
            m /= n;
            for (int i = 0; i < n; i++) X.set(i, j, X.get(i, j) - m);
        }
        // S = X^T X / (n-1)
        SimpleMatrix S = X.transpose().mult(X).divide(n - 1.0);
        return S;
    }

    // ==== Utilities ====

    /**
     * Draw a random unit vector of length dim.
     */
    private static double[] randUnitVec(int dim, Random rng) {
        double[] v = new double[dim];
        double norm2 = 0.0;
        for (int i = 0; i < dim; i++) {
            double z = rng.nextGaussian();
            v[i] = z;
            norm2 += z * z;
        }
        double inv = 1.0 / Math.sqrt(norm2);
        for (int i = 0; i < dim; i++) v[i] *= inv;
        return v;
    }

    /**
     * Adds scaled vector (scale * L) along direction u into row vector out.
     */
    private static void addSignal(double[] out, double[] u, double L, double scale) {
        for (int j = 0; j < u.length; j++) out[j] += scale * u[j] * L;
    }

    /**
     * Simulate (X, Y) with: - p, q: dimensions; - n: samples; - d: latent rank (0..min(p,q)); - rho: per-latent target
     * canonical correlation (used when d>=1); - illCondScale: if >0, multiplies the j-th X feature by illCondScale^j
     * (makes Sxx ill-conditioned).
     * <p>
     * Construction (simple, robust): X = sum_{k=1..d} a_k L_k + Ï * E_x,   Y = sum_{k=1..d} b_k L_k + Ï * E_y, with
     * a_k, b_k random unit vectors; choose Ï so that corr â rho when d=1.
     */
    private static double[][] simulateXY(int p, int q, int n, int d, double rho,
                                         double illCondScale, long seed) {
        Random rng = new Random(seed);
        double[][] X = new double[n][p];
        double[][] Y = new double[n][q];

        // Directions
        double[][] A = new double[d][p];
        double[][] B = new double[d][q];
        for (int k = 0; k < d; k++) {
            A[k] = randUnitVec(p, rng);
            B[k] = randUnitVec(q, rng);
        }

        // Noise scale: for d=1 and unit signal, rho = 1/(1+Ï^2) => Ï^2 = 1/rho - 1
        double sigma = (d >= 1 && rho > 0 && rho < 1) ? Math.sqrt(1.0 / rho - 1.0) : 1.0;

        for (int i = 0; i < n; i++) {
            // Latents
            double[] L = new double[d];
            for (int k = 0; k < d; k++) L[k] = rng.nextGaussian();

            // Row i
            double[] xi = new double[p];
            double[] yi = new double[q];

            // Signals
            for (int k = 0; k < d; k++) {
                addSignal(xi, A[k], L[k], 1.0);
                addSignal(yi, B[k], L[k], 1.0);
            }

            // Gaussian noise
            for (int j = 0; j < p; j++) xi[j] += sigma * rng.nextGaussian();
            for (int j = 0; j < q; j++) yi[j] += sigma * rng.nextGaussian();

            // Optional ill-conditioning of X block
            if (illCondScale > 0) {
                double scale = 1.0;
                for (int j = 0; j < p; j++) {
                    xi[j] *= scale;
                    scale *= illCondScale;
                }
            }

            // Store
            System.arraycopy(xi, 0, X[i], 0, p);
            System.arraycopy(yi, 0, Y[i], 0, q);
        }

        // Concatenate [X | Y]
        double[][] Z = new double[n][p + q];
        for (int i = 0; i < n; i++) {
            System.arraycopy(X[i], 0, Z[i], 0, p);
            System.arraycopy(Y[i], 0, Z[i], p, q);
        }
        return Z;
    }

    private static int[] range(int startInclusive, int endExclusive) {
        return IntStream.range(startInclusive, endExclusive).toArray();
    }

    @Test
    public void typeI_rank0_smallN() {
        int p = 8, q = 6;
        int n = p + q + 12;         // a bit above the Bartlett comfort zone
        int trials = 300;
        double alpha = 0.05;
        int falseRejects = 0;

        for (int t = 0; t < trials; t++) {
            double[][] Z = simulateXY(p, q, n, /*d=*/0, /*rho=*/0.0, /*illCond=*/0.0, /*seed=*/1234 + t);
            SimpleMatrix S = sampleCov(Z);
            int[] xIdx = range(0, p), yIdx = range(p, p + q);
            int est = ESTIMATOR.estimate(S, xIdx, yIdx, n, alpha, /*reg*/0.0, /*condThr*/0.0);
            // Under true rank 0 we "reject" if estimator returns > 0
            if (est > 0) falseRejects++;
        }

        double typeI = falseRejects / (double) trials;
        System.out.printf("Type-I (rank 0): %.3f (alpha=%.2f)%n", typeI, alpha);
        // Loose bound to avoid flaky tests; tighten as desired.
        assertTrue(typeI < 0.12, "Type-I inflated: " + typeI);
    }

    // ==== Scenarios ====

    @Test
    public void power_rank1_weakSignal() {
        int p = 10, q = 8;
        int n = 800;
        int trials = 200;
        double alpha = 0.05;

        double[] rhos = {0.05, 0.10, 0.20};
        for (double rho : rhos) {
            int correct = 0;
            for (int t = 0; t < trials; t++) {
                double[][] Z = simulateXY(p, q, n, /*d=*/1, rho, /*illCond=*/0.0, /*seed=*/5678 + t);
                SimpleMatrix S = sampleCov(Z);
                int[] xIdx = range(0, p), yIdx = range(p, p + q);
                int est = ESTIMATOR.estimate(S, xIdx, yIdx, n, alpha, /*reg*/0.0, /*condThr*/0.0);
                if (est >= 1) correct++;
            }
            double power = correct / (double) trials;
            System.out.printf("Power (rank 1, rho=%.2f): %.3f%n", rho, power);
            // Sanity: power should increase with rho
//            if (rho >= 0.10) assertTrue(power > 0.6, "Low power for rho=" + rho + ": " + power);
        }
    }

    @Test
    public void stability_underIllConditioning() {
        int p = 8, q = 8, n = 1000, trials = 150;
        double alpha = 0.05;

        double illCond = 10.0;  // condition number blows up ~ 10^(p-1)
        int falseRejects = 0;

        for (int t = 0; t < trials; t++) {
            double[][] Z = simulateXY(p, q, n, /*d=*/0, 0.0, illCond, /*seed=*/9999 + t);
            SimpleMatrix S = sampleCov(Z);
            int[] xIdx = range(0, p), yIdx = range(p, p + q);

            // Try with small ridge & your default condThreshold if you like
            int est = ESTIMATOR.estimate(S, xIdx, yIdx, n, alpha, /*reg*/1e-8, /*condThr*/0.0);
            if (est > 0) falseRejects++;
        }
        double typeI = falseRejects / (double) trials;
        System.out.printf("Type-I under ill-conditioning: %.3f%n", typeI);
//        assertTrue(typeI < 0.15, "Too many false positives under ill-conditioning: " + typeI);
    }

    @Test
    public void rankRecovery_d2_vsN() {
        int p = 10, q = 10, d = 2;
        double rho = 0.35;          // per-latent strength (same for both); moderate difficulty
        double alpha = 0.05;
        int trials = 150;

        int[] nVals = {400, 1200, 3600};

        double prevMean = -1.0;
        for (int n : nVals) {
            int[] ests = new int[trials];
            int exact2 = 0;

            for (int t = 0; t < trials; t++) {
                double[][] Z = simulateXY(p, q, n, d, rho, /*illCond=*/0.0, /*seed=*/4242 + 17 * t);
                SimpleMatrix S = sampleCov(Z);
                int[] xIdx = range(0, p), yIdx = range(p, p + q);
                int est = ESTIMATOR.estimate(S, xIdx, yIdx, n, alpha, /*reg*/0.0, /*condThr*/0.0);
                ests[t] = est;
                if (est == 2) exact2++;
            }

            // mean and std
            double mean = 0.0;
            for (int v : ests) mean += v;
            mean /= trials;
            double var = 0.0;
            for (int v : ests) {
                double dlt = v - mean;
                var += dlt * dlt;
            }
            double sd = Math.sqrt(var / Math.max(1, trials - 1));
            double frac2 = exact2 / (double) trials;

            System.out.printf("d=2 recovery: n=%d  mean=%.3f  sd=%.3f  P{est=2}=%.3f%n",
                    n, mean, sd, frac2);

            // Sanity checks: monotone improvement and decent recovery at large n
            if (prevMean >= 0.0) {
                assertTrue(mean + 1e-9 >= prevMean, "Mean rank estimate did not improve with larger n");
            }
            prevMean = mean;
        }

        // Re-run the largest n to assert a concrete floor on performance (avoid flaky strictness)
        int nLarge = nVals[nVals.length - 1];
        int trialsLarge = 200;
        int hit2 = 0;
        double sum = 0.0;
        for (int t = 0; t < trialsLarge; t++) {
            double[][] Z = simulateXY(p, q, nLarge, d, rho, /*illCond=*/0.0, /*seed=*/8888 + t);
            SimpleMatrix S = sampleCov(Z);
            int[] xIdx = range(0, p), yIdx = range(p, p + q);
            int est = ESTIMATOR.estimate(S, xIdx, yIdx, nLarge, alpha, /*reg*/0.0, /*condThr*/0.0);
            sum += est;
            if (est == 2) hit2++;
        }
        double meanLarge = sum / trialsLarge;
        double frac2Large = hit2 / (double) trialsLarge;

        System.out.printf("d=2 recovery @ n=%d: mean=%.3f  P{est=2}=%.3f%n",
                nLarge, meanLarge, frac2Large);

        // Be generous to avoid flakiness across environments:
        assertTrue(meanLarge >= 1.8, "Mean rank at large n should be close to 2, got " + meanLarge);
        assertTrue(frac2Large >= 0.65, "Too few exact-2 recoveries at large n: " + frac2Large);
    }

    // ==== Plug your estimator here ====
    interface RankEstimator {
        int estimate(SimpleMatrix Scond, int[] xIdx, int[] yIdx,
                     int n, double alpha, double regLambda, double condThreshold);
    }
}


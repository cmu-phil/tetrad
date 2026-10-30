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

package edu.cmu.tetrad.search.rlcd;

import java.util.Arrays;
import java.util.Random;

/**
 * Tells a direct edge between two indicators from a latent common cause, using third-order cumulants. Rank
 * constraints cannot: if L --&gt; X1, L --&gt; X2 and X1 --&gt; X2, then {X1, X2} against anything outside has rank 1,
 * just as if a second latent sat over the pair, and RLCD reports the second latent.
 * <p>
 * Setting. X1 and X2 share a factor L with reference variables R1..Rm (m at least 2), whose only connection to X1
 * and X2 is through L and which share no other source among themselves. Errors are independent; the model is linear.
 * <p>
 * The part of the third cumulant k(Xi, Xj, Xk), i, j, k in {1, 2}, due to L is
 * k(Xi, Ra, Rb) cov(Xj, Ra) cov(Xk, Rb) / cov(Ra, Rb)^2 for any two distinct references (averaged here over the
 * ordered pairs). Subtracting it leaves residual cumulants R_ijk involving only the sources X1 and X2 share beyond L
 * and their own errors. If X1 --&gt; X2, X1's only source beyond L is the one it passes to X2, so
 * T12 = R111 R122 - R112^2 = 0; if X2 --&gt; X1, T21 = R222 R112 - R122^2 = 0; if a further latent (or correlated
 * errors) is shared, both are nonzero provided the variables' own errors are skewed. Each is put on the scale
 * n = T / (|first product| + second term squared), which lies in [-1, 1] and is unchanged by rescaling any variable.
 * <p>
 * The decision is conservative: an edge has to be shown, not merely not refuted. X1 --&gt; X2 is returned when the
 * 90% percentile bootstrap interval for n12 lies inside (-delta, delta) (an equivalence test at 5%) and the 95%
 * interval for n21 excludes 0; X2 --&gt; X1 likewise with the roles swapped. LATENT is returned when both 95%
 * intervals exclude 0 and neither n is shown equivalent to 0. Anything else is UNDECIDED, which is the only answer
 * possible when the errors are Gaussian (every n is then 0 or a ratio of noise).
 * <p>
 * In simulation (linear, three clean references, N = 5000), with delta = 0.1 no latent was read as an edge, and about
 * half of the true edges were found; the rest came back undecided, or occasionally latent: n is a ratio of sample
 * cumulants and has a small bias under a true edge at N in the thousands (about -0.016 at N = 5000 in one design,
 * vanishing by N = 20000), which the percentile intervals carry. That errs toward keeping a latent. Basic
 * (bias-corrected) intervals recover some of those edges but behave erratically under Gaussian errors, so they are
 * not used. Weakly skewed or ceiling-truncated data, too few
 * rows, or references that are not pure indicators of the same factor will all degrade it; the last can make it
 * wrong rather than undecided. The simulation is impurity_cumulant_sim.py (kept outside the repository);
 * {@code edu.cmu.tetrad.search.harness.RlcdTwoIndicatorStudy} measures the step inside RLCD.
 *
 * @author josephramsey
 */
public final class TwoIndicatorTest {

    /**
     * The answer.
     */
    public enum Answer {
        /**
         * X1 --&gt; X2.
         */
        X1_TO_X2,
        /**
         * X2 --&gt; X1.
         */
        X2_TO_X1,
        /**
         * A further shared latent (or correlated errors).
         */
        LATENT,
        /**
         * Not decided.
         */
        UNDECIDED
    }

    /**
     * The result of the test.
     *
     * @param answer the answer.
     * @param n12    the normalized statistic that is 0 if X1 --&gt; X2.
     * @param n21    the normalized statistic that is 0 if X2 --&gt; X1.
     * @param n      the number of complete rows used.
     */
    public record Result(Answer answer, double n12, double n21, int n) {
    }

    private TwoIndicatorTest() {
    }

    /**
     * Runs the test.
     *
     * @param x1     the first variable's values.
     * @param x2     the second variable's values.
     * @param refs   the references' values, refs[r][row]; at least two.
     * @param delta  the equivalence margin for n, in (0, 1).
     * @param numBoot the number of bootstrap resamples (a few hundred).
     * @param seed   the bootstrap seed.
     * @return the result; UNDECIDED with NaN statistics if there are too few complete rows.
     */
    public static Result test(double[] x1, double[] x2, double[][] refs, double delta, int numBoot, long seed) {
        if (refs.length < 2) throw new IllegalArgumentException("At least two references are needed.");
        if (!(delta > 0 && delta < 1)) throw new IllegalArgumentException("delta must be in (0, 1).");
        if (numBoot < 100) throw new IllegalArgumentException("Use at least 100 bootstrap resamples.");

        // Columns: X1, X2, refs; complete rows only.
        int m = refs.length;
        double[][] cols = new double[2 + m][];
        cols[0] = x1;
        cols[1] = x2;
        System.arraycopy(refs, 0, cols, 2, m);
        int rows = x1.length;
        int[] keep = new int[rows];
        int n = 0;
        for (int i = 0; i < rows; i++) {
            boolean ok = true;
            for (double[] c : cols) {
                if (Double.isNaN(c[i])) {
                    ok = false;
                    break;
                }
            }
            if (ok) keep[n++] = i;
        }
        if (n < 50) return new Result(Answer.UNDECIDED, Double.NaN, Double.NaN, n);
        double[][] data = new double[2 + m][n];
        for (int v = 0; v < cols.length; v++) {
            for (int i = 0; i < n; i++) data[v][i] = cols[v][keep[i]];
        }

        int[] all = new int[n];
        for (int i = 0; i < n; i++) all[i] = i;
        double[] stat = stats(data, all);

        Random random = new Random(seed);
        double[] b12 = new double[numBoot], b21 = new double[numBoot];
        int[] idx = new int[n];
        for (int b = 0; b < numBoot; b++) {
            for (int i = 0; i < n; i++) idx[i] = random.nextInt(n);
            double[] s = stats(data, idx);
            b12[b] = s[0];
            b21[b] = s[1];
        }
        Arrays.sort(b12);
        Arrays.sort(b21);

        boolean nonzero12 = percentile(b12, 0.025) > 0 || percentile(b12, 0.975) < 0;
        boolean nonzero21 = percentile(b21, 0.025) > 0 || percentile(b21, 0.975) < 0;
        boolean equiv12 = -delta < percentile(b12, 0.05) && percentile(b12, 0.95) < delta;
        boolean equiv21 = -delta < percentile(b21, 0.05) && percentile(b21, 0.95) < delta;

        boolean edge12 = equiv12 && nonzero21;
        boolean edge21 = equiv21 && nonzero12;
        Answer answer;
        if (edge12 && !edge21) answer = Answer.X1_TO_X2;
        else if (edge21 && !edge12) answer = Answer.X2_TO_X1;
        else if (nonzero12 && nonzero21 && !equiv12 && !equiv21) answer = Answer.LATENT;
        else answer = Answer.UNDECIDED;

        return new Result(answer, stat[0], stat[1], n);
    }

    /**
     * Linear-interpolated percentile of a sorted array.
     */
    private static double percentile(double[] sorted, double q) {
        double pos = q * (sorted.length - 1);
        int lo = (int) Math.floor(pos), hi = (int) Math.ceil(pos);
        return sorted[lo] + (pos - lo) * (sorted[hi] - sorted[lo]);
    }

    /**
     * Returns {n12, n21} on the rows idx (with repetition) of data, whose variables are X1, X2, then references.
     */
    private static double[] stats(double[][] data, int[] idx) {
        int v = data.length, m = v - 2, n = idx.length;

        double[] mean = new double[v];
        for (int k = 0; k < v; k++) {
            double s = 0;
            for (int i : idx) s += data[k][i];
            mean[k] = s / n;
        }

        double k111 = 0, k112 = 0, k122 = 0, k222 = 0;
        double[][] covXR = new double[2][m];
        double[][] covRR = new double[m][m];
        double[][][] kXRR = new double[2][m][m];   // a < b only
        double[] c = new double[v];
        for (int i : idx) {
            for (int k = 0; k < v; k++) c[k] = data[k][i] - mean[k];
            double a = c[0], b = c[1];
            k111 += a * a * a;
            k112 += a * a * b;
            k122 += a * b * b;
            k222 += b * b * b;
            for (int r = 0; r < m; r++) {
                double cr = c[2 + r];
                covXR[0][r] += a * cr;
                covXR[1][r] += b * cr;
                for (int s = r + 1; s < m; s++) {
                    double rs = cr * c[2 + s];
                    covRR[r][s] += rs;
                    kXRR[0][r][s] += a * rs;
                    kXRR[1][r][s] += b * rs;
                }
            }
        }

        // The L part of k(Xi, Xj, Xk), averaged over ordered pairs of distinct references.
        int[][] triples = {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {1, 1, 1}};
        double[] k3 = {k111 / n, k112 / n, k122 / n, k222 / n};
        double[] resid = new double[4];
        for (int t = 0; t < 4; t++) {
            int i = triples[t][0], j = triples[t][1], k = triples[t][2];
            double sum = 0;
            int count = 0;
            for (int r = 0; r < m; r++) {
                for (int s = 0; s < m; s++) {
                    if (r == s) continue;
                    int lo = Math.min(r, s), hi = Math.max(r, s);
                    double crs = covRR[lo][hi] / n;
                    sum += (kXRR[i][lo][hi] / n) * (covXR[j][r] / n) * (covXR[k][s] / n) / (crs * crs);
                    count++;
                }
            }
            resid[t] = k3[t] - sum / count;
        }

        double r111 = resid[0], r112 = resid[1], r122 = resid[2], r222 = resid[3];
        double t12 = r111 * r122 - r112 * r112;
        double t21 = r222 * r112 - r122 * r122;
        double d12 = Math.abs(r111 * r122) + r112 * r112;
        double d21 = Math.abs(r222 * r112) + r122 * r122;
        double n12 = d12 > 0 ? t12 / d12 : 0;
        double n21 = d21 > 0 ? t21 / d21 : 0;
        return new double[]{n12, n21};
    }
}

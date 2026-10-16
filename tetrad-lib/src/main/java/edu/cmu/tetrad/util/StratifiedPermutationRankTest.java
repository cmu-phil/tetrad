///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.util;

import edu.cmu.tetrad.data.DataSet;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The permutation form of the stratified (domain-pooled) rank test, after Theorem 5 of Dong, Dai, Ng, Spirtes, and
 * Zhang (ICML 2026). Unlike the likelihood-ratio form in {@link StratifiedRankTests}, whose chi-square reference
 * assumes joint Gaussianity within each domain, this test estimates the null distribution of the pooled statistic
 * by permutation, within each domain separately, and so does not assume Gaussianity.
 * <p>
 * The scheme, for the null that the cross-covariance of X and Y given Z has rank at most r within every domain:
 * within each domain, X and Y are first residualized on Z; the canonical variates of the residuals are computed
 * once; under the null the trailing variates (those past the first r) of the two sides share no sources and are
 * asymptotically independent within the domain, so permuting the rows of one side's trailing block resamples the
 * null. The pooled statistic -- the Bartlett term summed over domains -- is recomputed under random permutations,
 * drawn independently per domain, and the p-value is the fraction of permuted statistics at least as large as the
 * observed one, with the usual add-one correction.
 * <p>
 * The exchangeability of the trailing variates is an asymptotic property (Dong et al. prove it for their model
 * class under rank faithfulness), so this is an asymptotically valid test, not an exact finite-sample one. Domains
 * too small to discriminate are skipped from both the observed and the permuted sums. If no domain is usable the
 * p-value is NaN. Each call costs roughly the number of permutations times the cost of the likelihood-ratio test,
 * so inside a search this test is slow; it is a robustness check for badly non-Gaussian data, not a routine
 * default.
 *
 * @author josephramsey
 */
public final class StratifiedPermutationRankTest {

    /**
     * Ridge added to diagonal blocks before inversion, as elsewhere in the stratified machinery.
     */
    private static final double EPS = 1e-10;

    /**
     * The per-domain data matrices, rows by variables, each domain centered at its own column means.
     */
    private final List<double[][]> data;

    /**
     * The per-domain sample sizes.
     */
    private final int[] ns;

    /**
     * The seed for the permutation stream; each call draws a fresh Random from it combined with the query, so
     * repeated calls with the same inputs give the same answer.
     */
    private long seed = 42L;

    /**
     * Constructs the test from one continuous data set per domain, all with the same variables in the same order.
     *
     * @param dataSets one continuous data set per domain.
     */
    public StratifiedPermutationRankTest(List<DataSet> dataSets) {
        if (dataSets == null || dataSets.isEmpty()) throw new IllegalArgumentException("No domains given.");
        List<String> names0 = dataSets.get(0).getVariableNames();
        this.data = new ArrayList<>();
        this.ns = new int[dataSets.size()];

        for (int t = 0; t < dataSets.size(); t++) {
            DataSet d = dataSets.get(t);
            if (!d.getVariableNames().equals(names0)) {
                throw new IllegalArgumentException("Domain " + t + " does not have the same variables, in the same "
                                                   + "order, as domain 0.");
            }
            int n = d.getNumRows(), m = d.getNumColumns();
            double[][] a = new double[n][m];
            for (int i = 0; i < n; i++) for (int j = 0; j < m; j++) a[i][j] = d.getDouble(i, j);

            // Center each domain at its own means.
            for (int j = 0; j < m; j++) {
                double mean = 0.0;
                for (int i = 0; i < n; i++) mean += a[i][j];
                mean /= n;
                for (int i = 0; i < n; i++) a[i][j] -= mean;
            }

            this.data.add(a);
            this.ns[t] = n;
        }
    }

    /**
     * Sets the seed for the permutation stream.
     *
     * @param seed the seed.
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    /**
     * The permutation p-value for the null that the cross-covariance of X and Y given Z has rank at most r within
     * every domain. Z is removed from X and Y first.
     *
     * @param xRaw            column indices of the first set.
     * @param yRaw            column indices of the second set.
     * @param z               column indices of the conditioning set; may be empty.
     * @param r               the rank bound under the null, with 0 &le; r &lt; min(|x|, |y|) after removal of Z.
     * @param numPermutations the number of permutations; 99 or 199 are reasonable choices.
     * @return the p-value, or NaN if no domain is large enough to contribute, or 1.0 if either set is empty after
     * removal of Z.
     */
    public double pValueRankLe(int[] xRaw, int[] yRaw, int[] z, int r, int numPermutations) {
        int[] x = RankTests.diff(xRaw, z);
        int[] y = RankTests.diff(yRaw, z);

        int p = x.length, q = y.length;
        if (p == 0 || q == 0) return 1.0;
        int m = Math.min(p, q);
        if (r < 0 || r >= m) {
            throw new IllegalArgumentException("Rank bound r must satisfy 0 <= r < min(|x|, |y|) = " + m + ": " + r);
        }
        if (numPermutations < 1) throw new IllegalArgumentException("numPermutations must be positive.");

        // Per-domain trailing canonical-variate blocks and the fixed whitening of each side.
        List<Trailing> trailing = new ArrayList<>();
        for (int t = 0; t < data.size(); t++) {
            if (ns[t] - z.length < p + q + 2) continue; // too small to discriminate; skip
            Trailing tr = trailingVariates(data.get(t), x, y, z, r);
            if (tr != null) trailing.add(tr);
        }

        if (trailing.isEmpty()) return Double.NaN;

        double observed = 0.0;
        for (Trailing tr : trailing) observed += tr.term(null);

        Random rng = new Random(seed ^ (31L * java.util.Arrays.hashCode(x)
                                        + 131L * java.util.Arrays.hashCode(y)
                                        + 1031L * java.util.Arrays.hashCode(z) + r));

        int atLeast = 0;
        for (int b = 0; b < numPermutations; b++) {
            double lambda = 0.0;
            for (Trailing tr : trailing) lambda += tr.term(rng);
            if (lambda >= observed) atLeast++;
        }

        return (1.0 + atLeast) / (numPermutations + 1.0);
    }

    /**
     * One domain's prepared trailing blocks: the trailing canonical variates of the two sides, with each side's
     * fixed whitening (row permutations do not change a side's own covariance, only the cross term).
     */
    private record Trailing(SimpleMatrix ca, SimpleMatrix cb, double coeff, int[] perm) {

        /**
         * The domain's Bartlett term under a fresh random permutation of the first side's rows (or under the
         * identity when rng is null): coeff times the sum of log(1 - rho^2) over the canonical correlations between
         * the permuted first block and the second block.
         */
        double term(Random rng) {
            int n = ca.getNumRows();
            if (rng == null) {
                for (int i = 0; i < n; i++) perm[i] = i;
            } else {
                for (int i = 0; i < n; i++) perm[i] = i;
                for (int i = n - 1; i > 0; i--) {
                    int j = rng.nextInt(i + 1);
                    int tmp = perm[i];
                    perm[i] = perm[j];
                    perm[j] = tmp;
                }
            }

            int a = ca.getNumCols(), b = cb.getNumCols();

            // Cross-covariance of the permuted first block with the second block. The blocks are centered and each
            // side is whitened once at construction, so only the cross term needs recomputing.
            SimpleMatrix cross = new SimpleMatrix(a, b);
            for (int i = 0; i < n; i++) {
                int pi = perm[i];
                for (int j = 0; j < a; j++) {
                    double v = ca.get(pi, j);
                    if (v == 0.0) continue;
                    for (int k = 0; k < b; k++) {
                        cross.set(j, k, cross.get(j, k) + v * cb.get(i, k));
                    }
                }
            }
            cross = cross.divide(n - 1.0);

            SimpleSVD<SimpleMatrix> svd = cross.svd();
            int mm = Math.min(a, b);
            double sum = 0.0;
            for (int i = 0; i < mm; i++) {
                double rho = Math.max(0.0, Math.min(1.0 - 1e-15, svd.getSingleValue(i)));
                sum += Math.log(1.0 - rho * rho);
            }
            return coeff * sum;
        }
    }

    /**
     * Prepares one domain's trailing canonical-variate blocks for the sets x and y given z at rank r: residualizes
     * on z, computes the canonical variates of the residuals, keeps the variates past the first r, and whitens each
     * side so that only the cross term varies under permutation. Returns null when the trailing dimensions are not
     * positive.
     */
    private Trailing trailingVariates(double[][] domain, int[] x, int[] y, int[] z, int r) {
        int n = domain.length;
        int p = x.length, q = y.length;

        SimpleMatrix X = columns(domain, x);
        SimpleMatrix Y = columns(domain, y);

        if (z.length > 0) {
            SimpleMatrix Z = columns(domain, z);
            SimpleMatrix ZtZ = Z.transpose().mult(Z);
            for (int i = 0; i < z.length; i++) ZtZ.set(i, i, ZtZ.get(i, i) + 1e-8);
            SimpleMatrix H = ZtZ.invert().mult(Z.transpose());
            X = X.minus(Z.mult(H.mult(X)));
            Y = Y.minus(Z.mult(H.mult(Y)));
        }

        SimpleMatrix Sxx = X.transpose().mult(X).divide(n - 1.0);
        SimpleMatrix Syy = Y.transpose().mult(Y).divide(n - 1.0);
        SimpleMatrix Sxy = X.transpose().mult(Y).divide(n - 1.0);

        SimpleMatrix Wx = invSqrtPsd(Sxx);
        SimpleMatrix Wy = invSqrtPsd(Syy);

        SimpleSVD<SimpleMatrix> svd = Wx.mult(Sxy).mult(Wy).svd();
        SimpleMatrix U = svd.getU();
        SimpleMatrix V = svd.getV();

        int a = p - r, b = q - r;
        if (a <= 0 || b <= 0) return null;

        // Canonical variates; trailing columns r.. of each side.
        SimpleMatrix CA = X.mult(Wx).mult(U).extractMatrix(0, n, r, p);
        SimpleMatrix CB = Y.mult(Wy).mult(V).extractMatrix(0, n, r, q);

        // Whiten each side once; permutations leave a side's own covariance unchanged.
        CA = CA.mult(invSqrtPsd(CA.transpose().mult(CA).divide(n - 1.0)));
        CB = CB.mult(invSqrtPsd(CB.transpose().mult(CB).divide(n - 1.0)));

        double coeff = -((n - z.length) - 0.5 * (p + q + 1.0)); // times log Pi (1 - rho^2), which is negative

        return new Trailing(CA, CB, coeff, new int[n]);
    }

    private static SimpleMatrix columns(double[][] domain, int[] cols) {
        int n = domain.length;
        SimpleMatrix out = new SimpleMatrix(n, cols.length);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < cols.length; j++) {
                out.set(i, j, domain[i][cols[j]]);
            }
        }
        return out;
    }

    /**
     * Symmetric PSD inverse square root with a small eigenvalue floor, as elsewhere in the stratified machinery.
     */
    private static SimpleMatrix invSqrtPsd(SimpleMatrix A) {
        SimpleMatrix sym = A.plus(A.transpose()).divide(2.0);
        int n = sym.getNumRows();
        for (int i = 0; i < n; i++) sym.set(i, i, sym.get(i, i) + EPS);
        SimpleEVD<SimpleMatrix> evd = sym.eig();
        SimpleMatrix W = new SimpleMatrix(n, n);
        SimpleMatrix out = new SimpleMatrix(n, n);
        for (int i = 0; i < n; i++) {
            double e = Math.max(evd.getEigenvalue(i).getReal(), 1e-12);
            SimpleMatrix v = evd.getEigenVector(i);
            double invs = 1.0 / Math.sqrt(e);
            for (int row = 0; row < n; row++) W.set(row, i, v.get(row, 0) * invs);
        }
        SimpleMatrix V = new SimpleMatrix(n, n);
        for (int i = 0; i < n; i++) {
            SimpleMatrix v = evd.getEigenVector(i);
            for (int row = 0; row < n; row++) V.set(row, i, v.get(row, 0));
        }
        out = W.mult(V.transpose());
        return out;
    }
}

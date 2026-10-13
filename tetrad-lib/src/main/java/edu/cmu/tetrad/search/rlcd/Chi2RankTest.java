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

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.EigenDecomposition_F64;
import org.ejml.interfaces.decomposition.SingularValueDecomposition_F64;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The canonical-correlation chi-square rank test used by RLCD, translated from <code>Chi2RankTest.py</code> in the
 * causal-learn implementation.
 * <p>
 * For column sets P and Q with canonical correlations ρ_1 ≥ ρ_2 ≥ ... ≥ ρ_m (m = min(|P|, |Q|)), the null hypothesis
 * rank ≤ r is tested with the statistic
 * <pre>
 *     T = [ N − r − (|P| + |Q| + 1)/2 + Σ_{i &lt; r} (1/ρ_i² − 1) ] · Σ_{i ≥ r} −log(1 − ρ_i²)
 * </pre>
 * referred to a chi-square distribution with (|P| − r)(|Q| − r) degrees of freedom. The multiplier is Lawley's
 * correction to Bartlett's approximation; it differs from the plain Bartlett multiplier (N − 1 − (p + q + 1)/2) used
 * in {@link edu.cmu.tetrad.util.RankTests}, so this class is kept separate to reproduce the causal-learn behavior.
 * <p>
 * The two column sets may overlap. Shared columns produce canonical correlations of exactly 1, which are clamped at
 * 1 − 1e−15 before the logarithm, exactly as in the Python; they always fall among the first r correlations in the
 * way RLCD calls this test, so they never contribute to the statistic.
 * <p>
 * Canonical correlations are computed from the correlation matrix as the singular values of
 * Σ_PP^{−1/2} Σ_PQ Σ_QQ^{−1/2}, with eigenvalues of the diagonal blocks floored at a small positive value for
 * numerical safety. Results are cached per (P, Q) pair.
 *
 * @author josephramsey (translation)
 */
public final class Chi2RankTest implements RankTester {
    private static final double EIG_FLOOR = 1e-12;

    private final double[][] corr;
    private final int sampleSize;
    private final double nScaling;
    private final Map<String, double[]> cache = new ConcurrentHashMap<>();

    /**
     * Constructs the test from a data set. The data are standardized internally (the test depends only on the
     * correlation matrix and the sample size).
     *
     * @param dataSet a continuous data set.
     */
    public Chi2RankTest(DataSet dataSet) {
        this(new CorrelationMatrix(dataSet), 1.0);
    }

    /**
     * Constructs the test from a covariance or correlation matrix.
     *
     * @param cov      the matrix (it is converted to a correlation matrix).
     * @param nScaling a multiplier on the sample size (Python <code>N_scaling</code>; 1.0 is the default).
     */
    public Chi2RankTest(ICovarianceMatrix cov, double nScaling) {
        CorrelationMatrix c = (cov instanceof CorrelationMatrix cm) ? cm : new CorrelationMatrix(cov);
        this.corr = c.getMatrix().toArray();
        this.sampleSize = c.getSampleSize();
        this.nScaling = nScaling;
    }

    /**
     * Returns the sample size.
     *
     * @return the sample size used in the statistic.
     */
    public int getSampleSize() {
        return sampleSize;
    }

    @Override
    public boolean failToReject(int[] pcols, int[] qcols, int r, double alpha) {
        int p = pcols.length, q = qcols.length;
        if (p == 0 || q == 0) return false;
        int m = Math.min(p, q);
        int df = (p - r) * (q - r);
        // The Python compares the statistic against scipy's chi2.ppf, which is NaN for df <= 0, so the comparison
        // is false and the null is rejected.
        if (r < 0 || r >= m || df <= 0) return false;

        double stat = statistic(pcols, qcols, r);

        double critical = new ChiSquaredDistribution(df).inverseCumulativeProbability(1 - alpha);
        return stat <= critical;
    }

    /**
     * The statistic T of the class comment for the rank ≤ r test, to be referred to a chi-square distribution with
     * (|P| − r)(|Q| − r) degrees of freedom. The caller is responsible for those degrees of freedom being positive.
     *
     * @param pcols column indices of the first set.
     * @param qcols column indices of the second set.
     * @param r     the hypothesized maximum rank; 0 ≤ r &lt; min(|P|, |Q|).
     * @return the statistic.
     */
    public double statistic(int[] pcols, int[] qcols, int r) {
        int p = pcols.length, q = qcols.length;
        int m = Math.min(p, q);
        double[] rho = canonicalCorrelations(pcols, qcols);

        double stat = 0.0;
        for (int i = r; i < m; i++) {
            double li = Math.min(rho[i], 1 - 1e-15);
            stat += -Math.log(1 - li * li);
        }
        double ratio = 0.0;
        for (int i = 0; i < r; i++) {
            double li = rho[i];
            ratio += 1.0 / (li * li) - 1.0;
        }
        ratio += sampleSize * nScaling - r - 0.5 * (p + q + 1);
        return stat * ratio;
    }

    /**
     * The p-value of the rank ≤ r test (not used by RLCD's decision, which compares the statistic with the
     * critical value for numerical robustness, but useful for inspection).
     *
     * @param pcols column indices of the first set.
     * @param qcols column indices of the second set.
     * @param r     the hypothesized maximum rank.
     * @return the p-value, or 0 when the degrees of freedom are not positive.
     */
    @Override
    public double pValue(int[] pcols, int[] qcols, int r) {
        int p = pcols.length, q = qcols.length;
        int m = Math.min(p, q);
        int df = (p - r) * (q - r);
        if (p == 0 || q == 0 || r < 0 || r >= m || df <= 0) return 0.0;
        double stat = statistic(pcols, qcols, r);
        return 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(stat);
    }

    /**
     * Canonical correlations between the two column sets, in descending order, min(|P|, |Q|) of them, clamped to
     * [0, 1].
     *
     * @param pcols column indices of the first set.
     * @param qcols column indices of the second set.
     * @return the canonical correlations.
     */
    public double[] canonicalCorrelations(int[] pcols, int[] qcols) {
        int[] ps = pcols.clone(), qs = qcols.clone();
        Arrays.sort(ps);
        Arrays.sort(qs);
        String key = Arrays.toString(ps) + "|" + Arrays.toString(qs);
        return cache.computeIfAbsent(key, k -> compute(ps, qs));
    }

    private double[] compute(int[] ps, int[] qs) {
        int p = ps.length, q = qs.length, m = Math.min(p, q);
        DMatrixRMaj Sxx = block(ps, ps), Syy = block(qs, qs), Sxy = block(ps, qs);
        DMatrixRMaj Wx = invSqrtSym(Sxx), Wy = invSqrtSym(Syy);
        DMatrixRMaj tmp = new DMatrixRMaj(p, q);
        CommonOps_DDRM.mult(Wx, Sxy, tmp);
        DMatrixRMaj T = new DMatrixRMaj(p, q);
        CommonOps_DDRM.mult(tmp, Wy, T);
        SingularValueDecomposition_F64<DMatrixRMaj> svd =
                DecompositionFactory_DDRM.svd(p, q, false, false, true);
        if (!svd.decompose(T)) throw new IllegalStateException("SVD failed in Chi2RankTest.");
        double[] s = svd.getSingularValues().clone();
        Arrays.sort(s);
        double[] rho = new double[m];
        for (int i = 0; i < m; i++) {
            double v = s[s.length - 1 - i];
            if (Double.isNaN(v) || v < 0) v = 0;
            if (v > 1) v = 1;
            rho[i] = v;
        }
        return rho;
    }

    private DMatrixRMaj block(int[] rows, int[] cols) {
        DMatrixRMaj out = new DMatrixRMaj(rows.length, cols.length);
        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                out.set(i, j, corr[rows[i]][cols[j]]);
            }
        }
        return out;
    }

    /**
     * Symmetric inverse square root with eigenvalue floor.
     */
    private static DMatrixRMaj invSqrtSym(DMatrixRMaj A) {
        int n = A.numRows;
        DMatrixRMaj Asym = new DMatrixRMaj(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                Asym.set(i, j, 0.5 * (A.get(i, j) + A.get(j, i)));
            }
        }
        EigenDecomposition_F64<DMatrixRMaj> eig = DecompositionFactory_DDRM.eig(n, true, true);
        if (!eig.decompose(Asym)) throw new IllegalStateException("Eigendecomposition failed in Chi2RankTest.");
        DMatrixRMaj Q = new DMatrixRMaj(n, n);
        double[] invSqrt = new double[n];
        for (int i = 0; i < n; i++) {
            double lambda = eig.getEigenvalue(i).getReal();
            if (Double.isNaN(lambda) || lambda < EIG_FLOOR) lambda = EIG_FLOOR;
            invSqrt[i] = 1.0 / Math.sqrt(lambda);
            DMatrixRMaj v = eig.getEigenVector(i);
            for (int r = 0; r < n; r++) Q.set(r, i, v.get(r, 0));
        }
        // Q diag(invSqrt) Q^T
        DMatrixRMaj QD = new DMatrixRMaj(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) QD.set(i, j, Q.get(i, j) * invSqrt[j]);
        }
        DMatrixRMaj out = new DMatrixRMaj(n, n);
        CommonOps_DDRM.multTransB(QD, Q, out);
        return out;
    }
}

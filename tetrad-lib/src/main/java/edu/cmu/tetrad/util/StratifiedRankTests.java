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

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rank tests for cross-covariance matrices pooled across the strata of a discrete domain (or batch, site, cohort)
 * variable, after Theorem 4 of Dong, Dai, Ng, Spirtes, and Zhang, "Identifying Partially Observed Causal Models from
 * Heterogeneous/Nonstationary Data" (ICML 2026), the Phase 1 test of their LCD-NOD framework.
 * <p>
 * The null hypothesis rank(&Sigma;<sub>X,Y|T</sub>) &le; r is tested by computing, within each stratum t of the
 * domain variable T, the same Bartlett canonical-correlation statistic that {@link RankTests} uses on homogeneous
 * data,
 * <pre>
 *     stat_t = -c_t * sum over i &gt;= r of log(1 - rho_{t,i}^2),
 *     c_t = (n_t - 1) - (p + q + 1)/2,
 * </pre>
 * and summing: &Lambda; = &Sigma;<sub>t</sub> stat_t is referred to a chi-square distribution whose degrees of freedom
 * are (p - r)(q - r) summed over the strata that enter the sum. The strata are disjoint samples, so their statistics
 * are independent and add; this is the point of difference from {@link edu.cmu.tetrad.search.rlcd.PooledRankTest},
 * which combines statistics over multiple imputations of one sample (the imputations share rows and must not be
 * treated as independent) and from {@link RankTests#estimateWilksRankConditioned}, which conditions on a set of
 * continuous variables within one homogeneous sample by Schur complement.
 * <p>
 * Dong et al. prove (their Theorem 1) that when edge coefficients and exogenous noise change freely across and
 * within domains -- their Nonstationary POLCM model, with coefficient processes independent of noise processes given
 * the domain -- the set of achievable within-domain covariance matrices equals the covariance set of the
 * homogeneous linear model with the same graph. So within-stratum rank constraints carry the same t-separation
 * information as in the homogeneous case, while ranks and partial correlations of the strata mixed together
 * generally do not. Practically: concatenating batches and testing the pooled covariance over-rejects badly; this
 * test does not.
 * <p>
 * Small strata: a stratum enters a given test only if its Bartlett multiplier c_t is positive; strata too small to
 * discriminate are skipped, and their degrees of freedom are not counted. If no stratum is usable the p-value
 * methods return NaN and {@link #estimateRank} conservatively reports full rank, mirroring the convention of
 * {@link RankTests#estimateWilksRankFast}. The chi-square approximation is asymptotic in each stratum, so many
 * tiny strata make it rough even when the multipliers are positive; merging small domains is advisable.
 * <p>
 * The per-stratum canonical correlations for a given pair of column sets are cached, so sequential rank estimation
 * costs one CCA per stratum. The class is thread-safe after construction.
 *
 * @author josephramsey
 */
public final class StratifiedRankTests {

    /**
     * Diagonal ridge added to the within-set blocks before inversion, as in RankTests.estimateWilksRankFast.
     */
    private static final double EPS = 1e-10;

    /**
     * Ridge for the Z-block inversion in the conditioned test, as in RankTests.estimateWilksRankConditioned.
     */
    private static final double Z_RIDGE = 1e-8;

    /**
     * The per-stratum correlation matrices, all over the same variables in the same column order.
     */
    private final List<SimpleMatrix> covs;

    /**
     * The per-stratum sample sizes, parallel to covs.
     */
    private final int[] ns;

    /**
     * Cache of per-stratum squared canonical correlations, keyed by the (x, y, z) index sets of a query.
     */
    private final Map<String, double[][]> rho2Cache = new ConcurrentHashMap<>();

    /**
     * Constructs the test from per-stratum correlation (or covariance) matrices and sample sizes. All matrices must
     * be square, of the same dimension, and over the same variables in the same column order; this is the caller's
     * responsibility, since only index arithmetic happens here.
     *
     * @param covs the per-stratum correlation or covariance matrices.
     * @param ns   the per-stratum sample sizes, parallel to covs.
     */
    public StratifiedRankTests(List<SimpleMatrix> covs, int[] ns) {
        if (covs == null || covs.isEmpty()) throw new IllegalArgumentException("No strata given.");
        if (ns == null || ns.length != covs.size()) {
            throw new IllegalArgumentException("Sample sizes do not match strata: " + (ns == null ? 0 : ns.length)
                                               + " sizes for " + covs.size() + " strata.");
        }
        int d = covs.get(0).getNumRows();
        for (int t = 0; t < covs.size(); t++) {
            SimpleMatrix S = covs.get(t);
            if (S.getNumRows() != d || S.getNumCols() != d) {
                throw new IllegalArgumentException("Stratum " + t + " matrix is " + S.getNumRows() + " x "
                                                   + S.getNumCols() + "; expected " + d + " x " + d + ".");
            }
            if (ns[t] < 1) throw new IllegalArgumentException("Stratum " + t + " has sample size " + ns[t] + ".");
        }
        this.covs = new ArrayList<>(covs);
        this.ns = Arrays.copyOf(ns, ns.length);
    }

    /**
     * Constructs the test from one data set per stratum. The data sets must all have the same variables in the same
     * order; each is reduced to its correlation matrix.
     *
     * @param dataSets one continuous data set per stratum.
     * @return the stratified test.
     */
    public static StratifiedRankTests fromDataSets(List<DataSet> dataSets) {
        if (dataSets == null || dataSets.isEmpty()) throw new IllegalArgumentException("No data sets given.");
        List<Node> vars0 = dataSets.get(0).getVariables();
        List<SimpleMatrix> covs = new ArrayList<>();
        int[] ns = new int[dataSets.size()];
        for (int t = 0; t < dataSets.size(); t++) {
            DataSet d = dataSets.get(t);
            if (!d.getVariableNames().equals(dataSets.get(0).getVariableNames())) {
                throw new IllegalArgumentException("Data set " + t + " does not have the same variables, in the same "
                                                   + "order, as data set 0.");
            }
            covs.add(new CorrelationMatrix(d).getMatrix().getSimpleMatrix());
            ns[t] = d.getNumRows();
        }
        if (vars0.isEmpty()) throw new IllegalArgumentException("Data sets have no variables.");
        return new StratifiedRankTests(covs, ns);
    }

    /**
     * Splits a data set into one data set per value of a discrete domain column, dropping that column. The domain
     * variable must be discrete, or a continuous column whose values are all integral (batch codes stored as
     * doubles are accepted).
     *
     * @param dataSet the data set, containing the domain column.
     * @param domain  the domain variable; a column of dataSet.
     * @return the per-stratum data sets, over the remaining variables, in ascending order of domain value.
     */
    public static List<DataSet> split(DataSet dataSet, Node domain) {
        int dcol = dataSet.getVariables().indexOf(domain);
        if (dcol < 0) {
            throw new IllegalArgumentException("Variable " + domain.getName() + " is not a column of the data set.");
        }

        boolean discrete = dataSet.getVariable(dcol) instanceof edu.cmu.tetrad.data.DiscreteVariable;

        Map<Integer, List<Integer>> rowsByValue = new LinkedHashMap<>();
        for (int i = 0; i < dataSet.getNumRows(); i++) {
            int v;
            if (discrete) {
                v = dataSet.getInt(i, dcol);
            } else {
                double x = dataSet.getDouble(i, dcol);
                if (Double.isNaN(x) || x != Math.rint(x)) {
                    throw new IllegalArgumentException("Domain column " + domain.getName() + " has non-integral value "
                                                       + x + " in row " + i + "; a domain index must be discrete.");
                }
                v = (int) Math.rint(x);
            }
            rowsByValue.computeIfAbsent(v, k -> new ArrayList<>()).add(i);
        }

        int[] keep = new int[dataSet.getNumColumns() - 1];
        int k = 0;
        for (int j = 0; j < dataSet.getNumColumns(); j++) if (j != dcol) keep[k++] = j;

        List<Integer> values = new ArrayList<>(rowsByValue.keySet());
        java.util.Collections.sort(values);

        List<DataSet> out = new ArrayList<>();
        for (int v : values) {
            List<Integer> rowList = rowsByValue.get(v);
            int[] rows = rowList.stream().mapToInt(Integer::intValue).toArray();
            out.add(dataSet.subsetRowsColumns(rows, keep));
        }
        return out;
    }

    /**
     * Resolves the name of a domain column for a data set: the named column if it exists; otherwise, if the data
     * set has exactly one discrete column, that column, with a note logged; otherwise an exception listing the
     * discrete columns found. Shared by the interface wrappers that take a domain-column parameter.
     *
     * @param dataSet   the data set.
     * @param requested the requested column name; may be null or blank.
     * @return the resolved column name.
     */
    public static String resolveDomainColumn(DataSet dataSet, String requested) {
        if (requested != null && !requested.trim().isEmpty() && dataSet.getVariable(requested.trim()) != null) {
            return requested.trim();
        }

        List<String> discreteColumns = new ArrayList<>();
        for (Node node : dataSet.getVariables()) {
            if (node instanceof edu.cmu.tetrad.data.DiscreteVariable) {
                discreteColumns.add(node.getName());
            }
        }

        if (discreteColumns.size() == 1) {
            TetradLogger.getInstance().log("Stratified mode: no column named '" + requested + "'; using the one "
                                           + "discrete column, '" + discreteColumns.get(0) + "', as the domain index.");
            return discreteColumns.get(0);
        }

        String found = discreteColumns.isEmpty()
                ? "the data set has no discrete columns"
                : "the discrete columns found are " + discreteColumns;
        throw new IllegalArgumentException("Stratified mode needs a domain column, but there is no column named '"
                                           + requested + "', and " + found + ". Set the 'Domain column name' "
                                           + "parameter to the column holding the domain (batch, site, cohort) "
                                           + "index.");
    }

    /**
     * Returns the number of strata.
     *
     * @return the number of strata.
     */
    public int getNumStrata() {
        return covs.size();
    }

    /**
     * Returns the total sample size over all strata.
     *
     * @return the total sample size.
     */
    public int getTotalSampleSize() {
        int total = 0;
        for (int n : ns) total += n;
        return total;
    }

    /**
     * Returns the dimension (number of variables) of the stratum matrices.
     *
     * @return the dimension.
     */
    public int getDimension() {
        return covs.get(0).getNumRows();
    }

    /**
     * The p-value for the null hypothesis rank(&Sigma;<sub>X,Y|T</sub>) &le; r, pooling the Bartlett statistic over
     * strata. The column sets may overlap, as they do when conditional independence is tested through augmented
     * sets; shared columns produce canonical correlations of one, which occupy the leading positions and do not
     * enter the statistic when r is at least the overlap size.
     *
     * @param x the column indices of the first set.
     * @param y the column indices of the second set.
     * @param r the rank bound under the null, with 0 &le; r &lt; min(|x|, |y|).
     * @return the p-value, or NaN if no stratum is large enough to contribute.
     */
    public double pValueRankLe(int[] x, int[] y, int r) {
        return pValueRankLeConditioned(x, y, new int[0], r);
    }

    /**
     * The p-value for the null hypothesis rank(&Sigma;<sub>X,Y|Z,T</sub>) &le; r, where within each stratum the
     * cross-covariance of X and Y is first conditioned on the variable set Z by Schur complement, and the Bartlett
     * statistic is then pooled over strata. With Z empty this is {@link #pValueRankLe}. Z is removed from X and Y
     * first.
     *
     * @param xRaw the column indices of the first set.
     * @param yRaw the column indices of the second set.
     * @param z    the column indices of the conditioning set; may be empty.
     * @param r    the rank bound under the null.
     * @return the p-value, or NaN if no stratum is large enough to contribute, or 1.0 if either set is empty after
     * removal of Z.
     */
    public double pValueRankLeConditioned(int[] xRaw, int[] yRaw, int[] z, int r) {
        int[] x = RankTests.diff(xRaw, z);
        int[] y = RankTests.diff(yRaw, z);

        int p = x.length, q = y.length;
        if (p == 0 || q == 0) return 1.0;
        int m = Math.min(p, q);
        if (r < 0 || r >= m) {
            throw new IllegalArgumentException("Rank bound r must satisfy 0 <= r < min(|x|, |y|) = " + m + ": " + r);
        }

        double[][] rho2 = rho2ByStratum(x, y, z);

        double lambda = 0.0;
        int df = 0;

        for (int t = 0; t < covs.size(); t++) {
            double c = bartlettMultiplier(ns[t] - z.length, p, q);
            if (c <= 0) continue; // stratum too small to discriminate; skip it and its df

            double sumLog = 0.0;
            for (int i = r; i < rho2[t].length; i++) {
                sumLog += Math.log(Math.max(1e-15, 1.0 - rho2[t][i]));
            }

            lambda += -c * sumLog;
            df += (p - r) * (q - r);
        }

        if (df <= 0) return Double.NaN;

        return 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(lambda);
    }

    /**
     * Estimates the rank of &Sigma;<sub>X,Y|T</sub> as the smallest r whose null hypothesis rank &le; r is not
     * rejected at the given level, mirroring {@link RankTests#estimateWilksRankFast}. If no stratum is large enough
     * to discriminate, min(|x|, |y|) is returned, conservatively reporting full rank.
     *
     * @param x     the column indices of the first set.
     * @param y     the column indices of the second set.
     * @param alpha the significance level.
     * @return the estimated rank.
     */
    public int estimateRank(int[] x, int[] y, double alpha) {
        return estimateRankConditioned(x, y, new int[0], alpha);
    }

    /**
     * Estimates the rank of &Sigma;<sub>X,Y|Z,T</sub>, conditioning on the variable set Z within each stratum by
     * Schur complement and pooling over strata, mirroring {@link RankTests#estimateWilksRankConditioned}. Z is
     * removed from X and Y first. If either set is empty after removal, 0 is returned; if no stratum is large
     * enough to discriminate, min(|x|, |y|) is returned.
     *
     * @param xRaw  the column indices of the first set.
     * @param yRaw  the column indices of the second set.
     * @param z     the column indices of the conditioning set; may be empty.
     * @param alpha the significance level.
     * @return the estimated rank.
     */
    public int estimateRankConditioned(int[] xRaw, int[] yRaw, int[] z, double alpha) {
        int[] x = RankTests.diff(xRaw, z);
        int[] y = RankTests.diff(yRaw, z);

        int p = x.length, q = y.length;
        if (p == 0 || q == 0) return 0;
        int m = Math.min(p, q);

        for (int r = 0; r < m; r++) {
            double pv = pValueRankLeConditioned(x, y, z, r);
            if (Double.isNaN(pv)) return m; // no usable stratum: conservatively full rank
            if (pv > alpha) return r;
        }

        return m;
    }

    /**
     * The squared canonical correlations between the x and y columns within each stratum, conditioned on z by Schur
     * complement when z is nonempty, cached per (x, y, z).
     */
    private double[][] rho2ByStratum(int[] x, int[] y, int[] z) {
        String key = Arrays.toString(x) + "|" + Arrays.toString(y) + "|" + Arrays.toString(z);
        return rho2Cache.computeIfAbsent(key, k -> {
            double[][] out = new double[covs.size()][];
            for (int t = 0; t < covs.size(); t++) {
                out[t] = squaredCanonicalCorrelations(covs.get(t), x, y, z);
            }
            return out;
        });
    }

    /**
     * The Bartlett multiplier (n - 1) - (p + q + 1)/2 of {@link RankTests#rankLeByWilks}, with n reduced by the size
     * of the conditioning set by the caller.
     */
    private static double bartlettMultiplier(int n, int p, int q) {
        return (n - 1.0) - 0.5 * (p + q + 1.0);
    }

    /**
     * Squared canonical correlations of S[x, y], conditioned on S[z, z] by Schur complement when z is nonempty,
     * in descending order, computed as in RankTests.estimateWilksRankFast: eigenvalues of
     * Sxx^{-1} Sxy Syy^{-1} Syx on the smaller side, clamped to [0, 1].
     */
    private static double[] squaredCanonicalCorrelations(SimpleMatrix S, int[] x, int[] y, int[] z) {
        SimpleMatrix Sxx = block(S, x, x);
        SimpleMatrix Syy = block(S, y, y);
        SimpleMatrix Sxy = block(S, x, y);

        if (z.length > 0) {
            SimpleMatrix Sxz = block(S, x, z);
            SimpleMatrix Syz = block(S, y, z);
            SimpleMatrix Szz = block(S, z, z);

            SimpleMatrix SzzR = Szz.copy();
            for (int i = 0; i < SzzR.getNumRows(); i++) SzzR.set(i, i, SzzR.get(i, i) + Z_RIDGE);
            SimpleMatrix SzzInv = SzzR.invert();

            Sxx = Sxx.minus(Sxz.mult(SzzInv).mult(Sxz.transpose()));
            Syy = Syy.minus(Syz.mult(SzzInv).mult(Syz.transpose()));
            Sxy = Sxy.minus(Sxz.mult(SzzInv).mult(Syz.transpose()));

            Sxx = Sxx.plus(Sxx.transpose()).divide(2.0);
            Syy = Syy.plus(Syy.transpose()).divide(2.0);
        }

        int p = x.length, q = y.length;
        int m = Math.min(p, q);

        for (int i = 0; i < p; i++) Sxx.set(i, i, Sxx.get(i, i) + EPS);
        for (int i = 0; i < q; i++) Syy.set(i, i, Syy.get(i, i) + EPS);

        SimpleMatrix SxxInv = Sxx.invert();
        SimpleMatrix SyyInv = Syy.invert();
        SimpleMatrix Syx = Sxy.transpose();

        SimpleMatrix M = (p <= q)
                ? SxxInv.mult(Sxy).mult(SyyInv).mult(Syx)   // p x p
                : SyyInv.mult(Syx).mult(SxxInv).mult(Sxy);  // q x q

        double[] evals = eigSymmetricClamped(M);
        Arrays.sort(evals);

        // Reverse to descending order and keep the first m.
        double[] rho2 = new double[m];
        for (int i = 0; i < m; i++) rho2[i] = evals[evals.length - 1 - i];
        return rho2;
    }

    /**
     * Extract S[rows, cols].
     */
    private static SimpleMatrix block(SimpleMatrix S, int[] rows, int[] cols) {
        SimpleMatrix out = new SimpleMatrix(rows.length, cols.length);
        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                out.set(i, j, S.get(rows[i], cols[j]));
            }
        }
        return out;
    }

    /**
     * Eigenvalues of a (nominally symmetric PSD) matrix, clamped to [0, 1], as in RankTests.
     */
    private static double[] eigSymmetricClamped(SimpleMatrix A) {
        SimpleEVD<SimpleMatrix> evd = A.eig();
        int n = A.numCols();
        double[] vals = new double[n];
        for (int i = 0; i < n; i++) {
            double v = evd.getEigenvalue(i).getReal();
            if (Double.isNaN(v) || v < 0) v = 0;
            if (v > 1) v = 1;
            vals[i] = v;
        }
        return vals;
    }
}

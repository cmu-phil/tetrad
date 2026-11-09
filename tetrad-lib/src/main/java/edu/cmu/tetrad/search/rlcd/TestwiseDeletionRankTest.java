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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A rank test by testwise deletion: each rank query is answered from the rows that are complete on the columns that
 * query mentions, with its own sample size. The statistic is the same Lawley-corrected canonical-correlation
 * chi-square as {@link Chi2RankTest}; only the matrix and the sample size differ from query to query.
 * <p>
 * <b>Why this exists.</b> The alternative for data with missing values is to impute and pool
 * ({@link PooledRankTest}). Regression imputation places imputed values on a linear manifold spanned by the observed
 * variables, which is the very low-rank cross-covariance a rank test looks for, so imputation can manufacture the
 * structure RLCD then reports; the more missing a variable is, the stronger the effect. Testwise deletion invents no
 * values. It pays for that with the three limitations below, so this is an alternative to be compared against
 * pooling, not a better default.
 * <p>
 * <b>Limitation 1, selection.</b> Each query's rows are selected on being complete over that query's columns. Under
 * MCAR this selection is ignorable and each decision is consistent. Under MAR or MNAR it generally is not: the
 * subsample correlation is a selected-sample correlation, and a query can be answered from a subpopulation rather
 * than the population. A rank deficiency can be created or destroyed this way.
 * <p>
 * <b>Limitation 2, incoherence across queries.</b> Different queries use different subsamples, so the set of rank
 * constraints RLCD accumulates need not come from any single population covariance matrix. Each decision can be
 * sound while the structure assembled from many of them is not. This is the per-decision versus per-objective
 * distinction already recorded for testwise deletion with BOSS and GRaSP, and RLCD's stage-2 cluster search is very
 * much a per-objective procedure: it builds covers out of many accepted deficiencies.
 * <p>
 * <b>Limitation 3, shrinking sample size.</b> A query over several heavily missing columns can leave very few rows.
 * Two guards apply, and both resolve to <i>reject</i> (report no rank deficiency), which suppresses clusters rather
 * than inventing them:
 * <ul>
 *     <li>fewer than {@code minSampleSize} complete rows (default 50);</li>
 *     <li>a non-positive Lawley multiplier, that is N − r − (|P| + |Q| + 1)/2 ≤ 0. This guard matters: left alone, a
 *     negative multiplier makes the statistic negative, the statistic falls below every critical value, and the test
 *     fails to reject for no reason but a small sample, which would hand RLCD a free cluster.</li>
 * </ul>
 * Use {@link #getSkippedQueries()} and {@link #getSampleSizeSummary()} after a search to see how often the guards
 * fired and how much data the decisions actually used. A large skip count means the result rests on the guard, not
 * on the data.
 * <p>
 * Correlation matrices are computed once per column union and cached, so the several queries that share a union (the
 * different splits and ranks RLCD tries) are computed once.
 *
 * @author josephramsey
 * @see PooledRankTest
 * @see Chi2RankTest
 */
public final class TestwiseDeletionRankTest implements RankTester {

    /**
     * Rows by columns, with {@link Double#NaN} for missing.
     */
    private final double[][] data;
    private final double nScaling;
    private int minSampleSize = 50;

    private final Map<String, Delegate> cache = new ConcurrentHashMap<>();
    private final AtomicInteger queries = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();
    private final Map<Integer, Integer> sampleSizes = new ConcurrentHashMap<>();

    /**
     * A per-union correlation matrix and the test over it; null test when the union has too few complete rows.
     */
    private record Delegate(int[] union, int n, Chi2RankTest test) {
    }

    /**
     * Constructs the test over a data set that may contain missing values.
     *
     * @param dataSet a continuous data set; missing values are NaN.
     */
    public TestwiseDeletionRankTest(DataSet dataSet) {
        this(dataSet, 1.0);
    }

    /**
     * Constructs the test over a data set that may contain missing values.
     *
     * @param dataSet  a continuous data set; missing values are NaN.
     * @param nScaling a multiplier on each query's sample size; 1.0 is the default.
     */
    public TestwiseDeletionRankTest(DataSet dataSet, double nScaling) {
        if (dataSet == null) throw new NullPointerException("Data set is null.");
        if (!dataSet.isContinuous()) throw new IllegalArgumentException("Testwise deletion requires continuous data.");
        int n = dataSet.getNumRows(), p = dataSet.getNumColumns();
        this.data = new double[n][p];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) this.data[i][j] = dataSet.getDouble(i, j);
        }
        this.nScaling = nScaling;
    }

    /**
     * Sets the least number of complete rows a query must have to be answered; below it the query is reported as a
     * rejection, so no rank deficiency is claimed.
     *
     * @param minSampleSize the minimum; must be at least 10.
     */
    public void setMinSampleSize(int minSampleSize) {
        if (minSampleSize < 10) throw new IllegalArgumentException("Minimum sample size must be at least 10.");
        this.minSampleSize = minSampleSize;
    }

    @Override
    public boolean failToReject(int[] pcols, int[] qcols, int r, double alpha) {
        Query q = prepare(pcols, qcols, r);
        if (q == null) return false;
        return q.delegate().test().failToReject(q.p(), q.q(), r, alpha);
    }

    @Override
    public double pValue(int[] pcols, int[] qcols, int r) {
        Query q = prepare(pcols, qcols, r);
        if (q == null) return 0.0;
        return q.delegate().test().pValue(q.p(), q.q(), r);
    }

    /**
     * The number of rank queries made.
     *
     * @return the count.
     */
    public int getQueries() {
        return queries.get();
    }

    /**
     * The number of queries resolved as rejections by a guard rather than by the test: too few complete rows, or a
     * non-positive Lawley multiplier.
     *
     * @return the count.
     */
    public int getSkippedQueries() {
        return skipped.get();
    }

    /**
     * A one-line summary of the complete-row counts of the distinct column unions tested: how many unions, and the
     * least, median, and greatest number of complete rows among them. Read against the number of rows in the data,
     * this says how much of the data the decisions rested on.
     *
     * @return the summary.
     */
    public String getSampleSizeSummary() {
        if (sampleSizes.isEmpty()) return "no queries yet";
        int[] ns = sampleSizes.values().stream().mapToInt(Integer::intValue).sorted().toArray();
        return String.format("%d column unions tested; complete rows min %d, median %d, max %d (of %d rows)",
                ns.length, ns[0], ns[ns.length / 2], ns[ns.length - 1], data.length);
    }

    // ------------------------------------------------------------------ internals

    private record Query(Delegate delegate, int[] p, int[] q) {
    }

    /**
     * Resolves a query against its union's correlation matrix, or returns null when a guard fires.
     */
    private Query prepare(int[] pcols, int[] qcols, int r) {
        queries.incrementAndGet();
        if (pcols.length == 0 || qcols.length == 0) return null;

        int[] union = union(pcols, qcols);
        Delegate d = cache.computeIfAbsent(Arrays.toString(union), k -> build(union));
        if (d.test() == null) {
            skipped.incrementAndGet();
            return null;
        }
        // Lawley multiplier N - r - (p + q + 1)/2 must be positive, or the statistic turns negative and the test
        // fails to reject on nothing but a small sample.
        if (d.n() - r - 0.5 * (pcols.length + qcols.length + 1) <= 0) {
            skipped.incrementAndGet();
            return null;
        }
        return new Query(d, remap(pcols, d.union()), remap(qcols, d.union()));
    }

    private Delegate build(int[] union) {
        int u = union.length;
        int rows = 0;
        boolean[] keep = new boolean[data.length];
        for (int i = 0; i < data.length; i++) {
            boolean ok = true;
            for (int j : union) {
                if (Double.isNaN(data[i][j])) {
                    ok = false;
                    break;
                }
            }
            keep[i] = ok;
            if (ok) rows++;
        }
        sampleSizes.put(Arrays.hashCode(union), rows);
        if (rows < Math.max(minSampleSize, u + 2)) return new Delegate(union, rows, null);

        double[] mean = new double[u];
        for (int i = 0; i < data.length; i++) {
            if (!keep[i]) continue;
            for (int a = 0; a < u; a++) mean[a] += data[i][union[a]];
        }
        for (int a = 0; a < u; a++) mean[a] /= rows;

        double[][] cov = new double[u][u];
        for (int i = 0; i < data.length; i++) {
            if (!keep[i]) continue;
            for (int a = 0; a < u; a++) {
                double da = data[i][union[a]] - mean[a];
                for (int b = a; b < u; b++) {
                    cov[a][b] += da * (data[i][union[b]] - mean[b]);
                }
            }
        }
        for (int a = 0; a < u; a++) {
            for (int b = a; b < u; b++) {
                double v = cov[a][b] / (rows - 1.0);
                cov[a][b] = v;
                cov[b][a] = v;
            }
        }

        double[] sd = new double[u];
        for (int a = 0; a < u; a++) sd[a] = Math.sqrt(Math.max(cov[a][a], 1e-30));
        double[][] corr = new double[u][u];
        for (int a = 0; a < u; a++) {
            for (int b = 0; b < u; b++) corr[a][b] = cov[a][b] / (sd[a] * sd[b]);
            corr[a][a] = 1.0;
        }

        return new Delegate(union, rows, new Chi2RankTest(corr, rows, nScaling));
    }

    private static int[] union(int[] pcols, int[] qcols) {
        int[] all = new int[pcols.length + qcols.length];
        System.arraycopy(pcols, 0, all, 0, pcols.length);
        System.arraycopy(qcols, 0, all, pcols.length, qcols.length);
        Arrays.sort(all);
        int[] out = new int[all.length];
        int m = 0;
        for (int i = 0; i < all.length; i++) {
            if (i == 0 || all[i] != all[i - 1]) out[m++] = all[i];
        }
        return Arrays.copyOf(out, m);
    }

    private static int[] remap(int[] cols, int[] union) {
        int[] out = new int[cols.length];
        for (int i = 0; i < cols.length; i++) out[i] = Arrays.binarySearch(union, cols[i]);
        return out;
    }

    /**
     * The variable names of the data, for messages.
     *
     * @param dataSet the data set this test was built from.
     * @return the names.
     */
    public static String describe(DataSet dataSet) {
        StringBuilder b = new StringBuilder();
        List<Node> variables = dataSet.getVariables();
        for (int j = 0; j < variables.size(); j++) {
            int missing = 0;
            for (int i = 0; i < dataSet.getNumRows(); i++) if (Double.isNaN(dataSet.getDouble(i, j))) missing++;
            if (missing > 0) {
                b.append(String.format("%s %.1f%% missing; ", variables.get(j).getName(),
                        100.0 * missing / dataSet.getNumRows()));
            }
        }
        return b.isEmpty() ? "no missing values" : b.toString();
    }
}

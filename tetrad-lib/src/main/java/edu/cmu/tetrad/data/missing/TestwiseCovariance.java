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

package edu.cmu.tetrad.data.missing;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-wise (family-wise) covariance estimation for components whose local calculations consume a covariance
 * submatrix and a sample size: for a requested column set, the sample covariance matrix is computed over exactly the
 * rows that are complete on those columns, together with the number of such rows. This is the per-family
 * statistic that {@link edu.cmu.tetrad.search.score.SemBicScore} computes inline; it is factored out here so that
 * other covariance-consuming scores and tests (BGe, the embedded basis-function scores, the degenerate-Gaussian and
 * basis-function likelihood-ratio tests) can offer the same TESTWISE missing-data policy without each re-deriving
 * the row bookkeeping.
 * <p>
 * Missing values are NaN entries of the underlying matrix. Results are cached per (sorted) column set, so a search
 * that revisits the same family pays for the covariance once. Row subsets supplied by a caller (e.g., from
 * subsampling) bypass the cache.
 * <p>
 * Every family is estimated on its own subsample, so the collection of family covariances is not in general
 * consistent with any single positive-definite matrix, and a score assembled from them is not the score of any
 * one dataset. This is the standard caveat of test-wise deletion and is unbiased only under MCAR.
 */
public final class TestwiseCovariance {

    /**
     * The data, rows by columns; NaN marks a missing entry.
     */
    private final Matrix data;

    /**
     * Cache of family statistics keyed by the sorted column set, for the all-rows case.
     */
    private final Map<ColumnKey, Family> cache = new ConcurrentHashMap<>();

    /**
     * Constructs the helper over a data matrix.
     *
     * @param data The data matrix (rows are cases, columns are variables); NaN marks a missing entry.
     */
    public TestwiseCovariance(Matrix data) {
        if (data == null) {
            throw new NullPointerException("Data matrix is null.");
        }

        this.data = data;
    }

    /**
     * Returns the number of rows of the underlying matrix.
     *
     * @return This number.
     */
    public int getNumRows() {
        return this.data.getNumRows();
    }

    /**
     * Returns the number of columns of the underlying matrix.
     *
     * @return This number.
     */
    public int getNumColumns() {
        return this.data.getNumColumns();
    }

    /**
     * Returns the sample covariance matrix (divisor n - 1) of the given columns over the rows complete on all of
     * them, in the order the columns are given, together with the number of such rows. Cached per column set.
     *
     * @param columns The columns; need not be sorted; must not contain duplicates. The result is indexed in this
     *                order.
     * @return The family statistics.
     */
    public Family family(int[] columns) {
        return family(columns, null);
    }

    /**
     * As {@link #family(int[])}, but restricted to the given candidate rows (null means all rows). Results for
     * explicit candidate row lists are not cached.
     *
     * @param columns       The columns.
     * @param candidateRows The rows to draw from, or null for all rows.
     * @return The family statistics.
     */
    public Family family(int[] columns, List<Integer> candidateRows) {
        if (columns == null) {
            throw new NullPointerException("Columns are null.");
        }

        boolean allRows = candidateRows == null || candidateRows.size() == this.data.getNumRows();

        if (!allRows) {
            return compute(columns, candidateRows);
        }

        // The cache stores each family in sorted column order; the result is permuted to the requested order, so
        // that callers requesting the same set in different orders (child-first vs. parents-first) each get a
        // covariance aligned with their own column array.
        int[] sorted = columns.clone();
        Arrays.sort(sorted);
        Family stored = this.cache.computeIfAbsent(new ColumnKey(sorted), key -> compute(sorted, null));

        if (Arrays.equals(sorted, columns)) {
            return stored;
        }

        int k = columns.length;
        int[] pos = new int[k];
        for (int i = 0; i < k; i++) pos[i] = Arrays.binarySearch(sorted, columns[i]);

        Matrix cov = new Matrix(k, k);
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                cov.set(i, j, stored.cov().get(pos[i], pos[j]));
            }
        }

        return new Family(cov, stored.n(), stored.rows());
    }

    /**
     * Returns the sample variance (divisor n - 1) of one column over its non-missing rows, and the count of those
     * rows, as a one-column family.
     *
     * @param column The column.
     * @return The family statistics for the single column.
     */
    public Family column(int column) {
        return family(new int[]{column});
    }

    /**
     * Returns a pairwise-deletion covariance matrix over the given variables: entry (i, j) is the covariance of
     * columns i and j over the rows complete on both, with each diagonal entry computed over the rows complete on
     * that column alone. The reported sample size is the smallest pairwise complete count. The result is not
     * guaranteed positive definite; it is intended for screening computations (e.g., pairwise correlation-based
     * basis-column selection) rather than for fitting.
     *
     * @param variables The variables, one per column of the underlying matrix.
     * @return The pairwise covariance matrix.
     */
    public ICovarianceMatrix pairwiseCovarianceMatrix(List<Node> variables) {
        int p = this.data.getNumColumns();

        if (variables.size() != p) {
            throw new IllegalArgumentException("Expected " + p + " variables but got " + variables.size() + ".");
        }

        Matrix cov = new Matrix(p, p);
        int minN = Integer.MAX_VALUE;

        for (int i = 0; i < p; i++) {
            Family fi = column(i);
            cov.set(i, i, fi.cov().get(0, 0));
            minN = Math.min(minN, fi.n());

            for (int j = i + 1; j < p; j++) {
                Family fij = family(new int[]{i, j});
                double c = fij.n() > 1 ? fij.cov().get(0, 1) : 0.0;
                cov.set(i, j, c);
                cov.set(j, i, c);
                minN = Math.min(minN, fij.n());
            }
        }

        if (minN == Integer.MAX_VALUE) minN = 0;

        return new CovarianceMatrix(variables, cov, Math.max(minN, 2));
    }

    /**
     * Clears the family cache.
     */
    public void clearCache() {
        this.cache.clear();
    }

    private Family compute(int[] columns, List<Integer> candidateRows) {
        List<Integer> rows = TestwiseRows.forMatrix(this.data).validRows(columns, candidateRows);
        int n = rows.size();
        int k = columns.length;

        double[] means = new double[k];

        for (int r : rows) {
            for (int j = 0; j < k; j++) {
                means[j] += this.data.get(r, columns[j]);
            }
        }

        for (int j = 0; j < k; j++) {
            means[j] = n > 0 ? means[j] / n : Double.NaN;
        }

        Matrix cov = new Matrix(k, k);

        if (n > 1) {
            for (int r : rows) {
                for (int a = 0; a < k; a++) {
                    double da = this.data.get(r, columns[a]) - means[a];

                    for (int b = a; b < k; b++) {
                        double db = this.data.get(r, columns[b]) - means[b];
                        cov.set(a, b, cov.get(a, b) + da * db);
                    }
                }
            }

            for (int a = 0; a < k; a++) {
                for (int b = a; b < k; b++) {
                    double v = cov.get(a, b) / (n - 1.0);
                    cov.set(a, b, v);
                    cov.set(b, a, v);
                }
            }
        } else {
            for (int a = 0; a < k; a++) {
                for (int b = 0; b < k; b++) {
                    cov.set(a, b, Double.NaN);
                }
            }
        }

        return new Family(cov, n, rows);
    }

    /**
     * The covariance matrix of a column set over the rows complete on that set, the number of such rows, and the
     * rows themselves. The covariance is indexed in the order the columns were requested.
     *
     * @param cov  The covariance matrix (divisor n - 1), or all-NaN if fewer than two complete rows exist.
     * @param n    The number of complete rows.
     * @param rows The complete rows, in increasing order.
     */
    public record Family(Matrix cov, int n, List<Integer> rows) {
    }

    private static final class ColumnKey {
        private final int[] sorted;
        private final int hash;

        private ColumnKey(int[] columns) {
            this.sorted = columns.clone();
            Arrays.sort(this.sorted);
            this.hash = Arrays.hashCode(this.sorted);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ColumnKey other)) return false;
            return Arrays.equals(this.sorted, other.sorted);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }
}

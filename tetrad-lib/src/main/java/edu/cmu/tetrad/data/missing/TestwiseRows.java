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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.Matrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Computes, and caches, the row subsets used by test-wise deletion: for a given set of columns, the rows of a dataset
 * on which all of those columns are observed. Searches ask for the same local row subsets repeatedly (every local
 * score or independence test involving the same variable set needs the same rows), and recomputing them is the main
 * overhead of the test-wise path; this class computes each subset once per column set.
 * <p>
 * Instances are obtained per dataset from {@link #forDataSet(DataSet)}, which maintains a weak registry so that a
 * score and a test running on the same dataset share one cache, and so that caches are garbage-collected with their
 * datasets. Caching is on by default. The cache does not observe mutations to the dataset; if a dataset is modified
 * in place after rows have been cached, call {@link #clearCache()} (or {@link #invalidate(DataSet)}).
 * <p>
 * "Missing" is defined by {@link MissingDataAudit#isMissing(DataSet, int, int)}: NaN for continuous variables and
 * {@link edu.cmu.tetrad.data.DiscreteVariable#MISSING_VALUE} for discrete variables, so this class serves the
 * continuous, discrete, and mixed cases uniformly.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see MissingDataPolicy#TESTWISE
 */
public final class TestwiseRows {

    /**
     * The per-dataset registry. Weak keys so caches die with their datasets; synchronized so that
     * {@link #forDataSet(DataSet)} is atomic.
     */
    private static final Map<DataSet, TestwiseRows> REGISTRY
            = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * The per-matrix registry, for callers that hold a raw data Matrix rather than a DataSet (e.g., the internal
     * covariance path of SemBicScore). Weak identity keys: Matrix does not override equals/hashCode.
     */
    private static final Map<Matrix, TestwiseRows> MATRIX_REGISTRY
            = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * The dataset, or null if this instance wraps a raw matrix.
     */
    private final DataSet dataSet;

    /**
     * The raw data matrix, or null if this instance wraps a dataset. Missing entries are NaN.
     */
    private final Matrix matrix;

    /**
     * The cache, from sorted column sets to immutable row lists. Concurrent because searches (and the parallel
     * covariance calculation in SemBicScore) query from multiple threads.
     */
    private final Map<ColumnKey, List<Integer>> cache = new ConcurrentHashMap<>();

    /**
     * Constructs an instance for the given dataset. Private; use {@link #forDataSet(DataSet)} so that caches are
     * shared across components analyzing the same dataset.
     *
     * @param dataSet The dataset.
     */
    private TestwiseRows(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException("Dataset is null.");
        }

        this.dataSet = dataSet;
        this.matrix = null;
    }

    /**
     * Constructs an instance for the given raw data matrix, in which missing entries are NaN. Private; use
     * {@link #forMatrix(Matrix)}.
     *
     * @param matrix The matrix.
     */
    private TestwiseRows(Matrix matrix) {
        if (matrix == null) {
            throw new NullPointerException("Matrix is null.");
        }

        this.dataSet = null;
        this.matrix = matrix;
    }

    /**
     * Returns the (shared, cached) instance for the given dataset, creating it if necessary.
     *
     * @param dataSet The dataset.
     * @return This instance.
     */
    public static TestwiseRows forDataSet(DataSet dataSet) {
        return REGISTRY.computeIfAbsent(dataSet, TestwiseRows::new);
    }

    /**
     * Returns the (shared, cached) instance for the given raw data matrix, in which missing entries are NaN,
     * creating it if necessary. Keyed on matrix identity.
     *
     * @param matrix The matrix.
     * @return This instance.
     */
    public static TestwiseRows forMatrix(Matrix matrix) {
        return MATRIX_REGISTRY.computeIfAbsent(matrix, TestwiseRows::new);
    }

    /**
     * Discards any cached instance for the given dataset. Call this if a dataset has been mutated in place after
     * analysis began.
     *
     * @param dataSet The dataset.
     */
    public static void invalidate(DataSet dataSet) {
        TestwiseRows testwiseRows = TestwiseRows.REGISTRY.remove(dataSet);

        if (testwiseRows != null) {
            testwiseRows.cache.clear();
        }
    }

    /**
     * The rows on which every one of the given columns is observed, in increasing order, as an immutable list. When
     * {@code candidateRows} is null or spans the whole dataset, the result is cached per column set; when a proper
     * subset of rows is supplied (e.g., by a subsampling search via RowsSettable), the result is computed directly
     * without caching, since such subsets change from call to call.
     *
     * @param columns       The column indices; need not be sorted or distinct.
     * @param candidateRows The rows to restrict attention to, or null for all rows.
     * @return This list.
     */
    public List<Integer> validRows(int[] columns, List<Integer> candidateRows) {
        boolean allRows = candidateRows == null || candidateRows.size() == numRows();

        if (!allRows) {
            return computeValidRows(columns, candidateRows);
        }

        return this.cache.computeIfAbsent(new ColumnKey(columns), key -> computeValidRows(columns, null));
    }

    /**
     * The rows on which every one of the given columns is observed, over all rows of the dataset, cached.
     *
     * @param columns The column indices; need not be sorted or distinct.
     * @return This list, immutable and in increasing order.
     */
    public List<Integer> validRows(int[] columns) {
        return validRows(columns, null);
    }

    /**
     * Clears this dataset's cache. Call this if the dataset has been mutated in place after analysis began.
     */
    public void clearCache() {
        this.cache.clear();
    }

    /**
     * The number of column sets currently cached. Exposed for testing.
     *
     * @return This count.
     */
    public int cacheSize() {
        return this.cache.size();
    }

    /**
     * Computes the valid rows for the given columns over the given candidate rows (all rows if null).
     *
     * @param columns       The column indices.
     * @param candidateRows The candidate rows, or null for all rows.
     * @return An immutable list of the valid rows, in the order of the candidate rows.
     */
    private List<Integer> computeValidRows(int[] columns, List<Integer> candidateRows) {
        List<Integer> rows = new ArrayList<>();

        if (candidateRows == null) {
            int n = numRows();

            K:
            for (int k = 0; k < n; k++) {
                for (int c : columns) {
                    if (isMissing(k, c)) continue K;
                }

                rows.add(k);
            }
        } else {
            K:
            for (int k : candidateRows) {
                for (int c : columns) {
                    if (isMissing(k, c)) continue K;
                }

                rows.add(k);
            }
        }

        return Collections.unmodifiableList(rows);
    }

    /**
     * The number of rows of the wrapped dataset or matrix.
     *
     * @return This count.
     */
    private int numRows() {
        return this.dataSet != null ? this.dataSet.getNumRows() : this.matrix.getNumRows();
    }

    /**
     * Whether the given cell is missing, per the wrapped representation: the Tetrad conventions for a dataset, or
     * NaN for a raw matrix.
     *
     * @param row    The row index.
     * @param column The column index.
     * @return True if missing.
     */
    private boolean isMissing(int row, int column) {
        if (this.dataSet != null) {
            return MissingDataAudit.isMissing(this.dataSet, row, column);
        } else {
            return Double.isNaN(this.matrix.get(row, column));
        }
    }

    /**
     * A cache key: the sorted, deduplicated column indices.
     */
    private static final class ColumnKey {

        /**
         * The sorted, deduplicated columns.
         */
        private final int[] sortedColumns;

        /**
         * The precomputed hash.
         */
        private final int hash;

        /**
         * Constructs a key from the given columns.
         *
         * @param columns The columns; need not be sorted or distinct.
         */
        private ColumnKey(int[] columns) {
            int[] sorted = columns.clone();
            Arrays.sort(sorted);

            int distinct = 0;

            for (int i = 0; i < sorted.length; i++) {
                if (i == 0 || sorted[i] != sorted[i - 1]) {
                    sorted[distinct++] = sorted[i];
                }
            }

            this.sortedColumns = Arrays.copyOf(sorted, distinct);
            this.hash = Arrays.hashCode(this.sortedColumns);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ColumnKey other)) return false;
            return this.hash == other.hash && Arrays.equals(this.sortedColumns, other.sortedColumns);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }
}

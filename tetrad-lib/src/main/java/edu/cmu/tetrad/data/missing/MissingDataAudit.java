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
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.EmCovarianceEstimator;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A descriptive audit of the missingness in a dataset, intended to be computed (and logged) before a search is run on
 * real data, and to give the user the evidence needed to choose a {@link MissingDataPolicy}. The audit reports
 * per-variable missing counts and rates, the number of complete rows, the distinct missingness patterns and their
 * frequencies, and the matrix of pairwise complete counts (the number of rows on which each pair of variables is
 * jointly observed), whose minimum is a useful proxy for how much information test-wise deletion actually has
 * available for the worst pair.
 * <p>
 * For continuous datasets, {@link #littlesMcarTest()} computes Little's (1988) chi-square test of the MCAR
 * hypothesis, using EM estimates of the mean and covariance under the saturated Gaussian model. A small p-value is
 * evidence against MCAR, in which case deletion-based policies ({@link MissingDataPolicy#LISTWISE},
 * {@link MissingDataPolicy#TESTWISE}) are suspect and a MAR-based policy
 * ({@link MissingDataPolicy#EM_COVARIANCE}, {@link MissingDataPolicy#MULTIPLE_IMPUTATION}) should be preferred. A
 * large p-value is consistent with MCAR but does not prove it.
 * <p>
 * Missing values are {@link Double#NaN} for continuous variables and {@link DiscreteVariable#MISSING_VALUE} for
 * discrete variables, per Tetrad convention.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see MissingDataPolicy
 * @see EmCovarianceEstimator
 */
public final class MissingDataAudit {

    /**
     * The per-variable missing rate at or above which advice() flags a variable as having high missingness.
     */
    public static final double HIGH_MISSING_RATE = 0.20;

    /**
     * The alpha level advice() uses to interpret Little's MCAR test.
     */
    public static final double MCAR_TEST_ALPHA = 0.05;

    /**
     * The pairwise complete count below which advice() warns that test-wise statistics may be unstable.
     */
    public static final int SMALL_PAIRWISE_COUNT = 30;

    /**
     * The dataset being audited.
     */
    private final DataSet dataSet;

    /**
     * The number of rows in the dataset.
     */
    private final int numRows;

    /**
     * The number of columns in the dataset.
     */
    private final int numColumns;

    /**
     * The number of missing entries for each variable, in dataset variable order.
     */
    private final int[] missingCounts;

    /**
     * For each pair of columns (i, j), the number of rows on which both are observed.
     */
    private final int[][] pairwiseCompleteCounts;

    /**
     * The number of rows with no missing entries.
     */
    private final int numCompleteRows;

    /**
     * The total number of missing entries.
     */
    private final int totalMissing;

    /**
     * The distinct missingness patterns and their row counts. Each key is a string of '1' (observed) and '0'
     * (missing) characters, one per column, in dataset variable order; iteration order is by first appearance.
     */
    private final Map<String, Integer> patternCounts;

    /**
     * Cached result of Little's test, or null if not yet computed.
     */
    private LittleResult littleResult;

    /**
     * Constructs the audit for the given dataset, computing all descriptive statistics. This is a single O(n * p^2)
     * pass over the data (for the pairwise counts).
     *
     * @param dataSet The dataset. May be continuous, discrete, or mixed.
     */
    public MissingDataAudit(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException("Dataset is null.");
        }

        this.dataSet = dataSet;
        this.numRows = dataSet.getNumRows();
        this.numColumns = dataSet.getNumColumns();
        this.missingCounts = new int[this.numColumns];
        this.pairwiseCompleteCounts = new int[this.numColumns][this.numColumns];
        this.patternCounts = new LinkedHashMap<>();

        int completeRows = 0;
        int totalMissing = 0;
        boolean[] observed = new boolean[this.numColumns];
        StringBuilder key = new StringBuilder(this.numColumns);

        for (int i = 0; i < this.numRows; i++) {
            key.setLength(0);
            boolean rowComplete = true;

            for (int j = 0; j < this.numColumns; j++) {
                observed[j] = !isMissing(this.dataSet, i, j);

                if (observed[j]) {
                    key.append('1');
                } else {
                    key.append('0');
                    this.missingCounts[j]++;
                    totalMissing++;
                    rowComplete = false;
                }
            }

            if (rowComplete) completeRows++;
            this.patternCounts.merge(key.toString(), 1, Integer::sum);

            for (int j = 0; j < this.numColumns; j++) {
                if (!observed[j]) continue;

                for (int k = j; k < this.numColumns; k++) {
                    if (observed[k]) {
                        this.pairwiseCompleteCounts[j][k]++;
                        this.pairwiseCompleteCounts[k][j] = this.pairwiseCompleteCounts[j][k];
                    }
                }
            }
        }

        this.numCompleteRows = completeRows;
        this.totalMissing = totalMissing;
    }

    /**
     * Returns true just in case the given cell of the dataset is missing, using Tetrad's conventions:
     * {@link DiscreteVariable#MISSING_VALUE} for discrete variables and {@link Double#NaN} otherwise.
     *
     * @param dataSet The dataset.
     * @param row     The row index.
     * @param column  The column index.
     * @return True if the cell is missing.
     */
    public static boolean isMissing(DataSet dataSet, int row, int column) {
        // getVariable(int) rather than getVariables().get(int): the latter copies the whole variable list into a
        // LinkedList and then walks it, making this per-cell test O(p) with an allocation. On a 148-column dataset
        // that turned the audits' O(p^2 n) pairwise scans into O(p^3 n) and took minutes.
        Node variable = dataSet.getVariable(column);

        if (variable instanceof DiscreteVariable) {
            return dataSet.getInt(row, column) == DiscreteVariable.MISSING_VALUE;
        } else {
            return Double.isNaN(dataSet.getDouble(row, column));
        }
    }

    /**
     * True just in case the dataset contains at least one missing entry.
     *
     * @return True if any entry is missing.
     */
    public boolean anyMissing() {
        return this.totalMissing > 0;
    }

    /**
     * The number of missing entries for the given column.
     *
     * @param column The column index.
     * @return This count.
     */
    public int getMissingCount(int column) {
        return this.missingCounts[column];
    }

    /**
     * The fraction of entries missing for the given column.
     *
     * @param column The column index.
     * @return This rate, in [0, 1].
     */
    public double getMissingRate(int column) {
        return this.numRows == 0 ? 0.0 : this.missingCounts[column] / (double) this.numRows;
    }

    /**
     * The fraction of all entries that are missing.
     *
     * @return This rate, in [0, 1].
     */
    public double getOverallMissingRate() {
        long cells = (long) this.numRows * this.numColumns;
        return cells == 0 ? 0.0 : this.totalMissing / (double) cells;
    }

    /**
     * The number of rows with no missing entries--i.e., the sample size that listwise deletion would retain.
     *
     * @return This count.
     */
    public int getNumCompleteRows() {
        return this.numCompleteRows;
    }

    /**
     * The number of distinct missingness patterns among the rows.
     *
     * @return This count.
     */
    public int getNumPatterns() {
        return this.patternCounts.size();
    }

    /**
     * The distinct missingness patterns and their row counts. Each key is a string of '1' (observed) and '0'
     * (missing) characters, one per column, in dataset variable order.
     *
     * @return An unmodifiable view of this map, in order of first appearance.
     */
    public Map<String, Integer> getPatternCounts() {
        return java.util.Collections.unmodifiableMap(this.patternCounts);
    }

    /**
     * The matrix of pairwise complete counts: entry (i, j) is the number of rows on which columns i and j are both
     * observed. The diagonal gives per-variable observed counts.
     *
     * @return A defensive copy of this matrix.
     */
    public int[][] getPairwiseCompleteCounts() {
        int[][] copy = new int[this.numColumns][];
        for (int j = 0; j < this.numColumns; j++) copy[j] = this.pairwiseCompleteCounts[j].clone();
        return copy;
    }

    /**
     * The minimum, over pairs of distinct columns, of the pairwise complete count. This is the sample size available
     * to test-wise deletion for the worst pair, and a candidate conservative effective sample size (see
     * {@link MissingDataSpec.EffectiveSampleSizeMode#MIN_PAIRWISE}).
     *
     * @return This minimum, or the number of rows if there are fewer than two columns.
     */
    public int getMinPairwiseCount() {
        int min = this.numRows;

        for (int j = 0; j < this.numColumns; j++) {
            for (int k = j + 1; k < this.numColumns; k++) {
                if (this.pairwiseCompleteCounts[j][k] < min) min = this.pairwiseCompleteCounts[j][k];
            }
        }

        return min;
    }

    /**
     * The mean, over pairs of distinct columns, of the pairwise complete count (see
     * {@link MissingDataSpec.EffectiveSampleSizeMode#MEAN_PAIRWISE}).
     *
     * @return This mean, or the number of rows if there are fewer than two columns.
     */
    public double getMeanPairwiseCount() {
        long sum = 0;
        int count = 0;

        for (int j = 0; j < this.numColumns; j++) {
            for (int k = j + 1; k < this.numColumns; k++) {
                sum += this.pairwiseCompleteCounts[j][k];
                count++;
            }
        }

        return count == 0 ? this.numRows : sum / (double) count;
    }

    /**
     * Computes Little's (1988) chi-square test of the MCAR hypothesis for a continuous dataset, using EM estimates
     * of the mean and covariance under the saturated Gaussian model. Rows with no observed values are excluded.
     * Patterns whose observed-variable covariance submatrix is singular are skipped (their degrees of freedom are
     * not counted). The result is cached.
     * <p>
     * Reference: Little, R. J. A. (1988). A test of missing completely at random for multivariate data with missing
     * values. Journal of the American Statistical Association, 83(404), 1198-1202.
     *
     * @return The test result.
     * @throws IllegalArgumentException If the dataset is not continuous.
     * @throws IllegalStateException    If the dataset has no missing values (the test is vacuous).
     */
    public LittleResult littlesMcarTest() {
        if (this.littleResult != null) {
            return this.littleResult;
        }

        if (!this.dataSet.isContinuous()) {
            throw new IllegalArgumentException("Little's MCAR test requires a continuous dataset.");
        }

        if (!anyMissing()) {
            throw new IllegalStateException("The dataset has no missing values; Little's MCAR test is vacuous.");
        }

        EmCovarianceEstimator estimator = new EmCovarianceEstimator(this.dataSet);
        ICovarianceMatrix cov = estimator.estimate();
        double[] mu = estimator.getMeans();

        int p = this.numColumns;
        double[][] sigma = new double[p][p];

        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                sigma[i][j] = cov.getValue(i, j);
            }
        }

        // Group rows by pattern; accumulate observed-variable sums per pattern.
        Map<String, double[]> sums = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (int i = 0; i < this.numRows; i++) {
            StringBuilder key = new StringBuilder(p);
            boolean anyObserved = false;

            for (int j = 0; j < p; j++) {
                boolean obs = !Double.isNaN(this.dataSet.getDouble(i, j));
                key.append(obs ? '1' : '0');
                if (obs) anyObserved = true;
            }

            if (!anyObserved) continue;

            String k = key.toString();
            double[] sum = sums.computeIfAbsent(k, s -> new double[p]);

            for (int j = 0; j < p; j++) {
                if (k.charAt(j) == '1') sum[j] += this.dataSet.getDouble(i, j);
            }

            counts.merge(k, 1, Integer::sum);
        }

        double d2 = 0.0;
        int sumObservedVars = 0;
        int numPatternsUsed = 0;
        int numPatternsSkipped = 0;

        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String pattern = e.getKey();
            int nP = e.getValue();

            List<Integer> obsIdx = new ArrayList<>();
            for (int j = 0; j < p; j++) {
                if (pattern.charAt(j) == '1') obsIdx.add(j);
            }

            int q = obsIdx.size();
            double[] diff = new double[q];
            double[] sum = sums.get(pattern);

            for (int a = 0; a < q; a++) {
                int j = obsIdx.get(a);
                diff[a] = sum[j] / nP - mu[j];
            }

            double[][] sigmaOO = new double[q][q];

            for (int a = 0; a < q; a++) {
                for (int b = 0; b < q; b++) {
                    sigmaOO[a][b] = sigma[obsIdx.get(a)][obsIdx.get(b)];
                }
            }

            try {
                RealMatrix inv = new LUDecomposition(new Array2DRowRealMatrix(sigmaOO, false))
                        .getSolver().getInverse();

                double quad = 0.0;

                for (int a = 0; a < q; a++) {
                    for (int b = 0; b < q; b++) {
                        quad += diff[a] * inv.getEntry(a, b) * diff[b];
                    }
                }

                d2 += nP * quad;
                sumObservedVars += q;
                numPatternsUsed++;
            } catch (SingularMatrixException ex) {
                numPatternsSkipped++;
            }
        }

        int df = sumObservedVars - p;
        double pValue;

        if (df <= 0 || numPatternsUsed < 2) {
            pValue = Double.NaN;
        } else {
            pValue = 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(d2);
        }

        this.littleResult = new LittleResult(d2, df, pValue, numPatternsUsed, numPatternsSkipped);
        return this.littleResult;
    }

    /**
     * Rule-based advice for handling the missingness in this dataset, as a list of short paragraphs, in decreasing
     * order of importance. This is shared by the GUI's Data Audit dialog, causal-cmd, and py-tetrad so that all three
     * interfaces give the same recommendations. The rules are deliberately conservative: no test can confirm MCAR or
     * rule out MNAR, so the advice recommends policies rather than asserting assumptions.
     *
     * @return The advice, one recommendation per entry; never empty.
     */
    public List<String> advice() {
        List<String> out = new ArrayList<>();

        NumberFormat pct = NumberFormat.getPercentInstance();
        pct.setMaximumFractionDigits(1);

        if (!anyMissing()) {
            out.add("No missing values were found. All missing-data policies behave identically on complete "
                    + "data, so no policy choice is needed.");
            return out;
        }

        // Listwise loss.
        double retained = this.numCompleteRows / (double) this.numRows;
        out.add("Listwise deletion would retain " + this.numCompleteRows + " of " + this.numRows + " rows ("
                + pct.format(retained) + ").");

        // Variables with high missingness.
        List<Node> variables = this.dataSet.getVariables();
        StringBuilder high = new StringBuilder();

        for (int j = 0; j < this.numColumns; j++) {
            if (getMissingRate(j) >= HIGH_MISSING_RATE) {
                if (high.length() > 0) high.append(", ");
                high.append(variables.get(j).getName()).append(" (").append(pct.format(getMissingRate(j)))
                        .append(")");
            }
        }

        if (high.length() > 0) {
            out.add("High missingness (>= " + pct.format(HIGH_MISSING_RATE) + ") for: " + high
                    + ". Consider whether these variables belong in the analysis; if they do, "
                    + "MULTIPLE_IMPUTATION uses their observed values most efficiently.");
        }

        // Unstable pairwise counts.
        int minPairwise = getMinPairwiseCount();

        if (minPairwise < SMALL_PAIRWISE_COUNT) {
            out.add("The smallest pairwise complete count is " + minPairwise
                    + ". Test-wise statistics for the affected variable pair(s) will rest on very few rows "
                    + "and may be unstable.");
        }

        // MCAR evidence and policy recommendation.
        if (this.dataSet.isContinuous()) {
            try {
                LittleResult r = littlesMcarTest();

                if (Double.isNaN(r.pValue)) {
                    out.add("Little's MCAR test could not produce a p-value (df = " + r.df + ", patterns used = "
                            + r.numPatternsUsed + "). With MCAR undetermined, EM_COVARIANCE (for approximately "
                            + "Gaussian data) or MULTIPLE_IMPUTATION are the safer choices.");
                } else if (r.pValue < MCAR_TEST_ALPHA) {
                    out.add("Little's MCAR test rejects the MCAR hypothesis (chi-square = "
                            + String.format("%.2f", r.chiSquare) + ", df = " + r.df + ", p = "
                            + String.format("%.4f", r.pValue) + "). TESTWISE and LISTWISE deletion may be "
                            + "biased. Prefer EM_COVARIANCE (valid under MAR for approximately Gaussian data) "
                            + "or MULTIPLE_IMPUTATION.");
                } else {
                    out.add("Little's MCAR test does not reject the MCAR hypothesis (chi-square = "
                            + String.format("%.2f", r.chiSquare) + ", df = " + r.df + ", p = "
                            + String.format("%.4f", r.pValue) + "). TESTWISE deletion (the default) is "
                            + "reasonable; LISTWISE is also unbiased if the retained sample is large enough, "
                            + "and EM_COVARIANCE will typically use the data more efficiently.");
                }
            } catch (Exception e) {
                out.add("Little's MCAR test could not be computed (" + e.getMessage() + "). With MCAR "
                        + "undetermined, EM_COVARIANCE or MULTIPLE_IMPUTATION are the safer choices.");
            }
        } else {
            out.add("Little's MCAR test applies only to continuous datasets, so no MCAR evidence is available "
                    + "for this (discrete or mixed) dataset. If missingness may depend on observed values, "
                    + "prefer MULTIPLE_IMPUTATION, which supports discrete and mixed data.");
        }

        out.add("Caveat: statistical tests can reject MCAR but can never confirm it, and no test can rule out "
                + "MNAR (missingness depending on the missing values themselves). Use background knowledge "
                + "about why values are missing when choosing a policy.");

        return out;
    }

    /**
     * A human-readable, multi-line summary of the audit, suitable for logging via TetradLogger before a search is
     * run. Includes Little's MCAR test for continuous datasets with missing values.
     *
     * @return This summary.
     */
    public String report() {
        NumberFormat pct = NumberFormat.getPercentInstance();
        pct.setMaximumFractionDigits(1);

        StringBuilder b = new StringBuilder();
        b.append("Missing data audit: ").append(this.numRows).append(" rows x ")
                .append(this.numColumns).append(" columns").append('\n');
        b.append("  Overall missing rate: ").append(pct.format(getOverallMissingRate())).append('\n');
        b.append("  Complete rows (listwise n): ").append(this.numCompleteRows).append('\n');
        b.append("  Distinct missingness patterns: ").append(getNumPatterns()).append('\n');
        b.append("  Min pairwise complete count: ").append(getMinPairwiseCount()).append('\n');
        b.append("  Mean pairwise complete count: ")
                .append(String.format("%.1f", getMeanPairwiseCount())).append('\n');

        List<Node> variables = this.dataSet.getVariables();
        b.append("  Per-variable missing rates:").append('\n');

        for (int j = 0; j < this.numColumns; j++) {
            if (this.missingCounts[j] > 0) {
                b.append("    ").append(variables.get(j).getName()).append(": ")
                        .append(pct.format(getMissingRate(j)))
                        .append(" (").append(this.missingCounts[j]).append(")").append('\n');
            }
        }

        if (anyMissing() && this.dataSet.isContinuous()) {
            try {
                LittleResult r = littlesMcarTest();
                b.append("  Little's MCAR test: chi-square = ").append(String.format("%.2f", r.chiSquare))
                        .append(", df = ").append(r.df)
                        .append(", p = ").append(String.format("%.4f", r.pValue)).append('\n');
                b.append("    (Small p is evidence against MCAR; prefer EM_COVARIANCE or MULTIPLE_IMPUTATION.)")
                        .append('\n');
            } catch (Exception e) {
                b.append("  Little's MCAR test: could not be computed (").append(e.getMessage()).append(")")
                        .append('\n');
            }
        }

        return b.toString();
    }

    /**
     * A string representation of the audit.
     *
     * @return This string.
     */
    @Override
    public String toString() {
        return report();
    }

    /**
     * The result of Little's MCAR test: the chi-square statistic, its degrees of freedom, the p-value, and pattern
     * accounting.
     */
    public static final class LittleResult {

        /**
         * The chi-square statistic, d^2.
         */
        public final double chiSquare;

        /**
         * The degrees of freedom: the sum over patterns of the number of observed variables, minus the number of
         * variables.
         */
        public final int df;

        /**
         * The p-value, or NaN if the degrees of freedom were nonpositive or fewer than two usable patterns were
         * found.
         */
        public final double pValue;

        /**
         * The number of missingness patterns that contributed to the statistic.
         */
        public final int numPatternsUsed;

        /**
         * The number of patterns skipped because their observed-variable covariance submatrix was singular.
         */
        public final int numPatternsSkipped;

        /**
         * Constructs a result.
         *
         * @param chiSquare          The chi-square statistic.
         * @param df                 The degrees of freedom.
         * @param pValue             The p-value.
         * @param numPatternsUsed    The number of patterns used.
         * @param numPatternsSkipped The number of patterns skipped.
         */
        private LittleResult(double chiSquare, int df, double pValue, int numPatternsUsed, int numPatternsSkipped) {
            this.chiSquare = chiSquare;
            this.df = df;
            this.pValue = pValue;
            this.numPatternsUsed = numPatternsUsed;
            this.numPatternsSkipped = numPatternsSkipped;
        }
    }
}

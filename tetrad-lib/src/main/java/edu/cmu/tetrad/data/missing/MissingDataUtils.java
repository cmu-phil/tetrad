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
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.NumberFormat;
import java.util.List;

/**
 * Small shared utilities for the missing-data policies: listwise deletion, resolution of a possibly-null
 * {@link MissingDataSpec} to a policy (with a logged warning where the historical behavior was a silent switch), a
 * cheap missingness summary suitable for logging from constructors, and the effective-sample-size calculation for
 * analyses run on covariance matrices estimated from incomplete data.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class MissingDataUtils {

    /**
     * Private constructor to prevent instantiation.
     */
    private MissingDataUtils() {
    }

    /**
     * Builds a MissingDataSpec from algcomparison/GUI/py-tetrad parameters (see the MISSING_* constants in
     * {@link Params}), or returns null--meaning "legacy default behavior"--if the policy parameter is absent or
     * "default". Policy values (case-insensitive): "fail", "listwise", "testwise", "em" (or "emCovariance"), "mi"
     * (or "multipleImputation"). ESS modes: "fullN", "minPairwise", "meanPairwise".
     *
     * @param parameters The parameters.
     * @return The spec, or null for the legacy default.
     * @throws IllegalArgumentException If the policy or ESS mode string is unrecognized.
     */
    public static MissingDataSpec fromParameters(Parameters parameters) {
        String policy = parameters.getString(Params.MISSING_DATA_POLICY, "default").trim();

        if (policy.isEmpty() || policy.equalsIgnoreCase("default")) {
            return null;
        }

        MissingDataSpec spec = switch (policy.toLowerCase()) {
            case "fail" -> MissingDataSpec.fail();
            case "listwise" -> MissingDataSpec.listwise();
            case "testwise" -> MissingDataSpec.testwise();
            case "em", "emcovariance", "em_covariance" -> MissingDataSpec.emCovariance();
            case "mi", "multipleimputation", "multiple_imputation" -> MissingDataSpec.multipleImputation(
                    parameters.getInt(Params.MISSING_NUM_IMPUTATIONS, 10));
            default -> throw new IllegalArgumentException("Unrecognized missing-data policy: '" + policy
                    + "'. Expected one of: default, fail, listwise, testwise, em, mi.");
        };

        spec = spec.withEmRidge(parameters.getDouble(Params.MISSING_EM_RIDGE, spec.getEmRidge()))
                .withEmTolerance(parameters.getDouble(Params.MISSING_EM_TOLERANCE, spec.getEmTolerance()))
                .withEmMaxIterations(parameters.getInt(Params.MISSING_EM_MAX_ITERATIONS, spec.getEmMaxIterations()));

        String essMode = parameters.getString(Params.MISSING_ESS_MODE, "fullN").trim();

        spec = switch (essMode.toLowerCase()) {
            case "", "fulln", "full_n" -> spec.withEssMode(MissingDataSpec.EffectiveSampleSizeMode.FULL_N);
            case "minpairwise", "min_pairwise" -> spec.withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MIN_PAIRWISE);
            case "meanpairwise", "mean_pairwise" -> spec.withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MEAN_PAIRWISE);
            default -> throw new IllegalArgumentException("Unrecognized missing-data ESS mode: '" + essMode
                    + "'. Expected one of: fullN, minPairwise, meanPairwise.");
        };

        return spec;
    }

    /**
     * Returns a new dataset consisting of the rows of the given dataset that have no missing entries (listwise
     * deletion). The given dataset is not modified.
     *
     * @param dataSet The dataset.
     * @return The complete-case dataset.
     * @throws IllegalArgumentException If no complete rows remain.
     */
    public static DataSet listwiseDelete(DataSet dataSet) {
        int n = dataSet.getNumRows();
        int p = dataSet.getNumColumns();
        List<Integer> keep = new java.util.ArrayList<>();

        K:
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                if (MissingDataAudit.isMissing(dataSet, i, j)) continue K;
            }

            keep.add(i);
        }

        if (keep.isEmpty()) {
            throw new IllegalArgumentException("Listwise deletion removed every row; no complete cases exist.");
        }

        int[] rows = new int[keep.size()];
        for (int i = 0; i < rows.length; i++) rows[i] = keep.get(i);

        return dataSet.subsetRows(rows);
    }

    /**
     * Resolves a possibly-null spec to a policy for a component that historically switched silently to test-wise
     * deletion on missing data. If the spec is non-null, its policy is returned. If the spec is null and the dataset
     * has missing values, {@link MissingDataPolicy#TESTWISE} is returned for backward compatibility, but a warning
     * naming the caller, the policy, and its MCAR assumption--together with a brief missingness summary--is logged
     * through {@link TetradLogger}, replacing the silent switch. If the dataset is complete, the policy is
     * irrelevant and TESTWISE is returned.
     *
     * @param dataSet The dataset.
     * @param spec    The spec, or null if the user did not provide one.
     * @param caller  The name of the calling component, for the log message.
     * @return The resolved policy.
     */
    public static MissingDataPolicy resolveOrWarn(DataSet dataSet, MissingDataSpec spec, String caller) {
        if (spec != null) {
            return spec.getPolicy();
        }

        if (dataSet.existsMissingValue()) {
            TetradLogger.getInstance().log(caller + ": The dataset contains missing values and no missing-data "
                    + "policy was specified; defaulting to TESTWISE deletion, which is unbiased only if values are "
                    + "missing completely at random (MCAR). Consider supplying a MissingDataSpec (e.g., "
                    + "EM_COVARIANCE for MAR continuous data). " + briefSummary(dataSet));
        }

        return MissingDataPolicy.TESTWISE;
    }

    /**
     * A one-line missingness summary cheap enough to compute in a constructor: one O(n * p) pass giving the overall
     * missing rate, the number of complete rows, and the worst variable. (The full {@link MissingDataAudit}, which
     * additionally computes pairwise complete counts in O(n * p^2), is opt-in.)
     *
     * @param dataSet The dataset.
     * @return This summary.
     */
    public static String briefSummary(DataSet dataSet) {
        int n = dataSet.getNumRows();
        int p = dataSet.getNumColumns();
        long totalMissing = 0;
        int completeRows = 0;
        int worstColumn = -1;
        int worstCount = 0;

        int[] counts = new int[p];

        for (int i = 0; i < n; i++) {
            boolean complete = true;

            for (int j = 0; j < p; j++) {
                if (MissingDataAudit.isMissing(dataSet, i, j)) {
                    counts[j]++;
                    totalMissing++;
                    complete = false;
                }
            }

            if (complete) completeRows++;
        }

        for (int j = 0; j < p; j++) {
            if (counts[j] > worstCount) {
                worstCount = counts[j];
                worstColumn = j;
            }
        }

        NumberFormat pct = NumberFormat.getPercentInstance();
        pct.setMaximumFractionDigits(1);

        StringBuilder b = new StringBuilder();
        b.append("Missing: ").append(pct.format(totalMissing / (double) ((long) n * p)))
                .append(" of entries; ").append(completeRows).append("/").append(n).append(" complete rows");

        if (worstColumn >= 0 && worstCount > 0) {
            b.append("; worst variable ").append(dataSet.getVariables().get(worstColumn).getName())
                    .append(" (").append(pct.format(worstCount / (double) n)).append(" missing)");
        }

        b.append(".");
        return b.toString();
    }

    /**
     * The effective sample size to use for penalized scores when the analysis runs on a covariance matrix estimated
     * from incomplete data, per the spec's {@link MissingDataSpec.EffectiveSampleSizeMode}. For {@code FULL_N} this
     * returns -1, the existing convention meaning "use the nominal sample size"; the pairwise modes compute the
     * pairwise complete counts (O(n * p^2)) via {@link MissingDataAudit}.
     *
     * @param dataSet The dataset the covariance matrix was estimated from.
     * @param spec    The spec.
     * @return The effective sample size, or -1 for the nominal sample size.
     */
    public static int effectiveSampleSize(DataSet dataSet, MissingDataSpec spec) {
        return switch (spec.getEssMode()) {
            case FULL_N -> -1;
            case MIN_PAIRWISE -> new MissingDataAudit(dataSet).getMinPairwiseCount();
            case MEAN_PAIRWISE -> (int) Math.round(new MissingDataAudit(dataSet).getMeanPairwiseCount());
        };
    }
}

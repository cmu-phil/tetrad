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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.blocks.BlockSpec;
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

    /**
     * The wrapper-level missing-data gate, called at the top of every algcomparison test and score wrapper's
     * {@code getTest}/{@code getScore} method. This is the point at which the explicit-choice contract for missing
     * data is enforced for all user-facing interfaces (the GUI search box, algcomparison, and py-tetrad, all of
     * which construct tests and scores through these wrappers).
     * <p>
     * Semantics: if the data model is null, is not a tabular {@link DataSet} (e.g., a covariance matrix, which
     * cannot contain missing values), or is a complete dataset, it is returned unchanged and the policy is
     * irrelevant. Otherwise the dataset contains missing values, and:
     * <ul>
     * <li>Policy "default" (or unset): an {@link IllegalArgumentException} is thrown asking the user to choose a
     * policy. This replaces the previous behavior in which components silently (later: with a logged warning)
     * switched to test-wise deletion. To restore the previous behavior explicitly, set the policy to
     * "testwise".</li>
     * <li>"fail": an exception is thrown, per that policy's contract.</li>
     * <li>"listwise": the complete-case dataset is returned. This is supported for every test and score, since it
     * is a pure data transformation performed here, upstream of the component.</li>
     * <li>"testwise" or "em": the dataset is passed through unchanged if {@code specAware} is true (the wrapper
     * passes a {@link MissingDataSpec} to a component that implements these policies natively); otherwise an
     * exception is thrown naming "listwise" as the supported alternative.</li>
     * <li>"mi": an exception is thrown. Multiple imputation runs the whole search over m imputed datasets via
     * {@link ImputationSearch} and is not the responsibility of a single test or score; it is not yet wired into
     * the wrapper interfaces. (ImputationSearch hands complete datasets to the algorithm it runs, so this gate
     * passes through in that context.)</li>
     * </ul>
     * Statistical caveats as elsewhere: test-wise and listwise deletion are unbiased in general only under MCAR;
     * EM is valid under MAR for approximately multivariate normal data.
     *
     * @param dataModel  The data model handed to the wrapper; may be null or a non-tabular model.
     * @param parameters The parameters, from which {@link Params#MISSING_DATA_POLICY} is read.
     * @param specAware  True if the wrapper passes a MissingDataSpec through to a component with native TESTWISE /
     *                   EM_COVARIANCE support.
     * @param caller     The user-facing name of the test or score, for error messages.
     * @return The data model, possibly replaced by its complete-case version under the "listwise" policy.
     * @throws IllegalArgumentException As described above.
     */
    public static DataModel gate(DataModel dataModel, Parameters parameters, boolean specAware, String caller) {
        return gate(dataModel, parameters, specAware
                ? java.util.Set.of("testwise", "em")
                : java.util.Set.of(), caller);
    }

    /**
     * The finer-grained form of {@link #gate(DataModel, Parameters, boolean, String)}, for components that support
     * some native policies but not others (e.g., the Chi-Square test performs test-wise deletion natively but has
     * no EM-covariance route, while Fisher Z supports both). The boolean form delegates here with
     * {@code {"testwise", "em"}} or the empty set.
     *
     * @param dataModel      The data model handed to the wrapper; may be null or a non-tabular model.
     * @param parameters     The parameters, from which {@link Params#MISSING_DATA_POLICY} is read.
     * @param nativePolicies The policies the underlying component implements natively, from among "testwise" and
     *                       "em". ("fail" and "listwise" are implemented here for every component, and "mi" is a
     *                       search-level policy.)
     * @param caller         The user-facing name of the test or score, for error messages.
     * @return The data model, possibly replaced by its complete-case version under the "listwise" policy.
     * @throws IllegalArgumentException As for the boolean form.
     */
    public static DataModel gate(DataModel dataModel, Parameters parameters, java.util.Set<String> nativePolicies,
                                 String caller) {
        if (!(dataModel instanceof DataSet dataSet)) {
            return dataModel;
        }

        if (!dataSet.existsMissingValue()) {
            return dataModel;
        }

        String policy = parameters.getString(Params.MISSING_DATA_POLICY, "default").trim().toLowerCase();

        switch (policy) {
            case "", "default" -> throw new IllegalArgumentException(caller
                    + ": The dataset contains missing values, and no missing-data policy has been chosen.\n"
                    + "Please set the '" + Params.MISSING_DATA_POLICY + "' parameter for this test or score to one of:\n"
                    + "  'testwise'  - test-wise deletion (each local calculation uses its complete rows; the previous default),\n"
                    + "  'listwise'  - analyze complete cases only (supported by every test and score),\n"
                    + "  'em'        - EM-estimated covariance (continuous data; supported where noted),\n"
                    + "  'fail'      - refuse to analyze data with missing values.\n"
                    + "Test-wise and listwise deletion are unbiased only if values are missing completely at random "
                    + "(MCAR); EM is valid under the weaker MAR assumption for approximately multivariate normal "
                    + "data. " + briefSummary(dataSet));
            case "fail" -> throw new IllegalArgumentException(caller
                    + ": The dataset contains missing values, and the missing-data policy is 'fail'. "
                    + briefSummary(dataSet));
            case "listwise" -> {
                DataSet complete = listwiseDelete(dataSet);
                TetradLogger.getInstance().log(caller + ": Missing-data policy 'listwise': using "
                        + complete.getNumRows() + " complete rows of " + dataSet.getNumRows() + ". "
                        + briefSummary(dataSet));
                return complete;
            }
            case "testwise", "em", "emcovariance", "em_covariance" -> {
                String canonical = policy.startsWith("em") ? "em" : "testwise";

                if (nativePolicies.contains(canonical)) {
                    return dataModel;
                } else {
                    StringBuilder supported = new StringBuilder("'listwise' (analyze complete cases only) and 'fail'");
                    for (String p : new String[]{"testwise", "em"}) {
                        if (nativePolicies.contains(p)) supported.append(", and natively '").append(p).append("'");
                    }

                    throw new IllegalArgumentException(caller
                            + ": This test/score does not support the missing-data policy '" + canonical + "'. "
                            + "It supports " + supported + ". Either set "
                            + Params.MISSING_DATA_POLICY + " = 'listwise', or choose a test/score with native "
                            + "support for '" + canonical + "' (e.g., Fisher Z, SEM BIC, BDeu, Discrete BIC, "
                            + "Conditional Gaussian BIC, Degenerate Gaussian BIC).");
                }
            }
            case "mi", "multipleimputation", "multiple_imputation" -> throw new IllegalArgumentException(caller
                    + ": Multiple imputation ('mi') runs the entire search over m imputed datasets (see "
                    + "ImputationSearch) and is not performed by a single test or score; it is not yet available "
                    + "through this interface. Please use 'em', 'testwise', or 'listwise' instead.");
            default -> throw new IllegalArgumentException(caller + ": Unrecognized missing-data policy: '" + policy
                    + "'. Expected one of: default, fail, listwise, testwise, em, mi.");
        }
    }

    /**
     * The block-spec variant of {@link #gate(DataModel, Parameters, boolean, String)}, for wrappers that construct
     * block tests and scores from a {@link BlockSpec} rather than from the data model handed to
     * {@code getTest}/{@code getScore}. The embedded dataset is checked; under the "listwise" policy a new
     * BlockSpec is returned wrapping the complete-case dataset with the same blocks, block variables, and ranks
     * (blocks index columns, which row deletion does not disturb). No block component currently has native
     * test-wise or EM support, so those policies throw here.
     *
     * @param blockSpec  The block spec.
     * @param parameters The parameters.
     * @param caller     The user-facing name of the test or score, for error messages.
     * @return The block spec, possibly rebuilt on the complete-case dataset under the "listwise" policy.
     * @throws IllegalArgumentException As for the data-model gate.
     */
    public static BlockSpec gate(BlockSpec blockSpec, Parameters parameters, String caller) {
        if (blockSpec == null) {
            return null;
        }

        DataModel gated = gate(blockSpec.dataSet(), parameters, false, caller);

        if (gated == blockSpec.dataSet()) {
            return blockSpec;
        }

        return new BlockSpec((DataSet) gated, blockSpec.blocks(), blockSpec.blockVariables(), blockSpec.ranks());
    }

    /**
     * Implements the "em" missing-data policy at the wrapper level for covariance-consuming scores that have an
     * {@link edu.cmu.tetrad.data.ICovarianceMatrix} constructor but no native {@link MissingDataSpec} handling
     * (EBIC, GIC, Poisson prior, Zhang-Shen bound, and the like). Called after
     * {@link #gate(DataModel, Parameters, java.util.Set, String)} with "em" in the native set: if the data model is
     * a dataset with missing values and the chosen policy is "em", the EM-estimated covariance matrix (valid under
     * MAR for approximately multivariate normal data) is returned in its place, with its sample size adjusted per
     * the spec's effective-sample-size mode if a pairwise mode is chosen; the wrapper's covariance branch then
     * constructs the score from it. In every other case the data model is returned unchanged.
     *
     * @param dataModel  The data model, already passed through the gate.
     * @param parameters The parameters.
     * @param caller     The user-facing name of the score, for the log message.
     * @return The data model, or the EM-estimated covariance matrix in its place under the "em" policy.
     */
    public static DataModel emCovarianceIfRequested(DataModel dataModel, Parameters parameters, String caller) {
        if (!(dataModel instanceof DataSet dataSet)) {
            return dataModel;
        }

        if (!dataSet.existsMissingValue()) {
            return dataModel;
        }

        String policy = parameters.getString(Params.MISSING_DATA_POLICY, "default").trim().toLowerCase();

        if (!(policy.equals("em") || policy.equals("emcovariance") || policy.equals("em_covariance"))) {
            return dataModel;
        }

        MissingDataSpec spec = fromParameters(parameters);

        edu.cmu.tetrad.data.EmCovarianceEstimator estimator = new edu.cmu.tetrad.data.EmCovarianceEstimator(dataSet);
        estimator.setRidge(spec.getEmRidge());
        estimator.setTolerance(spec.getEmTolerance());
        estimator.setMaxIterations(spec.getEmMaxIterations());
        edu.cmu.tetrad.data.ICovarianceMatrix cov = estimator.estimate();

        int ess = effectiveSampleSize(dataSet, spec);
        if (ess >= 0) {
            cov.setSampleSize(ess);
        }

        TetradLogger.getInstance().log(caller + ": Missing-data policy 'em': using the EM-estimated covariance "
                + "matrix (MAR, approximately multivariate normal), sample size " + cov.getSampleSize() + ". "
                + briefSummary(dataSet));

        return cov;
    }
}

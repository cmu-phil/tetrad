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

package edu.cmu.tetrad.data.audit;

import edu.cmu.tetrad.data.AndersonDarlingTest;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.missing.MissingDataAudit;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A pre-search audit of a data matrix. On construction, computes a battery of descriptive checks bearing on the
 * choice and reliability of causal search procedures - variable typing and cardinalities, small discrete cells,
 * constant and near-constant columns, high correlation and near-determinism among variables, marginal
 * non-Gaussianity, serial
 * dependence of rows in file order, sample adequacy, and missingness (the last delegated to
 * {@link MissingDataAudit}) - and emits the results as a list of {@link AuditFinding}s keyed by {@link FindingCode}.
 * <p>
 * This class reports findings only; it makes no recommendations. The interpretation of findings is left to the user
 * and to documentation that dispatches on the finding codes. Raw statistics computed along the way (correlations,
 * Anderson-Darling p-values, R-squared and eta-squared values, distinct-value counts) are available from accessors so
 * that downstream tools can display or reason over the numbers, not just the flags.
 * <p>
 * Separately from the findings, {@link #notes()} returns cross-references to other diagnostics whose scope overlaps
 * a finding that fired, for cases where the finding on its own is ambiguous between explanations that a different
 * tool can distinguish. Notes are kept out of {@link AuditFinding} messages and out of the "findings" array of
 * {@link #toJson()} precisely so that the findings-only contract above stays literally true: a note says what else
 * could be measured, never what the user should do about the data.
 * <p>
 * Columns found to be exactly constant (see {@link FindingCode#CONSTANT_COLUMN}) are flagged and then excluded from
 * all subsequent checks in the same audit: constant columns contribute nothing to small-cell, correlation,
 * near-determinism, non-Gaussianity, or serial-dependence diagnostics, and a constant continuous column would
 * otherwise poison the pairwise-complete correlation matrix (its correlations are undefined), masking findings such
 * as NEAR_DETERMINISM_LINEAR among the remaining variables. Each CONSTANT_COLUMN finding states this exclusion
 * in its message. Consequently {@link #getContinuousNames()} and the correlation-based accessors cover only the
 * non-constant continuous variables, while whole-dataset summaries (distinct-value counts, the sample-size ratio,
 * and missingness statistics) continue to describe the dataset as given.
 * <p>
 * Missing values (NaN for continuous variables, the discrete missing-value marker for discrete variables, as judged
 * by {@link MissingDataAudit#isMissing(DataSet, int, int)}) are excluded pairwise or listwise as appropriate to each
 * statistic; each check's documentation notes which.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see FindingCode
 * @see AuditFinding
 * @see MissingDataAudit
 */
public final class DataAudit {

    /**
     * The note attached by {@link #notes()} when at least one {@link FindingCode#NON_GAUSSIAN} finding fired.
     * <p>
     * The Anderson-Darling check is a marginal one, and a non-Gaussian marginal has more than one explanation. The
     * error term may itself be non-Gaussian, with the variable a linear additive function of its parents, which is
     * the case the LiNGAM family exploits. Or the variable may be a nonlinear function of its parents, or a
     * non-additive one, with Gaussian errors throughout: pushing Gaussian parents through a nonlinearity, or through
     * an interaction, produces a non-Gaussian marginal by itself. Marginal non-Gaussianity does not separate these,
     * and the second case violates the functional form that the first case's methods assume, so the cross-reference
     * is to a check of the conditional mean and of additivity across parents rather than to another marginal test.
     */
    public static final String NON_GAUSSIAN_NOTE =
            "Non-Gaussianity was flagged for at least one continuous variable. The Anderson-Darling check is "
                    + "marginal, and a non-Gaussian marginal is consistent both with a non-Gaussian error term in a "
                    + "linear additive model (the case LiNGAM-family methods exploit) and with a nonlinear or "
                    + "non-additive dependence on parents with Gaussian errors, which violates the functional form "
                    + "those methods assume. The nonlinearity checks tool (Tools > Nonlinearity Checks... in the "
                    + "Tetrad GUI) tests the conditional mean E(Y|X) directly and reports whether the effects of "
                    + "several parents combine additively, which distinguishes these cases; note that its verdicts "
                    + "are direction-relative, so both regression directions are informative.";

    /**
     * The note attached by {@link #notes()} when at least one {@link FindingCode#SERIAL_DEPENDENCE} finding fired
     * and the check was computed pooled, without a serial grouping variable.
     * <p>
     * File-order dependence has two distinct causes with the same signature. The rows may carry genuine serial
     * structure - a time series or spatial sequence - in which case the dependence is a property of the process.
     * Or the file may be sorted by block: rows grouped by subject, configuration, or condition, with within-block
     * homogeneity masquerading as autocorrelation. Variables constant within blocks read as near-1 lag-1
     * autocorrelation in file order, and smooth within-block sweeps read as strong positive dependence, without any
     * temporal process at all. The pooled check does not separate these; recomputing the check within groups (via
     * the serial grouping variable) does - under the block reading the within-group autocorrelations collapse and
     * block-constant variables are skipped, while under the serial reading the dependence survives grouping. The
     * two readings point at different downstream treatments, so the distinction matters before acting on the
     * finding.
     */
    public static final String SERIAL_DEPENDENCE_NOTE =
            "Serial dependence in file order was flagged with no serial grouping variable configured. This "
                    + "signature has two distinct causes: genuine serial structure (a time series or spatial "
                    + "sequence), or a file sorted by block (rows grouped by subject, configuration, or condition), "
                    + "where within-block homogeneity masquerades as autocorrelation. If a discrete variable "
                    + "identifies the blocks, recomputing the audit with it as the serial grouping variable "
                    + "separates the two: under the block reading the within-group autocorrelations collapse and "
                    + "block-constant variables are skipped, while under the serial reading the dependence "
                    + "survives grouping.";

    /**
     * Cross-reference emitted when SENTINEL_VALUE fires: the check judges the shape of the observed distribution,
     * and only the data's provenance settles whether the flagged value is a code or a measurement.
     */
    public static final String SENTINEL_VALUE_NOTE =
            "A repeated point mass at the extreme of a variable's range was flagged (SENTINEL_VALUE). Whether such "
                    + "a value is a missing-data code or a real measurement cannot be settled from the numbers "
                    + "alone; the codebook or data dictionary for the file settles it. If it is a code, the cells "
                    + "holding it are missing entries that the file does not mark as missing, so the missingness "
                    + "audit understates the missing rate and the pattern of codes across variables is a "
                    + "missingness pattern: recoding them to missing and rerunning the audit reports both. The "
                    + "Data Audit dialog offers that recode per flagged variable. Note that the audit\'s other "
                    + "continuous checks - correlations, near-determinism, non-Gaussianity, serial dependence - "
                    + "were computed with the flagged value treated as data, so their verdicts for the affected "
                    + "columns should be read after the recode, not before.";

    /**
     * The dataset being audited.
     */
    private final DataSet dataSet;

    /**
     * The thresholds used by the checks.
     */
    private final Config config;

    /**
     * The findings, in the order the checks run.
     */
    private final List<AuditFinding> findings = new ArrayList<>();

    /**
     * Whether each column is discrete.
     */
    private final boolean[] discrete;

    /**
     * Whether each column is constant (at most one distinct value among non-missing entries), as determined by
     * {@link #constancyChecks()}. Constant columns are flagged CONSTANT_COLUMN and excluded from subsequent checks.
     */
    private final boolean[] constant;

    /**
     * Variable names, in column order.
     */
    private final String[] names;

    /**
     * The number of distinct observed (non-missing) values per column, by name.
     */
    private final Map<String, Integer> observedDistinct = new LinkedHashMap<>();

    /**
     * Column indices of the continuous variables, in column order, excluding constant columns.
     */
    private final int[] continuousIndices;

    /**
     * Names of the continuous variables, in column order, excluding constant columns.
     */
    private final List<String> continuousNames = new ArrayList<>();

    /**
     * Pairwise-complete correlation matrix of the continuous variables, or null if fewer than two continuous
     * variables.
     */
    private final Matrix continuousCorrelation;

    /**
     * Anderson-Darling p-value per continuous variable name (only where enough non-missing values were available).
     */
    private final Map<String, Double> adPValues = new LinkedHashMap<>();

    /**
     * Multiple R-squared of each continuous variable on the other continuous variables, by name (only where
     * computable).
     */
    private final Map<String, Double> r2OnOthers = new LinkedHashMap<>();

    /**
     * Eta-squared for each (discrete, continuous) pair, keyed "discreteName|continuousName".
     */
    private final Map<String, Double> etaSquared = new LinkedHashMap<>();

    /**
     * Lag-1 autocorrelation in file order per continuous variable name (within groups, if a serial grouping variable
     * is configured), for those variables with enough observed values to compute it.
     */
    private final Map<String, Double> lag1Autocorrelations = new LinkedHashMap<>();

    /**
     * Ljung-Box p-value per continuous variable name over the configured number of lags, for those variables with
     * enough observed values to compute it.
     */
    private final Map<String, Double> ljungBoxPValues = new LinkedHashMap<>();

    /**
     * The delegated missingness audit, or null if the dataset has no missing values.
     */
    private final MissingDataAudit missingDataAudit;

    /**
     * Throws if the current thread has been interrupted, so that a caller running the audit on a worker thread (the
     * GUI's stop dialog, for instance) can cancel it. Called between variables in each per-variable and pairwise
     * pass. The exception is unchecked, since the audit runs in a constructor, but carries an InterruptedException
     * as its cause so that callers can recognize it as a cancellation rather than a failure.
     */
    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new RuntimeException(new InterruptedException("Data audit interrupted."));
        }
    }

    /**
     * Audits the given dataset with default thresholds.
     *
     * @param dataSet the dataset to audit; may not be null.
     */
    public DataAudit(DataSet dataSet) {
        this(dataSet, new Config());
    }

    /**
     * Audits the given dataset with the given thresholds.
     *
     * @param dataSet the dataset to audit; may not be null.
     * @param config  the thresholds to use; may not be null.
     */
    public DataAudit(DataSet dataSet, Config config) {
        if (dataSet == null) throw new NullPointerException("dataSet");
        if (config == null) throw new NullPointerException("config");

        this.dataSet = dataSet;
        this.config = config;

        int p = dataSet.getNumColumns();
        this.discrete = new boolean[p];
        this.constant = new boolean[p];
        this.names = new String[p];

        List<Node> variables = dataSet.getVariables();

        for (int j = 0; j < p; j++) {
            Node v = variables.get(j);
            this.names[j] = v.getName();
            this.discrete[j] = v instanceof DiscreteVariable;
        }

        MissingDataAudit mda = new MissingDataAudit(dataSet);
        this.missingDataAudit = mda.anyMissing() ? mda : null;

        variableNameCheck();
        censusCheck();
        constancyChecks();

        // Constant columns (flagged above) are excluded from the continuous-variable arrays and hence from all
        // subsequent continuous checks; see the class Javadoc.
        List<Integer> contIdx = new ArrayList<>();

        for (int j = 0; j < p; j++) {
            if (!this.discrete[j] && !this.constant[j]) {
                contIdx.add(j);
                this.continuousNames.add(this.names[j]);
            }
        }

        this.continuousIndices = contIdx.stream().mapToInt(Integer::intValue).toArray();

        smallCellChecks();
        sentinelValueCheck();
        duplicateColumnChecks();
        this.continuousCorrelation = correlationChecks();
        nearDeterminismDiscreteContinuousCheck();
        nearDeterminismNonlinearCheck();
        cellDeterminismCheck();
        nonGaussianityCheck();
        serialDependenceCheck();
        groupConstantCheck();
        sampleRatioCheck();
        missingnessCheck();
    }

    //==================================== PUBLIC ACCESSORS ====================================//

    /**
     * Returns the findings, in the order the checks run, unmodifiable.
     *
     * @return These findings.
     */
    public List<AuditFinding> getFindings() {
        return List.copyOf(this.findings);
    }

    /**
     * Returns the findings with the given code, in order, unmodifiable.
     *
     * @param code the code to filter by.
     * @return These findings.
     */
    public List<AuditFinding> getFindings(FindingCode code) {
        return this.findings.stream().filter(f -> f.getCode() == code).toList();
    }

    /**
     * Returns true if any finding has the given code.
     *
     * @param code the code to look for.
     * @return True if present.
     */
    public boolean hasFinding(FindingCode code) {
        return this.findings.stream().anyMatch(f -> f.getCode() == code);
    }

    /**
     * Returns the number of distinct observed (non-missing) values per column, by variable name, unmodifiable.
     *
     * @return This map.
     */
    public Map<String, Integer> getObservedDistinctCounts() {
        return new LinkedHashMap<>(this.observedDistinct);
    }

    /**
     * Returns the names of the continuous variables, in column order, excluding constant columns (see the class
     * Javadoc), unmodifiable.
     *
     * @return These names.
     */
    public List<String> getContinuousNames() {
        return List.copyOf(this.continuousNames);
    }

    /**
     * Returns the pairwise-complete Pearson correlation matrix of the continuous variables, in the order given by
     * {@link #getContinuousNames()}, or null if there are fewer than two continuous variables.
     *
     * @return This matrix or null.
     */
    public Matrix getContinuousCorrelationMatrix() {
        return this.continuousCorrelation == null ? null : new Matrix(this.continuousCorrelation);
    }

    /**
     * Returns the Anderson-Darling p-values per continuous variable name, for those variables with enough non-missing
     * values to test, unmodifiable.
     *
     * @return This map.
     */
    public Map<String, Double> getAdPValues() {
        return new LinkedHashMap<>(this.adPValues);
    }

    /**
     * Returns the multiple R-squared of each continuous variable regressed on the other continuous variables, by
     * name, for those variables where this was computable, unmodifiable.
     *
     * @return This map.
     */
    public Map<String, Double> getR2OnOtherContinuous() {
        return new LinkedHashMap<>(this.r2OnOthers);
    }

    /**
     * Returns eta-squared values for (discrete, continuous) pairs, keyed "discreteName|continuousName",
     * unmodifiable.
     *
     * @return This map.
     */
    public Map<String, Double> getEtaSquaredValues() {
        return new LinkedHashMap<>(this.etaSquared);
    }

    /**
     * Returns the lag-1 autocorrelation in file order per continuous variable name, for those variables with enough
     * observed values to compute it, unmodifiable. If a serial grouping variable is configured, autocorrelations are
     * computed within its groups (per-group centering, cross-boundary pairs excluded) and pooled.
     *
     * @return This map.
     */
    public Map<String, Double> getLag1Autocorrelations() {
        return new LinkedHashMap<>(this.lag1Autocorrelations);
    }

    /**
     * Returns the Ljung-Box p-value per continuous variable name, testing the joint null that the first
     * {@code serialMaxLag} autocorrelations in file order are zero, for those variables with enough observed values
     * to compute it, unmodifiable. The chi-square reference distribution is approximate when there are missing values
     * or a serial grouping variable.
     *
     * @return This map.
     */
    public Map<String, Double> getSerialDependencePValues() {
        return new LinkedHashMap<>(this.ljungBoxPValues);
    }

    /**
     * Returns the delegated missingness audit, or null if the dataset has no missing values.
     *
     * @return This audit or null.
     */
    public MissingDataAudit getMissingDataAudit() {
        return this.missingDataAudit;
    }

    /**
     * Sets every cell of the named continuous variable that holds the given value to missing (NaN), in place, and
     * returns the number of cells changed. This is the operation a SENTINEL_VALUE finding describes: it converts a
     * code the file wrote as an ordinary number into a missing entry, so that the missingness audit counts it, the
     * pairwise-complete correlations skip it, and scores and tests apply whatever missing-data policy is in force
     * instead of treating the code as a measurement.
     * <p>
     * The dataset is modified in place, which is destructive and not undoable: callers that need to preserve the
     * original should pass a copy. The comparison is exact equality against the stored double, which is what the
     * audit's own distinct-value tally uses, so the value taken from a finding's "value" entry matches exactly the
     * cells the finding counted. Cells already missing are left alone and are not counted in the return value.
     * <p>
     * Applying this to a variable the audit did not flag, or with a value the audit did not report, is permitted:
     * the audit's detector is a heuristic and the codebook is authoritative, so a user who knows the code is
     * entitled to recode a column the heuristic missed.
     *
     * @param dataSet  the dataset to modify; may not be null.
     * @param variable the name of a continuous variable in the dataset.
     * @param value    the value to treat as a missing-data code.
     * @return the number of cells set to missing.
     * @throws IllegalArgumentException if there is no such variable, or if it is discrete.
     */
    public static int recodeToMissing(DataSet dataSet, String variable, double value) {
        if (dataSet == null) throw new NullPointerException("dataSet");

        Node node = dataSet.getVariable(variable);

        if (node == null) {
            throw new IllegalArgumentException("No such variable in the dataset: " + variable);
        }

        if (node instanceof DiscreteVariable) {
            throw new IllegalArgumentException("Variable " + variable + " is discrete; recoding to missing here is "
                    + "defined for continuous variables only, since a discrete sentinel is a category and removing "
                    + "it renumbers the remaining ones.");
        }

        int j = dataSet.getColumnIndex(node);
        int changed = 0;

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            if (dataSet.getDouble(i, j) == value) {
                dataSet.setDouble(i, j, Double.NaN);
                changed++;
            }
        }

        return changed;
    }

    /**
     * Returns cross-references to other diagnostics bearing on the findings that fired, in a fixed order, or an empty
     * list if none apply. These are not recommendations about the data or the analysis; each note names a further
     * measurement that would resolve an ambiguity the audit cannot resolve on its own, and the audit takes no
     * position on whether the user should make it.
     *
     * @return This list of notes.
     * @see #NON_GAUSSIAN_NOTE
     */
    public List<String> notes() {
        List<String> notes = new ArrayList<>();
        if (hasFinding(FindingCode.NON_GAUSSIAN)) notes.add(NON_GAUSSIAN_NOTE);
        if (hasFinding(FindingCode.SERIAL_DEPENDENCE) && this.config.serialGroupVariable == null) {
            notes.add(SERIAL_DEPENDENCE_NOTE);
        }
        if (hasFinding(FindingCode.SENTINEL_VALUE)) notes.add(SENTINEL_VALUE_NOTE);
        return notes;
    }

    /**
     * Returns a human-readable multi-section report of the audit.
     *
     * @return This report.
     */
    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("Data audit: ").append(this.dataSet.getNumRows()).append(" rows, ")
                .append(this.dataSet.getNumColumns()).append(" variables (")
                .append(this.continuousNames.size()).append(" continuous, ")
                .append(this.dataSet.getNumColumns() - this.continuousNames.size()).append(" discrete).\n");

        long warnings = this.findings.stream().filter(f -> f.getSeverity() == AuditFinding.Severity.WARNING).count();
        long infos = this.findings.size() - warnings;
        sb.append("Findings: ").append(warnings).append(" warning(s), ").append(infos).append(" informational.\n");

        if (this.findings.isEmpty()) {
            sb.append("No findings; no checked property of the data matrix was flagged.\n");
        } else {
            for (AuditFinding f : this.findings) {
                sb.append("  ").append(f).append('\n');
            }
        }

        if (this.missingDataAudit != null) {
            sb.append('\n').append(this.missingDataAudit.report());
        }

        List<String> notes = notes();

        if (!notes.isEmpty()) {
            sb.append("\nNotes (cross-references to other diagnostics, not recommendations):\n");
            for (String note : notes) sb.append("  ").append(note).append('\n');
        }

        return sb.toString();
    }

    /**
     * Returns the findings and summary statistics as a JSON string, suitable for consumption by py-tetrad or other
     * tools. The schema has top-level fields "numRows", "numVariables", "numContinuous", "numDiscrete", "findings",
     * and "notes". "findings" is an array of objects with fields "code", "severity", "variables", "values", and
     * "message"; "notes" is an array of the strings returned by {@link #notes()}, kept separate from the findings so
     * that a consumer reading "findings" alone sees findings only.
     *
     * @return This JSON string.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"numRows\":").append(this.dataSet.getNumRows()).append(",");
        sb.append("\"numVariables\":").append(this.dataSet.getNumColumns()).append(",");
        sb.append("\"numContinuous\":").append(this.continuousNames.size()).append(",");
        sb.append("\"numDiscrete\":").append(this.dataSet.getNumColumns() - this.continuousNames.size()).append(",");
        sb.append("\"findings\":[");

        for (int i = 0; i < this.findings.size(); i++) {
            AuditFinding f = this.findings.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"code\":\"").append(f.getCode()).append("\",");
            sb.append("\"severity\":\"").append(f.getSeverity()).append("\",");
            sb.append("\"variables\":[");

            for (int k = 0; k < f.getVariables().size(); k++) {
                if (k > 0) sb.append(",");
                sb.append("\"").append(escape(f.getVariables().get(k))).append("\"");
            }

            sb.append("],\"values\":{");
            int k = 0;

            for (Map.Entry<String, Double> e : f.getValues().entrySet()) {
                if (k++ > 0) sb.append(",");
                sb.append("\"").append(escape(e.getKey())).append("\":").append(jsonNumber(e.getValue()));
            }

            sb.append("},\"message\":\"").append(escape(f.getMessage())).append("\"}");
        }

        sb.append("],\"notes\":[");
        List<String> notes = notes();

        for (int i = 0; i < notes.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escape(notes.get(i))).append("\"");
        }

        sb.append("]}");
        return sb.toString();
    }

    /**
     * Returns the report for this audit.
     *
     * @return This report.
     */
    @Override
    public String toString() {
        return report();
    }

    //==================================== CHECKS ====================================//

    /**
     * Flags variable names that collide with naming conventions Tetrad's own machinery gives special meaning,
     * aggregated into one finding per collision category so that a fully lagged dataset (in which every name past
     * lag zero carries a colon suffix by design) produces one INFO finding rather than one per column. The
     * categories, in the order emitted, are: names of the form base:k with nonnegative integer k, which the
     * time-series machinery reads as base lagged k steps (INFO, since lagged data carries such names on purpose);
     * names containing a colon whose suffix does not parse as a lag (WARNING, since such names collide with the
     * lag-suffix convention without being readable as lags); names beginning with "E_", the prefix under which SEM
     * graphs generate error-term nodes -- with a note when the remainder after "E_" is itself the name of another
     * variable in this dataset, so that a generated error node would collide with it outright (WARNING); and names
     * containing '*' or ',', which knowledge specifications interpret as a wildcard and a list separator
     * respectively (WARNING). Purely a property of the header; reads no data cells.
     */
    private void variableNameCheck() {
        List<String> lagLike = new ArrayList<>();
        List<String> colonNotLag = new ArrayList<>();
        List<String> errorPrefixed = new ArrayList<>();
        List<String> errorCollisions = new ArrayList<>();
        List<String> specChars = new ArrayList<>();

        Set<String> nameSet = new HashSet<>(Arrays.asList(this.names));

        for (String name : this.names) {
            int colon = name.indexOf(':');

            if (colon >= 0) {
                boolean validLag;

                try {
                    validLag = Integer.parseInt(name.substring(colon + 1)) >= 0;
                } catch (NumberFormatException e) {
                    validLag = false;
                }

                if (validLag) {
                    lagLike.add(name);
                } else {
                    colonNotLag.add(name);
                }
            }

            if (name.startsWith("E_")) {
                errorPrefixed.add(name);

                if (nameSet.contains(name.substring(2))) {
                    errorCollisions.add(name);
                }
            }

            if (name.indexOf('*') >= 0 || name.indexOf(',') >= 0) {
                specChars.add(name);
            }
        }

        if (!lagLike.isEmpty()) {
            this.findings.add(new AuditFinding(FindingCode.RESERVED_VARIABLE_NAME,
                    AuditFinding.Severity.INFO, lagLike,
                    Map.of("count", (double) lagLike.size()),
                    "Names of the form base:k, which Tetrad's time-series machinery reads as base lagged k steps ("
                            + listSome(lagLike) + "). If this dataset is lagged data, that reading is the intended "
                            + "one."));
        }

        if (!colonNotLag.isEmpty()) {
            this.findings.add(new AuditFinding(FindingCode.RESERVED_VARIABLE_NAME,
                    AuditFinding.Severity.WARNING, colonNotLag,
                    Map.of("count", (double) colonNotLag.size()),
                    "Names containing a colon whose suffix does not parse as a lag (" + listSome(colonNotLag)
                            + "). The colon in variable names is reserved by Tetrad's time-series machinery for lag "
                            + "suffixes, as in X:1 for X lagged once, and lagging data with such names is refused."));
        }

        if (!errorPrefixed.isEmpty()) {
            String collisionText = errorCollisions.isEmpty() ? ""
                    : " For " + listSome(errorCollisions) + ", the remainder after \"E_\" names another variable in "
                      + "this dataset, so a generated error node for that variable would have exactly this name.";

            this.findings.add(new AuditFinding(FindingCode.RESERVED_VARIABLE_NAME,
                    AuditFinding.Severity.WARNING, errorPrefixed,
                    Map.of("count", (double) errorPrefixed.size()),
                    "Names beginning with \"E_\" (" + listSome(errorPrefixed) + "), the prefix under which SEM "
                            + "graphs generate error-term nodes and which some graph utilities treat as marking an "
                            + "error term." + collisionText));
        }

        if (!specChars.isEmpty()) {
            this.findings.add(new AuditFinding(FindingCode.RESERVED_VARIABLE_NAME,
                    AuditFinding.Severity.WARNING, specChars,
                    Map.of("count", (double) specChars.size()),
                    "Names containing '*' or ',' (" + listSome(specChars) + "); knowledge specifications interpret "
                            + "'*' as a wildcard and ',' as a list separator."));
        }
    }

    /**
     * Renders up to eight of the given names as a comma-separated list, appending "and k more" past that, for use
     * in finding messages whose full variable lists are carried by the finding itself.
     */
    private static String listSome(List<String> names) {
        int limit = 8;

        if (names.size() <= limit) {
            return String.join(", ", names);
        }

        return String.join(", ", names.subList(0, limit)) + ", and " + (names.size() - limit) + " more";
    }

    /**
     * Counts distinct observed values per column and flags continuous variables with few distinct values and discrete
     * variables with many observed levels.
     */
    private void censusCheck() {
        int n = this.dataSet.getNumRows();

        for (int j = 0; j < this.names.length; j++) {
            Set<Double> distinct = new HashSet<>();

            for (int i = 0; i < n; i++) {
                if (MissingDataAudit.isMissing(this.dataSet, i, j)) continue;
                distinct.add(this.discrete[j] ? (double) this.dataSet.getInt(i, j) : this.dataSet.getDouble(i, j));
            }

            this.observedDistinct.put(this.names[j], distinct.size());

            if (!this.discrete[j] && distinct.size() <= this.config.fewContinuousValues && distinct.size() > 1) {
                this.findings.add(new AuditFinding(FindingCode.CONTINUOUS_FEW_VALUES,
                        AuditFinding.Severity.WARNING, List.of(this.names[j]),
                        Map.of("distinctValues", (double) distinct.size()),
                        "Continuous variable " + this.names[j] + " takes only " + distinct.size()
                                + " distinct observed values."));
            }

            if (this.discrete[j] && distinct.size() >= this.config.manyDiscreteLevels) {
                this.findings.add(new AuditFinding(FindingCode.DISCRETE_MANY_LEVELS,
                        AuditFinding.Severity.WARNING, List.of(this.names[j]),
                        Map.of("observedLevels", (double) distinct.size()),
                        "Discrete variable " + this.names[j] + " has " + distinct.size()
                                + " observed levels; conditioning on it will produce many small cells."));
            }
        }
    }

    /**
     * Flags exactly constant columns (at most one distinct value among non-missing entries, including the degenerate
     * cases of one or zero non-missing entries) as CONSTANT_COLUMN, and, for columns that do vary, flags continuous
     * variables with negligible variance and discrete variables with almost all mass on one category as
     * NEAR_CONSTANT. A column flagged CONSTANT_COLUMN is not additionally flagged NEAR_CONSTANT. Constancy is
     * determined by exact equality of observed values, not by the variance threshold, so floating-point cancellation
     * in the variance computation cannot misclassify a constant column. Uses non-missing values only.
     * <p>
     * As a side effect, records which columns are constant; the constructor excludes those columns from the
     * continuous-variable arrays and the discrete checks that follow, so that a constant column cannot mask
     * downstream findings (see the class Javadoc).
     */
    private void constancyChecks() {
        int n = this.dataSet.getNumRows();

        for (int j = 0; j < this.names.length; j++) {
            if (this.discrete[j]) {
                Map<Integer, Integer> counts = new LinkedHashMap<>();
                int total = 0;

                for (int i = 0; i < n; i++) {
                    if (MissingDataAudit.isMissing(this.dataSet, i, j)) continue;
                    counts.merge(this.dataSet.getInt(i, j), 1, Integer::sum);
                    total++;
                }

                if (counts.size() <= 1) {
                    this.constant[j] = true;

                    if (total == 0) {
                        this.findings.add(new AuditFinding(FindingCode.CONSTANT_COLUMN,
                                AuditFinding.Severity.WARNING, List.of(this.names[j]),
                                Map.of("numNonMissing", 0.0),
                                "Discrete variable " + this.names[j]
                                        + " has no non-missing values. Excluded from subsequent audit checks."));
                    } else {
                        int cat = counts.keySet().iterator().next();
                        this.findings.add(new AuditFinding(FindingCode.CONSTANT_COLUMN,
                                AuditFinding.Severity.WARNING, List.of(this.names[j]),
                                Map.of("numNonMissing", (double) total, "categoryIndex", (double) cat),
                                "Discrete variable " + this.names[j] + " is constant: all " + total
                                        + " non-missing values fall in one category."
                                        + " Excluded from subsequent audit checks."));
                    }

                    continue;
                }

                int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                double maxFreq = max / (double) total;

                if (maxFreq >= this.config.nearConstantFrequency) {
                    this.findings.add(new AuditFinding(FindingCode.NEAR_CONSTANT,
                            AuditFinding.Severity.WARNING, List.of(this.names[j]),
                            Map.of("modalFrequency", maxFreq),
                            "Discrete variable " + this.names[j] + " has " + fmt(100 * maxFreq)
                                    + "% of its observed mass on one category."));
                }
            } else {
                double sum = 0, sumSq = 0;
                double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
                int m = 0;

                for (int i = 0; i < n; i++) {
                    if (MissingDataAudit.isMissing(this.dataSet, i, j)) continue;
                    double x = this.dataSet.getDouble(i, j);
                    sum += x;
                    sumSq += x * x;
                    if (x < min) min = x;
                    if (x > max) max = x;
                    m++;
                }

                if (m == 0) {
                    this.constant[j] = true;
                    this.findings.add(new AuditFinding(FindingCode.CONSTANT_COLUMN,
                            AuditFinding.Severity.WARNING, List.of(this.names[j]),
                            Map.of("numNonMissing", 0.0),
                            "Continuous variable " + this.names[j]
                                    + " has no non-missing values. Excluded from subsequent audit checks."));
                    continue;
                }

                if (min == max) {
                    this.constant[j] = true;
                    this.findings.add(new AuditFinding(FindingCode.CONSTANT_COLUMN,
                            AuditFinding.Severity.WARNING, List.of(this.names[j]),
                            Map.of("value", min, "numNonMissing", (double) m),
                            "Continuous variable " + this.names[j] + " is constant: all " + m
                                    + " non-missing values equal " + min
                                    + ". Excluded from subsequent audit checks."));
                    continue;
                }

                double var = (sumSq - sum * sum / m) / (m - 1);

                if (var <= this.config.nearConstantVariance) {
                    this.findings.add(new AuditFinding(FindingCode.NEAR_CONSTANT,
                            AuditFinding.Severity.WARNING, List.of(this.names[j]),
                            Map.of("variance", var),
                            "Continuous variable " + this.names[j] + " is nearly constant."));
                }
            }
        }
    }

    /**
     * Flags continuous variables whose extreme observed value is a repeated point mass separated from the remainder
     * of the distribution by a gap far larger than the typical spacing between adjacent observed values elsewhere in
     * the column: the signature of a sentinel code standing in for a measurement that was not taken.
     * <p>
     * Both ends of the range are examined, since codes appear as implausibly low values (0, -1, -999) and as
     * implausibly high ones (99, 999, 9999); a variable coded at both ends yields two findings. Three conditions
     * must hold at an end for it to be flagged. The value must repeat at least {@code sentinelMinCount} times and
     * account for at least {@code sentinelMinMass} of the observed values, so that an isolated extreme observation
     * is not mistaken for a code. And the gap from that value to its nearest observed neighbor must be at least
     * {@code sentinelGapRatio} times the median spacing between adjacent distinct values among the rest of the
     * column, which is what separates a code from a legitimate floor: a count variable whose zero means "none" sits
     * one ordinary step below its neighbors, whereas a code sits far outside the measured range. The candidate is
     * excluded from the spacing reference so that its own gap does not inflate the quantity it is compared against.
     * <p>
     * The reference is a median of spacings rather than a spread statistic such as the interquartile range for a
     * reason worth recording: when the code carries a large share of the mass, it dominates the column's spread, and
     * an IQR-based reference grows with the very contamination the check is looking for, so heavily coded columns
     * escape detection. A spacing-based reference is computed from the uncoded values alone and does not.
     * <p>
     * Only continuous, non-constant variables are examined. A constant column is flagged CONSTANT_COLUMN and is
     * already excluded from the continuous indices; a discrete variable's sentinel category is visible in its level
     * list and needs no inference from spacing.
     */
    private void sentinelValueCheck() {
        int n = this.dataSet.getNumRows();

        for (int j : this.continuousIndices) {
            checkInterrupted();

            double[] observed = new double[n];
            int m = 0;

            for (int i = 0; i < n; i++) {
                if (MissingDataAudit.isMissing(this.dataSet, i, j)) continue;
                observed[m++] = this.dataSet.getDouble(i, j);
            }

            if (m == 0) continue;

            double[] sorted = Arrays.copyOf(observed, m);
            Arrays.sort(sorted);

            double[] distinct = new double[m];
            int[] counts = new int[m];
            int k = 0;

            for (int i = 0; i < m; i++) {
                if (k > 0 && sorted[i] == distinct[k - 1]) {
                    counts[k - 1]++;
                } else {
                    distinct[k] = sorted[i];
                    counts[k] = 1;
                    k++;
                }
            }

            if (k < this.config.sentinelMinDistinct) continue;

            sentinelCandidate(j, distinct, counts, k, m, true);
            sentinelCandidate(j, distinct, counts, k, m, false);
        }
    }

    /**
     * Examines one end of a continuous variable's observed range for a sentinel code and adds a SENTINEL_VALUE
     * finding if the count, mass and gap conditions of {@link #sentinelValueCheck()} all hold there.
     *
     * @param j        the column index of the variable.
     * @param distinct the distinct observed values, ascending, in positions 0 through k - 1.
     * @param counts   the number of observations at each distinct value, in the same positions.
     * @param k        the number of distinct observed values; at least sentinelMinDistinct, hence at least three.
     * @param m        the number of observed (non-missing) values in the column.
     * @param low      true to examine the minimum, false to examine the maximum.
     */
    private void sentinelCandidate(int j, double[] distinct, int[] counts, int k, int m, boolean low) {
        int index = low ? 0 : k - 1;
        double candidate = distinct[index];
        int count = counts[index];
        double mass = count / (double) m;

        if (count < this.config.sentinelMinCount) return;
        if (mass < this.config.sentinelMinMass) return;

        double gap = low ? distinct[1] - distinct[0] : distinct[k - 1] - distinct[k - 2];

        // Median spacing between adjacent distinct values among the k - 1 values other than the candidate, which
        // furnish k - 2 consecutive differences. Excluding the candidate keeps its own gap out of the reference.
        double[] spacings = new double[k - 2];

        for (int t = 0; t < k - 2; t++) {
            int a = low ? t + 1 : t;
            spacings[t] = distinct[a + 1] - distinct[a];
        }

        Arrays.sort(spacings);
        int half = spacings.length / 2;
        double medianSpacing = spacings.length % 2 == 1
                ? spacings[half] : 0.5 * (spacings[half - 1] + spacings[half]);

        if (!(medianSpacing > 0)) return;

        double gapRatio = gap / medianSpacing;

        if (gapRatio < this.config.sentinelGapRatio) return;

        boolean commonCode = isCommonMissingCode(candidate);

        Map<String, Double> values = new LinkedHashMap<>();
        values.put("value", candidate);
        values.put("count", (double) count);
        values.put("mass", mass);
        values.put("gap", gap);
        values.put("medianSpacing", medianSpacing);
        values.put("gapRatio", gapRatio);
        values.put("atMinimum", low ? 1.0 : 0.0);
        values.put("commonMissingCode", commonCode ? 1.0 : 0.0);

        this.findings.add(new AuditFinding(FindingCode.SENTINEL_VALUE, AuditFinding.Severity.WARNING,
                List.of(this.names[j]), values,
                "Continuous variable " + this.names[j] + " has " + count + " of its " + m + " observed values ("
                        + fmt(100 * mass) + "%) at its " + (low ? "minimum" : "maximum") + " " + fmt(candidate)
                        + ", separated from the rest of the distribution by a gap of " + fmt(gap) + ", "
                        + fmt(gapRatio) + " times the median spacing between adjacent observed values elsewhere in "
                        + "the column"
                        + (commonCode ? "; " + fmt(candidate) + " is a value commonly written as a missing-data code"
                        : "") + "."));
    }

    /**
     * True for the values most often written into data files as missing-data codes. This is used only to annotate a
     * SENTINEL_VALUE finding, never to trigger one: the count, mass and gap conditions decide, and a code that is
     * not on this list (a study-specific one such as -6, say) is flagged on the same evidence as one that is.
     *
     * @param x the candidate value.
     * @return True if x is a conventional missing-data code.
     */
    private static boolean isCommonMissingCode(double x) {
        if (x != Math.rint(x)) return false;

        long v = (long) Math.rint(x);

        return v == 0L || v == -1L || v == -8L || v == -9L || v == -88L || v == -99L || v == -999L || v == -9999L
                || v == 99L || v == 999L || v == 9999L;
    }

    /**
     * Flags small observed marginal cells for discrete variables and small expected pairwise cells for pairs of
     * discrete variables (expected counts computed under independence from the observed margins).
     */
    private void smallCellChecks() {
        int n = this.dataSet.getNumRows();

        // Marginal cells.
        for (int j = 0; j < this.names.length; j++) {
            if (!this.discrete[j] || this.constant[j]) continue;
            Map<Integer, Integer> counts = new LinkedHashMap<>();

            for (int i = 0; i < n; i++) {
                if (MissingDataAudit.isMissing(this.dataSet, i, j)) continue;
                counts.merge(this.dataSet.getInt(i, j), 1, Integer::sum);
            }

            int small = 0;
            int minCount = Integer.MAX_VALUE;

            for (int c : counts.values()) {
                if (c < this.config.smallCellCount) small++;
                minCount = Math.min(minCount, c);
            }

            if (small > 0) {
                DiscreteVariable v = (DiscreteVariable) this.dataSet.getVariables().get(j);
                this.findings.add(new AuditFinding(FindingCode.SMALL_MARGINAL_CELL,
                        AuditFinding.Severity.WARNING, List.of(this.names[j]),
                        Map.of("numSmallCells", (double) small, "minCellCount", (double) minCount,
                                "threshold", (double) this.config.smallCellCount),
                        "Discrete variable " + this.names[j] + " (" + v.getNumCategories() + " categories) has "
                                + small + " observed categor" + (small == 1 ? "y" : "ies") + " with fewer than "
                                + this.config.smallCellCount + " cases (smallest: " + minCount + ")."));
            }
        }

        // Pairwise expected cells.
        for (int a = 0; a < this.names.length; a++) {
            checkInterrupted();
            if (!this.discrete[a] || this.constant[a]) continue;

            for (int b = a + 1; b < this.names.length; b++) {
                if (!this.discrete[b] || this.constant[b]) continue;

                Map<Integer, Integer> countsA = new LinkedHashMap<>();
                Map<Integer, Integer> countsB = new LinkedHashMap<>();
                int total = 0;

                for (int i = 0; i < n; i++) {
                    if (MissingDataAudit.isMissing(this.dataSet, i, a)
                            || MissingDataAudit.isMissing(this.dataSet, i, b)) continue;
                    countsA.merge(this.dataSet.getInt(i, a), 1, Integer::sum);
                    countsB.merge(this.dataSet.getInt(i, b), 1, Integer::sum);
                    total++;
                }

                if (total == 0) continue;
                double minExpected = Double.MAX_VALUE;

                for (int ca : countsA.values()) {
                    for (int cb : countsB.values()) {
                        minExpected = Math.min(minExpected, ca * (double) cb / total);
                    }
                }

                if (minExpected < this.config.minExpectedPairwiseCell) {
                    this.findings.add(new AuditFinding(FindingCode.SMALL_PAIRWISE_CELLS,
                            AuditFinding.Severity.WARNING, List.of(this.names[a], this.names[b]),
                            Map.of("minExpectedCell", minExpected,
                                    "threshold", this.config.minExpectedPairwiseCell),
                            "Discrete pair (" + this.names[a] + ", " + this.names[b]
                                    + ") has a minimum expected cell count of " + fmt(minExpected)
                                    + " under independence."));
                }
            }
        }
    }

    /**
     * Computes the pairwise-complete correlation matrix of the continuous variables, flags highly correlated pairs,
     * and flags exact and near linear dependence of each continuous variable on the others (via the rank and inverse
     * of the correlation matrix).
     *
     * @return the correlation matrix, or null if fewer than two continuous variables.
     */
    private Matrix correlationChecks() {
        int pc = this.continuousIndices.length;
        if (pc < 2) return null;

        double[][] corr = new double[pc][pc];

        for (int a = 0; a < pc; a++) {
            checkInterrupted();
            corr[a][a] = 1.0;

            for (int b = a + 1; b < pc; b++) {
                int nUsed = pairwiseCompleteCount(this.continuousIndices[a], this.continuousIndices[b]);
                double r = pairwiseCorrelation(this.continuousIndices[a], this.continuousIndices[b]);
                corr[a][b] = r;
                corr[b][a] = r;

                if (!Double.isNaN(r) && Math.abs(r) >= this.config.highCorrelation) {
                    this.findings.add(new AuditFinding(FindingCode.HIGH_CORRELATION,
                            AuditFinding.Severity.WARNING,
                            List.of(this.continuousNames.get(a), this.continuousNames.get(b)),
                            Map.of("correlation", r, "threshold", this.config.highCorrelation,
                                    "nUsed", (double) nUsed),
                            "Continuous pair (" + this.continuousNames.get(a) + ", " + this.continuousNames.get(b)
                                    + ") has correlation " + fmt(r) + " on " + nUsed
                                    + " jointly observed rows."));
                }
            }
        }

        Matrix c = new Matrix(corr);

        // Guard: pairwise-complete matrices can contain NaN if some pair has < 2 complete rows.
        for (int a = 0; a < pc; a++) {
            for (int b = 0; b < pc; b++) {
                if (Double.isNaN(c.get(a, b))) return c;
            }
        }

        // If the complete-case count itself forces singularity, say so: the rank deficiency below is then a
        // consequence of arithmetic, not of relationships among specific variables.
        int numCompleteRows = this.missingDataAudit == null
                ? this.dataSet.getNumRows() : this.missingDataAudit.getNumCompleteRows();
        boolean forced = numCompleteRows - 1 < pc;

        if (forced && this.missingDataAudit != null) {
            this.findings.add(new AuditFinding(FindingCode.COMPLETE_CASES_FORCE_SINGULARITY,
                    AuditFinding.Severity.WARNING, List.of(),
                    Map.of("numCompleteRows", (double) numCompleteRows, "numContinuous", (double) pc),
                    "There are " + numCompleteRows + " complete rows for " + pc + " continuous variables; any "
                            + "covariance or correlation matrix computed on complete cases has rank at most "
                            + Math.max(0, numCompleteRows - 1) + " and is singular by arithmetic. Joint "
                            + "linear-dependence findings on these variables cannot be attributed to specific "
                            + "variable relationships. See DUPLICATE_COLUMNS findings, if any, for the "
                            + "localizable exact dependencies."));
        }

        // A pairwise-complete matrix assembled from different row subsets need not be positive semidefinite; if it
        // is not, it is not the correlation matrix of any single sample, and joint quantities computed from it
        // (rank, VIFs, partial correlations) can be incoherent.
        double minEig = Double.NaN;
        int numNegative = 0;

        try {
            org.ejml.simple.SimpleEVD<org.ejml.simple.SimpleMatrix> evd
                    = new org.ejml.simple.SimpleMatrix(corr).eig();
            minEig = Double.POSITIVE_INFINITY;

            for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
                double ev = evd.getEigenvalue(i).getReal();
                if (ev < minEig) minEig = ev;
                if (ev < -1e-8) numNegative++;
            }
        } catch (Exception e) {
            // Tolerated; the PSD check is then unavailable.
        }

        boolean notPsd = numNegative > 0;

        if (notPsd) {
            this.findings.add(new AuditFinding(FindingCode.PAIRWISE_CORRELATION_NOT_PSD,
                    AuditFinding.Severity.WARNING, List.of(),
                    Map.of("minEigenvalue", minEig, "numNegativeEigenvalues", (double) numNegative),
                    "The pairwise-complete correlation matrix of the continuous variables has " + numNegative
                            + " negative eigenvalue(s) (minimum " + fmt(minEig) + "); because each entry uses a "
                            + "different row subset, this matrix is not the correlation matrix of any single "
                            + "sample, and joint quantities derived from it (rank, R^2 on others, partial "
                            + "correlations) may be incoherent. Individual pairwise correlations remain "
                            + "interpretable, each on its own row count."));
        }

        boolean singular = c.rank() < pc;

        if (singular) {
            this.findings.add(new AuditFinding(FindingCode.EXACT_LINEAR_DEPENDENCE,
                    AuditFinding.Severity.WARNING, List.copyOf(this.continuousNames),
                    Map.of("rank", (double) c.rank(), "numContinuous", (double) pc),
                    "The pairwise-complete correlation matrix of the continuous variables is singular (rank "
                            + c.rank() + " of " + pc + ")."
                            + (forced ? " Note: with " + numCompleteRows + " complete rows this is forced by the "
                            + "complete-case count (see COMPLETE_CASES_FORCE_SINGULARITY) and does not identify "
                            + "relationships among specific variables."
                            : " Some variable is an exact linear function of the others.")
                            + (notPsd ? " The matrix is also not positive semidefinite (see "
                            + "PAIRWISE_CORRELATION_NOT_PSD), so this rank statement describes the assembled "
                            + "pairwise matrix, not any single sample." : "")));
        }

        try {
            Matrix inv = singular ? c.pseudoinverse() : c.inverse();

            for (int a = 0; a < pc; a++) {
                double vif = inv.get(a, a);
                if (vif <= 0) continue;
                double r2 = Math.min(1.0, Math.max(0.0, 1.0 - 1.0 / vif));
                this.r2OnOthers.put(this.continuousNames.get(a), r2);

                if (!singular && r2 >= this.config.r2Determinism) {
                    double[] subsetR2 = new double[1];
                    List<Integer> subset = explainingSubset(c, a, r2, subsetR2);

                    StringBuilder msg = new StringBuilder("Continuous variable " + this.continuousNames.get(a)
                            + " is nearly a linear function of the other continuous variables (R^2 = "
                            + fmt(r2) + ").");

                    if (!subset.isEmpty()) {
                        List<String> subsetNames = new ArrayList<>();
                        for (int b : subset) subsetNames.add(this.continuousNames.get(b));
                        boolean reached = subsetR2[0] >= EXPLAINING_SUBSET_FRACTION * r2;
                        msg.append(reached
                                ? " One small subset accounts for most of this: R^2 = "
                                : " The dependence is diffuse; the best small subset found greedily gives R^2 = ")
                                .append(fmt(subsetR2[0])).append(" on {")
                                .append(String.join(", ", subsetNames))
                                .append("}. With near-collinear predictors this subset choice is not unique.");
                        this.findings.add(new AuditFinding(FindingCode.NEAR_DETERMINISM_LINEAR,
                                AuditFinding.Severity.WARNING, List.of(this.continuousNames.get(a)),
                                Map.of("rSquared", r2, "threshold", this.config.r2Determinism,
                                        "subsetRSquared", subsetR2[0], "subsetSize", (double) subset.size()),
                                msg.toString()));
                    } else {
                        this.findings.add(new AuditFinding(FindingCode.NEAR_DETERMINISM_LINEAR,
                                AuditFinding.Severity.WARNING, List.of(this.continuousNames.get(a)),
                                Map.of("rSquared", r2, "threshold", this.config.r2Determinism),
                                msg.toString()));
                    }
                }
            }
        } catch (Exception e) {
            // Inversion can fail for badly conditioned pairwise-complete matrices; R^2 values are then unavailable,
            // but the singularity finding (if any) stands.
        }

        return c;
    }

    /**
     * Flags (discrete, continuous) pairs where the discrete variable nearly determines the continuous one, by
     * eta-squared (between-category sum of squares over total sum of squares), computed over rows where both are
     * observed.
     */
    private void nearDeterminismDiscreteContinuousCheck() {
        int n = this.dataSet.getNumRows();

        for (int a = 0; a < this.names.length; a++) {
            checkInterrupted();
            if (!this.discrete[a] || this.constant[a]) continue;

            for (int jc : this.continuousIndices) {
                Map<Integer, double[]> groups = new LinkedHashMap<>(); // category -> {count, sum, sumSq}
                double sum = 0, sumSq = 0;
                int m = 0;

                for (int i = 0; i < n; i++) {
                    if (MissingDataAudit.isMissing(this.dataSet, i, a)
                            || MissingDataAudit.isMissing(this.dataSet, i, jc)) continue;
                    double y = this.dataSet.getDouble(i, jc);
                    double[] g = groups.computeIfAbsent(this.dataSet.getInt(i, a), k -> new double[3]);
                    g[0]++;
                    g[1] += y;
                    g[2] += y * y;
                    sum += y;
                    sumSq += y * y;
                    m++;
                }

                if (m < 3 || groups.size() < 2) continue;
                double totalSS = sumSq - sum * sum / m;
                if (totalSS <= 0) continue;

                double betweenSS = 0;

                for (double[] g : groups.values()) {
                    betweenSS += g[1] * g[1] / g[0];
                }

                betweenSS -= sum * sum / m;
                double eta2 = Math.min(1.0, Math.max(0.0, betweenSS / totalSS));
                String key = this.names[a] + "|" + this.names[jc];
                this.etaSquared.put(key, eta2);

                if (eta2 >= this.config.etaSquaredDeterminism) {
                    String contName = this.names[jc];
                    this.findings.add(new AuditFinding(FindingCode.NEAR_DETERMINISM_DISCRETE_CONTINUOUS,
                            AuditFinding.Severity.WARNING, List.of(this.names[a], contName),
                            Map.of("etaSquared", eta2, "threshold", this.config.etaSquaredDeterminism),
                            "Discrete variable " + this.names[a] + " nearly determines continuous variable "
                                    + contName + " (eta^2 = " + fmt(eta2) + ")."));
                }
            }
        }
    }

    /**
     * Flags continuous variables that are nearly smooth (generally nonlinear) functions of small sets of other
     * continuous variables, which the linear (multiple R-squared) check cannot see. For each continuous target not
     * already flagged as NEAR_DETERMINISM_LINEAR, a determining subset is grown greedily, one variable at a
     * time up to the configured maximum size, scoring each candidate set by the leave-one-out cross-validated
     * R-squared of a natural-cubic-spline regression of the target on the set; the finding
     * NEAR_DETERMINISM_NONLINEAR is emitted for the first (hence smallest) subset whose leave-one-out R-squared
     * reaches the configured threshold. Leave-one-out cross-validation, computed exactly through the hat matrix, is
     * the overfitting guard: the reported R-squared is out-of-sample, so it cannot be inflated by basis flexibility,
     * and on independent data it sits near zero even after greedy selection.
     * <p>
     * The regression basis is a natural cubic spline per variable (truncated-power form, knots at the 10th, 30th,
     * 50th, 70th, and 90th percentiles, up to four columns per variable), with all pairwise tensor products of the
     * two variables' columns for two-variable sets and pairwise products of the linear columns only for larger sets.
     * All columns are standardized on the evaluation rows. Each candidate set is evaluated on the rows where the
     * target and the whole set are observed; a candidate is skipped when those rows number fewer than 40 or fewer
     * than three times the basis size. Rows are subsampled deterministically (even stride) above {@code
     * NONLINEAR_MAX_ROWS} rows, and when a target has more than {@code NONLINEAR_MAX_CANDIDATES} potential
     * determiners, the pool is restricted to the candidates most correlated with the target. The spline is linear
     * beyond its boundary knots, so dependence carried mostly by extreme tails can be understated; the absence of
     * this finding does not rule such dependence out.
     */
    private void nearDeterminismNonlinearCheck() {
        int maxSize = this.config.nonlinearDeterminismMaxSetSize;
        if (maxSize <= 0) return;

        int pc = this.continuousIndices.length;
        if (pc < 2) return;

        // Targets already flagged by the linear check are skipped: this finding is reserved for dependence the
        // linear check cannot see, so the two findings partition rather than duplicate.
        Set<String> linearFlagged = new HashSet<>();

        for (AuditFinding f : this.findings) {
            if (f.getCode() == FindingCode.NEAR_DETERMINISM_LINEAR && !f.getVariables().isEmpty()) {
                linearFlagged.add(f.getVariables().get(0));
            }
        }

        for (int a = 0; a < pc; a++) {
            checkInterrupted();

            String targetName = this.continuousNames.get(a);
            if (linearFlagged.contains(targetName)) continue;

            // Candidate pool: the other continuous variables, restricted on wide datasets to those most
            // correlated with the target (absolute pairwise correlation, NaN treated as zero).
            List<Integer> pool = new ArrayList<>();
            for (int b = 0; b < pc; b++) if (b != a) pool.add(b);

            if (pool.size() > NONLINEAR_MAX_CANDIDATES && this.continuousCorrelation != null) {
                final int fa = a;
                pool.sort((x, y) -> {
                    double rx = this.continuousCorrelation.get(fa, x);
                    double ry = this.continuousCorrelation.get(fa, y);
                    if (Double.isNaN(rx)) rx = 0.0;
                    if (Double.isNaN(ry)) ry = 0.0;
                    return Double.compare(Math.abs(ry), Math.abs(rx));
                });
                pool = pool.subList(0, NONLINEAR_MAX_CANDIDATES);
            }

            // Forward selection: at each size the candidate with the largest leave-one-out R-squared is added even
            // when it does not improve on the previous size, since a size-one dead end (all single variables
            // uninformative, as for a pure product Y = X1 * X2) must not block the sizes where the dependence
            // lives; the threshold, not monotone improvement, decides flagging. Backward pruning below restores
            // minimality when an uninformative variable was picked up along the way.
            List<Integer> chosen = new ArrayList<>();
            double bestLoo = Double.NEGATIVE_INFINITY;

            while (chosen.size() < maxSize) {
                Integer bestCand = null;
                double bestCandLoo = Double.NEGATIVE_INFINITY;

                for (int cand : pool) {
                    checkInterrupted();
                    if (chosen.contains(cand)) continue;

                    List<Integer> trial = new ArrayList<>(chosen);
                    trial.add(cand);
                    double loo = subsetLooRSquared(a, trial);

                    if (!Double.isNaN(loo) && loo > bestCandLoo) {
                        bestCandLoo = loo;
                        bestCand = cand;
                    }
                }

                if (bestCand == null) break;

                chosen.add(bestCand);
                bestLoo = bestCandLoo;

                if (bestLoo >= this.config.nonlinearR2Determinism) break;
            }

            if (bestLoo >= this.config.nonlinearR2Determinism) {
                // Backward pruning: any variable whose removal keeps the leave-one-out R-squared at or above
                // threshold is dropped (removing the one that leaves the largest R-squared first), so the reported
                // set is minimal with respect to the threshold.
                boolean pruned = true;

                while (pruned && chosen.size() > 1) {
                    pruned = false;
                    Integer dropVar = null;
                    double dropLoo = Double.NEGATIVE_INFINITY;

                    for (int candidateDrop : chosen) {
                        checkInterrupted();
                        List<Integer> trial = new ArrayList<>(chosen);
                        trial.remove(Integer.valueOf(candidateDrop));
                        double loo = subsetLooRSquared(a, trial);

                        if (!Double.isNaN(loo) && loo >= this.config.nonlinearR2Determinism && loo > dropLoo) {
                            dropLoo = loo;
                            dropVar = candidateDrop;
                        }
                    }

                    if (dropVar != null) {
                        chosen.remove(dropVar);
                        bestLoo = dropLoo;
                        pruned = true;
                    }
                }
            }

            if (bestLoo >= this.config.nonlinearR2Determinism) {
                double[][] cols = completeStandardizedColumns(a, chosen);
                double linearLooAtBest = Double.NaN;
                int rowsAtBest = 0;

                if (cols != null) {
                    double[] y = cols[0];
                    double[][] predictors = new double[chosen.size()][];
                    System.arraycopy(cols, 1, predictors, 0, chosen.size());
                    linearLooAtBest = looRSquared(linearFeatures(predictors), y);
                    rowsAtBest = y.length;
                }

                List<String> vars = new ArrayList<>();
                vars.add(targetName);
                List<String> subsetNames = new ArrayList<>();

                for (int b : chosen) {
                    vars.add(this.continuousNames.get(b));
                    subsetNames.add(this.continuousNames.get(b));
                }

                this.findings.add(new AuditFinding(FindingCode.NEAR_DETERMINISM_NONLINEAR,
                        AuditFinding.Severity.WARNING, vars,
                        Map.of("looRSquared", bestLoo, "threshold", this.config.nonlinearR2Determinism,
                                "linearLooRSquared", Double.isNaN(linearLooAtBest) ? -1.0 : linearLooAtBest,
                                "subsetSize", (double) chosen.size(), "rowsUsed", (double) rowsAtBest),
                        "Continuous variable " + targetName + " is nearly a smooth function of {"
                                + String.join(", ", subsetNames) + "}: leave-one-out cross-validated R^2 = "
                                + fmt(bestLoo) + " for a natural-cubic-spline regression on that set (leave-one-out "
                                + "linear R^2 on the same set = " + fmt(linearLooAtBest) + "; the gap between the "
                                + "two is dependence invisible to the linear near-determinism check). The R^2 is "
                                + "out-of-sample, so it is not an artifact of basis flexibility. With correlated "
                                + "predictors this subset is not unique, and supersets are not searched once the "
                                + "threshold is reached."));
            }
        }
    }

    /**
     * Returns the leave-one-out cross-validated spline-regression R-squared of the given target on the given
     * predictor subset (indices into the continuous arrays), or NaN when the evaluation is unavailable: too few
     * complete rows for the basis size (fewer than 40, or fewer than three times the number of basis columns), a
     * constant target on those rows, or a degenerate basis.
     */
    private double subsetLooRSquared(int target, List<Integer> subset) {
        double[][] cols = completeStandardizedColumns(target, subset);
        if (cols == null) return Double.NaN;

        double[] y = cols[0];
        double[][] predictors = new double[subset.size()][];
        System.arraycopy(cols, 1, predictors, 0, subset.size());

        double[][] features = splineFeatures(predictors);
        if (features == null) return Double.NaN;

        int m = y.length;
        int k = features[0].length;
        if (m < Math.max(40, 3 * k)) return Double.NaN;

        return looRSquared(features, y);
    }

    /**
     * Returns, for the given target and predictor columns (indices into the continuous arrays), the columns
     * restricted to the rows where all are observed, each standardized on those rows, with the target first;
     * subsampled by even stride above NONLINEAR_MAX_ROWS rows. Returns null when fewer than two complete rows
     * remain or the target is constant on them.
     */
    private double[][] completeStandardizedColumns(int target, List<Integer> predictors) {
        int nAll = this.dataSet.getNumRows();
        int[] datasetCols = new int[predictors.size() + 1];
        datasetCols[0] = this.continuousIndices[target];
        for (int i = 0; i < predictors.size(); i++) datasetCols[i + 1] = this.continuousIndices[predictors.get(i)];

        List<Integer> rows = new ArrayList<>();

        outer:
        for (int i = 0; i < nAll; i++) {
            for (int j : datasetCols) {
                if (MissingDataAudit.isMissing(this.dataSet, i, j)) continue outer;
            }
            rows.add(i);
        }

        if (rows.size() > NONLINEAR_MAX_ROWS) {
            List<Integer> sub = new ArrayList<>(NONLINEAR_MAX_ROWS);
            double stride = rows.size() / (double) NONLINEAR_MAX_ROWS;
            for (int t = 0; t < NONLINEAR_MAX_ROWS; t++) sub.add(rows.get((int) (t * stride)));
            rows = sub;
        }

        int m = rows.size();
        if (m < 2) return null;

        double[][] cols = new double[datasetCols.length][m];

        for (int c = 0; c < datasetCols.length; c++) {
            for (int t = 0; t < m; t++) cols[c][t] = this.dataSet.getDouble(rows.get(t), datasetCols[c]);
            if (!standardizeInPlace(cols[c]) && c == 0) return null;
        }

        return cols;
    }

    /**
     * Standardizes the column in place (mean zero, population standard deviation one) and returns true, or returns
     * false leaving the column centered when its standard deviation is zero.
     */
    private static boolean standardizeInPlace(double[] x) {
        int m = x.length;
        double mean = 0.0;
        for (double v : x) mean += v;
        mean /= m;

        double var = 0.0;
        for (int i = 0; i < m; i++) {
            x[i] -= mean;
            var += x[i] * x[i];
        }
        var /= m;

        if (var <= 0.0) return false;

        double sd = Math.sqrt(var);
        for (int i = 0; i < m; i++) x[i] /= sd;
        return true;
    }

    /**
     * Returns the natural-cubic-spline basis of the (standardized) column in truncated-power form: the column
     * itself, then up to three curvature columns from knots at the 10th, 30th, 50th, 70th, and 90th percentiles,
     * each curvature column standardized; degenerate (tied-knot or constant) curvature columns are dropped, down to
     * the linear column alone.
     */
    private static double[][] splineBasis(double[] x) {
        int m = x.length;

        double[] sorted = x.clone();
        java.util.Arrays.sort(sorted);
        double[] qs = {0.1, 0.3, 0.5, 0.7, 0.9};
        List<Double> knotList = new ArrayList<>();

        for (double q : qs) {
            double knot = sorted[Math.min(m - 1, (int) Math.floor(q * m))];
            if (knotList.isEmpty() || knot > knotList.get(knotList.size() - 1)) knotList.add(knot);
        }

        int numKnots = knotList.size();
        List<double[]> columns = new ArrayList<>();
        columns.add(x);

        if (numKnots >= 3) {
            double kLast = knotList.get(numKnots - 1);
            double kPenult = knotList.get(numKnots - 2);
            double[] dPenult = truncatedCubicDifference(x, kPenult, kLast);

            for (int j = 0; j < numKnots - 2; j++) {
                double[] dj = truncatedCubicDifference(x, knotList.get(j), kLast);
                double[] col = new double[m];
                for (int i = 0; i < m; i++) col[i] = dj[i] - dPenult[i];
                if (standardizeInPlace(col)) columns.add(col);
            }
        }

        return columns.toArray(new double[0][]);
    }

    /**
     * Returns the truncated-power natural-spline building block ((x - knot)+^3 - (x - lastKnot)+^3) / (lastKnot -
     * knot) for the given knot against the last knot.
     */
    private static double[] truncatedCubicDifference(double[] x, double knot, double lastKnot) {
        int m = x.length;
        double[] d = new double[m];
        double denom = lastKnot - knot;

        for (int i = 0; i < m; i++) {
            double u = Math.max(x[i] - knot, 0.0);
            double v = Math.max(x[i] - lastKnot, 0.0);
            d[i] = (u * u * u - v * v * v) / denom;
        }

        return d;
    }

    /**
     * Assembles the regression feature matrix for the given standardized predictor columns: an intercept, each
     * predictor's spline basis, then all pairwise tensor products of the two bases for two predictors, or pairwise
     * products of the linear columns only for three or more. Product columns are standardized; degenerate ones are
     * dropped. Returns null if no predictor contributes a column.
     */
    private static double[][] splineFeatures(double[][] predictors) {
        int s = predictors.length;
        int m = predictors[0].length;

        List<double[][]> bases = new ArrayList<>();
        for (double[] p : predictors) bases.add(splineBasis(p));

        List<double[]> feats = new ArrayList<>();
        double[] intercept = new double[m];
        java.util.Arrays.fill(intercept, 1.0);
        feats.add(intercept);

        for (double[][] basis : bases) feats.addAll(java.util.Arrays.asList(basis));

        if (s == 2) {
            for (double[] ca : bases.get(0)) {
                for (double[] cb : bases.get(1)) {
                    double[] prod = new double[m];
                    for (int i = 0; i < m; i++) prod[i] = ca[i] * cb[i];
                    if (standardizeInPlace(prod)) feats.add(prod);
                }
            }
        } else if (s >= 3) {
            for (int p1 = 0; p1 < s; p1++) {
                for (int p2 = p1 + 1; p2 < s; p2++) {
                    double[] prod = new double[m];
                    for (int i = 0; i < m; i++) prod[i] = predictors[p1][i] * predictors[p2][i];
                    if (standardizeInPlace(prod)) feats.add(prod);
                }
            }
        }

        if (feats.size() < 2) return null;

        double[][] features = new double[m][feats.size()];
        for (int j = 0; j < feats.size(); j++) {
            double[] col = feats.get(j);
            for (int i = 0; i < m; i++) features[i][j] = col[i];
        }

        return features;
    }

    /**
     * Assembles the linear feature matrix (intercept plus the standardized predictors themselves) for the contrast
     * value reported alongside the spline fit.
     */
    private static double[][] linearFeatures(double[][] predictors) {
        int s = predictors.length;
        int m = predictors[0].length;
        double[][] features = new double[m][s + 1];

        for (int i = 0; i < m; i++) {
            features[i][0] = 1.0;
            for (int j = 0; j < s; j++) features[i][j + 1] = predictors[j][i];
        }

        return features;
    }

    /**
     * Returns the leave-one-out cross-validated R-squared of the ridge-stabilized (lambda = 1e-10 relative to the
     * mean Gram diagonal) least-squares regression of y on the feature matrix, computed exactly through the hat
     * matrix leverages: each leave-one-out residual is the in-sample residual divided by one minus the leverage.
     * Returns NaN when y has no variance or the Gram matrix cannot be inverted.
     */
    private static double looRSquared(double[][] features, double[] y) {
        int m = features.length;
        int k = features[0].length;

        org.ejml.simple.SimpleMatrix f = new org.ejml.simple.SimpleMatrix(features);
        org.ejml.simple.SimpleMatrix gram = f.transpose().mult(f);

        double trace = 0.0;
        for (int j = 0; j < k; j++) trace += gram.get(j, j);
        double lambda = 1e-10 * trace / k;
        for (int j = 0; j < k; j++) gram.set(j, j, gram.get(j, j) + lambda);

        org.ejml.simple.SimpleMatrix gramInv;
        try {
            gramInv = gram.invert();
        } catch (Exception e) {
            return Double.NaN;
        }

        org.ejml.simple.SimpleMatrix yv = new org.ejml.simple.SimpleMatrix(m, 1);
        for (int i = 0; i < m; i++) yv.set(i, 0, y[i]);

        org.ejml.simple.SimpleMatrix beta = gramInv.mult(f.transpose()).mult(yv);
        org.ejml.simple.SimpleMatrix fitted = f.mult(beta);

        double meanY = 0.0;
        for (double v : y) meanY += v;
        meanY /= m;

        double varY = 0.0;
        for (double v : y) varY += (v - meanY) * (v - meanY);
        varY /= m;
        if (varY <= 0.0) return Double.NaN;

        double looSse = 0.0;

        for (int i = 0; i < m; i++) {
            double leverage = 0.0;

            for (int p = 0; p < k; p++) {
                double gi = 0.0;
                for (int q = 0; q < k; q++) gi += gramInv.get(p, q) * features[i][q];
                leverage += features[i][p] * gi;
            }

            leverage = Math.min(Math.max(leverage, 0.0), 0.9999);
            double resid = y[i] - fitted.get(i, 0);
            double looResid = resid / (1.0 - leverage);
            looSse += looResid * looResid;
        }

        return 1.0 - (looSse / m) / varY;
    }

    /**
     * The largest number of rows used by the nonlinear near-determinism check; above this the complete rows are
     * subsampled by even stride.
     */
    private static final int NONLINEAR_MAX_ROWS = 2000;

    /**
     * The largest candidate-determiner pool per target in the nonlinear near-determinism check; wider pools are
     * restricted to the candidates most correlated with the target.
     */
    private static final int NONLINEAR_MAX_CANDIDATES = 20;

    /**
     * Flags variables that are (near-)deterministic functions of small sets of few-valued variables, by direct cell
     * inspection: for each target variable and each candidate determining set (variables with at most the configured
     * number of distinct observed values, sets up to the configured size, smallest first), the target is scanned
     * within the joint-value cells of the set over the rows where the target and the whole set are observed. The
     * finding DETERMINISTIC_RELATION is emitted when the target is constant, within tolerance, in every cell
     * containing at least two rows, provided the scan is non-vacuous: at least 20 usable rows, at least 5 multi-row
     * cells, and at least half of the usable rows lying in multi-row cells (single-row cells are vacuously constant
     * and prove nothing). For a continuous target, "constant within tolerance" means the cell's range is at most the
     * configured tolerance times the target's standard deviation on the scanned rows; for a discrete target it means
     * a single observed category. Only minimal determining sets are reported, and a pair already reported as
     * DUPLICATE_COLUMNS is not re-reported as a one-element determinism. Each set size is admitted only if its full
     * enumeration fits a fixed work budget, so the check degrades deterministically (whole sizes are skipped, never
     * partial enumerations) on wide datasets with many few-valued columns.
     */
    private void cellDeterminismCheck() {
        int maxSize = this.config.cellDeterminismMaxSetSize;
        if (maxSize <= 0) return;

        int n = this.dataSet.getNumRows();
        int p = this.dataSet.getNumColumns();

        // Candidate determiners: non-constant variables with few distinct observed values.
        List<Integer> determiners = new ArrayList<>();
        for (int j = 0; j < p; j++) {
            if (this.constant[j]) continue;
            Integer d = this.observedDistinct.get(this.names[j]);
            if (d != null && d >= 2 && d <= this.config.cellDeterminismMaxCardinality) determiners.add(j);
        }
        if (determiners.isEmpty()) return;

        // Pairs already reported as duplicates: skip the corresponding one-element determinisms.
        Set<String> duplicatePairs = new HashSet<>();
        for (AuditFinding f : this.findings) {
            if (f.getCode() == FindingCode.DUPLICATE_COLUMNS && f.getVariables().size() == 2) {
                String a = f.getVariables().get(0), b = f.getVariables().get(1);
                duplicatePairs.add(a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a);
            }
        }

        final double tol = this.config.cellDeterminismTolerance;
        final long budget = 200_000_000L; // total row-scans admitted per set size; see the method Javadoc

        // Minimal determining sets found so far, per target.
        Map<Integer, List<int[]>> foundSets = new HashMap<>();

        for (int size = 1; size <= maxSize; size++) {
            // Deterministic budget rule: admit this size only if enumerating it fully, for every target, fits the
            // budget. (Choose over the determiner pool less the target itself when it is in the pool.)
            long ops = 0;
            for (int t = 0; t < p; t++) {
                if (this.constant[t]) continue;
                int d = determiners.size() - (determiners.contains(t) ? 1 : 0);
                ops += choose(d, size) * (long) n;
                if (ops > budget) break;
            }
            if (ops > budget) break;

            for (int t = 0; t < p; t++) {
                checkInterrupted();
                if (this.constant[t]) continue;

                List<Integer> pool = new ArrayList<>(determiners);
                pool.remove(Integer.valueOf(t));
                if (pool.size() < size) continue;

                List<int[]> found = foundSets.computeIfAbsent(t, k -> new ArrayList<>());

                int[] comb = new int[size];
                for (int i = 0; i < size; i++) comb[i] = i;

                while (true) {
                    int[] set = new int[size];
                    for (int i = 0; i < size; i++) set[i] = pool.get(comb[i]);

                    if (!containsFoundSubset(found, set)
                            && !(size == 1 && duplicatePairs.contains(pairKey(this.names[t], this.names[set[0]])))) {
                        AuditFinding finding = scanCells(t, set, tol);
                        if (finding != null) {
                            this.findings.add(finding);
                            found.add(set);
                        }
                    }

                    // next combination
                    int i = size - 1;
                    while (i >= 0 && comb[i] == pool.size() - size + i) i--;
                    if (i < 0) break;
                    comb[i]++;
                    for (int k = i + 1; k < size; k++) comb[k] = comb[k - 1] + 1;
                }
            }
        }
    }

    /**
     * Scans the target within the joint-value cells of the given determining set and returns a
     * DETERMINISTIC_RELATION finding if the target is constant (within tolerance) in every multi-row cell and the
     * scan is non-vacuous, else null. See {@link #cellDeterminismCheck()} for the criteria.
     */
    private AuditFinding scanCells(int target, int[] set, double tol) {
        int n = this.dataSet.getNumRows();
        boolean discreteTarget = this.discrete[target];

        // Per cell: [count, min, max] for a continuous target; [count, firstCategory, mixedFlag] for a discrete one.
        Map<String, double[]> cells = new HashMap<>();
        int m = 0;
        double sum = 0, sumSq = 0;

        StringBuilder key = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (MissingDataAudit.isMissing(this.dataSet, i, target)) continue;
            boolean ok = true;
            for (int j : set) {
                if (MissingDataAudit.isMissing(this.dataSet, i, j)) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;

            key.setLength(0);
            for (int j : set) {
                if (this.discrete[j]) key.append(this.dataSet.getInt(i, j));
                else key.append(Double.doubleToLongBits(this.dataSet.getDouble(i, j)));
                key.append(',');
            }

            double y = discreteTarget ? this.dataSet.getInt(i, target) : this.dataSet.getDouble(i, target);
            m++;
            sum += y;
            sumSq += y * y;

            double[] cell = cells.get(key.toString());
            if (cell == null) {
                cells.put(key.toString(), new double[]{1, y, y});
            } else {
                cell[0]++;
                if (discreteTarget) {
                    if (y != cell[1]) cell[2] = 1; // mixed
                } else {
                    if (y < cell[1]) cell[1] = y;
                    if (y > cell[2]) cell[2] = y;
                }
            }
        }

        if (m < 20) return null;

        double var = (sumSq - sum * sum / m) / m;
        double sd = var > 0 ? Math.sqrt(var) : 0.0;
        if (sd <= 0) return null; // target constant on the scanned rows; not a determinism of the set

        int multiCells = 0;
        long coveredRows = 0;
        double maxRelRange = 0.0;

        for (double[] cell : cells.values()) {
            if (cell[0] < 2) continue;
            multiCells++;
            coveredRows += (long) cell[0];

            if (discreteTarget) {
                if (cell[2] != 0) return null; // mixed categories in a multi-row cell
            } else {
                double relRange = (cell[2] - cell[1]) / sd;
                if (relRange > tol) return null;
                if (relRange > maxRelRange) maxRelRange = relRange;
            }
        }

        if (multiCells < 5) return null;
        double coverage = coveredRows / (double) m;
        if (coverage < 0.5) return null;

        List<String> vars = new ArrayList<>();
        vars.add(this.names[target]); // determined variable first; see FindingCode.DETERMINISTIC_RELATION
        StringBuilder setNames = new StringBuilder();
        for (int j : set) {
            vars.add(this.names[j]);
            if (setNames.length() > 0) setNames.append(", ");
            setNames.append(this.names[j]);
        }

        Map<String, Double> values = new LinkedHashMap<>();
        values.put("usedRows", (double) m);
        values.put("cells", (double) cells.size());
        values.put("multiRowCells", (double) multiCells);
        values.put("coverage", coverage);
        values.put("tolerance", tol);
        values.put("maxWithinCellRelRange", maxRelRange);

        return new AuditFinding(FindingCode.DETERMINISTIC_RELATION,
                AuditFinding.Severity.WARNING, vars, values,
                "Variable " + this.names[target] + " is constant within every multi-row cell of {" + setNames
                        + "} (" + m + " rows, " + cells.size() + " cells, " + multiCells
                        + " with >= 2 rows covering " + fmt(100 * coverage) + "% of rows): " + this.names[target]
                        + " is a function of these variables on the analyzed rows, up to tolerance.");
    }

    /**
     * Returns true if any already-found determining set is a subset of the candidate set (both sorted ascending).
     */
    private static boolean containsFoundSubset(List<int[]> found, int[] candidate) {
        outer:
        for (int[] f : found) {
            int i = 0;
            for (int c : candidate) {
                if (i < f.length && f[i] == c) i++;
            }
            if (i == f.length) return true;
        }
        return false;
    }

    /**
     * Order-independent key for an unordered pair of variable names.
     */
    private static String pairKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    /**
     * Binomial coefficient, saturating at Long.MAX_VALUE / 2 to keep budget sums overflow-safe.
     */
    private static long choose(int n, int k) {
        if (k < 0 || k > n) return 0;
        long r = 1;
        for (int i = 1; i <= k; i++) {
            r = r * (n - k + i) / i;
            if (r > Long.MAX_VALUE / 2) return Long.MAX_VALUE / 2;
        }
        return r;
    }

    /**
     * Flags continuous variables whose marginal distribution deviates from Gaussian by the Anderson-Darling test, at
     * the configured alpha, using non-missing values. Variables with fewer non-missing values than the configured
     * minimum are skipped. These findings are informational: non-Gaussianity threatens linear-Gaussian machinery but
     * is exploitable by LiNGAM-family methods.
     */
    private void nonGaussianityCheck() {
        int n = this.dataSet.getNumRows();

        for (int j : this.continuousIndices) {
            checkInterrupted();
            List<Double> values = new ArrayList<>();

            for (int i = 0; i < n; i++) {
                if (MissingDataAudit.isMissing(this.dataSet, i, j)) continue;
                values.add(this.dataSet.getDouble(i, j));
            }

            if (values.size() < this.config.minAdSampleSize) continue;
            double[] x = values.stream().mapToDouble(Double::doubleValue).toArray();
            AndersonDarlingTest test = new AndersonDarlingTest(x);
            double p = test.getP();
            String name = this.names[j];
            this.adPValues.put(name, p);

            if (p < this.config.adAlpha) {
                this.findings.add(new AuditFinding(FindingCode.NON_GAUSSIAN,
                        AuditFinding.Severity.INFO, List.of(name),
                        Map.of("aSquaredStar", test.getASquaredStar(), "pValue", p, "alpha", this.config.adAlpha),
                        "Continuous variable " + name + " is non-Gaussian by the Anderson-Darling test (p = "
                                + fmt(p) + ")."));
            }
        }
    }

    /**
     * Flags continuous variables that are serially dependent in file order, i.e., autocorrelated across consecutive
     * rows, which violates the i.i.d.-rows assumption of the usual independence tests and scores. For each continuous
     * variable, autocorrelations at lags 1..{@code serialMaxLag} are computed on the column in file order, and a
     * Ljung-Box test aggregates them into a single p-value against the joint null that all are zero. A variable is
     * flagged when the Ljung-Box p-value falls below {@code serialAlpha} AND the largest absolute autocorrelation is
     * at least {@code serialMinAbsAutocorrelation}; the magnitude condition keeps the check from flagging trivially
     * small dependence at large n. Flagged findings also report an AR(1) effective sample size,
     * n_eff = m (1 - r1) / (1 + r1), when the lag-1 autocorrelation r1 is positive and the dependence looks
     * AR(1)-like, since that communicates severity better than a correlation does; when the dependence instead looks
     * periodic, the message notes that in place of the AR(1) approximation (see design decision (6) below).
     * <p>
     * Design decisions, deliberate and contestable: (1) The check is one-sided with respect to row order. File order
     * is treated as potentially meaningful (time, spatial sequence, batch); a flag establishes that rows are not
     * exchangeable as given, but a clean result does not establish independence under any other ordering (e.g., if
     * the rows were shuffled before saving). (2) If {@code serialGroupVariable} names a discrete variable,
     * autocorrelations are computed within its groups - each group's rows form a subsequence in file order, series
     * are centered at their own group means, lag-k pairs are taken at subsequence distance k, and the per-group sums
     * are pooled before dividing - so that block-level mean shifts and concatenation-boundary jumps (e.g., two
     * regions' data stacked in one file) do not contaminate the estimate. A configured grouping variable that is
     * missing or not discrete is an error rather than a silent fallback, since a pooled estimate is exactly the
     * artifact grouping exists to avoid. (3) Missing values are handled pairwise: means and variances use all
     * observed values; a lag-k product contributes only if both endpoints are observed. The Ljung-Box chi-square
     * reference is therefore approximate under missingness or grouping, which the Javadoc of
     * {@link #getSerialDependencePValues()} also notes. (4) Variables whose within-group variance is negligible
     * relative to their scale are skipped: a variable that is constant within every group (e.g., a town-level
     * attribute grouped by town) centers to exact or near-exact zeros, and in the near-exact case (non-representable
     * decimals) the surviving c0 is rounding residue from which autocorrelations would be pure floating-point noise.
     * (5) Discrete variables are not checked in this version; a
     * lag-1 Cramer's V or runs test could be added later under the same finding code. (6) When the largest absolute
     * autocorrelation occurs at a lag greater than 1 while |r1| is below {@code serialMinAbsAutocorrelation}, the
     * message notes possible periodic dependence at that lag (e.g., a block or seasonal design) in place of the
     * AR(1) effective-sample-size clause: the AR(1) approximation is computed from r1 alone, so under periodic
     * dependence with a small r1 it reports nearly the full sample size and would falsely reassure. The finding then
     * also carries {@code periodicSuspect = 1} in its values, and {@code effectiveSampleSize} is still recorded there
     * whenever r1 is in (0, 1), so nothing is withheld from downstream consumers; only the message changes. This case
     * was observed on task fMRI data thinned to every kth row, where r1 fell to ~0.02 but a stimulus-locked
     * autocorrelation of ~0.7 remained at the task period.
     */
    private void serialDependenceCheck() {
        int n = this.dataSet.getNumRows();
        int maxLag = this.config.serialMaxLag;
        if (maxLag < 1 || this.continuousIndices.length == 0) return;

        int groupCol = -1;

        if (this.config.serialGroupVariable != null) {
            for (int j = 0; j < this.names.length; j++) {
                if (this.names[j].equals(this.config.serialGroupVariable)) {
                    groupCol = j;
                    break;
                }
            }

            if (groupCol == -1) {
                throw new IllegalArgumentException("Serial grouping variable '" + this.config.serialGroupVariable
                        + "' is not a variable in the dataset.");
            }

            if (!this.discrete[groupCol]) {
                throw new IllegalArgumentException("Serial grouping variable '" + this.config.serialGroupVariable
                        + "' must be discrete.");
            }
        }

        for (int j : this.continuousIndices) {
            checkInterrupted();
            // Partition rows into group subsequences in file order (one group if no grouping variable). Rows where
            // the grouping variable is missing are excluded, since their group is unknown.
            Map<Integer, List<Integer>> groupRows = new LinkedHashMap<>();

            for (int i = 0; i < n; i++) {
                if (groupCol >= 0 && MissingDataAudit.isMissing(this.dataSet, i, groupCol)) continue;
                int g = groupCol >= 0 ? this.dataSet.getInt(i, groupCol) : 0;
                groupRows.computeIfAbsent(g, k -> new ArrayList<>()).add(i);
            }

            // Pooled, per-group-centered autocovariances. c0 uses all observed values; ck uses pairs at subsequence
            // distance k with both endpoints observed. sumSqRaw (the uncentered second moment) supports a relative
            // guard below: a variable that is constant within every group centers to exact or near-exact zeros, and
            // with non-representable decimal values the near-exact case leaves rounding residue of relative order
            // eps^2 ~ 1e-32 in c0, from which autocorrelations would be pure floating-point noise. Such variables
            // are skipped, as they would be under an exact-zero c0.
            int m = 0;
            double c0 = 0;
            double sumSqRaw = 0;
            double[] ck = new double[maxLag + 1];
            List<double[]> centered = new ArrayList<>(); // per group: centered series with NaN where missing

            for (List<Integer> rows : groupRows.values()) {
                double sum = 0;
                int obs = 0;

                for (int i : rows) {
                    if (MissingDataAudit.isMissing(this.dataSet, i, j)) continue;
                    double x = this.dataSet.getDouble(i, j);
                    sum += x;
                    sumSqRaw += x * x;
                    obs++;
                }

                if (obs < 2) continue;
                double mean = sum / obs;
                double[] series = new double[rows.size()];

                for (int t = 0; t < rows.size(); t++) {
                    int i = rows.get(t);
                    series[t] = MissingDataAudit.isMissing(this.dataSet, i, j)
                            ? Double.NaN : this.dataSet.getDouble(i, j) - mean;
                }

                centered.add(series);
                m += obs;

                for (double x : series) {
                    if (!Double.isNaN(x)) c0 += x * x;
                }
            }

            // Relative guard: skip when the within-group variance is negligible relative to the variable's scale
            // (rounding residue is of relative order ~1e-32; genuine variation in real data is many orders larger).
            if (m < this.config.minSerialSampleSize || m <= maxLag + 2
                    || c0 <= 0 || c0 <= 1e-18 * sumSqRaw) continue;

            for (double[] series : centered) {
                for (int k = 1; k <= maxLag; k++) {
                    for (int t = 0; t + k < series.length; t++) {
                        if (Double.isNaN(series[t]) || Double.isNaN(series[t + k])) continue;
                        ck[k] += series[t] * series[t + k];
                    }
                }
            }

            double q = 0;
            double r1 = ck[1] / c0;
            double maxAbs = 0;
            int maxAbsLag = 1;

            for (int k = 1; k <= maxLag; k++) {
                double rk = ck[k] / c0;
                q += rk * rk / (m - k);

                if (Math.abs(rk) > maxAbs) {
                    maxAbs = Math.abs(rk);
                    maxAbsLag = k;
                }
            }

            q *= m * (m + 2.0);
            double p = Math.min(1.0, Math.max(0.0, 1.0 - edu.cmu.tetrad.util.ProbUtils.chisqCdf(q, maxLag)));

            String name = this.names[j];
            this.lag1Autocorrelations.put(name, r1);
            this.ljungBoxPValues.put(name, p);

            if (p < this.config.serialAlpha && maxAbs >= this.config.serialMinAbsAutocorrelation) {
                Map<String, Double> values = new LinkedHashMap<>();
                values.put("lag1Autocorrelation", r1);
                values.put("maxAbsAutocorrelation", maxAbs);
                values.put("maxAbsLag", (double) maxAbsLag);
                values.put("ljungBoxQ", q);
                values.put("ljungBoxP", p);
                values.put("maxLag", (double) maxLag);
                values.put("numObserved", (double) m);
                values.put("alpha", this.config.serialAlpha);
                values.put("minAbsThreshold", this.config.serialMinAbsAutocorrelation);

                boolean periodicSuspect = maxAbsLag > 1
                        && Math.abs(r1) < this.config.serialMinAbsAutocorrelation;

                if (periodicSuspect) {
                    values.put("periodicSuspect", 1.0);
                }

                String effNote = "";

                if (r1 > 0 && r1 < 1) {
                    double nEff = m * (1 - r1) / (1 + r1);
                    values.put("effectiveSampleSize", nEff);

                    if (!periodicSuspect) {
                        effNote = "; under an AR(1) approximation n = " + m + " behaves like n ~ "
                                + Math.round(nEff);
                    }
                }

                if (periodicSuspect) {
                    effNote = "; the dominant autocorrelation is at lag " + maxAbsLag + " while |r1| = "
                            + fmt(Math.abs(r1)) + " is small, consistent with periodic dependence (e.g., a block"
                            + " or seasonal design); the AR(1) effective-sample-size approximation is not"
                            + " informative here";
                }

                String groupNote = groupCol >= 0 ? ", within groups of " + this.names[groupCol] : "";
                this.findings.add(new AuditFinding(FindingCode.SERIAL_DEPENDENCE,
                        AuditFinding.Severity.WARNING, List.of(name), values,
                        "Continuous variable " + name + " is serially dependent in file order (r1 = " + fmt(r1)
                                + ", max |r| = " + fmt(maxAbs) + " at lag " + maxAbsLag + ", Ljung-Box p = "
                                + fmt(p) + " over " + maxLag + " lags" + groupNote
                                + "); rows may not be i.i.d." + effNote + "."));
            }
        }
    }

    /**
     * Flags a small ratio of sample size to number of variables.
     */
    private void sampleRatioCheck() {
        double ratio = this.dataSet.getNumRows() / (double) this.dataSet.getNumColumns();

        if (ratio < this.config.lowSampleRatio) {
            this.findings.add(new AuditFinding(FindingCode.LOW_SAMPLE_RATIO,
                    AuditFinding.Severity.WARNING, List.of(),
                    Map.of("ratio", ratio, "numRows", (double) this.dataSet.getNumRows(),
                            "numVariables", (double) this.dataSet.getNumColumns(),
                            "threshold", this.config.lowSampleRatio),
                    "Sample size / variable ratio is " + fmt(ratio) + "."));
        }
    }

    /**
     * If the dataset has missing values, emits a summary finding; details are available from
     * {@link #getMissingDataAudit()}. Little's MCAR test is attempted when there are at least two continuous
     * variables (it is built on the EM covariance estimator); failures to run it are tolerated silently, since the
     * summary statistics stand on their own.
     */
    private void missingnessCheck() {
        if (this.missingDataAudit == null) return;

        Map<String, Double> values = new LinkedHashMap<>();
        values.put("overallMissingRate", this.missingDataAudit.getOverallMissingRate());
        values.put("numCompleteRows", (double) this.missingDataAudit.getNumCompleteRows());
        values.put("numPatterns", (double) this.missingDataAudit.getNumPatterns());
        values.put("minPairwiseCount", (double) this.missingDataAudit.getMinPairwiseCount());

        String littleNote = "";

        if (this.continuousNames.size() >= 2) {
            try {
                MissingDataAudit.LittleResult little = this.missingDataAudit.littlesMcarTest();
                values.put("littlesMcarP", little.pValue);
                littleNote = " Little's MCAR test p = " + fmt(little.pValue) + ".";
            } catch (Exception e) {
                // Tolerated; the summary statistics stand on their own.
            }
        }

        this.findings.add(new AuditFinding(FindingCode.MISSING_DATA,
                AuditFinding.Severity.INFO, List.of(), values,
                "Dataset has missing values (overall rate "
                        + fmt(100 * this.missingDataAudit.getOverallMissingRate()) + "%, "
                        + this.missingDataAudit.getNumCompleteRows() + " complete rows, "
                        + this.missingDataAudit.getNumPatterns() + " patterns)." + littleNote));
    }

    //==================================== HELPERS ====================================//

    /**
     * Flags pairs of columns that are exact affine functions of one another on the rows where both are observed:
     * identical columns, complementary indicators (y = 1 - x), or rescaled copies. This applies to discrete and
     * continuous columns alike (discrete columns are compared through their integer category codes), requires at
     * least five jointly observed rows and both columns nonconstant on them, and reports the overlap count, since
     * an identity established on few rows is weaker evidence of redundancy than one established on many. Unlike the
     * rank-based EXACT_LINEAR_DEPENDENCE finding, which cannot localize a deficiency, each finding here names a
     * specific pair; these are the exact dependencies that survive any choice of row subset containing the overlap.
     */
    private void duplicateColumnChecks() {
        int p = this.dataSet.getNumColumns();
        int n = this.dataSet.getNumRows();

        for (int a = 0; a < p; a++) {
            checkInterrupted();
            if (this.constant[a]) continue;

            for (int b = a + 1; b < p; b++) {
                if (this.constant[b]) continue;

                double sa = 0, sb = 0, saa = 0, sbb = 0, sab = 0;
                int m = 0;

                for (int i = 0; i < n; i++) {
                    if (MissingDataAudit.isMissing(this.dataSet, i, a)
                            || MissingDataAudit.isMissing(this.dataSet, i, b)) continue;
                    double x = this.discrete[a] ? this.dataSet.getInt(i, a) : this.dataSet.getDouble(i, a);
                    double y = this.discrete[b] ? this.dataSet.getInt(i, b) : this.dataSet.getDouble(i, b);
                    sa += x;
                    sb += y;
                    saa += x * x;
                    sbb += y * y;
                    sab += x * y;
                    m++;
                }

                if (m < 5) continue;
                double va = saa - sa * sa / m;
                double vb = sbb - sb * sb / m;
                if (va <= 0 || vb <= 0) continue;

                double r = (sab - sa * sb / m) / Math.sqrt(va * vb);

                if (Math.abs(r) >= 1.0 - 1e-12) {
                    this.findings.add(new AuditFinding(FindingCode.DUPLICATE_COLUMNS,
                            AuditFinding.Severity.WARNING, List.of(this.names[a], this.names[b]),
                            Map.of("correlation", r, "nOverlap", (double) m),
                            "Columns " + this.names[a] + " and " + this.names[b] + " are exact "
                                    + (r > 0 ? "" : "sign-reversed ") + "affine copies of one another on all "
                                    + m + " jointly observed rows; on that overlap, one carries no information "
                                    + "the other does not."));
                }
            }
        }
    }

    /**
     * If a serial grouping variable is configured, flags every other variable that is constant within each level of
     * it (e.g., subject-level attributes in a repeated-measures file). The effective sample size of such a variable
     * for correlational judgments is the number of groups, not the number of rows, and when the number of groups is
     * small, group-constant variables are frequently exactly collinear with one another by accident of the group
     * sample. No finding is emitted when no grouping variable is configured, since group structure cannot be
     * inferred from the data alone.
     */
    private void groupConstantCheck() {
        if (this.config.serialGroupVariable == null) return;

        int groupCol = -1;

        for (int j = 0; j < this.names.length; j++) {
            if (this.names[j].equals(this.config.serialGroupVariable)) {
                groupCol = j;
                break;
            }
        }

        if (groupCol < 0 || !this.discrete[groupCol]) return;

        int n = this.dataSet.getNumRows();
        Set<Integer> groups = new HashSet<>();

        for (int i = 0; i < n; i++) {
            if (!MissingDataAudit.isMissing(this.dataSet, i, groupCol)) {
                groups.add(this.dataSet.getInt(i, groupCol));
            }
        }

        int numGroups = groups.size();
        if (numGroups < 2 || numGroups >= n) return;

        for (int j = 0; j < this.names.length; j++) {
            if (j == groupCol || this.constant[j]) continue;

            Map<Integer, Double> firstValue = new LinkedHashMap<>();
            boolean groupConstant = true;

            K:
            for (int i = 0; i < n; i++) {
                if (MissingDataAudit.isMissing(this.dataSet, i, groupCol)
                        || MissingDataAudit.isMissing(this.dataSet, i, j)) continue;
                int g = this.dataSet.getInt(i, groupCol);
                double v = this.discrete[j] ? this.dataSet.getInt(i, j) : this.dataSet.getDouble(i, j);
                Double seen = firstValue.get(g);

                if (seen == null) {
                    firstValue.put(g, v);
                } else if (seen != v) {
                    groupConstant = false;
                    break K;
                }
            }

            if (groupConstant && firstValue.size() >= 2) {
                this.findings.add(new AuditFinding(FindingCode.GROUP_CONSTANT_VARIABLE,
                        AuditFinding.Severity.WARNING, List.of(this.names[j]),
                        Map.of("numGroups", (double) numGroups),
                        "Variable " + this.names[j] + " is constant within every level of "
                                + this.config.serialGroupVariable + "; its effective sample size for "
                                + "correlational judgments is the number of groups (" + numGroups + "), not the "
                                + "number of rows (" + n + ")."));
            }
        }
    }

    /**
     * Computes the Pearson correlation between two continuous columns over rows where both are observed, or NaN if
     * fewer than three such rows or either column is constant on them.
     *
     * @param ja the first column index.
     * @param jb the second column index.
     * @return the correlation or NaN.
     */
    private double pairwiseCorrelation(int ja, int jb) {
        int n = this.dataSet.getNumRows();
        double sa = 0, sb = 0, saa = 0, sbb = 0, sab = 0;
        int m = 0;

        for (int i = 0; i < n; i++) {
            if (MissingDataAudit.isMissing(this.dataSet, i, ja)
                    || MissingDataAudit.isMissing(this.dataSet, i, jb)) continue;
            double x = this.dataSet.getDouble(i, ja);
            double y = this.dataSet.getDouble(i, jb);
            sa += x;
            sb += y;
            saa += x * x;
            sbb += y * y;
            sab += x * y;
            m++;
        }

        if (m < 3) return Double.NaN;
        double va = saa - sa * sa / m;
        double vb = sbb - sb * sb / m;
        if (va <= 0 || vb <= 0) return Double.NaN;
        return (sab - sa * sb / m) / Math.sqrt(va * vb);
    }

    /**
     * Counts the rows on which both of the given columns are observed.
     *
     * @param ja the first column index.
     * @param jb the second column index.
     * @return the count.
     */
    private int pairwiseCompleteCount(int ja, int jb) {
        int n = this.dataSet.getNumRows();
        int m = 0;

        for (int i = 0; i < n; i++) {
            if (MissingDataAudit.isMissing(this.dataSet, i, ja)
                    || MissingDataAudit.isMissing(this.dataSet, i, jb)) continue;
            m++;
        }

        return m;
    }

    /**
     * Fraction of the full R^2 that a greedily chosen predictor subset must reach for the subset to be reported as
     * accounting for the dependence in NEAR_DETERMINISM_LINEAR messages.
     */
    public static final double EXPLAINING_SUBSET_FRACTION = 0.99;

    /**
     * Maximum number of predictors the greedy explaining-subset search will select.
     */
    public static final int EXPLAINING_SUBSET_MAX_SIZE = 5;

    /**
     * Greedy forward selection on a correlation matrix, used to localize a NEAR_DETERMINISM_LINEAR finding: for
     * column a, predictors are added one at a time, each step choosing the column that most increases the R^2 of a
     * on the selected set, stopping when the subset R^2 reaches {@link #EXPLAINING_SUBSET_FRACTION} of fullR2 or the
     * set reaches {@link #EXPLAINING_SUBSET_MAX_SIZE} predictors. The achieved subset R^2 is written to
     * achievedR2[0], clamped to [0, 1].
     * <p>
     * With near-collinear predictors the selected set is not unique: if a is nearly duplicated by each of two
     * predictors, which one is selected first is an arbitrary consequence of tie-breaking. The result is one small
     * explaining set, not an attribution of cause. On a pairwise-complete matrix that is not positive semidefinite,
     * subset R^2 values describe the assembled matrix, not any single sample. Candidates whose submatrix cannot be
     * inverted are skipped.
     *
     * @param corr       the correlation matrix.
     * @param a          the column whose linear dependence on the others is being localized.
     * @param fullR2     the R^2 of column a on all other columns (from the VIF diagonal).
     * @param achievedR2 a length-1 array receiving the R^2 of a on the returned subset.
     * @return the selected column indices in order of selection; empty if no candidate increases R^2.
     */
    public static List<Integer> explainingSubset(Matrix corr, int a, double fullR2, double[] achievedR2) {
        int p = corr.getNumRows();
        List<Integer> selected = new ArrayList<>();
        double bestR2 = 0.0;
        achievedR2[0] = 0.0;

        while (selected.size() < EXPLAINING_SUBSET_MAX_SIZE && bestR2 < EXPLAINING_SUBSET_FRACTION * fullR2) {
            int bestCandidate = -1;
            double bestCandidateR2 = bestR2;

            for (int b = 0; b < p; b++) {
                if (b == a || selected.contains(b)) continue;

                int s = selected.size() + 1;
                Matrix rss = new Matrix(s, s);
                double[] ras = new double[s];

                for (int i = 0; i < s; i++) {
                    int vi = i < s - 1 ? selected.get(i) : b;
                    ras[i] = corr.get(a, vi);

                    for (int j = 0; j < s; j++) {
                        int vj = j < s - 1 ? selected.get(j) : b;
                        rss.set(i, j, corr.get(vi, vj));
                    }
                }

                double r2;

                try {
                    Matrix inv = rss.inverse();
                    r2 = 0.0;

                    for (int i = 0; i < s; i++) {
                        for (int j = 0; j < s; j++) {
                            r2 += ras[i] * inv.get(i, j) * ras[j];
                        }
                    }
                } catch (Exception e) {
                    continue; // Singular or near-singular submatrix; skip this candidate.
                }

                r2 = Math.min(1.0, Math.max(0.0, r2));

                if (r2 > bestCandidateR2 + 1e-12) {
                    bestCandidateR2 = r2;
                    bestCandidate = b;
                }
            }

            if (bestCandidate < 0) break; // No candidate improves R^2.

            selected.add(bestCandidate);
            bestR2 = bestCandidateR2;
        }

        achievedR2[0] = bestR2;
        return selected;
    }

    /**
     * Formats a double to four significant-ish decimal places for messages.
     *
     * @param x the value.
     * @return the formatted value.
     */
    private static String fmt(double x) {
        return String.format("%.4g", x);
    }

    /**
     * Renders a double as a JSON number, mapping non-finite values to null.
     *
     * @param x the value.
     * @return the JSON token.
     */
    private static String jsonNumber(Double x) {
        return (x == null || x.isNaN() || x.isInfinite()) ? "null" : x.toString();
    }

    /**
     * Escapes a string for embedding in JSON.
     *
     * @param s the string.
     * @return the escaped string.
     */
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();

        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }

        return sb.toString();
    }

    //==================================== CONFIG ====================================//

    /**
     * Thresholds for the audit's checks, with defaults. Instances are immutable; use the with-methods to derive
     * variants. The defaults are rules of thumb, documented per field; they are deliberately conservative, in the
     * sense that they flag situations most practitioners would want to know about.
     */
    public static final class Config {

        /**
         * A continuous variable with at most this many distinct observed values is flagged. Default 5.
         */
        private final int fewContinuousValues;

        /**
         * A discrete variable with at least this many observed levels is flagged. Default 10.
         */
        private final int manyDiscreteLevels;

        /**
         * A discrete category with fewer than this many observed cases is a small marginal cell. Default 10.
         */
        private final int smallCellCount;

        /**
         * A discrete pair with a minimum expected cell count under independence below this is flagged. Default 5 (the
         * classical chi-square rule of thumb).
         */
        private final double minExpectedPairwiseCell;

        /**
         * A continuous pair with absolute correlation at or above this is flagged. Default 0.9.
         */
        private final double highCorrelation;

        /**
         * A continuous variable with multiple R-squared on the other continuous variables at or above this is
         * flagged. Default 0.98.
         */
        private final double r2Determinism;

        /**
         * A (discrete, continuous) pair with eta-squared at or above this is flagged. Default 0.95.
         */
        private final double etaSquaredDeterminism;

        /**
         * Alpha for the Anderson-Darling non-Gaussianity flag. Default 0.01.
         */
        private final double adAlpha;

        /**
         * Minimum number of non-missing values for the Anderson-Darling test to run on a column. Default 20.
         */
        private final int minAdSampleSize;

        /**
         * A sample-size-to-variables ratio below this is flagged. Default 5.
         */
        private final double lowSampleRatio;

        /**
         * A discrete variable with modal category frequency at or above this is near-constant. Default 0.99.
         */
        private final double nearConstantFrequency;

        /**
         * A continuous variable with variance at or below this is near-constant. Default 1e-12.
         */
        private final double nearConstantVariance;

        /**
         * The number of lags over which serial dependence is tested (Ljung-Box). Default 5. Setting this to 0
         * disables the serial dependence check.
         */
        private final int serialMaxLag;

        /**
         * Alpha for the Ljung-Box serial dependence flag. Default 0.01.
         */
        private final double serialAlpha;

        /**
         * Minimum largest absolute autocorrelation (over the tested lags) for the serial dependence flag; keeps the
         * check from flagging trivially small dependence at large n. Default 0.2.
         */
        private final double serialMinAbsAutocorrelation;

        /**
         * Minimum number of observed values for the serial dependence check to run on a column. Default 20.
         */
        private final int minSerialSampleSize;

        /**
         * The name of a discrete variable defining groups within which autocorrelations are computed (per-group
         * centering, cross-boundary pairs excluded, pooled), or null to treat the file as one sequence. Naming a
         * variable that is absent or not discrete is an error, not a silent fallback. Default null.
         */
        private final String serialGroupVariable;

        /**
         * The largest determining-set size searched by the cell-determinism check (DETERMINISTIC_RELATION); 0
         * disables the check. Default 3.
         */
        private final int cellDeterminismMaxSetSize;

        /**
         * The largest number of distinct observed values a variable may have to serve as a member of a determining
         * set in the cell-determinism check. Default 30.
         */
        private final int cellDeterminismMaxCardinality;

        /**
         * The tolerance of the cell-determinism check for a continuous target: a cell counts as constant when its
         * range is at most this fraction of the target's standard deviation on the scanned rows. Default 1e-9
         * (essentially exact, while immune to floating-point noise).
         */
        private final double cellDeterminismTolerance;

        /**
         * A continuous variable whose leave-one-out cross-validated spline-regression R-squared on a small set of
         * other continuous variables is at or above this is flagged NEAR_DETERMINISM_NONLINEAR. Default 0.95. The
         * threshold is deliberately below the linear r2Determinism default: the statistic is out-of-sample, and
         * real derived columns (hand-entered fire-weather indices, for instance) carry entry noise that caps even a
         * perfect regressor below the linear check's calibration.
         */
        private final double nonlinearR2Determinism;

        /**
         * The largest determining-set size searched by the nonlinear near-determinism check
         * (NEAR_DETERMINISM_NONLINEAR); 0 disables the check. Default 3.
         */
        private final int nonlinearDeterminismMaxSetSize;

        /**
         * The minimum number of cells at an extreme value for SENTINEL_VALUE to fire there. Default 3. An absolute
         * floor, so that a single extreme observation is never flagged however small the sample.
         */
        private final int sentinelMinCount;

        /**
         * The minimum share of a column's observed values that must sit at an extreme value for SENTINEL_VALUE to
         * fire there. Default 0.005. A relative floor, which binds at large n where the count floor does not.
         */
        private final double sentinelMinMass;

        /**
         * The minimum ratio of the gap between the candidate extreme value and its nearest observed neighbor to the
         * median spacing between adjacent distinct values among the rest of the column, for SENTINEL_VALUE to fire.
         * Default 3.0. This is the condition that separates a code from a legitimate floor or ceiling.
         */
        private final double sentinelGapRatio;

        /**
         * The minimum number of distinct observed values for the SENTINEL_VALUE check to run on a column. Default 5.
         * At least three are needed for the spacing reference to be computable at all.
         */
        private final int sentinelMinDistinct;

        /**
         * Constructs a config with default thresholds.
         */
        public Config() {
            this(5, 10, 10, 5.0, 0.9, 0.98, 0.95, 0.01, 20, 5.0, 0.99, 1e-12, 5, 0.01, 0.2, 20, null);
        }

        /**
         * Constructs a config with the given thresholds. See the field documentation for meanings.
         *
         * @param fewContinuousValues     threshold for CONTINUOUS_FEW_VALUES.
         * @param manyDiscreteLevels      threshold for DISCRETE_MANY_LEVELS.
         * @param smallCellCount          threshold for SMALL_MARGINAL_CELL.
         * @param minExpectedPairwiseCell threshold for SMALL_PAIRWISE_CELLS.
         * @param highCorrelation         threshold for HIGH_CORRELATION.
         * @param r2Determinism           threshold for NEAR_DETERMINISM_LINEAR.
         * @param etaSquaredDeterminism   threshold for NEAR_DETERMINISM_DISCRETE_CONTINUOUS.
         * @param adAlpha                 alpha for NON_GAUSSIAN.
         * @param minAdSampleSize         minimum column n for the Anderson-Darling test.
         * @param lowSampleRatio          threshold for LOW_SAMPLE_RATIO.
         * @param nearConstantFrequency   modal-frequency threshold for discrete NEAR_CONSTANT.
         * @param nearConstantVariance    variance threshold for continuous NEAR_CONSTANT.
         */
        public Config(int fewContinuousValues, int manyDiscreteLevels, int smallCellCount,
                      double minExpectedPairwiseCell, double highCorrelation, double r2Determinism,
                      double etaSquaredDeterminism, double adAlpha, int minAdSampleSize,
                      double lowSampleRatio, double nearConstantFrequency, double nearConstantVariance) {
            this(fewContinuousValues, manyDiscreteLevels, smallCellCount, minExpectedPairwiseCell, highCorrelation,
                    r2Determinism, etaSquaredDeterminism, adAlpha, minAdSampleSize, lowSampleRatio,
                    nearConstantFrequency, nearConstantVariance, 5, 0.01, 0.2, 20, null);
        }

        /**
         * Constructs a config with the given thresholds, including the serial dependence settings. See the field
         * documentation for meanings.
         *
         * @param fewContinuousValues         threshold for CONTINUOUS_FEW_VALUES.
         * @param manyDiscreteLevels          threshold for DISCRETE_MANY_LEVELS.
         * @param smallCellCount              threshold for SMALL_MARGINAL_CELL.
         * @param minExpectedPairwiseCell     threshold for SMALL_PAIRWISE_CELLS.
         * @param highCorrelation             threshold for HIGH_CORRELATION.
         * @param r2Determinism               threshold for NEAR_DETERMINISM_LINEAR.
         * @param etaSquaredDeterminism       threshold for NEAR_DETERMINISM_DISCRETE_CONTINUOUS.
         * @param adAlpha                     alpha for NON_GAUSSIAN.
         * @param minAdSampleSize             minimum column n for the Anderson-Darling test.
         * @param lowSampleRatio              threshold for LOW_SAMPLE_RATIO.
         * @param nearConstantFrequency       modal-frequency threshold for discrete NEAR_CONSTANT.
         * @param nearConstantVariance        variance threshold for continuous NEAR_CONSTANT.
         * @param serialMaxLag                number of lags for SERIAL_DEPENDENCE (0 disables the check).
         * @param serialAlpha                 Ljung-Box alpha for SERIAL_DEPENDENCE.
         * @param serialMinAbsAutocorrelation minimum largest absolute autocorrelation for SERIAL_DEPENDENCE.
         * @param minSerialSampleSize         minimum column n for the serial dependence check.
         * @param serialGroupVariable         name of a discrete grouping variable for within-group autocorrelations,
         *                                    or null.
         */
        public Config(int fewContinuousValues, int manyDiscreteLevels, int smallCellCount,
                      double minExpectedPairwiseCell, double highCorrelation, double r2Determinism,
                      double etaSquaredDeterminism, double adAlpha, int minAdSampleSize,
                      double lowSampleRatio, double nearConstantFrequency, double nearConstantVariance,
                      int serialMaxLag, double serialAlpha, double serialMinAbsAutocorrelation,
                      int minSerialSampleSize, String serialGroupVariable) {
            this(fewContinuousValues, manyDiscreteLevels, smallCellCount, minExpectedPairwiseCell, highCorrelation,
                    r2Determinism, etaSquaredDeterminism, adAlpha, minAdSampleSize, lowSampleRatio,
                    nearConstantFrequency, nearConstantVariance, serialMaxLag, serialAlpha,
                    serialMinAbsAutocorrelation, minSerialSampleSize, serialGroupVariable, 3, 30, 1e-9,
                    0.95, 3, 3, 0.005, 3.0, 5);
        }

        /**
         * The full constructor, including the cell-determinism settings; private so the public constructors keep
         * their existing signatures while the with-methods can preserve every field.
         */
        private Config(int fewContinuousValues, int manyDiscreteLevels, int smallCellCount,
                       double minExpectedPairwiseCell, double highCorrelation, double r2Determinism,
                       double etaSquaredDeterminism, double adAlpha, int minAdSampleSize,
                       double lowSampleRatio, double nearConstantFrequency, double nearConstantVariance,
                       int serialMaxLag, double serialAlpha, double serialMinAbsAutocorrelation,
                       int minSerialSampleSize, String serialGroupVariable,
                       int cellDeterminismMaxSetSize, int cellDeterminismMaxCardinality,
                       double cellDeterminismTolerance,
                       double nonlinearR2Determinism, int nonlinearDeterminismMaxSetSize,
                       int sentinelMinCount, double sentinelMinMass,
                       double sentinelGapRatio, int sentinelMinDistinct) {
            this.fewContinuousValues = fewContinuousValues;
            this.manyDiscreteLevels = manyDiscreteLevels;
            this.smallCellCount = smallCellCount;
            this.minExpectedPairwiseCell = minExpectedPairwiseCell;
            this.highCorrelation = highCorrelation;
            this.r2Determinism = r2Determinism;
            this.etaSquaredDeterminism = etaSquaredDeterminism;
            this.adAlpha = adAlpha;
            this.minAdSampleSize = minAdSampleSize;
            this.lowSampleRatio = lowSampleRatio;
            this.nearConstantFrequency = nearConstantFrequency;
            this.nearConstantVariance = nearConstantVariance;
            this.serialMaxLag = serialMaxLag;
            this.serialAlpha = serialAlpha;
            this.serialMinAbsAutocorrelation = serialMinAbsAutocorrelation;
            this.minSerialSampleSize = minSerialSampleSize;
            this.serialGroupVariable = serialGroupVariable;
            this.cellDeterminismMaxSetSize = cellDeterminismMaxSetSize;
            this.cellDeterminismMaxCardinality = cellDeterminismMaxCardinality;
            this.cellDeterminismTolerance = cellDeterminismTolerance;
            this.nonlinearR2Determinism = nonlinearR2Determinism;
            this.nonlinearDeterminismMaxSetSize = nonlinearDeterminismMaxSetSize;
            this.sentinelMinCount = sentinelMinCount;
            this.sentinelMinMass = sentinelMinMass;
            this.sentinelGapRatio = sentinelGapRatio;
            this.sentinelMinDistinct = sentinelMinDistinct;
        }

        /**
         * Returns a config identical to this one but with the given high-correlation threshold.
         *
         * @param highCorrelation the new threshold.
         * @return the new config.
         */
        public Config withHighCorrelation(double highCorrelation) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount, this.minExpectedPairwiseCell, highCorrelation, this.r2Determinism, this.etaSquaredDeterminism, this.adAlpha, this.minAdSampleSize,
                    this.lowSampleRatio, this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, this.serialAlpha, this.serialMinAbsAutocorrelation, this.minSerialSampleSize, this.serialGroupVariable,
                    this.cellDeterminismMaxSetSize, this.cellDeterminismMaxCardinality, this.cellDeterminismTolerance,
                    this.nonlinearR2Determinism, this.nonlinearDeterminismMaxSetSize,
                    this.sentinelMinCount, this.sentinelMinMass, this.sentinelGapRatio,
                    this.sentinelMinDistinct);
        }

        /**
         * Returns a config identical to this one but with the given small-cell count threshold.
         *
         * @param smallCellCount the new threshold.
         * @return the new config.
         */
        public Config withSmallCellCount(int smallCellCount) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, smallCellCount, this.minExpectedPairwiseCell, this.highCorrelation, this.r2Determinism, this.etaSquaredDeterminism, this.adAlpha, this.minAdSampleSize,
                    this.lowSampleRatio, this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, this.serialAlpha, this.serialMinAbsAutocorrelation, this.minSerialSampleSize, this.serialGroupVariable,
                    this.cellDeterminismMaxSetSize, this.cellDeterminismMaxCardinality, this.cellDeterminismTolerance,
                    this.nonlinearR2Determinism, this.nonlinearDeterminismMaxSetSize,
                    this.sentinelMinCount, this.sentinelMinMass, this.sentinelGapRatio,
                    this.sentinelMinDistinct);
        }

        /**
         * Returns a config identical to this one but with the given Anderson-Darling alpha.
         *
         * @param adAlpha the new alpha.
         * @return the new config.
         */
        public Config withAdAlpha(double adAlpha) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount, this.minExpectedPairwiseCell, this.highCorrelation, this.r2Determinism, this.etaSquaredDeterminism, adAlpha, this.minAdSampleSize,
                    this.lowSampleRatio, this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, this.serialAlpha, this.serialMinAbsAutocorrelation, this.minSerialSampleSize, this.serialGroupVariable,
                    this.cellDeterminismMaxSetSize, this.cellDeterminismMaxCardinality, this.cellDeterminismTolerance,
                    this.nonlinearR2Determinism, this.nonlinearDeterminismMaxSetSize,
                    this.sentinelMinCount, this.sentinelMinMass, this.sentinelGapRatio,
                    this.sentinelMinDistinct);
        }

        /**
         * Returns a config identical to this one but with the given serial dependence grouping variable.
         *
         * @param serialGroupVariable the name of a discrete grouping variable, or null for no grouping.
         * @return the new config.
         */
        public Config withSerialGroupVariable(String serialGroupVariable) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount, this.minExpectedPairwiseCell, this.highCorrelation, this.r2Determinism, this.etaSquaredDeterminism, this.adAlpha, this.minAdSampleSize,
                    this.lowSampleRatio, this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, this.serialAlpha, this.serialMinAbsAutocorrelation, this.minSerialSampleSize, serialGroupVariable,
                    this.cellDeterminismMaxSetSize, this.cellDeterminismMaxCardinality, this.cellDeterminismTolerance,
                    this.nonlinearR2Determinism, this.nonlinearDeterminismMaxSetSize,
                    this.sentinelMinCount, this.sentinelMinMass, this.sentinelGapRatio,
                    this.sentinelMinDistinct);
        }

        /**
         * Returns a config identical to this one but with the given Ljung-Box alpha for serial dependence.
         *
         * @param serialAlpha the new alpha.
         * @return the new config.
         */
        /**
         * Returns a config identical to this one but with the given maximum determining-set size for the
         * cell-determinism check (0 disables the check).
         *
         * @param cellDeterminismMaxSetSize the new maximum set size.
         * @return the new config.
         */
        public Config withCellDeterminismMaxSetSize(int cellDeterminismMaxSetSize) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount,
                    this.minExpectedPairwiseCell, this.highCorrelation, this.r2Determinism,
                    this.etaSquaredDeterminism, this.adAlpha, this.minAdSampleSize, this.lowSampleRatio,
                    this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, this.serialAlpha,
                    this.serialMinAbsAutocorrelation, this.minSerialSampleSize, this.serialGroupVariable,
                    cellDeterminismMaxSetSize, this.cellDeterminismMaxCardinality, this.cellDeterminismTolerance,
                    this.nonlinearR2Determinism, this.nonlinearDeterminismMaxSetSize,
                    this.sentinelMinCount, this.sentinelMinMass, this.sentinelGapRatio,
                    this.sentinelMinDistinct);
        }

        /**
         * Returns a config identical to this one but with the given maximum distinct-value count for members of
         * determining sets in the cell-determinism check.
         *
         * @param cellDeterminismMaxCardinality the new maximum cardinality.
         * @return the new config.
         */
        public Config withCellDeterminismMaxCardinality(int cellDeterminismMaxCardinality) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount,
                    this.minExpectedPairwiseCell, this.highCorrelation, this.r2Determinism,
                    this.etaSquaredDeterminism, this.adAlpha, this.minAdSampleSize, this.lowSampleRatio,
                    this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, this.serialAlpha,
                    this.serialMinAbsAutocorrelation, this.minSerialSampleSize, this.serialGroupVariable,
                    this.cellDeterminismMaxSetSize, cellDeterminismMaxCardinality, this.cellDeterminismTolerance,
                    this.nonlinearR2Determinism, this.nonlinearDeterminismMaxSetSize,
                    this.sentinelMinCount, this.sentinelMinMass, this.sentinelGapRatio,
                    this.sentinelMinDistinct);
        }

        /**
         * Returns a config identical to this one but with the given relative tolerance for the cell-determinism
         * check.
         *
         * @param cellDeterminismTolerance the new tolerance.
         * @return the new config.
         */
        public Config withCellDeterminismTolerance(double cellDeterminismTolerance) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount,
                    this.minExpectedPairwiseCell, this.highCorrelation, this.r2Determinism,
                    this.etaSquaredDeterminism, this.adAlpha, this.minAdSampleSize, this.lowSampleRatio,
                    this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, this.serialAlpha,
                    this.serialMinAbsAutocorrelation, this.minSerialSampleSize, this.serialGroupVariable,
                    this.cellDeterminismMaxSetSize, this.cellDeterminismMaxCardinality, cellDeterminismTolerance,
                    this.nonlinearR2Determinism, this.nonlinearDeterminismMaxSetSize,
                    this.sentinelMinCount, this.sentinelMinMass, this.sentinelGapRatio,
                    this.sentinelMinDistinct);
        }

        /**
         * Returns a config identical to this one but with the given nonlinear near-determinism threshold.
         *
         * @param nonlinearR2Determinism the new threshold.
         * @return the new config.
         */
        public Config withNonlinearR2Determinism(double nonlinearR2Determinism) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount,
                    this.minExpectedPairwiseCell, this.highCorrelation, this.r2Determinism,
                    this.etaSquaredDeterminism, this.adAlpha, this.minAdSampleSize, this.lowSampleRatio,
                    this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, this.serialAlpha,
                    this.serialMinAbsAutocorrelation, this.minSerialSampleSize, this.serialGroupVariable,
                    this.cellDeterminismMaxSetSize, this.cellDeterminismMaxCardinality, this.cellDeterminismTolerance,
                    nonlinearR2Determinism, this.nonlinearDeterminismMaxSetSize,
                    this.sentinelMinCount, this.sentinelMinMass, this.sentinelGapRatio,
                    this.sentinelMinDistinct);
        }

        /**
         * Returns a config identical to this one but with the given nonlinear near-determinism maximum
         * determining-set size (0 disables the check).
         *
         * @param nonlinearDeterminismMaxSetSize the new maximum set size.
         * @return the new config.
         */
        public Config withNonlinearDeterminismMaxSetSize(int nonlinearDeterminismMaxSetSize) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount,
                    this.minExpectedPairwiseCell, this.highCorrelation, this.r2Determinism,
                    this.etaSquaredDeterminism, this.adAlpha, this.minAdSampleSize, this.lowSampleRatio,
                    this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, this.serialAlpha,
                    this.serialMinAbsAutocorrelation, this.minSerialSampleSize, this.serialGroupVariable,
                    this.cellDeterminismMaxSetSize, this.cellDeterminismMaxCardinality, this.cellDeterminismTolerance,
                    this.nonlinearR2Determinism, nonlinearDeterminismMaxSetSize,
                    this.sentinelMinCount, this.sentinelMinMass, this.sentinelGapRatio,
                    this.sentinelMinDistinct);
        }

        /**
         * Sets the serial correlation alpha value.
         *
         * @param serialAlpha the serial correlation alpha value.
         * @return a new Config object with the updated serial correlation alpha value.
         */
        public Config withSerialAlpha(double serialAlpha) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount, this.minExpectedPairwiseCell, this.highCorrelation, this.r2Determinism, this.etaSquaredDeterminism, this.adAlpha, this.minAdSampleSize,
                    this.lowSampleRatio, this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, serialAlpha, this.serialMinAbsAutocorrelation, this.minSerialSampleSize, this.serialGroupVariable,
                    this.cellDeterminismMaxSetSize, this.cellDeterminismMaxCardinality, this.cellDeterminismTolerance,
                    this.nonlinearR2Determinism, this.nonlinearDeterminismMaxSetSize,
                    this.sentinelMinCount, this.sentinelMinMass, this.sentinelGapRatio,
                    this.sentinelMinDistinct);
        }

        /**
         * Returns a config identical to this one but with the given minimum cell count for SENTINEL_VALUE.
         *
         * @param sentinelMinCount the new value.
         * @return the new config.
         */
        public Config withSentinelMinCount(int sentinelMinCount) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount, this.minExpectedPairwiseCell, this.highCorrelation,
                    this.r2Determinism, this.etaSquaredDeterminism, this.adAlpha, this.minAdSampleSize, this.lowSampleRatio,
                    this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, this.serialAlpha, this.serialMinAbsAutocorrelation,
                    this.minSerialSampleSize, this.serialGroupVariable, this.cellDeterminismMaxSetSize, this.cellDeterminismMaxCardinality, this.cellDeterminismTolerance,
                    this.nonlinearR2Determinism, this.nonlinearDeterminismMaxSetSize,
                    sentinelMinCount, this.sentinelMinMass, this.sentinelGapRatio, this.sentinelMinDistinct);
        }

        /**
         * Returns a config identical to this one but with the given minimum observed-value share for SENTINEL_VALUE.
         *
         * @param sentinelMinMass the new value.
         * @return the new config.
         */
        public Config withSentinelMinMass(double sentinelMinMass) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount, this.minExpectedPairwiseCell, this.highCorrelation,
                    this.r2Determinism, this.etaSquaredDeterminism, this.adAlpha, this.minAdSampleSize, this.lowSampleRatio,
                    this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, this.serialAlpha, this.serialMinAbsAutocorrelation,
                    this.minSerialSampleSize, this.serialGroupVariable, this.cellDeterminismMaxSetSize, this.cellDeterminismMaxCardinality, this.cellDeterminismTolerance,
                    this.nonlinearR2Determinism, this.nonlinearDeterminismMaxSetSize,
                    this.sentinelMinCount, sentinelMinMass, this.sentinelGapRatio, this.sentinelMinDistinct);
        }

        /**
         * Returns a config identical to this one but with the given minimum gap-to-median-spacing ratio for SENTINEL_VALUE.
         *
         * @param sentinelGapRatio the new value.
         * @return the new config.
         */
        public Config withSentinelGapRatio(double sentinelGapRatio) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount, this.minExpectedPairwiseCell, this.highCorrelation,
                    this.r2Determinism, this.etaSquaredDeterminism, this.adAlpha, this.minAdSampleSize, this.lowSampleRatio,
                    this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, this.serialAlpha, this.serialMinAbsAutocorrelation,
                    this.minSerialSampleSize, this.serialGroupVariable, this.cellDeterminismMaxSetSize, this.cellDeterminismMaxCardinality, this.cellDeterminismTolerance,
                    this.nonlinearR2Determinism, this.nonlinearDeterminismMaxSetSize,
                    this.sentinelMinCount, this.sentinelMinMass, sentinelGapRatio, this.sentinelMinDistinct);
        }

        /**
         * Returns a config identical to this one but with the given minimum distinct-value count for the SENTINEL_VALUE check to run.
         *
         * @param sentinelMinDistinct the new value.
         * @return the new config.
         */
        public Config withSentinelMinDistinct(int sentinelMinDistinct) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount, this.minExpectedPairwiseCell, this.highCorrelation,
                    this.r2Determinism, this.etaSquaredDeterminism, this.adAlpha, this.minAdSampleSize, this.lowSampleRatio,
                    this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, this.serialAlpha, this.serialMinAbsAutocorrelation,
                    this.minSerialSampleSize, this.serialGroupVariable, this.cellDeterminismMaxSetSize, this.cellDeterminismMaxCardinality, this.cellDeterminismTolerance,
                    this.nonlinearR2Determinism, this.nonlinearDeterminismMaxSetSize,
                    this.sentinelMinCount, this.sentinelMinMass, this.sentinelGapRatio, sentinelMinDistinct);
        }
    }
}

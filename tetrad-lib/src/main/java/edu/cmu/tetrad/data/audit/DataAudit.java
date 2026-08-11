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
import java.util.HashSet;
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
     * Variable names, in column order.
     */
    private final String[] names;

    /**
     * The number of distinct observed (non-missing) values per column, by name.
     */
    private final Map<String, Integer> observedDistinct = new LinkedHashMap<>();

    /**
     * Column indices of the continuous variables, in column order.
     */
    private final int[] continuousIndices;

    /**
     * Names of the continuous variables, in column order.
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
        this.names = new String[p];

        List<Node> variables = dataSet.getVariables();
        List<Integer> contIdx = new ArrayList<>();

        for (int j = 0; j < p; j++) {
            Node v = variables.get(j);
            this.names[j] = v.getName();
            this.discrete[j] = v instanceof DiscreteVariable;

            if (!this.discrete[j]) {
                contIdx.add(j);
                this.continuousNames.add(v.getName());
            }
        }

        this.continuousIndices = contIdx.stream().mapToInt(Integer::intValue).toArray();

        MissingDataAudit mda = new MissingDataAudit(dataSet);
        this.missingDataAudit = mda.anyMissing() ? mda : null;

        censusCheck();
        constancyChecks();
        smallCellChecks();
        this.continuousCorrelation = correlationChecks();
        nearDeterminismDiscreteContinuousCheck();
        nonGaussianityCheck();
        serialDependenceCheck();
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
     * Returns the names of the continuous variables, in column order, unmodifiable.
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

        return sb.toString();
    }

    /**
     * Returns the findings and summary statistics as a JSON string, suitable for consumption by py-tetrad or other
     * tools. The schema has top-level fields "numRows", "numVariables", "numContinuous", "numDiscrete", and
     * "findings", the last an array of objects with fields "code", "severity", "variables", "values", and "message".
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
                    if (total == 0) {
                        this.findings.add(new AuditFinding(FindingCode.CONSTANT_COLUMN,
                                AuditFinding.Severity.WARNING, List.of(this.names[j]),
                                Map.of("numNonMissing", 0.0),
                                "Discrete variable " + this.names[j] + " has no non-missing values."));
                    } else {
                        int cat = counts.keySet().iterator().next();
                        this.findings.add(new AuditFinding(FindingCode.CONSTANT_COLUMN,
                                AuditFinding.Severity.WARNING, List.of(this.names[j]),
                                Map.of("numNonMissing", (double) total, "categoryIndex", (double) cat),
                                "Discrete variable " + this.names[j] + " is constant: all " + total
                                        + " non-missing values fall in one category."));
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
                    this.findings.add(new AuditFinding(FindingCode.CONSTANT_COLUMN,
                            AuditFinding.Severity.WARNING, List.of(this.names[j]),
                            Map.of("numNonMissing", 0.0),
                            "Continuous variable " + this.names[j] + " has no non-missing values."));
                    continue;
                }

                if (min == max) {
                    this.findings.add(new AuditFinding(FindingCode.CONSTANT_COLUMN,
                            AuditFinding.Severity.WARNING, List.of(this.names[j]),
                            Map.of("value", min, "numNonMissing", (double) m),
                            "Continuous variable " + this.names[j] + " is constant: all " + m
                                    + " non-missing values equal " + min + "."));
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
     * Flags small observed marginal cells for discrete variables and small expected pairwise cells for pairs of
     * discrete variables (expected counts computed under independence from the observed margins).
     */
    private void smallCellChecks() {
        int n = this.dataSet.getNumRows();

        // Marginal cells.
        for (int j = 0; j < this.names.length; j++) {
            if (!this.discrete[j]) continue;
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
            if (!this.discrete[a]) continue;

            for (int b = a + 1; b < this.names.length; b++) {
                if (!this.discrete[b]) continue;

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
            corr[a][a] = 1.0;

            for (int b = a + 1; b < pc; b++) {
                double r = pairwiseCorrelation(this.continuousIndices[a], this.continuousIndices[b]);
                corr[a][b] = r;
                corr[b][a] = r;

                if (!Double.isNaN(r) && Math.abs(r) >= this.config.highCorrelation) {
                    this.findings.add(new AuditFinding(FindingCode.HIGH_CORRELATION,
                            AuditFinding.Severity.WARNING,
                            List.of(this.continuousNames.get(a), this.continuousNames.get(b)),
                            Map.of("correlation", r, "threshold", this.config.highCorrelation),
                            "Continuous pair (" + this.continuousNames.get(a) + ", " + this.continuousNames.get(b)
                                    + ") has correlation " + fmt(r) + "."));
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

        boolean singular = c.rank() < pc;

        if (singular) {
            this.findings.add(new AuditFinding(FindingCode.EXACT_LINEAR_DEPENDENCE,
                    AuditFinding.Severity.WARNING, List.copyOf(this.continuousNames),
                    Map.of("rank", (double) c.rank(), "numContinuous", (double) pc),
                    "The correlation matrix of the continuous variables is singular (rank " + c.rank()
                            + " of " + pc + "): some variable is an exact linear function of the others."));
        }

        try {
            Matrix inv = singular ? c.pseudoinverse() : c.inverse();

            for (int a = 0; a < pc; a++) {
                double vif = inv.get(a, a);
                if (vif <= 0) continue;
                double r2 = Math.min(1.0, Math.max(0.0, 1.0 - 1.0 / vif));
                this.r2OnOthers.put(this.continuousNames.get(a), r2);

                if (!singular && r2 >= this.config.r2Determinism) {
                    this.findings.add(new AuditFinding(FindingCode.NEAR_DETERMINISM_CONTINUOUS,
                            AuditFinding.Severity.WARNING, List.of(this.continuousNames.get(a)),
                            Map.of("rSquared", r2, "threshold", this.config.r2Determinism),
                            "Continuous variable " + this.continuousNames.get(a)
                                    + " is nearly a linear function of the other continuous variables (R^2 = "
                                    + fmt(r2) + ")."));
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
            if (!this.discrete[a]) continue;

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
                String key = this.names[a] + "|" + this.dataSet.getVariables().get(jc).getName();
                this.etaSquared.put(key, eta2);

                if (eta2 >= this.config.etaSquaredDeterminism) {
                    String contName = this.dataSet.getVariables().get(jc).getName();
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
     * Flags continuous variables whose marginal distribution deviates from Gaussian by the Anderson-Darling test, at
     * the configured alpha, using non-missing values. Variables with fewer non-missing values than the configured
     * minimum are skipped. These findings are informational: non-Gaussianity threatens linear-Gaussian machinery but
     * is exploitable by LiNGAM-family methods.
     */
    private void nonGaussianityCheck() {
        int n = this.dataSet.getNumRows();

        for (int j : this.continuousIndices) {
            List<Double> values = new ArrayList<>();

            for (int i = 0; i < n; i++) {
                if (MissingDataAudit.isMissing(this.dataSet, i, j)) continue;
                values.add(this.dataSet.getDouble(i, j));
            }

            if (values.size() < this.config.minAdSampleSize) continue;
            double[] x = values.stream().mapToDouble(Double::doubleValue).toArray();
            AndersonDarlingTest test = new AndersonDarlingTest(x);
            double p = test.getP();
            String name = this.dataSet.getVariables().get(j).getName();
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
     * n_eff = m (1 - r1) / (1 + r1), when the lag-1 autocorrelation r1 is positive, since that communicates severity
     * better than a correlation does.
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
     * lag-1 Cramer's V or runs test could be added later under the same finding code.
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

            String name = this.dataSet.getVariables().get(j).getName();
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

                String effNote = "";

                if (r1 > 0 && r1 < 1) {
                    double nEff = m * (1 - r1) / (1 + r1);
                    values.put("effectiveSampleSize", nEff);
                    effNote = "; under an AR(1) approximation n = " + m + " behaves like n ~ "
                            + Math.round(nEff);
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
         * @param r2Determinism           threshold for NEAR_DETERMINISM_CONTINUOUS.
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
         * @param r2Determinism               threshold for NEAR_DETERMINISM_CONTINUOUS.
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
        }

        /**
         * Returns a config identical to this one but with the given high-correlation threshold.
         *
         * @param highCorrelation the new threshold.
         * @return the new config.
         */
        public Config withHighCorrelation(double highCorrelation) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount,
                    this.minExpectedPairwiseCell, highCorrelation, this.r2Determinism, this.etaSquaredDeterminism,
                    this.adAlpha, this.minAdSampleSize, this.lowSampleRatio, this.nearConstantFrequency,
                    this.nearConstantVariance, this.serialMaxLag, this.serialAlpha,
                    this.serialMinAbsAutocorrelation, this.minSerialSampleSize, this.serialGroupVariable);
        }

        /**
         * Returns a config identical to this one but with the given small-cell count threshold.
         *
         * @param smallCellCount the new threshold.
         * @return the new config.
         */
        public Config withSmallCellCount(int smallCellCount) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, smallCellCount,
                    this.minExpectedPairwiseCell, this.highCorrelation, this.r2Determinism,
                    this.etaSquaredDeterminism, this.adAlpha, this.minAdSampleSize, this.lowSampleRatio,
                    this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, this.serialAlpha,
                    this.serialMinAbsAutocorrelation, this.minSerialSampleSize, this.serialGroupVariable);
        }

        /**
         * Returns a config identical to this one but with the given Anderson-Darling alpha.
         *
         * @param adAlpha the new alpha.
         * @return the new config.
         */
        public Config withAdAlpha(double adAlpha) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount,
                    this.minExpectedPairwiseCell, this.highCorrelation, this.r2Determinism,
                    this.etaSquaredDeterminism, adAlpha, this.minAdSampleSize, this.lowSampleRatio,
                    this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, this.serialAlpha,
                    this.serialMinAbsAutocorrelation, this.minSerialSampleSize, this.serialGroupVariable);
        }

        /**
         * Returns a config identical to this one but with the given serial dependence grouping variable.
         *
         * @param serialGroupVariable the name of a discrete grouping variable, or null for no grouping.
         * @return the new config.
         */
        public Config withSerialGroupVariable(String serialGroupVariable) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount,
                    this.minExpectedPairwiseCell, this.highCorrelation, this.r2Determinism,
                    this.etaSquaredDeterminism, this.adAlpha, this.minAdSampleSize, this.lowSampleRatio,
                    this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, this.serialAlpha,
                    this.serialMinAbsAutocorrelation, this.minSerialSampleSize, serialGroupVariable);
        }

        /**
         * Returns a config identical to this one but with the given Ljung-Box alpha for serial dependence.
         *
         * @param serialAlpha the new alpha.
         * @return the new config.
         */
        public Config withSerialAlpha(double serialAlpha) {
            return new Config(this.fewContinuousValues, this.manyDiscreteLevels, this.smallCellCount,
                    this.minExpectedPairwiseCell, this.highCorrelation, this.r2Determinism,
                    this.etaSquaredDeterminism, this.adAlpha, this.minAdSampleSize, this.lowSampleRatio,
                    this.nearConstantFrequency, this.nearConstantVariance, this.serialMaxLag, serialAlpha,
                    this.serialMinAbsAutocorrelation, this.minSerialSampleSize, this.serialGroupVariable);
        }
    }
}

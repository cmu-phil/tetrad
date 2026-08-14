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

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.util.Matrix;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A pre-search audit of a supplied covariance (or correlation) matrix. On construction, computes the checks that are
 * possible when only second moments and a stated sample size are available - symmetry, nonpositive variances,
 * positive semidefiniteness, exact and near linear dependence, implied high correlations and duplicate variables,
 * variance scale, and consistency of the stated sample size with the matrix - and emits the results as a list of
 * {@link AuditFinding}s keyed by {@link FindingCode}. This is the covariance-matrix counterpart of {@link DataAudit},
 * which audits tabular datasets.
 * <p>
 * This class reports findings only; it makes no recommendations. The interpretation of findings is left to the user
 * and to documentation that dispatches on the finding codes. Raw statistics computed along the way (the implied
 * correlation matrix, eigenvalue extremes, R-squared of each variable on the others) are available from accessors so
 * that downstream tools can display or reason over the numbers, not just the flags.
 * <p>
 * Several checks that {@link DataAudit} runs on tabular data are impossible here and are silently absent rather than
 * degraded: a covariance matrix carries no information about missingness, marginal distributions (non-Gaussianity),
 * serial dependence of rows, discrete variables, or nonlinear relationships. A clean covariance audit therefore
 * certifies much less about the data than a clean data audit; the {@link #report()} states this scope limit.
 * <p>
 * Variables with nonpositive variance (see {@link FindingCode#NONPOSITIVE_VARIANCE}) are flagged and then excluded
 * from all correlation-derived checks in the same audit, in analogy to {@link DataAudit}'s exclusion of constant
 * columns: their correlations are undefined, and including them would render the matrix trivially singular, masking
 * findings among the remaining variables. Each NONPOSITIVE_VARIANCE finding states this exclusion in its message.
 * Consequently {@link #getVariableNames()} and the correlation-based accessors cover only the positive-variance
 * variables.
 * <p>
 * If the supplied matrix is asymmetric beyond tolerance (see {@link FindingCode#COVARIANCE_NOT_SYMMETRIC}), it is
 * flagged and all subsequent numerical work is done on the symmetrized matrix (S + S')/2.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see FindingCode
 * @see AuditFinding
 * @see DataAudit
 */
public final class CovarianceAudit {

    /**
     * The covariance matrix being audited.
     */
    private final ICovarianceMatrix cov;

    /**
     * The thresholds used by the checks.
     */
    private final Config config;

    /**
     * The findings, in the order the checks run.
     */
    private final List<AuditFinding> findings = new ArrayList<>();

    /**
     * All variable names, in matrix order.
     */
    private final String[] allNames;

    /**
     * Whether each variable's diagonal entry is nonpositive; such variables are flagged NONPOSITIVE_VARIANCE and
     * excluded from correlation-derived checks.
     */
    private final boolean[] nonpositiveVariance;

    /**
     * Names of the positive-variance variables, in matrix order.
     */
    private final List<String> variableNames = new ArrayList<>();

    /**
     * The symmetrized covariance submatrix of the positive-variance variables.
     */
    private final Matrix symmetrized;

    /**
     * The correlation matrix implied by the symmetrized covariance submatrix of the positive-variance variables, or
     * null if fewer than two such variables.
     */
    private final Matrix correlation;

    /**
     * Multiple R-squared of each positive-variance variable on the others, by name, where computable.
     */
    private final Map<String, Double> r2OnOthers = new LinkedHashMap<>();

    /**
     * The smallest eigenvalue of the implied correlation matrix, or NaN if unavailable.
     */
    private double minEigenvalue = Double.NaN;

    /**
     * The largest eigenvalue of the implied correlation matrix, or NaN if unavailable.
     */
    private double maxEigenvalue = Double.NaN;

    /**
     * The eigenvalue-based rank of the implied correlation matrix, or -1 if unavailable.
     */
    private int correlationRank = -1;

    /**
     * Whether every diagonal entry of the supplied matrix is 1 within tolerance, i.e., the input is a correlation
     * matrix.
     */
    private final boolean correlationInput;

    /**
     * Audits the given covariance matrix with default thresholds.
     *
     * @param cov the covariance matrix to audit; may not be null.
     */
    public CovarianceAudit(ICovarianceMatrix cov) {
        this(cov, new Config());
    }

    /**
     * Audits the given covariance matrix with the given thresholds.
     *
     * @param cov    the covariance matrix to audit; may not be null.
     * @param config the thresholds to use; may not be null.
     */
    public CovarianceAudit(ICovarianceMatrix cov, Config config) {
        if (cov == null) throw new NullPointerException("cov");
        if (config == null) throw new NullPointerException("config");

        this.cov = cov;
        this.config = config;

        int p = cov.getDimension();
        this.allNames = new String[p];

        for (int j = 0; j < p; j++) {
            this.allNames[j] = cov.getVariableName(j);
        }

        Matrix raw = cov.getMatrix();

        symmetryCheck(raw);

        // All subsequent numerical work uses the symmetrized matrix; for a symmetric input this is the input.
        Matrix full = symmetrize(raw);

        this.nonpositiveVariance = new boolean[p];
        this.correlationInput = diagonalChecks(full);

        List<Integer> keep = new ArrayList<>();

        for (int j = 0; j < p; j++) {
            if (!this.nonpositiveVariance[j]) {
                keep.add(j);
                this.variableNames.add(this.allNames[j]);
            }
        }

        int pk = keep.size();
        Matrix sub = new Matrix(pk, pk);

        for (int a = 0; a < pk; a++) {
            for (int b = 0; b < pk; b++) {
                sub.set(a, b, full.get(keep.get(a), keep.get(b)));
            }
        }

        this.symmetrized = sub;

        varianceScaleCheck();
        this.correlation = correlationChecks();
        eigenChecks();
        sampleSizeChecks();
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
     * Returns the names of the positive-variance variables, in matrix order (see the class Javadoc), unmodifiable.
     *
     * @return These names.
     */
    public List<String> getVariableNames() {
        return List.copyOf(this.variableNames);
    }

    /**
     * Returns the correlation matrix implied by the (symmetrized) supplied covariance matrix, over the
     * positive-variance variables in the order given by {@link #getVariableNames()}, or null if there are fewer than
     * two such variables.
     *
     * @return This matrix or null.
     */
    public Matrix getImpliedCorrelationMatrix() {
        return this.correlation == null ? null : new Matrix(this.correlation);
    }

    /**
     * Returns the multiple R-squared of each positive-variance variable regressed on the others, by name, where this
     * was computable from the implied correlation matrix, unmodifiable.
     *
     * @return This map.
     */
    public Map<String, Double> getR2OnOthers() {
        return new LinkedHashMap<>(this.r2OnOthers);
    }

    /**
     * Returns the smallest eigenvalue of the implied correlation matrix, or NaN if it could not be computed (fewer
     * than two positive-variance variables, or a failed decomposition).
     *
     * @return This eigenvalue or NaN.
     */
    public double getMinEigenvalue() {
        return this.minEigenvalue;
    }

    /**
     * Returns the largest eigenvalue of the implied correlation matrix, or NaN if it could not be computed.
     *
     * @return This eigenvalue or NaN.
     */
    public double getMaxEigenvalue() {
        return this.maxEigenvalue;
    }

    /**
     * Returns the condition number (largest over smallest eigenvalue) of the implied correlation matrix, positive
     * infinity if the smallest eigenvalue is nonpositive, or NaN if the eigenvalues could not be computed.
     *
     * @return This condition number, positive infinity, or NaN.
     */
    public double getConditionNumber() {
        if (Double.isNaN(this.minEigenvalue) || Double.isNaN(this.maxEigenvalue)) return Double.NaN;
        if (this.minEigenvalue <= 0) return Double.POSITIVE_INFINITY;
        return this.maxEigenvalue / this.minEigenvalue;
    }

    /**
     * Returns the eigenvalue-based rank of the implied correlation matrix (the number of eigenvalues exceeding the
     * configured relative tolerance times the largest eigenvalue), or -1 if unavailable.
     *
     * @return This rank or -1.
     */
    public int getCorrelationRank() {
        return this.correlationRank;
    }

    /**
     * Returns true if every diagonal entry of the supplied matrix is 1 within tolerance, i.e., the input is a
     * correlation matrix rather than a covariance matrix on the original scales. This is a fact about the input, not
     * a defect, and so is reported here and in {@link #report()} rather than as a finding.
     *
     * @return True if the input is a correlation matrix.
     */
    public boolean isCorrelationInput() {
        return this.correlationInput;
    }

    /**
     * Returns a human-readable multi-section report of the audit.
     *
     * @return This report.
     */
    public String report() {
        StringBuilder sb = new StringBuilder();
        int p = this.cov.getDimension();
        int excluded = p - this.variableNames.size();

        sb.append("Covariance matrix audit: ").append(p).append(" variables, stated sample size ")
                .append(this.cov.getSampleSize()).append(".");

        if (this.correlationInput) {
            sb.append(" The diagonal is 1 within tolerance: this is a correlation matrix.");
        }

        sb.append('\n');

        if (excluded > 0) {
            sb.append(excluded).append(" variable(s) with nonpositive variance excluded from ")
                    .append("correlation-derived checks.\n");
        }

        if (!Double.isNaN(this.minEigenvalue)) {
            sb.append("Implied correlation matrix: eigenvalues in [").append(fmt(this.minEigenvalue))
                    .append(", ").append(fmt(this.maxEigenvalue)).append("], rank ").append(this.correlationRank)
                    .append(" of ").append(this.variableNames.size()).append(", condition number ")
                    .append(fmt(getConditionNumber())).append(".\n");
        }

        long warnings = this.findings.stream().filter(f -> f.getSeverity() == AuditFinding.Severity.WARNING).count();
        long infos = this.findings.size() - warnings;
        sb.append("Findings: ").append(warnings).append(" warning(s), ").append(infos).append(" informational.\n");

        if (this.findings.isEmpty()) {
            sb.append("No findings; no checked property of the matrix was flagged.\n");
        } else {
            for (AuditFinding f : this.findings) {
                sb.append(f).append('\n');
            }
        }

        sb.append('\n').append("Scope: a covariance matrix carries no information about missingness, marginal ")
                .append("distributions (non-Gaussianity), serial dependence of rows, discrete variables, or ")
                .append("nonlinear relationships, so no check of those properties is possible here. A clean audit ")
                .append("of a covariance matrix certifies less about the data than a clean audit of the tabular ")
                .append("dataset it came from.\n");

        return sb.toString();
    }

    //==================================== CHECKS ====================================//

    /**
     * Flags COVARIANCE_NOT_SYMMETRIC if some entry differs from its transpose entry by more than the configured
     * relative tolerance times the largest absolute entry.
     */
    private void symmetryCheck(Matrix raw) {
        int p = raw.getNumRows();
        double scale = 0.0;
        double maxAsym = 0.0;
        int worstA = -1;
        int worstB = -1;

        for (int a = 0; a < p; a++) {
            for (int b = 0; b < p; b++) {
                scale = Math.max(scale, Math.abs(raw.get(a, b)));
            }
        }

        for (int a = 0; a < p; a++) {
            for (int b = a + 1; b < p; b++) {
                double d = Math.abs(raw.get(a, b) - raw.get(b, a));

                if (d > maxAsym) {
                    maxAsym = d;
                    worstA = a;
                    worstB = b;
                }
            }
        }

        if (scale > 0 && maxAsym > this.config.symmetryTolerance * scale) {
            this.findings.add(new AuditFinding(FindingCode.COVARIANCE_NOT_SYMMETRIC,
                    AuditFinding.Severity.WARNING,
                    List.of(this.allNames[worstA], this.allNames[worstB]),
                    Map.of("maxAsymmetry", maxAsym, "largestAbsEntry", scale,
                            "relativeTolerance", this.config.symmetryTolerance),
                    "The matrix is not symmetric: entries (" + this.allNames[worstA] + ", " + this.allNames[worstB]
                            + ") and (" + this.allNames[worstB] + ", " + this.allNames[worstA] + ") differ by "
                            + fmt(maxAsym) + " (largest absolute entry " + fmt(scale) + "). A covariance matrix is "
                            + "symmetric by definition, so this indicates a transcription or assembly error. "
                            + "Subsequent checks in this audit use the symmetrized matrix (S + S')/2."));
        }
    }

    /**
     * Returns (S + S')/2.
     */
    private static Matrix symmetrize(Matrix raw) {
        int p = raw.getNumRows();
        Matrix s = new Matrix(p, p);

        for (int a = 0; a < p; a++) {
            for (int b = 0; b < p; b++) {
                s.set(a, b, (raw.get(a, b) + raw.get(b, a)) / 2.0);
            }
        }

        return s;
    }

    /**
     * Flags NONPOSITIVE_VARIANCE per offending variable and records the exclusions; returns whether every diagonal
     * entry is 1 within tolerance (correlation input).
     */
    private boolean diagonalChecks(Matrix full) {
        int p = full.getNumRows();
        boolean unitDiagonal = true;

        for (int j = 0; j < p; j++) {
            double v = full.get(j, j);

            if (Math.abs(v - 1.0) > 1e-8) unitDiagonal = false;

            if (v <= 0) {
                this.nonpositiveVariance[j] = true;

                String reason = v == 0
                        ? "has zero variance; it is the covariance-matrix analog of a constant column and carries "
                        + "no sample information"
                        : "has negative variance " + fmt(v) + ", which is not a variance at all; the matrix is "
                        + "invalid as given";

                this.findings.add(new AuditFinding(FindingCode.NONPOSITIVE_VARIANCE,
                        AuditFinding.Severity.WARNING, List.of(this.allNames[j]),
                        Map.of("variance", v),
                        "Variable " + this.allNames[j] + " " + reason + ". It is excluded from the "
                                + "correlation-derived checks in this audit."));
            }
        }

        return unitDiagonal && p > 0;
    }

    /**
     * Flags EXTREME_VARIANCE_SCALE if the ratio of the largest to the smallest positive variance meets the
     * configured threshold.
     */
    private void varianceScaleCheck() {
        int pk = this.symmetrized.getNumRows();
        if (pk < 2) return;

        double minVar = Double.POSITIVE_INFINITY;
        double maxVar = 0.0;
        int minIdx = -1;
        int maxIdx = -1;

        for (int a = 0; a < pk; a++) {
            double v = this.symmetrized.get(a, a);

            if (v < minVar) {
                minVar = v;
                minIdx = a;
            }

            if (v > maxVar) {
                maxVar = v;
                maxIdx = a;
            }
        }

        double ratio = maxVar / minVar;

        if (ratio >= this.config.extremeVarianceRatio) {
            this.findings.add(new AuditFinding(FindingCode.EXTREME_VARIANCE_SCALE,
                    AuditFinding.Severity.INFO,
                    List.of(this.variableNames.get(maxIdx), this.variableNames.get(minIdx)),
                    Map.of("maxVariance", maxVar, "minVariance", minVar, "ratio", ratio,
                            "threshold", this.config.extremeVarianceRatio),
                    "The largest-to-smallest variance ratio is " + fmt(ratio) + ": " + this.variableNames.get(maxIdx)
                            + " has variance " + fmt(maxVar) + " while " + this.variableNames.get(minIdx)
                            + " has variance " + fmt(minVar) + ". This usually reflects unstandardized variables "
                            + "in very different units and degrades numerical conditioning."));
        }
    }

    /**
     * Builds the implied correlation matrix of the positive-variance variables and flags DUPLICATE_COLUMNS and
     * HIGH_CORRELATION pairs; returns the matrix, or null if fewer than two such variables.
     */
    private Matrix correlationChecks() {
        int pk = this.symmetrized.getNumRows();
        if (pk < 2) return null;

        Matrix corr = new Matrix(pk, pk);

        for (int a = 0; a < pk; a++) {
            corr.set(a, a, 1.0);

            for (int b = a + 1; b < pk; b++) {
                double r = this.symmetrized.get(a, b)
                        / Math.sqrt(this.symmetrized.get(a, a) * this.symmetrized.get(b, b));
                corr.set(a, b, r);
                corr.set(b, a, r);

                if (Math.abs(r) >= 1.0 - this.config.duplicateCorrelationTolerance) {
                    this.findings.add(new AuditFinding(FindingCode.DUPLICATE_COLUMNS,
                            AuditFinding.Severity.WARNING,
                            List.of(this.variableNames.get(a), this.variableNames.get(b)),
                            Map.of("correlation", r),
                            "Variables " + this.variableNames.get(a) + " and " + this.variableNames.get(b)
                                    + " have implied correlation " + fmt(r) + ": the matrix presents them as exact "
                                    + (r > 0 ? "" : "sign-reversed ") + "affine copies of one another, so one "
                                    + "carries no information the other does not."));
                } else if (Math.abs(r) >= this.config.highCorrelation) {
                    this.findings.add(new AuditFinding(FindingCode.HIGH_CORRELATION,
                            AuditFinding.Severity.WARNING,
                            List.of(this.variableNames.get(a), this.variableNames.get(b)),
                            Map.of("correlation", r, "threshold", this.config.highCorrelation,
                                    "nUsed", (double) this.cov.getSampleSize()),
                            "Pair (" + this.variableNames.get(a) + ", " + this.variableNames.get(b)
                                    + ") has implied correlation " + fmt(r) + " (stated sample size "
                                    + this.cov.getSampleSize() + ")."));
                }
            }
        }

        return corr;
    }

    /**
     * Computes the eigenvalues of the implied correlation matrix; flags COVARIANCE_NOT_PSD and
     * EXACT_LINEAR_DEPENDENCE, and computes R-squared of each variable on the others (flagging
     * NEAR_DETERMINISM_CONTINUOUS) where the matrix is invertible.
     */
    private void eigenChecks() {
        if (this.correlation == null) return;

        int pk = this.correlation.getNumRows();
        double minEig;
        double maxEig;
        int numNegative = 0;
        int rank = 0;

        try {
            org.ejml.simple.SimpleEVD<org.ejml.simple.SimpleMatrix> evd
                    = new org.ejml.simple.SimpleMatrix(this.correlation.toArray()).eig();
            minEig = Double.POSITIVE_INFINITY;
            maxEig = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
                double ev = evd.getEigenvalue(i).getReal();
                if (ev < minEig) minEig = ev;
                if (ev > maxEig) maxEig = ev;
            }

            double negTol = this.config.psdTolerance * Math.max(1.0, maxEig);
            double rankTol = this.config.rankTolerance * Math.max(1.0, maxEig);

            for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
                double ev = evd.getEigenvalue(i).getReal();
                if (ev < -negTol) numNegative++;
                if (ev > rankTol) rank++;
            }
        } catch (Exception e) {
            // Tolerated; the PSD, rank, and R^2 checks are then unavailable.
            return;
        }

        this.minEigenvalue = minEig;
        this.maxEigenvalue = maxEig;
        this.correlationRank = rank;

        boolean notPsd = numNegative > 0;

        if (notPsd) {
            this.findings.add(new AuditFinding(FindingCode.COVARIANCE_NOT_PSD,
                    AuditFinding.Severity.WARNING, List.of(),
                    Map.of("minEigenvalue", minEig, "numNegativeEigenvalues", (double) numNegative),
                    "The implied correlation matrix has " + numNegative + " negative eigenvalue(s) (minimum "
                            + fmt(minEig) + "): this matrix is not the covariance matrix of any dataset. Such "
                            + "matrices arise from pairwise-complete assembly, transcription or rounding, or "
                            + "procedures that do not guarantee positive semidefiniteness. Quantities that "
                            + "presuppose a valid covariance matrix (likelihoods, partial correlations, regression "
                            + "coefficients) can fail or behave incoherently."));
        }

        boolean singular = rank < pk;
        int n = this.cov.getSampleSize();
        boolean forced = n - 1 < pk;

        if (singular) {
            this.findings.add(new AuditFinding(FindingCode.EXACT_LINEAR_DEPENDENCE,
                    AuditFinding.Severity.WARNING, List.copyOf(this.variableNames),
                    Map.of("rank", (double) rank, "numVariables", (double) pk),
                    "The implied correlation matrix is singular (rank " + rank + " of " + pk + ")."
                            + (forced ? " Note: with stated sample size " + n + " this is forced by arithmetic "
                            + "(see SAMPLE_SIZE_FORCES_SINGULARITY) and does not identify relationships among "
                            + "specific variables."
                            : " Some variable is an exact linear function of the others.")
                            + (notPsd ? " The matrix is also not positive semidefinite (see COVARIANCE_NOT_PSD), "
                            + "so this rank statement describes the matrix as supplied, not any single sample." : "")
                            + " See DUPLICATE_COLUMNS findings, if any, for the localizable exact dependencies."));
        }

        try {
            Matrix inv = singular ? this.correlation.pseudoinverse() : this.correlation.inverse();

            for (int a = 0; a < pk; a++) {
                double vif = inv.get(a, a);
                if (vif <= 0) continue;
                double r2 = Math.min(1.0, Math.max(0.0, 1.0 - 1.0 / vif));
                this.r2OnOthers.put(this.variableNames.get(a), r2);

                if (!singular && r2 >= this.config.r2Determinism) {
                    this.findings.add(new AuditFinding(FindingCode.NEAR_DETERMINISM_CONTINUOUS,
                            AuditFinding.Severity.WARNING, List.of(this.variableNames.get(a)),
                            Map.of("rSquared", r2, "threshold", this.config.r2Determinism),
                            "Variable " + this.variableNames.get(a) + " is nearly a linear function of the other "
                                    + "variables (implied R^2 = " + fmt(r2) + ")."));
                }
            }
        } catch (Exception e) {
            // Inversion can fail for badly conditioned matrices; R^2 values are then unavailable, but the
            // singularity finding (if any) stands.
        }
    }

    /**
     * Flags SAMPLE_SIZE_FORCES_SINGULARITY when the stated sample size cannot support a full-rank sample covariance
     * matrix of this dimension, and LOW_SAMPLE_RATIO when the sample-size-to-variables ratio is small.
     */
    private void sampleSizeChecks() {
        int n = this.cov.getSampleSize();
        int pk = this.variableNames.size();
        int p = this.cov.getDimension();

        if (pk > 0 && n - 1 < pk) {
            String tail;

            if (this.correlationRank < 0) {
                tail = "";
            } else if (this.correlationRank <= n - 1) {
                tail = " The supplied matrix has rank " + this.correlationRank + ", consistent with that "
                        + "arithmetic; joint linear-dependence findings cannot be attributed to relationships "
                        + "among specific variables.";
            } else {
                tail = " The supplied matrix nevertheless has rank " + this.correlationRank + " > "
                        + Math.max(0, n - 1) + ", so it cannot be an ordinary sample covariance matrix at the "
                        + "stated sample size: either the sample size is misstated or the matrix was regularized, "
                        + "shrunk, or model-implied.";
            }

            String message = "The stated sample size is " + n + " for " + pk + " positive-variance variables; any "
                    + "ordinary sample covariance matrix computed from " + n + " rows has rank at most "
                    + Math.max(0, n - 1) + " and is singular by arithmetic." + tail;

            this.findings.add(new AuditFinding(FindingCode.SAMPLE_SIZE_FORCES_SINGULARITY,
                    AuditFinding.Severity.WARNING, List.of(),
                    Map.of("sampleSize", (double) n, "numVariables", (double) pk),
                    message));
        }

        double ratio = n / (double) p;

        if (ratio < this.config.lowSampleRatio) {
            this.findings.add(new AuditFinding(FindingCode.LOW_SAMPLE_RATIO,
                    AuditFinding.Severity.WARNING, List.of(),
                    Map.of("ratio", ratio, "sampleSize", (double) n,
                            "numVariables", (double) p, "threshold", this.config.lowSampleRatio),
                    "Stated sample size / variable ratio is " + fmt(ratio) + "."));
        }
    }

    private static String fmt(double x) {
        return String.format("%.4g", x);
    }

    //==================================== CONFIG ====================================//

    /**
     * The thresholds used by a {@link CovarianceAudit}'s checks. Immutable; the with-methods return modified
     * copies.
     */
    public static final class Config {

        /**
         * An entry differing from its transpose entry by more than this fraction of the largest absolute entry
         * makes the matrix asymmetric. Default 1e-8.
         */
        private final double symmetryTolerance;

        /**
         * A pair with implied absolute correlation at or above 1 minus this is a duplicate pair. Default 1e-8
         * (looser than the exact-arithmetic tolerance used on tabular data, since supplied matrices are typically
         * rounded to a few decimals).
         */
        private final double duplicateCorrelationTolerance;

        /**
         * A pair with implied absolute correlation at or above this (and below the duplicate cutoff) is flagged.
         * Default 0.9, matching {@link DataAudit.Config}.
         */
        private final double highCorrelation;

        /**
         * A variable with implied multiple R-squared on the others at or above this is flagged. Default 0.98,
         * matching {@link DataAudit.Config}.
         */
        private final double r2Determinism;

        /**
         * An eigenvalue of the implied correlation matrix below minus this (relative to the largest eigenvalue, with
         * a floor of 1) makes the matrix not positive semidefinite. Default 1e-8.
         */
        private final double psdTolerance;

        /**
         * An eigenvalue at or below this (relative to the largest eigenvalue, with a floor of 1) does not count
         * toward the rank. Default 1e-8.
         */
        private final double rankTolerance;

        /**
         * A largest-to-smallest positive variance ratio at or above this is flagged. Default 1e8.
         */
        private final double extremeVarianceRatio;

        /**
         * A stated-sample-size-to-variables ratio below this is flagged. Default 5, matching
         * {@link DataAudit.Config}.
         */
        private final double lowSampleRatio;

        /**
         * Constructs a config with default thresholds.
         */
        public Config() {
            this(1e-8, 1e-8, 0.9, 0.98, 1e-8, 1e-8, 1e8, 5.0);
        }

        /**
         * Constructs a config with the given thresholds. See the field documentation for meanings.
         *
         * @param symmetryTolerance             relative tolerance for COVARIANCE_NOT_SYMMETRIC.
         * @param duplicateCorrelationTolerance tolerance for DUPLICATE_COLUMNS.
         * @param highCorrelation               threshold for HIGH_CORRELATION.
         * @param r2Determinism                 threshold for NEAR_DETERMINISM_CONTINUOUS.
         * @param psdTolerance                  relative eigenvalue tolerance for COVARIANCE_NOT_PSD.
         * @param rankTolerance                 relative eigenvalue tolerance for the rank.
         * @param extremeVarianceRatio          threshold for EXTREME_VARIANCE_SCALE.
         * @param lowSampleRatio                threshold for LOW_SAMPLE_RATIO.
         */
        public Config(double symmetryTolerance, double duplicateCorrelationTolerance, double highCorrelation,
                      double r2Determinism, double psdTolerance, double rankTolerance,
                      double extremeVarianceRatio, double lowSampleRatio) {
            this.symmetryTolerance = symmetryTolerance;
            this.duplicateCorrelationTolerance = duplicateCorrelationTolerance;
            this.highCorrelation = highCorrelation;
            this.r2Determinism = r2Determinism;
            this.psdTolerance = psdTolerance;
            this.rankTolerance = rankTolerance;
            this.extremeVarianceRatio = extremeVarianceRatio;
            this.lowSampleRatio = lowSampleRatio;
        }

        /**
         * Returns a config identical to this one but with the given high-correlation threshold.
         *
         * @param highCorrelation the new threshold.
         * @return the new config.
         */
        public Config withHighCorrelation(double highCorrelation) {
            return new Config(this.symmetryTolerance, this.duplicateCorrelationTolerance, highCorrelation,
                    this.r2Determinism, this.psdTolerance, this.rankTolerance, this.extremeVarianceRatio,
                    this.lowSampleRatio);
        }

        /**
         * Returns a config identical to this one but with the given R-squared determinism threshold.
         *
         * @param r2Determinism the new threshold.
         * @return the new config.
         */
        public Config withR2Determinism(double r2Determinism) {
            return new Config(this.symmetryTolerance, this.duplicateCorrelationTolerance, this.highCorrelation,
                    r2Determinism, this.psdTolerance, this.rankTolerance, this.extremeVarianceRatio,
                    this.lowSampleRatio);
        }

        /**
         * Returns a config identical to this one but with the given duplicate-correlation tolerance.
         *
         * @param duplicateCorrelationTolerance the new tolerance.
         * @return the new config.
         */
        public Config withDuplicateCorrelationTolerance(double duplicateCorrelationTolerance) {
            return new Config(this.symmetryTolerance, duplicateCorrelationTolerance, this.highCorrelation,
                    this.r2Determinism, this.psdTolerance, this.rankTolerance, this.extremeVarianceRatio,
                    this.lowSampleRatio);
        }

        /**
         * Returns a config identical to this one but with the given extreme-variance-ratio threshold.
         *
         * @param extremeVarianceRatio the new threshold.
         * @return the new config.
         */
        public Config withExtremeVarianceRatio(double extremeVarianceRatio) {
            return new Config(this.symmetryTolerance, this.duplicateCorrelationTolerance, this.highCorrelation,
                    this.r2Determinism, this.psdTolerance, this.rankTolerance, extremeVarianceRatio,
                    this.lowSampleRatio);
        }

        /**
         * Returns a config identical to this one but with the given low-sample-ratio threshold.
         *
         * @param lowSampleRatio the new threshold.
         * @return the new config.
         */
        public Config withLowSampleRatio(double lowSampleRatio) {
            return new Config(this.symmetryTolerance, this.duplicateCorrelationTolerance, this.highCorrelation,
                    this.r2Determinism, this.psdTolerance, this.rankTolerance, this.extremeVarianceRatio,
                    lowSampleRatio);
        }
    }
}

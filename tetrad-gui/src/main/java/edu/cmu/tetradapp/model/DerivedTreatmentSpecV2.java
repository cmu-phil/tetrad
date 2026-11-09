package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TMath;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

/**
 * Specification of a derived binary treatment variable, computed from a source variable in a data set by one of
 * several rules (median split, threshold, quantile bands, or discrete subset vs. rest).
 */
public final class DerivedTreatmentSpecV2 implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The rule used to derive the binary treatment from the source variable.
     */
    public enum RuleType {
        /**
         * Continuous source: values at or above the median map to 1, others to 0.
         */
        CONT_MEDIAN_SPLIT,
        /**
         * Continuous source: values greater than the threshold map to 1, others to 0.
         */
        CONT_THRESHOLD,
        /**
         * Continuous source: values at or below the qLow quantile map to 0, values at or above the qHigh quantile map
         * to 1, and values in between are treated as missing.
         */
        CONT_QUANTILE_BANDS,    // bottom qLow =>0, top qHigh =>1, middle => missing (-1)
        /**
         * Discrete source: category indices in the subset map to 1, others to 0.
         */
        DISC_SUBSET_VS_REST     // X in subset =>1 else 0
    }

    /**
     * The name of the derived treatment variable.
     */
    private final String derivedName;
    /**
     * The name of the source variable.
     */
    private final String sourceName;
    /**
     * The rule used to derive the treatment.
     */
    private final RuleType ruleType;

    /**
     * The threshold, used by the CONT_THRESHOLD rule.
     */
    private final double threshold;
    /**
     * The lower quantile, used by the CONT_QUANTILE_BANDS rule.
     */
    private final double qLow;
    /**
     * The upper quantile, used by the CONT_QUANTILE_BANDS rule.
     */
    private final double qHigh;
    /**
     * The set of category indices mapped to 1, used by the DISC_SUBSET_VS_REST rule.
     */
    private final Set<Integer> subset;

    /**
     * Constructs a new derived treatment specification. Parameters not used by the given rule type are ignored.
     *
     * @param derivedName the name of the derived treatment variable; must be non-empty after trimming
     * @param sourceName  the name of the source variable; must be non-empty after trimming
     * @param ruleType    the rule used to derive the treatment
     * @param threshold   the threshold, for CONT_THRESHOLD
     * @param qLow        the lower quantile, for CONT_QUANTILE_BANDS
     * @param qHigh       the upper quantile, for CONT_QUANTILE_BANDS
     * @param subset      the category indices mapped to 1, for DISC_SUBSET_VS_REST; may be null for other rules
     * @throws NullPointerException     if derivedName, sourceName or ruleType is null
     * @throws IllegalArgumentException if a name is empty, the quantiles do not satisfy 0 &lt; qLow &lt; qHigh &lt; 1
     *                                  for CONT_QUANTILE_BANDS, or the subset is empty for DISC_SUBSET_VS_REST
     */
    public DerivedTreatmentSpecV2(String derivedName,
                                  String sourceName,
                                  RuleType ruleType,
                                  double threshold,
                                  double qLow,
                                  double qHigh,
                                  Set<Integer> subset) {
        this.derivedName = Objects.requireNonNull(derivedName, "derivedName").trim();
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName").trim();
        this.ruleType = Objects.requireNonNull(ruleType, "ruleType");
        this.threshold = threshold;
        this.qLow = qLow;
        this.qHigh = qHigh;
        this.subset = (subset == null) ? Set.of() : new LinkedHashSet<>(subset);

        if (this.derivedName.isEmpty()) throw new IllegalArgumentException("v2.1: derivedName is empty.");
        if (this.sourceName.isEmpty()) throw new IllegalArgumentException("v2.1: sourceName is empty.");

        if (ruleType == RuleType.CONT_QUANTILE_BANDS) {
            if (!(0 < qLow && qLow < qHigh && qHigh < 1)) {
                throw new IllegalArgumentException("v2.1: quantiles must satisfy 0 < qLow < qHigh < 1.");
            }
        }
        if (ruleType == RuleType.DISC_SUBSET_VS_REST && this.subset.isEmpty()) {
            throw new IllegalArgumentException("v2.1: subset must not be empty.");
        }
    }

    /**
     * Returns the name of the derived treatment variable.
     *
     * @return the derived name
     */
    public String getDerivedName() { return derivedName; }
    /**
     * Returns the name of the source variable.
     *
     * @return the source name
     */
    public String getSourceName() { return sourceName; }
    /**
     * Returns the rule type.
     *
     * @return the rule type
     */
    public RuleType getRuleType() { return ruleType; }

    /**
     * Returns a short human-readable description of the rule and its parameters.
     *
     * @return the rule description
     */
    public String describeRule() {
        return switch (ruleType) {
            case CONT_MEDIAN_SPLIT -> "median split";
            case CONT_THRESHOLD -> "threshold: X > " + threshold;
            case CONT_QUANTILE_BANDS -> "quantile bands: qLow=" + qLow + ", qHigh=" + qHigh;
            case DISC_SUBSET_VS_REST -> "subset vs rest: " + subset;
        };
    }

    /**
     * v2.1: Returns vector aligned to data rows: {0,1} or -1 for missing/excluded.
     *
     * @param data the data set containing the source variable
     * @return an array with one entry per row of the data: 0, 1, or -1 for missing/excluded
     * @throws IllegalArgumentException if the source variable is not in the data, has the wrong type for the rule,
     *                                  or (for quantile-based rules) has no non-missing values
     */
    public int[] computeX01Full(DataSet data) {
        Objects.requireNonNull(data, "v2.1: data");

        Node src = data.getVariable(sourceName);
        if (src == null) throw new IllegalArgumentException("v2.1: unknown source variable: " + sourceName);

        int col = data.getColumnIndex(src);
        int n = data.getNumRows();
        int[] out = new int[n];
        Arrays.fill(out, -1);

        switch (ruleType) {
            case CONT_MEDIAN_SPLIT -> {
                ensureContinuous(src);
                double med = quantileNonMissing(data, col, 0.5);
                for (int r = 0; r < n; r++) {
                    double x = data.getDouble(r, col);
                    if (Double.isNaN(x)) continue;
                    out[r] = (x >= med) ? 1 : 0;
                }
            }
            case CONT_THRESHOLD -> {
                ensureContinuous(src);
                for (int r = 0; r < n; r++) {
                    double x = data.getDouble(r, col);
                    if (Double.isNaN(x)) continue;
                    out[r] = (x > threshold) ? 1 : 0;
                }
            }
            case CONT_QUANTILE_BANDS -> {
                ensureContinuous(src);
                double lo = quantileNonMissing(data, col, qLow);
                double hi = quantileNonMissing(data, col, qHigh);
                for (int r = 0; r < n; r++) {
                    double x = data.getDouble(r, col);
                    if (Double.isNaN(x)) continue;
                    if (x <= lo) out[r] = 0;
                    else if (x >= hi) out[r] = 1;
                    else out[r] = -1;
                }
            }
            case DISC_SUBSET_VS_REST -> {
                ensureDiscrete(src);
                for (int r = 0; r < n; r++) {
                    int v = data.getInt(r, col);
                    if (v < 0) continue;
                    out[r] = subset.contains(v) ? 1 : 0;
                }
            }
            default -> throw new IllegalStateException("v2.1: unhandled rule " + ruleType);
        }

        return out;
    }

    /**
     * Counts the number of 0, 1, and missing entries in the given derived treatment vector.
     *
     * @param x01Full a vector as returned by {@link #computeX01Full(DataSet)}
     * @return the counts
     */
    public Preview previewCounts(int[] x01Full) {
        int n0 = 0, n1 = 0, nm = 0;
        for (int v : x01Full) {
            if (v == 0) n0++;
            else if (v == 1) n1++;
            else nm++;
        }
        return new Preview(n0, n1, nm);
    }

    /**
     * Counts of 0, 1, and missing entries in a derived treatment vector.
     */
    public static final class Preview {
        /**
         * The counts of rows with treatment 0 (n0), treatment 1 (n1), and missing/excluded (nMissing).
         */
        public final int n0, n1, nMissing;
        /**
         * Constructs a new Preview.
         *
         * @param n0       the number of rows with treatment 0
         * @param n1       the number of rows with treatment 1
         * @param nMissing the number of missing/excluded rows
         */
        public Preview(int n0, int n1, int nMissing) {
            this.n0 = n0; this.n1 = n1; this.nMissing = nMissing;
        }
    }

    private static void ensureContinuous(Node v) {
        if (v instanceof DiscreteVariable) {
            throw new IllegalArgumentException("v2.1: source variable is discrete, but rule expects continuous.");
        }
    }

    private static void ensureDiscrete(Node v) {
        if (!(v instanceof DiscreteVariable)) {
            throw new IllegalArgumentException("v2.1: source variable is continuous, but rule expects discrete.");
        }
    }

    private static double quantileNonMissing(DataSet data, int col, double q) {
        double[] vals = collectNonMissing(data, col);
        if (vals.length == 0) throw new IllegalArgumentException("v2.1: no non-missing values in source.");
        Arrays.sort(vals);

        if (q <= 0) return vals[0];
        if (q >= 1) return vals[vals.length - 1];

        double pos = q * (vals.length - 1);
        int i = (int) TMath.floor(pos);
        int j = TMath.min(vals.length - 1, i + 1);
        double t = pos - i;
        return (1 - t) * vals[i] + t * vals[j];
    }

    private static double[] collectNonMissing(DataSet data, int col) {
        int n = data.getNumRows();
        double[] tmp = new double[n];
        int m = 0;
        for (int r = 0; r < n; r++) {
            double x = data.getDouble(r, col);
            if (!Double.isNaN(x)) tmp[m++] = x;
        }
        return Arrays.copyOf(tmp, m);
    }
}
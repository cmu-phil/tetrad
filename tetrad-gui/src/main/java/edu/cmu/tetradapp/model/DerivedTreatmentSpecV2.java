package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TMath;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

public final class DerivedTreatmentSpecV2 implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public enum RuleType {
        CONT_MEDIAN_SPLIT,
        CONT_THRESHOLD,
        CONT_QUANTILE_BANDS,    // bottom qLow =>0, top qHigh =>1, middle => missing (-1)
        DISC_SUBSET_VS_REST     // X in subset =>1 else 0
    }

    private final String derivedName;
    private final String sourceName;
    private final RuleType ruleType;

    private final double threshold;
    private final double qLow;
    private final double qHigh;
    private final Set<Integer> subset;

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

    public String getDerivedName() { return derivedName; }
    public String getSourceName() { return sourceName; }
    public RuleType getRuleType() { return ruleType; }

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

    public Preview previewCounts(int[] x01Full) {
        int n0 = 0, n1 = 0, nm = 0;
        for (int v : x01Full) {
            if (v == 0) n0++;
            else if (v == 1) n1++;
            else nm++;
        }
        return new Preview(n0, n1, nm);
    }

    public static final class Preview {
        public final int n0, n1, nMissing;
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
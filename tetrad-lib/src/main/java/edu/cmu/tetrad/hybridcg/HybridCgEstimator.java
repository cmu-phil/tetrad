// tetrad-lib/src/main/java/edu/cmu/tetrad/hybridcg/HybridCgEstimator.java
package edu.cmu.tetrad.hybridcg;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import edu.cmu.tetrad.util.Parameters;

import java.util.*;

/**
 * Estimator that prepares cutpoints (binning policy) and then runs MLE via HybridCgIm.HybridEstimator.
 *
 * Parameters (optional):
 *  - "hybridcg.alpha"           : double  (Dirichlet pseudocount for discrete CPTs; default 1.0)
 *  - "hybridcg.shareVariance"   : boolean (share one variance across rows for each cont child; default false)
 *  - "hybridcg.binPolicy"       : String  ("equal_frequency", "equal_interval", "none"; default "equal_frequency")
 *  - "hybridcg.bins"            : int     (#bins for each cont parent of a discrete child; default 3; min 2)
 */
public final class HybridCgEstimator {

    private HybridCgEstimator() {}

    public static HybridCgIm estimate(HybridCgPm pm, DataSet data, Parameters params) {
        Objects.requireNonNull(pm, "pm");
        Objects.requireNonNull(data, "data");
        if (params == null) params = new Parameters();

        final double alpha = params.getDouble("hybridcg.alpha", 1.0);
        final boolean shareVar = params.getBoolean("hybridcg.shareVariance", false);
        final String binPolicy = Optional.ofNullable(params.getString("hybridcg.binPolicy", "equal_frequency"))
                .orElse("equal_frequency").toLowerCase(Locale.ROOT);
        final int binsRequested = Math.max(2, params.getInt("hybridcg.bins", 3));

        // 1) If requested, set cutpoints for every discrete child that has >=1 continuous parent
        if (!"none".equals(binPolicy)) {
            setAllCutpoints(pm, data, binPolicy, binsRequested);
        }

        // 2) Run the existing MLE in HybridCgIm
        HybridCgIm.HybridEstimator mle = new HybridCgIm.HybridEstimator(alpha, shareVar);
        return mle.mle(pm, data);
    }

    // ---------- cutpoint helpers ----------

    private static void setAllCutpoints(HybridCgPm pm, DataSet data, String policy, int bins) {
        Node[] nodes = pm.getNodes();
        for (int y = 0; y < nodes.length; y++) {
            if (!pm.isDiscrete(y)) continue;
            int[] cps = pm.getContinuousParents(y);
            if (cps.length == 0) continue;

            Map<Node, double[]> cpMap = new LinkedHashMap<>();
            for (int t = 0; t < cps.length; t++) {
                Node p = nodes[cps[t]];
                double[] col = columnAsDoubles(data, p);
                double[] edges = switch (policy) {
                    case "equal_interval"  -> equalIntervalEdges(col, bins);
                    case "equal_frequency" -> equalFrequencyEdges(col, bins);
                    default -> throw new IllegalArgumentException("Unknown binPolicy: " + policy);
                };
                // Ensure strict increase (PM enforces strictly increasing)
                edges = strictlyIncreasingOrFallback(edges, col, bins);
                cpMap.put(p, edges);
            }
            pm.setContParentCutpointsForDiscreteChild(nodes[y], cpMap);
        }
    }

    /** Get a column as doubles (NaNs ignored by edge builders). */
    private static double[] columnAsDoubles(DataSet data, Node contVar) {
        int c = data.getColumn(contVar);
        if (c < 0) throw new IllegalArgumentException("Variable not in dataset: " + contVar);
        double[] out = new double[data.getNumRows()];
        for (int r = 0; r < out.length; r++) {
            out[r] = data.getDouble(r, c);
        }
        return out;
    }

    /** Equal-interval edges between min..max (length = bins-1). */
    private static double[] equalIntervalEdges(double[] raw, int bins) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (double v : raw) {
            if (Double.isNaN(v)) continue;
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (!(min < max)) {
            // degenerate; return simple ascending tiny steps
            double[] e = new double[Math.max(1, bins - 1)];
            for (int i = 0; i < e.length; i++) e[i] = min + i * Math.ulp(min == 0 ? 1.0 : min);
            return e;
        }
        double step = (max - min) / bins;
        double[] e = new double[bins - 1];
        for (int i = 0; i < e.length; i++) e[i] = min + (i + 1) * step;
        return e;
    }

    /** Equal-frequency edges by quantiles (length = bins-1). */
    private static double[] equalFrequencyEdges(double[] raw, int bins) {
        double[] data = Arrays.stream(raw).filter(d -> !Double.isNaN(d)).toArray();
        if (data.length == 0) {
            // fallback: arbitrary small steps
            return equalIntervalEdges(new double[]{0, 1}, bins);
        }
        Arrays.sort(data);

        int n = data.length;
        double[] e = new double[bins - 1];
        for (int k = 1; k <= bins - 1; k++) {
            double q = k / (double) bins;
            int idx = (int) Math.ceil(q * n) - 1;
            idx = Math.min(Math.max(idx, 0), n - 1);
            e[k - 1] = data[idx];
        }
        return e;
    }

    /** Make edges strictly increasing; if impossible (too many ties), fallback to equal-interval. */
    private static double[] strictlyIncreasingOrFallback(double[] edges, double[] col, int bins) {
        if (edges.length == 0) return edges;
        double[] out = edges.clone();
        boolean ok = true;
        for (int i = 1; i < out.length; i++) {
            if (!(out[i] > out[i - 1])) {
                ok = false; break;
            }
        }
        if (ok) return out;

        // Try to nudge ties upward by ulp
        for (int i = 1; i < out.length; i++) {
            if (!(out[i] > out[i - 1])) {
                out[i] = Math.nextUp(out[i - 1]);
            }
        }
        ok = true;
        for (int i = 1; i < out.length; i++) {
            if (!(out[i] > out[i - 1])) { ok = false; break; }
        }
        if (ok) return out;

        // Still not ok? fallback to equal-interval on this column
        return equalIntervalEdges(col, bins);
    }
}
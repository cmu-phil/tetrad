// tetrad-lib/src/main/java/edu/cmu/tetrad/hybridcg/HybridCgEstimator.java
package edu.cmu.tetrad.hybridcg;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import edu.cmu.tetrad.util.Parameters;

import java.util.*;

/**
 * Estimator that prepares cutpoints (binning policy) and then runs MLE via HybridCgIm.HybridEstimator.
 *
 * <p>Parameters (optional):</p>
 * <ul>
 *   <li><b>"hybridcg.alpha"</b> : double &mdash; Dirichlet pseudocount for discrete CPTs; default 1.0</li>
 *   <li><b>"hybridcg.shareVariance"</b> : boolean &mdash; share one variance across rows for each cont child; default false</li>
 *   <li><b>"hybridcg.binPolicy"</b> : String &mdash; "equal_frequency", "equal_interval", "none"; default "equal_frequency"</li>
 *   <li><b>"hybridcg.bins"</b> : int &mdash; number of bins for each cont parent of a discrete child; default 3; min 2</li>
 * </ul>
 */
public final class HybridCgEstimator {

    private HybridCgEstimator() {}

    /**
     * Estimates the parameters of a Hybrid Conditional Gaussian (HybridCG) Model using the given
     * parameter model (pm), data set, and optional parameters.
     *
     * @param pm the Hybrid Conditional Gaussian parameter model. Must not be null.
     * @param data the data set used for estimation. Must not be null.
     * @param params optional parameters for estimation, including settings for alpha, binning policy,
     *               shared variance, and number of bins. If null, defaults are used.
     * @return a HybridCgIm object that represents the estimated model.
     * @throws NullPointerException if pm or data is null.
     * @throws IllegalArgumentException if an invalid binning policy is provided.
     * @throws IllegalStateException if `binPolicy=none` is specified and the parameter model has no
     *                                cutpoints for discrete children with continuous parents.
     */
    public static HybridCgIm estimate(HybridCgPm pm, DataSet data, Parameters params) {
        Objects.requireNonNull(pm, "pm");
        Objects.requireNonNull(data, "data");

        // Build an aligned dataset so DataSet.getColumn(pmNode) works by identity
        DataSet aligned = alignDataVariablesToPm(pm, data);
        verifyAlignment(pm, aligned);

        if (params == null) params = new Parameters();
        final double alpha    = params.getDouble("hybridcg.alpha", 1.0);
        final boolean shareVar = params.getBoolean("hybridcg.shareVariance", false);
        final String binPolicy = Optional.ofNullable(params.getString("hybridcg.binPolicy", "equal_frequency"))
                .orElse("equal_frequency").toLowerCase(Locale.ROOT);

        switch (binPolicy) {
            case "equal_frequency":
            case "equal_interval":
            case "none":
                break;
            default:
                throw new IllegalArgumentException("Unknown binPolicy: " + binPolicy +
                                                   " (expected equal_frequency, equal_interval, or none)");
        }

        final int binsRequested = Math.max(2, params.getInt("hybridcg.bins", 3));

        if ("none".equals(binPolicy)) {
            // Ensure PM already carries cutpoints for any discrete child with continuous parents
            Node[] nodes = pm.getNodes();
            for (int y = 0; y < nodes.length; y++) {
                if (!pm.isDiscrete(y)) continue;
                if (pm.getContinuousParents(y).length == 0) continue;
                if (pm.getContParentCutpointsForDiscreteChild(y).isEmpty()) {
                    throw new IllegalStateException(
                            "binPolicy=none, but PM has no cutpoints for discrete child with continuous parents: "
                            + nodes[y].getName());
                }
            }
        } else {
            setAllCutpoints(pm, aligned, binPolicy, binsRequested);
        }

        HybridCgIm.HybridEstimator mle = new HybridCgIm.HybridEstimator(alpha, shareVar);
        return mle.mle(pm, aligned);
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

    private static void verifyAlignment(HybridCgPm pm, DataSet data) {
        var nodes = pm.getNodes();
        for (var v : nodes) {
            int col = data.getColumn(v);
            if (col < 0) {
                // Retry by name, safely
                var byNameVar = data.getVariable(v.getName());
                if (byNameVar == null) {
                    throw new IllegalArgumentException("Variable from PM not found in dataset: " + v.getName());
                }
                col = data.getColumn(byNameVar);
                if (col < 0) {
                    throw new IllegalArgumentException("Variable from PM not found in dataset: " + v.getName());
                }
            }
        }
    }

    private static double[] columnAsDoubles(DataSet data, Node contVar) {
        int c = data.getColumn(contVar);
        if (c < 0) {
            var byNameVar = data.getVariable(contVar.getName());
            if (byNameVar == null) {
                throw new IllegalArgumentException("Variable not in dataset: " + contVar.getName());
            }
            c = data.getColumn(byNameVar);
            if (c < 0) {
                throw new IllegalArgumentException("Variable not in dataset: " + contVar.getName());
            }
        }
        double[] out = new double[data.getNumRows()];
        for (int r = 0; r < out.length; r++) out[r] = data.getDouble(r, c);
        return out;
    }

    private static double[] equalIntervalEdges(double[] raw, int bins) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (double v : raw) {
            if (Double.isNaN(v)) continue;
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (!(min < max)) {
            // Degenerate column (all NaN or constant) — fallback to [0,1] base
            min = 0.0; max = 1.0;
        }
        double step = (max - min) / bins;
        double[] e = new double[bins - 1];
        for (int i = 0; i < e.length; i++) e[i] = min + (i + 1) * step;
        return e;
    }

    private static double[] strictlyIncreasingOrFallback(double[] edges, double[] col, int bins) {
        if (edges.length == 0) return equalIntervalEdges(col, bins);
        double[] out = edges.clone();

        // Nudge ties upward in one pass
        for (int i = 1; i < out.length; i++) {
            if (!(out[i] > out[i - 1])) {
                out[i] = Math.nextUp(out[i - 1]);
            }
        }
        for (int i = 1; i < out.length; i++) if (!(out[i] > out[i - 1])) {
            // Still not strictly increasing? fallback
            return equalIntervalEdges(col, bins);
        }
        return out;
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

    private static DataSet alignDataVariablesToPm(HybridCgPm pm, DataSet data) {
        if (!(data instanceof BoxDataSet box)) {
            throw new IllegalArgumentException("Expected a BoxDataSet, got " + data.getClass());
        }

        Map<String, Node> pmByName = new LinkedHashMap<>();
        for (Node v : pm.getNodes()) pmByName.put(v.getName(), v);

        List<Node> vars = new ArrayList<>(data.getNumColumns());
        for (int i = 0; i < data.getNumColumns(); i++) {
            Node dv = data.getVariable(i);
            Node pv = pmByName.getOrDefault(dv.getName(), dv);
            vars.add(pv);
        }

        return new BoxDataSet(box.getDataBox(), vars);
    }
}
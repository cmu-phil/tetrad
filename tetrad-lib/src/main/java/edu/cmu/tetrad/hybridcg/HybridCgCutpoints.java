package edu.cmu.tetrad.hybridcg;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.*;

/**
 * Utilities to populate HybridCgPm cutpoints for discrete children that have continuous parents.
 * Cutpoints are computed from a DataSet using either equal-interval or equal-frequency rules.
 */
public final class HybridCgCutpoints {

    public enum Method { EQUAL_INTERVALS, EQUAL_FREQUENCY }

    private HybridCgCutpoints() {}

    /**
     * For every DISCRETE child Y with ≥1 continuous parent, computes and installs cutpoints for each
     * continuous parent using the chosen method and desired number of bins.
     *
     * @param pm      existing HybridCgPm (unchanged except where cutpoints are set)
     * @param data    DataSet whose columns match pm.getNodes() (names)
     * @param bins    desired number of bins per continuous parent (>=2 → bins-1 cutpoints)
     * @param method  equal-intervals or equal-frequency
     */
    public static void setAll(HybridCgModel.HybridCgPm pm, DataSet data, int bins, Method method) {
        Objects.requireNonNull(pm, "pm");
        Objects.requireNonNull(data, "data");
        if (bins < 2) throw new IllegalArgumentException("bins must be >= 2");

        final Node[] nodes = pm.getNodes();

        // Build name -> column index once (robust to distinct Node instances)
        Map<String, Integer> colByName = new HashMap<>(data.getNumColumns() * 2);
        for (int j = 0; j < data.getNumColumns(); j++) {
            colByName.put(data.getVariable(j).getName(), j);
        }

        for (int y = 0; y < nodes.length; y++) {
            if (!pm.isDiscrete(y)) continue;

            int[] cps = pm.getContinuousParents(y);
            if (cps.length == 0) continue;

            Map<Node,double[]> cutsByParent = new LinkedHashMap<>();
            for (int t = 0; t < cps.length; t++) {
                Node parent = nodes[cps[t]];
                Integer cj = colByName.get(parent.getName());
                if (cj == null) {
                    throw new IllegalArgumentException("Node not found in DataSet: " + parent.getName());
                }

                double[] series = columnToArray(data, cj);
                double[] cuts = switch (method) {
                    case EQUAL_INTERVALS  -> equalIntervalCuts(series, bins);
                    case EQUAL_FREQUENCY  -> equalFrequencyCuts(series, bins);
                };
                cuts = strictlyIncreasingInterior(series, cuts);
                if (cuts.length == 0 && bins >= 2) {
                    double med = percentile(series, 0.5);
                    cuts = new double[]{ med };
                }
                cutsByParent.put(parent, cuts);
            }
            pm.setContParentCutpointsForDiscreteChild(nodes[y], cutsByParent);
        }
    }

    // ----------- Cutpoint calculators -----------

    private static double[] equalIntervalCuts(double[] x, int bins) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (double v : x) {
            if (Double.isFinite(v)) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        if (!(max > min)) return new double[0];
        double step = (max - min) / bins;
        double[] cuts = new double[bins - 1];
        for (int i = 1; i < bins; i++) cuts[i - 1] = min + i * step;
        return cuts;
    }

    private static double[] equalFrequencyCuts(double[] x, int bins) {
        double[] clean = Arrays.stream(x).filter(Double::isFinite).sorted().toArray();
        if (clean.length == 0) return new double[0];

        double[] cuts = new double[bins - 1];
        for (int i = 1; i < bins; i++) {
            double q = (double) i / bins;
            cuts[i - 1] = quantileFromSorted(clean, q);
        }
        return cuts;
    }

    // Remove duplicates, ensure strictly increasing, and keep only interior points
    private static double[] strictlyIncreasingInterior(double[] series, double[] rawCuts) {
        if (rawCuts.length == 0) return rawCuts.clone();
        // Sort, drop NaN/±Inf
        double[] c = Arrays.stream(rawCuts).filter(Double::isFinite).sorted().toArray();
        if (c.length == 0) return c;

        // Drop duplicates with a tiny epsilon separation
        final double EPS = 1e-12;
        List<Double> uniq = new ArrayList<>();
        double last = Double.NEGATIVE_INFINITY;
        for (double v : c) {
            if (uniq.isEmpty() || v - last > EPS) {
                uniq.add(v);
                last = v;
            }
        }
        // Also clamp to (min,max) so they’re truly interior
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (double v : series) {
            if (Double.isFinite(v)) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        if (!(max > min)) return new double[0];

        List<Double> interior = new ArrayList<>();
        for (double v : uniq) if (v > min && v < max) interior.add(v);

        double[] out = new double[interior.size()];
        for (int i = 0; i < out.length; i++) out[i] = interior.get(i);
        return out;
    }

    private static double quantileFromSorted(double[] sorted, double q) {
        if (sorted.length == 0) return Double.NaN;
        if (q <= 0) return sorted[0];
        if (q >= 1) return sorted[sorted.length - 1];
        double pos = q * (sorted.length - 1);
        int i = (int) Math.floor(pos);
        double frac = pos - i;
        if (i + 1 >= sorted.length) return sorted[i];
        return sorted[i] * (1 - frac) + sorted[i + 1] * frac;
    }

    private static double percentile(double[] x, double q) {
        double[] clean = Arrays.stream(x).filter(Double::isFinite).sorted().toArray();
        return quantileFromSorted(clean, q);
    }

    private static double[] columnToArray(DataSet data, int col) {
        int n = data.getNumRows();
        double[] out = new double[n];
        for (int r = 0; r < n; r++) out[r] = data.getDouble(r, col);
        return out;
    }
}
// TODO: adjust the package to match where BayesPmWrapper lives (often: edu.cmu.tetradapp.model)
package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.session.SessionModel;
import edu.cmu.tetradapp.session.SessionNode;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

/**
 * Wrapper for HybridCgModel.HybridCgPm.
 * - Holds the PM and metadata.
 * - Provides optional initialization of cutpoints from a DataSet (equal-intervals / equal-frequency).
 * - Reflection-friendly constructors.
 * - Editor-friendly setter to replace the PM.
 */
public class HybridCgPmWrapper implements SessionModel, Serializable {

    @Serial private static final long serialVersionUID = 1L;

    public enum CutMethod { EQUAL_INTERVALS, EQUAL_FREQUENCY }

    private HybridCgModel.HybridCgPm hybridCgPm;
    private String name = "Hybrid CG PM";
    private String notes = "";

    /** No-arg for reflection/persistence. */
    public HybridCgPmWrapper() { }

    /** Construct from an existing PM. */
    public HybridCgPmWrapper(HybridCgModel.HybridCgPm pm) {
        this.hybridCgPm = Objects.requireNonNull(pm, "hybridCgPm");
    }

    /**
     * Reflection-friendly: build a PM from a Graph + Parameters.
     * If no DataSet is available, you can optionally seed simple default cutpoints so an IM can be created.
     *
     * Params (all optional):
     *  - String  "modelName"               : display name
     *  - boolean "hybridcg.seedDefaults"   : if true (default), seed simple {-0.5, 0.5} cutpoints when data is unavailable
     */
    public HybridCgPmWrapper(Graph graph, Parameters params) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(params, "params");

        final List<Node> nodeOrder = new ArrayList<>(graph.getNodes());

        // Build type/category maps from node classes
        final Map<Node, Boolean> isDisc = new LinkedHashMap<>();
        final Map<Node, List<String>> cats  = new LinkedHashMap<>();

        for (Node v : nodeOrder) {
            boolean discrete = v instanceof DiscreteVariable;
            isDisc.put(v, discrete);
            cats.put(v, discrete ? ((DiscreteVariable) v).getCategories() : null);
        }

        this.hybridCgPm = new HybridCgModel.HybridCgPm(graph, nodeOrder, isDisc, cats);

        // Optionally seed simple default cutpoints so IM construction won't fail,
        // if there are discrete children with continuous parents and no data provided.
        if (params.getBoolean("hybridcg.seedDefaults", true)) {
            seedSimpleDefaultCutpoints(this.hybridCgPm);
        }

        String label = params.getString("modelName", null);
        if (label != null && !label.isBlank()) this.name = label;
    }

    /**
     * Convenience overload: build a PM from Graph + Parameters + DataSet,
     * computing cutpoints from data using equal-frequency (default) or equal-intervals.
     *
     * Params (all optional):
     *  - String  "modelName"             : display name
     *  - int     "hybridcg.cutBins"      : desired number of bins per continuous parent (default 3)
     *  - String  "hybridcg.cutMethod"    : "freq" or "intervals" (default "freq")
     *  - boolean "hybridcg.seedDefaults" : ignored here (we have data)
     */
    public HybridCgPmWrapper(Graph graph, Parameters params, DataSet data) {
        this(graph, params); // builds PM first
        int bins = Math.max(2, params.getInt("hybridcg.cutBins", 3));

        String m = params.getString("hybridcg.cutMethod", "freq").trim().toLowerCase(Locale.ROOT);
        CutMethod method = switch (m) {
            case "intervals", "equal_intervals", "equal-intervals" -> CutMethod.EQUAL_INTERVALS;
            default -> CutMethod.EQUAL_FREQUENCY;
        };

        if (data != null) {
            applyCutpointsFromDataInternal(this.hybridCgPm, data, bins, method);
        }
    }

    // ---------------- Accessors ----------------

    public HybridCgModel.HybridCgPm getHybridCgPm() { return hybridCgPm; }

    /** Used by the PM editor to replace the PM instance. */
    public void setHybridCgPm(HybridCgModel.HybridCgPm pm) { this.hybridCgPm = Objects.requireNonNull(pm); }

    public Graph getGraph() { return hybridCgPm == null ? null : hybridCgPm.getGraph(); }

    public String getName() { return name; }
    public void setName(String name) { this.name = (name == null || name.isBlank()) ? "Hybrid CG PM" : name; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = (notes == null) ? "" : notes; }

    @Override public String toString() {
        return "HybridCgPmWrapper{name='" + name + "', graph=" + getGraph() + "}";
    }

    // ---------------- Cutpoint helpers (public API) ----------------

    /**
     * Recompute and install cutpoints from a DataSet.
     * @param data  DataSet with columns matching PM node names
     * @param bins  desired number of bins (>=2)
     * @param method equal-frequency or equal-intervals
     */
    public void applyCutpointsFromData(DataSet data, int bins, CutMethod method) {
        Objects.requireNonNull(hybridCgPm, "PM is null");
        Objects.requireNonNull(data, "data");
        if (bins < 2) throw new IllegalArgumentException("bins must be >= 2");
        applyCutpointsFromDataInternal(hybridCgPm, data, bins, method == null ? CutMethod.EQUAL_FREQUENCY : method);
    }

    // ---------------- Internal implementations ----------------

    /** Seed simple default cutpoints when we have no DataSet handy. */
    private static void seedSimpleDefaultCutpoints(HybridCgModel.HybridCgPm pm) {
        Node[] nodes = pm.getNodes();
        for (int y = 0; y < nodes.length; y++) {
            if (!pm.isDiscrete(y)) continue;
            int[] cps = pm.getContinuousParents(y);
            if (cps.length == 0) continue;

            Map<Node, double[]> m = new LinkedHashMap<>();
            for (int t = 0; t < cps.length; t++) {
                Node p = nodes[cps[t]];
                // three bins by default: edges at -0.5, 0.5
                m.put(p, new double[]{ -0.5, 0.5 });
            }
            try {
                pm.setContParentCutpointsForDiscreteChild(nodes[y], m);
            } catch (RuntimeException ignore) {
                // If incompatible, skip; the user can set later from data.
            }
        }
    }

    private static void applyCutpointsFromDataInternal(HybridCgModel.HybridCgPm pm,
                                                       DataSet data,
                                                       int bins,
                                                       CutMethod method) {
        final Node[] nodes = pm.getNodes();

        // Build name → column index map once
        Map<Node, Integer> col = new HashMap<>();
        for (Node n : nodes) {
            int c = data.getColumn(n);
            if (c < 0) {
                // Allow mismatch silently, but you can throw if you prefer:
                // throw new IllegalArgumentException("Node not found in DataSet: " + n.getName());
                continue;
            }
            col.put(n, c);
        }

        for (int y = 0; y < nodes.length; y++) {
            if (!pm.isDiscrete(y)) continue;
            int[] cps = pm.getContinuousParents(y);
            if (cps.length == 0) continue;

            Map<Node, double[]> cuts = new LinkedHashMap<>();
            for (int t = 0; t < cps.length; t++) {
                Node p = nodes[cps[t]];
                Integer cj = col.get(p);
                if (cj == null) continue;

                double[] series = column(data, cj);
                double[] raw = (method == CutMethod.EQUAL_INTERVALS)
                        ? equalIntervalCuts(series, bins)
                        : equalFrequencyCuts(series, bins);

                double[] strict = strictlyIncreasingInterior(series, raw);

                // fallback if degenerate
                if (strict.length == 0 && bins >= 2) {
                    double med = quantile(series, 0.5);
                    strict = new double[]{ med }; // 2 bins
                }

                cuts.put(p, strict);
            }
            if (!cuts.isEmpty()) {
                try {
                    pm.setContParentCutpointsForDiscreteChild(nodes[y], cuts);
                } catch (RuntimeException ignore) {
                    // Incompatible shapes after type changes—skip; user can retry after adjusting PM.
                }
            }
        }
    }

    // --------- small numeric helpers (local, no external deps) ---------

    private static double[] column(DataSet data, int col) {
        int n = data.getNumRows();
        double[] out = new double[n];
        for (int r = 0; r < n; r++) out[r] = data.getDouble(r, col);
        return out;
    }

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
            cuts[i - 1] = quantileSorted(clean, q);
        }
        return cuts;
    }

    private static double[] strictlyIncreasingInterior(double[] series, double[] rawCuts) {
        if (rawCuts.length == 0) return rawCuts;
        double[] c = Arrays.stream(rawCuts).filter(Double::isFinite).sorted().toArray();
        if (c.length == 0) return c;

        final double EPS = 1e-12;
        List<Double> uniq = new ArrayList<>();
        double last = Double.NEGATIVE_INFINITY;
        for (double v : c) {
            if (uniq.isEmpty() || v - last > EPS) {
                uniq.add(v);
                last = v;
            }
        }

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

    private static double quantile(double[] x, double q) {
        double[] clean = Arrays.stream(x).filter(Double::isFinite).sorted().toArray();
        return quantileSorted(clean, q);
    }

    private static double quantileSorted(double[] sorted, double q) {
        if (sorted.length == 0) return Double.NaN;
        if (q <= 0) return sorted[0];
        if (q >= 1) return sorted[sorted.length - 1];
        double pos = q * (sorted.length - 1);
        int i = (int) Math.floor(pos);
        double frac = pos - i;
        if (i + 1 >= sorted.length) return sorted[i];
        return sorted[i] * (1 - frac) + sorted[i + 1] * frac;
    }
}
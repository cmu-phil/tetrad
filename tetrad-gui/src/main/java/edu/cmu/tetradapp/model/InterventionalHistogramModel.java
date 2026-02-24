package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.session.SessionModel;
import edu.cmu.tetradapp.util.WatchedProcess;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Prototype model for displaying a histogram for Y | do(X=...).
 *
 * IMPORTANT: The default sampler included here is a PLACEHOLDER:
 * it uses observational row-filtering on X assignments rather than true do().
 * Swap the sampler to:
 *   - RA/GAC adjustment-based resampling (DAG/CPDAG/MAG/PAG)
 *   - BN inference sampling from P(Y | do(X))
 *   - IPW/DR weighted resampling
 */
public final class InterventionalHistogramModel implements SessionModel {


    private String name = "Interventional Histogram";

    public interface InterventionalSampler {
        /** Return a pseudo-sample of Y values under the intervention spec. */
        double[] sampleY(DataSet data, Graph graph, Node y, Map<Node, Integer> doSpec, int n, Random rng);
    }

    // ---- inputs
    private final DataSet data;
    private final Graph graph;

    private String yName = "";
    private String doSpecText = "";  // e.g. "X10=0, X3=1"
    private int sampleSize = 5000;
    private int numBins = 9;
    private boolean removeZeroPoints = false;

    // ---- outputs
    private volatile double[] ySample = new double[0];
    private volatile DataSet ySampleDataSet = null;
    private volatile String statusMessage = "";

    // ---- strategy
    private InterventionalSampler sampler = new ObservationalFilterSampler(); // placeholder
    private final Random rng = new Random(1);

    public InterventionalHistogramModel(DataWrapper data, GraphSource graph) {
        this.data = Objects.requireNonNull((DataSet) data.getSelectedDataModel(), "data");
        this.graph = Objects.requireNonNull(graph.getGraph(), "graph");
    }

    public DataSet getData() { return data; }
    public Graph getGraph() { return graph; }

    public String getYName() { return yName; }
    public void setYName(String yName) { this.yName = safe(yName); }

    public String getDoSpecText() { return doSpecText; }
    public void setDoSpecText(String doSpecText) { this.doSpecText = safe(doSpecText); }

    public int getSampleSize() { return sampleSize; }
    public void setSampleSize(int sampleSize) {
        if (sampleSize < 100) throw new IllegalArgumentException("Sample size must be >= 100.");
        this.sampleSize = sampleSize;
    }

    public int getNumBins() { return numBins; }
    public void setNumBins(int numBins) {
        if (numBins < 2 || numBins > 200) throw new IllegalArgumentException("Bins must be 2..200.");
        this.numBins = numBins;
    }

    public boolean isRemoveZeroPoints() { return removeZeroPoints; }
    public void setRemoveZeroPoints(boolean removeZeroPoints) { this.removeZeroPoints = removeZeroPoints; }

    public String getStatusMessage() { return statusMessage; }

    public void setSampler(InterventionalSampler sampler) {
        this.sampler = Objects.requireNonNull(sampler, "sampler");
    }

    /** Latest interventional sample as a one-column DataSet named "Y*". */
    public DataSet getYSampleDataSet() { return ySampleDataSet; }

    /** Latest y* sample vector. */
    public double[] getYSample() { return ySample; }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {
        if (name == null) throw new IllegalArgumentException("Name cannot be null.");
        this.name = name;
    }

    /** Parse doSpecText into a map Node -> category index (discrete only, for now). */
    public Map<Node, Integer> parseDoSpec() {
        LinkedHashMap<Node, Integer> out = new LinkedHashMap<>();
        String s = safe(doSpecText).trim();
        if (s.isEmpty()) return out;

        // tokenize on comma/whitespace
        List<String> toks = tokenizeCsvWhitespace(s);

        for (String tok : toks) {
            String t = tok.trim();
            if (t.isEmpty()) continue;

            int eq = t.indexOf('=');
            if (eq <= 0 || eq >= t.length() - 1) {
                throw new IllegalArgumentException("Bad do() token: \"" + t + "\". Use X=0, Y=1, ...");
            }

            String name = t.substring(0, eq).trim();
            String valStr = t.substring(eq + 1).trim();

            Node x = graph.getNode(name);
            if (x == null) throw new IllegalArgumentException("Unknown variable in do(): " + name);

            int val;
            try {
                val = Integer.parseInt(valStr);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Bad value for " + name + ": \"" + valStr + "\" (expected integer category index).");
            }

            // sanity: require x to be discrete in the *data*
            Node dx = data.getVariable(name);
            if (!(dx instanceof DiscreteVariable)) {
                throw new IllegalArgumentException("Prototype do() currently supports discrete X only: " + name);
            }

            int k = ((DiscreteVariable) dx).getNumCategories();
            if (val < 0 || val >= k) throw new IllegalArgumentException("Value for " + name + " must be 0.." + (k - 1));

            out.put(x, val);
        }

        return out;
    }

    public Node resolveY() {
        String name = safe(yName).trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Please select Y.");
        Node y = graph.getNode(name);
        if (y == null) throw new IllegalArgumentException("Unknown Y: " + name);
        return y;
    }

    /** Run recompute in a WatchedProcess (same pattern as your other editors). */
    public void recomputeAsync(Runnable onFinish) {
        class P extends WatchedProcess {
            @Override public void watch() {
                try {
                    statusMessage = "";
                    Node y = resolveY();
                    Map<Node, Integer> doSpec = parseDoSpec();

                    double[] sample = sampler.sampleY(data, graph, y, doSpec, sampleSize, rng);

                    if (sample == null || sample.length == 0) {
                        ySample = new double[0];
                        ySampleDataSet = null;
                        statusMessage = "No sample produced.";
                    } else {
                        ySample = sample;
                        ySampleDataSet = oneColumnContinuous("Y*", sample);
                        statusMessage = "OK (n=" + sample.length + ")  [sampler=" + sampler.getClass().getSimpleName() + "]";
                    }
                } catch (Exception ex) {
                    statusMessage = ex.getMessage();
                    ySample = new double[0];
                    ySampleDataSet = null;
                    TetradLogger.getInstance().log("InterventionalHistogramModel error: " + ex);
                }

                javax.swing.SwingUtilities.invokeLater(onFinish);
            }
        }
        new P();
    }

    // ----------------------------
    // Placeholder sampler
    // ----------------------------

    /**
     * PLACEHOLDER: approximates do(X=...) by selecting rows where X matches (observational conditioning),
     * then bootstraps Y from those rows.
     *
     * This is wrong as causal semantics, but excellent for a UI prototype.
     * Replace with RA/GAC or BN inference sampling later.
     */
    public static final class ObservationalFilterSampler implements InterventionalSampler {
        @Override
        public double[] sampleY(DataSet data, Graph graph, Node yGraph, Map<Node, Integer> doSpec, int n, Random rng) {
            Node y = data.getVariable(yGraph.getName());
            if (y == null) throw new IllegalArgumentException("Y not found in data: " + yGraph.getName());

            // collect matching row indices
            int rows = data.getNumRows();
            int yCol = data.getColumn(y);

            List<Integer> candidates = new ArrayList<>(rows);

            for (int r = 0; r < rows; r++) {
                boolean ok = true;

                for (Map.Entry<Node, Integer> e : doSpec.entrySet()) {
                    Node xData = data.getVariable(e.getKey().getName());
                    if (xData == null) throw new IllegalArgumentException("X not found in data: " + e.getKey().getName());
                    int xCol = data.getColumn(xData);

                    if (!(xData instanceof DiscreteVariable)) {
                        throw new IllegalArgumentException("Prototype sampler supports discrete X only: " + xData.getName());
                    }

                    int want = e.getValue();
                    int got = data.getInt(r, xCol);
                    if (got != want) { ok = false; break; }
                }

                if (ok) candidates.add(r);
            }

            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("No rows match the (observational) filter. Try a different do() spec.");
            }

            double[] out = new double[n];
            for (int i = 0; i < n; i++) {
                int r = candidates.get(rng.nextInt(candidates.size()));
                if (y instanceof DiscreteVariable) {
                    out[i] = data.getInt(r, yCol);
                } else {
                    out[i] = data.getDouble(r, yCol);
                }
            }

            return out;
        }
    }

    // ----------------------------
    // Data helpers
    // ----------------------------

    /** Build a one-column continuous dataset using BoxDataSet/DoubleDataBox. */
    public static DataSet oneColumnContinuous(String name, double[] values) {
        ContinuousVariable v = new ContinuousVariable(name);
        List<Node> vars = Collections.singletonList(v);

        DoubleDataBox box = new DoubleDataBox(values.length, 1);
        for (int i = 0; i < values.length; i++) {
            box.set(i, 0, values[i]);
        }

        return new BoxDataSet(box, vars);
    }

    private static List<String> tokenizeCsvWhitespace(String s) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ',' || Character.isWhitespace(c)) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }

        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static String safe(String s) { return (s == null) ? "" : s; }
}
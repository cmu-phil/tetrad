package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.session.SessionModel;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetrad.util.TMath;

import java.io.Serial;
import java.util.*;

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


    /**
     * The name of this model.
     */
    private String name = "Interventional Histogram";

    /**
     * A strategy for drawing a pseudo-sample of Y values under an intervention do(X=x).
     */
    public interface InterventionalSampler {
        /**
         * Return a pseudo-sample of Y values under the intervention spec.
         *
         * @param data   the data set to sample from.
         * @param graph  the graph.
         * @param y      the outcome variable Y (a graph node).
         * @param doSpec the intervention, mapping each intervened-on variable to a category index.
         * @param n      the number of values to sample.
         * @param rng    the random number generator.
         * @return the sampled Y values.
         */
        double[] sampleY(DataSet data, Graph graph, Node y, Map<Node, Integer> doSpec, int n, Random rng);
    }

    // ---- inputs
    /**
     * The data set from which samples are drawn.
     */
    private final DataSet data;
    /**
     * The graph used to resolve variables and to choose adjustment variables.
     */
    private final Graph graph;

    /**
     * The name of the outcome variable Y.
     */
    private String yName = "";
    /**
     * The intervention specification text, e.g. "X10=0, X3=1".
     */
    private String doSpecText = "";  // e.g. "X10=0, X3=1"
    /**
     * The number of Y values to sample.
     */
    private int sampleSize = 5000;
    /**
     * The number of histogram bins.
     */
    private int numBins = 9;
    /**
     * Whether zero points should be removed from the display.
     */
    private boolean removeZeroPoints = false;

    // ---- outputs
    /**
     * The latest interventional sample of Y values.
     */
    private volatile double[] ySample = new double[0];
    /**
     * The latest interventional sample as a one-column data set, or null if none.
     */
    private volatile DataSet ySampleDataSet = null;
    /**
     * The status message from the most recent computation.
     */
    private volatile String statusMessage = "";

    // ---- strategy
//    private InterventionalSampler sampler = new ObservationalFilterSampler(); // placeholder
    /**
     * The sampler used to draw interventional samples.
     */
    private InterventionalSampler sampler = new ParentAdjustmentResampleSampler(); // prototype adjustment-style
    /**
     * The random number generator used for sampling.
     */
    private final Random rng = new Random(1);

    /**
     * Constructs a new model from the selected data set of the given data wrapper and the given graph.
     *
     * @param data  the data wrapper; its selected data model must be a DataSet.
     * @param graph the graph source.
     */
    public InterventionalHistogramModel(DataWrapper data, GraphSource graph) {
        this.data = Objects.requireNonNull((DataSet) data.getSelectedDataModel(), "data");
        this.graph = Objects.requireNonNull(graph.getGraph(), "graph");
    }

    /**
     * Returns the data set.
     *
     * @return the data set.
     */
    public DataSet getData() { return data; }
    /**
     * Returns the graph.
     *
     * @return the graph.
     */
    public Graph getGraph() { return graph; }

    /**
     * Returns the name of the outcome variable Y.
     *
     * @return the Y name.
     */
    public String getYName() { return yName; }
    /**
     * Sets the name of the outcome variable Y; null is treated as the empty string.
     *
     * @param yName the Y name.
     */
    public void setYName(String yName) { this.yName = safe(yName); }

    /**
     * Returns the intervention specification text.
     *
     * @return the do() specification text.
     */
    public String getDoSpecText() { return doSpecText; }
    /**
     * Sets the intervention specification text, e.g. "X10=0, X3=1"; null is treated as the empty string.
     *
     * @param doSpecText the do() specification text.
     */
    public void setDoSpecText(String doSpecText) { this.doSpecText = safe(doSpecText); }

    /**
     * Returns the number of Y values to sample.
     *
     * @return the sample size.
     */
    public int getSampleSize() { return sampleSize; }
    /**
     * Sets the number of Y values to sample.
     *
     * @param sampleSize the sample size; must be at least 100.
     * @throws IllegalArgumentException if the sample size is less than 100.
     */
    public void setSampleSize(int sampleSize) {
        if (sampleSize < 100) throw new IllegalArgumentException("Sample size must be >= 100.");
        this.sampleSize = sampleSize;
    }

    /**
     * Returns the number of histogram bins.
     *
     * @return the number of bins.
     */
    public int getNumBins() { return numBins; }
    /**
     * Sets the number of histogram bins.
     *
     * @param numBins the number of bins; must be between 2 and 200.
     * @throws IllegalArgumentException if the number of bins is out of range.
     */
    public void setNumBins(int numBins) {
        if (numBins < 2 || numBins > 200) throw new IllegalArgumentException("Bins must be 2..200.");
        this.numBins = numBins;
    }

    /**
     * Returns whether zero points should be removed from the display.
     *
     * @return true if zero points should be removed.
     */
    public boolean isRemoveZeroPoints() { return removeZeroPoints; }
    /**
     * Sets whether zero points should be removed from the display.
     *
     * @param removeZeroPoints true if zero points should be removed.
     */
    public void setRemoveZeroPoints(boolean removeZeroPoints) { this.removeZeroPoints = removeZeroPoints; }

    /**
     * Returns the status message from the most recent computation.
     *
     * @return the status message.
     */
    public String getStatusMessage() { return statusMessage; }

    /**
     * Sets the sampler used to draw interventional samples.
     *
     * @param sampler the sampler; must not be null.
     */
    public void setSampler(InterventionalSampler sampler) {
        this.sampler = Objects.requireNonNull(sampler, "sampler");
    }

    /**
     * Latest interventional sample as a one-column DataSet named "Y*".
     *
     * @return the sample data set, or null if none is available.
     */
    public DataSet getYSampleDataSet() { return ySampleDataSet; }

    /**
     * Latest y* sample vector.
     *
     * @return the sample vector.
     */
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

    /**
     * Parse doSpecText into a map Node -> category index (discrete only, for now).
     *
     * @return the map from intervened-on nodes to category indices; empty if no intervention is specified.
     * @throws IllegalArgumentException if the text is malformed, names an unknown or non-discrete variable, or
     *                                  gives an out-of-range category index.
     */
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

    /**
     * Resolves the outcome variable Y in the graph from the Y name.
     *
     * @return the node for Y.
     * @throws IllegalArgumentException if no Y is selected or Y is not in the graph.
     */
    public Node resolveY() {
        String name = safe(yName).trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Please select Y.");
        Node y = graph.getNode(name);
        if (y == null) throw new IllegalArgumentException("Unknown Y: " + name);
        return y;
    }

    /**
     * Run recompute in a WatchedProcess (same pattern as your other editors).
     *
     * @param onFinish a callback run on the Swing event thread when the computation finishes.
     */
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

                        Node yData = data.getVariable(y.getName());
                        if (yData instanceof DiscreteVariable dy) {
                            ySampleDataSet = oneColumnDiscrete("Y*", dy, sample);
                        } else {
                            ySampleDataSet = oneColumnContinuous("Y*", sample);
                        }

                        StringBuilder msg = new StringBuilder("OK (n=" + sample.length + ")  [sampler="
                                + sampler.getClass().getSimpleName() + "]");

                        if (sampler instanceof ParentAdjustmentResampleSampler p) {
                            int fb = p.getLastFallbackCount();
                            if (fb > 0) {
                                msg.append(String.format("  fallback to X-only: %d/%d (%.1f%%)",
                                        fb, sample.length, 100.0 * fb / sample.length));
                            }
                            List<String> ignored = p.getLastIgnoredContinuousZ();
                            if (!ignored.isEmpty()) {
                                msg.append("  dropped continuous Z: ").append(String.join(", ", ignored));
                            }
                        }

                        statusMessage = msg.toString();
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
    public static final class ObservationalFilterSampler implements InterventionalSampler, TetradSerializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new observational filter sampler.
         */
        public ObservationalFilterSampler() {
        }

        @Override
        public double[] sampleY(DataSet data, Graph graph, Node yGraph, Map<Node, Integer> doSpec, int n, Random rng) {
            Node y = data.getVariable(yGraph.getName());
            if (y == null) throw new IllegalArgumentException("Y not found in data: " + yGraph.getName());

            // collect matching row indices
            int rows = data.getNumRows();
            int yCol = data.getColumnIndex(y);

            List<Integer> candidates = new ArrayList<>(rows);

            for (int r = 0; r < rows; r++) {
                boolean ok = true;

                for (Map.Entry<Node, Integer> e : doSpec.entrySet()) {
                    Node xData = data.getVariable(e.getKey().getName());
                    if (xData == null) throw new IllegalArgumentException("X not found in data: " + e.getKey().getName());
                    int xCol = data.getColumnIndex(xData);

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

    /**
     * Prototype causal-ish sampler using a simple adjustment heuristic:
     *
     *   Z := union of parents of each intervened-on variable X in the *graph*
     *
     * Then approximates:
     *   P(Y | do(X=x)) = sum_z P(Y | X=x, Z=z) P(Z=z)
     *
     * by resampling:
     *   - sample z from empirical rows (P(Z))
     *   - then sample Y from rows matching (X=x, Z=z) (P(Y|X,Z))
     *
     * Notes / limitations:
     *  - This is still a PROTOTYPE. It is *not* guaranteed to be a valid adjustment set
     *    in general graphs, and it does not (yet) use RA/GAC.
     *  - For now it requires that all Z variables are DISCRETE (exact matching).
     *    Continuous Z are ignored with a log warning (easy to extend to binning).
     */
    public static final class ParentAdjustmentResampleSampler implements InterventionalSampler, TetradSerializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** If true, fall back to ignoring Z when no (X,Z)-matching rows exist. */
        private final boolean fallbackIgnoreZ;

        /** Max attempts to find a matching row for each draw before falling back. */
        private final int maxAttemptsPerDraw;

        /** Number of draws in the most recent sampleY call that fell back to X-only matching. Not serialized. */
        private transient int lastFallbackCount;

        /** Continuous adjustment variables dropped in the most recent sampleY call. Not serialized. */
        private transient List<String> lastIgnoredContinuousZ = new ArrayList<>();

        /**
         * Constructs a sampler that falls back to ignoring Z and makes up to 100 attempts per draw.
         */
        public ParentAdjustmentResampleSampler() {
            this(true, 100);
        }

        /**
         * Constructs a sampler with the given fallback behavior and attempt limit.
         *
         * @param fallbackIgnoreZ    whether to fall back to ignoring Z when no (X, Z)-matching row is found.
         * @param maxAttemptsPerDraw the maximum attempts to find a matching row per draw; at least 1 is used.
         */
        public ParentAdjustmentResampleSampler(boolean fallbackIgnoreZ, int maxAttemptsPerDraw) {
            this.fallbackIgnoreZ = fallbackIgnoreZ;
            this.maxAttemptsPerDraw = TMath.max(1, maxAttemptsPerDraw);
        }

        @Override
        public double[] sampleY(DataSet data,
                                Graph graph,
                                Node yGraph,
                                Map<Node, Integer> doSpec,
                                int n,
                                Random rng) {

            this.lastFallbackCount = 0;
            this.lastIgnoredContinuousZ = new ArrayList<>();

            if (doSpec == null || doSpec.isEmpty()) {
                // No intervention specified -> just bootstrap Y observationally.
                return bootstrapYFromAllRows(data, yGraph, n, rng);
            }

            Node y = requireDataVar(data, yGraph.getName());
            int yCol = data.getColumnIndex(y);

            // --- build Z = union of parents of intervened X's
            LinkedHashSet<String> zNames = new LinkedHashSet<>();
            for (Node xGraph : doSpec.keySet()) {
                for (Node p : graph.getParents(xGraph)) {
                    if (p == null) continue;
                    String pn = p.getName();
                    if (pn.equals(yGraph.getName())) continue;
                    if (doSpec.keySet().stream().anyMatch(xx -> xx != null && pn.equals(xx.getName()))) continue; // don't include X itself
                    zNames.add(pn);
                }
            }

            // Resolve Z in data; keep only discrete Z for exact matching
            List<DiscreteVariable> zVars = new ArrayList<>();
            List<Integer> zCols = new ArrayList<>();

            for (String zn : zNames) {
                Node z = data.getVariable(zn);
                if (z == null) continue;

                if (z instanceof DiscreteVariable dz) {
                    zVars.add(dz);
                    zCols.add(data.getColumnIndex(dz));
                } else {
                    lastIgnoredContinuousZ.add(zn);
                    TetradLogger.getInstance().warn(
                            "InterventionalHistogram: ignoring continuous Z (prototype exact-matching sampler): " + zn);
                }
            }

            // Resolve X vars in data
            List<DiscreteVariable> xVars = new ArrayList<>();
            List<Integer> xCols = new ArrayList<>();
            List<Integer> xWant = new ArrayList<>();

            for (Map.Entry<Node, Integer> ent : doSpec.entrySet()) {
                String xn = ent.getKey().getName();
                Node x = requireDataVar(data, xn);
                if (!(x instanceof DiscreteVariable dx)) {
                    throw new IllegalArgumentException("This sampler requires discrete X: " + xn);
                }
                int want = ent.getValue();
                int k = dx.getNumCategories();
                if (want < 0 || want >= k) throw new IllegalArgumentException("Bad do() value for " + xn + ": " + want);

                xVars.add(dx);
                xCols.add(data.getColumnIndex(dx));
                xWant.add(want);
            }

            int rows = data.getNumRows();

            // Precompute candidate rows matching X only (faster fallback, and also used when Z is empty)
            int[] xOnlyCandidates = rowsMatchingX(data, xCols, xWant);

            if (xOnlyCandidates.length == 0) {
                throw new IllegalArgumentException("No rows match X assignment (even observationally). Try a different do() spec.");
            }

            double[] out = new double[n];

            // For each draw:
            //  (a) sample Z values by picking a random row rZ
            //  (b) find a row r that matches (X and those Z values)
            for (int i = 0; i < n; i++) {

                // if no Z, just sample from X-only candidates
                if (zVars.isEmpty()) {
                    int r = xOnlyCandidates[rng.nextInt(xOnlyCandidates.length)];
                    out[i] = readY(data, y, yCol, r);
                    continue;
                }

                // Step (a): pick rZ to define z-values
                int rZ = rng.nextInt(rows);
                int[] zWant = new int[zVars.size()];
                for (int j = 0; j < zVars.size(); j++) {
                    zWant[j] = data.getInt(rZ, zCols.get(j));
                }

                // Step (b): find a row matching (X, Z)
                int r = findRowMatchingXZ(data, xCols, xWant, zCols, zWant, rng, maxAttemptsPerDraw);

                if (r < 0) {
                    lastFallbackCount++;

                    if (fallbackIgnoreZ) {
                        // fall back to X-only
                        r = xOnlyCandidates[rng.nextInt(xOnlyCandidates.length)];
                    } else {
                        // if you prefer: set NaN or throw; for UI, fallback is nicer
                        r = xOnlyCandidates[rng.nextInt(xOnlyCandidates.length)];
                    }
                }

                out[i] = readY(data, y, yCol, r);
            }

            return out;
        }

        /**
         * Number of draws in the most recent {@link #sampleY} call that fell back to X-only matching
         * because no row matched the (X, Z) stratum within the attempt limit.
         *
         * @return the number of fallback draws.
         */
        public int getLastFallbackCount() {
            return lastFallbackCount;
        }

        /**
         * Continuous adjustment variables dropped in the most recent {@link #sampleY} call. This
         * sampler matches Z exactly and so cannot use continuous Z; dropping a genuine confounder
         * biases the result toward the observational conditional.
         *
         * @return the names of the dropped continuous variables.
         */
        public List<String> getLastIgnoredContinuousZ() {
            return lastIgnoredContinuousZ == null ? Collections.emptyList() : lastIgnoredContinuousZ;
        }

        private static Node requireDataVar(DataSet data, String name) {
            Node v = data.getVariable(name);
            if (v == null) throw new IllegalArgumentException("Variable not found in data: " + name);
            return v;
        }

        private static double readY(DataSet data, Node y, int yCol, int row) {
            if (y instanceof DiscreteVariable) return data.getInt(row, yCol);
            return data.getDouble(row, yCol);
        }

        private static double[] bootstrapYFromAllRows(DataSet data, Node yGraph, int n, Random rng) {
            Node y = data.getVariable(yGraph.getName());
            if (y == null) throw new IllegalArgumentException("Y not found in data: " + yGraph.getName());
            int yCol = data.getColumnIndex(y);

            int rows = data.getNumRows();
            double[] out = new double[n];
            for (int i = 0; i < n; i++) {
                int r = rng.nextInt(rows);
                out[i] = (y instanceof DiscreteVariable) ? data.getInt(r, yCol) : data.getDouble(r, yCol);
            }
            return out;
        }

        private static int[] rowsMatchingX(DataSet data, List<Integer> xCols, List<Integer> xWant) {
            int rows = data.getNumRows();
            IntArrayList matches = new IntArrayList(rows);

            for (int r = 0; r < rows; r++) {
                boolean ok = true;
                for (int j = 0; j < xCols.size(); j++) {
                    int got = data.getInt(r, xCols.get(j));
                    if (got != xWant.get(j)) { ok = false; break; }
                }
                if (ok) matches.add(r);
            }

            return matches.toArray();
        }

        private static int findRowMatchingXZ(DataSet data,
                                             List<Integer> xCols, List<Integer> xWant,
                                             List<Integer> zCols, int[] zWant,
                                             Random rng,
                                             int maxAttempts) {
            int rows = data.getNumRows();

            // Randomized rejection sampling over rows
            for (int t = 0; t < maxAttempts; t++) {
                int r = rng.nextInt(rows);

                boolean ok = true;

                for (int j = 0; j < xCols.size(); j++) {
                    if (data.getInt(r, xCols.get(j)) != xWant.get(j)) { ok = false; break; }
                }
                if (!ok) continue;

                for (int j = 0; j < zCols.size(); j++) {
                    if (data.getInt(r, zCols.get(j)) != zWant[j]) { ok = false; break; }
                }
                if (ok) return r;
            }

            return -1;
        }

        /**
         * Tiny int list helper to avoid pulling in extra deps.
         */
        private static final class IntArrayList {
            private int[] a;
            private int n;

            IntArrayList(int cap) { a = new int[TMath.max(16, cap)]; }

            void add(int v) {
                if (n == a.length) a = Arrays.copyOf(a, a.length * 2);
                a[n++] = v;
            }

            int[] toArray() { return Arrays.copyOf(a, n); }
        }
    }

    // ----------------------------
    // Data helpers
    // ----------------------------

    /**
     * Build a one-column continuous dataset using BoxDataSet/DoubleDataBox.
     *
     * @param name   the name of the variable.
     * @param values the values.
     * @return the data set.
     */
    public static DataSet oneColumnContinuous(String name, double[] values) {
        ContinuousVariable v = new ContinuousVariable(name);
        List<Node> vars = Collections.singletonList(v);

        DoubleDataBox box = new DoubleDataBox(values.length, 1);
        for (int i = 0; i < values.length; i++) {
            box.set(i, 0, values[i]);
        }

        return new BoxDataSet(box, vars);
    }

    /**
     * Build a one-column discrete dataset whose variable copies the prototype's categories, so the
     * histogram shows category names rather than integer codes.
     *
     * @param name      the name of the variable.
     * @param prototype the discrete variable whose categories are copied.
     * @param values    the category indices, as doubles.
     * @return the data set.
     */
    public static DataSet oneColumnDiscrete(String name, DiscreteVariable prototype, double[] values) {
        DiscreteVariable v = new DiscreteVariable(name, prototype.getCategories());
        List<Node> vars = Collections.singletonList(v);

        VerticalIntDataBox box = new VerticalIntDataBox(values.length, 1);
        for (int i = 0; i < values.length; i++) {
            box.set(i, 0, (int) values[i]);
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
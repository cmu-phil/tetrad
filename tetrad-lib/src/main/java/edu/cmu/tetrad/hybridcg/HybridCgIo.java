package edu.cmu.tetrad.hybridcg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Saves and loads {@link HybridCgIm} models (parameters plus the full parametric model they sit on) as JSON text.
 *
 * <p>The file is self-describing: it records, per node, the type, the category labels for discrete nodes, the
 * discrete and continuous parent lists <i>in the order used at save time</i>, cutpoints for continuous parents of
 * discrete children, and the local parameter table. Row indices in the parameter tables are mixed-radix over the
 * recorded parent order (discrete parents first, then, for discrete children, one binned position per continuous
 * parent), leftmost position most significant — the same convention as
 * {@link HybridCgPm#getRowIndex(int, int[], int[])}.</p>
 *
 * <p>Because the parent order of a freshly reconstructed PM is not guaranteed to match the order recorded in the
 * file, {@link #fromJson(String)} remaps every row and every coefficient column by parent <i>name</i> rather than by
 * position. A file therefore remains valid regardless of how the graph orders parents internally after
 * reconstruction.</p>
 *
 * <p>Format sketch (version 1):</p>
 *
 * <pre>{@code
 * {
 *   "format": "hybridcg-im",
 *   "version": 1,
 *   "nodes": [
 *     {
 *       "name": "X3",
 *       "discrete": true,
 *       "x": 120, "y": 80,
 *       "categories": ["low", "high"],
 *       "discreteParents": ["D1"],
 *       "continuousParents": ["C1"],
 *       "cutpoints": [[-0.5, 0.5]],
 *       "probs": [[0.7, 0.3], ...]
 *     },
 *     {
 *       "name": "C2",
 *       "discrete": false,
 *       "discreteParents": ["D1"],
 *       "continuousParents": ["C1"],
 *       "params": [[intercept, coefC1, variance], ...]
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>NaN and infinite values (an unset variance, say) are written as bare {@code NaN} / {@code Infinity} tokens.
 * This is a common JSON extension accepted by Gson and by Python's {@code json} module, though not by strict
 * parsers.</p>
 */
public final class HybridCgIo {

    /**
     * The format tag written to and required of every file.
     */
    public static final String FORMAT = "hybridcg-im";

    /**
     * The current format version.
     */
    public static final int VERSION = 1;

    private HybridCgIo() {
    }

    // ============================== Save ==============================

    /**
     * Renders the given model as pretty-printed JSON.
     *
     * @param im the model
     * @return the JSON text
     */
    public static String toJson(HybridCgIm im) {
        Objects.requireNonNull(im, "im");
        HybridCgPm pm = im.getPm();
        Node[] nodes = pm.getNodes();

        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("version", VERSION);

        JsonArray nodeArray = new JsonArray();

        for (int y = 0; y < nodes.length; y++) {
            JsonObject o = new JsonObject();
            o.addProperty("name", nodes[y].getName());
            o.addProperty("discrete", pm.isDiscrete(y));
            o.addProperty("x", nodes[y].getCenterX());
            o.addProperty("y", nodes[y].getCenterY());

            if (pm.isDiscrete(y)) {
                JsonArray cats = new JsonArray();
                for (String c : pm.getCategories(y)) cats.add(c);
                o.add("categories", cats);
            }

            o.add("discreteParents", nameArray(pm, pm.getDiscreteParents(y)));
            o.add("continuousParents", nameArray(pm, pm.getContinuousParents(y)));

            int rows = pm.getNumRows(y);

            if (pm.isDiscrete(y)) {
                if (pm.getContinuousParents(y).length > 0) {
                    double[][] cuts = pm.getContParentCutpointsForDiscreteChild(y).orElseThrow(
                            () -> new IllegalStateException("Cutpoints unset for discrete child "
                                                            + "with continuous parents."));
                    JsonArray cutsArray = new JsonArray();
                    for (double[] c : cuts) {
                        JsonArray one = new JsonArray();
                        for (double v : c) one.add(v);
                        cutsArray.add(one);
                    }
                    o.add("cutpoints", cutsArray);
                }

                int card = pm.getCardinality(y);
                JsonArray probs = new JsonArray();
                for (int r = 0; r < rows; r++) {
                    JsonArray row = new JsonArray();
                    for (int c = 0; c < card; c++) row.add(im.getProbability(y, r, c));
                    probs.add(row);
                }
                o.add("probs", probs);
            } else {
                int m = pm.getContinuousParents(y).length;
                JsonArray params = new JsonArray();
                for (int r = 0; r < rows; r++) {
                    JsonArray row = new JsonArray();
                    row.add(im.getMean(y, r));
                    for (int j = 0; j < m; j++) row.add(im.getCoefficient(y, r, j));
                    row.add(im.getVariance(y, r));
                    params.add(row);
                }
                o.add("params", params);
            }

            nodeArray.add(o);
        }

        root.add("nodes", nodeArray);

        Gson gson = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
        return gson.toJson(root);
    }

    /**
     * Saves the given model to a file as JSON (UTF-8).
     *
     * @param im   the model
     * @param file the destination file
     * @throws IOException if the file cannot be written
     */
    public static void save(HybridCgIm im, File file) throws IOException {
        Files.writeString(file.toPath(), toJson(im), StandardCharsets.UTF_8);
    }

    // ============================== Load ==============================

    /**
     * Reconstructs a model from JSON text produced by {@link #toJson(HybridCgIm)} (or written by hand or from Python
     * in the same format). Rows and coefficient columns are remapped by parent name, so the file's recorded parent
     * order need not match the reconstructed PM's internal order.
     *
     * @param json the JSON text
     * @return the reconstructed model
     * @throws IllegalArgumentException if the text is not a valid version-1 hybrid CG model file
     */
    public static HybridCgIm fromJson(String json) {
        Objects.requireNonNull(json, "json");

        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Not parseable as JSON: " + e.getMessage(), e);
        }

        String format = root.has("format") ? root.get("format").getAsString() : null;
        if (!FORMAT.equals(format)) {
            throw new IllegalArgumentException("Not a hybrid CG model file (format tag = " + format + ").");
        }
        int version = root.has("version") ? root.get("version").getAsInt() : -1;
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported hybrid CG model file version: " + version);
        }

        JsonArray nodeArray = requireArray(root, "nodes", "file");

        // ---- Pass 1: nodes ----
        List<Node> nodeOrder = new ArrayList<>();
        Map<String, Node> byName = new LinkedHashMap<>();
        Map<String, JsonObject> entryByName = new LinkedHashMap<>();
        Map<String, Boolean> flags = new HashMap<>();
        Map<String, List<String>> catsByName = new HashMap<>();

        for (JsonElement el : nodeArray) {
            JsonObject o = el.getAsJsonObject();
            String name = requireString(o, "name");
            if (byName.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate node name in file: " + name);
            }
            boolean discrete = requireBoolean(o, "discrete", name);

            Node node;
            if (discrete) {
                List<String> cats = stringList(requireArray(o, "categories", name));
                if (cats.size() < 2) {
                    throw new IllegalArgumentException("Discrete node " + name + " needs at least 2 categories.");
                }
                catsByName.put(name, cats);
                node = new DiscreteVariable(name, cats);
            } else {
                node = new ContinuousVariable(name);
            }

            nodeOrder.add(node);
            byName.put(name, node);
            entryByName.put(name, o);
            flags.put(name, discrete);
        }

        // ---- Pass 2: graph, with parent-typing validation ----
        Graph dag = new EdgeListGraph();
        for (Node n : nodeOrder) dag.addNode(n);

        for (Node child : nodeOrder) {
            JsonObject o = entryByName.get(child.getName());
            List<String> dps = stringList(requireArray(o, "discreteParents", child.getName()));
            List<String> cps = stringList(requireArray(o, "continuousParents", child.getName()));

            Set<String> seen = new HashSet<>();
            for (String p : dps) {
                checkParent(byName, flags, child.getName(), p, true, seen);
                dag.addDirectedEdge(byName.get(p), child);
            }
            for (String p : cps) {
                checkParent(byName, flags, child.getName(), p, false, seen);
                dag.addDirectedEdge(byName.get(p), child);
            }
        }

        if (dag.paths().existsDirectedCycle()) {
            throw new IllegalArgumentException("The parent lists in the file imply a directed cycle.");
        }

        // ---- Display positions: use saved centers if every node has them; otherwise lay out fresh ----
        boolean allPositioned = true;
        for (Node n : nodeOrder) {
            JsonObject o = entryByName.get(n.getName());
            if (o.has("x") && o.has("y")) {
                n.setCenter(o.get("x").getAsInt(), o.get("y").getAsInt());
            } else {
                allPositioned = false;
            }
        }
        if (!allPositioned) {
            LayoutUtil.defaultLayout(dag);
        }

        // ---- PM ----
        Map<Node, Boolean> discreteFlags = new HashMap<>();
        Map<Node, List<String>> categoryMap = new HashMap<>();
        for (Node n : nodeOrder) {
            discreteFlags.put(n, flags.get(n.getName()));
            if (flags.get(n.getName())) categoryMap.put(n, catsByName.get(n.getName()));
        }

        HybridCgPm pm = new HybridCgPm(dag, nodeOrder, discreteFlags, categoryMap);

        // ---- Cutpoints (must precede any row-count computation for discrete children) ----
        for (Node child : nodeOrder) {
            JsonObject o = entryByName.get(child.getName());
            if (!flags.get(child.getName())) continue;
            List<String> cps = stringList(o.getAsJsonArray("continuousParents"));
            if (cps.isEmpty()) continue;

            JsonArray cutsArray = requireArray(o, "cutpoints", child.getName());
            if (cutsArray.size() != cps.size()) {
                throw new IllegalArgumentException("Node " + child.getName() + ": cutpoints count ("
                                                   + cutsArray.size() + ") does not match continuous parent count ("
                                                   + cps.size() + ").");
            }
            Map<Node, double[]> cutMap = new HashMap<>();
            for (int t = 0; t < cps.size(); t++) {
                cutMap.put(byName.get(cps.get(t)), doubleArray(cutsArray.get(t).getAsJsonArray()));
            }
            pm.setContParentCutpointsForDiscreteChild(child, cutMap);
        }

        // ---- IM, with name-keyed row/column remapping ----
        HybridCgIm im = new HybridCgIm(pm);
        Node[] pmNodes = pm.getNodes();

        for (int y = 0; y < pmNodes.length; y++) {
            String name = pmNodes[y].getName();
            JsonObject o = entryByName.get(name);

            List<String> savedDisc = stringList(o.getAsJsonArray("discreteParents"));
            List<String> savedCont = stringList(o.getAsJsonArray("continuousParents"));

            // Saved row-position sequence and dims, in the file's recorded order.
            List<String> savedSeq = new ArrayList<>(savedDisc);
            List<Integer> savedDimsList = new ArrayList<>();
            for (String p : savedDisc) savedDimsList.add(catsByName.get(p).size());

            if (pm.isDiscrete(y) && !savedCont.isEmpty()) {
                JsonArray cutsArray = o.getAsJsonArray("cutpoints");
                for (int t = 0; t < savedCont.size(); t++) {
                    savedSeq.add(savedCont.get(t));
                    savedDimsList.add(cutsArray.get(t).getAsJsonArray().size() + 1);
                }
            }

            int savedRows = 1;
            for (int d : savedDimsList) savedRows *= d;

            // New row-position sequence, matching pm.getRowDims(y).
            List<String> newSeq = new ArrayList<>();
            for (int p : pm.getDiscreteParents(y)) newSeq.add(pmNodes[p].getName());
            if (pm.isDiscrete(y)) {
                for (int p : pm.getContinuousParents(y)) newSeq.add(pmNodes[p].getName());
            }
            int[] newDims = pm.getRowDims(y);
            int numRows = pm.getNumRows(y);

            if (savedRows != numRows) {
                throw new IllegalArgumentException("Node " + name + ": file implies " + savedRows
                                                   + " rows but the reconstructed model has " + numRows + ".");
            }

            if (pm.isDiscrete(y)) {
                int card = pm.getCardinality(y);
                double[][] probs = doubleTable(requireArray(o, "probs", name), name, "probs", savedRows, card);

                for (int rNew = 0; rNew < numRows; rNew++) {
                    int rSaved = remapRow(rNew, newDims, newSeq, savedDimsList, savedSeq);
                    for (int c = 0; c < card; c++) im.setProbability(y, rNew, c, probs[rSaved][c]);
                }
            } else {
                int m = pm.getContinuousParents(y).length;
                double[][] params = doubleTable(requireArray(o, "params", name), name, "params", savedRows, m + 2);

                // Coefficient column of each continuous parent in the saved table, by name.
                Map<String, Integer> savedCoefCol = new HashMap<>();
                for (int j = 0; j < savedCont.size(); j++) savedCoefCol.put(savedCont.get(j), 1 + j);

                int[] newCont = pm.getContinuousParents(y);

                for (int rNew = 0; rNew < numRows; rNew++) {
                    int rSaved = remapRow(rNew, newDims, newSeq, savedDimsList, savedSeq);
                    im.setMean(y, rNew, params[rSaved][0]);
                    for (int j = 0; j < m; j++) {
                        Integer col = savedCoefCol.get(pmNodes[newCont[j]].getName());
                        if (col == null) {
                            throw new IllegalArgumentException("Node " + name + ": no saved coefficient for parent "
                                                               + pmNodes[newCont[j]].getName() + ".");
                        }
                        im.setCoefficient(y, rNew, j, params[rSaved][col]);
                    }
                    im.setVariance(y, rNew, params[rSaved][m + 1]);
                }
            }
        }

        return im;
    }

    /**
     * Loads a model from a JSON file (UTF-8).
     *
     * @param file the file to read
     * @return the reconstructed model
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the contents are not a valid model file
     */
    public static HybridCgIm load(File file) throws IOException {
        return fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    // ============================== Helpers ==============================

    /**
     * Decodes a row index over the new dims/sequence into per-parent values, then re-encodes it over the saved
     * dims/sequence. Both encodings are mixed-radix with the leftmost position most significant.
     */
    private static int remapRow(int rNew, int[] newDims, List<String> newSeq,
                                List<Integer> savedDims, List<String> savedSeq) {
        Map<String, Integer> valueByName = new HashMap<>();
        int rem = rNew;
        for (int k = newDims.length - 1; k >= 0; k--) {
            valueByName.put(newSeq.get(k), rem % newDims[k]);
            rem /= newDims[k];
        }

        int rSaved = 0;
        for (int k = 0; k < savedSeq.size(); k++) {
            Integer v = valueByName.get(savedSeq.get(k));
            if (v == null) {
                throw new IllegalArgumentException("Parent " + savedSeq.get(k)
                                                   + " in file has no counterpart in the reconstructed model.");
            }
            rSaved = rSaved * savedDims.get(k) + v;
        }
        return rSaved;
    }

    private static void checkParent(Map<String, Node> byName, Map<String, Boolean> flags,
                                    String child, String parent, boolean expectDiscrete, Set<String> seen) {
        if (!byName.containsKey(parent)) {
            throw new IllegalArgumentException("Node " + child + " lists unknown parent " + parent + ".");
        }
        if (!seen.add(parent)) {
            throw new IllegalArgumentException("Node " + child + " lists parent " + parent + " more than once.");
        }
        if (flags.get(parent) != expectDiscrete) {
            throw new IllegalArgumentException("Node " + child + " lists parent " + parent + " as "
                                               + (expectDiscrete ? "discrete" : "continuous")
                                               + " but it is declared otherwise.");
        }
    }

    private static JsonArray nameArray(HybridCgPm pm, int[] indices) {
        JsonArray a = new JsonArray();
        for (int i : indices) a.add(pm.getNodes()[i].getName());
        return a;
    }

    private static JsonArray requireArray(JsonObject o, String key, String where) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            throw new IllegalArgumentException("Missing array \"" + key + "\" for " + where + ".");
        }
        return o.getAsJsonArray(key);
    }

    private static String requireString(JsonObject o, String key) {
        if (!o.has(key)) throw new IllegalArgumentException("Missing \"" + key + "\" in node entry.");
        return o.get(key).getAsString();
    }

    private static boolean requireBoolean(JsonObject o, String key, String where) {
        if (!o.has(key)) throw new IllegalArgumentException("Missing \"" + key + "\" for " + where + ".");
        return o.get(key).getAsBoolean();
    }

    private static List<String> stringList(JsonArray a) {
        List<String> out = new ArrayList<>(a.size());
        for (JsonElement el : a) out.add(el.getAsString());
        return out;
    }

    private static double[] doubleArray(JsonArray a) {
        double[] out = new double[a.size()];
        for (int i = 0; i < a.size(); i++) out[i] = a.get(i).getAsDouble();
        return out;
    }

    private static double[][] doubleTable(JsonArray a, String node, String key, int rows, int cols) {
        if (a.size() != rows) {
            throw new IllegalArgumentException("Node " + node + ": \"" + key + "\" has " + a.size()
                                               + " rows; expected " + rows + ".");
        }
        double[][] out = new double[rows][];
        for (int r = 0; r < rows; r++) {
            JsonArray row = a.get(r).getAsJsonArray();
            if (row.size() != cols) {
                throw new IllegalArgumentException("Node " + node + ": \"" + key + "\" row " + r + " has "
                                                   + row.size() + " entries; expected " + cols + ".");
            }
            out[r] = doubleArray(row);
        }
        return out;
    }
}

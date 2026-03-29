package edu.cmu.tetrad.algcomparison.algorithm.other;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.PythonResource;
import edu.cmu.tetrad.util.TetradLogger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps the causal-learn GIN (Generalized Independent Noise) algorithm,
 * invoking it as a one-shot Python subprocess via {@code gin_runner.py} and
 * translating the returned {@code GeneralGraph} into a Tetrad {@link Graph}.
 *
 * <p>GIN discovers causal structure among observed variables in the presence
 * of latent confounders, under a Linear Non-Gaussian Latent Hierarchical Model
 * (LiNGLaH).  It returns a graph containing both the original observed nodes
 * and any latent nodes it infers; latent nodes are marked with
 * {@link NodeType#LATENT}.
 *
 * <p>Reference: Xie, F., Cai, R., Huang, B., Glymour, C., Hao, Z., &amp;
 * Zhang, K. (2020). Generalized independent noise condition for estimating
 * latent variable causal graphs. NeurIPS 33, 14891–14902.
 *
 * <h3>Required {@link Parameters}</h3>
 * <ul>
 *   <li>{@code Params.PYTHON_EXE} — path to the Python executable
 *       (e.g., {@code /usr/bin/python3}).  Defaults to the venv path in
 *       {@code DEFAULT_PYTHON_EXE} if not set.</li>
 * </ul>
 *
 * <h3>Optional {@link Parameters}</h3>
 * <ul>
 *   <li>{@value #GIN_INDEP_TEST} — independence test used internally by GIN:
 *       {@code "kci"} (default) or {@code "hsic"}</li>
 *   <li>{@code Params.ALPHA} — significance level (default 0.05).  Note:
 *       causal-learn's GIN does not currently expose alpha as a direct
 *       parameter; it is passed to the script for forward compatibility.</li>
 * </ul>
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "CL-GIN",
        command = "cl-gin",
        algoType = AlgType.search_for_structure_over_latents
)
@Bootstrapping
public class PythonGin implements Algorithm {

    @Serial
    private static final long serialVersionUID = 1L;

    // -----------------------------------------------------------------------
    // Parameter key constants and bundled-resource paths
    // -----------------------------------------------------------------------

    /**
     * Independence test method passed to causal-learn GIN.
     * Supported values: {@code "kci"} (default), {@code "hsic"}.
     */
    public static final String GIN_INDEP_TEST = "ginIndepTestMethod";

    // Bundled script constants — mirrors the pattern in ClKciPython.
    private static final String DEFAULT_PYTHON_EXE  =
            "/Users/josephramsey/venvs/kci/bin/python";
    private static final String BUNDLED_RESOURCE    = "python/python_gin.py";
    private static final String BUNDLED_CACHE_NAME  = "python_gin.py";

    // -----------------------------------------------------------------------
    // Algorithm interface — search
    // -----------------------------------------------------------------------

    /**
     * Runs GIN on the supplied dataset and returns the resulting causal graph.
     *
     * <p>Execution steps:
     * <ol>
     *   <li>Write the dataset to a temporary CSV file.</li>
     *   <li>Spawn {@code gin_runner.py} as a subprocess, passing the CSV path,
     *       independence-test method, and alpha.</li>
     *   <li>Read the JSON graph description from the process's stdout.</li>
     *   <li>Translate nodes and edges into a Tetrad {@link Graph}, marking any
     *       latent nodes discovered by GIN with {@link NodeType#LATENT}.</li>
     * </ol>
     *
     * @param dataModel the dataset; must be a continuous {@link DataSet}
     * @param parameters algorithm parameters (see class Javadoc)
     * @return the causal graph returned by GIN
     * @throws IllegalArgumentException if {@code dataModel} is not a {@link DataSet}
     * @throws RuntimeException         wrapping any {@link IOException} or
     *                                  {@link InterruptedException} from the subprocess
     */
    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException("PythonGin requires a tabular DataSet.");
        }

        // -----------------------------------------------------------------
        // 1. Resolve Python executable (same as ClKciPython)
        // -----------------------------------------------------------------
        String pythonExe = parameters.getString(Params.PYTHON_EXE, DEFAULT_PYTHON_EXE);
        if (pythonExe == null || pythonExe.isBlank()) {
            pythonExe = DEFAULT_PYTHON_EXE;
        }

        // -----------------------------------------------------------------
        // 2. Resolve script path — extract bundled resource to user cache
        //    (same mechanism as ClKciPython / kci_server.py)
        // -----------------------------------------------------------------
        String scriptPath;
        try {
            java.nio.file.Path extracted = PythonResource.extractToUserCache(
                    BUNDLED_RESOURCE, BUNDLED_CACHE_NAME);
            scriptPath = extracted.toAbsolutePath().toString();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to extract bundled GIN script from resource: "
                            + BUNDLED_RESOURCE, e);
        }

        // -----------------------------------------------------------------
        // 3. Other parameters
        // -----------------------------------------------------------------
        String indepTest =  "kci";//parameters.getString(GIN_INDEP_TEST, "kci");
        if (indepTest == null || indepTest.isBlank()) indepTest = "kci";

        double alpha = parameters.getDouble(Params.ALPHA, 0.05);

        File csvFile = null;
        try {
            csvFile = writeDataSetToTempCsv(dataSet);

            String jsonOutput = runGinScript(
                    pythonExe, scriptPath,
                    csvFile.getAbsolutePath(),
                    indepTest, alpha);

            return parseGinOutput(jsonOutput, dataSet);

        } catch (IOException | InterruptedException e) {
            System.out.println("PythonGin failed: " + e.getMessage());

            throw new RuntimeException("PythonGin failed: " + e.getMessage(), e);
        } finally {
            if (csvFile != null && csvFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                csvFile.delete();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Subprocess execution
    // -----------------------------------------------------------------------

    /**
     * Spawns {@code gin_runner.py} as a subprocess and returns all of its stdout
     * as a single string.
     *
     * <p>stderr is merged into stdout (via {@code redirectErrorStream(true)}) so
     * that Python warnings and tracebacks are captured alongside the JSON output.
     */
    private static String runGinScript(String pythonExe, String scriptPath,
                                       String csvPath, String indepTest, double alpha)
            throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder(
                pythonExe, "-u", scriptPath,
                csvPath,
                indepTest,
                String.valueOf(alpha)
        );

        pb.redirectErrorStream(true);   // merge stderr -> stdout

        // Run with the script's directory as cwd so relative imports work
        File scriptFile = new File(scriptPath);
        File wd = scriptFile.getParentFile();
        if (wd != null && wd.isDirectory()) {
            pb.directory(wd);
        }

        TetradLogger.getInstance().log("[PythonGin] command: "
                + String.join(" | ", pb.command()));

        Process proc = pb.start();

        // Drain stdout (which includes stderr due to redirectErrorStream)
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }

        int exitCode = proc.waitFor();
        String output = sb.toString().trim();

        TetradLogger.getInstance().log("[PythonGin] exit=" + exitCode
                + "  output-length=" + output.length());

        // A non-zero exit is only fatal if there is also no parseable JSON
        if (exitCode != 0 && !output.contains("\"ok\"")) {
            throw new IOException(
                    "gin_runner.py exited with code " + exitCode + ":\n" + output);
        }

        return output;
    }

    // -----------------------------------------------------------------------
    // JSON → Tetrad Graph translation
    // -----------------------------------------------------------------------

    /**
     * Parses the JSON produced by {@code gin_runner.py} and constructs a
     * Tetrad {@link Graph}.
     *
     * <p>The method searches for the <em>last</em> line in {@code rawOutput} that
     * starts with {@code '{'}.  This tolerates Python printing deprecation
     * warnings or other diagnostic lines before the JSON payload.
     *
     * @param rawOutput full stdout captured from the subprocess
     * @param dataSet   the original dataset (used only for variable-name
     *                  reference; not mutated)
     * @return the translated Tetrad graph
     * @throws IOException if JSON parsing fails or the Python side reported an error
     */
    private static Graph parseGinOutput(String rawOutput, DataSet dataSet)
            throws IOException {

        // Find the last output line that looks like a JSON object.
        // gin_runner.py may be preceded by numpy/sklearn import warnings.
        String jsonLine = null;
        for (String line : rawOutput.split("\n")) {
            line = line.trim();
            if (line.startsWith("{")) {
                jsonLine = line;
            }
        }

        if (jsonLine == null) {
            throw new IOException(
                    "No JSON object found in gin_runner.py output:\n" + rawOutput);
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonLine);

        // Check for Python-side error
        if (!root.path("ok").asBoolean(false)) {
            String err = root.path("error").asText("(unknown error)");
            String tb  = root.path("traceback").asText("");
            throw new IOException("GIN Python error: " + err
                    + (tb.isEmpty() ? "" : "\n" + tb));
        }

        // ------------------------------------------------------------------
        // Build node map: tetrad name -> Graph node
        // Observed nodes reuse their type (MEASURED); latent nodes are LATENT.
        // ------------------------------------------------------------------
        Map<String, Node> nodeMap = new LinkedHashMap<>();

        for (JsonNode nodeJson : root.path("nodes")) {
            String  name     = nodeJson.path("name").asText();
            boolean isLatent = nodeJson.path("latent").asBoolean(false);

            GraphNode node = new GraphNode(name);
            node.setNodeType(isLatent ? NodeType.LATENT : NodeType.MEASURED);
            nodeMap.put(name, node);
        }

        Graph graph = new EdgeListGraph(new ArrayList<>(nodeMap.values()));

        // ------------------------------------------------------------------
        // Translate edges
        //
        // causal-learn stores endpoint1 at node1 and endpoint2 at node2,
        // which is exactly Tetrad's Edge(node1, node2, ep1, ep2) convention.
        //
        // Examples:
        //   ep1=TAIL,   ep2=ARROW  →  node1 --> node2  (directed)
        //   ep1=ARROW,  ep2=ARROW  →  node1 <-> node2  (bidirected)
        //   ep1=TAIL,   ep2=TAIL   →  node1 --- node2  (undirected)
        //   ep1=CIRCLE, ep2=ARROW  →  node1 o-> node2  (partial ancestral)
        //   ep1=CIRCLE, ep2=CIRCLE →  node1 o-o node2  (nondirected)
        // ------------------------------------------------------------------
        for (JsonNode edgeJson : root.path("edges")) {
            String name1 = edgeJson.path("node1").asText();
            String ep1s  = edgeJson.path("endpoint1").asText();
            String name2 = edgeJson.path("node2").asText();
            String ep2s  = edgeJson.path("endpoint2").asText();

            Node n1 = nodeMap.get(name1);
            Node n2 = nodeMap.get(name2);

            if (n1 == null || n2 == null) {
                TetradLogger.getInstance().log(
                        "[PythonGin] Skipping edge with unknown node(s): "
                                + name1 + " -- " + name2);
                continue;
            }

            Endpoint ep1 = parseEndpoint(ep1s);
            Endpoint ep2 = parseEndpoint(ep2s);

            graph.addEdge(new Edge(n1, n2, ep1, ep2));
        }

        return graph;
    }

    /**
     * Converts an endpoint string from the Python JSON output to a Tetrad
     * {@link Endpoint} enum value.
     *
     * @param s one of {@code "TAIL"}, {@code "ARROW"}, {@code "CIRCLE"}
     * @return the corresponding {@link Endpoint}; defaults to {@link Endpoint#TAIL}
     *         for unrecognised strings (with a warning logged)
     */
    private static Endpoint parseEndpoint(String s) {
        return switch (s) {
            case "TAIL"   -> Endpoint.TAIL;
            case "ARROW"  -> Endpoint.ARROW;
            case "CIRCLE" -> Endpoint.CIRCLE;
            default -> {
                TetradLogger.getInstance().log(
                        "[PythonGin] Unrecognised endpoint '" + s + "'; defaulting to TAIL.");
                yield Endpoint.TAIL;
            }
        };
    }

    // -----------------------------------------------------------------------
    // CSV serialization (mirrors ProcessPythonCiService)
    // -----------------------------------------------------------------------

    /**
     * Writes {@code data} to a temporary CSV file with a header row.
     * The file is registered for deletion on JVM exit as a safety net, but
     * the caller is responsible for deleting it promptly after use.
     *
     * @param data the dataset to serialize
     * @return the temporary file
     * @throws IOException if the file cannot be created or written
     */
    private static File writeDataSetToTempCsv(DataSet data) throws IOException {
        File f = Files.createTempFile("tetrad_gin_", ".csv").toFile();
        f.deleteOnExit();

        try (BufferedWriter w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            // Header row
            List<Node> vars = data.getVariables();
            for (int j = 0; j < vars.size(); j++) {
                if (j > 0) w.write(",");
                w.write(vars.get(j).getName());
            }
            w.write("\n");

            // Data rows
            int n = data.getNumRows();
            int p = data.getNumColumns();
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < p; j++) {
                    if (j > 0) w.write(",");
                    double v = data.getDouble(i, j);
                    w.write(Double.isNaN(v) ? "nan" : Double.toString(v));
                }
                w.write("\n");
            }
        }

        return f;
    }

    // -----------------------------------------------------------------------
    // Algorithm interface — metadata
    // -----------------------------------------------------------------------

    /**
     * Returns a copy of the supplied graph for use as a comparison baseline.
     *
     * @param graph the graph to copy
     * @return a new {@link EdgeListGraph} with the same nodes and edges
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * Returns a human-readable description of this algorithm.
     *
     * @return description string
     */
    @Override
    public String getDescription() {
        return "GIN (Generalized Independent Noise) via Python / causal-learn.";//
//                + "Discovers latent hierarchical causal structure (LiNGLaH model). "
//                + "Requires gin_runner.py and a causal-learn installation.";
    }

    /**
     * GIN operates on continuous (non-Gaussian) data.
     *
     * @return {@link DataType#Continuous}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Returns the names of all {@link Parameters} consumed by this algorithm.
     *
     * @return list of parameter key strings
     */
    @Override
    public List<String> getParameters() {
        return Arrays.asList(Params.PYTHON_EXE, GIN_INDEP_TEST, Params.ALPHA);
    }
}

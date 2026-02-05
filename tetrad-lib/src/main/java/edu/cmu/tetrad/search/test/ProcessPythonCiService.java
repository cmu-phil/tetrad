package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spawns a persistent Python process running kci_server.py and communicates using JSONL over stdin/stdout.
 *
 * Intended for experimentation / profiling, not a hardened production transport.
 */
public final class ProcessPythonCiService implements PythonCiService {

    private final String pythonExe;
    private final String serverScriptPath;

    private Process proc;
    private BufferedWriter toPy;
    private BufferedReader fromPy;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile boolean verbose;

    private volatile Map<String, Object> params = new HashMap<>();
    private File csvTemp;

    /**
     * Constructs a new instance of the ProcessPythonCiService class, initializing
     * the service with the specified Python executable and server script path.
     *
     * @param pythonExe the path to the Python executable to be used by this service;
     *                  must not be null.
     * @param serverScriptPath the path to the server script to be executed by the Python
     *                         process; must not be null.
     */
    public ProcessPythonCiService(String pythonExe, String serverScriptPath) {
        this.pythonExe = Objects.requireNonNull(pythonExe, "pythonExe");
        this.serverScriptPath = Objects.requireNonNull(serverScriptPath, "serverScriptPath");
    }

    /**
     * Enables or disables verbose output for the service. When verbose mode is enabled,
     * additional details about the process execution may be logged for debugging or
     * informational purposes.
     *
     * @param verbose a boolean indicating whether verbose mode should be enabled (true)
     *                or disabled (false)
     */
    @Override
    public synchronized void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Initializes the service and its underlying resources if not already initialized.
     * This method ensures the setup required for executing Python-based conditional
     * independence tests is complete. Initialization involves preparing a temporary
     * CSV file from the provided dataset, starting the Python process, and configuring
     * the server with the necessary parameters.
     *
     * The method is thread-safe and skips initialization if it has already been completed.
     *
     * @param data the dataset to be used for the initialization; must not be null.
     * @param vars a list of {@code Node} objects representing variables; may be null.
     * @param kciParams a map of key-value pairs representing initialization parameters;
     *                  may be null, in which case an empty map is used.
     * @throws RuntimeException if an error occurs while setting up the server or resources.
     */
    @Override
    public synchronized void initializeIfNeeded(DataSet data, List<Node> vars, Map<String, Object> kciParams) {
        if (initialized.get()) return;

        this.params = (kciParams == null) ? new HashMap<>() : new HashMap<>(kciParams);

        try {
            this.csvTemp = writeDataSetToTempCsv(data);

            startProcess();

            // Read initial ready line
            Map<String, String> ready = readJsonLine();
            if (!"true".equalsIgnoreCase(ready.get("ok"))) {
                throw new IOException("Python server not ready: " + ready);
            }

            // init message
            String init = "{"
                    + "\"op\":\"init\","
                    + "\"csv_path\":" + jsonString(csvTemp.getAbsolutePath()) + ","
                    + "\"verbose\":" + (verbose ? "true" : "false") + ","
                    + "\"params\":" + jsonObject(params)
                    + "}";

            writeJsonLine(init);

            Map<String, String> resp = readJsonLine();
            if (!"true".equalsIgnoreCase(resp.get("ok"))) {
                throw new IOException("init failed: " + resp);
            }

            initialized.set(true);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Updates the service's internal parameters with the provided key-value pairs.
     * This method ensures that the update operation is propagated to the Python process
     * if the service has been initialized. If the parameters are null, an empty map is used instead.
     *
     * In case of communication failure or an unsuccessful update acknowledgment from
     * the Python process, a {@code RuntimeException} is thrown.
     *
     * This method is thread-safe.
     *
     * @param kciParams a map containing key-value pairs to be used as the new parameters;
     *                  may be null, in which case the parameters are reset to an empty map.
     * @throws RuntimeException if there is an I/O issue or the update operation is rejected
     *                          by the Python process.
     */
    @Override
    public synchronized void updateParams(Map<String, Object> kciParams) {
        this.params = (kciParams == null) ? new HashMap<>() : new HashMap<>(kciParams);

        if (!initialized.get()) return;

        try {
            String msg = "{"
                    + "\"op\":\"update_params\","
                    + "\"params\":" + jsonObject(params)
                    + "}";

            writeJsonLine(msg);
            Map<String, String> resp = readJsonLine();
            if (!"true".equalsIgnoreCase(resp.get("ok"))) {
                throw new IOException("update_params failed: " + resp);
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Calculates the p-value for testing conditional independence between two variables (x and y),
     * with respect to a set of conditional variables (z), given a significance level (alpha).
     * This method communicates with an external Python process to perform the underlying statistical computation.
     *
     * @param xIndex the index of the first variable (x) to be tested.
     * @param yIndex the index of the second variable (y) to be tested.
     * @param zIndices an array of indices representing the conditional variables (z).
     *                 If the array is empty or null, no conditioning is performed.
     * @param alpha the significance level for the test. Typical values are in the range (0, 1).
     * @return the computed p-value as a double. If an error occurs or the computation cannot be completed,
     *         {@code Double.NaN} is returned.
     * @throws IllegalStateException if the service has not been initialized.
     * @throws RuntimeException if there is an issue during communication with the Python process or the computation fails.
     */
    @Override
    public synchronized double pValue(int xIndex, int yIndex, int[] zIndices, double alpha) {
        if (!initialized.get()) {
            throw new IllegalStateException("ProcessPythonCiService not initialized");
        }
        try {
            String z = intArrayJson(zIndices);

            String msg = "{"
                    + "\"op\":\"pvalue\","
                    + "\"x\":" + xIndex + ","
                    + "\"y\":" + yIndex + ","
                    + "\"z\":" + z
                    + "}";

            writeJsonLine(msg);
            Map<String, String> resp = readJsonLine();

            if (!"true".equalsIgnoreCase(resp.get("ok"))) {
                throw new IOException("pvalue failed: " + resp);
            }

            String pStr = resp.get("p");
            return (pStr == null) ? Double.NaN : Double.parseDouble(pStr);

        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    /**
     * Closes the resources associated with the ProcessPythonCiService instance and terminates
     * the underlying Python process if it is running. This method performs the cleanup of
     * internal streams, the Python process reference, and any temporary files created during
     * the instance's lifecycle.
     *
     * The method ensures a best-effort cleanup, including the deletion of the temporary CSV
     * file if it exists. It also attempts a graceful termination of the Python process by
     * sending a "close" operation before forcefully destroying the process, if necessary.
     *
     * Thread safety is guaranteed as the method is synchronized. Any errors during resource
     * cleanup are intentionally ignored to avoid throwing exceptions during the closing stage.
     *
     * @throws IOException if an I/O error occurs during the closing operation.
     */
    @Override
    public synchronized void close() throws IOException {
        initialized.set(false);

        if (proc != null) {
            try {
                // try graceful close
                if (toPy != null) {
                    writeJsonLine("{\"op\":\"close\"}");
                }
            } catch (Exception ignored) {
            }

            try {
                proc.destroy();
            } catch (Exception ignored) {
            }
        }

        proc = null;
        toPy = null;
        fromPy = null;

        if (csvTemp != null && csvTemp.exists()) {
            // best effort cleanup
            //noinspection ResultOfMethodCallIgnored
            csvTemp.delete();
            csvTemp = null;
        }
    }

    // ------------------- internals -------------------

    private void startProcess() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(pythonExe, "-u", serverScriptPath);
        pb.redirectErrorStream(true); // merge stderr -> stdout so we see stack traces
        proc = pb.start();

        toPy = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8));
        fromPy = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
    }

    private void writeJsonLine(String json) throws IOException {
        toPy.write(json);
        toPy.write("\n");
        toPy.flush();
    }

    private Map<String, String> readJsonLine() throws IOException {
        String line = fromPy.readLine();
        if (line == null) throw new EOFException("Python process terminated");
        if (verbose) System.out.println("[py] " + line);
        return parseFlatJson(line);
    }

    /**
     * Minimal parser for flat JSON objects with primitive values.
     * Works for responses like {"ok":true,"p":0.123}.
     */
    private static Map<String, String> parseFlatJson(String json) {
        Map<String, String> out = new HashMap<>();
        String s = json.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);

        // split on commas not inside quotes (responses are simple; keep it small)
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inQ = !inQ;
            if (ch == ',' && !inQ) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        if (!cur.isEmpty()) parts.add(cur.toString());

        for (String part : parts) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) continue;
            String k = strip(kv[0]);
            String v = strip(kv[1]);
            out.put(unquote(k), unquote(v));
        }
        return out;
    }

    private static String strip(String s) {
        return s == null ? "" : s.trim();
    }

    private static String unquote(String s) {
        String t = strip(s);
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String intArrayJson(int[] a) {
        if (a == null || a.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(a[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private static String jsonObject(Map<String, Object> m) {
        if (m == null || m.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(jsonString(e.getKey())).append(":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v.toString());
            } else {
                sb.append(jsonString(v.toString()));
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static File writeDataSetToTempCsv(DataSet data) throws IOException {
        File f = Files.createTempFile("tetrad_kci_", ".csv").toFile();
        f.deleteOnExit();

        try (BufferedWriter w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            // header
            List<Node> vars = data.getVariables();
            for (int j = 0; j < vars.size(); j++) {
                if (j > 0) w.write(",");
                w.write(vars.get(j).getName());
            }
            w.write("\n");

            int n = data.getNumRows();
            int p = data.getNumColumns();
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < p; j++) {
                    if (j > 0) w.write(",");
                    double v = data.getDouble(i, j);
                    if (Double.isNaN(v)) {
                        // causal-learn KCI generally doesn't like NaNs; choose a policy:
                        // either write empty and impute on Python side, or throw here.
                        w.write("nan");
                    } else {
                        w.write(Double.toString(v));
                    }
                }
                w.write("\n");
            }
        }

        return f;
    }
}
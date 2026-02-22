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

    public ProcessPythonCiService(String pythonExe, String serverScriptPath) {
        this.pythonExe = Objects.requireNonNull(pythonExe, "pythonExe").trim();
        this.serverScriptPath = Objects.requireNonNull(serverScriptPath, "serverScriptPath").trim();

        if (this.pythonExe.isEmpty()) throw new IllegalArgumentException("pythonExe is empty");
        if (this.serverScriptPath.isEmpty()) throw new IllegalArgumentException("serverScriptPath is empty");
    }

    @Override
    public synchronized void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

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
            // best-effort cleanup if init fails partway
            try { close(); } catch (Exception ignored) {}
            throw new RuntimeException(ioe);
        }
    }

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

    @Override
    public synchronized double pValue(int xIndex, int yIndex, int[] zIndices, double alpha) {
        if (!initialized.get()) {
            throw new IllegalStateException("ProcessPythonCiService not initialized");
        }

        try {
            String z = intArrayJson(zIndices);

            // Include alpha so the Python side has it (even if it doesn't strictly need it for p-values).
            String msg = "{"
                    + "\"op\":\"pvalue\","
                    + "\"x\":" + xIndex + ","
                    + "\"y\":" + yIndex + ","
                    + "\"z\":" + z + ","
                    + "\"alpha\":" + alpha
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
            //noinspection ResultOfMethodCallIgnored
            csvTemp.delete();
            csvTemp = null;
        }
    }

    // ------------------- internals -------------------

    private void startProcess() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(pythonExe, "-u", serverScriptPath);

        // If you want: set a working dir to the script’s folder
        File scriptFile = new File(serverScriptPath);
        File wd = scriptFile.getParentFile();
        if (wd != null && wd.isDirectory()) {
            pb.directory(wd);
        }

        pb.redirectErrorStream(true);
        proc = pb.start();

        toPy = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8));
        fromPy = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
    }

    private void writeJsonLine(String json) throws IOException {
        if (toPy == null) throw new IOException("Python process not started (toPy == null)");
        toPy.write(json);
        toPy.write("\n");
        toPy.flush();
    }

    private Map<String, String> readJsonLine() throws IOException {
        if (fromPy == null) throw new IOException("Python process not started (fromPy == null)");
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
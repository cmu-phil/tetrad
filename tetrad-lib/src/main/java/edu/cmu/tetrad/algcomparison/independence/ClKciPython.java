package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.ProcessPythonCiService;
import edu.cmu.tetrad.search.test.PythonKciIndependenceTest;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.PythonResource;

import java.io.File;
import java.io.Serial;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@link IndependenceWrapper} that runs the Kernel-based Conditional Independence (KCI)
 * test implemented in the Python <em>causal-learn</em> library, bridging it into Tetrad's
 * {@link IndependenceTest} framework.
 *
 * <h2>How it works</h2>
 * Each call to {@link #getTest} launches (or connects to) a persistent Python process
 * running a bundled server script ({@code python/kci_server.py}). CI queries from Java
 * are forwarded to that process via {@link ProcessPythonCiService}, and results are
 * returned as standard {@link IndependenceTest} responses through
 * {@link PythonKciIndependenceTest}.
 *
 * <h2>Python environment</h2>
 * A Python interpreter that has {@code causal-learn} and its dependencies installed must
 * be available on the host machine. The path to that interpreter is supplied via the
 * {@link Params#PYTHON_EXE} parameter. There is no default that is likely to be valid on
 * any machine other than the developer's, so this parameter should always be set
 * explicitly before use.
 *
 * <h2>Server script resolution</h2>
 * The Python server script is resolved in the following order:
 * <ol>
 *   <li>If {@link Params#PYTHON_CI_SERVER} is set to a non-empty path (and not the
 *       sentinel value {@code "Use bundled script"}), that path is used directly.</li>
 *   <li>Otherwise, the bundled resource {@code python/kci_server.py} is extracted to
 *       the user's local cache directory (via {@link PythonResource#extractToUserCache})
 *       and that extracted copy is used.</li>
 * </ol>
 *
 * <h2>Parameters</h2>
 * <ul>
 *   <li>{@link Params#PYTHON_EXE} — path to the Python executable (required).</li>
 *   <li>{@link Params#PYTHON_CI_SERVER} — path to the KCI server script, or
 *       {@code "Use bundled script"} to use the packaged default.</li>
 *   <li>{@link Params#ALPHA} — significance level for the independence test
 *       (default: {@code 0.01}).</li>
 *   <li>{@link Params#VERBOSE} — if {@code true}, the test logs additional
 *       detail during execution (default: {@code false}).</li>
 * </ul>
 *
 * <h2>Data</h2>
 * Only continuous {@link DataSet} inputs are supported. Passing any other
 * {@link DataModel} type will throw an {@link IllegalArgumentException}.
 *
 * @see PythonKciIndependenceTest
 * @see ProcessPythonCiService
 * @see PythonResource
 */
@TestOfIndependence(
        name = "KCI, Causal Learn (Python)",
        command = "kci-cl-test",
        dataType = DataType.Continuous
)
@General
public class ClKciPython implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    // These defaults match what you were using; users can override via parameters.
    private static final String DEFAULT_PYTHON_EXE =
            "/Users/josephramsey/venvs/kci/bin/python";

    // If pythonCiServer is not set, we use the bundled resource "python/kci_server.py".
    private static final String BUNDLED_RESOURCE = "python/kci_server.py";
    private static final String BUNDLED_CACHE_NAME = "kci_server.py";
    private static final String USE_BUNDLED = "Use bundled script";

    /**
     * Default constructor for the ClKciPython class.
     * This constructor initializes an instance of the ClKciPython class.
     */
    public ClKciPython() {
    }

    /**
     * Creates and returns an IndependenceTest instance using the provided DataModel and Parameters.
     * The method ensures that the appropriate Python executable and server script are properly configured,
     * resolving paths as necessary. A ProcessPythonCiService instance is initialized, and its output is
     * used to create a PythonKciIndependenceTest with configurable settings such as alpha and verbosity.
     *
     * @param dataModel The data model to be analyzed. Must be an instance of DataSet (continuous).
     * @param parameters Configuration parameters for the test, including paths for the Python executable
     *                   and server script, as well as settings like alpha and verbosity.
     * @return An IndependenceTest instance configured using the provided dataModel and parameters.
     * @throws IllegalArgumentException If the dataModel is not an instance of DataSet, the Python executable
     *                                  path is invalid or missing, or the server script path is invalid.
     * @throws RuntimeException If there is an error extracting the bundled Python server script.
     */
    @Override
    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {
        dataModel = MissingDataUtils.gate(dataModel, parameters, false, "KCI, Causal Learn (Python)");

        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException(
                    "ClKciPython requires a DataSet (continuous). Got: " +
                            (dataModel == null ? "null" : dataModel.getClass().getName())
            );
        }

        dataSet = dataSet.copy();

        // -----------------------------
        // 1. Resolve python executable
        // -----------------------------
        String pythonExe = (parameters == null)
                ? DEFAULT_PYTHON_EXE
                : parameters.getString(Params.PYTHON_EXE, DEFAULT_PYTHON_EXE);

        if (pythonExe == null || pythonExe.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "pythonExe is not configured. Please specify the path to the Python executable."
            );
        }

        pythonExe = pythonExe.trim();
        File pyFile = new File(pythonExe);
        if (!pyFile.exists()) {
            throw new IllegalArgumentException("pythonExe does not exist: " + pythonExe);
        }

        // -----------------------------
        // 2. Resolve server script path
        // -----------------------------
        String serverScriptPath = null;

        if (parameters != null) {
            String raw = parameters.getString(Params.PYTHON_CI_SERVER, "");
            if (raw != null) {
                raw = raw.trim();
                if (!raw.isEmpty() && !USE_BUNDLED.equals(raw)) {
                    serverScriptPath = raw;
                }
            }
        }

        // If null → use bundled script
        if (serverScriptPath == null) {
            try {
                Path extracted = PythonResource.extractToUserCache(
                        BUNDLED_RESOURCE,
                        BUNDLED_CACHE_NAME
                );
                serverScriptPath = extracted.toAbsolutePath().toString();
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to extract bundled KCI server script from resource: "
                                + BUNDLED_RESOURCE,
                        e
                );
            }
        }

        File scriptFile = new File(serverScriptPath);
        if (!scriptFile.exists()) {
            throw new IllegalArgumentException(
                    "pythonCiServer does not exist: " + serverScriptPath
            );
        }

        // -----------------------------
        // 3. Create service + test
        // -----------------------------
        ProcessPythonCiService service =
                new ProcessPythonCiService(pythonExe, serverScriptPath);

        PythonKciIndependenceTest test =
                new PythonKciIndependenceTest(dataSet, service);

        if (parameters != null) {
            double alpha = parameters.getDouble(Params.ALPHA, 0.01);
            test.setAlpha(alpha);

            boolean verbose = parameters.getBoolean(Params.VERBOSE, false);
            test.setVerbose(verbose);
        }

        return test;
    }

    /**
     * Provides a description of the test being implemented by the ClKciPython class.
     *
     * @return A string representing the description of the test, specifically "KCI-CL (Python)".
     */
    @Override
    public String getDescription() {
        return "KCI-CL (Python)";
    }

    /**
     * Returns the data type associated with this implementation. The data type
     * indicates the type of data that the method operates on or supports.
     *
     * @return The data type of this implementation. In this case, the data type
     *         is {@code DataType.Continuous}.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the list of parameters required for the configuration of the ClKciPython test.
     * These parameters may include settings such as the alpha level, Python executable,
     * Python CI server path, and verbosity.
     *
     * @return A list of strings representing the names of parameters necessary
     *         for configuring the test implemented by the ClKciPython class.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.PYTHON_EXE);
        params.add(Params.PYTHON_CI_SERVER);
        params.add(Params.VERBOSE);
        params.add(Params.MISSING_DATA_POLICY);
        return params;
    }
}
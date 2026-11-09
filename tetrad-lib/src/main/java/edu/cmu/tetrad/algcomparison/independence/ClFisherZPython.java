package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.ProcessPythonCiService;
import edu.cmu.tetrad.search.test.PythonRcitIndependenceTest;
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
 * An {@link IndependenceWrapper} that runs the Fisher Z conditional independence test
 * implemented in the Python <em>causal-learn</em> library, bridging it into
 * Tetrad's {@link IndependenceTest} framework.
 *
 * <h2>How it works</h2>
 * Each call to {@link #getTest} launches (or connects to) a persistent Python process
 * running a bundled server script ({@code python/fisherz_server.py}). CI queries from Java
 * are forwarded to that process via {@link ProcessPythonCiService}, and results are
 * returned as standard {@link IndependenceTest} responses through
 * {@link PythonRcitIndependenceTest}.
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
 *   <li>Otherwise, the bundled resource {@code python/fisherz_server.py} is extracted to
 *       the user's local cache directory (via {@link PythonResource#extractToUserCache})
 *       and that extracted copy is used.</li>
 * </ol>
 *
 * <h2>Parameters</h2>
 * <ul>
 *   <li>{@link Params#PYTHON_EXE} — path to the Python executable (required).</li>
 *   <li>{@link Params#PYTHON_CI_SERVER} — path to the Fisher Z server script, or
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
 * @see PythonRcitIndependenceTest
 * @see ProcessPythonCiService
 * @see PythonResource
 */
@TestOfIndependence(
        name = "Fisher Z, Causal Learn (Python)",
        command = "fisherz-cl-test",
        dataType = DataType.Continuous
)
@General
public class ClFisherZPython implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 24L;

    private static final String DEFAULT_PYTHON_EXE =
            "/Users/josephramsey/venvs/kci/bin/python";

    private static final String BUNDLED_RESOURCE = "python/fisherz_server.py";

    private static final String BUNDLED_CACHE_NAME = "fisherz_server.py";

    private static final String USE_BUNDLED = "Use bundled script";

    /**
     * Default constructor for the ClFisherZPython class.
     *
     * This constructor initializes an instance of the ClFisherZPython class,
     * which serves as a wrapper and utility for performing independence tests
     * using Python-based methods. It does not take any parameters or perform
     * any custom initialization logic.
     */
    public ClFisherZPython() {
    }

    /**
     * Retrieves an independence test based on the provided data model and parameters.
     * The method specifically requires a continuous {@code DataSet} and utilizes a
     * Python-based process to perform the independence test.
     *
     * @param dataModel the input data model, must be an instance of {@code DataSet}
     * @param parameters an optional collection of configuration parameters, used to
     *                   customize the Python executable path, server script location,
     *                   alpha threshold, and verbosity settings
     * @return an instance of {@code IndependenceTest} configured with the provided
     *         data model and parameters
     * @throws IllegalArgumentException if the provided {@code dataModel} is not a continuous
     *                                  {@code DataSet} or if required parameters are invalid
     * @throws RuntimeException if the bundled Python server script cannot be extracted
     */
    @Override
    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {
        dataModel = MissingDataUtils.gate(dataModel, parameters, false, "Fisher Z, Causal Learn (Python)");

        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException(
                    "ClFisherZPython requires a DataSet (continuous). Got: " +
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
                        "Failed to extract bundled Fisher Z server script from resource: "
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

        PythonRcitIndependenceTest test =
                new PythonRcitIndependenceTest(dataSet, service);

        if (parameters != null) {
            double alpha = parameters.getDouble(Params.ALPHA, 0.01);
            test.setAlpha(alpha);

            boolean verbose = parameters.getBoolean(Params.VERBOSE, false);
            test.setVerbose(verbose);
        }

        return test;
    }

    /**
     * Provides a textual description of the test implementation.
     *
     * @return a string describing the test, "FisherZ-CL (Python)"
     */
    @Override
    public String getDescription() {
        return "FisherZ-CL (Python)";
    }

    /**
     * Retrieves the data type associated with the test.
     *
     * @return the data type of the test, which is {@code DataType.Continuous}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves a list of parameter names required for the Python-based independence test.
     *
     * @return a list of parameter names as strings, including:
     *         ALPHA, PYTHON_EXE, PYTHON_CI_SERVER, and VERBOSE.
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
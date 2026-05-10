package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.ProcessPythonCiService;
import edu.cmu.tetrad.search.test.PythonRcitIndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.PythonResource;

import java.io.File;
import java.io.Serial;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link IndependenceWrapper} that runs the Randomized Conditional Independence Test
 * (RCIT) implemented in the Python <em>causal-learn</em> library, bridging it into
 * Tetrad's {@link IndependenceTest} framework.
 *
 * <h2>How it works</h2>
 * Each call to {@link #getTest} launches (or connects to) a persistent Python process
 * running a bundled server script ({@code python/rcit_server.py}). CI queries from Java
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
 *   <li>Otherwise, the bundled resource {@code python/rcit_server.py} is extracted to
 *       the user's local cache directory (via {@link PythonResource#extractToUserCache})
 *       and that extracted copy is used.</li>
 * </ol>
 *
 * <h2>Parameters</h2>
 * <ul>
 *   <li>{@link Params#PYTHON_EXE} — path to the Python executable (required).</li>
 *   <li>{@link Params#PYTHON_CI_SERVER} — path to the RCIT server script, or
 *       {@code "Use bundled script"} to use the packaged default.</li>
 *   <li>{@link Params#ALPHA} — significance level for the independence test
 *       (default: {@code 0.01}).</li>
 *   <li>{@link Params#VERBOSE} — if {@code true}, the test logs additional
 *       detail during execution (default: {@code false}).</li>
 *   <li>{@code rcitNumF} — number of random Fourier features for the conditioning
 *       set Z (default: {@code 100}, matching causal-learn's default).</li>
 *   <li>{@code rcitNumF2} — number of random Fourier features for X and Y
 *       (default: {@code 5}, matching causal-learn's default).</li>
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
        name = "RCIT, Causal Learn (Python)",
        command = "rcit-cl-test",
        dataType = DataType.Continuous
)
@General
public class ClRcitPython implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    private static final String DEFAULT_PYTHON_EXE =
            "/Users/josephramsey/venvs/kci/bin/python";

    private static final String BUNDLED_RESOURCE = "python/rcit_server.py";
    private static final String BUNDLED_CACHE_NAME = "rcit_server.py";
    private static final String USE_BUNDLED = "Use bundled script";

    // Causal-learn RCIT defaults
    private static final int DEFAULT_NUM_F = 100;
    private static final int DEFAULT_NUM_F2 = 5;

    /**
     * Default constructor for the ClRcitPython class.
     */
    public ClRcitPython() {
    }

    /**
     * Retrieves an independence test based on the provided data model and parameters.
     *
     * @param dataModel  the input data model, must be an instance of {@code DataSet}
     * @param parameters configuration parameters
     * @return an instance of {@code IndependenceTest} configured with the provided
     *         data model and parameters
     * @throws IllegalArgumentException if the provided {@code dataModel} is not a continuous
     *                                  {@code DataSet} or if required parameters are invalid
     * @throws RuntimeException         if the bundled Python server script cannot be extracted
     */
    @Override
    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {

        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException(
                    "ClRcitPython requires a DataSet (continuous). Got: " +
                            (dataModel == null ? "null" : dataModel.getClass().getName())
            );
        }

        dataSet = dataSet.copy();

        // 1. Resolve python executable
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

        // 2. Resolve server script path
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

        if (serverScriptPath == null) {
            try {
                Path extracted = PythonResource.extractToUserCache(
                        BUNDLED_RESOURCE,
                        BUNDLED_CACHE_NAME
                );
                serverScriptPath = extracted.toAbsolutePath().toString();
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to extract bundled RCIT server script from resource: "
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

        // 3. Build RCIT params — pin to causal-learn defaults, allow override
        int numF  = (parameters == null) ? DEFAULT_NUM_F
                : parameters.getInt("rcitNumF", DEFAULT_NUM_F);
        int numF2 = (parameters == null) ? DEFAULT_NUM_F2
                : parameters.getInt("rcitNumF2", DEFAULT_NUM_F2);

        Map<String, Object> rcitParams = new HashMap<>();
        rcitParams.put("num_f",  numF);
        rcitParams.put("num_f2", numF2);

        // 4. Create service + test
        ProcessPythonCiService service =
                new ProcessPythonCiService(pythonExe, serverScriptPath);
        service.updateParams(rcitParams);

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
     * @return a string describing the test, "RCIT-CL (Python)"
     */
    @Override
    public String getDescription() {
        return "RCIT-CL (Python)";
    }

    /**
     * Retrieves the data type associated with the test.
     *
     * @return {@code DataType.Continuous}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the list of parameter names used by this test.
     *
     * @return list of parameter name strings
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.PYTHON_EXE);
        params.add(Params.PYTHON_CI_SERVER);
        params.add(Params.VERBOSE);
        params.add("rcitNumF");
        params.add("rcitNumF2");
        return params;
    }
}
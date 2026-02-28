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
import java.util.List;

/**
 * The ClRcitPython class is a wrapper implementation of the {@code IndependenceWrapper}
 * interface, providing functionality for testing conditional independence using
 * Python-based methods. It integrates with a Python server script to perform
 * statistical computations specifically designed for continuous data sets.
 *
 * The implementation supports configurable parameters, such as the path to the Python
 * executable, the Python server script location, and statistical settings including
 * the alpha significance level. It also offers a mechanism to use a bundled Python
 * server script if no custom script is provided.
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

    // These defaults match what you were using; users can override via parameters.
    private static final String DEFAULT_PYTHON_EXE =
            "/Users/josephramsey/venvs/kci/bin/python";

    // If pythonCiServer is not set, we use the bundled resource "python/kci_server.py".
    private static final String BUNDLED_RESOURCE = "python/rcit_server.py";

    // The cache name
    private static final String BUNDLED_CACHE_NAME = "rcit_server.py";

    // The string to
    private static final String USE_BUNDLED = "Use bundled script";

    /**
     * Default constructor for the ClRcitPython class.
     *
     * This constructor initializes an instance of the ClRcitPython class,
     * which serves as a wrapper and utility for performing independence tests
     * using Python-based methods. It does not take any parameters or perform
     * any custom initialization logic.
     */
    public ClRcitPython() {
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

        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException(
                    "ClRcitPython requires a DataSet (continuous). Got: " +
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
     * @return a string describing the test, "RCIT-CL (Python)"
     */
    @Override
    public String getDescription() {
        return "RCIT-CL (Python)";
    }

    /**
     * Retrieves the data type associated with the test.
     *
     * This method indicates the type of data (e.g., continuous, discrete, mixed)
     * that the implementation supports or is specialized for.
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
     * The parameters are used to configure various aspects of the test, such as thresholds,
     * Python executable paths, and server configurations.
     *
     * @return a list of parameter names as strings. The returned list typically includes:
     *         ALPHA, PYTHON_EXE, PYTHON_CI_SERVER, and VERBOSE.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.PYTHON_EXE);
        params.add(Params.PYTHON_CI_SERVER);
        params.add(Params.VERBOSE);
        return params;
    }
}
package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.ProcessPythonCiService;
import edu.cmu.tetrad.search.test.PythonKciIndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.PythonResource;

import java.io.File;
import java.io.Serial;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    public ClKciPython() {
    }

    @Override
    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {

        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException(
                    "ClKciPython requires a DataSet (continuous). Got: " +
                            (dataModel == null ? "null" : dataModel.getClass().getName())
            );
        }

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

    @Override
    public String getDescription() {
        return "KCI-CL (Python)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

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
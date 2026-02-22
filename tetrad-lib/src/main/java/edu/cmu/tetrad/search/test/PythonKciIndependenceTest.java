package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

/**
 * The PythonKciIndependenceTest class implements an independence test using the kernel
 * conditional independence (KCI) test provided through a Python backend, such as
 * causallearn. It facilitates independence determination between variables of a given
 * dataset and manages interaction with the associated Python service.
 *
 * This class is immutable except for certain configurable properties (e.g., alpha, verbosity).
 * It initializes resources on the Python side upon instantiation and provides mechanisms
 * to perform independence testing, adjust parameters, and release resources when no longer needed.
 *
 * Thread Safety:
 * - The class is thread-safe for concurrent read/query operations.
 * - Care is required when modifying properties (alpha, verbosity, or KCI parameters),
 *   as intended thread-safety for setters is only provided via `volatile` or synchronized service updates.
 * - The service interactions must ensure proper thread coordination.
 *
 * Responsibilities:
 * - Maps dataset variables to indices for Python integration.
 * - Executes the KCI test by delegating the computation to the Python service.
 * - Manages test parameters such as significance level (alpha) and verbosity.
 * - Facilitates resource cleanup by implementing the Closeable interface.
 */
public final class PythonKciIndependenceTest implements IndependenceTest, Closeable {

    private final DataSet data;
    private final List<Node> vars;
    private final Map<String, Integer> nameToIndex;

    private final PythonCiService service;

    private volatile double alpha = 0.01;
    private volatile boolean verbose;

    // Any KCI parameters you want to forward
    private final Map<String, Object> kciParams = new HashMap<>();

    /**
     * Constructs a PythonKciIndependenceTest object for performing conditional independence tests
     * using a Python backend via the provided PythonCiService. The method initializes required
     * fields and prepares the object for subsequent operations.
     *
     * @param data the dataset containing the variables and relationships to analyze; must not be null
     * @param service the PythonCiService used to execute independence tests in a Python environment; must not be null
     */
    public PythonKciIndependenceTest(DataSet data, PythonCiService service) {
        this.data = Objects.requireNonNull(data, "data");
        this.service = Objects.requireNonNull(service, "service");

        this.vars = Collections.unmodifiableList(new ArrayList<>(data.getVariables()));
        this.nameToIndex = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) {
            nameToIndex.put(vars.get(i).getName(), i);
        }

        // initialize remote side once
        service.initializeIfNeeded(data, vars, kciParams);
    }

    /**
     * Tests the conditional independence between two variables given a conditioning set,
     * using a Python backend for statistical computations.
     * Computes the p-value for the hypothesis that the variables are conditionally independent,
     * and determines whether to reject the null hypothesis based on a specified significance level (alpha).
     *
     * @param x the first variable (Node) for testing conditional independence; must not be null
     * @param y the second variable (Node) for testing conditional independence; must not be null
     * @param z the conditioning set (Set of Node) on which the independence hypothesis is conditioned; can be null or empty
     * @return an IndependenceResult containing the outcome of the test, including the computed p-value,
     *         whether the null hypothesis was rejected, and a related score
     * @throws InterruptedException if the thread running this method is interrupted
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

        int xi = idx(x);
        int yi = idx(y);

        int[] zi = z == null || z.isEmpty()
                ? new int[0]
                : z.stream().mapToInt(this::idx).sorted().toArray();

        double p = service.pValue(xi, yi, zi, alpha);

        if (verbose) {
            System.out.println(LogUtilsSearch.independenceFactMsg(x, y, z, p));
        }

        // Your convention: score = alpha - p  (positive means "dependence holds" when used as score-as-test)
        return new IndependenceResult(new IndependenceFact(x, y, z), p > alpha, p, alpha - p);
    }

    private int idx(Node n) {
        Integer i = nameToIndex.get(n.getName());
        if (i == null) {
            throw new IllegalArgumentException("Unknown variable: " + n.getName());
        }
        return i;
    }

    /**
     * Retrieves the list of variables involved in the analysis.
     *
     * @return a list of Node objects representing the variables in the dataset.
     */
    @Override
    public List<Node> getVariables() {
        return vars;
    }

    /**
     * Retrieves the data model associated with this instance.
     *
     * @return the DataModel object representing the dataset used for analysis.
     */
    @Override
    public DataModel getData() {
        return data;
    }

    /**
     * Indicates whether verbose output is enabled for the PythonKciIndependenceTest.
     *
     * @return true if verbose output is enabled, false otherwise
     */
    @Override
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether verbose output is enabled for this instance of PythonKciIndependenceTest.
     * When verbosity is enabled, additional details about the operations performed by the
     * Python backend may be provided in the output.
     *
     * @param verbose a boolean value indicating whether verbose output
     *                should be enabled (true) or disabled (false)
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        service.setVerbose(false);
    }

    /**
     * Retrieves the significance level (alpha) used in conditional independence tests.
     * The alpha value represents the threshold for deciding whether to reject
     * the null hypothesis of conditional independence.
     *
     * @return the alpha value as a double, representing the significance level for the tests
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the significance level (alpha) used in conditional independence tests.
     * The alpha value determines the threshold for rejecting the null hypothesis
     * of conditional independence. A lower alpha value indicates a stricter criterion
     * for concluding that two variables are not conditionally independent.
     *
     * @param alpha the significance level to be set; must be a double value
     *              between 0 and 1 (exclusive) representing the desired confidence level
     */
    @Override
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Closes the underlying PythonCiService associated with this instance, releasing any resources
     * and terminating any persistent connections.
     *
     * This method ensures that the external Python backend for performing conditional independence
     * tests is properly shut down. If an IOException occurs during the closing process, it will be
     * wrapped and propagated as a RuntimeException.
     *
     * @throws RuntimeException if an IOException is encountered while closing the service
     */
    @Override
    public void close() {
        try {
            service.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a string representation of the PythonKciIndependenceTest instance, providing
     * a concise description intended to identify the use of the KCI method for causal independence
     * tests integrated with a Python backend.
     *
     * @return a string describing the PythonKciIndependenceTest implementation.
     */
    @Override
    public String toString() {
        return "KCI (Python / causal-learn)";
    }

    /**
     * Sets a KCI-specific parameter for the PythonKciIndependenceTest instance. This method allows
     * users to modify or add configuration parameters specific to the Kernel-based Conditional
     * Independence method. Once a parameter is updated, the changes are propagated to the underlying
     * Python service responsible for performing conditional independence tests.
     *
     * @param key the name of the parameter to set; must not be null
     * @param value the value to assign to the specified parameter; can be any object representing
     *              the desired parameter value, depending on the expected type of the parameter
     */
    // Optional: allow setting KCI-specific parameters via your Parameters plumbing
    public void setKciParam(String key, Object value) {
        kciParams.put(key, value);
        service.updateParams(kciParams);
    }
}
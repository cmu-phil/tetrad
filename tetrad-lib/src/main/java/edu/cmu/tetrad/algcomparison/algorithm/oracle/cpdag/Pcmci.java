package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * PCMCI wrapper for algcomparison. NOTE: Knowledge comes from TsUtils.createLagData(...) and is used inside Pcmci.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "PCMCI",
        command = "pcmci",
        algoType = AlgType.forbid_latent_common_causes
)
@Experimental
public class Pcmci implements Algorithm, TakesIndependenceWrapper, HasKnowledge {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * An instance of {@link IndependenceWrapper} used to perform independence tests and provide associated
     * functionality such as obtaining descriptions, data type, and required parameters specific to the test. This
     * serves as the core element for determining conditional independence in the enclosing {@code Pcmci} algorithm.
     */
    private IndependenceWrapper test;
    /**
     * Represents the instance of {@link Knowledge} associated with the PCMCI algorithm. This variable contains
     * domain-specific causal information that can guide or constrain the search process in the PCMCI causal discovery
     * algorithm.
     * <p>
     * The {@link Knowledge} object can store both prior knowledge and structure constraints, including forbidden and
     * required edges in the causal graph.
     * <p>
     * It is used by methods to retrieve or set domain-specific knowledge to influence the behavior and outcomes of the
     * algorithm.
     */
    private Knowledge knowledge;

    /**
     * Default constructor for the Pcmci class. Initializes an instance of Pcmci without any dependencies or
     * parameters.
     */
    public Pcmci() {
    }

    /**
     * Constructs an instance of the Pcmci class with a specified independence test.
     *
     * @param test The {@link IndependenceWrapper} instance representing a specific independence test implementation to
     *             be used in PCMCI algorithm.
     */
    public Pcmci(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * Executes the PCMCI (Peter and Clark Momentary Conditional Independence) causal discovery algorithm on a given
     * data model using specified parameters. The method relies on the assumption that the input data model is a
     * {@link DataSet} and includes handling of lagged datasets, parameterized independence tests, and configurable
     * search options.
     *
     * @param dataModel  The input data model to perform the PCMCI search on. Must be an instance of {@link DataSet}. If
     *                   the provided data model is not of this type, an {@link IllegalArgumentException} is thrown.
     * @param parameters The set of parameters to configure the PCMCI search. These include: - TIME_LAG (default: 1):
     *                   Maximum time lag for lagged variables. - DEPTH (default: 3): Maximum size of conditioning sets
     *                   in conditional independence tests. - ALPHA (default: 0.05): Significance level for independence
     *                   tests. - VERBOSE (default: false): Toggle for detailed output logs.
     * @return A {@link Graph} representing the causal structure learned from the input data model.
     * @throws InterruptedException If the thread executing the method is interrupted during processing.
     */
    @Override
    public Graph search(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (!(dataModel instanceof DataSet raw)) {
            throw new IllegalArgumentException("PCMCI requires a DataSet.");
        }

        final int maxLag = Math.max(1, parameters.getInt(Params.TIME_LAG, 1));
        final int maxCondSize = parameters.getInt(Params.DEPTH, 3);
        final double alpha = parameters.getDouble(Params.ALPHA, 0.05);
        final boolean verbose = parameters.getBoolean(Params.VERBOSE, false);

        // Build lagged dataset (this also constructs consistent Knowledge for the lagged space).
        DataSet lagged = TsUtils.createLagData(raw, maxLag);

        // Build the test over the LAGGED variables.
        IndependenceTest indTest = getIndependenceWrapper().getTest(lagged, parameters);

        // Configure search. Pcmci will internally rebuild the lagged view and pull lagged.getKnowledge().
        edu.cmu.tetrad.search.Pcmci search = new edu.cmu.tetrad.search.Pcmci.Builder(raw, indTest)
                .maxLag(maxLag)
                .maxCondSize(maxCondSize)
                .alpha(alpha)
                .verbose(verbose)
                .collapseToLag0(false)
                .build();

        return search.search();
    }

    /**
     * Generates a comparison causal graph derived from the input graph by converting it to a completed partially
     * directed acyclic graph (CPDAG).
     *
     * @param graph The input {@link Graph} that represents the initial structure from which the comparison graph is
     *              derived.
     * @return A {@link Graph} that represents the CPDAG obtained from the input graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    /**
     * Provides a description for the PCMCI algorithm, including the configured independence test, if applicable.
     *
     * @return A string describing the PCMCI algorithm and the independence test being used. If no test is provided, the
     * description will indicate a "configured test".
     */
    @Override
    public String getDescription() {
        return "PCMCI using " + (this.test != null ? this.test.getDescription() : "configured test");
    }

    /**
     * Retrieves the data type of the dataset required for the PCMCI algorithm. This can be continuous, discrete, mixed,
     * or other specific types defined in {@link DataType}.
     *
     * @return The {@link DataType} instance representing the type required or handled by the PCMCI algorithm, as
     * provided by the associated {@code IndependenceWrapper}.
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Retrieves a list of parameter names used to configure the PCMCI algorithm. The parameters include:
     *
     * @return A list of parameter names as strings that are applicable for the PCMCI algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.TIME_LAG);   // -> maxLag
        params.add(Params.DEPTH);      // -> maxCondSize
        params.add(Params.ALPHA);
        params.add(Params.VERBOSE);
        return params;
    }

    /**
     * Retrieves the current instance of {@link IndependenceWrapper} associated with this object.
     *
     * @return The {@link IndependenceWrapper} instance representing the current independence test implementation used
     * in the PCMCI algorithm.
     */
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the {@link IndependenceWrapper} instance to be used for the PCMCI algorithm.
     *
     * @param test The {@link IndependenceWrapper} instance representing a specific implementation of an independence
     *             test to be associated with this object.
     */
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Knowledge getKnowledge() {
        return knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }
}
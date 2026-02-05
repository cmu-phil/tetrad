package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
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

import static edu.cmu.tetrad.search.utils.LogUtilsSearch.stampWithBic;

/**
 * PC-Test
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "PC-Test-Joe",
        command = "pc_test-joe",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class PcTestJoe extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The {@code test} variable holds an instance of the {@code IndependenceWrapper} used
     * by the {@code PcTest} algorithm for performing conditional independence tests.
     * This wrapper is central to the functioning of the algorithm, as it encapsulates
     * the logic and mechanisms required to evaluate dependencies or independencies
     * among variables in a dataset. It facilitates causal discovery by providing
     * the necessary tools to determine the statistical relationships needed to construct
     * the causal graph.
     *
     * Proper initialization of this variable is essential for the algorithm to operate
     * correctly. It should be assigned an implementation of the {@code IndependenceWrapper}
     * interface, which defines the underlying data type, parameters, and testing strategies
     * suitable for the analysis.
     */
    private IndependenceWrapper test;
    /**
     * Represents the {@code Knowledge} object associated with the current instance of the PcTest algorithm.
     * This object encapsulates prior knowledge or constraints that may influence the causal structure
     * discovery process performed by the algorithm.
     *
     * The {@code Knowledge} object is used to guide the search by applying predefined constraints
     * or assumptions, which can aid in achieving more accurate and domain-relevant results.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructs a new instance of the PcTest algorithm.
     *
     * This constructor creates an uninitialized PcTest object. Additional
     * configuration may be required before the algorithm can be utilized for
     * structure discovery or constraint-based causal inference. For proper usage,
     * ensure that the required parameters and dependencies (e.g., independence test,
     * knowledge) are set.
     */
    public PcTestJoe() {
    }

    /**
     * Constructs a new instance of the PcTest algorithm with the specified
     * independence wrapper.
     *
     * This constructor initializes the PcTest object using the provided
     * {@code IndependenceWrapper} for evaluating conditional independence tests.
     * The {@code IndependenceWrapper} plays a crucial role in determining the
     * dependency structure and causal relationships in constraint-based
     * causal inference.
     *
     * @param test An implementation of the {@code IndependenceWrapper} interface
     *             used to perform independence tests for the algorithm.
     */
    public PcTestJoe(IndependenceWrapper test) {
        this.test = test;
    }


    /**
     * Executes the PcTest algorithm to search for causal structures in a given data model.
     * This method performs constraint-based causal inference by utilizing a conditional
     * independence test and optional time series lagging support.
     *
     * @param dataModel The input data model to analyze, which may be a standard dataset or
     *                  a lagged dataset in the case of time series data.
     * @param parameters The set of parameters controlling the execution of the PcTest
     *                   algorithm, including the time lag and search depth.
     * @return A directed graph representing the identified causal structure.
     * @throws InterruptedException If the thread executing the search is interrupted.
     */
    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        DataModel dm = dataModel;
        Knowledge k = new Knowledge(this.knowledge);

        // Time series lagging support (matches the PC wrapper pattern).
        int lag = parameters.getInt(Params.TIME_LAG);
        if (lag > 0) {
            if (!(dm instanceof DataSet ds)) {
                throw new IllegalArgumentException("Expecting a DataSet for time lagging.");
            }

            DataSet lagged = TsUtils.createLagData(ds, lag);
            if (ds.getName() != null) lagged.setName(ds.getName());

            dm = lagged;
            k = lagged.getKnowledge();
        }

        IndependenceTest indTest = test.getTest(dm, parameters);

        edu.cmu.tetrad.search.PcTestJoe search = new edu.cmu.tetrad.search.PcTestJoe(indTest);
        search.setKnowledge(k);
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        Graph graph = search.search();
        stampWithBic(graph, dm);

        return graph;
    }

    /**
     * Generates a completed partially directed acyclic graph (CPDAG) by transforming the given graph.
     * This method takes a directed acyclic graph (DAG) as input, converts it into an edge list
     * representation, and then applies the transformation to produce the CPDAG.
     *
     * @param graph The input graph to be transformed. This graph is expected to be a directed acyclic
     *              graph (DAG) that serves as the basis for the conversion to a CPDAG.
     * @return A new graph representing the completed partially directed acyclic graph (CPDAG)
     *         derived from the input graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToCpdag(new EdgeListGraph(graph));
    }

    /**
     * Provides a description of the PcTest algorithm by combining its specific context
     * with the description of the underlying independence test.
     *
     * @return A string describing the PcTest algorithm, including the description
     *         from the associated independence test instance.
     */
    @Override
    public String getDescription() {
        return "PC-Test-Joe using " + test.getDescription();
    }

    /**
     * Retrieves the data type required by the search, specifying whether the dataset should be
     * continuous, discrete, or mixed.
     *
     * @return The {@code DataType} that defines the nature of the data required by the search.
     */
    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    /**
     * Retrieves the list of parameter names required by the PcTest algorithm.
     * These parameters control various aspects of the algorithm's execution,
     * such as search depth and time lag considerations.
     *
     * @return A list of parameter names as strings. The returned list includes
     *         parameters such as "DEPTH", "TIME_LAG", and "TIME_LAG_REPLICATING_GRAPH".
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.DEPTH);
        params.add(Params.TIME_LAG);
        params.add(Params.TIME_LAG_REPLICATING_GRAPH);
        params.add(Params.VERBOSE);
        return params;
    }

    /**
     * Retrieves the {@code Knowledge} object associated with this instance of the PcTest algorithm.
     * The {@code Knowledge} object encapsulates prior knowledge or constraints that may be used
     * during the execution of the algorithm to guide the search for causal structures.
     *
     * @return A {@code Knowledge} instance representing the prior knowledge or constraints,
     *         encapsulated and managed by the algorithm.
     */
    @Override
    public Knowledge getKnowledge() {
        return new Knowledge(this.knowledge);
    }

    /**
     * Sets the {@code Knowledge} object for this instance of the PcTest algorithm.
     * The {@code Knowledge} object encapsulates prior knowledge or constraints that
     * may be utilized during the execution of the algorithm to guide the search for
     * causal structures.
     *
     * @param knowledge The {@code Knowledge} instance representing prior knowledge or
     *                  constraints to be used by the algorithm.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the {@code IndependenceWrapper} instance associated with this object.
     * The {@code IndependenceWrapper} is utilized to perform conditional independence
     * tests, which are integral to the execution of the PcTest algorithm.
     *
     * @return The {@code IndependenceWrapper} instance used for conditional
     *         independence testing within the PcTest algorithm.
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the {@code IndependenceWrapper} instance for this PcTest algorithm.
     * The {@code IndependenceWrapper} is responsible for performing conditional
     * independence tests, which are essential for determining causal relationships
     * and dependencies during the execution of the algorithm.
     *
     * @param test An implementation of the {@code IndependenceWrapper} interface
     *             to be used for conditional independence testing within the PcTest
     *             algorithm.
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}
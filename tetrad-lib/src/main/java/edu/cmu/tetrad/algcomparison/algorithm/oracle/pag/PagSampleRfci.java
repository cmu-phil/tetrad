package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.independence.ProbabilisticTest;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.LinkedList;
import java.util.List;

/**
 * Jan 29, 2023 3:45:09 PM
 *
 * @author Kevin V. Bui (kvb2univpitt@gmail.com)
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "PAG-Sampling-RFCI",
        command = "pag-sampling-rfci",
        algoType = AlgType.allow_latent_common_causes
)
//@Experimental
public class PagSampleRfci extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge {

    /**
     * Constant <code>PAG_SAMPLING_RFCI_PARAMETERS</code>
     */
    public static final List<String> PAG_SAMPLING_RFCI_PARAMETERS = new LinkedList<>();
    /**
     * Constant <code>RFCI_PARAMETERS</code>
     */
    public static final List<String> RFCI_PARAMETERS = new LinkedList<>();
    /**
     * Constant <code>PROBABILISTIC_TEST_PARAMETERS</code>
     */
    public static final List<String> PROBABILISTIC_TEST_PARAMETERS = new LinkedList<>();
    @Serial
    private static final long serialVersionUID = 23L;

    static {
        // algorithm parameters
        PAG_SAMPLING_RFCI_PARAMETERS.add(Params.NUM_RANDOMIZED_SEARCH_MODELS);
        PAG_SAMPLING_RFCI_PARAMETERS.add(Params.VERBOSE);

        // Rfci parameters
        RFCI_PARAMETERS.add(Params.DEPTH);
        RFCI_PARAMETERS.add(Params.MAX_DISCRIMINATING_PATH_LENGTH);

        // IndTestProbabilistic parameters
        PROBABILISTIC_TEST_PARAMETERS.add(Params.NO_RANDOMLY_DETERMINED_INDEPENDENCE);
        PROBABILISTIC_TEST_PARAMETERS.add(Params.CUTOFF_IND_TEST);
        PROBABILISTIC_TEST_PARAMETERS.add(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE);
    }

    /**
     * The probabilistic test
     */
    private final IndependenceWrapper test = new ProbabilisticTest();
    /**
     * The knowledge
     */
    private Knowledge knowledge;

    /**
     * Constructs a new instance of the PagSampleRfci algorithm.
     */
    public PagSampleRfci() {
    }

    /**
     * Runs the search algorithm using the given data set and parameters.
     *
     * @param dataSet    the data set to perform the search on
     * @param parameters the parameters for the search algorithm
     * @return the graph resulting from the search algorithm
     */
    @Override
    public Graph runSearch(DataModel dataSet, Parameters parameters) {
        if (!(dataSet instanceof DataSet && dataSet.isDiscrete())) {
            throw new IllegalArgumentException("Expecting a discrete dataset.");
        }

        edu.pitt.dbmi.algo.bayesian.constraint.search.PagSamplingRfci pagSamplingRfci = new edu.pitt.dbmi.algo.bayesian.constraint.search.PagSamplingRfci(SimpleDataLoader.getDiscreteDataSet(dataSet));

        // PAG-Sampling-RFCI parameters
        pagSamplingRfci.setNumRandomizedSearchModels(parameters.getInt(Params.NUM_RANDOMIZED_SEARCH_MODELS));
        pagSamplingRfci.setVerbose(parameters.getBoolean(Params.VERBOSE));

        // Rfic parameters
        pagSamplingRfci.setKnowledge(this.knowledge);
        pagSamplingRfci.setDepth(parameters.getInt(Params.DEPTH));
        pagSamplingRfci.setMaxDiscriminatingPathLength(parameters.getInt(Params.MAX_DISCRIMINATING_PATH_LENGTH));

        // ProbabilisticTest parameters
        pagSamplingRfci.setThreshold(parameters.getBoolean(Params.NO_RANDOMLY_DETERMINED_INDEPENDENCE));
        pagSamplingRfci.setCutoff(parameters.getDouble(Params.CUTOFF_IND_TEST));
        pagSamplingRfci.setPriorEquivalentSampleSize(parameters.getDouble(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE));

        return pagSamplingRfci.search();
    }

    /**
     * Returns the comparison graph based on the true directed graph.
     *
     * @param graph The true directed graph.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph trueGraph = new EdgeListGraph(graph);
        return GraphTransforms.dagToPag(trueGraph);
    }

    /**
     * Returns a description of the method.
     *
     * @return The description of the method.
     */
    @Override
    public String getDescription() {
        return "PAG-Sampling-RFCI " + this.test.getDescription();
    }

    /**
     * Retrieves the data type associated with the method.
     *
     * @return the data type of the method, which is discrete.
     */
    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    /**
     * Retrieves the list of parameters for the method.
     *
     * @return the list of parameters for the method
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new LinkedList<>();

        parameters.addAll(PAG_SAMPLING_RFCI_PARAMETERS);
        parameters.addAll(RFCI_PARAMETERS);
        parameters.addAll(PROBABILISTIC_TEST_PARAMETERS);

        return parameters;
    }

    /**
     * Retrieves the knowledge associated with this method.
     *
     * @return The knowledge associated with this method.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge associated with this method.
     *
     * @param knowledge the knowledge object to be set
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }
}

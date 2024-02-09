package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.independence.ProbabilisticTest;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
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
public class PagSampleRfci implements Algorithm, HasKnowledge {

    /** Constant <code>PAG_SAMPLING_RFCI_PARAMETERS</code> */
    public static final List<String> PAG_SAMPLING_RFCI_PARAMETERS = new LinkedList<>();
    /** Constant <code>RFCI_PARAMETERS</code> */
    public static final List<String> RFCI_PARAMETERS = new LinkedList<>();
    /** Constant <code>PROBABILISTIC_TEST_PARAMETERS</code> */
    public static final List<String> PROBABILISTIC_TEST_PARAMETERS = new LinkedList<>();
    private static final long serialVersionUID = 23L;

    static {
        // algorithm parameters
        PAG_SAMPLING_RFCI_PARAMETERS.add(Params.NUM_RANDOMIZED_SEARCH_MODELS);
        PAG_SAMPLING_RFCI_PARAMETERS.add(Params.VERBOSE);

        // Rfci parameters
        RFCI_PARAMETERS.add(Params.DEPTH);
        RFCI_PARAMETERS.add(Params.MAX_PATH_LENGTH);

        // IndTestProbabilistic parameters
        PROBABILISTIC_TEST_PARAMETERS.add(Params.NO_RANDOMLY_DETERMINED_INDEPENDENCE);
        PROBABILISTIC_TEST_PARAMETERS.add(Params.CUTOFF_IND_TEST);
        PROBABILISTIC_TEST_PARAMETERS.add(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE);
    }

    private final IndependenceWrapper test = new ProbabilisticTest();
    private Knowledge knowledge;
    private List<Graph> bootstrapGraphs = new ArrayList<>();


    /** {@inheritDoc} */
    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        edu.pitt.dbmi.algo.bayesian.constraint.search.PagSamplingRfci pagSamplingRfci = new edu.pitt.dbmi.algo.bayesian.constraint.search.PagSamplingRfci(SimpleDataLoader.getDiscreteDataSet(dataSet));

        // PAG-Sampling-RFCI parameters
        pagSamplingRfci.setNumRandomizedSearchModels(parameters.getInt(Params.NUM_RANDOMIZED_SEARCH_MODELS));
        pagSamplingRfci.setVerbose(parameters.getBoolean(Params.VERBOSE));

        // Rfic parameters
        pagSamplingRfci.setKnowledge(this.knowledge);
        pagSamplingRfci.setDepth(parameters.getInt(Params.DEPTH));
        pagSamplingRfci.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));

        // ProbabilisticTest parameters
        pagSamplingRfci.setThreshold(parameters.getBoolean(Params.NO_RANDOMLY_DETERMINED_INDEPENDENCE));
        pagSamplingRfci.setCutoff(parameters.getDouble(Params.CUTOFF_IND_TEST));
        pagSamplingRfci.setPriorEquivalentSampleSize(parameters.getDouble(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE));

        return pagSamplingRfci.search();
    }

    /** {@inheritDoc} */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph trueGraph = new EdgeListGraph(graph);
        return GraphTransforms.dagToPag(trueGraph);
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "PAG-Sampling-RFCI " + this.test.getDescription();
    }

    /** {@inheritDoc} */
    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new LinkedList<>();

        parameters.addAll(PAG_SAMPLING_RFCI_PARAMETERS);
        parameters.addAll(RFCI_PARAMETERS);
        parameters.addAll(PROBABILISTIC_TEST_PARAMETERS);

        return parameters;
    }

    /** {@inheritDoc} */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /** {@inheritDoc} */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }
}

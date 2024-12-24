package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.independence.ProbabilisticTest;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.Rfci;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs RFCI-BSC, which is RFCI with bootstrap sampling of PAGs.
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "RFCI-BSC",
        command = "rfci-bsc",
        algoType = AlgType.forbid_latent_common_causes,
        dataType = DataType.Discrete
)
@Experimental
public class RfciBsc extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge {

    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * Independence test; must the ProbabilisticTest.
     */
    private final IndependenceWrapper test = new ProbabilisticTest();
    /**
     * Knowledge
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Blank constructor.
     */
    public RfciBsc() {

    }

    /**
     * Retrieves the knowledge associated with this object.
     *
     * @return The knowledge.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge object.
     *
     * @param knowledge The knowledge object to be set.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Runs a search algorithm using a given dataset and parameters.
     *
     * @param dataModel  The dataset to run the search on.
     * @param parameters The parameters for the search algorithm.
     * @return The resulting graph from the search algorithm.
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet && dataModel.isDiscrete())) {
            throw new IllegalArgumentException("Expecting a discrete dataset.");
        }

        Rfci search = new Rfci(this.test.getTest(dataModel, parameters));
        search.setKnowledge(this.knowledge);
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setMaxDiscriminatingPathLength(parameters.getInt(Params.MAX_DISCRIMINATING_PATH_LENGTH));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc RfciBsc = new edu.pitt.dbmi.algo.bayesian.constraint.search.RfciBsc(search);
        RfciBsc.setNumRandomizedSearchModels(parameters.getInt(Params.NUM_RANDOMIZED_SEARCH_MODELS));
        RfciBsc.setThresholdNoRandomDataSearch(parameters.getBoolean(Params.THRESHOLD_NO_RANDOM_DATA_SEARCH));
        RfciBsc.setCutoffDataSearch(parameters.getDouble(Params.CUTOFF_DATA_SEARCH));

        RfciBsc.setNumBscBootstrapSamples(parameters.getInt(Params.NUM_BSC_BOOTSTRAP_SAMPLES));
        RfciBsc.setThresholdNoRandomConstrainSearch(parameters.getBoolean(Params.THRESHOLD_NO_RANDOM_CONSTRAIN_SEARCH));
        RfciBsc.setCutoffConstrainSearch(parameters.getDouble(Params.CUTOFF_CONSTRAIN_SEARCH));

        RfciBsc.setLowerBound(parameters.getDouble(Params.LOWER_BOUND));
        RfciBsc.setUpperBound(parameters.getDouble(Params.UPPER_BOUND));
        RfciBsc.setOutputRBD(parameters.getBoolean(Params.OUTPUT_RBD));
        RfciBsc.setVerbose(parameters.getBoolean(Params.VERBOSE));
        try {
            return RfciBsc.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the comparison graph from the true directed graph, if there is one.
     *
     * @param graph The true directed graph, if there is one.
     * @return The true PAG.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph trueGraph = new EdgeListGraph(graph);
        return GraphTransforms.dagToPag(trueGraph);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "RFCI-BSC using " + this.test.getDescription();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the data type that the algorithm can handle, which is discrete.
     */
    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the parameters of the algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        // RFCI
        parameters.add(Params.DEPTH);
        parameters.add(Params.MAX_DISCRIMINATING_PATH_LENGTH);
        parameters.add(Params.COMPLETE_RULE_SET_USED);
        // RFCI-BSC
        parameters.add(Params.NUM_RANDOMIZED_SEARCH_MODELS);
        parameters.add(Params.THRESHOLD_NO_RANDOM_DATA_SEARCH);
        parameters.add(Params.CUTOFF_DATA_SEARCH);
        parameters.add(Params.NUM_BSC_BOOTSTRAP_SAMPLES);
        parameters.add(Params.THRESHOLD_NO_RANDOM_CONSTRAIN_SEARCH);
        parameters.add(Params.CUTOFF_CONSTRAIN_SEARCH);
        parameters.add(Params.LOWER_BOUND);
        parameters.add(Params.UPPER_BOUND);
        parameters.add(Params.OUTPUT_RBD);

        parameters.add(Params.VERBOSE);

        return parameters;
    }
}

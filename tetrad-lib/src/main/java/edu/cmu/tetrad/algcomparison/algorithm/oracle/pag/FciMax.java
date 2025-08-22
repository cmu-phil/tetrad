package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

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
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * FCI-Max algorithm.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FCI-Max",
        command = "fci-max",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
public class FciMax extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, TakesIndependenceWrapper,
        ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for FciMax.</p>
     */
    public FciMax() {
    }

    /**
     * <p>Constructor for FciMax.</p>
     *
     * @param test a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     */
    public FciMax(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * Runs a search algorithm to discover the causal graph structure.
     *
     * @param dataModel  the data set on which the search algorithm will be performed
     * @param parameters the parameters for the search algorithm
     * @return the discovered causal graph structure
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a data set for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        edu.cmu.tetrad.search.FciMax search = new edu.cmu.tetrad.search.FciMax(this.test.getTest(dataModel, parameters));
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setKnowledge(this.knowledge);
        search.setMaxDiscriminatingPathLength(parameters.getInt(Params.MAX_DISCRIMINATING_PATH_LENGTH));
        search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
        search.setPossibleDsepSearchDone(parameters.getBoolean(Params.DO_POSSIBLE_DSEP));
        search.setGuaranteePag(parameters.getBoolean(Params.GUARANTEE_PAG));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        return search.search();
    }

    /**
     * Returns the comparison graph transformed from the true directed graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph transformed from the true directed graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph trueGraph = new EdgeListGraph(graph);
        return GraphTransforms.dagToPag(trueGraph);
    }

    /**
     * Returns a description of the algorithm.
     *
     * @return a String representing the description of the algorithm.
     */
    public String getDescription() {
        return "FCI-Max (Fast Causal Inference Max P-value) using " + this.test.getDescription();
    }

    /**
     * Returns the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return the data type required for the search
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Returns the list of parameters used by the method. The parameters are returned as a List of Strings.
     *
     * @return a List of Strings representing the parameters used by the method.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add(Params.DEPTH);
        parameters.add(Params.MAX_DISCRIMINATING_PATH_LENGTH);
        parameters.add(Params.COMPLETE_RULE_SET_USED);
        parameters.add(Params.DO_POSSIBLE_DSEP);
        parameters.add(Params.GUARANTEE_PAG);
//        parameters.add(Params.PC_HEURISTIC);
        parameters.add(Params.TIME_LAG);

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    /**
     * Retrieves the knowledge associated with the algorithm.
     *
     * @return the knowledge object associated with the algorithm
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge associated with the algorithm.
     *
     * @param knowledge the knowledge object to be set
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the IndependenceWrapper associated with the algorithm.
     *
     * @return the IndependenceWrapper object associated with the algorithm
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the independence wrapper for the algorithm.
     *
     * @param test the independence wrapper
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}

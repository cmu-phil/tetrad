package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
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
import edu.cmu.tetrad.search.utils.PcCommon;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * The Fast Causal Inference (FCI) algorithm.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FCI",
        command = "fci",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
public class Fci extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, TakesIndependenceWrapper,
        ReturnsBootstrapGraphs, TakesCovarianceMatrix {

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
     * Constructor.
     */
    public Fci() {
    }

    /**
     * Constructor.
     *
     * @param test a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     */
    public Fci(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * Runs a search algorithm to find a graph based on the given data model and parameters.
     *
     * @param dataModel  the data model containing the dataset
     * @param parameters the parameters for the search algorithm
     * @return the resulting graph
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
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

        PcCommon.PcHeuristicType pcHeuristicType = switch (parameters.getInt(Params.PC_HEURISTIC)) {
            case 0 -> PcCommon.PcHeuristicType.NONE;
            case 1 -> PcCommon.PcHeuristicType.HEURISTIC_1;
            case 2 -> PcCommon.PcHeuristicType.HEURISTIC_2;
            case 3 -> PcCommon.PcHeuristicType.HEURISTIC_3;
            default ->
                    throw new IllegalArgumentException("Unknown conflict rule: " + parameters.getInt(Params.CONFLICT_RULE));
        };

        edu.cmu.tetrad.search.Fci search = new edu.cmu.tetrad.search.Fci(this.test.getTest(dataModel, parameters));
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setKnowledge(this.knowledge);
        search.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));
        search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
        search.setPossibleMsepSearchDone(parameters.getBoolean(Params.POSSIBLE_MSEP_DONE));
        search.setDoDiscriminatingPathTailRule(parameters.getBoolean(Params.DO_DISCRIMINATING_PATH_TAIL_RULE));
        search.setDoDiscriminatingPathColliderRule(parameters.getBoolean(Params.DO_DISCRIMINATING_PATH_COLLIDER_RULE));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setPcHeuristicType(pcHeuristicType);
        search.setStable(parameters.getBoolean(Params.STABLE_FAS));
        search.setRepairFaultyPag(parameters.getBoolean(Params.REPAIR_FAULTY_PAG));

        // Ablation
        search.setLeaveOutFinalOrientation(parameters.getBoolean(Params.ABLATATION_LEAVE_OUT_FINAL_ORIENTATION));

        return search.search();
    }

    /**
     * Returns the comparison graph based on the true directed graph, if there is one.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph trueGraph = new EdgeListGraph(graph);
        return GraphTransforms.dagToPag(trueGraph);
    }

    /**
     * Returns a short, one-line description of this algorithm. The description includes the description of the
     * independence test used by this algorithm.
     *
     * @return The description of the algorithm.
     */
    public String getDescription() {
        return "FCI (Fast Causal Inference) using " + this.test.getDescription();
    }

    /**
     * Returns the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return The data type required by the search algorithm.
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Retrieves the list of parameters used by this algorithm.
     *
     * @return The list of parameters used by this algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.DEPTH);
        parameters.add(Params.STABLE_FAS);
        parameters.add(Params.PC_HEURISTIC);
        parameters.add(Params.MAX_PATH_LENGTH);
        parameters.add(Params.POSSIBLE_MSEP_DONE);
        parameters.add(Params.DO_DISCRIMINATING_PATH_TAIL_RULE);
        parameters.add(Params.DO_DISCRIMINATING_PATH_COLLIDER_RULE);
        parameters.add(Params.COMPLETE_RULE_SET_USED);
        parameters.add(Params.TIME_LAG);
        parameters.add(Params.REPAIR_FAULTY_PAG);

        // Ablation
        parameters.add(Params.ABLATATION_LEAVE_OUT_FINAL_ORIENTATION);

        parameters.add(Params.VERBOSE);

        return parameters;
    }

    /**
     * Retrieves the knowledge object.
     *
     * @return The knowledge object.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge object for this method.
     *
     * @param knowledge The knowledge object to set.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the {@link IndependenceWrapper} object associated with this method. This method is used to get the
     * independence test used by the algorithm.
     *
     * @return The IndependenceWrapper object associated with this method.
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the IndependenceWrapper object associated with this method.
     *
     * @param test the independence wrapper to set
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }
}

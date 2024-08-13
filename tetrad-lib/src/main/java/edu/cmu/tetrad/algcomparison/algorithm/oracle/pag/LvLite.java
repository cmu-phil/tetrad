package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;


/**
 * This class represents the LV-Lite algorithm, which is an implementation of the GFCI algorithm for learning causal
 * structures from observational data using the BOSS algorithm as an initial CPDAG and using all score-based steps
 * afterward.
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "LV-Lite",
        command = "lv-lite",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
@Experimental
public class LvLite extends AbstractBootstrapAlgorithm implements Algorithm, UsesScoreWrapper, TakesIndependenceWrapper,
        HasKnowledge, ReturnsBootstrapGraphs, TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * The score to use.
     */
    private ScoreWrapper score;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * This class represents a LV-Lite algorithm.
     *
     * <p>
     * The LV-Lite algorithm is a bootstrap algorithm that runs a search algorithm to find a graph structure based on a
     * given data set and parameters. It is a subclass of the Abstract BootstrapAlgorithm class and implements the
     * Algorithm interface.
     * </p>
     *
     * @see AbstractBootstrapAlgorithm
     * @see Algorithm
     */
    public LvLite() {
        // Used for reflection; do not delete.
    }

    /**
     * LV-Lite is a class that represents a LV-Lite algorithm.
     *
     * <p>
     * The LV-Lite algorithm is a bootstrap algorithm that runs a search algorithm to find a graph structure based on a
     * given data set and parameters. It is a subclass of the AbstractBootstrapAlgorithm class and implements the
     * Algorithm interface.
     * </p>
     *
     * @param test  The independence test to use.
     * @param score The score to use.
     * @see AbstractBootstrapAlgorithm
     * @see Algorithm
     */
    public LvLite(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    /**
     * Runs the search algorithm to find a graph structure based on a given data model and parameters.
     *
     * @param dataModel  The data model to use for the search algorithm.
     * @param parameters The parameters to configure the search algorithm.
     * @return The resulting graph structure.
     * @throws IllegalArgumentException if the time lag is greater than 0 and the data model is not an instance of
     *                                  DataSet.
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a dataset for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        IndependenceTest test = this.test.getTest(dataModel, parameters);
        Score score = this.score.getScore(dataModel, parameters);

        if (test instanceof MsepTest) {
            if (parameters.getBoolean(Params.ABLATION_LEAVE_OUT_TUCKING_STEP)) {
                if (parameters.getInt(Params.LV_LITE_STARTS_WITH) == 1) {
                    throw new IllegalArgumentException("For d-separation oracle input, please use the GRaSP option.");
                }
            }
        }

        edu.cmu.tetrad.search.LvLite search = new edu.cmu.tetrad.search.LvLite(test, score);

        // BOSS
        search.setUseDataOrder(parameters.getBoolean(Params.USE_DATA_ORDER));
        search.setNumStarts(parameters.getInt(Params.NUM_STARTS));
        search.setUseBes(parameters.getBoolean(Params.USE_BES));

        // FCI-ORIENT
        search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));

        // LV-Lite
        search.setDoDiscriminatingPathTailRule(parameters.getBoolean(Params.DO_DISCRIMINATING_PATH_TAIL_RULE));
        search.setDoDiscriminatingPathColliderRule(parameters.getBoolean(Params.DO_DISCRIMINATING_PATH_COLLIDER_RULE));
        search.setRecursionDepth(parameters.getInt(Params.GRASP_DEPTH));
        search.setMaxBlockingPathLength(parameters.getInt(Params.MAX_BLOCKING_PATH_LENGTH));
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setMaxDdpPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));
        search.setTestTimeout(parameters.getLong(Params.TEST_TIMEOUT));
        search.setRepairFaultyPag(parameters.getBoolean(Params.REPAIR_FAULTY_PAG));

        // Ablation
        search.setAblationLeaveOutScoringStep(parameters.getBoolean(Params.ABLATION_LEAVE_OUT_SCORING_STEP));
        search.setAblationLeaveOutTestingStep(parameters.getBoolean(Params.ABLATION_LEAVE_OUT_TESTING_STEPS));

        if (parameters.getInt(Params.LV_LITE_STARTS_WITH) == 1) {
            search.setStartWith(edu.cmu.tetrad.search.LvLite.START_WITH.BOSS);
        } else if (parameters.getInt(Params.LV_LITE_STARTS_WITH) == 2) {
            search.setStartWith(edu.cmu.tetrad.search.LvLite.START_WITH.GRASP);
        } else {
            throw new IllegalArgumentException("Unknown start with option: " + parameters.getInt(Params.LV_LITE_STARTS_WITH));
        }

        // General
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setKnowledge(this.knowledge);

        return search.search();
    }

    /**
     * Retrieves a comparison graph by transforming a true directed graph into a partially directed graph (PAG).
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToPag(graph);
    }

    /**
     * Returns a short, one-line description of this algorithm. The description is generated by concatenating the
     * descriptions of the test and score objects associated with this algorithm.
     *
     * @return The description of this algorithm.
     */
    @Override
    public String getDescription() {
        return "LV-Lite (Latent Variable \"Lite\") using " + this.score.getDescription();
    }

    /**
     * Retrieves the data type required by the search algorithm.
     *
     * @return The data type required by the search algorithm.
     */
    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    /**
     * Retrieves the list of parameters used by the algorithm.
     *
     * @return The list of parameters used by the algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        // BOSS
        params.add(Params.USE_BES);
        params.add(Params.USE_DATA_ORDER);
        params.add(Params.NUM_STARTS);

        // FCI-ORIENT
        params.add(Params.COMPLETE_RULE_SET_USED);
        params.add(Params.DO_DISCRIMINATING_PATH_TAIL_RULE);
        params.add(Params.DO_DISCRIMINATING_PATH_COLLIDER_RULE);

        // LV-Lite
        params.add(Params.LV_LITE_STARTS_WITH);
        params.add(Params.GRASP_DEPTH);
        params.add(Params.MAX_BLOCKING_PATH_LENGTH);
        params.add(Params.DEPTH);
        params.add(Params.ABLATION_LEAVE_OUT_SCORING_STEP);
        params.add(Params.ABLATION_LEAVE_OUT_TESTING_STEPS);
        params.add(Params.MAX_PATH_LENGTH);
        params.add(Params.REPAIR_FAULTY_PAG);

        // General
        params.add(Params.TIME_LAG);
        params.add(Params.VERBOSE);
        params.add(Params.TEST_TIMEOUT);

        // Ablation
//        params.add(Params.ABLATATION_LEAVE_OUT_FINAL_ORIENTATION);

        return params;
    }

    /**
     * Retrieves the knowledge object associated with this method.
     *
     * @return The knowledge object.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge object associated with this method.
     *
     * @param knowledge the knowledge object to be set
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the ScoreWrapper object associated with this method.
     *
     * @return The ScoreWrapper object associated with this method.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * Sets the score wrapper for the algorithm.
     *
     * @param score the score wrapper.
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }
}

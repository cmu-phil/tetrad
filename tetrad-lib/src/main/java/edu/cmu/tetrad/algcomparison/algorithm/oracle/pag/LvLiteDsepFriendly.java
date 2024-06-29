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
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;


/**
 * Adjusts GFCI to use a permutation algorithm (such as BOSS-Tuck) to do the initial steps of finding adjacencies and
 * unshielded colliders.
 * <p>
 * GFCI reference is this:
 * <p>
 * J.M. Ogarrio and P. Spirtes and J. Ramsey, "A Hybrid Causal Search Algorithm for Latent Variable Models," JMLR 2016.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "LV-Lite-Dsep-Friendly",
//        command = "lv-lite-dsep-friendly",
//        algoType = AlgType.allow_latent_common_causes
//)
@Bootstrapping
public class LvLiteDsepFriendly extends AbstractBootstrapAlgorithm implements Algorithm, UsesScoreWrapper, TakesIndependenceWrapper,
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
     * <p>Constructor for GraspFci.</p>
     */
    public LvLiteDsepFriendly() {
        // Used for reflection; do not delete.
    }

    /**
     * <p>Constructor for GraspFci.</p>
     *
     * @param test  a {@link IndependenceWrapper} object
     * @param score a {@link ScoreWrapper} object
     */
    public LvLiteDsepFriendly(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    /**
     * Runs a search algorithm to find a graph structure based on a given data set and parameters.
     *
     * @param dataModel  the data set to be used for the search algorithm
     * @param parameters the parameters for the search algorithm
     * @return the graph structure found by the search algorithm
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

        test.setVerbose(parameters.getBoolean(Params.VERBOSE));
        edu.cmu.tetrad.search.LvLiteDsepFriendly search = new edu.cmu.tetrad.search.LvLiteDsepFriendly(test, score);

        // GRaSP
        search.setSeed(parameters.getLong(Params.SEED));
        search.setOrdered(parameters.getBoolean(Params.GRASP_ORDERED_ALG));
        search.setAllowInternalRandomness(parameters.getBoolean(Params.ALLOW_INTERNAL_RANDOMNESS));
        search.setUseScore(parameters.getBoolean(Params.GRASP_USE_SCORE));
        search.setUseRaskuttiUhler(parameters.getBoolean(Params.GRASP_USE_RASKUTTI_UHLER));
        search.setUseDataOrder(parameters.getBoolean(Params.USE_DATA_ORDER));
        search.setNumStarts(parameters.getInt(Params.NUM_STARTS));
        search.setDepth(parameters.getInt(Params.GRASP_DEPTH));

        // LV-Lite
        search.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));
        search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
        search.setDoDiscriminatingPathColliderRule(parameters.getBoolean(Params.DO_DISCRIMINATING_PATH_COLLIDER_RULE));
        search.setDoDiscriminatingPathTailRule(parameters.getBoolean(Params.DO_DISCRIMINATING_PATH_TAIL_RULE));
        search.setAllowableScoreDrop(parameters.getDouble(Params.ALLOWABLE_SCORE_DROP));

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
        return "LV-Lite-Dsep-Friendly (LV-Lite that can be used from a d-separation oracle--uses GRaSP) using " + this.test.getDescription()
               + " and " + this.score.getDescription();
    }

    /**
     * Retrieves the data type required by the search algorithm.
     *
     * @return The data type required by the search algorithm.
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Retrieves the list of parameters used by the algorithm.
     *
     * @return The list of parameters used by the algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        // GRaSP
        params.add(Params.GRASP_ORDERED_ALG);
        params.add(Params.GRASP_USE_RASKUTTI_UHLER);
        params.add(Params.USE_DATA_ORDER);
        params.add(Params.NUM_STARTS);
        params.add(Params.ALLOW_INTERNAL_RANDOMNESS);
        params.add(Params.GRASP_DEPTH);

        // FCI
        params.add(Params.DEPTH);
        params.add(Params.MAX_PATH_LENGTH);
        params.add(Params.COMPLETE_RULE_SET_USED);
        params.add(Params.DO_DISCRIMINATING_PATH_COLLIDER_RULE);
        params.add(Params.DO_DISCRIMINATING_PATH_TAIL_RULE);
        params.add(Params.ALLOWABLE_SCORE_DROP);

        // General
        params.add(Params.TIME_LAG);
        params.add(Params.SEED);
        params.add(Params.VERBOSE);

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
     * Retrieves the IndependenceWrapper object associated with this method. The IndependenceWrapper object contains an
     * IndependenceTest that checks the independence of two variables conditional on a set of variables using a given
     * dataset and parameters .
     *
     * @return The IndependenceWrapper object associated with this method.
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the independence wrapper.
     *
     * @param test the independence wrapper.
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
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
}

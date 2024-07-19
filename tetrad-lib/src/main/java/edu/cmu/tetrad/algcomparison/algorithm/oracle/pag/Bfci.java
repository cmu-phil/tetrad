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
import edu.cmu.tetrad.search.BFci;
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
@edu.cmu.tetrad.annotation.Algorithm(
        name = "BFCI",
        command = "bfci",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
@Experimental
public class Bfci extends AbstractBootstrapAlgorithm implements Algorithm, UsesScoreWrapper,
        TakesIndependenceWrapper, HasKnowledge, ReturnsBootstrapGraphs,
        TakesCovarianceMatrix {

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
     * No-arg constructor. Used for reflection; do not delete.
     */
    public Bfci() {
        // Used for reflection; do not delete.
    }

    /**
     * Constructs a new BFCI algorithm using the given test and score.
     *
     * @param test  the independence test to use
     * @param score the score to use
     */
    public Bfci(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    /**
     * Runs the search algorithm using the given dataset and parameters and returns the resulting graph.
     *
     * @param dataModel  the data model to run the search on
     * @param parameters the parameters used for the search algorithm
     * @return the graph resulting from the search algorithm
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

        BFci search = new BFci(this.test.getTest(dataModel, parameters), this.score.getScore(dataModel, parameters));

        search.setSeed(parameters.getLong(Params.SEED));
        search.setBossUseBes(parameters.getBoolean(Params.USE_BES));
        search.setMaxPathLength(parameters.getInt(Params.MAX_PATH_LENGTH));
        search.setSepsetFinderMethod(parameters.getInt(Params.SEPSET_FINDER_METHOD));
        search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
        search.setDoDiscriminatingPathTailRule(parameters.getBoolean(Params.DO_DISCRIMINATING_PATH_TAIL_RULE));
        search.setDoDiscriminatingPathColliderRule(parameters.getBoolean(Params.DO_DISCRIMINATING_PATH_COLLIDER_RULE));
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setNumThreads(parameters.getInt(Params.NUM_THREADS));
        search.setRepairFaultyPag(parameters.getBoolean(Params.REPAIR_FAULTY_PAG));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        // Ablation
        search.setLeaveOutFinalOrientation(parameters.getBoolean(Params.ABLATATION_LEAVE_OUT_FINAL_ORIENTATION));

        search.setKnowledge(knowledge);

        search.setNumStarts(parameters.getInt(Params.NUM_STARTS));

        return search.search();
    }

    /**
     * Retrieves the comparison graph generated by applying the DAG-to-PAG transformation to the given true directed
     * graph.
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph generated by applying the DAG-to-PAG transformation.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToPag(graph);
    }

    /**
     * Returns a description of the BFCI (Best-order FCI) algorithm using the description of its independence test and
     * score.
     *
     * @return The description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "BFCI (Best-order FCI) using " + this.test.getDescription()
               + " and " + this.score.getDescription();
    }

    /**
     * Retrieves the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return the data type required by the search algorithm
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * Retrieves the list of parameters used for the BFCI (Best-order FCI) algorithm.
     *
     * @return the list of parameters used for the BFCI algorithm
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        params.add(Params.USE_BES);
        params.add(Params.MAX_PATH_LENGTH);
        params.add(Params.SEPSET_FINDER_METHOD);
        params.add(Params.COMPLETE_RULE_SET_USED);
        params.add(Params.DO_DISCRIMINATING_PATH_TAIL_RULE);
        params.add(Params.DO_DISCRIMINATING_PATH_COLLIDER_RULE);
        params.add(Params.DEPTH);
        params.add(Params.TIME_LAG);
        params.add(Params.SEED);
        params.add(Params.NUM_THREADS);
        params.add(Params.REPAIR_FAULTY_PAG);
        params.add(Params.VERBOSE);

        // Parameters
        params.add(Params.NUM_STARTS);

        // Ablation
        params.add(Params.ABLATATION_LEAVE_OUT_FINAL_ORIENTATION);

        return params;
    }


    /**
     * Retrieves the knowledge associated with the algorithm.
     *
     * @return the knowledge associated with the algorithm
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge associated with the algorithm.
     *
     * @param knowledge a knowledge object
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Returns the IndependenceWrapper associated with this Bfci algorithm.
     *
     * @return the IndependenceWrapper object
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * Sets the IndependenceWrapper object for this algorithm.
     *
     * @param test the IndependenceWrapper object to set
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * Retrieves the ScoreWrapper associated with this algorithm.
     *
     * @return The ScoreWrapper object.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * Sets the score wrapper for this algorithm.
     *
     * @param score the score wrapper to set
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }
}

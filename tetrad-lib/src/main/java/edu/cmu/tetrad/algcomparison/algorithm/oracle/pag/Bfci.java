package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
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
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

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
public class Bfci implements Algorithm, UsesScoreWrapper,
        TakesIndependenceWrapper, HasKnowledge, ReturnsBootstrapGraphs {

    @Serial
    private static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private ScoreWrapper score;
    private Knowledge knowledge = new Knowledge();
    private List<Graph> bootstrapGraphs = new ArrayList<>();

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

    /** {@inheritDoc} */
    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            if (parameters.getInt(Params.TIME_LAG) > 0) {
                DataSet dataSet = (DataSet) dataModel;
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
            search.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
            search.setDoDiscriminatingPathRule(parameters.getBoolean(Params.DO_DISCRIMINATING_PATH_RULE));
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));

            search.setKnowledge(knowledge);

            search.setNumStarts(parameters.getInt(Params.NUM_STARTS));

            return search.search();
        } else {
            Bfci algorithm = new Bfci(this.test, this.score);
            DataSet data = (DataSet) dataModel;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            Graph graph = search.search();
            if (parameters.getBoolean(Params.SAVE_BOOTSTRAP_GRAPHS)) this.bootstrapGraphs = search.getGraphs();
            return graph;
        }
    }

    /** {@inheritDoc} */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToPag(graph);
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "BFCI (Best-order FCI) using " + this.test.getDescription()
                + " and " + this.score.getDescription();
    }

    /** {@inheritDoc} */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        params.add(Params.USE_BES);
        params.add(Params.MAX_PATH_LENGTH);
        params.add(Params.COMPLETE_RULE_SET_USED);
        params.add(Params.DO_DISCRIMINATING_PATH_RULE);
        params.add(Params.DEPTH);
        params.add(Params.TIME_LAG);
        params.add(Params.SEED);
        params.add(Params.VERBOSE);

        // Parameters
        params.add(Params.NUM_STARTS);

        return params;
    }


    /** {@inheritDoc} */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /** {@inheritDoc} */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /** {@inheritDoc} */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /** {@inheritDoc} */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

    /** {@inheritDoc} */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /** {@inheritDoc} */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /** {@inheritDoc} */
    @Override
    public List<Graph> getBootstrapGraphs() {
        return this.bootstrapGraphs;
    }
}

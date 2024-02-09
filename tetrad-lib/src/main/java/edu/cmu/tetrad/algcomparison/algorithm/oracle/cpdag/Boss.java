package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.List;

/**
 * BOSS-DC (Best Order Score Search Divide and Conquer)
 *
 * @author bryanandrews
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "BOSS",
        command = "boss",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class Boss implements Algorithm, UsesScoreWrapper, HasKnowledge,
        ReturnsBootstrapGraphs {
    private static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private Knowledge knowledge = new Knowledge();
    private List<Graph> bootstrapGraphs = new ArrayList<>();
    private long seed = 01;

    /**
     * Constructs a new BOSS algorithm.
     */
    public Boss() {
        // Used in reflection; do not delete.
    }

    /**
     * Constructs a new BOSS algorithm with the given score.
     *
     * @param score the score to use
     */
    public Boss(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * {@inheritDoc}
     *
     * Runs the BOSS algorithm.
     */
    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        this.seed = parameters.getLong(Params.SEED);

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

            Score score = this.score.getScore(dataModel, parameters);

            edu.cmu.tetrad.search.Boss boss = new edu.cmu.tetrad.search.Boss(score);

            boss.setUseBes(parameters.getBoolean(Params.USE_BES));
            boss.setNumStarts(parameters.getInt(Params.NUM_STARTS));
            boss.setNumThreads(parameters.getInt(Params.NUM_THREADS));
            boss.setUseDataOrder(parameters.getBoolean(Params.USE_DATA_ORDER));
            boss.setVerbose(parameters.getBoolean(Params.VERBOSE));
            PermutationSearch permutationSearch = new PermutationSearch(boss);
            permutationSearch.setKnowledge(this.knowledge);
            permutationSearch.setSeed(seed);
            Graph graph = permutationSearch.search();
            LogUtilsSearch.stampWithScore(graph, score);
            LogUtilsSearch.stampWithBic(graph, dataModel);
            return graph;
        } else {
            Boss algorithm = new Boss(this.score);

            DataSet data = (DataSet) dataModel;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);

            search.setParameters(parameters);
            Graph graph = search.search();
            if (parameters.getBoolean(Params.SAVE_BOOTSTRAP_GRAPHS)) this.bootstrapGraphs = search.getGraphs();
            return graph;
        }
    }

    /**
     * {@inheritDoc}
     *
     * Returns the true graph if there is one.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * {@inheritDoc}
     *
     * Returns the description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "BOSS (Best Order Score Search) using " + this.score.getDescription();
    }

    /**
     * {@inheritDoc}
     *
     * Returns the name of the algorithm.
     */
    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    /**
     * {@inheritDoc}
     *
     * Returns the parameters for the algorithm.
     */
    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();

        // Parameters
        params.add(Params.USE_BES);
        params.add(Params.NUM_STARTS);
        params.add(Params.TIME_LAG);
        params.add(Params.NUM_THREADS);
        params.add(Params.USE_DATA_ORDER);
        params.add(Params.SEED);
        params.add(Params.VERBOSE);

        return params;
    }

    /**
     * {@inheritDoc}
     *
     * Returns the score wrapper.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * {@inheritDoc}
     *
     * Sets the score wrapper.
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * {@inheritDoc}
     *
     * Returns the knowledge.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     *
     * Sets the knowledge.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * {@inheritDoc}
     *
     * Returns the bootstrap graphs.
     */
    @Override
    public List<Graph> getBootstrapGraphs() {
        return this.bootstrapGraphs;
    }
}

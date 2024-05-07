package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
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

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * BOSS (Best Order Score Search)
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
public class Boss extends AbstractBootstrapAlgorithm implements Algorithm, UsesScoreWrapper, HasKnowledge,
        ReturnsBootstrapGraphs, TakesCovarianceMatrix {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The score to use.
     */
    private ScoreWrapper score;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

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
     * <p>
     * Runs the BOSS algorithm.
     */
    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) {
        long seed = parameters.getLong(Params.SEED);
        parameters.set(Params.NUM_THREADS, 4);

        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a dataset for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
            if (dataModel.getName() != null) {
                timeSeries.setName(dataModel.getName());
            }
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        Score myScore = this.score.getScore(dataModel, parameters);

        edu.cmu.tetrad.search.Boss boss = new edu.cmu.tetrad.search.Boss(myScore);

        boss.setUseBes(parameters.getBoolean(Params.USE_BES));
        boss.setNumStarts(parameters.getInt(Params.NUM_STARTS));
        boss.setNumThreads(parameters.getInt(Params.NUM_THREADS));
        boss.setUseDataOrder(parameters.getBoolean(Params.USE_DATA_ORDER));
        boss.setVerbose(parameters.getBoolean(Params.VERBOSE));
        PermutationSearch permutationSearch = new PermutationSearch(boss);
        permutationSearch.setKnowledge(this.knowledge);
        permutationSearch.setSeed(seed);
        Graph graph = permutationSearch.search();
        LogUtilsSearch.stampWithScore(graph, myScore);
        LogUtilsSearch.stampWithBic(graph, dataModel);

        return graph;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the true graph if there is one.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the description of the algorithm.
     */
    @Override
    public String getDescription() {
        return "BOSS (Best Order Score Search) using " + this.score.getDescription();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the algorithm.
     */
    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    /**
     * {@inheritDoc}
     * <p>
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
     * <p>
     * Returns the score wrapper.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the score wrapper.
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the knowledge.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the knowledge.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

}

package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
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
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.search.utils.LogUtilsSearch.stampWithBic;

/**
 * BOSS-LiNGAM algorithm. This runs the BOSS algorithm to find the CPDAG and then orients the undirected edges using the
 * LiNGAM algorithm.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(name = "BOSS-LiNGAM", command = "boss-lingam", algoType = AlgType.forbid_latent_common_causes)
@Bootstrapping
public class BossLingam extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, UsesScoreWrapper, ReturnsBootstrapGraphs {
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
     * Constructs a new BOSS-LiNGAM algorithm.
     */
    public BossLingam() {
    }

    /**
     * Constructs a new BOSS-LiNGAM algorithm with the given score.
     *
     * @param scoreWrapper the score to use
     */
    public BossLingam(ScoreWrapper scoreWrapper) {
        this.score = scoreWrapper;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Runs the BOSS-LiNGAM algorithm.
     */
    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException("Expecting a dataset.");
        }

        if (parameters.getInt(Params.TIME_LAG) > 0) {
            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }
            dataSet = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        Score myScore = this.score.getScore(dataSet, parameters);

        edu.cmu.tetrad.search.Boss boss = new edu.cmu.tetrad.search.Boss(myScore);
        boss.setUseBes(parameters.getBoolean(Params.USE_BES));
        boss.setNumStarts(parameters.getInt(Params.NUM_STARTS));
        boss.setNumThreads(parameters.getInt(Params.NUM_THREADS));
        boss.setUseDataOrder(parameters.getBoolean(Params.USE_DATA_ORDER));
        boss.setVerbose(parameters.getBoolean(Params.VERBOSE));
        PermutationSearch permutationSearch = new PermutationSearch(boss);
        permutationSearch.setSeed(parameters.getLong(Params.SEED));
        permutationSearch.setKnowledge(this.knowledge);

        Graph cpdag = permutationSearch.search();

        edu.cmu.tetrad.search.BossLingam bossLingam = new edu.cmu.tetrad.search.BossLingam(cpdag, dataSet);
        Graph graph = bossLingam.search();

        stampWithBic(graph, dataSet);

        return graph;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the comparison graph.
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
        return "BOSS-LiNGAM using " + this.score.getDescription();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the data type that the algorithm can handle.
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
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.USE_BES);
        parameters.add(Params.NUM_STARTS);
        parameters.add(Params.TIME_LAG);
        parameters.add(Params.NUM_THREADS);
        parameters.add(Params.USE_DATA_ORDER);
        parameters.add(Params.SEED);
        parameters.add(Params.VERBOSE);
        return parameters;
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
        this.knowledge = new Knowledge(knowledge);
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

}

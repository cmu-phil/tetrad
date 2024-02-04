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
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.search.utils.LogUtilsSearch.stampWithBic;

/**
 * Peter/Clark algorithm (PC).
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(name = "BOSS-LiNGAM", command = "boss-lingam", algoType = AlgType.forbid_latent_common_causes)
@Bootstrapping
public class BossLingam implements Algorithm, HasKnowledge, UsesScoreWrapper, ReturnsBootstrapGraphs {
    private static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private Knowledge knowledge = new Knowledge();
    private List<Graph> bootstrapGraphs = new ArrayList<>();

    public BossLingam() {
    }

    public BossLingam(ScoreWrapper scoreWrapper) {
        this.score = scoreWrapper;
    }

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

            Score score = this.score.getScore(dataModel, parameters);

            edu.cmu.tetrad.search.Boss boss = new edu.cmu.tetrad.search.Boss(score);
            boss.setUseBes(parameters.getBoolean(Params.USE_BES));
            boss.setNumStarts(parameters.getInt(Params.NUM_STARTS));
            boss.setNumThreads(parameters.getInt(Params.NUM_THREADS));
            boss.setUseDataOrder(parameters.getBoolean(Params.USE_DATA_ORDER));
            boss.setVerbose(parameters.getBoolean(Params.VERBOSE));
            PermutationSearch permutationSearch = new PermutationSearch(boss);
            permutationSearch.setSeed(parameters.getLong(Params.SEED));
            permutationSearch.setKnowledge(this.knowledge);

            Graph cpdag = permutationSearch.search();

            edu.cmu.tetrad.search.BossLingam bossLingam = new edu.cmu.tetrad.search.BossLingam(cpdag, (DataSet) dataModel);
            Graph graph = bossLingam.search();

            stampWithBic(graph, dataModel);
            return graph;
        } else {
            BossLingam pcAll = new BossLingam(this.score);

            DataSet data = (DataSet) dataModel;
            GeneralResamplingTest search = new GeneralResamplingTest(data, pcAll, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            Graph graph = search.search();
            if (parameters.getBoolean(Params.SAVE_BOOTSTRAP_GRAPHS)) this.bootstrapGraphs = search.getGraphs();
            return graph;
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "BOSS-LiNGAM using " + this.score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

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

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public List<Graph> getBootstrapGraphs() {
        return this.bootstrapGraphs;
    }
}
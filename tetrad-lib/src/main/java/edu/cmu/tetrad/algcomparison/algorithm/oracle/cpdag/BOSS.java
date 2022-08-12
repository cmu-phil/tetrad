package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.Boss2;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.search.TimeSeriesUtils;
import edu.cmu.tetrad.sem.Parameter;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.List;

/**
 * GRaSP (Greedy Relaxations of Sparsest Permutation)
 *
 * @author bryanandrews
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "BOSS",
        command = "boss",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
@Experimental
public class BOSS implements Algorithm, UsesScoreWrapper, HasKnowledge {
    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private IKnowledge knowledge = new Knowledge2();

    public BOSS() {
        // Used in reflection; do not delete.
    }

    public BOSS(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            if (parameters.getInt(Params.TIME_LAG) > 0) {
                DataSet dataSet = (DataSet) dataModel;
                DataSet timeSeries = TimeSeriesUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG));
                if (dataSet.getName() != null) {
                    timeSeries.setName(dataSet.getName());
                }
                dataModel = timeSeries;
                knowledge = timeSeries.getKnowledge();
            }

            Score score = this.score.getScore(dataModel, parameters);

            Boss2 boss = new Boss2(score);
            boss.setAlgType(Boss2.AlgType.BOSS);

            boss.setDepth(parameters.getInt(Params.GRASP_DEPTH));
            boss.setUseDataOrder(parameters.getBoolean(Params.GRASP_USE_DATA_ORDER));
            boss.setVerbose(parameters.getBoolean(Params.VERBOSE));

            boss.setNumStarts(parameters.getInt(Params.NUM_STARTS));
            boss.setKnowledge(this.knowledge);
            boss.bestOrder(score.getVariables());
            return boss.getGraph();
        } else {
            BOSS algorithm = new BOSS(this.score);

            DataSet data = (DataSet) dataModel;
            GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING), parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE), parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT), parameters.getInt(Params.RESAMPLING_ENSEMBLE), parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
            search.setKnowledge(this.knowledge);


            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "BOSS (Better Order Score Search) using " + this.score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();

        // Flags
        params.add(Params.GRASP_DEPTH);
        params.add(Params.GRASP_USE_SCORE);
        params.add(Params.GRASP_USE_RASKUTTI_UHLER);
        params.add(Params.GRASP_USE_DATA_ORDER);
        params.add(Params.CACHE_SCORES);
        params.add(Params.TIME_LAG);
        params.add(Params.VERBOSE);

        // Parameters
        params.add(Params.NUM_STARTS);

        return params;
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
    public IKnowledge getKnowledge() {
        return this.knowledge.copy();
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge.copy();
    }
}

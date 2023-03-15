package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.BossOld;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.search.TimeSeriesUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;

import java.util.ArrayList;
import java.util.List;

/**
 * BOSS (Best Order Score Search)
 *
 * @author bryanandrews
 * @author josephramsey
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "BOSS-OLD",
//        command = "boss-old",
//        algoType = AlgType.forbid_latent_common_causes
//)
@Bootstrapping
@Experimental
@Deprecated
public class BOSSOLD implements Algorithm, UsesScoreWrapper, TakesIndependenceWrapper, HasKnowledge {
    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private IndependenceWrapper test;
    private Knowledge knowledge = new Knowledge();

    public BOSSOLD() {
        // Used in reflection; do not delete.
    }

//    public BOSS(ScoreWrapper score) {
//        this.score = score;
//    }

    public BOSSOLD(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
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
            IndependenceTest test = this.test.getTest(dataModel, parameters);

            BossOld bossOld = new BossOld(test, score);

            if (parameters.getInt(Params.BOSS_ALG) == 1) {
                bossOld.setAlgType(BossOld.AlgType.BOSS1);
            } else if (parameters.getInt(Params.BOSS_ALG) == 2) {
                bossOld.setAlgType(BossOld.AlgType.BOSS2);
            } else if (parameters.getInt(Params.BOSS_ALG) == 3) {
                bossOld.setAlgType(BossOld.AlgType.BOSS3);
            } else {
                throw new IllegalArgumentException("Unrecognized boss algorithm type.");
            }

            bossOld.setDepth(parameters.getInt(Params.DEPTH));
            bossOld.setUseDataOrder(parameters.getBoolean(Params.GRASP_USE_DATA_ORDER));
            bossOld.setUseScore(parameters.getBoolean(Params.GRASP_USE_SCORE));
            bossOld.setUseRaskuttiUhler(parameters.getBoolean(Params.GRASP_USE_RASKUTTI_UHLER));
            bossOld.setVerbose(parameters.getBoolean(Params.VERBOSE));
            bossOld.setNumStarts(parameters.getInt(Params.NUM_STARTS));
            bossOld.setCaching(parameters.getBoolean(Params.CACHE_SCORES));

            bossOld.setKnowledge(this.knowledge);

            bossOld.bestOrder(new ArrayList<Node>(score.getVariables()));
            return bossOld.getGraph(true);
        } else {
            BOSSOLD algorithm = new BOSSOLD(this.test, this.score);

            DataSet data = (DataSet) dataModel;
            GeneralResamplingTest search = new GeneralResamplingTest(
                    data,
                    algorithm,
                    parameters.getInt(Params.NUMBER_RESAMPLING),
                    parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE),
                    parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT),
                    parameters.getInt(Params.RESAMPLING_ENSEMBLE),
                    parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
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
        return "BOSS (Best Order Score Search) using " + this.score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();

        // Flags
        params.add(Params.BOSS_ALG);
        params.add(Params.DEPTH);
        params.add(Params.GRASP_USE_SCORE);
        params.add(Params.GRASP_USE_RASKUTTI_UHLER);
        params.add(Params.GRASP_USE_DATA_ORDER);
        params.add(Params.TIME_LAG);
        params.add(Params.CACHE_SCORES);
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
    public Knowledge getKnowledge() {
        return this.knowledge.copy();
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }
}

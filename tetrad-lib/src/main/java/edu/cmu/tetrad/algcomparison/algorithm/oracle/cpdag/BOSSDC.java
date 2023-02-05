package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.BossDC;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * BOSS-DC (Best Order Score Search Divide and Conquer)
 *
 * @author bryanandrews
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "BOSS-DC",
        command = "boss-dc",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
@Experimental
public class BOSSDC implements Algorithm, UsesScoreWrapper {
    static final long serialVersionUID = 23L;
    private ScoreWrapper score;

    public BOSSDC() {
        // Used in reflection; do not delete.
    }

    public BOSSDC(ScoreWrapper score) {
        this.score = score;
    }


    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        Score score = this.score.getScore(dataModel, parameters);
        BossDC boss = new BossDC(score);

        if (parameters.getInt(Params.BOSS_ALG) == 1) {
            boss.setAlgType(Boss.AlgType.BOSS1);
        } else if (parameters.getInt(Params.BOSS_ALG) == 2) {
            boss.setAlgType(Boss.AlgType.BOSS2);
        } else {
            throw new IllegalArgumentException("Unrecognized boss algorithm type.");
        }

        boss.setDepth(parameters.getInt(Params.DEPTH));
        boss.setUseDataOrder(parameters.getBoolean(Params.GRASP_USE_DATA_ORDER));
        boss.setVerbose(parameters.getBoolean(Params.VERBOSE));
        boss.setNumStarts(parameters.getInt(Params.NUM_STARTS));
        boss.setCaching(parameters.getBoolean(Params.CACHE_SCORES));

        boss.bestOrder(new ArrayList<>(score.getVariables()));

        return boss.getGraph(true);
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "BOSSDC (Best Order Score Search Divide and Conquer) using " + this.score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();

        // Flags
        params.add(Params.GRASP_USE_DATA_ORDER);
        params.add(Params.CACHE_SCORES);
        params.add(Params.VERBOSE);

        // Parameters
        params.add(Params.BOSS_ALG);
        params.add(Params.NUM_STARTS);
        params.add(Params.DEPTH);

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

}

package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.BossNew;
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
        name = "BOSS-New",
        command = "boss-new",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
@Experimental
public class BOSSNEW implements Algorithm, UsesScoreWrapper, HasKnowledge {
    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private Knowledge knowledge = new Knowledge();

    public BOSSNEW() {
        // Used in reflection; do not delete.
    }

    public BOSSNEW(ScoreWrapper score) {
        this.score = score;
    }


    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        Score score = this.score.getScore(dataModel, parameters);
        BossNew boss = new BossNew(score);

//        Knowledge knowledge = new Knowledge();
//        for (int tier = 0; tier < 10; tier++) {
//            for (int i = 1; i <= 100; i++) {
//                knowledge.addToTier(tier, "X" + (100 * tier + i));
//            }
//        }
        boss.setKnowledge(this.knowledge);

        boss.setDepth(parameters.getInt(Params.DEPTH));
        boss.setVerbose(parameters.getBoolean(Params.VERBOSE));
        boss.setNumStarts(parameters.getInt(Params.NUM_STARTS));

        return boss.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "BOSSNEW (Best Order Score Search) using " + this.score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();

        // Flags
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

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }
}
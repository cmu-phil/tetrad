package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Bdce;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * BOSS-DC (Best Order Score Search Divide and Conquer Experimental)
 *
 * @author bryanandrews
 * @author josephramsey
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "BOSS-DCE",
//        command = "boss-dce",
//        algoType = AlgType.forbid_latent_common_causes
//)
@Bootstrapping
@Experimental
public class BDCE implements Algorithm, UsesScoreWrapper {
    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private List<Graph> bootstrapGraphs = new ArrayList<>();


    public BDCE() {
        // Used in reflection; do not delete.
    }

    public BDCE(ScoreWrapper score) {
        this.score = score;
    }


    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        Score score = this.score.getScore(dataModel, parameters);
        Bdce boss = new Bdce(score);

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
        return "BOSSDC (Best Order Score Search Divide and Conquer Experimental) using " + this.score.getDescription();
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

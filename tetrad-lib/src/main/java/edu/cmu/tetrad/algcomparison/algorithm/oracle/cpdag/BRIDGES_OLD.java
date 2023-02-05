package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.BridgesOld;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * BRIDGES (experimental algorithm).
 *
 * @author bryanandrews
 */
//@edu.cmu.tetrad.annotation.Algorithm(
//        name = "BRIDGES_OLD",
//        command = "bridges-old",
//        algoType = AlgType.forbid_latent_common_causes
//)
//@Bootstrapping
//@Experimental
public class BRIDGES_OLD implements Algorithm, HasKnowledge, UsesScoreWrapper {

    static final long serialVersionUID = 23L;

    private ScoreWrapper score;

    private Knowledge knowledge = new Knowledge();


    public BRIDGES_OLD() {}

    public BRIDGES_OLD(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {

        Score score = this.score.getScore(dataModel, parameters);
        Graph graph;

        BridgesOld search = new BridgesOld(score);
//        search.setKnowledge(knowledge);
        search.setVerbose(false);
//        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setMaxDegree(parameters.getInt(Params.MAX_DEGREE));
        search.setSymmetricFirstStep(parameters.getBoolean(Params.SYMMETRIC_FIRST_STEP));
        search.setFaithfulnessAssumed(parameters.getBoolean(Params.FAITHFULNESS_ASSUMED));
        search.setParallelized(parameters.getBoolean(Params.PARALLELIZED));

        Object obj = parameters.get(Params.PRINT_STREAM);
        if (obj instanceof PrintStream) {
            search.setOut((PrintStream) obj);
        }

        graph = search.search();

        return graph;

    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "BRIDGES_OLD (BRIDGES is not Restricted to Imaps During Greedy Equivalent Search) using " + this.score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.SYMMETRIC_FIRST_STEP);
        parameters.add(Params.MAX_DEGREE);
        parameters.add(Params.PARALLELIZED);
        parameters.add(Params.FAITHFULNESS_ASSUMED);
        parameters.add(Params.VERBOSE);

        return parameters;
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
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }


}

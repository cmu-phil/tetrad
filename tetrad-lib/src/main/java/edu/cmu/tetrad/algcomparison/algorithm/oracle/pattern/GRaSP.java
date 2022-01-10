package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.UsesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Grasp;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * GRaSP
 *
 * @author bandrews
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "GRaSP",
        command = "grasp",
        algoType = AlgType.forbid_latent_common_causes
)

public class GRaSP implements Algorithm, UsesScoreWrapper {

    static final long serialVersionUID = 23L;
    private ScoreWrapper score;

    public GRaSP() { }

    public GRaSP(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {

        Grasp search = new Grasp(this.score.getScore(dataSet, parameters));
        search.setDepth(parameters.getInt(Params.DEPTH,1));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));

        Object obj = parameters.get(Params.PRINT_STREAM);

        if (obj instanceof PrintStream) {
            search.setOut((PrintStream) obj);
        }

        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "GRaSP using " + this.score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.DEPTH);

        return parameters;
    }

    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

}

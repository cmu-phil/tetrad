package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;

/**
 * CPC.
 *
 * @author jdramsey
 */
public class Jcpc implements Algorithm, TakesInitialGraph, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private ScoreWrapper score;
    private Algorithm initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public Jcpc(IndependenceWrapper type, ScoreWrapper score) {
        this.test = type;
        this.score = score;
    }

    public Jcpc(IndependenceWrapper type, Algorithm initialGraph) {
        this.test = type;
        this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {

        DataSet continuousDataSet = DataUtils.getContinuousDataSet(dataSet);
        edu.cmu.tetrad.search.Jcpc search = new edu.cmu.tetrad.search.Jcpc(
                test.getTest(continuousDataSet, parameters),
                score.getScore(continuousDataSet, parameters));
        search.setKnowledge(knowledge);

        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "JCPC (Joe's CPC) using " + test.getDescription() + (initialGraph != null ? " with initial graph from " +
                initialGraph.getDescription() : "");
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.add("depth");
        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }
}

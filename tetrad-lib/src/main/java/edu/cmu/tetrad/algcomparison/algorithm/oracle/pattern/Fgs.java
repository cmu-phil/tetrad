package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;

/**
 * FGS (the heuristic version).
 *
 * @author jdramsey
 */
public class Fgs implements Algorithm, TakesInitialGraph, HasKnowledge {
    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private Algorithm initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();

    public Fgs(ScoreWrapper score) {
        this.score = score;
    }

    public Fgs(ScoreWrapper score, Algorithm initialGraph) {
        this.score = score;
        this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        Graph initial = null;

        if (initialGraph != null) {
            initial = initialGraph.search(dataSet, parameters);
        }

        edu.cmu.tetrad.search.Fgs search = new edu.cmu.tetrad.search.Fgs(score.getScore(dataSet, parameters));
        search.setFaithfulnessAssumed(parameters.getBoolean("faithfulnessAssumed"));
        search.setKnowledge(knowledge);
        search.setVerbose(parameters.getBoolean("verbose"));
        search.setMaxIndegree(parameters.getInt("maxIndegree"));

//        if (initial != null) {
//            search.setInitialGraph(initial);
//        }

        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    @Override
    public String getDescription() {
        return "FGS (Fast Greedy Search) using " + score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = score.getParameters();
        parameters.add("faithfulnessAssumed");
        parameters.add("maxIndegree");
        parameters.add("verbose");
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

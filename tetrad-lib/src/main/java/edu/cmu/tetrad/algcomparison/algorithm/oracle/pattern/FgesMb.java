package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesInitialGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.FgesMb2;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Parameters;

import java.util.Collections;
import java.util.List;

/**
 * FGES (the heuristic version).
 *
 * @author jdramsey
 */
public class FgesMb implements Algorithm, TakesInitialGraph, HasKnowledge {
    static final long serialVersionUID = 23L;
    private ScoreWrapper score;
    private Algorithm initialGraph = null;
    private IKnowledge knowledge = new Knowledge2();
    private String targetName;

    public FgesMb(ScoreWrapper score) {
        this.score = score;
    }

    public FgesMb(ScoreWrapper score, Algorithm initialGraph) {
        this.score = score;
        this.initialGraph = initialGraph;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        Graph initial = null;

        if (initialGraph != null) {
            initial = initialGraph.search(dataSet, parameters);
        }

        Score score = this.score.getScore(DataUtils.getContinuousDataSet(dataSet), parameters);
        FgesMb2 search
                = new FgesMb2(score);
        search.setFaithfulnessAssumed(parameters.getBoolean("faithfulnessAssumed"));
        search.setKnowledge(knowledge);

        if (initial != null) {
            search.setInitialGraph(initial);
        }

        this.targetName = parameters.getString("targetName");
        Node target = score.getVariable(targetName);

        return search.search(Collections.singletonList(target));
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        Node target = graph.getNode(targetName);
        return GraphUtils.markovBlanketDag(target, new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "FGES (Fast Greedy Search) using " + score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = score.getParameters();
        parameters.add("targetName");
        parameters.add("faithfulnessAssumed");
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

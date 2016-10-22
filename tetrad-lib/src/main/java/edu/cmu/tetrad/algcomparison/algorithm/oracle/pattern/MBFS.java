package edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;

import java.util.List;

/**
 * PC.
 *
 * @author jdramsey
 */
public class MBFS implements Algorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private IKnowledge knowledge = new Knowledge2();
    private String targetName;

    public MBFS(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        IndependenceTest test = this.test.getTest(dataSet, parameters);
        edu.cmu.tetrad.search.Mbfs search = new edu.cmu.tetrad.search.Mbfs(
                test,
                parameters.getInt("depth")
        );

        search.setKnowledge(knowledge);

        this.targetName = parameters.getString("targetName");
        Node target = test.getVariable(targetName);
        return search.search(target);
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        Node target = graph.getNode(targetName);
        return GraphUtils.markovBlanketDag(target, new EdgeListGraph(graph));
    }

    @Override
    public String getDescription() {
        return "MBFS (Markov Blanket Fan Search) using " + test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.add("depth");
        parameters.add("targetName");
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

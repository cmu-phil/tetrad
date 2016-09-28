package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.search.GFci;
import edu.cmu.tetrad.util.Parameters;
import java.io.PrintStream;
import java.util.List;

/**
 * GFCI.
 *
 * @author jdramsey
 */
public class Gfci implements Algorithm, HasKnowledge {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private ScoreWrapper score;
    private IKnowledge knowledge = new Knowledge2();

    public Gfci(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        GFci search = new GFci(test.getTest(dataSet, parameters), score.getScore(dataSet, parameters));
        search.setMaxIndegree(parameters.getInt("maxIndegree"));
        search.setKnowledge(knowledge);
        search.setVerbose(parameters.getBoolean("verbose"));
        search.setFaithfulnessAssumed(parameters.getBoolean("faithfulnessAssumed"));

        Object obj = parameters.get("printStream");
        if (obj instanceof PrintStream) {
            search.setOut((PrintStream) obj);
        }

        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new DagToPag(graph).convert();
    }

    @Override
    public String getDescription() {
        return "GFCI (Greedy Fast Causal Inference) using " + test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.addAll(score.getParameters());
        parameters.add("faithfulnessAssumed");
        parameters.add("maxIndegree");
        parameters.add("printStream");
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

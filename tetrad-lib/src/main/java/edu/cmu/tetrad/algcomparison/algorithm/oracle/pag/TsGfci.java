package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.TsDagToPag;
import edu.cmu.tetrad.search.GFci;

import java.util.List;

/**
 * tsGFCI.
 *
 * @author jdramsey
 * @author dmalinsky
 */
public class TsGfci implements Algorithm, HasKnowledge {
    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private ScoreWrapper score;
    private IKnowledge knowledge = null;

    public TsGfci(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    public Graph search(DataSet dataSet, Parameters parameters) {
        GFci search = new GFci(test.getTest(dataSet, parameters), score.getScore(dataSet, parameters));
        search.setKnowledge(knowledge);
        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new TsDagToPag(graph).convert();
    }

    public String getDescription() {
        return "tsGFCI (Time Series Greedy Fast Causal Inference) using " + test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = test.getParameters();
        parameters.addAll(score.getParameters());
        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }
}

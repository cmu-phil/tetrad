package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.TsDagToPag;
import edu.cmu.tetrad.search.GFci;

import java.util.List;
import java.util.Map;

/**
 * tsGFCI.
 *
 * @author jdramsey
 * @author dmalinsky
 */
public class TsGfci implements Algorithm, HasKnowledge {
    private ScoreWrapper score;
    private IKnowledge knowledge = null;

    public TsGfci(ScoreWrapper score) {
        this.score = score;
    }

    public Graph search(DataSet dataSet, Parameters parameters) {
        GFci search = new GFci(score.getScore(dataSet, parameters));
        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new TsDagToPag(graph).convert();
    }

    public String getDescription() {
        return "tsGFCI (Time Series Greedy Fast Causal Inference) using " + score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return score.getDataType();
    }

    @Override
    public Map<String, Object> getParameters() {
        return score.getParameters();
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

package edu.cmu.tetrad.algcomparison.algorithms.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithms.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.search.GFci;

import java.util.List;

/**
 * GFCI.
 *
 * @author jdramsey
 */
public class TsGfci implements Algorithm {
    private ScoreWrapper score;

    public TsGfci(ScoreWrapper score) {
        this.score = score;
    }

    public Graph search(DataSet dataSet, Parameters parameters) {
        GFci search = new GFci(score.getScore(dataSet, parameters));
        return search.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new DagToPag(graph).convert();
    }

    public String getDescription() {
        return "tsGFCI (Time Series Greedy Fast Causal Inference) using " + score.getDescription();
    }

    @Override
    public DataType getDataType() {
        return score.getDataType();
    }

    @Override
    public List<String> getParameters() {
        return score.getParameters();
    }
}

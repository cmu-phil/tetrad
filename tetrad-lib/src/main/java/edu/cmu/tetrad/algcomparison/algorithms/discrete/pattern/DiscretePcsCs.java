package edu.cmu.tetrad.algcomparison.algorithms.discrete.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestChiSquare;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * PC-Stable using the Chi Square independence test.
 * @author jdramsey
 */
public class DiscretePcsCs implements Algorithm {

    @Override
    public Graph search(DataSet dataSet, Parameters parameters) {
        IndependenceTest test = new IndTestChiSquare(dataSet, parameters.getDouble("alpha"));
        Pc pc = new Pc(test);
        return pc.search();
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return SearchGraphUtils.patternForDag(graph);
    }

    @Override
    public String getDescription() {
        return "PC using the Ghi Square test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        return parameters;
    }
}

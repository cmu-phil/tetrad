package edu.cmu.tetrad.algcomparison.continuous;

import edu.cmu.tetrad.algcomparison.ComparisonAlgorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestMixedLrt;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class ContinuousPc implements ComparisonAlgorithm {
    public Graph search(DataSet dataSet, Map<String, Number> parameters) {
        IndependenceTest test = new IndTestMixedLrt(dataSet, parameters.get("alpha").doubleValue());
        Pc pc = new Pc(test);
        return pc.search();
    }

    public String getName() {
        return "AJMixedPc";
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }
}

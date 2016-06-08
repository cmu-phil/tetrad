package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestMixedLrt;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.PcStable;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedPcs implements Algorithm {
    public Graph search(DataSet dataSet, Map<String, Number> parameters) {
        IndependenceTest test = new IndTestMixedLrt(dataSet, parameters.get("alpha").doubleValue());
        PcStable pc = new PcStable(test);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "PC-Stable using the Mixed LRT test";
    }
}

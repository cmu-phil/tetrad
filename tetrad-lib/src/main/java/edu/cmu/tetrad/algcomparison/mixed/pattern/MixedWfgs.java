package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.ComparisonAlgorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.WFgs;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedWfgs implements ComparisonAlgorithm {
    public Graph search(DataSet dataSet, Map<String, Number> parameters) {
        WFgs fgs = new WFgs(dataSet);
        fgs.setPenaltyDiscount(parameters.get("penaltyDiscount").doubleValue());
        return fgs.search();
    }

    public String getName() {
        return "WFGS-m";
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }
}

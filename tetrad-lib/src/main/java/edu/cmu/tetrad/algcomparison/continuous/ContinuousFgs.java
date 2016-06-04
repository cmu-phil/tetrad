package edu.cmu.tetrad.algcomparison.continuous;

import edu.cmu.tetrad.algcomparison.ComparisonAlgorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.search.MixedBicScore;
import edu.cmu.tetrad.search.SearchGraphUtils;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
class ContinuousFgs implements ComparisonAlgorithm {
    public Graph search(DataSet dataSet, Map<String, Number> parameters) {
        MixedBicScore score = new MixedBicScore(dataSet);
        score.setPenaltyDiscount(parameters.get("penaltyDiscount").doubleValue());
        Fgs fgs = new Fgs(score);
        return fgs.search();
    }

    public String getName() {
        return "MixedFgs";
    }

    public Graph getComparisonGraph(Graph dag) {
        return new DagToPag(dag).convert();
    }
}

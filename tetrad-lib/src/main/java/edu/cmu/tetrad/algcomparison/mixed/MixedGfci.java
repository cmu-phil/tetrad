package edu.cmu.tetrad.algcomparison.mixed;

import edu.cmu.tetrad.algcomparison.ComparisonAlgorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedGfci implements ComparisonAlgorithm {
    public Graph search(DataSet dataSet, Map<String, Number> parameters) {
        MixedBicScore score = new MixedBicScore(dataSet);
        GFci pc = new GFci(score);
        return pc.search();
    }

    public String getName() {
        return "MixedGfci";
    }

    @Override
    public Graph getComparisonGraph(Graph dag) {
        return new DagToPag(dag).convert();
    }
}

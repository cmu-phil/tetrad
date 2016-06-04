package edu.cmu.tetrad.algcomparison.mixed.pag;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedGfci implements Algorithm {
    public Graph search(DataSet dataSet, Map<String, Number> parameters) {
        MixedBicScore score = new MixedBicScore(dataSet);
        GFci pc = new GFci(score);
        return pc.search();
    }

    public String getName() {
        return "GFCI-m";
    }

    @Override
    public Graph getComparisonGraph(Graph dag) {
        return new DagToPag(dag).convert();
    }
}

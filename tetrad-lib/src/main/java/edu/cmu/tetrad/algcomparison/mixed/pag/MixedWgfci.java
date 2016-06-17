package edu.cmu.tetrad.algcomparison.mixed.pag;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.DagToPag;
import edu.cmu.tetrad.search.DagToPag2;
import edu.cmu.tetrad.search.WGfci;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedWgfci implements Algorithm {
    public Graph search(DataSet dataSet, Map<String, Number> parameters) {
        WGfci fgs = new WGfci(dataSet);
        fgs.setPenaltyDiscount(parameters.get("penaltyDiscount").doubleValue());
        return fgs.search();
    }

    @Override
    public Graph getComparisonGraph(Graph dag) {
        return new DagToPag2(dag).convert();
    }

    public String getDescription() {
        return "WGFCI using the SEM BIC score";
    }
}

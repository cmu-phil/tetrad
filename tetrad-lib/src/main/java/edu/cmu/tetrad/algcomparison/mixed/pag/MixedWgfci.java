package edu.cmu.tetrad.algcomparison.mixed.pag;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.WGfci;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedWgfci implements Algorithm {
    public Graph search(DataSet dataSet, Parameters parameters) {
        WGfci fgs = new WGfci(dataSet);
        fgs.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        return fgs.search();
    }

    @Override
    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "WGFCI using the SEM BIC score (oriented assuming no latents)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }
}

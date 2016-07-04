package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.WFgs;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedWfgs implements Algorithm {
    public Graph search(DataSet dataSet, Parameters parameters) {
        WFgs fgs = new WFgs(dataSet);
        fgs.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        return fgs.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }

    public String getDescription() {
        return "WFGS using the SEM BIC score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }
}

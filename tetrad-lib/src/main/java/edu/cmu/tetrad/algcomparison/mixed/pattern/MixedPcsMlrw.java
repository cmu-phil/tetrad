package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Cpc;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.PcStable;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.pitt.csb.mgm.IndTestMultinomialLogisticRegressionWald;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedPcsMlrw implements Algorithm {
    public Graph search(DataSet dataSet, Parameters parameters) {
        IndependenceTest test = new IndTestMultinomialLogisticRegressionWald(
                dataSet, parameters.getDouble("alpha"), false);
        PcStable pc = new PcStable(test);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }


    public String getDescription() {
        return "PCS using the Multinomial Logistic Regression Wald Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }
}

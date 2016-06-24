package edu.cmu.tetrad.algcomparison.mixed.pag;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fci;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.PcStable;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.pitt.csb.mgm.IndTestMultinomialLogisticRegressionWald;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedFciMlrw implements Algorithm {
    public Graph search(DataSet dataSet, Map<String, Number> parameters) {
        IndependenceTest test = new IndTestMultinomialLogisticRegressionWald(
                dataSet, parameters.get("alpha").doubleValue(), false);
        Fci pc = new Fci(test);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }


    public String getDescription() {
        return "FCI using the Multinomial Logistic Regression Wald Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }
}

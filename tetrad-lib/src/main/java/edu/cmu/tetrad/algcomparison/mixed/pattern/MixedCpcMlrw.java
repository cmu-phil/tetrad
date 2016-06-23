package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.Algorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;
import edu.pitt.csb.mgm.IndTestMultinomialLogisticRegressionWald;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedCpcMlrw implements Algorithm {
    public Graph search(DataSet dataSet, Map<String, Number> parameters) {
        IndependenceTest test = new IndTestMultinomialLogisticRegressionWald(
                dataSet, parameters.get("alpha").doubleValue(), false);
        Cpc pc = new Cpc(test);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }


    public String getDescription() {
        return "CPC using the Multinomial Logistic Regresion Wald Test";
    }
}

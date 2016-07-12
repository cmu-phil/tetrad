package edu.cmu.tetrad.algcomparison.mixed.pattern;

import edu.cmu.tetrad.algcomparison.interfaces.Algorithm;
import edu.cmu.tetrad.algcomparison.interfaces.DataType;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.pitt.csb.mgm.IndTestMultinomialLogisticRegressionWald;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jdramsey on 6/4/16.
 */
public class MixedPcMlrw implements Algorithm {
    public Graph search(DataSet dataSet, Parameters parameters) {
        IndependenceTest test = new IndTestMultinomialLogisticRegressionWald(
                dataSet, parameters.getDouble("alpha"), false);
        Pc pc = new Pc(test);
        return pc.search();
    }

    public Graph getComparisonGraph(Graph dag) {
        return SearchGraphUtils.patternForDag(dag);
    }


    public String getDescription() {
        return "CPC using the Multinomial Logistic Regression Wald Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> usesParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        return parameters;
    }
}

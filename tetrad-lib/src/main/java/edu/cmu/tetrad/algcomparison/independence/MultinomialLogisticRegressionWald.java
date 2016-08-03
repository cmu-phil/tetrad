package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.pitt.csb.mgm.IndTestMultinomialLogisticRegressionWald;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class MultinomialLogisticRegressionWald implements IndependenceWrapper {

    @Override
    public edu.cmu.tetrad.search.IndependenceTest getTest(DataSet dataSet, Parameters parameters) {
        return new IndTestMultinomialLogisticRegressionWald(
                dataSet,
                parameters.getDouble("alpha", .001),
                false);
    }

    @Override
    public String getDescription() {
        return "MultinomialLogisticRetressionWald test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("alpha", 0.001);
        return params;
    }
}

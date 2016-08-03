package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.IndependenceTest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class FisherZ implements IndependenceWrapper {

    @Override
    public IndependenceTest getTest(DataSet dataSet, Parameters parameters) {
        return new IndTestFisherZ(dataSet, parameters.getDouble("alpha", 0.001));
    }

    @Override
    public String getDescription() {
        return "Fisher Z test";
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

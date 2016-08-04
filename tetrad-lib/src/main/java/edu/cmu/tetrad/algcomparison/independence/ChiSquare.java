package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.IndTestChiSquare;
import edu.cmu.tetrad.search.IndependenceTest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class ChiSquare implements IndependenceWrapper {

    @Override
    public IndependenceTest getTest(DataSet dataSet, Parameters parameters) {
        return new IndTestChiSquare(dataSet, parameters.getDouble("alpha"));
    }

    @Override
    public String getDescription() {
        return "Chi Square test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add("alpha");
        return params;
    }

}

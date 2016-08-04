package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.IndTestConditionalGaussianLrt;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Experimental;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class ConditionalGaussianLRT implements IndependenceWrapper, Experimental {

    @Override
    public IndependenceTest getTest(DataSet dataSet, Parameters parameters) {
        return new IndTestConditionalGaussianLrt(dataSet, parameters.getDouble("alpha"));
    }

    @Override
    public String getDescription() {
        return "Conditional Gaussian Likelihood Ratio Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add("alpha");
        return params;
    }

}

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.IndTestCorrelationT;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class CorrelationT implements IndependenceWrapper {
    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataSet dataSet, Parameters parameters) {
        double alpha = parameters.getDouble("alpha");
        return new IndTestCorrelationT(dataSet, alpha);
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
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add("alpha");
        return params;
    }
}

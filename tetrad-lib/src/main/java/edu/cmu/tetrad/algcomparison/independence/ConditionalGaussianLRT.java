package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.IndTestConditionalGaussianLrt;
import edu.cmu.tetrad.search.IndependenceTest;

import java.util.Collections;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 * @author jdramsey
 */
public class ConditionalGaussianLRT implements IndTestWrapper {
    private DataSet dataSet = null;
    private IndependenceTest test = null;

    @Override
    public IndependenceTest getTest(DataSet dataSet, Parameters parameters) {
        if (dataSet != this.dataSet) {
            this.dataSet = dataSet;
            this.test = new IndTestConditionalGaussianLrt(dataSet, parameters.getDouble("alpha"));
        }
        return test;
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
        return Collections.singletonList("alpha");
    }

}

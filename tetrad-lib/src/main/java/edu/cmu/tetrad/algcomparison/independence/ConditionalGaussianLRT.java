package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.IndTestConditionalGaussianLRT;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Experimental;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class ConditionalGaussianLRT implements IndependenceWrapper, Experimental {
    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        final IndTestConditionalGaussianLRT test
                = new IndTestConditionalGaussianLRT(DataUtils.getMixedDataSet(dataSet),
                parameters.getDouble("alpha"));
        test.setNumCategoriesToDiscretize(parameters.getInt("numCategoriesToDiscretize"));
//        test.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
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
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        parameters.add("assumeMixed");
        parameters.add("penaltyDiscount");
        return parameters;
    }

}

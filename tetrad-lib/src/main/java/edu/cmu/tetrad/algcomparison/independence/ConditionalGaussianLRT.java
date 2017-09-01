package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.IndTestDescription;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.IndTestConditionalGaussianLRT;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Experimental;
import edu.cmu.tetrad.util.Parameters;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@IndTestDescription(name = "conditional-gaussian-lrt", description = "Conditional Gaussian Likelihood Ratio Test", dataType = DataType.Mixed)
public class ConditionalGaussianLRT implements IndependenceWrapper, Experimental {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        final IndTestConditionalGaussianLRT test
                = new IndTestConditionalGaussianLRT(DataUtils.getMixedDataSet(dataSet),
                        parameters.getDouble("alpha"),
                        parameters.getBoolean("discretize"));
        test.setNumCategoriesToDiscretize(parameters.getInt("numCategoriesToDiscretize"));
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
        parameters.add("discretize");
        return parameters;
    }

}

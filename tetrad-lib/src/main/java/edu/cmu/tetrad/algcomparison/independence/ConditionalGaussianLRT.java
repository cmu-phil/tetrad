package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.IndTestConditionalGaussianLRT;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@TestOfIndependence(
        name = "Conditional Gaussian (CG) Likelihood Ratio Test",
        command = "cg-lr-test",
        dataType = DataType.Mixed
)
public class ConditionalGaussianLRT implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        final IndTestConditionalGaussianLRT test
                = new IndTestConditionalGaussianLRT(DataUtils.getMixedDataSet(dataSet),
                        parameters.getDouble(Params.ALPHA),
                        parameters.getBoolean(Params.DISCRETIZE));
        test.setNumCategoriesToDiscretize(parameters.getInt(Params.NUM_CATEGORIES_TO_DISCRETIZE));
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
        parameters.add(Params.ALPHA);
        parameters.add(Params.DISCRETIZE);
        return parameters;
    }

}

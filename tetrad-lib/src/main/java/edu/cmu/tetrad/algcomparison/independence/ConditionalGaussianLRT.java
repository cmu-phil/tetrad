package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.test.IndTestConditionalGaussianLrt;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 */
@TestOfIndependence(
        name = "CG-LRT (Conditional Gaussian Likelihood Ratio Test)",
        command = "cg-lr-test",
        dataType = DataType.Mixed
)
@Mixed
public class ConditionalGaussianLRT implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        IndTestConditionalGaussianLrt test
                = new IndTestConditionalGaussianLrt(SimpleDataLoader.getMixedDataSet(dataSet),
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
        parameters.add(Params.NUM_CATEGORIES_TO_DISCRETIZE);
        return parameters;
    }

}

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.IndTestConditionalGaussianLRT;
import edu.cmu.tetrad.search.IndTestDegenerateGaussianLRT;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for DG LRT.
 *
 * @author bandrews
 */
@TestOfIndependence(
        name = "Degenerate Gaussian (DG) Likelihood Ratio Test",
        command = "dg-lr-test",
        dataType = DataType.Mixed
)
public class DegenerateGaussianLRT implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        final IndTestDegenerateGaussianLRT test = new IndTestDegenerateGaussianLRT(DataUtils.getMixedDataSet(dataSet));
        test.setAlpha(parameters.getInt(Params.ALPHA));
        return test;
    }

    @Override
    public String getDescription() {
        return "Degenerate Gaussian Likelihood Ratio Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        return parameters;
    }

}

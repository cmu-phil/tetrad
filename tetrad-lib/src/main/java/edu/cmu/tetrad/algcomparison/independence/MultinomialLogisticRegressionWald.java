package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.csb.mgm.IndTestMultinomialLogisticRegressionWald;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@TestOfIndependence(
        name = "Multinomial Logistic Retression Wald Test",
        command = "multinomial-logistic-regression-wald",
        dataType = DataType.Mixed
)
public class MultinomialLogisticRegressionWald implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public edu.cmu.tetrad.search.IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        return new IndTestMultinomialLogisticRegressionWald(
                DataUtils.getMixedDataSet(dataSet),
                parameters.getDouble("alpha"),
                false);
    }

    @Override
    public String getDescription() {
        return "Multinomial Logistic Retression Wald Test";
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

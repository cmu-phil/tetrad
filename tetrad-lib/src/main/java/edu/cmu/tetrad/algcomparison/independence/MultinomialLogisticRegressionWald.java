package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.csb.mgm.IndTestMultinomialLogisticRegressionWald;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
//@TestOfIndependence(
//        name = "Multinomial Logistic Retression Wald Test",
//        command = "multinomial-logistic-regression-wald",
//        dataType = DataType.Mixed
//)
public class MultinomialLogisticRegressionWald implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        return new IndTestMultinomialLogisticRegressionWald(
                SimpleDataLoader.getMixedDataSet(dataSet),
                parameters.getDouble("alpha"),
                false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Multinomial Logistic Retression Wald Test";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add("alpha");
        return params;
    }
}

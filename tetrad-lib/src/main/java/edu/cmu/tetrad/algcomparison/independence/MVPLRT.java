package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.IndTestMVPLRT;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@Experimental
@TestOfIndependence(
        name = "Multinomial Logistic Regression Likelihood Ratio Test",
        command = "multinomial-logistic-regression-likelihood-ratio",
        dataType = DataType.Mixed
)
public class MVPLRT implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        final IndTestMVPLRT test = new IndTestMVPLRT(DataUtils.getMixedDataSet(dataSet), parameters.getDouble("alpha"), parameters.getInt("fDegree"), parameters.getInt("discretize") > 0);
        return test;
    }

    @Override
    public String getDescription() {
        return "Multinomial Logistic Regression Likelihood Ratio Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        parameters.add("fDegree");
        parameters.add("discretize");
        return parameters;
    }

}

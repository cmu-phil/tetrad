package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.IndTestMNLRLRT;
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
        name = "Mixed Variable Polynomial Likelihood Ratio Test",
        command = "mixed-var-polynominal-likelihood-ratio",
        dataType = DataType.Mixed
)
public class MNLRLRT implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        final IndTestMNLRLRT test = new IndTestMNLRLRT(DataUtils.getMixedDataSet(dataSet), parameters.getDouble("alpha"));
        return test;
    }

    @Override
    public String getDescription() {
        return "Mixed Variable Polynomial Likelihood Ratio Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        return parameters;
    }

}

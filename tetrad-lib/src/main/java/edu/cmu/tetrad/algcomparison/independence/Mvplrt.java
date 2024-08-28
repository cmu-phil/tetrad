package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndTestMvpLrt;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@Experimental
@TestOfIndependence(
        name = "Mixed Variable Polynomial Likelihood Ratio Test",
        command = "mvplr-test",
        dataType = DataType.Mixed
)
public class Mvplrt implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the test.
     */
    public Mvplrt() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        return new IndTestMvpLrt(SimpleDataLoader.getMixedDataSet(dataSet), parameters.getDouble("alpha"), parameters.getInt("fDegree"), parameters.getBoolean("discretize"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Multinomial Logistic Regression Likelihood Ratio Test";
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
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        parameters.add("fDegree");
        parameters.add("discretize");
        return parameters;
    }

}

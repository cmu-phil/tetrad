package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.work_in_progress.IndTestMnlrLr;
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

// Taking this out of the interface since it's not used in the codebase at
// request of the author.
@Experimental
//@TestOfIndependence(
//        name = "Multinomial Logistic Regression Likelihood Ratio Test",
//        command = "mnlrlr-test",
//        dataType = DataType.Mixed
//)
public class Mnlrlrt implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the test.
     */
    public Mnlrlrt() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        return new IndTestMnlrLr(SimpleDataLoader.getMixedDataSet(dataSet), parameters.getDouble("alpha"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Mixed Variable Polynomial Likelihood Ratio Test";
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
        return parameters;
    }

}

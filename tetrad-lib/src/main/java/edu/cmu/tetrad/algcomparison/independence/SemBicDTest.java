package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.ScoreIndTest;
import edu.cmu.tetrad.search.work_in_progress.SemBicScoreDeterministic;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * The SemBicDTest class implements the IndependenceWrapper interface and represents a test for independence based on
 * SEM BIC algorithm. It is annotated with the TestOfIndependence and LinearGaussian annotations.
 */
public class SemBicDTest implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the SEM BIC test.
     */
    public SemBicDTest() {

    }

    /**
     * Retrieves an IndependenceTest object for testing independence against a given data set and parameters.
     *
     * @param dataSet    The data set to test independence against.
     * @param parameters The parameters of the test.
     * @return An IndependenceTest object for testing independence.
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        SemBicScoreDeterministic score = new SemBicScoreDeterministic(new CovarianceMatrix((DataSet) dataSet));
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        return new ScoreIndTest(score, dataSet);
    }

    /**
     * Returns a short description of this IndependenceTest.
     *
     * @return The description.
     */
    @Override
    public String getDescription() {
        return "SEM BIC test";
    }

    /**
     * Retrieves the data type of the test dataset.
     *
     * @return The data type of the test dataset.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Returns the list of parameters used in this method.
     *
     * @return The list of parameters.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add("penaltyDiscount");
        return params;
    }
}

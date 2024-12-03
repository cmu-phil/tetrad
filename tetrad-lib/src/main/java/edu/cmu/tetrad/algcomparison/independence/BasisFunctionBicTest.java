package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.ScoreIndTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/// **
// * The BasisFunctionText class implements the IndependenceWrapper interface and represents a test for independence based
// * on Basis Function BIC algorithm.
// */
//@TestOfIndependence(
//        name = "Basis Function BIC Test",
//        command = "bf-bic-test",
//        dataType = {DataType.Continuous, DataType.Covariance}
//)
//@Mixed
public class BasisFunctionBicTest implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the Basis Function BIC test.
     */
    public BasisFunctionBicTest() {
    }

    /**
     * Returns an instance of IndependenceTest for the Basis Function BIC test.
     *
     * @param dataSet    The data set to test independence against.
     * @param parameters The parameters of the test.
     * @return An instance of IndependenceTest for the Basis Function BIC test.
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        boolean precomputeCovariances = parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES);
        edu.cmu.tetrad.search.score.BasisFunctionBicScore score
                = new edu.cmu.tetrad.search.score.BasisFunctionBicScore(SimpleDataLoader.getMixedDataSet(dataSet),
                precomputeCovariances, parameters.getInt(Params.TRUNCATION_LIMIT),
                parameters.getInt(Params.BASIS_TYPE), parameters.getDouble(Params.BASIS_SCALE));
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
        return new ScoreIndTest(score, dataSet);
    }

    /**
     * Returns a short description of the test.
     *
     * @return A short description of the test.
     */
    @Override
    public String getDescription() {
        return "Basis Function BIC Test";
    }

    /**
     * Returns the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return The data type required for the search.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the parameters required for the Basis Function BIC test.
     *
     * @return A list of strings representing the parameters required for the Basis Function BIC test.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.PENALTY_DISCOUNT);
        params.add(Params.TRUNCATION_LIMIT);
        params.add(Params.STRUCTURE_PRIOR);
        params.add(Params.PRECOMPUTE_COVARIANCES);
        return params;
    }
}

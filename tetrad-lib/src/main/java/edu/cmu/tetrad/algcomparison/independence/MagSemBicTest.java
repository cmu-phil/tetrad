package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.ScoreIndTest;
import edu.cmu.tetrad.search.work_in_progress.MagSemBicScore;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "MAG SEM BIC Test",
        command = "mag-sem-bic-test",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class MagSemBicTest implements IndependenceWrapper {

    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the test.
     */
    public MagSemBicTest() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        MagSemBicScore score;
        boolean precomputeCovariances = parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES);

        if (dataSet instanceof ICovarianceMatrix) {
            score = new MagSemBicScore((ICovarianceMatrix) dataSet);
        } else {
            score = new MagSemBicScore((DataSet) dataSet, precomputeCovariances);
        }
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

        return new ScoreIndTest(score, dataSet);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "SEM BIC Test";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.PENALTY_DISCOUNT);
        params.add(Params.STRUCTURE_PRIOR);
        params.add(Params.PRECOMPUTE_COVARIANCES);
        return params;
    }
}

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.ScoreIndTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 */
@TestOfIndependence(
        name = "SEM BIC Test",
        command = "sem-bic-test",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class SemBicTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        boolean precomputeCovariances = parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES);

        SemBicScore score;

        if (dataSet instanceof ICovarianceMatrix) {
            score = new SemBicScore((ICovarianceMatrix) dataSet);
        } else {
            score = new SemBicScore((DataSet) dataSet, precomputeCovariances);
        }
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
        score.setStructurePrior(parameters.getDouble(Params.STRUCTURE_PRIOR));

        return new ScoreIndTest(score, dataSet);
    }

    @Override
    public String getDescription() {
        return "SEM BIC Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.PENALTY_DISCOUNT);
        params.add(Params.STRUCTURE_PRIOR);
        params.add(Params.PRECOMPUTE_COVARIANCES);
        return params;
    }
}

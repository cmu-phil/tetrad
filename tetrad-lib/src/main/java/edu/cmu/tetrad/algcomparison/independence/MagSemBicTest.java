package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.test.ScoreIndTest;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.score.MagSemBicScore;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@TestOfIndependence(
        name = "MAG SEM BIC Test",
        command = "mag-sem-bic-test",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class MagSemBicTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        MagSemBicScore score;

        if (dataSet instanceof ICovarianceMatrix) {
            score = new MagSemBicScore((ICovarianceMatrix) dataSet);
        } else {
            score = new MagSemBicScore((DataSet) dataSet);
        }
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

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
        return params;
    }
}

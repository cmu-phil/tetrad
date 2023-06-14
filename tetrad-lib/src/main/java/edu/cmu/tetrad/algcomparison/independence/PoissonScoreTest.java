package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.score.PoissonPriorScore;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndependenceTest;
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
        name = "Poisson Prior Test",
        command = "poisson-prior-test",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class PoissonScoreTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        PoissonPriorScore score;

        if (dataSet instanceof ICovarianceMatrix) {
            score = new PoissonPriorScore((ICovarianceMatrix) dataSet);
        } else {
            score = new PoissonPriorScore((DataSet) dataSet, true);
        }
        score.setLambda(parameters.getDouble(Params.POISSON_LAMBDA));

        return new ScoreIndTest(score, dataSet);
    }

    @Override
    public String getDescription() {
        return "Poisson Prior Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.POISSON_LAMBDA);
        return params;
    }
}

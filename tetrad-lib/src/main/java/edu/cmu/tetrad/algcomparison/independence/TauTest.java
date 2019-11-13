package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.search.TauScore;
import edu.cmu.tetrad.search.IndTestScore;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@TestOfIndependence(
        name = "Tau Test",
        command = "tautest",
        dataType = {DataType.Continuous, DataType.Covariance}
)
public class TauTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        TauScore score = null;

        if (dataSet instanceof ICovarianceMatrix) {
            score = new TauScore((ICovarianceMatrix) dataSet);
        } else {
            score = new TauScore(new CovarianceMatrix((DataSet) dataSet));
        }
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        score.setStructurePrior(parameters.getDouble("structurePrior"));

        return new IndTestScore(score, dataSet);
    }

    @Override
    public String getDescription() {
        return "Tau Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add("penaltyDiscount");
        params.add("structurePrior");
        return params;
    }
}

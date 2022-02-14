package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.BicScore;
import edu.cmu.tetrad.search.IndTestScore;
import edu.cmu.tetrad.search.IndependenceTest;
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
        name = "Discrete BIC Test",
        command = "disc-bic-test",
        dataType = DataType.Discrete
)
@Experimental
public class DiscreteBicTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        BicScore score = new BicScore(DataUtils.getDiscreteDataSet(dataSet));
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
        score.setStructurePrior(parameters.getDouble(Params.STRUCTURE_PRIOR));
        return new IndTestScore(score);
    }

    @Override
    public String getDescription() {
        return "Discrete BIC Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.STRUCTURE_PRIOR);
        return parameters;
    }
}

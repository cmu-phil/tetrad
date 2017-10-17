package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@TestOfIndependence(
        name = "Discrete BIC Test",
        command = "disc-bic",
        dataType = DataType.Discrete
)
public class DiscreteBicTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        Score score = new BicScore(DataUtils.getDiscreteDataSet(dataSet));
//        score.setSamplePrior(parameters.getDouble("samplePrior"));
//        score.setStructurePrior(parameters.getDouble("structurePrior"));
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
//        parameters.add("samplePrior");
//        parameters.add("structurePrior");
        return parameters;
    }
}

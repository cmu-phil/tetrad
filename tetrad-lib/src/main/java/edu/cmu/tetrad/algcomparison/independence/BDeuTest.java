package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.*;
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
        name = "BDeu Test",
        command = "bdeu-test",
        dataType = DataType.Discrete
)
public class BDeuTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        BDeuScore score = new BDeuScore(DataUtils.getDiscreteDataSet(dataSet));
        score.setSamplePrior(parameters.getDouble(Params.SAMPLE_PRIOR));
        score.setStructurePrior(parameters.getDouble(Params.STRUCTURE_PRIOR));
        return new IndTestScore(score);
    }

    @Override
    public String getDescription() {
        return "BDeu Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.SAMPLE_PRIOR);
        parameters.add(Params.STRUCTURE_PRIOR);
        return parameters;
    }
}

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndTestChiSquare;
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
        name = "Chi Square Test",
        command = "chi-square-test",
        dataType = DataType.Discrete
)
public class ChiSquare implements IndependenceWrapper {

    private static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        IndTestChiSquare test = new IndTestChiSquare(SimpleDataLoader.getDiscreteDataSet(dataSet), parameters.getDouble("test"));
        test.setMinCountPerTable(parameters.getInt(Params.MIN_COUNT_PER_TABLE));
        return test;
    }

    @Override
    public String getDescription() {
        return "Chi Square Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.MIN_COUNT_PER_TABLE);
        return params;
    }

}

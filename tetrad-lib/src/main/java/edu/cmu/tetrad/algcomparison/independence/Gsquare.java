package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndTestGSquare;
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
        name = "G Square Test",
        command = "g-square-test",
        dataType = DataType.Discrete
)
public class Gsquare implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        return new IndTestGSquare(SimpleDataLoader.getDiscreteDataSet(dataSet), parameters.getDouble("alpha"));
    }

    @Override
    public String getDescription() {
        return "G Square Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        return params;
    }
}

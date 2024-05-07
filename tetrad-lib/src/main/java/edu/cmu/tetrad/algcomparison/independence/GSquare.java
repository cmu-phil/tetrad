package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndTestGSquare;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "G Square Test",
        command = "g-square-test",
        dataType = DataType.Discrete
)
public class GSquare implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * GSquare class represents a wrapper for the G Square test, which is a statistical test for independence between
     * two variables conditional on a third variable.
     * <p>
     * This class implements the IndependenceWrapper interface, which requires the implementation of several methods.
     */
    public GSquare() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        IndTestGSquare test = new IndTestGSquare(SimpleDataLoader.getDiscreteDataSet(dataSet), parameters.getDouble("test"));
        test.setMinCountPerCell(parameters.getDouble(Params.MIN_COUNT_PER_CELL));
        return test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "G Square Test";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.MIN_COUNT_PER_CELL);
        return params;
    }
}

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.ChiSquareTest;
import edu.cmu.tetrad.search.test.IndTestChiSquare;
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
        name = "Chi Square Test",
        command = "chi-square-test",
        dataType = DataType.Discrete
)
public class ChiSquare implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Initializes a new instance of the ChiSquare class.
     */
    public ChiSquare() {
    }

    /**
     * Retrieves an instance of the IndependenceTest interface that performs a Chi Square Test for independence.
     *
     * @param dataSet    The data set to test independence against.
     * @param parameters The parameters of the test.
     * @return An instance of the IndependenceTest interface that performs a Chi Square Test for independence.
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        IndTestChiSquare test = new IndTestChiSquare(SimpleDataLoader.getDiscreteDataSet(dataSet), parameters.getDouble(Params.ALPHA));
        test.setMinCountPerCell(parameters.getDouble(Params.MIN_COUNT_PER_CELL));

        if (parameters.getInt(Params.CELL_TABLE_TYPE) == 1) {
            test.setCellTableType(ChiSquareTest.CellTableType.AD_TREE);
        } else {
            test.setCellTableType(ChiSquareTest.CellTableType.COUNT_SAMPLE);
        }

        return test;
    }

    /**
     * Returns a short description of the Chi Square Test.
     *
     * @return A String representing the short description of the Chi Square Test.
     */
    @Override
    public String getDescription() {
        return "Chi Square Test";
    }

    /**
     * Returns the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return The data type required by the search.
     */
    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    /**
     * Retrieves the parameters required by the Chi Square Test.
     *
     * @return A list of strings representing the parameters required by the Chi Square Test.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.MIN_COUNT_PER_CELL);
        params.add(Params.CELL_TABLE_TYPE);
        return params;
    }
}

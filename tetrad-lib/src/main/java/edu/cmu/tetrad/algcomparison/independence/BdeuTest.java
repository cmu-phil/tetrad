package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.BdeuScore;
import edu.cmu.tetrad.search.test.ScoreIndTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "BDeu Test",
        command = "bdeu-test",
        dataType = DataType.Discrete
)
@Experimental
public class BdeuTest implements IndependenceWrapper {

    private static final long serialVersionUID = 23L;

    /**
     * {@inheritDoc}
     *
     * Returns the test.
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        BdeuScore score = new BdeuScore(SimpleDataLoader.getDiscreteDataSet(dataSet));
        score.setSamplePrior(parameters.getDouble(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE));
        score.setStructurePrior(parameters.getDouble(Params.STRUCTURE_PRIOR));
        return new ScoreIndTest(score);
    }

    /**
     * {@inheritDoc}
     *
     * Returns the description of the test.
     */
    @Override
    public String getDescription() {
        return "BDeu Test";
    }

    /**
     * {@inheritDoc}
     *
     * Returns the data type of the test.
     */
    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    /**
     * {@inheritDoc}
     *
     * Returns the parameters of the test.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE);
        parameters.add(Params.STRUCTURE_PRIOR);
        return parameters;
    }
}

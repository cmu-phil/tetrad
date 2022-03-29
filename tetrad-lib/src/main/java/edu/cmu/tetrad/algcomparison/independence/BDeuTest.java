package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.BDeuScore;
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
        name = "BDeu Test",
        command = "bdeu-test",
        dataType = DataType.Discrete
)
@Experimental
public class BDeuTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(final DataModel dataSet, final Parameters parameters) {
        final BDeuScore score = new BDeuScore(DataUtils.getDiscreteDataSet(dataSet));
        score.setSamplePrior(parameters.getDouble(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE));
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
        final List<String> parameters = new ArrayList<>();
        parameters.add(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE);
        parameters.add(Params.STRUCTURE_PRIOR);
        return parameters;
    }
}

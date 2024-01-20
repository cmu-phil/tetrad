package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.work_in_progress.IndTestFisherZPseudoinverse;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 */
@TestOfIndependence(
        name = "Fisher Z Test Pseudoinverse",
        command = "fisher-z-test-pseudoinverse",
        dataType = {DataType.Continuous}
)
@LinearGaussian
public class FisherZPseudoinverse implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        double alpha = parameters.getDouble(Params.ALPHA);

        if (!(dataSet instanceof DataSet) || !dataSet.isContinuous()) {
            throw new IllegalArgumentException("Expecting a tabular continous data set.");
        }

        return new IndTestFisherZPseudoinverse((DataSet) dataSet, alpha);
    }

    @Override
    public String getDescription() {
        return "Fisher Z test Pseudoinverse";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        return params;
    }
}

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Pseudo Fisher Z test.
 *
 * @author bryan andrews
 */
@TestOfIndependence(
        name = "Pseudo Fisher Z Test",
        command = "pseudo-fisher-z-test",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class PseudoTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        double alpha = 0.01;

        if (dataSet instanceof ICovarianceMatrix) {
            return new IndTestFisherZ((ICovarianceMatrix) dataSet, alpha);
        } else if (dataSet instanceof DataSet) {
            return new IndTestFisherZ((DataSet) dataSet, alpha);
        }

        throw new IllegalArgumentException("Expecting either a data set or a covariance matrix.");
    }

    @Override
    public String getDescription() {
        return "Pseudo Fisher Z test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        return params;
    }
}

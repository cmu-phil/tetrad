package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
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
        name = "Fisher Z Test",
        command = "fisher-z-test",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class FisherZ implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {
        double alpha = parameters.getDouble(Params.ALPHA);

        IndTestFisherZ test;

        if (dataModel instanceof ICovarianceMatrix) {
            test = new IndTestFisherZ((ICovarianceMatrix) dataModel, alpha);
        } else if (dataModel instanceof DataSet) {
            test = new IndTestFisherZ((DataSet) dataModel, alpha);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        test.setUsePseudoinverse(parameters.getBoolean(Params.USE_PSEUDOINVERSE));
        return test;
    }

    @Override
    public String getDescription() {
        return "Fisher Z test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.USE_PSEUDOINVERSE);
        return params;
    }
}

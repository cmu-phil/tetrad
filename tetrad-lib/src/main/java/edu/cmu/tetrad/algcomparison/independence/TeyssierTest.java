package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.IndTestTeyssier;
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
        name = "Teyssier Test",
        command = "teyssier-test",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class TeyssierTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        double alpha = parameters.getDouble(Params.ALPHA);

        if (dataSet instanceof ICovarianceMatrix) {
            return new IndTestTeyssier((ICovarianceMatrix) dataSet, parameters.getDouble(Params.PENALTY_DISCOUNT));
        } else if (dataSet instanceof DataSet) {
            return new IndTestTeyssier((DataSet) dataSet, parameters.getDouble(Params.PENALTY_DISCOUNT));
        }

        throw new IllegalArgumentException("Expecting eithet a data set or a covariance matrix.");
    }

    @Override
    public String getDescription() {
        return "Teyssier test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
//        params.add(Params.PENALTY_DISCOUNT);
        return params;
    }
}

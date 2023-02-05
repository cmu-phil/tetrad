package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.IndTestCodec;
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
//@TestOfIndependence(
//        name = "CODEC Test",
//        command = "codec-test",
//        dataType = {DataType.Continuous, DataType.Covariance}
//)
@LinearGaussian
public class Codec implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        double alpha = parameters.getDouble(Params.ALPHA);

        if (dataSet instanceof DataSet) {
            return new IndTestCodec((DataSet) dataSet, alpha);
        }

        throw new IllegalArgumentException("Expecting a data set.");
    }

    @Override
    public String getDescription() {
        return "CODEC";
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

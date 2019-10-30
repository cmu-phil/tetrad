package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Gaussian;
import edu.cmu.tetrad.annotation.Linear;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.search.IndTestFisherZ;
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
        name = "Fisher Z Test",
        command = "fisher-z-test",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@Gaussian
@Linear
public class FisherZ implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        double alpha = parameters.getDouble(Params.ALPHA);

        if (dataSet instanceof ICovarianceMatrix) {
            IndTestFisherZ indTestFisherZ = new IndTestFisherZ((ICovarianceMatrix) dataSet, alpha);
            indTestFisherZ.setSellke(parameters.getBoolean("useSellkeAdjustment"));
            return indTestFisherZ;
        } else if (dataSet instanceof DataSet) {
            IndTestFisherZ indTestFisherZ = new IndTestFisherZ((DataSet) dataSet, alpha);
            indTestFisherZ.setSellke(parameters.getBoolean("useSellkeAdjustment"));
            return indTestFisherZ;
        }

        throw new IllegalArgumentException("Expecting eithet a data set or a covariance matrix.");
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
        params.add(Params.USE_SELLKE_ADJUSTMENT);
        return params;
    }
}

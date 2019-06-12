package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
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
        name = "Kernel Conditional Independence (KCI) Test",
        command = "kci-test",
        dataType = DataType.Continuous
)
public class Kci implements IndependenceWrapper {

    static final long serialVersionUID = 23L;


    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        final edu.cmu.tetrad.search.Kci kci = new edu.cmu.tetrad.search.Kci(DataUtils.getContinuousDataSet(dataSet),
                parameters.getDouble(Params.ALPHA));
        kci.setApproximate(parameters.getBoolean(Params.KCI_USE_APPROMATION));
        kci.setWidthMultiplier(parameters.getDouble(Params.KERNEL_MULTIPLIER));
        kci.setNumBootstraps(parameters.getInt(Params.KCI_NUM_BOOTSTRAPS));
        kci.setThreshold(parameters.getDouble(Params.THRESHOLD_FOR_NUM_EIGENVALUES));
        kci.setEpsilon(parameters.getDouble(Params.KCI_EPSILON));
        return kci;
    }

    @Override
    public String getDescription() {
        return "KCI";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.KCI_USE_APPROMATION);
        params.add(Params.ALPHA);
        params.add(Params.KERNEL_MULTIPLIER);
        params.add(Params.KCI_NUM_BOOTSTRAPS);
        params.add(Params.THRESHOLD_FOR_NUM_EIGENVALUES);
        params.add(Params.KCI_EPSILON);
        return params;
    }
}
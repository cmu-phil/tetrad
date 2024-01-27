package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for KCI test.
 *
 * @author josephramsey
 */
@TestOfIndependence(
        name = "KCI-Test (Kernel Conditional Independence Test)",
        command = "kci-test",
        dataType = DataType.Continuous
)
@General
public class Kci implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Returns a KCI test.
     *
     * @param dataSet    The data set to test independence against.
     * @param parameters The paramters of the test.
     * @return A KCI test.
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.test.Kci kci = new edu.cmu.tetrad.search.test.Kci(SimpleDataLoader.getContinuousDataSet(dataSet),
                parameters.getDouble(Params.ALPHA));
        kci.setApproximate(parameters.getBoolean(Params.KCI_USE_APPROXIMATION));
        kci.setWidthMultiplier(parameters.getDouble(Params.KERNEL_MULTIPLIER));
        kci.setNumBootstraps(parameters.getInt(Params.KCI_NUM_BOOTSTRAPS));
        kci.setThreshold(parameters.getDouble(Params.THRESHOLD_FOR_NUM_EIGENVALUES));
        kci.setEpsilon(parameters.getDouble(Params.KCI_EPSILON));
        return kci;
    }

    /**
     * Returns the name of the test.
     *
     * @return The name of the test.
     */
    @Override
    public String getDescription() {
        return "KCI";
    }

    /**
     * Returns the data type of the test, which is continuous.
     *
     * @return The data type of the test, which is continuous.
     * @see DataType
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Returns the parameters of the test.
     *
     * @return The parameters of the test.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.KCI_USE_APPROXIMATION);
        params.add(Params.ALPHA);
        params.add(Params.KERNEL_MULTIPLIER);
        params.add(Params.KCI_NUM_BOOTSTRAPS);
        params.add(Params.THRESHOLD_FOR_NUM_EIGENVALUES);
        params.add(Params.KCI_EPSILON);
        return params;
    }
}
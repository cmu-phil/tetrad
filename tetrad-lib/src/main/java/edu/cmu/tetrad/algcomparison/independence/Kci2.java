package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
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
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "KCI-Test 2 (Kernel Conditional Independence Test)",
        command = "kci-test-2",
        dataType = DataType.Continuous
)
@General
public class Kci2 implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * `Kci` constructor.
     */
    public Kci2() {

    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a KCI test.
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.test.Kci2 kci = new edu.cmu.tetrad.search.test.Kci2((DataSet) dataSet);
        kci.setAlpha(parameters.getDouble(Params.ALPHA));

        kci.epsilon = parameters.getDouble(Params.KCI_EPSILON);
        kci.scalingFactor = parameters.getDouble(Params.SCALING_FACTOR);     // tune if you like
        kci.approximate = parameters.getBoolean(Params.KCI_USE_APPROXIMATION);      // fast by default
        kci.numPermutations = parameters.getInt(Params.KCI_NUM_BOOTSTRAPS);  // only used if approximate=false
        switch (parameters.getInt(Params.KERNEL_TYPE)) {
            case 1:
                kci.kernelType = edu.cmu.tetrad.search.test.Kci2.KernelType.GAUSSIAN;
                break;
            case 2:
                kci.kernelType = edu.cmu.tetrad.search.test.Kci2.KernelType.GAUSSIAN;
                break;
            case 3:
                throw new IllegalArgumentException("Polynomial kernal type not supported");
            default:
                throw new IllegalArgumentException("Unknown kernel type: " + parameters.getInt(Params.KERNEL_TYPE));
        }

        return kci;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the test.
     */
    @Override
    public String getDescription() {
        return "KCI-2";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the data type of the test, which is continuous.
     *
     * @see DataType
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the parameters of the test.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.KCI_USE_APPROXIMATION);
        params.add(Params.ALPHA);
        params.add(Params.SCALING_FACTOR);
        params.add(Params.KCI_NUM_BOOTSTRAPS);
        params.add(Params.KCI_EPSILON);
        params.add(Params.KERNEL_TYPE);
        return params;
    }
}

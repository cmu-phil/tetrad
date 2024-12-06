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
 * @version $Id: $Id
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
    private boolean verbose = true;

    /**
     * `Kci` constructor.
     */
    public Kci() {

    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a KCI test.
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {


        edu.cmu.tetrad.search.test.Kci kci = new edu.cmu.tetrad.search.test.Kci(SimpleDataLoader.getContinuousDataSet(dataSet),
                parameters.getDouble(Params.ALPHA));

        switch (parameters.getInt(Params.KERNEL_TYPE)) {
            case 1:
                kci.setKernelType(edu.cmu.tetrad.search.test.Kci.KernelType.GAUSSIAN);
                break;
            case 2:
                kci.setKernelType(edu.cmu.tetrad.search.test.Kci.KernelType.POLYNOMIAL);
                break;
        }

        kci.setPolyDegree(parameters.getInt(Params.POLYNOMIAL_DEGREE));
        kci.setPolyConst(parameters.getDouble(Params.POLYNOMIAL_CONSTANT));

        kci.setApproximate(parameters.getBoolean(Params.KCI_USE_APPROXIMATION));
        kci.setScalingFactor(parameters.getDouble(Params.SCALING_FACTOR));
        kci.setNumBootstraps(parameters.getInt(Params.KCI_NUM_BOOTSTRAPS));
        kci.setThreshold(parameters.getDouble(Params.THRESHOLD_FOR_NUM_EIGENVALUES));
        kci.setEpsilon(parameters.getDouble(Params.KCI_EPSILON));
        return kci;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the test.
     */
    @Override
    public String getDescription() {
        return "KCI";
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
        params.add(Params.THRESHOLD_FOR_NUM_EIGENVALUES);
        params.add(Params.KCI_EPSILON);
        params.add(Params.KERNEL_TYPE);
        params.add(Params.POLYNOMIAL_DEGREE);
        params.add(Params.POLYNOMIAL_CONSTANT);
        return params;
    }

    /**
     * Sets the verbosity level of the KCI test. By default true for this (slow) test.
     *
     * @param verbose a boolean indicating whether verbose output should be enabled (true) or disabled (false).
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}

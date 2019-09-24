package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ConditionalCorrelationIndependence;
import edu.cmu.tetrad.search.IndTestConditionalCorrelation;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import java.util.ArrayList;
import java.util.List;

// Can't change the name of this yet.

/**
 * Wrapper for Daudin Conditional Independence test.
 *
 * @author jdramsey
 */
@TestOfIndependence(
        name = "Conditional Correlation Independence (CCI) Test",
        command = "cci-test",
        dataType = DataType.Continuous
)
public class CciTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;
    private Graph initialGraph = null;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        final IndTestConditionalCorrelation cci = new IndTestConditionalCorrelation(DataUtils.getContinuousDataSet(dataSet),
                parameters.getDouble(Params.ALPHA));
        if (parameters.getInt(Params.KERNEL_TYPE) == 1) {
            cci.setKernel(ConditionalCorrelationIndependence.Kernel.Gaussian);

        } else if (parameters.getInt(Params.KERNEL_TYPE) == 2) {
            cci.setKernel(ConditionalCorrelationIndependence.Kernel.Epinechnikov);
        } else {
            throw new IllegalStateException("Kernel not configured.");
        }

        if (parameters.getInt(Params.BASIS_TYPE) == 1) {
            cci.setBasis(ConditionalCorrelationIndependence.Basis.Polynomial);
        } else if (parameters.getInt(Params.BASIS_TYPE) == 2) {
            cci.setBasis(ConditionalCorrelationIndependence.Basis.Cosine);
        } else {
            throw new IllegalStateException("Basis not configured.");
        }

        cci.setNumFunctions(parameters.getInt(Params.NUM_BASIS_FUNCTIONS));
        cci.setKernelMultiplier(parameters.getDouble(Params.KERNEL_MULTIPLIER));
        cci.setFastFDR(parameters.getBoolean("fastFDR"));
        cci.setKernelRegressionSampleSize(parameters.getInt(Params.KERNEL_REGRESSION_SAMPLE_SIZE));
        cci.setNumDependenceSpotChecks(parameters.getInt("numDependenceSpotChecks"));
        cci.setEarlyReturn(true);

        return cci;
    }

    @Override
    public String getDescription() {
        return "CCI Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.NUM_BASIS_FUNCTIONS);
        params.add(Params.KERNEL_TYPE);
        params.add(Params.KERNEL_MULTIPLIER);
        params.add(Params.BASIS_TYPE);
        params.add("fastFDR");
        params.add(Params.KERNEL_REGRESSION_SAMPLE_SIZE);
        params.add("numDependenceSpotChecks");
        return params;
    }
}
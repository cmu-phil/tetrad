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
import java.util.ArrayList;
import java.util.List;

// Can't change the name of this yet.

/**
 * Wrapper for Daudin Conditional Independence test.
 *
 * @author jdramsey
 */
@TestOfIndependence(
        name = "CCI Test",
        command = "cci-test",
        dataType = DataType.Continuous
)
public class CciTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;
    private Graph initialGraph = null;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        final IndTestConditionalCorrelation cci = new IndTestConditionalCorrelation(DataUtils.getContinuousDataSet(dataSet),
                parameters.getDouble("alpha"));
        if (parameters.getInt("kernelType") == 1) {
            cci.setKernel(ConditionalCorrelationIndependence.Kernel.Gaussian);

        } else if (parameters.getInt("kernelType") == 2) {
            cci.setKernel(ConditionalCorrelationIndependence.Kernel.Epinechnikov);
        } else {
            throw new IllegalStateException("Kernel not configured.");
        }

        if (parameters.getInt("basisType") == 1) {
            cci.setBasis(ConditionalCorrelationIndependence.Basis.Polynomial);
        } else if (parameters.getInt("basisType") == 2) {
            cci.setBasis(ConditionalCorrelationIndependence.Basis.Cosine);
        } else {
            throw new IllegalStateException("Basis not configured.");
        }

        cci.setNumFunctions(parameters.getInt("numBasisFunctions"));
        cci.setKernelMultiplier(parameters.getDouble("kernelMultiplier"));
        cci.setFastFDR(parameters.getBoolean("fastFDR"));
        cci.setKernelRegressionSampleSize(parameters.getInt("kernelRegressionSampleSize"));
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
        params.add("alpha");
        params.add("numBasisFunctions");
        params.add("kernelType");
        params.add("kernelMultiplier");
        params.add("basisType");
        params.add("fastFDR");
        params.add("kernelRegressionSampleSize");
        params.add("numDependenceSpotChecks");
        return params;
    }
}
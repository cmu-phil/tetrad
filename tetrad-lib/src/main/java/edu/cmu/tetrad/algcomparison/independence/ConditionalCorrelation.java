package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Cci;
import edu.cmu.tetrad.search.IndTestConditionalCorrelation;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@TestOfIndependence(
        name = "Conditional Correlation Test",
        command = "cond-correlation",
        dataType = DataType.Continuous
)
public class ConditionalCorrelation implements IndependenceWrapper {

    static final long serialVersionUID = 23L;
    private Graph initialGraph = null;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        final IndTestConditionalCorrelation cci = new IndTestConditionalCorrelation(DataUtils.getContinuousDataSet(dataSet),
                parameters.getDouble("alpha"));
        if (parameters.getInt("kernelType") == 1) {
            cci.setKernel(Cci.Kernel.Gaussian);
        } else if (parameters.getInt("kernelType") == 2) {
            cci.setKernel(Cci.Kernel.Uniform);
        } else {
            throw new IllegalStateException("Kernel not configured.");
        }
        cci.setNumFunctions(parameters.getInt("numBasisFunctions"));
        cci.setWidth(parameters.getDouble("kernelWidth"));
        return cci;
    }

    @Override
    public String getDescription() {
        return "Conditional Correlation Test";
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
        params.add("kernelWidth");
        return params;
    }
}

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.IndTestKciMatlab;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.csb.KCI;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@TestOfIndependence(
        name = "Kernel Independence (Matlab)",
        command = "kci-matlab",
        dataType = DataType.Continuous
)
public class KciMatlab implements IndependenceWrapper {

    static final long serialVersionUID = 23L;


    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        return new IndTestKciMatlab(DataUtils.getContinuousDataSet(dataSet),
                parameters.getDouble("alpha"));
    }

    @Override
    public String getDescription() {
        return "KCI (Matlab)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add("kciUseAppromation");
        params.add("alpha");
        params.add("kernelMultiplier");
        params.add("kciNumBootstraps");
        params.add("thresholdForNumEigenvalues");
        params.add("kciEpsilon");
        return params;
    }
}

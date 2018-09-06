package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
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
        name = "Kernel Independence Test",
        command = "kci",
        dataType = DataType.Continuous
)
public class Kci implements IndependenceWrapper {

    static final long serialVersionUID = 23L;


    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        final edu.cmu.tetrad.search.Kci kci = new edu.cmu.tetrad.search.Kci(DataUtils.getContinuousDataSet(dataSet),
                parameters.getDouble("alpha"));
        kci.setApproximate(parameters.getBoolean("kciUseAppromation"));
        kci.setWidthMultiplier(parameters.getDouble("kernelMultiplier"));
        kci.setNumBootstraps(parameters.getInt("kciNumBootstraps"));
        kci.setThreshold(parameters.getDouble("thresholdForNumEigenvalues"));
        kci.setEpsilon(parameters.getDouble("kciEpsilon"));
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
        params.add("kciUseAppromation");
        params.add("alpha");
        params.add("kernelMultiplier");
        params.add("kciNumBootstraps");
        params.add("thresholdForNumEigenvalues");
        params.add("kciEpsilon");
        return params;
    }
}
package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestConditionalCorrelation;
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
        name = "Kernel Independence Test",
        command = "kci",
        dataType = DataType.Continuous
)
public class Kci implements IndependenceWrapper {

    static final long serialVersionUID = 23L;


    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        final KCI kci = new KCI(DataUtils.getContinuousDataSet(dataSet),
                parameters.getDouble("alpha"));
        kci.setWidth(parameters.getDouble("kernelMultiplier"));
        return kci;
    }

    @Override
    public String getDescription() {
        return "Kernel Independence Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add("alpha");
        params.add("kernelMultiplier");
        return params;
    }
}

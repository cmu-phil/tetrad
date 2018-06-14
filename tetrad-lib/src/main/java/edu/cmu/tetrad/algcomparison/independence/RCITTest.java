package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.IndTestConditionalCorrelation;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.rcit.RandomizedConditionalIndependenceTest;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@TestOfIndependence(
        name = "RCIT Test",
        command = "rcit-test",
        dataType = DataType.Continuous
)
public class RCITTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        final RandomizedConditionalIndependenceTest rcit = new RandomizedConditionalIndependenceTest(DataUtils.getContinuousDataSet(dataSet));
        rcit.setAlpha(parameters.getDouble("alpha"));
        rcit.setNum_feature(parameters.getInt("rcitNumFeatures"));
        return rcit;
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
        params.add("rcitNumFeatures");
        return params;
    }

}

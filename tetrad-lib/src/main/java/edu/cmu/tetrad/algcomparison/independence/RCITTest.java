package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.rcit.RandomIndApproximateMethod;
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
@Experimental
public class RCITTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        final RandomizedConditionalIndependenceTest rcit = new RandomizedConditionalIndependenceTest(DataUtils.getContinuousDataSet(dataSet));
        rcit.setAlpha(parameters.getDouble("alpha"));
        rcit.setNum_feature(parameters.getInt("rcitNumFeatures"));

        int algType = parameters.getInt("rcitApproxType");

//                lpd4,  // the Lindsay-Pilla-Basak method (default)
//                gamma, // the Satterthwaite-Welch method
//                hbe,   // the Hall-Buckley-Eagleson method
//                chi2,  // a normalized chi-squared statistic -- won't work JR
//                perm   // permutation testing (warning: this one is slow but recommended for small samples generally <500 )

        if (algType == 1) {
            rcit.setApprox(RandomIndApproximateMethod.lpd4);
        } else if (algType == 2) {
            rcit.setApprox(RandomIndApproximateMethod.gamma);
        } else if (algType == 3) {
            rcit.setApprox(RandomIndApproximateMethod.hbe);
        } else if (algType == 4) {
            rcit.setApprox(RandomIndApproximateMethod.perm);
        }

        return rcit;
    }

    @Override
    public String getDescription() {
        return "RCIT";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add("rcitApproxType");
        params.add("alpha");
        params.add("rcitNumFeatures");
        return params;
    }

}
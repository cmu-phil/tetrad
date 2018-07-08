package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.search.ScoredIndTest;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.rcit.RandomIndApproximateMethod;
import edu.pitt.dbmi.algo.rcit.RandomizedConditionalIndependenceTest;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for CCI Score.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Score(
        name = "RCIT Score",
        command = "rcit-score",
        dataType = {DataType.Continuous}
)
public class RcitScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        final RandomizedConditionalIndependenceTest rcit = new RandomizedConditionalIndependenceTest(DataUtils.getContinuousDataSet(dataSet));
        rcit.setAlpha(parameters.getDouble("alpha"));
        rcit.setNum_feature(parameters.getInt("rcitNumFeatures"));

        int algType = parameters.getInt("rcitApproxType");

//                lpd4,  // the Lindsay-Pilla-Basak method (default)
//                gamma, // the Satterthwaite-Welch method
//                hbe,   // the Hall-Buckley-Eagleson method
//                chi2,  // a normalized chi-squared statistic   -- won't work JR
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

        return new ScoredIndTest(rcit);
    }

    @Override
    public String getDescription() {
        return "RCIT Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("rcitApproxType");
        parameters.add("alpha");
        parameters.add("rcitNumFeatures");
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }

}
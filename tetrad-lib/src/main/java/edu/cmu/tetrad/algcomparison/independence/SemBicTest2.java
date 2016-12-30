package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.IndTestScore;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SemBicScore;
import edu.cmu.tetrad.search.SemBicScore2;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class SemBicTest2 implements IndependenceWrapper {
    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        SemBicScore2 score = new SemBicScore2(DataUtils.getCovMatrix(dataSet));
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        IndTestScore indTestScore = new IndTestScore(score);
        indTestScore.setMinScoreDifference(parameters.getDouble("minScoreDifference"));
        return indTestScore;
    }

    @Override
    public String getDescription() {
        return "SEM BIC test 2";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add("penaltyDiscount");
        params.add("minScoreDifference");
        return params;
    }
}

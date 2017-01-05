package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.ConditionalGaussianScore;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.search.SemBicScore2;
import edu.cmu.tetrad.util.Experimental;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class SemBic2Score implements ScoreWrapper, Experimental {
    static final long serialVersionUID = 23L;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        final SemBicScore2 score = new SemBicScore2(DataUtils.getContinuousDataSet(dataSet));
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        return score;
    }

    @Override
    public String getDescription() {
        return "Conditional Gaussian BIC Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
        parameters.add("cgExact");
        parameters.add("assumeMixed");
        return parameters;
    }
}

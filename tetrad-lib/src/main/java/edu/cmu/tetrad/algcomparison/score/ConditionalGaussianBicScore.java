package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.ConditionalGaussianScore;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Experimental;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class ConditionalGaussianBicScore implements ScoreWrapper, Experimental {
    private Score score;

    @Override
    public Score getScore(DataSet dataSet, Parameters parameters) {
        return new ConditionalGaussianScore(dataSet);
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
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("penaltyDiscount", 4);
        return parameters;
    }
}

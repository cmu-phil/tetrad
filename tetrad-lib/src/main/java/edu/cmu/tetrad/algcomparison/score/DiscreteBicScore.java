package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.Score;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class DiscreteBicScore implements ScoreWrapper {

    @Override
    public Score getScore(DataSet dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.BicScore score
                = new edu.cmu.tetrad.search.BicScore(dataSet);
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount", 4));
        return score;
    }

    @Override
    public String getDescription() {
        return "Discrete BIC Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("penaltyDiscount", 4);
        return parameters;
    }
}

package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.Score;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class SemBicScore implements ScoreWrapper {

    @Override
    public Score getScore(DataSet dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.SemBicScore semBicScore
                = new edu.cmu.tetrad.search.SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
        semBicScore.setPenaltyDiscount(parameters.getDouble("penaltyDiscount", 4));
        return semBicScore;
    }

    @Override
    public String getDescription() {
        return "Sem BIC Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("penaltyDiscount", 4);
        return params;
    }

}

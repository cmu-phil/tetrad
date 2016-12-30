package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class SemBicScore3 implements ScoreWrapper {
    static final long serialVersionUID = 23L;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.SemBicScore3 semBicScore
                = new edu.cmu.tetrad.search.SemBicScore3(DataUtils.getCovMatrix(dataSet));
        semBicScore.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        return semBicScore;
    }

    @Override
    public String getDescription() {
        return "Sem BIC Score 3";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
        return parameters;
    }

}

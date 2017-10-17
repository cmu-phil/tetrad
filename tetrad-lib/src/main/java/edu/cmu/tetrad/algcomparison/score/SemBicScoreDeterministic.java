package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Parameters;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Sem BIC Score Deterministic",
        command = "sem-bic-deterministic",
        dataType = {DataType.Continuous, DataType.Covariance}
)
public class SemBicScoreDeterministic implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        edu.cmu.tetrad.search.SemBicScoreDeterministic semBicScore
                = new edu.cmu.tetrad.search.SemBicScoreDeterministic(DataUtils.getCovMatrix(dataSet));
        semBicScore.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        semBicScore.setDeterminismThreshold(parameters.getDouble("determinismThreshold"));
        return semBicScore;
    }

    @Override
    public String getDescription() {
        return "Sem BIC Score Deterministic";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
        parameters.add("determinismThreshold");
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }

}

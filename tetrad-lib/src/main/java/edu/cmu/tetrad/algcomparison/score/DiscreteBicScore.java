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
        name = "Discrete BIC Score",
        command = "disc-bic",
        dataType = DataType.Discrete
)
public class DiscreteBicScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        edu.cmu.tetrad.search.BicScore score
                = new edu.cmu.tetrad.search.BicScore(DataUtils.getDiscreteDataSet(dataSet));
        score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
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
    public List<String> getParameters() {
        List<String> paramDescriptions = new ArrayList<>();
        paramDescriptions.add("penaltyDiscount");
        return paramDescriptions;
    }

    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }
}

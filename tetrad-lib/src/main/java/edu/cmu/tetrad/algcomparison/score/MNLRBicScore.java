package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.MNLRScore;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Parameters;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for MVP BIC Score.
 *
 * @author Bryan Andrews
 */
@Experimental
@edu.cmu.tetrad.annotation.Score(
        name = "Multinomial Logistic Regression BIC Score",
        command = "multinomial-logistic-regression-bic",
        dataType = DataType.Mixed
)
public class MNLRBicScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        return new MNLRScore(DataUtils.getMixedDataSet(dataSet),
                1,
                parameters.getInt("fDegree", 1));
    }

    @Override
    public String getDescription() {
        return "Multinomial Logistic Regression BIC Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("fDegree");
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return null;
    }
}

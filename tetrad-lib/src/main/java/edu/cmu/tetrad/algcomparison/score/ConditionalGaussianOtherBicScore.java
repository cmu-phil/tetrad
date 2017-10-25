package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ConditionalGaussianOtherScore;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Parameters;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@Experimental
@edu.cmu.tetrad.annotation.Score(
        name = "Conditional Gaussian Other BIC Score",
        command = "conditional-gaussian-other-bic",
        dataType = DataType.Mixed
)
public class ConditionalGaussianOtherBicScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        final ConditionalGaussianOtherScore conditionalGaussianScore
                = new ConditionalGaussianOtherScore(DataUtils.getMixedDataSet(dataSet), parameters.getDouble("structurePrior"), parameters.getInt("discretize") > 0);
        conditionalGaussianScore.setNumCategoriesToDiscretize(parameters.getInt("numCategoriesToDiscretize"));
        return conditionalGaussianScore;
    }

    @Override
    public String getDescription() {
        return "Conditional Gaussian Other BIC Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add("structurePrior");
        parameters.add("discretize");
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return null;
    }
}

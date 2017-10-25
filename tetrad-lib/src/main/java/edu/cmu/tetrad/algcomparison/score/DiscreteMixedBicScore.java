package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.DiscreteMixedScore;
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
        name = "Discrete Mixed BIC Score",
        command = "disc-mixed-bic",
        dataType = DataType.Mixed
)
public class DiscreteMixedBicScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        final DiscreteMixedScore discreteMixedScore
                = new DiscreteMixedScore(DataUtils.getMixedDataSet(dataSet), parameters.getDouble("structurePrior"));
        discreteMixedScore.setNumCategoriesToDiscretize(parameters.getInt("numCategoriesToDiscretize"));
        return discreteMixedScore;
    }

    @Override
    public String getDescription() {
        return "Discrete Mixed BIC Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("structurePrior");
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return null;
    }
}

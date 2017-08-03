package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.MVPScore;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Experimental;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for MVP BIC Score.
 *
 * @author Bryan Andrews
 */
public class MVPBicScore implements ScoreWrapper, Experimental {
    static final long serialVersionUID = 23L;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        return new MVPScore(DataUtils.getMixedDataSet(dataSet),
                parameters.getDouble("structurePrior", 0),
                parameters.getInt("fDegree", -1),
                parameters.getInt("discretize", 0) > 0);
    }

    @Override
    public String getDescription() {
        return "Mixed Variable Polynomial BIC Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("structurePrior");
        parameters.add("fDegree");
        parameters.add("discretize");
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return null;
    }
}

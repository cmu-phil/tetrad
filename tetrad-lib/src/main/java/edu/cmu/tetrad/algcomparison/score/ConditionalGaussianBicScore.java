package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.ConditionalGaussianScore;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Experimental;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
public class  ConditionalGaussianBicScore implements ScoreWrapper, Experimental {
    static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        final ConditionalGaussianScore conditionalGaussianScore
                = new ConditionalGaussianScore(DataUtils.getMixedDataSet(dataSet), parameters.getDouble("structurePrior"), parameters.getBoolean("discretize"));
        conditionalGaussianScore.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        conditionalGaussianScore.setNumCategoriesToDiscretize(parameters.getInt("numCategoriesToDiscretize"));
        return conditionalGaussianScore;
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
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
        parameters.add("cgExact");
        parameters.add("assumeMixed");
        parameters.add("structurePrior");
        parameters.add("discretize");
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }
}

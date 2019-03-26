package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ConditionalGaussianScore;
import edu.cmu.tetrad.search.DegenerateGaussianScore;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for degenerate Gaussian BIC score
 *
 * @author bandrews
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Degenerate Gaussian BIC Score",
        command = "degen-gauss-bic",
        dataType = DataType.Mixed
)
public class DegenerateGaussianBicScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        final DegenerateGaussianScore degenerateGaussianScore = new DegenerateGaussianScore(DataUtils.getMixedDataSet(dataSet));
        degenerateGaussianScore.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        degenerateGaussianScore.setStructurePrior(parameters.getDouble("structurePrior"));
        return degenerateGaussianScore;
    }

    @Override
    public String getDescription() {
        return "Degenerate Gaussian BIC Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
        parameters.add("structurePrior");
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return  dataSet.getVariable(name);
    }
}

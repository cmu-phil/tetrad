package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.Score;

import java.util.Collections;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 * @author jdramsey
 */
public class ConditionalGaussianBicScore implements ScoreWrapper {
    private DataSet dataSet = null;
    private Score score = null;

    @Override
    public Score getScore(DataSet dataSet, Parameters parameters) {
        if (dataSet != this.dataSet) {
            this.dataSet = dataSet;
            edu.cmu.tetrad.search.SemBicScore semBicScore
                    = new edu.cmu.tetrad.search.SemBicScore(new CovarianceMatrixOnTheFly(dataSet));
            semBicScore.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
            this.score = semBicScore;
        }
        return score;
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
        return Collections.singletonList("penaltyDiscount");
    }

}

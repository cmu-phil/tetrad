package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.algcomparison.independence.IndTestWrapper;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.BffGes;
import edu.cmu.tetrad.search.IndTestFisherZ;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Score;

import java.util.Collections;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 * @author jdramsey
 */
public class SemBicScore implements ScoreWrapper {
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
        return "Sem BIC Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        return Collections.singletonList("penaltyDiscount");
    }

}

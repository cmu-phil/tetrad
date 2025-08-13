package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for linear, Gaussian SEM BIC score.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Rank BIC Score",
        command = "rank-bic-score",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class RankBicScore implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data set.
     */
    private DataModel dataSet;

    /**
     * Constructs a new instance of the SemBicScore.
     */
    public RankBicScore() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        edu.cmu.tetrad.search.score.RankBicScore rankBicScore;

        if (dataSet instanceof DataSet) {
            rankBicScore = new edu.cmu.tetrad.search.score.RankBicScore((DataSet) this.dataSet);
        } else {
            throw new IllegalArgumentException("Expecting a dataset.");
        }

        rankBicScore.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

        return rankBicScore;
    }

    /**
     * Returns the description of the Sem BIC Score.
     *
     * @return the description of the Sem BIC Score
     */
    @Override
    public String getDescription() {
        return "Rank BIC Score";
    }

    /**
     * Returns the data type of the current score.
     *
     * @return the data type of the score
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Returns a list of parameters applicable to this method.
     *
     * @return a list of parameters
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.SEM_BIC_STRUCTURE_PRIOR);
        parameters.add(Params.SEM_BIC_RULE);
        parameters.add(Params.PRECOMPUTE_COVARIANCES);
        parameters.add(Params.SINGULARITY_LAMBDA);
        return parameters;
    }

    /**
     * Retrieves the variable with the given name from the data set.
     *
     * @param name the name of the variable
     * @return the variable with the given name, or null if no such variable exists
     */
    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }

}

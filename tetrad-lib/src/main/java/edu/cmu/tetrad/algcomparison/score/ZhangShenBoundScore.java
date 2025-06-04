package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.ZsbScore;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for linear, Gaussian Extended BIC score (Chen and Chen).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Score(
        name = "ZS Bound Score",
        command = "zsbound-score",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class ZhangShenBoundScore implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data set.
     */
    private DataModel dataSet;

    /**
     * This class represents the constructor for the ZhangShenBoundScore class. It is used to create an instance of the
     * ZhangShenBoundScore class.
     */
    public ZhangShenBoundScore() {

    }

    /**
     * Calculates the score based on the given data set and parameters.
     *
     * @param dataSet    The data set to test independence against.
     * @param parameters The parameters of the test.
     * @return The calculated score.
     * @throws IllegalArgumentException If the data set is neither a dataset nor a covariance matrix.
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        boolean precomputeCovariances = parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES);

        ZsbScore score;

        if (dataSet instanceof DataSet) {
            score = new ZsbScore((DataSet) this.dataSet, precomputeCovariances);
        } else if (dataSet instanceof ICovarianceMatrix) {
            score = new ZsbScore((ICovarianceMatrix) this.dataSet);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        score.setRiskBound(parameters.getDouble(Params.ZS_RISK_BOUND));
        score.setLambda(parameters.getDouble(Params.SINGULARITY_LAMBDA));

        return score;
    }

    /**
     * Returns the description of the Zhang-Shen Bound Score.
     *
     * @return the description of the score
     */
    @Override
    public String getDescription() {
        return "Zhang-Shen Bound Score";
    }

    /**
     * Returns the data type of the score.
     *
     * @return the data type of the score
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Returns the list of parameters required for the Zhang-Shen Bound Score.
     *
     * @return The list of parameters required for the score.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ZS_RISK_BOUND);
        parameters.add(Params.PRECOMPUTE_COVARIANCES);
        parameters.add(Params.SINGULARITY_LAMBDA);
        return parameters;
    }

    /**
     * Retrieves the variable with the given name from the data set.
     *
     * @param name the name of the variable to retrieve.
     * @return the variable as a {@link Node} object.
     */
    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }
}

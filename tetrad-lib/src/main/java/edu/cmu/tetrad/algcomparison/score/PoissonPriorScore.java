package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for the Poisson prior score (Bryan)
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Poisson Prior Score",
        command = "poisson-prior-score",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class PoissonPriorScore implements ScoreWrapper {

    private static final long serialVersionUID = 23L;
    private DataModel dataSet;

    /**
     * {@inheritDoc}
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        edu.cmu.tetrad.search.score.PoissonPriorScore score;

        if (dataSet instanceof DataSet) {
            score = new edu.cmu.tetrad.search.score.PoissonPriorScore((DataSet) this.dataSet, parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES));
        } else if (dataSet instanceof ICovarianceMatrix) {
            score = new edu.cmu.tetrad.search.score.PoissonPriorScore((ICovarianceMatrix) this.dataSet);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        score.setLambda(parameters.getDouble(Params.POISSON_LAMBDA));
        score.setUsePseudoInverse(parameters.getBoolean(Params.USE_PSEUDOINVERSE));

        return score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Poisson Prior Score";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.PRECOMPUTE_COVARIANCES);
        parameters.add(Params.POISSON_LAMBDA);
        parameters.add(Params.USE_PSEUDOINVERSE);
        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }
}

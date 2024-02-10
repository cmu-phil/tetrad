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
        name = "Sem BIC Score",
        command = "sem-bic-score",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class SemBicScore implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;
    private DataModel dataSet;

    /**
     * {@inheritDoc}
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        edu.cmu.tetrad.search.score.SemBicScore semBicScore;
        boolean precomputeCovariances = parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES);

        if (dataSet instanceof DataSet) {
            semBicScore = new edu.cmu.tetrad.search.score.SemBicScore((DataSet) this.dataSet, precomputeCovariances);
        } else if (dataSet instanceof ICovarianceMatrix) {
            semBicScore = new edu.cmu.tetrad.search.score.SemBicScore((ICovarianceMatrix) this.dataSet);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        semBicScore.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
        semBicScore.setStructurePrior(parameters.getDouble(Params.SEM_BIC_STRUCTURE_PRIOR));
        semBicScore.setUsePseudoInverse(parameters.getBoolean(Params.USE_PSEUDOINVERSE));

        switch (parameters.getInt(Params.SEM_BIC_RULE)) {
            case 1:
                semBicScore.setRuleType(edu.cmu.tetrad.search.score.SemBicScore.RuleType.CHICKERING);
                break;
            case 2:
                semBicScore.setRuleType(edu.cmu.tetrad.search.score.SemBicScore.RuleType.NANDY);
                break;
            default:
                throw new IllegalStateException("Expecting 1 or 2: " + parameters.getInt(Params.SEM_BIC_RULE));
        }

        return semBicScore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Sem BIC Score";
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
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.SEM_BIC_STRUCTURE_PRIOR);
        parameters.add(Params.SEM_BIC_RULE);
        parameters.add(Params.PRECOMPUTE_COVARIANCES);
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

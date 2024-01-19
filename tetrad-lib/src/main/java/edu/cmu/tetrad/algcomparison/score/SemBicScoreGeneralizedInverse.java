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
 * Wrapper for linear, Gaussian SEM BIC score. This version of the SEM BIC score uses the generalized inverse of the
 * covariance matrix, which is more numerically stable than the standard inverse.
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Sem BIC Score Generalized Inverse",
        command = "sem-bic-score-gen-inv",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class SemBicScoreGeneralizedInverse implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        edu.cmu.tetrad.search.score.SemBicScoreGeneralizedInverse semBicScore;
        boolean precomputeCovariances = parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES);

        if (dataSet instanceof DataSet) {
            semBicScore = new edu.cmu.tetrad.search.score.SemBicScoreGeneralizedInverse((DataSet) this.dataSet, precomputeCovariances);
        } else if (dataSet instanceof ICovarianceMatrix) {
            semBicScore = new edu.cmu.tetrad.search.score.SemBicScoreGeneralizedInverse((ICovarianceMatrix) this.dataSet);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        semBicScore.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
        semBicScore.setStructurePrior(parameters.getDouble(Params.SEM_BIC_STRUCTURE_PRIOR));

        switch (parameters.getInt(Params.SEM_BIC_RULE)) {
            case 1:
                semBicScore.setRuleType(edu.cmu.tetrad.search.score.SemBicScoreGeneralizedInverse.RuleType.CHICKERING);
                break;
            case 2:
                semBicScore.setRuleType(edu.cmu.tetrad.search.score.SemBicScoreGeneralizedInverse.RuleType.NANDY);
                break;
            default:
                throw new IllegalStateException("Expecting 1 or 2: " + parameters.getInt(Params.SEM_BIC_RULE));
        }

        return semBicScore;
    }

    @Override
    public String getDescription() {
        return "Sem BIC Score Generalized Inverse";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.SEM_BIC_STRUCTURE_PRIOR);
        parameters.add(Params.SEM_BIC_RULE);
        parameters.add(Params.PRECOMPUTE_COVARIANCES);
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }

}

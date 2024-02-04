package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.GicScores;
import edu.cmu.tetrad.search.test.ScoreIndTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 */
@TestOfIndependence(
        name = "Generalized Information Criterion Score Tests",
        command = "gic-score-tests",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class GICScoreTests implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        GicScores score;
        boolean precomputeCovariances = parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES);

        if (dataSet instanceof DataSet) {
            score = new GicScores((DataSet) dataSet, precomputeCovariances);
        } else if (dataSet instanceof ICovarianceMatrix) {
            score = new GicScores((ICovarianceMatrix) dataSet);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        int anInt = parameters.getInt((Params.SEM_GIC_RULE));
        GicScores.RuleType ruleType;

        switch (anInt) {
            case 1:
                ruleType = GicScores.RuleType.BIC;
                break;
            case 2:
                ruleType = GicScores.RuleType.GIC2;
                break;
            case 3:
                ruleType = GicScores.RuleType.RIC;
                break;
            case 4:
                ruleType = GicScores.RuleType.RICc;
                break;
            case 5:
                ruleType = GicScores.RuleType.GIC5;
                break;
            case 6:
                ruleType = GicScores.RuleType.GIC6;
                break;
            default:
                throw new IllegalArgumentException("Unrecognized rule type: " + anInt);
        }

        score.setRuleType(ruleType);
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
        score.setUsePseudoInverse(parameters.getBoolean(Params.USE_PSEUDOINVERSE));

        return new ScoreIndTest(score, dataSet);
    }

    @Override
    public String getDescription() {
        return "Generalized Information Criterion Score Tests";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.SEM_GIC_RULE);
        params.add(Params.PENALTY_DISCOUNT_ZS);
        params.add(Params.PRECOMPUTE_COVARIANCES);
        params.add(Params.USE_PSEUDOINVERSE);
        return params;
    }
}

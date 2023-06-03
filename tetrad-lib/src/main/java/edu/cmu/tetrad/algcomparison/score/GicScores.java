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
 * Wrapper for linear, Gaussian Extended BIC score (Chen and Chen).
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Generalied Information Criterion Scores",
        command = "gic-scores",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class GicScores implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        edu.cmu.tetrad.search.score.GicScores score;

        if (dataSet instanceof DataSet) {
            score = new edu.cmu.tetrad.search.score.GicScores((DataSet) this.dataSet);
        } else if (dataSet instanceof ICovarianceMatrix) {
            score = new edu.cmu.tetrad.search.score.GicScores((ICovarianceMatrix) this.dataSet);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        int anInt = parameters.getInt((Params.SEM_GIC_RULE));
        edu.cmu.tetrad.search.score.GicScores.RuleType ruleType;

        switch (anInt) {
            case 1:
                ruleType = edu.cmu.tetrad.search.score.GicScores.RuleType.BIC;
                break;
            case 2:
                ruleType = edu.cmu.tetrad.search.score.GicScores.RuleType.GIC2;
                break;
            case 3:
                ruleType = edu.cmu.tetrad.search.score.GicScores.RuleType.RIC;
                break;
            case 4:
                ruleType = edu.cmu.tetrad.search.score.GicScores.RuleType.RICc;
                break;
            case 5:
                ruleType = edu.cmu.tetrad.search.score.GicScores.RuleType.GIC5;
                break;
            case 6:
                ruleType = edu.cmu.tetrad.search.score.GicScores.RuleType.GIC6;
                break;
            default:
                throw new IllegalArgumentException("Unrecognized rule type: " + anInt);
        }

        score.setRuleType(ruleType);
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

        return score;
    }

    @Override
    public String getDescription() {
        return "Generalized Information Criterion Scores";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.SEM_GIC_RULE);
        parameters.add(Params.PENALTY_DISCOUNT_ZS);
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }
}

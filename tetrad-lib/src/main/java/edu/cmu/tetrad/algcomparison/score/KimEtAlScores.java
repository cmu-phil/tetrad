package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.GicScores;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for linear, Gaussian Extended BIC score (Chen and Chen).
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Kim et al. Scores",
        command = "kim-scores",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class KimEtAlScores implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;

        GicScores score;

        if (dataSet instanceof DataSet) {
            score = new GicScores((DataSet) this.dataSet);
        } else if (dataSet instanceof ICovarianceMatrix) {
            score = new GicScores((ICovarianceMatrix) this.dataSet);
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

        return score;
    }

    @Override
    public String getDescription() {
        return "Kim et al. Scores";
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

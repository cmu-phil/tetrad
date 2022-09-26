package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.IndTestScore;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SemBicScore;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author jdramsey
 */
@TestOfIndependence(
        name = "Kim et al. BIC Tests",
        command = "kim-bic-tests",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class KimEtAlScoreTests implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.KimEtAlScores score;

        if (dataSet instanceof DataSet) {
            score = new edu.cmu.tetrad.search.KimEtAlScores((DataSet) dataSet);
        } else if (dataSet instanceof ICovarianceMatrix) {
            score = new edu.cmu.tetrad.search.KimEtAlScores((ICovarianceMatrix) dataSet);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        int anInt = parameters.getInt((Params.SEM_GIC_RULE));
        edu.cmu.tetrad.search.KimEtAlScores.RuleType ruleType;

        switch (anInt) {
            case 1:
                ruleType = edu.cmu.tetrad.search.KimEtAlScores.RuleType.BIC;
                break;
            case 2:
                ruleType = edu.cmu.tetrad.search.KimEtAlScores.RuleType.GIC2;
                break;
            case 3:
                ruleType = edu.cmu.tetrad.search.KimEtAlScores.RuleType.RIC;
                break;
            case 4:
                ruleType = edu.cmu.tetrad.search.KimEtAlScores.RuleType.RICc;
                break;
            case 5:
                ruleType = edu.cmu.tetrad.search.KimEtAlScores.RuleType.GIC5;
                break;
            case 6:
                ruleType = edu.cmu.tetrad.search.KimEtAlScores.RuleType.GIC6;
                break;
            default:
                throw new IllegalArgumentException("Unrecognized rule type: " + anInt);
        }

        score.setRuleType(ruleType);
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));


        return new IndTestScore(score, dataSet);
    }

    @Override
    public String getDescription() {
        return "Kim et al. BIC Tests";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.SEM_GIC_RULE);
        params.add(Params.PENALTY_DISCOUNT);
        return params;
    }
}

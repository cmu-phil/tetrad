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
 * The GicScores class is an implementation of the ScoreWrapper interface that calculates the Generalized Information
 * Criterion (GIC) scores for a given data model. It is used to test the independence between variables in the data
 * set.
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Generalized Information Criterion Scores",
        command = "gic-scores",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class GicScores implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * The data set.
     */
    private DataModel dataSet;

    /**
     * Constructs a new instance of the algorithm.
     */
    public GicScores() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        boolean precomputeCovariances = parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES);

        edu.cmu.tetrad.search.score.GicScores score;

        if (dataSet instanceof DataSet) {
            score = new edu.cmu.tetrad.search.score.GicScores((DataSet) this.dataSet, precomputeCovariances);
        } else if (dataSet instanceof ICovarianceMatrix) {
            score = new edu.cmu.tetrad.search.score.GicScores((ICovarianceMatrix) this.dataSet);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        int anInt = parameters.getInt((Params.SEM_GIC_RULE));
        edu.cmu.tetrad.search.score.GicScores.RuleType ruleType = switch (anInt) {
            case 1 -> edu.cmu.tetrad.search.score.GicScores.RuleType.BIC;
            case 2 -> edu.cmu.tetrad.search.score.GicScores.RuleType.GIC2;
            case 3 -> edu.cmu.tetrad.search.score.GicScores.RuleType.RIC;
            case 4 -> edu.cmu.tetrad.search.score.GicScores.RuleType.RICc;
            case 5 -> edu.cmu.tetrad.search.score.GicScores.RuleType.GIC5;
            case 6 -> edu.cmu.tetrad.search.score.GicScores.RuleType.GIC6;
            default -> throw new IllegalArgumentException("Unrecognized rule type: " + anInt);
        };

        score.setRuleType(ruleType);
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
        score.setLambda(parameters.getDouble(Params.REGULARIZATION_LAMBDA));

        return score;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Generalized Information Criterion Scores";
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
        parameters.add(Params.SEM_GIC_RULE);
        parameters.add(Params.PENALTY_DISCOUNT_ZS);
        parameters.add(Params.PRECOMPUTE_COVARIANCES);
        parameters.add(Params.REGULARIZATION_LAMBDA);

        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getVariable(String name) {
        return dataSet.getVariable(name);
    }
}

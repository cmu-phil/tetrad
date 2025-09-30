package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.InstanceAugmentedSemBicScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for InstanceAugmentedSemBicScore that:
 *  - pulls the testing DataSet from Params.TESTING_DATA (injected by the algorithm wrapper),
 *  - takes the instance row from Params.INSTANCE_ROW,
 *  - aligns columns by training variable names,
 *  - uses alpha from Params.IS_ALPHA (default 1.0).
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Instance-specific Augmented Sem BIC Score",
        command = "is-sem-bic-score",
        dataType = {DataType.Continuous, DataType.Covariance}
)
public final class InstanceAugmentedSemBicScoreWrapper implements ScoreWrapper, HasKnowledge {

    private Knowledge knowledge;

    @Override
    public Score getScore(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet train) || !train.isContinuous()) {
            throw new IllegalArgumentException("Requires a continuous DataSet.");
        }

        // testing data and row
        DataSet testing = knowledge.getTestingData();
        if (testing == null) {
            // Fall back to using the training data as the source for the instance row
            testing = train;
        }
        if (!testing.isContinuous()) {
            throw new IllegalArgumentException("Testing data must be continuous.");
        }

        int row = parameters.getInt(Params.INSTANCE_ROW, 0);
        if (row < 0 || row >= testing.getNumRows()) {
            throw new IllegalArgumentException("Instance row out of range: " + row);
        }

        // extract instance values aligned to train's variable order
        List<Node> vars = train.getVariables();
        double[] x = new double[vars.size()];
        for (int j = 0; j < vars.size(); j++) {
            Node v = vars.get(j);
            int col = testing.getColumn(testing.getVariable(v.getName()));
            x[j] = testing.getDouble(row, col);
        }

        // build covariance from train, then instance-augmented score
        CovarianceMatrix cov = new CovarianceMatrix(train);
        InstanceAugmentedSemBicScore score = new InstanceAugmentedSemBicScore(cov, x);

        // alpha
        double alpha = parameters.getDouble(Params.INSTANCE_SPECIFIC_ALPHA, 1.0);
        score.setAlpha(alpha);

        // optional: penalty discount passthrough
        if (parameters.getParametersNames().contains(Params.PENALTY_DISCOUNT)) {
            score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
        }

        return score;
    }

    @Override
    public String getDescription() {
        return "Instance-Augmented SEM-BIC (continuous)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> ps = new ArrayList<>();
        ps.add(Params.INSTANCE_SPECIFIC_ALPHA);
        ps.add(Params.INSTANCE_ROW);
        ps.add(Params.PENALTY_DISCOUNT);
        // Params.TESTING_DATA is passed via Parameters by the algorithm wrapper.
        return ps;
    }

    @Override
    public Node getVariable(String name) {
        return null;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge.copy();
    }
}
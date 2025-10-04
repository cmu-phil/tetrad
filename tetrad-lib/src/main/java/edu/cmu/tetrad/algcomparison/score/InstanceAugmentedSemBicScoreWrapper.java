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
 * Wrapper for InstanceAugmentedSemBicScore that: - pulls the testing DataSet from Params.TESTING_DATA (injected by the
 * algorithm wrapper), - takes the instance row from Params.INSTANCE_ROW, - aligns columns by training variable names, -
 * uses alpha from Params.IS_ALPHA (default 1.0).
 */
@edu.cmu.tetrad.annotation.Score(
        name = "Instance-specific Augmented Sem BIC Score",
        command = "is-sem-bic-score",
        dataType = {DataType.Continuous, DataType.Covariance}
)
public final class InstanceAugmentedSemBicScoreWrapper implements ScoreWrapper, HasKnowledge {

    /**
     * Constructs a new instance of the `InstanceAugmentedSemBicScoreWrapper` class.
     * This class serves as a wrapper for computing scores based on the
     * "Instance-Augmented SEM-BIC" method for continuous data. It provides extended functionality
     * for handling instance-specific parameters and supports integration with scoring frameworks.
     */
    public InstanceAugmentedSemBicScoreWrapper() {

    }

    /**
     * Represents the prior background knowledge utilized in the scoring or analysis process. This variable holds
     * constraints or assumptions about the data, which guide or restrict the structure learning or model evaluation.
     */
    private Knowledge knowledge;

    /**
     * Computes the score for a given data model and set of parameters.
     *
     * @param dataModel The data model, which must be a continuous {@code DataSet}.
     *                  If an invalid data model is provided, an {@code IllegalArgumentException} is thrown.
     * @param parameters The parameters used for configuring the score computation,
     *                   including instance-specific alpha, instance row index, and penalty discount (optional).
     *                   The row index must be within the range of rows for the testing data or training data being used.
     * @return A {@code Score} object representing the computed instance-augmented score.
     * @throws IllegalArgumentException If the data model is not continuous, the testing data is not continuous,
     *                                  or the instance row index is out of range.
     */
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

    /**
     * Provides a description of the scoring method used in this class.
     *
     * @return A string describing the scoring method as "Instance-Augmented SEM-BIC (continuous)".
     */
    @Override
    public String getDescription() {
        return "Instance-Augmented SEM-BIC (continuous)";
    }

    /**
     * Returns the data type associated with this scoring method.
     *
     * @return The data type, which is {@code DataType.Continuous}.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves a list of parameter names required by the scoring method.
     * The parameters include specific settings necessary for configuring
     * the instance-augmented scoring process.
     *
     * @return A list of parameter names, including instance-specific alpha,
     *         instance row index, and the penalty discount.
     */
    @Override
    public List<String> getParameters() {
        List<String> ps = new ArrayList<>();
        ps.add(Params.INSTANCE_SPECIFIC_ALPHA);
        ps.add(Params.INSTANCE_ROW);
        ps.add(Params.PENALTY_DISCOUNT);
        // Params.TESTING_DATA is passed via Parameters by the algorithm wrapper.
        return ps;
    }

    /**
     * Retrieves a variable node by its name.
     *
     * @param name The name of the variable to retrieve.
     * @return The {@code Node} corresponding to the specified variable name,
     *         or {@code null} if no such variable exists.
     */
    @Override
    public Node getVariable(String name) {
        return null;
    }

    /**
     * Retrieves the knowledge associated with this instance.
     *
     * @return The {@code Knowledge} object encapsulating the domain-specific constraints
     *         or background knowledge for the current instance.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge associated with this instance. The provided {@code Knowledge}
     * object is copied to ensure that modifications to the original object do not
     * affect this instance's knowledge.
     *
     * @param knowledge The {@code Knowledge} object encapsulating the domain-specific
     *                  constraints or background knowledge. This cannot be {@code null}.
     *                  If {@code null}, an appropriate exception or error handling
     *                  may be triggered (depending on implementation).
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge.copy();
    }
}
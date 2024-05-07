package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * SemBicScoreDeterministic is a class that implements the ScoreWrapper interface. It is used to calculate the Sem BIC
 * Score for deterministic models.
 */
//@edu.cmu.tetrad.annotation.Score(
//        name = "Sem BIC Score Deterministic",
//        command = "sem-bic-deterministic",
//        dataType = {DataType.Continuous, DataType.Covariance}
//)
public class SemBicScoreDeterministic implements ScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data set.
     */
    private DataModel dataSet;

    /**
     * Constructs a new instance of the SemBicScoreDeterministic.
     */
    public SemBicScoreDeterministic() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        boolean precomputeCovariances = parameters.getBoolean("precomputeCovariances");
        edu.cmu.tetrad.search.work_in_progress.SemBicScoreDeterministic semBicScore
                = new edu.cmu.tetrad.search.work_in_progress.SemBicScoreDeterministic(SimpleDataLoader.getCovarianceMatrix(dataSet,
                precomputeCovariances));
        semBicScore.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
        semBicScore.setDeterminismThreshold(parameters.getDouble("determinismThreshold"));
        return semBicScore;
    }

    /**
     * Returns a short description of the method.
     *
     * @return The description of the method.
     */
    @Override
    public String getDescription() {
        return "Sem BIC Score Deterministic";
    }

    /**
     * Retrieves the data type of the ScoreWrapper implementation.
     *
     * @return The data type of the ScoreWrapper implementation.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the list of parameters required for the getScore() method.
     *
     * @return A list of String names of parameters required for the getScore() method.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("penaltyDiscount");
        parameters.add("determinismThreshold");
        return parameters;
    }

    /**
     * Retrieves the Node with the given name from the data set.
     *
     * @param name the name of the variable
     * @return the Node with the specified name
     */
    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }

}

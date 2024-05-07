package edu.cmu.tetradapp.model;

import java.util.List;

/**
 * Methods common to regression models.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface RegressionModel {
    /**
     * <p>getVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<String> getVariableNames();

    /**
     * <p>getRegressorNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<String> getRegressorNames();

    /**
     * <p>setRegressorName.</p>
     *
     * @param predictors a {@link java.util.List} object
     */
    void setRegressorName(List<String> predictors);

    /**
     * <p>getTargetName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    String getTargetName();

    /**
     * <p>setTargetName.</p>
     *
     * @param target a {@link java.lang.String} object
     */
    void setTargetName(String target);
}

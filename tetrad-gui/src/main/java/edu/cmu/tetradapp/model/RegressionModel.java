package edu.cmu.tetradapp.model;

import java.util.List;

/**
 * Methods common to regression models.
 *
 * @author jdramsey
 */
public interface RegressionModel {
    List<String> getVariableNames();

    List<String> getRegressorNames();

    void setRegressorName(List<String> predictors);

    String getTargetName();

    void setTargetName(String target);
}

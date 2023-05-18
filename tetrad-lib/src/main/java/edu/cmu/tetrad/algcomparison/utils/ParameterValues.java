package edu.cmu.tetrad.algcomparison.utils;

import java.util.Map;

/**
 * Tags a class that can return values for parameters.
 *
 * @author josephramsey
 */
public interface ParameterValues {
    Map<String, Object> parameterValues();
}

package edu.cmu.tetrad.algcomparison.utils;

import java.util.Map;

/**
 * Tags a class that can return values for parameters.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface ParameterValues {
    /**
     * <p>parameterValues.</p>
     *
     * @return a {@link java.util.Map} object
     */
    Map<String, Object> parameterValues();
}

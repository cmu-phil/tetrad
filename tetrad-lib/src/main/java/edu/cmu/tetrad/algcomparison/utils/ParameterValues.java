package edu.cmu.tetrad.algcomparison.utils;

import java.util.Map;

/**
 * Tags a class that can return values for parameters.
 *
 * @author jdramsey
 */
public interface ParameterValues {
    Map<String, Object> paremeterValues();
}

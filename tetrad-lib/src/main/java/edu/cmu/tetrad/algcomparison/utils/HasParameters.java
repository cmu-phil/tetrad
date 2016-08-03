package edu.cmu.tetrad.algcomparison.utils;

import java.util.List;
import java.util.Map;

/**
 * Tags a gadget as having parameters
 */
public interface HasParameters {

    /**
     * @return Returns a map of parameter names to their defaults.
     */
    public Map<String, Object> getParameters();
}

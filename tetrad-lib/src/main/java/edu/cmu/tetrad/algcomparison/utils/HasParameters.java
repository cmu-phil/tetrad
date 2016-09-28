package edu.cmu.tetrad.algcomparison.utils;

import java.util.List;

/**
 * Tags a gadget as having parameters
 */
public interface HasParameters {

    /**
     * @return Returns the list of parameter names that are used. These are looked up
     * in ParamMap, so if they're not already defined they'll need to be defined there.
     */
    public List<String> getParameters();
}

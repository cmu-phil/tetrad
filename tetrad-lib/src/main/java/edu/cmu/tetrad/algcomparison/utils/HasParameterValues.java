package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.util.Parameters;

/**
 * Tags a gadget as having parameters values.
 */
public interface HasParameterValues {

    /**
     * @return Returns a list of additional parameter values.
     */
    public Parameters getParameterValues();
}

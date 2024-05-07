package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.util.Parameters;

/**
 * Tags a gadget as having parameters values.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface HasParameterValues {

    /**
     * <p>getParameterValues.</p>
     *
     * @return Returns a list of additional parameter values.
     */
    Parameters getParameterValues();
}

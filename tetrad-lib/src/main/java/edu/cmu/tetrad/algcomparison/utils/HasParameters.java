package edu.cmu.tetrad.algcomparison.utils;

import java.util.List;

/**
 * Tags a gadget as having parameters
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface HasParameters {

    /**
     * Returns the list of parameter names that are used. These are looked up in ParamMap, so if they're not
     *
     * @return Returns the list of parameter names that are used. These are looked up in ParamMap, so if they're not
     * already defined they'll need to be defined there.
     */
    List<String> getParameters();
}

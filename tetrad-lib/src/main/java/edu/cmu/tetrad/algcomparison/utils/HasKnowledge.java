package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.data.Knowledge;

/**
 * Stores a knowledge object.
 *
 * @author dmalinsky
 * @version $Id: $Id
 */
public interface HasKnowledge {

    /**
     * Returns a knowledge object.
     *
     * @return a knowledge object.
     */
    Knowledge getKnowledge();

    /**
     * Sets a knowledge object.
     *
     * @param knowledge a knowledge object.
     */
    void setKnowledge(Knowledge knowledge);
}

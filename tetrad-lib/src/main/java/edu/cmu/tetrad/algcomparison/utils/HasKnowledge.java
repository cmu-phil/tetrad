package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.data.Knowledge;

/**
 * Stores a knowledge object.
 *
 * @author dmalinsky
 */
public interface HasKnowledge {

    /**
     * @return a knowledge object.
     */
    Knowledge getKnowledge();

    /**
     * Sets a knowledge object.
     */
    void setKnowledge(Knowledge knowledge);
}

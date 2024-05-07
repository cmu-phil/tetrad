package edu.cmu.tetrad.algcomparison.utils;

import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;

/**
 * Tags an algorithm as using a score wrapper.
 * <p>
 * Author : Jeremy Espino MD Created  7/6/17 2:19 PM
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface UsesScoreWrapper {

    /**
     * Returns the score wrapper.
     *
     * @return the score wrapper.
     */
    ScoreWrapper getScoreWrapper();

    /**
     * Sets the score wrapper.
     *
     * @param score the score wrapper.
     */
    void setScoreWrapper(ScoreWrapper score);
}

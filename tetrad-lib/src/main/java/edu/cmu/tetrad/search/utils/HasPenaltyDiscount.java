package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.search.score.Score;

/**
 * Provides an interface for an algorithm can can get/set a value for penalty disoucnt.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface HasPenaltyDiscount extends Score {
    /**
     * <p>getPenaltyDiscount.</p>
     *
     * @return a double
     */
    double getPenaltyDiscount();

    /**
     * <p>setPenaltyDiscount.</p>
     *
     * @param penaltyDiscount a double
     */
    void setPenaltyDiscount(double penaltyDiscount);
}

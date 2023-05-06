package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.search.score.Score;

/**
 * Interface for an algorithm can can get/set a value for penalty disoucnt.
 *
 * @author josephramsey
 */
public interface HasPenaltyDiscount extends Score {
    void setPenaltyDiscount(double penaltyDiscount);

    double getPenaltyDiscount();
}

package edu.cmu.tetrad.search;

/**
 * Created by jdramsey on 2/21/16.
 */
public interface HasPenaltyDiscount extends Score {
    void setPenaltyDiscount(double penaltyDiscount);
    double getPenaltyDiscount();
}

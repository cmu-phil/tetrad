package edu.cmu.tetrad.util;

public interface EffectiveSampleSizeSettable {

    /**
     * Returns the effective sample size.
     */
    int getEffectiveSampleSize();

    /**
     * Sets the effective sample size, or -1 if the actual sample size should be used.
     *
     * @param nEff the effective sample size
     */
    void setEffectiveSampleSize(int nEff);
}
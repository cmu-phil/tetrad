package edu.cmu.tetrad.search;

/**
 * Gives an interface for classes where the effective sample size can be set by the user. This is useful when the sample
 * size of the data or covariance matrix is not the sample size that the class should use, as for example, in the case
 * of bootstrapping or subsampling.
 *
 * @author josephramsey
 */
public interface EffectiveSampleSizeSettable {

    /**
     * Sets the sample size if the sample size of the data or covariance matrix is not the sample size that should be
     * used by the class.
     *
     * @param sampleSize The sample size to use.
     */
    void setEffectiveSampleSize(int sampleSize);
}

package edu.cmu.tetrad.search;

/**
 * Gives an interface for classes where the sample size can be set by the user. This is useful when the sample size of the
 * data or covariance matrix is not the sample size that the test should use, as for example in the case of bootstrapping
 * or subsampling.
 *
 * @author josephramsey
 */
public interface SampleSizeSettable {

    /**
     * Sets the sample size if the sample size of the data or covariance matrix is not the sample size that should be
     * used by the class.
     *
     * @param sampleSize The sample size to use.
     */
    void setSampleSize(int sampleSize);
}

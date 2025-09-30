package edu.cmu.tetrad.search.score;

/**
 * Marker interface for scores that include an instance-specific component.
 *
 * Provides access to the weighting factor alpha.
 */
public interface InstanceSpecificScore extends Score {
    /**
     * @return the instance weight alpha
     */
    double getAlpha();

    /**
     * @param alpha the new instance weight
     */
    void setAlpha(double alpha);
}
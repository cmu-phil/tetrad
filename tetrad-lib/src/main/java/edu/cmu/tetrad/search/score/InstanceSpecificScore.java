package edu.cmu.tetrad.search.score;

/**
 * Marker interface for scores that include an instance-specific component.
 * <p>
 * Provides access to the weighting factor alpha.
 */
public interface InstanceSpecificScore extends Score {
    /**
     * Retrieves the instance-specific weighting factor alpha.
     *
     * @return The value of the weighting factor alpha, which represents the instance-specific component.
     */
    double getAlpha();

    /**
     * Sets the instance-specific weight alpha.
     *
     * @param alpha the weighting factor to be set, representing the instance-specific component
     */
    void setAlpha(double alpha);
}
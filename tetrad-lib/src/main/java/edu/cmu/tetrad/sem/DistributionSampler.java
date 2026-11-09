package edu.cmu.tetrad.sem;

import org.apache.commons.math3.distribution.RealDistribution;

/**
 * The {@code Sampler} class provides functionality for sampling values
 * from a specified probability distribution.
 *
 * This class wraps around an implementation of {@code RealDistribution}
 * to enable sampling from distributions such as normal, uniform, or others
 * supported by the provided distribution implementation.
 *
 * The {@code RealDistribution} instance used by this class is passed
 * during construction and must implement the sampling behavior according
 * to its distribution type.
 */
public class DistributionSampler implements Sampler {
    /**
     * Represents a probability distribution used for generating samples.
     *
     * This variable holds an instance of {@code RealDistribution}, which
     * provides methods to model and sample from a specific probability
     * distribution. Examples of distributions that may be encapsulated
     * include normal, uniform, exponential, and other continuous distributions
     * supported by the implementation.
     *
     * The distribution is immutable once initialized and is used to
     * produce random values based on its inherent properties.
     */
    private final RealDistribution distribution;

    /**
     * Constructs a {@code Sampler} instance with the specified probability distribution.
     *
     * @param distribution A {@code RealDistribution} instance representing the probability
     *                     distribution to sample from. The specified distribution provides
     *                     methods for generating random values based on its inherent
     *                     properties.
     */
    public DistributionSampler(RealDistribution distribution) {
        this.distribution = distribution;
    }

    /**
     * Retrieves the probability distribution encapsulated within this instance.
     *
     * This method returns the {@code RealDistribution} object that represents
     * the probability distribution used for generating samples or performing
     * probability-related computations. The distribution is defined during
     * the object's construction and remains immutable throughout its lifetime.
     *
     * @return The {@code RealDistribution} instance representing the probability distribution.
     */
    public RealDistribution getDistribution() {
        return distribution;
    }

    /**
     * Generates a single random sample from the probability distribution encapsulated
     * within the {@code Sampler} instance.
     *
     * This method uses the {@code sample()} method of the underlying {@code RealDistribution}
     * to produce a random value drawn from the specified distribution. The type of distribution
     * (e.g., normal, uniform, etc.) determines the characteristics of the sample.
     *
     * @return A random sample as a {@code double} value, generated according to the properties
     *         of the underlying probability distribution.
     */
    public double sample() {
        return distribution.sample();
    }
}
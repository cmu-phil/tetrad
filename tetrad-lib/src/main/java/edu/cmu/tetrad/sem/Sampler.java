package edu.cmu.tetrad.sem;

/**
 * Represents a generic sampling interface that provides a method
 * to obtain a sampled value. Implementations of this interface
 * are expected to define specific sampling strategies.
 */
public interface Sampler {
     double sample();
}
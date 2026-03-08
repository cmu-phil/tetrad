package edu.cmu.tetrad.sem;

/**
 * Represents a generic sampling interface that provides a method
 * to obtain a sampled value. Implementations of this interface
 * are expected to define specific sampling strategies.
 */
public interface Sampler {

     /**
      * Generates a sample value based on the implementation's defined sampling strategy.
      *
      * @return a sampled value as a double
      * @throws IllegalArgumentException if the sampling conditions are invalid
      */
     double sample() throws IllegalArgumentException;
}
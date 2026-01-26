package edu.cmu.tetrad.search.test.ffci_utils;

import org.ejml.simple.SimpleMatrix;

import java.util.Random;

public interface FeatureMap {
    // raw is n×d already standardized if desired
    SimpleMatrix compute(SimpleMatrix raw, FeatureSpec spec, Random rng);
    String id(); // stable identifier for caching keys
}


package edu.cmu.tetrad.search.test.ffci_utils;

import org.ejml.simple.SimpleMatrix;

public interface Residualizer {
    Residualization fit(SimpleMatrix fZ, double lambda);
    String id();
}


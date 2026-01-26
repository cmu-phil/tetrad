package edu.cmu.tetrad.search.test.ffci_utils;

import org.ejml.simple.SimpleMatrix;

public interface Residualization {
    // returns inv(A)*B (A = cov(fZ,fZ)+lambda I) without forming inv(A)
    SimpleMatrix solve(SimpleMatrix B); // A X = B
}

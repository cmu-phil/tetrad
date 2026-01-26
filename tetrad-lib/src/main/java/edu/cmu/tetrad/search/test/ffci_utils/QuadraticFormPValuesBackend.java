package edu.cmu.tetrad.search.test.ffci_utils;

public interface QuadraticFormPValuesBackend {
    double pValue(double stat, double[] eig, PValueMethod method);
}


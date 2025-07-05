package edu.cmu.tetrad.search;

@FunctionalInterface
public interface RawMarginalIndependenceTest {
    double computePValue(double[] x, double[] y) throws InterruptedException;
}

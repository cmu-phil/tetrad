package edu.cmu.tetrad.data;

/**
 * The type of the data set--continuous if all continuous variables, discrete if
 * all discrete variables; otherwise, mixed.
 *
 * @author josephramsey
 */
public enum DataType {
    Continuous, Discrete, Mixed, Graph, Covariance, All
}

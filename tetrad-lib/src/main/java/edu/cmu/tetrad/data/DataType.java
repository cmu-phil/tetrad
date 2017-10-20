package edu.cmu.tetrad.data;

/**
 * The type of the data set--continuous if all continuous variables, discrete if
 * all discrete variables; otherwise, mixed.
 *
 * @author jdramsey
 */
public enum DataType {
    Continuous, Discrete, Mixed, Graph, Covariance
}

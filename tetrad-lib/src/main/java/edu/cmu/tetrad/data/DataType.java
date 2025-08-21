package edu.cmu.tetrad.data;

/**
 * The type of the data set--continuous if all continuous variables, discrete if all discrete variables; otherwise,
 * mixed.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public enum DataType {

    /**
     * Continuous.
     */
    Continuous,

    /**
     * Discrete.
     */
    Discrete,

    /**
     * Mixed.
     */
    Mixed,

    /**
     * Graph.
     */
    Graph,

    /**
     * Covariance.
     */
    Covariance,

    /**
     * Defined over blocks in the data.
     */
    Blocks,

    /**
     * All.
     */
    All
}

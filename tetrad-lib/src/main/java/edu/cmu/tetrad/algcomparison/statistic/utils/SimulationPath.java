package edu.cmu.tetrad.algcomparison.statistic.utils;

/**
 * Some simulations may wish to implement this interface to specify a simulation path, which will be printed in the
 * output.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface SimulationPath {
    /**
     * <p>getPath.</p>
     *
     * @return a {@link java.lang.String} object
     */
    String getPath();
}

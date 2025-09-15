package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Interface for algorithm that optimize the fitting function of a SemIm model by adjusting its freeParameters in search
 * of a global maximum.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface SemOptimizer extends TetradSerializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * Optimizes the fitting function of a Sem by adjusting its parameter values.
     *
     * @param sem The unoptimized Sem (will be optimized).
     */
    void optimize(SemIm sem);

    /**
     * <p>getNumRestarts.</p>
     *
     * @return a int
     */
    int getNumRestarts();

    /**
     * <p>setNumRestarts.</p>
     *
     * @param numRestarts a int
     */
    void setNumRestarts(int numRestarts);
}






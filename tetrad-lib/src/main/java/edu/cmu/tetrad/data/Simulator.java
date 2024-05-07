package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Created by jdramsey on 12/22/15.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface Simulator extends TetradSerializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * Simulates data from the model associated with this object.
     *
     * @param sampleSize      the number of rows to simulate.
     * @param latentDataSaved if true, latent variables are saved in the data set.
     * @return the simulated data set.
     */
    DataSet simulateData(int sampleSize, boolean latentDataSaved);
}

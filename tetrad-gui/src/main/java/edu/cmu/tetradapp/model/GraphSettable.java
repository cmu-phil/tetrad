package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

/**
 * <p>GraphSettable interface.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface GraphSettable extends GraphSource {
    /**
     * <p>getParameters.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    Parameters getParameters();

    /**
     * <p>setGraph.</p>
     *
     * @param newValue a {@link edu.cmu.tetrad.graph.Graph} object
     */
    void setGraph(Graph newValue);

    /**
     * <p>getNumModels.</p>
     *
     * @return a int
     */
    int getNumModels();

    /**
     * <p>getModelSourceName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    String getModelSourceName();

    /**
     * <p>getModelIndex.</p>
     *
     * @return a int
     */
    int getModelIndex();

    /**
     * <p>setModelIndex.</p>
     *
     * @param index a int
     */
    void setModelIndex(int index);
}

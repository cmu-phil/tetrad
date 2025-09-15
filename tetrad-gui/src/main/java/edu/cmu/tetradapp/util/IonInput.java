package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.graph.Graph;

/**
 * Tags a class that can provide input to ION.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface IonInput {
    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    Graph getGraph();
}




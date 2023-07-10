package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

/**
 * @author josephramsey
 */
public interface GraphSettable extends GraphSource {
    Parameters getParameters();

    void setGraph(Graph newValue);

    int getNumModels();

    String getModelSourceName();

    int getModelIndex();

    void setModelIndex(int index);
}

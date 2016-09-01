package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

/**
 * @author jdramsey
 */
public interface GraphSettable {
    Graph getGraph();

    Parameters getParameters();

    void setGraph(Graph newValue);
}

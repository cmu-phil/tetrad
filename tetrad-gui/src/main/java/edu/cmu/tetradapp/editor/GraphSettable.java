package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.CompletedPatternWrapper;

/**
 * Created by jdramsey on 8/31/16.
 */
public interface GraphSettable {
    CompletedPatternWrapper getGraph();

    Parameters getParameters();

    void setGraph(Graph newValue);
}

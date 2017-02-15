package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ScoredGraph;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;

/**
 * Created by jdramsey on 2/22/16.
 */
public interface IFgesRunner {
    FgesRunner.Type getType();

    List<ScoredGraph> getTopGraphs();

    Parameters getParams();

    Graph getSourceGraph();

    DataModel getDataModel();
}

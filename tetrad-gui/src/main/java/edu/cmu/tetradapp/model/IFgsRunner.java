package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.ScoredGraph;

import java.util.List;

/**
 * Created by jdramsey on 2/22/16.
 */
public interface IFgsRunner {
    FgsRunner.Type getType();

    List<ScoredGraph> getTopGraphs();

    SearchParams getParams();

    Graph getSourceGraph();

    DataModel getDataModel();
}

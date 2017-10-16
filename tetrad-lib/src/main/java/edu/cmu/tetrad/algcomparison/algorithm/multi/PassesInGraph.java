package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;

public interface PassesInGraph {
    Graph search(List<DataModel> dataSet, Parameters parameters, Graph graph);
}

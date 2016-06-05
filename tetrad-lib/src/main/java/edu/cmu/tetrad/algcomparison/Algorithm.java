package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public interface Algorithm {
    Graph search(DataSet dataSet, Map<String, Number> parameters);

    String getName();

    Graph getComparisonGraph(Graph dag);

    String getDescription();
}

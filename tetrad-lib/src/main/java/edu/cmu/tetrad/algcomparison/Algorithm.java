package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public interface Algorithm {
    public enum DataType {Continuous, Discrete, Mixed}

    Graph search(DataSet dataSet, Parameters parameters);

    Graph getComparisonGraph(Graph dag);

    String getDescription();

    DataType getDataType();
}

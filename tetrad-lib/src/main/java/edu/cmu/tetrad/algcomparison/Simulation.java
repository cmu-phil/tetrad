package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

/**
 * Created by jdramsey on 6/4/16.
 */
public interface Simulation {
    Graph getTrueGraph();
    int getNumDataSets();
    DataSet getDataSet(int i, Parameters parameters);
    DataType getDataType(Parameters parameters);
    String getDescription();
}

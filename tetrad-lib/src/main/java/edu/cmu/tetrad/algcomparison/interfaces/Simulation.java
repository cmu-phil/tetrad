package edu.cmu.tetrad.algcomparison.interfaces;

import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

/**
 * Created by jdramsey on 6/4/16.
 */
public interface Simulation {
    int getNumDataSets();
    Graph getTrueGraph(int index);
    DataSet getDataSet(int i);
    DataType getDataType(Parameters parameters);
    String getDescription();
}

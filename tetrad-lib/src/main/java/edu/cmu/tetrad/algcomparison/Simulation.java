package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

/**
 * Created by jdramsey on 6/4/16.
 */
public interface Simulation {
    DataSet getDataSet(int i, Parameters parameters);

    Graph getDag();

    String toString();

    boolean isMixed();

    int getNumDataSets();

    DataType getDataType(Parameters parameters);
}

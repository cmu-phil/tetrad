package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

import java.util.Map;

/**
 * Created by jdramsey on 6/4/16.
 */
public interface Simulation {
    DataSet getDataSet(int i, Parameters parameters);

    Graph getDag();

    String toString();

    boolean isMixed();

    int getNumDataSets();
}

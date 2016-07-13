package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

/**
 * The interface that simulations must implement.
 * @author jdramsey
 */
public interface Simulation {

    /**
     * @return The numeer of data sets to simulate.
     */
    int getNumDataSets();

    /**
     * @param index The index of the desired true graph.
     * @return That graph.
     */
    Graph getTrueGraph(int index);

    /**
     * @param i The index of the desired simulated data set.
     * @return That data set.
     */
    DataSet getDataSet(int i);

    /**
     * @return Returns the type of the data, continuous, discrete or mixed.
     */
    DataType getDataType();

    /**
     * @return Returns a one-line description of the simulation, to be printed
     * at the beginning of the report.
     */
    String getDescription();
}

package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.utils.HasParameters;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.List;

/**
 * The interface that simulations must implement.
 *
 * @author jdramsey
 */
public interface Simulation extends HasParameters, TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * Creates a data set and simulates data.
     */
    void createData(Parameters parameters);

    /**
     * @return The number of data sets to simulate.
     */
    int getNumDataModels();

    /**
     * @return That graph.
     * @param index The index of the desired true graph.
     */
    Graph getTrueGraph(int index);

    /**
     * @param index The index of the desired simulated data set.
     * @return That data set.
     */
    DataModel getDataModel(int index);

    /**
     * @return Returns the type of the data, continuous, discrete or mixed.
     */
    DataType getDataType();

    /**
     * @return Returns a one-line description of the simulation, to be printed
     * at the beginning of the report.
     */
    String getDescription();

    /**
     * @return Returns the parameters used in the simulation. These are the
     * parameters whose values can be varied.
     */
    List<String> getParameters();
}

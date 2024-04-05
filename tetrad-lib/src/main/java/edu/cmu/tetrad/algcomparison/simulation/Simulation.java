package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.utils.HasParameters;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.List;

/**
 * The interface that simulations must implement.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface Simulation extends HasParameters, TetradSerializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * Creates a data set and simulates data.
     *
     * @param parameters The parameters to use in the simulation.
     * @param newModel   If true, a new model is created. If false, the model is reused.
     */
    void createData(Parameters parameters, boolean newModel);

    /**
     * Returns the number of data models.
     *
     * @return The number of data sets to simulate.
     */
    int getNumDataModels();

    /**
     * Returns the true graph at the given index.
     *
     * @param index The index of the desired true graph.
     * @return That graph.
     */
    Graph getTrueGraph(int index);

    /**
     * Returns the number of data sets to simulate.
     *
     * @param index The index of the desired simulated data set.
     * @return That data set.
     */
    DataModel getDataModel(int index);

    /**
     * Returns the data type of the data.
     *
     * @return Returns the type of the data, continuous, discrete or mixed.
     */
    DataType getDataType();

    /**
     * Returns the description of the simulation.
     *
     * @return Returns a one-line description of the simulation, to be printed at the beginning of the report.
     */
    String getDescription();

    /**
     * Returns the short name of the simulation.
     *
     * @return The short name of the simulation.
     */
    default String getShortName() {
        return getClass().getSimpleName();
    }

    /**
     * Returns the list of parameters used in the simulation.
     *
     * @return Returns the parameters used in the simulation. These are the parameters whose values can be varied.
     */
    List<String> getParameters();

    /**
     * Retrieves the class of a random graph for the simulation.
     *
     * @return The class of a random graph for the simulation.
     */
    Class<? extends edu.cmu.tetrad.algcomparison.graph.RandomGraph> getRandomGraphClass();

    /**
     * Returns the class of the simulation. This method is used to retrieve the class
     * of a simulation based on the selected simulations in the model.
     *
     * @return The class of the simulation.
     */
    Class<? extends edu.cmu.tetrad.algcomparison.simulation.Simulation> getSimulationClass();
}

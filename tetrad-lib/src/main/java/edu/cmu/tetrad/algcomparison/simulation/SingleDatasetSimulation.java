///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;

/**
 * A {@link Simulation} implementation that returns a single supplied data set.
 *
 * @author josephramsey
 */
public class SingleDatasetSimulation implements Simulation {

    /**
     * The {@code dataSet} variable represents a single supplied data set.
     */
    private final DataSet dataSet;

    /**
     * A {@link Simulation} implementation that returns a single supplied data set.
     *
     * @param dataSet The data set to return.
     */
    public SingleDatasetSimulation(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    /**
     * Creates a new data model for the simulation.
     *
     * @param parameters the parameters for creating the data model
     * @param newModel   a flag indicating whether to create a new model
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
        // Do nothing, since the data set is already supplied.
    }

    /**
     * Returns the number of data models (1).
     *
     * @return The number of data models.
     */
    @Override
    public int getNumDataModels() {
        return 1;
    }

    /**
     * Gets the true graph for the simulation at the specified index.
     *
     * @param index The index of the desired true graph; must be 0.
     * @return null, since there is no true graph for this simulation.
     */
    @Override
    public Graph getTrueGraph(int index) {
        if (index != 0) throw new IllegalArgumentException("This simulation is for a single supplied " +
                                                           "dataset only.");
        return null;
    }

    /**
     * Retrieves the data model at the specified index from this simulation.
     *
     * @param index The index of the desired data model (must be 0).
     * @return The data model at the specified index.
     * @throws IllegalArgumentException if the index is not 0.
     */
    @Override
    public DataModel getDataModel(int index) {
        if (index != 0) throw new IllegalArgumentException("This simulation is for a single supplied " +
                                                           "dataset only.");
        return dataSet;
    }

    /**
     * Retrieves the data type of the data set.
     *
     * @return The data type of the data set, which can be continuous, discrete, or mixed.
     * @throws IllegalStateException If the data type is unknown.
     */
    @Override
    public DataType getDataType() {
        if (dataSet.isContinuous()) {
            return DataType.Continuous;
        } else if (dataSet.isDiscrete()) {
            return DataType.Discrete;
        } else if (dataSet.isMixed()) {
            return DataType.Mixed;
        } else {
            throw new IllegalStateException("Unknown data type.");
        }
    }

    /**
     * Returns the description of the simulation.
     *
     * @return Returns a one-line description of the simulation, to be printed at the beginning of the report.
     */
    @Override
    public String getDescription() {
        return "This \"simulation\" returns a single supplied data set. It is of type " + getDataType();
    }

    /**
     * Returns the list of parameters used in the simulation.
     *
     * @return The list of parameters used in the simulation.
     */
    @Override
    public List<String> getParameters() {
        return List.of();
    }

    /**
     * Returns null, as there is not random graph for this simulation.
     *
     * @return null.
     */
    @Override
    public Class<? extends RandomGraph> getRandomGraphClass() {
        return null;
    }

    /**
     * Retrieves the class of the simulation. This method is used to retrieve the class of a simulation based on the
     * selected simulations in the model.
     *
     * @return The class of the simulation.
     */
    @Override
    public Class<? extends Simulation> getSimulationClass() {
        return getClass();
    }
}


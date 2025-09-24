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

import edu.cmu.tetrad.algcomparison.utils.HasParameters;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
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
    @Serial
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
     * Returns the class of the simulation. This method is used to retrieve the class of a simulation based on the
     * selected simulations in the model.
     *
     * @return The class of the simulation.
     */
    Class<? extends edu.cmu.tetrad.algcomparison.simulation.Simulation> getSimulationClass();
}


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
import edu.cmu.tetrad.data.Knowledge;

/**
 * Jun 4, 2019 5:21:45 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public final class SimulationUtils {

    private SimulationUtils() {
    }

    /**
     * Creates a simulation instance based on the specified simulation type and random graph. This method
     * serves as a shorthand for calling the full create method with a null knowledge parameter.
     *
     * @param simulationType the type of simulation to create, which must correspond to a valid simulation type
     *                       such as SimulationTypes.BAYS_NET, SimulationTypes.STRUCTURAL_EQUATION_MODEL, etc.
     * @param randomGraph    the random graph to base the simulation on, representing the structure required
     *                       for the simulation.
     * @return the created simulation instance corresponding to the specified simulation type and random graph.
     * @throws IllegalArgumentException if the provided simulation type is unknown or unsupported.
     */
    public static Simulation create(String simulationType, RandomGraph randomGraph) {
        return create(simulationType, randomGraph, null);
    }

    /**
     * Creates a simulation instance based on the specified simulation type, random graph, and knowledge.
     *
     * @param simulationType the type of simulation to create. Must be one of:
     *                       SimulationTypes.BAYS_NET,
     *                       SimulationTypes.STRUCTURAL_EQUATION_MODEL,
     *                       SimulationTypes.GENERAL_ADDITIVE_MODEL,
     *                       SimulationTypes.GENERAL_NOISE_SEM,
     *                       SimulationTypes.ADDITIVE_NOISE_SEM,
     *                       SimulationTypes.GENERAL_STRUCTURAL_EQUATION_MODEL,
     *                       SimulationTypes.LEE_AND_HASTIE,
     *                       SimulationTypes.CONDITIONAL_GAUSSIAN,
     *                       or SimulationTypes.TIME_SERIES.
     * @param randomGraph    the random graph to base the simulation on.
     * @param knowledge      additional knowledge that may be used by certain simulation types, such as
     *                       SimulationTypes.TIME_SERIES. Can be null for types that do not require it.
     * @return the created simulation instance corresponding to the specified simulation type.
     * @throws IllegalArgumentException if the specified simulation type is unknown or unsupported.
     */
    public static Simulation create(String simulationType, RandomGraph randomGraph, Knowledge knowledge) {
        return switch (simulationType) {
            case SimulationTypes.BAYS_NET -> new BayesNetSimulation(randomGraph);
            case SimulationTypes.STRUCTURAL_EQUATION_MODEL -> new SemSimulation(randomGraph);
            case SimulationTypes.GENERAL_ADDITIVE_MODEL -> new GeneralAdditiveModel(randomGraph);
            case SimulationTypes.GENERAL_NOISE_SEM -> new GeneralNoiseSimulation(randomGraph);
            case SimulationTypes.ADDITIVE_NOISE_SEM -> new AdditiveNoiseSimulation(randomGraph);
            case SimulationTypes.DESIGNED_EXPERIMENT -> new DesignedExperimentSimulation(randomGraph);
            case SimulationTypes.OBSERVATIONAL_STUDY -> new ObservationalStudySimulation(randomGraph);
            case SimulationTypes.GENERAL_STRUCTURAL_EQUATION_MODEL -> new GeneralSemSimulationSpecial1(randomGraph);
            case SimulationTypes.LEE_AND_HASTIE -> new LeeHastieSimulation(randomGraph);
            case SimulationTypes.CONDITIONAL_GAUSSIAN -> new ConditionalGaussianSimulation(randomGraph);
            case SimulationTypes.TIME_SERIES -> new TimeSeriesSemSimulation(randomGraph, knowledge);
            default -> throw new IllegalArgumentException(
                    String.format("Unknown simulation type %s.", simulationType));
        };
    }

}


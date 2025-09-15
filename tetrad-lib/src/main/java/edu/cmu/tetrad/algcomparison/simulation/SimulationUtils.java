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
     * <p>create.</p>
     *
     * @param simulationType a {@link java.lang.String} object
     * @param randomGraph    a {@link edu.cmu.tetrad.algcomparison.graph.RandomGraph} object
     * @return a {@link edu.cmu.tetrad.algcomparison.simulation.Simulation} object
     */
    public static Simulation create(String simulationType, RandomGraph randomGraph) {
        return switch (simulationType) {
            case SimulationTypes.BAYS_NET -> new BayesNetSimulation(randomGraph);
            case SimulationTypes.STRUCTURAL_EQUATION_MODEL -> new SemSimulation(randomGraph);
            case SimulationTypes.LINEAR_FISHER_MODEL -> new LinearFisherModel(randomGraph);
            case SimulationTypes.GAUSSIAN_PROCESS_STRUCTURAL_EQUATION_MODEL -> new GpSemSimulation(randomGraph);
            case SimulationTypes.NONLINEAR_ADDITIVE_NOISE_MODEL -> new NonlinearAdditiveNoiseModel(randomGraph);
            case SimulationTypes.POST_NONLINEAR_MODEL -> new PostnonlinearCausalModel(randomGraph);
            case SimulationTypes.CAUSAL_PERCEPTRON_NETWORK -> new CausalPerceptronNetwork(randomGraph);
            case SimulationTypes.GENERAL_STRUCTURAL_EQUATION_MODEL -> new GeneralSemSimulationSpecial1(randomGraph);
            case SimulationTypes.LEE_AND_HASTIE -> new LeeHastieSimulation(randomGraph);
            case SimulationTypes.CONDITIONAL_GAUSSIAN -> new ConditionalGaussianSimulation(randomGraph);
            case SimulationTypes.TIME_SERIES -> new TimeSeriesSemSimulation(randomGraph);
            default -> throw new IllegalArgumentException(
                    String.format("Unknown simulation type %s.", simulationType));


        };
    }

}


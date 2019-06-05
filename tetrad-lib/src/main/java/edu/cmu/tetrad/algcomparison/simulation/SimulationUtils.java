/*
 * Copyright (C) 2019 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;

/**
 *
 * Jun 4, 2019 5:21:45 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public final class SimulationUtils {

    private SimulationUtils() {
    }

    public static Simulation create(String simulationType, RandomGraph randomGraph) {
        switch (simulationType) {
            case SimulationTypes.BAYS_NET:
                return new BayesNetSimulation(randomGraph);
            case SimulationTypes.STRUCTURAL_EQUATION_MODEL:
                return new SemSimulation(randomGraph);
            case SimulationTypes.LINEAR_FISHER_MODEL:
                return new LinearFisherModel(randomGraph);
            case SimulationTypes.GENERAL_STRUCTURAL_EQUATION_MODEL:
                return new GeneralSemSimulationSpecial1(randomGraph);
            case SimulationTypes.LEE_AND_HASTIE:
                return new LeeHastieSimulation(randomGraph);
            case SimulationTypes.CONDITIONAL_GAUSSIAN:
                return new ConditionalGaussianSimulation(randomGraph);
            case SimulationTypes.TIME_SERIES:
                return new TimeSeriesSemSimulation(randomGraph);
            case SimulationTypes.BOOLEAN_GLASS_SIMULATION:
                return new BooleanGlassSimulation(randomGraph);
            default:
                throw new IllegalArgumentException(
                        String.format("Unknown simulation type %s.", simulationType));
        }
    }

}

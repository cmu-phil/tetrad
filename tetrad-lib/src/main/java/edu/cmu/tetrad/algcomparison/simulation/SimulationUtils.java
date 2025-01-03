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
        switch (simulationType) {
            case SimulationTypes.BAYS_NET:
                return new BayesNetSimulation(randomGraph);
            case SimulationTypes.STRUCTURAL_EQUATION_MODEL:
                return new SemSimulation(randomGraph);
            case SimulationTypes.NON_LINEAR_STRUCTURAL_EQUATION_MODEL:
                return new NLSemSimulation(randomGraph);
            case SimulationTypes.NONLINEAR_ADDITIVE_MODEL:
                return new NonlinearAdditiveModel(randomGraph);
            case SimulationTypes.GENERAL_STRUCTURAL_EQUATION_MODEL:
                return new GeneralSemSimulationSpecial1(randomGraph);
            case SimulationTypes.LEE_AND_HASTIE:
                return new LeeHastieSimulation(randomGraph);
            case SimulationTypes.CONDITIONAL_GAUSSIAN:
                return new ConditionalGaussianSimulation(randomGraph);
            case SimulationTypes.TIME_SERIES:
                return new TimeSeriesSemSimulation(randomGraph);
            default:
                throw new IllegalArgumentException(
                        String.format("Unknown simulation type %s.", simulationType));
        }
    }

}

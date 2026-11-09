/// ////////////////////////////////////////////////////////////////////////////
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
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.List;

/**
 * A linear SEM simulation whose exogenous noises are drawn independently from the distribution given by the
 * customNoiseExpression parameter (default Exp(1)) rather than from Gaussians. This is the data-generating
 * model assumed by LiNGAM-style algorithms.
 * <p>
 * The noise distribution is fixed to the custom expression; the engine-level switch between Gaussian and
 * custom noise is not a user option for this simulation (for Gaussian noises, use {@link SemSimulation}).
 * Because the noises are drawn directly from the expression, the error variance and error covariance bounds
 * of the Gaussian simulation do not apply and are not listed: the noise scale is whatever the expression
 * gives, and any correlated errors in the graph are ignored in this simulation.
 */
public class NonGaussianSemSimulation extends SemSimulation {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a NonGaussianSemSimulation object with the given RandomGraph object.
     *
     * @param graph the RandomGraph object used for simulation.
     * @throws NullPointerException if graph is null.
     */
    public NonGaussianSemSimulation(RandomGraph graph) {
        super(graph);
    }

    /**
     * Returns the description of the simulation.
     *
     * @return a short, one-line description of the simulation.
     */
    @Override
    public String getDescription() {
        return "Linear Non-Gaussian SEM simulation using " + getRandomGraph().getDescription();
    }

    /**
     * Returns the short name of the simulation.
     *
     * @return The short name of the simulation.
     */
    @Override
    public String getShortName() {
        return "Linear Non-Gaussian SEM Simulation";
    }

    /**
     * Retrieves the parameters required for the simulation. These are the Linear Gaussian SEM's parameters
     * minus the error variance and error covariance bounds, which the non-Gaussian noise path does not use,
     * plus the noise distribution expression.
     *
     * @return A list of String names representing the parameters.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = super.getParameters();

        // In the non-Gaussian path each noise is an independent draw from the expression, so the Gaussian
        // error variance bounds and error covariance settings have no effect; listing them would be
        // misleading.
        parameters.remove(Params.VAR_LOW);
        parameters.remove(Params.VAR_HIGH);
        parameters.remove(Params.COV_LOW);
        parameters.remove(Params.COV_HIGH);
        parameters.remove(Params.COV_SYMMETRIC);

        if (!parameters.contains(Params.CUSTOM_NOISE_EXPRESSION)) {
            parameters.add(Params.CUSTOM_NOISE_EXPRESSION);
        }

        return parameters;
    }

    /**
     * The exogenous noise family this simulation forces: 2 = independent draws from the customNoiseExpression
     * distribution.
     *
     * @return the forced noise option.
     */
    @Override
    protected int noiseOption() {
        return 2;
    }
}

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

package edu.cmu.tetrad.algcomparison.examples;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.LeeHastieSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

/**
 * An example script to save out data files and graphs from a simulation.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Save {


    /**
     * Constructs a new instance of the Save.
     */
    public Save() {

    }

    /**
     * <p>main.</p>
     *
     * @param args a {@link java.lang.String} object
     */
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.set(Params.NUM_RUNS, 10);
        parameters.set(Params.NUM_MEASURES, 50, 100);
        parameters.set(Params.AVG_DEGREE, 4);
        parameters.set(Params.SAMPLE_SIZE, 100, 500);

        parameters.set(Params.NUM_CATEGORIES, 3);
        parameters.set(Params.PERCENT_DISCRETE, 50);
        parameters.set(Params.DIFFERENT_GRAPHS, true);

        Simulation simulation = new LeeHastieSimulation(new RandomForward());
        Comparison comparison = new Comparison();
        comparison.setSaveData(true);
        comparison.setSaveGraphs(true);
        comparison.saveToFiles("comparison", simulation, parameters);
    }
}






///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.examples;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.ConditionalGaussianSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.util.Parameters;

/**
 * An example script to save out data files and graphs from a simulation.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SaveDGSimulations {

    /**
     * Constructs a new instance of the SaveDGSimulations.
     */
    public SaveDGSimulations() {
    }

    /**
     * <p>main.</p>
     *
     * @param args a {@link java.lang.String} object
     */
    public static void main(String... args) {
        Parameters parameters = new Parameters();
        parameters.set("numRuns", 30);
        parameters.set("numMeasures", 100, 500, 1000);
        parameters.set("avgDegree", 4);
        parameters.set("maxDegree", 100);
        parameters.set("numCategories", 3);
        parameters.set("minCategories", 2);
        parameters.set("maxCategories", 4);
        parameters.set("sampleSize", 1000);
        parameters.set("percentDiscrete", 50);
        parameters.set("differentGraphs", true);

        Simulation simulation = new ConditionalGaussianSimulation(new RandomForward());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.saveToFiles("comparison-CG-measures", simulation, parameters);
    }
}





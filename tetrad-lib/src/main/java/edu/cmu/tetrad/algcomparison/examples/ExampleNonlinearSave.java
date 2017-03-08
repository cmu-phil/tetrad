///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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
import edu.cmu.tetrad.algcomparison.simulation.*;
import edu.cmu.tetrad.util.Parameters;

/**
 * An example script to save out data files and graphs from a simulation.
 *
 * @author jdramsey
 */
public class ExampleNonlinearSave {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 100);
        parameters.set("numMeasures", 20);
        parameters.set("avgDegree", 3);
        parameters.set("sampleSize", 500);
        parameters.set("percentDiscrete", 0);
        parameters.set("minCategories", 2);
        parameters.set("maxCategories", 5);
        parameters.set("differentGraphs",true);

        parameters.set("interceptLow", 0);
        parameters.set("interceptHigh", 1);
        parameters.set("contiuousInfluence", 0.5);
        parameters.set("linearLow", 0.5);
        parameters.set("linearHigh", 1.0);
        parameters.set("quadraticLow", 0.5);
        parameters.set("quadraticHigh", 1.0);
        parameters.set("cubicLow", 0.2);
        parameters.set("cubicHigh", 0.3);
        parameters.set("varLow", 1);
        parameters.set("varHigh", 1);
        parameters.set("betaLow", 5);
        parameters.set("betaHigh", 8);
        parameters.set("gammaLow", 1.0);
        parameters.set("gammaHigh", 1.5);

        Simulation simulation = new LinearSineSimulation(new RandomForward());
        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.saveToFiles("comparison", simulation, parameters);
    }
}





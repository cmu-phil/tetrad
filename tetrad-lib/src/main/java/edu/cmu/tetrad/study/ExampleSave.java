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

package edu.cmu.tetrad.study;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;

import java.text.DecimalFormat;

/**
 * An example script to save out data files and graphs from a simulation.
 *
 * @author jdramsey
 */
public class ExampleSave {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 10);
        parameters.set("numMeasures", 50, 100, 500);
        parameters.set("avgDegree", 2, 4, 6);
        parameters.set("sampleSize", 100, 500, 1000);

        parameters.set("differentGraphs", true);
        parameters.set("maxDegree", 100);
        parameters.set("maxIndegree", 100);
        parameters.set("maxOutdegree", 100);
        parameters.set("connected", false);
        parameters.set("coefLow", 0.2);
        parameters.set("coefHigh", 0.9);
        parameters.set("coefSymmetric", true);
        parameters.set("varLow", 1);
        parameters.set("varHigh", 3);
        parameters.set("randomizeColumns", true);

        NumberFormatUtil.getInstance().setNumberFormat(new DecimalFormat("0.000000"));

        Simulation simulation = new SemSimulation(new RandomForward());
        Comparison comparison = new Comparison();
        comparison.saveToFiles("/Users/user/comparison-data/condition_2", simulation, parameters);

    }
}






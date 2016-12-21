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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.LeeHastieSimulation;
import edu.cmu.tetrad.util.Parameters;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class TestGenerateMixedData {

    public void test1() {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 100);
        parameters.set("numMeasures", 100);
        parameters.set("avgDegree", 4);
        parameters.set("sampleSize", 5000);

        parameters.set("maxDegree", 8);

        parameters.set("minCategories", 2);
        parameters.set("maxCategories", 5);
        parameters.set("percentDiscrete", 50);

        parameters.set("intervalBetweenRecordings", 20);

        parameters.set("varLow", 1.);
        parameters.set("varHigh", 3.);
        parameters.set("coefLow", .1);
        parameters.set("coefHigh", 1.5);
        parameters.set("coefSymmetric", true);
        parameters.set("meanLow", -1);
        parameters.set("meanHigh", 1);

        final LeeHastieSimulation simulation = new LeeHastieSimulation(new RandomForward());
        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(false);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(false);
        comparison.setSaveGraphs(true);

        comparison.setTabDelimitedTables(true);

        comparison.saveToFiles("mixed.lee.hastie.avg.degree.4", simulation, parameters);
    }

    public static void main(String...args) {
        new TestGenerateMixedData().test1();
    }
}





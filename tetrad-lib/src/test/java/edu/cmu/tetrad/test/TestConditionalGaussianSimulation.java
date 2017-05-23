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
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.score.ConditionalGaussianBicScore;
import edu.cmu.tetrad.algcomparison.simulation.ConditionalGaussianSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class TestConditionalGaussianSimulation {

    public void testBryan(String... args) {
        Parameters parameters = new Parameters();

        parameters.set("numCategoriesToDiscretize", 5);

        parameters.set("numRuns", 10);
        parameters.set("numMeasures", 100);
        parameters.set("avgDegree", 2);
        parameters.set("sampleSize", 10000);
        parameters.set("minCategories", 2);
        parameters.set("maxCategories", 5);
        parameters.set("percentDiscrete", 50);
        parameters.set("differentGraphs", true);

        parameters.set("maxDegree", 5);
        parameters.set("maxIndegree", 5);
        parameters.set("maxOutdegree", 5);

        parameters.set("structurePrior", 1);
        parameters.set("fDegree", -1);
        parameters.set("discretize", true);
//        parameters.set("discretize", true, false);

        Statistics statistics = new Statistics();

        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedTime());

        statistics.setWeight("AHP", 1.0);
        statistics.setWeight("AHR", 1.0);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new Fges(new ConditionalGaussianBicScore()));

        Simulations simulations = new Simulations();

        simulations.add(new ConditionalGaussianSimulation(new RandomForward()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(true);
        comparison.setShowUtilities(true);
        comparison.setParallelized(false);

        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
    }

    public static void main(String... args) {
        new TestConditionalGaussianSimulation().testBryan();
    }
}





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
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.score.ConditionalGaussianBicScore;
import edu.cmu.tetrad.algcomparison.score.DegenerateGaussianBicScore;
import edu.cmu.tetrad.algcomparison.simulation.ConditionalGaussianSimulation;
import edu.cmu.tetrad.algcomparison.simulation.LeeHastieSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;

/**
 * Test the degenerate Gaussian score.
 *
 * @author bandrews
 * @version $Id: $Id
 */
public class TestDegenerateGaussian {

    /**
     * Initializes a new instance of the TestDegenerateGaussian class.
     */
    public TestDegenerateGaussian() {
    }

    /**
     * The main method initializes various parameters, statistics, algorithms, simulations, and a comparison object. It
     * sets the values for the parameters and adds statistics, algorithms, and simulations to the respective objects.
     * Lastly, it calls the compareFromSimulations method of the comparison object to perform a comparison.
     *
     * @param args the command-line arguments
     */
    public static void main(String... args) {
        Parameters parameters = new Parameters();
        parameters.set("numRuns", 3);
        parameters.set("numMeasures", 100);
        parameters.set("avgDegree", 2, 4);
        parameters.set("maxDegree", 100);
        parameters.set("numCategories", 3);
        parameters.set("minCategories", 2);
        parameters.set("maxCategories", 4);
        parameters.set("differentGraphs", true);

        parameters.set("sampleSize", 1000);
        parameters.set("percentDiscrete", 50);

        parameters.set("penaltyDiscount", 1);
        parameters.set("structurePrior", 1); // overloading parameters!
        parameters.set("samplePrior", 1);
        parameters.set("discretize", false);

        parameters.set("alpha", 1e-2, 1e-3, 1e-4);
        parameters.set("mgmParam1", 0.2);
        parameters.set("mgmParam2", 0.2);
        parameters.set("mgmParam3", 0.2);

        parameters.set("verbose", false);

        Statistics statistics = new Statistics();

        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedCpuTime());


        Algorithms algorithms = new Algorithms();
        algorithms.add(new Fges(new ConditionalGaussianBicScore()));
        algorithms.add(new Fges(new DegenerateGaussianBicScore()));

        Simulations simulations = new Simulations();
        simulations.add(new ConditionalGaussianSimulation(new RandomForward()));
        simulations.add(new LeeHastieSimulation(new RandomForward()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);

        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
    }
}





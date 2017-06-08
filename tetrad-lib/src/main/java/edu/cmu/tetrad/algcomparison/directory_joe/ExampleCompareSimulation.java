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

package edu.cmu.tetrad.algcomparison.directory_joe;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.*;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.independence.BDeuTest;
import edu.cmu.tetrad.algcomparison.independence.ChiSquare;
import edu.cmu.tetrad.algcomparison.independence.DiscreteBicTest;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.DiscreteBicScore;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.BayesNetSimulation;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class ExampleCompareSimulation {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.set("minCategories", 4);
        parameters.set("maxCategories", 4);

        parameters.set("numRuns", 50);
        parameters.set("differentGraphs", true);
        parameters.set("sampleSize", 1000);

        parameters.set("numMeasures", 20);
        parameters.set("numLatents", 0);
        parameters.set("avgDegree", 2);
        parameters.set("maxDegree", 100);
        parameters.set("maxIndegree", 100);
        parameters.set("maxOutdegree", 100);
        parameters.set("connected", false);

        parameters.set("sampleSize", 500);

        parameters.set("alpha", .01);
        parameters.set("depth", -1);

        parameters.set("samplePrior", 1);
        parameters.set("structurePrior", 3);

        parameters.set("penaltyDiscount", 1);

        Statistics statistics = new Statistics();

        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new MathewsCorrAdj());
        statistics.add(new MathewsCorrArrow());
        statistics.add(new F1Adj());
        statistics.add(new F1Arrow());
        statistics.add(new SHD());
        statistics.add(new ElapsedTime());

        statistics.setWeight("AP", 1.0);
        statistics.setWeight("AR", 0.5);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new Pc(new ChiSquare()));
//        algorithms.add(new Pc(new DiscreteBicTest()));
        algorithms.add(new Pc(new BDeuTest()));
        algorithms.add(new PcStable(new ChiSquare()));
//        algorithms.add(new PcStable(new DiscreteBicTest()));
        algorithms.add(new PcStable(new BDeuTest()));
        algorithms.add(new Cpc(new ChiSquare()));
//        algorithms.add(new Cpc(new DiscreteBicTest()));
        algorithms.add(new Cpc(new BDeuTest()));
        algorithms.add(new CpcStable(new ChiSquare()));
//        algorithms.add(new CpcStable(new DiscreteBicTest()));
        algorithms.add(new CpcStable(new BDeuTest()));
        algorithms.add(new PcMax(new ChiSquare(), false));
//        algorithms.add(new PcMax(new DiscreteBicTest(), false));
        algorithms.add(new PcMax(new BDeuTest(), false));
        algorithms.add(new Fges(new DiscreteBicScore(), false));
        algorithms.add(new Fges(new BdeuScore(), false));

        Simulations simulations = new Simulations();

        simulations.add(new BayesNetSimulation(new RandomForward()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
//        comparison.setParallelized(false);

        comparison.compareFromSimulations("comparison_richard", simulations, algorithms, statistics, parameters);
    }
}





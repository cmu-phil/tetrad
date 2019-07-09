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
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.PcAll;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.independence.ChiSquare;
import edu.cmu.tetrad.algcomparison.independence.GSquare;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.ConditionalGaussianBicScore;
import edu.cmu.tetrad.algcomparison.score.DiscreteBicScore;
import edu.cmu.tetrad.algcomparison.simulation.BayesNetSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class ExampleCompareSimulationDiscrete {
    public static void main(String... args) {
        Parameters parameters = new Parameters();
        int sampleSize = 5000;

        parameters.set("numRuns", 10);
        parameters.set("numMeasures", 10, 20);
        parameters.set("avgDegree", 2, 4);
        parameters.set("sampleSize", sampleSize); // This varies.
        parameters.set("minCategories", 3);
        parameters.set("maxCategories", 3);
        parameters.set("differentGraphs", true);

        parameters.set("alpha", 0.05, 0.01, 0.001);
        parameters.set("colliderDiscoveryRule", 1, 2, 3);
        parameters.set("conflictRule", 3);;

        parameters.set("maxDegree", 100);
        parameters.set("samplePrior",  1, 5, 10, 15, 20, 25, 30, 50, 80, 100);
        parameters.set("structurePrior", 1, 2, 3, 4, 5);

//        parameters.set("penaltyDiscount", 1, 2, 3, 4);
        parameters.set("discretize", true);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("numMeasures"));
        statistics.add(new ParameterColumn("avgDegree"));
        statistics.add(new ParameterColumn("sampleSize"));
        statistics.add(new ParameterColumn("colliderDiscoveryRule"));
        statistics.add(new ParameterColumn("samplePrior"));
        statistics.add(new ParameterColumn("structurePrior"));
//        statistics.add(new ParameterColumn("penaltyDiscount"));
        statistics.add(new ParameterColumn("discretize"));
        statistics.add(new ParameterColumn("alpha"));

        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadPrecisionCommonEdges());
        statistics.add(new ArrowheadRecall());
//        statistics.add(new MathewsCorrAdj());
//        statistics.add(new MathewsCorrArrow());
//        statistics.add(new F1Adj());
//        statistics.add(new F1Arrow());
        statistics.add(new SHD());
//        statistics.add(new ElapsedTime());

//        statistics.setWeight("AP", 1.0);
//        statistics.setWeight("AR", 1.0);
//        statistics.setWeight("AHP", 1.0);
//        statistics.setWeight("AHR", 1.0);
        statistics.setWeight("SHD", 1.0);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new PcAll(new ChiSquare()));
        algorithms.add(new PcAll(new GSquare()));
//
        algorithms.add(new Fges(new BdeuScore()));
        algorithms.add(new Fges(new DiscreteBicScore()));
        algorithms.add(new Fges(new ConditionalGaussianBicScore()));

        Simulations simulations = new Simulations();

        simulations.add(new BayesNetSimulation(new RandomForward()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(true);
//        comparison.setShowUtilities(true);

        comparison.setComparisonGraph(Comparison.ComparisonGraph.Pattern_of_the_true_DAG);

        comparison.compareFromSimulations("comparison.discrete.study", simulations, "comparison_all_" + sampleSize, algorithms, statistics, parameters);
    }
}





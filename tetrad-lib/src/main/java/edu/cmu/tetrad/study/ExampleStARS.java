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
import edu.cmu.tetrad.algcomparison.algorithm.FirstInflection;
import edu.cmu.tetrad.algcomparison.algorithm.StARS;
import edu.cmu.tetrad.algcomparison.algorithm.StabilitySelection;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class ExampleStARS {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

//        parameters.set("numMeasures", 100);
//        parameters.set("avgDegree", 2, 4);
//        parameters.set("sampleSize", 100, 500);
//        parameters.set("numRuns", 5);

        parameters.set("numMeasures", 200);
        parameters.set("avgDegree", 2, 4, 6);
        parameters.set("sampleSize", 100, 500);
        parameters.set("numRuns", 2);

        parameters.set("differentGraphs", true);
        parameters.set("numLatents", 0);
        parameters.set("maxDegree", 100);
        parameters.set("maxIndegree", 100);
        parameters.set("maxOutdegree", 100);
        parameters.set("connected", false);

        parameters.set("coefLow", 0.2);
        parameters.set("coefHigh", 0.9);
        parameters.set("varLow", 1);
        parameters.set("varHigh", 3);
        parameters.set("verbose", false);
        parameters.set("coefSymmetric", true);
        parameters.set("percentDiscrete", 0);
        parameters.set("numCategories", 3);
        parameters.set("differentGraphs", true);
        parameters.set("intervalBetweenShocks", 10);
        parameters.set("intervalBetweenRecordings", 10);
        parameters.set("fisherEpsilon", 0.001);
        parameters.set("randomizeColumns", true);

        parameters.set("alpha", 0.01);
        parameters.set("depth", -1);
        parameters.set("penaltyDiscount", 2);

        parameters.set("useMaxPOrientationHeuristic", false);
        parameters.set("maxPOrientationMaxPathLength", 3);
        parameters.set("verbose", false);

        parameters.set("scaleFreeAlpha", 0.00001);
        parameters.set("scaleFreeBeta", 0.4);
        parameters.set("scaleFreeDeltaIn", .1);
        parameters.set("scaleFreeDeltaOut", 3);

        parameters.set("symmetricFirstStep", false);
        parameters.set("faithfulnessAssumed", true);
        parameters.set("maxDegree", 100);

        parameters.set("StARS.tolerance", .5);
        parameters.set("StARS.cutoff", .05);
        parameters.set("numSubsamples", 7);

        parameters.set("percentSubsampleSize", .5);
        parameters.set("percentStability", .5);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("numMeasures"));
        statistics.add(new ParameterColumn("avgDegree"));
        statistics.add(new ParameterColumn("sampleSize"));

        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedTime());

        statistics.setWeight("AP", 0.25);
        statistics.setWeight("AR", 0.25);
        statistics.setWeight("AHP", 0.25);
        statistics.setWeight("AHR", 0.25);

        Algorithms algorithms = new Algorithms();

        parameters.set("logScale", false);
        algorithms.add(new StabilitySelection(new Fges(new SemBicScore())));
        algorithms.add(new StARS(new Fges(new SemBicScore()), "penaltyDiscount", 1, 5));
        algorithms.add(new FirstInflection(new Fges(new SemBicScore()), "penaltyDiscount", 1, 5, .1));

//        parameters.set("penaltyDiscount", 5, 11, 15);
//        algorithms.add(new Fges(new SemBicScore()));

//        parameters.set("logScale", true);
//        Algorithm fges = new Fges(new FisherZScore());
//        algorithms.add(new StARS(fges, "alpha", -10, -2, -8));
//        algorithms.add(new FirstInflection(fges, "alpha", -10, -2, -3));

        Simulations simulations = new Simulations();

        simulations.add(new LinearFisherModel(new RandomForward()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.Pattern_of_the_true_DAG);

        comparison.compareFromSimulations("first.inflection", simulations, algorithms, statistics, parameters);
    }
}





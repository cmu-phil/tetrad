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
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.*;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.score.FisherZScore;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
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
        if (args.length > 0) {
            int numMeasures = Integer.parseInt(args[0]);
            int avgDegree = Integer.parseInt(args[1]);

            Parameters parameters = new Parameters();

//        parameters.set("minCategories", 3);
//        parameters.set("maxCategories", 3);

            parameters.set("numRuns", 2);
            parameters.set("differentGraphs", true);
            parameters.set("sampleSize", 1000);

            parameters.set("numMeasures", numMeasures);
            parameters.set("numLatents", 0);
            parameters.set("avgDegree", avgDegree);
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
            parameters.set("numRuns", 1);
            parameters.set("percentDiscrete", 0);
            parameters.set("numCategories", 3);
            parameters.set("differentGraphs", true);
            parameters.set("sampleSize", 1000);
            parameters.set("intervalBetweenShocks", 10);
            parameters.set("intervalBetweenRecordings", 10);
            parameters.set("fisherEpsilon", 0.001);
            parameters.set("randomizeColumns", true);

            parameters.set("alpha", 1e-8);
            parameters.set("depth", -1);
            parameters.set("penaltyDiscount", 4);

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

            Statistics statistics = new Statistics();

            statistics.add(new ParameterColumn("numMeasures"));
            statistics.add(new ParameterColumn("avgDegree"));

            statistics.add(new AdjacencyPrecision());
            statistics.add(new AdjacencyRecall());
            statistics.add(new ArrowheadPrecision());
            statistics.add(new ArrowheadRecall());
//        statistics.add(new NumBidirectedEdges());
//        statistics.add(new MathewsCorrAdj());
//        statistics.add(new MathewsCorrArrow());
//        statistics.add(new F1Adj());
//        statistics.add(new F1Arrow());
//        statistics.add(new SHD());
            statistics.add(new ElapsedTime());

            statistics.setWeight("AP", 0.25);
            statistics.setWeight("AR", 0.25);
            statistics.setWeight("AHP", 0.25);
            statistics.setWeight("AHR", 0.25);

            Algorithms algorithms = new Algorithms();

//        algorithms.add(new Pc(new FisherZ()));
//        algorithms.add(new Pc(new SemBicTest()));
//        algorithms.add(new Cpc(new FisherZ()));
//        algorithms.add(new PcStable(new FisherZ()));
//        algorithms.add(new CpcStable(new FisherZ()));
//        algorithms.add(new PcStableMax(new FisherZ(), false));
//        algorithms.add(new PcStableMax(new SemBicTest(), false));
            algorithms.add(new Fges(new FisherZScore(), false));
//        algorithms.add(new Fges(new SemBicScore(), false));

            Simulations simulations = new Simulations();

            simulations.add(new LinearFisherModel(new RandomForward()));

            Comparison comparison = new Comparison();

            comparison.setShowAlgorithmIndices(true);
            comparison.setShowSimulationIndices(true);
            comparison.setSortByUtility(false);
            comparison.setShowUtilities(true);

            comparison.compareFromSimulations("comparisonJoe", simulations, algorithms, statistics, parameters);
        }
    }
}





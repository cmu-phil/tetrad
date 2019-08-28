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
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.PcAll;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class ExampleCompareSimulation {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 20);
        parameters.set("differentGraphs", true);
        parameters.set("sampleSize", 1000, 100000);

        parameters.set("numMeasures", 20);
        parameters.set("numLatents", 0);
        parameters.set("avgDegree", 4);
        parameters.set("maxDegree", 1000);
        parameters.set("maxIndegree", 1000);
        parameters.set("maxOutdegree", 1000);
        parameters.set("connected", false);

        parameters.set("coefLow", 0.1);
        parameters.set("coefHigh", .6);
        parameters.set("varLow", 1);
        parameters.set("varHigh", 3);
        parameters.set("verbose", false);
        parameters.set("coefSymmetric", false);
        parameters.set("percentDiscrete", 0);
        parameters.set("numCategories", 3);
        parameters.set("differentGraphs", true);
        parameters.set("intervalBetweenShocks", 100);
        parameters.set("intervalBetweenRecordings", 100);
        parameters.set("fisherEpsilon", 0.00001);
        parameters.set("randomizeColumns", true);

//        parameters.set(Params.STANDARDIZE, false);

        parameters.set("alpha", 0.01);
        parameters.set("depth", -1);

        parameters.set(Params.USE_MAX_P_ORIENTATION_HEURISTIC, false);
        parameters.set(Params.SYMMETRIC_FIRST_STEP, true);
        parameters.set(Params.FAITHFULNESS_ASSUMED, false);
        parameters.set("maxPOrientationMaxPathLength", 3);
        parameters.set("verbose", false);

        parameters.set("maxDegree", 100);

        parameters.set("penaltyDiscount", 1);
        parameters.set("structurePrior", 0);

        parameters.set("alpha", 0.01);

        parameters.set(Params.STABLE_FAS, true);
        parameters.set(Params.CONCURRENT_FAS, true);
        parameters.set(Params.COLLIDER_DISCOVERY_RULE, 2, 3);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new ParameterColumn("thresholdAlpha"));
        statistics.add(new ParameterColumn("penaltyDiscount"));

        statistics.add(new NumberOfEdgesEst());
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ArrowheadPrecisionCommonEdges());
        statistics.add(new ArrowheadRecallCommonEdges());
        statistics.add(new F1All());
//        statistics.add(new BicTrue());
//        statistics.add(new BicEst());
//        statistics.add(new BicDiffPerRecord());
        statistics.add(new ElapsedTime());
        statistics.add(new TailPrecision());
        statistics.add(new TailRecall());
        statistics.add(new GraphExactlyRight());

        statistics.setWeight("AP", 1);
        statistics.setWeight("AR", 1);
        statistics.setWeight("AHP", 1);
        statistics.setWeight("AHR", 1);

        Algorithms algorithms = new Algorithms();

//        algorithms.add(new PcAll(new FisherZ()));
        algorithms.add(new Fges(new SemBicScore()));
//        algorithms.add(new Gfci(new FisherZ(), new SemBicScore()));

        Simulations simulations = new Simulations();

        simulations.add(new SemSimulation(new RandomForward()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(false);
        comparison.setShowSimulationIndices(false);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.Pattern_of_the_true_DAG);

        comparison.compareFromSimulations("comparisonJoe", simulations, algorithms, statistics, parameters);
    }
}





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

package edu.cmu.tetrad.study.examples.conditions;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.FirstInflection;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ExampleFirstInflection {

    /**
     * Private constructor to prevent instantiation.
     */
    private ExampleFirstInflection() {
    }

    /**
     * <p>main.</p>
     *
     * @param args a {@link java.lang.String} object
     */
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.set("numMeasures", 40, 100);
        parameters.set("avgDegree", 2);
        parameters.set("sampleSize", 400, 800);
        parameters.set("numRuns", 10);

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

//        parameters.set("logScale", true);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("numMeasures"));
        statistics.add(new ParameterColumn("avgDegree"));
        statistics.add(new ParameterColumn("sampleSize"));

        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ArrowheadPrecisionCommonEdges());
        statistics.add(new ArrowheadRecallCommonEdges());
        statistics.add(new ElapsedCpuTime());

        statistics.setWeight("AP", 0.25);
        statistics.setWeight("AR", 0.25);
        statistics.setWeight("AHP", 0.25);
        statistics.setWeight("AHR", 0.25);

        Algorithms algorithms = new Algorithms();

        Algorithm fges = new Fges(new SemBicScore());
//        algorithms.add(new FirstInflection(fges, "alpha", -7, -2, -.5));
        algorithms.add(new FirstInflection(fges, "penaltyDiscount", 0.7, 5, 1));

        Simulations simulations = new Simulations();

        simulations.add(new LinearFisherModel(new RandomForward()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.CPDAG_of_the_true_DAG);

        comparison.compareFromSimulations("first.inflection", simulations, algorithms, statistics, parameters);
    }
}





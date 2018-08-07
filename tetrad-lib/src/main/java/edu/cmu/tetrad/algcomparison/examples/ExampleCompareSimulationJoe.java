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

package edu.cmu.tetrad.algcomparison.examples;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.*;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.independence.*;
import edu.cmu.tetrad.algcomparison.simulation.*;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class ExampleCompareSimulationJoe {
    public static void main(String... args) {
        Parameters parameters = new Parameters();
        parameters.set("numRuns", 5);
        parameters.set("numMeasures", 10);
        parameters.set("avgDegree", 2);
        parameters.set("sampleSize", 500);
        parameters.set("differentGraphs", true);

        parameters.set("fasRule", 2);
        parameters.set("colliderDiscoveryRule", 3);
        parameters.set("conflictRule", 3);
        parameters.set("depth", -1);
        parameters.set("useMaxPOrientationHeuristic", false);
        parameters.set("maxPOrientationMaxPathLength", 3);

        parameters.set("alpha", 0.01);
        parameters.set("numBasisFunctions", 8);
        parameters.set("kernelType", 1);
        parameters.set("kernelMultiplier", 1);
        parameters.set("basisType", 2);

        parameters.set("penaltyDiscount", 1);

        final String function = "TSUM(NEW(B) * $^2)";
//        final String function = "1 - TSUM(4 * exp(-$)^2 / 2) * $))";
//        final String function = "1 - 4 * exp(-(TSUM($))^2 / 2) * $ * $ * $";
        parameters.set("generalSemFunctionTemplateMeasured", function);
        parameters.set("generalSemFunctionTemplateLatent", function);
        parameters.set("generalSemErrorTemplate", "N(0, 0.5)");
        parameters.set("generalSemParameterTemplate", "U(.4, .4001)");

        parameters.set("verbose", true);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("sampleSize"));
        statistics.add(new ParameterColumn("kernelType"));
        statistics.add(new ParameterColumn("kernelMultiplier"));
        statistics.add(new ParameterColumn("basisType"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
//        statistics.add(new MathewsCorrAdj());
//        statistics.add(new MathewsCorrArrow());
//        statistics.add(new F1Adj());
//        statistics.add(new F1Arrow());
        statistics.add(new SHD());
        statistics.add(new ElapsedTime());

        statistics.setWeight("AP", 1.0);
        statistics.setWeight("AR", 0.5);
        statistics.setWeight("AHP", 1.0);
        statistics.setWeight("AHR", 0.5);
//        statistics.setWeight("SHD", 1.0);

        Algorithms algorithms = new Algorithms();

//        algorithms.add(new PcAll(new Kci()));
//        algorithms.add(new PcAll(new KciMatlab()));
        algorithms.add(new PcAll(new RcitJRI()));
        algorithms.add(new PcAll(new CciTest()));
        algorithms.add(new PcAll(new ConditionalGaussianLRT()));
        algorithms.add(new PcAll(new FisherZ()));
        algorithms.add(new PcAll(new SemBicTest()));

        Simulations simulations = new Simulations();

        final GeneralSemSimulation simulation = new GeneralSemSimulation(new RandomForward());
        simulations.add(simulation);
//        Simulation simulation = new LoadDataAndGraphs("comparison10vars");

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(true);
        comparison.setParallelized(false);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.Pattern_of_the_true_DAG);

//        comparison.saveToFiles("comparison10vars", simulation, parameters);
//        comparison.compareFromFiles("comparison10vars", "comparison10vars", algorithms, statistics, parameters);

        comparison.compareFromSimulations("comparisonJoe", simulations, algorithms, statistics, parameters);
    }
}





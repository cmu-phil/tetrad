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
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.FisherZScore;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class PcMaxStudy {
    public static void main(String... args) {
        Statistics statistics = new Statistics();

//        statistics.add(new ParameterColumn(Params.COLLIDER_DISCOVERY_RULE));
//        statistics.add(new ParameterColumn(Params.DO_MARKOV_LOOP));
//        statistics.add(new ParameterColumn(Params.USE_FDR_FOR_INDEPENDENCE));
        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new ParameterColumn(Params.PENALTY_DISCOUNT));
        statistics.add(new ParameterColumn(Params.ALPHA));
        statistics.add(new ParameterColumn(Params.DEPTH));
//        statistics.add(new ParameterColumn(Params.ORIENTATION_ALPHA));

        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
//        statistics.add(new ArrowheadPrecisionCommonEdges());
//        statistics.add(new ArrowheadRecallCommonEdges());
//        statistics.add(new F1All());
//        statistics.add(new ColliderFp());
//        statistics.add(new ColliderFn());
////        statistics.add(new ColliderNumCoveringErrors());
////        statistics.add(new ColliderNumUncoveringErrors());
//        statistics.add(new PercentAmbiguous());
//        statistics.add(new BicDiff());
////        statistics.add(new NumberOfEdgesEst());
////        statistics.add(new GraphExactlyRight());
//        statistics.add(new ElapsedTime());

        statistics.setWeight("AR", 1);
        statistics.setWeight("AHP", .5);
//        statistics.setWeight("%AMB", 1);
//        statistics.setWeight("AHR", 1);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new FgesCpc(new FisherZ()));
        algorithms.add(new Fges(new SemBicScore()));
        algorithms.add(new PcAll(new FisherZ()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
//        comparison.setShowUtilities(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        comparison.compareFromSimulations("pcmax", simulations, algorithms, statistics, getParameters());

    }

    private static Parameters getParameters() {
        Parameters parameters = new Parameters();

        parameters.set(Params.COEF_LOW, 0.1);
        parameters.set(Params.COEF_HIGH, 1.0);
        parameters.set(Params.VAR_LOW, 1);
        parameters.set(Params.VAR_HIGH, 3);

        parameters.set(Params.STABLE_FAS, true);
        parameters.set(Params.CONCURRENT_FAS, false);
        parameters.set(Params.CONFLICT_RULE, 3);
        parameters.set(Params.SYMMETRIC_FIRST_STEP, true);
        parameters.set(Params.COLLIDER_DISCOVERY_RULE, 2);

        parameters.set(Params.NUM_RUNS, 5);
        parameters.set(Params.DEPTH, -1);
        parameters.set(Params.ALPHA, .2, .1, .05, 0.01, 0.001, 0.0001);
        parameters.set(Params.ORIENTATION_ALPHA, -1);
        parameters.set(Params.PENALTY_DISCOUNT, 1, 2, 3);
        parameters.set(Params.NUM_MEASURES, 10);
        parameters.set(Params.AVG_DEGREE, 4);
        parameters.set(Params.SAMPLE_SIZE, 500, 1000, 2000, 5000, 10000);
        parameters.set(Params.DO_MARKOV_LOOP, true);
        parameters.set(Params.USE_FDR_FOR_INDEPENDENCE, true);

//        parameters.set(Params.ERRORS_NORMAL, false);

        parameters.set(Params.VERBOSE, false);


        return parameters;
    }
}





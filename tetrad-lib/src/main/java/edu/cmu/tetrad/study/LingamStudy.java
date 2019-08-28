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
import edu.cmu.tetrad.algcomparison.algorithm.continuous.dag.Lingam;
import edu.cmu.tetrad.algcomparison.algorithm.multi.Fask;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.FAS;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.R3;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.RSkew;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fas;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class LingamStudy {
    public static void main(String... args) {
        Statistics statistics = new Statistics();

//        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new ParameterColumn(Params.PENALTY_DISCOUNT));
        statistics.add(new ParameterColumn(Params.ALPHA));

        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
//        statistics.add(new F1All());
//        statistics.add(new GraphExactlyRight());
        statistics.add(new ElapsedTime());

        statistics.setWeight("AP", 1);
        statistics.setWeight("AR", 1);
        statistics.setWeight("AHP", 1);
        statistics.setWeight("AHR", 1);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new Lingam());
        algorithms.add(new R3(new FAS(new FisherZ())));
        algorithms.add(new RSkew(new FAS(new FisherZ())));
        algorithms.add(new Fask(new FisherZ()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        Simulations simulations = new Simulations();
        simulations.add(new LinearFisherModel(new RandomForward()));

        comparison.compareFromSimulations("lingam", simulations, algorithms, statistics, getParameters());

    }

    private static Parameters getParameters() {
        Parameters parameters = new Parameters();

        parameters.set(Params.VERBOSE, false);

        parameters.set(Params.NUM_RUNS, 10);
        parameters.set(Params.SAMPLE_SIZE, 1000);

        parameters.set(Params.COEF_LOW, 0.2);
        parameters.set(Params.COEF_HIGH, 0.7);
//        parameters.set(Params.VAR_LOW, 1);
//        parameters.set(Params.VAR_HIGH, 3);
        parameters.set(Params.COEF_SYMMETRIC, true);
        parameters.set(Params.RANDOMIZE_COLUMNS, true);

        parameters.set(Params.DEPTH, -1);

        parameters.set(Params.SYMMETRIC_FIRST_STEP, false);
        parameters.set(Params.FAITHFULNESS_ASSUMED, true);
        parameters.set(Params.MAX_DEGREE, 100);
        parameters.set(Params.MAX_INDEGREE, 100);
        parameters.set(Params.MAX_OUTDEGREE, 100);
        parameters.set(Params.NUM_MEASURES, 10);
        parameters.set(Params.AVG_DEGREE, 2);

        parameters.set(Params.PENALTY_DISCOUNT, 1.);
        parameters.set(Params.ALPHA, 0.01);
        parameters.set(Params.FAST_ICA_A, 1.1);
        parameters.set(Params.FAST_ICA_MAX_ITER, 2000);
        parameters.set(Params.FAST_ICA_TOLERANCE, 1e-6);

        parameters.set(Params.ERRORS_NORMAL, false);
        parameters.set(Params.RANDOMIZE_COLUMNS, true);

        return parameters;
    }

}





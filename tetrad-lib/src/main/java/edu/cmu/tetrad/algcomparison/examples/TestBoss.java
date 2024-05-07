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
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Boss;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

/**
 * Test the degenerate Gaussian score.
 *
 * @author bandrews
 * @version $Id: $Id
 */
public class TestBoss {

    /**
     * Initializes a new instance of the TestBoss class.
     */
    public TestBoss() {
    }

    /**
     * <p>main.</p>
     *
     * @param args a {@link java.lang.String} object
     */
    public static void main(String... args) {
//        if (false) {
//            testGigaflops();
//            return;
//        }

        Parameters parameters = new Parameters();
        parameters.set(Params.NUM_RUNS, 5);
        parameters.set(Params.DIFFERENT_GRAPHS, true);
        parameters.set(Params.NUM_MEASURES, 30);
        parameters.set(Params.AVG_DEGREE, 4);
        parameters.set(Params.SAMPLE_SIZE, 1000);
        parameters.set(Params.COEF_LOW, 0);
        parameters.set(Params.COEF_HIGH, 1);
        parameters.set(Params.VAR_LOW, 1);
        parameters.set(Params.VAR_HIGH, 3);
        parameters.set(Params.SIMULATION_ERROR_TYPE, 1);
        parameters.set(Params.SIMULATION_PARAM1, 1);

        parameters.set(Params.PENALTY_DISCOUNT, 2);
        parameters.set(Params.SEM_BIC_STRUCTURE_PRIOR, 0);
        parameters.set(Params.ALPHA, 1e-2);

        parameters.set(Params.USE_BES, false);
        parameters.set(Params.NUM_STARTS, 1);
        parameters.set(Params.NUM_THREADS, 1);
        parameters.set(Params.USE_DATA_ORDER, false);

        parameters.set(Params.VERBOSE, false);

        Statistics statistics = new Statistics();
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedCpuTime());

        Algorithms algorithms = new Algorithms();
//        algorithms.add(new DirectLingam(new SemBicScore()));
        algorithms.add(new Fges(new SemBicScore()));
        algorithms.add(new Boss(new SemBicScore()));
//        algorithms.add(new Dagma());

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));
//        simulations.add(new LeeHastieSimulation(new RandomForward()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.CPDAG_of_the_true_DAG);

        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
    }

    /**
     * <p>testGigaflops.</p>
     */
    public static void testGigaflops() {

        final long start = MillisecondTimes.timeMillis();// System.currentTimeMillis();

        for (int i = 0; i < 200; i++) {
            int N = 1024;
            RealMatrix A = new BlockRealMatrix(N, N);
            RealMatrix B = new BlockRealMatrix(N, N);

            MillisecondTimes.type = MillisecondTimes.Type.CPU;

            RealMatrix C = A.multiply(B);
            final long end = MillisecondTimes.timeMillis();// System.currentTimeMillis();

            double gflop = N * N * N * 2e-9;
            double sec = (end - start) * 1e-3;

            System.out.println(gflop / sec);
        }

        final long end = MillisecondTimes.timeMillis();// System.currentTimeMillis();

        System.out.println((end - start) * 1e-3);

    }
}





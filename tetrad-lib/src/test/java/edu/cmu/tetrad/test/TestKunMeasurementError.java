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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.FgesD;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Pc;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Pcd;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;

/**
 * Pulling this test out for Madelyn.
 *
 * @author jdramsey
 */
public class TestKunMeasurementError {

    public void TestCycles_Data_fMRI_FASK() {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 20);

        parameters.set("penaltyDiscount", 1);
        parameters.set("depth", -1);
        parameters.set("determinismThreshold", .1);
        parameters.set("verbose", true);

        parameters.set("symmetricFirstStep", false);
        parameters.set("faithfulnessAssumed", false);
        parameters.set("maxDegree", 100);

        Statistics statistics = new Statistics();

//        statistics.add(new ParameterColumn("determinismThreshold"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());

        Simulations simulations = new Simulations();

//        String dir = "/Users/user/Downloads/Simul1_T500";
        String dir = "/Users/user/Downloads/Simul1_T2000";
//        String dir = "/Users/user/Downloads/Simul2_T500";
//        String dir = "/Users/user/Downloads/Simul2_T2000";

        simulations.add(new LoadContinuousDataAndSingleGraphKun(
                dir, "Cov_X"));
        simulations.add(new LoadContinuousDataAndSingleGraphKun(
                dir, "Cov_tilde"));
        simulations.add(new LoadContinuousDataAndSingleGraphKun(
                dir, "Cov_tilde_hat"));
//
        Algorithms algorithms = new Algorithms();

        IndependenceWrapper test = new SemBicTest();
        ScoreWrapper score = new SemBicScore();

        algorithms.add(new Pc(test));
        algorithms.add(new Fges(score));
        algorithms.add(new Pcd( ));
        algorithms.add(new FgesD());

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(false);
        comparison.setSaveGraphs(false);
        comparison.setTabDelimitedTables(false);

        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
    }

    public static void main(String... args) {
        new TestKunMeasurementError().TestCycles_Data_fMRI_FASK();
    }
}





///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.continuous.dag.Fask;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author josephramsey
 */
public class SpecialExampleSimulationClark {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 20);
        parameters.set("sampleSize", 1000);
        parameters.set("twoCycleAlpha", 1);

        Statistics statistics = new Statistics();

        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
//        statistics.add(new TwoCycleTruePositive());
//        statistics.add(new TwoCycleFalseNegative());
//        statistics.add(new TwoCycleFalsePositive());


        // For randomm forward graph
        parameters.set("numMeasures", 10);
        parameters.set("numLatents", 0);
        parameters.set("avgDegree", 2);
        parameters.set("maxDegree", 100);
        parameters.set("maxIndegree", 100);
        parameters.set("maxOutdegree", 100);
        parameters.set("connected", false);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new Fask());

        Simulations simulations = new Simulations();

//        simulations.add(new SpecialDataClark(new SpecialGraphClark()));
        simulations.add(new SpecialDataClark(new RandomForward()));

        Comparison comparison = new Comparison();
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setSaveGraphs(true);
        comparison.setSaveCPDAGs(true);
        comparison.setSavePags(true);

//        comparison.saveToFiles("comparison", new SpecialDataClark(new SpecialGraphClark()), parameters);
        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
    }
}






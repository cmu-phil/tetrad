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
import edu.cmu.tetrad.algcomparison.algorithm.multi.Images;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author josephramsey
 */
public class TestImagesSimulation {

    public static void main(String... args) {
        new TestImagesSimulation().test1();
    }

    public void test1() {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 1);
        parameters.set("numMeasures", 100);
        parameters.set("avgDegree", 8);

        parameters.set("sampleSize", 500);
        parameters.set("penaltyDiscount", 4);

        parameters.set("intervalBetweenRecordings", 20);

        parameters.set("varLow", 1.);
        parameters.set("varHigh", 3.);
        parameters.set("coefLow", .1);
        parameters.set("coefHigh", 1);
        parameters.set("coefSymmetric", true);
        parameters.set("meanLow", -1);
        parameters.set("meanHigh", 1);

        parameters.set("numRuns", 1);
        parameters.set("randomSelectionSize", 3);

        Statistics statistics = new Statistics();

//        statistics.add(new ParameterColumn("numCategories"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedCpuTime());

        statistics.setWeight("AP", 1.0);
        statistics.setWeight("AR", 0.5);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new Images());

        Simulations simulations = new Simulations();

        simulations.add(new LinearFisherModel(new RandomForward()));

        Comparison comparison = new Comparison();
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
//        comparison.setSaveGraphs(true);

        comparison.setTabDelimitedTables(false);

        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
    }
}






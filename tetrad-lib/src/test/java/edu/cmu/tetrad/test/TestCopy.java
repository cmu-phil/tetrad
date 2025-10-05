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
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Pc;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

/**
 * Tests to make sure the DelimiterType enumeration hasn't been tampered with.
 *
 * @author josephramsey
 */
public final class TestCopy {

    /**
     * An example script to simulate data and run a comparison analysis on it.
     *
     * @author josephramsey
     */
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 10);
        parameters.set("numMeasures", 100);
        parameters.set("avgDegree", 2, 4, 6);
        parameters.set("sampleSize", 200);
        parameters.set("alpha", 1e-4, 1e-3, 1e-2);
        parameters.set("penaltyDiscount", 1);
        parameters.set(Params.USE_MAX_P_HEURISTIC, false, true);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("sampleSize"));
        statistics.add(new ParameterColumn("avgDegree"));
        statistics.add(new ParameterColumn("alpha"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new MathewsCorrAdj());
        statistics.add(new MathewsCorrArrow());
        statistics.add(new F1Adj());
        statistics.add(new F1Arrow());
        statistics.add(new StructuralHammingDistance());
        statistics.add(new ElapsedCpuTime());

        statistics.setWeight("AP", 1.0);
        statistics.setWeight("AHP", 1.0);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new Pc(new FisherZ()));
        algorithms.add(new Pc(new FisherZ()));

        Simulations simulations = new Simulations();

        simulations.add(new SemSimulation(new RandomForward()));

        Comparison comparison = new Comparison();
        comparison.setSortByUtility(true);
        comparison.setShowUtilities(true);

        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
    }
}









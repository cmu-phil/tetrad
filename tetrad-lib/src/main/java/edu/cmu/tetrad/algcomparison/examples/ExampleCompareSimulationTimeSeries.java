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
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.TsFci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.TsGfci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.TsImages;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.search.IndTestScore;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Score;
import edu.cmu.tetrad.search.ScoredIndTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.simulation.TimeSeriesSemSimulation;
import edu.cmu.tetrad.algcomparison.statistic.*;

/**
 * An example script to simulate time series data and run a comparison analysis on it.
 *
 * @authors jdramsey, dmalinsky
 */
public class ExampleCompareSimulationTimeSeries {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 10);
        parameters.set("numMeasures", 10);
        parameters.set("avgDegree", 4);
        parameters.set("sampleSize", 500);
        parameters.set("alpha", 1e-4, 1e-3, 1e-2);

        Statistics statistics = new Statistics();

        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new MathewsCorrAdj());
        statistics.add(new MathewsCorrArrow());
        statistics.add(new F1Adj());
        statistics.add(new F1Arrow());
        statistics.add(new SHD());
        statistics.add(new ElapsedTime());

        statistics.setWeight("AP", 1.0);
        statistics.setWeight("AR", 0.5);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new TsFci(new FisherZ()));
        algorithms.add(new TsGfci(new FisherZ(), new SemBicScore()));
        algorithms.add(new TsImages(new SemBicScore()));

        Simulations simulations = new Simulations();

        simulations.add(new TimeSeriesSemSimulation(new RandomForward()));

        Comparison comparison = new Comparison();

        comparison.setSortByUtility(true);
        comparison.setShowUtilities(true);

        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
    }
}





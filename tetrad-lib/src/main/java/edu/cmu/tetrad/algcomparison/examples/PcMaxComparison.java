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
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.LargeSemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;

/**
 * An example script to load in data sets and graphs from files and analyze them. The
 * files loaded must be in the same format as
 * </p>
 * new Comparison().saveDataSetAndGraphs("comparison/save1", simulation, parameters);
 * </p>
 * saves them. For other formats, specialty data loaders can be written to implement the
 * Simulation interface.
 *
 * @author jdramsey
 */
public class PcMaxComparison {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 1);
        parameters.set("numMeasures", 1000);
        parameters.set("avgDegree", 2, 4);
        parameters.set("sampleSize", 1000);

        // Can leave the simulation parameters out since
        // we're loading from file here.
        parameters.set("depth", -1);
        parameters.set("alpha", 0.001);
        parameters.set("penaltyDiscount", 4);
//        parameters.set("standardize", true);
//        parameters.set("measurementVariance", 0, .01, .1, .2, .3, .4, .5);//, 1);
        parameters.set("verbose", false);

        Statistics statistics = new Statistics();

//        statistics.add(new ParameterColumn("avgDegree"));
//        statistics.add(new ParameterColumn("sampleSize"));
//        statistics.add(new ParameterColumn("measurementVariance"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new PercentBidirectedEdges());
        statistics.add(new PercentAmbiguous());
        statistics.add(new ElapsedTime());

        statistics.setWeight("AP", 1.0);
        statistics.setWeight("AR", 0.5);
        statistics.setWeight("AHP", 1.0);
        statistics.setWeight("AHR", 0.5);
        statistics.setWeight("BID", 0.5);

        Algorithms algorithms = new Algorithms();

//        algorithms.add(new Pc(new FisherZ()));
        algorithms.add(new Cpc(new FisherZ()));
//        algorithms.add(new PcStable(new FisherZ()));
//        algorithms.add(new PcMax(new FisherZ()));
        algorithms.add(new Fgs(new SemBicScore()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setShowUtilities(true);
        comparison.setSortByUtility(false);
        comparison.setTabDelimitedTables(true);
        comparison.setSaveGraphs(false);
        comparison.setParallelized(false);

        Simulations simulations = new Simulations();
        Simulation simulation = new LargeSemSimulation(new RandomForward());
        simulations.add(simulation);

//        comparison.saveToFiles("pcmax_comparison", simulation, parameters);
        comparison.compareFromSimulations("pcmax_comparison", simulations, algorithms, statistics, parameters);
    }
}





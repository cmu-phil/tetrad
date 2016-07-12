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

package edu.cmu.tetrad.algcomparison.comparisons;

import edu.cmu.tetrad.algcomparison.*;
import edu.cmu.tetrad.algcomparison.interfaces.Simulation;
import edu.cmu.tetrad.algcomparison.mixed.pattern.*;
import edu.cmu.tetrad.algcomparison.simulation.MixedLeeHastieSimulation;
import edu.cmu.tetrad.algcomparison.statistic.*;

/**
 * @author Joseph Ramsey
 */
public class RunComparisonCG {
    public static void main(String... args) {

        Parameters parameters = new Parameters();

        parameters.put("numRuns", 10);
//        parameters.put("sampleSize", 5180);
//        parameters.put("numMeasures", 570);
        parameters.put("sampleSize", 500);
        parameters.put("numMeasures", 100);
        parameters.put("numEdges", 2 * parameters.getInt("numMeasures"));
        parameters.put("numLatents", 0);
        parameters.put("numCategories", 4);

//        parameters.putDouble("alpha", 5e-3);
        parameters.put("alpha", 1e-4);

        parameters.put("penaltyDiscount", 4);

        parameters.put("fgsDepth", -1);
        parameters.put("printGraphs", 0);

        parameters.put("scaleFreeAlpha", .1);
        parameters.put("scaleFreeBeta", .8);
        parameters.put("scaleFreeDeltaIn", 3.0);
        parameters.put("scaleFreeDeltaOut", 3.0);
        parameters.put("samplePrior", 1);
        parameters.put("structurePrior", 1);

        parameters.put("mgmParam1", 0.1);
        parameters.put("mgmParam2", 0.1);
        parameters.put("mgmParam3", 0.1);

        parameters.put("percentDiscreteForMixedSimulation", 50);

        Statistics stats = new Statistics();

        stats.add(new AdjacencyPrecisionStat());
        stats.add(new AdjacencyRecallStat());
        stats.add(new ArrowPrecisionStat());
        stats.add(new ArrowRecallStat());
        stats.add(new MathewsCorrAdjStat());
        stats.add(new MathewsCorrArrowStat());
        stats.add(new F1AdjStat());
        stats.add(new F1ArrowStat());
        stats.add(new ShdStat());
        stats.add(new ElapsedTimeStat());

//        stats.put("AP", 1.0);
//        stats.put("AR", 1.0);
//        stats.put("OP", 1.0);
//        stats.put("OR", 1.0);
//        stats.put("McAdj", 1.0);
//        stats.put("McOr", 0.5);
        stats.setWeight("F1Adj", 1.0);
        stats.setWeight("F1Arrow", 0.5);
//        stats.put("SHD", 1.0);
//        stats.put("E", .2);

//        List<Algorithm> algorithms = getFullAlgorithmsList();
        Algorithms algorithms = new Algorithms();

        algorithms.add(new MixedFgs2CG()); //*
        algorithms.add(new MixedPcCgLrtTest());
        algorithms.add(new MixedCpcCgLrtTest()); //*
        algorithms.add(new MixedCpcLrt()); //*

        Simulation simulation = new MixedLeeHastieSimulation(parameters);
//        Simulation simulation = new LinearGaussianSemSimulation(parameters.getInt("numRuns"));
//        Simulation simulation = new MixedSemThenDiscretizeHalfSimulation(parameters.getInt("numRuns"));
//        Simulation simulation = new DiscreteBayesNetSimulation(parameters.getInt("numRuns"));

//        Simulation simulation = new LoadDataFromFileWithoutGraph("/Users/jdramsey/Downloads/data1.1.txt");
//        Simulation simulation = new LoadDataFromFileWithoutGraph("/Users/jdramsey/BitTorrent Sync/Joe_hipp_voxels/Hipp_L_first10.txt");
//        Simulation simulation = new LoadDataFromFileWithoutGraph("/Users/jdramsey/BitTorrent Sync/Joe_hipp_voxels/Hipp_L_last10.txt");

        new Comparison().compareAlgorithms("comparison/Comparison.txt", simulation, algorithms,
                stats, parameters);
    }

}





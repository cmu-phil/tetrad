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
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Rfci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.*;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
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
public class ExampleCompareFromFiles {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 1);
//        parameters.set("numMeasures", 20,100,1000);
        parameters.set("numMeasures",1000);
        parameters.set("numLatents", 200);
        parameters.set("avgDegree", 2);
        parameters.set("sampleSize", 1000);

        parameters.set("penaltyDisount", 2);
        parameters.set("alpha", 1e-4);


        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("avgDegree"));
        statistics.add(new ParameterColumn("sampleSize"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new MathewsCorrArrow());
        statistics.add(new F1Adj());
        statistics.add(new F1Arrow());
        statistics.add(new SHD());
        statistics.add(new ElapsedTime());

        statistics.setWeight("AP", 1.0);
        statistics.setWeight("AR", 0.5);
        statistics.setWeight("AHP", 1.0);
        statistics.setWeight("AHR", 0.5);
//        statistics.setWeight("TP", 1.0);
//        statistics.setWeight("TR", 0.5);

        Algorithms algorithms = new Algorithms();

//        algorithms.add(new Gfci(new ChiSquare(), new BdeuScore()));
//        algorithms.add(new Fci(new FisherZ()));
//        algorithms.add(new Fci(new ChiSquare()));
//        algorithms.add(new Rfci(new ChiSquare()));
//        algorithms.add(new Rfci(new FisherZ()));
        algorithms.add(new Gfci(new FisherZ(), new SemBicScore()));
//        algorithms.add(new Fges(new BdeuScore(),true));
//        algorithms.add(new Fges(new DiscreteBicScore(),true));
//        algorithms.add(new Fges(new SemBicScore()));
//        algorithms.add(new Gfci(new ChiSquare(), new DiscreteBicScore())));
//        algorithms.add(new Fges(new BdeuScore()));
//        algorithms.add(new Fges(new DiscreteBicScore()));
//        algorithms.add(new PcMax(new FisherZ(), false));
//        algorithms.add(new PcMax(new ChiSquare(),true));
//        algorithms.add(new PcMax(new FisherZ(), false));
//        algorithms.add(new Pc(new FisherZ()));

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(false);
        comparison.setSortByUtility(true);
        comparison.setShowUtilities(true);
        comparison.setParallelized(false);
        comparison.setSaveGraphs(true);
        //DagToPag p = new DagToPag(graph);

        comparison.compareFromFiles("comparison", algorithms, statistics, parameters);
    }
}





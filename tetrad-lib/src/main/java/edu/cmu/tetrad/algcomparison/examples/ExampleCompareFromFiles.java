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
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.algcomparison.algorithms.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithms.oracle.pattern.Cpc;
import edu.cmu.tetrad.algcomparison.algorithms.oracle.pattern.Cpcs;
import edu.cmu.tetrad.algcomparison.algorithms.oracle.pattern.Pc;
import edu.cmu.tetrad.algcomparison.algorithms.oracle.pattern.Pcs;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.statistic.*;

/**
 * An example script to load in data sets and graphs from files and analyze them. The
 * files loaded must be in the same format as
 * </p>
 * new Comparison().saveDataSetAndGraphs("comparison/save1", simulation, parameters);
 * </p>
 * saves them. For other formats, specialty data loaders can be written to implement the
 * Simulation interface.
 * @author jdramsey
 */
public class ExampleCompareFromFiles {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.put("numRuns", 10);
        parameters.put("numMeasures", 100);
        parameters.put("numEdges", 2 * parameters.getInt("numMeasures"));
        parameters.put("sampleSize", 1000);
        parameters.put("alpha", 1e-4);

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
        statistics.setWeight("AHP", 1.0);
        statistics.setWeight("AHR", 0.5);

        statistics.setSortByUtility(true);
        statistics.setShowUtilities(true);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new Pc(new FisherZ()));
        algorithms.add(new Cpc(new FisherZ()));
        algorithms.add(new Pcs(new FisherZ()));
        algorithms.add(new Cpcs(new FisherZ()));

        new Comparison().compareAlgorithms("comparison/save1",
                "comparison/Comparison.txt",
                algorithms, statistics, parameters);
    }
}





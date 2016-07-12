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
import edu.cmu.tetrad.algcomparison.continuous.pattern.*;
import edu.cmu.tetrad.algcomparison.interfaces.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.ContinuousLinearGaussianSemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.LoadContinuousDatasetsAndGraphsFromDirectory;
import edu.cmu.tetrad.algcomparison.statistic.*;

/**
 * @author Joseph Ramsey
 */
public class ExampleComparison {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.put("numRuns", 10);
        parameters.put("numMeasures", 100);
        parameters.put("numEdges", 2 * parameters.getInt("numMeasures"));
        parameters.put("sampleSize", 1000);
        parameters.put("alpha", 1e-4, 1e-3, 1e-2);

        Statistics statistics = new Statistics();

        statistics.add(new AdjacencyPrecisionStat());
        statistics.add(new AdjacencyRecallStat());
        statistics.add(new ArrowPrecisionStat());
        statistics.add(new ArrowRecallStat());
        statistics.add(new MathewsCorrAdjStat());
        statistics.add(new MathewsCorrArrowStat());
        statistics.add(new F1AdjStat());
        statistics.add(new F1ArrowStat());
        statistics.add(new ShdStat());
        statistics.add(new ElapsedTimeStat());

        statistics.setWeight("AP", 1.0);
        statistics.setWeight("AR", 0.5);

        statistics.setSortByUtility(true);
        statistics.setShowUtilities(true);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new ContinuousPcFz());
        algorithms.add(new ContinuousCpcFz());
        algorithms.add(new ContinuousPcsFz());
        algorithms.add(new ContinuousCpcsFz());

//        Simulation simulation = new ContinuousLinearGaussianSemSimulation(parameters);
        Simulation simulation = new LoadContinuousDatasetsAndGraphsFromDirectory("comparison/save1", parameters);

        new Comparison().compareAlgorithms("comparison/Comparison.txt", simulation, algorithms,
                statistics, parameters);
//        new Comparison().saveDataSetAndGraphs("comparison/save1", simulation, parameters);
    }
}





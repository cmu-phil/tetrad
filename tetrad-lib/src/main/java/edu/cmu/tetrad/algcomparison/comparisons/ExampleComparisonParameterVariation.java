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

import edu.cmu.tetrad.algcomparison.Algorithms;
import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.algcomparison.Statistics;
import edu.cmu.tetrad.algcomparison.continuous.pattern.ContinuousCpcFz;
import edu.cmu.tetrad.algcomparison.continuous.pattern.ContinuousCpcsFz;
import edu.cmu.tetrad.algcomparison.continuous.pattern.ContinuousPcFz;
import edu.cmu.tetrad.algcomparison.continuous.pattern.ContinuousPcsFz;
import edu.cmu.tetrad.algcomparison.interfaces.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.LoadContinuousDatasetsAndGraphsFromDirectory;
import edu.cmu.tetrad.algcomparison.statistic.*;

/**
 * @author Joseph Ramsey
 */
public class ExampleComparisonParameterVariation {
    public static void main(String... args) {

        // These will be overridden from the saved data, except for alpha,
        // which is set below.
        Parameters parameters = new Parameters();

        // Need to pick out statistics.
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

        // Need to choose algorithms to run on this data.
        Algorithms algorithms = new Algorithms();
        algorithms.add(new ContinuousPcFz());
        algorithms.add(new ContinuousCpcFz());
        algorithms.add(new ContinuousPcsFz());
        algorithms.add(new ContinuousCpcsFz());

        for (double alpha : new double[]{0.001, 0.01, 0.05}) {
            parameters.put("alpha", alpha);
            Simulation simulation = new LoadContinuousDatasetsAndGraphsFromDirectory("comparison/save1", parameters);
            new Comparison().compareAlgorithms("comparison/Comparison." + alpha + ".txt", simulation, algorithms,
                    statistics, parameters);
        }
    }
}





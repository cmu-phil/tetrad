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

package mycomparisons;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithms.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithms.oracle.pag.Gfci;
import edu.cmu.tetrad.algcomparison.algorithms.oracle.pattern.Fgs2;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.algcomparison.statistic.*;

/**
 * @author jdramsey
 */
public class RichardComparison {
    public static void main(String... args) {

        Parameters parameters = new Parameters();

//        parameters.put("numRuns", 10);
//        parameters.put("sampleSize", 50, 100, 500, 1000, 5000);
//        parameters.put("numMeasures", 10, 50);
//        parameters.put("edgeFactor", 2, 5);
//        parameters.put("numCategories", 2, 4, 6);
//        parameters.put("variance", 0.0, 0.01, 0.1, 0.2, 0.3, 0.4, 0.5);
//        parameters.put("percentDiscreteForMixedSimulation", 100);

        parameters.put("alpha", 1e-3);
        parameters.put("penaltyDiscount", 4);

        parameters.put("fgsDepth", -1);
        parameters.put("samplePrior", 1);
        parameters.put("structurePrior", 1);

        Statistics statistics = new Statistics();

        statistics.add(new AdjacencyPrecisionStat());
        statistics.add(new AdjacencyRecallStat());
        statistics.add(new ArrowheadPrecisionStat());
        statistics.add(new ArrowheadRecallStat());
        statistics.add(new MathewsCorrAdjStat());
        statistics.add(new MathewsCorrArrowStat());
        statistics.add(new F1AdjStat());
        statistics.add(new F1ArrowStat());
        statistics.add(new ShdStat());
        statistics.add(new ElapsedTimeStat());

        statistics.setSortByUtility(false);
        statistics.setShowUtilities(false);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new Fgs2(new SemBicScore()));
        algorithms.add(new Gfci(new SemBicScore()));

//        Simulation simulation = new ContinuousSemThenDiscretizeSimulation();
//        new Comparison().saveDataSetAndGraphs("comparison/saveRichard2", simulation,
//                parameters);

        new Comparison().compareAlgorithms("comparison/saveRichardContinuous",
                "comparison/ComparisonContinuous.txt", algorithms, statistics, parameters);

    }
}





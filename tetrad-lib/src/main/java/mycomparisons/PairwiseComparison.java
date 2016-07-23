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
import edu.cmu.tetrad.algcomparison.algorithms.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithms.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithms.mixed.pattern.MixedFgsDiscretingContinuousVariables;
import edu.cmu.tetrad.algcomparison.algorithms.mixed.pattern.MixedFgsTreatingDiscreteAsContinuous;
import edu.cmu.tetrad.algcomparison.algorithms.oracle.pattern.*;
import edu.cmu.tetrad.algcomparison.algorithms.pairwise.*;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.DiscreteBicScore;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.*;
import edu.cmu.tetrad.algcomparison.statistic.*;
import mycomparisons.experimental.ContinuousGpc;
import mycomparisons.experimental.MixedWfgs;
import mycomparisons.experimental.MixedWgfci;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jdramsey
 */
public class PairwiseComparison {
    public static void main(String... args) {

        Parameters parameters = new Parameters();

        parameters.put("numRuns", 10);

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

        statistics.setWeight("AHP", 1.0);
        statistics.setWeight("AHR", 0.5);

        Algorithms algorithms = new Algorithms();

        Fgs initialGraph = new Fgs(new SemBicScore());

        algorithms.add(new EB(initialGraph));
        algorithms.add(new R1(initialGraph));
        algorithms.add(new R2(initialGraph));
        algorithms.add(new R3(initialGraph));
        algorithms.add(new R4(initialGraph));
        algorithms.add(new RSkew(initialGraph));
        algorithms.add(new RSkewE(initialGraph));
        algorithms.add(new Skew(initialGraph));
        algorithms.add(new SkewE(initialGraph));
        algorithms.add(new SkewE(initialGraph));
        algorithms.add(new Tahn(initialGraph));

        Simulations simulations = new Simulations();

        simulations.add(new GeneralSemSimulation());

        new Comparison().compareAlgorithms("comparison/PairwiseComparison.txt",
                simulations, algorithms, statistics, parameters);
    }
}





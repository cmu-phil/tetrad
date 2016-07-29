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

package edu.cmu.tetrad.algcomparison.myexamples;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithms.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithms.myalgorithms.Wfgs;
import edu.cmu.tetrad.algcomparison.algorithms.oracle.pattern.*;
import edu.cmu.tetrad.algcomparison.independence.ConditionalGaussianLRT;
import edu.cmu.tetrad.algcomparison.score.ConditionalGaussianBicScore;
import edu.cmu.tetrad.algcomparison.simulation.LeeHastieSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.DataType;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class CompareNewAlgorithms {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.put("numRuns", 2);
        parameters.put("numMeasures", 100);
        parameters.put("avgDegree", 4);
        parameters.put("sampleSize", 500);
//        parameters.put("alpha", 1e-4, 1e-3, 1e-2);
        parameters.put("percentDiscrete", 25);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("percentDiscrete"));
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

        statistics.setSortByUtility(true);
        statistics.setShowUtilities(true);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new Pc(new ConditionalGaussianLRT()));
        algorithms.add(new Cpc(new ConditionalGaussianLRT(), new Fgs(new ConditionalGaussianBicScore())));
        algorithms.add(new Pcs(new ConditionalGaussianLRT()));
        algorithms.add(new Cpcs(new ConditionalGaussianLRT()));
        algorithms.add(new Wfgs());

        Simulations simulations = new Simulations();

        simulations.add(new LeeHastieSimulation(DataType.Discrete));

        new Comparison().compareAlgorithms("comparison/Comparison.txt",
                simulations, algorithms, statistics, parameters);
    }
}





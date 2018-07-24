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
import edu.cmu.tetrad.algcomparison.algorithm.mixed.Mgm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.*;
import edu.cmu.tetrad.algcomparison.independence.ConditionalGaussianLRT;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
//import edu.cmu.tetrad.algcomparison.independence.MNLRLRT;
import edu.cmu.tetrad.algcomparison.independence.MNLRLRT;
import edu.cmu.tetrad.algcomparison.independence.MVPLRT;
import edu.cmu.tetrad.algcomparison.score.*;
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
public class CompareFromFiles {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        // Can leave the simulation parameters out since
        // we're loading from file here.

        parameters.set("numRuns", 3);
        parameters.set("maxDistinctValuesDiscrete", 5);

        parameters.set("structurePrior", -0.5);
        parameters.set("fDegree", -1);
        parameters.set("discretize", 0);

        parameters.set("alpha", 1e-3, 1e-4);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("avgDegree"));
        statistics.add(new ParameterColumn("sampleSize"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedTime());

        statistics.setWeight("AP", 1.0);
        statistics.setWeight("AR", 0.5);
        statistics.setWeight("AHP", 1.0);
        statistics.setWeight("AHR", 0.5);

        Algorithms algorithms = new Algorithms();

//        algorithms.add(new Fges(new ConditionalGaussianBicScore()));
//        algorithms.add(new Fges(new ConditionalGaussianOtherBicScore()));
        algorithms.add(new Fges(new MVPBicScore()));
//        algorithms.add(new Fges(new MNLRBicScore()));
//        algorithms.add(new Fges(new DiscreteMixedBicScore()));
//        algorithms.add(new Cpc(new ConditionalGaussianLRT()));
//        algorithms.add(new Cpc(new MVPLRT()));
//        algorithms.add(new Cpc(new MNLRLRT(), new Mgm()));

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromFiles("comparison", algorithms, statistics, parameters);
    }
}





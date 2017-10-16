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
import edu.cmu.tetrad.algcomparison.algorithm.external.ExternalAlgorithmBNTPc;
import edu.cmu.tetrad.algcomparison.algorithm.external.ExternalAlgorithmPcalgPc;
import edu.cmu.tetrad.algcomparison.algorithm.external.ExternalAlgorithmTetrad;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.algcomparison.score.MVPBicScore;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;

//import edu.cmu.tetrad.algcomparison.independence.MNLRLRT;

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
public class CompareExternalAlgorithms {
    public static void main(String... args) {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 10);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("numMeasures"));
        statistics.add(new ParameterColumn("avgDegree"));
        statistics.add(new ParameterColumn("sampleSize"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new F1Adj());
        statistics.add(new F1Arrow());
        statistics.add(new F1All());
        statistics.add(new ElapsedTime());

        statistics.setWeight("AP", 1.0);
        statistics.setWeight("AR", 0.5);
        statistics.setWeight("AHP", 1.0);
        statistics.setWeight("AHR", 0.5);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new ExternalAlgorithmTetrad("PC_(\"Peter_and_Clark\"),_Priority_Rule,_using_Fisher_Z_test,_alpha_=_0.001"));
        algorithms.add(new ExternalAlgorithmPcalgPc("PC_pcalg_defaults_alpha_=_0.001"));
        algorithms.add(new ExternalAlgorithmBNTPc("learn_struct_pdag_pc_alpha_=_0.001"));

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(false);

        comparison.generateReportFromExternalAlgorithms("/Users/user/comparison-data/condition_1",
                "/Users/user/causal-comparisons/condition_1",
                algorithms, statistics, parameters);
    }
}





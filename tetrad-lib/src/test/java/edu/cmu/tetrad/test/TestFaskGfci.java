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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Rfci;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;

/**
 * Pulling this test out for Madelyn.
 *
 * @author jdramsey
 */
public class TestFaskGfci {

    public void TestFaskGfci() {
        Parameters parameters = new Parameters();

        // Random forward
        parameters.set("numMeasures", 100);
        parameters.set("numLatents", 20);
        parameters.set("avgDegree", 2);
        parameters.set("maxDegree", 100);
        parameters.set("maxIndegree", 100);
        parameters.set("maxOutdegree", 100);
        parameters.set("connected", false   );

        // Linear Fisher model
        parameters.set("coefLow", .2);
        parameters.set("coefHigh", .9);
        parameters.set("varLow", 1);
        parameters.set("varHigh", 3);
        parameters.set("verbose", false);
        parameters.set("includePositiveCoefs", true, false);
        parameters.set("includeNegativeCoefs", true, false);
        parameters.set("errorsNormal", false);
        parameters.set("betaLeftValue", 1);
        parameters.set("betaRightValue", 5);
        parameters.set("numRuns", 1);
        parameters.set("percentDiscrete", 0);
        parameters.set("numCategories", 4);
        parameters.set("differentGraphs", false);
        parameters.set("sampleSize", 1000);
        parameters.set("intervalBetweenShocks", 10);
        parameters.set("intervalBetweenRecordings", 10);
        parameters.set("fisherEpsilon", 0.001);
        parameters.set("randomizeColumns", false);
        
        // Sem BIC, test
        parameters.set("penaltyDiscount", 2);

        parameters.set("alpha", 0.001);

        // FASK GFCI Concatenated
        parameters.set("depth", -1);
        parameters.set("numRuns", 10);
        parameters.set("randomSelectionSize", 1);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("includePositiveCoefs"));
        statistics.add(new ParameterColumn("includeNegativeCoefs"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedTime());

        Simulations simulations = new Simulations();

        simulations.add(new LinearFisherModel(new RandomForward()));

        Algorithms algorithms = new Algorithms();

        algorithms.add(new Fci(new SemBicTest()));
        algorithms.add(new Rfci(new SemBicTest()));
        algorithms.add(new Gfci(new SemBicTest(), new SemBicScore()));
        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(false);
        comparison.setSaveGraphs(false);
        comparison.setTabDelimitedTables(false);
        comparison.setSaveGraphs(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.PAG_of_the_true_DAG);

        String directory = "fask_gfci1";

        comparison.compareFromSimulations(directory, simulations, algorithms, statistics, parameters);
    }
    
    
    public static void main(String... args) {
        new TestFaskGfci().TestFaskGfci();
    }
}





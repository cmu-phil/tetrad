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
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.*;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.independence.ConditionalGaussianLRT;
import edu.cmu.tetrad.algcomparison.score.ConditionalGaussianBicScore;
import edu.cmu.tetrad.algcomparison.simulation.*;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;
import org.junit.Test;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class TestConditionalGaussianSimulation {

    public void test1() {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 1);
        parameters.set("numMeasures", 100);
        parameters.set("avgDegree", 4);
        parameters.set("sampleSize", 1000);
        parameters.set("penaltyDiscount", 4);

        parameters.set("maxDegree", 6);

        parameters.set("numCategories", 2, 3, 4, 5);
        parameters.set("percentDiscrete", 50);

        parameters.set("assumeMixed", false);

        parameters.set("intervalBetweenRecordings", 10);

        parameters.set("varLow", .3);
        parameters.set("varHigh", 2);
        parameters.set("coefLow", .5);
        parameters.set("coefHigh", 1.2);
        parameters.set("coefSymmetric", true);
        parameters.set("meanLow", 0);
        parameters.set("meanHigh", 1);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("numCategories"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedTime());

        statistics.setWeight("AP", 1.0);
        statistics.setWeight("AR", 0.5);

        Algorithms algorithms = new Algorithms();

        algorithms.add(new Fgs(new ConditionalGaussianBicScore()));
        algorithms.add(new PcMax(new ConditionalGaussianLRT()));

        Simulations simulations = new Simulations();


        simulations.add(getConditionalGaussianSimulation());

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(false);
        comparison.setShowSimulationIndices(false);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(false);
        comparison.setSaveGraphs(true);

        comparison.setTabDelimitedTables(false);

        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
    }

    private ConditionalGaussianSimulation getConditionalGaussianSimulation() {
        return new ConditionalGaussianSimulation(new RandomForward());
    }

    public static void main(String...args) {
        new TestConditionalGaussianSimulation().test1();
    }
}





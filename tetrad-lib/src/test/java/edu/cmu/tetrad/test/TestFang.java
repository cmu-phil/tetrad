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
import edu.cmu.tetrad.algcomparison.algorithm.multi.CcdMaxConcatenated;
import edu.cmu.tetrad.algcomparison.algorithm.multi.Fang;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.CcdMax;
import edu.cmu.tetrad.algcomparison.graph.Cyclic;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.algcomparison.simulation.LoadContinuousDataAndSingleGraph;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.algcomparison.statistic.TwoCycleFalseNegative;
import edu.cmu.tetrad.algcomparison.statistic.TwoCycleFalsePositive;
import edu.cmu.tetrad.algcomparison.statistic.TwoCycleTruePositive;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import static edu.cmu.tetrad.util.StatUtils.*;
import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.signum;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class TestFang {

    public void test0() {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 5);
        parameters.set("sampleSize", 1000);
        parameters.set("avgDegree", 2);
        parameters.set("numMeasures", 50);
        parameters.set("maxDegree", 1000);
        parameters.set("maxIndegree", 1000);
        parameters.set("maxOutdegree", 1000);

        parameters.set("coefLow", .2);
        parameters.set("coefHigh", .6);
        parameters.set("varLow", .2);
        parameters.set("varHigh", .4);
        parameters.set("coefSymmetric", true);
        parameters.set("probCycle", 1.0);
        parameters.set("probTwoCycle", .2);
        parameters.set("intervalBetweenShocks", 1);
        parameters.set("intervalBetweenRecordings", 1);

        parameters.set("alpha", 0.001);
        parameters.set("depth", 4);
        parameters.set("orientVisibleFeedbackLoops", true);
        parameters.set("doColliderOrientation", true);
        parameters.set("useMaxPOrientationHeuristic", true);
        parameters.set("maxPOrientationMaxPathLength", 3);
        parameters.set("applyR1", true);
        parameters.set("orientTowardDConnections", true);
        parameters.set("gaussianErrors", false);
        parameters.set("assumeIID", false);
        parameters.set("collapseTiers", true);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("avgDegree"));
        statistics.add(new ParameterColumn("numMeasures"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new TwoCycleFalsePositive());
        statistics.add(new TwoCycleRecall());
        statistics.add(new ElapsedTime());

        Simulations simulations = new Simulations();
        simulations.add(new LinearFisherModel(new Cyclic()));

        Algorithms algorithms = new Algorithms();
        algorithms.add(new CcdMax(new FisherZ()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(false);
        comparison.setShowSimulationIndices(false);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(false);
        comparison.setSaveGraphs(true);

        comparison.setTabDelimitedTables(false);

        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
//        comparison.compareFromFiles("comparison", algorithms, statistics, parameters);
//        comparison.saveToFiles("comparison", new LinearFisherModel(new RandomForward()), parameters);

    }


    public void TestRuben() {
        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 3);
        parameters.set("depth", -1);

        parameters.set("numRandomSelections", 10);
        parameters.set("randomSelectionSize", 10);
        parameters.set("Structure", "Placeholder");

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("Structure"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new TwoCyclePrecision());
        statistics.add(new TwoCycleRecall());
        statistics.add(new TwoCycleFalsePositive());
        statistics.add(new TwoCycleFalseNegative());
        statistics.add(new TwoCycleTruePositive());
        statistics.add(new ElapsedTime());

        Simulations simulations = new Simulations();

        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure1_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure1_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure2_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure2_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure3_amp_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure3_amp_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure3_contr_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure4_amp_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure4_amp_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure4_contr_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure5_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure5_contr"));

        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure2_amp_c4"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure2_contr_c4"));

        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure2_contr_p2n6"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Structure2_contr_p6n2"));


        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/ComplexMatrix_1"));

        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI-selected/Diamond"));

        Algorithms algorithms = new Algorithms();
        algorithms.add(new Fang());
//        algorithms.add(new CcdMax(new SemBicTest()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(false);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(false);
        comparison.setSaveGraphs(false);

        comparison.setTabDelimitedTables(false);

        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
    }

    public void TestCycles_Data_fMRI_FANG() {
        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 6);
        parameters.set("depth", -1);
        parameters.set("maxCoef", 0.7);
        parameters.set("depErrorsAlpha", 0.0001);

        parameters.set("numRandomSelections", 10);
        parameters.set("randomSelectionSize", 10);
        parameters.set("Structure", "Placeholder");

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("Structure"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new TwoCyclePrecision());
        statistics.add(new TwoCycleRecall());
        statistics.add(new TwoCycleFalsePositive());
        statistics.add(new TwoCycleFalseNegative());
        statistics.add(new TwoCycleTruePositive());
        statistics.add(new ElapsedTime());

        Simulations simulations = new Simulations();

        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network1_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network2_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network3_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network4_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network5_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network5_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network5_contr_p2n6"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network5_contr_p6n2"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network6_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network6_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network7_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network7_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network8_amp_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network8_amp_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network8_contr_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network9_amp_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network9_amp_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network9_contr_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Diamond"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Markov_Complex_1"));

        Algorithms algorithms = new Algorithms();
        algorithms.add(new Fang());

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(false);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(false);
        comparison.setSaveGraphs(false);

        comparison.setTabDelimitedTables(false);

        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
    }

    public void TestCycles_Data_fMRI_CCDMax() {
        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 3);
        parameters.set("depth", -1);
        parameters.set("orientVisibleFeedbackLoops", true);
        parameters.set("doColliderOrientation", true);
        parameters.set("useMaxPOrientationHeuristic", true);
        parameters.set("maxPOrientationMaxPathLength", 3);
        parameters.set("applyR1", true);
        parameters.set("orientTowardDConnections", true);
        parameters.set("gaussianErrors", false);
        parameters.set("assumeIID", false);
        parameters.set("collapseTiers", true);

        parameters.set("numRandomSelections", 60);
        parameters.set("randomSelectionSize", 10);
        parameters.set("Structure", "Placeholder");

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("Structure"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new TwoCyclePrecision());
        statistics.add(new TwoCycleRecall());
        statistics.add(new TwoCycleFalsePositive());
        statistics.add(new TwoCycleFalseNegative());
        statistics.add(new TwoCycleTruePositive());
        statistics.add(new ElapsedTime());

        Simulations simulations = new Simulations();

        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network1_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network2_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network3_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network4_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network5_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network5_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network5_contr_p2n6"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network5_contr_p6n2"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network6_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network6_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network7_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network7_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network8_amp_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network8_amp_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network8_contr_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network9_amp_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network9_amp_contr"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Network9_contr_amp"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Diamond"));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                "/Users/jdramsey/Downloads/Cycles_Data_fMRI/Markov_Complex_1"));

        Algorithms algorithms = new Algorithms();
//        algorithms.add(new FangConcatenated());
        algorithms.add(new CcdMaxConcatenated(new SemBicTest()));

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(false);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(false);
        comparison.setSaveGraphs(false);

        comparison.setTabDelimitedTables(false);

        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
    }

    public void TestSmith() {
        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 4);
        parameters.set("depth", -1);

        parameters.set("numRandomSelections", 10);
        parameters.set("randomSelectionSize", 10);
        parameters.set("Structure", "Placeholder");


        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("Structure"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new TwoCyclePrecision());
        statistics.add(new TwoCycleRecall());
        statistics.add(new TwoCycleFalsePositive());
        statistics.add(new TwoCycleFalseNegative());
        statistics.add(new TwoCycleTruePositive());
        statistics.add(new ElapsedTime());

        Simulations simulations = new Simulations();

        String path = "/Users/jdramsey/Downloads/smithsim.algcomp/";

        simulations.add(new LoadContinuousDataSmithSim(path + "1"));
        simulations.add(new LoadContinuousDataSmithSim(path + "2"));
        simulations.add(new LoadContinuousDataSmithSim(path + "3"));
        simulations.add(new LoadContinuousDataSmithSim(path + "4"));
        simulations.add(new LoadContinuousDataSmithSim(path + "5"));
        simulations.add(new LoadContinuousDataSmithSim(path + "6"));
        simulations.add(new LoadContinuousDataSmithSim(path + "7"));
        simulations.add(new LoadContinuousDataSmithSim(path + "8"));
        simulations.add(new LoadContinuousDataSmithSim(path + "9"));
        simulations.add(new LoadContinuousDataSmithSim(path + "10"));
        simulations.add(new LoadContinuousDataSmithSim(path + "11"));
        simulations.add(new LoadContinuousDataSmithSim(path + "12"));
        simulations.add(new LoadContinuousDataSmithSim(path + "13"));
        simulations.add(new LoadContinuousDataSmithSim(path + "14"));
        simulations.add(new LoadContinuousDataSmithSim(path + "15"));
        simulations.add(new LoadContinuousDataSmithSim(path + "16"));
        simulations.add(new LoadContinuousDataSmithSim(path + "17"));
        simulations.add(new LoadContinuousDataSmithSim(path + "18"));
        simulations.add(new LoadContinuousDataSmithSim(path + "19"));
        simulations.add(new LoadContinuousDataSmithSim(path + "20"));
        simulations.add(new LoadContinuousDataSmithSim(path + "21"));
        simulations.add(new LoadContinuousDataSmithSim(path + "22"));
        simulations.add(new LoadContinuousDataSmithSim(path + "22_2"));
        simulations.add(new LoadContinuousDataSmithSim(path + "23"));
        simulations.add(new LoadContinuousDataSmithSim(path + "24"));
        simulations.add(new LoadContinuousDataSmithSim(path + "25"));
        simulations.add(new LoadContinuousDataSmithSim(path + "26"));
        simulations.add(new LoadContinuousDataSmithSim(path + "27"));
        simulations.add(new LoadContinuousDataSmithSim(path + "28"));

        Algorithms algorithms = new Algorithms();
//        algorithms.add(new ImagesSemBic());
        algorithms.add(new Fang());

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(false);
        comparison.setShowSimulationIndices(false);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(false);
        comparison.setSaveGraphs(true);

        comparison.setTabDelimitedTables(false);

        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
//        comparison.compareFromFiles("comparison", algorithms, statistics, parameters);
//        comparison.saveToFiles("comparison", new LinearFisherModel(new RandomForward()), parameters);

    }

    public void TestPwwd7() {
        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 1);
        parameters.set("depth", 4);
        parameters.set("maxCoef", .5);

        parameters.set("numRandomSelections", 5);
        parameters.set("randomSelectionSize", 5);

        parameters.set("Structure", "Placeholder");

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("Structure"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new TwoCyclePrecision());
        statistics.add(new TwoCycleRecall());
        statistics.add(new TwoCycleFalsePositive());
        statistics.add(new TwoCycleFalseNegative());
        statistics.add(new TwoCycleTruePositive());
        statistics.add(new ElapsedTime());

        Simulations simulations = new Simulations();

        String path = "/Users/jdramsey/Downloads/pwdd7.algcomp/";

        simulations.add(new LoadContinuousDataSmithSim(path + "1"));
        simulations.add(new LoadContinuousDataSmithSim(path + "2"));
        simulations.add(new LoadContinuousDataSmithSim(path + "3"));
        simulations.add(new LoadContinuousDataSmithSim(path + "4"));
        simulations.add(new LoadContinuousDataSmithSim(path + "5"));
        simulations.add(new LoadContinuousDataSmithSim(path + "6"));
        simulations.add(new LoadContinuousDataSmithSim(path + "7"));
        simulations.add(new LoadContinuousDataSmithSim(path + "8"));
        simulations.add(new LoadContinuousDataSmithSim(path + "9"));
        simulations.add(new LoadContinuousDataSmithSim(path + "10"));
        simulations.add(new LoadContinuousDataSmithSim(path + "11"));
        simulations.add(new LoadContinuousDataSmithSim(path + "12"));
//        simulations.add(new LoadContinuousDataSmithSim(path + "13"));
        simulations.add(new LoadContinuousDataSmithSim(path + "14"));
        simulations.add(new LoadContinuousDataSmithSim(path + "15"));
        simulations.add(new LoadContinuousDataSmithSim(path + "16"));
        simulations.add(new LoadContinuousDataSmithSim(path + "17"));
        simulations.add(new LoadContinuousDataSmithSim(path + "18"));
        simulations.add(new LoadContinuousDataSmithSim(path + "19"));
        simulations.add(new LoadContinuousDataSmithSim(path + "20"));
        simulations.add(new LoadContinuousDataSmithSim(path + "22"));
        simulations.add(new LoadContinuousDataSmithSim(path + "22_2"));
        simulations.add(new LoadContinuousDataSmithSim(path + "23"));
        simulations.add(new LoadContinuousDataSmithSim(path + "24"));
        simulations.add(new LoadContinuousDataSmithSim(path + "25"));
        simulations.add(new LoadContinuousDataSmithSim(path + "26"));
        simulations.add(new LoadContinuousDataSmithSim(path + "27"));
        simulations.add(new LoadContinuousDataSmithSim(path + "28"));

        Algorithms algorithms = new Algorithms();
        algorithms.add(new Fang());
//        algorithms.add(new ImagesSemBic());

        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(false);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setParallelized(false);
        comparison.setSaveGraphs(true);

        comparison.setTabDelimitedTables(false);

        comparison.compareFromSimulations("comparison", simulations, algorithms, statistics, parameters);
    }

//    @Test
//    public void loadAlexandersDataset() {
//        String path = "/Users/jdramsey/Downloads/converted_rndhrs_numeric_only.txt";
//
//        File file = new File(path);
//
//        DataReader reader = new DataReader();
//        reader.setVariablesSupplied(true);
//        reader.setDelimiter(DelimiterType.WHITESPACE);
//        reader.setMissingValueMarker("NA");
//
//        try {
//            DataSet dataSet = reader.parseTabular(file);
//            System.out.println(dataSet);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

//    public void loadAlexandersDataset2() {
//        String path = "/Users/jdramsey/Downloads/fails_328_lines.txt";
//
//        File file = new File(path);
//
//        AbstractContinuousDataReader reader = new TabularContinuousDataReader(
//                Paths.get("/Users/jdramsey/Downloads/converted_rndhrs_numeric_only.txt"), ' ');
//
//        DataReader reader = new DataReader();
//        reader.setVariablesSupplied(true);
//        reader.setDelimiter(DelimiterType.WHITESPACE);
//        reader.setMissingValueMarker("NA");
//
//        try {
//            DataSet dataSet = reader.parseTabular(file);
//            System.out.println(dataSet);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    @Test
    public void test5() {
        for (int j = 0; j < 10; j++) {

            int n = 1000;
            double rho = .1;

            double[] eX = new double[n];
            double[] eY = new double[n];
            double[] x = new double[n];
            double[] y = new double[n];

            BetaDistribution d = new BetaDistribution(2, 5);

            for (int i = 0; i < n; i++) {
                eX[i] = d.sample();
                eY[i] = d.sample();
                x[i] = eX[i];
                y[i] = rho * x[i] + eY[i];
//
//                x[i] = signum(skewness(x)) * x[i];
//                y[i] = signum(skewness(y)) * y[i];
            }

            standardizeData(x);
            standardizeData(y);

            System.out.println("cov = " + s(x, y, eY));
        }
    }

    private double s(double[] x, double[] y, double[] eY) {
        double exy = 0.0;

        double ex = 0.0;
        double ey = 0.0;

        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (y[k] > 0) {
                exy += x[k] * eY[k];
                ex += x[k];
                ey += y[k];
                n++;
            }
        }

        exy /= n;
        ex /= n;
        ey /= n;

        return (exy - ex * ey);
    }

    public static void standardizeData(double[] data) {
        double sum = 0.0;

        for (int i = 0; i < data.length; i++) {
            sum += data[i];
        }

        double mean = sum / data.length;

        for (int i = 0; i < data.length; i++) {
            data[i] -= mean;
        }

        double norm = 0.0;

        for (int i = 0; i < data.length; i++) {
            double v = data[i];
            norm += v * v;
        }

        norm = Math.sqrt(norm / (data.length - 1));

        for (int i = 0; i < data.length; i++) {
            data[i] /= norm;
        }
    }

    @Test
    public void testAutistic1() {
        List<DataSet> autistic = new ArrayList<>();
        List<DataSet> typical = new ArrayList<>();

        Map<DataSet, String> filenames = new HashMap<>();
        Map<Graph, String> graphnames = new HashMap<>();

        DataReader reader = new DataReader();

//        String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_108_Variable";
//
//        try {
//            autistic.add(reader.parseTabular(new File(path, "autistic_normal_ROI_data_spline_smooth_clean_001.txt")));
//            autistic.add(reader.parseTabular(new File(path, "autistic_normal_ROI_data_spline_smooth_clean_002.txt")));
//            autistic.add(reader.parseTabular(new File(path, "autistic_normal_ROI_data_spline_smooth_clean_003.txt")));
//            autistic.add(reader.parseTabular(new File(path, "autistic_normal_ROI_data_spline_smooth_clean_004.txt")));
//            autistic.add(reader.parseTabular(new File(path, "autistic_normal_ROI_data_spline_smooth_clean_005.txt")));
//            autistic.add(reader.parseTabular(new File(path, "autistic_normal_ROI_data_spline_smooth_clean_006.txt")));
//            autistic.add(reader.parseTabular(new File(path, "autistic_normal_ROI_data_spline_smooth_clean_007.txt")));
//            autistic.add(reader.parseTabular(new File(path, "autistic_normal_ROI_data_spline_smooth_clean_008.txt")));
//            autistic.add(reader.parseTabular(new File(path, "autistic_normal_ROI_data_spline_smooth_clean_009.txt")));
//            autistic.add(reader.parseTabular(new File(path, "autistic_normal_ROI_data_spline_smooth_clean_010.txt")));
//
//            typical.add(reader.parseTabular(new File(path, "typical_normal_ROI_data_spline_smooth_clean_001.txt")));
//            typical.add(reader.parseTabular(new File(path, "typical_normal_ROI_data_spline_smooth_clean_002.txt")));
//            typical.add(reader.parseTabular(new File(path, "typical_normal_ROI_data_spline_smooth_clean_003.txt")));
//            typical.add(reader.parseTabular(new File(path, "typical_normal_ROI_data_spline_smooth_clean_004.txt")));
//            typical.add(reader.parseTabular(new File(path, "typical_normal_ROI_data_spline_smooth_clean_005.txt")));
//            typical.add(reader.parseTabular(new File(path, "typical_normal_ROI_data_spline_smooth_clean_006.txt")));
//            typical.add(reader.parseTabular(new File(path, "typical_normal_ROI_data_spline_smooth_clean_007.txt")));
//            typical.add(reader.parseTabular(new File(path, "typical_normal_ROI_data_spline_smooth_clean_008.txt")));
//            typical.add(reader.parseTabular(new File(path, "typical_normal_ROI_data_spline_smooth_clean_009.txt")));
//            typical.add(reader.parseTabular(new File(path, "typical_normal_ROI_data_spline_smooth_clean_010.txt")));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        try {
            String path = "/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/USM_Datasets2";
            File dir = new File(path);

            for (File file : dir.listFiles()) {
                String name = file.getName();

                if (name.startsWith("ROI") && !name.contains("graph")) {
                    DataSet dataSet = reader.parseTabular(new File(path, name));
                    filenames.put(dataSet, name);

                    if (name.contains("autistic")) {
                        autistic.add(dataSet);
                    } else {
                        typical.add(dataSet);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 1);
        parameters.set("depth", 3);
        parameters.set("maxCoef", .7);
        parameters.set("depErrorsAlpha", 0.001);
        parameters.set("markDependentResiduals", false);

        parameters.set("numRandomSelections", 50);
        parameters.set("randomSelectionSize", 1);
        parameters.set("Structure", "Placeholder");

        int fromAutistic = autistic.size();
        int fromTypical = typical.size();

        Fang fang = new Fang();

        List<Graph> allAusticGraphs = runFangOnSubsets(autistic, parameters, fang, fromAutistic,
                filenames, graphnames);
        List<Graph> allTypicalGraphs = runFangOnSubsets(typical, parameters, fang, fromTypical,
                filenames, graphnames);

        Set<String> allNames = new HashSet<>();
        List<Node> nodes = new ArrayList<>();

        for (Graph graph : allAusticGraphs) {
            for (Node node : graph.getNodes()) {
                if (!allNames.contains(node.getName())) {
                    nodes.add(node);
                    allNames.add(node.getName());
                }
            }
        }

        allAusticGraphs = replaceNodes(allAusticGraphs, nodes);
        allTypicalGraphs = replaceNodes(allTypicalGraphs, nodes);

        List<Graph> allGraphs = new ArrayList<>(allAusticGraphs);
        allGraphs.addAll(allTypicalGraphs);

        for (int i = 0; i < allGraphs.size(); i++) {

            List<Graph> trainingAutisticGraphs = new ArrayList<>(allAusticGraphs);
            List<Graph> trainingTypicalGraphs = new ArrayList<>(allTypicalGraphs);

            Graph testGraph = allGraphs.get(i);

            trainingAutisticGraphs.remove(testGraph);
            trainingTypicalGraphs.remove(testGraph);

            List<Graph> trainingGraphs = new ArrayList<>(trainingAutisticGraphs);
            trainingGraphs.addAll(trainingTypicalGraphs);

            Map<Edge, Double> autisticEdgeCount = new HashMap<>();
            Map<Edge, Double> typicalEdgeCount = new HashMap<>();

            Set<Edge> allEdgesSet = new HashSet<>();

            for (Graph graph : trainingAutisticGraphs) {
                for (Edge edge : graph.getEdges()) {
                    countEdge(autisticEdgeCount, edge);
                }

                allEdgesSet.addAll(graph.getEdges());
            }

            for (Graph graph : trainingTypicalGraphs) {
                for (Edge edge : graph.getEdges()) {
                    countEdge(typicalEdgeCount, edge);
                }

                allEdgesSet.addAll(graph.getEdges());
            }

            List<Edge> allEdges = new ArrayList<>(allEdgesSet);

            double[] truth = new double[trainingGraphs.size()];

            for (int k = 0; k < trainingAutisticGraphs.size(); k++) {
                truth[k] = 1.0;
            }

            for (int k = autistic.size(); k < trainingGraphs.size(); k++) {
                truth[k] = 0.0;
            }

            List<Edge> sublist = new ArrayList<>();

            List<Edge> forAutisismIfPresent = new ArrayList<>();
            List<Edge> forAutisismIfAbsent = new ArrayList<>();
            List<Edge> forTypicalIfPresent = new ArrayList<>();
            List<Edge> forTypicalIfAbsent = new ArrayList<>();

            for (int y = 0; y < allEdges.size(); y++) {
                double[] est = new double[trainingGraphs.size()];

                for (int k = 0; k < trainingGraphs.size(); k++) {
                    est[k] = (trainingGraphs.get(k).containsEdge(allEdges.get(y))) ? 1.0 : 0.0;
                }

                if (cond(est, truth, 1, 1)) {
                    forAutisismIfPresent.add(allEdges.get(y));
                    sublist.add(allEdges.get(y));
                }

                if (cond(est, truth, 0, 1)) {
                    forAutisismIfAbsent.add(allEdges.get(y));
                    sublist.add(allEdges.get(y));
                }

                if (cond(est, truth, 1, 0)) {
                    forTypicalIfPresent.add(allEdges.get(y));
                    sublist.add(allEdges.get(y));
                }

                if (cond(est, truth, 0, 0)) {
                    forTypicalIfAbsent.add(allEdges.get(y));
                    sublist.add(allEdges.get(y));
                }
            }

            PrintStream out = null;

            try {
                File dir = new File("/Users/jdramsey/Downloads/alldata");
                dir.mkdirs();
                out = new PrintStream(new File(dir, "data" + (i + 1) + ".txt"));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            for (int j = 0; j < sublist.size(); j++) {
                out.print("X" + (j + 1) + "\t");
            }

            out.println("T");

            for (int k = 0; k < allGraphs.size(); k++) {
                for (int j = 0; j < sublist.size(); j++) {
                    out.print(allGraphs.get(k).containsEdge(sublist.get(j)) ? "1\t" : "0\t");
                }

                out.println(allAusticGraphs.contains(allGraphs.get(k)) ? "1" : "0");
            }

            out.close();

            PrintStream keyOut = null;

            try {
                File dir = new File("/Users/jdramsey/Downloads/allkeys");
                dir.mkdirs();
                keyOut = new PrintStream(new File(dir, "key" + (i + 1) + ".txt"));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            for (int j = 0; j < sublist.size(); j++) {
                keyOut.println("X" + (j + 1) + ". " + sublist.get(j));
            }

            for (Graph graph : allGraphs) {
                if (!graph.equals(testGraph)) continue;

                int numAutistic = 0;
                int numTypical = 0;

                List<Edge> present = new ArrayList<>();
                List<Edge> absent = new ArrayList<>();

                for (Edge edge : forAutisismIfPresent) {
                    if (graph.containsEdge(edge)) {
//                        present.add(edge);
//                        numAutistic++;
                    }
                }

                for (Edge edge : forAutisismIfAbsent) {
//                    if (edgeFromTo(edge, "Cingulum_Post_R", "Cingulum_Post_L")) {
                        if (!graph.containsEdge(edge) && graph.containsEdge(edge.reverse())) {
                            absent.add(edge);
                            numAutistic++;
                        }
//                    }
                }
//
                for (Edge edge : forTypicalIfPresent) {
                    if (graph.containsEdge(edge)) {
//                        present.add(edge);
//                        numTypical++;
                    }
                }
//
                for (Edge edge : forTypicalIfAbsent) {
                    if (!graph.containsEdge(edge)) {
//                        absent.add(edge);
//                        numTypical++;
                    }
                }

                String name = "" + (i + 1);//graphnames.get(graph);

                if (numAutistic > 0 && numTypical == 0 && allAusticGraphs.contains(graph)) {
                    System.out.println(name + ". Autistic " + numAutistic + " " + numTypical);
                } else if (numTypical > 0 && numAutistic == 0 && allTypicalGraphs.contains(graph)) {
                    System.out.println(name + ". Not autistic " + numAutistic + " " + numTypical);
                } else if (numAutistic > numTypical && !allAusticGraphs.contains(graph)) {
                    System.out.println(name + ". FP " + numAutistic + " " + numTypical);
                } else if (numAutistic < numTypical && !allTypicalGraphs.contains(graph)) {
                    System.out.println(name + ". FN " + numAutistic + " " + numTypical);
                } else {
                    System.out.println(name + ". - " + numAutistic + " " + numTypical);
                }

                for (int t = 0; t < present.size(); t++) {
                    System.out.println("..... present " + present.get(t));
                }

                for (int t = 0; t < absent.size(); t++) {
                    System.out.println("..... absent " + absent.get(t));
                }
            }

        }
    }

    private boolean edgeFromTo(Edge edge, String left, String right) {
        return edge.getNode1().getName().equals(left) && edge.getNode2().getName().equals(right);
    }

    // Returns true if a2 = j on condition that a1 = i.
    private boolean cond(double[] a1, double[] a2, int i, int j) {
        int occurs = 0;
        int isTheCase = 0;

        for (int w = 0; w < a1.length; w++) {
            if (a1[w] == i) {
                occurs++;

                if (a2[w] == j) {
                    isTheCase++;
                }
            }
        }

        return occurs >= 10 && isTheCase == occurs;
    }

    private Double count(Map<Edge, Double> edges1Count, Edge edge) {
        if (edges1Count.get(edge) == null) return 0.0;
        return edges1Count.get(edge);
    }

    private void countEdge(Map<Edge, Double> map, Edge edge) {
        if (count(map, edge) == null) {
            map.put(edge, 0.0);
        }

        map.put(edge, count(map, edge) + 1);
    }

    private List<Graph> replaceNodes(List<Graph> graphs, List<Node> nodes) {
        List<Graph> replaced = new ArrayList<>();

        for (Graph graph : graphs) {
            replaced.add(GraphUtils.replaceNodes(graph, nodes));
        }

        return replaced;
    }

    private List<Graph> runFangOnSubsets(List<DataSet> dataSets, Parameters parameters, Fang fang,
                                         int subsetSize, Map<DataSet, String> filenames, Map<Graph, String> graphNames) {
        List<Graph> graphs = new ArrayList<>();

        List<DataSet> copy = new ArrayList<>(dataSets);
//        Collections.shuffle(copy);

        for (int i = 0; i < subsetSize; i++) {
            Graph search = fang.search(copy.get(i), parameters);
            graphs.add(search);
            graphNames.put(search, filenames.get(copy.get(i)));
        }

        return graphs;
    }

    public static void main(String... args) {
        new TestFang().TestCycles_Data_fMRI_FANG();
    }
}





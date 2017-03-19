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
import edu.cmu.tetrad.data.DelimiterType;
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
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

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

        Parameters parameters = new Parameters();

        parameters.set("penaltyDiscount", 1);
        parameters.set("depth", 3);
        parameters.set("maxCoef", .7);
        parameters.set("depErrorsAlpha", 0.001);
        parameters.set("markDependentResiduals", false);

        parameters.set("numRandomSelections", 50);
        parameters.set("randomSelectionSize", 1);
        parameters.set("Structure", "Placeholder");

//        Files train = new Files("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_108_Variable", parameters);
        Files train = new Files("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/USM_Datasets2", parameters);
//        Files train = new Files("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Whole_Cerebellum_Scans", parameters);
//
        Files test = new Files("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Joe_108_Variable", parameters);
//        Files test = new Files("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/USM_Datasets2", parameters);
//        Files test = new Files("/Users/jdramsey/Documents/LAB_NOTEBOOK.2012.04.20/data/Whole_Cerebellum_Scans", parameters);

        Set<String> allNames = new HashSet<>();
        List<Node> nodes = new ArrayList<>();

        List<Graph> trainGraphs = train.getGraphs();
        List<Graph> testGraphs = test.getGraphs();

        for (Graph graph : trainGraphs) {
            for (Node node : graph.getNodes()) {
                if (!allNames.contains(node.getName())) {
                    nodes.add(node);
                    allNames.add(node.getName());
                }
            }
        }

        for (Graph graph : testGraphs) {
            for (Node node : graph.getNodes()) {
                if (!allNames.contains(node.getName())) {
                    nodes.add(node);
                    allNames.add(node.getName());
                }
            }
        }

        trainGraphs = replaceNodes(trainGraphs, nodes);
        testGraphs = replaceNodes(testGraphs, nodes);

//        trainTest(train, test, trainGraphs, testGraphs);
        leaveOneOut(train, nodes, trainGraphs);
    }

    private void trainTest(Files train, Files test, List<Graph> trainGraphs, List<Graph> testGraphs) {
        int numTp = 0;
        int numFp = 0;
        int numMeh = 0;

        List<Edge> allEdges = getAllEdges(trainGraphs, train.getTypes(), trainGraphs);
        List<List<Edge>> ret = train(trainGraphs, allEdges, train.getTypes());

        for (int i = 0; i < testGraphs.size(); i++) {
            int _class = test(i, test.getFilenames(), testGraphs, test.getTypes(), ret);

            if (_class == 1) numTp++;
            if (_class == -1) numFp++;
            if (_class == 0) numMeh++;
        }

        System.out.println();
        System.out.println("# TP = " + numTp);
        System.out.println("# FP = " + numFp);
        System.out.println("# Unclassified = " + numMeh);

        NumberFormat nf = new DecimalFormat("0.00");
        System.out.println("Precision = " + nf.format((numTp / (double) (numTp + numFp))));
        System.out.println();
    }

    private void leaveOneOut(Files train, List<Node> nodes, List<Graph> trainGraphs) {
        int numTp = 0;
        int numFp = 0;
        int numUnclassified = 0;

        for (int i = 0; i < trainGraphs.size(); i++) {
            List<Graph> trainingGraphs = new ArrayList<>(trainGraphs);
            trainingGraphs.remove(trainGraphs.get(i));
            List<Edge> allEdges = getAllEdges(trainGraphs, train.getTypes(), trainingGraphs);

            List<List<Edge>> ret = train(trainingGraphs, allEdges, train.getTypes());
            int _class = test(i, train.getFilenames(), trainGraphs, train.getTypes(), ret);

            if (_class == 1) numTp++;
            if (_class == -1) numFp++;
            if (_class == 0) numUnclassified++;

            printFiles(trainGraphs, train.getTypes(), i, ret);
        }

        System.out.println();
        System.out.println("# TP = " + numTp);
        System.out.println("# FP = " + numFp);
        System.out.println("# Unclassified = " + numUnclassified);

        NumberFormat nf = new DecimalFormat("0.00");
        System.out.println("Precision = " + nf.format((numTp / (double) (numTp + numFp))));
        System.out.println();
    }

    private class Files {
        private List<String> filenames = new ArrayList<>();
        private List<DataSet> datasets = new ArrayList<>();
        private List<Graph> graphs = new ArrayList<>();
        private List<Boolean> types = new ArrayList<>();

        public Files(String path, Parameters parameters) {
            loadFiles(path, parameters);
        }

        private void loadFiles(String path, Parameters parameters) {
            DataReader reader = new DataReader();
            reader.setVariablesSupplied(true);
            reader.setDelimiter(DelimiterType.TAB);

            File dir = new File(path);

            for (File file : dir.listFiles()) {
                String name = file.getName();

                if (name.contains("ROI_data") && !name.contains("graph")) {
                    try {
                        if (name.contains("autistic")) {
                            types.add(true);
                            DataSet dataSet = reader.parseTabular(new File(path, name));
                            filenames.add(name);
                            Fang fang = new Fang();
                            Graph search = fang.search(dataSet, parameters);
                            graphs.add(search);
                        } else if (name.contains("typical")) {
                            types.add(false);
                            DataSet dataSet = reader.parseTabular(new File(path, name));
                            filenames.add(name);
                            Fang fang = new Fang();
                            Graph search = fang.search(dataSet, parameters);
                            graphs.add(search);
                        }
                    } catch (IOException e) {
                        System.out.println("File " + name + " could not be parsed.");
                    }
                }
            }
        }


        public List<String> getFilenames() {
            return filenames;
        }

        public void setFilenames(List<String> filenames) {
            this.filenames = filenames;
        }

        public List<DataSet> getDatasets() {
            return datasets;
        }

        public void setDatasets(List<DataSet> datasets) {
            this.datasets = datasets;
        }

        public List<Graph> getGraphs() {
            return graphs;
        }

        public void setGraphs(List<Graph> graphs) {
            this.graphs = graphs;
        }

        public List<Boolean> getTypes() {
            return types;
        }

        public void setTypes(List<Boolean> types) {
            this.types = types;
        }
    }

    private List<Edge> getAllEdges(List<Graph> _graphs, List<Boolean> types, List<Graph> trainingGraphs) {
        Map<Edge, Double> autisticEdgeCount = new HashMap<>();
        Map<Edge, Double> typicalEdgeCount = new HashMap<>();

        Set<Edge> allEdgesSet = new HashSet<>();

        for (int k = 0; k < trainingGraphs.size(); k++) {
            for (Edge edge : trainingGraphs.get(k).getEdges()) {
                if (types.get(k)) {
                    countEdge(autisticEdgeCount, edge);
                } else {
                    countEdge(typicalEdgeCount, edge);
                }
            }

            allEdgesSet.addAll(_graphs.get(k).getEdges());
        }
        return new ArrayList<>(allEdgesSet);
    }

    private void printFiles(List<Graph> _graphs, List<Boolean> types, int i, List<List<Edge>> ret) {
        PrintStream out = null;
        List<Edge> sublist = ret.get(4);

        try {
            File dir2 = new File("/Users/jdramsey/Downloads/alldata");
            dir2.mkdirs();
            out = new PrintStream(new File(dir2, "data" + (i + 1) + ".txt"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        for (int j = 0; j < sublist.size(); j++) {
            out.print("X" + (j + 1) + "\t");
        }

        out.println("T");

        for (int k = 0; k < _graphs.size(); k++) {
            for (int j = 0; j < sublist.size(); j++) {
                out.print(_graphs.get(k).containsEdge(sublist.get(j)) ? "1\t" : "0\t");
            }

            out.println(types.get(k) ? "1" : "0");
        }

        out.close();

        PrintStream keyOut = null;

        try {
            File dir2 = new File("/Users/jdramsey/Downloads/allkeys");
            dir2.mkdirs();
            keyOut = new PrintStream(new File(dir2, "key" + (i + 1) + ".txt"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        for (int j = 0; j < sublist.size(); j++) {
            keyOut.println("X" + (j + 1) + ". " + sublist.get(j));
        }
    }

    private int test(int i, List<String> _filenames, List<Graph> _graphs, List<Boolean> types, List<List<Edge>> ret) {
        Graph testGraph = _graphs.get(i);

        int numAutistic = 0;
        int numTypical = 0;

        List<Edge> present = new ArrayList<>();
        List<Edge> absent = new ArrayList<>();

        for (Edge edge : ret.get(0)) {
            if (testGraph.containsEdge(edge)) {
//                        present.add(edge);
//                        numAutistic++;
            }
        }

        for (Edge edge : ret.get(1)) {
            if (!testGraph.containsEdge(edge) && testGraph.containsEdge(edge.reverse())) {
                absent.add(edge);
                numAutistic++;
            }
        }
//
        for (Edge edge : ret.get(2)) {
            if (testGraph.containsEdge(edge)) {
//                        present.add(edge);
//                        numTypical++;
            }
        }
//
        for (Edge edge : ret.get(3)) {
            if (!testGraph.containsEdge(edge)) {
//                        absent.add(edge);
//                        numTypical++;
            }
        }

        String name = "" + (i + 1) + ". " + _filenames.get(i) + ". ";

        int tp = 0;
        int fp = 0;

        if (numAutistic > 0 && types.get(i)) {
            System.out.println(name + ". Autistic " + numAutistic + " " + numTypical);
            tp++;
        } else if (numAutistic > numTypical && !types.get(i)) {
            System.out.println(name + ". FP " + numAutistic + " " + numTypical);
            fp++;
        }

        for (Edge aPresent : present) {
            System.out.println("..... present " + aPresent);
        }

        for (Edge anAbsent : absent) {
            System.out.println("..... absent " + anAbsent);
        }

        if (tp > 0) {
            return 1;
        } else if (fp > 0) {
            return -1;
        } else {
            return 0;
        }
    }

    private List<List<Edge>> train(List<Graph> trainingGraphs, List<Edge> allEdges, List<Boolean> types) {

        double[] truth = new double[trainingGraphs.size()];
        int w = 0;

        for (int k = 0; k < trainingGraphs.size(); k++) {
            truth[w++] = types.get(k) ? 1.0 : 0.0;
        }

        List<Edge> forAutisismIfPresent = new ArrayList<>();
        List<Edge> forAutisismIfAbsent = new ArrayList<>();
        List<Edge> forTypicalIfPresent = new ArrayList<>();
        List<Edge> forTypicalIfAbsent = new ArrayList<>();

        for (int y = 0; y < allEdges.size(); y++) {
            double[] est = new double[trainingGraphs.size()];
            int _w = 0;

            for (int k = 0; k < trainingGraphs.size(); k++) {
                est[_w++] = (trainingGraphs.get(k).containsEdge(allEdges.get(y))) ? 1.0 : 0.0;
            }

            if (cond(est, truth, 1, 1)) {
                forAutisismIfPresent.add(allEdges.get(y));
            }

            if (cond(est, truth, 0, 1)) {
                forAutisismIfAbsent.add(allEdges.get(y));
            }

            if (cond(est, truth, 1, 0)) {
                forTypicalIfPresent.add(allEdges.get(y));
            }

            if (cond(est, truth, 0, 0)) {
                forTypicalIfAbsent.add(allEdges.get(y));
            }
        }

        List<Edge> sublist = new ArrayList<>();
        
        sublist.addAll(forAutisismIfPresent);
        sublist.addAll(forAutisismIfAbsent);
        sublist.addAll(forTypicalIfPresent);
        sublist.addAll(forTypicalIfAbsent);

        // return from train.
        List<List<Edge>> ret = new ArrayList<>();
        ret.add(forAutisismIfPresent);
        ret.add(forAutisismIfAbsent);
        ret.add(forTypicalIfPresent);
        ret.add(forTypicalIfAbsent);
        ret.add(sublist);
        return ret;
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

        return occurs >= 1 && isTheCase == occurs;
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





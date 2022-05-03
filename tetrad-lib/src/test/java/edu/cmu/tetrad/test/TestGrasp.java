///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.GRaSP;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.PC;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.FciMax;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.GRaSPFCI;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Rfci;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.algcomparison.independence.ChiSquare;
import edu.cmu.tetrad.algcomparison.independence.DSeparationTest;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.simulation.BayesNetSimulation;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IndependenceFacts;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.sem.StandardizedSemIm;
import edu.cmu.tetrad.util.*;
import org.apache.commons.collections4.OrderedMap;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static edu.cmu.tetrad.search.OtherPermAlgs.Method.SP;
import static java.util.Collections.shuffle;

/**
 * Tests to make sure the DelimiterType enumeration hasn't been tampered with.
 *
 * @author Joseph Ramsey
 */
@SuppressWarnings("ALL")
public final class TestGrasp {

    @NotNull
    private static Grasp getGrasp(Score score, IndependenceTest test) {
        Grasp grasp;

        if (true) {
            grasp = new Grasp(test);
        } else {
            grasp = new Grasp(score);
        }

        return grasp;
    }

    private static boolean printFailed(Graph g, Graph dag, String alg) {
        double ap = new AdjacencyPrecision().getValue(g, dag, null);
        double ar = new AdjacencyRecall().getValue(g, dag, null);
        double ahp = new ArrowheadPrecision().getValue(g, dag, null);
        double ahr = new ArrowheadRecall().getValue(g, dag, null);

        NumberFormat nf = new DecimalFormat("0.00");

        if (dag.getNumEdges() != g.getNumEdges()) {
            System.out.println("Failed " + alg +
                    " ap = " + nf.format(ap) + " ar = " + nf.format(ar)
                    + " ahp = " + nf.format(ahp) + " ahr = " + nf.format(ahr));
            return true;
        }

        return false;
    }

    private static void runTestLoop(Graph g, List<Node> order, Score score, IndependenceTest test, boolean useTest) {
        g = new EdgeListGraph(g);
        order = new ArrayList<>(order);

        Grasp grasp = getGrasp(score, test);

        grasp.setCacheScores(true);
        grasp.setNumStarts(1);
        grasp.setVerbose(true);
        List<Node> perm = grasp.bestOrder(order);
        Graph dag = grasp.getGraph(false);

        printFailed(g, dag, order + " \n" + dag);
    }

    @AfterClass
    public static void afterClass() throws Exception {

    }

    //    @Test
    public void testGrasp1() {
        Parameters params = new Parameters();
        params.set(Params.NUM_MEASURES, 20);
        params.set(Params.AVG_DEGREE, 4);
        params.set(Params.SAMPLE_SIZE, 1000);
        params.set(Params.NUM_RUNS, 10);
        params.set(Params.COEF_LOW, 0);
        params.set(Params.COEF_HIGH, 1);
        params.set(Params.NUM_STARTS, 1);

        params.set(Params.PENALTY_DISCOUNT, 2);
        params.set(Params.ZS_RISK_BOUND, 0.001); //, 0.01, 0.1);
        params.set(Params.EBIC_GAMMA, 0.8);
        params.set(Params.ALPHA, 0.001);

        params.set(Params.GRASP_DEPTH, 5);
        params.set(Params.GRASP_UNCOVERED_DEPTH, 0, 2);
        params.set(Params.GRASP_NONSINGULAR_DEPTH, 0, 2);

        params.set(Params.GRASP_ORDERED_ALG, true, false);
        params.set(Params.GRASP_USE_SCORE, true);
        params.set(Params.GRASP_USE_VERMA_PEARL, false);
        params.set(Params.GRASP_USE_DATA_ORDER, false);
//        params.set(Params.GRASP_ALG, false);


        Algorithms algorithms = new Algorithms();
        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.EbicScore(), new FisherZ()));

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.GRASP_DEPTH));
        statistics.add(new ParameterColumn(Params.GRASP_UNCOVERED_DEPTH));
        statistics.add(new ParameterColumn(Params.GRASP_NONSINGULAR_DEPTH));
        statistics.add(new ParameterColumn(Params.GRASP_ORDERED_ALG));
        statistics.add(new ParameterColumn(Params.EBIC_GAMMA));
        statistics.add(new ParameterColumn(Params.NUM_MEASURES));
        statistics.add(new ParameterColumn(Params.AVG_DEGREE));
        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new NumberOfEdgesTrue());
        statistics.add(new NumberOfEdgesEst());
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/bandrews/Downloads/grasp/testGrasp1",
                simulations, algorithms, statistics, params);
    }

    @Test
    public void allPaperRuns() {
        Parameters params = new Parameters();

        params.set(Params.RANDOMIZE_COLUMNS, true);
        params.set(Params.DIFFERENT_GRAPHS, true);
        params.set(Params.NUM_RUNS, 20);
        params.set(Params.COEF_LOW, 0);
        params.set(Params.COEF_HIGH, 1);
        params.set(Params.NUM_STARTS, 1);
        params.set(Params.VAR_LOW, 1.0);
        params.set(Params.VAR_HIGH, 1.0);
        params.set(Params.STANDARDIZE, true);

        params.set(Params.PENALTY_DISCOUNT, 2);
        params.set(Params.ALPHA, 0.001);

        params.set(Params.GRASP_DEPTH, 3);
        params.set(Params.GRASP_UNCOVERED_DEPTH, 1);
        params.set(Params.GRASP_NONSINGULAR_DEPTH, 1);
        params.set(Params.GRASP_TOLERANCE_DEPTH, 0);

        params.set(Params.GRASP_ORDERED_ALG, true);
        params.set(Params.GRASP_USE_SCORE, true);
        params.set(Params.GRASP_USE_VERMA_PEARL, false);
        params.set(Params.GRASP_USE_DATA_ORDER, true);
        params.set(Params.GRASP_ALLOW_RANDOMNESS_INSIDE_ALGORITHM, false);
        params.set(Params.CACHE_SCORES, true);
        params.set(Params.VERBOSE, true);

        params.set(Params.PRECOMPUTE_COVARIANCES, true);

        {
//            params.set(Params.GRASP_UNCOVERED_DEPTH, 1);
//            params.set(Params.GRASP_NONSINGULAR_DEPTH, 1);

//            params.set(Params.NUM_MEASURES, 20, 30, 40, 50, 60, 70, 80, 90, 100);
//            params.set(Params.AVG_DEGREE, 6);
//            params.set(Params.SAMPLE_SIZE, 1000);
//
            String dataPath, resultsPath;
//
//            dataPath = "/Users/josephramsey/Downloads/grasp-data/vary_measured";
//            resultsPath = "/Users/josephramsey/Downloads/grasp/vary_measured_1";

//            doPaperRun(params, dataPath, resultsPath, true);

//            params.set(Params.NUM_MEASURES, 60);
//            params.set(Params.AVG_DEGREE, 2, 3, 4, 5, 6, 7, 8, 9, 10);
//            params.set(Params.STANDARDIZE, 1000);
//
//            dataPath = "/Users/josephramsey/Downloads/grasp-data/vary_avg_degree";
//            resultsPath = "/Users/josephramsey/Downloads/grasp/vary_avg_degree_1";
//
//            doPaperRun(params, dataPath, resultsPath, true);
//
//            params.set(Params.NUM_MEASURES, 60);
//            params.set(Params.AVG_DEGREE, 6);
//            params.set(Params.SAMPLE_SIZE, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000);
//
//            dataPath = "/Users/josephramsey/Downloads/grasp-data/vary_sample_size";
//            resultsPath = "/Users/josephramsey/Downloads/grasp/vary_sample_size_1";
//
//            doPaperRun(params, dataPath, resultsPath, true);

        }

        {
            String dataPath, resultsPath;

            params.set(Params.GRASP_UNCOVERED_DEPTH, 0, 1);
            params.set(Params.GRASP_NONSINGULAR_DEPTH, 0);

            params.set(Params.NUM_MEASURES, 20, 30, 40, 50, 60, 70, 80, 90, 100);
            params.set(Params.AVG_DEGREE, 6);
            params.set(Params.SAMPLE_SIZE, 1000);

            dataPath = "/Users/josephramsey/Downloads/grasp-data/vary_measured";
            resultsPath = "/Users/josephramsey/Downloads/grasp/vary_measured_2";

            doPaperRun(params, dataPath, resultsPath, false);

            params.set(Params.NUM_MEASURES, 60);
            params.set(Params.AVG_DEGREE, 2,  3, 4, 5, 6, 7, 8, 9, 10);
            params.set(Params.STANDARDIZE, 1000);

            dataPath = "/Users/josephramsey/Downloads/grasp-data/vary_avg_degree";
            resultsPath = "/Users/josephramsey/Downloads/grasp/vary_avg_degree_2";

            doPaperRun(params, dataPath, resultsPath, false);

            params.set(Params.NUM_MEASURES, 60);
            params.set(Params.AVG_DEGREE, 6);
            params.set(Params.SAMPLE_SIZE, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000);

            dataPath = "/Users/josephramsey/Downloads/grasp-data/vary_sample_size";
            resultsPath = "/Users/josephramsey/Downloads/grasp/vary_sample_size_2";

            doPaperRun(params, dataPath, resultsPath, false);

        }
    }

    public void doPaperRun(Parameters params, String dataPath, String resultsPath, boolean doPcFges) {
        Algorithms algorithms = new Algorithms();
        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.SemBicScore(), new FisherZ()));

        if (doPcFges) {
            algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(
                    new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
            algorithms.add(new PC(new FisherZ()));
        }

        Simulations simulations = new Simulations();
        Simulation simulation = new SemSimulation(new RandomForward());
        simulations.add(simulation);

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.GRASP_DEPTH));
        statistics.add(new ParameterColumn(Params.GRASP_UNCOVERED_DEPTH));
        statistics.add(new ParameterColumn(Params.GRASP_NONSINGULAR_DEPTH));
        statistics.add(new ParameterColumn(Params.GRASP_ORDERED_ALG));
        statistics.add(new ParameterColumn(Params.PENALTY_DISCOUNT));
        statistics.add(new ParameterColumn(Params.ALPHA));
        statistics.add(new ParameterColumn(Params.NUM_MEASURES));
        statistics.add(new ParameterColumn(Params.AVG_DEGREE));
        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new NumberOfEdgesTrue());
        statistics.add(new NumberOfEdgesEst());
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setSaveGraphs(true);
        comparison.setSavePags(true);
        comparison.setSaveData(false);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

//        comparison.saveToFiles("/Users/josephramsey/Downloads/grasp/vary_sample_size",
//                simulation, params);

        comparison.compareFromFiles(dataPath,
                resultsPath,
                algorithms, statistics, params);
    }

    @Test
    public void testPaperSimulationsAll() {
        Parameters params = new Parameters();

//        (20,30,40,50,60,70,80,90,100)-6-1000
//        60-(2,3,4,5,6,7,8,9,10)-1000
//        60-6-(200,500,1000,2000,5000,10000,20000,50000,100000)

        String type;

        type = "esp_varying_num_variables";
        params.set(Params.NUM_MEASURES, 20, 30, 40, 50, 60, 70, 80, 90, 100);
        params.set(Params.AVG_DEGREE, 6);
        params.set(Params.SAMPLE_SIZE, 1000);

        testPaperSimulationsVisit(params, type);

        type = "esp_varying_avg_degree";
        params.set(Params.NUM_MEASURES, 60);
        params.set(Params.AVG_DEGREE, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        params.set(Params.SAMPLE_SIZE, 1000);

        testPaperSimulationsVisit(params, type);

        type = "esp_varying_sample_size";
        params.set(Params.NUM_MEASURES, 60);
        params.set(Params.AVG_DEGREE, 6);
        params.set(Params.SAMPLE_SIZE, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000);
        testPaperSimulationsVisit(params, type);
    }

    private void testPaperSimulationsVisit(Parameters params, String type) {
        RandomUtil.getInstance().setSeed(492939494L);

        NumberFormatUtil.getInstance().setNumberFormat(new DecimalFormat("0.000000"));

        params.set(Params.COEF_LOW, 0);
        params.set(Params.COEF_HIGH, 1);
        params.set(Params.COV_LOW, 0);
        params.set(Params.COV_HIGH, 0);
        params.set(Params.VAR_LOW, 1);
        params.set(Params.VAR_HIGH, 1);
        params.set(Params.NUM_RUNS, 20);
        params.set(Params.VERBOSE, false);
        params.set(Params.NUM_STARTS, 1);

        params.set(Params.PENALTY_DISCOUNT, 2.0);
        params.set(Params.EBIC_GAMMA, 0.8);
        params.set(Params.ALPHA, 0.001);

        params.set(Params.GRASP_DEPTH, 3);
        params.set(Params.GRASP_UNCOVERED_DEPTH, 0);//1);
        params.set(Params.GRASP_NONSINGULAR_DEPTH, 0);//1);
        params.set(Params.GRASP_ORDERED_ALG, true);
        params.set(Params.GRASP_USE_VERMA_PEARL, false);
        params.set(Params.GRASP_USE_DATA_ORDER, false);
        params.set(Params.CACHE_SCORES, true);


        Algorithms algorithms = new Algorithms();
        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.SemBicScore(), new FisherZ()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Pc(new FisherZ()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(new edu.cmu.tetrad.algcomparison.score.LinearGaussianBicScore()));

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.NUM_MEASURES));
        statistics.add(new ParameterColumn(Params.AVG_DEGREE));
        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new F1Adj());
        statistics.add(new ElapsedTime());

        Comparison comparison = new Comparison();
        comparison.setSaveData(false);
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/varying_final2/testPaperSimulations_"
                        + type, simulations,
                algorithms, statistics, params);
    }


    //    @Test
    public void testGraspForClark() {
        Parameters params = new Parameters();
        params.set(Params.NUM_MEASURES, 10);
        params.set(Params.AVG_DEGREE, 4);
        params.set(Params.SAMPLE_SIZE, 1000);
        params.set(Params.NUM_RUNS, 50);
        params.set(Params.COEF_LOW, 0);
        params.set(Params.COEF_HIGH, 1);
        params.set(Params.NUM_STARTS, 5);

        params.set(Params.PENALTY_DISCOUNT, 2);
        params.set(Params.ZS_RISK_BOUND, 0.001); //, 0.01, 0.1);
        params.set(Params.EBIC_GAMMA, 0.8);
        params.set(Params.ALPHA, 0.001);

        params.set(Params.GRASP_DEPTH, 5);
        params.set(Params.GRASP_UNCOVERED_DEPTH, 1);
        params.set(Params.GRASP_NONSINGULAR_DEPTH, 1);

        params.set(Params.GRASP_ORDERED_ALG, true);
//        params.set(Params.GRASP_USE_SCORE, true);
        params.set(Params.GRASP_USE_VERMA_PEARL, false);
        params.set(Params.GRASP_USE_DATA_ORDER, false);
        params.set(Params.CACHE_SCORES, true);
//        params.set(Params.GRASP_ALG, false);


        Algorithms algorithms = new Algorithms();
        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.SemBicScore(), new FisherZ()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(
                new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.PC(new FisherZ()));

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Statistics statistics = new Statistics();
//        statistics.add(new ParameterColumn(Params.GRASP_DEPTH));
//        statistics.add(new ParameterColumn(Params.GRASP_UNCOVERED_DEPTH));
//        statistics.add(new ParameterColumn(Params.GRASP_NONSINGULAR_DEPTH));
//        statistics.add(new ParameterColumn(Params.GRASP_ORDERED_ALG));
//        statistics.add(new ParameterColumn(Params.EBIC_GAMMA));
//        statistics.add(new ParameterColumn(Params.NUM_MEASURES));
//        statistics.add(new ParameterColumn(Params.AVG_DEGREE));
//        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new NumberOfEdgesTrue());
        statistics.add(new NumberOfEdgesEst());
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/testGraspForClark",
                simulations, algorithms, statistics, params);
    }

    //    @Test
    public void testGrasp1Bryan() {
        Parameters params = new Parameters();
        params.set(Params.NUM_MEASURES, 100);
        params.set(Params.AVG_DEGREE, 10);
        params.set(Params.SAMPLE_SIZE, 1000);
        params.set(Params.NUM_RUNS, 1);
        params.set(Params.COEF_LOW, 0);
        params.set(Params.COEF_HIGH, 1);
        params.set(Params.NUM_STARTS, 1);

        params.set(Params.PENALTY_DISCOUNT, 2);
        params.set(Params.ZS_RISK_BOUND, 0.001); //, 0.01, 0.1);
        params.set(Params.EBIC_GAMMA, 0.8);
        params.set(Params.ALPHA, 0.001);

        params.set(Params.GRASP_DEPTH, 3);
        params.set(Params.GRASP_UNCOVERED_DEPTH, 2);
        params.set(Params.NUM_ROUNDS, 50);

        params.set(Params.GRASP_CHECK_COVERING, false);
        params.set(Params.GRASP_FORWARD_TUCK_ONLY, false);
        params.set(Params.GRASP_BREAK_AFTER_IMPROVEMENT, true);
        params.set(Params.GRASP_ORDERED_ALG, false);
        params.set(Params.GRASP_USE_SCORE, true);
        params.set(Params.GRASP_USE_VERMA_PEARL, false);
        params.set(Params.GRASP_USE_VP_SCORING, false);
        params.set(Params.GRASP_USE_DATA_ORDER, false);

        params.set(Params.GRASP_ALG, false);


        Algorithms algorithms = new Algorithms();
        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.EbicScore(), new FisherZ()));

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.GRASP_DEPTH));
//        statistics.add(new ParameterColumn(Params.GRASP_UNCOVERED_DEPTH));
//        statistics.add(new ParameterColumn(Params.GRASP_USE_VP_SCORING));
//        statistics.add(new ParameterColumn(Params.GRASP_USE_TUCK));
//        statistics.add(new ParameterColumn(Params.GRASP_CHECK_COVERING));
        statistics.add(new ParameterColumn(Params.EBIC_GAMMA));
        statistics.add(new ParameterColumn(Params.NUM_MEASURES));
        statistics.add(new ParameterColumn(Params.AVG_DEGREE));
        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new NumberOfEdgesTrue());
        statistics.add(new NumberOfEdgesEst());
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/testGrasp1",
                simulations, algorithms, statistics, params);
    }


    //    @Test
    public void testComparePearlGrowShrink() {
        Parameters params = new Parameters();
        params.set(Params.NUM_MEASURES, 20);
        params.set(Params.AVG_DEGREE, 6);
        params.set(Params.SAMPLE_SIZE, 1000);
        params.set(Params.NUM_RUNS, 5);
        params.set(Params.COEF_LOW, 0);
        params.set(Params.COEF_HIGH, 1);
        params.set(Params.NUM_STARTS, 1);

        params.set(Params.ALPHA, 0.001, 0.01);
        params.set(Params.PENALTY_DISCOUNT, 2.0);
        params.set(Params.GRASP_DEPTH, 5);
        params.set(Params.GRASP_UNCOVERED_DEPTH, 2);
        params.set(Params.GRASP_FORWARD_TUCK_ONLY, false);
        params.set(Params.GRASP_USE_VERMA_PEARL, true, false);
        params.set(Params.TIMEOUT, -1);
        params.set(Params.VERBOSE, true);


        Algorithms algorithms = new Algorithms();
        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.SemBicScore(), new FisherZ()));

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.ALPHA));
        statistics.add(new ParameterColumn(Params.PENALTY_DISCOUNT));
        statistics.add(new ParameterColumn(Params.GRASP_USE_VERMA_PEARL));
        statistics.add(new NumberOfEdgesTrue());
        statistics.add(new NumberOfEdgesEst());
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/testComparePearlGrowShrink",
                simulations, algorithms, statistics, params);
    }

    //    @Test
    public void testCompareGrasp1Grasp2() {
        Parameters params = new Parameters();
        params.set(Params.NUM_MEASURES, 10, 20, 40);
        params.set(Params.AVG_DEGREE, 4, 6, 8);
        params.set(Params.SAMPLE_SIZE, 200);
        params.set(Params.NUM_RUNS, 10);
        params.set(Params.COEF_LOW, 0);
        params.set(Params.COEF_HIGH, 1);
        params.set(Params.NUM_STARTS, 1);

//        params.set(Params.ALPHA, 0.001, 0.01);
        params.set(Params.PENALTY_DISCOUNT, 2.0);
        params.set(Params.GRASP_DEPTH, 10);
        params.set(Params.GRASP_UNCOVERED_DEPTH, 2);
//        params.set(Params.GRASP_FORWARD_TUCK_ONLY, false);
//        params.set(Params.GRASP_USE_PEARL, false);
        params.set(Params.GRASP_ALG, true, false);
        params.set(Params.TIMEOUT, -1);
        params.set(Params.VERBOSE, true);


        Algorithms algorithms = new Algorithms();
        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.SemBicScore(), new FisherZ()));

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Statistics statistics = new Statistics();
//        statistics.add(new ParameterColumn(Params.ALPHA));
        statistics.add(new ParameterColumn(Params.GRASP_ALG));
        statistics.add(new ParameterColumn(Params.NUM_MEASURES));
        statistics.add(new ParameterColumn(Params.AVG_DEGREE));
//        statistics.add(new ParameterColumn(Params.PENALTY_DISCOUNT));
//        statistics.add(new ParameterColumn(Params.GRASP_USE_PEARL));
        statistics.add(new NumberOfEdgesTrue());
        statistics.add(new NumberOfEdgesEst());
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/testCompareGrasp1Grasp2",
                simulations, algorithms, statistics, params);
    }

    //    @Test
    public void testGrasp2() {
        Parameters params = new Parameters();
        params.set(Params.NUM_MEASURES, 7);
        params.set(Params.AVG_DEGREE, 3);
        params.set(Params.SAMPLE_SIZE, 1000);
        params.set(Params.NUM_RUNS, 10);
        params.set(Params.COEF_LOW, 0);
        params.set(Params.COEF_HIGH, 1);
        params.set(Params.NUM_STARTS, 1);
        params.set(Params.ALPHA, 0.001);

        params.set(Params.PENALTY_DISCOUNT, 2);
        params.set(Params.ZS_RISK_BOUND, 0.001); //, 0.01, 0.1);
        params.set(Params.EBIC_GAMMA, 0.1);

        params.set(Params.GRASP_DEPTH, 3);
        params.set(Params.GRASP_CHECK_COVERING, false);
        params.set(Params.GRASP_FORWARD_TUCK_ONLY, false);
        params.set(Params.GRASP_BREAK_AFTER_IMPROVEMENT, false);
        params.set(Params.GRASP_ORDERED_ALG, true);
        params.set(Params.GRASP_USE_SCORE, true);
        params.set(Params.GRASP_USE_VERMA_PEARL, false);
        params.set(Params.GRASP_USE_DATA_ORDER, false);


        // use defaults.
        params.set(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE, 10);

        Algorithms algorithms = new Algorithms();
        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.BdeuScore(), new ChiSquare()));

        Simulations simulations = new Simulations();
        simulations.add(new BayesNetSimulation(new RandomForward()));

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.GRASP_DEPTH));
        statistics.add(new ParameterColumn(Params.NUM_ROUNDS));
        statistics.add(new ParameterColumn(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE));
        statistics.add(new ParameterColumn(Params.STRUCTURE_PRIOR));
        statistics.add(new ParameterColumn(Params.GRASP_FORWARD_TUCK_ONLY));
        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new NumberOfEdgesTrue());
        statistics.add(new NumberOfEdgesEst());
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/testGrasp2",
                simulations, algorithms, statistics, params);
    }

    //    @Test
    public void tesLuFigure3() {
        RandomUtil.getInstance().setSeed(492939494L);

        Parameters params = new Parameters();
        params.set(Params.NUM_MEASURES, 20);
        params.set(Params.AVG_DEGREE, 4);
        params.set(Params.SAMPLE_SIZE, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000,
                50000, 100000, 200000);

        params.set(Params.COEF_HIGH, 1);
        params.set(Params.COV_LOW, 0);
        params.set(Params.COV_HIGH, 0);
        params.set(Params.VAR_LOW, 1);
        params.set(Params.VAR_HIGH, 1);
        params.set(Params.NUM_RUNS, 30);
        params.set(Params.VERBOSE, true);
        params.set(Params.NUM_STARTS, 1);

        params.set(Params.PENALTY_DISCOUNT, 2.0);
        params.set(Params.EBIC_GAMMA, 0.8);
        params.set(Params.ALPHA, 0.001);

        params.set(Params.GRASP_DEPTH, 2);
        params.set(Params.GRASP_UNCOVERED_DEPTH, 1);
        params.set(Params.GRASP_NONSINGULAR_DEPTH, 1);
        params.set(Params.GRASP_ORDERED_ALG, false);
        params.set(Params.GRASP_USE_VERMA_PEARL, false);
        params.set(Params.GRASP_USE_DATA_ORDER, false);
        params.set(Params.CACHE_SCORES, true);


        Algorithms algorithms = new Algorithms();
        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.SemBicScore(), new FisherZ()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.PC(new FisherZ()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new ParameterColumn(Params.AVG_DEGREE));
        statistics.add(new CorrectSkeleton());
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new SHD());
        statistics.add(new F1Adj());
        statistics.add(new ElapsedTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/testLuFigure3",
                simulations, algorithms, statistics, params);
    }

    //    @Test
    public void testLuFigure6() {
        RandomUtil.getInstance().setSeed(492939494L);

        Parameters params = new Parameters();
        params.set(Params.SAMPLE_SIZE, 500);
        params.set(Params.NUM_MEASURES, 60);
        params.set(Params.AVG_DEGREE, 1, 2, 3, 4, 5);

        params.set(Params.COEF_HIGH, 1);
        params.set(Params.COV_LOW, 0);
        params.set(Params.COV_HIGH, 0);
        params.set(Params.VAR_LOW, 1);
        params.set(Params.VAR_HIGH, 1);
        params.set(Params.NUM_RUNS, 50);
        params.set(Params.VERBOSE, true);
        params.set(Params.NUM_STARTS, 1);

        params.set(Params.PENALTY_DISCOUNT, 2.0);
        params.set(Params.EBIC_GAMMA, 0.8);
        params.set(Params.ALPHA, 0.001);

        params.set(Params.GRASP_DEPTH, 5);
        params.set(Params.GRASP_UNCOVERED_DEPTH, 1);
        params.set(Params.GRASP_NONSINGULAR_DEPTH, 1);
        params.set(Params.GRASP_ORDERED_ALG, true);
        params.set(Params.GRASP_USE_VERMA_PEARL, false);

        Algorithms algorithms = new Algorithms();
        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.SemBicScore(), new FisherZ()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Pc(new FisherZ()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(
//                new edu.cmu.tetrad.algcomparison.score.LinearGaussianBicScore()));

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.AVG_DEGREE));
        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new F1Adj());
        statistics.add(new ElapsedTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/Lu.figure.6.grasp2.fges.pc.b", simulations,
                algorithms, statistics, params);
    }


    //    @Test
    public void testPaperSimulations() {
        RandomUtil.getInstance().setSeed(492939494L);

        int numMeasures = 60; //100;
        int avgDegree = 6; // 10;

        Parameters params = new Parameters();
        params.set(Params.SAMPLE_SIZE, 1000);
        params.set(Params.NUM_MEASURES, numMeasures);
        params.set(Params.AVG_DEGREE, avgDegree);
        params.set(Params.COEF_LOW, 0);

        params.set(Params.COEF_HIGH, 1);
        params.set(Params.COV_LOW, 0);
        params.set(Params.COV_HIGH, 0);
        params.set(Params.VAR_LOW, 1);
        params.set(Params.VAR_HIGH, 1);
        params.set(Params.NUM_RUNS, 10);
        params.set(Params.VERBOSE, true);
        params.set(Params.NUM_STARTS, 1);

        params.set(Params.PENALTY_DISCOUNT, 2.0);
        params.set(Params.EBIC_GAMMA, 0.8);
        params.set(Params.ALPHA, 0.001);

        params.set(Params.GRASP_DEPTH, 2);
        params.set(Params.GRASP_UNCOVERED_DEPTH, 1);
        params.set(Params.GRASP_NONSINGULAR_DEPTH, 1);
        params.set(Params.GRASP_ORDERED_ALG, false);
        params.set(Params.GRASP_USE_VERMA_PEARL, false);
        params.set(Params.GRASP_USE_DATA_ORDER, false);
        params.set(Params.CACHE_SCORES, true);


        Algorithms algorithms = new Algorithms();
        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.SemBicScore(), new FisherZ()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.PC(new FisherZ()));
        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.AVG_DEGREE));
        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new F1Adj());
        statistics.add(new ElapsedTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/testPaperSimulations_"
                        + numMeasures + "_" + avgDegree, simulations,
                algorithms, statistics, params);
    }

    //    @Test
    public void wayneCheckDensityClaim1() {
        int count1 = 0;

        for (int i = 0; i < 100; i++) {

            Graph g = GraphUtils.randomGraph(20, 0, 40,
                    100, 100, 100, false);
            SemPm pm = new SemPm(g);
            SemIm im = new SemIm(pm);
            DataSet d = im.simulateData(1000, false);

            IndependenceTest test = new IndTestFisherZ(d, 0.00001);

            List<Node> pi = new ArrayList<>(test.getVariables());

            Grasp grasp = new Grasp(test);

            grasp.setUseRaskuttiUhler(true);
            grasp.setOrdered(false);

            grasp.setVerbose(false);

            grasp.setDepth(3);
            grasp.bestOrder(pi);
            Graph estCpdagGasp = grasp.getGraph(true);

            grasp.setOrdered(true);
            grasp.bestOrder(pi);
            Graph estCpdagGrasp = grasp.getGraph(true);

            if (estCpdagGasp.getNumEdges() < estCpdagGrasp.getNumEdges()) {
                count1++;
                System.out.println("TRUE");
            } else {
                System.out.println("False");
            }
        }

        System.out.println(count1);
    }

    //    @Test
    public void wayneCheckDensityClaim2() {
        List<Ret> allFacts = new ArrayList<>();

        // Only oracle unfaithful examples.
        allFacts.add(getFactsSimpleCanceling());
        allFacts.add(wayneTriangleFaithfulnessFailExample());
        allFacts.add(wayneTriMutationFailsForFaithfulGraphExample());
        allFacts.add(getFigure7());
        allFacts.add(getFigure8());
        allFacts.add(getFigure11());
        allFacts.add(wayneExample2());

        int count = 0;

        boolean verbose = false;
        int numRounds = 50;
        int depth = 5;
        int maxPermSize = 6;

        boolean printCpdag = false;

        for (int i = 0; i < allFacts.size(); i++) {
            Ret facts = allFacts.get(i);
            count++;

            System.out.println();
            System.out.println("Test #" + (i + 1));
            System.out.println(facts.getLabel());
            System.out.println(facts.getFacts());
        }

        for (int i = 0; i < allFacts.size(); i++) {
            boolean passed = true;

            Ret facts = allFacts.get(i);
            count++;

            TeyssierScorer scorer = new TeyssierScorer(new IndTestDSep(facts.getFacts()),
                    new GraphScore(facts.getFacts()));

            OrderedMap<String, Set<Graph>> graphs = new ListOrderedMap<>();
            OrderedMap<String, Set<String>> labels = new ListOrderedMap<>();

            List<Node> variables = facts.facts.getVariables();
            Collections.sort(variables);

            PermutationGenerator gen = new PermutationGenerator(variables.size());
            int[] perm;
            int count1 = 0;
            int total = 0;

            while ((perm = gen.next()) != null) {
                List<Node> pi = GraphUtils.asList(perm, variables);

                Grasp grasp = new Grasp(new IndTestDSep(facts.getFacts()));

                grasp.setUseRaskuttiUhler(true);
                grasp.setDepth(100);
                grasp.setOrdered(true);
                grasp.setVerbose(false);

                grasp.bestOrder(pi);
                Graph estCpdagGrasp = grasp.getGraph(true);

                if (estCpdagGrasp.getNumEdges() == facts.truth) {
                    count1++;
                } else {
                    System.out.println("Counterexample: Test #" + (i + 1) + " Permutation = " + pi + " #Edges = "
                            + estCpdagGrasp.getNumEdges() + " #Frugal = " + facts.truth);
                }

                total++;
            }

            System.out.println("Test #" + (i + 1) + " #Frugal = " + count1 + " Total #Permutations = " + total);
        }
    }

    //    @Test
    public void bryanCheckDensityClaims() {
        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.NAME);

        long start = System.currentTimeMillis();
        boolean usePearl = true;
        int numVars = 5; // Will change this in OtherParams.sp() too

        try {
//            String path = "/Users/josephramsey/Downloads/grasp/out_53_0index/out_53.txt";
//            String path = "/Users/josephramsey/Downloads/grasp/out_80_0index/out_80.txt";
//            String path = "/Users/josephramsey/Downloads/studeny_out.txt";
//            String path = "/Users/josephramsey/Downloads/udags4.txt";
            String path = "/Users/josephramsey/Downloads/udags5.txt";
//            String path = "/Users/josephramsey/Downloads/udags6.txt";
            File file = new File(path);
            System.out.println(file.getAbsolutePath());
            FileReader in1 = new FileReader(file);
            BufferedReader in = new BufferedReader(in1);
            String line;
            int index = 0;
            int indexed = 0;
            int existsNonfrugal = 0;
            int all = 0;

            List<Integer> hard = new ArrayList<>();
//            hard.add(7);
//            hard.add(13);
//            hard.add(36);
//            hard.add(37);
//            hard.add(38);
//            hard.add(39);
//            hard.add(40);
//            hard.add(41);
//            hard.add(42);
//            hard.add(47);
//            hard.add(48);
//            hard.add(50);
//            hard.add(51);
//            hard.add(52);
//            hard.add(53);
//            hard.add(54);
//            hard.add(55);
//            hard.add(56);
//            hard.add(57);
//            hard.add(58);
//            hard.add(59);
//            hard.add(63);
//            hard.add(64);
//            hard.add(65);
//            hard.add(70);
//            hard.add(71);
//            hard.add(72);
//            hard.add(73);

            // all u-frugal
//            hard.add(1);
//            hard.add(2);
//            hard.add(4);
//            hard.add(6);
//            hard.add(8);
//            hard.add(9);
//            hard.add(14);
//            hard.add(16);
//            hard.add(17);
//            hard.add(20);
//            hard.add(21);
//            hard.add(22);
//            hard.add(24);
//            hard.add(25);
//            hard.add(26);
//            hard.add(27);
//            hard.add(28);
//            hard.add(29);
//            hard.add(32);
//            hard.add(34);
//            hard.add(35);
//            hard.add(36);
//            hard.add(38);
//            hard.add(39);
//            hard.add(43);
//            hard.add(44);
//            hard.add(46);
//            hard.add(47);
//            hard.add(48);
//            hard.add(49);
//            hard.add(53);
//            hard.add(54);
//            hard.add(55);
//            hard.add(56);
//            hard.add(59);
//            hard.add(62);
//            hard.add(64);
//            hard.add(66);
//            hard.add(67);
//            hard.add(68);
//            hard.add(69);
//            hard.add(71);
//            hard.add(73);
//            hard.add(74);
//            hard.add(75);
//            hard.add(77);
//            hard.add(78);
//            hard.add(79);
//            hard.add(80);

//            // failures by grasp
//            hard.add(9);
//            hard.add(21);
//            hard.add(26);
//            hard.add(38);
//            hard.add(48);
//            hard.add(49);
//            hard.add(56);
//            hard.add(62);
//
//            // failures by esp
//            hard.add(46);
//            hard.add(59);
//            hard.add(66);
//            hard.add(68);

//            // failures by tsp
//            hard.add(14);
//            hard.add(20);
//            hard.add(36);
//            hard.add(39);
//            hard.add(43);
//            hard.add(44);
//            hard.add(47);
//            hard.add(53);
//            hard.add(55);


            // new u-frugal
            List<Integer> ufr = new ArrayList<>();

            ufr.add(2);
            ufr.add(3);
            ufr.add(4);
            ufr.add(5);
            ufr.add(6);
            ufr.add(7);
            ufr.add(10);
            ufr.add(11);
            ufr.add(12);
            ufr.add(13);
            ufr.add(14);
            ufr.add(15);
            ufr.add(18);
            ufr.add(19);
            ufr.add(20);
            ufr.add(21);
            ufr.add(22);
            ufr.add(25);
            ufr.add(26);
            ufr.add(31);
            ufr.add(33);
            ufr.add(37);
            ufr.add(41);
            ufr.add(43);
            ufr.add(44);
            ufr.add(50);
            ufr.add(56);
            ufr.add(57);
            ufr.add(58);
            ufr.add(59);
            ufr.add(62);
            ufr.add(63);
            ufr.add(65);
            ufr.add(67);
            ufr.add(69);
            ufr.add(70);
            ufr.add(72);
            ufr.add(73);
            ufr.add(78);
            ufr.add(83);
            ufr.add(87);
            ufr.add(92);

            //            List<Integer> nufr = new ArrayList<>();
//            nufr.add(1); nufr.add(2); nufr.add(4); nufr.add(5); nufr.add(25); nufr.add(33); nufr.add(43); nufr.add(44); nufr.add(45); nufr.add(61); nufr.add(62); nufr.add(66); nufr.add(67); nufr.add(72); nufr.add(74); nufr.add(76); nufr.add(78); nufr.add(79); nufr.add(80); nufr.add(81); nufr.add(82); nufr.add(84); nufr.add(85); nufr.add(86); nufr.add(95); nufr.add(96); nufr.add(100); nufr.add(103); nufr.add(104); nufr.add(106); nufr.add(107); nufr.add(108); nufr.add(109); nufr.add(110); nufr.add(111); nufr.add(113); nufr.add(114); nufr.add(115); nufr.add(127); nufr.add(128); nufr.add(129); nufr.add(130); nufr.add(133); nufr.add(135); nufr.add(136); nufr.add(137); nufr.add(139); nufr.add(140); nufr.add(141); nufr.add(144); nufr.add(145); nufr.add(148); nufr.add(149); nufr.add(150); nufr.add(151); nufr.add(152); nufr.add(153); nufr.add(155); nufr.add(156); nufr.add(157);
//
//            List<Integer> nfr = new ArrayList<>();
//            nfr.add(6); nfr.add(73); nfr.add(87); nfr.add(154); nfr.add(158); nfr.add(159);

            List<Node> variables = new ArrayList<>();
            for (int i = 0; i < numVars; i++) {
                variables.add(new ContinuousVariable("" + i));
            }

            while ((line = in.readLine()) != null) {
                index++;

//                if (!hard.contains(index)) continue;;
//                if (index != 13) continue;

//                if (nufr.contains(index)) continue;
//                if (nfr.contains(index)) continue;

                all++;

                System.out.println("\nLine " + index + " " + line);
                line = line.trim();

                GraphoidAxioms axioms = getGraphoidAxioms(line, variables);
                axioms.setTrivialtyAssumed();
                axioms.setSymmetryAssumed();

                System.out.println(axioms.getIndependenceFacts().getVariableNames());

//                if (axioms.semigraphoid()) {
//                    System.out.println("\tSEMIGRAPHOID");
//                }
//
//                if (axioms.graphoid()) {
//                    System.out.println("\tGRAPHOID");
//                }

//                if (axioms.compositionalGraphoid()) {
//                    System.out.println("\tCOMPOSITIONAL GRAPHOID");
//                }

                if (true) {
                    IndependenceFacts facts = axioms.getIndependenceFacts();
                    facts.setNodes(variables);


//                    List<Node> variables = facts.getVariables();
                    IndTestDSep test = new IndTestDSep(facts, variables);

                    OtherPermAlgs spAlg = new OtherPermAlgs(test);
                    spAlg.setUsePearl(true);
                    spAlg.setMethod(OtherPermAlgs.Method.SP);
                    spAlg.setUseDataOrder(true);
                    spAlg.setNumVariables(numVars);
                    List<Node> spPi = spAlg.bestOrder(variables);
                    Graph spGraph = spAlg.getGraph(false);
                    int spNumEdges = spGraph.getNumEdges();

                    List<Node> failingInitialPi = null;
                    Graph failingDag = null;
                    List<Node> failingEstPi = null;

                    PermutationGenerator gen = new PermutationGenerator(variables.size());
                    int[] perm;

                    List<List<Node>> pis = new ArrayList<>();
                    Map<List<Node>, Integer> ests = new HashMap<>();

                    Grasp grasp = new Grasp(test);

                    grasp.setDepth(3);
                    grasp.setUncoveredDepth(1);
                    grasp.setNonSingularDepth(1);
                    grasp.setUseRaskuttiUhler(usePearl);
                    grasp.setOrdered(true);
                    grasp.setVerbose(false);
                    grasp.setCacheScores(false);

                    int count = 0;

                    while ((perm = gen.next()) != null) {
                        List<Node> pi = GraphUtils.asList(perm, variables);

                        List<Node> estGraphPi = grasp.bestOrder(pi);
                        Graph estGraph = grasp.getGraph(false);
                        int estNumEdges = grasp.getNumEdges();

                        if (estNumEdges > spNumEdges) {
                            failingInitialPi = pi;
                            failingDag = estGraph;
                            failingEstPi = estGraphPi;

//                            if (failingInitialPi != null) {
                            System.out.println("\t#### line = " + index + " FOUND NON-FRUGAL MODEL, INITIAL = " + failingInitialPi + " FINAL = "
                                    + failingEstPi);
                            printExistsNonfrugalCase(line, index, facts, spPi, spGraph, failingInitialPi, failingDag, failingEstPi);
//                                existsNonfrugal++;
//                            }


//                            break;
                        }
                    }

                    if (failingInitialPi != null) {
                        System.out.println("\t#### line = " + index + " FOUND NON-FRUGAL MODEL, INITIAL = " + failingInitialPi + " FINAL = "
                                + failingEstPi);
                        printExistsNonfrugalCase(line, index, facts, spPi, spGraph, failingInitialPi, failingDag, failingEstPi);
                        existsNonfrugal++;
                    }

                }

                System.out.println("\tExists nonfrugal = " + existsNonfrugal + " all = " + all + " (" + (System.currentTimeMillis() - start) / 1000.0 + " s)");
            }

            System.out.println("\nTOTAL: exists nonfrugal = " + existsNonfrugal + " all = " + all + " (" + (System.currentTimeMillis() - start) / 1000.0 + " s)");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void printExistsNonfrugalCase(String line, int index, IndependenceFacts facts, List<Node> spPi, Graph spGraph, List<Node> failingInitialPi, Graph failingDag, List<Node> failingEstPi) {
        System.out.println("Failed, line " + index + " " + line);
        System.out.println("Elementary facts = " + facts);
        System.out.println("Failing initial permutation: " + failingInitialPi);
        System.out.println("Failing GRASP final permutation: " + failingEstPi);
        System.out.println("SP permutation = " + spPi);
        System.out.println("SP DAG = " + spGraph);
        System.out.println("Failing Estimated DAG = " + failingDag);

        IndTestDSep dsep = new IndTestDSep(failingDag);

        for (IndependenceFact fact : facts.getFacts()) {
            if (dsep.isDSeparated(fact.getX(), fact.getY(), fact.getZ())) {
                System.out.println("Possible unfaithful d-connection: " + fact);
            }
        }
    }

    //    @Test
    public void simulateDataForPaper() {
        NumberFormatUtil.getInstance().setNumberFormat(new DecimalFormat("0.000000"));

        Parameters params = new Parameters();
        params.set(Params.SAMPLE_SIZE, 500, 1000, 5000);
        params.set(Params.NUM_MEASURES, 20, 60, 100);
        params.set(Params.AVG_DEGREE, 6, 10);
        params.set(Params.COEF_LOW, 0);
        params.set(Params.COEF_HIGH, 1);
        params.set(Params.VAR_LOW, 1);
        params.set(Params.VAR_HIGH, 1);
        params.set(Params.NUM_RUNS, 30);
        params.set(Params.VERBOSE, false);

        System.out.println(params);

        SemSimulation simulation = new SemSimulation(new RandomForward());

        Comparison comparison = new Comparison();
        comparison.setSaveGraphs(true);

        comparison.saveToFiles("/Users/josephramsey/Downloads/grasp/simulation", simulation, params);
    }

    private GraphoidAxioms getGraphoidAxioms(String line, List<Node> nodes) throws IOException {
//        Map<Integer, Node> nodes = new HashMap<>();
//
//        for (int i = 0; i < variables.size(); i++) {
//            nodes.put(i, variables.get(i));
//        }

        Set<GraphoidAxioms.GraphoidIndFact> facts = new LinkedHashSet<>();
        Map<GraphoidAxioms.GraphoidIndFact, String> textSpecs = new HashMap<>();

        if (!line.isEmpty()) {
            String[] split = line.split(",");
            for (String ic : split) {
                Set<Node> x = new HashSet<>();
                Set<Node> y = new HashSet<>();
                Set<Node> z = new HashSet<>();

                String[] tokens1 = ic.split("\\|");
                String[] tokens2 = tokens1[0].split(":");

                for (int i = 0; i < tokens2[0].length(); i++) {
                    int i1 = Integer.parseInt(tokens2[0].substring(i, i + 1).trim());
//                    Node node;

//                    if (nodes.get(i1) == null) {
//                        nodes.put(i1, new GraphNode(i1 + ""));
//                    }

                    x.add(nodes.get(i1));
                }

                for (int i = 0; i < tokens2[1].length(); i++) {
                    String substring = tokens2[1].substring(i, i + 1);
                    int i1 = Integer.parseInt(substring.trim());
//                    Node node;

//                    if (nodes.get(i1) == null) {
//                        nodes.put(i1, new GraphNode(i1 + ""));
//                    }

                    y.add(nodes.get(i1));
                }

                if (tokens1.length == 2) {
                    for (int i = 0; i < tokens1[1].length(); i++) {
                        int i1 = Integer.parseInt(tokens1[1].substring(i, i + 1).trim());
//                        Node node;

//                        if (nodes.get(i1) == null) {
//                            nodes.put(i1, new ContinuousVariable(i1 + ""));
//                        }

                        z.add(nodes.get(i1));
                    }
                }

                GraphoidAxioms.GraphoidIndFact fact = new GraphoidAxioms.GraphoidIndFact(x, y, z);
                facts.add(fact);
                textSpecs.put(fact, ic);
            }
        }

        return new GraphoidAxioms(facts, nodes, textSpecs);
    }

    //    @Test
    public void testLuFigure3() {
        Parameters params = new Parameters();
        params.set(Params.SAMPLE_SIZE, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000);//, 200000);
        params.set(Params.NUM_MEASURES, 20);
        params.set(Params.AVG_DEGREE, 4);
        params.set(Params.COEF_LOW, 0.2);
        params.set(Params.COEF_HIGH, 0.8);
        params.set(Params.COV_LOW, 0);
        params.set(Params.COV_HIGH, 0);
        params.set(Params.VAR_LOW, 1);
        params.set(Params.VAR_HIGH, 1);
        params.set(Params.VERBOSE, true);
        params.set(Params.NUM_RUNS, 30);
        params.set(Params.NUM_STARTS, 1);

        params.set(Params.GRASP_DEPTH, 3);
        params.set(Params.GRASP_UNCOVERED_DEPTH, 1);
        params.set(Params.GRASP_NONSINGULAR_DEPTH, 1);
        params.set(Params.GRASP_ORDERED_ALG, true);
        params.set(Params.GRASP_USE_VERMA_PEARL, false);

        params.set(Params.PENALTY_DISCOUNT, 2.0);
        params.set(Params.ALPHA, 0.001);
//
        Algorithms algorithms = new Algorithms();
        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.SemBicScore(), new FisherZ()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Pc(new FisherZ()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(new edu.cmu.tetrad.algcomparison.score.LinearGaussianBicScore()));

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new F1Adj());
        statistics.add(new ElapsedTime());

        Comparison comparison = new Comparison();
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/Lu.figure.3.grasp2.fges.pc", simulations,
                algorithms, statistics, params);
    }

    //    @Test
    public void testClark() {

        // Special graph that fans from one node to 5 then back to one.

        Parameters params = new Parameters();
        params.set(Params.SAMPLE_SIZE, 500);
        params.set(Params.COEF_LOW, 0);
        params.set(Params.COEF_HIGH, 1);
        params.set(Params.NUM_RUNS, 10);
        params.set(Params.NUM_ROUNDS, 10);
        params.set(Params.VERBOSE, true);
        params.set(Params.NUM_STARTS, 1);

        params.set(Params.COLLIDER_DISCOVERY_RULE, 2);
        params.set(Params.PENALTY_DISCOUNT, 2.0);
        params.set(Params.ALPHA, 0.001);

        params.set(Params.GRASP_DEPTH, 3);
        params.set(Params.GRASP_CHECK_COVERING, false);
        params.set(Params.GRASP_FORWARD_TUCK_ONLY, false);
        params.set(Params.GRASP_BREAK_AFTER_IMPROVEMENT, true);
        params.set(Params.GRASP_ORDERED_ALG, true);
        params.set(Params.GRASP_USE_SCORE, true);
        params.set(Params.GRASP_USE_VERMA_PEARL, false);
        params.set(Params.GRASP_USE_DATA_ORDER, false);

        Algorithms algorithms = new Algorithms();
        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.
                EbicScore(), new FisherZ()));
        algorithms.add(new edu.cmu.tetrad.algcomparison
                .algorithm.oracle.cpdag.PcAll(new FisherZ()));
        algorithms.add(new edu.cmu.tetrad.algcomparison
                .algorithm.oracle.cpdag.Fges(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));

        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        Node x3 = new ContinuousVariable("X3");
        Node x4 = new ContinuousVariable("X4");
        Node x5 = new ContinuousVariable("X5");
        Node x6 = new ContinuousVariable("X6");
        Node x7 = new ContinuousVariable("X7");

        List<Node> nodes = new ArrayList<>();

        nodes.add(x1);
        nodes.add(x2);
        nodes.add(x3);
        nodes.add(x4);
        nodes.add(x5);
        nodes.add(x6);
        nodes.add(x7);

        Graph graph = new EdgeListGraph(nodes);

        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x1, x4);
        graph.addDirectedEdge(x1, x5);
        graph.addDirectedEdge(x1, x6);

        graph.addDirectedEdge(x2, x7);
        graph.addDirectedEdge(x3, x7);
        graph.addDirectedEdge(x4, x7);
        graph.addDirectedEdge(x5, x7);
        graph.addDirectedEdge(x6, x7);

        System.out.println(graph);

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new SingleGraph(graph)));

        Statistics statistics = new Statistics();
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new SHD());
        statistics.add(new ElapsedTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/clark", simulations,
                algorithms, statistics, params);
    }

    public List<Node> getPrefix(List<Node> order, int i) {
        List<Node> prefix = new ArrayList<>();
        for (int j = 0; j < i; j++) {
            prefix.add(order.get(j));
        }
        return prefix;
    }

    //    @Test
    public void testRaskutti() {
        Ret facts = getFactsRaskutti();

        IndTestDSep test = new IndTestDSep(facts.getFacts());

        OtherPermAlgs otherPermAlgs = new OtherPermAlgs(test);
        otherPermAlgs.setCacheScores(true);
        otherPermAlgs.setMethod(SP);
        otherPermAlgs.setNumStarts(1);

        List<Node> variables = test.getVariables();
        PermutationGenerator gen = new PermutationGenerator(variables.size());
        int[] perm;

        while ((perm = gen.next()) != null) {
            List<Node> p = GraphUtils.asList(perm, variables);

            List<Node> p2 = otherPermAlgs.bestOrder(test.getVariables());
            Graph cpdag = otherPermAlgs.getGraph(true);

            System.out.println(p + " " + cpdag.getNumEdges());
        }
    }

    //    @Test
    public void testManyVarManyDegreeTest() {
        Parameters params = new Parameters();

        params.set(Params.NUM_MEASURES, 5, 6, 7);
        params.set(Params.AVG_DEGREE, 1, 2, 3, 4, 5);
        params.set(Params.GRASP_USE_SCORE, false);
        params.set(Params.NUM_ROUNDS, 10);
        params.set(Params.NUM_RUNS, 100);
        params.set(Params.VERBOSE, true);

        params.set(Params.GRASP_DEPTH, 3);
        params.set(Params.GRASP_CHECK_COVERING, false);
        params.set(Params.GRASP_FORWARD_TUCK_ONLY, false);
        params.set(Params.GRASP_BREAK_AFTER_IMPROVEMENT, true);
        params.set(Params.GRASP_ORDERED_ALG, true);
        params.set(Params.GRASP_USE_SCORE, true);
        params.set(Params.GRASP_USE_VERMA_PEARL, false);
        params.set(Params.GRASP_USE_DATA_ORDER, false);

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.NUM_MEASURES));
        statistics.add(new ParameterColumn(Params.AVG_DEGREE));
        statistics.add(new NumberOfEdgesTrue());
        statistics.add(new NumberOfEdgesEst());
        statistics.add(new SHD());
        statistics.add(new ElapsedTime());

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));
//        simulations.add(new SemSimulationTrueModel(new RandomForward()));

        Algorithms algorithms = new Algorithms();
        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.SemBicScore(), new DSeparationTest()));

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setTabDelimitedTables(false);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/manyvarsmanyavgdegrees",
                simulations, algorithms, statistics, params);
    }

    private boolean isCpdagForDag(Graph cpdag, Graph dag) {
        if (!GraphUtils.undirectedGraph(cpdag).equals(GraphUtils.undirectedGraph(dag))) {
            return false;
        }

        return true;
    }

    private void printGraphs(String label, Map<String, Set<Graph>> graphs) {
        if (!graphs.containsKey(label)) return;

        List<Graph> _graphs = new ArrayList<>(graphs.get(label));
    }

    public Ret getFactsSimple() {
        Node x1 = new GraphNode("1");
        Node x2 = new GraphNode("2");
        Node x3 = new GraphNode("3");
        Node x4 = new GraphNode("4");

        IndependenceFacts facts = new IndependenceFacts();

        facts.add(new IndependenceFact(x1, x3, list(x2)));
        facts.add(new IndependenceFact(x2, x4, list(x1, x3)));

        return new Ret("Simple 4-node 2-path model", facts, 4);
    }

    public Ret getFactsSimpleCanceling() {
        Node x1 = new GraphNode("1");
        Node x2 = new GraphNode("2");
        Node x3 = new GraphNode("3");
        Node x4 = new GraphNode("4");

        IndependenceFacts facts = new IndependenceFacts();

        facts.add(new IndependenceFact(x1, x3, list(x2)));
        facts.add(new IndependenceFact(x2, x4, list(x1, x3)));
        facts.add(new IndependenceFact(x1, x4, list())); // unfaithful.

        return new Ret("Simple 4-node path canceling model", facts, 4);
    }

    public Ret wayneTriangleFaithfulnessFailExample() {
        Node x1 = new GraphNode("1");
        Node x2 = new GraphNode("2");
        Node x3 = new GraphNode("3");
        Node x4 = new GraphNode("4");

        IndependenceFacts facts = new IndependenceFacts();

        facts.add(new IndependenceFact(x1, x2, list()));
        facts.add(new IndependenceFact(x1, x2, list(x3)));
        facts.add(new IndependenceFact(x1, x3, list()));
        facts.add(new IndependenceFact(x1, x3, list(x2)));
        facts.add(new IndependenceFact(x2, x3, list(x4)));

        return new Ret("Wayne triangle faithfulness fail example", facts, 4);
    }

    public Ret getFigure8() {
        Node x1 = new GraphNode("1");
        Node x2 = new GraphNode("2");
        Node x3 = new GraphNode("3");
        Node x4 = new GraphNode("4");
        Node x5 = new GraphNode("5");

        IndependenceFacts facts = new IndependenceFacts();

        facts.add(new IndependenceFact(x1, x3, list(x2)));
        facts.add(new IndependenceFact(x2, x4, list(x1, x3)));
        facts.add(new IndependenceFact(x4, x5, list()));

        return new Ret("Solus Theorem 11, SMR !==> ESP (Figure 8)", facts, 8);
    }

    public Ret getFigure11() {
        Node x1 = new GraphNode("1");
        Node x2 = new GraphNode("2");
        Node x3 = new GraphNode("3");
        Node x4 = new GraphNode("4");
        Node x5 = new GraphNode("5");
        Node x6 = new GraphNode("6");

        IndependenceFacts facts = new IndependenceFacts();

        facts.add(new IndependenceFact(x1, x3));
        facts.add(new IndependenceFact(x1, x5, list(x2, x3, x4)));
        facts.add(new IndependenceFact(x4, x6, list(x1, x2, x3, x5)));
        facts.add(new IndependenceFact(x1, x3, list(x2, x4, x5, x6)));

        return new Ret("Solus Theorem 12, TSP !==> Orientation Faithfulness (Figure 11)", facts, 12);
    }

    public Ret getBryanWorseCaseMParentsNChildren(int m, int n) {
        int newCount = 1;
        int layerCount = 0;
        List<List<Node>> layers = new ArrayList<>();
        List<Node> nodes = new ArrayList<>();

        for (int l = 0; l < 3; l++) {
            layers.add(new ArrayList<>());
        }

        for (int i = 0; i < m; i++) {
            GraphNode node = new GraphNode("" + (newCount++));
            layers.get(0).add(node);
            nodes.add(node);
        }

        for (int i = 0; i < 2; i++) {
            GraphNode node = new GraphNode("" + (newCount++));
            layers.get(1).add(node);
            nodes.add(node);
        }

        for (int i = 0; i < n; i++) {
            GraphNode node = new GraphNode("" + (newCount++));
            layers.get(2).add(node);
            nodes.add(node);
        }

        Graph graph = new EdgeListGraph(nodes);

        for (int l1 = 0; l1 < layers.size(); l1++) {
            for (int l2 = l1; l2 < layers.size(); l2++) {
                for (int i = 0; i < layers.get(l1).size(); i++) {
                    for (int j = 0; j < layers.get(l2).size(); j++) {

                        if (l1 == 1 && l2 == 1) continue;
                        Node node1 = layers.get(l1).get(i);
                        Node node2 = layers.get(l2).get(j);

                        if (node1 == node2) continue;
                        if (graph.isAdjacentTo(node1, node2)) continue;

                        graph.addDirectedEdge(node1, node2);
                    }
                }
            }
        }

        System.out.println(graph);

        IndependenceFacts facts = new IndependenceFacts(graph);

        return new Ret("Bryan's worst case, m = " + m + " n = " + n, facts, graph.getNumEdges());
    }

    public Ret getFigure7() {
        Node x1 = new GraphNode("1");
        Node x2 = new GraphNode("2");
        Node x3 = new GraphNode("3");
        Node x4 = new GraphNode("4");

        IndependenceFacts facts = new IndependenceFacts();

        facts.add(new IndependenceFact(x1, x2, list(x4)));
        facts.add(new IndependenceFact(x1, x3, list(x2)));
        facts.add(new IndependenceFact(x2, x4, list(x1, x3)));

        return new Ret("Solus Theorem 12, ESP !==> TSP (Figure 7)", facts, 4);
    }

    public Ret getFigure6() {
        Node x1 = new GraphNode("1");
        Node x2 = new GraphNode("2");
        Node x3 = new GraphNode("3");
        Node x4 = new GraphNode("4");
        Node x5 = new GraphNode("5");

        IndependenceFacts facts = new IndependenceFacts();

        facts.add(new IndependenceFact(x1, x5, list(x2, x3)));
        facts.add(new IndependenceFact(x2, x4, list(x1, x3)));
        facts.add(new IndependenceFact(x3, x5, list(x1, x2, x4)));
        facts.add(new IndependenceFact(x1, x4, list(x2, x3, x5)));
        facts.add(new IndependenceFact(x1, x4, list(x2, x3)));

        return new Ret("Solus Theorem 11, TSP !==> Faithfulness (Figure 6)", facts, 7);
    }

    public Ret getFactsRaskutti() {
        Node x1 = new GraphNode("1");
        Node x2 = new GraphNode("2");
        Node x3 = new GraphNode("3");
        Node x4 = new GraphNode("4");

        IndependenceFacts facts = new IndependenceFacts();

        facts.add(new IndependenceFact(x1, x3, list(x2)));
        facts.add(new IndependenceFact(x2, x4, list(x1, x3)));
        facts.add(new IndependenceFact(x1, x2, list(x4)));

        return new Ret("Raskutti Theorem 2.4 SMR !==> Adjacency Faithfulness", facts, 4);
    }

    public Ret getWayneExample1() {
        Node x0 = new GraphNode("0");
        Node x1 = new GraphNode("1");
        Node x2 = new GraphNode("2");
        Node x3 = new GraphNode("3");
        Node x4 = new GraphNode("4");

        IndependenceFacts facts = new IndependenceFacts();

        facts.add(new IndependenceFact(x0, x2, list(x1)));
        facts.add(new IndependenceFact(x0, x2, list(x1, x3)));
        facts.add(new IndependenceFact(x0, x2, list(x1, x3, x4)));

        facts.add(new IndependenceFact(x0, x4, list(x1, x3)));
        facts.add(new IndependenceFact(x0, x4, list(x2, x3)));
        facts.add(new IndependenceFact(x0, x4, list(x1, x2, x3)));

        facts.add(new IndependenceFact(x1, x3, list(x0)));
        facts.add(new IndependenceFact(x1, x3, list(x0, x2)));
        facts.add(new IndependenceFact(x1, x3, list(x0, x2, x4)));

        facts.add(new IndependenceFact(x1, x4, list(x0, x2)));
        facts.add(new IndependenceFact(x1, x4, list(x2, x3)));
        facts.add(new IndependenceFact(x1, x4, list(x0, x2, x3)));

        facts.add(new IndependenceFact(x2, x3, list(x0)));
        facts.add(new IndependenceFact(x2, x3, list(x1)));
        facts.add(new IndependenceFact(x2, x3, list(x0, x1)));

        facts.add(new IndependenceFact(x0, x4, list()));

        return new Ret("Wayne example 1", facts, 8);
    }

    private Ret wayneExample2() {
        Node x = new GraphNode("x");
        Node y = new GraphNode("y");
        Node z1 = new GraphNode("z1");
        Node z2 = new GraphNode("z2");
        Node z3 = new GraphNode("z3");
        Node z4 = new GraphNode("z4");

        List<Node> nodes = new ArrayList<>();
        nodes.add(x);
        nodes.add(y);
        nodes.add(z1);
        nodes.add(z2);
        nodes.add(z3);
        nodes.add(z4);

        Graph graph = new EdgeListGraph(nodes);

        graph.addDirectedEdge(x, z1);
        graph.addDirectedEdge(z1, z2);
        graph.addDirectedEdge(z2, y);
        graph.addDirectedEdge(x, z3);
        graph.addDirectedEdge(z3, z4);
        graph.addDirectedEdge(z4, y);

        IndependenceFacts facts = new IndependenceFacts(graph);

        facts.add(new IndependenceFact(x, y));

        return new Ret("Wayne example #2", facts, 6);

    }

    private Ret wayneTriMutationFailsForFaithfulGraphExample() {
        Node x0 = new GraphNode("0");
        Node x1 = new GraphNode("1");
        Node x2 = new GraphNode("2");
        Node x3 = new GraphNode("3");
        Node x4 = new GraphNode("4");

        List<Node> nodes = new ArrayList<>();
        nodes.add(x0);
        nodes.add(x1);
        nodes.add(x2);
        nodes.add(x3);
        nodes.add(x4);

        Graph graph = new EdgeListGraph(nodes);

        graph.addDirectedEdge(x0, x2);
        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x4);


        IndependenceFacts facts = new IndependenceFacts(graph);

        return new Ret("Wayne correct triMutation fail example", facts, 5);

    }

    private boolean setPathsCanceling(Node x1, Node x4, StandardizedSemIm imsd, List<List<Node>> existingPaths) {
        SemGraph graph = imsd.getSemPm().getGraph();
        graph.setShowErrorTerms(false);

        List<List<Node>> paths = GraphUtils.allDirectedPathsFromTo(graph, x1, x4, -1);

        if (paths.size() < 2) return false;

        List<List<Node>> paths2 = new ArrayList<>();
        paths2.addAll(paths);
        paths2.addAll(existingPaths);

        for (int i = 0; i < paths2.size(); i++) {
            for (int j = i + 1; j < paths2.size(); j++) {
                List<Node> path1 = new ArrayList<>(paths2.get(i));
                List<Node> path2 = new ArrayList<>(paths2.get(j));
                path1.retainAll(path2);
                path1.remove(x1);
                path1.remove(x4);
                if (!path1.isEmpty()) return false;
            }
        }

        existingPaths.addAll(paths);

        List<Double> products = new ArrayList<>();

        for (List<Node> path : paths) {
            double p = 1.0;

            for (int i = 0; i < path.size() - 1; i++) {
                Node x = path.get(i);
                Node y = path.get(i + 1);
                p *= imsd.getEdgeCoef(x, y);
            }

            products.add(p);
        }

        double sum = 0;

        for (int i = 1; i < products.size(); i++) {
            sum += products.get(i);
        }

        double factor = 1;
        Set<NodePair> pairs = new HashSet<>();
        boolean changed = true;

        while (changed) {
            changed = false;

            for (int j = 1; j < paths.size(); j++) {
                List<Node> path = paths.get(j);
                double p = 1.0;

                for (int i = 0; i < path.size() - 1; i++) {
                    Node x = path.get(i);
                    Node y = path.get(i + 1);
                    if (pairs.contains(new NodePair(x, y))) continue;
                    boolean set = imsd.setEdgeCoefficient(x, y, -factor * imsd.getEdgeCoef(x, y) * (products.get(0)) / sum);
                    if (set) {
                        pairs.add(new NodePair(x, y));
                        changed = true;
                    }
                }

                products.add(p);
            }
        }

        return true;
    }

    private List<Node> list(Node... nodes) {
        List<Node> list = new ArrayList<>();
        Collections.addAll(list, nodes);
        return list;
    }

    //    @Test
    public void testPfci() {
        Parameters params = new Parameters();
        params.set(Params.SAMPLE_SIZE, 1000);
        params.set(Params.NUM_MEASURES, 30);
        params.set(Params.NUM_LATENTS, 6);
        params.set(Params.AVG_DEGREE, 6);
        params.set(Params.RANDOMIZE_COLUMNS, true);
        params.set(Params.COEF_LOW, 0);
        params.set(Params.COEF_HIGH, 1);
        params.set(Params.VAR_LOW, 1);
        params.set(Params.VAR_HIGH, 1);
        params.set(Params.VERBOSE, true);

        params.set(Params.NUM_RUNS, 10);

        params.set(Params.MAX_PATH_LENGTH, -1);
        params.set(Params.COMPLETE_RULE_SET_USED, true);
        params.set(Params.MAX_PATH_LENGTH, -1);

        // Flags
        params.set(Params.GRASP_DEPTH, 5);
        params.set(Params.GRASP_UNCOVERED_DEPTH, 3);
        params.set(Params.GRASP_FORWARD_TUCK_ONLY, false);
        params.set(Params.GRASP_USE_VERMA_PEARL, false);
        params.set(Params.TIMEOUT, 30);
        params.set(Params.NUM_STARTS, 1);
        params.set(Params.GRASP_ALG, true, false);

        params.set(Params.PENALTY_DISCOUNT, 2);
        params.set(Params.ALPHA, 0.001);

        Algorithms algorithms = new Algorithms();
        algorithms.add(new GRaSPFCI(new edu.cmu.tetrad.algcomparison.score.SemBicScore(), new FisherZ()));
        algorithms.add(new FciMax(new FisherZ()));
        algorithms.add(new Rfci(new FisherZ()));
        algorithms.add(new Gfci(new FisherZ(), new edu.cmu.tetrad.algcomparison.score.SemBicScore()));

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.GRASP_ALG));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ArrowheadPrecisionCommonEdges());
        statistics.add(new ArrowheadRecallCommonEdges());
        statistics.add(new ElapsedTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/testPfci", simulations,
                algorithms, statistics, params);
    }

    //    @Test
    public void test6Examples() {
        List<Ret> allFacts = new ArrayList<>();

        allFacts.add(getFactsSimple());
        allFacts.add(getFactsSimpleCanceling());
        allFacts.add(wayneTriangleFaithfulnessFailExample());
        allFacts.add(wayneTriMutationFailsForFaithfulGraphExample());
        allFacts.add(getFigure7());
        allFacts.add(getFigure8());
//        allFacts.add(getFigure11());

//        allFacts.add(getBryanWorseCaseMParentsNChildren(2, 2));

        allFacts.add(getFigure8());

        int count = 0;

        boolean verbose = false;
        int numRounds = 50;
        int depth = 50;
        int maxPermSize = 4;

        boolean printCpdag = false;

        for (int i = 0; i < allFacts.size(); i++) {
            Ret facts = allFacts.get(i);
            count++;

            System.out.println();
            System.out.println("Test #" + (i + 1));
            System.out.println(facts.getLabel());
            System.out.println(facts.getFacts());
        }

        for (int i = 0; i < allFacts.size(); i++) {
            boolean passed = true;

            Ret facts = allFacts.get(i);
            count++;

            TeyssierScorer scorer = new TeyssierScorer(new IndTestDSep(facts.getFacts()),
                    new GraphScore(facts.getFacts()));

            OrderedMap<String, Set<Graph>> graphs = new ListOrderedMap<>();
            OrderedMap<String, Set<String>> labels = new ListOrderedMap<>();

            List<Node> variables = facts.facts.getVariables();
            Collections.sort(variables);

            PermutationGenerator gen = new PermutationGenerator(variables.size());
            int[] perm;

            while ((perm = gen.next()) != null) {
                List<Node> p = GraphUtils.asList(perm, variables);

                Grasp search = new Grasp(new IndTestDSep(facts.getFacts()));
                search.setDepth(depth);
                List<Node> order = search.bestOrder(p);
//                System.out.println(p + " " + order + " truth = " + facts.getTruth() + " found = " + search.getNumEdges());// + " " + search.getGraph(false));

                if (search.getNumEdges() != facts.getTruth()) {
                    passed = false;
//                        break;
                }
            }

            System.out.println((i + 1) + " " + (passed ? "P " : "F"));
        }
    }

    //    @Test
    public void test7Examples() {
        List<Ret> allFacts = new ArrayList<>();

        allFacts.add(getFactsSimple());
        allFacts.add(getFactsSimpleCanceling());
        allFacts.add(wayneTriangleFaithfulnessFailExample());
        allFacts.add(wayneTriMutationFailsForFaithfulGraphExample());
        allFacts.add(getFigure7());
        allFacts.add(getFigure8());
        allFacts.add(getFigure11());
        allFacts.add(wayneExample2());
//        allFacts.add(getBryanWorseCaseMParentsNChildren(2, 2));

//        allFacts.add(getFigure8());

        int count = 0;

        boolean verbose = false;
        int numRounds = 50;
        int depth = 5;
        int maxPermSize = 6;

        boolean printCpdag = false;

        for (int i = 0; i < allFacts.size(); i++) {
            Ret facts = allFacts.get(i);
            count++;

            System.out.println();
            System.out.println("Test #" + (i + 1));
            System.out.println(facts.getLabel());
            System.out.println(facts.getFacts());
        }

        for (int i = 0; i < allFacts.size(); i++) {
            boolean passed = true;

            Ret facts = allFacts.get(i);
            count++;

            TeyssierScorer scorer = new TeyssierScorer(new IndTestDSep(facts.getFacts()),
                    new GraphScore(facts.getFacts()));

            OrderedMap<String, Set<Graph>> graphs = new ListOrderedMap<>();
            OrderedMap<String, Set<String>> labels = new ListOrderedMap<>();

            List<Node> variables = facts.facts.getVariables();
            Collections.sort(variables);

            PermutationGenerator gen = new PermutationGenerator(variables.size());
            int[] perm;

            while ((perm = gen.next()) != null) {
                List<Node> p = GraphUtils.asList(perm, variables);

                Grasp search = new Grasp(new IndTestDSep(facts.getFacts()));
                search.setDepth(depth);
                search.setUncoveredDepth(depth);
                search.setNonSingularDepth(depth);
                search.setUseRaskuttiUhler(false);
                List<Node> order = search.bestOrder(p);
//                    System.out.println(p + " " + order + " truth = " + facts.getTruth() + " found = " + search.getNumEdges());
//                    System.out.println(search.getGraph(false));
//
                if (search.getNumEdges() != facts.getTruth()) {
                    passed = false;
//                        break;
                }
            }

            System.out.println((i + 1) + " " + (passed ? "P " : "F"));
        }
    }

    //    @Test
    public void testWorstCaseExamples() {
        List<Ret> allFacts = new ArrayList<>();

        allFacts.add(getBryanWorseCaseMParentsNChildren(3, 2));

        int count = 0;

        boolean printCpdag = false;

        for (int i = 0; i < allFacts.size(); i++) {
            Ret facts = allFacts.get(i);
            count++;

            System.out.println();
            System.out.println("Test #" + (i + 1));
            System.out.println(facts.getLabel());
            System.out.println(facts.getFacts());
        }

        for (int i = 0; i < allFacts.size(); i++) {
            boolean passed = true;

            Ret facts = allFacts.get(i);
            count++;

            TeyssierScorer scorer = new TeyssierScorer(new IndTestDSep(facts.getFacts()),
                    new GraphScore(facts.getFacts()));

            OrderedMap<String, Set<Graph>> graphs = new ListOrderedMap<>();
            OrderedMap<String, Set<String>> labels = new ListOrderedMap<>();

            List<Node> variables = facts.facts.getVariables();
            Collections.sort(variables);

            PermutationGenerator gen = new PermutationGenerator(variables.size());
            int[] perm;

            while ((perm = gen.next()) != null) {
                List<Node> p = GraphUtils.asList(perm, variables);

                OtherPermAlgs search = new OtherPermAlgs(new IndTestDSep(facts.getFacts()));
//                    search.setMaxPermSize(6);
                search.setDepth(Integer.MAX_VALUE);
//                    search.setDepth((method == BOSS1 || method == BOSS2 || method == GRASP || method == RCG) ? 20 : 5);
                search.setNumRounds(20);
//                    search.setVerbose(true);
                List<Node> order = search.bestOrder(p);
                System.out.println(p + " " + order + " " + search.getNumEdges());// + " " + search.getGraph(false));

                if (search.getNumEdges() != facts.getTruth()) {
                    passed = false;
//                        break;
                }

//                Pc search = new Pc(new IndTestDSep(facts.getFacts()));
//                System.out.println(p + " " + search.search().getNumEdges());

//                Fges search = new Fges(new GraphScore(facts.getFacts()));
//                System.out.println(p + " " + search.search().getNumEdges());

            }

            System.out.println((i + 1) + " " + (passed ? "P " : "F"));
        }
    }

    //    @Test
    public void testWayne2() {
//        int[] numNodes = new int[]{30};//4, 5, 6, 7};
//        int[] avgDegree = new int[]{8};//1, 2, 3, 4};
//        int[] size = new int[]{1000};//100, 1000, 10000, 100000};

        int[] numNodes = new int[]{4, 5, 6, 7};
        int[] avgDegree = new int[]{1, 2, 3, 4};
        int[] size = new int[]{100, 1000, 10000, 100000};

        double coefLow = 0.2;
        double coefHigh = 0.7;
        boolean coefSymmetric = true;
        double varlow = 1;
        double varHigh = 3;
        boolean randomizeColumns = false;
        double[] alpha = new double[]{0.001, 0.005, 0.01, 0.05, 0.1};
        int numRuns = 100;
        System.out.println("NumNodes\tAvgDegree\tSize\tGS\tPearl0.001\tPearl0.005\tPearl0.01\tPear0.05\tPearl0.1");

        for (int m : numNodes) {
            for (int a : avgDegree) {
                for (int s : size) {
                    int gsCount = 0;
                    int[] pearlCounts = new int[alpha.length];

                    int gsShd = 0;
                    int[] pearlShd = new int[alpha.length];

                    for (int r = 0; r < numRuns; r++) {
                        NumberFormat nf = new DecimalFormat("0.00");

                        int numEdges = (int) (a * m / 2.);

                        Graph graph = GraphUtils.randomGraph(m, 0,
                                numEdges, 100, 100, 100, false);

                        Parameters parameters = new Parameters();
                        parameters.set(Params.COEF_LOW, coefLow);
                        parameters.set(Params.COEF_HIGH, coefHigh);
                        parameters.set(Params.COEF_SYMMETRIC, coefSymmetric);
                        parameters.set(Params.VAR_LOW, varlow);
                        parameters.set(Params.VAR_HIGH, varHigh);
                        parameters.set(Params.RANDOMIZE_COLUMNS, randomizeColumns);

                        SemPm pm = new SemPm(graph);
                        SemIm im = new SemIm(pm, parameters);

                        DataSet dataSet = im.simulateData(s, false);
                        List<Node> V = dataSet.getVariables();

                        IndTestDSep dsep = new IndTestDSep(graph);

                        edu.cmu.tetrad.search.SemBicScore score = new edu.cmu.tetrad.search.SemBicScore(dataSet);
                        score.setPenaltyDiscount(1);

                        // Random permutation over 1...|V|.
                        List<Integer> l = new ArrayList<>();
                        for (int w = 0; w < V.size(); w++) {
                            l.add(w);
                        }

                        Collections.shuffle(l);
                        Collections.shuffle(l);
                        Collections.shuffle(l);

                        int[] perm = new int[l.size()];
                        for (int w = 0; w < V.size(); w++) {
                            perm[w] = l.get(w);
                        }

                        List<Node> _perm0 = GraphUtils.asList(perm, dsep.getVariables());

                        TeyssierScorer scorer1 = new TeyssierScorer(dsep,
                                new GraphScore(graph));
                        scorer1.setUseVermaPearl(true);
                        scorer1.score(_perm0);
                        Graph g1 = scorer1.getGraph(true);

                        IndependenceTest test = new IndTestFisherZ(dataSet, 0.05);
                        List<Node> _perm = GraphUtils.asList(perm, test.getVariables());

                        TeyssierScorer scorer2 = new TeyssierScorer(test, score);
                        scorer2.setUseVermaPearl(true);
                        scorer2.score(_perm);

                        Graph g2 = scorer2.getGraph(true);
                        g2 = GraphUtils.replaceNodes(g2, g1.getNodes());

                        if (g1.equals(g2)) gsCount++;
                        gsShd += SearchGraphUtils.structuralHammingDistance(
                                SearchGraphUtils.cpdagForDag(g1), SearchGraphUtils.cpdagForDag(g2));

                        for (int i = 0; i < alpha.length; i++) {
//                            test.setAlpha(alpha[i]);
                            test = new IndTestFisherZ(dataSet, alpha[i]);

                            TeyssierScorer scorer3 = new TeyssierScorer(test, score);
                            scorer3.setUseVermaPearl(true);
                            scorer3.score(_perm);
                            Graph g3 = scorer3.getGraph(true);

                            g3 = GraphUtils.replaceNodes(g3, g1.getNodes());

                            if (g1.equals(g3)) pearlCounts[i]++;
                            pearlShd[i] += SearchGraphUtils.structuralHammingDistance(
                                    SearchGraphUtils.cpdagForDag(g1), SearchGraphUtils.cpdagForDag(g3));
                        }
                    }

                    System.out.print(m + "\t" + a + "\t" + s + "\t");

//                    System.out.print(gsCount / (double) numRuns + "\t");
                    System.out.print(gsShd / (double) numRuns + "\t");

//                    for (int i = 0; i < alpha.length; i++) {
//                        System.out.print(pearlCounts[i] / (double) numRuns + "\t");
//                    }

                    for (int i = 0; i < alpha.length; i++) {
                        System.out.print(pearlShd[i] / (double) numRuns + "\t");
                    }

                    System.out.println();
                }
            }
        }
    }

    //    @Test
    public void testFgesCondition1() {

        // This just checks to make sure the causalOrdering method is behaving correctly.

        for (int k = 0; k < 100; k++) {
            Graph g = GraphUtils.randomGraph(10, 0, 15, 100,
                    100, 100, false);
            IndTestDSep test = new IndTestDSep(g);
            GraphScore score = new GraphScore(g);

            Fges fges = new Fges(score);
            Graph cpdag1 = fges.search();

            List<Node> pi = GraphUtils.getCausalOrdering(cpdag1);

            TeyssierScorer scorer = new TeyssierScorer(test, score);
            scorer.setUseScore(false);
            scorer.score(pi);
            Graph cpdag2 = scorer.getGraph(true);

            System.out.println("Cpdag1 # edges = " + cpdag1.getNumEdges());
            System.out.println("Cpdag2 # edges = " + cpdag2.getNumEdges());

            assert cpdag1.getNumEdges() == cpdag2.getNumEdges();
        }
    }

    //    @Test
    public void testFgesCondition2() {

        // This just checks to make sure the causalOrdering method is behaving correctly.

        int count = 0;
        int all = 0;

        for (int k = 0; k < 100; k++) {
            Graph g = GraphUtils.randomGraph(20, 0, 30, 100,
                    100, 100, false);
            SemPm pm = new SemPm(g);
            SemIm im = new SemIm(pm);
            DataSet d = im.simulateData(1000, false);

            IndTestFisherZ test = new IndTestFisherZ(d, 0.001);
            edu.cmu.tetrad.search.SemBicScore score = new edu.cmu.tetrad.search.SemBicScore(d);
            score.setPenaltyDiscount(2);

            Fges fges = new Fges(score);
            Graph cpdag = fges.search();

            List<Node> pi1 = GraphUtils.getCausalOrdering(cpdag);

            List<Node> pi2 = new ArrayList<>(pi1);
            shuffle(pi2);

            TeyssierScorer scorer = new TeyssierScorer(test, score);
            scorer.setUseScore(true);
            scorer.score(pi1);
            Graph cpdag1 = scorer.getGraph(false);

            scorer.score(pi2);
            Graph cpdag2 = scorer.getGraph(false);

            System.out.println("Cpdag1 # edges = " + cpdag1.getNumEdges());
            System.out.println("Cpdag2 # edges = " + cpdag2.getNumEdges());


            if (cpdag1.getNumEdges() <= cpdag2.getNumEdges()) {
                count++;
            }

            all++;
        }

        System.out.println(count / (float) all);
    }

    //    @Test
    public void testAddUnfaithfulIndependencies() {
        Graph graph = GraphUtils.randomGraph(7, 0, 15, 100, 100,
                100, false);

        System.out.println("Source = " + graph);//SearchGraphUtils.cpdagForDag(graph));

        IndTestDSep dsep = new IndTestDSep(graph);
        IndependenceFacts facts = new IndependenceFacts(graph);

        List<Node> nodes = graph.getNodes();

        int count = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 1; j < i; j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                System.out.println("<x, y> = <" + x + ", " + y + ">");

                List<List<Node>> treks = GraphUtils.treks(graph, x, y, 4);

                if (treks.size() >= 2) {
                    IndependenceFact fact = new IndependenceFact(x, y, new ArrayList<>());
                    facts.add(fact);
                    System.out.println("Added " + fact);

                    count++;
                } else {
                    List<List<Node>> paths = GraphUtils.allPathsFromTo(graph, x, y, 4);

                    if (paths.size() >= 1) {
                        List<List<Node>> nonTrekPaths = new ArrayList<>();

                        for (List<Node> path : paths) {
                            if (!treks.containsAll(path)) {
                                nonTrekPaths.add(path);
                            }
                        }

                        Set<Node> pathColliders = new HashSet<>();

                        for (List<Node> path : nonTrekPaths) {
                            for (int w = 1; w < path.size() - 1; w++) {
                                if (!graph.isDefCollider(path.get(w - 1), path.get(w), path.get(w + 1))) {
                                    pathColliders.add(path.get(w));
                                }
                            }
                        }

                        if (dsep.isIndependent(x, y, new ArrayList<>(pathColliders))) {
                            IndependenceFact fact = new IndependenceFact(x, y, new ArrayList<>(pathColliders));
                            facts.add(fact);
                            System.out.println("Added " + fact);
                            count++;
                        }
                    }
                }

                if (count >= 2) break;
            }
        }

        if (count >= 2) {

            IndependenceTest test = new IndTestDSep(facts);

            Grasp grasp = new Grasp(test);
            grasp.bestOrder(test.getVariables());
            Graph other = grasp.getGraph(false);

            grasp.bestOrder(test.getVariables());
            Graph frugal = other;
            System.out.println("SP " + frugal);

            System.out.println("\n-----\n");

            assert (frugal.getNumEdges() == other.getNumEdges());
        }
    }

    private static class Ret {
        private final String label;
        private final IndependenceFacts facts;
        private int truth;

        public Ret(String label, IndependenceFacts facts, int truth) {
            this.label = label;
            this.facts = facts;
            this.truth = truth;
        }

        public String getLabel() {
            return label;
        }

        public IndependenceFacts getFacts() {
            return facts;
        }

        public int getTruth() {
            return truth;
        }
    }
}




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
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.*;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Rfci;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.independence.MSeparationTest;
import edu.cmu.tetrad.algcomparison.score.GicScores;
import edu.cmu.tetrad.algcomparison.score.MSeparationScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.simulation.*;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.DegenerateGaussianScore;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestChiSquare;
import edu.cmu.tetrad.search.test.IndTestDegenerateGaussianLrt;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.GraphoidAxioms;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.TeyssierScorer;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.sem.StandardizedSemIm;
import edu.cmu.tetrad.util.*;
import edu.pitt.dbmi.data.reader.Delimiter;
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

import static edu.cmu.tetrad.util.RandomUtil.shuffle;


/**
 * @author josephramsey
 */
@SuppressWarnings("ALL")
public final class TestGrasp {
    boolean precomputeCovariances = true;

    public static void main(String[] args) {
//        new TestGrasp().testLuFigure3();
//        new TestGrasp().testLuFigure6();
//        new TestGrasp().testGrasp2();
//        new TestGrasp().wayneCheckDensityClaim2();
//        new TestGrasp().bryanCheckDensityClaims();

//        new TestGrasp().testMsep();

        new TestGrasp().testPredictGoodStats();

    }

    @NotNull
    private static edu.cmu.tetrad.search.Grasp getGrasp(Score score, IndependenceTest test) {
        edu.cmu.tetrad.search.Grasp grasp;

        if (true) {
            grasp = new edu.cmu.tetrad.search.Grasp(test);
        } else {
            grasp = new edu.cmu.tetrad.search.Grasp(score);
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

        edu.cmu.tetrad.search.Grasp grasp = getGrasp(score, test);

        grasp.setNumStarts(1);
        grasp.setVerbose(true);
        List<Node> perm = grasp.bestOrder(order);
        Graph dag = grasp.getGraph(false);

        printFailed(g, dag, order + " \n" + dag);
    }

    @AfterClass
    public static void afterClass() throws Exception {

    }

    private static void extractedWayne(Node x1, Node x2, Node x3, Node x4, IndependenceTest chiSq) {
        System.out.println(LogUtilsSearch.independenceFact(x1, x2, nodeSet()) + " " + chiSq.checkIndependence(x1, x2).isIndependent());
        System.out.println(LogUtilsSearch.independenceFact(x1, x2, nodeSet(x3)) + " " + chiSq.checkIndependence(x1, x2, x3).isIndependent());
        System.out.println(LogUtilsSearch.independenceFact(x1, x2, nodeSet(x4)) + " " + chiSq.checkIndependence(x1, x2, x4).isIndependent());
        System.out.println(LogUtilsSearch.independenceFact(x1, x2, nodeSet(x3, x4)) + " " + chiSq.checkIndependence(x1, x2, x3, x4).isIndependent());
    }

    @NotNull
    private static Set<Node> nodeSet(Node... n) {
        Set<Node> list = new HashSet<>();
        for (Node m : n) list.add(m);
        return list;
    }

    private void testExampleBnSim() {

        Parameters params = new Parameters();

        params.set(Params.NUM_MEASURES, 20);
        params.set(Params.NUM_LATENTS, 0);
        params.set(Params.AVG_DEGREE, 6);

        params.set(Params.MIN_CATEGORIES, 3);
        params.set(Params.MAX_CATEGORIES, 3);

        params.set(Params.RANDOMIZE_COLUMNS, true);
        params.set(Params.SAMPLE_SIZE, 500);
        params.set(Params.SAVE_LATENT_VARS, false);
        params.set(Params.SEED, 29493L);

        params.set(Params.NUM_RUNS, 1);

        BayesNetSimulation sim_ = new BayesNetSimulation(new RandomForward());
        sim_.createData(params, true);
        DataModel data_model = sim_.getDataModel(0);
        Graph graph = sim_.getTrueGraph(0);

    }

    private void testCgScore() {

        Parameters params = new Parameters();

        params.set(Params.NUM_MEASURES, 20);
        params.set(Params.NUM_LATENTS, 0);
        params.set(Params.AVG_DEGREE, 6);

        params.set(Params.MIN_CATEGORIES, 3);
        params.set(Params.MAX_CATEGORIES, 3);
        params.set(Params.PERCENT_DISCRETE, 50);
        params.set(Params.DIFFERENT_GRAPHS, false);

        params.set(Params.RANDOMIZE_COLUMNS, true);
        params.set(Params.SAMPLE_SIZE, 500);
        params.set(Params.SAVE_LATENT_VARS, false);
        params.set(Params.SEED, 29493L);

        params.set(Params.NUM_RUNS, 1);

        LeeHastieSimulation sim_ = new LeeHastieSimulation(new RandomForward());
        sim_.createData(params, true);
        DataSet data = (DataSet) sim_.getDataModel(0);
        Graph graph = sim_.getTrueGraph(0);

        double penaltyDiscount = 2.0;
        double structurePrior = 1.0;
        boolean discretize = true;

        DegenerateGaussianScore score = new DegenerateGaussianScore((DataSet) data, precomputeCovariances);

        IndTestDegenerateGaussianLrt test = new IndTestDegenerateGaussianLrt((DataSet) data);
        test.setAlpha(0.01);


        edu.cmu.tetrad.search.Fges alg = new edu.cmu.tetrad.search.Fges(score);
        Graph pat = alg.search();

        System.out.println("FGES" + pat);

        edu.cmu.tetrad.search.Pc pc = new edu.cmu.tetrad.search.Pc(test);
        Graph pat2 = alg.search();

        System.out.println("PC" + pat2);

        Parameters parameters = new Parameters();

//        GRaSP grasp = new GRaSP(new ConditionalGaussianBicScore(), new ConditionalGaussianLRT());
//        Graph pat3 = grasp.search(data, parameters);

        edu.cmu.tetrad.search.Grasp boss = new edu.cmu.tetrad.search.Grasp(test, score);
        boss.setUseDataOrder(true);
        boss.setNumStarts(1);
        boss.bestOrder(score.getVariables());
        Graph pat3 = boss.getGraph(true);

        System.out.println("GRaSP" + pat3);

    }

    private void testPredictGoodStats() {

//        RandomUtil.getInstance().setSeed(12341292889L);

        Parameters params = new Parameters();

        params.set(Params.NUM_MEASURES, 100);
        params.set(Params.NUM_LATENTS, 0);
        params.set(Params.AVG_DEGREE, 10);

        params.set(Params.DIFFERENT_GRAPHS, true);

        params.set(Params.RANDOMIZE_COLUMNS, true);
        params.set(Params.SAMPLE_SIZE, 1000);

        params.set(Params.NUM_RUNS, 1);
//        params.set(Params.PARALLELIZED, false);

//        params.set(Params.ALPHA, 0.05);
        params.set(Params.PENALTY_DISCOUNT, 4.0);
//        params.set(Params.POISSON_LAMBDA, 1, 2, 4);
//        params.set(Params.ZS_RISK_BOUND, 0.001, 0.01, 0.05, 0.1);

//        params.set(Params.STABLE_FAS, false, true);
//        params.set(Params.USE_MAX_P_HEURISTIC, false, true);
//        params.set(Params.USE_BES, false, true);

//        params.set(Params.GRASP_DEPTH, 3);
//        params.set(Params.GRASP_SINGULAR_DEPTH, 1);
//        params.set(Params.GRASP_NONSINGULAR_DEPTH, 1);
//        params.set(Params.GRASP_ORDERED_ALG, false);
//        params.set(Params.GRASP_USE_RASKUTTI_UHLER, false);
//        params.set(Params.GRASP_USE_DATA_ORDER, true);

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Algorithms algorithms = new Algorithms();

//        algorithms.add(new Pc(new FisherZ()));
//        algorithms.add(new Pc(new SemBicDTest()));
//        algorithms.add(new Fges(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
//        algorithms.add(new Fges(new edu.cmu.tetrad.algcomparison.score.PoissonPriorScore()));
//        algorithms.add(new Fges(new edu.cmu.tetrad.algcomparison.score.ZhangShenBoundScore()));
//        algorithms.add(new Grasp(new FisherZ(), new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
//        algorithms.add(new Grasp(new FisherZ(), new edu.cmu.tetrad.algcomparison.score.PoissonPriorScore()));
//        algorithms.add(new Grasp(new FisherZ(), new edu.cmu.tetrad.algcomparison.score.ZhangShenBoundScore()));
        algorithms.add(new Boss(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
//        algorithms.add(new Boss(new edu.cmu.tetrad.algcomparison.score.PoissonPriorScore()));
//        algorithms.add(new Boss(new edu.cmu.tetrad.algcomparison.score.ZhangShenBoundScore()));

        Statistics statistics = new Statistics();
//        statistics.add(new ParameterColumn(Params.ALPHA));
//        statistics.add(new ParameterColumn(Params.PENALTY_DISCOUNT));
//        statistics.add(new ParameterColumn(Params.POISSON_LAMBDA));
//        statistics.add(new ParameterColumn(Params.ZS_RISK_BOUND));
//        statistics.add(new FractionDependentUnderNull(0.01));
//        statistics.add(new FractionDependentUnderNull());
//        statistics.add(new PvalueUniformityUnderNull(0.01));
//        statistics.add(new PvalueDistanceToAlpha(0.01));
//        statistics.add(new MarkovAdequacyScore());
//        statistics.add(new BicEst(2));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
//        statistics.add(new ArrowheadPrecisionCommonEdges());
//        statistics.add(new ArrowheadRecallCommonEdges());
//        statistics.add(new StructuralHammingDistance());

        statistics.add(new ElapsedCpuTime());

//        statistics.setWeight("MAS", 1.0);

        Comparison comparison = new Comparison();
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);
//        comparison.setSortByUtility(true);
        comparison.setShowAlgorithmIndices(true);
        comparison.compareFromSimulations("grasp_boss_timing", simulations, algorithms, statistics, params);
    }

    //    @Test
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
        params.set(Params.GRASP_SINGULAR_DEPTH, 1);
        params.set(Params.GRASP_NONSINGULAR_DEPTH, 1);
        params.set(Params.GRASP_TOLERANCE_DEPTH, 0);

        params.set(Params.GRASP_ORDERED_ALG, true);
        params.set(Params.GRASP_USE_SCORE, true);
        params.set(Params.GRASP_USE_RASKUTTI_UHLER, false);
        params.set(Params.USE_DATA_ORDER, true);
        params.set(Params.ALLOW_INTERNAL_RANDOMNESS, false);
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

            params.set(Params.GRASP_SINGULAR_DEPTH, 0, 1);
            params.set(Params.GRASP_NONSINGULAR_DEPTH, 0);

            params.set(Params.NUM_MEASURES, 20, 30, 40, 50, 60, 70, 80, 90, 100);
            params.set(Params.AVG_DEGREE, 6);
            params.set(Params.SAMPLE_SIZE, 1000);

            dataPath = "/Users/josephramsey/Downloads/grasp-data/vary_measured";
            resultsPath = "/Users/josephramsey/Downloads/grasp/vary_measured_2";

            doPaperRun(params, dataPath, resultsPath, false);

            params.set(Params.NUM_MEASURES, 60);
            params.set(Params.AVG_DEGREE, 2, 3, 4, 5, 6, 7, 8, 9, 10);
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
        algorithms.add(new Grasp(new FisherZ(), new edu.cmu.tetrad.algcomparison.score.SemBicScore()));

        if (doPcFges) {
            algorithms.add(new Fges(
                    new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
            algorithms.add(new Pc(new FisherZ()));
        }

        Simulations simulations = new Simulations();
        Simulation simulation = new SemSimulation(new RandomForward());
        simulations.add(simulation);

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.GRASP_DEPTH));
        statistics.add(new ParameterColumn(Params.GRASP_SINGULAR_DEPTH));
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
        statistics.add(new ElapsedCpuTime());

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

    //    @Test
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
        params.set(Params.GRASP_SINGULAR_DEPTH, 0);//1);
        params.set(Params.GRASP_NONSINGULAR_DEPTH, 0);//1);
        params.set(Params.GRASP_ORDERED_ALG, true);
        params.set(Params.GRASP_USE_RASKUTTI_UHLER, false);
        params.set(Params.USE_DATA_ORDER, false);
        params.set(Params.CACHE_SCORES, true);


        Algorithms algorithms = new Algorithms();
        algorithms.add(new Grasp(new FisherZ(), new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
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
        statistics.add(new ElapsedCpuTime());

        Comparison comparison = new Comparison();
        comparison.setSaveData(false);
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/varying_final2/testPaperSimulations_"
                                          + type, simulations,
                algorithms, statistics, params);
    }

    public void newAlgsHeadToHead() {
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
        params.set(Params.GRASP_SINGULAR_DEPTH, 1);
        params.set(Params.GRASP_NONSINGULAR_DEPTH, 1);
        params.set(Params.GRASP_TOLERANCE_DEPTH, 0);

        params.set(Params.GRASP_ORDERED_ALG, true);
        params.set(Params.GRASP_USE_SCORE, true);
        params.set(Params.GRASP_USE_RASKUTTI_UHLER, false);
        params.set(Params.USE_DATA_ORDER, true);
        params.set(Params.ALLOW_INTERNAL_RANDOMNESS, false);
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

            params.set(Params.GRASP_SINGULAR_DEPTH, 1);
            params.set(Params.GRASP_NONSINGULAR_DEPTH, 1);

            params.set(Params.NUM_MEASURES, 60);
            params.set(Params.AVG_DEGREE, 3);
            params.set(Params.SAMPLE_SIZE, 1000);

            dataPath = "/Users/josephramsey/Downloads/grasp-data/vary_avg_degree";
            resultsPath = "/Users/josephramsey/Downloads/grasp/newAlgsComparison";

            doNewAgsHeadToHead(params, dataPath, resultsPath, false);
//
//            params.set(Params.NUM_MEASURES, 60);
//            params.set(Params.AVG_DEGREE, 2, 3, 4, 5, 6, 7, 8, 9, 10);
//            params.set(Params.STANDARDIZE, 1000);
//
//            dataPath = "/Users/josephramsey/Downloads/grasp-data/vary_avg_degree";
//            resultsPath = "/Users/josephramsey/Downloads/grasp/vary_avg_degree_2";
//
//            doPaperRun(params, dataPath, resultsPath, false);
//
//            params.set(Params.NUM_MEASURES, 60);
//            params.set(Params.AVG_DEGREE, 6);
//            params.set(Params.SAMPLE_SIZE, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000);
//
//            dataPath = "/Users/josephramsey/Downloads/grasp-data/vary_sample_size";
//            resultsPath = "/Users/josephramsey/Downloads/grasp/vary_sample_size_2";
//
//            doPaperRun(params, dataPath, resultsPath, false);

        }
    }

    public void doNewAgsHeadToHead(Parameters params, String dataPath, String resultsPath, boolean doPcFges) {
        Algorithms algorithms = new Algorithms();
//        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.SemBicScore(), new FisherZ()));
        algorithms.add(new Boss(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
//        algorithms.add(new BRIDGES(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));

//        if (doPcFges) {
//            algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(
//                    new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
//            algorithms.add(new PC(new FisherZ()));
//        }

        Simulations simulations = new Simulations();
        Simulation simulation = new SemSimulation(new RandomForward());
        simulations.add(simulation);

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.GRASP_DEPTH));
        statistics.add(new ParameterColumn(Params.GRASP_SINGULAR_DEPTH));
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
        statistics.add(new ElapsedCpuTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setSaveGraphs(true);
        comparison.setSavePags(true);
        comparison.setSaveData(false);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.CPDAG_of_the_true_DAG);

//        comparison.saveToFiles("/Users/josephramsey/Downloads/grasp/vary_sample_size",
//                simulation, params);

        comparison.compareFromFiles(dataPath,
                resultsPath,
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
        params.set(Params.GRASP_SINGULAR_DEPTH, 1);
        params.set(Params.GRASP_NONSINGULAR_DEPTH, 1);

        params.set(Params.GRASP_ORDERED_ALG, true);
//        params.set(Params.GRASP_USE_SCORE, true);
        params.set(Params.GRASP_USE_RASKUTTI_UHLER, false);
        params.set(Params.USE_DATA_ORDER, false);
        params.set(Params.CACHE_SCORES, true);
//        params.set(Params.GRASP_ALG, false);


        Algorithms algorithms = new Algorithms();
        algorithms.add(new Grasp(new FisherZ(), new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
        algorithms.add(new Fges(
                new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
        algorithms.add(new Pc(new FisherZ()));

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
        statistics.add(new ElapsedCpuTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/testGraspForClark",
                simulations, algorithms, statistics, params);
    }

    //    @Test
    public void testGrasp1Bryan() {
        Parameters params = new Parameters();
        params.set(Params.NUM_MEASURES, 20);
        params.set(Params.AVG_DEGREE, 10);
        params.set(Params.SAMPLE_SIZE, 6000);
        params.set(Params.NUM_RUNS, 1);
        params.set(Params.COEF_LOW, 0);
        params.set(Params.COEF_HIGH, 1);
        params.set(Params.NUM_STARTS, 1);

        params.set(Params.PENALTY_DISCOUNT, 2);
        params.set(Params.ZS_RISK_BOUND, 0.001); //, 0.01, 0.1);
        params.set(Params.EBIC_GAMMA, 0.8);
        params.set(Params.ALPHA, 0.001);

        params.set(Params.GRASP_DEPTH, 3);
        params.set(Params.GRASP_SINGULAR_DEPTH, 2);
        params.set(Params.NUM_ROUNDS, 50);

        params.set(Params.GRASP_CHECK_COVERING, false);
        params.set(Params.GRASP_FORWARD_TUCK_ONLY, false);
        params.set(Params.GRASP_BREAK_AFTER_IMPROVEMENT, true);
        params.set(Params.GRASP_ORDERED_ALG, false);
        params.set(Params.GRASP_USE_SCORE, true);
        params.set(Params.GRASP_USE_RASKUTTI_UHLER, false);
        params.set(Params.GRASP_USE_VP_SCORING, false);
        params.set(Params.USE_DATA_ORDER, false);

        params.set(Params.GRASP_ALG, false);


        Algorithms algorithms = new Algorithms();
        algorithms.add(new Grasp(new FisherZ(), new edu.cmu.tetrad.algcomparison.score.EbicScore()));

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
        statistics.add(new ElapsedCpuTime());

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
        params.set(Params.GRASP_SINGULAR_DEPTH, 2);
        params.set(Params.GRASP_FORWARD_TUCK_ONLY, false);
        params.set(Params.GRASP_USE_RASKUTTI_UHLER, true, false);
        params.set(Params.TIMEOUT, -1);
        params.set(Params.VERBOSE, true);


        Algorithms algorithms = new Algorithms();
        algorithms.add(new Grasp(new FisherZ(), new edu.cmu.tetrad.algcomparison.score.SemBicScore()));

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.ALPHA));
        statistics.add(new ParameterColumn(Params.PENALTY_DISCOUNT));
        statistics.add(new ParameterColumn(Params.GRASP_USE_RASKUTTI_UHLER));
        statistics.add(new NumberOfEdgesTrue());
        statistics.add(new NumberOfEdgesEst());
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedCpuTime());

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
        params.set(Params.GRASP_SINGULAR_DEPTH, 2);
//        params.set(Params.GRASP_FORWARD_TUCK_ONLY, false);
//        params.set(Params.GRASP_USE_PEARL, false);
        params.set(Params.GRASP_ALG, true, false);
        params.set(Params.TIMEOUT, -1);
        params.set(Params.VERBOSE, true);


        Algorithms algorithms = new Algorithms();
        algorithms.add(new Grasp(new FisherZ(), new edu.cmu.tetrad.algcomparison.score.SemBicScore()));

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
        statistics.add(new ElapsedCpuTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/testCompareGrasp1Grasp2",
                simulations, algorithms, statistics, params);
    }

    @Test
    public void name() {
    }

    //    @Test
    public void testGrasp2() {
        Parameters params = new Parameters();
        params.set(Params.NUM_MEASURES, 1000);
        params.set(Params.AVG_DEGREE, 8);
        params.set(Params.SAMPLE_SIZE, 6000);
        params.set(Params.NUM_RUNS, 1);
        params.set(Params.COEF_LOW, 0);
        params.set(Params.COEF_HIGH, 1.0);
        params.set(Params.NUM_STARTS, 1);
        params.set(Params.ALPHA, 0.001);
        params.set(Params.VERBOSE, true);
        params.set(Params.PARALLELIZED, true);
        params.set(Params.DEPTH, 6);
        params.set(Params.GRASP_TOLERANCE_DEPTH, 2);
        params.set(Params.GRASP_SINGULAR_DEPTH, 1);
        params.set(Params.GRASP_NONSINGULAR_DEPTH, 1);

        params.set(Params.PENALTY_DISCOUNT, 2);
        params.set(Params.FAITHFULNESS_ASSUMED, true);
        params.set(Params.ZS_RISK_BOUND, 1e-10);
        params.set(Params.SEM_GIC_RULE, 6);

        // use defaults.
//        params.set(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE, 10);

        Algorithms algorithms = new Algorithms();
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.PC(
//                new edu.cmu.tetrad.algcomparison.independence.FisherZ()));
        algorithms.add(new Fges(
                new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
//        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.SemBicScore(), new FisherZ()));
//        algorithms.add(new BRIDGES(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
//        algorithms.add(new BOSS_OLD(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
//        algorithms.add(new BOSS(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
//        algorithms.add(new BOSS2(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
//        algorithms.add(new SIMPLE_DEMO_GA(new edu.cmu.tetrad.algcomparison.score.SemBicScore(), new FisherZ()));

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.PENALTY_DISCOUNT));
        statistics.add(new ParameterColumn(Params.NUM_MEASURES));
        statistics.add(new ParameterColumn(Params.AVG_DEGREE));
        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new ParameterColumn(Params.COEF_HIGH));
//        statistics.add(new NumberOfEdgesTrue());
//        statistics.add(new NumberOfEdgesEst());
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new BicEst());
        statistics.add(new ElapsedCpuTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.CPDAG_of_the_true_DAG);
        comparison.setSaveData(false);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/testGrasp2",
                simulations, algorithms, statistics, params);
    }

    //    @Test
    public void testLuFigure3() {
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

        params.set(Params.GRASP_DEPTH, 3);
        params.set(Params.GRASP_SINGULAR_DEPTH, 0, 1);
        params.set(Params.GRASP_NONSINGULAR_DEPTH, 0, 1);
        params.set(Params.GRASP_ORDERED_ALG, false);
        params.set(Params.GRASP_USE_RASKUTTI_UHLER, false);
        params.set(Params.USE_DATA_ORDER, false);
        params.set(Params.CACHE_SCORES, true);

        Algorithms algorithms = new Algorithms();
        algorithms.add(new Grasp(new FisherZ(), new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
        algorithms.add(new Pc(new FisherZ()));
        algorithms.add(new Fges(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new ParameterColumn(Params.AVG_DEGREE));
        statistics.add(new ParameterColumn(Params.GRASP_SINGULAR_DEPTH));
        statistics.add(new ParameterColumn(Params.GRASP_NONSINGULAR_DEPTH));
        statistics.add(new CorrectSkeleton());
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new StructuralHammingDistance());
        statistics.add(new F1Adj());
        statistics.add(new F1Arrow());
        statistics.add(new ElapsedCpuTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.CPDAG_of_the_true_DAG);
        comparison.setSaveData(false);

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

        params.set(Params.GRASP_DEPTH, 3);
        params.set(Params.GRASP_SINGULAR_DEPTH, 0, 1);
        params.set(Params.GRASP_NONSINGULAR_DEPTH, 0, 1);
        params.set(Params.GRASP_ORDERED_ALG, true);
        params.set(Params.GRASP_USE_RASKUTTI_UHLER, false);

        Algorithms algorithms = new Algorithms();
        algorithms.add(new Grasp(new FisherZ(), new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
        algorithms.add(new Pc(new FisherZ()));
        algorithms.add(new Fges(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new ParameterColumn(Params.AVG_DEGREE));
        statistics.add(new ParameterColumn(Params.GRASP_SINGULAR_DEPTH));
        statistics.add(new ParameterColumn(Params.GRASP_NONSINGULAR_DEPTH));
        statistics.add(new CorrectSkeleton());
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new StructuralHammingDistance());
        statistics.add(new F1Adj());
        statistics.add(new F1Arrow());
        statistics.add(new ElapsedCpuTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.CPDAG_of_the_true_DAG);
        comparison.setSaveData(false);

        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/Lu.figure.6", simulations,
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
        params.set(Params.GRASP_SINGULAR_DEPTH, 1);
        params.set(Params.GRASP_NONSINGULAR_DEPTH, 1);
        params.set(Params.GRASP_ORDERED_ALG, false);
        params.set(Params.GRASP_USE_RASKUTTI_UHLER, false);
        params.set(Params.USE_DATA_ORDER, false);
        params.set(Params.CACHE_SCORES, true);


        Algorithms algorithms = new Algorithms();
        algorithms.add(new Grasp(new FisherZ(), new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
        algorithms.add(new Pc(new FisherZ()));
        algorithms.add(new Fges(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));

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
        statistics.add(new ElapsedCpuTime());

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

            Graph g = RandomGraph.randomGraph(20, 0, 40,
                    100, 100, 100, false);
            SemPm pm = new SemPm(g);
            SemIm im = new SemIm(pm);
            DataSet d = im.simulateData(1000, false);

            IndependenceTest test = new IndTestFisherZ(d, 0.00001);

            List<Node> pi = new ArrayList<>(test.getVariables());

            edu.cmu.tetrad.search.Grasp grasp = new edu.cmu.tetrad.search.Grasp(test);

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
    public void bryanCheckDensityClaims() {
        long start = MillisecondTimes.timeMillis();
        boolean usePearl = true;
        int numVars = 5; // Will change this in OtherParams.sp() too

        try {
//            String path = "/Users/josephramsey/Downloads/grasp/out_53_0index/out_53.txt";
//            String path = "/Users/josephramsey/Downloads/grasp/out_80_0index/out_80.txt";
//            String path = "/Users/josephramsey/Downloads/studeny_out.txt";
//            String path = "/Users/josephramsey/Downloads/udags4.txt";
            String path = "/Users/josephramsey/Downloads/udags5/udags5.txt";
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
                axioms.ensureTriviality();
                axioms.ensureSymmetry();

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
                    MsepTest test = new MsepTest(facts, variables);

                    edu.cmu.tetrad.search.Grasp boss = new edu.cmu.tetrad.search.Grasp(test, new GraphScore(facts));
                    boss.setNonSingularDepth(1);
                    boss.setUncoveredDepth(1);

                    List<Node> spPi = boss.bestOrder(variables);
                    Graph spGraph = boss.getGraph(true);
                    int spNumEdges = spGraph.getNumEdges();

                    List<Node> failingInitialPi = null;
                    Graph failingDag = null;
                    List<Node> failingEstPi = null;

                    PermutationGenerator gen = new PermutationGenerator(variables.size());
                    int[] perm;

                    List<List<Node>> pis = new ArrayList<>();
                    Map<List<Node>, Integer> ests = new HashMap<>();

                    edu.cmu.tetrad.search.Grasp grasp = new edu.cmu.tetrad.search.Grasp(test);

                    grasp.setDepth(3);
                    grasp.setUncoveredDepth(1);
                    grasp.setNonSingularDepth(1);
                    grasp.setUseRaskuttiUhler(usePearl);
                    grasp.setOrdered(true);
                    grasp.setVerbose(false);

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

                System.out.println("\tExists nonfrugal = " + existsNonfrugal + " all = " + all + " (" + (MillisecondTimes.timeMillis() - start) / 1000.0 + " s)");
            }

            System.out.println("\nTOTAL: exists nonfrugal = " + existsNonfrugal + " all = " + all + " (" + (MillisecondTimes.timeMillis() - start) / 1000.0 + " s)");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void testForWayne() {
        List<Node> nodes = new ArrayList<>();
        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        Node x3 = new ContinuousVariable("X3");
        Node x4 = new ContinuousVariable("X4");
        Node x5 = new ContinuousVariable("X5");

        nodes.add(x1);
        nodes.add(x2);
        nodes.add(x3);
        nodes.add(x4);
        nodes.add(x5);

        Graph graph = new EdgeListGraph(nodes);

        graph.addDirectedEdge(x1, x5);
        graph.addDirectedEdge(x2, x5);
        graph.addDirectedEdge(x3, x5);
        graph.addDirectedEdge(x4, x5);
        graph.addDirectedEdge(x1, x4);

//        System.out.println(graph);

//        IndTestMSep msep = new IndTestMSep(graph);
//        GraphScore score = new GraphScore(graph);
        Set<Node> order = set(x1, x2, x3, x4, x5);

        Grasp boss = new Grasp(new MSeparationTest(graph), new MSeparationScore(graph));

        Parameters parameters = new Parameters();
        parameters.set(Params.GRASP_USE_RASKUTTI_UHLER, true);

//        Boss boss = new Boss(msep, score);
//        boss.setUseRaskuttiUhler(true);
//        boss.setUseScore(false);
//        boss.setDepth(3);
//        boss.setNumStarts(1);
        Graph best = boss.search(null, parameters);
        System.out.println("best = " + best);

//        TeyssierScorer scorer = new TeyssierScorer(msep, score);
////        scorer.setUseScore(false);
//        scorer.setUseRaskuttiUhler(true);
//
//        scorer.score(order);
//        System.out.println(scorer.score());
//        System.out.println(scorer.getGraph(false));

    }

    private void testForWayne2() {
        List<Node> nodes = new ArrayList<>();
        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        Node x3 = new ContinuousVariable("X3");
        Node x4 = new ContinuousVariable("X4");

        nodes.add(x1);
        nodes.add(x2);
        nodes.add(x3);
        nodes.add(x4);

        Graph graph = new EdgeListGraph(nodes);

        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x4);
        graph.addDirectedEdge(x1, x4);

        System.out.println(graph);

        BayesPm pm = new BayesPm(graph);

        pm.setNumCategories(x1, 2);
        pm.setNumCategories(x2, 2);
        pm.setNumCategories(x3, 2);
        pm.setNumCategories(x4, 3);

        BayesIm im = new MlBayesIm(pm);

        im.setProbability(0, 0, 0, 0.5);
        im.setProbability(0, 0, 1, 0.5);

        im.setProbability(1, 0, 0, 0.6);
        im.setProbability(1, 0, 1, 0.4);
        im.setProbability(1, 1, 0, 0.1);
        im.setProbability(1, 1, 1, 0.9);

        im.setProbability(2, 0, 0, 0.7);
        im.setProbability(2, 0, 1, 0.3);
        im.setProbability(2, 1, 0, 0.2);
        im.setProbability(2, 1, 1, 0.8);

        //--

        im.setProbability(3, 0, 0, 0.1);
        im.setProbability(3, 0, 1, 0.1);
        im.setProbability(3, 0, 2, 0.8);

        im.setProbability(3, 1, 0, 0.05);
        im.setProbability(3, 1, 1, 0.05);
        im.setProbability(3, 1, 2, 0.9);

        im.setProbability(3, 2, 0, 0.1125);
        im.setProbability(3, 2, 1, 0.1125);
        im.setProbability(3, 2, 2, 0.775);

        im.setProbability(3, 3, 0, 0.0625);
        im.setProbability(3, 3, 1, 0.0625);
        im.setProbability(3, 3, 2, 0.875);


        System.out.println(im);

        DataSet data = im.simulateData(1000000, false);

        graph = GraphUtils.replaceNodes(graph, data.getVariables());

        x1 = graph.getNode("X1");
        x2 = graph.getNode("X2");
        x3 = graph.getNode("X3");
        x4 = graph.getNode("X4");

        System.out.println(data);

        IndependenceTest chiSq = new IndTestChiSquare(data, 0.01);

//        chiSq.checkIndependence(x1, x2);

        extractedWayne(x1, x2, x3, x4, chiSq);
        extractedWayne(x1, x3, x2, x4, chiSq);
        extractedWayne(x1, x4, x2, x3, chiSq);
        extractedWayne(x2, x3, x1, x4, chiSq);
        extractedWayne(x2, x4, x1, x3, chiSq);
        extractedWayne(x3, x4, x1, x2, chiSq);


    }

    private void testForWayne3() {
        List<Node> nodes = new ArrayList<>();
        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        Node x3 = new ContinuousVariable("X3");
        Node x4 = new ContinuousVariable("X4");

        nodes.add(x1);
        nodes.add(x2);
        nodes.add(x3);
        nodes.add(x4);

        Graph graph = new EdgeListGraph(nodes);

        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x2, x4);
        graph.addDirectedEdge(x3, x4);

        System.out.println(graph);

        BayesPm pm = new BayesPm(graph);

        pm.setNumCategories(x1, 2);
        pm.setNumCategories(x2, 2);
        pm.setNumCategories(x3, 2);
        pm.setNumCategories(x4, 2);

        BayesIm im = new MlBayesIm(pm);

        im.setProbability(0, 0, 0, 0.5);
        im.setProbability(0, 0, 1, 0.5);

        im.setProbability(1, 0, 0, 0.3);
        im.setProbability(1, 0, 1, 0.7);
        im.setProbability(1, 1, 0, 0.4);
        im.setProbability(1, 1, 1, 0.6);

        im.setProbability(2, 0, 0, 0.4);
        im.setProbability(2, 0, 1, 0.6);
        im.setProbability(2, 1, 0, 0.3);
        im.setProbability(2, 1, 1, 0.7);

        //--

        im.setProbability(3, 0, 0, 0.1);
        im.setProbability(3, 0, 1, 0.9);

        im.setProbability(3, 1, 0, 0.7);
        im.setProbability(3, 1, 1, 0.3);

        im.setProbability(3, 2, 0, 0.7);
        im.setProbability(3, 2, 1, 0.3);

        im.setProbability(3, 3, 0, 0.8);
        im.setProbability(3, 3, 1, 0.2);


        System.out.println(im);

        DataSet data = im.simulateData(1000000, false);

        graph = GraphUtils.replaceNodes(graph, data.getVariables());

        x1 = graph.getNode("X1");
        x2 = graph.getNode("X2");
        x3 = graph.getNode("X3");
        x4 = graph.getNode("X4");

//        System.out.println(data);

        IndependenceTest chiSq = new IndTestChiSquare(data, 0.01);

//        chiSq.checkIndependence(x1, x2);

        extractedWayne(x1, x2, x3, x4, chiSq);
        extractedWayne(x1, x3, x2, x4, chiSq);
        extractedWayne(x1, x4, x2, x3, chiSq);
        extractedWayne(x2, x3, x1, x4, chiSq);
        extractedWayne(x2, x4, x1, x3, chiSq);
        extractedWayne(x3, x4, x1, x2, chiSq);


    }

    private Set<Node> set(ContinuousVariable... s) {
        Set<Node> l = new HashSet<>();

        for (Node n : s) {
            l.add(n);
        }

        return l;
    }

    private void printExistsNonfrugalCase(String line, int index, IndependenceFacts facts, List<Node> spPi, Graph spGraph, List<Node> failingInitialPi, Graph failingDag, List<Node> failingEstPi) {
        System.out.println("Failed, line " + index + " " + line);
        System.out.println("Elementary facts = " + facts);
        System.out.println("Failing initial permutation: " + failingInitialPi);
        System.out.println("Failing GRASP final permutation: " + failingEstPi);
        System.out.println("SP permutation = " + spPi);
        System.out.println("SP DAG = " + spGraph);
        System.out.println("Failing Estimated DAG = " + failingDag);

        MsepTest msep = new MsepTest(failingDag);

        for (IndependenceFact fact : facts.getFacts()) {
            if (msep.isMSeparated(fact.getX(), fact.getY(), fact.getZ())) {
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
//    public void testLuFigure6gure3() {
//        Parameters params = new Parameters();
//        params.set(Params.SAMPLE_SIZE, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000);//, 200000);
//        params.set(Params.NUM_MEASURES, 20);
//        params.set(Params.AVG_DEGREE, 4);
//        params.set(Params.COEF_LOW, 0.2);
//        params.set(Params.COEF_HIGH, 0.8);
//        params.set(Params.COV_LOW, 0);
//        params.set(Params.COV_HIGH, 0);
//        params.set(Params.VAR_LOW, 1);
//        params.set(Params.VAR_HIGH, 1);
//        params.set(Params.VERBOSE, true);
//        params.set(Params.NUM_RUNS, 30);
//        params.set(Params.NUM_STARTS, 1);
//
//        params.set(Params.GRASP_DEPTH, 3);
//        params.set(Params.GRASP_UNCOVERED_DEPTH, 0, 1);
//        params.set(Params.GRASP_NONSINGULAR_DEPTH, 0, 1);
//        params.set(Params.GRASP_ORDERED_ALG, true);
//        params.set(Params.GRASP_USE_VERMA_PEARL, false);
//
//        params.set(Params.PENALTY_DISCOUNT, 2.0);
//        params.set(Params.ALPHA, 0.001);
////
//        Algorithms algorithms = new Algorithms();
//        algorithms.add(new GRaSP(new edu.cmu.tetrad.algcomparison.score.SemBicScore(), new FisherZ()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.PC(new FisherZ()));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
//
//        Simulations simulations = new Simulations();
//        simulations.add(new SemSimulation(new RandomForward()));
//
//        Statistics statistics = new Statistics();
//        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
//        statistics.add(new AdjacencyPrecision());
//        statistics.add(new AdjacencyRecall());
//        statistics.add(new ArrowheadPrecision());
//        statistics.add(new ArrowheadRecall());
//        statistics.add(new F1Adj());
//        statistics.add(new ElapsedTime());
//
//        Comparison comparison = new Comparison();
//        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);
//
//        comparison.compareFromSimulations("/Users/josephramsey/Downloads/grasp/Lu.figure.3", simulations,
//                algorithms, statistics, params);
//    }

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
        params.set(Params.GRASP_USE_RASKUTTI_UHLER, false);
        params.set(Params.USE_DATA_ORDER, false);

        Algorithms algorithms = new Algorithms();
        algorithms.add(new Grasp(new FisherZ(), new edu.cmu.tetrad.algcomparison.score.
                EbicScore()));
        algorithms.add(new Cpc(new FisherZ()));
        algorithms.add(new Fges(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));

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
        statistics.add(new StructuralHammingDistance());
        statistics.add(new ElapsedCpuTime());

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
        params.set(Params.GRASP_USE_RASKUTTI_UHLER, false);
        params.set(Params.USE_DATA_ORDER, false);

        Statistics statistics = new Statistics();
        statistics.add(new ParameterColumn(Params.NUM_MEASURES));
        statistics.add(new ParameterColumn(Params.AVG_DEGREE));
        statistics.add(new NumberOfEdgesTrue());
        statistics.add(new NumberOfEdgesEst());
        statistics.add(new StructuralHammingDistance());
        statistics.add(new ElapsedCpuTime());

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));
//        simulations.add(new SemSimulationTrueModel(new RandomForward()));

        Algorithms algorithms = new Algorithms();
        algorithms.add(new Grasp(new MSeparationTest(), new edu.cmu.tetrad.algcomparison.score.SemBicScore()));

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

        facts.add(new IndependenceFact(x1, x3, set(x2)));
        facts.add(new IndependenceFact(x2, x4, set(x1, x3)));

        return new Ret("Simple 4-node 2-path model", facts, 4);
    }

    public Ret getFactsSimpleCanceling() {
        Node x1 = new GraphNode("1");
        Node x2 = new GraphNode("2");
        Node x3 = new GraphNode("3");
        Node x4 = new GraphNode("4");

        IndependenceFacts facts = new IndependenceFacts();

        facts.add(new IndependenceFact(x1, x3, set(x2)));
        facts.add(new IndependenceFact(x2, x4, set(x1, x3)));
        facts.add(new IndependenceFact(x1, x4, set())); // unfaithful.

        return new Ret("Simple 4-node path canceling model", facts, 4);
    }

    public Ret wayneTriangleFaithfulnessFailExample() {
        Node x1 = new GraphNode("1");
        Node x2 = new GraphNode("2");
        Node x3 = new GraphNode("3");
        Node x4 = new GraphNode("4");

        IndependenceFacts facts = new IndependenceFacts();

        facts.add(new IndependenceFact(x1, x2, set()));
        facts.add(new IndependenceFact(x1, x2, set(x3)));
        facts.add(new IndependenceFact(x1, x3, set()));
        facts.add(new IndependenceFact(x1, x3, set(x2)));
        facts.add(new IndependenceFact(x2, x3, set(x4)));

        return new Ret("Wayne triangle faithfulness fail example", facts, 4);
    }

    public Ret getFigure8() {
        Node x1 = new GraphNode("1");
        Node x2 = new GraphNode("2");
        Node x3 = new GraphNode("3");
        Node x4 = new GraphNode("4");
        Node x5 = new GraphNode("5");

        IndependenceFacts facts = new IndependenceFacts();

        facts.add(new IndependenceFact(x1, x3, set(x2)));
        facts.add(new IndependenceFact(x2, x4, set(x1, x3)));
        facts.add(new IndependenceFact(x4, x5, set()));

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
        facts.add(new IndependenceFact(x1, x5, set(x2, x3, x4)));
        facts.add(new IndependenceFact(x4, x6, set(x1, x2, x3, x5)));
        facts.add(new IndependenceFact(x1, x3, set(x2, x4, x5, x6)));

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

        facts.add(new IndependenceFact(x1, x2, set(x4)));
        facts.add(new IndependenceFact(x1, x3, set(x2)));
        facts.add(new IndependenceFact(x2, x4, set(x1, x3)));

        return new Ret("Solus Theorem 12, ESP !==> TSP (Figure 7)", facts, 4);
    }

    public Ret getFigure6() {
        Node x1 = new GraphNode("1");
        Node x2 = new GraphNode("2");
        Node x3 = new GraphNode("3");
        Node x4 = new GraphNode("4");
        Node x5 = new GraphNode("5");

        IndependenceFacts facts = new IndependenceFacts();

        facts.add(new IndependenceFact(x1, x5, set(x2, x3)));
        facts.add(new IndependenceFact(x2, x4, set(x1, x3)));
        facts.add(new IndependenceFact(x3, x5, set(x1, x2, x4)));
        facts.add(new IndependenceFact(x1, x4, set(x2, x3, x5)));
        facts.add(new IndependenceFact(x1, x4, set(x2, x3)));

        return new Ret("Solus Theorem 11, TSP !==> Faithfulness (Figure 6)", facts, 7);
    }

    public Ret getFactsRaskutti() {
        Node x1 = new GraphNode("1");
        Node x2 = new GraphNode("2");
        Node x3 = new GraphNode("3");
        Node x4 = new GraphNode("4");

        IndependenceFacts facts = new IndependenceFacts();

        facts.add(new IndependenceFact(x1, x3, set(x2)));
        facts.add(new IndependenceFact(x2, x4, set(x1, x3)));
        facts.add(new IndependenceFact(x1, x2, set(x4)));

        return new Ret("Raskutti Theorem 2.4 SMR !==> Adjacency Faithfulness", facts, 4);
    }

    public Ret getWayneExample1() {
        Node x0 = new GraphNode("0");
        Node x1 = new GraphNode("1");
        Node x2 = new GraphNode("2");
        Node x3 = new GraphNode("3");
        Node x4 = new GraphNode("4");

        IndependenceFacts facts = new IndependenceFacts();

        facts.add(new IndependenceFact(x0, x2, set(x1)));
        facts.add(new IndependenceFact(x0, x2, set(x1, x3)));
        facts.add(new IndependenceFact(x0, x2, set(x1, x3, x4)));

        facts.add(new IndependenceFact(x0, x4, set(x1, x3)));
        facts.add(new IndependenceFact(x0, x4, set(x2, x3)));
        facts.add(new IndependenceFact(x0, x4, set(x1, x2, x3)));

        facts.add(new IndependenceFact(x1, x3, set(x0)));
        facts.add(new IndependenceFact(x1, x3, set(x0, x2)));
        facts.add(new IndependenceFact(x1, x3, set(x0, x2, x4)));

        facts.add(new IndependenceFact(x1, x4, set(x0, x2)));
        facts.add(new IndependenceFact(x1, x4, set(x2, x3)));
        facts.add(new IndependenceFact(x1, x4, set(x0, x2, x3)));

        facts.add(new IndependenceFact(x2, x3, set(x0)));
        facts.add(new IndependenceFact(x2, x3, set(x1)));
        facts.add(new IndependenceFact(x2, x3, set(x0, x1)));

        facts.add(new IndependenceFact(x0, x4, set()));

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

        List<List<Node>> paths = graph.paths().allDirectedPaths(x1, x4, -1);

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

    private Set<Node> set(Node... nodes) {
        Set<Node> list = new HashSet<>();
        Collections.addAll(list, nodes);
        return list;
    }

    //    @Test
    public void testFciAlgs() {
        RandomUtil.getInstance().setSeed(38482838482L);

        Parameters params = new Parameters();
        params.set(Params.ALPHA, 1e-5, 0.0001, 0.001, 0.01, 0.1);
        params.set(Params.PENALTY_DISCOUNT, 1, 2, 4);
//        params.set(Params.ZS_RISK_BOUND, 0.1, 0.5, 0.9);
        params.set(Params.EBIC_GAMMA, .2, .6, .8);

        params.set(Params.SAMPLE_SIZE, 1000, 10000);
        params.set(Params.NUM_MEASURES, 30);
        params.set(Params.AVG_DEGREE, 6);
        params.set(Params.NUM_LATENTS, 8);
        params.set(Params.RANDOMIZE_COLUMNS, true);
        params.set(Params.COEF_LOW, 0);
        params.set(Params.COEF_HIGH, 1);
        params.set(Params.VAR_LOW, 1);
        params.set(Params.VAR_HIGH, 3);
        params.set(Params.VERBOSE, false);

        params.set(Params.NUM_RUNS, 20);

        params.set(Params.BOSS_ALG, 1);
        params.set(Params.DEPTH, -1);
        params.set(Params.MAX_PATH_LENGTH, 2);
        params.set(Params.COMPLETE_RULE_SET_USED, true);
        params.set(Params.POSSIBLE_MSEP_DONE, true);
        params.set(Params.DO_DISCRIMINATING_PATH_TAIL_RULE, true);

        // Flags
        params.set(Params.GRASP_USE_RASKUTTI_UHLER, false);
        params.set(Params.GRASP_USE_SCORE, true);
        params.set(Params.USE_DATA_ORDER, true);
        params.set(Params.NUM_STARTS, 1);

        // default for GIC scores is gic = 4, pd = 1.
        params.set(Params.SEM_GIC_RULE, 4);
//        params.set(Params.ALPHA, 0.01);
        params.set(Params.SEM_BIC_STRUCTURE_PRIOR, 3);

        params.set(Params.DIFFERENT_GRAPHS, true);

        params.set(Params.ADD_ORIGINAL_DATASET, false);

//        params.set(Params.SEED, -1l);

        IndependenceWrapper test = new FisherZ();

        List<ScoreWrapper> scores = new ArrayList<>();
        scores.add(new edu.cmu.tetrad.algcomparison.score.SemBicScore());
//        scores.add(new edu.cmu.tetrad.algcomparison.score.EbicScore());
//        scores.add(new edu.cmu.tetrad.algcomparison.score.KimEtAlScores());
//        scores.add(new edu.cmu.tetrad.algcomparison.score.ZhangShenBoundScore());

        List<Algorithm> algorithms = new ArrayList<>();

        algorithms.add(new Fci(test));
//        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.FciMax(test));
        algorithms.add(new Rfci(test));
//
        for (ScoreWrapper score : scores) {
            algorithms.add(new Gfci(test, score));
        }
//
        Algorithms _algorithms = new Algorithms();

        for (Algorithm algorithm : algorithms) {
            _algorithms.add(algorithm);
        }

        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        Statistics statistics = new Statistics();

        statistics.add(new LegalPag());
//        statistics.add(new NoAlmostCyclicPathsCondition());
//        statistics.add(new NoCyclicPathsCondition());
        statistics.add(new NoAlmostCyclicPathsCondition());
        statistics.add(new NoCyclicPathsCondition());
        statistics.add(new MaximalityCondition());

        statistics.add(new ParameterColumn(Params.ALPHA));
        statistics.add(new ParameterColumn(Params.PENALTY_DISCOUNT));
        statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
        statistics.add(new ParameterColumn(Params.DEPTH));
        statistics.add(new ParameterColumn(Params.ZS_RISK_BOUND));
//        statistics.add(new ParameterColumn(Params.EBIC_GAMMA));

//        // Joe table.
//        statistics.add(new LegalPag());
//        statistics.add(new NumDirectedEdges());
//        statistics.add(new TrueDagPrecisionTails());
//        statistics.add(new TrueDagPrecisionArrow());
//        statistics.add(new NumDirectedShouldBePartiallyDirected());
        statistics.add(new NumDirectedEdges());
        statistics.add(new NumBidirectedEdgesEst());
        statistics.add(new TrueDagPrecisionTails());
        statistics.add(new BidirectedLatentPrecision());
//
//        // Greg table
//        statistics.add(new AncestorPrecision());
//        statistics.add(new AncestorRecall());
//        statistics.add(new AncestorF1());
//        statistics.add(new SemidirectedPrecision());
//        statistics.add(new SemidirectedRecall());
//        statistics.add(new SemidirectedPathF1());
//        statistics.add(new NoSemidirectedPrecision());
//        statistics.add(new NoSemidirectedRecall());
//        statistics.add(new NoSemidirectedF1());


//        statistics.add(new ElapsedTime());
        statistics.add(new ElapsedCpuTime());

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

        comparison.compareFromSimulations(
                "/Users/josephramsey/Downloads/grasp/testFciAlgs", simulations,
                _algorithms, statistics, params);

    }

    // Test algs from msep
    public void testFcoAlgsFromMsep() {
        RandomUtil.getInstance().setSeed(38482838482L);

        Parameters params = new Parameters();
//        params.set(Params.SAMPLE_SIZE, 10000);
//        params.set(Params.NUM_MEASURES, 20);
//        params.set(Params.AVG_DEGREE, 4;
//        params.set(Params.NUM_LATENTS, 8);
        params.set(Params.RANDOMIZE_COLUMNS, true);

        params.set(Params.COEF_LOW, 0);
        params.set(Params.COEF_HIGH, 1);
        params.set(Params.VAR_LOW, 1);
        params.set(Params.VAR_HIGH, 3);
        params.set(Params.VERBOSE, false);

        params.set(Params.NUM_RUNS, 20);

        params.set(Params.BOSS_ALG, 1);
        params.set(Params.DEPTH, -1);
        params.set(Params.MAX_PATH_LENGTH, 2);
        params.set(Params.COMPLETE_RULE_SET_USED, true);
        params.set(Params.POSSIBLE_MSEP_DONE, true);
        params.set(Params.DO_DISCRIMINATING_PATH_TAIL_RULE, true);

        // Flags
        params.set(Params.GRASP_USE_RASKUTTI_UHLER, false);
        params.set(Params.GRASP_USE_SCORE, true);
        params.set(Params.USE_DATA_ORDER, true);
        params.set(Params.NUM_STARTS, 1);

        // default for GIC scores is gic = 4, pd = 1.
        params.set(Params.SEM_GIC_RULE, 4);
        params.set(Params.PENALTY_DISCOUNT, 1);
        params.set(Params.ALPHA, 0.01);
        params.set(Params.ZS_RISK_BOUND, 0.2);
        params.set(Params.SEM_BIC_STRUCTURE_PRIOR, 2);

        params.set(Params.DIFFERENT_GRAPHS, true);

        params.set(Params.ADD_ORIGINAL_DATASET, false);

        Statistics dagStats = new Statistics();

        // Joe table.
        dagStats.add(new NumDirectedEdges());
        dagStats.add(new TrueDagPrecisionArrow());
        dagStats.add(new TrueDagPrecisionTails());
        dagStats.add(new NumBidirectedEdgesEst());
        dagStats.add(new BidirectedLatentPrecision());
//                dagStats.add(new SemidirectedRecall());

        // Greg table
        dagStats.add(new AncestorPrecision());
        dagStats.add(new AncestorRecall());
        dagStats.add(new AncestorF1());
        dagStats.add(new SemidirectedPrecision());
        dagStats.add(new SemidirectedRecall());
        dagStats.add(new SemidirectedPathF1());
        dagStats.add(new NonancestorPrecision());
        dagStats.add(new NonancestorRecall());
        dagStats.add(new NoSemidirectedF1());

        Statistics pagStats = new Statistics();

        pagStats.add(new FalsePositiveAdjacencies());
        pagStats.add(new FalseNegativesAdjacencies());


//                dagStats.add(new NoSemidirectedPrecision());
//                dagStats.add(new NoSemidirectedRecall());

//                dagStats.add(new NumDirectedEdges());
//                dagStats.add(new NumBidirectedEdgesEst());
//                dagStats.add(new TrueDagPrecisionArrow());
//                dagStats.add(new TrueDagPrecisionTails());
//                dagStats.add(new BidirectedLatentPrecision());

        Map<Integer, Map<String, Map<Statistic, Double>>> trueGraphMap = new HashMap<>();
        List<Graph> trueGraphs = new ArrayList<>();

        List<String> algNames = new ArrayList<>();

        for (int i = 0; i < 40; i++) {

            Graph trueGraph = RandomGraph.randomGraph(20, 8, 40,
                    100, 100, 100, false);

            Graph truePag = GraphTransforms.dagToPag(trueGraph);

            trueGraphMap.put(i, new HashMap<>());
            trueGraphs.add(trueGraph);

            Map<String, Map<Statistic, Double>> algNameMap = trueGraphMap.get(i);

            IndependenceWrapper test = new MSeparationTest(new EdgeListGraph(trueGraph));
            ScoreWrapper score = new MSeparationScore(new EdgeListGraph(trueGraph));

            Algorithms algorithms = new Algorithms();

            algorithms.add(new Fci(test));
//            algorithms.add(new FciMax(test));
//            algorithms.add(new Rfci(test));
//            algorithms.add(new GFCI(test, score));
//            algorithms.add(new BFCI(test, score));

            algNames = new ArrayList<>();

            for (Algorithm algorithm : algorithms.getAlgorithms()) {
                algNames.add(algorithm.getClass().getSimpleName());
            }

            int j = -1;

            for (Algorithm algorithm : algorithms.getAlgorithms()) {
                String algName = algNames.get(++j);
                algNameMap.put(algName, new HashMap<>());
                Graph estGraph = algorithm.search(null, params);

                for (Statistic statistic : dagStats.getStatistics()) {
                    double stat = statistic.getValue(trueGraph, estGraph, null);
                    algNameMap.get(algName).put(statistic, stat);
                }

                for (Statistic statistic : pagStats.getStatistics()) {
                    double stat = statistic.getValue(truePag, estGraph, null);
                    algNameMap.get(algName).put(statistic, stat);
                }
            }
        }

        TextTable table = new TextTable(algNames.size() + 1, dagStats.size() + pagStats.size() + 1);

        for (int i = 0; i < algNames.size(); i++) {
            table.setToken(i + 1, 0, algNames.get(i));
        }

        for (int j = 0; j < dagStats.size(); j++) {
            table.setToken(0, j + 1, dagStats.getStatistics().get(j).getAbbreviation());
        }

        for (int j = 0; j < pagStats.size(); j++) {
            table.setToken(0, dagStats.size() + j + 1, pagStats.getStatistics().get(j).getAbbreviation());
        }

        NumberFormat nf = new DecimalFormat("0.00");

        for (int i = 0; i < algNames.size(); i++) {
            for (int j = 0; j < dagStats.size(); j++) {
                Statistic statistic = dagStats.getStatistics().get(j);

                double sum = 0.0;
                int count = 0;

                for (int k = 0; k < trueGraphs.size(); k++) {
                    Map<String, Map<Statistic, Double>> algNameMap = trueGraphMap.get(k);
                    Map<Statistic, Double> statisticMap = algNameMap.get(algNames.get(i));

                    double value = statisticMap.get(statistic);

                    if (!Double.isNaN(value)) {
                        sum += value;
                        count++;
                    }
                }

                sum /= (double) count;

                table.setToken(i + 1, j + 1, nf.format(sum));
            }

            for (int j = 0; j < pagStats.size(); j++) {
                Statistic statistic = pagStats.getStatistics().get(j);

                double sum = 0.0;
                int count = 0;

                for (int k = 0; k < trueGraphs.size(); k++) {
                    Map<String, Map<Statistic, Double>> algNameMap = trueGraphMap.get(k);
                    Map<Statistic, Double> statisticMap = algNameMap.get(algNames.get(i));

                    double value = statisticMap.get(statistic);

                    if (!Double.isNaN(value)) {
                        sum += value;
                        count++;
                    }
                }

                sum /= (double) count;

                table.setToken(i + 1, dagStats.size() + j + 1, nf.format(sum));
            }
        }

        System.out.println(table);
    }

    public void testDsep() {
        Graph graph = RandomGraph.randomGraph(20, 0, 40, 100, 100, 100, false);

        for (Node x : graph.getNodes()) {
            Set<Node> parents = new HashSet<>(graph.getParents(x));

            for (Node y : graph.getNodes()) {
                if (!graph.paths().isDescendentOf(y, x) && !parents.contains(y)) {
                    if (!graph.paths().isMSeparatedFrom(x, y, parents, false)) {
                        System.out.println("Failure! " + LogUtilsSearch.dependenceFactMsg(x, y, parents, 1.0));
                    }
                }
            }
        }
    }

    public void testScores() {
        for (int grouping : new int[]{7}) {//, 2, 3, 4, 5, 67}) {
            RandomUtil.getInstance().setSeed(38482838482L);

            Parameters params = new Parameters();
            params.set(Params.SAMPLE_SIZE, 10000);
            params.set(Params.NUM_MEASURES, 20);
            params.set(Params.NUM_LATENTS, 5);
            params.set(Params.AVG_DEGREE, 7);
            params.set(Params.RANDOMIZE_COLUMNS, true);
            params.set(Params.COEF_LOW, 0);
            params.set(Params.COEF_HIGH, 1);
            params.set(Params.VAR_LOW, 1);
            params.set(Params.VAR_HIGH, 3);
            params.set(Params.VERBOSE, false);

            params.set(Params.NUM_RUNS, 60);

            params.set(Params.BOSS_ALG, 1, 2, 3);
            params.set(Params.DEPTH, 3);
            params.set(Params.SEM_BIC_STRUCTURE_PRIOR, 4);
            params.set(Params.DO_DISCRIMINATING_PATH_COLLIDER_RULE, false);
            params.set(Params.DO_DISCRIMINATING_PATH_TAIL_RULE, false);

            // default for GIC scores is gic = 4, pd = 1.
            params.set(Params.SEM_GIC_RULE, 2, 3, 4);
            params.set(Params.PENALTY_DISCOUNT, 1, 2, 3);
            params.set(Params.ALPHA, 0.01);
            params.set(Params.ZS_RISK_BOUND, 0.00001);

            params.set(Params.DIFFERENT_GRAPHS, true);

            Algorithms algorithms = new Algorithms();

            algorithms.add(new Boss(new edu.cmu.tetrad.algcomparison.score.SemBicScore()));
            algorithms.add(new Boss(new edu.cmu.tetrad.algcomparison.score.PoissonPriorScore()));
            algorithms.add(new Boss(new edu.cmu.tetrad.algcomparison.score.EbicScore()));
            algorithms.add(new Boss(new GicScores()));
            algorithms.add(new Boss(new edu.cmu.tetrad.algcomparison.score.ZhangShenBoundScore()));

            Simulations simulations = new Simulations();
            simulations.add(new SemSimulation(new RandomForward()));

            Statistics statistics = new Statistics();

            statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
            statistics.add(new ParameterColumn(Params.ALPHA));
            statistics.add(new ParameterColumn(Params.PENALTY_DISCOUNT));
            statistics.add(new ParameterColumn(Params.BOSS_ALG));
            statistics.add(new AdjacencyPrecision());
            statistics.add(new AdjacencyRecall());
            statistics.add(new ArrowheadPrecision());
            statistics.add(new ArrowheadRecall());
            statistics.add(new ArrowheadPrecisionCommonEdges());
            statistics.add(new ArrowheadRecallCommonEdges());
            statistics.add(new ElapsedCpuTime());

            Comparison comparison = new Comparison();
            comparison.setShowAlgorithmIndices(true);
            comparison.setComparisonGraph(Comparison.ComparisonGraph.true_DAG);

            comparison.compareFromSimulations(
                    "/Users/josephramsey/Downloads/grasp/scores", simulations,
                    algorithms, statistics, params);
        }
    }

    public void testScores2() {
        for (int grouping : new int[]{7}) {//, 2, 3, 4, 5, 67}) {
            RandomUtil.getInstance().setSeed(38482838482L);

            Parameters params = new Parameters();
            params.set(Params.SAMPLE_SIZE, 1000);
            params.set(Params.NUM_MEASURES, 100);
            params.set(Params.NUM_LATENTS, 0);
            params.set(Params.AVG_DEGREE, 10);
            params.set(Params.RANDOMIZE_COLUMNS, true);
            params.set(Params.COEF_LOW, 0);
            params.set(Params.COEF_HIGH, 1);
            params.set(Params.VAR_LOW, 1);
            params.set(Params.VAR_HIGH, 3);
            params.set(Params.VERBOSE, false);

            params.set(Params.NUM_RUNS, 5);

            params.set(Params.BOSS_ALG, 1);
            params.set(Params.DEPTH, 3);
            params.set(Params.SEM_BIC_STRUCTURE_PRIOR, 4);
            params.set(Params.DO_DISCRIMINATING_PATH_COLLIDER_RULE, false);
            params.set(Params.DO_DISCRIMINATING_PATH_TAIL_RULE, false);

            // default for GIC scores is gic = 4, pd = 1.
            params.set(Params.SEM_GIC_RULE, 4);
            params.set(Params.PENALTY_DISCOUNT, 2);
            params.set(Params.ALPHA, 0.01);
            params.set(Params.ZS_RISK_BOUND, 1, 0.8, 0.6, 0.4, 0.2, 0.1, 0.01, 0.001, 0.0001, 0.00001, 0.000001, 0.0000001, 0.0);

            params.set(Params.DIFFERENT_GRAPHS, true);

            Algorithms algorithms = new Algorithms();

            algorithms.add(new Boss(new edu.cmu.tetrad.algcomparison.score.ZhangShenBoundScore()));

            Simulations simulations = new Simulations();
            simulations.add(new SemSimulation(new RandomForward()));

            Statistics statistics = new Statistics();

//            statistics.add(new ParameterColumn(Params.SAMPLE_SIZE));
//            statistics.add(new ParameterColumn(Params.ALPHA));
//            statistics.add(new ParameterColumn(Params.PENALTY_DISCOUNT));
            statistics.add(new ParameterColumn(Params.ZS_RISK_BOUND));
            statistics.add(new AdjacencyPrecision());
            statistics.add(new AdjacencyRecall());
            statistics.add(new ArrowheadPrecision());
            statistics.add(new ArrowheadRecall());
            statistics.add(new ArrowheadPrecisionCommonEdges());
            statistics.add(new ArrowheadRecallCommonEdges());
            statistics.add(new ElapsedCpuTime());

            Comparison comparison = new Comparison();
            comparison.setShowAlgorithmIndices(true);
            comparison.setComparisonGraph(Comparison.ComparisonGraph.CPDAG_of_the_true_DAG);

            comparison.compareFromSimulations(
                    "/Users/josephramsey/Downloads/grasp/zsb_varyrisk", simulations,
                    algorithms, statistics, params);
        }

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

            TeyssierScorer scorer = new TeyssierScorer(new MsepTest(facts.getFacts()),
                    new GraphScore(facts.getFacts()));

            OrderedMap<String, Set<Graph>> graphs = new ListOrderedMap<>();
            OrderedMap<String, Set<String>> labels = new ListOrderedMap<>();

            List<Node> variables = facts.facts.getVariables();
            Collections.sort(variables);

            PermutationGenerator gen = new PermutationGenerator(variables.size());
            int[] perm;

            while ((perm = gen.next()) != null) {
                List<Node> p = GraphUtils.asList(perm, variables);

                edu.cmu.tetrad.search.Grasp search = new edu.cmu.tetrad.search.Grasp(new MsepTest(facts.getFacts()));
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

            TeyssierScorer scorer = new TeyssierScorer(new MsepTest(facts.getFacts()),
                    new GraphScore(facts.getFacts()));

            OrderedMap<String, Set<Graph>> graphs = new ListOrderedMap<>();
            OrderedMap<String, Set<String>> labels = new ListOrderedMap<>();

            List<Node> variables = facts.facts.getVariables();
            Collections.sort(variables);

            PermutationGenerator gen = new PermutationGenerator(variables.size());
            int[] perm;

            while ((perm = gen.next()) != null) {
                List<Node> p = GraphUtils.asList(perm, variables);

                edu.cmu.tetrad.search.Grasp search = new edu.cmu.tetrad.search.Grasp(new MsepTest(facts.getFacts()));
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

                        Graph graph = RandomGraph.randomGraph(m, 0,
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

                        MsepTest msep = new MsepTest(graph);

                        SemBicScore score = new SemBicScore(dataSet, precomputeCovariances);
                        score.setPenaltyDiscount(1);

                        // Random permutation over 1...|V|.
                        List<Integer> l = new ArrayList<>();
                        for (int w = 0; w < V.size(); w++) {
                            l.add(w);
                        }

                        shuffle(l);
                        shuffle(l);
                        shuffle(l);

                        int[] perm = new int[l.size()];
                        for (int w = 0; w < V.size(); w++) {
                            perm[w] = l.get(w);
                        }

                        List<Node> _perm0 = GraphUtils.asList(perm, msep.getVariables());

                        TeyssierScorer scorer1 = new TeyssierScorer(msep,
                                new GraphScore(graph));
                        scorer1.setUseRaskuttiUhler(true);
                        scorer1.score(_perm0);
                        Graph g1 = scorer1.getGraph(true);

                        IndependenceTest test = new IndTestFisherZ(dataSet, 0.05);
                        List<Node> _perm = GraphUtils.asList(perm, test.getVariables());

                        TeyssierScorer scorer2 = new TeyssierScorer(test, score);
                        scorer2.setUseRaskuttiUhler(true);
                        scorer2.score(_perm);

                        Graph g2 = scorer2.getGraph(true);
                        g2 = GraphUtils.replaceNodes(g2, g1.getNodes());

                        if (g1.equals(g2)) gsCount++;
                        gsShd += GraphSearchUtils.structuralHammingDistance(
                                GraphTransforms.dagToCpdag(g1), GraphTransforms.dagToCpdag(g2));

                        for (int i = 0; i < alpha.length; i++) {
//                            test.setAlpha(alpha[i]);
                            test = new IndTestFisherZ(dataSet, alpha[i]);

                            TeyssierScorer scorer3 = new TeyssierScorer(test, score);
                            scorer3.setUseRaskuttiUhler(true);
                            scorer3.score(_perm);
                            Graph g3 = scorer3.getGraph(true);

                            g3 = GraphUtils.replaceNodes(g3, g1.getNodes());

                            if (g1.equals(g3)) pearlCounts[i]++;
                            pearlShd[i] += GraphSearchUtils.structuralHammingDistance(
                                    GraphTransforms.dagToCpdag(g1), GraphTransforms.dagToCpdag(g3));
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
            Graph g = RandomGraph.randomGraph(10, 0, 15, 100,
                    100, 100, false);
            MsepTest test = new MsepTest(g);
            GraphScore score = new GraphScore(g);

            edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(score);
            Graph cpdag1 = fges.search();

            Paths paths = cpdag1.paths();
            List<Node> initialOrder = cpdag1.getNodes();
            List<Node> pi = paths.getValidOrder(initialOrder, true);

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
            Graph g = RandomGraph.randomGraph(20, 0, 30, 100,
                    100, 100, false);
            SemPm pm = new SemPm(g);
            SemIm im = new SemIm(pm);
            DataSet d = im.simulateData(1000, false);

            IndTestFisherZ test = new IndTestFisherZ(d, 0.001);
            SemBicScore score = new SemBicScore(d, precomputeCovariances);
            score.setPenaltyDiscount(2);

            edu.cmu.tetrad.search.Fges fges = new edu.cmu.tetrad.search.Fges(score);
            Graph cpdag = fges.search();

            Paths paths = cpdag.paths();
            List<Node> initialOrder = cpdag.getNodes();
            List<Node> pi1 = paths.getValidOrder(initialOrder, true);

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
        Graph graph = RandomGraph.randomGraph(7, 0, 15, 100, 100,
                100, false);

        System.out.println("Source = " + graph);//SearchGraphUtils.cpdagForDag(graph));

        MsepTest msep = new MsepTest(graph);
        IndependenceFacts facts = new IndependenceFacts(graph);

        List<Node> nodes = graph.getNodes();

        int count = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 1; j < i; j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                System.out.println("<x, y> = <" + x + ", " + y + ">");

                List<List<Node>> treks = graph.paths().treks(x, y, 4);

                if (treks.size() >= 2) {
                    IndependenceFact fact = new IndependenceFact(x, y, new HashSet<>());
                    facts.add(fact);
                    System.out.println("Added " + fact);

                    count++;
                } else {
                    List<List<Node>> paths = graph.paths().allPaths(x, y, 4);

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

                        if (msep.checkIndependence(x, y, new HashSet<>(pathColliders)).isIndependent()) {
                            IndependenceFact fact = new IndependenceFact(x, y, new HashSet<>(pathColliders));
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

            IndependenceTest test = new MsepTest(facts);

            edu.cmu.tetrad.search.Grasp grasp = new edu.cmu.tetrad.search.Grasp(test);
            grasp.bestOrder(test.getVariables());
            Graph other = grasp.getGraph(false);

            grasp.bestOrder(test.getVariables());
            Graph frugal = other;
            System.out.println("SP " + frugal);

            System.out.println("\n-----\n");

            assert (frugal.getNumEdges() == other.getNumEdges());
        }
    }

    public void testJaime() {
        try {
//            String path = "/Users/josephramsey/Downloads/sample100genes.csv1.imputed.txt";
            String path = "/Users/josephramsey/Downloads/Arabidopsis_dataset_Wdtf.csv1.impute.txt";
            DataSet data = SimpleDataLoader.loadContinuousData(new File(path), "//", '\"',
                    "*", true, Delimiter.TAB, false);

            System.out.println(data.getNumColumns());

            Parameters parameters = new Parameters();
            parameters.set(Params.PENALTY_DISCOUNT, 4);
            parameters.set(Params.SELECTION_MIN_EFFECT, 0.1);
            parameters.set(Params.NUM_SUBSAMPLES, 100);
            parameters.set(Params.TARGETS, "DTF_16LD DTF_16LDVern DTF_23LD DTF_23SD");
            parameters.set(Params.TOP_BRACKET, 500);
            parameters.set(Params.PARALLELIZED, false);
            parameters.set(Params.CSTAR_CPDAG_ALGORITHM, 4);
            parameters.set(Params.FILE_OUT_PATH, "cstar-out.2.1");
            parameters.set(Params.REMOVE_EFFECT_NODES, false);
            parameters.set(Params.SAMPLE_STYLE, 1);

//            RestrictedBoss alg = new RestrictedBoss(new edu.cmu.tetrad.algcomparison.score.SemBicScore());
            Cstar alg = new Cstar(new FisherZ(), new edu.cmu.tetrad.algcomparison.score.SemBicScore());
            Graph graph = alg.search(data, parameters);
        } catch (IOException e) {
            throw new RuntimeException(e);
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




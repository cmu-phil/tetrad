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

import edu.cmu.tetrad.bayes.*;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.mb.Mmmb;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class TestMbClassify extends TestCase {

    static int[][] testCrosstabs = {{496, 53}, {93, 358}};
    static int[][] testCrosstabsNew = {{38, 9, 7}, {10, 15, 14}, {5, 3, 59}};

    public TestMbClassify(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        TetradLogger.getInstance().addOutputStream(System.out);
        TetradLogger.getInstance().setForceLog(true);
    }


    public void tearDown() {
        TetradLogger.getInstance().setForceLog(false);
        TetradLogger.getInstance().removeOutputStream(System.out);
    }

    public void test1() {

    }

    // Slow; leave out of unit test.
    // needs comma delimiter.

    public void rtest1() {

        try {
            DataReader reader = new DataReader();
            reader.setDelimiter(DelimiterType.COMMA);
            reader.setIdsSupplied(true);
            reader.setIdLabel(null);

            DataSet data = reader.parseTabular(new File("test_data/soybean.data"));

            System.out.println(data);

            reader.setKnownVariables(data.getVariables());
            double alpha = 0.000001;
            int depth = 2;
            double prior = 0.0001;
            int maxMissing = 9;

            // The same data is being used for training and testing.
            new MbClassify(data, data, "class", alpha, depth, prior, maxMissing)
                    .classify();

        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void rtest2() {
        String train = "test_data/markovBlanketTestDisc.dat";
        String test = "test_data/markovBlanketTestDisc.dat";
        String variable = "A6";
        String alpha = "0.01";
        String depth = "3";
        String prior = "0.001";
        String maxMissing = "4";

        MbClassify mbClassify = new MbClassify(train, test, variable, alpha, depth, prior, maxMissing);
        mbClassify.classify();
        int[][] crossTabs = mbClassify.crossTabulation();

        assertTrue(Arrays.equals(crossTabs[0], testCrosstabs[0]));
        assertTrue(Arrays.equals(crossTabs[1], testCrosstabs[1]));
    }

    public void rtest3() {
        String train = "test_data/sampledata.txt";
        String test = "test_data/sampledata.txt";
        String variable = "col2";
        String alpha = "0.05";
        String depth = "2";
        String prior = "0.05";
        String maxMissing = "1";

        MbClassify mbClassify = new MbClassify(train, test, variable, alpha, depth, prior, maxMissing);
        mbClassify.classify();
        int[][] crossTabsNew = mbClassify.crossTabulation();

        assertTrue(Arrays.equals(crossTabsNew[0], testCrosstabsNew[0]));
        assertTrue(Arrays.equals(crossTabsNew[1], testCrosstabsNew[1]));
        assertTrue(Arrays.equals(crossTabsNew[2], testCrosstabsNew[2]));
    }

//    public void rtest4() {
//        String train = "test_data/ticdata2000.txt";
//        String test = "test_data/ticdata2000.txt";
//        String variable = "x2";
//        String alpha = "0.01";
//        String depth = "1";
//        String prior = "0.05";
//        String maxMissing = "1";
//
//        MbClassify mbClassify = new MbClassify(train, test, variable, alpha, depth, prior, maxMissing);
//        mbClassify.classify();
//    }

    public void rtest5() {

        String train = "test_data/ar_test_01-20-07.txt";
        String test = "test_data/ar_test_01-20-07.txt";
//        String train = "test_data/ar_test1-03-02-04.txt";
//        String test = "test_data/ar_test1-03-02-04.txt";
        String variable = "X280";
        String alpha = "0.05";
        String depth = "2";
        String prior = "0.05";
        String maxMissing = "1";

        MbClassify mbClassify = new MbClassify(train, test, variable, alpha, depth, prior, maxMissing);
        mbClassify.classify();
    }


    public void makeData(PrintStream _out) {
        try {
            Dag randomGraph = new Dag(GraphUtils.randomGraph(1400, 0, 30, 9,
                    3, 9, false));
            DataSet dataSet = simulateContinuous(randomGraph, 300, _out);

            Writer out = new FileWriter(new File("test_data/myout.dat"));
            DataWriter.writeRectangularData(dataSet, out, ',');
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private DataSet simulateContinuous(Dag randomGraph,
                                       int sampleSize, PrintStream out) {
//        System.out.println("Simulating continuous...");

        LargeSemSimulator simulator =
                new LargeSemSimulator(randomGraph);

        return simulator.simulateDataAcyclic(sampleSize);
    }


    public void rtest6() {
        try {

            String trainPath = "test_data/sp1s_aa_train.txt";
            String target = "X3";

//            String trainPath = "test_data/myout.dat";
//            String target = "X0001";

            double alpha = 0.05;
            int depth = 3;

            DataReader reader = new DataReader();
//            parser.setDelimiter(DelimiterType.WHITESPACE);
//            parser.setIdsSupplied(true);
//            parser.setIdLabel(null);
            reader.setVariablesSupplied(false);
//            parser.setDelimiter(DelimiterType.COMMA);

            DataSet train = null;
            train = reader.parseTabular(new File(trainPath));

//            int[] subsetCols = new int[300];
//            for (int i = 0; i < subsetCols.length; i++) {
//                subsetCols[i] = i;
//            }
//
//            train = train.subsetColumns(subsetCols);


            IndependenceTest indTest = new IndTestFisherZ(train, alpha);

            long time0 = System.currentTimeMillis();
            System.out.println("Start");

//            MbFanSearch search = new MbFanSearch(indTest, depth);
//            HitonMb search = new HitonMb(indTest, depth, false);
            Mmmb search = new Mmmb(indTest, depth, false);
//            List<Node> mb = search.findMb(target);
//            System.out.println(mb);

//            MbFanSearch search = new MbFanSearch(indTest, depth);
//            Graph mbPattern = search.search(target);

            List<Node> mb = search.findMb(target);

            long diff = System.currentTimeMillis() - time0;
            TetradLogger.getInstance().log("info", "Elapsed: " + diff);

            TetradLogger.getInstance().log("info", "MB = " + mb);

//            mb.add(train.getVariable(target));
//            RectangularDataSet subset = train.subsetColumns(mb);
//
//            GesSearch patternSearch = new GesSearch(subset);
//        patternSearch.setDepth(depth);
//            Graph mbPattern = patternSearch.search();
//            System.out.println(mbPattern);
//
//            Dag selectedDag = MbUtils.getOneMbDag(mbPattern);
//
//            SemPm semPm = new SemPm(selectedDag);
//            SemIm semIm = new SemIm(semPm, new CovarianceMatrix(subset));
//
//            System.out.println(semIm);

        }
        catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

    }

    public void rtest7() {
        try {

            String trainPath = "test_data/sp1s_aa_train.txt";
            String target = "X3";
            double alpha = 0.0001;
            int depth = 2;

            DataReader reader = new DataReader();
            reader.setDelimiter(DelimiterType.WHITESPACE);
            reader.setVariablesSupplied(false);

            DataSet train = reader.parseTabular(new File(trainPath));

//            int[] subsetCols = new int[300];
//            for (int i = 0; i < subsetCols.length; i++) {
//                subsetCols[i] = i;
//            }
//
//            train = train.subsetColumns(subsetCols);
            IndependenceTest indTest = new IndTestFisherZ(train, alpha);

            Mbfs search = new Mbfs(indTest, depth);
//            Hiton search = new Hiton(indTest, depth);
//            List<Node> mb = search.findMb(target);
//            System.out.println(mb);

            search.findMb(target);
        }
        catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

    }


    public void rtestSimulatedClassify() {

        // Create a random graph, a random PM, a random IM, and a random
        // data set.
        int numVars = 1000;
        int numEdges = 1000;
        int numTrainSamples = 1000;
        int numTestSamples = 1000;
        int minNumCategories = 2;
        int maxNumCategories = 2;
        double alpha = 0.001;
        int depth = 2;

        System.out.println("Number of variables = " + numVars);
        System.out.println("Number of randomly selected edges = " + numEdges);
        System.out.println("Number of training samples  = " + numTrainSamples);
        System.out.println("Number of test samples  = " + numTestSamples);
        System.out.println();
        System.out.println();

        System.out.println("... creating random DAG");
        Dag randomGraph = new Dag(GraphUtils.randomGraph(numVars, 0, numEdges, 40,
                40, 40, false));

        System.out.println("... creating Bayes PM");
        BayesPm bayesPm =
                new BayesPm(randomGraph, minNumCategories, maxNumCategories);

        System.out.println("... creating Bayes IM");
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);

        System.out.println("... simulating data");
        DataSet trainData = bayesIm.simulateData(numTrainSamples, false);
//        RectangularDataSet trainData = simulateDataMultiple(bayesIm, numTrainSamples, 10);
        DataSet testData = bayesIm.simulateData(numTestSamples, false);
//        RectangularDataSet testData = trainData;

        // Taking each variable in turn as target...
        List variablesTest = testData.getVariables();

        for (int e = 0; e < 10; e++) {
            int i = RandomUtil.getInstance().nextInt(numVars);
            DiscreteVariable target = (DiscreteVariable) variablesTest.get(i);
            Node node = randomGraph.getNode(target.getName());
            Graph trueMb0 = GraphUtils.markovBlanketDag(node, randomGraph);

            if (trueMb0.getNumNodes() < 4) {
                e--;
                continue;
            }

            System.out.println();
            System.out.println(
                    "***************************************************");
            System.out.println();
            System.out.println("EXAMPLE #" + (e + 1) + " TARGET = " + target);

            Dag trueMb1 = new Dag(trueMb0);
            Dag trueMb2 = useVariablesFromData(trueMb1, trainData);

            System.out.println("\nTrue MB: " + trueMb2);

            System.out.println("\nClassification using true MB:");

//            System.out.println("\n###Row summing updater, updating on data points:");
//            classifyMbRsu(trainData, testData, trueMb2, target);

//            System.out.println("\n###Row summing updater, updating on tautologies:");
//            classifyMbRsu2(trainData, testData, trueMb2, target);
//
//            System.out.println("\n###On-the-fly marginal calculator, updating on data points");
            classifyMbOfm(trainData, testData, trueMb2, target);
//
//            System.out.println("\n###On-the-fly marginal calculator, updating on tautologies");
//            classifyMbOfm2(trainData, testData, trueMb2, target);

            // Calculate the MB Pattern for that target from data.
            trainAndTest(trainData, testData, target, alpha, depth);
        }
    }


    public void rCompareAlphas() {

        // Create a random graph, a random PM, a random IM, and a random
        // data set.
        int numVars = 1000;
        int numEdges = 1000;
        int numTrainSamples = 500;
        int numTestSamples = 100;
        int depth = 2;
        int minNumCategories = 3;
        int maxNumCategories = 3;

//        System.out.println("Alpha for MBF = " + alpha);
        System.out.println("Number of variables = " + numVars);
        System.out.println("Number of randomly selected edges = " + numEdges);
        System.out.println("Number of training samples  = " + numTrainSamples);
        System.out.println("Number of test samples  = " + numTestSamples);
//        System.out.println("Depth for MBF = " + depth);
        System.out.println();
        System.out.println();

        System.out.println("... creating random DAG");
        Dag randomGraph = new Dag(GraphUtils.randomGraph(numVars, 0, numEdges, 40,
                40, 40, false));

        System.out.println("... creating SEM PM");
        BayesPm bayesPm =
                new BayesPm(randomGraph, minNumCategories, maxNumCategories);

        System.out.println("... creating SEM IM");
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);

        System.out.println("... simulating data");
        DataSet trainData = bayesIm.simulateData(numTrainSamples, false);
        DataSet testData = bayesIm.simulateData(numTestSamples, false);

        // Taking each variable in turn as target...
        List variables = trainData.getVariables();

        for (int e = 0; e < 10; e++) {
            int i = RandomUtil.getInstance().nextInt(numVars);
            DiscreteVariable target = (DiscreteVariable) variables.get(i);
            Node node = randomGraph.getNode(target.getName());
            Graph trueMb0 = GraphUtils.markovBlanketDag(node, randomGraph);

            if (trueMb0.getNumNodes() < 7) {
                e--;
                continue;
            }

            System.out.println();
            System.out.println(
                    "***************************************************");
            System.out.println();
            System.out.println("TARGET: " + target);

            Dag trueMb1 = new Dag(trueMb0);
            Dag trueMb2 = useVariablesFromData(trueMb1, trainData);

            System.out.println("\nTrue MB: " + trueMb2);

            System.out.println("\nClassification using true MB:");

            classifyMbRsu(trainData, testData, trueMb2, target);

            // Calculate the MB Pattern for that target from data.
            for (double alpha = 0.01; alpha <= 0.05; alpha += 0.01) {
                trainAndTest(trainData, testData, target, alpha, depth);
            }
        }
    }

    public void rtestCompareTainingSampleSizes() {

        // Create a random graph, a random PM, a random IM, and a random
        // data set.
        int numVars = 1500;
        int numEdges = 1500;
        int numTestSamples = 100;
        int minNumCategories = 3;
        int maxNumCategories = 3;

        System.out.println("Number of variables = " + numVars);
        System.out.println("Number of randomly selected edges = " + numEdges);
        System.out.println("Number of test samples  = " + numTestSamples);
        System.out.println();
        System.out.println();

        System.out.println("... creating random DAG");
        Dag randomGraph = new Dag(GraphUtils.randomGraph(numVars, 0, numEdges, 40,
                40, 40, false));

        System.out.println("... creating SEM PM");
        BayesPm bayesPm =
                new BayesPm(randomGraph, minNumCategories, maxNumCategories);

        System.out.println("... creating SEM IM");
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);

        System.out.println("... simulating data");
        DataSet testData = bayesIm.simulateData(numTestSamples, false);
        List variables = testData.getVariables();

        int i = RandomUtil.getInstance().nextInt(numVars);
        DiscreteVariable target = (DiscreteVariable) variables.get(i);
        Node node = randomGraph.getNode(target.getName());
        Graph trueMb0 = GraphUtils.markovBlanketDag(node, randomGraph);
        System.out.println("TARGET: " + target);

        for (int numTrainSamples = 100;
             numTrainSamples < 2000; numTrainSamples += 100) {
            System.out.println();
            System.out.println(
                    "***************************************************");
            System.out.println();
            System.out.println(
                    "Number of training samples  = " + numTrainSamples);
            DataSet trainData =
                    bayesIm.simulateData(numTrainSamples, false);

            Dag trueMb1 = new Dag(trueMb0);
            Dag trueMb2 = useVariablesFromData(trueMb1, trainData);

            System.out.println("\nTrue MB: " + trueMb2);
            System.out.println("\nClassification using true MB:");

//            System.out.println("\n###Row summing updater, updating on data points:");
            classifyMbRsu(trainData, testData, trueMb2, target);
        }
    }

    private void trainAndTest(DataSet trainData,
                              DataSet testData, DiscreteVariable target, double alpha,
                              int depth) {
        System.out.println();
        System.out.println(
                "** RUNNING MBF alpha = " + alpha + " " + "depth = " + depth);

        IndependenceTest test = new IndTestGSquare(trainData, alpha);

        Mbfs search = new Mbfs(test, depth);
        Graph untrimmedGraph = search.search(target.getName());

        List<Node> untrimmedNodes = untrimmedGraph.getNodes();
        System.out.println(untrimmedNodes.size() + " GES nodes: " + untrimmedNodes);

        System.out.println("=================MBF RESULT=================");
        classifyEachDag(untrimmedGraph, search, trainData, testData, target);

        GesMbFilter gesFilter = new GesMbFilter(trainData);
        Graph pattern = gesFilter.filter(untrimmedNodes, target);

        System.out.println(
                "\n=================MBF+GES RESULT=================");
        classifyEachDag(pattern, search, trainData, testData, target);
    }

    private void classifyEachDag(Graph pattern, Mbfs search,
                                 DataSet trainData, DataSet testData,
                                 DiscreteVariable target) {
        System.out.println("\nPattern = " + pattern);

        // List the MB's consistent with this pattern...
        List<Graph> dags = MbUtils.generateMbDags(pattern, true, search.getTest(),
                search.getDepth(), search.getTarget());

        // For each such DAG...
        for (int j = 0; j < dags.size(); j++) {
            Dag estimatedMb = new Dag(dags.get(j));
            System.out.println(
                    "\nMBD # " + (j + 1) + " in pattern: " + estimatedMb);

            //                System.out.println("\n### (Row summing updater:)");
            classifyMbRsu(trainData, testData, estimatedMb, target);

            //                System.out.println("\n### (On the fly marginal calculator:)");
            //                classifyMbOfm(estimatedMb, trainData, target);
        }
    }

    /**
     * Uses RowSummingUpdater
     */
    private void classifyMbRsu(DataSet trainData,
                               DataSet testData, Dag estimatedMb,
                               DiscreteVariable target) {

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        // Classify the target using training data as testing data.
        List<Node> mbNodes = estimatedMb.getNodes();

        //The Markov blanket nodes will correspond to a subset of the variables
        //in the training dataset.  Find the subset dataset.
        DataSet trainDataSubset = trainData.subsetColumns(mbNodes);

        //To create a Bayes net for the Markov blanket we need the DAG.
        BayesPm mbBayesPm = new BayesPm(estimatedMb);

        //To parameterize the Bayes net we need the number of values
        //of each variable.
        List varsTrain = trainDataSubset.getVariables();

        for (int i = 0; i < varsTrain.size(); i++) {
            DiscreteVariable dv = (DiscreteVariable) varsTrain.get(i);
            int ncats = dv.getNumCategories();
            mbBayesPm.setNumCategories(mbNodes.get(i), ncats);
        }

        //Create an updater for the instantiated Bayes net.
        BayesIm mbBayesIm =
                new MlBayesEstimator().estimate(mbBayesPm, trainDataSubset);
        RowSummingExactUpdater updater = new RowSummingExactUpdater(mbBayesIm);

        //The subset dataset of the dataset to be classified containing
        //the variables in the Markov blanket.
        DataSet testDataSubset = testData.subsetColumns(mbNodes);

        //Get the raw data from the dataset to be classified, the number
        //of variables and the number of cases.
        int ncases = testDataSubset.getNumRows();

        int[] estimatedCategories = new int[ncases];
        Arrays.fill(estimatedCategories, -1);

        //The variables in the dataset.
        List varsClassify = testDataSubset.getVariables();

        //For each case in the dataset to be classified compute the estimated
        //value of the target variable and increment the appropriate element
        //of the crosstabulation array.
        for (int k = 0; k < ncases; k++) {

            //Create an Evidence instance for the instantiated Bayes net
            //which will allow that updating.
            Evidence evidence = Evidence.tautology(mbBayesIm);
            Proposition proposition = evidence.getProposition();

            //Restrict all other variables to their observed values in
            //this case.
            for (int m = 0; m < varsClassify.size(); m++) {
                DiscreteVariable testVar =
                        (DiscreteVariable) varsClassify.get(m);

                if (testVar.equals(target)) {
                    continue;
                }

                int iother = proposition.getNodeIndex(testVar.getName());
                proposition.setCategory(iother, testDataSubset.getInt(k, m));
            }

            updater.setEvidence(evidence);

            // for each possible value of target compute its probability in
            // the updated Bayes net.  Select the value with the highest
            // probability as the estimated value.
            int indexTargetBN = proposition.getNodeIndex(target.getName());

            //Straw man values--to be replaced.
            double highestProb = -0.1;
            int estimatedCategory = -1;

            for (int m = 0; m < target.getNumCategories(); m++) {
                double marginal = updater.getMarginal(indexTargetBN, m);

                if (marginal > highestProb) {
                    highestProb = marginal;
                    estimatedCategory = m;
                }
            }

            estimatedCategories[k] = estimatedCategory;
        }

        //Create a crosstabulation table to store the counts of observed
        //versus estimated occurrences of each value of the target variable.
        int targetIndex = varsClassify.indexOf(target);
        int numCategories = target.getNumCategories();
        int[][] crossTabs = new int[numCategories][numCategories];

        //Will count the number of cases where the target variable
        //is correctly classified.
        int numberCorrect = 0;
        int numberCounted = 0;

        for (int k = 0; k < ncases; k++) {
            int estimatedCategory = estimatedCategories[k];
            int observedValue = testDataSubset.getInt(k, targetIndex);

            if (estimatedCategory < 0) {
                continue;
            }

            crossTabs[observedValue][estimatedCategory]++;
            numberCounted++;

            if (observedValue == estimatedCategory) {
                numberCorrect++;
            }
        }

        double percentCorrect =
                100.0 * ((double) numberCorrect) / ((double) numberCounted);

        // Print the cross classification.
        System.out.println();
        System.out.println("\t\t\tEstimated\t");
        System.out.print("Observed\t");

        for (int m = 0; m < numCategories; m++) {
            System.out.print(target.getCategory(m) + "\t");
        }

        System.out.println();

        for (int k = 0; k < numCategories; k++) {
            System.out.print(target.getCategory(k) + "\t");

            for (int m = 0; m < numCategories; m++) {
                System.out.print(crossTabs[k][m] + "\t\t");
            }

            System.out.println();
        }

        System.out.println();
        System.out.println("Number correct = " + numberCorrect);
        System.out.println("Number counted = " + numberCounted);
        System.out.println(
                "Percent correct = " + nf.format(percentCorrect) + "%");
    }

    private void classifyMbRsu2(DataSet trainData,
                                DataSet testData, Dag estimatedMb,
                                DiscreteVariable target) {

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        // Classify the target using training data as testing data.
        List<Node> mbNodes = estimatedMb.getNodes();

        //The Markov blanket nodes will correspond to a subset of the variables
        //in the training dataset.  Find the subset dataset.
        DataSet trainDataSubset = trainData.subsetColumns(mbNodes);

        //To create a Bayes net for the Markov blanket we need the DAG.
        BayesPm mbBayesPm = new BayesPm(estimatedMb);

        //To parameterize the Bayes net we need the number of values
        //of each variable.
        List varsTrain = trainDataSubset.getVariables();

        for (int i = 0; i < varsTrain.size(); i++) {
            DiscreteVariable dv = (DiscreteVariable) varsTrain.get(i);
            int ncats = dv.getNumCategories();
            mbBayesPm.setNumCategories(mbNodes.get(i), ncats);
        }

        //Create an updater for the instantiated Bayes net.
        BayesIm mbBayesIm =
                new MlBayesEstimator().estimate(mbBayesPm, trainDataSubset);
        RowSummingExactUpdater updater = new RowSummingExactUpdater(mbBayesIm);

        //The subset dataset of the dataset to be classified containing
        //the variables in the Markov blanket.
        DataSet testDataSubset = testData.subsetColumns(mbNodes);

        //Get the raw data from the dataset to be classified, the number
        //of variables and the number of cases.
        int ncases = testDataSubset.getNumRows();

        int[] estimatedCategories = new int[ncases];
        Arrays.fill(estimatedCategories, -1);

        //The variables in the dataset.
        List<Node> varsClassify = testDataSubset.getVariables();

        //For each case in the dataset to be classified compute the estimated
        //value of the target variable and increment the appropriate element
        //of the crosstabulation array.
        for (int k = 0; k < ncases; k++) {

            //Create an Evidence instance for the instantiated Bayes net
            //which will allow that updating.
            Evidence evidence = Evidence.tautology(mbBayesIm);
            Proposition proposition = evidence.getProposition();
            updater.setEvidence(evidence);

            // for each possible value of target compute its probability in
            // the updated Bayes net.  Select the value with the highest
            // probability as the estimated value.
            int indexTargetBN = proposition.getNodeIndex(target.getName());

            //Straw man values--to be replaced.
            double highestProb = -0.1;
            int estimatedCategory = -1;

            for (int m = 0; m < target.getNumCategories(); m++) {
                double marginal = updater.getMarginal(indexTargetBN, m);

                if (marginal > highestProb) {
                    highestProb = marginal;
                    estimatedCategory = m;
                }
            }

            estimatedCategories[k] = estimatedCategory;
        }

        //Create a crosstabulation table to store the counts of observed
        //versus estimated occurrences of each value of the target variable.
        int targetIndex = varsClassify.indexOf(target);
        int numCategories = target.getNumCategories();
        int[][] crossTabs = new int[numCategories][numCategories];

        //Will count the number of cases where the target variable
        //is correctly classified.
        int numberCorrect = 0;
        int numberCounted = 0;

        for (int k = 0; k < ncases; k++) {
            int estimatedCategory = estimatedCategories[k];
            int observedValue = testDataSubset.getInt(k, targetIndex);

            if (estimatedCategory < 0) {
                continue;
            }

            crossTabs[observedValue][estimatedCategory]++;
            numberCounted++;

            if (observedValue == estimatedCategory) {
                numberCorrect++;
            }
        }

        double percentCorrect =
                100.0 * ((double) numberCorrect) / ((double) numberCounted);

        // Print the cross classification.
        System.out.println();
        System.out.println("\t\t\tEstimated\t");
        System.out.print("Observed\t");

        for (int m = 0; m < numCategories; m++) {
            System.out.print(target.getCategory(m) + "\t");
        }

        System.out.println();

        for (int k = 0; k < numCategories; k++) {
            System.out.print(target.getCategory(k) + "\t");

            for (int m = 0; m < numCategories; m++) {
                System.out.print(crossTabs[k][m] + "\t\t");
            }

            System.out.println();
        }

        System.out.println();
        System.out.println("Number correct = " + numberCorrect);
        System.out.println("Number counted = " + numberCounted);
        System.out.println(
                "Percent correct = " + nf.format(percentCorrect) + "%");
    }

    /**
     * Uses OntheflyMarginalCalculator
     */
    private void classifyMbOfm(DataSet trainData,
                               DataSet testData, Dag estimatedMb,
                               DiscreteVariable target) {

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        // Classify the target using training data as testing data.
        List<Node> mbNodes = estimatedMb.getNodes();

        //The Markov blanket nodes will correspond to a subset of the variables
        //in the training dataset.  Find the subset dataset.
        DataSet trainDataSubset = trainData.subsetColumns(mbNodes);

        //To create a Bayes net for the Markov blanket we need the DAG.
        BayesPm mbBayesPm = new BayesPm(estimatedMb);

        //To parameterize the Bayes net we need the number of values
        //of each variable.
        List varsTrain = trainDataSubset.getVariables();

        for (int i = 0; i < varsTrain.size(); i++) {
            DiscreteVariable dv = (DiscreteVariable) varsTrain.get(i);
            int ncats = dv.getNumCategories();
            mbBayesPm.setNumCategories(mbNodes.get(i), ncats);
        }

        //Create an updater for the instantiated Bayes net.
        OnTheFlyMarginalCalculator bayesUpdater =
                new OnTheFlyMarginalCalculator(mbBayesPm, trainDataSubset);

        //The subset dataset of the dataset to be classified containing
        //the variables in the Markov blanket.
        DataSet testDataSubset = testData.subsetColumns(mbNodes);

        //Get the raw data from the dataset to be classified, the number
        //of variables and the number of cases.
        int ncases = testDataSubset.getNumRows();

        int[] estimatedCategories = new int[ncases];
        Arrays.fill(estimatedCategories, -1);

        //The variables in the dataset.
        List varsClassify = testDataSubset.getVariables();

        //For each case in the dataset to be classified compute the estimated
        //value of the target variable and increment the appropriate element
        //of the crosstabulation array.
        for (int k = 0; k < ncases; k++) {

            //Create an Evidence instance for the instantiated Bayes net
            //which will allow that updating.
            Proposition proposition = Proposition.tautology(bayesUpdater);

            //Restrict all other variables to their observed values in
            //this case.
            for (int m = 0; m < varsClassify.size(); m++) {
                DiscreteVariable testVar =
                        (DiscreteVariable) varsClassify.get(m);

                if (testVar.equals(target)) {
                    continue;
                }

                int iother = proposition.getNodeIndex(testVar.getName());
                proposition.setCategory(iother, testDataSubset.getInt(k, m));
            }

            bayesUpdater.setEvidence(new Evidence(proposition));

            // for each possible value of target compute its probability in
            // the updated Bayes net.  Select the value with the highest
            // probability as the estimated value.
            int indexTargetBN = proposition.getNodeIndex(target.getName());

            //Straw man values--to be replaced.
            double highestProb = -0.1;
            int estimatedCategory = -1;

            for (int category = 0;
                 category < target.getNumCategories(); category++) {
                double marginal =
                        bayesUpdater.getMarginal(indexTargetBN, category);

                if (marginal > highestProb) {
                    highestProb = marginal;
                    estimatedCategory = category;
                }
            }

            //Sometimes the marginal cannot be computed because certain
            //combinations of values of the variables do not occur in the
            //training dataset.  If that happens skip the case.
            if (estimatedCategory < 0) {
                continue;
            }

            estimatedCategories[k] = estimatedCategory;
        }

        //Create a crosstabulation table to store the counts of observed
        //versus estimated occurrences of each value of the target variable.
        int targetIndex = varsClassify.indexOf(target);
        int numCategories = target.getNumCategories();
        int[][] crossTabs = new int[numCategories][numCategories];

        //Will count the number of cases where the target variable
        //is correctly classified.
        int numberCorrect = 0;
        int numberCounted = 0;

        for (int k = 0; k < ncases; k++) {
            int estimatedCategory = estimatedCategories[k];
            int observedValue = testDataSubset.getInt(k, targetIndex);

            if (estimatedCategory < 0) {
                continue;
            }

            crossTabs[observedValue][estimatedCategory]++;
            numberCounted++;

            if (observedValue == estimatedCategory) {
                numberCorrect++;
            }
        }

        double percentCorrect =
                100.0 * ((double) numberCorrect) / ((double) numberCounted);

        // Print the cross classification.
        System.out.println();
        System.out.println("\t\t\tEstimated\t");
        System.out.print("Observed\t");

        for (int m = 0; m < numCategories; m++) {
            System.out.print(target.getCategory(m) + "\t");
        }

        System.out.println();

        for (int k = 0; k < numCategories; k++) {
            System.out.print(target.getCategory(k) + "\t");

            for (int m = 0; m < numCategories; m++) {
                System.out.print(crossTabs[k][m] + "\t\t");
            }

            System.out.println();
        }

        System.out.println();
        System.out.println("Number correct = " + numberCorrect);
        System.out.println("Number counted = " + numberCounted);
        System.out.println(
                "Percent correct = " + nf.format(percentCorrect) + "%");
    }

    private void classifyMbOfm2(DataSet trainData,
                                DataSet testData, Dag estimatedMb,
                                DiscreteVariable target) {

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        // Classify the target using training data as testing data.
        List<Node> mbNodes = estimatedMb.getNodes();

        //The Markov blanket nodes will correspond to a subset of the variables
        //in the training dataset.  Find the subset dataset.
        DataSet trainDataSubset = trainData.subsetColumns(mbNodes);

        //To create a Bayes net for the Markov blanket we need the DAG.
        BayesPm mbBayesPm = new BayesPm(estimatedMb);

        //To parameterize the Bayes net we need the number of values
        //of each variable.
        List varsTrain = trainDataSubset.getVariables();

        for (int i = 0; i < varsTrain.size(); i++) {
            DiscreteVariable dv = (DiscreteVariable) varsTrain.get(i);
            int ncats = dv.getNumCategories();
            mbBayesPm.setNumCategories(mbNodes.get(i), ncats);
        }

        //Create an updater for the instantiated Bayes net.
        OnTheFlyMarginalCalculator bayesUpdater =
                new OnTheFlyMarginalCalculator(mbBayesPm, trainDataSubset);

        //The subset dataset of the dataset to be classified containing
        //the variables in the Markov blanket.
        DataSet testDataSubset = testData.subsetColumns(mbNodes);

        //Get the raw data from the dataset to be classified, the number
        //of variables and the number of cases.
        int ncases = testDataSubset.getNumRows();

        int[] estimatedCategories = new int[ncases];
        Arrays.fill(estimatedCategories, -1);

        //The variables in the dataset.
        List<Node> varsClassify = testDataSubset.getVariables();

        //For each case in the dataset to be classified compute the estimated
        //value of the target variable and increment the appropriate element
        //of the crosstabulation array.
        for (int k = 0; k < ncases; k++) {

            //Create an Evidence instance for the instantiated Bayes net
            //which will allow that updating.
            Proposition proposition = Proposition.tautology(bayesUpdater);
            bayesUpdater.setEvidence(new Evidence(proposition));

            // for each possible value of target compute its probability in
            // the updated Bayes net.  Select the value with the highest
            // probability as the estimated value.
            int indexTargetBN = proposition.getNodeIndex(target.getName());

            //Straw man values--to be replaced.
            double highestProb = -0.1;
            int estimatedCategory = -1;

            for (int category = 0;
                 category < target.getNumCategories(); category++) {
                double marginal =
                        bayesUpdater.getMarginal(indexTargetBN, category);

                if (marginal > highestProb) {
                    highestProb = marginal;
                    estimatedCategory = category;
                }
            }

            estimatedCategories[k] = estimatedCategory;
        }

        //Create a crosstabulation table to store the counts of observed
        //versus estimated occurrences of each value of the target variable.
        int targetIndex = varsClassify.indexOf(target);
        int numCategories = target.getNumCategories();
        int[][] crossTabs = new int[numCategories][numCategories];

        //Will count the number of cases where the target variable
        //is correctly classified.
        int numberCorrect = 0;
        int numberCounted = 0;

        for (int k = 0; k < ncases; k++) {
            int estimatedCategory = estimatedCategories[k];
            int observedValue = testDataSubset.getInt(k, targetIndex);

            if (estimatedCategory < 0) {
                continue;
            }

            crossTabs[observedValue][estimatedCategory]++;
            numberCounted++;

            if (observedValue == estimatedCategory) {
                numberCorrect++;
            }
        }

        double percentCorrect =
                100.0 * ((double) numberCorrect) / ((double) numberCounted);

        // Print the cross classification.
        System.out.println();
        System.out.println("\t\t\tEstimated\t");
        System.out.print("Observed\t");

        for (int m = 0; m < numCategories; m++) {
            System.out.print(target.getCategory(m) + "\t");
        }

        System.out.println();

        for (int k = 0; k < numCategories; k++) {
            System.out.print(target.getCategory(k) + "\t");

            for (int m = 0; m < numCategories; m++) {
                System.out.print(crossTabs[k][m] + "\t\t");
            }

            System.out.println();
        }

        System.out.println();
        System.out.println("Number correct = " + numberCorrect);
        System.out.println("Number counted = " + numberCounted);
        System.out.println(
                "Percent correct = " + nf.format(percentCorrect) + "%");
    }

    private Dag useVariablesFromData(Dag oldDag, DataSet dataSet) {
        Dag newDag = new Dag();
        List<Node> oldNodes = oldDag.getNodes();
        List<Node> newNodes = new ArrayList<Node>();

        for (Node oldNode : oldNodes) {
            String name = oldNode.getName();
            Node variable = dataSet.getVariable(name);
            newNodes.add(variable);
            newDag.addNode(variable);
        }

        Set<Edge> oldEdges = oldDag.getEdges();

        for (Edge edge : oldEdges) {
            Node node1 = newNodes.get(oldNodes.indexOf(edge.getNode1()));
            Node node2 = newNodes.get(oldNodes.indexOf(edge.getNode2()));
            Endpoint endpoint1 = edge.getEndpoint1();
            Endpoint endpoint2 = edge.getEndpoint2();
            Edge newEdge = new Edge(node1, node2, endpoint1, endpoint2);
            newDag.addEdge(newEdge);
        }

        return newDag;
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestMbClassify.class);
    }
}






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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.RandomUtil;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class TestMbfReport extends TestCase {
    private NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    public static int CONTINUOUS = 0;
    public static int DISCRETE = 1;

    static int[][] testCrosstabs = {{487, 49}, {81, 366}};
    static int[][] testCrosstabsNew = {{38, 9, 7}, {10, 15, 14}, {5, 3, 59}};
    private PrintWriter fileOut;

    public TestMbfReport(String name) {
        super(name);
    }

    public void testBlank() {
        // Blank to keep the automatic JUnit runner happy.
    }

    public void rtestReportOut() {
        try {
            double alpha = 0.001;
            int dimension = 200;
            int sampleSize = 1000;
            int depth = 2;
            String variableType = "Continuous";
//            String variableType = "Discrete";
            int numTargets = 25;

            String fileOutName = "target/test_data/mbfsreport_"
                    + nf.format(alpha) + "_" +
                    +dimension + "_"
                    + sampleSize + "_" +
                    +depth + "_" +
                    variableType + "_" +
                    numTargets
                    + ".txt";
            fileOut = new PrintWriter(new FileWriter(new File(fileOutName)));

            generateReport(alpha, dimension, sampleSize, depth, variableType,
                    numTargets);

            fileOut.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void rtestReportOutOvernightRun() {
        try {
            double[] alphas = new double[]{0.0001, 0.001, 0.01};
            int[] dimensions = new int[]{500, 1000, 2000, 5000};
            int sampleSize = 1000;
            int depth = 2;
            int numTargets = 25;

//            for (double alpha : alphas) {
//                for (int dimension : dimensions) {
//                    doRun(alpha, dimension, sampleSize, depth, "Continuous",
//                            numTargets);
//                }
//            }
//
//            for (double alpha : alphas) {
//                for (int dimension : dimensions) {
//                    doRun(alpha, dimension, sampleSize, depth, "Discrete",
//                            numTargets);
//                }
//            }
//
//            doRun(0.0001, 10000, sampleSize, depth, "Continuous",
//                    numTargets);
//            doRun(0.001, 10000, sampleSize, depth, "Continuous",
//                    numTargets);
//            doRun(0.01, 10000, sampleSize, depth, "Continuous",
//                    numTargets);

            doRun(0.01, 10000, sampleSize, 2, "Continuous",
                    numTargets);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

//    public void rtestReportOutOvernightRun2() {
//        try {
//            Logger logger = LogUtils.getInstance().getLogger();
//            logger.setUseParentHandlers(false);
//
//
//            double[] alphas = new double[]{0.01, 0.001, 0.0001};
//            int[] dimensions = new int[]{500, 1000, 2000, 5000};
//            int sampleSize = 1000;
//            int depth = 2;
////            String variableType = "Continuous";
//            String variableType = "Discrete";
//            int numTargets = 25;
//
//            for (double alpha : alphas) {
//                for (int dimension : dimensions) {
//                    doRun(alpha, dimension, sampleSize, depth, variableType,
//                            numTargets);
//                }
//            }
//
//            doRun(0.0001, 10000, sampleSize, depth, "Continuous",
//                    numTargets);
//            doRun(0.01, 10000, sampleSize, depth, "Continuous",
//                    numTargets);
//        }
//        catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    private void doRun(double alpha, int dimension, int sampleSize, int depth,
                       String variableType, int numTargets) throws IOException {
        String fileOutName = "target/test_data/mbfsreport_"
                + nf.format(alpha) + "_" +
                +dimension + "_"
                + sampleSize + "_" +
                +depth + "_" +
                variableType + "_" +
                numTargets
                + ".txt";
        fileOut = new PrintWriter(new FileWriter(new File(fileOutName)));

        generateReport(alpha, dimension, sampleSize, depth, variableType,
                numTargets);

        fileOut.close();
    }

    private void generateReport(double alpha, int dimension, int sampleSize,
                                int depth, String variableType, int numTargets) {
        printLine("MBF trials from random targets in a single simulated data set.");
        printLine("");
        printLine("Alpha = " + nf.format(alpha));
        printLine("Dimension = " + dimension);
        printLine("Sample size  = " + sampleSize);
        printLine("Depth = " + depth);
        printLine("Variable type = " + variableType);
        printLine("Num targets = " + numTargets);
        printLine("");
        printLine("");

        if ("Continuous".equals(variableType)) {
            examineContinuousDatabase(numTargets, dimension, sampleSize,
                    alpha, depth);
        } else if ("Discrete".equals(variableType)) {
            examineDiscreteDatabase(numTargets, dimension, sampleSize,
                    alpha, depth);
        } else {
            throw new IllegalStateException();
        }
    }

    private void examineContinuousDatabase(int numTargets, int dimension,
                                           int sampleSize, double alpha, int depth) {
        printLine("TARGET\tSIZE" + "\tFP\tFN" + "\tPFP\tPFN" +
                "\tCFP\tCFN" + "\tPCFP\tPCFN" + "\tTIME");

        System.out.println("Creating graph.");

        Dag randomGraph = new Dag(GraphUtils.randomGraph(dimension, 0, dimension, 40,
                40, 40, false));

        System.out.println("Starting simulation.");

        LargeSemSimulator simulator = new LargeSemSimulator(randomGraph);
        DataSet dataSet = simulator.simulateDataAcyclic(sampleSize);
        IndependenceTest test = new IndTestFisherZGeneralizedInverse(dataSet, alpha);

        System.out.println("Running MBF");
        examineRandomTargets(numTargets, randomGraph, test, depth);
    }


    private void examineDiscreteDatabase(int numTargets, int dimension,
                                         int sampleSize, double alpha, int depth) {
        printLine("TARGET\tSIZE" + "\tFP\tFN" + "\tPFP\tPFN" +
                "\tCFP\tCFN" + "\tPCFP\tPCFN" + "\tTIME");

        Dag randomGraph = new Dag(GraphUtils.randomGraph(dimension, 0, dimension, 40,
                40, 40, false));

        BayesPm bayesPm = new BayesPm(randomGraph, 2, 2);
        BayesIm bayesIm = new MlBayesIm(bayesPm, MlBayesIm.RANDOM);
        DataSet dataSet = bayesIm.simulateData(sampleSize, false);
        IndependenceTest test = new IndTestChiSquare(dataSet, alpha);

        examineRandomTargets(numTargets, randomGraph, test, depth);
    }

    private void examineRandomTargets(int numTargets, Dag trueGraph,
                                      IndependenceTest test, int depth) {
        RandomUtil random = RandomUtil.getInstance();
        int numNodes = trueGraph.getNumNodes();

        for (int i = 0; i < numTargets; i++) {
            int index = random.nextInt(numNodes);

            Node tTrue = trueGraph.getNodes().get(index);
            String targetName = tTrue.getName();
            Graph gTrue = GraphUtils.markovBlanketDag(tTrue, trueGraph);

//            if (gTrue.getNumNodes() - 1 < 4) {
//                i--;
//                continue;
//            }

            Mbfs mbSearch = new Mbfs(test, depth);
            Graph gEst = mbSearch.search(tTrue.getName());

            double elapsedTime = mbSearch.getElapsedTime() / 1000d;
            Node tEst = gEst.getNode(tTrue.getName());

            orientNondirectedEdgesAs(gEst, gTrue);

            MbUtils.trimToMbNodes(gEst, tEst, false);
            MbUtils.trimEdgesAmongParents(gEst, tEst);
            MbUtils.trimEdgesAmongParentsOfChildren(gEst, tEst);

            int fp = getFp(gEst.getNodes(), gTrue.getNodes(), targetName);
            int fn = getFn(gEst.getNodes(), gTrue.getNodes(), targetName);

            int pfp = getFp(gEst.getParents(tEst), gTrue.getParents(tTrue), targetName);
            int pfn = getFn(gEst.getParents(tEst), gTrue.getParents(tTrue), targetName);

            int cfp = getFp(gEst.getChildren(tEst), gTrue.getChildren(tTrue), targetName);
            int cfn = getFn(gEst.getChildren(tEst), gTrue.getChildren(tTrue), targetName);

            List<Node> childrenEst = gEst.getChildren(tEst);
            List<Node> pcEst = new LinkedList<Node>();

            for (Node node : childrenEst) {
                pcEst.addAll(gEst.getParents(node));
            }

            List<Node> childrenTrue = gTrue.getChildren(tTrue);
            List<Node> pcTrue = new LinkedList<Node>();

            for (Node node : childrenTrue) {
                pcTrue.addAll(gTrue.getParents(node));
            }

            int pcfp = getFp(pcEst, pcTrue, targetName);
            int pcfn = getFn(pcEst, pcTrue, targetName);

            int mbSize = extractVarNames(gTrue.getNodes(), tTrue.getName()).size();

            printLine((i + 1) + "\t" + mbSize + "\t" +
                    fp + "\t" + fn + "\t" +
                    pfp + "\t" + pfn + "\t" +
                    cfp + "\t" + cfn + "\t" +
                    pcfp + "\t" + pcfn + "\t" +
                    nf.format(elapsedTime));
        }
    }

    private int getFp(List<Node> nodesEst, List<Node> nodesTrue,
                      String targetName) {
        List<String> truth = extractVarNames(nodesTrue, targetName);
        List<String> est = extractVarNames(nodesEst, targetName);
        List<String> estAndTruth = new ArrayList<String>(est);
        estAndTruth.retainAll(truth);

        List<String> estFp = new ArrayList<String>(est);
        estFp.removeAll(estAndTruth);
        return estFp.size();
    }

    private int getFn(List<Node> nodesEst, List<Node> nodesTrue, String targetName) {
        List<String> truth = extractVarNames(nodesTrue, targetName);
        List<String> mbf = extractVarNames(nodesEst, targetName);
        List<String> estAndTruth = new ArrayList<String>(mbf);
        estAndTruth.retainAll(truth);

        List<String> estFn = new ArrayList<String>(truth);
        estFn.removeAll(estAndTruth);
        return estFn.size();
    }

    private void orientNondirectedEdgesAs(Graph gEst, Graph gTrue) {
        for (Edge edge : gEst.getEdges()) {
            if (Edges.isUndirectedEdge(edge) || Edges.isBidirectedEdge(edge)) {
                Node a1 = edge.getNode1();
                Node a2 = edge.getNode2();

                Node b1 = gTrue.getNode(a1.getName());
                Node b2 = gTrue.getNode(a2.getName());

                if (b1 != null && b2 != null && gTrue.getEdge(b1, b2) != null) {
                    gEst.setEndpoint(a1, a2, gTrue.getEndpoint(b1, b2));
                    gEst.setEndpoint(a2, a1, gTrue.getEndpoint(b2, b1));
                }
            }
        }
    }

    private List<String> extractVarNames(List<Node> nodes, String targetName) {
        List<String> varNames = new ArrayList<String>();

        for (Node node : nodes) {
            varNames.add(node.getName());
        }

        varNames.remove(targetName);
        Collections.sort(varNames);
        return varNames;
    }

    private void printLine(String s) {
        System.out.println(s);
        fileOut.println(s);
        fileOut.flush();
    }

    public static void main(String[] args) {
        new TestMbfReport("").rtestReportOutOvernightRun();
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestMbfReport.class);
    }
}






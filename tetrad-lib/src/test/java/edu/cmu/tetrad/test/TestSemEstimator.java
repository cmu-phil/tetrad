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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;

/**
 * Tests the MeasurementSimulator class using diagnostics devised by Richard
 * Scheines. The diagnostics are described in the Javadocs, below.
 *
 * @author Joseph Ramsey
 */
public class TestSemEstimator extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestSemEstimator(String name) {
        super(name);
    }

    public void testSet1() {
        System.out.println("\n\nTest Set 1.");
        Graph graph = constructGraph1();
        SemPm semPm = new SemPm(graph);
        ICovarianceMatrix covMatrix = constructCovMatrix1();
        SemEstimator estimator = new SemEstimator(covMatrix, semPm);
        System.out.println();
        System.out.println("... Before:");
        System.out.println(estimator);
        estimator.estimate();
        System.out.println();
        System.out.println("... After:");
        System.out.println(estimator);
    }

    public void testSet2() {
        System.out.println("\n\nTest Set 2.");
        Graph graph = constructGraph2();
        SemPm semPm = new SemPm(graph);
        ICovarianceMatrix covMatrix = constructCovMatrix2();
        SemEstimator estimator = new SemEstimator(covMatrix, semPm);
        System.out.println();
        System.out.println("... Before:");
        System.out.println(estimator);
        estimator.estimate();
        System.out.println();
        System.out.println("... After:");
        System.out.println(estimator);
    }

    public void testSet3() {
        System.out.println("\n\nTest Set 3.");
        Graph graph = constructGraph2();
        SemPm semPm = new SemPm(graph);
        ICovarianceMatrix covMatrix = constructCovMatrix2();
        SemEstimator estimator = new SemEstimator(covMatrix, semPm);
        System.out.println();
        System.out.println("... Before:");
        System.out.println(estimator);
        estimator.estimate();
        System.out.println();
        System.out.println("... After:");
        System.out.println(estimator);
    }

    public void testSet8() {
        Graph graph = GraphConverter.convert("X1-->X2,X2-->X3,X3-->X4,X4-->X1");

        System.out.println(graph);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet data = im.simulateData(1000, false);

        SemIm est = new SemEstimator(data, pm, new SemOptimizerPowell()).estimate();
//        SemIm est = new SemEstimator(data, pm, new SemOptimizerScattershot()).estimate();

        System.out.println("\nPowell");
        printStats(im, est);

//        SemIm est2 = new SemEstimator(data, pm, new SemOptimizerScattershot()).estimate();
//
//        System.out.println("\nRandom");
//        printStats(im, est2);
    }

    private void printStats(SemIm im, SemIm est) {
        NumberFormat nf2 = new DecimalFormat("0.0000");

        System.out.println("chi square = " + nf2.format(est.getChiSquare()));
        System.out.println("p = " + nf2.format(est.getPValue()));

        double[] imParamValues = im.getFreeParamValues();
        double[] estParamValues = est.getFreeParamValues();

        System.out.println(Arrays.toString(imParamValues));
        System.out.println(Arrays.toString(estParamValues));

        System.out.println();
        List<Parameter> parameters = im.getSemPm().getParameters();
        NumberFormat nf = new DecimalFormat("0.0");

        for (int i = 0; i < imParamValues.length; i++) {
            Parameter parameter = parameters.get(i);
            double v = (Math.abs(imParamValues[i] - estParamValues[i]) / Math.abs(imParamValues[i])) * 100.;
            System.out.println(nf.format(v)  + "% " + parameter.toString());
        }
    }

    public void testSet9() {
        Graph graph = GraphConverter.convert("X1-->X2,X2-->X3,X3-->X4,X4-->X1");

        System.out.println(graph);

        SemPm pm = new SemPm(graph);

        double[] sum1 = new double[pm.getFreeParameters().size()];
        double[] sum2 = new double[pm.getFreeParameters().size()];
        double chisqSum1 = 0.0;
        double chisqSum2 = 0.0;
        double pSum1 = 0.0;
        double pSum2 = 0.0;

        int runs = 1;

        for (int i = 0; i < runs; i++) {
            System.out.println("Round " + (i + 1));

            SemIm im = new SemIm(pm);

            double[] trueParamValues = im.getFreeParamValues();

            DataSet data = im.simulateData(1000, false);

            System.out.println("CDS");
            SemIm est1 = new SemEstimator(data, pm, new SemOptimizerPowell()).estimate();
            double[] imParamValues1 = est1.getFreeParamValues();

//            System.out.println("Random");
//            SemIm est2 = new SemEstimator(data, pm, new SemOptimizerScattershot()).estimate();
//            double[] imParamValues2 = est2.getFreeParamValues();

            for (int j = 0; j < sum1.length; j++) sum1[j] += 100 * Math.abs(trueParamValues[j] - imParamValues1[j]) / Math.abs(trueParamValues[j]);
//            for (int j = 0; j < sum2.length; j++) sum2[j] += 100 * Math.abs(trueParamValues[j] - imParamValues2[j]) / Math.abs(trueParamValues[j]);

            chisqSum1 += est1.getChiSquare();
//            chisqSum2 += est2.getChiSquare();
            pSum1 += est1.getPValue();
//            pSum2 += est2.getPValue();
        }

        for (int i = 0; i < sum1.length; i++) sum1[i] /= runs;
        for (int i = 0; i < sum2.length; i++) sum2[i] /= runs;

        double chisqAvg1 = chisqSum1 / runs;
        double chisqAvg2 = chisqSum2 / runs;
        double pValuesSum1 = pSum1 / runs;
        double pValueSum2 = pSum2 / runs;

        NumberFormat nf2 = new DecimalFormat("0.0000");

        System.out.println("\n=====POWELL=====");

        System.out.println("avg chi square = " + nf2.format(chisqAvg1));
        System.out.println("avg p = " + nf2.format(pValuesSum1));

        System.out.println();
        List<Parameter> parameters = pm.getParameters();
        NumberFormat nf = new DecimalFormat("0.0");

        System.out.println("Avg 100 * abs(p_est - p_true) / abs(p_true))");

        for (int i = 0; i < sum1.length; i++) {
            Parameter parameter = parameters.get(i);
            System.out.println(nf.format(sum1[i])  + "% " + parameter.getName());
        }

        System.out.println("\n=====RANDOM=====");

        System.out.println("avg chi square = " + nf2.format(chisqAvg2));
        System.out.println("avg p = " + nf2.format(pValueSum2));

        System.out.println();
        System.out.println("Avg 100 * abs(p_est - p_true) / abs(p_true))");

        for (int i = 0; i < sum1.length; i++) {
            Parameter parameter = parameters.get(i);
            System.out.println(nf.format(sum2[i])  + "% " + parameter.getName());
        }

    }

//    public void test4() {
//        Graph graph = DataGraphUtils.randomDag(10, 10, false);
//        SemPm pm = new SemPm(graph);
//        SemIm im = new SemIm(pm);
//        DataSet dataSet = im.simulateData(1000, false);
//        Ges search = new Ges(dataSet);
//        Graph pattern = search.search();
//        Graph dag = SearchGraphUtils.dagFromPattern(pattern);
//        SemPm pm2 = new SemPm(dag);
//        SemEstimator estimator = new SemEstimator(dataSet, pm2);
//        SemIm estSem = estimator.estimate();
//        System.out.println(estSem);
//    }
//
//    public void test5() {
//        Dag graph = DataGraphUtils.randomDag(10, 10, false);
//        BayesPm pm = new BayesPm(graph);
//        BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);
//        DataSet dataSet = im.simulateData(1000, false);
//        Cpc search = new Cpc(new IndTestGSquare(dataSet, 0.05));
//        Graph pattern = search.search();
//        Dag dag = new Dag(SearchGraphUtils.dagFromPattern(pattern));
//        BayesPm pm2 = new BayesPm(dag);
//        MlBayesEstimator estimator = new MlBayesEstimator();
//        BayesIm estSem = estimator.estimate(pm2, dataSet);
//        System.out.println(estSem);
//
//    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestSemEstimator.class);
    }

    private static Graph constructGraph0() {
        Graph graph = new EdgeListGraph();

        Node x1 = new GraphNode("empcur");
        Node x2 = new GraphNode("self-eff");
        Node x3 = new GraphNode("depressed");
        Node x4 = new GraphNode("dadchild");
        Node x5 = new GraphNode("dadmom");
        Node x6 = new GraphNode("home");
        Node x7 = new GraphNode("negbeh");
        Node x8 = new GraphNode("coglang");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);
        graph.addNode(x6);
        graph.addNode(x7);
        graph.addNode(x8);

        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x5);
        graph.addDirectedEdge(x3, x6);
        graph.addDirectedEdge(x5, x4);
        graph.addDirectedEdge(x4, x6);
        graph.addDirectedEdge(x6, x7);
        graph.addDirectedEdge(x6, x8);

        return graph;
    }

    private static ICovarianceMatrix constructCovMatrix0() {
        String[] vars = new String[]{"empcur", "self-eff", "depressed",
                "dadchild", "dadmom", "home", "negbeh", "coglang"};
        double[][] arr = {{1.0}, {0.215, 1.0}, {-0.164, -0.472, 1.0},
                {0.1120, 0.079, -0.1570, 1.0},
                {0.034, 0.121, -0.184, 0.4070, 1.0},
                {0.101, 0.197, -0.190, 0.176, 0.12, 1.0},
                {0.071, -0.172, 0.206, -0.049, -0.084, -0.291, 1.0},
                {0.043, -0.038, -0.037, -0.062, 0.028, 0.166, -0.149, 1.0}};
//        TetradMatrix arr2 = TetradMatrix.instance(arr);
        return new CovarianceMatrix(DataUtils.createContinuousVariables(vars), new TetradMatrix(arr),
                173);
    }

    private Graph constructGraph1() {
        Graph graph = new EdgeListGraph();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x5 = new GraphNode("X5");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);

        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x3, x4);
        graph.addDirectedEdge(x1, x4);
        graph.addDirectedEdge(x4, x5);

        return graph;
    }

    private ICovarianceMatrix constructCovMatrix1() {
        String[] vars = new String[]{"X1", "X2", "X3", "X4", "X5"};
        double[][] arr = {{1.04408}, {0.80915, 1.55607},
                {0.89296, 1.67375, 2.87584},
                {2.23792, 2.68536, 3.94996, 7.78259},
                {1.17516, 1.36337, 1.99039, 4.04533, 3.14922}};

        double[][] m = MatrixUtils.convertLowerTriangleToSymmetric(arr);
        TetradMatrix m2 = new TetradMatrix(m);
        return new CovarianceMatrix(DataUtils.createContinuousVariables(vars), m2, 1000);
    }

    private Graph constructGraph2() {
        Graph graph = new EdgeListGraph();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x5 = new GraphNode("X5");
        Node x6 = new GraphNode("X6");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);
        graph.addNode(x6);

        graph.addDirectedEdge(x1, x2);
        graph.addDirectedEdge(x1, x5);
        graph.addDirectedEdge(x2, x5);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x4, x5);
        graph.addDirectedEdge(x4, x6);

        return graph;
    }

    public void testOptimizer2() {
        Graph graph = new Dag(GraphUtils.randomGraph(5, 0, 5, 30, 15, 15, false));
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet data = im.simulateData(1000, false);

        SemIm im2 = new SemIm(pm);
        im2.setDataSet(data);

        SemOptimizer opt = new SemOptimizerPowell();

        opt.optimize(im2);

        System.out.println(im);
    }

    public void testOptimizer3() {
        Graph graph = new Dag(GraphUtils.randomGraph(5, 0, 5, 30, 15, 15, false));
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet data = im.simulateData(1000, false);

        SemIm im2 = new SemIm(pm);
        im2.setDataSet(data);

        SemOptimizer opt = new SemOptimizerPowell();

        opt.optimize(im2);

        System.out.println(im);
    }

    private ICovarianceMatrix constructCovMatrix2() {
        String[] vars = new String[]{"X1", "X2", "X3", "X4", "X5", "X6"};

        double[][] arr = {{0.915736}, {0.636415, 1.446795},
                {0.596983, 1.289278, 2.202219},
                {-0.004218, -0.012488, 0.017168, 0.979152},
                {2.106086, 2.864279, 2.696651, 1.334353, 9.705821},
                {0.029125, -0.027681, -0.043718, 0.679363, 0.886868, 1.495396}};

        double[][] m = MatrixUtils.convertLowerTriangleToSymmetric(arr);
//        TetradMatrix m2 = TetradMatrix.instance(m);
        return new CovarianceMatrix(DataUtils.createContinuousVariables(vars), new TetradMatrix(m),
                1000);
    }
}






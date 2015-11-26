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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.sem.StandardizedSemIm;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.List;


/**
 * Tests Sem.
 *
 * @author Joseph Ramsey
 */
public class TestStandardizedSem extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestStandardizedSem(String name) {
        super(name);
    }

    // Test the code that standardizes a data set.
    public void test1() {
        SemGraph graph = new SemGraph(new Dag(GraphUtils.randomGraph(5, 0, 5, 30, 15, 15, false)));

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet dataSet = im.simulateData(1000, false);
        TetradMatrix _dataSet = dataSet.getDoubleData();
        _dataSet = DataUtils.standardizeData(_dataSet);
        DataSet dataSetStandardized = ColtDataSet.makeData(dataSet.getVariables(), _dataSet);

        System.out.println(DataUtils.cov(_dataSet));
        System.out.println(DataUtils.mean(_dataSet));

        SemEstimator estimator = new SemEstimator(dataSetStandardized, pm);
        SemIm imStandardized = estimator.estimate();

        System.out.println("Edge coef: " + imStandardized.getEdgeCoef());
        System.out.println("Error cover: " + imStandardized.getErrCovar());
        System.out.println("Variable means: " + new TetradVector(imStandardized.getMeans()));

        System.out.println("Original edge coefficients: " + imStandardized.getEdgeCoef());
        System.out.println("Original error covariances: " + imStandardized.getErrCovar());

        StandardizedSemIm sem = new StandardizedSemIm(im);

        System.out.println("Edge coefficients after construction: " + imStandardized.getEdgeCoef());
        System.out.println("Error covariances after construction: " + imStandardized.getErrCovar());


        assertTrue(isStandardized(sem));
    }

    public void test2() {
        RandomUtil.getInstance().setSeed(5729384723L);

        SemGraph graph = new SemGraph();

        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        Node x3 = new ContinuousVariable("X3");
        Node x4 = new ContinuousVariable("X4");
        Node x5 = new ContinuousVariable("X5");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);
        graph.addNode(x5);

        graph.setShowErrorTerms(true);

        graph.addDirectedEdge(x1, x2);

        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x4, x3);

        graph.addDirectedEdge(x2, x4);
        graph.addDirectedEdge(x1, x4);
        graph.addDirectedEdge(x5, x4);


        System.out.println(graph);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        StandardizedSemIm sem = new StandardizedSemIm(im);

        System.out.println(sem);

        assertTrue(isStandardized(sem));
    }

    public void test3() {
        RandomUtil.getInstance().setSeed(582374923L);
        SemGraph graph = new SemGraph();

        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        Node x3 = new ContinuousVariable("X3");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);

        graph.setShowErrorTerms(true);

        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x1, x2);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        System.out.println(im);

        StandardizedSemIm sem = new StandardizedSemIm(im);

        System.out.println(sem);

        DataSet data = sem.simulateData(5000, false);

        System.out.println(sem.getVariableNodes());
        System.out.println(DataUtils.cov(data.getDoubleData()));

        System.out.println(sem.getCoefficientRange(x1, x2));

        assertFalse(sem.setEdgeCoefficient(x1, x2, 1.2));
        assertFalse(sem.setEdgeCoefficient(x1, x2, 1.5));
        assertTrue(sem.setEdgeCoefficient(x1, x2, .5));
        assertTrue(sem.setEdgeCoefficient(x1, x3, -.1));

        System.out.println(sem);

        assertTrue(isStandardized(sem));
    }

    // This tests what the user is going to try to do in the GUI.
    public void test4() {
        SemGraph graph = new SemGraph(new Dag(GraphUtils.randomGraph(10, 0, 10, 30, 15, 15, false)));
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        StandardizedSemIm sem = new StandardizedSemIm(im);

        for (int i = 0; i < 20; i++) {
            List<Edge> edges = new ArrayList<Edge>(graph.getEdges());
            RandomUtil random = RandomUtil.getInstance();
            int index = random.nextInt(edges.size());
            Edge edge = edges.get(index);

            Node a = edge.getNode1();
            Node b = edge.getNode2();

            StandardizedSemIm.ParameterRange range = sem.getCoefficientRange(a, b);
            double high = range.getHigh();
            double low = range.getLow();

            double coef = low + random.nextDouble() * (high - low);
            assertTrue(sem.setEdgeCoefficient(a, b, coef));

            coef = high + random.nextDouble() * (high - low);
            assertFalse(sem.setEdgeCoefficient(a, b, coef));

            coef = low - random.nextDouble() * (high - low);
            assertFalse(sem.setEdgeCoefficient(a, b, coef));
        }
    }

    public void test5() {
        RandomUtil.getInstance().setSeed(582374923L);
        SemGraph graph = new SemGraph();
        graph.setShowErrorTerms(true);

        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        Node x3 = new ContinuousVariable("X3");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);

        graph.setShowErrorTerms(true);

        Node ex1 = graph.getExogenous(x1);
        Node ex2 = graph.getExogenous(x2);
        Node ex3 = graph.getExogenous(x3);

        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x2, x3);
//        graph.addDirectedEdge(x1, x2);
//        graph.addBidirectedEdge(ex1, ex2);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

//        System.out.println(im);

        DataSet dataSet = im.simulateDataRecursive(1000, false);
        TetradMatrix _dataSet = dataSet.getDoubleData();
        _dataSet = DataUtils.standardizeData(_dataSet);
        DataSet dataSetStandardized = ColtDataSet.makeData(dataSet.getVariables(), _dataSet);


        SemEstimator estimator = new SemEstimator(dataSetStandardized, im.getSemPm());
        SemIm imStandardized = estimator.estimate();

//        System.out.println(imStandardized);

        StandardizedSemIm sem = new StandardizedSemIm(im);
//        sem.setErrorCovariance(ex1, ex2, -.24);
        System.out.println(sem);
        assertTrue(isStandardized(sem));
    }

    public void test6() {
//        RandomUtil.getInstance().setSeed(582374923L);
        SemGraph graph = new SemGraph();
        graph.setShowErrorTerms(true);

        Node x1 = new ContinuousVariable("X1");
        Node x2 = new ContinuousVariable("X2");
        Node x3 = new ContinuousVariable("X3");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);

        graph.setShowErrorTerms(true);

        Node ex1 = graph.getExogenous(x1);
        Node ex2 = graph.getExogenous(x2);
        Node ex3 = graph.getExogenous(x3);

        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x1, x2);
        graph.addBidirectedEdge(ex1, ex2);

//        List<List<Node>> treks = DataGraphUtils.treksIncludingBidirected(graph, x1, x3);
//
//        for (List<Node> trek : treks) {
//            System.out.println(trek);
//        }

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet dataSet = im.simulateDataRecursive(1000, false);
        System.out.println("im " + im.getErrCovar(x1, x2));

//        System.out.println("adjusted " + im.getErrCovar(x1, x2) * Math.sqrt(im.getVariance(x1) * im.getVariance(x2)));

        TetradMatrix _dataSet = dataSet.getDoubleData();
        _dataSet = DataUtils.standardizeData(_dataSet);
        DataSet dataSetStandardized = ColtDataSet.makeData(dataSet.getVariables(), _dataSet);

        SemEstimator estimator = new SemEstimator(dataSetStandardized, im.getSemPm());
        SemIm imStandardized = estimator.estimate();

        System.out.println(imStandardized);

        System.out.println("im st " + imStandardized.getErrCovar(x1, x2));
        StandardizedSemIm sem = new StandardizedSemIm(im);

//        sem.setErrorCovariance(x1, x2, -.17);

        System.out.println("sem " + sem.getErrorCovariance(x1, x2));

        System.out.println(sem);

        assertTrue(isStandardized(sem));
    }

    // This tests what the user is going to try to do in the GUI.
    public void test7() {
        RandomUtil random = RandomUtil.getInstance();
        random.setSeed(9394929393L);

        SemGraph graph = new SemGraph(new Dag(GraphUtils.randomGraph(5, 0, 5, 30, 15, 15, false)));

        List<Node> nodes = graph.getNodes();
        int n1 = RandomUtil.getInstance().nextInt(nodes.size());
        int n2 = RandomUtil.getInstance().nextInt(nodes.size());

        while (n1 == n2) {
            n2 = RandomUtil.getInstance().nextInt(nodes.size());
        }

        Node node1 = nodes.get(n1);
        Node node2 = nodes.get(n2);
        Edge _edge = Edges.bidirectedEdge(node1, node2);
        System.out.println(_edge);
        graph.addEdge(_edge);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        StandardizedSemIm sem = new StandardizedSemIm(im);

        DataSet data3 = sem.simulateDataReducedForm(1000, false);
        System.out.println(new CovarianceMatrix(data3));

        graph.setShowErrorTerms(false);

         for (int i = 0; i < 1; i++) {
            for (Edge edge : graph.getEdges()) {
                Node a = edge.getNode1();
                Node b = edge.getNode2();

                if (Edges.isDirectedEdge(edge)) {
                    double initial = sem.getEdgeCoefficient(a, b);
                    StandardizedSemIm.ParameterRange range = sem.getCoefficientRange(a, b);
                    assertEquals(initial, sem.getEdgeCoefficient(a, b));

                    double low = range.getLow();
                    double high = range.getHigh();

                    double _coef = sem.getEdgeCoefficient(a, b);

                    double coef = low + random.nextDouble() * (high - low);
                    assertTrue(sem.setEdgeCoefficient(a, b, coef));

                    sem.setEdgeCoefficient(a, b, _coef);

                    coef = high + random.nextDouble() * (high - low);
                    assertFalse(sem.setEdgeCoefficient(a, b, coef));

                    coef = low - random.nextDouble() * (high - low);
                    assertFalse(sem.setEdgeCoefficient(a, b, coef));
                } else if (Edges.isBidirectedEdge(edge)) {
                    System.out.println("covariance = " + sem.getErrorCovariance(a, b));
                    sem.setErrorCovariance(node1, node2, .15);

                    assertTrue(isStandardized(sem));

                    StandardizedSemIm.ParameterRange range2 = sem.getCovarianceRange(a, b);
                    System.out.println(range2);

                    double low = range2.getLow();
                    double high = range2.getHigh();

                    if (low == Double.NEGATIVE_INFINITY) low = -10000;
                    if (high == Double.POSITIVE_INFINITY) high = 10000;

                    double _coef = sem.getErrorCovariance(a, b);

                    double coef = low + random.nextDouble() * (high - low);
                    System.out.println("Picked " + coef);
                    assertTrue(sem.setErrorCovariance(a, b, coef));

                    sem.setErrorCovariance(a, b, _coef);

                    if (high != 10000) {
                        coef = high + random.nextDouble() * (high - low);
                        assertFalse(sem.setErrorCovariance(a, b, coef));
                    }

                    if (low != -10000) {
                        coef = low - random.nextDouble() * (high - low);
                        assertFalse(sem.setErrorCovariance(a, b, coef));
                    }
                }
            }
        }
    }

    public void rtest8() {
//        RandomUtil.getInstance().setSeed(2958442283L);
        SemGraph graph = new SemGraph();

        Node x = new ContinuousVariable("X");
        Node y = new ContinuousVariable("Y");
        Node z = new ContinuousVariable("Z");

        graph.addNode(x);
        graph.addNode(y);
        graph.addNode(z);

        graph.addDirectedEdge(x, y);
        graph.addBidirectedEdge(x, y);
        graph.addDirectedEdge(x, z);
        graph.addDirectedEdge(y, z);

        graph.setShowErrorTerms(true);

        SemPm semPm = new SemPm(graph);
        SemIm semIm = new SemIm(semPm);

//        semIm.setEdgeCoef(x, z, .4971);
//        semIm.setEdgeCoef(y, z, .3774);
//        semIm.setEdgeCoef(x, y, -.2502);
//
//        semIm.setErrCovar(x, y, .2654);
////        System.out.println("*** " + semIm.getErrCovar(graph.getExogenous(x), graph.getExogenous(y)));
//
//        semIm.setErrCovar(x, 1);
//        semIm.setErrCovar(y, 1.06);
//        semIm.setErrCovar(z, .6051);

//        DataSet dataSet = semIm.simulateDataReducedForm(1000, false);
//        dataSet = ColtDataSet.makeContinuousData(dataSet.getVariables(), DataUtils.standardizeData(dataSet.getDoubleData()));
//        semIm = new SemEstimator(dataSet, semPm).estimate();

        System.out.println(semIm);

//        System.out.println(semIm.getImplCovar());

        StandardizedSemIm sem = new StandardizedSemIm(semIm, StandardizedSemIm.Initialization.CALCULATE_FROM_SEM);

//        sem.setErrorCovariance(x, y, 0.8);

        System.out.println(sem);

        DataSet data = semIm.simulateDataCholesky(1000, false);
        data = ColtDataSet.makeContinuousData(data.getVariables(), DataUtils.standardizeData(data.getDoubleData()));
        SemEstimator estimator = new SemEstimator(data, semPm);
        semIm = estimator.estimate();

        DataSet data2 = semIm.simulateDataReducedForm(1000, false);
        System.out.println(new CovarianceMatrix(data2));

        DataSet data3 = sem.simulateDataReducedForm(1000, false);
        System.out.println(new CovarianceMatrix(data3));

        StandardizedSemIm.ParameterRange range2 = sem.getCovarianceRange(x, y);
        System.out.println(range2);

        double high = range2.getHigh();
        double low = range2.getLow();

        if (high == Double.POSITIVE_INFINITY) high = 1000;
        if (low == Double.NEGATIVE_INFINITY) low = -1000;

        double coef = low + RandomUtil.getInstance().nextDouble() * (high - low);
        System.out.println("Picked " + coef);
        assertTrue(sem.setErrorCovariance(x, y, coef));

//        assertTrue(sem.setErrorCovariance(x, y, 1));
        System.out.println(new CovarianceMatrix(data3));

        assert (isStandardized(sem));
    }

    private boolean isStandardized(StandardizedSemIm sem) {
        DataSet dataSet = sem.simulateData(5000, false);

        TetradMatrix _dataSet = dataSet.getDoubleData();

        TetradMatrix cov = DataUtils.cov(_dataSet);
        TetradVector means = DataUtils.mean(_dataSet);

//        System.out.println(sem.edgeCoef());
//        System.out.println(cov);

        for (int i = 0; i < cov.rows(); i++) {
            if (!(Math.abs(cov.get(i, i) - 1) < .1)) {
                System.out.println("Variable " + sem.getErrorNodes().get(i) + " variance not equal to 1: " +
                        cov.get(i, i));
                return false;
            }

            if (!(Math.abs(means.get(i)) < .1)) {
                System.out.println("Mean not equal to 0:" + means.get(i));
                return false;
            }
        }


        return true;
    }


    public void testSliderValues() {
        int n = 100;

        for (int i = 0; i <= 100; i++) {
            assertEquals(i, sliderToSlider(i, -5, 5, n));
        }

        for (int i = 0; i <= 100; i++) {
            assertEquals(i, sliderToSlider(i, -5, Double.POSITIVE_INFINITY, n));
        }

        for (int i = 0; i <= 100; i++) {
            assertEquals(i, sliderToSlider(i, Double.NEGATIVE_INFINITY, 5, n));
        }

        for (int i = 0; i <= 100; i++) {
            assertEquals(i, sliderToSlider(i, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, n));
        }


    }

    private int sliderToSlider(int slider, double min, double max, int n) {
        double value = sliderToValue(slider, min, max, n);
        return valueToSlider(value, min, max, n);
    }


    private double sliderToValue(int slider, double min, double max, int n) {
        double f;
        if (min != Double.NEGATIVE_INFINITY && max != Double.POSITIVE_INFINITY) {
            f = min + ((double) slider / n) * (max - min);
        }
        else if (min != Double.NEGATIVE_INFINITY) {
            f = min + Math.tan(((double) slider / n) * (Math.PI / 2));
        }
        else if (max != Double.POSITIVE_INFINITY) {
            f = max + Math.tan(-(((double) n - slider) / n) * (Math.PI / 2));
//            System.out.println("slider = " + slider + " min = " + min + " max = " + max + "  f = " + f);
        }
        else {
            f = Math.tan(-Math.PI / 2 + ((double) slider / n) * Math.PI);
        }
        return f;
    }

    private int valueToSlider(double value, double min, double max, int n) {
        double x;
        if (min != Double.NEGATIVE_INFINITY && max != Double.POSITIVE_INFINITY) {
            x = n * (value - min) / (max - min);
        }
        else if (min != Double.NEGATIVE_INFINITY) {
            x = (2. * n) / Math.PI * Math.atan(value - min);
        }
        else if (max != Double.POSITIVE_INFINITY) {
            x = n + (2. * n) / Math.PI * Math.atan(value - max);
//            System.out.println("value = " + value + " x = " + x);
        }
        else {
            x = (n / Math.PI) * (Math.atan(value) + Math.PI / 2);
        }

        int slider = (int) Math.round(x);
        if (slider > 100) slider = 100;
        if (slider < 0) slider = 0;
        return slider;
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestStandardizedSem.class);
    }
}



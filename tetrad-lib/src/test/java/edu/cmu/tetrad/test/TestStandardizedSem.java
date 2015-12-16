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
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * @author Joseph Ramsey
 */
public class TestStandardizedSem {

    // Test the code that standardizes a data set.
    @Test
    public void test1() {
        List<Node> nodes = new ArrayList<Node>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        SemGraph graph = new SemGraph(new Dag(GraphUtils.randomGraph(nodes, 0, 5,
                30, 15, 15, false)));

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet dataSet = im.simulateData(1000, false);
        TetradMatrix _dataSet = dataSet.getDoubleData();
        _dataSet = DataUtils.standardizeData(_dataSet);
        DataSet dataSetStandardized = ColtDataSet.makeData(dataSet.getVariables(), _dataSet);

        DataUtils.cov(_dataSet);
        DataUtils.mean(_dataSet);

        SemEstimator estimator = new SemEstimator(dataSetStandardized, pm);
        SemIm imStandardized = estimator.estimate();

        imStandardized.getEdgeCoef();
        imStandardized.getErrCovar();
        new TetradVector(imStandardized.getMeans());

        imStandardized.getEdgeCoef();
        imStandardized.getErrCovar();

        StandardizedSemIm sem = new StandardizedSemIm(im);

        imStandardized.getEdgeCoef();
        imStandardized.getErrCovar();

        assertTrue(isStandardized(sem));
    }

    @Test
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

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        StandardizedSemIm sem = new StandardizedSemIm(im);

        assertTrue(isStandardized(sem));
    }

    @Test
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

        StandardizedSemIm sem = new StandardizedSemIm(im);

        DataSet data = sem.simulateData(5000, false);

        assertFalse(sem.setEdgeCoefficient(x1, x2, 1.2));
        assertFalse(sem.setEdgeCoefficient(x1, x2, 1.5));
        assertTrue(sem.setEdgeCoefficient(x1, x2, .5));
        assertTrue(sem.setEdgeCoefficient(x1, x3, -.1));

        assertTrue(isStandardized(sem));
    }

    // This tests what the user is going to try to do in the GUI.
    @Test
    public void test4() {
        List<Node> nodes = new ArrayList<Node>();

        for (int i1 = 0; i1 < 10; i1++) {
            nodes.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        SemGraph graph = new SemGraph(new Dag(GraphUtils.randomGraph(nodes, 0, 10,
                30, 15, 15, false)));
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

    @Test
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

        DataSet dataSet = im.simulateDataRecursive(1000, false);
        TetradMatrix _dataSet = dataSet.getDoubleData();
        _dataSet = DataUtils.standardizeData(_dataSet);
        DataSet dataSetStandardized = ColtDataSet.makeData(dataSet.getVariables(), _dataSet);


        SemEstimator estimator = new SemEstimator(dataSetStandardized, im.getSemPm());
        SemIm imStandardized = estimator.estimate();

        StandardizedSemIm sem = new StandardizedSemIm(im);
//        sem.setErrorCovariance(ex1, ex2, -.24);
        assertTrue(isStandardized(sem));
    }

    @Test
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

        TetradMatrix _dataSet = dataSet.getDoubleData();
        _dataSet = DataUtils.standardizeData(_dataSet);
        DataSet dataSetStandardized = ColtDataSet.makeData(dataSet.getVariables(), _dataSet);

        SemEstimator estimator = new SemEstimator(dataSetStandardized, im.getSemPm());
        SemIm imStandardized = estimator.estimate();

        StandardizedSemIm sem = new StandardizedSemIm(im);

        assertTrue(isStandardized(sem));
    }

    // This tests what the user is going to try to do in the GUI.
    @Test
    public void test7() {
        RandomUtil random = RandomUtil.getInstance();
        random.setSeed(9394929393L);

        List<Node> nodes1 = new ArrayList<Node>();

        for (int i1 = 0; i1 < 5; i1++) {
            nodes1.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        SemGraph graph = new SemGraph(new Dag(GraphUtils.randomGraph(nodes1, 0, 5,
                30, 15, 15, false)));

        List<Node> nodes = graph.getNodes();
        int n1 = RandomUtil.getInstance().nextInt(nodes.size());
        int n2 = RandomUtil.getInstance().nextInt(nodes.size());

        while (n1 == n2) {
            n2 = RandomUtil.getInstance().nextInt(nodes.size());
        }

        Node node1 = nodes.get(n1);
        Node node2 = nodes.get(n2);
        Edge _edge = Edges.bidirectedEdge(node1, node2);
        graph.addEdge(_edge);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        StandardizedSemIm sem = new StandardizedSemIm(im);

        DataSet data3 = sem.simulateDataReducedForm(1000, false);

        graph.setShowErrorTerms(false);

         for (int i = 0; i < 1; i++) {
            for (Edge edge : graph.getEdges()) {
                Node a = edge.getNode1();
                Node b = edge.getNode2();

                if (Edges.isDirectedEdge(edge)) {
                    double initial = sem.getEdgeCoefficient(a, b);
                    StandardizedSemIm.ParameterRange range = sem.getCoefficientRange(a, b);
                    assertEquals(initial, sem.getEdgeCoefficient(a, b), 0.1);

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
                    sem.setErrorCovariance(node1, node2, .15);

                    assertTrue(isStandardized(sem));

                    StandardizedSemIm.ParameterRange range2 = sem.getCovarianceRange(a, b);

                    double low = range2.getLow();
                    double high = range2.getHigh();

                    if (low == Double.NEGATIVE_INFINITY) low = -10000;
                    if (high == Double.POSITIVE_INFINITY) high = 10000;

                    double _coef = sem.getErrorCovariance(a, b);

                    double coef = low + random.nextDouble() * (high - low);
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

    @Test
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
        StandardizedSemIm sem = new StandardizedSemIm(semIm, StandardizedSemIm.Initialization.CALCULATE_FROM_SEM);

        DataSet data = semIm.simulateDataCholesky(1000, false);
        data = ColtDataSet.makeContinuousData(data.getVariables(), DataUtils.standardizeData(data.getDoubleData()));
        SemEstimator estimator = new SemEstimator(data, semPm);
        semIm = estimator.estimate();

        DataSet data2 = semIm.simulateDataReducedForm(1000, false);

        DataSet data3 = sem.simulateDataReducedForm(1000, false);

        StandardizedSemIm.ParameterRange range2 = sem.getCovarianceRange(x, y);

        double high = range2.getHigh();
        double low = range2.getLow();

        if (high == Double.POSITIVE_INFINITY) high = 1000;
        if (low == Double.NEGATIVE_INFINITY) low = -1000;

        double coef = low + RandomUtil.getInstance().nextDouble() * (high - low);
        assertTrue(sem.setErrorCovariance(x, y, coef));

        assertTrue(isStandardized(sem));
    }

    private boolean isStandardized(StandardizedSemIm sem) {
        DataSet dataSet = sem.simulateData(5000, false);

        TetradMatrix _dataSet = dataSet.getDoubleData();

        TetradMatrix cov = DataUtils.cov(_dataSet);
        TetradVector means = DataUtils.mean(_dataSet);

        for (int i = 0; i < cov.rows(); i++) {
            if (!(Math.abs(cov.get(i, i) - 1) < .1)) {
                return false;
            }

            if (!(Math.abs(means.get(i)) < .1)) {
                return false;
            }
        }


        return true;
    }


    @Test
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
        }
        else {
            x = (n / Math.PI) * (Math.atan(value) + Math.PI / 2);
        }

        int slider = (int) Math.round(x);
        if (slider > 100) slider = 100;
        if (slider < 0) slider = 0;
        return slider;
    }
}



///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.sem.StandardizedSemIm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.util.FastMath;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;


/**
 * @author josephramsey
 */
public class TestStandardizedSem {

    // Test the code that standardizes a data set.
    @Test
    public void test1() {
        RandomUtil.getInstance().setSeed(1949993L);

        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        SemGraph graph = new SemGraph(new Dag(RandomGraph.randomGraph(nodes, 0, 5,
                30, 15, 15, false)));

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet dataSet = im.simulateData(1000, false);
        DataSet dataSetStandardized = DataTransforms.standardizeData(dataSet);
        Matrix _dataSet = dataSet.getDoubleData();

        DataUtils.cov(_dataSet);
        DataUtils.mean(_dataSet);

        SemEstimator estimator = new SemEstimator(dataSetStandardized, pm);
        SemIm imStandardized = estimator.estimate();

        imStandardized.getEdgeCoef();
        imStandardized.getErrCovar();
        new Vector(imStandardized.getMeans());

        imStandardized.getEdgeCoef();
        imStandardized.getErrCovar();

        StandardizedSemIm sem = new StandardizedSemIm(im, new Parameters());

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
        StandardizedSemIm sem = new StandardizedSemIm(im, new Parameters());

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

        StandardizedSemIm sem = new StandardizedSemIm(im, new Parameters());

        assertFalse(sem.setEdgeCoefficient(x1, x2, 1.2));
        assertFalse(sem.setEdgeCoefficient(x1, x2, 1.5));
        assertTrue(sem.setEdgeCoefficient(x1, x2, .5));
        assertTrue(sem.setEdgeCoefficient(x1, x3, -.1));

        assertTrue(isStandardized(sem));
    }

    // This tests what the user is going to try to do in the GUI.
    @Test
    public void test4() {
        RandomUtil.getInstance().setSeed(1949993L);

        List<Node> nodes = new ArrayList<>();

        for (int i1 = 0; i1 < 10; i1++) {
            nodes.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        SemGraph graph = new SemGraph(new Dag(RandomGraph.randomGraph(nodes, 0, 10,
                30, 15, 15, false)));
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        StandardizedSemIm sem = new StandardizedSemIm(im, new Parameters());

        for (int i = 0; i < 20; i++) {
            List<Edge> edges = new ArrayList<>(graph.getEdges());
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

        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x2, x3);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        StandardizedSemIm sem = new StandardizedSemIm(im, new Parameters());
        assertTrue(isStandardized(sem));
    }

    @Test
    public void test6() {
        RandomUtil.getInstance().setSeed(1949993L);

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

        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x1, x2);
        graph.addBidirectedEdge(ex1, ex2);

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        StandardizedSemIm sem = new StandardizedSemIm(im, new Parameters());

        assertTrue(isStandardized(sem));
    }

    // This tests what the user is going to try to do in the GUI.
//    @Test
    public void test7() {
        RandomUtil random = RandomUtil.getInstance();
        random.setSeed(1949993L);

        List<Node> nodes1 = new ArrayList<>();

        for (int i1 = 0; i1 < 5; i1++) {
            nodes1.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        SemGraph graph = new SemGraph(new Dag(RandomGraph.randomGraph(nodes1, 0, 5,
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
        StandardizedSemIm sem = new StandardizedSemIm(im, new Parameters());

        graph.setShowErrorTerms(false);

        for (int i = 0; i < 1; i++) {
            for (Edge edge : graph.getEdges()) {
                Node a = edge.getNode1();
                Node b = edge.getNode2();

                if (Edges.isDirectedEdge(edge)) {
                    double initial = sem.getEdgeCoef(a, b);
                    StandardizedSemIm.ParameterRange range = sem.getCoefficientRange(a, b);
                    assertEquals(initial, sem.getEdgeCoef(a, b), 0.1);

                    double low = range.getLow();
                    double high = range.getHigh();

                    double _coef = sem.getEdgeCoef(a, b);

                    double coef = low + random.nextDouble() * (high - low);
                    assertTrue(sem.setEdgeCoefficient(a, b, coef));

                    sem.setEdgeCoefficient(a, b, _coef);

                    coef = high + random.nextDouble() * (high - low);
                    assertFalse(sem.setEdgeCoefficient(a, b, coef));

                    coef = low - random.nextDouble() * (high - low);
                    assertFalse(sem.setEdgeCoefficient(a, b, coef));
                } else if (Edges.isBidirectedEdge(edge)) {
                    sem.setErrorCovariance(node1, node2, .15);

//                    assertTrue(isStandardized(sem));

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

    private boolean isStandardized(StandardizedSemIm sem) {
        Matrix cov = sem.getImplCovar();
        double[] means = sem.means();

        System.out.println("cov" + cov);

        for (int i = 0; i < cov.getNumRows(); i++) {
            if (!(FastMath.abs(cov.get(i, i) - 1) < .1)) {
                return false;
            }

            if (!(FastMath.abs(means[i]) < .1)) {
                return false;
            }
        }


        return true;
    }


    @Test
    public void testSliderValues() {
        final int n = 100;

        for (int i = 0; i <= 100; i++) {
            assertEquals(i, sliderToSlider(i, -5, 5));
        }

        for (int i = 0; i <= 100; i++) {
            assertEquals(i, sliderToSlider(i, -5, Double.POSITIVE_INFINITY));
        }

        for (int i = 0; i <= 100; i++) {
            assertEquals(i, sliderToSlider(i, Double.NEGATIVE_INFINITY, 5));
        }

        for (int i = 0; i <= 100; i++) {
            assertEquals(i, sliderToSlider(i, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));
        }


    }

    private int sliderToSlider(int slider, double min, double max) {
        double value = sliderToValue(slider, min, max, 100);
        return valueToSlider(value, min, max, 100);
    }


    private double sliderToValue(int slider, double min, double max, int n) {
        double f;
        if (min != Double.NEGATIVE_INFINITY && max != Double.POSITIVE_INFINITY) {
            f = min + ((double) slider / n) * (max - min);
        } else if (min != Double.NEGATIVE_INFINITY) {
            f = min + FastMath.tan(((double) slider / n) * (FastMath.PI / 2));
        } else if (max != Double.POSITIVE_INFINITY) {
            f = max + FastMath.tan(-(((double) n - slider) / n) * (FastMath.PI / 2));
        } else {
            f = FastMath.tan(-FastMath.PI / 2 + ((double) slider / n) * FastMath.PI);
        }
        return f;
    }

    private int valueToSlider(double value, double min, double max, int n) {
        double x;
        if (min != Double.NEGATIVE_INFINITY && max != Double.POSITIVE_INFINITY) {
            x = n * (value - min) / (max - min);
        } else if (min != Double.NEGATIVE_INFINITY) {
            x = (2. * n) / FastMath.PI * FastMath.atan(value - min);
        } else if (max != Double.POSITIVE_INFINITY) {
            x = n + (2. * n) / FastMath.PI * FastMath.atan(value - max);
        } else {
            x = (n / FastMath.PI) * (FastMath.atan(value) + FastMath.PI / 2);
        }

        int slider = (int) FastMath.round(x);
        if (slider > 100) slider = 100;
        if (slider < 0) slider = 0;
        return slider;
    }
}




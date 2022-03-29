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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
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
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;


/**
 * @author Joseph Ramsey
 */
public class TestStandardizedSem {

    // Test the code that standardizes a data set.
    @Test
    public void test1() {
        final List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        final SemGraph graph = new SemGraph(new Dag(GraphUtils.randomGraph(nodes, 0, 5,
                30, 15, 15, false)));

        final SemPm pm = new SemPm(graph);
        final SemIm im = new SemIm(pm);

        final DataSet dataSet = im.simulateData(1000, false);
        final DataSet dataSetStandardized = DataUtils.standardizeData(dataSet);
        final Matrix _dataSet = dataSet.getDoubleData();

        DataUtils.cov(_dataSet);
        DataUtils.mean(_dataSet);

        final SemEstimator estimator = new SemEstimator(dataSetStandardized, pm);
        final SemIm imStandardized = estimator.estimate();

        imStandardized.getEdgeCoef();
        imStandardized.getErrCovar();
        new Vector(imStandardized.getMeans());

        imStandardized.getEdgeCoef();
        imStandardized.getErrCovar();

        final StandardizedSemIm sem = new StandardizedSemIm(im, new Parameters());

        imStandardized.getEdgeCoef();
        imStandardized.getErrCovar();

        assertTrue(isStandardized(sem));
    }

    @Test
    public void test2() {
        RandomUtil.getInstance().setSeed(5729384723L);

        final SemGraph graph = new SemGraph();

        final Node x1 = new ContinuousVariable("X1");
        final Node x2 = new ContinuousVariable("X2");
        final Node x3 = new ContinuousVariable("X3");
        final Node x4 = new ContinuousVariable("X4");
        final Node x5 = new ContinuousVariable("X5");

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

        final SemPm pm = new SemPm(graph);
        final SemIm im = new SemIm(pm);
        final StandardizedSemIm sem = new StandardizedSemIm(im, new Parameters());

        assertTrue(isStandardized(sem));
    }

    @Test
    public void test3() {
        RandomUtil.getInstance().setSeed(582374923L);
        final SemGraph graph = new SemGraph();

        final Node x1 = new ContinuousVariable("X1");
        final Node x2 = new ContinuousVariable("X2");
        final Node x3 = new ContinuousVariable("X3");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);

        graph.setShowErrorTerms(true);

        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x1, x2);

        final SemPm pm = new SemPm(graph);
        final SemIm im = new SemIm(pm);

        final StandardizedSemIm sem = new StandardizedSemIm(im, new Parameters());

        assertFalse(sem.setEdgeCoefficient(x1, x2, 1.2));
        assertFalse(sem.setEdgeCoefficient(x1, x2, 1.5));
        assertTrue(sem.setEdgeCoefficient(x1, x2, .5));
        assertTrue(sem.setEdgeCoefficient(x1, x3, -.1));

        assertTrue(isStandardized(sem));
    }

    // This tests what the user is going to try to do in the GUI.
    @Test
    public void test4() {
        final List<Node> nodes = new ArrayList<>();

        for (int i1 = 0; i1 < 10; i1++) {
            nodes.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        final SemGraph graph = new SemGraph(new Dag(GraphUtils.randomGraph(nodes, 0, 10,
                30, 15, 15, false)));
        final SemPm pm = new SemPm(graph);
        final SemIm im = new SemIm(pm);
        final StandardizedSemIm sem = new StandardizedSemIm(im, new Parameters());

        for (int i = 0; i < 20; i++) {
            final List<Edge> edges = new ArrayList<>(graph.getEdges());
            final RandomUtil random = RandomUtil.getInstance();
            final int index = random.nextInt(edges.size());
            final Edge edge = edges.get(index);

            final Node a = edge.getNode1();
            final Node b = edge.getNode2();

            final StandardizedSemIm.ParameterRange range = sem.getCoefficientRange(a, b);
            final double high = range.getHigh();
            final double low = range.getLow();

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
        final SemGraph graph = new SemGraph();
        graph.setShowErrorTerms(true);

        final Node x1 = new ContinuousVariable("X1");
        final Node x2 = new ContinuousVariable("X2");
        final Node x3 = new ContinuousVariable("X3");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);

        graph.setShowErrorTerms(true);

        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x2, x3);

        final SemPm pm = new SemPm(graph);
        final SemIm im = new SemIm(pm);

        final StandardizedSemIm sem = new StandardizedSemIm(im, new Parameters());
        assertTrue(isStandardized(sem));
    }

    @Test
    public void test6() {
        final SemGraph graph = new SemGraph();
        graph.setShowErrorTerms(true);

        final Node x1 = new ContinuousVariable("X1");
        final Node x2 = new ContinuousVariable("X2");
        final Node x3 = new ContinuousVariable("X3");

        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);

        graph.setShowErrorTerms(true);

        final Node ex1 = graph.getExogenous(x1);
        final Node ex2 = graph.getExogenous(x2);

        graph.addDirectedEdge(x1, x3);
        graph.addDirectedEdge(x2, x3);
        graph.addDirectedEdge(x1, x2);
        graph.addBidirectedEdge(ex1, ex2);

        final SemPm pm = new SemPm(graph);
        final SemIm im = new SemIm(pm);

        final StandardizedSemIm sem = new StandardizedSemIm(im, new Parameters());

        assertTrue(isStandardized(sem));
    }

    // This tests what the user is going to try to do in the GUI.
    @Test
    public void test7() {
        final RandomUtil random = RandomUtil.getInstance();
        random.setSeed(9394929393L);

        final List<Node> nodes1 = new ArrayList<>();

        for (int i1 = 0; i1 < 5; i1++) {
            nodes1.add(new ContinuousVariable("X" + (i1 + 1)));
        }

        final SemGraph graph = new SemGraph(new Dag(GraphUtils.randomGraph(nodes1, 0, 5,
                30, 15, 15, false)));

        final List<Node> nodes = graph.getNodes();
        final int n1 = RandomUtil.getInstance().nextInt(nodes.size());
        int n2 = RandomUtil.getInstance().nextInt(nodes.size());

        while (n1 == n2) {
            n2 = RandomUtil.getInstance().nextInt(nodes.size());
        }

        final Node node1 = nodes.get(n1);
        final Node node2 = nodes.get(n2);
        final Edge _edge = Edges.bidirectedEdge(node1, node2);
        graph.addEdge(_edge);

        final SemPm pm = new SemPm(graph);
        final SemIm im = new SemIm(pm);
        final StandardizedSemIm sem = new StandardizedSemIm(im, new Parameters());

        graph.setShowErrorTerms(false);

        for (int i = 0; i < 1; i++) {
            for (final Edge edge : graph.getEdges()) {
                final Node a = edge.getNode1();
                final Node b = edge.getNode2();

                if (Edges.isDirectedEdge(edge)) {
                    final double initial = sem.getEdgeCoef(a, b);
                    final StandardizedSemIm.ParameterRange range = sem.getCoefficientRange(a, b);
                    assertEquals(initial, sem.getEdgeCoef(a, b), 0.1);

                    final double low = range.getLow();
                    final double high = range.getHigh();

                    final double _coef = sem.getEdgeCoef(a, b);

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

                    final StandardizedSemIm.ParameterRange range2 = sem.getCovarianceRange(a, b);

                    double low = range2.getLow();
                    double high = range2.getHigh();

                    if (low == Double.NEGATIVE_INFINITY) low = -10000;
                    if (high == Double.POSITIVE_INFINITY) high = 10000;

                    final double _coef = sem.getErrorCovariance(a, b);

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

    private boolean isStandardized(final StandardizedSemIm sem) {
        final Matrix cov = sem.getImplCovar();
        final double[] means = sem.means();

        System.out.println("cov" + cov);

        for (int i = 0; i < cov.rows(); i++) {
            if (!(Math.abs(cov.get(i, i) - 1) < .1)) {
                return false;
            }

            if (!(Math.abs(means[i]) < .1)) {
                return false;
            }
        }


        return true;
    }


    @Test
    public void testSliderValues() {
        final int n = 100;

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

    private int sliderToSlider(final int slider, final double min, final double max, final int n) {
        final double value = sliderToValue(slider, min, max, n);
        return valueToSlider(value, min, max, n);
    }


    private double sliderToValue(final int slider, final double min, final double max, final int n) {
        final double f;
        if (min != Double.NEGATIVE_INFINITY && max != Double.POSITIVE_INFINITY) {
            f = min + ((double) slider / n) * (max - min);
        } else if (min != Double.NEGATIVE_INFINITY) {
            f = min + Math.tan(((double) slider / n) * (Math.PI / 2));
        } else if (max != Double.POSITIVE_INFINITY) {
            f = max + Math.tan(-(((double) n - slider) / n) * (Math.PI / 2));
        } else {
            f = Math.tan(-Math.PI / 2 + ((double) slider / n) * Math.PI);
        }
        return f;
    }

    private int valueToSlider(final double value, final double min, final double max, final int n) {
        final double x;
        if (min != Double.NEGATIVE_INFINITY && max != Double.POSITIVE_INFINITY) {
            x = n * (value - min) / (max - min);
        } else if (min != Double.NEGATIVE_INFINITY) {
            x = (2. * n) / Math.PI * Math.atan(value - min);
        } else if (max != Double.POSITIVE_INFINITY) {
            x = n + (2. * n) / Math.PI * Math.atan(value - max);
        } else {
            x = (n / Math.PI) * (Math.atan(value) + Math.PI / 2);
        }

        int slider = (int) Math.round(x);
        if (slider > 100) slider = 100;
        if (slider < 0) slider = 0;
        return slider;
    }
}



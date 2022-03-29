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
import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.DeltaTetradTest;
import edu.cmu.tetrad.search.Tetrad;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * TODO: Some of these tests give answers different from Bollen now. Why?
 *
 * @author Joseph Ramsey
 */
public class TestDeltaTetradTest {

    @Test
    public void test4aIteratedPositives() {
        RandomUtil.getInstance().setSeed(482834823L);

        final int numTrials = 10;
        final double alpha = 0.2;
        final SemIm sem = getFigure4aSem();

        final int[] sampleSizes = new int[]{100, 500, 1000, 5000};

        final double[][] answers = {{.1, .5, .2, .1, .1, .1},
                {0.1, 0.6, 0.0, 0.0, 0.0, 0.0},
                {0.1, 0.7, 0.2, 0.2, 0.2, 0.2},
                {0.3, 0.7, 0.2, 0.1, 0.1, 0.1}};

        for (int i = 0; i < 4; i++) {
            System.out.println("i = " + i);
            final int sampleSize = sampleSizes[i];
            final int[] count = new int[6];

            for (int k = 0; k < numTrials; k++) {
                final DataSet data = sem.simulateData(sampleSize, false);
                final Node x1 = data.getVariable("x1");
                final Node x2 = data.getVariable("x2");
                final Node x3 = data.getVariable("x3");
                final Node x4 = data.getVariable("x4");

                final Tetrad t1234 = new Tetrad(x1, x2, x3, x4);
                final Tetrad t1342 = new Tetrad(x1, x3, x4, x2);
                final Tetrad t1423 = new Tetrad(x1, x4, x2, x3);

                final DeltaTetradTest test = new DeltaTetradTest(new CorrelationMatrix(data));

                final double p1 = test.getPValue(t1234);
                final double p2 = test.getPValue(t1342);
                final double p3 = test.getPValue(t1423);
                final double p4 = test.getPValue(t1234, t1342);
                final double p5 = test.getPValue(t1234, t1423);
                final double p6 = test.getPValue(t1342, t1423);

                if (p1 < alpha) count[0]++;
                if (p2 < alpha) count[1]++;
                if (p3 < alpha) count[2]++;
                if (p4 < alpha) count[3]++;
                if (p5 < alpha) count[4]++;
                if (p6 < alpha) count[5]++;
            }

            final double[] _answer = new double[6];

            for (int j = 0; j < 6; j++) {
                final double v = count[j] / (double) numTrials;
                _answer[j] = v;
            }

//            System.out.println(MatrixUtils.toString(_answer));
//            System.out.println(MatrixUtils.toString(answers[i]));

//            assertTrue(Arrays.equals(_answer, answers[i]));
        }
    }

    // Bollen and Ting, Confirmatory Tetrad Analysis, p. 164 Sympathy and Anger.

    @Test
    public void testBollenExample1() {
        final CovarianceMatrix cov = getBollenExample1Data();
        final List<Node> variables = cov.getVariables();

        final Node v1 = variables.get(0);
        final Node v2 = variables.get(1);
        final Node v3 = variables.get(2);
        final Node v4 = variables.get(3);
        final Node v5 = variables.get(4);
        final Node v6 = variables.get(5);

        final Tetrad t1 = new Tetrad(v1, v2, v3, v4);
        final Tetrad t2 = new Tetrad(v1, v2, v3, v5);
        final Tetrad t3 = new Tetrad(v1, v2, v3, v6);
        final Tetrad t4 = new Tetrad(v1, v4, v5, v6);
        final Tetrad t5 = new Tetrad(v1, v4, v2, v3);
        final Tetrad t6 = new Tetrad(v1, v5, v2, v3);
        final Tetrad t7 = new Tetrad(v1, v6, v2, v3);
        final Tetrad t8 = new Tetrad(v1, v6, v4, v5);

        final DeltaTetradTest test = new DeltaTetradTest(cov);
//        DeltaTetradTest test = new DeltaTetradTest(new CorrelationMatrix(cov));

        final double chiSq = test.calcChiSquare(t1, t2, t3, t4, t5, t6, t7, t8);
        final double pValue = test.getPValue();

        // They get chi square = 6.71 p = .57 8 df but using the raw data which they don't provide here.
        // Just using the covariance matrix provided, I get chi square = 8.46, p = 0.39, df = 8.

        assertEquals(11.42, chiSq, 0.01);
        assertEquals(0.18, pValue, 0.01);
    }

    // Bollen and Ting p. 167 (Confirmatory Tetrad Analysis). Union Sentiment.

    @Test
    public void testBollenExample2() {
        final CovarianceMatrix cov = getBollenExample2Data();
        final List<Node> variables = cov.getVariables();

        final Node y1 = variables.get(0);
        final Node y2 = variables.get(1);
        final Node y3 = variables.get(2);
        final Node x1 = variables.get(3);
        final Node x2 = variables.get(4);

        final Tetrad t1 = new Tetrad(y1, x1, x2, y2);

        final DeltaTetradTest test = new DeltaTetradTest(cov);

        final double chiSq = test.calcChiSquare(t1);
        final double pValue = test.getPValue();

        assertEquals(.68, chiSq, 0.01);
        assertEquals(0.40, pValue, 0.01);

        // They get chi square = .73  p = .39  df = 1
    }

    // Bollen 2000 A Tetrad Test for Causal Indicators, p. 13.

    @Test
    public void testBollenSimulationExample() {
        final CovarianceMatrix cov = getBollenSimulationExampleData();

        final List<Node> variables = cov.getVariables();

        final Node y1 = variables.get(0);
        final Node y2 = variables.get(1);
        final Node y3 = variables.get(2);
        final Node y4 = variables.get(3);
        final Node y5 = variables.get(4);

        final Tetrad t1 = new Tetrad(y1, y2, y3, y4);
        final Tetrad t2 = new Tetrad(y1, y2, y4, y3);
        final Tetrad t3 = new Tetrad(y1, y3, y4, y2);
        final Tetrad t4 = new Tetrad(y1, y2, y3, y5);
        final Tetrad t5 = new Tetrad(y1, y2, y5, y3);
        final Tetrad t6 = new Tetrad(y1, y3, y5, y2);
        final Tetrad t7 = new Tetrad(y1, y2, y4, y5);
        final Tetrad t8 = new Tetrad(y1, y2, y5, y4);
        final Tetrad t9 = new Tetrad(y1, y4, y5, y2);
        final Tetrad t10 = new Tetrad(y1, y3, y4, y5);
        final Tetrad t11 = new Tetrad(y1, y3, y5, y4);
        final Tetrad t12 = new Tetrad(y1, y4, y5, y3);
        final Tetrad t13 = new Tetrad(y2, y3, y4, y5);
        final Tetrad t14 = new Tetrad(y2, y3, y5, y4);
        final Tetrad t15 = new Tetrad(y2, y4, y5, y3);

        final Tetrad[] tetrads = new Tetrad[]{t1, t2, t3, t4};

        final DeltaTetradTest test = new DeltaTetradTest(cov);

        double chiSq = test.calcChiSquare(tetrads[0]);
        double pValue = test.getPValue();

        assertEquals(58.1, chiSq, 0.1);
        assertEquals(2.46E-14, pValue, .1E-14);

        final Tetrad[] independentTetrads = new Tetrad[]{t1, t2, t4, t6, t10};

        chiSq = test.calcChiSquare(independentTetrads[0]);
        pValue = test.getPValue();

        assertEquals(58.1, chiSq, 0.1);
        assertEquals(2.46E-14, pValue, 0.1E-14);

        {
            chiSq = test.calcChiSquare(independentTetrads);
            pValue = test.getPValue();

            assertEquals(89.34, chiSq, 0.01);
            assertEquals(0.0, pValue, 0.01);
        }

        // They get chsq = 64.13 and so do I.
    }

    @Test
    public void test3() {
        final SemPm pm = makePm();
        final DataSet data = new SemIm(pm).simulateData(1000, false);

        final CovarianceMatrix cov = new CovarianceMatrix(data);

        final List<Node> variables = data.getVariables();
        final Node x1 = variables.get(0);
        final Node x2 = variables.get(1);
        final Node x3 = variables.get(2);
        final Node x4 = variables.get(3);
        final Node x5 = variables.get(4);

        final Tetrad t1234 = new Tetrad(x1, x2, x3, x4);
        final Tetrad t1342 = new Tetrad(x1, x3, x4, x2);
        final Tetrad t1423 = new Tetrad(x1, x4, x2, x3);

        final DeltaTetradTest test1 = new DeltaTetradTest(data);
//        DeltaTetradTest test = new DeltaTetradTest(new CorrelationMatrix(cov));

        final double chiSq1 = test1.calcChiSquare(t1234, t1342);

        final DeltaTetradTest test2 = new DeltaTetradTest(cov);
//        DeltaTetradTest test = new DeltaTetradTest(new CorrelationMatrix(cov));

        final double chiSq2 = test2.calcChiSquare(t1234, t1342);
    }

    private SemPm makePm() {
        final List<Node> variableNodes = new ArrayList<>();
        final ContinuousVariable x1 = new ContinuousVariable("X1");
        final ContinuousVariable x2 = new ContinuousVariable("X2");
        final ContinuousVariable x3 = new ContinuousVariable("X3");
        final ContinuousVariable x4 = new ContinuousVariable("X4");
        final ContinuousVariable x5 = new ContinuousVariable("X5");

        variableNodes.add(x1);
        variableNodes.add(x2);
        variableNodes.add(x3);
        variableNodes.add(x4);
        variableNodes.add(x5);

        final Graph _graph = new EdgeListGraph(variableNodes);
        final SemGraph graph = new SemGraph(_graph);
        graph.addDirectedEdge(x5, x1);
        graph.addDirectedEdge(x5, x2);
        graph.addDirectedEdge(x5, x3);
        graph.addDirectedEdge(x5, x4);

        return new SemPm(graph);
    }

    private SemIm getFigure4aSem() {
        final Graph graph = new EdgeListGraph();

        final Node xi1 = new GraphNode("xi1");
        final Node x1 = new GraphNode("x1");
        final Node x2 = new GraphNode("x2");
        final Node x3 = new GraphNode("x3");
        final Node x4 = new GraphNode("x4");

        graph.addNode(xi1);
        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);

        graph.addDirectedEdge(xi1, x1);
        graph.addDirectedEdge(xi1, x2);
        graph.addDirectedEdge(xi1, x3);
        graph.addDirectedEdge(xi1, x4);

        final SemPm pm = new SemPm(graph);

        final Parameters params = new Parameters();
//        params.setCoefRange(0.3, 0.8);

        return new SemIm(pm, params);
    }

    private SemIm getFigure4bSem() {
        final Graph graph = new EdgeListGraph();

        final Node xi1 = new GraphNode("xi1");
        final Node xi2 = new GraphNode("xi2");
        final Node x1 = new GraphNode("x1");
        final Node x2 = new GraphNode("x2");
        final Node x3 = new GraphNode("x3");
        final Node x4 = new GraphNode("x4");

        graph.addNode(xi1);
        graph.addNode(xi2);
        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);

        graph.addDirectedEdge(xi1, x1);
        graph.addDirectedEdge(xi1, x2);
        graph.addDirectedEdge(xi2, x3);
        graph.addDirectedEdge(xi2, x4);
        graph.addDirectedEdge(xi1, xi2);

        final SemPm pm = new SemPm(graph);
        return new SemIm(pm);
    }

    private CovarianceMatrix getBollenExample1Data() {

        // Sympathy and anger, p. 164.

        final double[][] d = new double[][]{
                {6.982},
                {4.686, 6.047},
                {4.335, 3.307, 5.037},
                {-2.294, -1.453, -1.979, 5.569},
                {-2.209, -1.262, -1.738, 3.931, 5.328},
                {-1.671, -1.401, -1.564, 3.915, 3.601, 4.977}
        };

        final Node v1 = new ContinuousVariable("v1");
        final Node v2 = new ContinuousVariable("v2");
        final Node v3 = new ContinuousVariable("v3");
        final Node v4 = new ContinuousVariable("v4");
        final Node v5 = new ContinuousVariable("v5");
        final Node v6 = new ContinuousVariable("v6");

        final List<Node> nodes = new ArrayList<>();
        nodes.add(v1);
        nodes.add(v2);
        nodes.add(v3);
        nodes.add(v4);
        nodes.add(v5);
        nodes.add(v6);

        final Matrix matrix = new Matrix(6, 6);

        for (int i = 0; i < 6; i++) {
            for (int j = 0; j <= i; j++) {
                matrix.set(i, j, d[i][j]);
                matrix.set(j, i, d[i][j]);
            }
        }

        return new CovarianceMatrix(nodes, matrix, 138);
    }

    private CovarianceMatrix getBollenExample2Data() {

        // Union sentiment.

        final double[][] d = new double[][]{
                {14.610},
                {-5.250, 11.017},
                {-8.057, 11.087, 31.971},
                {-0.482, 0.677, 1.559, 1.021},
                {-18.857, 17.861, 28.250, 7.139, 215.662},
        };

        final Node y1 = new ContinuousVariable("y1");
        final Node y2 = new ContinuousVariable("y2");
        final Node y3 = new ContinuousVariable("y3");
        final Node x1 = new ContinuousVariable("x1");
        final Node x2 = new ContinuousVariable("x2");

        final List<Node> nodes = new ArrayList<>();
        nodes.add(y1);
        nodes.add(y2);
        nodes.add(y3);
        nodes.add(x1);
        nodes.add(x2);

        final Matrix matrix = new Matrix(5, 5);

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j <= i; j++) {
                matrix.set(i, j, d[i][j]);
                matrix.set(j, i, d[i][j]);
            }
        }

        return new CovarianceMatrix(nodes, matrix, 173);
    }

    private CovarianceMatrix getBollenSimulationExampleData() {

        final double[][] d = new double[][]{
                {2.034},
                {0.113, 1.281},
                {0.510, 0.093, 1.572},
                {0.105, 0.857, 0.447, 1.708},
                {0.998, 0.228, 0.170, 0.345, 1.651},
        };

        final Node y1 = new ContinuousVariable("y1");
        final Node y2 = new ContinuousVariable("y2");
        final Node y3 = new ContinuousVariable("y3");
        final Node y4 = new ContinuousVariable("y4");
        final Node y5 = new ContinuousVariable("y5");

        final List<Node> nodes = new ArrayList<>();
        nodes.add(y1);
        nodes.add(y2);
        nodes.add(y3);
        nodes.add(y4);
        nodes.add(y5);

        final Matrix matrix = new Matrix(5, 5);

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j <= i; j++) {
                matrix.set(i, j, d[i][j]);
                matrix.set(j, i, d[i][j]);
            }
        }

        return new CovarianceMatrix(nodes, matrix, 1000);
    }
}




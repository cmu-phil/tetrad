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
import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.DeltaTetradTest;
import edu.cmu.tetrad.search.utils.TetradInt;
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
 * @author josephramsey
 */
public class TestDeltaTetradTest {

    @Test
    public void test4aIteratedPositives() {
        RandomUtil.getInstance().setSeed(482834823L);

        final int numTrials = 10;
        final double alpha = 0.2;
        SemIm sem = getFigure4aSem();

        int[] sampleSizes = {100, 500, 1000, 5000};

        double[][] answers = {{.1, .5, .2, .1, .1, .1},
                {0.1, 0.6, 0.0, 0.0, 0.0, 0.0},
                {0.1, 0.7, 0.2, 0.2, 0.2, 0.2},
                {0.3, 0.7, 0.2, 0.1, 0.1, 0.1}};

        for (int i = 0; i < 4; i++) {
            System.out.println("i = " + i);
            int sampleSize = sampleSizes[i];
            int[] count = new int[6];

            for (int k = 0; k < numTrials; k++) {
                DataSet data = sem.simulateData(sampleSize, false);
                int x1 = 1;
                int x2 = 2;
                int x3 = 3;
                int x4 = 4;

                TetradInt t1234 = new TetradInt(x1, x2, x3, x4);
                TetradInt t1342 = new TetradInt(x1, x3, x4, x2);
                TetradInt t1423 = new TetradInt(x1, x4, x2, x3);

                DeltaTetradTest test = new DeltaTetradTest(new CorrelationMatrix(data));

                double p1 = test.computePValue(t1234);
                double p2 = test.computePValue(t1342);
                double p3 = test.computePValue(t1423);
                double p4 = test.computePValue(t1234, t1342);
                double p5 = test.computePValue(t1234, t1423);
                double p6 = test.computePValue(t1342, t1423);

                if (p1 < alpha) count[0]++;
                if (p2 < alpha) count[1]++;
                if (p3 < alpha) count[2]++;
                if (p4 < alpha) count[3]++;
                if (p5 < alpha) count[4]++;
                if (p6 < alpha) count[5]++;
            }

            double[] _answer = new double[6];

            for (int j = 0; j < 6; j++) {
                double v = count[j] / (double) numTrials;
                _answer[j] = v;
            }

        }
    }

    // Bollen and Ting, Confirmatory Tetrad Analysis, p. 164 Sympathy and Anger.

    @Test
    public void testBollenExample1() {
        CovarianceMatrix cov = getBollenExample1Data();
        List<Node> variables = cov.getVariables();

        int v1 = 0;
        int v2 = 1;
        int v3 = 2;
        int v4 = 3;
        int v5 = 4;
        int v6 = 5;

        TetradInt t1 = new TetradInt(v1, v2, v3, v4);
        TetradInt t2 = new TetradInt(v1, v2, v3, v5);
        TetradInt t3 = new TetradInt(v1, v2, v3, v6);
        TetradInt t4 = new TetradInt(v1, v4, v5, v6);
        TetradInt t5 = new TetradInt(v1, v4, v2, v3);
        TetradInt t6 = new TetradInt(v1, v5, v2, v3);
        TetradInt t7 = new TetradInt(v1, v6, v2, v3);
        TetradInt t8 = new TetradInt(v1, v6, v4, v5);

        DeltaTetradTest test = new DeltaTetradTest(cov);
//        DeltaTetradTest test = new DeltaTetradTest(new CorrelationMatrix(cov));

//        double chiSq = test.calcChiSquare(t1, t2, t3, t4, t5, t6, t7, t8);
        double pValue = test.computePValue(t1, t2, t3, t4, t5, t6, t7, t8);

        // They get chi square = 6.71 p = .57 8 df but using the raw data which they don't provide here.
        // Just using the covariance matrix provided, I get chi square = 8.46, p = 0.39, df = 8.

//        assertEquals(8.46, chiSq, 0.01);
//        assertEquals(0.18, pValue, 0.01);
        assertEquals(0.24, pValue, 0.01);
    }

    // Bollen and Ting p. 167 (Confirmatory Tetrad Analysis). Union Sentiment.

    @Test
    public void testBollenExample2() {
        CovarianceMatrix cov = getBollenExample2Data();
        List<Node> variables = cov.getVariables();

        int y1 = 0;
        int y2 = 1;
        int y3 = 2;
        int x1 = 3;
        int x2 = 4;

        TetradInt t1 = new TetradInt(y1, x1, x2, y2);

        DeltaTetradTest test = new DeltaTetradTest(cov);

//        double chiSq = test.calcChiSquare(t1);
        double pValue = test.computePValue(t1);

//        assertEquals(0.43, chiSq, 0.01);
        assertEquals(0.47, pValue, 0.01);

        // They get chi square = .73  p = .39  df = 1
    }

    // Bollen 2000 A Tetrad Test for Causal Indicators, p. 13.

    @Test
    public void testBollenSimulationExample() {
        CovarianceMatrix cov = getBollenSimulationExampleData();

        List<Node> variables = cov.getVariables();

        int y1 = 0;
        int y2 = 1;
        int y3 = 2;
        int y4 = 3;
        int y5 = 4;

        TetradInt t1 = new TetradInt(y1, y2, y3, y4);
        TetradInt t2 = new TetradInt(y1, y2, y4, y3);
        TetradInt t3 = new TetradInt(y1, y3, y4, y2);
        TetradInt t4 = new TetradInt(y1, y2, y3, y5);
        TetradInt t5 = new TetradInt(y1, y2, y5, y3);
        TetradInt t6 = new TetradInt(y1, y3, y5, y2);
        TetradInt t7 = new TetradInt(y1, y2, y4, y5);
        TetradInt t8 = new TetradInt(y1, y2, y5, y4);
        TetradInt t9 = new TetradInt(y1, y4, y5, y2);
        TetradInt t10 = new TetradInt(y1, y3, y4, y5);
        TetradInt t11 = new TetradInt(y1, y3, y5, y4);
        TetradInt t12 = new TetradInt(y1, y4, y5, y3);
        TetradInt t13 = new TetradInt(y2, y3, y4, y5);
        TetradInt t14 = new TetradInt(y2, y3, y5, y4);
        TetradInt t15 = new TetradInt(y2, y4, y5, y3);

        TetradInt[] tetrads = {t1, t2, t3, t4};

        DeltaTetradTest test = new DeltaTetradTest(cov);

//        double chiSq = test.calcChiSquare(tetrads[0]);
        double pValue = test.computePValue(tetrads[0]);

//        assertEquals(44.57, chiSq, 0.1);
        assertEquals(2.46E-11, pValue, .001);

        TetradInt[] independentTetrads = {t1, t2, t4, t6, t10};

//        chiSq = test.calcChiSquare(independentTetrads[0]);
        pValue = test.computePValue(independentTetrads[0]);

//        assertEquals(44.6, chiSq, 0.1);
        assertEquals(2.46E-11, pValue, .001);

        {
//            chiSq = test.calcChiSquare(independentTetrads);
            pValue = test.computePValue(independentTetrads);

//            assertEquals(95.39, chiSq, 0.01);
            assertEquals(0.0, pValue, 0.01);
        }

        // They get chsq = 64.13 and so do I.
    }

    @Test
    public void test3() {
        SemPm pm = makePm();
        DataSet data = new SemIm(pm).simulateData(1000, false);

        CovarianceMatrix cov = new CovarianceMatrix(data);

        List<Node> variables = data.getVariables();
        int x1 = 0;
        int x2 = 1;
        int x3 = 2;
        int x4 = 3;
        int x5 = 4;

        TetradInt t1234 = new TetradInt(x1, x2, x3, x4);
        TetradInt t1342 = new TetradInt(x1, x3, x4, x2);
        TetradInt t1423 = new TetradInt(x1, x4, x2, x3);

        DeltaTetradTest test1 = new DeltaTetradTest(data);
        double chiSq1 = test1.computePValue(t1234, t1342);

        DeltaTetradTest test2 = new DeltaTetradTest(cov);
        double chiSq2 = test2.computePValue(t1234, t1342);
    }

    private SemPm makePm() {
        List<Node> variableNodes = new ArrayList<>();
        ContinuousVariable x1 = new ContinuousVariable("X1");
        ContinuousVariable x2 = new ContinuousVariable("X2");
        ContinuousVariable x3 = new ContinuousVariable("X3");
        ContinuousVariable x4 = new ContinuousVariable("X4");
        ContinuousVariable x5 = new ContinuousVariable("X5");

        variableNodes.add(x1);
        variableNodes.add(x2);
        variableNodes.add(x3);
        variableNodes.add(x4);
        variableNodes.add(x5);

        Graph _graph = new EdgeListGraph(variableNodes);
        SemGraph graph = new SemGraph(_graph);
        graph.addDirectedEdge(x5, x1);
        graph.addDirectedEdge(x5, x2);
        graph.addDirectedEdge(x5, x3);
        graph.addDirectedEdge(x5, x4);

        return new SemPm(graph);
    }

    private SemIm getFigure4aSem() {
        Graph graph = new EdgeListGraph();

        Node xi1 = new GraphNode("xi1");
        Node x1 = new GraphNode("x1");
        Node x2 = new GraphNode("x2");
        Node x3 = new GraphNode("x3");
        Node x4 = new GraphNode("x4");

        graph.addNode(xi1);
        graph.addNode(x1);
        graph.addNode(x2);
        graph.addNode(x3);
        graph.addNode(x4);

        graph.addDirectedEdge(xi1, x1);
        graph.addDirectedEdge(xi1, x2);
        graph.addDirectedEdge(xi1, x3);
        graph.addDirectedEdge(xi1, x4);

        SemPm pm = new SemPm(graph);

        Parameters params = new Parameters();
//        params.setCoefRange(0.3, 0.8);

        return new SemIm(pm, params);
    }

    private SemIm getFigure4bSem() {
        Graph graph = new EdgeListGraph();

        Node xi1 = new GraphNode("xi1");
        Node xi2 = new GraphNode("xi2");
        Node x1 = new GraphNode("x1");
        Node x2 = new GraphNode("x2");
        Node x3 = new GraphNode("x3");
        Node x4 = new GraphNode("x4");

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

        SemPm pm = new SemPm(graph);
        return new SemIm(pm);
    }

    private CovarianceMatrix getBollenExample1Data() {

        // Sympathy and anger, p. 164.

        double[][] d = {
                {6.982},
                {4.686, 6.047},
                {4.335, 3.307, 5.037},
                {-2.294, -1.453, -1.979, 5.569},
                {-2.209, -1.262, -1.738, 3.931, 5.328},
                {-1.671, -1.401, -1.564, 3.915, 3.601, 4.977}
        };

        Node v1 = new ContinuousVariable("v1");
        Node v2 = new ContinuousVariable("v2");
        Node v3 = new ContinuousVariable("v3");
        Node v4 = new ContinuousVariable("v4");
        Node v5 = new ContinuousVariable("v5");
        Node v6 = new ContinuousVariable("v6");

        List<Node> nodes = new ArrayList<>();
        nodes.add(v1);
        nodes.add(v2);
        nodes.add(v3);
        nodes.add(v4);
        nodes.add(v5);
        nodes.add(v6);

        Matrix matrix = new Matrix(6, 6);

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

        double[][] d = {
                {14.610},
                {-5.250, 11.017},
                {-8.057, 11.087, 31.971},
                {-0.482, 0.677, 1.559, 1.021},
                {-18.857, 17.861, 28.250, 7.139, 215.662},
        };

        Node y1 = new ContinuousVariable("y1");
        Node y2 = new ContinuousVariable("y2");
        Node y3 = new ContinuousVariable("y3");
        Node x1 = new ContinuousVariable("x1");
        Node x2 = new ContinuousVariable("x2");

        List<Node> nodes = new ArrayList<>();
        nodes.add(y1);
        nodes.add(y2);
        nodes.add(y3);
        nodes.add(x1);
        nodes.add(x2);

        Matrix matrix = new Matrix(5, 5);

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j <= i; j++) {
                matrix.set(i, j, d[i][j]);
                matrix.set(j, i, d[i][j]);
            }
        }

        return new CovarianceMatrix(nodes, matrix, 173);
    }

    private CovarianceMatrix getBollenSimulationExampleData() {

        double[][] d = {
                {2.034},
                {0.113, 1.281},
                {0.510, 0.093, 1.572},
                {0.105, 0.857, 0.447, 1.708},
                {0.998, 0.228, 0.170, 0.345, 1.651},
        };

        Node y1 = new ContinuousVariable("y1");
        Node y2 = new ContinuousVariable("y2");
        Node y3 = new ContinuousVariable("y3");
        Node y4 = new ContinuousVariable("y4");
        Node y5 = new ContinuousVariable("y5");

        List<Node> nodes = new ArrayList<>();
        nodes.add(y1);
        nodes.add(y2);
        nodes.add(y3);
        nodes.add(y4);
        nodes.add(y5);

        Matrix matrix = new Matrix(5, 5);

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j <= i; j++) {
                matrix.set(i, j, d[i][j]);
                matrix.set(j, i, d[i][j]);
            }
        }

        return new CovarianceMatrix(nodes, matrix, 1000);
    }
}





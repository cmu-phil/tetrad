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
import edu.cmu.tetrad.sem.SemImInitializationParams;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.TetradMatrix;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the BooleanFunction class.
 *
 * @author Joseph Ramsey
 */
public class TestDeltaTetradTest extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestDeltaTetradTest(String name) {
        super(name);
    }

    public void test4aIteratedPositives() {
        int numTrials = 1;
        double alpha = 0.2;
        SemIm sem = getFigure4aSem();

        System.out.println();
        System.out.println("1: t1234");
        System.out.println("2: t1342");
        System.out.println("3: t1423");
        System.out.println("4: t1234 & t1342");
        System.out.println("5: t1234 & t1423");
        System.out.println("6: t1342 & t1423");
        System.out.println("7: t1234 & t1342 & t1423");

        for (int sampleSize : new int[]{100, 500, 1000, 5000}) {//, 10000, 50000, 100000}) {
            int[] count = new int[7];

            for (int i = 0; i < numTrials; i++) {
                DataSet data = sem.simulateData(sampleSize, false);
                Node x1 = data.getVariable("x1");
                Node x2 = data.getVariable("x2");
                Node x3 = data.getVariable("x3");
                Node x4 = data.getVariable("x4");

                Tetrad t1234 = new Tetrad(x1, x2, x3, x4);
                Tetrad t1342 = new Tetrad(x1, x3, x4, x2);
                Tetrad t1423 = new Tetrad(x1, x4, x2, x3);

//                DeltaTetradTest test = new DeltaTetradTest(data);
//                DeltaTetradTest test = new DeltaTetradTest(new CovarianceMatrix(data));
                DeltaTetradTest test = new DeltaTetradTest(new CorrelationMatrix(data));

                double p1 = test.getPValue(t1234);
                double p2 = test.getPValue(t1342);
                double p3 = test.getPValue(t1423);
                double p4 = test.getPValue(t1234, t1342);
                double p5 = test.getPValue(t1234, t1423);
                double p6 = test.getPValue(t1342, t1423);
//                double p7 = test.getPValue(t1234, t1342, t1423);

                if (p1 < alpha) count[0]++;
                if (p2 < alpha) count[1]++;
                if (p3 < alpha) count[2]++;
                if (p4 < alpha) count[3]++;
                if (p5 < alpha) count[4]++;
                if (p6 < alpha) count[5]++;
//                if (p7 < alpha) count[6]++;
            }

            System.out.println();
            System.out.println("Figure 4a N = " + sampleSize);
            System.out.println();

            for (int j = 0; j < 6; j++) {
                System.out.println((j + 1) + ": FP rate = " + count[j] / (double) numTrials);
            }
        }
    }

    // Bollen and Ting, Confirmatory Tetrad Analysis, p. 164 Sympathy and Anger.

    public void testBollenExample1() {
        CovarianceMatrix cov = getBollenExample1Data();

        System.out.println(cov);

        List<Node> variables = cov.getVariables();

        Node v1 = variables.get(0);
        Node v2 = variables.get(1);
        Node v3 = variables.get(2);
        Node v4 = variables.get(3);
        Node v5 = variables.get(4);
        Node v6 = variables.get(5);

        Tetrad t1 = new Tetrad(v1, v2, v3, v4);
        Tetrad t2 = new Tetrad(v1, v2, v3, v5);
        Tetrad t3 = new Tetrad(v1, v2, v3, v6);
        Tetrad t4 = new Tetrad(v1, v4, v5, v6);
        Tetrad t5 = new Tetrad(v1, v4, v2, v3);
        Tetrad t6 = new Tetrad(v1, v5, v2, v3);
        Tetrad t7 = new Tetrad(v1, v6, v2, v3);
        Tetrad t8 = new Tetrad(v1, v6, v4, v5);

        DeltaTetradTest test = new DeltaTetradTest(cov);
//        DeltaTetradTest test = new DeltaTetradTest(new CorrelationMatrix(cov));

        double chiSq = test.calcChiSquare(t1, t2, t3, t4, t5, t6, t7, t8);

        System.out.println("Chi Square = " + chiSq);
        System.out.println("P value = " + test.getPValue());

        // They get chi square = 6.71 p = .57 8 df but using the raw data which they don't provide here.
        // Just using the covariance matrix provided, I get chi square = 8.46, p = 0.39, df = 8.
    }

    // Bollen and Ting p. 167 (Confirmatory Tetrad Analysis). Union Sentiment.

    public void testBollenExample2() {
        CovarianceMatrix cov = getBollenExample2Data();
        System.out.println(cov);

        List<Node> variables = cov.getVariables();

        Node y1 = variables.get(0);
        Node y2 = variables.get(1);
        Node y3 = variables.get(2);
        Node x1 = variables.get(3);
        Node x2 = variables.get(4);

        Tetrad t1 = new Tetrad(y1, x1, x2, y2);

        DeltaTetradTest test = new DeltaTetradTest(cov);

        double chiSq = test.calcChiSquare(t1);

        System.out.println("Chi Square = " + chiSq);
        System.out.println("P value = " + test.getPValue());

        // They get chi square = .73  p = .39  df = 1
        // So do I.
    }

    // Bollen 2000 A Tetrad Test for Causal Indicators, p. 13.

    public void testBollenSimulationExample() {
        CovarianceMatrix cov = getBollenSimulationExampleData();


        System.out.println(cov);

        List<Node> variables = cov.getVariables();

        Node y1 = variables.get(0);
        Node y2 = variables.get(1);
        Node y3 = variables.get(2);
        Node y4 = variables.get(3);
        Node y5 = variables.get(4);

        Tetrad t1 = new Tetrad(y1, y2, y3, y4);
        Tetrad t2 = new Tetrad(y1, y2, y4, y3);
        Tetrad t3 = new Tetrad(y1, y3, y4, y2);
        Tetrad t4 = new Tetrad(y1, y2, y3, y5);
        Tetrad t5 = new Tetrad(y1, y2, y5, y3);
        Tetrad t6 = new Tetrad(y1, y3, y5, y2);
        Tetrad t7 = new Tetrad(y1, y2, y4, y5);
        Tetrad t8 = new Tetrad(y1, y2, y5, y4);
        Tetrad t9 = new Tetrad(y1, y4, y5, y2);
        Tetrad t10 = new Tetrad(y1, y3, y4, y5);
        Tetrad t11 = new Tetrad(y1, y3, y5, y4);
        Tetrad t12 = new Tetrad(y1, y4, y5, y3);
        Tetrad t13 = new Tetrad(y2, y3, y4, y5);
        Tetrad t14 = new Tetrad(y2, y3, y5, y4);
        Tetrad t15 = new Tetrad(y2, y4, y5, y3);

        Tetrad[] tetrads = new Tetrad[]{t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15};

        DeltaTetradTest test = new DeltaTetradTest(cov);

        for (int i = 0; i < 15; i++) {
            System.out.println("INDEX " + (i + 1));
            System.out.println("Tetrad = " + tetrads[i]);
            double chiSq = test.calcChiSquare(tetrads[i]);
            System.out.println("Chi Square = " + chiSq);
            System.out.println("P value = " + test.getPValue());
        }

        System.out.println();

        Tetrad[] independentTetrads = new Tetrad[]{t1, t2, t4, t6, t10};

        for (int i = 0; i < 5; i++) {
            System.out.println("INDEX " + (i + 1));
            System.out.println("Tetrad = " + independentTetrads[i]);
            double chiSq = test.calcChiSquare(independentTetrads[i]);
            System.out.println("Chi Square = " + chiSq);
            System.out.println("P value = " + test.getPValue());
        }

        System.out.println();

        {
            double chiSq = test.calcChiSquare(independentTetrads);
            System.out.println("Chi Square = " + chiSq);
            System.out.println("P value = " + test.getPValue());
        }

        // They get chsq = 64.13 and so do I.

//        System.out.println();
//
//        {
//            Tetrad[] tetradList2 = new Tetrad[]{t5, t6, t7, t15};
//            double chiSq = test.calcChiSquare(tetradList2);
//            System.out.println("Chi Square = " + chiSq);
//            System.out.println("P value = " + test.getPValue());
//        }
    }

    public void test3() {
        SemPm pm = makePm();
        DataSet data = new SemIm(pm).simulateData(1000, false);

        CovarianceMatrix cov = new CovarianceMatrix(data);

        System.out.println(cov);

        List<Node> variables = data.getVariables();
        Node x1 = variables.get(0);
        Node x2 = variables.get(1);
        Node x3 = variables.get(2);
        Node x4 = variables.get(3);
        Node x5 = variables.get(4);

        Tetrad t1234 = new Tetrad(x1, x2, x3, x4);
        Tetrad t1342 = new Tetrad(x1, x3, x4, x2);
        Tetrad t1423 = new Tetrad(x1, x4, x2, x3);

        DeltaTetradTest test1 = new DeltaTetradTest(data);
//        DeltaTetradTest test = new DeltaTetradTest(new CorrelationMatrix(cov));

        double chiSq1 = test1.calcChiSquare(t1234, t1342);

        System.out.println("Chi Square = " + chiSq1);
        System.out.println("P value = " + test1.getPValue());

        System.out.println();

        DeltaTetradTest test2 = new DeltaTetradTest(cov);
//        DeltaTetradTest test = new DeltaTetradTest(new CorrelationMatrix(cov));

        double chiSq2 = test2.calcChiSquare(t1234, t1342);

        System.out.println("Chi Square = " + chiSq2);
        System.out.println("P value = " + test2.getPValue());
    }

    private SemPm makePm() {
        List<Node> variableNodes = new ArrayList<Node>();
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

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestDeltaTetradTest.class);
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

        SemImInitializationParams params = new SemImInitializationParams();
//        params.setCoefRange(0.3, 0.8);

        SemIm im = new SemIm(pm, params);
        return im;
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
        SemIm im = new SemIm(pm);
        return im;
    }

    private CovarianceMatrix getBollenExample1Data() {

        // Sympathy and anger, p. 164.

        double[][] d = new double[][]{
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

        List<Node> nodes = new ArrayList<Node>();
        nodes.add(v1);
        nodes.add(v2);
        nodes.add(v3);
        nodes.add(v4);
        nodes.add(v5);
        nodes.add(v6);

        TetradMatrix matrix = new TetradMatrix(6, 6);

        for (int i = 0; i < 6; i++) {
            for (int j = 0; j <= i; j++) {
                matrix.set(i, j, d[i][j]);
                matrix.set(j, i, d[i][j]);
            }
        }

        CovarianceMatrix dataSet = new CovarianceMatrix(nodes, matrix, 138);

        return dataSet;
    }

    private CovarianceMatrix getBollenExample2Data() {
        double[][] d = new double[][]{
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

        List<Node> nodes = new ArrayList<Node>();
        nodes.add(y1);
        nodes.add(y2);
        nodes.add(y3);
        nodes.add(x1);
        nodes.add(x2);

        TetradMatrix matrix = new TetradMatrix(5, 5);

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j <= i; j++) {
                matrix.set(i, j, d[i][j]);
                matrix.set(j, i, d[i][j]);
            }
        }

        CovarianceMatrix dataSet = new CovarianceMatrix(nodes, matrix, 173);

        return dataSet;
    }

    private CovarianceMatrix getBollenSimulationExampleData() {

        // Sympathy and anger, p. 164.

        double[][] d = new double[][]{
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

        List<Node> nodes = new ArrayList<Node>();
        nodes.add(y1);
        nodes.add(y2);
        nodes.add(y3);
        nodes.add(y4);
        nodes.add(y5);

        TetradMatrix matrix = new TetradMatrix(5, 5);

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j <= i; j++) {
                matrix.set(i, j, d[i][j]);
                matrix.set(j, i, d[i][j]);
            }
        }

        CovarianceMatrix dataSet = new CovarianceMatrix(nodes, matrix, 1000);

        return dataSet;
    }
}




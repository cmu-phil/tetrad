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
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.List;

/**
 * Tests Sem.
 *
 * @author Joseph Ramsey
 */
public class TestSem extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestSem(String name) {
        super(name);
    }

    public void testSet1() {
        Graph graph = constructGraph1();
        SemPm semPm = new SemPm(graph);
        ICovarianceMatrix covMatrix = constructCovMatrix1();
        SemIm sem = new SemIm(semPm, covMatrix);

        //        System.out.println(graph);
        //        System.out.println(semPm);
        //        System.out.println(covMatrix);
        System.out.println(sem);
    }

    public void testSet2() {
        Graph graph = constructGraph2();
        SemPm semPm = new SemPm(graph);
        ICovarianceMatrix covMatrix = constructCovMatrix2();
        SemIm sem = new SemIm(semPm, covMatrix);

        System.out.println(sem);
    }

    /**
     * Tests storing and retrieving param comparisons.
     */
    public void testSet3() {
        Graph graph = constructGraph2();
        SemPm semPm = new SemPm(graph);
        List parameters = semPm.getParameters();

        Parameter a = (Parameter) parameters.get(0);
        Parameter b = (Parameter) parameters.get(1);
        assertEquals(ParamComparison.NC, semPm.getParamComparison(a, b));

        semPm.setParamComparison(a, b, ParamComparison.EQ);

        assertEquals(ParamComparison.EQ, semPm.getParamComparison(a, b));
        assertEquals(ParamComparison.EQ, semPm.getParamComparison(b, a));

    }

    /**
     * The point of this test is to try to detect if the function of the
     * estimation ever changes for perhaps extraneous reasons.
     */
    public void testEstimation() {
        Graph graph = constructGraph1();
        SemPm semPm = new SemPm(graph);
        ICovarianceMatrix covMatrix = constructCovMatrix1();

        SemEstimator estimator = new SemEstimator(covMatrix, semPm);
        estimator.estimate();
        SemIm semIm2 = estimator.getEstimatedSem();

        System.out.println(semIm2);

        double[][] edgeCoef = semIm2.getEdgeCoef().toArray();

        double[][] _edgeCoef = {{0.0000, 0.7750, 0.0000, 1.3192, 0.0000},
                {0.0000, 0.0000, 1.0756, 0.0000, 0.0000},
                {0.0000, 0.0000, 0.0000, 0.9639, 0.0000},
                {0.0000, 0.0000, 0.0000, 0.0000, 0.5198},
                {0.0000, 0.0000, 0.0000, 0.0000, 0.0000}};

        for (int i = 0; i < edgeCoef.length; i++) {
            for (int j = 0; j < edgeCoef[i].length; j++) {
                assertEquals(edgeCoef[i][j], _edgeCoef[i][j], .001);
            }
        }

        double[][] errCovar = semIm2.getErrCovar().toArray();

        double[][] _errCovar = {{1.0439, 0.0000, 0.0000, 0.0000, 0.0000},
                {0.0000, 0.9293, 0.0000, 0.0000, 0.0000},
                {0.0000, 0.0000, 1.0756, 0.0000, 0.0000},
                {0.0000, 0.0000, 0.0000, 1.0233, 0.0000},
                {0.0000, 0.0000, 0.0000, 0.0000, 1.0465}};

        for (int i = 0; i < edgeCoef.length; i++) {
            for (int j = 0; j < edgeCoef[i].length; j++) {
                assertEquals(errCovar[i][j], _errCovar[i][j], .001);
            }
        }
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

        System.out.println(MatrixUtils.toString(m));
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
        Node x7 = new GraphNode("X7");
        Node x8 = new GraphNode("X8");

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
        graph.addDirectedEdge(x2, x6);
        graph.addDirectedEdge(x3, x7);
        graph.addDirectedEdge(x4, x5);
        graph.addDirectedEdge(x4, x6);
        graph.addDirectedEdge(x5, x3);
        graph.addDirectedEdge(x6, x8);
        graph.addDirectedEdge(x6, x7);

        return graph;
    }

    private ICovarianceMatrix constructCovMatrix2() {
        String[] vars =
                new String[]{"X1", "X2", "X3", "X4", "X5", "X6", "X7", "X8"};
        int sampleSize = 173;

        double[][] arr = {{1.0}, {.215, 1.0}, {-.164, -.472, 1.0},
                {.112, .079, -.157, 1.0}, {.034, .121, -.184, .407, 1.0},
                {.101, .197, -.190, .176, .120, 1.0},
                {.071, -.172, .206, -.049, -.084, -.291, 1.0},
                {.043, -.038, -.037, -.062, .028, .166, -.149, 1.0}};

        double[][] m = MatrixUtils.convertLowerTriangleToSymmetric(arr);
        TetradMatrix m2 = new TetradMatrix(m);

        return new CovarianceMatrix(DataUtils.createContinuousVariables(vars), m2, 100);
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestSem.class);
    }
}






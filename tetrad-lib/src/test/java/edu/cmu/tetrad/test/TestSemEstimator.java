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
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the MeasurementSimulator class using diagnostics devised by Richard
 * Scheines. The diagnostics are described in the Javadocs, below.
 *
 * @author Joseph Ramsey
 */
public class TestSemEstimator {

    @Test
    public void testSet1() {
        Graph graph = constructGraph1();
        SemPm semPm = new SemPm(graph);
        ICovarianceMatrix covMatrix = constructCovMatrix1();
        SemEstimator estimator = new SemEstimator(covMatrix, semPm);
        estimator.estimate();
    }

    @Test
    public void testSet2() {
        Graph graph = constructGraph2();
        SemPm semPm = new SemPm(graph);
        ICovarianceMatrix covMatrix = constructCovMatrix2();
        new SemEstimator(covMatrix, semPm);
    }

    public void testSet3() {
        Graph graph = constructGraph2();
        SemPm semPm = new SemPm(graph);
        ICovarianceMatrix covMatrix = constructCovMatrix2();
        SemEstimator estimator = new SemEstimator(covMatrix, semPm);
        estimator.estimate();
    }

    @Test
    public void testSet8() {
        Graph graph = GraphConverter.convert("X1-->X2,X2-->X3,X3-->X4,X4-->X1");

        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet data = im.simulateData(1000, false);

        new SemEstimator(data, pm, new SemOptimizerPowell()).estimate();
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

    @Test
    public void testOptimizer2() {
        List<Node> nodes = new ArrayList<Node>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph graph = new Dag(GraphUtils.randomGraph(nodes, 0, 5,
                30, 15, 15, false));
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet data = im.simulateData(1000, false);

        SemIm im2 = new SemIm(pm);
        im2.setDataSet(data);

        SemOptimizer opt = new SemOptimizerPowell();

        opt.optimize(im2);
    }

    @Test
    public void testOptimizer3() {
        List<Node> nodes = new ArrayList<Node>();

        for (int i = 0; i < 5; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph graph = new Dag(GraphUtils.randomGraph(nodes, 0, 5,
                30, 15, 15, false));
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);

        DataSet data = im.simulateData(1000, false);

        SemIm im2 = new SemIm(pm);
        im2.setDataSet(data);

        SemOptimizer opt = new SemOptimizerPowell();

        opt.optimize(im2);
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






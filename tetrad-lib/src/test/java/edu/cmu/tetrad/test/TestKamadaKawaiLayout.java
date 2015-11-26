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

import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.util.TetradAlgebra;
import edu.cmu.tetrad.util.TetradMatrix;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Tests the functions of EndpointMatrixGraph and EdgeListGraph through the
 * Graph interface.
 *
 * @author Joseph Ramsey
 */
public final class TestKamadaKawaiLayout extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestKamadaKawaiLayout(String name) {
        super(name);
    }

    public void testLayout() {
        //        Dag dag = DataGraphUtils.createRandomDag(40, 0, 80, 6, 6, 6, true);

        Dag dag = new Dag();

        GraphNode x1 = new GraphNode("X1");
        GraphNode x2 = new GraphNode("X2");
        GraphNode x3 = new GraphNode("X3");
        GraphNode x4 = new GraphNode("X4");
        GraphNode x5 = new GraphNode("X5");
        GraphNode x6 = new GraphNode("X6");
        GraphNode x7 = new GraphNode("X7");

        dag.addNode(x1);
        dag.addNode(x2);
        dag.addNode(x3);
        dag.addNode(x4);
        dag.addNode(x5);
        dag.addNode(x6);
        dag.addNode(x7);

        dag.addDirectedEdge(x1, x2);
        dag.addDirectedEdge(x2, x3);
        dag.addDirectedEdge(x4, x5);
        dag.addDirectedEdge(x5, x6);
        //        dag.addDirectedEdge(x6, x7);

        System.out.println(dag);
        //        KamadaKawaiLayout Layout = new KamadaKawaiLayout(dag);
        //        Layout.doLayout();
    }

    public void testSolve() {
        double[][] data1 = new double[][]{{1, 2}, {3, 4}};
        TetradMatrix a =
                new TetradMatrix(data1);

        double[][] data = new double[][]{{1}, {1}};
        TetradMatrix b = new TetradMatrix(data);

        TetradMatrix c = TetradAlgebra.solve(a, b);

        System.out.println(a);
        System.out.println(b);
        System.out.println(c);
    }

    //    public void testEdgeCrossings() {
    //        Graph graph = DataGraphUtils.createRandomDag(15, 0, 25, 3, 3, 3, false);
    //
    //        kamadaKawaiLayout Layout = new kamadaKawaiLayout(graph);
    //
    //        Point from1 = new Point(0, 0);
    //        Point to1 = new Point(3, 0);
    //        Point from2 = new Point(2, 0);
    //        Point to2 = new Point(4, 0);
    //
    //        boolean intersects = Layout.edgesIntersect(from1, to1, from2, to2);
    //
    //        System.out.println("Intersects: " + intersects);
    //    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestKamadaKawaiLayout.class);
    }
}






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

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.TimeLagGraph;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;


/**
 * @author Joseph Ramsey
 */
public final class TestTimeLagGraph extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestTimeLagGraph(String name) {
        super(name);
    }

    public void test1() {
        TimeLagGraph graph = new TimeLagGraph();

        Node x0 = new GraphNode("X");
        Node y0 = new GraphNode("Y");


        graph.addNode(x0);
        graph.addNode(y0);

        graph.setMaxLag(2);

        Node x1 = graph.getNode("X", 1);

        graph.addDirectedEdge(x1, y0);

        graph.setMaxLag(4);

        graph.setNumInitialLags(2);

        graph.setMaxLag(3);

        graph.setMaxLag(5);

        Node y1 = graph.getNode("Y", 1);

        graph.addDirectedEdge(y1, x0);

        graph.setMaxLag(1);

        try {
            graph.setMaxLag(0);
            // fail.
        } catch (Exception e) {
            // succeed.
        }

        graph.removeHighLagEdges(0);

        graph.addDirectedEdge(x0, y0);

        System.out.println(graph);
    }

    public void test2() {
        // This can't stay here; it will create a cycle.

        TimeLagGraph graph = new TimeLagGraph();

        Node x0 = new GraphNode("X");
        Node y0 = new GraphNode("Y");


        graph.addNode(x0);
        graph.addNode(y0);

        graph.setMaxLag(2);

        Node x1 = graph.getNode("X", 1);
        Node y1 = graph.getNode("Y", 1);

//        SemPm pm = new SemPm(graph);

    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestTimeLagGraph.class);
    }
}



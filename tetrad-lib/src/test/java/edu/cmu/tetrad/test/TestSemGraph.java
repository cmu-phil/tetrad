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

import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.SemGraph;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author josephramsey
 */
public final class TestSemGraph extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestSemGraph(String name) {
        super(name);
    }

    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestSemGraph.class);
    }

    public void test1() {
        SemGraph graph = new SemGraph();

        Node a = new GraphNode("a");
        Node b = new GraphNode("b");
        Node c = new GraphNode("c");
        Node d = new GraphNode("d");
        Node e = new GraphNode("e");

        graph.addNode(a);
        graph.addNode(b);
        graph.addNode(c);
        graph.addNode(d);
        graph.addNode(e);

        graph.addDirectedEdge(a, b);
        graph.addDirectedEdge(c, d);
        graph.addBidirectedEdge(a, c);
        graph.addBidirectedEdge(b, d);

        graph.removeEdge(a, b);

        graph.addDirectedEdge(e, a);

        graph.addBidirectedEdge(c, d);

        SemGraph graph2 = new SemGraph(graph);

        TestCase.assertEquals(graph, graph2);
    }
}







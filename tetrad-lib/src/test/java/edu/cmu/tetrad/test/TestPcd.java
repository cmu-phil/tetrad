///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.Pcd;
import edu.cmu.tetrad.search.test.MsepTest;

import static org.junit.Assert.assertEquals;


/**
 * Tests the BooleanFunction class.
 *
 * @author josephramsey
 */
public class TestPcd {

    /**
     * Runs the PC algorithm on the graph X1 --> X2, X1 --> X3, X2 --> X4, X3 --> X4. Should produce X1 -- X2, X1 -- X3,
     * X2 --> X4, X3 --> X4.
     */
//    @Test
    public void testSearch1() {
        checkSearch("X1-->X2,X1-->X3,X2-->X4,X3-->X4",
                "X1---X2,X1---X3,X2-->X4,X3-->X4");
    }

    /**
     * This will fail if the orientation loop doesn't continue after the first orientation.
     */
//    @Test
    public void testSearch2() {
        checkSearch("A-->D,A-->B,B-->D,C-->D,D-->E",
                "A-->D,A---B,B-->D,C-->D,D-->E");
    }

    /**
     * This will fail if the orientation loop doesn't continue after the first orientation.
     */
//    @Test
    public void testSearch3() {
        Knowledge knowledge = new Knowledge();
        knowledge.setForbidden("B", "D");
        knowledge.setForbidden("D", "B");
        checkWithKnowledge(
                knowledge);
    }

    /**
     * Presents the input graph to FCI and checks to make sure the output of FCI is equivalent to the given output
     * graph.
     */
    private void checkSearch(String inputGraph, String outputGraph) {

        // Set up graph and node objects.
        Graph graph = GraphUtils.convert(inputGraph);

        // Set up search.
        IndependenceTest independence = new MsepTest(graph);
        Pcd pc = new Pcd(independence);

        // Run search
        Graph resultGraph = pc.search();

        // Build comparison graph.
        Graph trueGraph = GraphUtils.convert(outputGraph);

        resultGraph = GraphUtils.replaceNodes(resultGraph, trueGraph.getNodes());

        // Do test.
        assertEquals(resultGraph, trueGraph);
    }

    /**
     * Presents the input graph to FCI and checks to make sure the output of FCI is equivalent to the given output
     * graph.
     */
    private void checkWithKnowledge(Knowledge knowledge) {

        // Set up graph and node objects.
        Graph graph = GraphUtils.convert("A-->B,C-->B,B-->D");

        // Set up search.
        IndependenceTest independence = new MsepTest(graph);
        Pc pc = new Pc(independence);
        pc.setKnowledge(knowledge);

        // Run search
        Graph resultGraph = pc.search();

        // Build comparison graph.
        Graph trueGraph = GraphUtils.convert("A-->B,C-->B,D");

        resultGraph = GraphUtils.replaceNodes(resultGraph, trueGraph.getNodes());

        // Do test.
        assertEquals(resultGraph, trueGraph);
    }
}






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

import edu.cmu.tetrad.data.DelimiterType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.MsepTest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests the PC search.
 *
 * @author josephramsey
 */
public class TestPcStableMax {

    /**
     * Runs the PC algorithm on the graph X1 --> X2, X1 --> X3, X2 --> X4, X3 --> X4. Should produce X1 -- X2, X1 -- X3,
     * X2 --> X4, X3 --> X4.
     */
    @Test
    public void testSearch1() {
        checkSearch("X1-->X2,X1-->X3,X2-->X4,X3-->X4",
                "X1---X2,X1---X3,X2-->X4,X3-->X4");
    }

    /**
     * Runs the PC algorithm on the graph X1 --> X2, X1 --> X3, X2 --> X4, X3 --> X4. Should produce X1 -- X2, X1 -- X3,
     * X2 --> X4, X3 --> X4.
     */
    @Test
    public void testSearch2() {
        checkSearch("X1-->X2,X1-->X3,X2-->X4,X3-->X4",
                "X1---X2,X1---X3,X2-->X4,X3-->X4");
    }

    /**
     * This will fail if the orientation loop doesn't continue after the first orientation.
     */
    @Test
    public void testSearch3() {
        checkSearch("A-->D,A-->B,B-->D,C-->D,D-->E",
                "A-->D,A---B,B-->D,C-->D,D-->E");
    }

    /**
     * This will fail if the orientation loop doesn't continue after the first orientation.
     */
    @Test
    public void testSearch4() {
        Knowledge knowledge = new Knowledge();
        knowledge.setForbidden("B", "D");
        knowledge.setForbidden("D", "B");
        knowledge.setForbidden("C", "B");

        System.out.println(knowledge);

        checkWithKnowledge("A-->B,C-->B,B-->D", knowledge);
    }

    //    @Test
    public void testCites() {
        final String citesString = "164\n" +
                                   "ABILITY\tGPQ\tPREPROD\tQFJ\tSEX\tCITES\tPUBS\n" +
                                   "1.0\n" +
                                   ".62\t1.0\n" +
                                   ".25\t.09\t1.0\n" +
                                   ".16\t.28\t.07\t1.0\n" +
                                   "-.10\t.00\t.03\t.10\t1.0\n" +
                                   ".29\t.25\t.34\t.37\t.13\t1.0\n" +
                                   ".18\t.15\t.19\t.41\t.43\t.55\t1.0";

        char[] citesChars = citesString.toCharArray();
        ICovarianceMatrix dataSet = SimpleDataLoader.loadCovarianceMatrix(citesChars, "//", DelimiterType.WHITESPACE,
                '\"', "*");

        Knowledge knowledge = new Knowledge();

        knowledge.addToTier(1, "ABILITY");
        knowledge.addToTier(2, "GPQ");
        knowledge.addToTier(3, "QFJ");
        knowledge.addToTier(3, "PREPROD");
        knowledge.addToTier(4, "SEX");
        knowledge.addToTier(5, "PUBS");
        knowledge.addToTier(6, "CITES");

        Pc pc = new Pc(new IndTestFisherZ(dataSet, 0.11));
        pc.setStable(true);
        pc.setUseMaxPHeuristic(true);
        pc.setKnowledge(knowledge);

        Graph CPDAG = pc.search();

        Graph _true = new EdgeListGraph(CPDAG.getNodes());

        _true.addDirectedEdge(CPDAG.getNode("ABILITY"), CPDAG.getNode("CITES"));
        _true.addDirectedEdge(CPDAG.getNode("ABILITY"), CPDAG.getNode("GPQ"));
        _true.addDirectedEdge(CPDAG.getNode("ABILITY"), CPDAG.getNode("PREPROD"));
        _true.addDirectedEdge(CPDAG.getNode("GPQ"), CPDAG.getNode("QFJ"));
        _true.addDirectedEdge(CPDAG.getNode("PREPROD"), CPDAG.getNode("CITES"));
        _true.addDirectedEdge(CPDAG.getNode("PREPROD"), CPDAG.getNode("PUBS"));
        _true.addDirectedEdge(CPDAG.getNode("PUBS"), CPDAG.getNode("CITES"));
        _true.addDirectedEdge(CPDAG.getNode("QFJ"), CPDAG.getNode("CITES"));
        _true.addDirectedEdge(CPDAG.getNode("QFJ"), CPDAG.getNode("PUBS"));
        _true.addDirectedEdge(CPDAG.getNode("SEX"), CPDAG.getNode("PUBS"));

        System.out.println(CPDAG + " " + _true);

        assertEquals(CPDAG, _true);
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
        Pc pc = new Pc(independence);

        // Run search
//        Graph resultGraph = pc.search();
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
    private void checkWithKnowledge(String input, Knowledge knowledge) {
        // Set up graph and node objects.
        Graph graph = GraphUtils.convert(input);

        // Set up search.
        IndependenceTest independence = new MsepTest(graph);
        Pc pc = new Pc(independence);
        pc.setStable(true);
        pc.setUseMaxPHeuristic(true);

        // Set up search.
        pc.setKnowledge(knowledge);

        // Run search
        Graph resultGraph = pc.search();

        // Build comparison graph.
        Graph trueGraph = GraphUtils.convert("A---B,B-->C,D");

        resultGraph = GraphUtils.replaceNodes(resultGraph, trueGraph.getNodes());

        // Do test.
        assertEquals(trueGraph, resultGraph);
    }
}






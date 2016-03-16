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
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the PC search.
 *
 * @author Joseph Ramsey
 */
public class TestPcLocal {

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

    @Test
    public void testSearch3b() {
        checkSearch("A<--B,A<--C", "A<--B,A<--C");
    }

    /**
     * This will fail if the orientation loop doesn't continue after the first orientation.
     */
    @Test
    public void testSearch4() {
        IKnowledge knowledge = new Knowledge2();
        knowledge.setForbidden("B", "D");
        knowledge.setForbidden("D", "B");
        knowledge.setForbidden("C", "B");

        checkWithKnowledge("A-->B,C-->B,B-->D", "A---B,B-->C,D", /*"A---B,B-->C,A-->D,C-->D", */
                knowledge);
    }

    @Test
    public void testCites() {
        String citesString = "164\n" +
                "ABILITY\tGPQ\tPREPROD\tQFJ\tSEX\tCITES\tPUBS\n" +
                "1.0\n" +
                ".62\t1.0\n" +
                ".25\t.09\t1.0\n" +
                ".16\t.28\t.07\t1.0\n" +
                "-.10\t.00\t.03\t.10\t1.0\n" +
                ".29\t.25\t.34\t.37\t.13\t1.0\n" +
                ".18\t.15\t.19\t.41\t.43\t.55\t1.0";

        char[] citesChars = citesString.toCharArray();
        DataReader reader = new DataReader();
        ICovarianceMatrix dataSet = reader.parseCovariance(citesChars);

        IKnowledge knowledge = new Knowledge2();

        knowledge.addToTier(1, "ABILITY");
        knowledge.addToTier(2, "GPQ");
        knowledge.addToTier(3, "QFJ");
        knowledge.addToTier(3, "PREPROD");
        knowledge.addToTier(4, "SEX");
        knowledge.addToTier(5, "PUBS");
        knowledge.addToTier(6, "CITES");

        Pc pc = new Pc(new IndTestFisherZ(dataSet, 0.11));
        pc.setKnowledge(knowledge);

        Graph pattern = pc.search();

        Graph _true = new EdgeListGraph(pattern.getNodes());

        _true.addDirectedEdge(pattern.getNode("ABILITY"), pattern.getNode("CITES"));
        _true.addDirectedEdge(pattern.getNode("ABILITY"), pattern.getNode("GPQ"));
        _true.addDirectedEdge(pattern.getNode("ABILITY"), pattern.getNode("PREPROD"));
        _true.addDirectedEdge(pattern.getNode("GPQ"), pattern.getNode("QFJ"));
        _true.addDirectedEdge(pattern.getNode("PREPROD"), pattern.getNode("CITES"));
        _true.addDirectedEdge(pattern.getNode("PREPROD"), pattern.getNode("PUBS"));
        _true.addDirectedEdge(pattern.getNode("PUBS"), pattern.getNode("CITES"));
        _true.addDirectedEdge(pattern.getNode("QFJ"), pattern.getNode("CITES"));
        _true.addDirectedEdge(pattern.getNode("QFJ"), pattern.getNode("PUBS"));
        _true.addDirectedEdge(pattern.getNode("SEX"), pattern.getNode("PUBS"));

//        System.out.println(pattern);

        assertEquals(pattern, _true);
    }

    /**
     * Presents the input graph to Fci and checks to make sure the output of Fci is equivalent to the given output
     * graph.
     */
    private void checkSearch(String inputGraph, String outputGraph) {

        // Set up graph and node objects.
        Graph graph = GraphConverter.convert(inputGraph);

        // Set up search.
        IndependenceTest independence = new IndTestDSep(graph);
        PcLocal pc = new PcLocal(independence);

        // Run search
//        Graph resultGraph = pc.search();
        Graph resultGraph = pc.search();

        // Build comparison graph.
        Graph trueGraph = GraphConverter.convert(outputGraph);

        // PrintUtil out problem and graphs.
//        System.out.println("\nInput graph:");
//        System.out.println(graph);
//        System.out.println("\nResult graph:");
//        System.out.println(resultGraph);
//        System.out.println("\nTrue graph:");
//        System.out.println(trueGraph);
//
        resultGraph = GraphUtils.replaceNodes(resultGraph, trueGraph.getNodes());

        // Do test.
        assertTrue(resultGraph.equals(trueGraph));
    }

    /**
     * Presents the input graph to Fci and checks to make sure the output of Fci is equivalent to the given output
     * graph.
     */
    private void checkWithKnowledge(String inputGraph, String outputGraph,
                                    IKnowledge knowledge) {
        // Set up graph and node objects.
        Graph graph = GraphConverter.convert(inputGraph);

        // Set up search.
        IndependenceTest independence = new IndTestDSep(graph);
        Pc pc = new Pc(independence);

        // Set up search.
        pc.setKnowledge(knowledge);
//        pc.setVerbose(true);

        // Run search
        Graph resultGraph = pc.search();

        // Build comparison graph.
        Graph trueGraph = GraphConverter.convert(outputGraph);

//        System.out.println("Knowledge = " + knowledge);
//        System.out.println("True graph = " + graph);
//        System.out.println("Result graph = " + resultGraph);

        // Do test.
        assertTrue(resultGraph.equals(trueGraph));
    }

    @Test
    public void testPcStable2() {
        RandomUtil.getInstance().setSeed(1450030184196L);
        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph graph = GraphUtils.randomGraph(nodes, 0, 10, 30, 15, 15, false);
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(200, false);

        TetradLogger.getInstance().setForceLog(false);
        IndependenceTest test = new IndTestFisherZ(data, 0.05);

        PcStable pc = new PcStable(test);
        pc.setVerbose(false);
        Graph pattern = pc.search();

        for (int i = 0; i < 1; i++) {
            DataSet data2 = DataUtils.reorderColumns(data);
            IndependenceTest test2 = new IndTestFisherZ(data2, 0.05);
            PcStable pc2 = new PcStable(test2);
            pc2.setVerbose(false);
            Graph pattern2 = pc2.search();
            assertTrue(pattern.equals(pattern2));
        }
    }


}






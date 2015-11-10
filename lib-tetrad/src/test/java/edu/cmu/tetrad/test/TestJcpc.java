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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemImInitializationParams;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.TetradLogger;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests the BooleanFunction class.
 *
 * @author Joseph Ramsey
 */
public class TestJcpc extends TestCase {

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestJcpc(String name) {
        super(name);
    }

    public void testBlank() {
        // Blank to keep the automatic JUnit runner happy.
    }

    /**
     * Runs the PC algorithm on the graph X1 --> X2, X1 --> X3, X2 --> X4, X3 --> X4. Should produce X1 -- X2, X1 -- X3,
     * X2 --> X4, X3 --> X4.
     */
    public void rtestSearch1() {
        checkSearch("X1-->X2,X1-->X3,X2-->X4,X3-->X4",
                "X1---X2,X1---X3,X2-->X4,X3-->X4");
    }

    public void testSearch4() {
        int numVars = 4;
        int sampleSize = 1000;

        Dag trueGraph = new Dag(GraphUtils.randomGraph(numVars, 0, numVars, 30, 15, 15, false));

        System.out.println("\nInput graph:");
        System.out.println(trueGraph);

        System.out.println("********** SAMPLE SIZE = " + sampleSize);

        SemPm semPm = new SemPm(trueGraph);
        SemIm bayesIm = new SemIm(semPm);
        DataSet dataSet = bayesIm.simulateData(sampleSize, false);

        IndependenceTest test = new IndTestFisherZ(dataSet, 0.001);
        Jcpc ges = new Jcpc(test);

        // Run search
        Graph resultGraph = ges.search();

        // PrintUtil out problem and graphs.
        System.out.println("\nResult graph:");
        System.out.println(resultGraph);
    }


    public void testSearch5() {
        int numVars = 10;
        int numEdges = 20;
        int sampleSize = 1000;

        Dag trueGraph = new Dag(GraphUtils.randomGraph(numVars, 0, numEdges, 7,
                5, 5, false));

        System.out.println("\nInput graph:");
        System.out.println(trueGraph);

        System.out.println("********** SAMPLE SIZE = " + sampleSize);

        SemPm semPm = new SemPm(trueGraph);
        SemIm bayesIm = new SemIm(semPm);
        DataSet dataSet = bayesIm.simulateData(sampleSize, false);

        IndependenceTest test = new IndTestFisherZ(dataSet, 0.001);
        Jcpc ges = new Jcpc(test);

        // Run search
        Graph resultGraph = ges.search();

        // PrintUtil out problem and graphs.
        System.out.println("\nResult graph:");
        System.out.println(resultGraph);
    }

    public void testSearch9() {
        TetradLogger.getInstance().setForceLog(false);

        Graph trueGraph = new EdgeListGraph();

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Node x3 = new GraphNode("X3");
        Node x4 = new GraphNode("X4");
        Node x5 = new GraphNode("X5");

        trueGraph.addNode(x1);
        trueGraph.addNode(x2);
        trueGraph.addNode(x3);
        trueGraph.addNode(x4);
        trueGraph.addNode(x5);

        trueGraph.addDirectedEdge(x1, x3);
        trueGraph.addDirectedEdge(x2, x3);
        trueGraph.addDirectedEdge(x3, x4);
        trueGraph.addDirectedEdge(x4, x5);
        trueGraph.addDirectedEdge(x1, x5);
        trueGraph.addDirectedEdge(x2, x5);

        System.out.println("True graph = " + trueGraph);

        int sampleSize = 1000;

        System.out.println("Large sem simulator");
        SemPm pm = new SemPm(trueGraph);
        SemImInitializationParams params = new SemImInitializationParams();
        SemIm im = new SemIm(pm, params);

        System.out.println("... simulating data");
        DataSet dataSet = im.simulateData(sampleSize, false);

        Graph truePattern = SearchGraphUtils.patternForDag(trueGraph);

        System.out.println("JCPC");

        Jcpc search = new Jcpc(new IndTestFisherZ(dataSet, 0.001));
        Graph patternJcpc = search.search();
        System.out.println(SearchGraphUtils.graphComparisonString("JCPC pattern ", patternJcpc, "True pattern", truePattern, false));
        System.out.println(patternJcpc);
    }

    /**
     * Presents the input graph to Fci and checks to make sure the output of Fci is equivalent to the given output
     * graph.
     */
    private void checkSearch(String inputGraph, String outputGraph) {

        // Set up graph and node objects.
        Graph graph = GraphConverter.convert(inputGraph);
        SemPm semPm = new SemPm(graph);
        SemIm semIM = new SemIm(semPm);
        DataSet dataSet = semIM.simulateData(500, false);

        // Set up search.
        Ges ges = new Ges(dataSet);
        ges.setTrueGraph(graph);

        // Run search
        Graph resultGraph = ges.search();

        // Build comparison graph.
        Graph trueGraph = GraphConverter.convert(outputGraph);

        // PrintUtil out problem and graphs.
        System.out.println("\nInput graph:");
        System.out.println(graph);
        System.out.println("\nResult graph:");
        System.out.println(resultGraph);

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
        SemPm semPm = new SemPm(graph);
        SemIm semIM = new SemIm(semPm);
        DataSet dataSet = semIM.simulateData(1000, false);

        // Set up search.
        Ges ges = new Ges(dataSet);
        ges.setKnowledge(knowledge);

        // Run search
        Graph resultGraph = ges.search();

        // PrintUtil out problem and graphs.
        System.out.println(knowledge);
        System.out.println("Input graph:");
        System.out.println(graph);
        System.out.println("Result graph:");
        System.out.println(resultGraph);

        // Build comparison graph.
        Graph trueGraph = GraphConverter.convert(outputGraph);

        // Do test.
        assertTrue(resultGraph.equals(trueGraph));
    }

    public void testPowerSet() {
        List<Node> nodes = new ArrayList<Node>();

        nodes.add(new GraphNode("X"));
        nodes.add(new GraphNode("Y"));
        nodes.add(new GraphNode("Z"));

        System.out.println(powerSet(nodes));
    }

    private static List<Set<Node>> powerSet(List<Node> nodes) {
        List<Set<Node>> subsets = new ArrayList<Set<Node>>();
        int total = (int) Math.pow(2, nodes.size());
        for (int i = 0; i < total; i++) {
            Set<Node> newSet = new HashSet<Node>();
            String selection = Integer.toBinaryString(i);

            int shift = nodes.size() - selection.length();

            for (int j = nodes.size() - 1; j >= 0; j--) {
                if (j >= shift && selection.charAt(j - shift) == '1') {
                    newSet.add(nodes.get(j));
                }
            }
            subsets.add(newSet);
        }

        return subsets;
    }
    /**
     * This method uses reflection to collect up all of the test methods from this class and return them to the test
     * runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestJcpc.class);
    }
}





